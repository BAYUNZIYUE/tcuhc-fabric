# 02 — Gameplay model and configuration

## 1. Match lifecycle

The match is a three-flag state machine on `UhcGameManager`:

```
isPregenerating ──┐
                  ├─► [lobby]  isGamePlaying=false  isGameEnded=false
isGamePlaying ────┼─► [live]   isGamePlaying=true
isGameEnded ──────┴─► [ended]  isGamePlaying=false  isGameEnded=true
```

| Phase | Player state | What runs |
| --- | --- | --- |
| **Lobby** | `ADVENTURE`, invulnerable while configuring, on the hexagon spawn platform at y≈160 | `TaskPregenerate`, `TaskHUDInfo`, `TaskSpawnPlatformProtect` |
| **Configuring** | `SURVIVAL`; operator holds the config book; everyone holds the team-select book | `UhcConfigManager` chat-input state machine |
| **Live** | `SURVIVAL` after a 10s `TaskTitleCountDown` | `TaskScoreboard`, `TaskNetherCave`, `TaskBorderReminder`, mode tasks |
| **Ended** | `SPECTATOR` | `TaskBroadcastData`, winner particles |

`startGame()` is the single gate and it refuses on: game already running,
pregeneration incomplete (unless `forceStart`), nobody has picked a team, some
players have not picked, or `formTeams()` failing. Each refusal returns a
**specific Chinese reason string** to the operator. Preserve that — silent
failures here are the worst class of bug during a live match.

Ordering inside `startGame()` matters and is not arbitrary:

```
formTeams() -> isGamePlaying = true -> initWorlds() -> stopConfiguring()
-> setupIngameTeams() -> spreadPlayers() -> destroySpawnPlatform()
-> TaskTitleCountDown
```

`isGamePlaying` is set **before** `initWorlds()` because `PlayerStatistics.addStat`
silently drops writes unless the game is playing.

## 2. Modes and battle types are orthogonal

Two independent enums, both on `UhcGameManager`.

**`EnumMode`** — *who fights whom*. Carries a `deathRegen` flag.

| Mode | Teams | Notes |
| --- | --- | --- |
| `NORMAL` 普通 | N colour teams | `deathRegen = true` |
| `SOLO` 单人 | 1 player per team | |
| `BOSS` Boss | 1 boss vs everyone | boss bar tracks boss health |
| `GHOST` 隐身 | solo | permanent invisibility + glowing splash potion |
| `BOMBER` 小天才 | solo | Resistance II + invisibility, luck tipped arrows, optional TNT, melee damage halved |
| `KING` 国王 | N colour teams | `deathRegen = true`; team's first player is king; king death kills the team; `TaskKingEffectField` grants Speed within 5 blocks |
| `HUNTER` 猎人 | prey (red) vs hunters (blue) | hunters get a tracking compass; prey wins on timeout |
| `GHOSTHUNTER` 幽灵猎人 | prey (red, invisible) vs hunters (blue) | hunters get compass + glowing potion |

**`EnumBattleType`** — *what the world is like*.

| Type | Effect |
| --- | --- |
| `NORMAL` 普通 | vanilla-ish |
| `MARINE` 海战 | ocean-only overworld generator, boats in the start chest, Water Breathing at start, trident/Riptide/Channeling loot, bonus chests allowed in ocean biomes |
| `ICARUS` 鞘翅 | bound+mending elytra equipped at start, fireworks in loot, fly-into-wall damage halved, higher final border ceiling |

`EnumLevelType` (DEFAULT / AMPLIFIED / LARGEBIOMES) and `Weather` are a third and
fourth axis.

### Rule

When adding a mode you must touch **all** of these switch sites, because none of
them has a default that does something sensible:

- `UhcPlayerManager.automaticFormTeams()` and `manuallyFormTeams()`
- `UhcPlayerManager.spreadPlayers()`
- `UhcPlayerManager.addInitialEquipments()`
- `TaskTitleCountDown.onFinish()` (start-of-game kit)
- `BookNBT.getPlayerBook()` (team-select book) and `getAdjustBook()`
- `TaskScoreboard.onTimer()` (compass / win condition)
- `EnumMode.toString()` (display name)

A missing branch is silent: the player simply gets no kit and no way to pick a
side. **Grep for `getGameMode()` before claiming a mode is done.**

## 3. Teams

`UhcGameTeam` wraps either a colour (`setColorTeam`) or a single player
(`setPlayerTeam`). The **first player in the list is the king** in KING mode.
`UhcGameColor` carries a `DyeColor`, a `Formatting`, and a Chinese name; ids 0–7
are playable, `8 = WHITE` means observer, `9 = BLACK` means "assign me randomly".

Team formation has two paths driven by the `randomTeams` option.
`automaticFormTeams()` shuffles combat players into `teamCount` balanced teams.
`manuallyFormTeams()` honours each player's `colorSelected`, shuffles the team
list so colours are unpredictable, then load-balances the players who chose
random. Both return `false` with a reason recorded via `failTeamForm(...)`.

`setupIngameTeams()` mirrors the UHC teams into the **vanilla scoreboard** so the
client renders name colours and TAB grouping, and applies `friendlyFire` and
`teamCollision`. It wipes all existing scoreboard teams first.

Team health scaling: a team smaller than the largest team gets proportionally
more max health (`20.0 * playersPerTeam / team.getPlayerCount()`), so an uneven
split is not an automatic loss.

## 4. Statistics and scoring

