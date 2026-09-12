# Test plan

Two tiers. Tier 1 runs here and now; tier 2 needs a JDK 21 and a live server and
has **not** been run in this session.

---

## Tier 1 — automated, runs without Minecraft

```bash
python docs/agent_run/2026-09-12-issue-fixes/tests/run_all.py
```

| Suite | Checks |
| --- | --- |
| `test_java_syntax.py` | brace/paren balance in every touched file; every helper this run calls is actually declared; new types are imported; removed constants have no dangling references |
| `test_structural.py` | one or more source assertions per task (T1–T8), plus the two cross-cutting invariants (all mixins registered, all options reachable from the config book) |
| `test_noise.py` | the MARINE sea floor, ported 1:1 to Python: bounds contract, faceting metric, and gameplay envelope |

Results: [`TEST_RESULTS.md`](TEST_RESULTS.md). Raw log: `tests/last_run.log`.

These are regression guards, not proof of correctness. They exist because the
defects fixed here — a missing `case`, an unregistered mixin, an option with no
book entry — all compile cleanly and fail silently.

---

## Tier 2 — in game. NOT RUN.

**Blocked:** this machine has JDK 17.0.9 only; a filesystem-wide search found no
other `java.exe`. Loom refuses to configure the project:

```text
Minecraft 1.21.1 requires Java 21 but Gradle is using 17
```

So the mod was not compiled and not run. Nothing below has been executed.

### Prerequisites

1. Install a JDK 21 and point Gradle at it in `~/.gradle/gradle.properties`:
   `org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-21.x.x-hotspot`
2. `./gradlew build` — must succeed. **This is the first real gate**: it is the
   only thing that proves the edits compile.
3. `./gradlew runServer`, then provision `run/` per `AGENT.md`
   (`eula.txt`, `server.properties` with RCON on, op yourself,
   `restart-server.bat` + a `start-server.bat`).
4. Fast-iteration profile in `run/uhc.properties`:
   `borderStart=300`, `gameTime=600`, `borderStartTime=120`, `borderEndTime=400`.

### T0 — launch (gates everything else)

- [ ] Server reaches `Done` with no mixin error. `defaultRequire: 1` means any
      injector whose target moved is fatal at launch, so this alone validates
      that `SubmergedDensityFunction`, the deferred-death task, and the new
      option all load.
- [ ] A vanilla 1.21.1 client connects.

### T1 — issue 5, inventory drops on death

1. Start a NORMAL match with two players.
2. Give one a recognisable inventory (`/give @p diamond 5`).
3. Kill them (`/kill`, or the other player).

- [ ] The items drop on the ground at the death point.
- [ ] The player is in spectator **immediately afterwards** (within a tick).
- [ ] With `forceViewport` on, the camera follows a living team-mate.
- [ ] A moral item also drops (the `PlayerItems` stack).

Regression to watch: the spectator switch now happens one task tick later, so
confirm nothing observes the player as "alive but dead" in between — check the
scoreboard alive count updates and `checkWinner` still fires when the last team
member dies.

### T2 — issue 4, KING team selection

1. `/uhc config`, set `gameMode` to `KING`, set `randomTeams` to `关闭` (off).
2. Open the player book.

- [ ] The book shows one row per team colour plus a random row, not just the
      observer row.
- [ ] `/uhc select 0`..`3` assigns the right colour.
- [ ] `/uhc start` proceeds instead of refusing with "有玩家未选队".
- [ ] In game, the first player of each team has the crown helmet.

### T3 — issue 6, no duplicate config books

1. `/uhc config`.
2. `/uhc config` again. And a third time.

- [ ] Exactly **one** config book in the inventory, every time.
- [ ] Editing any option with `<` / `>` updates that one book in place.
- [ ] The book reopens on the same page after an edit.
- [ ] Same for the team-select book after repeated `/uhc select`.

### T4 — issue 7, reconfigure after a match without creative

1. Play a match to completion (or `/uhc stop`).
2. While still in spectator, run `/uhc config`.

- [ ] "已返回大厅" is broadcast.
- [ ] Everyone is back in adventure mode on the spawn platform at y≈160.
- [ ] Health/hunger are full, inventories cleared, status effects gone.
- [ ] Scoreboard sidebar is hidden and the vanilla teams are gone.
- [ ] The config book and player book are usable — **no creative mode needed**.
- [ ] Team selection and `/uhc start` work for a second match.
- [ ] Stats from the previous match do not carry into the new score board.

