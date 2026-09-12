# Handoff — 2026-09-12

Written for the next agent picking this up. The previous session ran out of
context. Everything below is verified state, not plan.

Read [AGENT.md](../../../AGENT.md) first for the project rules, then this file
for where the work actually stands.

---

## 1. Resume in 60 seconds

```bash
# branch 1.21.1, HEAD 3294174 (v1.2.8). ALL WORK IS UNCOMMITTED.
git status --short

./gradlew build                       # green
./gradlew runServer                   # dev server in run/
python docs/agent_run/2026-09-12-issue-fixes/tests/run_all.py   # 97 pass / 0 fail
python scripts/rcon.py "list"         # talk to the running server
```

Dev server: `localhost:25565`, `online-mode=false`, join as **`Runaway_Fancy`**
(pre-opped level 4 by offline UUID in `run/ops.json`). Client must be **exactly
1.21.1**. RCON on 25575, password `tcuhc`.

**Nothing has been committed or pushed.** The user has not asked for it. Do not
commit unless asked.

---

## 2. How this session got here

Four rounds, in order:

1. **Analysis.** Read the whole package, produced `docs/design_principle/`
   (6 files) — architecture, gameplay/config, worldgen, mixin+porting, and an
   invariants/findings file. Set up `.claude/skills/` (4 repo-local skills),
   `.mcp.json`, `scripts/` (`rcon.py`, `audit_mixins.py`), and `AGENT.md`.
2. **Branch sync.** Moved to `1.21.1` and re-verified every finding against it.
   Most notably the "27 of 50 mixins are inert" problem had already been fixed
   upstream by 1.2.5; the audit is clean at 46/46 now.
3. **Issue-report fixes.** Worked `docs/user_report_issue/2026_9_6.md` into tasks
   T1–T8 and fixed them. See [PLAN.md](PLAN.md) and [CHANGES.md](CHANGES.md).
4. **Build + live testing.** The user supplied a JDK 21 path, which unblocked
   local compilation for the first time. Running the server immediately turned up
   three defects static analysis could not see (F1/F2/F3), and then a real
   operator test pass turned up two more (T10/T11). All five are fixed.

The important shape of it: **every single round of actually running the thing
found bugs that reading the code had not.** Budget for that.

---

## 3. Current status

| | |
| --- | --- |
| Branch | `1.21.1`, HEAD `3294174` (v1.2.8) |
| Build | **green** — `build/libs/tcuhc-fabric-mc1.21.1-1.2.8.jar` |
| Tier-1 tests | **97 passed, 0 failed** (`test_java_syntax` 26, `test_structural` 62, `test_noise` 9) |
| Mixin audit | **46 / 46 registered** — keep it there |
| Dev server | running, fresh MARINE world, 0 players |
| Committed | **nothing** |

### Fixed and confirmed by the user in game

| Task | Issue | Status |
| --- | --- | --- |
| T1 | 5 — no inventory drops on death | **PASS** — root cause was the spectator switch running before vanilla's `if (!isSpectator()) drop(...)` |
| T2 | 4 — `randomTeams` off, no team choice | **PASS** — `case KING:` was missing from `getPlayerBook` |
| T3 | 6 — duplicate config books | **PASS** — "works great now" |
| T5 | 10 — MARINE ocean generation | **PASS** — "flying around it looks good", no faceting |
| T7 | 3 — no golden apple count | **PASS** — both normal and enchanted counted |
| T6 | 2 — stale terrain | **PASS** — warning fires on mismatch, absent on a fresh world |
| T8 | 8, 9 — pregeneration speed | **PASS** — `pregenerateParallelism` option works, persists |
| F1 | — | `lazydfu` removed; it made `runServer` impossible two different ways |
| F2 | — | `/uhc debug terrain` crashed on MARINE via `getSimpleName()` |
| F3 | — | `/uhc regen` could not restart the server. **Verified end-to-end**: a real regen at 10:47 restarted cleanly and reached `Done (5.373s)!` |

### Fixed, built, running — but NOT yet verified in game

**This is the first thing to do.** Both landed after the user's last test pass.

| Task | What | Where |
| --- | --- | --- |
| **T10** | Death showed the vanilla "Respawn / Title Screen" panel instead of going straight to spectator | `UhcGameManager.initWorlds` / `returnToLobby`, `UhcPlayerManager.onPlayerRespawn` + `enterSpectatorNow` + `returnToDeathPos`, `UhcGamePlayer.spectateTaskArmed` |
| **T11** | `/uhc stop` ended nothing and said nothing | `UhcGameManager.stopGameByOperator()`, `UhcGameCommand.executeStop` |

T11's negative branch is already confirmed over RCON (`/uhc stop` with no match
running now answers instead of being silent). The positive branch needs a live
match.

**Known risk in T10, flag it while testing:** the respawn teleport fires in the
same tick vanilla finishes the respawn. If there is any position flicker or
desync, defer it one tick with a `TaskOnce` the way the death-side switch already
is. The tradeoff is a one-tick window where the player is visible at world spawn
in survival.

---

## 4. Open issues, in the order worth doing them

