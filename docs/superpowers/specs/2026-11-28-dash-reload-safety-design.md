# Dash Sword #85 reload safety — design

## Intent and why

Issue #85 asks for PlugManX-safe reloads. Existing dash teardown covers the echo renderer and visibility mask but not wind trail displays, so a reload mid-dash strands real display entities.

## Goals

- A dash in progress at disable/reload leaves no wind displays, listeners or invisibility behind.

## Non-goals

- Making the whole plugin hot-reload safe (outside dash scope), fail-closing reload while a dash is active, or changing `cancelTasks` ordering for non-dash systems.

## Constraints

`ShenanigansCore.onDisable` already shuts down `MannequinEchoRenderer` and `DashVisibilityMask` before `cancelTasks`; `ReloadSafetyWiringTest` source-pins those hooks and their ordering matters.

## Approaches considered

1. **Per-effect static registry + shutdown (chosen).** Mirror the existing mask/renderer pattern in `WindTrailEffect`.
2. One umbrella `DashSwordEffect.shutdown()` seam: rejected, would duplicate per-effect ownership and need new bookkeeping.
3. Rely on `setPersistent(false)`: rejected, it only prevents restart persistence, not live leaks.

## Decided design

- Add a static `ACTIVE` set and `shutdown()` to `WindTrailEffect`, mirroring `DashVisibilityMask`: instances register on construction, deregister in `close()`, and `shutdown()` closes a stable snapshot idempotently.
- Register `runSafely("dash wind trails", ...)` in `ShenanigansCore.onDisable` before `cancelTasks`, beside the two existing dash hooks.
- Pin the hook and its ordering in `ReloadSafetyWiringTest.java:104-113` (ordering precedent `ExecutionerVisualCleanupTest.java:88-105`); add an idempotence test if a MockBukkit seam is cheap.
- Live matrix: reload mid-dash with a leak detector, `/plugman usage`, and `reload all` (PacketEvents stale API risk).

## Left to the builder

- Confirm PlugManX actually closes the classloader (affects static resets) and whether `reload all` needs a separate guard.
