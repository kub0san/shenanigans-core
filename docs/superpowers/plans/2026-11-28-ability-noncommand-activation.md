# Non-command ability activation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one cast funnel shared by `/ability` and a new right-click-on-held-dash-sword trigger, with identical gates, cooldown key and denial text.

**Architecture:** `CastSource` + `CastRequest` + `CastTriggerService` own the decision (`CastInput` reason precedence) and call the existing `AbilityCaster`; `AbilityCommand` submits instead of deciding locally; a MONITOR `ignoreCancelled=true` interact listener submits right-clicks. No companion mod, no controls UI, no domains.

**Tech Stack:** Java 25, Paper 1.21.11+ API, JUnit 5, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-11-28-ability-noncommand-activation-design.md`

## Global Constraints

- `AbilityCaster.java:69-107` keeps installed gates, domain lock, atomic cooldown claim, perform, flourish and DEBUG bypass; `AbilityGateService.java:30-45` keeps deployed/ownership/held-item checks.
- `CastInput.java:37-56` reason precedence and reply text are reused verbatim; gates run exactly once per cast.
- Right-click listener at MONITOR with `ignoreCancelled = true` so `ActionLockListener.java:106-115` cancellations never reach the funnel and produce no feedback.
- `DashSwordHitListener.java:40-50` keeps left-click refunds; cooldown key is `DashSwordAbility.ID.toString()`.
- `/ability` stays the universal fallback. No new config keys.

## Review Focus

- Command and right-click produce the same denial reason/text and consume the same cooldown key; pinned by parity tests.
- No double gate or double cooldown claim — one decision per `CastRequest`.
- Locked clicks never fire a cast — pinned by a cancelled-interact test.

---

### Task 1: Cast funnel types and service

**Files:**
- Create: `src/main/java/com/hizeric/shenaniganscore/core/ability/CastSource.java`, `CastRequest.java`, `CastTriggerService.java`
- Modify: `src/main/java/com/hizeric/shenaniganscore/ShenanigansCore.java:323-337`
- Test: `src/test/java/com/hizeric/shenaniganscore/core/ability/CastSourceTest.java`

**Interfaces:**
- Consumes: `CastInput`, `AbilityCaster`, `AbilityGateService`.
- Produces: `CastSource` values `COMMAND` and `ITEM`; `CastRequest(player, slot, source)`; `CastTriggerService.submit(CastRequest)` that performs the decision and cast once.

- [ ] **Step 1:** Failing test: `CastSource` contains `COMMAND` and `ITEM`, and `submit` calls `AbilityCaster` exactly once with a valid request.
- [ ] **Step 2:** Run `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.core.ability.CastSourceTest'` — Expected: FAIL.
- [ ] **Step 3:** Implement the three types and register the service centrally; no listener yet.
- [ ] **Step 4:** Run the test class — Expected: PASS.
- [ ] **Step 5:** Commit `git commit -m "feat: add unified ability cast trigger service"`

### Task 2: Migrate `/ability` onto the service

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/command/AbilityCommand.java:76-118`
- Test: `src/test/java/.../AbilityCasterFailureTest.java`, `CastInputTest.java`, `AbilityRailTest.java`

- [ ] **Step 1:** Add parity tests: existing denial reasons/text and slot resolution still occur through the service.
- [ ] **Step 2:** Run the three test classes — Expected: FAIL once the command asserts service routing.
- [ ] **Step 3:** Replace local decision in `AbilityCommand` with `CastTriggerService.submit(...)`; keep command tree and help unchanged.
- [ ] **Step 4:** Run the three test classes — Expected: PASS.
- [ ] **Step 5:** Commit `git commit -m "refactor: route /ability through cast service"`

### Task 3: Dash right-click trigger

**Files:**
- Create: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/DashSwordRightClickListener.java`
- Modify: `src/main/java/com/hizeric/shenaniganscore/origins/minecraft/weapons/dashsword/MinecraftAbilities.java`
- Test: new `DashSwordRightClickTest.java`

**Interfaces:**
- Consumes: `CastTriggerService`, held-item gate.
- Produces: a MONITOR `PlayerInteractEvent` handler with `ignoreCancelled = true` that submits `CastRequest(player, 1, ITEM)` only for the held dash sword and right-click actions.

- [ ] **Step 1:** Failing tests: held dash sword right-click submits once; cancelled event submits nothing; other items submit nothing; left-click submits nothing.
- [ ] **Step 2:** Run the new test class — Expected: FAIL.
- [ ] **Step 3:** Implement the listener and register it from `MinecraftAbilities.register(...)`.
- [ ] **Step 4:** Run `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.*'` plus `HeldItemAbilityTest` — Expected: PASS.
- [ ] **Step 5:** Commit `git commit -m "feat: allow right-click dash sword casts"`

### Task 4: Live 26.3 verification

- [ ] **Step 1:** Deploy with `.\\TestServer.ps1 deploy shenanigans-core`; confirm with Craftwire `list_instances` → `server_info` plus a real assertion.
- [ ] **Step 2:** With qcart: compare `/ability 1` and right-click denial text/cooldown, verify interact locks suppress the cast, and confirm offhand/eat/place conflicts observed.
- [ ] **Step 3:** Record conflict findings in this plan's `findings.md` as follow-up decisions; do not expand scope here.
