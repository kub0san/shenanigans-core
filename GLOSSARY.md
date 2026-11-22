# Shenanigans Core

A Paper PvP plugin built around four Jujutsu Kaisen domain expansions and a set of standalone
techniques, played inside an arena-mode battleground. This glossary defines the concepts the
project uses; it is not a specification or an implementation guide.

## Abilities and casters

**Ability**:
Any registered castable thing — a technique or a domain expansion — identified by a neutral id
that the shop, rail items and commands read without importing a franchise's classes.
_Avoid_: skill, spell, move.

**Technique**:
A standalone combat ability cast from one of a player's ability slots. The six techniques are
Fuga, Red, Blue, Purple, Piercing Blood and World Slash.
_Avoid_: attack, ability (too broad when the distinction matters).

**Domain Expansion**:
An arena-level ability that encloses the field and replaces the ground with a domain floor.
The four are Malevolent Shrine, Unlimited Void, Time Cell Moon Palace and Womb Profusion.
_Avoid_: ultimate, arena, zone.

**Caster**:
The player who activated an ability and owns the resulting session.
_Avoid_: owner, user, source.

**Cast**:
A single activation of an ability by a caster.
_Avoid_: use, trigger, fire.

**Ability Slot**:
One of the three bindings a player casts from with `/ability 1`, `/ability 2` or `/ability 3`.
_Avoid_: hotbar slot, keybind.

**Hidden Domain**:
A domain that is castable only through the admin debug path — never sold, bound or listed.
_Avoid_: secret ability, disabled domain, unobtainable.

## Domains

**Barrier**:
The enclosing shell of a closed domain. It is a visual: it does not itself block blocks or
entities, and a persistent shell display holds the enclosure once the animation settles.
_Avoid_: dome, wall, shield.

**Open Domain**:
A domain with no enclosing barrier that projects its effect outward. Malevolent Shrine is the
open domain; the other three are closed.
_Avoid_: field, area-of-effect.

**Domain Floor**:
The real blocks a domain lays under the arena — not a client-side overlay. Domain-owned blocks
are claimed so nothing outside the domain may change them.
_Avoid_: ground, terrain, platform.

**Collapse**:
The ordinary domain outro: the barrier and floor recede in reverse build order. Used on natural
expiry, caster quit and plugin shutdown.
_Avoid_: close, teardown, despawn.

**Shatter**:
The caster-death outro: the barrier breaks into moving fracture fronts that hand the floor back
as they pass.
_Avoid_: break, explode, burst.

**Fracture Front**:
One moving wave of a shatter, responsible for restoring the floor it crosses.
_Avoid_: wave, shard, ring.

**Session End Reason**:
The distinction that selects a domain's outro: ordinary ending selects collapse, caster death
selects shatter. A domain never performs both.
_Avoid_: end cause, exit type.

## Battleground

**Battleground**:
The arena game mode: rounds, economy, combat and always-on protection of the arena's original
blocks.
_Avoid_: match, minigame.

**Round**:
One scoring period of a battleground. Streaks reset at a round boundary.
_Avoid_: match, wave, life.

**Baseline**:
The persisted record of the arena's original blocks. Protection and restoration are derived
from it; player-placed blocks are never persisted into it.
_Avoid_: snapshot, backup, original.

**Arena Protection**:
The always-on guard that denies changes to baseline blocks and blocks TNT, creepers and end
crystals there. There is no command to switch it off.
_Avoid_: anti-grief, arena lock.

**Credits**:
The battleground currency players earn and spend between rounds.
_Avoid_: money, coins.

**Streak**:
A player's run of consecutive round wins, reset on death or at a round boundary.
_Avoid_: combo, chain.

## Visuals

**Client Overlay**:
Fake blocks sent only to a player's client to hide or restyle a position, with the real world
left untouched. Overlays are tracked so they can be re-sent after a chunk update or reverted
when the technique ends.
_Avoid_: ghost block, fake block, illusion.

**Origin**:
A source of abilities (for example Jujutsu Kaisen). The core layer is origin-agnostic; an origin
assembles its catalogue and registers it behind a neutral interface.
_Avoid_: mod, module, theme.
