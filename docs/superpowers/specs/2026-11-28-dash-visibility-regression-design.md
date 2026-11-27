# Dash Sword #68 visibility regression — design

## Intent and why

Keep the closed fix for #68 (held item ghosted or replaced clientside) true after #67 changes dash duration and early-exit paths.

## Goals

- Caster is never masked, in first person or F5.
- Equipment listener handoff stays atomic on recast.
- Every end path restores invisibility and equipment exactly once.

## Non-goals

- Rewriting the PacketEvents mask, adding async handoff, or making third parties see the caster's body.

## Constraints

`DashVisibilityMask` owns PacketEvents listener registration; `ShenanigansCore.onDisable` hook must stay before `cancelTasks`; restore must remain idempotent.

## Approaches considered

1. **Funnel all ends through `finish()`/`clear()` and add live checks (chosen).** No new end paths; verify the existing invariant.
2. A separate listener/restore task per dash: rejected, creates races.
3. Mask everyone including the caster: rejected, that was the bug.

## Decided design

- Do not add any new `Timeline` exit that bypasses `finish()`. Any new guard added by #66/#67 calls `finish()`.
- Keep `relinquish()` synchronous in the mask constructor; no async/yield, so the old listener is gone before the new one registers.
- Keep `DashVisibilityMaskViewerTest` policy assertions; add a pure seam only if a restore policy can be extracted without a PacketEvents singleton.
- Live 26.3 checklist: first-person/F5 held item, hotbar swap mid-dash, rapid debug recast, forced early ends (death, quit, `/leave`, stall) with a second viewer, mid-dash reload.

## Left to the builder

- Run the live checklist with qcart; close the residual handoff question only if a live check disproves it.
