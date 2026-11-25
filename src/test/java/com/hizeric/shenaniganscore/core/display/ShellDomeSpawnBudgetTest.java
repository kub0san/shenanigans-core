package com.hizeric.shenaniganscore.core.display;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #76: the shell dome used to spawn its entire plate plan (up to 280
 * BlockDisplays) inside one tick once the barrier completed. Amortising the
 * spawn keeps the same final geometry but bounds the per-tick work.
 *
 * <p>The first three tests pin the pure budget math. The last one drives the
 * real {@link ShellDome#show()} under MockBukkit: every planned plate must be
 * emitted exactly once, in plan order, in successive ≤24-plate batches, and
 * finish at the same full-size geometry as the old one-tick spawn.</p>
 */
class ShellDomeSpawnBudgetTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFullDomeIsSplitAcrossTicks() {
        assertEquals(24, ShellDomePlan.batch(280, 0));
        assertEquals(24, ShellDomePlan.batch(280, 24));
        assertEquals(16, ShellDomePlan.batch(280, 264));
        assertEquals(0, ShellDomePlan.batch(280, 280));
    }

    @Test
    void aSmallDomeFitsInOneTick() {
        assertEquals(10, ShellDomePlan.batch(10, 0));
        assertEquals(0, ShellDomePlan.batch(10, 10));
    }

    @Test
    void invalidCursorNeverSpawnsMoreThanThePlan() {
        assertEquals(0, ShellDomePlan.batch(280, 300));
        assertEquals(0, ShellDomePlan.batch(280, -1));
        assertEquals(0, ShellDomePlan.batch(0, 0));
    }

    @Test
    void showEmitsEveryPlannedPlateOnceInOrderAtItsFullGeometry() throws Exception {
        ServerMock server = MockBukkit.mock();
        WorldMock world = server.addSimpleWorld("shell-dome");
        ShellDome dome = new ShellDome(new Location(world, 0, 64, 0), 32.0, Material.BLACK_CONCRETE);

        Field planField = ShellDome.class.getDeclaredField("plan");
        planField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> plan = (List<Object>) planField.get(dome);
        Field platesField = ShellDome.class.getDeclaredField("plates");
        platesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<BlockDisplay> plates = (List<BlockDisplay>) platesField.get(dome);

        int total = plan.size();
        assertTrue(total > ShellDomePlan.PLATES_PER_TICK * 2,
                "a full dome must need more than two batches to be meaningful (was " + total + ")");

        int shows = 0;
        while (!dome.spawned()) {
            int before = plates.size();
            dome.show();
            int added = plates.size() - before;
            assertEquals(Math.min(ShellDomePlan.PLATES_PER_TICK, total - before), added,
                    "each show() must materialise exactly one budgeted batch");
            assertTrue(added <= ShellDomePlan.PLATES_PER_TICK,
                    "a single tick must never spawn more than the plate budget");
            shows++;
            assertTrue(shows <= total, "the loop must terminate");
        }
        assertEquals((total + ShellDomePlan.PLATES_PER_TICK - 1) / ShellDomePlan.PLATES_PER_TICK, shows,
                "successive shows must cover the whole plan in 24-plate batches");
        assertEquals(total, plates.size());

        // An extra show after completion must not emit or re-scale anything.
        dome.show();
        assertEquals(total, plates.size());

        // Final geometry: every plate sits at its planned location and its raw
        // transformation matrix matches a full-size TransformUtils application
        // of the planned scale/rotation — never the flat-shrunk spawn state.
        // MockBukkit's getTransformation() decomposes a non-uniformly scaled
        // matrix unreliably, so compare the stored matrix directly instead.
        Field matrixField = Class.forName("org.mockbukkit.mockbukkit.entity.DisplayMock")
                .getDeclaredField("transformationMatrix");
        matrixField.setAccessible(true);
        BlockDisplay reference = world.spawn(new Location(world, 0, 0, 0), BlockDisplay.class);
        Method at = plan.get(0).getClass().getDeclaredMethod("at");
        Method scale = plan.get(0).getClass().getDeclaredMethod("scale");
        Method rotation = plan.get(0).getClass().getDeclaredMethod("rotation");
        at.setAccessible(true);
        scale.setAccessible(true);
        rotation.setAccessible(true);
        for (int i = 0; i < total; i++) {
            Object spec = plan.get(i);
            BlockDisplay plate = plates.get(i);
            assertEquals(at.invoke(spec), plate.getLocation(), "plate " + i + " must keep its planned location");

            Vector3f expectedScale = (Vector3f) scale.invoke(spec);
            Quaternionf expectedRotation = (Quaternionf) rotation.invoke(spec);
            TransformUtils.applyTransform(reference, expectedScale, expectedRotation,
                    new Vector3f(0.5f, 0.5f, 0.5f), 10);
            Matrix4f expected = (Matrix4f) matrixField.get(reference);
            Matrix4f actual = (Matrix4f) matrixField.get(plate);
            assertEquals(expected, actual, "plate " + i + " must finish at the planned full-size geometry");
            assertEquals(10, plate.getInterpolationDuration(),
                    "the bloom-in must use the final 10-tick interpolation, not the 0-tick spawn value");
        }
    }
}
