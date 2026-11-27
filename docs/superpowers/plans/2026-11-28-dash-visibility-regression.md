# Dash Sword visibility regression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the #68 invariant true after the curved/decelerating launch: the caster's held item never ghosts or appears replaced, and every dash end restores visibility and equipment exactly once.

**Architecture:** No rewrite. `DashVisibilityMask` keeps its UUID self-exemption, synchronous `relinquish()` handoff and idempotent `restore()`; every new guard in the launch/regression plans funnels through `Timeline.finish()`/`clear()`. Tests pin the invariant; live 26.3 checks cover what mocks cannot.

**Tech Stack:** Java 25, Paper 1.21.11+ API, JUnit 5, PacketEvents bridge, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-11-28-dash-visibility-regression-design.md`

## Global Constraints

- Caster is always exempt from the mask by UUID (`DashVisibilityMask.shouldMaskViewer` :106-111); first person and F5 both covered.
- `relinquish()` stays synchronous in the constructor (:77-99); no async/yield, no new per-dash listener.
- `DashSwordEffect.finish()` → `clear()` → `visibility.restore()` stays the single end fan-in; `ShenanigansCore.onDisable` hook stays before `cancelTasks`.
- Run after `2026-11-28-dash-sword-curved-launch.md`; it adds early-end paths that must use this fan-in.

## Review Focus

- Any new `return`/early exit in `Timeline.run()` that skips `finish()` — pinned by source assertion plus live early-end checks.
- Disable hook ordering relative to `cancelTasks` — pinned by `ReloadSafetyWiringTest`.
- Self-exemption policy — already pinned by `DashVisibilityMaskViewerTest`; keep it green.

---

### Task 1: Pin the invariant in tests

**Files:**
- Modify: `src/test/java/com/hizeric/shenaniganscore/DashVisibilityMaskViewerTest.java`
- Modify: `src/test/java/com/hizeric/shenaniganscore/ReloadSafetyWiringTest.java`

**Interfaces:**
- Consumes: existing `DashVisibilityMask.shouldMaskViewer`, `DashSwordEffect.finish()`, `ShenanigansCore.onDisable`.
- Produces: source assertions that `Timeline` early ends call `finish()` and that the disable hook precedes `cancelTasks`.

- [ ] **Step 1: Write failing tests**

```java
@Test void viewerPolicyStillExemptsCaster() {
    // existing assertions: self, null, non-player, mismatched id all false; third party true
}
@Test void everyTimelineEndReachesFinish() {
    // source-pin: no `return` in Timeline.run() between mask creation and finish()
}
```

- [ ] **Step 2: Run tests to verify failure**

Run: `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.DashVisibilityMaskViewerTest' --tests 'com.hizeric.shenaniganscore.ReloadSafetyWiringTest'` — Expected: FAIL on the new source assertions.

- [ ] **Step 3: Add assertions only; do not change production code**

If an early end really bypasses `finish()`, fix it by calling `finish()` (not by duplicating `restore()`).

- [ ] **Step 4: Run tests to verify pass**

Same command — Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "test: pin dash visibility restore invariant"`

### Task 2: Live 26.3 verification

**Files:** none (verification only).

- [ ] **Step 1:** From `C:\\Tools\\global-test-server-26.3`, run `.\\TestServer.ps1 deploy shenanigans-core`; Craftwire `list_instances` → `server_info` plus a real assertion.
- [ ] **Step 2:** With qcart and a second viewer: first-person and F5 held item, hotbar swap mid-dash, rapid `/bg debug ability <dash>` recast, forced early ends (death, quit, `/leave`, stall) and mid-dash reload.
- [ ] **Step 3:** Record results in this plan's `findings.md`; if the residual handoff gap shows up live, add a synchronous re-broadcast on recast (no async).
