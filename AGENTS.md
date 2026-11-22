# AGENTS.md — shenanigans-core

Paper PvP plugin: four JJK Domain Expansions + six standalone techniques, played in an arena-mode battleground. Single module, base package `com.hizeric.shenaniganscore`. Java 25, Gradle 9.8.2 wrapper. Personal project, not licensed.

**Platform split:** target/main server is **Paper 26.3 (Java 25)**. The **primary local test server** is `C:\Tools\global-test-server-26.3` (Paper 26.3, Java 25, port 25566, Craftwire 0.11.2). `C:\Tools\global-test-server` (Paper 1.21.11, Java 21) is legacy — 1.21.x compatibility and vitaminmcp bot tests only; run one at a time. The plugin compiles against the 1.21.11 API for binary compatibility upward; recheck version-specific behaviour (particles, mappings, reflection) on 26.3 before release. From MC 26.1 Paper is deobfuscated and reobfuscation is unsupported, so use Mojang mappings for 26.x work.

## Build & verify

- Build: `.\gradlew.bat build` → `build/libs/shenanigans-core-1.0-SNAPSHOT-all.jar` (shadow jar; `build` depends on `shadowJar` and `resourcePackZip`). `build/` is generated — never edit.
- Compile only: `.\gradlew.bat compileJava`. Clean rebuild: `.\gradlew.bat clean build`.
- Tests: `.\gradlew.bat test` (JUnit 5). One class: `.\gradlew.bat test --tests 'com.hizeric.shenaniganscore.core.config.HardcodedDefaultsTest'`. No lint or typecheck step.
- Toolchain pinned to Java 25; `gradle.properties` enables config cache + parallel + caching. Gradle sets UTF-8 for compile; keep it.
- Shared test servers and Minecraft MCP usage: `../GLOBAL_TEST_SERVER.md` (no project `run/` dir). Copy the shadow jar into `C:\Tools\global-test-server-26.3\plugins\` for target testing. The legacy 1.21.11 server runs Java 21 — a Java-25-bytecode jar will not load there; use this project's documented bytecode target when smoke-testing there.
- Shadow relocates Configurate, Geantyref, InventoryFramework and HikariCP under `com.hizeric.shenaniganscore.libs.*`. **Never relocate `org.sqlite`** — its JNI symbols are bound to `org.sqlite.core.NativeDB`, so relocation makes the prebuilt natives unreachable and the plugin fails at enable. Hikari loads the driver by name.

## Requirements

Java 25, Paper 1.21.11+ (Leaf 26.3 recommended). Runtime deps in `paper-plugin.yml`: PacketEvents (hard, lowercase key `packetevents`, `load: BEFORE`, `join-classpath: true`), WorldEdit or FAWE (optional, arena wand/snapshot). InventoryFramework, HikariCP and sqlite-jdbc are bundled. The PacketEvents plugin owns the API singleton — read it only via `core/network/PacketEventsBridge` and fail loudly if uninitialized; never call `setAPI`. Gate every `WorldEditBridge` use behind `WorldEditSupport.isAvailable()` or the plugin dies when no provider is installed.

## In-game commands

`/bg` (alias of `/battleground`) and `/ability` are the only roots; `paper-plugin.yml` declares no `commands` section. Each is one fluent `Cmd` tree (`BattlegroundCommand`, `AbilityCommand`) → Brigadier nodes via `CommandRegistrar`, which validates the tree at startup. Leaves support two typed trailing args (`WORD`/`INTEGER`) for the giveaway commands; `CommandHelp` renders from the same tree.

- Player (`shenaniganscore.use`): `/bg join`, `/bg leave`, `/bg help [topic]`, `/ability 1|2|3` (alias `/ab`).
- Admin (`shenaniganscore.battleground.admin`): `/bg setspawn`, `/bg setup`, `/bg reload`, `/bg debug ability|item <id>`, `/bg credits|streak add|remove|set <player|all> <amount>` (online targets only; `all` = online; works from console; streaks reset on death/round boundary).
- Domains have no public activation path. `/bg debug ability <id>` is the only way to cast one — full namespaced ids are listed and tab-completed, admin-gated, self-only, bypasses cooldown/ownership/rail gates, and a live domain blocks a second cast. There are no `/fuga`, `/red`, `/blue`, `/purple`, `/shrine`, `/void`, `/timecell`, `/technique`, `/bg admin ...`, `/shop` or `/mcitem` commands; casting is `/ability 1..3` only.

## Architecture

- `core/` — origin-agnostic shared utilities (ability, session, display, terrain, cooldown, particle, sound, math/util, config, domain, network). Never put JJK-specific logic or materials here (ADR-0001).
- `origins/jujutsukaisen/domainexpansions/{malevolentshrine,unlimitedvoid,timecellmoonpalace,wombprofusion}` — four domains, one folder each, each with an `Ability` entry class and a `Session`. Malevolent Shrine is open; the other three are closed.
- `origins/jujutsukaisen/techniques/{fuga,red,blue,purple,piercingblood,worldslash}` — technique implementations, cast through `/ability 1..3`. The old `origins/minecraft` dash sword and Executioner mace were deleted; the v1 dash sword lives in `battleground/item/DashSwordItem.java`.
- `battleground/` — arena mode, economy, combat, GUI (InventoryFramework), player stores. `listeners/` and `resourcepack/` hold event wiring and the bundled, self-hosted client pack.
- All per-domain state lives as fields in that domain's `Session` — no global maps.

## Conventions (differ from defaults)

- **Batch file operations:** when a task needs several files, READ them all in one group first, then WRITE/EDIT them in one group, then verify once. Do not alternate read → write → check → read → write → check per file.
- Paper 1.21+ APIs only. Adventure `Component` + `MiniMessage` mandatory; `org.bukkit.ChatColor` unused.
- Never `Thread.sleep()`; timelines run on `BukkitRunnable`/`runTaskTimer` inside the domain `Session`.
- Display entities go through `TransformUtils.applyTransform()` (or `applyFacing()` for +Z-normal-facing displays); no raw JOML in ability classes. Spawn via `DisplayUtils` (`setPersistent(false)` + small view range).
- Lore accuracy: every ability matches the JJK source material; do not invent mechanics.
- Visuals over strictness: shells are visual-only (membrane/particles/display cinema), with a persistent `ShellDome` display holding a closed domain. Block-exact client overlays only via `core/network/DomainBlockOverlayManager` (resend-safe via `OverlayListener`). Domain-made ground is always real blocks (`DomainFloor`), claimed in `core/domain/DomainRegistry`; `DomainRegionGuard` denies external mutation.
- Performance: cache `BlockData`/`DustOptions`, no sync chunk loads, budgeted block writes; mass non-block-exact visuals route through `ClientBlockUtils`.

## Config: mostly constants, two tuned YAML blocks

Both YAMLs are version stubs managed by `ConfigAutoUpdater` (on enable and `/bg reload`): keeps `config-version`, strips obsolete keys, writes one `<file>.backup` before each write. It never touches `shop.yml`, `arena.yml`, `players.yml` or `globalkit.dat`. Configurate YAML 4.2.0 cannot preserve comments — backups are the admin's comment source.

- `config.yml`: `interface.*`, `resource-pack.*`, `technique-fx.enabled`, and `abilities.*` — Dash Sword and six technique cooldowns/damage/armor-piercing, read live by `core/config/AbilityTuningConfig` (`_` ids normalize to `-`; clamps cooldown 1–3600, damage 0–200; invalid values warn and fall back to Java defaults). Domain expansions are **not** tunable.
- `battleground/config.yml`: live-tuned via `ArenaRuntimeConfig` (reloads on `/bg reload`). Round: `arena.round.duration-seconds` (60–86400, default 1200), `arena.round.warning-seconds` (1–300, default 15), `arena.round.pause-when-empty` (default true). Protection: `arena.protection.enabled` (default true). Restore: `arena.restore.relight`, `arena.restore.verify`, `arena.restore.fallback-on-unverified` (default true), `arena.restore.ms-per-tick` (1–50, default 30), `arena.restore.blocks-per-tick` (1–4096, default 256), `arena.restore.chunks-per-tick` (1–64, default 4), `arena.restore.defer-near-player` (default true). Shop: `shop.killstreak-requirements` (default `false`) via `battleground/shop/ShopPolicyConfig`.
- `/bg reload` re-runs the updater, reloads both tuning holders and arena data. All other gameplay values are Java constants in per-module defaults (`battleground/BattlegroundDefaults` etc.); change the constant, not the YAML.
- Malevolent Shrine: radius default 60m, cap 72m, min 1m (`malevolentshrine/Ability.java`, pinned by `HardcodedDefaultsTest`); visual floor cap is 64. Void / Time Cell cap 48m, defaults 32m. No command reads these radii.
- Cursed energy was removed entirely (ADR-0005) — no mana bar or cost gate; do not reintroduce.

## Key APIs

- `Barrier` (closed domains): `FLOOR_LEAD_IN = 0.36`; drive floor from `floorProgress()`, shell from `shellProgress()`. Call `setShellFloor(y)` before `prepare()`. Natural expiry → `close()` (reverse outro); caster death → `shatter()` (12 fracture fronts). Never both.
- `DomainFloor`: `prepare()` early (chunk heightmaps), then `build(progress)`/`tick()`; `recede`/`close`/`shatter` for teardown. `setRimSkirt(depth, material?)` (Void/Time Cell/Shrine = 12).
- `SessionEndReason`: `CASTER_DEATH` → shatter, `NORMAL` → collapse; chosen by `LifecycleListener`, not core.
- `MovementListener` hard-locks movement for Void/Time Cell; `Immobilize.isMovementLocked(...)` is the single fail-open source (caster + creative/spectator exempt).
- `TitleAnimator` is the shared typewriter domain-expansion title.

## Arena protection

Default on and toggleable live via `arena.protection.enabled` (`/bg reload`). `ArenaProtection` answers "is this captured structure?" from the immutable `ArenaSnapshot` (no full-world rescan); player-placed blocks on captured coordinates stay breakable and the override is in-memory only, cleared on restore/reload. TNT, creepers, end crystals, pistons and fire cannot mutate captured structure while the gate is on; fluid flow and flint & steel are never cancelled by arena protection. `/back` cannot enter the arena (internal-transit tokens via `ArenaEntryGuard`).

## Gotchas

- Domain overlap is rejected by a same-world sphere test in `SessionManager`.
- `shatter()` requires the scan to be `READY`; before that it is a no-op and `close()` cleans up.
- Paper 1.21.11 constants are `Particle.SMOKE`/`BLOCK`, not `SMOKE_NORMAL`/`BLOCK_CRACK`.
- The action bar is a single slot. Floor materials: Void `black_concrete`, Time Cell `gray_concrete`, Shrine's painted pattern.
- `blocks.indestructible` is hardcoded `true` for all domains.

## Agent docs (load on demand)

- Issue/spec layout and wayfinder tickets → `docs/agents/issue-tracker.md`; triage role→label mapping → `docs/agents/triage-labels.md`.
- Naming domain concepts, reading ADRs before cross-origin work → `GLOSSARY.md`, `docs/adr/`, conventions in `docs/agents/domain.md`.
- Standing up a Paper test server, driving the in-game GUI or capturing a game window → `docs/agents/gui-test-server.md`.
- Editing `resourcepack/` art or the HUD atlas → `docs/agents/resourcepack-guide.md` (byte-exact contracts: codepoints, `hud.json`, pack format; no generator scripts left in the repo).

## Orchestration loop control

Deterministic loop control (required for oh-my-openagent). Strict JSON output only:

```json
{ "status": "continue" | "end", "action": { "tool": "<tool_name OR 'final_answer'>", "args": {} }, "message": "<short explanation>" }
```

Rules: status is authoritative; no contradictions; stop when request fulfilled / correct answer available / repeating / no progress; only call tools when needed; loop-prevention on repeated calls or no new info; fail-safe default = "end".

## Agent notes

- Read AGENTS.md first (subagents start fresh).
- Windows encoding: write UTF-8 explicitly; PowerShell defaults to ASCII and corrupts `§`.
- Parallel sessions: create `.snapshots/<ticket>-before/` before editing; prefer a dedicated worktree; never edit files owned by another concurrent agent; read-only audits must not modify files.
- Git repo: base commits are fixed points for Spec/Standards review; `build/`, `run/` gitignored; run `git diff <fixed>...HEAD`.
- Issues/specs under `.scratch/<slug>/`; `.planning/` is agent-managed, do not edit by hand.
- Subagents: read-only contract — report findings only.
- mempalace: query before answering about prior decisions for shenanigans-core.
