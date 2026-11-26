# shenanigans-core is a multi-origin collection, not a JJK plugin

shenanigans-core is a personal, unserious PvP sandbox whose purpose is to collect abilities,
items and concepts from many series, not to implement Jujutsu Kaisen. JJK is the first origin
shipped; Terraria and others are planned. The multi-origin shape is recorded here because the
code can be read either way — the `origins/` seam and the origin-agnostic `core/` already exist —
but the old README and AGENTS.md described a JJK-only plugin. Future work should add a new
`origins/<series>/` folder rather than making `core/` aware of a series.
