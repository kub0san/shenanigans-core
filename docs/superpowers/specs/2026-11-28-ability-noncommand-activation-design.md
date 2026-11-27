# Issue #80 non-command ability activation — design

## Intent and why

Issue #80 proposes non-command ways to activate abilities. Dash Sword is the natural first consumer: held slot 1 already maps to it, left-click is owned by the refund listener, and right-click is comparatively free. This is a separate project from the launch rewrite.

## Goals

- One funnel through which command, item and future input triggers call the same gates.
- Dash Sword gains right-click activation with identical cooldown/ownership/held-item gating.

## Non-goals

- Companion mod / serverbound payload (P3 of #80), Controls-tab bindings, domains getting non-command activation (#74), or changing `/ability` as the universal fallback.

## Constraints

`AbilityCaster.java:69-107` owns installed gates, domain lock, atomic cooldown claim, perform and DEBUG bypass; `AbilityGateService.java:30-45` owns deployed/ownership/held-item checks; `CastInput.java:37-56` reason precedence and `AbilityRail.java:18-23,30-36,50-52` slots must stay consistent. `DashSwordHitListener.java:40-50` already owns left-click MONITOR refunds. `ActionLockListener` cancels interact/sneak/GUI; `Immobilize.java:25-41`.

## Approaches considered

1. **P0 funnel + dash right-click (chosen).** Build `CastSource`/`CastTriggerService`, migrate `/ability` onto it, then add right-click for the held dash sword. No behavior change for commands.
2. Direct right-click listener calling `DashSwordEffect.launch()`: rejected, would bypass gates.
3. Companion-mod keybinds: rejected for now, large new surface and versioning.

## Decided design

- Add `CastSource` values for command and item-triggered casts; a `CastRequest` carrying player, slot and source; one `CastTriggerService` registered centrally in `ShenanigansCore.java:323-337`.
- **User decision (2026-11-28): the service owns the decision and gates**, reusing `CastInput.java:37-56` reason precedence and the existing reply text. `/ability` submits a request instead of deciding locally in `AbilityCommand.java:76-118`, so command and right-click cannot double-gate or diverge in denial text.
- **User decision (2026-11-28): the right-click listener runs at MONITOR with `ignoreCancelled = true`**, so a click cancelled by `ActionLockListener.java:106-115` never reaches the funnel and produces no feedback.
- Scope is P0 funnel plus dash-sword right-click; no companion mod, no Controls-tab bindings, no domains (#74), no new config; `/ability` stays the universal fallback.
- Keep `CastSourceTest`, `AbilityCasterFailureTest`, `CastInputTest`, `AbilityRailTest` and `HeldItemAbilityTest` green; add tests that command and item triggers hit the same gate order and cooldown key (`DashSwordAbility.ID.toString()`).

## Left to the builder

- Interaction conflicts (place/eat/offhand), opt-in default, sneak-vs-#71 and #73 channel remain follow-up decisions **after** the dash right-click lands; they do not block this plan.
