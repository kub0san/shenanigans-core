package com.hizeric.shenaniganscore.core.network;

import com.github.retrooper.packetevents.PacketEvents;

/**
 * Readiness contract for the PacketEvents hard dependency.
 *
 * <p>PacketEvents is a <b>provided-at-runtime</b> dependency ({@code compileOnly} in
 * {@code build.gradle.kts}, declared as {@code required: true} in {@code paper-plugin.yml} with
 * {@code load: BEFORE} and {@code join-classpath: true}). The <b>prebuilt PacketEvents plugin
 * owns the API singleton</b>: its own {@code onLoad()} performs
 * {@code PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))} and {@code getAPI().load()},
 * and its {@code onEnable()} calls {@code init()} (verified against upstream source,
 * {@code PacketEventsPlugin.java}, branch {@code 2.0}).</p>
 *
 * <p><b>Never call {@code PacketEvents.setAPI(...)} from this plugin.</b> A second
 * {@code SpigotPacketEventsBuilder.build(...)} would create a second API instance with a second
 * netty injector fighting over the same channel pipelines. This plugin only ever reads
 * {@code PacketEvents.getAPI()}.</p>
 *
 * <p>This class intentionally owns no state. It exists so packet features have a single,
 * honest place to assert the dependency is alive instead of NPE-ing (or worse, silently
 * no-oping) mid-domain. Packet features land with the domain visual overhaul; until then this
 * bridge is a verified, unused-at-runtime seam — by design.</p>
 *
 * <p>Failure policy (decision D5 of the overhaul plan): if PacketEvents is not ready, the
 * plugin does not run packaged half features. {@link #requireReady()} throws a clear exception
 * instead of proceeding degraded.</p>
 */
public final class PacketEventsBridge {

    private PacketEventsBridge() {
    }

    /**
     * @return true when the PacketEvents API singleton exists and is initialized. Never throws,
     *         even if the PacketEvents classes are absent from the classpath (e.g. a server with
     *         a hand-edited {@code paper-plugin.yml}) — it simply reports not-ready.
     */
    public static boolean isReady() {
        try {
            var api = PacketEvents.getAPI();
            return api != null && api.isInitialized();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @throws IllegalStateException with an actionable message when PacketEvents is not ready.
     *         Call this at the top of any code path that touches the PacketEvents API.
     */
    public static void requireReady() {
        if (!isReady()) {
            throw new IllegalStateException(
                    "PacketEvents is not initialized. shenanigans-core requires the PacketEvents plugin "
                            + "(paper-plugin.yml: required: true, load: BEFORE, join-classpath: true). "
                            + "Install PacketEvents and restart; do not paper over this failure.");
        }
    }

    /**
     * @return the PacketEvents version string for logging, or {@code "unknown"} when not ready.
     */
    public static String version() {
        try {
            var api = PacketEvents.getAPI();
            return api == null ? "unknown" : String.valueOf(api.getVersion());
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
