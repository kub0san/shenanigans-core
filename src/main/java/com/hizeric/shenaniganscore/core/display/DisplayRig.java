package com.hizeric.shenaniganscore.core.display;

import org.bukkit.entity.Entity;

import java.util.List;

/**
 * Minimal entity registry shared by the technique builders.
 *
 * <p>A rig owns every transient display entity a technique spawns. Sharing this one accessor lets
 * display lifecycle helpers add and remove entities without knowing the concrete rig type, while
 * each builder keeps its own (much wider) state fields.
 */
public interface DisplayRig {

    /** Live, mutable list of entities owned by this rig. */
    List<Entity> entities();
}