### 4.1 Verify T10 and T11 in game — *blocking, 10 minutes*

Join, start a match, `/kill`, watch for the panel. Then `/uhc stop` mid-match and
check the score board prints. Procedures in [TEST_PLAN.md](TEST_PLAN.md).

### 4.2 B4 — enchantment lookups use the root registry — *likely crash, 2 sites*

```java
// WRONG - Registries.REGISTRIES is the root registry; Enchantment went dynamic in 1.21
Registry<Enchantment> r = (Registry<Enchantment>) Registries.REGISTRIES.get(RegistryKeys.ENCHANTMENT.getValue());
if (r == null) throw new IllegalStateException("Missing enchantment registry");
```

Still present in **both** places (`grep` confirms 2 hits):

- `task/TaskTitleCountDown.java:144` — ICARUS elytra, **KING crown**, hunter compass
- `util/PlayerItems.java:143` — moral items

`BonusChestFeature.getEnchantment` already shows the correct form:
`world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(key)`.

This is high priority because **KING mode is actively being played** and the
crown path runs at match start. It is `throw`-on-null, so it fails loudly.
Reaching it needs a match start in the affected mode, which is why no test run so
far has hit it.

### 4.3 B3 — bonus chest stats are always 0, and there are two implementations

`gen/feature/BonusChestGenerator.java` is a complete placer whose `register()` is
**never called** — `TcUhcMod.onInitialize()` uses `BonusChestFeature` instead
(confirmed: 0 references to `BonusChestGenerator` in `TcUhcMod`).

`ChestBlockEntityMixin` credits `CHEST_FOUND` / `EMPTY_CHEST_FOUND` only when the
chest carries loot-table key `tcuhc:bonus_chest/bonus` or the Chinese custom names
`奖励宝箱` / `空宝箱`. The live path sets neither — its own
`BONUS_CHEST_NAME = Text.literal("Bonus Chest")` constants are unused and, being
English, would not match anyway.

Pick one implementation. Preferred: make `BonusChestFeature` set the loot-table
key, which also makes the chest re-rollable.

### 4.4 B7 — `automaticFormTeams` ignores colour choice; empty teams can crash KING

Verified by reading, not yet reproduced. See
[05-invariants-and-findings.md](../../design_principle/05-invariants-and-findings.md).

### 4.5 Smaller / lower confidence

- **B8** — a list of small items in the findings file.
- **Issue 9, chunk loading** — bounded and logged since 1.2.6, and a real
  841-chunk run showed **zero** failed chunks. Going further needs actual log
  lines from a failure the user can reproduce.
- **T8 default** — `pregenerateParallelism` defaults to 2. Whether that is right
  for this hardware is empirical; the user has been running 4.
- **MARINE island coverage** — the Python model says 0.836% of area reaches sea
  level; in game it measures **4.0%** across two disjoint samples. Explained, not
  a bug: the model measures the density isosurface, `OCEAN_FLOOR` measures the
  top solid block *after* surface rules deposit sand and gravel. If 4% is too much
  land for a naval mode, lower `SubmergedDensityFunction.CENTER_Y` — each block
  down cuts it substantially. The old-vs-new comparison was like-for-like, so
  "island density preserved" still holds.

### 4.6 Explicitly out of scope — do not start these unasked

- The **New idea** section of the user report (Bingo + UHC + Bedwars, Mine war).
  Idea 3, presets, already shipped in 1.2.8 as `/uhc preset`; the "3 buffer slots"
  variation was not implemented.
- **MARINE mushroom islands.** The user was asked directly and chose
  **"leave ocean-only"**. `mushroom_fields` has never been in
  `createMarineBiomeSource`, so this is not a regression. Do not add it.
- **Sea villages are deliberate** —
  `data/minecraft/tags/worldgen/biome/has_structure/village_plains.json` adds
  every ocean biome to the village tag. The user explicitly wants them kept.

---

## 5. Standing decisions to honour

- **"Write config, don't auto-run installers."** The user chose this at the
  start. Third-party MCP servers and plugins are *configured* in `.mcp.json` /
  documented in `docs/tooling/README.md`, but must be fetched and approved by the
  user. Nothing third-party executes without them saying so.
- **Do not commit or push** unless asked.
- **Do not merge `1.21.1` into `master`** or port work back to it — `master` is
  the old 1.18.1 line.
- Player-facing strings are **hardcoded Simplified Chinese**, log messages are
  **English**. There is no lang file; do not start a partial i18n migration.
- `injectors.defaultRequire: 1` stays at 1. Never weaken it to make a build pass.

---

## 6. Environment traps that already cost time

Every one of these was hit for real in this session.

