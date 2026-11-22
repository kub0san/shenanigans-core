package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Everything that is about <i>who is inside</i> the domain.
 *
 * <p><b>Victims</b></p>
 * <ul>
 *   <li><b>Glyph halo</b> — flickering glyphs orbit the victim's head (text displays).</li>
 *   <li><b>Signal streams</b> — trails fly from the network straight into the head.</li>
 *   <li><b>Glyph inflow (new)</b> — real enchanting-table glyphs spiral into the head.</li>
 *   <li><b>Locking sigil (new)</b> — a spinning hexagon is burned under their feet; deeper
 *       stages add a second counter-rotating hexagon and rings around the body.</li>
 *   <li><b>Scrambled action bar</b> and the pulse "download" flash.</li>
 * </ul>
 *
 * <p><b>Caster</b></p>
 * <ul>
 *   <li><b>Infinity aura (new)</b> — a glowing ∞ turns slowly around Gojo, with a bright
 *       spark chasing around it.</li>
 * </ul>
 */
public final class Limitless {

    private Limitless() {}

    private static final Random RANDOM = new Random();

    private static final String GLYPHS =
            "∞無限視聴嗅味触痛熱冷音光闇空虚情報0101ΣΩΔπ";

    private static final String[] MESSAGES_STAGE_1 = {
            "You cannot process this",
            "Too much, all at once",
            "Where does 'you' end?"
    };
    private static final String[] MESSAGES_STAGE_2 = {
            "Nothing can be done",
            "Every thought arrives too late",
            "There is only information"
    };

    private static final int HALO_SIZE = 5;

    private static final Color[] TRAIL_COLORS = {
            Color.fromRGB(175, 225, 255), Color.fromRGB(255, 255, 255), Color.fromRGB(110, 205, 255)
    };

