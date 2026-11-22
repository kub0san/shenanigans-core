package com.hizeric.shenaniganscore.core.network;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.util.ClientBlockUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the server-independent projection and the guarded send path of the PacketEvents
 * block-change transport. PacketEvents is compileOnly for the plugin and is deliberately not on
 * the unit-test compile classpath; {@code WrappedBlockState} static init needs the live plugin-owned
 * API singleton, so packet construction itself is verified against the real dependency API in the
 * task report rather than faked here. No test ever installs an API singleton.
 *
 * <p>The send path and the enable install are exercised through package-private seams with recorder
 * doubles, so deleting either production call fails a behavioral assertion instead of only a text
 * check.</p>
 */
class PacketEventsTransportTest {

    /** Minimal stand-in: the projection only ever reads {@code getAsString()}. */
    private static BlockData blockData(String state) {
        return (BlockData) Proxy.newProxyInstance(
                PacketEventsTransportTest.class.getClassLoader(),
                new Class<?>[]{BlockData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAsString", "toString" -> state;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    /** The send path only checks the world reference is non-null; no World method is invoked. */
    private static World world() {
        return (World) Proxy.newProxyInstance(
                PacketEventsTransportTest.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "World[test]";
                    default -> null;
                });
    }

    /** Stable identity for the player forwarded to the packet sink. */
    private static Player player() {
        return (Player) Proxy.newProxyInstance(
                PacketEventsTransportTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Player[test]";
                    default -> null;
                });
    }

    private static Path source(String... parts) {
        Path path = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore");
        for (String part : parts) {
            path = path.resolve(part);
        }
        return path;
    }

    /** Removes line and block comments while preserving string/char literal contents. */
    private static String stripComments(String java) {
        StringBuilder out = new StringBuilder(java.length());
        boolean line = false;
        boolean block = false;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < java.length(); i++) {
            char c = java.charAt(i);
            char next = i + 1 < java.length() ? java.charAt(i + 1) : '\0';

            if (line) {
                if (c == '\n' || c == '\r') {
                    line = false;
                    out.append(c);
                }
                continue;
            }
            if (block) {
                if (c == '*' && next == '/') {
                    block = false;
                    i++;
                } else if (c == '\n' || c == '\r') {
                    out.append(c);
                }
                continue;
            }
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < java.length()) {
                    out.append(java.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                out.append(c);
                if (c == '\\' && i + 1 < java.length()) {
                    out.append(java.charAt(++i));
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                line = true;
                i++;
            } else if (c == '/' && next == '*') {
                block = true;
                i++;
            } else if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c == '\'') {
                inChar = true;
                out.append(c);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Extracts a method body by brace matching, ignoring braces inside string/char literals. */
    private static String methodBody(String source, String signature) {
        int sig = source.indexOf(signature);
        assertTrue(sig >= 0, "expected method: " + signature);
        int open = source.indexOf('{', sig);
        assertTrue(open >= 0, "expected a method body for: " + signature);

        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
                continue;
            }
            if (inChar) {
                if (c == '\\') i++;
                else if (c == '\'') inChar = false;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                if (--depth == 0) return source.substring(open + 1, i);
            }
        }
        throw new AssertionError("unbalanced braces in " + signature);
    }

    @Test
    void projectsBlockPositionFromLocationWithoutALiveServer() {
        PacketEventsTransport.BlockChange change = PacketEventsTransport.toBlockChange(
                new Location(null, 12.9, -5, -33.2), blockData("minecraft:stone"));

        assertEquals(12, change.x());
        assertEquals(-5, change.y());
        // Location.getBlockZ() floors negatives; packet positions must use the same block coords.
        assertEquals(-34, change.z());
    }

    @Test
    void carriesTheBlockDataStateStringThroughVerbatim() {
        String state = "minecraft:stone[axis=x]";
        PacketEventsTransport.BlockChange change = PacketEventsTransport.toBlockChange(
                new Location(null, 1, 2, 3), blockData(state));

        assertEquals(state, change.state());
    }

    @Test
    void sendFailsClosedWhenThePacketEventsSingletonIsAbsent() {
        assertFalse(PacketEventsBridge.isReady(), "unit tests must not install a PacketEvents API");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new PacketEventsTransport().send(null, null));

        assertTrue(error.getMessage().contains("PacketEvents is not initialized"));
    }