### T5 — issue 10, MARINE sea floor

1. `/uhc config`, set `battleType` to `海战` (MARINE), `/uhc regen` twice.
2. After the restart, fly around the overworld in spectator.

- [ ] The sea floor has rounded, meandering relief — **no quadrilateral plateaus
      with straight edges**, which is the reported symptom.
- [ ] No terrain holes or abrupt cliffs at chunk-section boundaries (this is
      what the bounds-contract fix addresses).
- [ ] Small islands break the surface occasionally. Tier 1 measured 0.836% of
      columns at/above sea level, versus 0.813% before — so roughly one island
      per 120 columns, same as the old generator.
- [ ] Deepest water around y≈30, floor centred near y≈50.
- [ ] `/uhc debug terrain 200` and `/uhc debug biome 200` report ocean biomes and
      a plausible height spread.

### T6 — issue 2, stale terrain warning

1. With a world generated under `battleType=NORMAL`, stop the server.
2. Set `battleType=MARINE` in `run/uhc.properties` by hand.
3. Start the server **without** regenerating.

- [ ] A red three-line warning is broadcast naming the old and new settings and
      telling the operator to run `/uhc regen`.
- [ ] The same warning appears in the server log at WARN level.
- [ ] `run/world/uhc.json` contains a `generatorIdentity` field.
- [ ] After `/uhc regen`, the next start is silent (the world folder is wiped, so
      the identity is recorded fresh).
- [ ] A world created before this change (no `generatorIdentity`) adopts the
      current identity silently rather than warning.

### T7 — issue 3, golden apple stat

1. In a running match, eat a golden apple and an enchanted golden apple.
2. End the match.

- [ ] The final score board shows 食用金苹果 = 2.
- [ ] Eating one as a non-tracked player (e.g. a Carpet fake player) does not
      throw — check the log for a swallowed `NullPointerException`.

### T8 — issues 8/9, pregeneration throughput

1. Set `pregenerateParallelism` to 1, `/uhc regen`, time the pregeneration.
2. Repeat at 2 (the default), then 6.

- [ ] The option is visible and adjustable on the 世界设置 page of the config book.
- [ ] Higher values measurably shorten pregeneration.
- [ ] At the highest value, check MSPT in the TAB header — the trade-off is
      server smoothness, which is exactly why the default stays at 2.
- [ ] No `Pregenerate permanently failed chunk` lines in the log at the default.
- [ ] An invalid value in `uhc.properties` falls back to 2 with a warning rather
      than stopping pregeneration.

### Issues 1 and 9 — already-fixed, confirm only

- [ ] Pregeneration reaches 100% without a long stall near 99%.
- [ ] If it does stall, capture the log lines
      `Pregenerate still waiting on chunk ...` / `permanently failed chunk ...`
      — they name the chunks and are the starting point for any further work.

---

## Scripted helpers

```bash
python scripts/rcon.py --file docs/agent_run/2026-09-12-issue-fixes/tests/ingame_t3_config_books.txt
python scripts/rcon.py --file docs/agent_run/2026-09-12-issue-fixes/tests/ingame_t4_return_to_lobby.txt
```

These drive the server side; the inventory and book assertions still need a pair
of human eyes on a client.

### T10 follow-up - post-game spectator lifecycle

This supersedes T4's earlier “adventure mode” expectation: the requested lobby
mode after an ended match is now survival.

1. Start a match and end it through either a win or `/uhc stop`.
2. Immediately after the result title appears, try to fly and inspect the fight area.
3. Run `/uhc config`.

- [ ] Every player, including the surviving winner, becomes spectator immediately.
- [ ] Players remain spectators after the delayed statistics broadcast finishes.
- [ ] `/uhc config` teleports everyone to the lobby and changes them to survival.
- [ ] A player reconnecting while configuration is open also receives survival mode.

### T12 - reset command clarity

- [x] `/uhc reset` prints an explanation instead of “unknown or incomplete command”.
- [ ] `/uhc reset gameplay` restores gameplay defaults and gives confirmation.
- [ ] `/uhc reset generation` restores generation defaults, gives confirmation, and
      explains that `/uhc regen` is needed.
- [ ] The two reset buttons in the config book invoke the readable forms.

### T13 - useful bonus-chest enchanted books

- [ ] Open enough newly generated bonus chests to find enchanted books in each
      battle type.
- [ ] No generated enchanted book has Unbreaking (耐久).
- [ ] Other useful book enchantments continue to appear.
