package com.hizeric.shenaniganscore.core.display;

import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A camera-locked, per-viewer screen overlay made of layered {@link QuadUtils} quads.
 *
 * <p>Each quad is hidden from everyone ({@code setVisibleByDefault(false)}) and then shown to a
 * single player with {@link Player#showEntity}. The quads ride that player as <b>passengers</b>,
 * so the client keeps them pinned to the camera with no per-tick work and no lag. Billboards are
 * set to CENTER, so they always face the camera. If the server refuses the passenger attachment,
 * {@link #tickFollow()} re-pins them to the eye each tick instead.</p>
 *
 * <h3>Screen units</h3>
 * The server cannot read a player's FOV or aspect, so rectangles are placed in <b>nominal screen
 * units</b>: {@code x} and {@code y} run from -1 to +1 across a nominal FOV-90, 16:9 view, and
 * {@code w}/{@code h} are in the same units (2 = the full nominal width/height). Players with a
 * narrower FOV see the outer edge of the layout cropped. {@link #cover(int)} is the exception: it
 * overscans far past any realistic FOV, so a flash or a background can never leave a gap.
 *
 * <h3>Layering</h3>
 * Higher indices sit slightly closer to the camera, so layer 0 is the back-most. Keep a
 * background on index 0 and put lines and drops above it.
 *
 * <p>Main-thread only. Call {@link #close()} to remove every entity.</p>
 */
public final class ScreenOverlay {

    /** How far in front of the eye the front-most quad sits, in blocks. */
    private static final float DISTANCE = 0.62f;
    /** Gap between layers, in blocks. Enough to stop z-fighting, too small to see. */
    private static final float LAYER_GAP = 0.004f;
    /** Half the nominal visible height / width at {@link #DISTANCE} (FOV 90, 16:9). */
    private static final float HALF_H = 0.62f;
    private static final float HALF_W = 1.10f;
    /** Overscan for {@link #cover}: bigger than any realistic view at this distance. */
    private static final float COVER_W = 7.0f;
    private static final float COVER_H = 4.5f;

    /** Which way is "in front of the camera" in billboard space. Flip if the overlay renders behind you. */
    private static volatile float zSign = -1.0f;

    private final Player viewer;
    private final List<TextDisplay> quads = new ArrayList<>();
    private boolean closed;
    private boolean attached;

    public ScreenOverlay(Player viewer) {
        this.viewer = Objects.requireNonNull(viewer, "viewer");
    }

    /** Flips the camera-forward direction (see {@link #zSign}). */
    public static void setForwardSign(float sign) {
        zSign = sign >= 0.0f ? 1.0f : -1.0f;
    }

    /** Creates {@code count} stacked quads, all transparent, shown only to the viewer. */
    public void open(int count) {
        if (closed) return;
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        for (int i = 0; i < count; i++) {
            TextDisplay quad = QuadUtils.spawn(viewer.getEyeLocation(), Display.Billboard.CENTER, 8.0f, true);
            quads.add(quad);
            viewer.showEntity(plugin, quad);
        }

        attached = true;
        for (TextDisplay quad : quads) {
            try {
                if (!viewer.addPassenger(quad)) attached = false;
            } catch (Throwable t) {
                attached = false;
            }
        }
        if (!attached) {
            plugin.getLogger().warning("ScreenOverlay: passenger attach failed for "
                    + viewer.getName() + "; using the per-tick follow fallback.");
        }
    }

    /**
     * Places layer {@code index} as a rectangle. {@code cx},{@code cy} are the centre in nominal
     * screen units (-1..+1); {@code w},{@code h} the size in the same units (2 = full screen).
     */
    public void rect(int index, float cx, float cy, float w, float h, float rollDegrees, int interp) {
        TextDisplay quad = at(index);
        if (quad == null) return;
        float z = zSign * (DISTANCE - index * LAYER_GAP);
        QuadUtils.apply(quad, w * HALF_W, h * HALF_H, cx * HALF_W, cy * HALF_H, z, rollDegrees, interp);
    }

    /** Convenience: an axis-aligned rectangle with no roll. */
    public void rect(int index, float cx, float cy, float w, float h, int interp) {
        rect(index, cx, cy, w, h, 0.0f, interp);
    }

    /** Makes layer {@code index} cover the entire view, with plenty of overscan. */
    public void cover(int index) {
        TextDisplay quad = at(index);
        if (quad == null) return;
        float z = zSign * (DISTANCE - index * LAYER_GAP);
        QuadUtils.apply(quad, COVER_W, COVER_H, 0.0f, 0.0f, z, 0.0f, 0);
    }

    /** Collapses layer {@code index} to nothing. */
    public void hide(int index) {
        TextDisplay quad = at(index);
        if (quad == null) return;
        QuadUtils.setColor(quad, Color.BLACK, 0);
    }

    /** Sets a layer's colour and alpha (0-255). */
    public void color(int index, Color rgb, int alpha) {
        TextDisplay quad = at(index);
        if (quad == null) return;
        QuadUtils.setColor(quad, rgb, alpha);
    }

    public int size() {
        return quads.size();
    }

    public Player viewer() {
        return viewer;
    }

    /**
     * Fallback for servers that refuse passenger attachment: re-pins every detached quad to the
     * viewer's eye. Call each tick while the overlay is open. A no-op when attached.
     */
    public void tickFollow() {
        if (closed || attached) return;
        var eye = viewer.getEyeLocation();
        for (TextDisplay quad : quads) {
            if (quad.isValid()) {
                quad.setTeleportDuration(1);
                quad.teleport(eye);
            }
        }
    }

    /** Removes every quad. Safe to call more than once. */
    public void close() {
        if (closed) return;
        closed = true;
        for (TextDisplay quad : quads) {
            if (quad != null && quad.isValid()) {
                try {
                    viewer.removePassenger(quad);
                } catch (Throwable ignored) {
                    // the entity is removed below regardless
                }
                quad.remove();
            }
        }
        quads.clear();
    }

    private TextDisplay at(int index) {
        if (closed || index < 0 || index >= quads.size()) return null;
        TextDisplay quad = quads.get(index);
        return quad.isValid() ? quad : null;
    }
}
