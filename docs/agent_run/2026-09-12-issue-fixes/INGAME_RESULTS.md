# In-game results — tier 2

The blocker is gone. A JDK 21 was located at `E:/game/HMCL/prism/java21`
(Temurin 21.0.12.1, full JDK), Gradle was pointed at it, and the mod now
**compiles and runs**.

- **Date:** 2026-09-12
- **Build:** `./gradlew build` → **BUILD SUCCESSFUL in 1m 20s**,
  `build/libs/tcuhc-fabric-mc1.21.1-1.2.8.jar` (692 KB)
- **Server:** `./gradlew runServer` → **`Done (5.286s)!`**
- **Raw evidence:** [`evidence/`](evidence/)

## The gate that matters

`tcuhc.mixins.json` sets `injectors.defaultRequire: 1`, so **any** injector whose
target moved is fatal at launch. The server reaching `Done` is therefore proof
that all 46 mixins applied against real 1.21.1 — including the rewritten
`SubmergedDensityFunction`, the deferred-death task, and the new option.

## Three defects found only by running it

Neither was visible to static analysis. Both are fixed and covered by tests.

### F1 — `lazydfu` made `gradlew runServer` impossible

`build.gradle` carried `modRuntimeOnly 'com.github.astei:lazydfu:0.1.2'`, a 2020
mod for MC 1.16. Two separate fatal failures:

1. It still declares `fabric-loader 0.10.8`. Gradle pulls that in transitively
   and Loom remaps it as a mod, so Knot sees two `FabricLoader` classes:
   `duplicate fabric loader classes found on classpath` → server never starts.
2. With that excluded, its `SchemasMixin` `@Redirect` targets
   `Schemas.method_15471()`, which no longer exists:
   `Critical injection failure ... could not find any targets` → server dies on
   boot.

Removed. It was `modRuntimeOnly`, so it never shipped in the jar and nothing
depends on it. This is almost certainly why nobody had run the dev server on
this branch — `CONTINUATION_DRAFT.md` records a policy of building on GitHub
Actions and deploying the jar, which sidesteps `runServer` entirely.

### F2 — `/uhc debug terrain` crashed on MARINE, the one mode it exists to diagnose

```
java.lang.IncompatibleClassChangeError: net.minecraft.server.MinecraftServer and
net.minecraft.server.MinecraftServer$SubmergedDensityFunction$51cbcf27... 
disagree on InnerClasses attribute
```

`debugTerrain` printed `router.finalDensity().getClass().getSimpleName()`. Under
MARINE those density functions **are** the classes nested inside
`MinecraftServerMixin`; Mixin merges them into `MinecraftServer` under a
generated name without fixing up the `InnerClasses` attribute, and
`getSimpleName()` reads exactly that attribute. `getName()` does not.

Fixed with a `simpleClassName(Class)` helper. Pre-existing — not introduced by
this run — but squarely in scope, since issue 10 is "MARINE generation is broken"
and this is the tool for investigating it. Regression test: T9.

### F3 — `/uhc regen` stopped the server and could not restart it

Found the hard way: an operator ran `/uhc regen` on the live dev server, the
server stopped as designed, and nothing came back. `run/regen-restart.log`:

```
[09/12/2026 Sat 10:10:46.14] regen restart helper started (old pid=28860)
[09/12/2026 Sat 10:10:46.41] starting server
'gradlew.bat' is not recognized as an internal or external command,
```

`gradlew.bat` was in the working directory the whole time. The cause is
`NoDefaultCurrentDirectoryInExePath=1`, which Git Bash / MSYS puts in the
environment. The dev server was launched from a Bash shell, so Gradle inherited
it, the server JVM inherited it, and the `cmd /c restart-server.bat` the mod
spawns inherited it too. That variable tells `cmd` **not** to search the current
directory when resolving a command, so a bare `call gradlew.bat` cannot find a
file sitting right next to it.

`run/start-server.bat` now calls it by explicit path:

```bat
cd /d "%~dp0.."
call "%~dp0..\gradlew.bat" runServer
```

Verified by probe under the failing condition — bare call reproduces
`'gradlew.bat' is not recognized`, explicit path prints `Gradle 8.8`.

Worth knowing about the blast radius: `regenerateTerrain()` launches the helper,
deletes the `preload` marker, then stops the server. The world folder is **not**
wiped at that moment — `tryUpdateSaveFolder` wipes it on the *next* boot when it
finds no marker. So a failed restart is recoverable: the world is intact on disk
but staged for deletion, and simply starting the server again completes the
regen. Nothing was lost here.