    @Test
    void sendProjectsAndForwardsEveryQueuedChangeThroughThePacketSink() {
        List<String> calls = new ArrayList<>();
        List<PacketEventsTransport.BlockChange> sent = new ArrayList<>();
        Player player = player();
        World world = world();
        PacketEventsTransport transport = new PacketEventsTransport(
                () -> calls.add("ready"),
                (actualPlayer, change) -> {
                    calls.add("send");
                    assertSame(player, actualPlayer, "the queued player must be forwarded");
                    sent.add(change);
                });

        Map<Location, BlockData> changes = new LinkedHashMap<>();
        changes.put(new Location(world, 12.9, -5, -33.2), blockData("minecraft:stone[axis=x]"));
        changes.put(new Location(world, 7.1, 64, 8.9), blockData("minecraft:air"));

        transport.send(player, changes);

        assertEquals(List.of("ready", "send", "send"), calls,
                "the readiness guard must run once before any packet work");
        assertEquals(2, sent.size(), "every queued change must reach the packet sink");
        assertEquals(
                new PacketEventsTransport.BlockChange(12, -5, -34, "minecraft:stone[axis=x]"),
                sent.get(0));
        assertEquals(new PacketEventsTransport.BlockChange(7, 64, 8, "minecraft:air"), sent.get(1));
    }

    @Test
    void enableInstallSequenceRunsTheGuardBeforeTheInstaller() throws Exception {
        Method install = ShenanigansCore.class.getDeclaredMethod(
                "installBlockChangeTransport", Runnable.class, Consumer.class);
        assertTrue(Modifier.isStatic(install.getModifiers()), "the install seam must be static");
        install.setAccessible(true);

        List<String> calls = new ArrayList<>();
        List<ClientBlockUtils.BlockChangeTransport> installed = new ArrayList<>();
        install.invoke(null,
                (Runnable) () -> calls.add("ready"),
                (Consumer<ClientBlockUtils.BlockChangeTransport>) transport -> {
                    calls.add("install");
                    installed.add(transport);
                });

        assertEquals(List.of("ready", "install"), calls,
                "enable must require readiness before installing the transport");
        assertEquals(1, installed.size(), "enable must install exactly one transport");
        assertTrue(installed.get(0) instanceof PacketEventsTransport,
                "enable must install the PacketEvents transport");
    }

    @Test
    void enableDelegatesToTheInstallSequenceAfterTheReadinessGate() throws IOException {
        String core = stripComments(Files.readString(source("ShenanigansCore.java"), StandardCharsets.UTF_8));
        String enable = methodBody(core, "public void onEnable()");

        int gate = enable.indexOf("PacketEventsBridge.isReady()");
        int install = enable.indexOf(
                "installBlockChangeTransport(PacketEventsBridge::requireReady, ClientBlockUtils::setTransport)");
        assertTrue(gate >= 0, "the enable readiness/disable gate must be preserved");
        assertTrue(install > gate, "the transport install must be wired after the enable gate");

        String transport = stripComments(Files.readString(
                source("core", "network", "PacketEventsTransport.java"), StandardCharsets.UTF_8));
        assertFalse(transport.contains("PacketEvents.setAPI"),
                "the PacketEvents plugin owns the singleton");
        assertFalse(core.contains("PacketEvents.setAPI"),
                "the PacketEvents plugin owns the singleton");
    }

    @Test
    void legacyClientBlockUtilsContractIsUnchanged() throws Exception {
        assertEquals(void.class, ClientBlockUtils.BlockChangeTransport.class
                .getDeclaredMethod("send", Player.class, Map.class).getReturnType());
        assertEquals(1, ClientBlockUtils.BlockChangeTransport.class.getDeclaredMethods().length);

        // Overlay resend and per-player cleanup must survive the transport swap.
        ClientBlockUtils.class.getDeclaredMethod(
                "resendChunk", Player.class, World.class, int.class, int.class);
        ClientBlockUtils.class.getDeclaredMethod("clearPlayer", Player.class);
        ClientBlockUtils.class.getDeclaredMethod("clearPlayer", UUID.class);
        ClientBlockUtils.class.getDeclaredMethod("shutdown");
        Method setTransport = ClientBlockUtils.class.getDeclaredMethod(
                "setTransport", ClientBlockUtils.BlockChangeTransport.class);
        assertTrue(Modifier.isPublic(setTransport.getModifiers()), "setTransport must stay public");
        assertNotNull(ShenanigansCore.class);
    }
}
