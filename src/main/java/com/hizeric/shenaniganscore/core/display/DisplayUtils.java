package com.hizeric.shenaniganscore.core.display;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Validated factories and common configuration for visual display entities.
 *
 * All methods must be called on the Bukkit server thread. Keep animation
 * timelines, entity groups, and removal/cleanup logic in separate utilities.
 */
public final class DisplayUtils {

    private static final int MAX_BRIGHTNESS = 15;
    private static final int MAX_TELEPORT_DURATION = 59;

    private DisplayUtils() {}

    // ---------------------------------------------------------------------
    // Factories
    // ---------------------------------------------------------------------

    public static BlockDisplay spawnBlock(
            Location location,
            Material material,
            Options options
    ) {
        Objects.requireNonNull(material, "material");
        if (!material.isBlock() || material.isAir()) {
            throw new IllegalArgumentException(
                    "A block display requires a non-air block material"
            );
        }

        return spawnBlock(location, material.createBlockData(), options);
    }

    public static BlockDisplay spawnBlock(
            Location location,
            Material material,
            float viewRange,
            int brightness
    ) {
        return spawnBlock(
                location,
                material,
                Options.builder()
                        .viewRange(viewRange)
                        .brightness(brightness)
                        .build()
        );
    }

    public static BlockDisplay spawnBlock(
            Location location,
            BlockData blockData,
            Options options
    ) {
        requireMainThread();
        Objects.requireNonNull(blockData, "blockData");

        Location spawnAt = validateLocation(location);
        Options settings = optionsOrDefault(options);

        return spawnAt.getWorld().spawn(spawnAt, BlockDisplay.class, display -> {
            configure(display, settings);
            display.setBlock(blockData.clone());
        });
    }

    public static ItemDisplay spawnItem(
            Location location,
            ItemStack item,
            Options options
    ) {
        requireMainThread();
        Objects.requireNonNull(item, "item");

        if (item.getType().isAir()) {
            throw new IllegalArgumentException(
                    "An item display cannot display an empty item stack"
            );
        }

        Location spawnAt = validateLocation(location);
        Options settings = optionsOrDefault(options);

        return spawnAt.getWorld().spawn(spawnAt, ItemDisplay.class, display -> {
            configure(display, settings);
            display.setItemStack(item.clone());
        });
    }

    public static ItemDisplay spawnItem(
            Location location,
            ItemStack item,
            float viewRange,
            int brightness
    ) {
        return spawnItem(
                location,
                item,
                Options.builder()
                        .viewRange(viewRange)
                        .brightness(brightness)
                        .build()
        );
    }

    public static TextDisplay spawnText(
            Location location,
            Component text,
            Options options
    ) {
        requireMainThread();
        Objects.requireNonNull(text, "text");

        Location spawnAt = validateLocation(location);
        Options settings = optionsOrDefault(options);

        return spawnAt.getWorld().spawn(spawnAt, TextDisplay.class, display -> {
            configure(display, settings);
            display.text(text);
            display.setBillboard(
                    settings.billboard != null
                            ? settings.billboard
                            : Display.Billboard.CENTER
            );
        });
    }

    public static TextDisplay spawnText(
            Location location,
            Component text,
            float viewRange
    ) {
        return spawnText(
                location,
                text,
                Options.builder()
                        .viewRange(viewRange)
                        .billboard(Display.Billboard.CENTER)
                        .build()
        );
    }

    // ---------------------------------------------------------------------
    // Reusable display configuration
    // ---------------------------------------------------------------------

    private static void configure(Display display, Options options) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setVisibleByDefault(options.visibleByDefault);

        display.setViewRange(options.viewRange);

        if (options.brightness >= 0) {
            display.setBrightness(
                    new Display.Brightness(options.brightness, options.brightness)
            );
        }

        if (options.billboard != null) {
            display.setBillboard(options.billboard);
        }

        if (options.interpolationDelay >= 0) {
            display.setInterpolationDelay(options.interpolationDelay);
        }

        if (options.interpolationDuration >= 0) {
            display.setInterpolationDuration(options.interpolationDuration);
        }

        display.setTeleportDuration(options.teleportDuration);

        if (options.shadowRadius >= 0.0f) {
            display.setShadowRadius(options.shadowRadius);
        }

        if (options.shadowStrength >= 0.0f) {
            display.setShadowStrength(options.shadowStrength);
        }

        if (options.displayWidth >= 0.0f) {
            display.setDisplayWidth(options.displayWidth);
        }

        if (options.displayHeight >= 0.0f) {
            display.setDisplayHeight(options.displayHeight);
        }

        if (options.transformation != null) {
            display.setTransformation(copyTransformation(options.transformation));
        }

        if (options.glowColor != null) {
            display.setGlowColorOverride(options.glowColor);
        }

