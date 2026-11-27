# Dash Sword reload safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A dash in progress at plugin disable/reload leaves no wind display entities, listeners or invisibility behind.

**Architecture:** Mirror the existing `DashVisibilityMask` static `ACTIVE` registry pattern in `WindTrailEffect`, then add a third safe shutdown hook beside the two existing dash hooks in `ShenanigansCore.onDisable`, before `cancelTasks`.

**Tech Stack:** Java 25, Paper 1.21.11+ API, Bukkit Display entities, JUnit 5, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-11-28-dash-reload-safety-design.md`

## Global Constraints

- Dash-only scope: do not make the whole plugin hot-reload safe, do not fail-close reload, do not reorder non-dash teardown.
- `ShenanigansCore.onDisable` currently calls `MannequinEchoRenderer.shutdown()` :426 and `DashVisibilityMask.shutdown()` :429 **before** `cancelTasks` :457; the new hook goes there too.
- `setPersistent(false)` is not sufficient for live leaks; `shutdown()` must close live instances.
- Shutdown must be idempotent via a stable snapshot of `ACTIVE`, as in `DashVisibilityMask.java:215-223`.

## Review Focus

- No wind display survives `shutdown()`; pinned by idempotence test if a MockBukkit seam is cheap, otherwise by source pin + live `/plugman usage` leak check.
- Hook ordering relative to `cancelTasks` — pinned by `ReloadSafetyWiringTest` with the `ExecutionerVisualCleanupTest.java:88-105` ordering precedent.

---

### Task 1: WindTrailEffect static registry and shutdown

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/WindTrailEffect.java:60-80,211-216`
- Test: `src/test/java/com/hizeric/shenaniganscore/ReloadSafetyWiringTest.java`

**Interfaces:**
- Consumes: existing `close()` :211-216.
- Produces: static `ACTIVE` set (instance register on construction, deregister in `close()`) and `static void shutdown()` closing a stable snapshot idempotently.

- [ ] **Step 1: Write failing source test**

```java
@Test void windTrailHasStaticShutdown() {
    // source-pin: WindTrailEffect declares `static void shutdown()` and an ACTIVE registry
}
```

- [ ] **Step 2:** Run `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.ReloadSafetyWiringTest'` — Expected: FAIL.
- [ ] **Step 3:** Implement the registry and `shutdown()` exactly like `DashVisibilityMask`; keep `close()` idempotent via a `closed` flag.
- [ ] **Step 4:** Run the test class — Expected: PASS.
- [ ] **Step 5:** Commit `git commit -m "feat: add dash wind trail shutdown registry"`

### Task 2: Wire and order the disable hook

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/ShenanigansCore.java:424-457`
- Modify: `src/test/java/com/hizeric/shenaniganscore/ReloadSafetyWiringTest.java:104-113`

**Interfaces:**
- Consumes: `WindTrailEffect.shutdown()` from Task 1.
- Produces: `runSafely("dash wind trails", ...)` call before `cancelTasks`, plus source-pinned ordering.

- [ ] **Step 1:** Add a failing ordering assertion that the wind hook index is less than the `cancelTasks` index.
- [ ] **Step 2:** Run the test class — Expected: FAIL.
- [ ] **Step 3:** Add the `runSafely` call beside the two existing dash hooks, inside the same guard block.
- [ ] **Step 4:** Run `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.ReloadSafetyWiringTest'` — Expected: PASS.
- [ ] **Step 5:** Commit `git commit -m "fix: tear down dash wind trails on disable"`

### Task 3: Live reload matrix

- [ ] **Step 1:** Deploy with `.\\TestServer.ps1 deploy shenanigans-core`; confirm with Craftwire `list_instances` → `server_info` plus a real assertion.
- [ ] **Step 2:** Start a dash, reload the single plugin with PlugManX, check `/plugman usage` and a display-entity leak detector.
- [ ] **Step 3:** Repeat with `reload all` and record whether PacketEvents stale-API warnings appear (`DashVisibilityMask.java:134/142/156/235`); decide separately if a guard is needed.
- [ ] **Step 4:** Record all results in this plan's `findings.md`.