`UhcGamePlayer.PlayerStatistics` is an `EnumMap<EnumStat, Float>`. **Writes are
dropped unless `isGamePlaying`** — the single most common reason a stat "does not
count". Stats are collected by mixins scattered across `entity/`, `item/`, and
`block/`.

Final score (`UhcGameManager.calculatePlayerScore`):

```
aliveTime/20/60  +  kills*2  +  diamonds  +  goldenApples*0.5
                 +  damageDealt/100  +  (10 if on the winning team)
```

`finalizeAliveTimes()` backfills `ALIVE_TIME` for players who survived to the end.

## 5. The option system

Three small classes in `options/`:

- **`OptionType`** — the value strategy. `IntegerType` / `FloatType` clamp to
  `[min,max]` and step; `BooleanType` accepts `true` / `开启` / `开` / `是`;
  `EnumType` stores an *index* and parses by constant name, `toString()`, or
  display string. `getDisplayString` renders booleans and vanilla `Difficulty`
  in Chinese.
- **`Option`** — id, Chinese name, description, type, default, plus a `Taskable`
  task list fired on every change, plus a `needToSave` flag that splits options
  into "gameplay" and "generation" groups for `/uhc reset 0|1`.
- **`Options`** — the registry. All 32 options are declared in the constructor,
  then `uhc.properties` is loaded over the defaults and immediately re-saved.
  Unknown keys log a warning instead of crashing.

`options/OptionsPreset` (added in 1.2.8) serialises the whole option map to a
named file under the preset directory, validated against
`[A-Za-z0-9_-]{1,32}`, and is driven by `/uhc preset`.

Adding an option is one line here plus one line in `BookNBT.getConfigBookPage`
— **an option with no book entry is invisible to operators**:

```java
addOption(new Option("myKnob", "我的开关", new OptionType.BooleanType(), false)
        .setDescription("这个开关做什么。"));
// optionally .addTask(taskReselectTeam) or .setNeedToSave()
```

`taskReselectTeam` is attached to every option that invalidates team choices
(mode, battle type, level type, randomTeams, teamCount). It clears everyone's
selection and re-gives the books.

## 6. The book UI

`util/BookNBT.java` builds `WRITTEN_BOOK` stacks whose lines are `Text`
components with `ClickEvent.RUN_COMMAND` / `SUGGEST_COMMAND`. A `TcUhcBookKind`
string in `DataComponentTypes.CUSTOM_DATA` tags each book as `config`, `player`,
or `adjust`.

Each option row renders as a three-part control strip:

```
名称  < 当前值 >
      |   |     \_ /uhc option <id> add
      |   \_______ /uhc option <id> set   -> chat-input mode
      \___________ /uhc option <id> sub
```

The config book is **paginated server-side**: the book always contains exactly
one page, and `configBookPage` on `UhcConfigManager` selects which of the 5
logical pages is rendered. Navigation is a header row of `/uhc configPage <n>`
links. This exists because a written book has a hard page limit that the full
option list exceeds.

`refreshConfigBooksInPlace()` rewrites the book **in the slot it already
occupies** rather than giving a new one, then sends `OpenWrittenBookS2CPacket` if
the player is holding it, so the book visually updates under the cursor.

### Rule

Never `insertStack` a fresh config book on change — that is what produced
duplicate books. Use `refreshConfigBook()`.

## 7. Chat is fully intercepted

`ServerPlayNetworkHandlerMixin.onChatMessage` **cancels vanilla chat
unconditionally** and routes to `UhcGameManager.onPlayerChat`, which tries
`UhcConfigManager.onPlayerChat` first (it may be consuming a numeric input for
`/uhc option ... set` or a page jump), and otherwise formats team-vs-global chat
manually. Prefix `p ` forces a global message during a live match.

### Consequence

Anything relying on vanilla chat (signed messages, chat reports, other chat mods)
will not work. Any new chat-driven input must register through
`UhcConfigManager`, not a second interceptor.

## 8. Commands

`UhcGameCommand` registers a single `/uhc` root from `CommandManagerMixin`'s
constructor injection. Destructive actions (`regen`, `start`, `forceStart`) use a
**static boolean two-step confirmation** and are cancellable with
`/uhc cancelRegen` / `/uhc cancelStart`. `requirePlayer` falls back to the first
online player so console execution still works.

Command surface:

```
/uhc                      version info
/uhc select <0-9>         pick team/faction (bound to the player book)
/uhc deathpos             clickable teleport suggestion to your death point
/uhc config               op: start configuring, give the config book
/uhc configPage <n>       op: jump to a config book page (0-indexed)
/uhc configPageJump <n>   op: same, 1-indexed
/uhc configPagePrompt     op: ask for a page number in chat
/uhc option <id> add|sub|set
/uhc reset                explain the two default-reset categories
/uhc reset gameplay       reset gameplay options (legacy alias: 0)
/uhc reset generation     reset generation options (legacy alias: 1)
/uhc regen | cancelRegen  op: wipe + regenerate terrain (2-step, restarts server)
/uhc start | forceStart | cancelStart
/uhc stop                 op: end the match immediately
/uhc adjust [end|kill <p>|resu <p>|respawn <p>]
/uhc givemorals [player]  op: hand out moral items

# added in 1.2.8
/uhc preset list
/uhc preset save <name> [overwrite]
/uhc preset load <name> [confirm]
/uhc preset show <name>
/uhc preset diff <name>
/uhc preset delete <name> [confirm]
/uhc debug biome <radius>       op: sample biomes around you
/uhc debug terrain <radius>     op: sample terrain heights around you
```

`preset load` and `preset delete` use the same two-step `confirm` pattern as
`regen` / `start`.
