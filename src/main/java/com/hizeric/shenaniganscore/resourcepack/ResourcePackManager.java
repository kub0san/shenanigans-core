package com.hizeric.shenaniganscore.resourcepack;

import com.hizeric.shenaniganscore.ShenanigansCore;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves the bundled pixel-art resource pack from a tiny loopback-friendly HTTP
 * listener and pushes it to every joining player as a required pack with a
 * SHA-1 hash. The pack is required: declining or failing to download it prevents
 * the player from joining, so the GUI always has its custom icons available.
 */
public final class ResourcePackManager implements Listener {
    private static final String PACK_RESOURCE = "/shenanigans-resource-pack.zip";
    private static final String PACK_PATH = "/shenanigans-resource-pack.zip";

    private final ShenanigansCore plugin;
    private byte[] zip;
    private String sha1;
    private ResourcePackInfo info;
    private HttpServer http;
    private volatile boolean enabled;

    public ResourcePackManager(ShenanigansCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        enabled = plugin.getConfig().getBoolean("resource-pack.enabled", true);
        if (!enabled) return;
        zip = loadPack();
        if (zip == null) {
            plugin.getLogger().warning("Resource pack zip not bundled; custom icons disabled.");
            enabled = false;
            return;
        }
        sha1 = sha1(zip);
        UUID id = UUID.nameUUIDFromBytes("shenanigans-resource-pack".getBytes(StandardCharsets.UTF_8));
        int port = plugin.getConfig().getInt("resource-pack.port", 34367);

        // A required pack whose URL is unreachable by clients disconnects every
        // remote player. Resolve/validate first and disable the pack (rather than
        // failing enable()) when no client-reachable address can be determined.
        final URI uri;
        try {
            String configuredHost = plugin.getConfig().getString("resource-pack.host", "");
            String bindIp = plugin.getServer().getIp();
            String host = advertisedHost(configuredHost, bindIp);
            // Tell the admin which address we picked: a wrong guess here disconnects every
            // remote player, so it must be obvious from the startup log.
            boolean autoDetected = (configuredHost == null || configuredHost.isBlank())
                    && (bindIp == null || bindIp.isBlank() || isUnreachableHost(bindIp.trim()));
            if (autoDetected && host != null) {
                plugin.getLogger().info("resource-pack.host is not set; auto-detected client-reachable host "
                        + host + ". Set resource-pack.host explicitly if clients cannot reach it.");
            }
            uri = resolveAdvertisedUri(host, port, bindIp);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Resource pack disabled: " + e.getMessage()
                            + ". Set resource-pack.host to an address reachable by clients.", e);
            enabled = false;
            return;
        }

