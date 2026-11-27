# Issue #73 ability HUD channel — design (dash-relevant scope)

## Intent and why

Issue #73 asks to move ability/ultimate information off the shared action bar. Dash Sword has no direct action-bar message; its only presence is HUD slot 1, so the dash-relevant part is about the shared channel, not a new dash system.

## Goals

- One-shot ability notices move off the shared action bar; the persistent three-slot HUD keeps working.
- Dash slot 1 keeps resolving from the held item and keeps its existing glyphs.

## Non-goals

- Redesigning the whole HUD, adding new codepoints/art, or fixing #50 atlas validation here.

## Constraints

`TechniqueHud.send()`/`heldSource()` at :40-88; `ActionBarView` :35-41/:80-87; `IndicatorRouter` :27-35; `RoundController` refreshes every 5 ticks (:82,135-141). DASH_SWORD glyphs U+E0E6–E0FC exist in `TechniqueGlyphs.java:68-72` and `resourcepack/assets/shenanigans/font/hud.json:23-24`; atlas contract `docs/agents/resourcepack-guide.md:90-100`.

## Approaches considered

1. **Migrate only notices to subtitle; leave the persistent HUD (chosen).** Smallest change, avoids the contested boss bar and title channels.
2. Move the whole three-slot HUD to a boss bar: rejected, collides with restore bars and #61 titles.
3. Move everything to subtitles: rejected, continuous HUD would spam the subtitle channel.

## Decided design

- Wire `TechniqueHud.showNotice()` :161-180 / `ActionBarNotice` :14-69 to a subtitle sender, using the same glyph components (no new codepoints).
- **User decision (2026-11-28): real notices migrate.** One-shot `/ability` denial and cooldown replies from `AbilityCommand.java:93-117` and `AbilityCaster.fail()` :120-122 go through `showNotice` instead of `ctx.reply`; no more no-op plumbing-only path. Glyph and no-pack `◆` fallback are required.
- **User decision (2026-11-28): sticky notices re-send** on the existing HUD refresh (`RoundController.java:82,135-141`) until `clearNotice()` releases them; send a blank subtitle on release and on domain start so no caption lingers.
- Leave `TechniqueHud.send()` slot rendering and `IndicatorRouter` action-bar suppression unchanged.
- Leave dash slot 1 derived from the held item (`AbilityCommand.java:64-67`) and refunds silent (`DashSwordHitListener.java:49`).
- Update `ActionBarViewTest`, `IndicatorRouterContractTest` and `ActionBarNoticeTest` for the new notice channel.

## Left to the builder

- Moving the persistent three-slot HUD itself is explicitly out of scope; #42 throttle, #50 art, #72 and restore-bar contention would be blockers for that follow-up.
