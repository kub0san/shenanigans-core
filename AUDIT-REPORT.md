# Comprehensive Audit Report — shenanigans-core

**Target:** Paper 1.21.11+ PvP plugin (Java 25 / Gradle 9.8.2), `com.hizeric.shenaniganscore`
**Scope:** all 243 main source files (43,623 LOC) + 86 test files (381 `@Test`) + build/metadata/resources
**Date:** 2026-11-21
**Method:** 8 parallel read-only audits by package + dimension, plus authoritative web research. Every finding carries a `file:line` and a concrete evidence anchor. The working tree contained large uncommitted user changes; no source file was modified.

---

## 1. Executive Summary

The codebase is **architecturally sound at the package level** — `core/` imports zero `origins` classes, `origins/` imports zero `battleground` classes, dependency inversion is explicit through `core/ability/AbilityCaster.Gate` (`AbilityCaster.java:21-40`), and `onDisable` is ordered and fault-isolated. Concurrency discipline is unusually good: explicit `requireMainThread()` guards, immutable snapshots, concurrent collections, comprehensive task cancellation, and no `Thread.sleep`/`ExecutorService`/`parallelStream` anywhere. The write side of player persistence is correctly off-thread.

That foundation is undermined by a small number of **gameplay-correctness defects with severe consequences** and a larger set of **lifecycle/performance leaks**:

- **One CRITICAL data-loss bug:** if the server reloads or stops during the ~1-second caster-death shatter animation, the *real* domain floor is never restored. The black/gray platform is permanently baked into the world (`AUDIT-01`).
- **Two HIGH gameplay bugs, both verified against Paper API bytecode:** `PlayerTeleportEvent` has its own `HandlerList`, so the `PlayerMoveEvent` handlers that are supposed to lock Unlimited Void and Time Cell captives never see teleports. Ender pearls, chorus fruit and portals bypass both domains' core mechanic (`AUDIT-02`, `AUDIT-03`).
- **One HIGH architecture bug:** the documented domain-overlap rejection is dead code — `SessionManager.isOverlapping()` has zero production callers, so two players can run overlapping domains with interleaved real floors (`AUDIT-04`).
- **Two HIGH availability bugs from one config default:** the required resource pack advertises `127.0.0.1` to clients, so remote players cannot join on a fresh install (`AUDIT-05`); malformed `host`/`port` values throw `IllegalArgumentException` before the catch block and disable the entire plugin (`AUDIT-06`).
- **Main-thread I/O and unbudgeted writes:** first player lookup is synchronous SQLite against a **single-connection pool with a 30 s busy timeout** (`AUDIT-07`); Shrine scars perform ~90 unbudgeted real block writes per tick, bypassing the 1,200-block budget (`AUDIT-08`).
- **Unbounded memory growth:** `ClientBlockUtils.clearPlayer`, `CooldownManager.clearAll/clearExpired` and `SetupSession.cancelAll` all have **zero call sites** — dead cleanup APIs leave per-player state behind forever (`AUDIT-09`, `AUDIT-25`, `AUDIT-26`).
- **Highest-impact redesign opportunity:** collapse the six 800–1,400 LOC technique Builders and four domain Sessions behind a shared `TechniqueProjectile` base class and per-effect `fx/` companions, and replace the 90-call `getInstance()` service locator with injected ports (`AUDIT-10`, `AUDIT-11`, `AUDIT-18`, `AUDIT-19`).

The plugin is **not Folia-compatible** (`folia-supported` absent, global `BukkitScheduler` throughout) — consistent with its documented Paper/Leaf target, but it must be stated explicitly to prevent misdeployment (`AUDIT-50`).

**Bottom line:** fix `AUDIT-01` through `AUDIT-08` before the next production build. They are correctness/availability defects, not polish. The redesign items can follow incrementally without a rewrite.

---

## 2. Prioritized Findings Table

Severity legend: **CRITICAL** = data loss / permanent world corruption. **HIGH** = core mechanic broken, plugin disabled, or severe TPS risk. **MEDIUM** = real defect with bounded impact. **LOW** = minor robustness/perf/consistency.