    private static final Particle.DustOptions ICE_D = new Particle.DustOptions(Color.fromRGB(175, 225, 255), 0.75f);
    private static final Particle.DustOptions WHITE_D = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 0.85f);
    private static final Particle.DustOptions CYAN_D = new Particle.DustOptions(Color.fromRGB(110, 205, 255), 0.75f);

    // ------------------------------------------------------------------
    // Per-refresh tick (every Session.OVERLOAD_STEP_TICKS)
    // ------------------------------------------------------------------

    public static void tick(Session session, double elapsed) {
        // YAML value (6). 0 = no halos; anything above 12 is clamped.
        int maxHalos = 6;
        if (maxHalos < 0) maxHalos = 0;
        if (maxHalos > 12) maxHalos = 12;

        Set<UUID> haloed = new HashSet<>();
        int haloCount = 0;

        for (Map.Entry<UUID, Integer> entry : new ArrayList<>(session.victimStages.entrySet())) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline() || !p.isValid()) continue;
            int stage = entry.getValue();

            scramble(p, stage, elapsed);
            streams(session, p, stage, elapsed);
            glyphInflow(session, p, stage);
            lockSigil(session, p, stage, elapsed);

            if (stage >= 1 && haloCount < maxHalos) {
                haloCount++;
                haloed.add(p.getUniqueId());
                updateHalo(session, p, stage, elapsed);
            }
        }

        for (UUID id : new ArrayList<>(session.halos.keySet())) {
            if (!haloed.contains(id)) removeHalo(session, id);
        }
    }

    // ------------------------------------------------------------------
    // Caster: infinity aura
    // ------------------------------------------------------------------

    /** A slowly turning lemniscate (∞) around the caster with a spark racing along it. */
    public static void tickAura(Session session, double elapsed) {
        Player caster = session.casterPlayer();
        if (!caster.isOnline() || caster.getWorld() != session.getCenter().getWorld()) return;

        World world = caster.getWorld();
        Location base = caster.getLocation().add(0, 1.15, 0);
        double rot = elapsed * 0.7;
        double cr = Math.cos(rot), sr = Math.sin(rot);
        double a = 1.35;
        int pts = Math.max(12, (int) Math.round(22 * session.quality));

        for (int i = 0; i < pts; i++) {
            double t = Math.PI * 2.0 * i / pts;
            double s = Math.sin(t);
            double denom = 1.0 + s * s;
            double x = a * Math.cos(t) / denom;
            double y = a * 0.55 * s * Math.cos(t) / denom;
            world.spawnParticle(Particle.DUST,
                    base.getX() + x * cr, base.getY() + y, base.getZ() + x * sr,
                    1, 0, 0, 0, 0, i % 4 == 0 ? WHITE_D : ICE_D);
        }

        double tb = elapsed * 2.2;
        double sb = Math.sin(tb);
        double db = 1.0 + sb * sb;
        double bx = a * Math.cos(tb) / db;
        double by = a * 0.55 * sb * Math.cos(tb) / db;
        world.spawnParticle(Particle.END_ROD,
                base.getX() + bx * cr, base.getY() + by, base.getZ() + bx * sr,
                1, 0, 0, 0, 0.001);
    }

    // ------------------------------------------------------------------
    // Victims: action bar
    // ------------------------------------------------------------------

    private static void scramble(Player p, int stage, double elapsed) {
        Component bar;
        if (stage <= 0) {
            bar = MiniMessage.miniMessage().deserialize(
                    "<gray><italic>Something is wrong... the air has no edges.");
        } else {
            String[] pool = stage >= 2 ? MESSAGES_STAGE_2 : MESSAGES_STAGE_1;
            String msg = pool[(int) elapsed % pool.length];
            bar = Component.text()
                    .append(Component.text("∞ ", NamedTextColor.AQUA))
                    .append(noise(7))
                    .append(Component.text(" " + msg + " ", NamedTextColor.WHITE, TextDecoration.ITALIC))
                    .append(noise(7))
                    .append(Component.text(" ∞", NamedTextColor.AQUA))
                    .build();
        }
        p.sendActionBar(bar);
    }

    private static Component noise(int length) {
        TextComponent.Builder b = Component.text();
        for (int i = 0; i < length; i++) {
            b.append(Component.text(randomGlyph(),
                    TextColor.color(150 + RANDOM.nextInt(100), 200 + RANDOM.nextInt(55), 255)));
        }
        return b.build();
    }

    private static String randomGlyph() {
        return String.valueOf(GLYPHS.charAt(RANDOM.nextInt(GLYPHS.length())));
    }

    private static String randomGlyphs(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(randomGlyph());
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Victims: streams, glyph inflow, locking sigil
    // ------------------------------------------------------------------

    private static void streams(Session session, Player p, int stage, double elapsed) {
        int nodes = session.sourceCount();
        World world = p.getWorld();
        if (nodes == 0 || world != session.getCenter().getWorld()) return;

        int count = stage <= 0 ? 1 : (stage == 1 ? 3 : 5);
        Location head = p.getEyeLocation();
        for (int i = 0; i < count; i++) {
            Location src = session.sourcePoint(RANDOM.nextInt(nodes), elapsed);
            if (src.distanceSquared(head) > 45.0 * 45.0) continue;
            world.spawnParticle(Particle.TRAIL, src, 1, 0, 0, 0, 0,
                    new Particle.Trail(head.clone(),
                            TRAIL_COLORS[RANDOM.nextInt(TRAIL_COLORS.length)],
                            14 + RANDOM.nextInt(8)));
        }
    }

    /** Enchanting-table glyphs flying into the head from all around. */
    private static void glyphInflow(Session session, Player p, int stage) {
        World world = p.getWorld();
        Location head = p.getEyeLocation();
        int count = Math.max(2, (int) Math.round((stage <= 0 ? 2 : (stage == 1 ? 5 : 9)) * session.quality));
        for (int i = 0; i < count; i++) {
            double dx = (RANDOM.nextDouble() - 0.5) * 8.0;
            double dy = (RANDOM.nextDouble() - 0.2) * 4.0;
            double dz = (RANDOM.nextDouble() - 0.5) * 8.0;
            world.spawnParticle(Particle.ENCHANT, head, 0, dx, dy, dz, 1.0);
            // Heavy sculk charge riding the same path — the crushing weight of the flood.
            world.spawnParticle(Particle.SCULK_CHARGE, head, 0, dx, dy, dz, 1.0,
                    (float) (RANDOM.nextDouble() * Math.PI * 2.0));
        }
    }

    /** Hexagon burned under the victim's feet; deeper stages add a counter-rotating hexagon and body rings. */
    private static void lockSigil(Session session, Player p, int stage, double elapsed) {
        if (stage < 1) return;
        World world = p.getWorld();
        Location f = p.getLocation();
        double y = f.getY() + 0.08;

        hexOutline(world, f.getX(), y, f.getZ(), 1.05, elapsed * 0.9, stage >= 2 ? WHITE_D : ICE_D);
        if (stage >= 2) {
            hexOutline(world, f.getX(), y, f.getZ(), 1.55, -elapsed * 0.6, CYAN_D);
        }

        int pts = Math.max(6, (int) Math.round(10 * session.quality));
        ring(world, f.getX(), f.getY() + 1.0, f.getZ(), 0.75, elapsed * 1.4, pts, ICE_D);
        if (stage >= 2) {
            ring(world, f.getX(), f.getY() + 0.35, f.getZ(), 0.7, -elapsed * 1.1, pts, CYAN_D);
            ring(world, f.getX(), f.getY() + 1.7, f.getZ(), 0.6, -elapsed * 1.7, pts, WHITE_D);
        }
    }

    private static void hexOutline(World world, double cx, double y, double cz,
                                   double r, double rot, Particle.DustOptions options) {
        for (int k = 0; k < 6; k++) {
            double a0 = rot + k * Math.PI / 3.0;
            double a1 = a0 + Math.PI / 3.0;
            for (int s = 0; s < 3; s++) {
                double t = s / 3.0;
                double x = (Math.cos(a0) + (Math.cos(a1) - Math.cos(a0)) * t) * r;
                double z = (Math.sin(a0) + (Math.sin(a1) - Math.sin(a0)) * t) * r;
                world.spawnParticle(Particle.DUST, cx + x, y, cz + z, 1, 0, 0, 0, 0, options);
            }
        }
    }

    private static void ring(World world, double cx, double y, double cz,
                             double r, double rot, int pts, Particle.DustOptions options) {
        for (int i = 0; i < pts; i++) {
            double a = rot + Math.PI * 2.0 * i / pts;
            world.spawnParticle(Particle.DUST, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r,
                    1, 0, 0, 0, 0, options);
        }
    }

    // ------------------------------------------------------------------
    // Victims: glyph halo
    // ------------------------------------------------------------------

    private static void updateHalo(Session session, Player p, int stage, double elapsed) {
        UUID id = p.getUniqueId();
        List<TextDisplay> halo = session.halos.get(id);
        if (halo == null) {
            halo = spawnHalo(session, p);
            session.halos.put(id, halo);
        }
        if (halo.isEmpty()) return;

        Location head = p.getLocation().add(0, 1.75, 0);
        double speed = 1.5 + stage * 0.4;
        for (int i = 0; i < halo.size(); i++) {
            TextDisplay td = halo.get(i);
            if (td == null || !td.isValid()) continue;
            double ang = elapsed * speed + i * Math.PI * 2.0 / HALO_SIZE;
            double r = 1.0 + 0.2 * Math.sin(elapsed * 2.0 + i);
            double y = 0.25 * Math.sin(elapsed * 1.7 + i * 1.3);
            td.setTeleportDuration(Session.OVERLOAD_STEP_TICKS);
            td.teleport(head.clone().add(Math.cos(ang) * r, y, Math.sin(ang) * r));
            if (RANDOM.nextInt(3) == 0) {
                td.text(Component.text(randomGlyph(), glyphColor()));
            }
        }
    }

    private static TextColor glyphColor() {
        return TextColor.color(160 + RANDOM.nextInt(95), 215 + RANDOM.nextInt(40), 255);
    }

    private static List<TextDisplay> spawnHalo(Session session, Player p) {
        List<TextDisplay> list = new ArrayList<>(HALO_SIZE);
        World world = p.getWorld();
        Location head = p.getLocation().add(0, 1.75, 0);
        for (int i = 0; i < HALO_SIZE; i++) {
            try {
                TextDisplay td = world.spawn(head, TextDisplay.class, t -> {
                    t.setBillboard(Display.Billboard.CENTER);
                    t.setDefaultBackground(false);
                    t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    t.setShadowed(false);
                    t.setSeeThrough(true);
                    t.setPersistent(false);
                    t.setBrightness(new Display.Brightness(15, 15));
                    t.setViewRange(1.0f);
                    t.setTeleportDuration(Session.OVERLOAD_STEP_TICKS);
                    t.text(Component.text(randomGlyph(), glyphColor()));
                    t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                            new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
                });
                session.registerVisual(td);
                list.add(td);
            } catch (Throwable ignored) {
                // A thinner halo is fine.
            }
        }
        return list;
    }

    private static void removeHalo(Session session, UUID id) {
        List<TextDisplay> halo = session.halos.remove(id);
        if (halo == null) return;
        for (TextDisplay td : halo) {
            if (td == null) continue;
            session.spawnedDisplays.remove(td);
            if (td.isValid()) td.remove();
        }
    }

    public static void clearAll(Session session) {
        for (UUID id : new ArrayList<>(session.halos.keySet())) {
            removeHalo(session, id);
        }
        session.halos.clear();
    }

    // ------------------------------------------------------------------
    // Pulse download
    // ------------------------------------------------------------------

    public static void pulseCrossing(Session session, double previousRadius, double radius) {
        Location eye = session.eyeCenter;
        for (UUID id : new ArrayList<>(session.victimStages.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || p.getWorld() != eye.getWorld()) continue;
            double d = p.getEyeLocation().distance(eye);
            if (d > previousRadius && d <= radius) {
                onPulseHit(p);
            }
        }
    }

    private static void onPulseHit(Player p) {
        p.showTitle(Title.title(
                Component.empty(),
                Component.text(randomGlyphs(14), TextColor.color(160, 220, 255)),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(260), Duration.ofMillis(260))));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 0.5f + RANDOM.nextFloat() * 0.5f);
    }
}
