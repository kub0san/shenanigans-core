package com.hizeric.shenaniganscore.core.model;

import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/**
 * Vanilla-only building blocks for display-entity models (no resource pack needed).
 *
 * <ul>
 *   <li><b>Flesh</b>: block displays of any block.</li>
 *   <li><b>Faces</b>: mob skulls shown through an item display with the {@code HEAD} transform.</li>
 *   <li><b>Eyes</b>: flat eyeball items that always face the viewer.</li>
 * </ul>
 *
 * <h3>Skull calibration</h3>
 * How big a skull renders, where its centre sits relative to the display's position, and which way
 * its face points all depend on the client's item-model transforms, so they are <b>calibration
 * constants</b>. Every placement of a face goes
 * through {@link #headSize()}, {@link #headCenterY()} and {@link #headYawOffset()}, so one correction
 * fixes every face.
 *
 * <p>If a pack with custom models is added later, only this class changes: each factory gets a
 * second renderer and the rest of the model code is untouched.</p>
 */
public final class Parts {

    /** The kinds of face. Their skin tones differ, which is what makes a pile of them read as varied. */
    public enum HeadType {
        PIGLIN(Material.PIGLIN_HEAD),
        ZOMBIE(Material.ZOMBIE_HEAD),
        SKELETON(Material.SKELETON_SKULL),
        WITHER(Material.WITHER_SKELETON_SKULL),
        CREEPER(Material.CREEPER_HEAD);

        public final Material material;

        HeadType(Material material) {
            this.material = material;
        }
    }

    public enum EyeType {
        SPIDER(Material.SPIDER_EYE),
        FERMENTED(Material.FERMENTED_SPIDER_EYE),
        ENDER(Material.ENDER_EYE);

        public final Material material;

        EyeType(Material material) {
            this.material = material;
        }
    }

    // Best guesses for a worn skull: a 0.5-block cube drawn at 1.1875x, sitting on top of its origin.
    private static volatile float headSize = 0.59f;
    private static volatile float headCenterY = 0.30f;
    private static volatile float headYawOffset = 0.0f;

    private Parts() {
    }

    /** World size, in blocks, of a skull at display scale 1. */
    public static float headSize() {
        return headSize;
    }

    /** Height of a skull's visual centre above the display's origin, in model units. */
    public static float headCenterY() {
        return headCenterY;
    }

    /** Extra yaw (degrees) so that "forward" for a face means the way it actually looks. */
    public static float headYawOffset() {
        return headYawOffset;
    }

    /** A block display, created tiny and invisible-ish; the rig positions and scales it. */
    public static BlockDisplay block(Location at, Material material, int brightness, float viewRange) {
        return DisplayUtils.spawnBlock(at, material, viewRange, brightness);
    }

    /** A skull as an item display. */
    public static ItemDisplay head(Location at, HeadType type, int brightness, float viewRange) {
        ItemDisplay display = DisplayUtils.spawnItem(at, new ItemStack(type.material), viewRange, brightness);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.HEAD);
        return display;
    }

    /** A flat eyeball that always turns to face whoever is looking. */
    public static ItemDisplay eye(Location at, EyeType type, int brightness, float viewRange) {
        ItemDisplay display = DisplayUtils.spawnItem(at, new ItemStack(type.material), viewRange, brightness);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.CENTER);
        return display;
    }
}
