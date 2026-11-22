package com.hizeric.shenaniganscore.core.display;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Solid-colour rectangles of any size, colour and alpha, built from text displays.
 *
 * <p>A {@link TextDisplay} whose text is a single space draws only its background, and the
 * background takes any ARGB, is always fully bright, and can be scaled, moved and rotated by its
 * transformation. That makes it a free, glowing, translucent quad: ideal for haze, light streaks
 * and screen overlays.</p>
 *
 * <h3>Calibration</h3>
 * The background of a one-space line is <i>not</i> one block big. Its size and its anchor depend
 * on the client's font metrics, so the three numbers below are <b>calibration constants</b>:
 * <ul>
 *   <li>{@code unitW}/{@code unitH}: world size, in blocks, of the quad at scale 1.</li>
 *   <li>{@code centerShift}: how far the quad's centre hangs below the entity origin, as a fraction
 *       of its own height. {@code 0.5} means the quad hangs entirely below the origin;
 *       {@code 0} means it is centred on it.</li>
 * </ul>
 * They can be changed at runtime ({@link #calibrate}). Everything that places a quad goes through
 * {@link #apply}, so one correction fixes every effect.
 */
public final class QuadUtils {

    private static volatile float unitW = 0.15f;
    private static volatile float unitH = 0.25f;
    private static volatile float centerShift = 0.5f;

    private QuadUtils() {
    }

    /** Overrides the calibration. Values are sanity-clamped so a typo cannot break rendering. */
    public static void calibrate(float width, float height, float shift) {
        unitW = Math.max(0.01f, Math.min(4.0f, width));
        unitH = Math.max(0.01f, Math.min(4.0f, height));
        centerShift = Math.max(-1.0f, Math.min(1.0f, shift));
    }

    public static float unitW() {
        return unitW;
    }

    public static float unitH() {
        return unitH;
    }

    public static float centerShift() {
        return centerShift;
    }

    /**
     * Spawns an invisible-until-configured quad. It starts fully transparent and tiny, so callers
     * can place it and reveal it with {@link #apply} and {@link #setColor}.
     *
     * @param hidden if true the quad is not visible to anyone until shown to a specific player
     */
    public static TextDisplay spawn(Location at, Display.Billboard billboard, float viewRange, boolean hidden) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(at.getWorld(), "world");
        return at.getWorld().spawn(at.clone(), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(!hidden);
            d.setBillboard(billboard);
            d.setViewRange(viewRange);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setLineWidth(200);
            d.text(Component.text(" "));
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(0);
            d.setTeleportDuration(0);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                    new Vector3f(0.001f, 0.001f, 0.001f), new Quaternionf()));
        });
    }

    /**
     * Places the quad so its <b>centre</b> sits at {@code (cx, cy, cz)} relative to the entity,
     * with the given size in blocks and a roll (degrees) about its own centre.
     *
     * @param interp transformation interpolation in ticks; 0 snaps
     */
    public static void apply(TextDisplay quad, float widthBlocks, float heightBlocks,
                             float cx, float cy, float cz, float rollDegrees, int interp) {
        if (quad == null || !quad.isValid()) return;
        float w = Math.max(0.0005f, widthBlocks);
        float h = Math.max(0.0005f, heightBlocks);

        Vector3f scale = new Vector3f(w / unitW, h / unitH, 1.0f);
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.toRadians(rollDegrees));

        // The quad's centre sits at C (below the origin) when untransformed. Rotating about that
        // centre, then moving it to P, gives translation = P - R*C.
        Vector3f c = new Vector3f(0.0f, -centerShift * h, 0.0f);
        Vector3f rc = new Vector3f(c).rotate(rot);
        Vector3f translation = new Vector3f(cx - rc.x, cy - rc.y, cz - rc.z);

        quad.setInterpolationDelay(0);
        quad.setInterpolationDuration(Math.max(0, interp));
        quad.setTransformation(new Transformation(translation, rot, scale, new Quaternionf()));
    }

    /** Sets the quad's colour and alpha (0-255). A metadata update, not a respawn. */
    public static void setColor(TextDisplay quad, Color rgb, int alpha) {
        if (quad == null || !quad.isValid()) return;
        int a = Math.max(0, Math.min(255, alpha));
        quad.setBackgroundColor(Color.fromARGB(a, rgb.getRed(), rgb.getGreen(), rgb.getBlue()));
    }
}
