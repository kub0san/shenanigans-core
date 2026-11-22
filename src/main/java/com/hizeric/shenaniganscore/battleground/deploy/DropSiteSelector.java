package com.hizeric.shenaniganscore.battleground.deploy;

import com.hizeric.shenaniganscore.battleground.BattlegroundDefaults;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * On-demand, whole-arena drop selection. Builds a {@link DropCohort} from the deployed roster at
 * selection time and delegates to the pure {@link DropZone}. The cohort weight function lives here
 * (a hook point for kill/death tracking) so DropZone stays testable.
 */
public final class DropSiteSelector {

    private final Collection<UUID> deployed;
    private final Function<UUID, Double> weightOf;
    private final Function<UUID, UUID> lastKillerOf;

    /** Neutral hooks: every anchor has weight 1 and nobody is avoided. */
    public DropSiteSelector(Collection<UUID> deployed) {
        this(deployed, uuid -> 1.0, uuid -> null);
    }

    public DropSiteSelector(Collection<UUID> deployed,
                            Function<UUID, Double> weightOf,
                            Function<UUID, UUID> lastKillerOf) {
        this.deployed = deployed;
        this.weightOf = weightOf == null ? uuid -> 1.0 : weightOf;
        this.lastKillerOf = lastKillerOf == null ? uuid -> null : lastKillerOf;
    }

    public DropZone.Selection select(Player player, ArenaRegion region) {
        World world = region == null ? null : region.world();
        if (world == null) return DropZone.Selection.failure(DropZone.Reason.NO_SOLID_GROUND);

        UUID self = player == null ? null : player.getUniqueId();
        UUID lastKiller = self == null ? null : lastKillerOf.apply(self);

        // Snapshot the roster once — concurrent Deploy clicks won't race with a live collection.
        List<DropCohort.Anchor> attractors = new ArrayList<>();
        List<DropZone.Point> avoiders = new ArrayList<>();
        for (UUID uuid : List.copyOf(deployed)) {
            if (uuid == null || uuid.equals(self)) continue;
            Player other = Bukkit.getPlayer(uuid);
            if (other == null || !other.isOnline()) continue;
            Location loc = other.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(world)) continue;
            int bx = loc.getBlockX();
            int bz = loc.getBlockZ();
            if (uuid.equals(lastKiller)) {
                avoiders.add(new DropZone.Point(bx, bz));
            } else {
                double weight = weightOf.apply(uuid);
                attractors.add(new DropCohort.Anchor(bx, bz,
                        Double.isFinite(weight) && weight > 0 ? weight : 1.0));
            }
        }
        DropCohort cohort = new DropCohort(attractors, avoiders);

        DropZone.Config config = new DropZone.Config(
                BattlegroundDefaults.DEPLOY_SAMPLE_STEP,
                // C10: the vanilla default is -64, but datapacks can change the build limit.
                // The world is non-null here (select() bails out otherwise), so ask it directly.
                world.getMinHeight(),
                region.max().getBlockY(),
                BattlegroundDefaults.DEPLOY_DROP_HEIGHT,
                BattlegroundDefaults.DEPLOY_SPAWN_CLEARANCE,
                BattlegroundDefaults.DEPLOY_CLEARANCE_HEIGHT,
                region.min().getBlockY(),
                region.max().getBlockY(),
                BattlegroundDefaults.DEPLOY_SWEET_SPOT,
                BattlegroundDefaults.DEPLOY_SIGMA,
                BattlegroundDefaults.DEPLOY_MIN_GAP,
                BattlegroundDefaults.DEPLOY_TIERS);

        return DropZone.select(new BukkitDropProbe(world),
                region.min().getBlockX(), region.max().getBlockX(),
                region.min().getBlockZ(), region.max().getBlockZ(),
                cohort, config);
    }
}
