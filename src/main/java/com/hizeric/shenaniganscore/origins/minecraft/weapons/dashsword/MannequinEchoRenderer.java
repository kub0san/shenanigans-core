package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemProfile;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.network.PacketEventsBridge;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet-only mannequin afterimages for the Rift Cleave dash trail.
 *
 * <p>Each dash owns one renderer. It spawns {@code minecraft:mannequin} entities with the caster's
 * live skin, makes them invisible + glowing so only a cyan outline renders (no translucent body),
 * and drives {@code minecraft:scale} down with {@link DashAnimation#ghostScale(int)} until the echo
 * ages out. Nothing here touches Bukkit display entities or the player's real profile.</p>
 *
 * <p>All PacketEvents access goes through {@link PacketEventsBridge}; this class never calls
 * {@code setAPI}. Fake entities are client-side packets only, so every viewer needs its own spawn,
 * metadata and team packets. Viewers are synced every tick, which also covers a player entering
 * range after an echo was spawned.</p>
 */
public final class MannequinEchoRenderer {

    private static final int VIEW_RADIUS = 48;
    private static final Set<MannequinEchoRenderer> ACTIVE = ConcurrentHashMap.newKeySet();

    private static final WrapperPlayServerTeams.ScoreBoardTeamInfo TEAM_INFO =
            new WrapperPlayServerTeams.ScoreBoardTeamInfo(
                    Component.empty(), Component.empty(), Component.empty(),
                    WrapperPlayServerTeams.NameTagVisibility.NEVER,
                    WrapperPlayServerTeams.CollisionRule.NEVER,
                    NamedTextColor.AQUA,
                    WrapperPlayServerTeams.OptionData.NONE);

    private final Player caster;
    private final String teamName;
    private final ItemProfile skin;
    private final Map<Integer, Echo> echoes = new LinkedHashMap<>();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private boolean closed;

    public MannequinEchoRenderer(Player caster) {
        this.caster = caster;
        this.teamName = "shenanigans:echo_" + UUID.randomUUID().toString().substring(0, 8);
        this.skin = skinOf(caster);
        ACTIVE.add(this);
    }

    /** Copy the live {@code textures} property; fall back to the dynamic name form if absent. */
    private static ItemProfile skinOf(Player player) {
        try {
            for (ProfileProperty property : player.getPlayerProfile().getProperties()) {
                if ("textures".equals(property.getName())) {
                    String signature = property.getSignature();
                    return new ItemProfile(player.getName(), player.getUniqueId(),
                            List.of(new ItemProfile.Property("textures", property.getValue(),
                                    signature == null ? "" : signature)));
                }
            }
        } catch (Throwable ignored) {
            // Profile provider unavailable — use the dynamic form below.
        }
        return new ItemProfile(player.getName(), null, List.of());
    }

    /** Spawn one static echo at {@code point}, facing the same way as the caster at spawn time. */
    public void spawn(Location point, float yaw, float pitch, int bornTick) {
        if (closed) return;
        PacketEventsBridge.requireReady();
        syncViewers();
        Echo echo = new Echo(SpigotReflectionUtil.generateEntityId(), UUID.randomUUID(),
                point.getX(), point.getY(), point.getZ(), yaw, pitch, bornTick);
        echoes.put(echo.id, echo);
        for (Viewer viewer : viewers.values()) {
            ensureTeam(viewer);
            sendSpawn(viewer.player, echo);
            sendMeta(viewer.player, echo);
            sendTeam(viewer.player, WrapperPlayServerTeams.TeamMode.ADD_ENTITIES, echo.uuid.toString());
            viewer.spawned.add(echo.id);
        }
    }

    /** Shrink live echoes and drop the ones that reached {@link DashAnimation#GHOST_LIFE}. */
    public void tick(int currentTick) {
        if (closed || !PacketEventsBridge.isReady()) return;
        syncViewers();
        for (Iterator<Echo> it = echoes.values().iterator(); it.hasNext(); ) {
            Echo echo = it.next();
            int age = currentTick - echo.bornTick;
            if (age >= DashAnimation.GHOST_LIFE) {
                despawn(echo);
                it.remove();
                continue;
            }
            echo.scale = DashAnimation.ghostScale(age);
            for (Viewer viewer : viewers.values()) {
                sendScale(viewer.player, echo);
            }
        }
    }

    /** Destroy every echo and remove the glow team for all viewers. Idempotent. */
    public void close() {
        if (closed) return;
        closed = true;
        ACTIVE.remove(this);
        for (Viewer viewer : viewers.values()) {
            int[] ids = new int[viewer.spawned.size()];
            int i = 0;
            for (int id : viewer.spawned) ids[i++] = id;
            if (ids.length > 0) send(viewer.player, new WrapperPlayServerDestroyEntities(ids));
            if (viewer.teamCreated) {
                sendTeam(viewer.player, WrapperPlayServerTeams.TeamMode.REMOVE, null);
            }
        }
        viewers.clear();
        echoes.clear();
    }

    /** PlugManX / reload safety net — call from the plugin's disable path. */
    public static void shutdown() {
        for (MannequinEchoRenderer renderer : new ArrayList<>(ACTIVE)) {
            try {
                renderer.close();
            } catch (Throwable ignored) {
                // Teardown must never abort other cleanup hooks.
            }
        }
    }

    // ------------------------------------------------------------------ viewers

    private void syncViewers() {
        if (caster == null || !caster.isOnline()) return;
        World world = caster.getWorld();
        Location here = caster.getLocation();

        // Drop viewers who left the world, logged out or walked out of range, taking their
        // client-side copies with them so entity ids and the team never linger on those clients.
        for (Iterator<Map.Entry<UUID, Viewer>> it = viewers.entrySet().iterator(); it.hasNext(); ) {
            Viewer viewer = it.next().getValue();
            Player player = viewer.player;
            boolean keep = player.isOnline() && player.getWorld() == world
                    && (player.getUniqueId().equals(caster.getUniqueId())
                        || player.getLocation().distanceSquared(here) <= VIEW_RADIUS * VIEW_RADIUS);
            if (keep) continue;
            for (int id : viewer.spawned) {
                send(player, new WrapperPlayServerDestroyEntities(id));
            }
            if (viewer.teamCreated) {
                sendTeam(player, WrapperPlayServerTeams.TeamMode.REMOVE, null);
            }
            it.remove();
        }

        // Caster always sees their own echoes; nearby players see them too. A viewer entering
        // range late gets the full spawn + metadata + team state for every still-live echo.
        List<Player> candidates = new ArrayList<>();
        candidates.add(caster);
        for (Player player : world.getPlayers()) {
            if (player.getUniqueId().equals(caster.getUniqueId())) continue;
            if (player.getLocation().distanceSquared(here) <= VIEW_RADIUS * VIEW_RADIUS) {
                candidates.add(player);
            }
        }
        for (Player player : candidates) {
            Viewer viewer = viewers.computeIfAbsent(player.getUniqueId(), k -> new Viewer(player));
            ensureTeam(viewer);
            for (Echo echo : echoes.values()) {
                if (!viewer.spawned.add(echo.id)) continue;
                sendSpawn(player, echo);
                sendMeta(player, echo);
                sendTeam(player, WrapperPlayServerTeams.TeamMode.ADD_ENTITIES, echo.uuid.toString());
            }
        }
    }

    private void ensureTeam(Viewer viewer) {
        if (viewer.teamCreated) return;
        send(viewer.player, new WrapperPlayServerTeams(
                teamName, WrapperPlayServerTeams.TeamMode.CREATE, TEAM_INFO, List.of()));
        viewer.teamCreated = true;
    }

    private void despawn(Echo echo) {
        for (Viewer viewer : viewers.values()) {
            if (!viewer.spawned.remove(echo.id)) continue;
            send(viewer.player, new WrapperPlayServerDestroyEntities(echo.id));
            if (viewer.teamCreated) {
                sendTeam(viewer.player, WrapperPlayServerTeams.TeamMode.REMOVE_ENTITIES,
                        echo.uuid.toString());
            }
        }
    }

    // ------------------------------------------------------------------ packets

    private void sendSpawn(Player player, Echo echo) {
        send(player, new WrapperPlayServerSpawnEntity(echo.id, Optional.of(echo.uuid),
                EntityTypes.MANNEQUIN, new Vector3d(echo.x, echo.y, echo.z),
                echo.pitch, echo.yaw, echo.yaw, 0, Optional.empty()));
        sendScale(player, echo);
    }

    private void sendMeta(Player player, Echo echo) {
        List<EntityData<?>> meta = List.of(
                // 0x60 = invisible (0x20) + glowing (0x40): outline only, fully see-through body.
                new EntityData<Byte>(0, EntityDataTypes.BYTE, (byte) 0x60),
                new EntityData<Boolean>(4, EntityDataTypes.BOOLEAN, true),       // silent
                new EntityData<Boolean>(5, EntityDataTypes.BOOLEAN, true),       // no gravity
                new EntityData<Byte>(16, EntityDataTypes.BYTE, (byte) 0x7F),     // hidden outer layers
                new EntityData<ItemProfile>(17, EntityDataTypes.RESOLVABLE_PROFILE, skin),
                new EntityData<Boolean>(18, EntityDataTypes.BOOLEAN, true),      // immovable
                // 19 is hide_description (an Optional component); slot 15 changed type in 26.3.
                new EntityData<Optional<Component>>(19, EntityDataTypes.OPTIONAL_ADV_COMPONENT,
                        Optional.empty()));
        send(player, new WrapperPlayServerEntityMetadata(echo.id, meta));
    }

    private void sendScale(Player player, Echo echo) {
        send(player, new WrapperPlayServerUpdateAttributes(echo.id,
                List.of(new WrapperPlayServerUpdateAttributes.Property(
                        Attributes.SCALE, echo.scale, List.of()))));
    }

    private void sendTeam(Player player, WrapperPlayServerTeams.TeamMode mode, String entity) {
        // The ScoreBoardTeamInfo is ignored by REMOVE/REMOVE_ENTITIES; passing the shared info
        // keeps us on the non-deprecated constructor.
        send(player, new WrapperPlayServerTeams(teamName, mode, TEAM_INFO,
                entity == null ? List.of() : List.of(entity)));
    }

    private static void send(Player player, PacketWrapper<?> packet) {
        if (player == null || !player.isOnline()) return;
        // Public entry points call requireReady(); teardown is defensive because PacketEvents may
        // already be disabled when the plugin is being unloaded.
        if (!PacketEventsBridge.isReady()) return;
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Throwable t) {
            ShenanigansCore plugin = ShenanigansCore.getInstance();
            if (plugin != null) {
                plugin.getLogger().warning("Failed to send mannequin echo packet to "
                        + player.getName() + ": " + t.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ state

    private static final class Viewer {
        final Player player;
        boolean teamCreated;
        final Set<Integer> spawned = new HashSet<>();

        Viewer(Player player) {
            this.player = player;
        }
    }

    private static final class Echo {
        final int id;
        final UUID uuid;
        final double x, y, z;
        final float yaw, pitch;
        final int bornTick;
        float scale = 1.0f;

        Echo(int id, UUID uuid, double x, double y, double z, float yaw, float pitch, int bornTick) {
            this.id = id;
            this.uuid = uuid;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.bornTick = bornTick;
        }
    }
}
