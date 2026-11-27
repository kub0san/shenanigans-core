# Dash Sword sound-sync regression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the #82 slash-sound fix intact after the dash becomes curved and decelerating: cues stay at ticks 0, 1 and 10.

**Architecture:** Sound beats remain pure data in `DashAnimation`; the new decayed path distance only fixes visual positions, not cue timing. Tests pin the exact tick list and decay/clamp cases.

**Tech Stack:** Java 25, Paper 1.21.11+ API, JUnit 5, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-11-28-dash-sound-sync-regression-design.md`

## Global Constraints

- Exact cue ticks: SWING `0`, RIFT_CLOSE `1`, RIFT_BREATH `10`; tail replay stays blocked (`DashAnimation.java:89-93`).
- Cue emission stays after early-end guards in `DashSwordEffect.java:145`; positional audio at the live player location.
- Momentum `2.4`, decay `0.035`, 28-tick window. No retiming, no new sounds.
- Run after `2026-11-28-dash-sword-curved-launch.md`; consumes `distanceAfter` and `trailDistance`.

## Review Focus

- Short dash ending before tick 10: only RIFT_BREATH is dropped; pinned by early-exit test.
- First-tick abort: no SWING replay after clamp; pinned by `soundsForTick` test.
- Decay does not shift cue ticks: pinned by `[0,1,10]` assertion under a decayed distance case.
- Audio position on a curved path: positional sound still follows the player, not the slash origin; verified live.

---

### Task 1: Pin cue ticks under the new distance math

**Files:**
- Modify: `src/test/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashAnimationTest.java`
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashAnimation.java:56-59,80-82,89-93`

**Interfaces:**
- Consumes: `DashMovement.distanceAfter(int,double,double)`.
- Produces: unchanged `soundsForTick(int tick) -> List<SoundCue>` with beats `[0,1,10]`.

- [ ] **Step 1: Write failing tests**

```java
@Test void soundCuesStayAtZeroOneTenUnderDecay() {
    assertEquals(List.of(0, 1, 10), DashAnimationTest.soundTicks());
    assertFalse(DashAnimation.soundsForTick(1).isEmpty());
}
@Test void clampedTailDoesNotReplaySounds() {
    assertTrue(DashAnimation.soundsForTick(21).isEmpty());
}
```

- [ ] **Step 2: Run test class to verify failure**

Run: `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashAnimationTest'` — Expected: FAIL until the decayed distance case is added.

- [ ] **Step 3: Add decayed/clamp cases without changing cue ticks**

Keep `DashAnimation.java:56-59` verbatim; add a test asserting `trailDistance(distanceAfter(16, 2.4, 0.035), 999.0)` equals that accumulated distance and that `soundsForTick` is unaffected by it.

- [ ] **Step 4: Run test class to verify pass**

Same command — Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "test: pin dash sound beats under decay"`

### Task 2: Early-exit cue behavior and live confirmation

**Files:** none (verification only)

- [ ] **Step 1:** In `DashSwordEffect.Timeline`, confirm cue emission remains after the early-end guards and that no new guard was added before tick 1.
- [ ] **Step 2:** From `C:\Tools\global-test-server-26.3`, run `.\\TestServer.ps1 deploy shenanigans-core`; confirm with Craftwire `list_instances` → `server_info` plus a real assertion.
- [ ] **Step 3:** With qcart, cast a normal dash and a short/stalled dash; confirm the slash arrives with the visible slash and the whoosh is absent when the dash ends early. Record the result in this plan's `findings.md`.