| ID | Severity | Category | File:Line | Finding |
|----|----------|----------|-----------|---------|
| AUDIT-01 | CRITICAL | Bug / Lifecycle | `unlimitedvoid/Session.java:738-740,677-681`; `timecellmoonpalace/Session.java:684-686,616-620`; `SessionManager.java:52-58` | Reload/shutdown during caster-death shatter skips `floor.close()`, permanently stranding the real domain floor in the world. |
| AUDIT-02 | HIGH | Bug | `listeners/MovementListener.java:64-76,114-122` | Unlimited Void movement lock bypassed by all gameplay teleports — `PlayerMoveEvent` handler never receives `PlayerTeleportEvent`; branch is dead code. |
| AUDIT-03 | HIGH | Bug | `timecellmoonpalace/Enforcer.java:50-69,117-124` | Time Cell barrier/freeze lock bypassed by teleports — same root cause; captives pearl/portal across the sphere. |
| AUDIT-04 | HIGH | Bug / Architecture | `SessionManager.java:70-84`; `BattlegroundCommand.java:140-144` | Overlap rejection (`isOverlapping`) has zero production callers; two players can run overlapping domains. |
| AUDIT-05 | HIGH | Bug | `config.yml:21-23`; `ResourcePackManager.java:58,80-89,116-125` | Required resource pack advertises loopback host to clients → remote players blocked at download on fresh install. |
| AUDIT-06 | HIGH | Bug | `ResourcePackManager.java:57-63,143-148` | Malformed `resource-pack.host`/`port` throws `IllegalArgumentException` before the `IOException` catch → whole plugin disabled. |
| AUDIT-07 | HIGH | Performance | `PlayerStore.java:96,127-135,28-31`; `Database.java:44-48` | First player lookup is synchronous SQL on main thread; 1-connection pool + 30 s busy timeout can freeze the server. |
| AUDIT-08 | HIGH | Performance | `malevolentshrine/Dismantle.java:504-533,422`; `Floor.java:289-298`; `DomainFloor.java:892-910` | ~90 unbudgeted real `setBlockData` writes/tick from Shrine scars bypass the 1,200-block budget. |
| AUDIT-09 | HIGH | Architecture / Bug | `SetupSession.java:22,77-79` | Static `ACTIVE` map holds Player/plugin/task refs; `cancelAll()` has zero call sites → reload bug + classloader leak. |
| AUDIT-10 | HIGH | Architecture | `fuga/Builder.java:1-1387`, `purple/PurpleBuilder.java:1-1054`, `piercingblood/PiercingBloodBuilder.java:1-984`, `blue/BlueBuilder.java:1-937`, `worldslash/WorldSlashBuilder.java:1-824`, `red/Builder.java:1-803` | Six technique Builders (≈5,989 LOC) are god classes with zero direct test coverage. |
| AUDIT-11 | HIGH | Architecture | test tree (381 `@Test`: battleground 312, core 48, origins 21) | Test investment avoids the highest-risk code: zero tests for all six technique packages, unlimitedvoid, wombprofusion, core/display, core/network. |
| AUDIT-12 | MEDIUM | Thread Safety / Bug | `core/terrain/DomainFloor.java:1113-1128,1044-1049,1181-1183` | Async chunk-restore completion can throw before decrementing counters → restore never finishes, blocks stay overridden. |
| AUDIT-13 | MEDIUM | Bug / Config | `BattlegroundCommand.java:76-80`; `ShenanigansCore.java:100-101` | `/bg reload` never calls `plugin.reloadConfig()` or re-applies `GuiStyle` → edited config values have no effect. |
| AUDIT-14 | MEDIUM | Thread Safety | `ShenanigansCore.java:57,276,303-326` | Shutdown-safety singleton `instance` is not `volatile`; async threads may see stale non-null value → `IllegalPluginAccessException`. |
| AUDIT-15 | MEDIUM | Performance | `DomainRegionGuard.java:176-189,85-95` | `liveOwners()` HashSet allocated on every fluid/ignite event even with no claim and zero domains. |
| AUDIT-16 | MEDIUM | Bug | `timecellmoonpalace/Enforcer.java:127,143,155` | Three `@EventHandler`s omit `ignoreCancelled` → frozen players punished (limb severed) for actions already cancelled by other plugins. |
| AUDIT-17 | MEDIUM | Architecture | `ShenanigansCore.java:57,290` (90 calls in 52 files; 10 core files) | Service-locator `getInstance()` couples origin-agnostic `core/` to the plugin root and blocks unit testing. |
| AUDIT-18 | MEDIUM | Architecture | `malevolentshrine/Session.java:244`, `BlueProjectile.java:165`, `fuga/Projectile.java:158`, `WorldSlashCast.java:203`, `Slashes.java:104,129,406,478`, `Dismantle.java:422,429` | 17 direct `runTask*(getInstance(), …)` calls bypass the null-safe helpers → NPE on late scheduling during shutdown. |
| AUDIT-19 | MEDIUM | Architecture | `GuiScreen.java:44` | Static `REQUIRED` permission map has no bulk teardown → stale permission decisions survive reload. |
| AUDIT-20 | MEDIUM | Architecture | `Shrine Session.java:1-1047`, `Void Session.java:1-753`, `TimeCell Session.java:1-692`, `Womb Session.java:1-508` | Four domain Sessions are large untested orchestrators; Time Cell's `Stage/Film/Rule24` split is the pattern the others ignore. |
| AUDIT-21 | MEDIUM | Architecture | `BlueProjectile.java:164-179`, `PurpleProjectile.java:178-191`, `red/Projectile.java:148-162`, `fuga/Projectile.java:157-164`, `PiercingBloodShot.java:173-188` | Near-identical projectile state machine duplicated five times; fixes must be ported manually. |
| AUDIT-22 | MEDIUM | Architecture | `malevolentshrine/Builder.java:53`, `Session.java:188` | `public static boolean HIGH_DETAIL` is a process-wide mutable global; correct only because overlap prevention accidentally holds. |
| AUDIT-23 | MEDIUM | Architecture / Data loss | `Database.java:77-95` | Authoritative SQLite DB has no backup; `backupTo()` is dead code; `synchronous=NORMAL` not power-loss durable. |
| AUDIT-24 | MEDIUM | Performance / Leak | `ClientBlockUtils.java:37,45,158-172`; `DomainBlockOverlayManager.java:49` | `clearPlayer()` has zero call sites; no `PlayerQuitEvent` handler → unbounded per-player block-state retention. |
| AUDIT-25 | MEDIUM | Performance / Leak | `CooldownManager.java:19-22,110-127` | `clearExpired()`/`clearAll(uuid)` have zero call sites and even `clearExpired` misses the totals maps. |
| AUDIT-26 | MEDIUM | Performance | `DomainFloor.java:377-451`; `Shrine Session.java:343-344` | `prepare()` scans ~12,800 columns with 100k+ object allocations in one tick at domain activation. |
| AUDIT-27 | MEDIUM | Performance / Thread Safety | `BattlegroundManager.java:290-292`; `ArenaBlockGuard.java:67-127` | Arena baseline reload does sync file I/O + possible 2.56M-lookup full scan on main thread each round boundary. |
| AUDIT-28 | MEDIUM | Performance | `DomainBlockOverlayManager.java:41,136-147,213-225,257-269` | 10 Hz viewer sweep re-iterates every block for every viewer (~32k ops/2 ticks) and string-parses block states. |
| AUDIT-29 | MEDIUM | Performance | `Dismantle.java:404-429`; `Slashes.java:72-129`; `Film.java:402-416` | Per-tick micro-task storms (one task per wave/blade/limb) with no distance culling. |
| AUDIT-30 | MEDIUM | Performance | `DomainFloor.java:782-800,107,115-116` | `shatter()` restores ~7,200 columns in 24 tick buckets with no budget → 1,000-3,000 writes in one tick. |
| AUDIT-31 | MEDIUM | Bug / Config | `ConfigFiles.java:53-91` | Parse failure overwrites config with bundled defaults (loses admin values); writes are non-atomic. |
| AUDIT-32 | LOW | Bug | `ResourcePackManager.java:108-125` | Delayed pack send captures `Player` with no `isOnline()` recheck. |
| AUDIT-33 | LOW | Bug / Architecture | `ShenanigansCore.java:262` vs `:81-83` | `WorldEditBridge.setEnabled(false)` called unconditionally on disable, violating the guarded-load invariant. |
| AUDIT-34 | LOW | Bug | `ResourcePackManager.java:40,73-75`; `ShenanigansCore.java:268-276` | `disable()` never clears `enabled`; manager field not nulled on shutdown; `TechniqueHud.java:104` reads stale state. |
| AUDIT-35 | LOW | Bug | `LifecycleListener.java:34-38` | Dereferences `getInstance()` without the null guard every other listener uses → NPE during reload window. |
| AUDIT-36 | LOW | Thread Safety | `GuiChatPrompt.java:101-116,29,95-99` | Async chat handler uses non-volatile static `plugin` and cancels a main-thread task off-thread. |
| AUDIT-37 | LOW | Architecture | `BattlegroundListener.java:245-246` | Checks permission node `shenaniganscore.battleground.command.bypass` that is declared nowhere. |
| AUDIT-38 | LOW | Performance | `Immobilize.java:25-36` (≈41 callers) | No empty-session fast path on a gate called from ~41 handlers, including hot move events. |
| AUDIT-39 | LOW | Architecture | `CommandRegistrar.java:62-67`; `Cmd.java:85-95` | Two-argument command nodes silently discard their first handler; validator does not enforce the invariant. |
| AUDIT-40 | LOW | Thread Safety / Data loss | `PlayerStore.java:69-90,192-201,243-250` | Shutdown drain `wait()` loop has no deadline; failed last-chance writes re-dirtied but never retried. |
| AUDIT-41 | LOW | Performance | `BattlegroundCommand.java:76-81`, `GlobalKitEditorScreen.java:66-75` | Admin/GUI reload paths do blocking file/SQL I/O on main thread. |
| AUDIT-42 | LOW | Performance | `Shrine Session.java:876-878`; `Rule24.java:464,470`; `Womb Session.java:393,466` | `MiniMessage.deserialize` + `String.repeat` rebuilt every HUD tick. |
| AUDIT-43 | LOW | Performance | `BattlegroundListener.java:182-197,449-455` | Region-exit check on every move event duplicates `ArenaEntryGuard.onMove` bounding-box test. |
| AUDIT-44 | LOW | Performance | `DomainFloor.java:618-620,837-851` | Whole-world entity scan every 10 ticks during floor build; `getNearbyEntities` available. |
| AUDIT-45 | LOW | Performance | `Shrine Session.java:802-824,853-866` | Ambient particle bursts ≈2,160 spawns/s with only partial culling. |
| AUDIT-46 | LOW | Performance | `Barrier.java:288,319,333` | `new Particle.DustOptions(...)` allocated every tick despite AGENTS.md cache rule. |
| AUDIT-47 | LOW | Architecture | `Barrier.java:65,133-136,150-160` | Documented "shatter requires scan READY" contract not implemented; `ready()` has no callers; docs contradict visual-only code. |
| AUDIT-48 | LOW | Architecture | build files (whole repo) | No static analysis (Checkstyle/SpotBugs/ArchUnit); AGENTS.md test counts stale (378/87 vs measured 381/86). |
| AUDIT-49 | LOW | Thread Safety / Bug | `resourcepack/ResourcePackManager.java:141,151-184` | HTTP daemon thread correctly confined, but host binding and advertised URL are decoupled (root of AUDIT-05). |
| AUDIT-50 | LOW | Architecture / Thread Safety | `paper-plugin.yml:9-14`; `ShenanigansCore.java:266` | No `folia-supported` key while global BukkitScheduler is used — Paper-only by design but undocumented. |

