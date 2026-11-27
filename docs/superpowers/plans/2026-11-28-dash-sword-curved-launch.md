# Dash Sword curved launch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Dash Sword's locked straight 2.6/tick launch with a fixed, gently curving, decelerating path whose afterimages and wind streaks follow real travel.

**Architecture:** Pure path math in `DashMovement` supplies heading and decayed distance; `DashSwordEffect.Timeline` integrates that path and feeds its points to `DashAnimation`, `MannequinEchoRenderer` and `WindTrailEffect`. No camera input, config schema, damage, cooldown or gate changes.

**Tech Stack:** Java 25, Paper 1.21.11+ API compiled for it, target Paper/Leaf 26.3, JUnit 5, Gradle 9.8.2 wrapper, PacketEvents bridge, Adventure/MiniMessage.

**Spec:** `docs/superpowers/specs/2026-11-28-dash-sword-curved-launch-design.md`

## Global Constraints

- Java 25; Paper 1.21.11+ API only; target test server is Leaf 26.3 (`C:\Tools\global-test-server-26.3`). Not Folia.
- Main thread only for Bukkit state; no `Thread.sleep()`; timelines run through the scheduler inside `DashSwordEffect.Timeline`.
- Exact tuned values: momentum `2.4`, decay `0.035` per tick, yaw drift `1.6` degrees per tick about world up, pitch frozen, 28-tick window. `DEFAULT_STEERING_ALPHA` stays `0.0`.
- `core/` stays origin-agnostic (ADR-0001); all changes stay under `origins/minecraft/weapons/dashsword/`.
- Adventure `Component` + MiniMessage only; no `org.bukkit.ChatColor`.
- Display entities use `DisplayUtils` + `TransformUtils`; no raw JOML in ability classes.
- Build: `.\\gradlew.bat build`; tests: `.\\gradlew.bat test`; single class: `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashMovementTest'`.
- Execute this track first: #66, #68 and #82 regression plans consume its interfaces.

## Review Focus

- Vertical launch direction: `headingAt` must not produce a NaN right-vector; pinned by `DashMovementTest` vertical-input case.
- Decayed distance under the 28-tick cap: `distanceAfter` must match the summed per-tick steps; pinned by `DashMovementTest` decay case and `DashAnimationTest` trail case.
- Realized travel shorter than the path formula (wall/ground early end): echo points must stay clamped; pinned by `DashAnimationTest` clamp case.
- Echo orientation during a turn: yaw/pitch must come from the beat's heading, not the cast; pinned by `DashAnimationTest` heading case.
- Wind display plane on a turned heading: new streaks use live heading without retro-rotating old displays; verified by live 26.3 pass and code review (no pure seam exists today).

---

### Task 1: Pure path math and constants

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashMovement.java:14-49`
- Test: `src/test/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashMovementTest.java`

**Interfaces:**
- Consumes: existing `speed(double momentum, double decayPerTick, int tick)` and `velocity(Vector direction, double speed)`.
- Produces: `headingAt(Vector launchForward, int tick) -> Vector` (normalized, world-up rotation, vertical fallback `(1,0,0)`); `distanceAfter(int ticks, double momentum, double decayPerTick) -> double` (sum of `speed(...)` for ticks 1..ticks); constants `DEFAULT_MOMENTUM = 2.4`, `DEFAULT_DECAY_PER_TICK = 0.035`, `YAW_DRIFT_DEGREES_PER_TICK = 1.6`.

- [ ] **Step 1: Write failing tests**

```java
@Test void headingAtTurnsRightWithoutChangingPitch() {
    Vector h = DashMovement.headingAt(new Vector(0, 0, 1), 28);
    assertEquals(44.8, Math.toDegrees(Math.atan2(h.getX(), h.getZ())), 0.1);
    assertEquals(0.0, h.getY(), 1e-9);
}
@Test void headingAtHandlesVerticalInput() {
    assertEquals(new Vector(1, 0, 0), DashMovement.headingAt(new Vector(0, 1, 0), 0));
}
@Test void distanceAfterUsesDecayedSteps() {
    assertEquals(distanceAfter(28, 2.4, 0.035), distanceAfter(27, 2.4, 0.035)
            + DashMovement.speed(2.4, 0.035, 28), 1e-9);
}
```

- [ ] **Step 2: Run the test class to verify it fails**

Run: `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashMovementTest'` — Expected: FAIL on missing `headingAt` / `distanceAfter`.

