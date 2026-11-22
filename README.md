# shenanigans-core

A Paper PvP plugin built around four Jujutsu Kaisen Domain Expansions and six standalone techniques, played in an arena-style battleground.

## Features

- **Domains:** Malevolent Shrine (open), Unlimited Void, Time Cell Moon Palace, Womb Profusion (closed)
- **Techniques:** Fuga, Red, Blue, Purple, Piercing Blood, World Slash — cast with `/ability 1|2|3`
- **Arena mode:** capture, loadouts, killstreak shop, protection and restore system
- **Resource pack:** bundled, self-hosted HUD and ability icons

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

## Commands

- `/bg join`, `/bg leave`, `/bg help [topic]` — battleground
- `/ability 1|2|3` (alias `/ab`) — techniques
- `/bg debug ability <id>` — admin-only domain cast

See [`AGENTS.md`](AGENTS.md) for architecture, configuration and contributor notes.

## License

Personal project. No license is granted; all rights reserved.
