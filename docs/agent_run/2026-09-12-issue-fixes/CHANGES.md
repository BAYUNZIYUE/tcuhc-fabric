# Changes

9 source files, +354 / −37. No file was renamed, moved, or deleted. No
dependency, build setting, or datapack JSON was touched.

```
 UhcGameManager.java                          | 69 ++++++++++++++-
 UhcGamePlayer.java                           | 15 ++++
 UhcPlayerManager.java                        | 99 ++++++++++++++++++++--
 mixins/core/MinecraftServerMixin.java        | 99 +++++++++++++++++-----
 mixins/item/PlayerEntityMixin.java           | 22 ++++-
 options/Options.java                         |  3 +-
 task/TaskPregenerate.java                    | 42 +++++++--
 util/BookNBT.java                            |  7 +-
 util/UhcWorldData.java                       | 35 ++++++++
```

---

## T1 — issue 5: inventory now drops on death

**`UhcPlayerManager.java`**

- `onPlayerDeath` no longer calls `player.changeGameMode(GameMode.SPECTATOR)`
  inline. It calls the new `enterSpectatorAfterDeathProcessing(gamePlayer)`.
- New private `enterSpectatorAfterDeathProcessing` wraps the gamemode switch and
  the `TaskKeepSpectate` registration in a `TaskOnce`, so both happen one task
  tick later.

Why: the UHC death hook injects at the HEAD of `ServerPlayerEntity.onDeath`, and
vanilla's body then runs `if (!this.isSpectator()) { this.drop(damageSource); }`.
Switching to spectator inline made that check false, so `drop(...)` — and
therefore `dropInventory()` and the mod's own `dropAllItemsWithoutClear`
redirect — never ran. Deferring keeps the player a survival player for the rest
of vanilla's death handling.

## T2 — issue 4: KING mode is selectable

**`util/BookNBT.java`** — `getPlayerBook()` gains `case KING:` falling through
into `case NORMAL:`. KING uses the same colour-team selection; the king is just
the first player of each team.

Without it, KING + `randomTeams` off rendered only the observer row, so nobody
could join a team and `/uhc start` always refused with "有玩家未选队".

## T3 — issue 6: no duplicate config books

**`UhcPlayerManager.java`**

- New `giveOrRefreshConfigBook(player)` — replaces every existing config book in
  place, and only inserts one if none was found.
- New `giveOrRefreshPlayerBook(player)` — the same contract for the team-select
  book.
- `regiveConfigItems` now uses both instead of `insertStack`.

**`UhcGameManager.java`** — `startConfiguration` calls
`playerManager.giveOrRefreshConfigBook(operator)` instead of
`operator.getInventory().insertStack(BookNBT.getConfigBook(...))`.

`/uhc config` is now idempotent, which matters because it is also the entry
point for setting up the next match (T4).

## T4 — issue 7: reconfiguring after a match needs no creative mode

**`UhcGameManager.java`**

- New `returnToLobby()` — clears `isGamePlaying`/`isGameEnded`, cancels tasks,
  removes the world border, hides the scoreboard, drops the boss bar, wipes the
  vanilla scoreboard teams, calls `playerManager.resetForNextGame()`, rebuilds
  the spawn platform, restarts `TaskHUDInfo`, and broadcasts 已返回大厅.
  It deliberately does **not** touch terrain — regenerating stays an explicit
  `/uhc regen` decision.
- `startConfiguration` calls it first when `isGameEnded`.
- Added `import net.minecraft.scoreboard.Team;`.

**`UhcPlayerManager.java`** — new `resetForNextGame()`: clears teams and the
combat/observer lists, resets `playersPerTeam` and the last failure reason, and
for every tracked player resets their game state and puts the live entity back
to `SURVIVAL` with full health, no effects, an empty inventory, and a fresh
spawn-platform position.

**`UhcGamePlayer.java`** — new `resetForNextGame()`: cancels tasks and clears
`isAlive`, `deathTime`, `deathPos`, `team`, `colorSelected`, `borderReminder`
and the statistics. UUID and name are kept.

## T5 — issue 10: MARINE sea floor

**`mixins/core/MinecraftServerMixin.java`** — `SubmergedDensityFunction`
rewritten:

- `valueNoise` → `gradientNoise`, with a domain warp and five rotated octaves,
  matching the technique `LandSurfaceDensityFunction` already used. The javadoc
  on `valueNoise` itself warns against using it for a large open surface: its
  smoothstep interpolation is flat at every lattice point, which produced the
  quadrilateral plateaus the report describes. Measured: lattice flatness ratio
  0.0208 → 1.0052.
- `minValue()`/`maxValue()` corrected from `[-0.5, 0.5]` to `[-1.0, 1.0]`.
  `sample()` has always ranged over `[-1, 1]`, and the `ChunkNoiseSampler` uses
  the declared bounds to skip interpolation cells.
