# TC UHC for Fabric

TC UHC is a server-side Fabric mod that manages complete Minecraft Ultra Hardcore matches. It provides team selection, multiple game and battle modes, configurable match timing and borders, custom world generation and loot, world pre-generation, spectator handling, live match administration, and reusable configuration presets. Vanilla clients can join without installing the mod.

This branch targets **Minecraft 1.21.1** and currently builds **TC UHC 1.2.9**.

## Features

- Eight match modes: Normal, Solo, Boss, Ghost, Bomber, King, Hunter, and Ghost Hunter.
- Normal, Marine, and Icarus battle types with mode-specific terrain and equipment behavior.
- In-game books for team selection, match configuration, and administrative adjustment.
- Configurable teams, difficulty, weather, borders, match phases, loot, ores, merchants, mobs, and pre-generation.
- Overworld and optional Nether pre-generation with progress and estimated-time reporting.
- Saved configuration presets with list, inspect, compare, load, and delete operations.
- Custom structures, trades, recipes, loot, death handling, scoring, and post-match spectator support.
- Dedicated-server operation with no client-side mod requirement.

## Requirements

| Component | Version |
|---|---|
| Minecraft Java Edition | 1.21.1 |
| Fabric Loader | 0.15.0 or later |
| Java runtime | 21 |

The required Fabric API modules are bundled into the produced mod JAR by the build configuration.

## Installation

1. Install a Fabric 1.21.1 dedicated server with Java 21.
2. Download the release JAR named `tcuhc-fabric-mc1.21.1-<version>.jar`.
3. Place the JAR in the server's `mods` directory.
4. Start the server. TC UHC creates and maintains `uhc.properties` in the server working directory.
5. Join the server as an operator and run `/uhc config` to prepare the lobby and configure the next match.

Back up existing worlds before using `/uhc regen`; regeneration deliberately replaces the current match worlds.

## Basic operation

The usual match flow is:

1. Run `/uhc config` and set the match options.
2. Have players select a team or observer status using the selection book.
3. Wait for world pre-generation to finish, when enabled.
4. Run `/uhc start` twice to confirm and begin the match.
5. Use `/uhc stop` only when an operator must end a running match.
6. After the result is shown, players remain in Spectator mode to inspect the battlefield. Run `/uhc config` when everyone should return to the lobby and Survival mode.

Complete command documentation is available in [English](docs/commands/en.md) and [简体中文](docs/commands/zh-CN.md).

## Configuration

Settings can be changed through the in-game configuration book or `/uhc option <name> <add|sub|set>`. They are persisted to `uhc.properties`. Some settings have delayed activation:

- World-generation and loot-frequency changes require `/uhc regen`.
- Pre-generation startup settings require a server restart.
- Gameplay settings normally apply to the next match.

Use `/uhc preset save <name>` to save a complete configuration and `/uhc preset load <name>` to restore it. The command manuals list every option, range, default, and preset operation.

## Building from source

Java 21 is required. On Windows:

```powershell
.\gradlew.bat clean build
```

On Linux or macOS:

```bash
./gradlew clean build
```

Compiled artifacts are written to `build/libs/`. The deployable JAR for the current version is:

```text
build/libs/tcuhc-fabric-mc1.21.1-1.2.9.jar
```

Change `mod_version` in `gradle.properties` before producing a new release. Release versions must use the stable semantic format `major.minor.patch`, for example `1.2.10`.

## Testing and continuous integration

Run the dependency-free repository tests with:

```bash
python tests/run_all.py
```

Run a complete compile and package check with:

```bash
./gradlew clean build
```

GitHub Actions runs repository checks, the Gradle build, packaging, and a dedicated-server startup smoke test on every push and pull request. See [tests/README.md](tests/README.md) for test scope and local smoke-test safety notes.

## Build and release workflow

The **Build or Release** workflow always validates the version, runs all tests, builds both JARs, and smoke-tests a server. It can be dispatched manually or triggered by pushing a matching `v<version>` tag.

- Select `create_release: false` to compile and retain downloadable workflow artifacts without publishing a release.
- Select `create_release: true` to create a `v<version>` GitHub tag/release and attach the binary and sources JARs after all checks pass.

The workflow rejects malformed versions and refuses to replace an existing release tag. With an authenticated [GitHub CLI](https://cli.github.com/), the same choices can be dispatched locally after committing all changes:

```bash
python scripts/github_release.py --build-only
python scripts/github_release.py --release
```

## Documentation

- [Command reference — English](docs/commands/en.md)
- [命令参考 — 简体中文](docs/commands/zh-CN.md)
- [Design principles](docs/design_principle/README.md)
- [Development and verification notes](docs/agent_run/)

## Credits and license

TC UHC originates from [Gamepiaynmo/TC-UHC](https://github.com/Gamepiaynmo/TC-UHC) and has been maintained and ported by the contributors listed in `fabric.mod.json`.

The project is distributed under the terms in [LICENSE](LICENSE).

---

## 中文简介

TC UHC 是一个服务端 Fabric 模组，用于组织完整的极限生存竞技对局。当前分支支持 Minecraft 1.21.1、Fabric Loader 0.15.0 及以上版本和 Java 21；原版客户端无需安装模组即可加入。安装、编译、自动测试和发布流程见上文，完整中文命令说明请参阅[简体中文命令参考](docs/commands/zh-CN.md)。
