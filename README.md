# shenanigans-core

A little bit of everything we like, thrown into one big PvP playground.

**shenanigans-core** is a Paper PvP plugin where we bring abilities, weapons, and other cool ideas from different series into Minecraft. Think Jujutsu Kaisen, Terraria, and whatever else we feel like adding along the way.

This is just a fun project for a personal server, so don't expect everything to be perfectly balanced. The whole point is to mess around, try out cool mechanics, and have some fun with it.

Right now, the plugin has its first origin: **Jujutsu Kaisen**, featuring four Domain Expansions and six cursed techniques. There's plenty more planned, with Terraria content and other series hopefully making their way into the plugin soon!

An *origin* is just what we call each series we take inspiration from.

## Features

- **Multiple Origins:** Starting with Jujutsu Kaisen, with Terraria and other series planned for the future.
- **Domain Expansions:** Malevolent Shrine , Unlimited Void, Time Cell Moon Palace, and Womb Profusion
- **Cursed Techniques:** Fuga, Red, Blue, Purple, Piercing Blood, and World Slash. Cast them using `/ability 1|2|3`.
- **Battleground:** A dedicated arena mode with capture mechanics, loadouts, a killstreak shop, and protection/restore systems.
- **Custom Resource Pack:** A bundled, self-hosted resource pack with custom HUD elements, ability icons, and item textures.

## Project Layout

A quick look at how everything is organized:

- `src/main/java/.../core/` — The stuff that keeps everything running: displays, sessions, configs, particles, networking, and shared utilities.
- `src/main/java/.../origins/jujutsukaisen/` — All the JJK-related content, including domains and techniques.
- `src/main/java/.../battleground/` — Everything related to the arena: combat, economy, menus, storage, and more.
- `resourcepack/` — Our custom resource pack, including HUD textures, item models, and other assets.

## Requirements

- Java 25
- Paper 1.21.11+ (target: Leaf 26.3-27, Paper API 26.3.build.27-alpha; Java 25)
- PacketEvents (required)
- WorldEdit or FAWE (optional — used for arena wand / snapshots)

## Testing

- Shared test server: `C:\Tools\global-test-server-26.3` (Leaf 26.3-27, Java 25, game port 25566). Canonical guide: `C:\Projects\GLOBAL_TEST_SERVER.md`.
- From that folder: `.\TestServer.ps1 setup`, `deploy shenanigans-core`, `restart`, `status`, `verify`, `snapshot`, `reset`, `stop`. `.\TestServer.ps1 -Help` prints the full command table and MCP map.
- Player data is scrubbed except qcart (`befc5d95-fd63-4cae-b0f5-7c2fb271c699`). Craftwire is the primary E2E tool; a green handshake is not a pass.

## Building

Want to build it yourself? Easy enough!

```powershell
.\gradlew.bat build
```

The finished shadow JAR will be generated at:

`build/libs/shenanigans-core-1.0-SNAPSHOT-all.jar`

Just drop it into your server's `plugins/` folder and you're good to go.

You can also run the tests with:

```powershell
.\gradlew.bat test
```

## Configuration

On the first run, the plugin automatically generates `config.yml` and `battleground/config.yml`.

You can tweak technique cooldowns and damage in `config.yml`, while round length, protection, and restore budgets are configured in `battleground/config.yml`.

Made some changes? Run `/bg reload` to apply them!

## Commands

- `/bg join`, `/bg leave`, `/bg help [topic]` — Join, leave, or get help with the battleground.
- `/ability 1|2|3` (alias `/ab`) — Use your available techniques.
- `/bg debug ability <id>` — Admin-only command for testing domain abilities.

## License

Licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
