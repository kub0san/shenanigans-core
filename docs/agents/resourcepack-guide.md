# Resource Pack Guide — shenanigans-core

This is the single source of truth for the custom resource pack. It is written for an
art-design agent who needs to produce or replace art without reading Java. Follow it
exactly; the Java code and the JSON descriptors depend on these contracts byte-for-byte.

---

## 1. Pack overview

The pack is **bundled in the jar**, **self-hosted by the plugin** over a tiny HTTP
responder, and **pushed as REQUIRED on join** (declining or failing to download it
prevents the player from joining). There is no manual `server.properties`
resource-pack setting and no player chat prompt — pack status is logged only.

- **Font id:** `shenanigans:hud` (NOT `infuse:effects`, NOT `shenanigans:techniques`)
- **Atlas file:** `resourcepack/assets/shenanigans/textures/font/hud.png` (512×512, 16×16 grid of 32×32 cells)
- **Font JSON:** `resourcepack/assets/shenanigans/font/hud.json` (bitmap provider, `ascent: 24`, `height: 32`)
- **Pack root:** `resourcepack/pack.mcmeta` + `resourcepack/pack.png` (64×64)
- **Build:** Gradle `resourcePackZip` zips `resourcepack/` → `build/generated/resourcepack/shenanigans-resource-pack.zip`, embedded in the shadow jar at the root.

The HUD is a **pure-visual, three-tile action bar**: one tile per slot (held item,
rail slot 1, rail slot 2). With the pack on, the bar shows only three glyph tiles
joined by a single space — **no numbers, letters, colours, gauges, or side text**.
Without the pack, plain text marks are used as the fallback.

Item textures/models are **phase 2** (out of scope for the HUD slice). Do not touch
`assets/shenanigans/textures/item/**`, `assets/shenanigans/models/item/**`, or
`assets/shenanigans/items/**` unless explicitly asked.

---

## 2. Pack format (`pack.mcmeta`)

```json
{
  "pack": {
    "description": "Shenanigans Core – 1.21.11+ resources",
    "min_format": [75, 0],
    "max_format": [999, 0]
  }
}
```

- `pack_format` **75** = Minecraft 1.21.11. The range `[75,0] → [999,0]` covers
  1.21.11 through 26.3+ without warnings. `supported_formats` is intentionally
  absent — it was removed in 1.21.9 and its presence makes the strict metadata
  codec reject the pack on 26.x clients.
- `pack.png` must be **64×64 RGBA** (standard pack icon).

Do not change these values. Do not delete these files — a missing `pack.mcmeta`
makes the entire pack invalid, and because the pack is required, clients will be
kicked on join.

---

## 3. Build flow

```
resourcepack/  ──►  Gradle resourcePackZip  ──►  build/generated/resourcepack/shenanigans-resource-pack.zip
                                                          │
                                                          ▼  processResources.from(resourcePackZip)
                                              shadow jar (root: shenanigans-resource-pack.zip)
                                                          │
                                                          ▼  ResourcePackManager.loadPack()
                                              HTTP responder (port 8123) + SHA-1
                                                          │
                                                          ▼  Adventure ResourcePackRequest (required=true)
                                                          player
```

- Run `.\gradlew.bat build` to rebuild. The shadow jar lands at
  `build/libs/shenanigans-core-1.0-SNAPSHOT-all.jar`.
- The plugin reads the zip from its own classpath (`/shenanigans-resource-pack.zip`)
  at runtime — no external file path.
- **Never** add a generator script to the repo (Task 6 removed them). Generate art
  with whatever tool you like, but leave no scripts behind. Use a temp folder
  outside the repo (e.g. `C:\Users\<you>\AppData\Local\Temp`) if you need one.

---

## 4. HUD atlas contract (THE critical part)

### 4.1 `hud.png` — 512×512, 16×16 grid of 32×32 cells

- **Grid:** 16 columns × 16 rows = 256 cells. 254 are used; 2 (bottom-right) may be
  blank/transparent.
- **Cell addressing:** codepoint `0xE000 + n` → column `n % 16`, row `n / 16`,
  pixel origin `(col*32, row*32)`. Left-to-right, top-to-bottom.
- **Per-technique blocks of 23 cells**, starting at these codepoints:
  ```
  0xE000, 0xE017, 0xE02E, 0xE045, 0xE05C, 0xE073, 0xE08A, 0xE0A1,
  0xE0B8, 0xE0CF, 0xE0E6
  ```
  Within each block:
  - **offset 0** = ready state
  - **offset 1** = locked state
  - **offsets 2..22** = cooldown fill levels 0..20 (offset 2 = empty bar, offset 22 = full bar)
