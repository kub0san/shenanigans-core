# Ability HUD notice channel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move one-shot ability notices (including `/ability` denials and cooldown nudges) off the shared action bar onto subtitles, with sticky notices re-sent until released. The persistent three-slot HUD stays untouched.

**Architecture:** `TechniqueHud.showNotice()` / `ActionBarNotice` become the single notice sink and send subtitles with the existing glyph components; `AbilityCommand` and `AbilityCaster.fail()` call it instead of `ctx.reply`; the existing 5-tick HUD refresh re-sends sticky notices and clears the subtitle on release/domain start.

**Tech Stack:** Java 25, Paper 1.21.11+ API, Adventure `Component`/MiniMessage, JUnit 5, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-11-28-ability-hud-notice-channel-design.md`

## Global Constraints

- Existing glyphs only: DASH_SWORD U+E0E6–E0FC in `TechniqueGlyphs.java:68-72`, `resourcepack/assets/shenanigans/font/hud.json:23-24`; keep the no-pack `◆` fallback.
- `TechniqueHud.send()` :40-66, `ActionBarView` :35-41/:80-87, `IndicatorRouter` :27-35 and their suppression rules are unchanged.
- Dash slot 1 still resolves from the held item (`AbilityCommand.java:64-67`); refunds stay silent (`DashSwordHitListener.java:49`).
- No new codepoints, no atlas edits, no #50/#42/#72 work. Moving the persistent HUD is explicitly out of scope.

## Review Focus

- No notice is lost in migration: every current `ctx.reply` failure in `AbilityCommand.java:93-117` and `AbilityCaster.fail()` :120-122 has a subtitle equivalent with identical text.
- Sticky notices do not linger: blank subtitle on `clearNotice()` and on domain start; pinned by `ActionBarNoticeTest`.
- Action-bar HUD rendering stays byte-identical; pinned by `ActionBarViewTest` and `IndicatorRouterContractTest`.

---

### Task 1: Subtitle notice sink

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/ability/TechniqueHud.java:161-180`
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/indicator/ActionBarNotice.java:14-69`
- Test: `src/test/java/.../ActionBarNoticeTest.java`

**Interfaces:**
- Consumes: existing notice component construction.
- Produces: `showNotice(...)` sends `player.sendSubtitle(...)` using the same component; `clearNotice(...)` sends a blank subtitle.

- [ ] **Step 1:** Add failing tests asserting subtitle send on `showNotice` and blank send on `clearNotice`.
- [ ] **Step 2:** Run `.\\gradlew.bat test --tests 'com.hizeric.shenaniganscore.battleground.indicator.ActionBarNoticeTest'` — Expected: FAIL.
- [ ] **Step 3:** Switch the sink to subtitles; keep sticky/TTL state fields as-is.
- [ ] **Step 4:** Run the test class — Expected: PASS.
- [ ] **Step 5:** Commit `git commit -m "feat: send ability notices as subtitles"`

### Task 2: Migrate the real one-shot notices

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/command/AbilityCommand.java:93-117`
- Modify: `src/main/java/com/hizeric/shenaniganscore/core/ability/AbilityCaster.java:120-122`
- Test: `src/test/java/.../ActionBarViewTest.java`, `src/test/java/.../IndicatorRouterContractTest.java`

- [ ] **Step 1:** Add a failing test listing every denial path that must call `showNotice` instead of `ctx.reply`.
- [ ] **Step 2:** Run the two test classes — Expected: FAIL.
- [ ] **Step 3:** Replace `ctx.reply` on denial/cooldown paths with `showNotice`; keep text and glyph fallback identical. Success replies stay as-is.
- [ ] **Step 4:** Run both test classes — Expected: PASS.
- [ ] **Step 5:** Commit `git commit -m "refactor: route ability denials to subtitle notices"`

### Task 3: Sticky re-send and clear

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/ability/TechniqueHud.java`
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/round/RoundController.java:82,135-141`
- Test: `src/test/java/.../ActionBarNoticeTest.java`

- [ ] **Step 1:** Failing test: a sticky notice is re-sent on each HUD refresh until `clearNotice()`, then one blank subtitle; domain start also clears.
- [ ] **Step 2:** Run the test class — Expected: FAIL.
- [ ] **Step 3:** Re-send on refresh only when the notice is still sticky and unexpired; clear on release and domain start.
- [ ] **Step 4:** Run `.\\gradlew.bat test` — Expected: PASS.
- [ ] **Step 5:** Commit `git commit -m "fix: keep sticky ability notices until released"`

### Task 4: Live 26.3 verification

- [ ] **Step 1:** Deploy with `.\\TestServer.ps1 deploy shenanigans-core`; confirm with Craftwire `list_instances` → `server_info` plus a real assertion.
- [ ] **Step 2:** With qcart: trigger each `/ability` denial, confirm subtitle text/glyph and no action-bar notice; confirm sticky release and domain start clear the subtitle.
- [ ] **Step 3:** Record results in this plan's `findings.md`.
