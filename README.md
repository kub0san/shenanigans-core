# shenanigans-core

A Paper PvP plugin that collects popular abilities, items and concepts from series we like into one
arena-mode playground. It's a personal project for a personal server — made for PvP shenanigans,
not meant to be taken seriously or balanced.

Today the plugin ships one origin: Jujutsu Kaisen (four Domain Expansions and six techniques).
The plan is to grow it into a multi-origin collection — Terraria-based content and other series
are next. An "origin" is simply a series the plugin draws from.

## Features

- **Origins:** Jujutsu Kaisen is implemented today; Terraria and other series are planned
- **Domains (JJK):** Malevolent Shrine (open), Unlimited Void, Time Cell Moon Palace, Womb Profusion (closed)
- **Techniques (JJK):** Fuga, Red, Blue, Purple, Piercing Blood, World Slash — cast with `/ability 1|2|3`
- **Arena mode:** capture, loadouts, killstreak shop, protection and restore system
- **Resource pack:** bundled, self-hosted HUD and ability icons

## Project layout

- `src/main/java/.../core/` — origin-agnostic utilities (display, sessions, config, particles, network)
- `src/main/java/.../origins/jujutsukaisen/` — the first implemented origin: domains and techniques
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