- **Shared empty glyph:** `0xE0FD` (the last used cell).
- **11 techniques × 23 tiles = 253 + 1 empty = 254 used cells.**

### 4.2 Each 32×32 tile

- **Cooldown/progress bar is drawn UNDER the icon inside the same tile.** The icon
  occupies the upper band (~y 5–19); the bar frame sits at the bottom (~y 24–28).
- **Tiles have framing** — a two-tone border (near-black outer, mid-grey inner)
  around a dark panel. Every tile uses the same frame so the bar is the only
  varying element.
- **NO numbers, letters, or text anywhere in the atlas.** Glyphs are pure shapes.
- **Placeholder art may be simple coloured geometry**, but the 23 tiles of a block
  MUST be distinguishable: level 0 = empty bar, level 20 = full bar, and each
  level visibly differs. Technique identity comes from both a distinct **colour**
  and a distinct **shape** per technique.
- Locked tiles show a greyed icon with a diagonal slash. Ready tiles show a full
  bright icon.

### 4.3 `hud.json` — bitmap font provider

```json
{
  "providers": [
    {
      "type": "bitmap",
      "file": "shenanigans:textures/font/hud.png",
      "ascent": 24,
      "height": 32,
      "chars": [
        "\uE000\uE001...\uE00F",
        "...16 rows total...",
        "\uE0F0\uE0F1...\uE0FD"
      ]
    }
  ]
}
```

- `type`: `"bitmap"` (required).
- `file`: `"shenanigans:textures/font/hud.png"` (namespace:path, no leading slash).
- `ascent`: `24` (fixed).
- `height`: `32` (fixed).
- `chars`: array of **16 strings**, each up to 16 characters. Row 0 = codepoints
  E000..E00F, row 1 = E010..E01F, …, row 15 = E0F0..E0FD (14 entries; the
  2 spare cells E0FE/E0FF are simply omitted or blank). Codepoints are escaped as
  single-backslash `\uXXXX` (valid JSON). Total = 254 codepoints in ascending order.

**Do not reorder, skip, or duplicate codepoints.** The Java side maps codepoint N to
tile N by index — any mismatch renders the wrong art.

---

## 5. Java-side contract (read-only for the art agent)

The art agent does not edit Java, but must know these invariants so the art matches:

- `TechniqueGlyphs.FONT = "shenanigans:hud"` — the font id the renderer references.
- `TechniqueGlyphs.BLOCK_STARTS` = the 11 start codepoints above.
- `TechniqueGlyphs.READY = 0`, `LOCKED = 1`, `COOLDOWN = 2..22`, `EMPTY = '\uE0FD'`.
- `TechniqueGlyphs.index(id.toString())` maps a technique id to its block start.
  The 11 tabled ids: `jjk:blue`, `jjk:red`, `jjk:fuga`, `jjk:piercing_blood`,
  `jjk:purple`, `jjk:world_slash`, `jjk:malevolent_shrine`, `jjk:unlimited_void`,
  `jjk:time_cell_moon_palace`, `jjk:womb_profusion`, `minecraft:dash_sword`.
- `ActionBarView.render()` emits exactly 3 icon fragments joined by one space.
  With the pack on, each fragment is `<font:shenanigans:hud>` + the codepoint.
- **No slot numbers, gauges, text, or colours** in the with-pack path.

---

## 6. Item model system (phase 2 — context only)

Item textures and models are **not** part of the HUD slice but follow the same
pack conventions:

- `assets/shenanigans/items/*.json` — item definitions (model references).
- `assets/shenanigans/models/item/*.json` — model descriptors pointing at textures.
- `assets/shenanigans/textures/item/*.png` — item textures (32×32, same as icons).
- GUI buttons use vanilla materials; custom models are for abilities only.
- The canvas stays vanilla stained glass — no custom background art.

**Do not modify these unless explicitly asked.** They are out of scope for the HUD.

---

## 7. Art rules (do / don't)

### DO
- Keep every tile exactly 32×32.
- Keep the frame consistent across all tiles (same border colours/width).
- Draw the progress bar under the icon, inside the same tile.
- Use distinct colours AND shapes per technique (11 techniques → 11 distinct
  colours + 11 distinct shapes).
- Make cooldown levels monotonically visible (level 0 empty → level 20 full).
- Keep locked tiles visually distinct (greyed + diagonal slash).
- Name files exactly as specified (`hud.png`, `hud.json`).
- Verify with `.\gradlew.bat build` and inspect the zip/jar.