---

## 3. Detailed Findings & Remediation

### AUDIT-01 — CRITICAL — Bug / Lifecycle — Shutdown during shatter strands the real domain floor

**Evidence** — `unlimitedvoid/Session.java:738-740` and `:677-681` (Time Cell structurally identical at `:684-686`, `:616-620`):
```java
private void finishShatter() {              // animator onFinished callback
    shatterAnimator = null;
    if (shell != null) shell.close();
    if (floor != null) floor.close();       // ONLY real-floor close in the shatter path
    if (isActive()) finalizeCloseState();
}
// finalizeCloseState():
if (shatterAnimator == null) {
    if (shell != null) shell.close();
    if (floor != null) floor.close();
}
```
`SessionManager.java:52-58`: `stopAll()` calls `session.close()` (not `end(SessionEndReason)`).
`ShenanigansCore.java:266`: `Bukkit.getScheduler().cancelTasks(this);` in `onDisable`.

**Analysis** — `end(CASTER_DEATH)` (Void `:649-668`) cancels the timeline task, starts `BarrierShatterAnimator`, and deliberately leaves the session **active** so `finishShatter()` can close the shell and — critically — `DomainFloor`, the only component that restores the platform's real blocks. `finalizeCloseState()` intentionally skips `floor.close()` while `shatterAnimator != null`. On `/reload` or shutdown in that window, `stopAll()` calls `close()`, which also skips `floor.close()` because the animator is still set, then deactivates and removes the session. `onDisable` then cancels all tasks, so `finishShatter()` can never run. Net result: `DomainFloor.close()` is never called, so no `restoreNow()` and no watchdog run. Domain floors are **real blocks** (AGENTS.md; `DomainFloor` javadoc 31-68), and world data is saved — so the black/gray platform is permanent. `DomainRegistry.clearAll()` (`:261`) only drops claims. Normal expiry is unaffected: `close()` → `finalizeCloseState()` → `floor.close()` runs synchronously because `JavaPlugin` sets `isEnabled=false` before `onDisable`, so `DomainFloor.close()` takes its `restoreNow()` branch. Only the shatter-in-flight window is broken.

**Recommendation** — Add a reason-aware force-close to the session contract and call it from `SessionManager.stopAll()`:
```java
// DomainSession
void closeNow() {
    if (shatterAnimator != null) { shatterAnimator.close(); shatterAnimator = null; }
    if (shell != null) shell.close();
    if (floor != null) floor.close();   // plugin disabling -> restoreNow() path
    deactivate();
}
```
Alternatively, make `close()` itself always close `shell`/`floor` when `!plugin.isEnabled()`, regardless of `shatterAnimator`. Keep the animator's normal `onFinished` idempotence.

**Verification** — Cast `jjk:unlimited_void`, kill the caster, then `/reload confirm` (or stop the server) within ~1 s. Rejoin and inspect the domain centre: today the flat black platform persists; after the fix it is gone. Also assert idempotence when `finishShatter()` fires normally.

---

### AUDIT-02 — HIGH — Bug — Unlimited Void movement lock bypassed by teleports

**Evidence** — `listeners/MovementListener.java:64-76`, dead helper `:114-122`:
```java
@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
public void onPlayerMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    if (!Immobilize.isMovementLocked(player)) return;
    // Teleports are a subclass of move events — split them out explicitly.
    if (event instanceof PlayerTeleportEvent teleport) {
        if (isGameplayTeleport(teleport.getCause())) teleport.setCancelled(true);
        return;                              // admin/plugin teleports allowed
    }
    ...
}
```

**Analysis** — `PlayerTeleportEvent extends PlayerMoveEvent` **but declares its own `HandlerList`**. Bukkit/Paper dispatch by the event's concrete class: a handler registered for `PlayerMoveEvent` attaches to `PlayerMoveEvent.HANDLER_LIST` and is never invoked for a `PlayerTeleportEvent` (which uses `PlayerTeleportEvent.HANDLER_LIST`). Confirmed directly from the installed Paper 1.21.11 API bytecode: `PlayerTeleportEvent.getHandlers()` → `getstatic Field HANDLER_LIST // PlayerTeleportEvent.HANDLER_LIST (#29)` and the class overrides both `getHandlers()` and `getHandlerList()`; `PlayerMoveEvent.getHandlers()` → `getstatic Field HANDLER_LIST // PlayerMoveEvent.HANDLER_LIST (#85)`. The entire `instanceof` branch is unreachable. A locked victim can therefore escape with an ender pearl, chorus fruit (`CONSUMABLE_EFFECT`), or nether/end portal, and after landing the next `PlayerMoveEvent` has `from == to`, so the `!event.hasChangedPosition()` snap-back at `:78` also declines. This contradicts the documented policy (class javadoc 39-46; AGENTS.md "gameplay teleports are blocked").

**Recommendation** — Register a dedicated concrete-type handler (the pattern already used by `ArenaEntryGuard.java:85` and `OverlayListener.java:37`):
```java
@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
public void onTeleport(PlayerTeleportEvent event) {
    if (!Immobilize.isMovementLocked(event.getPlayer())) return;
    if (isGameplayTeleport(event.getCause())) event.setCancelled(true);
}
```
Remove the unreachable `instanceof` branch from `onPlayerMove`; keep the helper.

**Verification** — Cast Unlimited Void on a survival player; victim throws an ender pearl / eats chorus fruit / enters a portal. Before: they escape. After: those causes are cancelled while admin `/tp`, `COMMAND`, `PLUGIN`, `SPECTATE` teleports still succeed.

---

### AUDIT-03 — HIGH — Bug — Time Cell barrier/freeze bypassed by teleports

**Evidence** — `timecellmoonpalace/Enforcer.java:50-69`, dead helper `:117-124`:
```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onMove(PlayerMoveEvent e) {
    ...
    if (e instanceof PlayerTeleportEvent teleport && !isGameplayTeleport(teleport.getCause())) {
        return;                                  // moot: never reached
    }
    // 1) Closed barrier: crossing the sphere in either direction is refused
    if (s.cfg.barrierLock && s.barrierApplies(p) && s.inside(from) != s.inside(to)) {
        e.setTo(hold(from, to));
        return;
    }
```

