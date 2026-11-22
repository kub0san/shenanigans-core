# Local GUI test server — agent guide

How to stand up a throwaway Paper server for shenanigans-core, join it with the
user's Minecraft client, and capture the GUIs. Everything lives under `%TEMP%`,
never in the repo. Written 2026-11-19 from a working session.

## 0. Current setup (2026-11-23)

- **Target/main server: Paper 26.3 (Java 25)** — this is what the plugin is released for.
- **Primary local test server:** `C:\Tools\global-test-server-26.3`, Paper **26.3**, Java 25, port 25566, Craftwire installed. Use this for GUI work against the target. The older `C:\Tools\global-test-server` (1.21.11, Java 21, port 25565, ViaVersion) is legacy compatibility/bot testing only — run one at a time. See `C:\Projects\GLOBAL_TEST_SERVER.md` and `C:\Tools\global-test-server\README.md`.
- The old throwaway `%TEMP%\opencode\shenanigans-test-server` described below was **removed on 2026-11-22**. Do not recreate it: copy the built shadow jar into the global server's `plugins/` instead.
- From MC 26.1 Paper is deobfuscated and reobfuscation is unsupported; use Mojang mappings for 26.x work.

## 0b. Historical: former throwaway instance (REMOVED 2026-11-22)

- Directory: `C:\Users\marcus panget\AppData\Local\Temp\opencode\shenanigans-test-server`
- Server: `paper-1.21.11-132.jar` (PaperMC "fill" API v3; the old `api.papermc.io/v2` returns 410).
- Plugins in `plugins/`:
  - `packetevents-spigot-2.14.0.jar` — hard dependency (GitHub release).
  - `FastAsyncWorldEdit-Paper-2.16.2.jar` — arena setup needs it (GitHub release).
  - `ViaVersion.jar`, `ViaBackwards.jar` — **5.12.3-SNAPSHOT** from Modrinth; lets the
    user's Lunar Client **26.3** join this **1.21.11** server. ViaBackwards = newer
    client → older server; ViaVersion = required companion.
  - `shenanigans-core.jar` — copy `build/libs/shenanigans-core-1.0-SNAPSHOT-all.jar` here.
- `server.properties`: `online-mode=false`, `server-port=25565`, `level-name=shenanigans-test`,
  `gamemode=creative`, `spawn-protection=0`.
- `eula.txt`: `eula=true`.
- Native alternative: PrismLauncher instance **"uku's pvp modpack" (Minecraft 1.21.11 +
  Fabric 0.18.4)** avoids Via entirely. NOTE: this Prism build is the **"Cracked"**
  edition (offline account `kidgamer4321`) — it is the user's own setup, not something
  to install or reproduce silently. In practice the Via path was unstable (connection
  reset on item hover, client exit); the native instance is the more reliable target
  if the user approves its use.
- Prism quirks seen: a "No Update Available" modal must be dismissed (Enter) before
  clicks reach the main window; a "Tier-Tagger outdated" in-game warning needs "Ignore".
- Lunar 26.3 launched in this session needs ViaVersion + ViaBackwards **5.12.3-SNAPSHOT**
  from Modrinth; the server log confirms both load and enable on Paper 1.21.11.
- `ops.json`: offline UUIDs for the test names (server is online-mode=false, so the
  UUID is MD5 of `OfflinePlayer:<name>` → v3 UUID):
  - `qcart` → `4138b063-029f-3475-9c51-3c26abb72403`
  - `kidgamer4321` → `0ac818c8-8157-3b09-97a6-bcc484dbde8f`
  Compute with: `python -c "import uuid,hashlib; print(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:qcart').encode()).digest(), version=3))"`.
- Start: `Start-Process java -ArgumentList '-Xms1G -Xmx2G -jar "<dir>\paper-1.21.11-132.jar" nogui' -WorkingDirectory <dir> -RedirectStandardOutput <dir>\server.log -RedirectStandardError <dir>\server.err -WindowStyle Hidden`
- Verify in log: `PacketEvents ready`, `shenanigans-sqlite - Start completed`,
  `WorldEdit API provider: WorldEdit`, `Battleground enabled`, `Done`.
- Redeploy: rebuild with `.\gradlew.bat build`, copy the shadow jar over, **kill the
  old java process** (world lock!), then restart. Kill by:
  `Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'paper-1.21.11-132' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }`.

## 1b. Custom resource pack (added 2026-11-19)

- Source art lives in `resourcepack/` (bundled pixel art).
- `resourcePackZip` Gradle task zips `resourcepack/` into
  `shenanigans-resource-pack.zip`, embedded in the shadow jar at the root.
- `com.hizeric.shenaniganscore.resourcepack.ResourcePackManager` serves it from a
  tiny built-in `ServerSocket` HTTP responder (default port **8123**, bind
  `127.0.0.1`) and sends it via Adventure `ResourcePackRequest` with a SHA-1 hash.
- Config: `config.yml → resource-pack: { enabled, port, host }`. The pack is bundled
  in the jar, self-hosted, and sent as REQUIRED on join — declining or failing to
  download it prevents the player from joining. Pack status is logged only.