| Trap | What happens | Do this |
| --- | --- | --- |
| **JDK** | Loom hard-refuses Java 17: `Minecraft 1.21.1 requires Java 21 but Gradle is using 17` | `~/.gradle/gradle.properties` has `org.gradle.java.home=E\:/game/HMCL/prism/java21`. Per-user on purpose — never commit an absolute path into the repo's `gradle.properties`. |
| **`gradlew --version` lies** | Prints JVM 17 even when correctly configured | That is the *launcher* JVM. `org.gradle.java.home` applies to the daemon. Trust a real build, never `--version`. |
| **`cmd` cannot find `gradlew.bat`** | `NoDefaultCurrentDirectoryInExePath=1` is set by Git Bash and inherited Gradle → server JVM → the `cmd` the mod spawns for `/uhc regen`. A bare `call gradlew.bat` dies with "not recognized" even though the file is right there. | Always call by explicit path in `.bat` helpers. Already fixed in `run/start-server.bat`; F3 in [INGAME_RESULTS.md](INGAME_RESULTS.md). |
| **Bash heredocs mangle content** | `python - <<'PY'` corrupts non-ASCII (em dashes → `??`) and backslash escapes on Windows stdin | Use the Write/Edit tools for anything with Chinese text or backslashes. If scripting, read/write with explicit `encoding='utf-8'` and normalise `\r\n` yourself — the Java sources are **CRLF**, so naive `\n` anchors will not match. |
| **`gradlew build` during `runServer`** | Project lock contention | Stop the server first (`python scripts/rcon.py "stop"`), build, restart. |
| **`/uhc regen` escapes task tracking** | The mod restarts the server through an external helper, so the agent's background task reports "completed" while a *new* server is actually live | Check with `python scripts/rcon.py "list"` before concluding the server is down. |
| **RCON timeouts** | `uhc debug terrain 24` force-loads 2401 chunks and blows the 10s default | Pass `--timeout 240` or larger, or use a smaller radius. |
| **No fake players** | **No Carpet build exists for MC 1.21.1** (upstream jumps 1.21 → 1.21.2), and RCON cannot create a player entity | Anything needing a player body needs a real vanilla 1.21.1 client. There is a `compat/carpet/EntityPlayerMPFakeMixin` ready if a build ever lands. |
| **Spawn platform contaminates terrain samples** | First MARINE sample read `max=161` — that was the wool platform at y≈160 | Sample at chunk centres well away from origin. |

### How `/uhc regen` actually behaves

Worth knowing before panicking about a lost world: `regenerateTerrain()` launches
the restart helper, deletes the `preload` marker, and stops the server. It does
**not** wipe the world at that moment — `tryUpdateSaveFolder` wipes it on the
*next* boot when it finds no marker. So a failed restart is always recoverable:
the world is intact on disk but staged for deletion, and simply starting the
server again completes the regen.

---

## 7. Where everything lives

| Path | What |
| --- | --- |
| [AGENT.md](../../../AGENT.md) | project rules, build, test environment, 10 non-negotiables |
| [docs/design_principle/](../../design_principle/) | the analysed architecture, 6 files. `05-invariants-and-findings.md` is the defect backlog |
| [docs/tooling/README.md](../../tooling/README.md) | what is configured and why, plus what was rejected |
| `.claude/skills/` | 4 repo-local skills: `tcuhc-dev`, `fabric-mixin`, `tcuhc-gameplay`, `tcuhc-worldgen` |
| [PLAN.md](PLAN.md) | triage of all 10 reported issues, T1–T8 |
| [CHANGES.md](CHANGES.md) | every source edit, file by file |
| [TEST_PLAN.md](TEST_PLAN.md) | tier 1 automated, tier 2 in-game procedures |
| [TEST_RESULTS.md](TEST_RESULTS.md) | tier-1 results and the MARINE noise numerics |
| [INGAME_RESULTS.md](INGAME_RESULTS.md) | tier 2: build + server evidence, F1/F2/F3, and the round-2 operator pass |
| `tests/` | the runnable suites, `last_run.log`, RCON scenario files |
| `scripts/rcon.py` | stdlib-only Source RCON client, no dependencies |
| `scripts/audit_mixins.py` | run after **any** change under `mixins/`. Exit 0 clean / 1 unregistered / 2 missing |

### One caveat on the findings file

`05-invariants-and-findings.md` still lists **B2** and **B6** under "Still open".
Both have since been addressed — B2 by T2 (`case KING:`, now 2 hits in
`BookNBT.java`, confirmed working by the user) and B6 by T6 (the stale-terrain
warning). That section is stale; the table in §3 above is current. Worth
correcting the file when you next touch it.

---

## 8. Round 3 continuation (current state)

This section supersedes the earlier “verify T10 and T11” note.

- The user confirmed the death-to-spectator behavior and `/uhc stop` now work.
- Match settlement now changes all online players to spectator immediately, so
  the surviving winner can inspect the battlefield too.
- Players remain spectators until `/uhc config`; that command returns everyone
  to the lobby in survival. Reconnects during configuration also use survival.
- `/uhc reset` now explains that it restores defaults. Prefer the readable
  `/uhc reset gameplay` and `/uhc reset generation` forms; `0` and `1` remain
  compatibility aliases.
- The live bonus-chest enchantment pools no longer include Unbreaking.
- Build and server startup are green; the suite is 97/97. The server was left
  running for client verification.

Still needing client verification: the immediate post-game spectator state,
survival after `/uhc config`, and random bonus-chest output.