        if (options.glowing != null) {
            display.setGlowing(options.glowing);
        }
    }

    // ---------------------------------------------------------------------
    // Transform helpers
    // ---------------------------------------------------------------------

    /** Creates a scale-only display transformation. */
    public static Transformation scale(float x, float y, float z) {
        requireFinite(x, "scale x");
        requireFinite(y, "scale y");
        requireFinite(z, "scale z");

        return new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(x, y, z),
                new Quaternionf()
        );
    }

    /** Creates a translation-only display transformation. */
    public static Transformation translation(float x, float y, float z) {
        requireFinite(x, "translation x");
        requireFinite(y, "translation y");
        requireFinite(z, "translation z");

        return new Transformation(
                new Vector3f(x, y, z),
                new Quaternionf(),
                new Vector3f(1.0f),
                new Quaternionf()
        );
    }

    /**
     * Copies a transformation so callers cannot mutate the options' JOML
     * objects after passing them to a factory.
     */
    private static Transformation copyTransformation(Transformation source) {
        return new Transformation(
                new Vector3f(source.getTranslation()),
                new Quaternionf(source.getLeftRotation()),
                new Vector3f(source.getScale()),
                new Quaternionf(source.getRightRotation())
        );
    }

    // ---------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------

    private static Location validateLocation(Location location) {
        Objects.requireNonNull(location, "location");

        if (location.getWorld() == null) {
            throw new IllegalArgumentException(
                    "Display location must belong to a world"
            );
        }

        if (!Double.isFinite(location.getX())
                || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ())) {
            throw new IllegalArgumentException(
                    "Display location coordinates must be finite"
            );
        }

        return location.clone();
    }

    private static Options optionsOrDefault(Options options) {
        return options == null ? Options.defaults() : options;
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "DisplayUtils must only be called from the Bukkit main thread"
            );
        }
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    // ---------------------------------------------------------------------
    // Options
    // ---------------------------------------------------------------------

    public static final class Options {
        private final float viewRange;
        private final int brightness;
        private final Display.Billboard billboard;
        private final int interpolationDelay;
        private final int interpolationDuration;
        private final int teleportDuration;
        private final float shadowRadius;
        private final float shadowStrength;
        private final float displayWidth;
        private final float displayHeight;
        private final boolean visibleByDefault;
        private final Boolean glowing;
        private final Color glowColor;
        private final Transformation transformation;

        private Options(Builder builder) {
            this.viewRange = builder.viewRange;
            this.brightness = builder.brightness;
            this.billboard = builder.billboard;
            this.interpolationDelay = builder.interpolationDelay;
            this.interpolationDuration = builder.interpolationDuration;
            this.teleportDuration = builder.teleportDuration;
            this.shadowRadius = builder.shadowRadius;
            this.shadowStrength = builder.shadowStrength;
            this.displayWidth = builder.displayWidth;
            this.displayHeight = builder.displayHeight;
            this.visibleByDefault = builder.visibleByDefault;
            this.glowing = builder.glowing;
            this.glowColor = builder.glowColor;
            this.transformation = builder.transformation == null
                    ? null
                    : copyTransformation(builder.transformation);
        }

        public static Options defaults() {
            return builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private float viewRange = 1.0f;

            // -1 means leave the server default / do not apply an override.
            private int brightness = -1;
            private Display.Billboard billboard;
            private int interpolationDelay = 0;
            private int interpolationDuration = 0;
            private int teleportDuration = 0;
            private float shadowRadius = 0.0f;
            private float shadowStrength = 1.0f;
            private float displayWidth = -1.0f;
            private float displayHeight = -1.0f;
            private boolean visibleByDefault = true;
            private Boolean glowing;
            private Color glowColor;
            private Transformation transformation;

            private Builder() {}

            public Builder viewRange(float viewRange) {
                if (!Float.isFinite(viewRange) || viewRange <= 0.0f) {
                    throw new IllegalArgumentException(
                            "viewRange must be finite and greater than zero"
                    );
                }
                this.viewRange = viewRange;
                return this;
            }

            /** Brightness range is 0–15. */
            public Builder brightness(int brightness) {
                if (brightness < 0 || brightness > MAX_BRIGHTNESS) {
                    throw new IllegalArgumentException(
                            "brightness must be between 0 and 15"
                    );
                }
                this.brightness = brightness;
                return this;
            }

            public Builder billboard(Display.Billboard billboard) {
                this.billboard = Objects.requireNonNull(billboard, "billboard");
                return this;
            }

            public Builder interpolation(int delayTicks, int durationTicks) {
                if (delayTicks < 0 || durationTicks < 0) {
                    throw new IllegalArgumentException(
                            "interpolation values cannot be negative"
                    );
                }
                this.interpolationDelay = delayTicks;
                this.interpolationDuration = durationTicks;
                return this;
            }

            public Builder teleportDuration(int ticks) {
                if (ticks < 0 || ticks > MAX_TELEPORT_DURATION) {
                    throw new IllegalArgumentException(
                            "teleport duration must be between 0 and 59 ticks"
                    );
                }
                this.teleportDuration = ticks;
                return this;
            }

            public Builder shadow(float radius, float strength) {
                requireFinite(radius, "shadow radius");
                requireFinite(strength, "shadow strength");
                if (radius < 0.0f || strength < 0.0f) {
                    throw new IllegalArgumentException(
                            "shadow values cannot be negative"
                    );
                }
                this.shadowRadius = radius;
                this.shadowStrength = strength;
                return this;
            }

            /**
             * Sets the display's culling bounds. Use positive values for
             * oversized models that would otherwise disappear near the edge
             * of the entity's default bounds.
             */
            public Builder cullingBounds(float width, float height) {
                requireFinite(width, "display width");
                requireFinite(height, "display height");
                if (width < 0.0f || height < 0.0f) {
                    throw new IllegalArgumentException(
                            "culling bounds cannot be negative"
                    );
                }
                this.displayWidth = width;
                this.displayHeight = height;
                return this;
            }

            public Builder visibleByDefault(boolean visible) {
                this.visibleByDefault = visible;
                return this;
            }

            public Builder glowing(boolean glowing) {
                this.glowing = glowing;
                return this;
            }

            public Builder glowColor(Color color) {
                this.glowColor = Objects.requireNonNull(color, "color");
                return this;
            }

            public Builder transformation(Transformation transformation) {
                this.transformation =
                        Objects.requireNonNull(transformation, "transformation");
                return this;
            }

            public Options build() {
                return new Options(this);
            }
        }
    }
}
