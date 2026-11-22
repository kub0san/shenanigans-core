# Ability Balance Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add real YAML configuration for the Dash Sword and the six standalone techniques (cooldown, damage, armor piercing) plus a global killstreak-requirement toggle that is disabled by default. All values are tunable live through `/bg reload`.

**Architecture:** Two typed Configurate holders — a generic, id-keyed `AbilityTuningConfig` in `core/config` (no JJK-specific logic) and `ShopPolicyConfig` in `battleground/shop`. Each holder owns a `static volatile current` swapped atomically by `load(File, Logger)`; callers read `current()` at cast/build time so reload takes effect without re-registering abilities. Keys live in fresh namespaces in the two existing managed YAML files; `ConfigAutoUpdater` merges them in and keeps admin values.

**Tech Stack:** Java 25, Paper 1.21.11+, Gradle 9.8.2 wrapper, JUnit 5, Configurate YAML 4.2.0, Adventure `MiniMessage`.

**Spec:** No separate spec file was written; the approved design is recorded in the session decisions and in this plan. Ledger note required at setup.

## Global Constraints

- Scope: Dash Sword + Fuga, Red, Blue, Purple, Piercing Blood, World Slash only. **No domain expansions or ultimates.**
- Fresh namespaces only: `abilities.items.*`, `abilities.techniques.*` in `config.yml`; `shop.killstreak-requirements` in `battleground/config.yml`. Never reuse legacy keys (`jujutsukaisen.techniques.*.cooldown-seconds`, `minecraft.dash.*`, `economy.*`) — `ConfigAutoUpdater` strips them.
- Simplified key names: `cooldown`, `damage`, `armor-piercing`; plural `items`. No `-seconds` suffix, no `minimum-cooldown` key.
- Every shipped ability cooldown is >= 30 seconds as a *default value*, not an enforced config floor. Higher-damage abilities default higher.
- Clamps: cooldown `[1, 3600]`, damage `[0, 200]`. Invalid/out-of-range values warn once and fall back to the caller-supplied shipped default. A bad config never disables the plugin.
- Ability ids are normalized: `_` becomes `-` when looking up YAML keys (`world_slash` -> `world-slash`).
- Armor piercing is configurable only where it already exists (Purple, Piercing Blood, World Slash). World Slash percent-of-max-health damage stays hardcoded.
- Live tuning is `/bg reload` only; never rely on server `/reload`. No config reads in the Bukkit main thread that can block on IO beyond the initial load/reload.
- Fail-soft: if a holder was never loaded, accessors return the caller's fallback default. `ShenanigansCore.getInstance()` may be null during shutdown — prefer the static holder current().
- `core/config` stays origin-agnostic: the holder knows nothing about JJK ids or materials. Defaults live with the abilities.
- Adventure `Component`/`MiniMessage` only; no `org.bukkit.ChatColor`. Never `Thread.sleep()`.
- Working tree (main checkout) holds unrelated uncommitted work; work in the dedicated worktree and stage exact files by full path.
- Windows: files are UTF-8.

## Review Focus

1. **Reload actually changes behavior.** The holder swap must be visible to every cast-time read; a stale captured value in `AbilityDefinition` would silently ignore `/bg reload`. Test each accessor against a reloaded holder.
2. **Invalid or hostile YAML.** Out-of-range numbers, wrong types, missing keys, and an unparseable file must produce a warning plus the shipped default, never an exception that stops enable or a zero/instant cooldown.
3. **Killstreak toggle consistency.** When disabled, `castGate` must report `required=0, streakMet=true`, the market row must show `required=0`, and the HUD must follow. No path may still gate on streak.
4. **Damage wiring regressions.** The five tuned projectile damage values must be read from config while preserving the existing per-power scaling and local clamp; the World Slash percentage must remain untouched.
5. **Naming drift.** Verify `items` is plural, keys are hyphenated, there is no `minimum-cooldown`, and no legacy key was resurrected.

## File Structure

