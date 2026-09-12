# Agent run — 2026-09-12 — issue-report fixes

Fixes for [`docs/user_report_issue/2026_9_6.md`](../../user_report_issue/2026_9_6.md).

| Document | What it holds |
| --- | --- |
| [HANDOFF.md](HANDOFF.md) | **start here if you are a new agent** — current status, open issues in priority order, environment traps |
| [PLAN.md](PLAN.md) | triage of all 10 reported issues, root-cause analysis, the T1–T8 task list, scope boundary |
| [TEST_PLAN.md](TEST_PLAN.md) | tier 1 (automated) and tier 2 (in game) procedures |
| [TEST_RESULTS.md](TEST_RESULTS.md) | tier 1 automated results, the MARINE numerics, and the known gaps |
| [INGAME_RESULTS.md](INGAME_RESULTS.md) | **tier 2: the mod compiles and runs.** Build + server evidence, two defects found only by running it |
| [CHANGES.md](CHANGES.md) | every source edit, file by file |
| `tests/` | the runnable suites plus `last_run.log` and the RCON scenario files |

## Outcome

| Reported issue | Outcome |
| --- | --- |
| 1. Stuck at 99% | already fixed in 1.2.6 — verified, not re-fixed |
| 2. Normal mode uses the marine generator | **mitigated** — stale terrain is now detected and reported (T6) |
| 3. No golden apple count | already fixed in 1.2.5 — **hardened** (T7) |
| 4. `randomTeams` off, no team choice | **fixed** (T2) |
| 5. No inventory drops on death | **fixed** (T1) — root cause found |
| 6. Duplicate config books | **fixed** (T3) |
| 7. Post-match config needs creative | **fixed** (T4) |
| 8. World generation slow | **lever added** (T8) |
| 9. Chunk loading fails | bounded and logged since 1.2.6 — needs real logs to go further |
| 10. MARINE ocean generation | **fixed** (T5) — two defects, both measured, confirmed in game |

Plus two defects found only by actually running the server (see
[INGAME_RESULTS.md](INGAME_RESULTS.md)): `lazydfu` made `gradlew runServer`
impossible, and `/uhc debug terrain` crashed on MARINE — the one mode it exists
to diagnose.

The **New idea** section of the report is feature work and was not touched;
see [PLAN.md § Out of scope](PLAN.md#out-of-scope). Idea 3 (presets) already
shipped in v1.2.8.

## Run the tests

```bash
python docs/agent_run/2026-09-12-issue-fixes/tests/run_all.py
```

71 assertions, exit 0. They verify source structure and the MARINE noise
algorithm; compilation and runtime are covered separately in
[INGAME_RESULTS.md](INGAME_RESULTS.md).

## Status

**The mod compiles and the server boots** on JDK 21
(`E:/game/HMCL/prism/java21`). `Done (5.286s)!` with `defaultRequire: 1` means
all 46 mixins applied against real 1.21.1.

Verified in game: T5 (MARINE floor), T6 (stale-terrain warning), T8 (parallelism
option), and issues 1/9 (pregeneration completes with zero failed chunks).
Running it also turned up two defects static analysis could not see — see
[INGAME_RESULTS.md](INGAME_RESULTS.md).

## The one thing to do next

**Verify T10 and T11 in game.** Both were fixed after the user's last test pass,
so they are built and running but unconfirmed:

- **T10** — dying should go straight to spectator at the death spot, with no
  "Respawn / Title Screen" panel.
- **T11** — `/uhc stop` mid-match should print the final score board and answer
  the operator. (The no-match-running branch is already confirmed over RCON.)

Then work [HANDOFF.md](HANDOFF.md) §4, which lists everything still open in the
order worth doing it. B4 (enchantment lookups through the root registry, 2 sites)
is the highest-value one — it is a likely crash on the KING crown path.
