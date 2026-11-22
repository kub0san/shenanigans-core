package com.hizeric.shenaniganscore.battleground.deploy;

import java.util.List;

/**
 * The players a new drop should be pulled toward, with a weight (how much we want to converge on
 * them) and the players to stay clear of (typically the last killer).
 */
public record DropCohort(List<Anchor> attractors, List<DropZone.Point> avoiders) {

    /** An anchor: converge near (x, z) with the given gravity weight. */
    public record Anchor(int x, int z, double weight) { }

    public DropCohort {
        attractors = attractors == null ? List.of() : List.copyOf(attractors);
        avoiders = avoiders == null ? List.of() : List.copyOf(avoiders);
    }

    public static DropCohort empty() {
        return new DropCohort(List.of(), List.of());
    }
}
