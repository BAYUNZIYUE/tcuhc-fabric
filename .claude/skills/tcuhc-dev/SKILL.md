---
name: tcuhc-dev
description: Build, run, and in-game test the TC-UHC Fabric mod for Minecraft 1.21.1. Use whenever building this repo, launching a dev server, reproducing a UHC bug, verifying a gameplay change, or deciding between the local Gradle loop and the GitHub Actions build.
---

# TC-UHC development loop

Server-only Fabric mod, Minecraft **1.21.1**, Yarn `1.21.1+build.1`, Loom 1.7.1,
Gradle 8.8. Branch `1.21.1` is the active line of work.

## Hard prerequisite: JDK 21

Loom refuses to configure otherwise:

```
Failed to setup Minecraft, java.lang.IllegalStateException:
Minecraft 1.21.1 requires Java 21 but Gradle is using 17
```

Check before anything else:

```bash
./gradlew -version        # look at the "JVM:" line, not just `java -version`
```

If it is not 21, point Gradle at a JDK 21 for this project only — do not change
the machine's default JDK:

```bash
# gradle.properties (already gitignored?  no - use ~/.gradle/gradle.properties instead)
org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-21.x.x-hotspot
```

Prefer `~/.gradle/gradle.properties` so the path never gets committed.

## Commands

```bash
./gradlew build              # compile + remap; jar lands in build/libs/
./gradlew runServer          # dev server in ./run  (needs JDK 21)
./gradlew clean build
./gradlew build --stacktrace # first stop when a mixin fails to apply
```

There is **no test source set and no JUnit** in this project. "Testing" means
running the server and driving a match. Do not invent a `src/test` layout
without asking.

## First-run dev server setup

`./gradlew runServer` creates `run/`. It is gitignored. Before the first launch:

1. `run/eula.txt` → `eula=true`
2. `run/server.properties`:
   - `online-mode=false` (so you can join with any name / multiple clients)
   - `enable-rcon=true`, `rcon.port=25575`, `rcon.password=<something>`
   - `level-name=world`
   - leave `level-type` alone — the mod drives terrain itself
3. `run/ops.json` — add your UUID at level 4, or run `op <name>` in the console.
   Most of `/uhc` requires permission level 2.
4. **`run/restart-server.sh`** (or `.bat` on Windows) next to the world folder.
   `/uhc regen` throws `IllegalStateException: Missing regen restart helper`
   without it. The repo root has a reference `restart-server.sh`.

`uhc.properties` is written to the server's working directory on first boot and
holds every option. Edit it directly to set up a scenario without clicking
through the book.

## Driving a match

Minimum viable loop, from the server console or in-game as an op:

```
/uhc config                 # gives the config book, enters configuring state
/uhc option gameMode set    # then type the value in chat, e.g. KING
/uhc option borderStart add # or use the book's < > controls
/uhc select 0               # each player picks a team (0-7 colours, 8 observer, 9 random)
/uhc forceStart             # twice - skips waiting for pregeneration
/uhc forceStart
```

Speed-ups for testing:

- Set `borderStart` low (e.g. 300) so pregeneration finishes in seconds.
- Set `gameTime` low to reach the end-of-match scoring path quickly.
- `/uhc adjust kill <player>` / `/uhc adjust resu <player>` to drive win
  conditions without fighting.
- `/uhc stop` ends the match immediately.
- Carpet fake players (`/player Bot1 spawn`) work — there is a dedicated
  `compat/carpet/EntityPlayerMPFakeMixin` so their deaths enter the UHC chain.
  Carpet is not a declared dependency on this branch; add it as
  `modRuntimeOnly` in `build.gradle` if you want bots.

**Switching `battleType` requires `/uhc regen`.** Terrain lives in the world
folder; the option lives in `uhc.properties`. Without a regen you get the old
terrain with the new rules.

## Where to look when something does not work

| Symptom | First check |
| --- | --- |
| Server crashes at launch with a mixin error | an injector's target moved in 1.21.1; `defaultRequire: 1` makes this fatal on purpose |
| A feature silently does nothing | is its mixin listed in `src/main/resources/tcuhc.mixins.json`? run `python scripts/audit_mixins.py` |
| A stat stays at 0 | `PlayerStatistics.addStat` is a no-op unless `isGamePlaying` |
| "Unknown structure type tcuhc:x" at world load | missing from the `static {}` block in `mixins/core/RegistriesMixin` |
| Pregeneration sits at 99% | grep the log for `Pregenerate permanently failed chunk` / `still waiting on chunk` |
| An exception vanished | `UhcGameManager`'s `onXxx` hooks and `tick()` swallow exceptions via `printStackTrace()`; check stdout, not chat |

## Fallback: GitHub Actions as the compile gate

`.github/workflows/gradle.yml` builds on `ubuntu-latest` with JDK 21 and uploads
`build/libs/` as an artifact. `CONTINUATION_DRAFT.md` records a standing policy
of treating the GitHub build as authoritative. If no local JDK 21 is available,
push the branch and read the workflow result rather than guessing at
compilation. Then download the artifact jar and drop it into a real server's
`mods/` folder to verify behaviour.

## Read first

`docs/design_principle/` holds the architecture, invariants, and a list of known
defects. Read `05-invariants-and-findings.md` before concluding that something
is a new bug.