- Add `src/main/java/com/hizeric/shenaniganscore/core/config/AbilityTuningConfig.java`
- Add `src/main/java/com/hizeric/shenaniganscore/core/config/AbilityTuningConfigTest.java`
- Add `src/main/java/com/hizeric/shenaniganscore/battleground/shop/ShopPolicyConfig.java`
- Add `src/test/java/com/hizeric/shenaniganscore/battleground/shop/ShopPolicyConfigTest.java`
- Add `src/test/java/com/hizeric/shenaniganscore/core/config/BundledConfigSchemaTest.java`
- Modify `src/main/resources/config.yml`, `src/main/resources/battleground/config.yml`
- Modify `src/main/java/com/hizeric/shenaniganscore/ShenanigansCore.java`, `src/main/java/com/hizeric/shenaniganscore/battleground/command/BattlegroundCommand.java`
- Modify `src/main/java/com/hizeric/shenaniganscore/battleground/item/DashSwordItem.java`
- Modify the six `origins/jujutsukaisen/techniques/*/Ability.java`
- Modify `BlueProjectile.java`, `red/Projectile.java`, `fuga/Projectile.java`, `PiercingBloodShot.java`, `PurpleProjectile.java`, `WorldSlashCast.java`
- Modify `battleground/economy/EconomyService.java`, `battleground/shop/MarketView.java`, `battleground/menu/shell/MarketScreen.java`
- Modify `src/test/java/com/hizeric/shenaniganscore/core/config/HardcodedDefaultsTest.java`

---

### Task 1: Typed config holders and bundled YAML schema

**Files:**
- Add: `src/main/java/com/hizeric/shenaniganscore/core/config/AbilityTuningConfig.java`
- Add: `src/test/java/com/hizeric/shenaniganscore/core/config/AbilityTuningConfigTest.java`
- Add: `src/main/java/com/hizeric/shenaniganscore/battleground/shop/ShopPolicyConfig.java`
- Add: `src/test/java/com/hizeric/shenaniganscore/battleground/shop/ShopPolicyConfigTest.java`
- Add: `src/test/java/com/hizeric/shenaniganscore/core/config/BundledConfigSchemaTest.java`
- Modify: `src/main/resources/config.yml`, `src/main/resources/battleground/config.yml`

**Interfaces:**
- Produces: `AbilityTuningConfig.load(File, Logger)`, `.defaults()`, `.current()`, `.cooldown(Section, String, int)`, `.damage(String, double)`, `.armorPiercing(String, boolean)`, `enum Section { ITEMS, TECHNIQUES }`.
- Produces: `ShopPolicyConfig.load(File, Logger)`, `.defaults()`, `.current()`, `.killstreakRequirements()`.
- Ids with `_` normalize to `-`; YAML uses hyphenated keys. Clamps: cooldown `[1,3600]`, damage `[0,200]`; invalid value warns and returns the passed fallback.

- [x] **Step 1: Write the failing holder tests**

`AbilityTuningConfigTest` covers: `defaults()` returns fallbacks; a temp YAML with `abilities.items.dash-sword.cooldown: 45` makes `cooldown(ITEMS,"dash-sword",30)` return 45; `cooldown(ITEMS,"dash_sword",30)` resolves the same key; out-of-range cooldown and damage fall back to the supplied default; a non-numeric value falls back; a boolean `armor-piercing: false` is honored and a missing key uses the fallback; loading a second file replaces `current()` (the reload simulation).

`ShopPolicyConfigTest` covers: `defaults().killstreakRequirements()` is false; `shop.killstreak-requirements: true` loads as true; a non-boolean value warns and falls back to false; a second load replaces `current()`.

`BundledConfigSchemaTest` loads both bundled resources from the classpath and asserts the exact keys: `abilities.items.dash-sword.cooldown`, all six `abilities.techniques.<id>.cooldown`, `damage` on Blue/Red/Piercing Blood/Purple/Fuga, `armor-piercing` on Piercing Blood/Purple/World Slash, and `shop.killstreak-requirements: false`; it also asserts no key contains `minimum-cooldown` or `-seconds`.

- [x] **Step 2: Run the tests and watch them fail**

Command: `./gradlew.bat test --tests 'com.hizeric.shenaniganscore.core.config.AbilityTuningConfigTest' --tests 'com.hizeric.shenaniganscore.battleground.shop.ShopPolicyConfigTest' --tests 'com.hizeric.shenaniganscore.core.config.BundledConfigSchemaTest'`
Expected: compile failure — the holder classes do not exist yet.