**Analysis** — Same root cause as AUDIT-02. Both intended behaviours fail: the closed-barrier crossing check (`:66-70`) is never evaluated for a teleport, so a captive can pearl/portal across the sphere in either direction; the frozen clamp (`:76-84`) is bypassed; and the admin-teleport exemption (`:62-64`) is moot. This defeats the domain's core rule ("Nobody crosses the sphere in either direction" — class javadoc 27).

**Recommendation** — Add a dedicated `PlayerTeleportEvent` handler on the same per-domain `Enforcer` (no lifecycle change needed — `Session.java:179,593,634,647` already unregisters all handlers on this listener):
```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onTeleport(PlayerTeleportEvent e) {
    try {
        if (!s.isActive()) return;
        if (isGameplayTeleport(e.getCause())) { e.setCancelled(true); return; }
        if (s.cfg.barrierLock && s.barrierApplies(e.getPlayer())
                && s.inside(e.getFrom()) != s.inside(e.getTo())) e.setCancelled(true);
    } catch (Throwable ignored) { /* fail-open, consistent with other handlers */ }
}
```
Remove the unreachable `instanceof` branch from `onMove`.

**Verification** — With `barrierLock` on, pearl from inside the sphere to outside (and back): before the teleport succeeds; after it is cancelled. Confirm an admin `PLUGIN`/`COMMAND` teleport still crosses freely.

---

### AUDIT-04 — HIGH — Bug / Architecture — Domain overlap rejection is dead code

**Evidence** — `SessionManager.java:70-84` defines `isOverlapping(Location, double)`; `DomainOverlap.java` and `DomainOverlapTest.java` implement/test the geometry. The only live guard is `BattlegroundCommand.java:140-144`:
```java
if (plugin.getSessionManager().hasActiveSession(p.getUniqueId())) {
    ctx.reply("<red>You already own a live domain — close it before casting another.");
    return;
}
def.perform(p);
```
Repo-wide search: `isOverlapping` appears only at its definition, Javadocs, and tests — **zero production call sites**. All four `Ability.cast(...)` methods (e.g. `malevolentshrine/Ability.java:57-60`) omit it.

**Analysis** — AGENTS.md states "Domain overlap rejected via same-world sphere test in `SessionManager`." The geometry and snapshot API exist but nothing invokes them. The per-caster guard blocks a second cast by the *same* player, but a *different* player can cast a domain whose sphere touches a live one. Both then build real `DomainFloor` platforms; `DomainRegistry.claim()` overwrites `ownersByWorld` for shared positions; `DomainRegionGuard` and overlay managers fight over the same blocks; one ending double-restores or leaves holes. `startSession()` also blindly `put`s by caster UUID with no collision check.

**Recommendation** — Call `isOverlapping(player.getLocation(), Ability.DEFAULT_RADIUS)` in each domain `Ability.cast(...)` with a clear "another domain is already active here" message, and re-check defensively in `SessionManager.startSession()` before `put`/`capture`/`activate`.

**Verification** — Two admins stand within roughly the sum of the radii and each run `/bg debug ability jjk:malevolent_shrine` (or Void + Time Cell). Today both start; after the fix the second is rejected with a clear message.

---

### AUDIT-05 — HIGH — Bug — Required resource pack advertises loopback to clients

**Evidence** — `config.yml:21-23`:
```yaml
resource-pack:
  enabled: true
  port: 8123
  host: "127.0.0.1"
```
`ResourcePackManager.java:80-81` returns the configured host whenever non-blank, so the fallback/warning at `:82-89` never fires. The advertised URI is built at `:58` and sent with `.required(true)` at `:120`; the socket binds the wildcard address at `:147`.

**Analysis** — The URI is sent to *clients*, where `127.0.0.1` resolves to the player's own machine, not the server. The listener is reachable on the server's wildcard address, so this is a listener/URL mismatch. With `required(true)`, modern clients must load the pack to join normally; on any non-same-machine deployment the download fails (`FAILED_DOWNLOAD` / `INVALID_URL`), GUI icons are missing, and affected players are disconnected. Out of the box, a fresh server is broken for remote players.

**Recommendation** — Do not ship loopback with `enabled: true` + `required(true)`. Detect loopback/unspecified hosts: log a `SEVERE` warning and either send the pack as non-required or skip it until a routable host is configured. Bind the socket to the same address that is advertised.

**Verification** — Join from a second machine with default config: today the pack fails and join may be blocked. After the fix, either clients join with an optional pack or a clear log tells the admin to set `resource-pack.host`.

---

### AUDIT-06 — HIGH — Bug — Malformed pack host/port disables the whole plugin

**Evidence** — `ResourcePackManager.java:57-63`:
```java
int port = plugin.getConfig().getInt("resource-pack.port", 8123);
URI uri = URI.create("http://" + host() + ":" + port + "/shenanigans-resource-pack.zip");
info = ResourcePackInfo.resourcePackInfo().id(id).uri(uri).hash(sha1).build();
try {
    http = new HttpServer(port, zip);
    http.start();
} catch (IOException e) { ... }
```
`HttpServer` binds with `new InetSocketAddress(port)` at `:147`.

**Analysis** — `URI.create(...)` and `host()` run **before** the `try` that catches only `IOException`. A host with spaces/invalid characters makes `URI.create` throw `IllegalArgumentException`; an out-of-range port makes `new InetSocketAddress(port)` throw the same. Neither is an `IOException`, so both propagate through `enable()` → `onEnable()` (`ShenanigansCore.java:103`) and Bukkit disables the plugin. This contradicts the class's own graceful-degradation design (`:50-53,63-68` "custom icons disabled") and `ConfigAutoUpdater`'s "one broken file cannot disable the plugin" invariant.

**Recommendation** — Validate `port` into `1..65535` (fall back to 8123 with a warning), normalize/validate the host before building the URI, and extend the catch to `IllegalArgumentException` so a bad value disables only the pack.

**Verification** — Set `resource-pack.port: 99999` (or `host: "bad host"`), start the server: today the plugin fails to enable; after the fix it enables with custom icons disabled and a warning.

---

### AUDIT-07 — HIGH — Performance — Blocking SQL on main thread with a 30 s single-connection pool

**Evidence** — `PlayerStore.java:92-99,28-31`:
```java
public BattlegroundPlayer get(UUID uuid) {
    if (uuid == null) return null;
    BattlegroundPlayer player = cache.get(uuid);
    if (player != null) return player;
    player = load(uuid);          // repository.load(uuid) -> synchronous JDBC
    cache.put(uuid, player);
    return player;
}
```
Javadoc `:28-29`: *"Reads are served from cache; the first read loads the SQL row on the main thread."*
`Database.java:44-48`: `jdbc:sqlite:...?journal_mode=WAL&synchronous=NORMAL&busy_timeout=30000`, `config.setMaximumPoolSize(1);`.

**Analysis** — `get()` is reached from join/repair/HUD paths on the primary thread, so every player's first lookup is a synchronous `SELECT`. The pool has **one** connection and a **30-second** busy timeout. When the async flush (`PlayerStore.java:169`) holds that connection, a main-thread `load()` can block for seconds to 30 s. Paper's main thread must never block on I/O; a stalled tick freezes mobs, redstone and packets, and multiplies into visible TPS loss. The write path itself is correctly off-thread; only the read side is broken.

**Recommendation** — Pre-load online players asynchronously on `AsyncPlayerPreLoginEvent`/`PlayerJoinEvent` and apply the record on the main thread; or have `get()` return an unloaded placeholder immediately and complete the load async with a guarded sync hop. Raise `maximumPoolSize` to ~4 and lower `busy_timeout` to ~2000 ms with a fail-open default record on timeout.

