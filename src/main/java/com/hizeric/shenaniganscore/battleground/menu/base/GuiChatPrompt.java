package com.hizeric.shenaniganscore.battleground.menu.base;

import com.hizeric.shenaniganscore.battleground.menu.shell.MenuLatch;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Asks a player to type something in chat. The menu closes, the player types an
 * answer (or "cancel"), and the answer comes back on the main thread. Nothing
 * the player types is shown to other players. Registers its listener on first use.
 */
public final class GuiChatPrompt implements Listener {
    private static final long TIMEOUT_TICKS = 20L * 60;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    /** The owning plugin travels with the prompt so the async chat handler reads no mutable static. */
    private record Pending(Player player, Plugin plugin, Consumer<String> onInput,
                           Runnable onCancel, ScheduledTask timeout) {
    }

    private GuiChatPrompt() {
    }

    /** @param onInput called with the typed text; @param onCancel on "cancel" or timeout */
    public static void ask(Plugin plugin, Player player, String instruction,
                           Consumer<String> onInput, Runnable onCancel) {
        if (REGISTERED.compareAndSet(false, true)) {
            try {
                Bukkit.getPluginManager().registerEvents(new GuiChatPrompt(), plugin);
            } catch (RuntimeException e) {
                REGISTERED.set(false);
                throw e;
            }
        }
        UUID id = player.getUniqueId();
        discard(id);
        player.closeInventory();

        say(player, GuiTheme.accent() + "<bold>\u270E " + GuiTheme.TEXT + instruction);
        say(player, GuiTheme.MUTED + "Type your answer in chat, or type " + GuiTheme.RED + "cancel"
                + GuiTheme.MUTED + " to go back. " + GuiTheme.FAINT + "(1 minute)");

        ScheduledTask timeout = player.getScheduler().runDelayed(plugin, task -> {
            Pending expired = PENDING.remove(id);
            if (expired != null) {
                say(player, GuiTheme.warn("Timed out, nothing was changed."));
                expired.onCancel().run();
            }
        }, null, TIMEOUT_TICKS);
        PENDING.put(id, new Pending(player, plugin, onInput, onCancel, timeout));
    }

    public static void say(Player player, String miniMessage) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    private static void discard(UUID id) {
        Pending old = PENDING.remove(id);
        if (old == null) return;
        old.timeout().cancel();
        // Re-issuing a prompt must run the displaced prompt's cleanup — otherwise askExact()'s
        // MenuLatch suppression token is never released and staged-hub reopen stays disabled.
        Player player = old.player();
        Plugin owner = old.plugin();
        if (player == null || owner == null || old.onCancel() == null) return;
        try {
            player.getScheduler().run(owner, task -> old.onCancel().run(), null);
        } catch (RuntimeException e) {
            // Scheduler unavailable (shutdown): run the cleanup inline rather than leak the latch.
            old.onCancel().run();
        }
    }

    /** Drops every waiting prompt. Called when the plugin shuts down. */
    public static void clear() {
        PENDING.values().forEach(p -> p.timeout().cancel());
        PENDING.clear();
    }

    /**
     * PlugManX/reload hook: Bukkit unregisters our listener on disable but the static
     * {@code registered} flag would otherwise stay true and silently kill chat prompts
     * after the plugin is enabled again.
     */
    public static void shutdown() {
        clear();
        REGISTERED.set(false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending pending = PENDING.remove(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        // This handler is async: bounce to the entity scheduler and run the callbacks there,
        // using the plugin captured when the prompt was opened (never mutable static state).
        event.getPlayer().getScheduler().run(pending.plugin(), task -> {
            pending.timeout().cancel();
            if (text.equalsIgnoreCase("cancel")) {
                pending.onCancel().run();
            } else {
                pending.onInput().accept(text);
            }
        }, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        discard(event.getPlayer().getUniqueId());
        // A prompt's callbacks only release the latch; on quit they never run, so clear it here.
        MenuLatch.get().clear(event.getPlayer());
    }
}
