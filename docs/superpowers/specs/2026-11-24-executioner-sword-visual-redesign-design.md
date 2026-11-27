# Executioner's Sword — visual redesign (2026-11-24)

## Intent

The current `ExecutionerSwordEffect` looks unlike the rest of the plugin's display-entity FX.
In the smoke-test clip the execution reads as a red `X` on the floor, a chunky purple bar,
three unshaped sword icons and a small "VERDICT" label colliding with a nameplate; the
uppercut reads as a flat gold tile and a floating sword. Rebuild both variants with the plugin's
own minimal display-entity vocabulary — thin glowing block geometry, fake light and particles —
while keeping the existing gameplay identical. No JJK title sequences or glyph seals: this is a
Minecraft-origin weapon.

## Goals

- A clear focal point and readable beat structure in both variants.
- No giant near-camera item models; no flat crosses made of ground blocks.
- Reuse the neutral `core/display` helpers (`DisplayUtils`, `TransformUtils`, `TechniqueLighting`,
  `CameraShake`) rather than inventing a new FX stack or borrowing JJK technique helpers.
- Keep the 24-tick execution and the 10-tick uppercut; keep damage, threshold, cooldown and
  ownership untouched.

## Non-goals / anti-goals

- No resource-pack edits: no new textures, models, `item_model` definitions or atlas tiles.
  (Item art and HUD icons are a later, separate phase.)
- No gameplay retuning, no new commands, no changes to registration or migration.
- Anti-goal: anything that reads as vanilla blocks/items scaled up. The FX must be made of thin
  glowing geometry and particles — no named title, no Japanese glyphs, no domain-expansion seals.

## Constraints

- Paper 1.21.11 API, runtime 26.3; main-thread only, `BukkitRunnable` timelines, no `Thread.sleep`.
- Every spawned entity registered and removed in `cleanup()`; `setPersistent(false)` via
  `DisplayUtils`; keep `viewRange` small and `brightness(15)` like the other techniques.
- Live timelines are tracked in `ExecutionerSwordEffect`; `shutdown()` finishes each one from
  `onDisable()` before `cancelTasks`. `finish()` is idempotent and also runs on every abort and
  natural end, so no frozen target or leftover display survives a reload.
- The action bar is a single slot; arm feedback must be one short line, not chat spam.

## Approach (chosen: A — in-engine choreography)

Keep the FX entirely in Java. The alternative (custom `item_model` + texture for a curved slash
arc) was rejected for now because the resource pack is intentionally frozen until icons and the
HUD are finished.

### Uppercut (10 ticks)

- Replace the `GOLD_BLOCK` tile and the 2.5× netherite sword with a `Slashes.blade()`-style
  two-layer blade: thin white-hot core + ~5× wider red-glass glow, sweeping from −34° to +34°
  across the target's front over 4 ticks, then thinning to 0.01 before removal.
- Add a short `Slashes.cross()` accent at contact and a rising crimson dust helix around the
  target for 8 ticks.
- Add a small local camera-shake utility (`core/display/CameraShake.java`, self-contained;
  Malevolent Shrine's `Shake` stays untouched) and kick it once on hit.

### Execution (24 ticks)

Drop the `CRIMSON_ROOTS` plates, the three orbiting 2.5× sword `ItemDisplay`s, the 3× downward
"guillotine" sword and the plain "VERDICT" text. Replace with:

1. **t0 — sigil ring:** 16 thin glowing segments (alternating white-hot core / red glass) in a
   flat circle of radius ~1.8 around the target, rotating slowly. This is the "court" motif,
   built from geometry instead of crosses.
2. **t0 — light:** a short fake `Material.LIGHT` burst at the anchor via `TechniqueLighting`
   (or bright displays when lighting is unavailable), so the ring reads in daylight.
3. **t4 — blades:** three thin converging blades (white core + red glow, `teleportDuration(1)`)
   orbiting radius 2.4 → 0 over 12 ticks — the "gathering the sentence" beat.
4. **t12 — the cut:** one horizontal white-hot blade with a red glow through the target's chest,
   built from thin glowing block displays, not an item display.
5. **t14 — kill:** `resolveKill()` fires with sound and a camera kick. No ability name, title
   sequence or text display — the beat reads from the geometry alone.
6. **t16 — sever:** 12 thin shard displays fling outward (radius 0 → 3), each vanished with
   `BlockDisplayOps.shrink()`; ring contracts, camera shake.
7. **t18–24 — dissolve:** all remaining displays fade/shrink; `finish()` removes everything,
   deregisters the timeline and restores the target's velocity and no-damage ticks as today.

Layered sound beats stay roughly where they are, retimed to the new beats (gather → cut →
kill → sever).

### Arm feedback

`ExecutionerSwordHitListener.arm()` sends a single `sendActionBar` line instead of
`sendMessage`, so repeated debug casts do not fill chat.

## Files expected to change

- `origins/minecraft/weapons/executionsword/ExecutionerSwordEffect.java` (rewrite both timelines).
- `core/display/CameraShake.java` (new, small, self-contained).
- `origins/minecraft/weapons/executionsword/ExecutionerSwordHitListener.java` (action bar).
- `ShenanigansCore.java` (finish live cutscenes before `cancelTasks` on disable).
- `src/test/.../ExecutionerDefaultsTest.java` (pin the 24-tick constant / any new public constants).
- `src/test/.../ExecutionerVisualCleanupTest.java` (no title/glyph FX, unchanged gameplay numbers,
  cleanup on every exit path and on disable).
- This design doc.

## Verification

`./gradlew.bat build`; deploy the shadow jar to the 26.3 test server; cast both variants with the
existing bots and capture a short clip; compare against the current one for readability, absence
of giant item models, clean cleanup, and no leftover displays after the timeline.
