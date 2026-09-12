# Test results

- **Run:** 2026-09-12
- **Branch:** `1.21.1`, base commit `3294174` (v1.2.8)
- **Command:** `python docs/agent_run/2026-09-12-issue-fixes/tests/run_all.py`
- **Raw log:** [`tests/last_run.log`](tests/last_run.log)

## Verdict

| Suite | Result |
| --- | --- |
| `test_java_syntax.py` | **26 passed, 0 failed** |
| `test_structural.py` | **62 passed, 0 failed** |
| `test_noise.py` | **9 passed, 0 failed** |
| **Total** | **97 passed, 0 failed** (exit 0) |

These suites cover source structure and pure algorithms. They do not compile or
run the mod — that is tier 2, and it has since been done separately: see
**[INGAME_RESULTS.md](INGAME_RESULTS.md)**, where the mod builds
(`BUILD SUCCESSFUL`) and the server reaches `Done (5.286s)!` on JDK 21.

> Superseded note: when this file was first written no JDK 21 was installed, so
> nothing had been compiled. That is no longer true.

---

## The result that matters most: T5, the MARINE sea floor

Both the old and new algorithms were ported 1:1 from
`mixins/core/MinecraftServerMixin.java` into Python — including Java's 64-bit
wrapping multiply and `>>>` unsigned shift — and then measured. The Python port
reads `CENTER_Y`, `AMPLITUDE` and `FLOOR_RAMP` straight out of the Java source
at import time, so it cannot silently drift from the implementation.

### Faceting (the reported symptom)

Value noise uses smoothstep interpolation, whose derivative is **zero at every
lattice point**. The old octaves were axis-aligned at periods 80/40/20, so
wherever x and z are both multiples of 80 all three octaves are flat at once.
Metric: mean gradient magnitude on that lattice ÷ mean gradient magnitude over
the field.

| | ratio | meaning |
| --- | --- | --- |
| old (`valueNoise`) | **0.0208** | field is essentially flat on an 80-block grid → quadrilateral plateaus with straight edges |
| new (`gradientNoise` + domain warp + 5 rotated octaves) | **1.0052** | the lattice is statistically unremarkable |

A 48× improvement, and the new value sitting at ~1.0 is the "no preferred grid"
result you want rather than merely "less bad".

### Bounds contract

`DensityFunction.minValue()`/`maxValue()` must bound `sample()`; the
`ChunkNoiseSampler` uses them to skip interpolation cells.

| | declared | actually sampled | |
| --- | --- | --- | --- |
| old | `[-0.5, 0.5]` | `[-1.000, 1.000]` | **violated** — cells holding real terrain could be discarded |
| new | `[-1.0, 1.0]` | `[-1.000, 1.000]` | honoured |

### Gameplay envelope preserved

This one changed the plan. The first rewrite kept the original `CENTER_Y = 48`,
`AMPLITUDE = 23` and measured **0.000%** of the map above sea level — gradient
noise summed over octaves is far more concentrated around its mean than the old
value-noise sum, so it removed every island. MARINE seeds oak logs and saplings
in its bonus chests, so those islands are part of the mode, not an artefact.

`CENTER_Y`/`AMPLITUDE` were recalibrated to **50/32** against the measured old
envelope. A `|n|^0.7` tail-expansion curve matched even better but was rejected:
its derivative is infinite at 0, which would put a visible crease along the
mean-height contour.

| | height range | area at/above sea level 63 |
| --- | --- | --- |
| old | `[27.5, 69.0]` | **0.813%** |
| new | `[29.5, 69.4]` | **0.836%** |

Same island density, same peak height, no faceting.

---

## Per-task evidence