- `CENTER_Y` 48 → **50**, `AMPLITUDE` 23 → **32**. Not cosmetic: gradient noise
  summed over octaves is much more concentrated than the old value-noise sum, so
  keeping 48/23 produced a floor that never breached the surface (0.000% island
  coverage against the old 0.813%). MARINE seeds oak logs and saplings in its
  bonus chests, so the islands are part of the mode. 50/32 restores the envelope
  — measured 0.836% coverage, max height 69.4 vs the old 69.0.
- Added `FLOOR_RAMP` and `OCTAVE_WEIGHT_SUM` named constants.

The `continents` → constant `-1.0` swap was **kept**, with a comment. It looks
dead given the 1.2.7 note that replacing `router.continents()` cannot change
terrain shape, but it is not: `continents` is what the multi-noise sampler reads
for *biome* placement, and pinning it is what keeps `createMarineBiomeSource`'s
continentalness ranges resolving to open-ocean entries.

## T6 — issue 2: stale terrain is reported

**`util/UhcWorldData.java`**

- New `String generatorIdentity` field, persisted to `<world>/uhc.json`.
- New `checkGeneratorIdentity(current)` — adopts and saves the current identity
  on a world that has never recorded one (so pre-existing worlds do not warn),
  returns null when it matches, and returns the previous value when it differs.

**`UhcGameManager.java`**

- New `getGeneratorIdentity()` — `battleType`, `levelType` and
  `disableOceanBiomes` as one comparable string.
- New `warnOnStaleTerrain()` — logs at WARN and broadcasts a three-line red
  warning naming both identities and telling the operator to run `/uhc regen`.
- `onServerInited()` calls it.

Terrain lives in the world folder; the settings that shaped it live in
`uhc.properties`, which survives a regen. Changing `battleType` without
regenerating silently keeps the old terrain — that is what "normal mode seems to
be using the marine world generator" actually was.

## T7 — issue 3: golden apple stat hardening

**`mixins/item/PlayerEntityMixin.java`**

- `ENCHANTED_GOLDEN_APPLE` now counts towards 食用金苹果 as well as
  `GOLDEN_APPLE`.
- `UhcGameManager.instance` and the resolved `UhcGamePlayer` are both
  null-checked, with early returns.

The stat itself was already restored in 1.2.5 by registering this mixin. The
null guards matter because this is on the eating path: an untracked eater (a
mid-match joiner before their game player exists, or a fake player) would have
thrown inside `eatFood`.

## T8 — issues 8, 9: pregeneration parallelism is configurable

**`options/Options.java`** — new `pregenerateParallelism` option
(`IntegerType(1, 16, 1)`, default **2** — the previously hard-coded value, so
behaviour is unchanged out of the box), added to `SERVER_START_OPTIONS`.

**`util/BookNBT.java`** — the option added to the 世界设置 config-book page,
next to `pregenerateOnStart` and `netherPregenerate`.

**`task/TaskPregenerate.java`**

- `PARALLELISM_LIMIT` / `ENQUEUE_THRESHOLD` constants replaced by instance
  fields `parallelismLimit` / `enqueueThreshold`, snapshotted in the constructor
  rather than read per tick — changing it mid-run would resize the in-flight
  window underneath the `queuedCount` bookkeeping.
- New `resolveParallelism()` reads the option and falls back to
  `DEFAULT_PARALLELISM = 2` with a warning if it is missing or malformed, so a
  bad setting can never stop pregeneration outright.
- Added `import me.fallenbreath.tcuhc.options.Options;`.

---

## Also

**`.gitignore`** — added `__pycache__/` and `*.pyc`, since this run adds Python
test suites under `docs/agent_run/`.

---

## Round 3 operator feedback

### T10 follow-up: ended matches remain spectatable

`UhcGameManager.endGame()` now changes every online player to `SPECTATOR`
immediately and resets each camera to the player. Previously this happened only
when `TaskBroadcastData` began eight seconds later, leaving surviving players in
survival after the winner had already been announced. Players remain spectators
for the whole ended phase.

`UhcPlayerManager.resetForNextGame()` now restores `SURVIVAL`, as requested,
when `/uhc config` transitions an ended match back to the lobby. A player who
reconnects while configuration is open also enters survival.

### T12: `/uhc reset` is self-explanatory

The command required the undocumented numeric argument `0` or `1`, so Brigadier
reported “unknown or incomplete” for the natural `/uhc reset` form. It is
useful—it restores option defaults—so it was kept and clarified:

- `/uhc reset` explains both categories without changing anything.
- `/uhc reset gameplay` restores gameplay, timing, and team defaults.
- `/uhc reset generation` restores ore, chest, merchant, and mob-generation defaults and
  tells the operator that `/uhc regen` is required.
- The old `0` and `1` forms remain compatible; config-book buttons now use the
  readable forms.

### T13: remove Unbreaking books from bonus chests

`gen/feature/BonusChestFeature.java` no longer includes `UNBREAKING` in the
NORMAL, MARINE, or ICARUS enchanted-book pools. This is the live chest path: it
constructs enchanted books directly while placing bonus chests.