**Verification** — `spark profiler -- ticks` during a 50-player mass join; confirm `PlayerStore.get/load` and `HikariDataSource.getConnection` show zero main-thread milliseconds.

---

### AUDIT-08 — HIGH — Performance — Unbudgeted real block writes from Shrine scars

**Evidence** — `malevolentshrine/Dismantle.java:504-533` (Wave runnable, `runTaskTimer(...,1L,1L)` at `:422`):
```java
for (int i = 0; i < SEGMENTS; i++) {          // SEGMENTS = 9
    Location l = segLoc(i);
    if (core[i].isValid()) core[i].teleport(l);
    if (glow[i].isValid()) glow[i].teleport(l);
    session.floor.scar(l.getX(), l.getZ(), true);       // -> real block write
    ...
}
```
`Floor.java:289-298` → `DomainFloor.java:892-910` → `block.setBlockData(material, false);` at `:905`.
`maxLiveWaves()` at `Dismantle.java:94-97` returns up to 10.

**Analysis** — Up to 10 waves × 9 segments = **90 real `setBlockData` calls per tick** from `scar()` alone, plus up to 90 cooldown writes in `Floor.tick()` (`:400-406`), ~180 display-entity teleports and ~150 particle spawns. These bypass `DomainFloor.tick()`'s 1,200-block/tick budget (`:614-616`) because `setSurface()` writes directly. Every real block write invalidates lighting/chunk state and is main-thread work — hundreds per tick from a purely cosmetic scar is a direct MSPT spike, worst during a PvP fight.

**Recommendation** — Route scars through `ClientBlockUtils.sendFakeBlocks` / the block-overlay manager (visual-only, like the rest of the shell), or add a per-tick scar budget and coalesce duplicate coordinates. Throttle display teleports (e.g. every 2nd tick) and cull particles by viewer distance as `Session.visualRangeSq()` already does for ambient effects.

**Verification** — `spark` during an active Shrine with 10 live waves; count `World#setBlockData` and `Entity#teleport` invocations per tick and confirm MSPT stays under 50.

---

### AUDIT-09 through AUDIT-11 — HIGH — Architecture

**AUDIT-09 — `SetupSession.ACTIVE` never cleared.** Evidence: `SetupSession.java:22` `private static final Map<UUID, SetupSession> ACTIVE = new ConcurrentHashMap<>();` holding `Player` + `ShenanigansCore` + `BukkitTask` refs; `cancelAll()` (`:77-79`) has zero call sites. `Analysis:` `cancelTasks(this)` stops the watcher but does not remove map entries, so on a PlugMan reload (explicitly tolerated at `ShenanigansCore.java:141-149`) the stale entry blocks a new `/bg setup` for that admin (`:43` sees `ACTIVE.containsKey`) and prevents GC of the old plugin classloader. `Recommendation:` call `SetupSession.cancelAll()` in `onDisable` before the task sweep; ultimately move `ACTIVE` into a plugin-owned `ArenaSetupService`. `Verification:` `grep -rn cancelAll src/main/java` shows the call in `onDisable`; start `/bg setup`, reload, re-issue `/bg setup` → starts fresh.

**AUDIT-10 — Technique Builders are god classes.** Evidence: six files at 803–1,387 LOC (≈5,989 total), e.g. `fuga/Builder.java:104-1387` with records `Part, Cube, Debris, Fling, Spire, VCube, Aura, Timed` (`:135-153`) and static `spawnArrow/tickWindup/release/tickFlight/createExplosion/burnAura/tickImpact/tickAmbient/cleanup` (`:254,330,487,614,720,890,899,1308,1335`). No test file references any Builder. `Analysis:` each Builder is a static namespace owning the entire visual lifecycle of one technique, with one huge public surface and no injectable seam — deep-module violations that make silent visual regressions likely. `Recommendation:` keep `Builder` as a thin facade; extract one `fx/` emitter per effect (`fuga/fx/ArrowFx`, `DebrisFx`, `FireCrownFx`, …), each owning its nested records and spawn/tick/cleanup, with a fake display sink for tests. `Verification:` each Builder < 300 LOC; characterization tests exist for one technique's transform stream.

**AUDIT-11 — Tests avoid the highest-risk code.** Evidence: 381 `@Test` total — battleground 312, core 48, origins 21; zero test files in `core/display`, `core/network`, `unlimitedvoid`, `wombprofusion`, all six technique packages, `listeners`, `resourcepack`. None of the top-15 largest classes has a test reference. `Analysis:` pure-logic areas are thoroughly covered, but ~9,000 LOC of rendering/gameplay state machines are uncharacterized; 381 tests give false confidence. `Recommendation (priority order):` (1) `core/network` overlay resend/revert; (2) projectile transitions via the AUDIT-21 base class; (3) domain `start→close` lifecycle; (4) `DisplayUtils`/`Barrier` transforms with a fake sink. `Verification:` each zero-test package has at least one test; break a branch in `PurpleProjectile.enter()` and confirm a test fails.

---

### AUDIT-12 through AUDIT-31 — MEDIUM (condensed: evidence → analysis → recommendation → verification)

**AUDIT-12 — Async restore completion can strand a domain floor.** `DomainFloor.java:1113-1128` uses `thenAccept` with a `try/catch` covering only *registration*. If the later `runTask` throws (mid-disable) or `restoreColumns` throws, `pendingAsyncRestores--` and `inFlightChunks.remove(key)` (`:1120-1121`) never run → `readyToFinish()` (`:1181-1183`) never true → `finishRestore`/`DomainRegistry.release` (`:1197`) never run; blocks stay overridden and the runnable ticks until disable. *Fix:* `whenComplete((chunk, throwable) -> { ShenanigansCore.runTask(() -> { try { if (throwable == null) restoreColumns(columns); } finally { pendingAsyncRestores--; inFlightChunks.remove(key); } }); });`. *Verify:* unload a chunk while a domain closes; assert counters reach zero.

**AUDIT-13 — `/bg reload` doesn't apply config.** `BattlegroundCommand.java:76-80` runs `new ConfigAutoUpdater(plugin).run()` + `manager().reloadConfig()` (which is only `arenaStore.load()`, `BattlegroundManager.java:577-580`). It never calls `plugin.reloadConfig()` or `GuiStyle.apply()` (`ShenanigansCore.java:101`, once). Edits to `interface.*`, `technique-fx.enabled` (`TechniqueFx.java:39`) and `resource-pack.*` have no effect, despite the config comments promising reload. *Fix:* call `plugin.reloadConfig()` and `GuiStyle.apply(getDataFolder())`; re-init the resource pack or correct the comments. *Verify:* change `interface.main-color`, `/bg reload`, open a GUI.

**AUDIT-14 — `instance` not volatile.** `ShenanigansCore.java:57` plain static, nulled at `:276`, read by the "shutdown-safe" helpers at `:303-326`, while async scheduling exists (`PlayerStore.java:169`). No JMM happens-before guarantees an async thread sees `null`; it may schedule against a disabled plugin → `IllegalPluginAccessException`. Some call sites bypass the helpers entirely (`Dismantle.java:422`, `Slashes.java:104`). *Fix:* `private static volatile ShenanigansCore instance;` and route direct calls through helpers. *Verify:* reload while domains tick; check logs.

