# AGENT.md

Operating guide for AI coding agents working on **tcuhc-fabric**.
Format follows the [AGENTS.md](https://agents.md/) convention.

---

## Project

TC-UHC: a **server-side-only** Fabric mod that turns a Minecraft server into an
Ultra Hardcore game host — match lifecycle, team formation, 8 game modes, 3
battle types, custom structures, an in-game book configuration UI, and world
pregeneration.

Downstream of [Gamepiaynmo/TC-UHC](https://github.com/Gamepiaynmo/TC-UHC) via
[Fallen-Breath/tcuhc-fabric](https://github.com/Fallen-Breath/tcuhc-fabric).
Active branch: **`1.21.1`**. `master` is the old 1.18.1 line — do not port
changes onto it.

| | |
| --- | --- |
| Minecraft | 1.21.1 |
| Mappings | Yarn `1.21.1+build.1` |
| Loader | Fabric Loader `0.15.11` |
| Fabric API | `0.116.9+1.21.1` (api-base, biome-v1, resource-loader-v0, lifecycle-events-v1, all JiJ `include`d) |
| Build | Gradle 8.8 + Fabric Loom 1.7.1 |
| **JDK** | **21 — mandatory, see below** |
| Mod version | `1.2.9` |
| Mod id / group | `tcuhc` / `me.fallenbreath` |
| Entrypoint | `me.fallenbreath.tcuhc.TcUhcMod` (`main` only, no client) |

---

## Read before editing

`docs/design_principle/` is the analysed architecture of the whole package.
Start with `README.md`, then the file covering your area. `05-invariants-and-findings.md`
lists known defects — check it before reporting a bug as new.

Repo-local Claude Code skills in `.claude/skills/` (`tcuhc-dev`,
`fabric-mixin`, `tcuhc-gameplay`, `tcuhc-worldgen`) carry the task-level detail
and load on demand.

---

## Build and verify

```bash
./gradlew build        # compile + remap -> build/libs/tcuhc-fabric-mc1.21.1-*.jar
./gradlew runServer    # dev server in ./run
./gradlew clean build
python scripts/audit_mixins.py   # after ANY change under mixins/
```

### JDK 21 is a hard prerequisite — already configured here

Loom refuses to configure on anything older:
`Minecraft 1.21.1 requires Java 21 but Gradle is using 17`.

**This machine is set up and verified.** Temurin 21.0.12.1 lives at
`E:/game/HMCL/prism/java21` — an HMCL/Prism launcher runtime, but a full JDK
(`javac` present, `release` says `IMAGE_TYPE="JDK"`). Gradle is pointed at it
from the **per-user** file, so the absolute path never reaches the repo:

```properties
# ~/.gradle/gradle.properties
org.gradle.java.home=E\:/game/HMCL/prism/java21
```

Note that `./gradlew --version` still reports the *launcher* JVM (17). That is
expected, not a fault — `org.gradle.java.home` applies to the daemon that runs
the build. Confirm with a real build, never with `--version`.

If the Java 17 error comes back, that file is missing or the path moved.

`.github/workflows/gradle.yml` (ubuntu-latest, JDK 21) remains the release gate,
and `CONTINUATION_DRAFT.md` records a standing policy of treating it as
authoritative — but the local loop works now, so use it.

### There are no unit tests

No `src/test`, no JUnit, no assertion harness. Verification means **running a
match**. Do not invent a test source set without asking — almost nothing here is
testable without a live `MinecraftServer`.

---

## Test game environment

This is the environment a change must be verified in. It is not optional
ceremony: `defaultRequire: 1` means a broken injector kills the server at
launch, and most of this mod's behaviour is invisible to the compiler.

### Target

| | |
| --- | --- |
| Kind | Fabric **dedicated server** (the mod declares `"environment": "server"`) |
| Minecraft | 1.21.1 exactly — not 1.21.4, not 1.21.x-latest |
| Java runtime | 21 |
| Client | any **vanilla** 1.21.1 client. This is a hard product constraint: no custom packets, blocks, or items, so a vanilla client must always be able to join. Test with one. |
| Working dir | `run/` (created by `runServer`, gitignored) |

### Provisioning `run/` before first launch

`./gradlew runServer` creates the directory. Then:

1. **`run/eula.txt`** → `eula=true`

2. **`run/server.properties`**

   ```properties
   online-mode=false        # join with arbitrary names / several clients
   enable-rcon=true
   rcon.port=25575
   rcon.password=tcuhc
   level-name=world
   max-players=20
   view-distance=8          # keep low; pregeneration is the bottleneck
   ```

   Leave `level-type` alone — the mod drives terrain itself.

3. **Operator** — `op <yourname>` in the console. Most of `/uhc` needs
   permission level 2.

4. **Regen restart helpers**, copied into `run/` next to the world folder.
   `UhcGameManager.regenerateTerrain()` looks for `restart-server.sh` (POSIX) or
   `restart-server.bat` / `.cmd` (Windows) and throws
   `IllegalStateException: Missing regen restart helper` if absent, so
   **`/uhc regen` is untestable without one**. The repo root ships both
   `restart-server.sh` and `restart-server.bat`.

   Each of them hands off to a **`start-server.sh` / `start-server.bat` that you
   must write** — the launch command is environment-specific and the repo does
   not ship one. For a Loom dev server:

   ```bat
   @echo off
   cd /d "%~dp0.."
   call "%~dp0..\gradlew.bat" runServer
   ```

   Without it the server stops on `/uhc regen` and never comes back; the reason
   is logged to `run/regen-restart.log`.

   **Call `gradlew.bat` by explicit path, not bare.** If the server JVM was
   started from Git Bash, `NoDefaultCurrentDirectoryInExePath=1` is inherited
   down to the helper's `cmd`, which then refuses to search the working
   directory: a bare `call gradlew.bat` dies with `'gradlew.bat' is not
   recognized` even though it is sitting right there. This cost one silent
   failed regen.

5. **`uhc.properties`** appears in `run/` on first boot. Edit it directly to set
   up a scenario instead of clicking through the config book.

6. Fake players: **no Carpet build exists for MC 1.21.1** — upstream goes 1.21
   straight to 1.21.2. So any test needing a player entity needs a real vanilla
   1.21.1 client; RCON cannot produce one. The `1.21-1.4.147+v240613` jar might
   apply, but that is untested here.

   `run/` on this machine is already provisioned, `ops.json` included
   (`Runaway_Fancy`, level 4, offline UUID since `online-mode=false`).

   If a compatible Carpet ever lands, add it as `modRuntimeOnly` to get
   `/player Bot1 spawn`: there is a dedicated
   `compat/carpet/EntityPlayerMPFakeMixin` so bot deaths enter the UHC chain.
   Carpet is not a declared dependency on this branch.

### Fast iteration profile

Full defaults mean a 2000-block border and a multi-minute pregeneration. For
testing, set in `run/uhc.properties`:

```properties
borderStart=300
gameTime=600
borderStartTime=120
borderEndTime=400
netherCloseTime=400
caveCloseTime=500
```

### Driving a match

```text
/uhc config                 # enter configuring, receive the config book
/uhc option gameMode set    # then type the value in chat (e.g. KING)
/uhc select 0               # each player picks: 0-7 colour, 8 observer, 9 random
/uhc forceStart             # twice - skips waiting for pregeneration
/uhc adjust kill <player>   # force win conditions without fighting
/uhc stop

/uhc preset save <name>     # snapshot the whole option set
/uhc preset load <name>     # then `confirm` to apply
/uhc preset diff <name>     # what would change
/uhc debug biome <radius>   # sample biomes, for verifying generator swaps
/uhc debug terrain <radius>
```

Scripted, via RCON — no dependencies:

```bash
python scripts/rcon.py "list" "uhc"
python scripts/rcon.py --file scripts/scenarios/inspect-state.txt
```

### Verification checklist for a gameplay change

- [ ] Server reaches "Done" with no mixin error at launch
- [ ] A **vanilla** 1.21.1 client connects
- [ ] `/uhc config` gives exactly one config book; editing an option updates it
      **in place** (no duplicates)
- [ ] Every player can select a side in the mode under test
- [ ] `/uhc forceStart` starts; countdown, kit, team colours in TAB
- [ ] The behaviour you changed actually happens
- [ ] Match end prints the score board with non-zero stats
- [ ] Server log is clean — the mod's `onXxx` hooks and `tick()` swallow
      exceptions via `printStackTrace()`, so **a silent failure still leaves a
      stack trace on stdout**. Read it.

### Things that need a world wipe

Terrain is baked into `run/world/`. These require `/uhc regen` (twice to
confirm; it restarts the server) or deleting `run/world/`:

- changing `battleType` — especially to or from `MARINE`
- changing `levelType` (works again as of 1.2.5 — the mixin was reported inert in
  an earlier audit and has since been ported)
- changing `disableOceanBiomes` (1.2.7)
- any structure, feature, biome-tag, or generator change

Reusing a MARINE world for a NORMAL match is the most likely explanation for
"normal mode is using the marine generator".

---

## Conventions

- **Java 8 source level** is still declared in `build.gradle` while the mixin
  config is `JAVA_17` and CI runs JDK 21. Match the surrounding code; if you need
  a newer language feature, raise `sourceCompatibility`/`targetCompatibility`
  properly rather than working around it.
- **Tabs** for indentation, Allman braces in the newer files, K&R in the
  inherited ones. Match the file you are in.
- **Player-facing strings are Simplified Chinese, hardcoded. Log messages are
  English.** There is no lang file. Do not start a partial i18n migration.
- Files inherited from upstream keep their
  `/* From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC */` header.
- Comments explain *why*, especially around 1.21.1 workarounds. The existing
  ones are load-bearing — do not strip them.
- Mixins delegate; logic lives in `UhcGameManager` / `UhcPlayerManager` / a
  helper, never in the injector body.

---

## Non-negotiables

1. **A mixin missing from `src/main/resources/tcuhc.mixins.json` is dead code.**
   It compiles. It never runs. Run `python scripts/audit_mixins.py` after every
   change under `mixins/`. The repo is currently **clean at 46/46** — it spent
   most of the port with half the mixins inert, so keep the audit green.
2. **A custom structure/feature type missing from `mixins/core/RegistriesMixin`'s
   `static {}` block** fails at datapack load, not at compile time.
3. **Never touch a `BlockEntity` during chunk generation.** Queue it, flush in
   `ServerTickEvents.END_SERVER_TICK`.
4. **Never block the server thread on a chunk future.** Poll holder status —
   that is what `TaskPregenerate` does and why.
5. **Never cache a `ServerPlayerEntity`.** `UhcGamePlayer` holds a `UUID`;
   resolve via `getRealPlayer()`. Players reconnect mid-match.
6. **Stats only record while `isGamePlaying` is true.**
7. **Adding an `EnumMode` means editing seven switch statements** — see
   `docs/design_principle/02-gameplay-and-config.md` §2. Missing branches are
   silent.
8. **Dynamic registries** (biome, enchantment, damage type, loot table) resolve
   through a `DynamicRegistryManager`, never `Registries.X`.
9. **Do not weaken `injectors.defaultRequire` to 0** to make a build pass.
10. **`remappedSrc/` is stale 1.18-era decompiler output.** Not a source set, not
    compiled. Never edit it, never cite it as a 1.21.1 reference.

---

## Boundaries

- Do not commit or push unless asked.
- Do not merge `1.21.1` into `master`, or port 1.21.1 work back to it.
- Do not commit `run/`, `build/`, `.gradle/`, or an absolute
  `org.gradle.java.home` in the repo's `gradle.properties`.
- Do not restructure the package layout or introduce a framework (DI, an event
  bus, a config library) — the mod is deliberately small and hand-rolled.
- Do not add client-side code or custom network packets.
- `/uhc regen` **deletes the world folder and stops the server**. Never run it
  against anything but a throwaway dev world.