| Task | Issue | Automated evidence |
| --- | --- | --- |
| **T1** | 5 | `onPlayerDeath` no longer contains `changeGameMode(GameMode.SPECTATOR)`; it calls `enterSpectatorAfterDeathProcessing`, which defers via `TaskOnce`. This is the whole fix: vanilla's `if (!isSpectator()) drop(...)` was being skipped. |
| **T2** | 4 | All 8 `EnumMode` constants now appear in `getPlayerBook`. The same test also checks `spreadPlayers`, `automaticFormTeams`, `manuallyFormTeams` and `getAdjustBook` — all complete. |
| **T3** | 6 | Neither `startConfiguration` nor `regiveConfigItems` blind-inserts a book; `giveOrRefreshConfigBook` replaces in place before falling back to insert. |
| **T4** | 7 | `returnToLobby()` exists, clears `isGameEnded`, calls `resetForNextGame` and `generateSpawnPlatform`; `resetForNextGame` restores `GameMode.SURVIVAL`; `startConfiguration` routes through it when the match has ended. |
| **T5** | 10 | See above. Plus: no `valueNoise` in `SubmergedDensityFunction`, `gradientNoise` and domain warp present, bounds declared `[-1.0, 1.0]`. |
| **T6** | 2 | `UhcWorldData.generatorIdentity` + `checkGeneratorIdentity`; `onServerInited` calls `warnOnStaleTerrain`; the warning text contains `/uhc regen`. |
| **T7** | 3 | `ENCHANTED_GOLDEN_APPLE` counted; both `UhcGameManager.instance` and the resolved game player are null-checked. |
| **T8** | 8, 9 | `pregenerateParallelism` registered, listed in `SERVER_START_OPTIONS`, read by `TaskPregenerate`, hard-coded `PARALLELISM_LIMIT` gone, `DEFAULT_PARALLELISM` fallback present. |
| **T10 follow-up** | operator feedback | `endGame()` applies `GameMode.SPECTATOR` to every online player immediately; the lobby transition is the path back to survival. |
| **T12** | operator feedback | Bare `/uhc reset` executes help, readable `gameplay` and `generation` forms are registered, and numeric compatibility remains. |
| **T13** | operator feedback | `Enchantments.UNBREAKING` is absent from all live `BonusChestFeature` book pools. |

## Cross-cutting invariants

| Invariant | Result |
| --- | --- |
| Every mixin on disk is registered in `tcuhc.mixins.json` | **46 / 46** |
| Every registered option is reachable from the config book | **33 / 33** — including the new `pregenerateParallelism` |

The second one is new in this run and immediately earned its keep: it is what
guarantees `pregenerateParallelism` is not another knob that exists only in
`uhc.properties`.

---

## Issues verified as already fixed upstream, not re-fixed here

| Issue | Evidence |
| --- | --- |
| 1 — stuck at 99% | The `MAX_RETRY_COUNT = 300` × `RETRY_DELAY_TICKS = 20` budget (≈5 min per wedged chunk) is gone, replaced in 1.2.6 by `CHUNK_TIMEOUT_TICKS = 300` (15 s/chunk), `MAX_TICKET_RELOADS = 5`, and `STALL_TIMEOUT_TICKS = 1200`. |
| 3 — golden apple count | `item.PlayerEntityMixin` was unregistered before 1.2.5; it is registered now. T7 hardens the remaining edges. |
| 9 — chunk loading fails | Same subsystem as 1; failures are now bounded and logged by name. Going further needs the actual log lines from a real run. |

---

## Known gaps

1. ~~No compilation.~~ **Closed** — builds on JDK 21, see
   [INGAME_RESULTS.md](INGAME_RESULTS.md).
2. **No client.** T1, T2, T3, T4 and T7 need a player entity. RCON cannot make
   one and there is no Carpet build for 1.21.1, so these five remain
   static-analysis inferences.
3. **T1 tick-ordering.** The spectator switch now lands one task tick after the
   death. Static analysis says nothing observes the player in between, but that
   is exactly the kind of thing that needs watching in game — see the regression
   note in `TEST_PLAN.md` T1.
4. **T8 is a lever, not a fix.** It does not make pregeneration faster by
   itself; it lets an operator trade server smoothness for speed. Whether the
   default of 2 is right for the user's hardware is an empirical question.
5. **Issue 2 is mitigated, not eliminated.** The warning tells the operator the
   terrain is stale; it does not regenerate anything, by design.