- `GuiTheme.model(material, "modelName", name, lore)` sets Paper's
  `DataComponentTypes.ITEM_MODEL` to `shenanigans:<modelName>`; vanilla material is
  the graceful fallback. 10 pixel icons (5 `ability_*` techniques, 3 `ultimate_*`
  domain techniques, plus `ability_catalog` and `ultimate_locked`; GUI buttons use
  vanilla materials)
  + 4 sound events. Canvas stays **vanilla stained glass** — no custom background
  art. The **action bar uses a single `shenanigans:hud` bitmap font** backed by one
  512x512 `hud.png` atlas (16x16 grid of 32x32 cells; `TechniqueGlyphs` +
  `TechniqueHud`), with plain text marks as the no-pack fallback. Codepoints run
  U+E000..U+E0FD: 11 technique blocks of 23 tiles each (ready, locked, then
  cooldown fill levels 0–20) plus one shared empty. Exactly three glyph tiles are
  rendered — one per slot — joined by a single space; **no slot numbers, gauges,
  text, or colours** appear in the with-pack path. Cooldown progress is a 21-level
  bar drawn under the icon inside the same tile, driven by
  `CooldownManager.getTotalSeconds` + `getRemainingMillis`. Pack format is `[75,0] → [999,0]` so
  1.21.11 and newer (including 26.3) load without warnings. The **HUB screen deliberately keeps vanilla
  item icons** — custom models are for abilities only; all other GUI buttons use vanilla materials.
- `TechniqueFx` emits a palette-matched particle burst on technique casts,
  excluding domain expansions.
- Rebuild: `./gradlew build` — the `resourcePackZip` task re-zips `resourcepack/`
  into the jar.

## 1. Two build traps already solved (don't reintroduce)

1. **Never relocate `org.sqlite`** in `build.gradle.kts`. `sqlitejdbc.dll` exports
   JNI symbols baked to `org.sqlite.core.NativeDB`; relocation breaks JNI mangling →
   `UnsatisfiedLinkError` → plugin disabled at enable. HikariCP relocation is fine.
   Alternative: declare `org.xerial:sqlite-jdbc:3.54.1.0` under Paper `libraries:`.
2. **Never touch `WorldEditBridge` unconditionally.** `ShenanigansCore.onEnable()`
   guards `WorldEditBridge.setEnabled(true)` behind `WorldEditSupport.isAvailable()`;
   otherwise the optional WorldEdit API classes are loaded and the plugin dies when
   FAWE/WorldEdit is absent.

## 2. GUI test flow (in-game)

1. User joins `localhost:25565` with **Lunar 26.3** (Via plugins bridge the protocol).
2. As OP, run `/bg admin setup` — it detects FAWE and hands over a wand (or user runs
   `//wand`). Select two corners (left + right click) → region, staging, spawn and
   snapshot save automatically.
3. `/bg join` → stages the player and opens the hub. Hub → Market / Loadout /
   Admin (OP) / Deploy. Admin → Rounds / Loadout editor. Confirm via RESET buttons.
4. Capture each screen and compare to the HTML preview at
   `%TEMP%\opencode\shenanigans-gui-preview.html` (exact slot layout + tooltips).

## 3. Screenshots on Windows (the non-obvious part)

- `capture.ps1` uses GDI `CopyFromScreen` — **copies the screen**, so an occluded window
  gives you whatever window is on top. The user's fullscreen editor covered Lunar and
  every "Lunar" capture was actually the editor. Don't trust it if windows overlap.
- `capture2.ps1` uses `PrintWindow(hwnd, hdc, PW_RENDERFULLCONTENT=2)` — renders the
  **target window's own buffer** even when fully occluded. This is the reliable one:
  `powershell -NoProfile -ExecutionPolicy Bypass -File capture2.ps1 -Title 'Lunar Client 26.3' -Out shot.png`
- Minecraft fullscreen: PrintWindow may return black for exclusive fullscreen; if so,
  ask the user to run borderless/windowed, or use the F2 in-game screenshot key and
  read `%APPDATA%\.minecraft\screenshots` (Lunar keeps its own screenshots dir too).
- NEVER capture the full desktop and read it back — it exposes the user's private
  documents. Capture a specific window only.

## 4. Input automation (`input.ps1`)

- `click x y`, `rclick x y` — OS-level mouse via `SetCursorPos` + `mouse_event`.
- `key '{ENTER}'` / `key 't'` / `type '/bg admin setup'` — `SendKeys`.
- `rect <title>` — largest matching visible window rect.
- Critical: synthetic clicks land on whatever is **topmost** at those coordinates.
  If the editor is in front of the game, clicks hit the editor. Only drive a window
  that is actually in the foreground (the fullscreen game is fine).
- Minecraft chat key is `t`, command key `/` (check `%APPDATA%\.minecraft\optionsLC.txt`).

## 5. Design source of truth

Plan: `C:\Users\marcus panget\.opencode\plan\battleground-gui-instrument-panel.md`.
Configurable palette lives in `config.yml` under `interface:` (`glass-material`,
`main-color`, default red), loaded by `battleground/menu/base/GuiStyle.java`.
Handoff note for the SQL work: `.scratch/battleground-rewrite/sqlite-natives-handoff.md`.

## 6. Gotchas seen in a real session

- Killing a running MC java process produces a Lunar "Unknown Java Crash 0xFFFFFFFF"
  dialog — dismiss it and relaunch Lunar; it's cosmetic, not a real game crash.
- Lunar's Versions/Modpacks page can make the launcher exit when clicked while occluded
  (input hits the wrong window). Don't blind-click that page; capture with PrintWindow first.
- Paper's "fill" API (`fill.papermc.io/v3/...`) is the working download API.
- Another agent may own `build.gradle.kts` / storage files; keep edits there minimal and
  announce them (handoff note).
