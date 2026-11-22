package com.hizeric.shenaniganscore.core.display;

import org.bukkit.Location;

import java.util.Map;

/**
 * A display rig that owns client-side fake lights.
 *
 * <p>Extends {@link DisplayRig} so the shared lighting helpers can reconcile light state without
 * knowing which technique produced it. The map remains owned by the rig and is mutated in place by
 * {@link TechniqueLighting#setLights(Map, org.bukkit.World, Map)}.</p>
 */
public interface LightingRig extends DisplayRig {

    /** Mutable map of fake-light positions to their current level. */
    Map<Location, Integer> lights();
}
