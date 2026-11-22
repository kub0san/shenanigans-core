# Admin balance commands — design

## Intent / why

Admins need an in-game way to run credit and kill-streak giveaways. The values
already exist per player (`BattlegroundPlayer.credits`, `.streak`) and are
persisted through `PlayerStore.markDirty`; this is a command surface over that
state, not a shop-configuration tool.

## Goals

- Six admin-only commands:
  - `/bg credits add <player|all> <amount>`
  - `/bg credits remove <player|all> <amount>`
  - `/bg credits set <player|all> <amount>`
  - `/bg streak add <player|all> <amount>`
  - `/bg streak remove <player|all> <amount>`
  - `/bg streak set <player|all> <amount>`
- `all` means **online players only** (decided with the user).
- Named targets resolve with `Bukkit.getPlayerExact`; offline players are not
  targetable by name.
- Changes persist via `PlayerStore.markDirty(uuid)`.
- Works from console as well as in-game.

## Non-goals / anti-goals

- No shop or catalogue editing, no price/streak-requirement changes.
- No offline-player targeting and no persistent "all" roster.
- No new permission node — reuse `shenaniganscore.battleground.admin`.
- No changes to the seven existing player/admin paths' behavior.

## Constraints

- `Cmd`/`CommandNode`/`CommandRegistrar` support exactly one trailing argument
  today (`WORD` or `INTEGER`). Two typed arguments are required for
  `<target> <amount>`.
- `CommandHelp` renders usage from the same tree, so it must learn the second
  argument or help will drift.
- `BattlegroundPlayer.setCredits`/`addCredits` and `setStreak` already clamp at
  zero. `recordBestStreak` is display-only.
- Streak resets on death and round boundary and gates casts; admin grants are
  temporary by design.

## Approaches considered

1. **Extend the `Cmd` framework with a second typed argument** — add
   `arg2`/`argName2`/`argHandler2` to `CommandNode`, a second `arg(...)` call on
   `Cmd`, chained `.then(...)` in `CommandRegistrar`, a second value in
   `CommandContext`, and two-token usage in `CommandHelp`. Clean Brigadier
   tab-completion and type checking; costs a small framework change with tests.
2. **One greedy `WORD` argument, split by hand** — no framework change, but
   amount becomes a string, tab-completion is poor, and `/bg credits add all 50`
   only works if users quote or the parser splits correctly. Rejected.
3. **Separate literal nodes per operation and fixed values** — impossible for
   arbitrary player names/amounts without dynamic nodes. Rejected.

**Picked: (1).** The user specified the exact two-argument syntax, and the
framework is small and already the single source of truth for registration,
help, and validation.

## Decided details (my calls, correctable)

- Handler logic lives in a new `battleground/command/BalanceCommands.java`
  returning two `CommandNode` groups; `BattlegroundCommand` just attaches them.
- `amount` is normalized with `Math.max(0, amount)`; `set 0` and `remove` past
  zero are valid and land on zero.
- `add`/`set` streak also call `recordBestStreak` so the display-only best value
  stays consistent.
- `remove` never calls `recordBestStreak` (best is a high-water mark, not a
  running balance).
- Unknown target replies `<red>No player named ... is online.`; `all` reports
  how many players were updated.
- Every leaf is `Permissions.BG_ADMIN` and `CommandNode.Group.ADMIN`.

## Left to the builder

- Exact signatures and test cases live in the implementation plan produced by
  `writing-plans`; no design judgment remains here.