- [x] **Step 3: Implement both holders**

Use `YamlConfigurationLoader` through `ConfigFiles.loadFromBytes`-style parsing (or a local loader) under `core/config`; wrap parse errors in a warning and install `defaults()`. Keep `current()` non-null always. Never log ability ids from `core` — the holder is generic.

- [x] **Step 4: Add the bundled schema**

In `config.yml`, update the "all gameplay values are hardcoded" comment to carve out the `abilities` block, then add the approved values: dash-sword cooldown 30; blue 30/2.0; red 40/18.0; piercing-blood 45/14.0/armor-piercing true; purple 60/24.0/armor-piercing true; fuga 75/24.0; world-slash 90/armor-piercing true. In `battleground/config.yml`, add `shop.killstreak-requirements: false` and update the stale comment.

- [x] **Step 5: Run the task tests**

Command: same as Step 2.
Expected: all three test classes pass, `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit**

`git add` the six new/modified files by full path and commit `feat(config): add typed ability tuning and shop policy holders`.

---

### Task 2: Plugin load and `/bg reload` wiring

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/ShenanigansCore.java`
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/command/BattlegroundCommand.java`
- Add: `src/test/java/com/hizeric/shenaniganscore/core/config/ConfigReloadWiringTest.java`

**Interfaces:**
- Produces: `ShenanigansCore.abilityTuning()` returning `AbilityTuningConfig`, `ShenanigansCore.shopPolicy()` returning `ShopPolicyConfig`, `ShenanigansCore.reloadTuning()` performing both loads.
- Consumes: `AbilityTuningConfig.load/current`, `ShopPolicyConfig.load/current` (Task 1).

- [x] **Step 1: Write the failing wiring test**

`ConfigReloadWiringTest` uses reflection to assert `ShenanigansCore` declares `abilityTuning()`, `shopPolicy()`, and `reloadTuning()`, and that `BattlegroundCommand` source contains a call to `reloadTuning()` (read the source file as a resource/UTF-8 text). It also asserts `AbilityTuningConfig.current()` changes after a second `load` (reload semantics) using a temp file.

- [x] **Step 2: Run the test**

Command: `./gradlew.bat test --tests 'com.hizeric.shenaniganscore.core.config.ConfigReloadWiringTest'`
Expected: fail — the methods and the command call do not exist.

- [x] **Step 3: Wire lifecycle**

In `ShenanigansCore.onEnable`, call `reloadTuning()` immediately after `new ConfigAutoUpdater(this).run()` so the managed files are merged/created first. Add the three methods; `reloadTuning()` loads from `getDataFolder()/config.yml` and `getDataFolder()/battleground/config.yml` using the plugin logger. In `BattlegroundCommand`'s `reload` leaf, call `plugin.reloadTuning()` after `new ConfigAutoUpdater(plugin).run()` and before `manager().reloadConfig()`, then update the reply text to mention tuning.

- [x] **Step 4: Run the test and the full suite**

Command: `./gradlew.bat test --tests 'com.hizeric.shenaniganscore.core.config.ConfigReloadWiringTest'` — Expected: pass.
Command: `./gradlew.bat test` — Expected: all tests pass, no regressions.

- [x] **Step 5: Commit**

`feat(config): load and reload tuning holders with the plugin`

---

### Task 3: Dash Sword cooldown from config

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/item/DashSwordItem.java`
- Add: `src/test/java/com/hizeric/shenaniganscore/battleground/item/DashSwordTuningTest.java`

**Interfaces:**
- Produces: `DashSwordItem.DEFAULT_COOLDOWN_SECONDS` (30), `DashSwordItem.cooldownSeconds()` reading `AbilityTuningConfig.current().cooldown(ITEMS, "dash-sword", DEFAULT_COOLDOWN_SECONDS)`.
- Consumes: `AbilityTuningConfig` (Task 1).

- [x] **Step 1: Write the failing test**

`DashSwordTuningTest`: load a temp config with `abilities.items.dash-sword.cooldown: 55`, assert `cooldownSeconds() == 55` and that the generated lore string contains `55s cooldown` and not `6s cooldown`; then load a config with no entry and assert `30`.

- [x] **Step 2: Run the test**