**AUDIT-15 — `liveOwners()` built on every fluid event.** `DomainRegionGuard.java:176-189`, helper `:85-95`: Java evaluates arguments before the call, so `liveOwners()` allocates a `HashSet` and iterates sessions on every `BlockFromToEvent`/`BlockIgniteEvent` server-wide, even when `owner == null` and zero domains are active — contradicting the class javadoc (`:45-47`). *Fix:* `if (owner == null) return;` then look up the single session directly. *Verify:* JFR allocation profile with a fluid source and no domains → zero sets.

**AUDIT-16 — Time Cell punishes already-cancelled events.** `Enforcer.java:127` (onSwing), `:143` (onInteract), `:155` (onDamage) omit `ignoreCancelled` unlike `onMove:50`/`onJump:104`. `Rule24.attempt/forced` (`:381-396`) deal real damage and sever a limb, so a frozen player whose event was already cancelled by spawn protection/another domain is punished for an action that never happened. *Fix:* add `ignoreCancelled = true` to all three annotations. *Verify:* cancel a frozen player's interact in a stub plugin → no `sever`, no health loss.

**AUDIT-17 — Service locator.** `ShenanigansCore.getInstance()` appears 90 times in 52 files; 10 of 49 `core/` files use it (`Barrier.java:221,237,369,389`; `DomainFloor.java:755,1001`; `DomainBlockOverlayManager.java:169,271`; `Immobilize.java:27`; `TechniqueFx.java:38`). `core/` is documented origin-agnostic yet depends on the concrete plugin root; dependencies are invisible in constructors and core objects cannot be unit-tested without a live plugin. *Fix:* introduce `core/spi/SchedulerPort` + `LoggerPort` (or `PluginContext`) injected at composition; keep `getInstance()` only in the plugin root, listeners and command roots. *Verify:* `grep -rn 'import …ShenanigansCore' src/main/java/.../core` returns zero; core classes constructible in plain JUnit.

**AUDIT-18 — Two scheduling idioms.** 17 direct `runTask*(ShenanigansCore.getInstance(), …)` calls vs 16 uses of the null-safe helpers (`ShenanigansCore.java:304-326`); sites include `malevolentshrine/Session.java:244`, `BlueProjectile.java:165`, `fuga/Projectile.java:158`, `WorldSlashCast.java:203`, `Slashes.java:104,129,406,478`, `Dismantle.java:422,429`. Late scheduling after `instance = null` NPEs. *Fix:* inject `SchedulerPort` (AUDIT-17) and replace all 17, preserving `BukkitRunnable.cancel()` semantics. *Verify:* `grep -rn 'runTask.*ShenanigansCore.getInstance()'` returns zero; reload with active domains produces no NPE.

**AUDIT-19 — `GuiScreen.REQUIRED` not torn down.** `GuiScreen.java:44` static map; no `REQUIRED.clear()` call anywhere; onDisable "menu state" step (`ShenanigansCore.java:256-260`) clears `MenuManager`, `MenuLatch`, `GuiChatPrompt` but not `REQUIRED`. `wrap()` (`:207`) consults it → stale permission decisions after reload. *Fix:* add `GuiScreen.clearAll()` called in that teardown; fold into `MenuState`. *Verify:* assert map empty after `shutdown()`.

**AUDIT-20 — Domain Sessions are large untested orchestrators.** Shrine `Session.java:1-1047`, Void `:1-753`, Time Cell `:1-692`, Womb `:1-508` LOC; phase logic lives alongside buffs, displays and listeners; unlimitedvoid/wombprofusion have zero tests. Time Cell already shows the better split (`Stage.java:690`, `Film.java:426`, `Rule24.java:474`, `Enforcer.java`). *Fix:* keep `Session` as state machine ≤400 LOC and lifecycle owner; move phase visuals into `stage/fx` companions; add a shared `AbstractDomainSession` owning scheduling; one `start→close` test per domain. *Verify:* LOC thresholds; lifecycle tests assert tasks cancelled and blocks restored.

**AUDIT-21 — Duplicated projectile state machine.** `BlueProjectile.java:164-179`, `PurpleProjectile.java:178-191`, `red/Projectile.java:148-162`, `fuga/Projectile.java:157-164`, `PiercingBloodShot.java:173-188` repeat `start()` → `runTaskTimer(getInstance(),0,1)`, `run()` → `try { step(); } catch (Throwable) { log; clearWindupSlowness(); }`, plus `enum State`, `Vector direction`, `Location currentLocation`, `double traveled`, `clampInt`. *Fix:* `core/projectile/TechniqueProjectile extends BukkitRunnable` with final `start()`/`run()` (template method), abstract `step()`/`onFailure(Throwable)`, and a `SchedulerPort`. *Verify:* five `run()` bodies differ only by label; transition tests added.

**AUDIT-22 — `HIGH_DETAIL` mutable global.** `malevolentshrine/Builder.java:53` `public static boolean HIGH_DETAIL = true`, assigned `Session.java:188`, read at `Builder.java:298,436,585,700,705,747`. Correct today only because overlap prevention keeps a single Shrine live. *Fix:* make it a final field on the per-cast `Rig` (`Builder.java:171`) or a constructor parameter. *Verify:* two Shrine rigs with independent detail levels in a test.

**AUDIT-23 — No database backup.** `Database.java:77-95` defines a consistent `VACUUM INTO` `backupTo(File)` with **zero callers**; all progression/kit/shop data lives solely in `database.db`; `synchronous=NORMAL` (`:44-45`) is not power-loss durable. Config files get `.backup` before every rewrite but the database does not. *Fix:* call `backupTo` on enable before migrations and/or on an off-main-thread interval; consider `synchronous=FULL`; document the backup location. *Verify:* `database.db.backup` exists and passes `PRAGMA integrity_check;`.

**AUDIT-24 — `clearPlayer()` never called.** `ClientBlockUtils.java:37,45,158-172` static `PENDING`/`OVERLAYS` maps hold up to thousands of `BlockKey→BlockData` per player; `clearPlayer(UUID)` has zero call sites; `OverlayListener` (`:22-40`) has no `PlayerQuitEvent` handler and `BattlegroundManager.onQuit()` (`:639-647`) doesn't clear. `DomainBlockOverlayManager.hiddenFrom` (`:49`) is only cleared in `shutdown()`/revert. *Fix:* call `clearPlayer` on quit; add `forgetViewer(UUID)`. *Verify:* heap dump after 200 join/quit cycles shows no retained offline-UUID entries.

**AUDIT-25 — Cooldown cleanup never called.** `CooldownManager.java:19-22,110-127`: `clearExpired()` and `clearAll(uuid)` have zero callers; even `clearExpired()` never prunes `cooldownTotals`/`keyedCooldownTotals`. Entries accumulate for the plugin lifetime. *Fix:* call `clearAll(uuid)` on quit/death; schedule `clearExpired()` every ~20 min; extend it to the totals maps. *Verify:* map entry count plateaus after N unique players cast and quit.

**AUDIT-26 — `prepare()` scans ~12,800 columns in one tick.** `DomainFloor.java:377-451` (called from `Shrine Session.java:343-344`) loops radius², calling `getHighestBlockYAt` and appending 12+ change entries per column (100k+ objects) on the first tick of the sign phase. *Fix:* budget `prepare()` across ticks or use async `ChunkSnapshot` for heights with a main-thread hop for `BlockData`. *Verify:* spark at activation; no single tick > 50 ms.

