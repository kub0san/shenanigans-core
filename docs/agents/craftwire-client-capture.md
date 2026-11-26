# Craftwire client capture — agent guide

How an agent joins the Leaf 26.3 test server with a **hidden Craftwire client** and
captures the HUD / ability screens. Written 2026-11-24 from a working session; updated
for the `TestServer.ps1`-managed server (offline/cracked, whitelist off).

A **bot** cannot do this: plugin bots have no renderer, and java/bedrock bots are
protocol-only. Only a `client` instance can `screenshot`, `hud_read`, or `gui_read`
the way a player sees it. Use the tools under `tools["craftwire"]` (via `execute`).

---

## 0. Current live state

| Thing | Value |
|---|---|
| Hub | `http://127.0.0.1:7777/mcp` (shared, token in `~/.craftwire/http.json`) — leave it running |
| Server folder | `C:\Tools\global-test-server-26.3` |
| Game port | `25566`, offline/cracked, **whitelist off** (any username can join) |
| Capture identity | `AgentView` (offline UUID `9f405998-7c7d-33c3-9633-da8ed65bc604`); qcart remains the canonical player identity |
| Server instance id | `server-5` (changes on every restart) |
| Client instance id | `client-1` (name `AgentView`) |

The client's game dir is `~/.craftwire/client/instances/AgentView`. The 26.3 client
was already downloaded (~246 MB), so later starts are fast.

---

## 1. Prerequisites

1. Hub listening on 7777. If not: `npx -y craftwire@0.11.2 serve` from the server folder.
2. Server running. If not, start/restart it through the **one canonical path** — do not
   hand-launch a jar (the retired build-257 Paper jar is not the launch target;
   `server.jar` is Leaf):

   ```powershell
   cd C:\Tools\global-test-server-26.3
   .\TestServer.ps1 restart
   ```

   `restart` scopes the stop to this server, launches Leaf with the exact Task 1 flags,
   and waits for a **new** `Done (` in `logs\latest.log`, so a stale line cannot pass.
3. No whitelist step: the test server is offline/cracked with the whitelist **off**.
   Join as `AgentView` (offline UUID `9f405998-7c7d-33c3-9633-da8ed65bc604`).

After a server restart, the old client is disconnected — start a fresh one per §2.

---

## 2. Start the client

```js
await tools.craftwire.client_process({
  action: "start",
  server: "127.0.0.1:25566",
  version: "26.3",
  username: "AgentView",
  lite: true,
  timeoutMs: 240000,
});
// -> { instance: "client-1", pid: ..., state: "running" }
```

Use a different `username` for additional clients (hub allows up to 4).

**Capture identity policy:** a capture session leaves `AgentView` player files in
`world\players`. `TestServer.ps1 verify` tolerates them (qcart's four files remain
canonical), and `TestServer.ps1 reset` restores the qcart-only baseline. No whitelist
entry is needed.

**Required-pack prompt:** the plugin pushes a REQUIRED resource pack, so on first join
the client stops on a `PackConfirmScreen`. Accept it before capturing:

```js
await tools.craftwire.gui_read({ instance: "client-1" });
// title: "This server requires the use of a custom resource pack."
// widgets[2] = "Proceed", widgets[3] = "Disconnect"

await tools.craftwire.gui_action({ instance: "client-1", action: "click_widget", widget: "2" });
```

`widget` must be the **index as a string** (`"2"`), not a number. Do not rely on
`wait_for({condition:"resource_pack"})` here — the event can fire before the wait starts
and it will time out even though the pack is fine.

Verify: `tools.craftwire.list_instances()` should list `server-5` and `client-1`.

---

## 3. Capture

```js
// Full frame (HUD on by default)
await tools.craftwire.screenshot({
  instance: "client-1",
  savePath: "C:\\Users\\marcus panget\\AppData\\Local\\Temp\\opencode\\capture.png",
  maxSize: 1024,
});
// -> { width, height, fullWidth, fullHeight, savedPath }

// HUD as text: actionbar, titles, sidebar, tab list, bossbars
await tools.craftwire.hud_read({ instance: "client-1" });

// Open screen as slots/tooltips; transparent GUI-only PNG
await tools.craftwire.gui_read({ instance: "client-1" });
await tools.craftwire.screenshot({ instance: "client-1", layer: "gui", savePath: "..." });

// Tune the view before a shot
await tools.craftwire.client_settings({ instance: "client-1", guiScale: 2, hideHud: false });
```

Other useful tools: `camera` (freecam / look_at / frame_entity), `record` (video),
`client_eval` (render-thread JS, first call downloads GraalJS), `input` (keys/hotbar),
`chat`.

**Action bar note:** the three ability tiles only render while the viewer is
**deployed** in the battleground. Stage and deploy with:

```js
await tools.craftwire.server_command({ command: "bg join", asPlayer: "AgentView" });
await tools.craftwire.gui_read({ instance: "client-1" });   // BATTLEGROUND hub
await tools.craftwire.gui_action({ instance: "client-1", action: "click", slot: 22, click: "left" }); // DEPLOY
```

The glyph art is not shipped yet, so the action bar renders as empty boxes;
`hud_read` still returns the exact codepoints. That is expected, not a pack failure.

---

## 4. Cleanup

```js
await tools.craftwire.client_process({ action: "stop", username: "AgentView" });
// or { action: "stop", all: true }
```

Stop the server with `.\TestServer.ps1 stop` from `C:\Tools\global-test-server-26.3`
if it is no longer needed. To clear capture-session `AgentView` files and return to
the qcart-only baseline, stop the server and run `.\TestServer.ps1 reset`. The shared
hub on 7777 should stay up for other sessions.

---

## 5. Troubleshooting

| Symptom | Fix |
|---|---|
| `list_instances` shows only the server | Start the client per §2. |
| Client stuck on resource-pack screen | Click Proceed per §2. |
| `ECONNREFUSED` from craftwire MCP | Hub down — start it, then toggle the MCP `disabled: true → false` in `~/.config/opencode/opencode.json` to force reconnect. |
| Client instantly disconnects | Server down or not on `25566` — `.\TestServer.ps1 restart` then `status`. Whitelist is off, so this is not a whitelist problem. |
| Screenshot shows toast/progress screen | Wait for the sky-drop to finish, then re-shoot. |
| Empty glyph boxes in action bar | Expected: art not added yet. The HUD text itself is correct. |
