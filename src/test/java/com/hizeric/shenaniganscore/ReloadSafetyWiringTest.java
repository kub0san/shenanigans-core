package com.hizeric.shenaniganscore;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #85: a PlugManX reload replays enable/disable on the same plugin instance. The lifecycle
 * must be guarded (no duplicate installs) and symmetric (every cached registration is re-created
 * or explicitly cleared). Registration needs a live server, so the contracts are source-pinned,
 * following the {@code ConfigReloadWiringTest} convention.
 */
class ReloadSafetyWiringTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "ShenanigansCore.java");
    private static final Path CLIENT_BLOCKS = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "core", "util", "ClientBlockUtils.java");

    private static String block(String source, String start, String end) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, "missing block start: " + start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(to > from, "missing block end after " + start + ": " + end);
        return source.substring(from, to);
    }

    private static String enable(String source) {
        return block(source, "public void onEnable() {", "private void registerPermissions() {");
    }

    private static String commands(String source) {
        return block(source, "private void registerCommands() {", "public void onDisable() {");
    }

    private static String disable(String source) {
        return block(source, "public void onDisable() {", "private void runSafely(");
    }

    @Test
    void enableIsGuardedAndReinitialisesEveryCachedHolder() throws IOException {
        assertTrue(Files.isRegularFile(MAIN), "expected source at " + MAIN.toAbsolutePath());
        String body = enable(Files.readString(MAIN, StandardCharsets.UTF_8));

        assertTrue(body.contains("enabled.compareAndSet(false, true)"),
                "enable must refuse a duplicate install on the same instance");
        assertTrue(body.indexOf("enabled.compareAndSet(false, true)")
                        < body.indexOf("registerListeners()"),
                "the guard must run before any listener/registration work");
        assertTrue(body.contains("WorldEditSupport.reset()"),
                "the optional-provider availability cache must be cleared on every enable");
        assertTrue(body.contains("WorldEditBridge.clearCachedFormat()"),
                "the cached schematic format must be cleared on every enable");
        assertTrue(body.contains("installBlockChangeTransport(PacketEventsBridge::requireReady"),
                "the PacketEvents transport must be reinstalled after a readiness check");
        assertTrue(body.contains("new ConfigAutoUpdater(this).run()"),
                "config stubs must be re-merged on every enable");
        assertTrue(body.contains("reloadTuning()"), "tuning holders must be re-read on every enable");
        assertTrue(body.contains("GuiStyle.apply("), "the GUI palette must be reapplied on every enable");
        assertTrue(body.contains("Databases.install("), "the SQL pool must be opened on every enable");
    }

    @Test
    void theCommandsLifecycleHandlerIsRegisteredOncePerInstance() throws IOException {
        String body = commands(Files.readString(MAIN, StandardCharsets.UTF_8));

        assertTrue(body.contains("if (commandsRegistered) return;"),
                "a second COMMANDS handler would double-register /bg and /ability on lifecycle replay");
        assertTrue(body.contains("commandsRegistered = true;"),
                "the guard must be armed before the handler is installed");
        assertTrue(body.indexOf("if (commandsRegistered) return;")
                        < body.indexOf("registerEventHandler(LifecycleEvents.COMMANDS"),
                "the guard must precede the handler registration");
        assertTrue(body.contains("new CommandRegistrar().validate(roots)"),
                "the same declarative tree must still be validated before registration");

        String source = Files.readString(MAIN, StandardCharsets.UTF_8);
        assertFalse(disable(source).contains("commandsRegistered = false"),
                "clearing the flag on disable would re-register the surviving handler on re-enable");
    }

    @Test
    void disableIsGuardedAndUnregistersItsOwnRegistrations() throws IOException {
        String body = disable(Files.readString(MAIN, StandardCharsets.UTF_8));

        assertTrue(body.contains("enabled.compareAndSet(true, false)"),
                "disable must be idempotent when PlugManX replays it");
        assertTrue(body.indexOf("enabled.compareAndSet(true, false)")
                        < body.indexOf("runSafely("),
                "the guard must precede all teardown work");
        assertTrue(body.contains("HandlerList.unregisterAll(this)"),
                "a defensive listener backstop is required for same-classloader reloads");
        assertTrue(body.contains("hasActiveSessions()"),
                "a live domain must be announced rather than silently torn down");
        assertTrue(body.contains("RoundState.IDLE"),
                "a live round must be announced rather than silently torn down");

        for (String hook : new String[] {
                "battlegroundManager.disable()", "diffWriter.shutdown()", "SetupSession.cancelAll()",
                "Databases.closeAndClear()", "sessionManager.stopAll()",
                "ClientBlockUtils.shutdown()",
                "MannequinEchoRenderer.shutdown()", "DashVisibilityMask.shutdown()",
                "GuiChatPrompt.shutdown()", "DomainRegistry.get().clearAll()",
                "CameraShake.restoreAll()", "WorldEditBridge.setEnabled(false)",
                "DomainFloorRestoreLedger.cancelReplay()", "Bukkit.getScheduler().cancelTasks(this)"}) {
            assertTrue(body.contains(hook), "teardown must still run: " + hook);
        }
    }

    @Test
    void theUnusedDomainBlockOverlaySubsystemIsGone() throws IOException {
        Path overlayManager = Path.of("src", "main", "java", "com", "hizeric",
                "shenaniganscore", "core", "network", "DomainBlockOverlayManager.java");
        Path overlayListener = Path.of("src", "main", "java", "com", "hizeric",
                "shenaniganscore", "listeners", "OverlayListener.java");

        assertFalse(Files.exists(overlayManager),
                "DomainBlockOverlayManager had no callers and must not be shipped");
        assertFalse(Files.exists(overlayListener),
                "OverlayListener only resynced the unused overlay manager");

        String source = Files.readString(MAIN, StandardCharsets.UTF_8);
        assertFalse(source.contains("DomainBlockOverlayManager"),
                "no plugin wiring may reference the removed overlay manager");
        assertFalse(source.contains("OverlayListener"),
                "no plugin wiring may register the removed overlay listener");
        assertFalse(source.contains("domain overlays"),
                "the removed overlay subsystem must not keep a shutdown hook label");
    }

    @Test
    void shutdownResetsTheClientBlockTransportSoAStaleApiIsNeverReused() throws IOException {
        assertTrue(Files.isRegularFile(CLIENT_BLOCKS), "expected source at " + CLIENT_BLOCKS.toAbsolutePath());
        String body = block(Files.readString(CLIENT_BLOCKS, StandardCharsets.UTF_8),
                "public static void shutdown()", "private static void enqueue(");
        assertTrue(body.contains("transport = new PaperTransport()"),
                "shutdown must drop the previous enable's PacketEvents transport");
        assertTrue(body.indexOf("transport.send(player, changes)")
                        < body.indexOf("transport = new PaperTransport()"),
                "the synchronous revert must use the old transport before it is replaced");
    }

    @Test
    void theOptionalBridgeResetsActuallyClearTheirCaches() throws IOException {
        Path support = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "battleground", "arena", "WorldEditSupport.java");
        Path bridge = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "battleground", "arena", "WorldEditBridge.java");
        String supportSource = Files.readString(support, StandardCharsets.UTF_8);
        String bridgeSource = Files.readString(bridge, StandardCharsets.UTF_8);
        assertTrue(supportSource.contains("public static void reset()")
                        && supportSource.contains("apiVisible = null"),
                "WorldEditSupport.reset() must invalidate the cached class-visibility probe");
        assertTrue(bridgeSource.contains("public static void clearCachedFormat()")
                        && bridgeSource.contains("cachedFormat = null"),
                "WorldEditBridge.clearCachedFormat() must invalidate the cached schematic format");
    }

    @Test
    void thePlugManxBestEffortWarningIsRetainedUntilLiveVerification() throws IOException {
        String source = Files.readString(MAIN, StandardCharsets.UTF_8);
        assertTrue(source.contains("best-effort only"),
                "the supported-path warning must stay until the live reload matrix passes");
        assertTrue(source.contains("getPlugin(\"PlugManX\")"),
                "the warning must still key off an installed PlugMan(X) plugin");
    }
}