**AUDIT-27 — Arena baseline I/O/scan on main thread.** `BattlegroundManager.java:290-292` → `ArenaBlockGuard.java:67-127`: each round end does synchronous `Files.readAllBytes`+decode; on missing/unreadable file it falls back to a full X×Z×Y `scan()` with `getChunkAt`/`getChunkSnapshot` (up to 2.56M lookups for a 100×100×256 arena). *Fix:* async load/scan with a `runTask` hop; incremental baseline updates from place/break events. *Verify:* spark around a round boundary with the baseline file deleted.

**AUDIT-28 — Overlay viewer sweep is quadratic-ish.** `DomainBlockOverlayManager.java:41,136-147,213-225,257-269`: a 10 Hz task walks every block of every overlay for every in-range viewer (~32,000 `HashSet.add` ops per 2 ticks for a 32 m overlay with 10 viewers), and `sendBlock` string-parses `BlockData` via `WrappedBlockState.getByString` per block on main thread. *Fix:* dirty queues for changed blocks; sweep every 10–20 ticks or on move threshold; cache `WrappedBlockState`. *Verify:* measure `tickViewers/sendTo` main-thread ms and packet rate.

**AUDIT-29 — Per-tick micro-task storms.** `Dismantle.java:404-429` polls a timestamp every tick then spawns a second timer; `Slashes.java:72-104,116-129` starts a task per blade and per scar; `Film.java:402-416` per severed limb — all repeating with no distance culling. *Fix:* `runTaskLater` for staggers; consolidate into the domain's existing 1-tick loop with an active-effects list; cull particles by `visualRangeSq()`. *Verify:* count active repeating tasks during a Shrine fight.

**AUDIT-30 — `shatter()` ignores write budget.** `DomainFloor.java:782-800` rebuckets all ~7,200 columns (48 m) into 24 tick buckets; each callback restores ~300 columns with no budget → 1,000-3,000 `setBlockData` calls in one tick, exceeding the documented 1,200/2,400 budgets. Normal collapse honours `budget`; shatter is the outlier. *Fix:* one budgeted `restoreLoadedSlice` iterator over fracture order across ticks; keep the watchdog (`:1139-1152`). *Verify:* spark during the 1.2 s fracture window; expect >1,200 writes/tick today.

**AUDIT-31 — Config recovery loses admin values.** `ConfigFiles.java:53-64` overwrites a corrupt file with the **bundled** copy (`.backup` holds the corrupt file), and `:75-76` writes truncate-in-place without temp + `ATOMIC_MOVE`. A crash mid-write leaves a truncated config that startup then "recovers" by discarding all customizations. *Fix:* restore from `.backup` first; write via temp + `ATOMIC_MOVE`. *Verify:* truncate `config.yml`, restart → previous backup restored.

---

### AUDIT-32 through AUDIT-50 — LOW (one-line evidence → fix → verification each)

- **AUDIT-32** `ResourcePackManager.java:108-125`: delayed 20-tick send captures `Player` with no `isOnline()` recheck → offline send / retained ref. *Fix:* recheck `isOnline()` or use `player.getScheduler().runDelayed`. *Verify:* join then disconnect within 1 s → no stack trace.
- **AUDIT-33** `ShenanigansCore.java:262` vs `:81-83`: unguarded `WorldEditBridge.setEnabled(false)` on disable can trigger `NoClassDefFoundError` on WorldEdit-less servers; swallowed by `runSafely` but logs spurious SEVERE. *Fix:* track `worldEditEnabled` and guard. *Verify:* stop server without WorldEdit → no teardown error.
- **AUDIT-34** `ResourcePackManager.java:40,73-75`; `ShenanigansCore.java:268-276`: `disable()` stops HTTP but leaves `enabled == true`; manager field never nulled, so `TechniqueHud.java:104` reads stale state. *Fix:* clear flag + null field.
- **AUDIT-35** `LifecycleListener.java:34-38`: no null guard on `getInstance()` while onDisable nulls instance+sessionManager (`:269,276`) → NPE for quit/death during PlugMan reload. *Fix:* `if (core == null || core.getSessionManager() == null) return;`.
- **AUDIT-36** `GuiChatPrompt.java:101-116,29,95-99`: async chat handler uses non-volatile static `plugin` (nulled `:97`) → `runTask(null,…)` on async thread; `pending.timeout().cancel()` (`:108`) mutates a main-thread task off-thread. *Fix:* capture `Plugin` in the `Pending` record; volatile field; defer cancel to main.
- **AUDIT-37** `BattlegroundListener.java:245-246`: checks `shenaniganscore.battleground.command.bypass`, declared nowhere (`Permissions.java:4-8`, `paper-plugin.yml:18-27`) → OP-only fallback, invisible in permission managers. *Fix:* declare as OP or delete the check.
- **AUDIT-38** `Immobilize.java:25-36` (≈41 callers incl. 37 `ActionLockListener` event types): no `activeSessions.isEmpty()` fast path. *Fix:* early return; cache per-tick locked set.
- **AUDIT-39** `CommandRegistrar.java:62-67`; `Cmd.java:85-95`: two-arg nodes only invoke the second handler; `validateNode()` (`:134-169`) doesn't enforce the documented invariant. Today `BalanceCommands` passes the same method to both so behaviour is correct. *Fix:* validate equivalence or invoke both.
- **AUDIT-40** `PlayerStore.java:69-90,192-201,243-250`: shutdown `wait(50)` loop has no deadline — a cancelled queued task leaves `activeWrites > 0` forever; `flushSync()` re-dirties failed writes but never retries. *Fix:* overall deadline + cancellation-aware decrement + explicit unflushed-UUID log.
- **AUDIT-41** `BattlegroundCommand.java:76-81`, `GlobalKitEditorScreen.java:66-75`: admin/GUI reload does file I/O and synchronous SQL on main thread. *Fix:* async then `runTask` hop.
- **AUDIT-42** `Shrine Session.java:876-878`, `Rule24.java:464,470`, `Womb Session.java:393,466`: re-parses MiniMessage and rebuilds `String.repeat` bars each HUD tick. *Fix:* cache `MiniMessage` + precompute fixed components; throttle ≤2 Hz.
- **AUDIT-43** `BattlegroundListener.java:182-197,449-455`: region-exit check runs every move event for every player and duplicates `ArenaEntryGuard.onMove` (`:105-124`). *Fix:* fuse handlers; add `ignoreCancelled`.
- **AUDIT-44** `DomainFloor.java:618-620,837-851`: `world.getEntities()` whole-world scan every 10 ticks during floor build. *Fix:* `world.getNearbyEntities(center, radius, radius, radius)`.
- **AUDIT-45** `Shrine Session.java:802-824,853-866`: ~360 particles/burst × 6/s ≈ 2,160/s with only partial culling. *Fix:* batch `spawnParticle` counts; precompute ring angles.
- **AUDIT-46** `Barrier.java:288,319,333`: `new Particle.DustOptions(...)` allocated every tick (~24+ points/tick) despite AGENTS.md cache rule; domain Sessions cache theirs. *Fix:* two `private static final` constants.
- **AUDIT-47** `Barrier.java:65,133-136,150-160`: `ready()` has no callers, `scanComplete` always true, `shatter()` has no `ready()` guard — the documented "shatter requires READY" invariant is unimplemented; `BarrierShatterAnimator.java:27-32` still claims fake blocks are restored. Harmless (shell visual-only). *Fix:* either add the guard + fallback, or update docs and delete the dead fields.
- **AUDIT-48** Build-wide: no Checkstyle/SpotBugs/ArchUnit; AGENTS.md says 378 tests / 87 suites vs measured 381 / 86. *Fix:* ArchUnit rules for the import conventions; update docs.
- **AUDIT-49** `ResourcePackManager.java:141,151-184`: HTTP daemon thread is correctly confined (`volatile running`, no Bukkit API) but the socket binds the wildcard while the URL advertises loopback (root of AUDIT-05). *Fix:* bind to the advertised address; covered by AUDIT-05.
- **AUDIT-50** `paper-plugin.yml:9-14`; `ShenanigansCore.java:266`: no `folia-supported` key and global `BukkitScheduler` used throughout. Correct for Paper/Leaf target, but undocumented. *Fix:* document "Paper/Leaf only; not Folia" or migrate to `GlobalRegionScheduler`/`RegionScheduler`/`EntityScheduler`/`AsyncScheduler` and add the key.

