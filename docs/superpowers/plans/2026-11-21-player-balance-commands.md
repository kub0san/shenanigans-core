# Admin Balance Commands Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add admin-only `/bg credits|streak add|remove|set <player|all> <amount>` commands for online-player giveaways, persisted through `PlayerStore`.

**Architecture:** Extend the existing `Cmd`/`CommandNode`/`CommandRegistrar` framework from one trailing argument to two typed arguments so the user-specified syntax maps to real Brigadier arguments. A new `BalanceCommands` class returns two `CommandNode` groups that `BattlegroundCommand` attaches; all writes go through `BattlegroundPlayer` setters and `PlayerStore.markDirty`.

**Tech Stack:** Java 25, Paper 1.21.11+/26.3, Gradle 9.8.2 wrapper, JUnit 5, Adventure `MiniMessage`.

**Spec:** `docs/superpowers/specs/2026-11-21-player-balance-commands-design.md`

## Global Constraints

- Admin-only: every new leaf uses `Permissions.BG_ADMIN` and `CommandNode.Group.ADMIN`. No new permission node.
- `all` means **online players only**; named targets use `Bukkit.getPlayerExact`, so offline players are not targetable.
- No shop/catalogue configuration changes and no behavior change to the seven existing `/bg` paths.
- All values clamp at zero; no negative credits or streak may be produced.
- Persistence only via `PlayerStore.markDirty(uuid)` after each mutated record.
- Commands must work from console as well as in-game.
- Adventure `Component`/`MiniMessage` only; no `org.bukkit.ChatColor`. No config reads for gameplay values.
- Working tree holds unrelated uncommitted work: stage exact files by full path, never a directory.
- Windows: write UTF-8 explicitly.

## Review Focus

1. **`amount` is negative or huge.** Must clamp to `0` for `add`/`set`/`remove` and never go negative. Tested via `BalanceCommands.normalizeAmount`.
2. **`all` with nobody online.** Must reply cleanly, not throw or report success. Tested by an empty-target branch in `apply`, plus source review of the target list.
3. **Unknown player name.** Must reply `<red>No player named ... is online.` and mutate nothing. Source-reviewed in `BalanceCommands.apply`; no Bukkit unit test possible.
4. **Help drift after adding a second argument.** `CommandHelp` must render `/bg credits add <target> <amount>`, not just `<target>`. Tested in `CommandTwoArgTest`.
5. **Streak high-water mark.** `add`/`set` keep `bestStreak` in sync; `remove` leaves it untouched. Tested at the `BattlegroundPlayer` level in `BalanceCommandsTest` where possible.

---

### Task 1: Second typed argument in the command framework

**Files:**
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/command/CommandNode.java`
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/command/Cmd.java`
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/command/CommandContext.java`
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/command/CommandRegistrar.java`
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/command/CommandHelp.java`
- Test: `src/test/java/com/hizeric/shenaniganscore/battleground/command/CommandTwoArgTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `Cmd.arg2(String name, CommandNode.Arg type, Consumer<CommandContext> handler)`; `CommandNode.arg2()`, `argName2()`, `argHandler2()`; `CommandContext.argument2()` and `CommandContext.of(source, arg1, arg2)`.

- [ ] **Step 1: Write the failing test**

```java
package com.hizeric.shenaniganscore.battleground.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTwoArgTest {

    private static CommandNode tree() {
        return Cmd.root("demo", "Demo", "shenaniganscore.use", CommandNode.Group.PLAYER)
                .child(Cmd.group("credits", "Credits", "shenaniganscore.use", CommandNode.Group.PLAYER)
                        .child(Cmd.leaf("add", "Add credits", "shenaniganscore.use", CommandNode.Group.PLAYER)
                                .arg("target", CommandNode.Arg.WORD, ctx -> { })
                                .arg2("amount", CommandNode.Arg.INTEGER, ctx -> { })))
                .build();
    }

    @Test
    void secondArgumentIsCarriedByTheNode() {
        CommandNode leaf = tree().children().get(0).children().get(0);
        assertEquals(CommandNode.Arg.WORD, leaf.arg());
        assertEquals(CommandNode.Arg.INTEGER, leaf.arg2());
        assertEquals("target", leaf.argName());
        assertEquals("amount", leaf.argName2());
    }

    @Test
    void helpRendersBothArguments() {
        String text = String.join("\n", CommandHelp.render(List.of(tree()), "add"));
        assertTrue(text.contains("/demo credits add <target> <amount>"), text);
    }

    @Test
    void treeWithSecondArgumentStillValidates() {
        assertEquals(List.of(), new CommandRegistrar().validate(List.of(tree())));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "*CommandTwoArgTest*"`
