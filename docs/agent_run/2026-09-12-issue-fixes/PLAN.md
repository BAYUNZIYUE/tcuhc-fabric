# Agent run — fix `docs/user_report_issue/2026_9_6.md`

- **Date:** 2026-09-12
- **Branch:** `1.21.1`
- **Base commit:** `3294174` (v1.2.8)
- **Source report:** [`docs/user_report_issue/2026_9_6.md`](../../user_report_issue/2026_9_6.md)
- **Reporter:** Runaway_Fancy (09/05/2026)

## Scope

The report's **Issue report** section (10 items). The **New idea** section is
feature work, not defects, and is explicitly out of scope for this run — see
[Out of scope](#out-of-scope) at the bottom.

## Triage

Each issue was re-checked against the current v1.2.8 tree, not against the
earlier audit. Several were already fixed by the 1.2.5 → 1.2.8 commits.

| # | Reported issue | Status after triage | Task |
| --- | --- | --- | --- |
| 1 | World generation sticks at 99% | **Already fixed** in 1.2.6 — the `MAX_RETRY_COUNT = 300` budget (≈5 min per wedged chunk) was replaced by `CHUNK_TIMEOUT_TICKS`/`MAX_TICKET_RELOADS`/`STALL_TIMEOUT_TICKS` | verify only |
| 2 | Normal mode seems to use the marine generator | **Not a generator bug** — `adjustOverworldBiomes` returns early unless MARINE. Terrain is baked into the world folder, so a world first generated under MARINE keeps ocean terrain after switching back. No warning is given. | **T6** |
| 3 | No golden apple count in final stats | **Already fixed** in 1.2.5 (`item.PlayerEntityMixin` was unregistered, now registered). Two residual defects remain: unguarded `getGamePlayer(...)` NPE, and enchanted golden apples are not counted. | **T7** |
| 4 | `randomTeams` off → still no option to choose a team | **Confirmed open.** `BookNBT.getPlayerBook()` has no `case KING:`, so KING + manual teams renders only the observer row. | **T2** |
| 5 | No player inventory drops on death | **Confirmed open, root cause found.** See below. | **T1** |
| 6 | Editing config gives duplicate config books | **Confirmed open.** `startConfiguration()` unconditionally `insertStack`s a book; `/uhc config` twice ⇒ two books. | **T3** |
| 7 | Post-match reconfiguration needs creative mode | **Confirmed open.** After `endGame()` everyone is `SPECTATOR` and nothing resets that; a spectator cannot use the book. | **T4** |
| 8 | World generation still slow | **Partly open.** `PARALLELISM_LIMIT = 2` is hard-coded and deliberately conservative, with no operator lever. | **T8** |
| 9 | Chunk loading fails | Same subsystem as 1 and 8; now bounded and logged rather than hanging. Needs log evidence from a real run to go further. | verify only |
| 10 | `forceMarineOverworldBiomes` 对于海洋生成有问题 | **Confirmed open.** The MARINE sea floor still uses the technique the 1.2.7 work explicitly documented as wrong, plus a density-function contract violation. | **T5** |

### Root cause, issue 5 (inventory does not drop)

`ServerPlayerEntityMixin` injects into `ServerPlayerEntity.onDeath` at
`@At("HEAD")`, and that path reaches
`UhcPlayerManager.onPlayerDeath(...)`, which calls
`player.changeGameMode(GameMode.SPECTATOR)` **synchronously**.

Vanilla `ServerPlayerEntity.onDeath` then continues into:

```java
if (!this.isSpectator()) {
    this.drop(damageSource);   // -> dropInventory() -> inventory.dropAll()
}
```

The player is already a spectator by then, so `drop(...)` never runs and the
mod's own `dropInventoryWithoutClear` redirect never fires. The mixin comment
says HEAD was chosen so the hook survives Mojang moving internal stat writes —
that reasoning is fine; only the gamemode switch has to move.

### Root cause, issue 10 (MARINE ocean generation)

`MinecraftServerMixin.SubmergedDensityFunction` (the MARINE sea floor) has two
defects that the newer `disableOceanBiomes` work already diagnosed and solved
for the land path:

1. **It uses `valueNoise`.** The javadoc on that very method now reads: *"Do not
   use this for a large open land surface: smoothstep interpolation flattens the
   field around every lattice point and piles all the slope onto the cell
   mid-lines, which reads as quadrilateral plateaus with straight edges.
   `gradientNoise` is the one to use there."* The MARINE sea floor is exactly
   such a surface. `LandSurfaceDensityFunction` was rewritten to use
   `gradientNoise` + domain warping + five rotated octaves for this reason;
   `SubmergedDensityFunction` never got the same treatment.
2. **It violates the `DensityFunction` bounds contract.** `sample()` returns
   values across `[-1, 1]`, but `minValue()`/`maxValue()` declare `-0.5`/`0.5`.
   The chunk sampler uses those bounds to skip interpolation cells, so terrain
   can be clipped or dropped. `LandSurfaceDensityFunction` correctly declares
   `[-1, 1]`.

MARINE also swaps `continents` for a constant `-1.0`. That looks dead at first
glance — the 1.2.7 comment establishes that replacing `router.continents()`
cannot change terrain shape, because `final_density` and
`initial_density_without_jaggedness` are declared inline and hold their own
copies of the subtree. It is **not** dead: `continents` is still what the
multi-noise sampler reads for *biome* placement, and pinning it to `-1.0` is
what keeps `createMarineBiomeSource`'s continentalness ranges resolving to
open-ocean entries. Left in place, with a comment explaining why.

## Tasks

| Task | Issue | File(s) | Change |
| --- | --- | --- | --- |
| **T1** | 5 | `UhcPlayerManager.java` | Defer the spectator switch (and `TaskKeepSpectate`) by one tick with `TaskOnce`, so vanilla's `drop(...)` still sees a non-spectator. |
| **T2** | 4 | `util/BookNBT.java` | Add `case KING:` to `getPlayerBook()` alongside `case NORMAL:`. |
| **T3** | 6 | `UhcGameManager.java`, `UhcPlayerManager.java` | Make book handout idempotent: `giveOrRefreshConfigBook()` replaces an existing book in place instead of inserting a second. |
| **T4** | 7 | `UhcGameManager.java`, `UhcGameCommand.java` | `returnToLobby()` — `/uhc config` after a finished match clears `isGameEnded`, restores `ADVENTURE`, rebuilds the spawn platform, re-gives books. No creative mode needed. |
| **T5** | 10 | `mixins/core/MinecraftServerMixin.java` | Rewrite `SubmergedDensityFunction` on the gradient-noise/domain-warp/rotated-octave technique; fix `minValue`/`maxValue` to `[-1, 1]`. |
| **T6** | 2 | `util/UhcWorldData.java`, `UhcGameManager.java` | Record the generator identity (`battleType` + `levelType` + `disableOceanBiomes`) that a world was created with; warn loudly at startup on mismatch and tell the operator to `/uhc regen`. |
| **T7** | 3 | `mixins/item/PlayerEntityMixin.java` | Null-guard `getGamePlayer(...)`; also count `ENCHANTED_GOLDEN_APPLE`. |
| **T8** | 8, 9 | `options/Options.java`, `util/BookNBT.java`, `task/TaskPregenerate.java` | New `pregenerateParallelism` option (default **2** = current behaviour) so operators can trade server smoothness for pregeneration speed. |

Ordering: T1, T2, T7 are independent and land first. T3 and T4 both touch the
config flow and land together. T5, T6, T8 are worldgen/config and land last.

## Verification strategy

**A JDK 21 is not installed on this machine** (only 17.0.9; a filesystem-wide
search found no other `java.exe`). Fabric Loom refuses to configure:

```text
Minecraft 1.21.1 requires Java 21 but Gradle is using 17
```

So `./gradlew build` and `./gradlew runServer` cannot run here, and **no claim
of "it compiles" or "it works in game" is made in this run.**

What *is* verifiable now, and is therefore what `tests/` does:

| Layer | Method | Catches |
| --- | --- | --- |
| **Structural** | Python checks over the source tree | mixin registration drift, enum branches missing from a switch, options missing from the config book, re-introduction of a fixed defect |
| **Algorithmic** | The two noise algorithms ported 1:1 to Python and measured | the faceting and bounds-contract defects in T5 — these are pure functions of `(x, z)` and need no Minecraft |

What is **not** verified and needs a live 1.21.1 server: compilation, mixin
application, and every in-game behaviour. `TEST_PLAN.md` has the manual and
RCON-scripted procedure for each task, ready to run once a JDK 21 exists.

## Out of scope

The report's **New idea** section is feature work, not defects:

1. Bingo + UHC + Bedwars — a new game mode; touches all seven `EnumMode` switch
   sites plus new win conditions and worldgen. Needs a design decision first.
2. Mine war — same.
3. Presets — **already delivered** in v1.2.8 (`/uhc preset`).
4. "With pre-set, adding buffers for map, 3 per preset" — the intent is unclear
   (map seed slots? pre-generated world snapshots?). Needs clarification before
   it can be specified, let alone built.