Command: `./gradlew.bat test --tests 'com.hizeric.shenaniganscore.battleground.item.DashSwordTuningTest'`
Expected: fail — the constant/accessor do not exist.

- [x] **Step 3: Implement**

Replace the hardcoded `6s cooldown` lore and any cooldown constant with the accessor; keep the rest of the lore and item behavior unchanged. Expose the lore-producing method so the test can call it without a Bukkit server.

- [x] **Step 4: Run the test and full suite**

Command: `./gradlew.bat test --tests 'com.hizeric.shenaniganscore.battleground.item.DashSwordTuningTest'` — Expected: pass.
Command: `./gradlew.bat test` — Expected: all pass.

- [x] **Step 5: Commit**

`feat(config): tune dash sword cooldown from YAML`

---

### Task 4: Technique cooldowns from config

**Files:**
- Modify the six `Ability.java` files under `origins/jujutsukaisen/techniques/{blue,red,fuga,piercingblood,purple,worldslash}/`.
- Add: `src/test/java/com/hizeric/shenaniganscore/origins/jujutsukaisen/techniques/TechniqueCooldownTuningTest.java`
- Modify: `src/test/java/com/hizeric/shenaniganscore/core/config/HardcodedDefaultsTest.java`

**Interfaces:**
- Each `Ability.getCooldownSeconds()` keeps its signature but reads `AbilityTuningConfig.current().cooldown(TECHNIQUES, <id>, DEFAULT_COOLDOWN_SECONDS)`.
- New shipped defaults: blue 30, red 40, piercing blood 45, purple 60, fuga 75, world slash 90.

- [x] **Step 1: Write the failing tests**

`TechniqueCooldownTuningTest`: load a temp YAML overriding every technique cooldown to a distinct value and assert each `Ability.getCooldownSeconds()` returns its override (proves no stale capture); load an empty config and assert the shipped defaults above. Add assertions to `HardcodedDefaultsTest` pinning those six defaults and `>= 30`.

- [x] **Step 2: Run the tests**

Command: `./gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.TechniqueCooldownTuningTest' --tests 'com.hizeric.shenaniganscore.core.config.HardcodedDefaultsTest'`
Expected: fail — values still hardcoded below 30.

- [x] **Step 3: Implement**

Update each `DEFAULT_COOLDOWN_SECONDS` constant to the shipped default and change `getCooldownSeconds()` to read the holder. Leave the `AbilityDefinition` supplier wiring (already `Ability::getCooldownSeconds`) unchanged.

- [x] **Step 4: Run tests**

Command: same as Step 2 — Expected: pass. Then `./gradlew.bat test` — Expected: all pass.

- [x] **Step 5: Commit**

`feat(config): tune technique cooldowns from YAML`

---

### Task 5: Technique damage from config

**Files:**
- Modify: `blue/BlueProjectile.java`, `red/Projectile.java`, `fuga/Projectile.java`, `piercingblood/PiercingBloodShot.java`, `purple/PurpleProjectile.java`.
- Add: `src/test/java/com/hizeric/shenaniganscore/origins/jujutsukaisen/techniques/TechniqueDamageTuningTest.java`

**Interfaces:**
- Each projectile gains a public static `DEFAULT_DAMAGE_PER_POWER` and reads `AbilityTuningConfig.current().damage(<id>, DEFAULT_DAMAGE_PER_POWER)` in its constructor before the existing local clamp.
- New shipped defaults: blue 2.0, red 18.0, piercing blood 14.0, purple 24.0, fuga 24.0.

- [x] **Step 1: Write the failing test**

`TechniqueDamageTuningTest`: load a temp YAML overriding all five damage values and assert a static `configuredDamage()` accessor on each projectile returns the override (and the shipped default when absent). Also assert each shipped default is >= the old literal for the four buffed techniques.

- [x] **Step 2: Run the test**

Command: `./gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.TechniqueDamageTuningTest'`
Expected: fail — the constants/accessors do not exist.

- [x] **Step 3: Implement**

Replace the five `MathUtil.clamp(<literal>, ...)` / `clamp(<literal>, ...)` assignments with the holder read, keeping the existing local clamp ranges exactly. Do not change power scaling, falloff, or kill logic.

- [x] **Step 4: Run tests**