The documented snippet in `restart-server.bat` and in `AGENT.md` was carrying the
bare form, so this was a latent trap for anyone following the docs, not just a
local slip.

---

## Verified in game

### T8 — `pregenerateParallelism` (issues 8, 9) — PASS

```
> uhc option pregenerateParallelism add   (x2)
run/uhc.properties:  pregenerateParallelism=4
```

Registered, reachable via `/uhc option`, persists. Default 2 preserved.

### Issues 1 and 9 — pregeneration completes cleanly — PASS

841 chunks pregenerated to `ChunkStatus.FULL`; `run/world/preload` written.

```
grep -c "permanently failed chunk|still waiting on chunk"  ->  0
```

**Zero failed chunks, zero retry-stall lines, no hang near 99%.** The 1.2.6
timeout rework holds up on a real run.

### T6 — stale terrain warning (issue 2) — PASS

`uhc.json` after first boot:

```json
"generatorIdentity": "battleType=NORMAL,levelType=DEFAULT,disableOceanBiomes=true"
```

Then `battleType` was switched to MARINE and the server restarted **without**
regenerating:

```
[WARN] (TC UHC) World terrain was generated with
  [battleType=NORMAL,levelType=DEFAULT,disableOceanBiomes=true]
  but the current settings are
  [battleType=MARINE,levelType=DEFAULT,disableOceanBiomes=true]
```

That is precisely the situation behind *"normal mode seems using the marine
world generator"*, now reported instead of silent. After a regen the warning is
correctly **absent** on the fresh world — no false positive.

### T5 — MARINE sea floor (issue 10) — PASS, with one caveat

The generator swap is live and the fixed debug command shows it:

```
生成器 NoiseChunkGenerator | finalDensity=MinecraftServer$SubmergedDensityFunction$774f...,
                             continents=MinecraftServer$ConstantDensityFunction$41be...
```

Biomes, 625 samples: **100% ocean** — `lukewarm_ocean` 94.4%, `deep_lukewarm_ocean` 5.6%.

Terrain, sampled clear of the spawn platform:

| Sample | Submerged | Floor min/max/avg | Max depth | At/above sea level |
| --- | --- | --- | --- | --- |
| chunks 4–16 (169 pts) | 99.4% | 37 / 63 / **48.8** | 26 | 0.6% |
| chunks 4–32 (841 pts) | 96.9% | 32 / 70 / **50.8** | 31 | 4.0% |
| chunks −34..−6 (841 pts) | 96.6% | 36 / 70 / **51.7** | 27 | 4.0% |

The Python model predicted centre **50**, range **[29.5, 69.4]**. Measured
averages of 48.8 / 50.8 / 51.7 and a range of 32–70 match closely, which
confirms the 1:1 port the faceting measurement was based on was faithful.

The ASCII profile of the third sample shows an island with a rounded, irregular
outline — not the rectangular plateau with straight edges that was reported.

**Caveat, stated plainly.** In-game island coverage is **4.0%** in both
independent samples, against the model's **0.836%**. The two numbers measure
different things: the model measures where the density isosurface crosses y=63,
while `OCEAN_FLOOR` is the top solid block *after* surface rules deposit sand and
gravel, which lifts the column a few blocks. Near the threshold that difference
multiplies the count. The old/new comparison in `test_noise.py` was like-for-like
(same model both sides), so the conclusion "island coverage is preserved" still
holds — but the absolute in-game figure is ~4%, not ~0.8%. If 4% is too much land
for MARINE, lower `SubmergedDensityFunction.CENTER_Y`; each block down cuts it
substantially.

---

---

## Round 2 — operator test pass, and what it found

An operator joined a vanilla 1.21.1 client and walked the tier-2 list. Five of
the six checks passed outright:

| Check | Result |
| --- | --- |
| T5 MARINE sea floor | **PASS** — "flying around it looks good". No faceting. |
| T3 duplicate config books | **PASS** — "all bug fixed, it works great now" |
| T2 KING team selection | **PASS** |
| T7 golden apple count | **PASS** — both the normal and the enchanted apple counted (score board read 金苹果 2) |
| T1 inventory drops | **PASS** — the diamonds hit the ground |
| T4 return to lobby | **PARTIAL** — `/uhc config` worked; `/uhc stop` did not |