        info = ResourcePackInfo.resourcePackInfo().id(id).uri(uri).hash(sha1).build();
        try {
            http = new HttpServer(port, zip, plugin.getLogger());
            http.start();
        } catch (IOException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not start resource-pack HTTP server on port " + port
                            + "; custom icons disabled: " + e.getMessage(), e);
            enabled = false;
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Resource pack served at " + uri + " (" + zip.length + " bytes, sha1=" + sha1 + ")");
    }

    public void disable() {
        if (http != null) http.stop();
    }

    public boolean enabled() { return enabled; }

    /** Resolves the host to advertise: explicit config, then a reachable bind address, then auto-detection. */
    String advertisedHost(String configuredHost, String bindIp) {
        return chooseAdvertisedHost(configuredHost, bindIp, detectHostCandidates());
    }

    /**
     * Pure selection seam for {@link #advertisedHost(String, String)}. Detection is injected so
     * every branch (including wildcard binds) is testable without touching network interfaces.
     */
    static String chooseAdvertisedHost(String configuredHost, String bindIp, List<String> detected) {
        String configured = configuredHost == null ? "" : configuredHost.trim();
        if (!configured.isEmpty()) return configured;
        String bind = bindIp == null ? "" : bindIp.trim();
        if (!bind.isEmpty() && !isUnreachableHost(bind)) return bind;
        return pickReachableHost(detected);
    }

    /**
     * Prefers a site-local IPv4 address (typical LAN server), then a public one. Loopback,
     * wildcard, link-local and non-IPv4 candidates are ignored: advertising any of them makes a
     * required pack fail to download and disconnects every player.
     */
    static String pickReachableHost(List<String> candidates) {
        if (candidates == null) return null;
        String best = null;
        int bestPriority = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            if (candidate == null) continue;
            String host = candidate.trim();
            if (host.isEmpty() || isUnreachableHost(host)) continue;
            int priority = ipv4Priority(host);
            if (priority < bestPriority) {
                bestPriority = priority;
                best = host;
            }
        }
        return best;
    }

    /** Non-virtual, up interfaces only; bridges/veths are virtual and would advertise a dead address. */
    static List<String> detectHostCandidates() {
        List<String> found = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address) found.add(address.getHostAddress());
                }
            }
        } catch (SocketException ignored) {
            // Return whatever was gathered before the failure.
        }
        return found;
    }

    private static int ipv4Priority(String host) {
        int[] octets = ipv4(host);
        if (octets == null) return Integer.MAX_VALUE;
        if (octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168)) return 0;
        if (octets[0] == 0 || octets[0] == 127 || octets[0] >= 224) return Integer.MAX_VALUE;
        if (octets[0] == 169 && octets[1] == 254) return Integer.MAX_VALUE; // link-local
        if (octets[0] == 100 && octets[1] >= 64 && octets[1] <= 127) return Integer.MAX_VALUE; // CGNAT
        return 1;
    }

    private static int[] ipv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return null;
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) return null;
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
            if (octets[i] < 0 || octets[i] > 255) return null;
        }
        return octets;
    }

    /**
     * Pure, side-effect-free URL resolution so it can be tested without a server.
     *
     * <p>The advertised host must be reachable by remote clients: a loopback or
     * wildcard address makes a required pack fail to download and disconnects
     * every player. Reject such values rather than advertising them.</p>
     *
     * @throws IllegalArgumentException if the port is out of range or no usable
     *                                  host can be resolved
     */
    static URI resolveAdvertisedUri(String configuredHost, int port, String bindIp) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("resource-pack.port must be in 1..65535 (was " + port + ")");
        }
        String host = configuredHost != null && !configuredHost.isBlank()
                ? configuredHost.trim()
                : (bindIp == null ? "" : bindIp.trim());
        if (isUnreachableHost(host)) {
            throw new IllegalArgumentException("no client-reachable resource-pack.host (configured='"
                    + configuredHost + "', bind='" + bindIp + "')");
        }
        return URI.create("http://" + host + ":" + port + PACK_PATH);
    }

    private static boolean isUnreachableHost(String host) {
        if (host == null || host.isBlank()) return true;
        String normalized = host.toLowerCase(Locale.ROOT);
        // Strip a single pair of surrounding brackets for IPv6 host comparison.
        String bare = normalized.startsWith("[") && normalized.endsWith("]")
                ? normalized.substring(1, normalized.length() - 1)
                : normalized;
        return bare.equals("localhost")
                || bare.equals("0.0.0.0")
                || bare.equals("::")
                || bare.equals("0:0:0:0:0:0:0:0")
                || bare.equals("::1")
                || bare.equals("0:0:0:0:0:0:0:1")
                || bare.equals("::ffff:127.0.0.1")
                || bare.startsWith("127.")
                || bare.startsWith("::ffff:127.");
    }

    private byte[] loadPack() {
        try (InputStream in = getClass().getResourceAsStream(PACK_RESOURCE)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        // Folia-compatible: use the player's own entity scheduler instead of the
        // global Bukkit scheduler, and skip the task if the player logs out first.
        player.getScheduler().runDelayed(plugin, task -> send(player), null, 20L);
    }

    /** Sends the required pack to one player. */
    public void send(Player player) {
        if (!enabled || info == null) return;
        // replace=false stacks this pack on top of any server.properties pack rather
        // than clobbering it; this plugin is the sole source in practice.
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(true)
                .replace(false)
                .callback((id, status, audience) -> {
                    // Adventure does not guarantee the callback thread; hop back to
                    // the player's entity scheduler before touching game state.
                    if (player.isOnline()) {
                        player.getScheduler().run(plugin, task -> onStatus(player, status), null);
                    }
                })
                .build();
        player.sendResourcePacks(request);
    }

    private void onStatus(Player player, ResourcePackStatus status) {
        switch (status) {
            case SUCCESSFULLY_LOADED ->
                    plugin.getLogger().info("[pack] " + player.getName() + " loaded the resource pack.");
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD ->
                    plugin.getLogger().warning("[pack] " + player.getName() + " " + status);
            default -> plugin.getLogger().info("[pack] " + player.getName() + " " + status);
        }
    }

    /** Minimal HTTP/1.1 responder — no jdk.httpserver module dependency. */
    private static final class HttpServer {
        /** Admission cap: a client-reachable listener must not accept unbounded sockets (issue #48). */
        private static final int MAX_CONNECTIONS = 64;
        private static final long ACCEPT_WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

        private final ServerSocket socket;
        private final byte[] body;
        private final Logger logger;
        private volatile boolean running = true;
        private Thread acceptThread;
        private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        private final Semaphore permits = new Semaphore(MAX_CONNECTIONS);
        private long lastAcceptWarning;

        HttpServer(int port, byte[] body, Logger logger) throws IOException {
            this.body = body;
            this.logger = logger;
            this.socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
        }

        void start() {
            acceptThread = new Thread(this::acceptLoop, "shenanigans-pack-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        private void acceptLoop() {
            while (running) {
                try {
                    Socket client = socket.accept();
                    if (!permits.tryAcquire()) {
                        // Over the cap: close immediately so no FD or worker is consumed.
                        try { client.close(); } catch (IOException ignored) { }
                        continue;
                    }
                    try {
                        workers.submit(() -> {
                            try {
                                serve(client);
                            } finally {
                                permits.release();
                            }
                        });
                    } catch (RuntimeException e) {
                        permits.release();
                        throw e;
                    }
                } catch (IOException e) {
                    if (!running) break;
                    // FD exhaustion makes accept fail in a tight loop; rate-limit the warning.
                    long now = System.nanoTime();
                    if (now - lastAcceptWarning >= ACCEPT_WARNING_INTERVAL_NANOS) {
                        lastAcceptWarning = now;
                        logger.log(Level.WARNING, "Resource pack accept failed", e);
                    }
                }
            }
        }

        private void serve(Socket client) {
            try (client) {
                client.setSoTimeout(5000);
                // Read the request line. Only a GET for the exact advertised pack
                // path is served (F-39): stray probes must not receive the zip.
                String requestLine = readRequestLine(client.getInputStream());
                String[] parts = requestLine.split(" ");
                boolean get = parts.length >= 2 && "GET".equals(parts[0]);
                boolean correctPath = get && parts[1].equals(PACK_PATH);
                OutputStream out = client.getOutputStream();
                if (!get) {
                    out.write("HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                    return;
                }
                if (!correctPath) {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                    return;
                }
                byte[] header = ("HTTP/1.1 200 OK\r\nContent-Type: application/zip\r\nContent-Length: "
                        + body.length + "\r\nConnection: close\r\nCache-Control: no-cache\r\n\r\n")
                        .getBytes(StandardCharsets.ISO_8859_1);
                out.write(header);
                out.write(body);
                out.flush();
            } catch (IOException e) {
                // Per-connection failure is expected (client aborted, etc.).
                logger.log(Level.FINE, "Resource pack connection failed", e);
            }
        }

        private static String readRequestLine(InputStream in) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
            int prev = -1;
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n' && prev == '\r') break;
                baos.write(b);
                if (baos.size() >= 8192) break;
                prev = b;
            }
            String line = baos.toString(StandardCharsets.ISO_8859_1);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            return line;
        }

        void stop() {
            running = false;
            try { socket.close(); } catch (IOException ignored) { /* socket already closed during shutdown */ }
            workers.shutdownNow();
            try {
                if (!workers.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warning("Resource pack workers did not finish within 2s of shutdown");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (acceptThread != null) {
                try { acceptThread.join(2000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}