- [ ] **Step 3: Implement the two helpers and update constants**

Add the exact signatures above; rotate about world up by `YAW_DRIFT_DEGREES_PER_TICK * tick`; keep `steer()` and `DEFAULT_STEERING_ALPHA = 0.0` unchanged; document the three values in Javadoc.

- [ ] **Step 4: Run the test class to verify it passes**

Run: same command — Expected: PASS.

- [ ] **Step 5: Commit**

`git add src/main/java/.../DashMovement.java src/test/java/.../DashMovementTest.java` → `git commit -m "feat: add curved decayed dash path math"`

### Task 2: Timeline integrates the path and visuals follow it

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashSwordEffect.java:80-186`
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashAnimation.java:80-82`
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/WindTrailEffect.java:158-208`
- Test: `src/test/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashAnimationTest.java`

**Interfaces:**
- Consumes: `headingAt`, `distanceAfter`, constants from Task 1.
- Produces: `DashAnimation.trailDistance(double pathDistance, double actualTravel) -> double` (clamped); echo points from accumulated path; `WindTrailEffect.trailStreak(Location point, Vector heading, int tick)` and `tick(...)` using live heading for new streaks and drift.

- [ ] **Step 1: Write failing tests**

```java
@Test void trailDistanceUsesAccumulatedDecayedDistance() {
    double d = DashMovement.distanceAfter(16, 2.4, 0.035);
    assertEquals(d, DashAnimation.trailDistance(d, 999.0), 1e-9);
}
@Test void trailDistanceStillClampsToRealizedTravel() {
    assertEquals(1.5, DashAnimation.trailDistance(9.0, 1.5), 1e-9);
}
```

- [ ] **Step 2: Run the test class to verify it fails**

Run: `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashAnimationTest'` — Expected: FAIL on the new signature.

- [ ] **Step 3: Implement the timeline change**

In `Timeline.run()`, compute `headingAt(launchForward, tick)` and `DashMovement.velocity(heading, speed(2.4, 0.035, tick))`; accumulate `distanceAfter(tick, 2.4, 0.035)` as path distance. Feed that point (clamped by `current.distance(origin)`) to echo spawn, ribbon, launch particles and wind streaks; feed beat heading to echo yaw/pitch and to new wind streaks/drift. Leave `launchBurst()` cast-anchored and all early-end guards/cue ticks unchanged.

- [ ] **Step 4: Run the full dashsword test package**

Run: `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.*'` — Expected: PASS.

- [ ] **Step 5: Commit**

`git add src/main/java/.../dashsword src/test/java/.../dashsword` → `git commit -m "feat: curve and decelerate dash sword launch"`

### Task 3: Live 26.3 verification

**Files:** none (verification only)

- [ ] **Step 1: Deploy and verify readiness**

From `C:\Tools\global-test-server-26.3`: `.\\TestServer.ps1 deploy shenanigans-core`, then Craftwire `list_instances` → `server_info` plus a real assertion. `restart` alone is not a pass.

- [ ] **Step 2: Playtest**

Confirm a horizontal cast bends visibly to the right, slows, and lands around 44 blocks instead of ~72.8; confirm a downward ground-start dash still emits no trail (issue #66), held item stays visible (issue #68) and the slash sound still fires with the visible slash (issue #82).

- [ ] **Step 3: Record evidence**

Write observed numbers and any tuning change into `findings.md` for this plan; if constants change, update the design doc and Task 1 tests in the same commit.
