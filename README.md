# shenanigans-core

A Paper PvP plugin built around four Jujutsu Kaisen Domain Expansions and six standalone techniques, played in an arena-style battleground.

## Features

- **Domains:** Malevolent Shrine (open), Unlimited Void, Time Cell Moon Palace, Womb Profusion (closed)
- **Techniques:** Fuga, Red, Blue, Purple, Piercing Blood, World Slash — cast with `/ability 1|2|3`
- **Arena mode:** capture, loadouts, killstreak shop, protection and restore system
- **Resource pack:** bundled, self-hosted HUD and ability icons

## Project layout

- `src/main/java/.../core/` — origin-agnostic utilities (display, sessions, config, particles, network)
- `src/main/java/.../origins/jujutsukaisen/` — domain expansions and techniques
- `src/main/java/.../battleground/` — arena mode, economy, combat, menus and storage
- `resourcepack/` — the self-hosted client pack (HUD atlas, item models, textures)

## Requirements

- Java 25
- Paper 1.21.11+ (target: Paper 26.3)
- PacketEvents (required)
- WorldEdit or FAWE (optional — arena wand / snapshot)

## Build

```powershell
.\gradlew.bat build
```

Output: `build/libs/shenanigans-core-1.0-SNAPSHOT-all.jar` (shadow jar). Drop it into the server's `plugins/` folder.

Run tests with `.\gradlew.bat test`.

## Configuration

On first run the plugin generates `config.yml` and `battleground/config.yml`. Technique cooldowns and damage are tuned live in `config.yml`; round length, protection and restore budgets live in `battleground/config.yml`. `/bg reload` reapplies both.

## Commands

- `/bg join`, `/bg leave`, `/bg help [topic]` — battleground
- `/ability 1|2|3` (alias `/ab`) — techniques
- `/bg debug ability <id>` — admin-only domain cast

## License

Licensed under the GNU General Public License v3.0 — see [LICENSE](LICENSE).