Command: same as Step 2 — Expected: pass. Then `./gradlew.bat test` — Expected: all pass.

- [x] **Step 5: Commit**

`feat(config): tune technique damage from YAML`

---

### Task 6: Armor piercing from config

**Files:**
- Modify: `piercingblood/PiercingBloodShot.java`, `purple/PurpleProjectile.java`, `worldslash/WorldSlashCast.java`.
- Add: `src/test/java/com/hizeric/shenaniganscore/origins/jujutsukaisen/techniques/ArmorPiercingTuningTest.java`

**Interfaces:**
- Each class reads `AbilityTuningConfig.current().armorPiercing(<id>, true)` in its constructor; a public static `armorPiercingEnabled()` accessor exposes the resolved value for tests.
- World Slash's percent-of-max-health damage computation remains hardcoded.

- [x] **Step 1: Write the failing test**

`ArmorPiercingTuningTest`: load a temp YAML setting `armor-piercing: false` for the three ids and assert each `armorPiercingEnabled()` is false; load an empty config and assert all three default to true. Assert World Slash still has no configurable damage key (source review).

- [x] **Step 2: Run the test**

Command: `./gradlew.bat test --tests 'com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.ArmorPiercingTuningTest'`
Expected: fail — accessors do not exist.

- [x] **Step 3: Implement**

Replace the three hardcoded `= true` assignments with holder reads and add the accessors. Keep the DamageSource bypass/fallback branches unchanged.

- [x] **Step 4: Run tests**

Command: same as Step 2 — Expected: pass. Then `./gradlew.bat test` — Expected: all pass.

- [x] **Step 5: Commit**

`feat(config): toggle armor piercing from YAML`

---

### Task 7: Killstreak requirement toggle

**Files:**
- Modify: `battleground/economy/EconomyService.java`, `battleground/shop/MarketView.java`, `battleground/menu/shell/MarketScreen.java`, `src/test/java/com/hizeric/shenaniganscore/core/config/HardcodedDefaultsTest.java`.
- Add: `src/test/java/com/hizeric/shenaniganscore/battleground/economy/KillstreakToggleTest.java`

**Interfaces:**
- `EconomyService.applyPolicy(CastGate, boolean)` returns a gate with `required=0, streakMet=true` when the flag is false; `castGate(...)` reads `ShopPolicyConfig.current().killstreakRequirements()` and applies it.
- `MarketView.page(..., int streak, Predicate<ShopEntry> owned, boolean killstreakRequirements)` overload; the existing signature delegates with `true`.
- `MarketScreen` passes the current policy flag.

- [x] **Step 1: Write the failing test**

`KillstreakToggleTest`: build a `CastGate(false, 5, 2, false)` and assert `applyPolicy(gate, false)` yields `required=0, streakMet=true`, while `applyPolicy(gate, true)` leaves it unchanged. Assert `MarketView.page(..., false)` rows have `required=0` and `streakMet=true`. Pin `ShopPolicyConfig.defaults().killstreakRequirements() == false` in `HardcodedDefaultsTest`.

- [x] **Step 2: Run the test**

Command: `./gradlew.bat test --tests 'com.hizeric.shenaniganscore.battleground.economy.KillstreakToggleTest' --tests 'com.hizeric.shenaniganscore.core.config.HardcodedDefaultsTest'`
Expected: fail — methods/overload missing.

- [x] **Step 3: Implement**

Add `applyPolicy`, route `castGate` through it, add the `MarketView.page` overload, and pass the policy flag from `MarketScreen`. `TechniqueHud` and `AbilityCommand` need no edits: they consume `castGate`, which now carries the neutral gate. Do not change catalog data or player streak persistence.

- [x] **Step 4: Run tests and build**

Command: same as Step 2 — Expected: pass.
Command: `./gradlew.bat build` — Expected: `BUILD SUCCESSFUL`, shadow jar produced.

- [x] **Step 5: Commit**

`feat(config): disable killstreak requirements by default`

---

## Self-Review Notes

- No subagent implementers: inline execution with one fresh-context whole-branch review at the end.
- Each task's test command is the record; ledger lines carry the exact command and result.
- The only intentional behavior change to existing players is the killstreak toggle default and the retuned cooldowns/damage — both are the user's explicit request.
