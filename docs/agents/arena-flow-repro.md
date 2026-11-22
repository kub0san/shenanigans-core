# Arena fluid / flint repro

## Reported symptoms (plan contract)

- Water/lava flow and flint & steel were reported broken in the arena with no live domain.
- The approved plan did not supply an exact block/location; the repro is symptom-based.

## Code-level evidence (ruled in / ruled out)

- **Arena protection does not cancel fluid flow.** `ArenaProtection#onFluidReplace` handles `BlockFromToEvent` with `ignoreCancelled = true` but never cancels; it only drops a stale in-memory player-placed override when flow replaces a player block (`ArenaProtection.java`).
- **Arena protection does not handle flint & steel ignition.** `ArenaProtection` has no `BlockIgniteEvent` handler; `ArenaProtectionTest#fluidFlowIsNeverCancelledAndIgnitionHasNoArenaHandler` pins this.
- **Domain enforcement is separate.** `DomainRegionGuard` owns domain fluid/ignite decisions and remains independent of the arena protection toggle (`ArenaProtectionTest` asserts its handlers exist).
- **Snapshot restore writes air.** `ArenaSnapshot` stores air as a normal palette entry and `PastePlanTest` (retargeted snapshot assertion) pins `ignoreAirBlocks == false` equivalent so restore can erase player-placed blocks.

## Ruled-out causes (code inspection)

- Arena protection cancelling `BlockFromToEvent` or `BlockIgniteEvent`: ruled out by handler shape and unit test.
- Restore ignoring air (leaving player blocks behind in fluid spaces): ruled out by snapshot air assertion and snapshot restore design.

## Live repro (manual, pending)

Not run in this worktree (no Paper server). Manual in-game pass required:

1. In arena with no live domain, place water and lava; confirm they flow/spread normally.
2. Use flint & steel on a permitted surface in the arena with no live domain; confirm ignition is not cancelled by arena protection.
3. Toggle `arena.protection.enabled` off/on via `/bg reload`; confirm fluid/ignition behavior is unchanged by the arena gate and captured-structure breakability follows the toggle.

Integration test pinning the symptoms should assert `BlockFromToEvent`/`BlockIgniteEvent` are not cancelled by arena protection with no live domain (see plan Task 3/Final acceptance criteria).
