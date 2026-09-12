# Development tooling

What is configured in this repo, what it does, and what still needs a manual
install. Nothing here was installed for you — the config is committed, the
packages are fetched on first use so you can review them first.

## 1. JDK 21 — configured and working

Loom refuses to configure on Java 17
(`Minecraft 1.21.1 requires Java 21 but Gradle is using 17`).

This machine uses the Temurin 21.0.12.1 runtime shipped with the HMCL/Prism
launcher. It is a full JDK, `javac` included:

```properties
# ~/.gradle/gradle.properties   (per-user, so the absolute path is never committed)
org.gradle.java.home=E\:/game/HMCL/prism/java21
```

`./gradlew build` → BUILD SUCCESSFUL; `./gradlew runServer` → `Done`.

Caveat: `./gradlew --version` still prints the launcher JVM (17). That is normal
— `org.gradle.java.home` applies to the build daemon. Trust an actual build.

### `lazydfu` was removed from the dev runtime

`build.gradle` carried `modRuntimeOnly 'com.github.astei:lazydfu:0.1.2'`, a 2020
mod for MC 1.16 that made `runServer` impossible in two independent ways: it
drags in `fabric-loader 0.10.8` (→ "duplicate fabric loader classes found on
classpath") and its `SchemasMixin` targets a method that no longer exists (→
crash during mixin apply). It was `modRuntimeOnly`, so it never shipped.

## 2. Claude Code skills — `.claude/skills/`

Written for this repository, not generic. They load automatically when the task
matches their description.

| Skill | Covers |
| --- | --- |
| `tcuhc-dev` | build commands, dev-server setup, driving a match, symptom → first-check table |
| `fabric-mixin` | mixin conventions, injector choice, registry freezing, debugging a non-applying injector |
| `tcuhc-gameplay` | adding an option / `EnumMode` / `Task` / `/uhc` subcommand, and the seven switch sites a mode must touch |
| `tcuhc-worldgen` | structures, features, deferred placement, the MARINE generator, pregeneration tuning |

### Third-party alternative

There is a community plugin with generic NeoForge/Fabric modding guidance
([chouzz/minecraft-mod-dev](https://github.com/chouzz/minecraft-mod-dev)):

```text
/plugin marketplace add chouzz/minecraft-mod-dev
/plugin install minecraft-mod-dev@chouzz-plugins
```

It is broad rather than deep and knows nothing about TC-UHC's conventions. The
repo-local skills above should be the primary source; add the plugin only if you
want general 1.21+ modding reference material.

## 3. MCP server — `.mcp.json`

One server is configured:

```json
{ "mcpServers": { "minecraft-dev": { "command": "npx", "args": ["-y", "@mcdxai/minecraft-dev-mcp"] } } }
```

[`@mcdxai/minecraft-dev-mcp`](https://github.com/MCDxAI/minecraft-dev-mcp) —
21 tools for exactly the work this branch is doing:

- download, remap, and decompile any Minecraft 1.14+ version on demand
- translate symbols between Yarn, Mojmap, Intermediary, and obfuscated names
- **diff two Minecraft versions** — the fastest way to answer "what replaced
  this 1.18 API in 1.21.1"
- validate Mixin, Access Widener, and Access Transformer files
- analyse Fabric/Quilt/Forge/NeoForge mod jars

Yarn is supported through 1.21.11, so **1.21.1 is covered**.

First use downloads and decompiles Minecraft: **~500 MB of cache per version**,
under `%APPDATA%\minecraft-dev-mcp` on Windows. Expect the first call to take
several minutes. Set `CACHE_DIR` in the `env` block to relocate it.

Claude Code will ask you to approve the project-scoped server the first time the
repo is opened. Requires Node.js on `PATH`. If `npx` is not resolved on Windows,
change `"command": "npx"` to `"command": "npx.cmd"`.

### Alternative, if the above is not enough

[`@adhisang/minecraft-modding-mcp`](https://github.com/adhi-jp/minecraft-modding-mcp)
— 41 tools, overlapping scope, with a stronger `validate-project` /
`validate-mixin` story and registry inspection. Drop-in replacement:

```json
"minecraft-modding": { "command": "npx", "args": ["-y", "@adhisang/minecraft-modding-mcp"] }
```

Running both at once is wasteful (two decompile caches, 60+ tools). Pick one.

## 4. In-game testing — `scripts/rcon.py`

Standard-library-only Source RCON client. **No dependencies, nothing to
install.** Preferred over the available Minecraft RCON MCP servers, which either
require a Docker-hosted server or an unrelated third-party API key.

Enable RCON in the dev server first:

```properties
# run/server.properties
enable-rcon=true
rcon.port=25575
rcon.password=tcuhc
```

Then:

```bash
python scripts/rcon.py "list"
python scripts/rcon.py "uhc config" "uhc option gameMode set"
python scripts/rcon.py --file scripts/scenarios/inspect-state.txt
```

Connection settings resolve from flags → `RCON_HOST`/`RCON_PORT`/`RCON_PASSWORD`
→ `run/server.properties` → `127.0.0.1:25575`.

Scenario files live in `scripts/scenarios/`:

| File | Purpose |
| --- | --- |
| `inspect-state.txt` | read-only dump of match state |
| `quick-match.txt` | shrink the world and enter configuration |
| `marine-regen.txt` | notes + commands for switching to MARINE |

## 5. Repo health check — `scripts/audit_mixins.py`

Diffs `tcuhc.mixins.json` against the mixin classes on disk. A mixin that is not
registered compiles but never applies, and nothing else in the toolchain warns
about it.

```bash
python scripts/audit_mixins.py
```

Current state at v1.2.8: **46 registered, 46 on disk — clean.** It was 23/50
before the 1.2.5 sync; `docs/design_principle/05-invariants-and-findings.md`
records what that backlog had disabled.

Exit codes: `0` clean, `1` unregistered classes exist, `2` the config lists a
class that is missing.

## 6. Not configured, and why

| Considered | Verdict |
| --- | --- |
| JUnit / a `src/test` source set | The mod has no pure-logic layer worth unit-testing in isolation; almost everything needs a live `MinecraftServer`. Adding a test harness is a real design decision — ask before doing it. |
| `rgbkrk/rcon-mcp` | Requires the server to run in a Docker container named `mc`. Does not fit a Loom `runServer` workflow. |
| `Peterson047/Minecraft-MCP-Server` | Needs a clone, a venv, and a `GEMINI_API_KEY`. Not appropriate for this project. |
| A filesystem or git MCP | Claude Code already has these built in. |
