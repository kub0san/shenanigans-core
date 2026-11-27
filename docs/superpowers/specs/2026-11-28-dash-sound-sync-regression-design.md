# Dash Sword #82 sound-sync regression — design

## Intent and why

Preserve the closed fix for #82 (slash sound lagged the visible slash) under the curved, decelerating launch. The shipped cue ticks are SWING 0, RIFT_CLOSE 1, RIFT_BREATH 10.

## Goals

- Slash audio still fires on the visible slash beats (0–2), not half a second later.
- Cue ticks remain `[0,1,10]` and are pinned by tests, including under decay and early exit.

## Non-goals

- Retiming the whoosh to a path milestone, adding a new sound, or emitting everything at cast time.

## Constraints

`DashAnimation.soundsForTick()` blocks clamped-tail replay; cues emit only on reached frames in `DashSwordEffect`; positional audio uses the live player location.

## Approaches considered

1. **Keep cue ticks, fix position math (chosen).** The lag fix was about timing; deceleration only changes where the player is, so keep the beats and make the trail math truthful.
2. Retime RIFT_BREATH to a distance milestone: rejected, changes a shipped fix without feedback.
3. Cast-time emit for all cues: rejected, would make one loud blast instead of a 3-phase choreography.

## Decided design

- Keep `DashAnimation.java:56-59` exactly as shipped (`0`, `1`, `10`) and `DashAnimationTest.java:78-90` unchanged.
- Replace the linear `speed * tick` in `trailDistance()` with the accumulated curve distance from #67, clamped to realized travel; this is what feeds ribbon, echoes and wind streaks, not the sound ticks.
- Keep cue emission after the early-end guards: a dash ending before tick 10 simply drops RIFT_BREATH, which is the intended short-dash behavior.
- Add decay/clamp cases to `DashAnimationTest` and confirm audio timing in-game with qcart.

## Left to the builder

- In-game confirmation that tick 1 still lines up with the visible slash after the speed change.