---

## 4. Redesign Proposal

The existing layering is **sound — do not rewrite it**. The problems are three structural defects layered on top: a service locator (`getInstance()`), static lifecycle state with no owner, and god-class visual builders. Migrate in six independently verifiable phases:

### Target package structure
```
com.hizeric.shenaniganscore
├─ ShenanigansCore                  composition root ONLY; getInstance() retained for legacy listeners
├─ core/
│  ├─ spi/            [NEW]  SchedulerPort, LoggerPort, PluginContext        → AUDIT-17/18
│  ├─ projectile/     [NEW]  TechniqueProjectile, ProjectileState            → AUDIT-21
│  ├─ ability/ config/ cooldown/ domain/ model/ network/
│  ├─ display/        → display/rig/, display/fx/  shared display primitives
│  ├─ session/        DomainSession orchestration; lifecycle hooks injected
│  └─ terrain/ sound/ particle/ util/
├─ battleground/
│  ├─ arena/          → ArenaSetupService [NEW] owns SetupSession.ACTIVE     → AUDIT-09
│  ├─ menu/           → MenuState [NEW] owns GuiScreen.REQUIRED + GuiSessions → AUDIT-19
│  └─ (unchanged: ability, command, combat, deploy, economy, indicator,
│       item, loadout, player, shop, storage)
├─ origins/jujutsukaisen/
│  ├─ domainexpansions/<domain>/
│  │    Ability, Session (state machine ≤400 LOC), stage/fx companions       → AUDIT-20
│  └─ techniques/<tech>/
│       Ability, Projectile, Builder (thin facade ≤300 LOC), fx/ emitters    → AUDIT-10
└─ listeners/  resourcepack/
```
Mirror this tree under `src/test`, adding the missing `core/display`, `core/network` and `origins/**` test packages (AUDIT-11).

### Migration path
1. **Lifecycle hotfix (low risk, do first):** force floor restore on shutdown (`AUDIT-01`); overlap check (`AUDIT-04`); dedicated teleport handlers (`AUDIT-02/03`); call `SetupSession.cancelAll()`, `GuiScreen.clearAll()`, `BattlegroundLock.clear()`, `ClientBlockUtils.clearPlayer`, `CooldownManager.clearAll/clearExpired` from the right lifecycle points (`AUDIT-09/19/24/25`); fix resource-pack host validation (`AUDIT-05/06`). This phase alone removes every CRITICAL/HIGH correctness issue.
2. **Ports (mechanical):** add `core/spi`, inject into the 10 core files using `getInstance()`, replace 17 direct scheduling sites with `SchedulerPort` (`AUDIT-17/18`), mark `instance` volatile (`AUDIT-14`). Verify the forbidden-import greps return zero.
3. **Performance budgets:** budget `prepare()`/`shatter()` block writes and Shrine scars (`AUDIT-08/26/30`); make first player load async and enlarge the pool (`AUDIT-07`); async baseline reload (`AUDIT-27`); consolidate micro-tasks and cull particles (`AUDIT-29/45`).
4. **Projectile base class:** extract `TechniqueProjectile`, migrate the five projectiles and `WorldSlashCast`, add transition tests (`AUDIT-21`).
5. **Builder/Session decomposition:** use `timecellmoonpalace/{Stage,Film,Rule24}` as the reference; extract one `fx/` emitter per effect; add characterization tests before each extraction (`AUDIT-10/20`).
6. **Guardrails:** ArchUnit/Checkstyle rules enforcing the AGENTS.md import conventions (`ChatColor` forbidden, no JJK in `core/`, no raw JOML, no `Thread.sleep`); reconciled test counts; explicitly document Paper-only platform support (`AUDIT-48/50`).

**Recommended library choices (only if you add them):** no new runtime dependency is needed for phases 1–6. If you later add a real SQL backend, use HikariCP with pool sizing tuned per Paper's database guide and NTP-synced clocks; if you add caching, Caffeine gives bounded, timed eviction for the cooldown/overlay maps — but the immediate leak fixes above need neither.

---

## 5. References

- [PaperMC Docs — Supporting Paper and Folia](https://docs.papermc.io/paper/dev/folia-support/) — `folia-supported: true` required for Folia.
- [PaperMC Docs — Scheduling](https://docs.papermc.io/paper/dev/scheduler/) — scheduler API mapping; `BukkitScheduler` assumes a single main thread; async tasks must hop back via the owning entity/region scheduler; `AsyncScheduler` cancels pending tasks on disable.
- [PaperMC Docs — How plugins work](https://docs.papermc.io/paper/dev/how-do-plugins-work/) — onLoad/onEnable/onDisable lifecycle; `/reload` causes classloader leaks, duplicate listeners, broken schedulers.
- [PaperMC Docs — Using databases](https://docs.papermc.io/paper/dev/using-databases/) — HikariCP (`HikariConfig`/`HikariDataSource`), NTP requirement, pool sizing, no I/O on the main thread.
- [DeepWiki — PaperMC/Folia Scheduler APIs](https://deepwiki.com/PaperMC/Folia/3.2-scheduler-apis) — `AsyncCatcher` wrong-thread `IllegalStateException("Asynchronous <reason>!")`; guarded operations.
- [Fumora Lab — Bukkit Scheduler sync/async guide](https://www.fumora.net/lab/bukkit-scheduler-sync-async-guide) — main-thread check behaviour and async rule.
- [SpigotMC — Guide to async IO](https://www.spigotmc.org/threads/guide-to-async-io.623371/) — async scheduling alone does not guarantee update ordering/correctness.
- [SpigotMC — Proper tear-down of plugins in onDisable()](https://www.spigotmc.org/threads/proper-tear-down-of-plugins-in-ondisable.258185/) — onDisable cleanup responsibilities.
- [PaperMC Issue #12766 — LifecycleEventManager unregisterEventHandler](https://github.com/PaperMC/Paper/issues/12766) and [Issue #786 — ASM ClassLoader leaks](https://github.com/PaperMC/Paper/issues/786) — unregistered listeners retain the generated classloader → metaspace leak.
- [Paper Plugin Development Best Practices](https://www.mcrpg.com/articles/paper-plugin-best-practices) — `PlayerMoveEvent` fires many times/second including head rotation; bail out early.
- [HikariCP (brettwooldridge)](https://github.com/brettwooldridge/HikariCP?tab=readme-ov-file) — connection-pool semantics and timer requirements.

---

*Report generated from a read-only audit of the working tree as of 2026-11-21. Every finding was required to carry an exact `file:line` and evidence; areas verified clean were deliberately omitted from the findings table rather than padded.*
