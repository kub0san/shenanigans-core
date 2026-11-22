# Arena rewrite integration — design (2026-11-23)

## Intent / why

You rewrote 11 arena-system classes and dropped them in
`C:\Users\marcus panget\Pictures\hizeric\downloads`. They are drop-in replacements
(same packages, same public signatures) carrying hardening fixes: expiring entry
tokens with rate-limited denials, normalized/copy-safe bounds, coordinate-based
containment, atomic `arena.yml`/schematic saves, tracked-and-released prewarm chunk
tickets, bounding-box entity sweeps, cached WorldEdit probes/streams, and live
restore-animation settings read from `arena.restore.animation.*`.

The one behavioural change that needed a decision was restore progress UI. **Choice B**:
take the hardening, but keep the existing boss-bar progress and leave the action bar
free for the technique HUD (AGENTS.md: "the action bar is a single slot").

## Goals

- Integrate all 11 rewritten classes into the source tree.
- Preserve the current restore progress UX: boss bar shows progress, action bar is
  not used by default.
- Add the new optional config keys without breaking existing config files.
- Release prewarm chunk tickets on shutdown.
- Keep `./gradlew build` (compile + JUnit + shadow jar) green.

## Non-goals / anti-goals

- No new commands, no gameplay-constant changes, no round/shop/economy changes.
- No config `VERSION` bump — the new keys are not in the obsolete-key list, so the
  auto-updater will not strip them.
- No reworking of other callers; existing call sites already match the new APIs.
- Do not reintroduce an action-bar progress bar as the default.

## Constraints

- Java 25, Paper API, single-module Gradle project.
- Action bar is a single shared slot; restore progress must not clobber the HUD.
- Share the change batch: read everything first, then write, then verify once
  (AGENTS.md convention).
- Write UTF-8 explicitly on Windows; PowerShell defaults corrupt non-ASCII.

## Approaches

- **A — Take the downloads verbatim.** Cleanest swap. Rejected: `RestoreAnimator`
  deletes the boss bar and defaults action-bar progress to on, which conflicts with
  the single-slot HUD constraint.
- **B — Take the hardening, keep the old progress UX. (chosen)** Ten files copied
  as-is; `RestoreAnimator` adapted to retain boss-bar progress, and the
  `action-bar` config default flipped to `false`. Costs one extra edit; keeps the
  HUD untouched.

## The decided how

1. Copy 10 files verbatim into their matching paths under
   `src/main/java/com/hizeric/shenaniganscore/battleground/arena/`
   (nine) and `arena/restore/` (`ArenaRestoreEngine`).
2. `ArenaRuntimeConfig`: change only the `EMPTY` default for
   `restoreAnimationActionBar` from `true` to `false`; update the javadoc example.
   All other new keys keep your values (`enabled=true`, `particles-per-chunk=18`,
   `sounds=true`).
3. `RestoreAnimator`: start from your file, then re-add the boss bar —
   `bossBar` field back on `Settings`, restore the create/update/"Deployment ready"/
   remove-all calls, re-add `BarColor`/`BarStyle`/`BossBar` imports. Keep the new
   `Supplier<Settings>` live-reload path and `Settings.from(ArenaRuntimeConfig)`.
   `Settings.defaults()` returns the old 8-field values
   `(true, 18, true, 160, true, false, true, 64)`; `from()` sets `bossBar=true`
   and takes `actionBar` from config (default false). Boss bar removal stays at the
   top of `onFinished()` before the early return.
4. `src/main/resources/battleground/config.yml`: add the `arena.restore.animation`
   block (`enabled: true`, `particles-per-chunk: 18`, `sounds: true`,
   `action-bar: false`).
5. `ConfigAutoUpdater.BATTLEGROUND_ALLOWED_KEYS`: add the four new keys so they are
   explicitly protected from the obsolete-key sweep.
6. `BattlegroundManager.disable()`: call `arenaStore.releasePrewarm()` so injected
   chunk tickets are handed back on shutdown/reload. `/bg reload` already reaches
   `prewarmNextMap()`, which releases first.

## What's left to the builder

- Snapshot the pre-edit state (`.snapshots/arena-rewrite-before/`) per the parallel-
  session convention, then apply the batch above.
- Run `.\gradlew.bat clean build`; fix any compile/test failure before reporting done.
- In-game smoke test on the 26.3 server is optional; these are internal fixes with no
  new commands or visuals, but restoring a map once to see the boss bar is cheap.