Two follow-up defects came out of it. Both are fixed.

### T10 — death left the vanilla respawn panel up instead of going to spectator

Dying by TNT or `/kill` put the "You died — Respawn / Title Screen" panel on
screen. The player only reached spectator after clicking through it.

This was always true, including before the T1 fix — it just became visible once
death stopped being instantaneous. Vanilla sends `DeathMessageS2CPacket` on every
death and the client shows the panel unless the `doImmediateRespawn` gamerule
says otherwise. Nothing in the mod had ever set it.

A UHC death is final, so that panel offers a choice that does not exist. The
match now runs with `doImmediateRespawn` on (set in `initWorlds`, restored to
vanilla in `returnToLobby`), and `onPlayerRespawn` turns the resulting instant
respawn into spectator mode at the place the player died — otherwise vanilla
would hand back a survival player standing at world spawn, which in MARINE is
the middle of the ocean.

The spectator switch is now reachable from two directions — the deferred
post-death task from T1, and the respawn — so `enterSpectatorNow` was made
idempotent and `TaskKeepSpectate` is armed at most once per death.

**The T1 fix is unchanged and still load-bearing.** The switch is still deferred
past vanilla's `if (!isSpectator()) drop(...)`, which is what makes the inventory
drop at all.

### T11 — `/uhc stop` ended nothing and said nothing

`executeStop` called `endGame()` and returned. `endGame()` only tears the HUD
down: no message, no final score board, no winner recorded, no feedback to the
operator who typed the command. From a seat in the game it was indistinguishable
from the command not existing.

It now goes through `stopGameByOperator()`, which settles the match the same way
`onNoTeamWin()` does — `finalizeAliveTimes`, `printFinalScores`, an empty winner
list, `endGame`, `TaskBroadcastData` — under an admin title and message, and
answers the operator either way (including "there is no match running").

### Not changed: MARINE mushroom islands

The operator noted there are no mushroom islands, only ocean and the sea
villages. `mushroom_fields` has never been in `createMarineBiomeSource` — this is
not a regression, and on being asked the operator chose to keep MARINE
ocean-only. The sea villages are deliberate:
`data/minecraft/tags/worldgen/biome/has_structure/village_plains.json` adds every
ocean biome to the village tag. Untouched.


## Still not verified — needs a client or a bot

`T1` (inventory drops on death), `T2` (KING team selection), `T3` (no duplicate
config books), `T4` (return to lobby), `T7` (golden apple stat) all need a player
entity. RCON alone cannot produce one, and there is **no Carpet build for 1.21.1**
(upstream jumps 1.21 → 1.21.2), so fake players were not available either.

The procedures are in [`TEST_PLAN.md`](TEST_PLAN.md); they need ten minutes with
a vanilla 1.21.1 client against the running server. The server is configured for
it: `online-mode=false`, `Runaway_Fancy` pre-opped at level 4.

Static evidence for these five remains what it was — see
[`TEST_RESULTS.md`](TEST_RESULTS.md). T1 in particular changes tick ordering and
deserves the in-game check.

## Reproducing this environment

```bash
# ~/.gradle/gradle.properties   (per-user, so the path is never committed)
org.gradle.java.home=E\:/game/HMCL/prism/java21

./gradlew build
./gradlew runServer
python scripts/rcon.py "uhc debug terrain 10"
```

`run/` is provisioned and gitignored: `eula.txt`, `server.properties` (RCON on
25575, password `tcuhc`), a fast-iteration `uhc.properties`, `ops.json`, and both
halves of the `/uhc regen` restart chain (`restart-server.bat` +
`start-server.bat`).

---

## Round 3 - latest operator feedback

- `./gradlew build`: **PASS** (`BUILD SUCCESSFUL`, JDK 21).
- Automated source/algorithm suites: **97 passed, 0 failed**.
- Dedicated server: **PASS**, reached `Done (0.777s)!`; no new exception or
  datapack-load error. The only matching warning is the expected absent optional
  Carpet fake-player class.
- `/uhc reset`: **PASS over RCON**. The bare command now prints the two reset
  categories instead of returning Brigadier's incomplete-command error.
- Post-game spectator and survival-on-config transitions: **implemented and
  structurally covered, awaiting the next client match**.
- Unbreaking removal: **implemented and structurally covered**. Verifying the
  random chest output still requires opening newly generated bonus chests.

The dev server was restarted with the latest code and left running on
`localhost:25565` for the client checks.