Expected: compile failure — `arg2`/`argName2`/`argument2` do not exist.

- [ ] **Step 3: Extend the framework**

- `CommandNode`: add `private final Arg arg2; private final String argName2; private final Consumer<CommandContext> argHandler2;`, append three parameters to the package-private constructor, assign them, add three accessors.
- `Cmd`: add three matching fields, a new `arg2(String, Arg, Consumer<CommandContext>)` method next to `arg(...)`, and pass all three through `build()`.
- `CommandContext`: add `private final String argument2;`, add `of(source, arg1, arg2)` and `public String argument2()`. Existing `of(...)` overloads delegate with `null`.
- `CommandRegistrar.build`: when `arg2() != NONE`, nest a second `Commands.argument(argName2, INTEGER-or-WORD)` inside the first, and pass both values to `CommandContext.of(source, value1, value2)`. `validate`: require a name and handler for `arg2` when present.
- `CommandHelp`: in both `collect(...)` and `usage(...)`, append `" <" + argName2 + ">"` when `arg2() != NONE`.

Keep all existing one-argument nodes unchanged — both `help [topic]` and `debug ability|item [id]` must still work.

- [ ] **Step 4: Run the suite**

Run: `.\gradlew.bat test`
Expected: PASS, including `CommandTreeTest`, `CommandHelpTest`, `AdminTargetResolverTest`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/hizeric/shenaniganscore/battleground/command/CommandNode.java src/main/java/com/hizeric/shenaniganscore/battleground/command/Cmd.java src/main/java/com/hizeric/shenaniganscore/battleground/command/CommandContext.java src/main/java/com/hizeric/shenaniganscore/battleground/command/CommandRegistrar.java src/main/java/com/hizeric/shenaniganscore/battleground/command/CommandHelp.java src/test/java/com/hizeric/shenaniganscore/battleground/command/CommandTwoArgTest.java
git commit -m "feat(commands): support two typed arguments per node"
```

---

### Task 2: Balance commands

**Files:**
- Create: `src/main/java/com/hizeric/shenaniganscore/battleground/command/BalanceCommands.java`
- Modify: `src/main/java/com/hizeric/shenaniganscore/battleground/command/BattlegroundCommand.java`
- Test: `src/test/java/com/hizeric/shenaniganscore/battleground/command/BalanceCommandsTest.java`
- Test: `src/test/java/com/hizeric/shenaniganscore/battleground/command/CommandTreeTest.java`

**Interfaces:**
- Consumes: `Cmd.arg2`, `CommandContext.argument2()` from Task 1; `PlayerStore.get/markDirty`, `BattlegroundPlayer.addCredits/setCredits/setStreak/recordBestStreak`.
- Produces: `BalanceCommands.credits(ShenanigansCore plugin)`, `BalanceCommands.streak(ShenanigansCore plugin)` returning `CommandNode`; `public static int BalanceCommands.normalizeAmount(int)`; `public static boolean BalanceCommands.isAll(String target)`.

- [ ] **Step 1: Write the failing test**

```java
package com.hizeric.shenaniganscore.battleground.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceCommandsTest {

    @Test
    void amountNeverGoesNegative() {
        assertEquals(0, BalanceCommands.normalizeAmount(-50));
        assertEquals(0, BalanceCommands.normalizeAmount(0));
        assertEquals(50, BalanceCommands.normalizeAmount(50));
    }

    @Test
    void onlyTheLiteralAllSelectsEveryone() {
        assertTrue(BalanceCommands.isAll("all"));
        assertTrue(BalanceCommands.isAll("ALL"));
        assertFalse(BalanceCommands.isAll("allan"));
        assertFalse(BalanceCommands.isAll(""));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests "*BalanceCommandsTest*"`
Expected: compile failure — `BalanceCommands` does not exist.

- [ ] **Step 3: Implement `BalanceCommands`**

Public shape:

```java
public final class BalanceCommands {
    public enum Operation { ADD, REMOVE, SET }

    public static CommandNode credits(ShenanigansCore plugin) { ... }
    public static CommandNode streak(ShenanigansCore plugin) { ... }
    public static int normalizeAmount(int amount) { return Math.max(0, amount); }
    public static boolean isAll(String target) { return "all".equalsIgnoreCase(target); }
}
```

Each group is `Cmd.group("credits"|"streak", ..., Permissions.BG_ADMIN, Group.ADMIN)` with three `Cmd.leaf` children (`add`, `remove`, `set`), each carrying `Permissions.BG_ADMIN`, `Group.ADMIN`, `arg("target", WORD, ...)` and `arg2("amount", INTEGER, ...)`. All six call one private `apply(ctx, plugin, streak, operation)`.

`apply` behaviour:
- `String target = ctx.argument(); int amount = normalizeAmount(Integer.parseInt(ctx.argument2()));`
- If `isAll(target)`, targets = `Bukkit.getOnlinePlayers()`; else `Player exact = Bukkit.getPlayerExact(target)`, and on null reply `<red>No player named <white>target<red> is online.` and return.
- If targets is empty, reply `<gray>No online players to update.` and return.
- For each target: get `BattlegroundPlayer data = store.get(uuid)` where `store = plugin.getBattlegroundManager().playerStore()`; apply:
  - credits ADD → `data.addCredits(amount)`; REMOVE → `data.addCredits(-amount)`; SET → `data.setCredits(amount)`
  - streak ADD → `data.setStreak(data.streak() + amount); data.recordBestStreak(data.streak())`; REMOVE → `data.setStreak(data.streak() - amount)`; SET → `data.setStreak(amount); data.recordBestStreak(amount)`
  - then `store.markDirty(uuid)`.
- Reply `<green>Credits|Streak <operation>pdated for <white>N <green>online player(s).` with the field and verb spelled out. No Bukkit type is needed in the pure helpers.

Do not call `Bukkit.getOfflinePlayer`, do not touch `ShopCatalog`, and do not call `ctx.player()` — console must work.

- [ ] **Step 4: Attach the groups**

In `BattlegroundCommand.buildTree()`, add `.child(BalanceCommands.credits(plugin))` and `.child(BalanceCommands.streak(plugin))` in the admin section. Building the tree with a null plugin (as `CommandTreeTest` does) must stay safe — handlers capture `plugin`, builders never dereference it.

- [ ] **Step 5: Pin the tree**

In `CommandTreeTest.requiredNodesArePresent`, add the six paths `/battleground credits add|remove|set` and `/battleground streak add|remove|set`. In `adminLeavesCarryTheAdminPermission`, add those six paths. In `deletedNodesAreGone`, nothing changes. Add one assertion that `node(..., "/battleground credits add").arg() == WORD` and `.arg2() == INTEGER`.

- [ ] **Step 6: Run the suite**

Run: `.\gradlew.bat test`
Expected: PASS, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/hizeric/shenaniganscore/battleground/command/BalanceCommands.java src/main/java/com/hizeric/shenaniganscore/battleground/command/BattlegroundCommand.java src/test/java/com/hizeric/shenaniganscore/battleground/command/BalanceCommandsTest.java src/test/java/com/hizeric/shenaniganscore/battleground/command/CommandTreeTest.java
git commit -m "feat(commands): admin credit and streak giveaway commands"
```

---

### Task 3: Docs, build, deploy

**Files:**
- Modify: `AGENTS.md` (command list)
- Modify: `src/main/resources/config.yml` only if a deleted command is referenced (expected: none)

**Interfaces:**
- Consumes: everything above.
- Produces: nothing.

- [ ] **Step 1: Update AGENTS.md**

Add the six admin paths to the in-game command list, noting online-only targets and that streaks reset on death/round boundary. Update the test count to the suite's actual numbers.

- [ ] **Step 2: Clean build**

Run: `.\gradlew.bat clean build`
Expected: BUILD SUCCESSFUL; shadow jar at `build/libs/shenanigans-core-1.0-SNAPSHOT-all.jar`.

- [ ] **Step 3: Deploy to the running test server**

Server: `%TEMP%\opencode\shenanigans-test-server`, PID found by matching `paper-26.3-256` in the java command line.
- Kill the running server process (world lock).
- Copy the new shadow jar to `plugins/shenanigans-core.jar`.
- Restart with `java -Xms1G -Xmx2G -jar paper-26.3-256.jar nogui`, hidden, redirect output to `server.log`/`server.err`.
- Wait for `Done (` and verify no errors, `Registered 11 abilities`, `WorldEdit API provider: WorldEdit`.

- [ ] **Step 4: Commit**

```bash
git add AGENTS.md src/main/resources/config.yml
git commit -m "docs: list admin balance commands"
```

---

## Self-Review Notes

- **Spec coverage:** six commands → Task 2; online-only `all` → Task 2 `apply`; two-arg syntax → Task 1; persistence → `markDirty` in Task 2; no shop changes → Global Constraints; deploy/restart → Task 3.
- **Type consistency:** `arg2`/`argName2`/`argHandler2` and `argument2()` are introduced in Task 1 and consumed by Task 2 and the tests; operation names `ADD`/`REMOVE`/`SET` are used only inside `BalanceCommands`.
- **Honest limitation:** named/offline target resolution and the count of online players cannot be unit-tested without a server; the pure helpers are tested, and the live server from Task 3 is the verification path for the rest.
