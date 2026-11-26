# Craftwire recorder — agent guide

How to record a **video** of a Paper 26.3 test server with Craftwire's `record`
tool. Written 2026-11-24 after a session where the first three recordings came
out static. Complements `craftwire-client-capture.md` (still images and HUD).

The model cannot watch a video. Use `screenshot` for what the agent needs to see;
use `record` for footage the human partner will watch.

---

## 0. Prerequisites

1. Shared Craftwire hub listening on `127.0.0.1:7777` — leave it running.
2. Paper 26.3 server running (see `C:\Projects\GLOBAL_TEST_SERVER.md`).
3. **ffmpeg available to the hub.** `record` answers `FFMPEG_NOT_FOUND` without
   it. Craftwire checks `PATH`, the usual install folders, and
   `CRAFTWIRE_FFMPEG`. On this machine there is no system ffmpeg; a bundled build
   was copied to `C:\ffmpeg\bin\ffmpeg.exe` (also `%LOCALAPPDATA%\Microsoft\WinGet\Links\ffmpeg.exe`).
   To regenerate it:

   ```powershell
   python -m pip install --target "$env:TEMP\opencode\ffmpegtools" imageio-ffmpeg
   # binary: $env:TEMP\opencode\ffmpegtools\imageio_ffmpeg\binaries\ffmpeg-win-x86_64-v9.0.2.exe
   ```

4. A hidden client (bots have no renderer and cannot be recorded):

   ```js
   await tools.craftwire.client_process({
     action: "start", server: "127.0.0.1:25566", version: "26.3",
     username: "AgentView", lite: true, timeoutMs: 240000
   });
   ```

   Accept the required resource-pack screen if it appears (`gui_read` →
   `gui_action({action:"click_widget", widget:"2"})`). Whitelist the username.

---

## 1. Basic recording

```js
await tools.craftwire.record({
  instance: "client-1",
  savePath: "C:\\Users\\marcus panget\\AppData\\Local\\Temp\\opencode\\showcase.mp4",
  preset: "light",          // light = 720p/30fps, cheap; high/max for final cuts
  hud: true, chat: false,   // hud:false hides hotbar/crosshair like F1
  durationMs: 4000,         // auto-stop; omit and call record {action:"stop"} manually
  wait: false
});
// ... trigger the thing being showcased here ...
await tools.craftwire.record({instance: "client-1", action: "stop"});
// or record {action:"status"} to read the last result without stopping
```

Result includes `savedPath`, `durationMs`, `fps`, `frames`, `duplicatedFrames`,
`droppedFrames`, `sizeBytes`. Vertex: `duplicatedFrames: 0` means the game
actually rendered each frame.

---

## 2. Camera — the part that usually goes wrong

`record` films a **fixed render camera**, not a tracked player. If the subject
leaves the camera's view, the footage is empty. Keep the camera near a loaded
chunk: Craftwire warns `Camera is N blocks from the player; chunks beyond render
distance are not drawn` — treat that warning as a failed take.

- `camera {action:"set", x,y,z,yaw,pitch}` — static vantage point.
- `camera {action:"frame_entity", entity:"AgentView"}` — frames the entity once
  (verified useful for stills).
- `camera {action:"path", keyframes:[{t,x,y,z,yaw,pitch},...], interpolation:"linear", ease:"none"}`
  — moves the camera over time; use for a chase shot.
- `camera {action:"reset"}` / `freecam_off` — restore the player view. Note that
  a stray `look_at` from an earlier call can pin the camera far away and make
  every later screenshot identical; reset before a take.

For a fast ability (a dash lasts well under a second), per-shot screenshots are
~500 ms each — too slow. Use `record` for motion, and take a separate `screenshot`
for a single clean frame.

---

## 3. Battleground-specific trap: round resets

This is why the 2026-11-24 recordings were static. The arena ends its round and
teleports deployed players back to the hub. The viewer client leaves the arena
while the camera stays behind, so the recorder films unloaded chunks (a flat
gray void) for the rest of the take.

Before each take:
- verify positions with `server_eval`, e.g.
  `player("Observer").getLocation()` and `player("Caster").getLocation()`;
- if a reset happened, re-stage/deploy the viewer (`bg join` → click DEPLOY) and
  teleport it back near the camera;
- re-check the camera-distance warning;
- keep takes short (3–5 s) and trigger the ability immediately after `record start`.

---

## 4. Verify the take before showing anyone

Extract a few frames and compare sizes — identical sizes mean a static video:

```powershell
$ff = "C:\ffmpeg\bin\ffmpeg.exe"
$src = "<savedPath>"
foreach ($t in 0.2, 0.6, 1.0, 1.4) {
  & $ff -loglevel error -i $src -ss $t -frames:v 1 -q:v 3 "check_$t.jpg"
}
Get-ChildItem check_*.jpg | Select-Object Name,Length
```

Then read the images with the `read` tool. If they are all byte-identical, the
take failed — re-stage the viewer and try again.

---

## 5. Cleanup

```js
await tools.craftwire.record({instance: "client-1", action: "stop"}); // if still running
await tools.craftwire.client_process({action: "stop", all: true});
await tools.craftwire.server_process({action: "stop"});
```

Leave the shared hub on port 7777 up for other sessions.
