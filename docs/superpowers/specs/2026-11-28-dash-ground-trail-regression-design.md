# Dash Sword #66 regression guard — design

## Intent and why

Preserve the closed fix for #66 (downward trail through the floor) while #67 changes the launch to a curved, decelerating path. PR #90 was source/unit-verified only; this plan adds the missing coverage.

## Goals

- A blocked downward ground-start dash still emits zero trail/echo/ribbon/wind-streak spawns.
- Visual positions stay tied to realized travel under decay and a curved chord.
- Stall detection does not fire merely because speed decays.

## Non-goals

- Changing the launch feel itself (that is #67), turning off downward dashes globally, or adding per-tick raytraces.

## Constraints

`DashSwordEffect.Timeline` guards run before any spawn; `DashAnimation` stays pure; 28-tick cap; existing tests must be updated, not weakened.

## Approaches considered

1. **Guard + realized-travel clamp with live heading (chosen).** Keep the pre-spawn `blockedGroundStart` check, feed the new accumulated curve distance into the clamp, and compare stall against expected decayed motion.
2. Per-tick raytrace from the player to the ground: rejected as heavy and it would change ground-start semantics.
3. Ban downward dashes: rejected as a behavior change beyond the issue.

## Decided design

- Keep `blockedGroundStart(initialForwardY, onGround, airborneOnce)` as the first gate before any spawn, as today.
- Replace `DashAnimation.trailDistance(speed * tick, actualTravel)` with a call taking the accumulated **curve** distance from #67, still clamped by `current.distance(origin)`. The clamp floor remains the `+0.9 Y` origin.
- Use the current curve heading (not only initial pitch) for echo orientation and wind facing, so a turned path cannot draw a phantom lateral/upward trail.
- Stall check compares realized movement against the expected decayed step for that tick instead of a flat `STALL_DELTA = 0.05`, so intended slow-down does not end the dash.
- Add a spawn-count seam around `Timeline.run()` (or a pure decision helper) so tests assert zero effects for blocked ground starts and non-zero for horizontal ground dashes; add truth-table tests for the guard and stall-with-decay.

## Left to the builder

- Live 26.3 verification of downward ground starts, first-person/F5 and horizontal ground dashes.
- Keep the regression assertions when #67 updates `DashAnimationTest`.
