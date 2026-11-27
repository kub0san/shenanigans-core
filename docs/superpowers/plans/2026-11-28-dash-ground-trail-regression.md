# Dash Sword ground-trail regression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove that issue #66's blocked downward ground-start dash emits no trail after the curved/decelerating launch rewrite.

**Architecture:** Pure decision helpers in `DashAnimation` receive the new accumulated path distance and current heading; `DashSwordEffect.Timeline` keeps all guards before any spawn; a spawn-count seam makes the gating order testable without a live server.

**Tech Stack:** Java 25, Paper 1.21.11+ API, JUnit 5, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-11-28-dash-ground-trail-regression-design.md`

## Global Constraints

- Java 25; Paper 1.21.11+ API; target Leaf 26.3. Main-thread only; no `Thread.sleep()`.
- Exact values: `STALL_DELTA = 0.05`, `STALL_TICKS = 2`, origin is cast location + `0.9` Y, momentum `2.4`, decay `0.035`.
- Guards must run before every spawn; no new bypass of `finish()`/`clear()`.
- Run this track after `2026-11-28-dash-sword-curved-launch.md`; it consumes `headingAt`, `distanceAfter` and `trailDistance`.

## Review Focus

- Downward ground start: zero effects; pinned by spawn-count test.
- Horizontal ground start: effects still spawn; pinned by the same test's control case.
- Decayed stall: slow-but-moving dash must not end; pinned by stall-tolerance test.
- One-tick airborne hop: `airborneOnce` must not mask a still-blocked start; pinned by guard truth table.
- Live 26.3 ground/floor behavior: pinned by the final verification step.

---

### Task 1: Guard and distance helpers under curve + decay

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashAnimation.java:84-87`
- Test: `src/test/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashAnimationTest.java`

**Interfaces:**
- Consumes: `DashMovement.distanceAfter(int,double,double)`, `headingAt(Vector,int)`.
- Produces: `blockedGroundStart(double headingY, boolean onGround, boolean airborneOnce) -> boolean`; `trailDistance(double pathDistance, double actualTravel) -> double`.

- [ ] **Step 1: Write failing tests**

```java
@Test void blockedGroundStartUsesLiveHeading() {
    assertTrue(DashAnimation.blockedGroundStart(-0.3, true, false));
    assertFalse(DashAnimation.blockedGroundStart(-0.3, true, true));
    assertFalse(DashAnimation.blockedGroundStart(0.0, true, false));
}
@Test void stalledOnlyWhenDecayedStepIsMissed() {
    assertFalse(DashAnimation.stalled(0.04, DashMovement.speed(2.4, 0.035, 28)));
}
```

- [ ] **Step 2: Run test class to verify failure**

Run: `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashAnimationTest'` — Expected: FAIL on new signature/helper.

- [ ] **Step 3: Implement helpers**

Change `blockedGroundStart` to take the current heading's Y; add `stalled(double moved, double expectedStep)` comparing against the decayed step instead of a flat delta; keep `trailDistance` clamped to realized travel.

- [ ] **Step 4: Run test class to verify pass**

Same command — Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "test: pin dash ground guard under decay"`

### Task 2: Spawn-count seam and gating order

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashSwordEffect.java:97-186`
- Create: `src/test/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashSpawnGateTest.java`

**Interfaces:**
- Consumes: Task 1 helpers; existing `Timeline` end fan-in.
- Produces: a pure `shouldSpawnAt(double headingY, boolean onGround, boolean airborneOnce, double moved, double expectedStep) -> boolean` seam used by `Timeline.run()` and the test.

- [ ] **Step 1: Write failing test**

```java
@Test void blockedDownwardGroundDashSpawnsNothing() {
    assertFalse(DashSwordEffect.shouldSpawnAt(-0.5, true, false, 2.0, 1.0));
    assertTrue(DashSwordEffect.shouldSpawnAt(0.0, true, false, 2.0, 1.0));
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSpawnGateTest'` — Expected: FAIL on missing seam.

- [ ] **Step 3: Add seam and call it before every spawn**

Implement `shouldSpawnAt` from the Task 1 helpers; call it at the top of the spawn block in `Timeline.run()` so guards precede echoes, ribbon and wind.

- [ ] **Step 4: Run full dashsword package**

Run: `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.*'` — Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "fix: gate dash spawns before visuals"`

### Task 3: Live verification

**Files:** none.

- [ ] **Step 1:** From `C:\Tools\global-test-server-26.3`, run `.\\TestServer.ps1 deploy shenanigans-core`; confirm with Craftwire `list_instances` → `server_info` plus a real assertion.
- [ ] **Step 2:** Cast downward into the floor and verify no trail/echo/ribbon; cast horizontally and verify effects remain; force a stall and verify it only ends on a real wall.
- [ ] **Step 3:** Record observed behavior in this plan's `findings.md`.