### DON'T
- Don't add numbers, letters, or text to any tile.
- Don't move or reorder codepoints — the Java side indexes by position.
- Don't change `ascent` (24) or `height` (32).
- Don't change the font id (`shenanigans:hud`).
- Don't change `pack_format` (75) or delete `pack.mcmeta`/`pack.png`.
- Don't add generator scripts to the repo.
- Don't touch item textures/models unless asked (phase 2).
- Don't use `git add -A` — the repo has unrelated untracked changes.

---

## 8. Verification checklist

Before declaring art work done:

1. **PNG:** `hud.png` is 512×512, RGBA, 16×16 grid of 32×32 cells.
   ```python
   from PIL import Image
   im = Image.open("resourcepack/assets/shenanigans/textures/font/hud.png")
   assert im.size == (512, 512)
   ```
2. **JSON:** parses as valid UTF-8 JSON; `chars` has 16 rows; flattened codepoints
   are exactly `U+E000..U+E0FD` ascending; `ascent==24`, `height==32`,
   `file=="shenanigans:textures/font/hud.png"`.
3. **No stale references:** grep for `techniques_`, `gauge_`, `techniques.json`,
   `empty.png`, `U+FFFF`, `U+FFFE`, `U+FFFD` in `*.java`, `*.yml`, `*.json`,
   `*.kts` — should be zero.
4. **Build:** `.\gradlew.bat build` succeeds.
5. **Zip/jar:** both `build/generated/resourcepack/shenanigans-resource-pack.zip`
   and the shadow jar's embedded copy contain
   `assets/shenanigans/font/hud.json` and `assets/shenanigans/textures/font/hud.png`,
   and none of the deleted HUD assets.
6. **In-game:** with the pack, the action bar shows 3 glyph tiles with no text;
   without the pack, plain marks appear. Cooldown fills the bar 0→20.

---

## 9. Step-by-step: replace placeholder tiles with real art

1. **Read this guide** and the atlas contract (§4) fully.
2. **Open the existing `hud.png`** in your image editor (512×512). Keep the grid
   aligned — each cell is 32×32, cell N at `(N%16*32, N//16*32)`.
3. **For each of the 11 techniques**, replace the 23 placeholder tiles (ready,
   locked, cooldown 0..20) with real art. Use the codepoint table in §4.1 to
   find the right cell. Keep the frame; replace only the icon + bar.
4. **Replace the shared empty tile** at `0xE0FD` if desired.
5. **Do not change `hud.json`** unless you added/removed codepoints (you shouldn't).
6. **Run the verification checklist** (§8).
7. **Commit only the changed asset files** by explicit path:
   ```
   git add resourcepack/assets/shenanigans/textures/font/hud.png
   git commit -m "art(hud): replace placeholder tiles with real art"
   ```
   (If you also changed `hud.json`, add it explicitly too.)
8. **Do not** use `git add -A` — the repo has unrelated untracked changes.

---

## 10. Quick reference: codepoint table

| Block | Start | Ready | Locked | Cooldown 0..20 | Technique |
|---|---|---|---|---|---|
| 0 | E000 | E000 | E001 | E002..E016 | (technique 0) |
| 1 | E017 | E017 | E018 | E019..E02D | (technique 1) |
| 2 | E02E | E02E | E02F | E030..E044 | (technique 2) |
| 3 | E045 | E045 | E046 | E047..E05B | (technique 3) |
| 4 | E05C | E05C | E05D | E05E..E072 | (technique 4) |
| 5 | E073 | E073 | E074 | E075..E089 | (technique 5) |
| 6 | E08A | E08A | E08B | E08C..E0A0 | (technique 6) |
| 7 | E0A1 | E0A1 | E0A2 | E0A3..E0B7 | (technique 7) |
| 8 | E0B8 | E0B8 | E0B9 | E0BA..E0CE | (technique 8) |
| 9 | E0CF | E0CF | E0D0 | E0D1..E0E5 | (technique 9) |
| 10 | E0E6 | E0E6 | E0E7 | E0E8..E0FC | (technique 10) |
| — | E0FD | — | — | — | shared empty |

Technique index → id mapping (for reference):
0 = `jjk:blue`, 1 = `jjk:red`, 2 = `jjk:fuga`, 3 = `jjk:piercing_blood`,
4 = `jjk:purple`, 5 = `jjk:world_slash`, 6 = `jjk:malevolent_shrine`,
7 = `jjk:unlimited_void`, 8 = `jjk:time_cell_moon_palace`,
9 = `jjk:womb_profusion`, 10 = `minecraft:dash_sword`.