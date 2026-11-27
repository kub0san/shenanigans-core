# Dash Sword #45 item 3 capability guard — design

## Intent and why

Issue #45 item 3 is still open: dash reflection/metadata visuals can break on version changes and currently only warn per packet. Items 1 and 2 of #45 were declined and stay out of scope.

## Goals

- Detect an unsupported mannequin metadata layout once, log once, and keep the dash playable.
- No leaked entities or listeners after the guard trips.

## Non-goals

- Adding config, new dependencies, a Bukkit-display fallback mannequin, or changing dash movement.

## Constraints

`MannequinEchoRenderer` is the only reflection-dependent dash visual (`generateEntityId`, metadata slots 0/4/5/16/17/18/19, `RESOLVABLE_PROFILE`); `WindTrailEffect` is pure Bukkit displays; `DashVisibilityMask` PE listener is comparatively stable.

## Approaches considered

1. **Catch-first-failure flag (chosen).** On the first failed spawn/send, disable echoes for the server session, warn once, keep wind + mask.
2. Probe metadata types at startup: rejected, more code and a new failure surface.
3. Disable all dash visuals: rejected, over-broad and loses the readable wind trail.

## Decided design

- Add a one-time capability flag in `MannequinEchoRenderer`: once a packet fails (or `requireReady()` throws), set disabled, log one warning, and make `spawn()`, `tick()` and `syncViewers()` no-ops.
- Keep `close()`/`shutdown()` safe when disabled; the existing static shutdown hook in `ShenanigansCore.onDisable` stays.
- Keep the caster-hidden mask and wind displays running so the dash remains legible.
- Add a pure policy helper/test seam (mirroring `DashVisibilityMaskViewerTest`) if needed to test the disable decision without PacketEvents.

## Left to the builder

- Verify on Paper 26.3 that current metadata slots really work; if the first cast trips the guard, capture the actual exception for the issue.
