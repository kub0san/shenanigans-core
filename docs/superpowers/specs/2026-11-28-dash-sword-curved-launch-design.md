# Dash Sword curved, decelerating launch — design

## Intent and why

GitHub #67 (OPEN, qcart): the dash is "slippery" and "completely straight". The user chose a **fixed curve that slows down** — one clean dash, no live look-steering — and asked that the rest of the related dash issues be handled separately. This design supersedes part A (straight trajectory) and parts B/D (calculated straight-line visuals) of `2026-11-24-dash-sword-visual-overhaul-design.md`.

## Goals

- Launch bends gently along a predictable arc and decelerates instead of holding 2.6 blocks/tick.
- The mechanic stays simple: no new input handling, no aim-lock changes, no config schema.
- Echoes and wind streaks follow the **actual travelled path**, not a linear formula.
- Closed fixes #66, #68 and #82 stay intact (their own plans verify them).

## Non-goals / anti-goals

- Live steering toward current camera look (`DEFAULT_STEERING_ALPHA` stays 0).
- Damage, cooldown, refund, gate or shop changes.
- New config keys; constants are documented in code and this doc instead.
- Making the dash a controllable flight mini-game.

## Constraints

Paper 26.3 / Java 25, main-threadonly; `DashMovement` pure helpers; `DashAnimation` pure timing data; no `Thread.sleep()`; 28-tick window; `TransformUtils` for displays; no cross-origin logic in `core/` (ADR-0001).

## Approaches considered

1. **Constant yaw drift + exponential decay (chosen).** Momentum 2.4, decay 0.035/tick, yaw drift 1.6°/tick about world-up, pitch frozen. ~44.8° total turn, speed 2.4→0.89, arc ~44 blocks. One formula, no curve inversion; bend starts immediately.
2. **Ease-in/ease-out turn + decay.** 40° total, decay 0.02, peak 2.24°/tick mid-dash, ~55 blocks. Gentler onset/settle, more trig and more tuning surface.
3. **Fixed lateral parabola.** Positions trivial, but velocity must be reconstructed from finite differences; higher drift risk between motion and visuals.

Choice: (1). It is the smallest complete change that delivers "curved and slower" and leaves room for playtest tuning.

## Decided design

- `DashMovement.java`: keep `DEFAULT_STEERING_ALPHA = 0`; set `DEFAULT_MOMENTUM = 2.4`, `DEFAULT_DECAY_PER_TICK = 0.035`; add `yawDriftDegreesPerTick = 1.6` and a pure `headingAt(forward, tick)` helper that rotates about world up by `1.6 * tick`, reusing the existing right-vector fallback when the look direction is vertical. Document exact values here and in Javadoc.
- `DashSwordEffect.Timeline`: capture launch heading and origin as today; each tick compute `headingAt(launchForward, tick)` and `speed(2.4, 0.035, tick)`, apply `DashMovement.velocity(heading, speed)`; accumulate travelled distance along the curve and keep `current.distance(origin)` as the safety clamp. No camera input is read mid-dash.
- `DashAnimation.trailDistance()` changes from `speed * tick` to the accumulated curve distance (still clamped to realized travel). `DashAnimationTest` pins the new decayed values.
- Echo placement uses the accumulated path point, with yaw/pitch derived from `headingAt(...)` at that beat. `MannequinEchoRenderer` stays a pure sink.
- `WindTrailEffect.trailStreak()` receives the same path point; `tick()` receives the path heading each tick so drift and facing follow the turn. `launchBurst()` remains cast-anchored.
- Builder check ruling: the launch-stage `CLEAVE_BURST` and `RIFT_RIBBON` particles in `DashSwordEffect.spawnLaunch()` :156-164 also use the accumulated path point, so no straight-line artifact survives; only the cast-anchored launch burst ring stays put. Cost: two more position calls, no new state.
- Builder check ruling: wind reorientation applies to **new** streaks and each live display's drift direction/facing; already-spawned radial planes are not retroactively rotated. Cost: a slight fan of old streaks during a turn, accepted for a simpler diff.
- All existing early-end guards, `finish()`/`clear()` fan-in, cue ticks and visibility lifecycle stay unchanged.

## Left to the builder

- Live 26.3 playtest: confirm "gentle, not slippery" feel, then tune the three constants together (document any change in this file).
- Verify wind display axes under a turned heading.
- Add pure tests for `headingAt`, decayed distance and echo-point placement; keep the #66/#68/#82 regression suites green.
