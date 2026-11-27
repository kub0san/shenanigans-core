# Dash Sword capability guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect unsupported mannequin metadata/reflection once, log once, disable only the echoes, and keep the dash playable (wind + visibility mask stay).

**Architecture:** A small pure capability flag in `MannequinEchoRenderer` gates `spawn`/`tick`/`syncViewers`; the first failed packet or `requireReady()` throw trips it idempotently. Cleanup stays in `close()`/`shutdown()`; no config, no dependency, no movement change.

**Tech Stack:** Java 25, Paper 1.21.11+ API, PacketEvents, JUnit 5, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-11-28-dash-capability-guard-design.md`

## Global Constraints

- Only item 3 of #45 is in scope; items 1 and 2 were declined in the issue.
- `MannequinEchoRenderer` is the only reflection-dependent dash visual (`generateEntityId`, metadata slots 0/4/5/16/17/18/19, `RESOLVABLE_PROFILE`). `WindTrailEffect` and `DashVisibilityMask` keep running.
- One warning per server session; no per-packet spam. No new config keys or dependencies.
- `close()`/`shutdown()` (`MannequinEchoRenderer.java:124-150`, `ShenanigansCore.java:426`) stay idempotent and safe when disabled.

## Review Focus

- Idempotence of the disable trip: two failures still produce one warning and one state change; pinned by test.
- No leaked display entities or team after the trip — pinned by reusing `close()` on the disable path.
- Dash movement untouched: `DashSwordEffect.java:56-144` has no changes in this plan.

---

### Task 1: Pure capability policy and test

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/MannequinEchoRenderer.java`
- Create: `src/test/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/MannequinEchoCapabilityTest.java`

**Interfaces:**
- Consumes: existing `send()` failure path :247-260 and `requireReady()` :96.
- Produces: `EchoCapability` with `boolean disabled()`, `boolean trip(Throwable failure)` returning true only on the first trip, and `reset()` for tests/shutdown.

- [ ] **Step 1: Write failing tests**

```java
@Test void firstFailureDisablesOnce() {
    EchoCapability c = new EchoCapability();
    assertTrue(c.trip(new RuntimeException("bad metadata")));
    assertFalse(c.trip(new RuntimeException("another")));
    assertTrue(c.disabled());
}
@Test void resetReenables() {
    EchoCapability c = new EchoCapability();
    c.trip(new RuntimeException());
    c.reset();
    assertFalse(c.disabled());
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.MannequinEchoCapabilityTest'` — Expected: FAIL on missing class.

- [ ] **Step 3: Implement the pure flag**

Small nested/package-private class in the renderer package; no Bukkit or PacketEvents types in its API.

- [ ] **Step 4: Run test to verify pass**

Same command — Expected: PASS.

- [ ] **Step 5: Commit**

`git commit -m "feat: add idempotent dash echo capability flag"`

### Task 2: Wire the flag into the renderer

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/MannequinEchoRenderer.java:94-121,154-197,247-260`

**Interfaces:**
- Consumes: `EchoCapability` from Task 1.
- Produces: `spawn()`, `tick()`, `syncViewers()` return immediately when disabled; `send()` trips the flag on failure and logs one warning.

- [ ] **Step 1:** Guard the three entry points with `if (capability.disabled()) return;`; call `capability.trip(failure)` in the existing per-packet catch and log there only when `trip()` returns true.
- [ ] **Step 2:** On trip, call the existing `close()` so current echoes and the team are removed; leave the static `shutdown()` hook untouched.
- [ ] **Step 3:** Run `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.*'` — Expected: PASS.
- [ ] **Step 4:** Commit `git commit -m "fix: degrade dash echoes gracefully on metadata failure"`

### Task 3: Live 26.3 verification

- [ ] **Step 1:** Deploy with `.\\TestServer.ps1 deploy shenanigans-core`; confirm with Craftwire `list_instances` → `server_info` plus a real assertion.
- [ ] **Step 2:** Cast with `/bg debug ability <dash>`; confirm wind and held-item mask still work. If the guard trips, capture the first exception into this plan's `findings.md` and cite it in #45.
- [ ] **Step 3:** Confirm no repeated warning spam after multiple casts.
