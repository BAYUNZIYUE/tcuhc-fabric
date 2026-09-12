# 05 — Invariants, and findings from this analysis

Part A is the rule set to code against. Part B is what a read-through turned up.

**Audited against `3294174` (v1.2.8, branch `1.21.1`).** The previous audit was
written against `8f2301f` (v1.2.4); the four commits that landed between them
resolved several findings, which are kept below as a record rather than deleted.

---

## Part A — Invariants

Break one of these and the failure will be silent, not a compile error.

1. **`UhcGameManager.instance` may be null.** It is created from the
   `MinecraftServer` constructor, so anything running earlier (or in a
   `Registries` static block, or a worldgen worker before the server exists)
   must null-check it. `BonusChestFeature.generate` and
   `EntityPlayerMPFakeMixin` both do.
2. **Never cache `ServerPlayerEntity`.** Store the `UUID` in a `UhcGamePlayer`
   and resolve with `getRealPlayer()` each time. Players reconnect mid-match.
3. **`PlayerStatistics.addStat` / `setStat` are no-ops unless `isGamePlaying`.**
   If a stat is missing from the final board, check the game-state flag before
   suspecting the hook.
4. **A mixin absent from `tcuhc.mixins.json` is dead code.** It compiles. It
   never runs. Run `python scripts/audit_mixins.py` after every mixin change —
   the repo is currently clean at 46/46 and should stay that way.
5. **A custom structure or feature type absent from `RegistriesMixin`'s static
   block fails at datapack load**, with an "unknown type" error rather than a
   compile error.
6. **Never touch a `BlockEntity` during chunk generation.** Queue it and flush in
   `END_SERVER_TICK`. See [03-worldgen.md](03-worldgen.md) §5.
7. **Never block the server thread on a chunk future.** Poll holder status.
8. **Adding an `EnumMode` means editing seven switch statements.** See
   [02-gameplay-and-config.md](02-gameplay-and-config.md) §2.
9. **Refresh books in place**, never `insertStack` a replacement.
10. **Everything periodic is a `Task`** owned by a `Taskable`, so `cancelTasks()`
    can actually stop it on regen or stop.
11. **Vanilla clients must be able to join.** Server-side only; no custom
    packets, blocks, or items.
12. **Dynamic registries need a `DynamicRegistryManager`.** `Registries.X` static
    access does not work for biome, enchantment, damage type, or loot table in
    1.21.1.
13. **`/uhc regen` needs a restart helper script** beside the world folder or it
    throws. Test environments must provide one.
14. **`defaultRequire: 1` is a feature.** If a port breaks an injector you want
    the launch to fail loudly.
15. **Every registered option needs a config-book page entry.** An option that
    exists in `Options` but not in `BookNBT.getConfigBookPage` is invisible to
    operators and reachable only through `/uhc option`.

---

## Part B — Findings

Items marked **inferred** come from static reading only and have not been
reproduced in a running game — the local build cannot run yet (see `AGENT.md`,
JDK 21 requirement).

### Resolved by the 1.2.5 → 1.2.8 commits

| Was | Now |
| --- | --- |
| 27 of 50 mixins compiled but unregistered — golden apple, diamond, and chest stats dead, `mobCount` inert, moral-item resurrection dead | **46/50 registered, 0 unregistered.** `21467cf` restored the batch; 4 obsolete classes were deleted. `python scripts/audit_mixins.py` is clean. |
| `ServerPropertiesHandlerMixin` targeted a removed 1.18 signature, so **`levelType` was wired to nothing** | Rewritten against the 1.21.1 `String levelType` record component, registered, and it now logs the resolved value at startup. |
| Pregeneration could sit at ~99% for ~5 minutes per wedged chunk (`MAX_RETRY_COUNT = 300` × `RETRY_DELAY_TICKS = 20`) | `6c4ef6f` replaced the retry budget with three separate guards: `CHUNK_TIMEOUT_TICKS = 300` (15 s per chunk), `MAX_TICKET_RELOADS = 5` with ticket re-adds every `TICKET_RELOAD_RETRY_STEP = 3` retries, and `STALL_TIMEOUT_TICKS = 1200` for a globally wedged pipeline. |
| Presets were an open feature request in the user report | `3294174` added `OptionsPreset` plus `/uhc preset save\|load\|delete\|list\|show\|overwrite`. Names are validated against `[A-Za-z0-9_-]{1,32}`. |
| Drowned trident drop, leaf apple drop, enchant-book trade limit | Fixed in `6c4ef6f`; loot changes now go through the new `util/LootInjector` (a `SERVER_STARTED` + `END_DATA_PACK_RELOAD` hook) instead of loot mixins. |

### Still open

#### B2. KING mode cannot pick a team when `randomTeams` is off — **verified by reading**

`BookNBT.getPlayerBook()` switches on `gameMode` to build the team-select rows.
Its cases are `NORMAL`, `SOLO`, `GHOST`, `BOMBER`, `BOSS`, `HUNTER`,
`GHOSTHUNTER`. **`KING` is still missing** (`grep -c "case KING"` → 0). With
`randomTeams = false` and `gameMode = KING`, the book renders only the observer
row, so nobody can select a combat team and `startGame()` always refuses with
"有玩家未选队".

This matches the report *"Random tea[m], off still no option to chose in team."*

Fix: add `case KING:` alongside `case NORMAL:` — KING uses the same colour-team
selection.

#### B3. Bonus chests are never credited, and there are two implementations — **verified**

`gen/feature/BonusChestGenerator.java` (11 KB) is a complete chunk-event-driven
bonus chest placer with retry, rollback, and summary logging. Its `register()`
is still **never called** — `TcUhcMod.onInitialize()` calls
`BonusChestFeature.registerDeferredPlacementHook()` instead.

The consequence is a stat gap. `ChestBlockEntityMixin` credits `CHEST_FOUND` /
`EMPTY_CHEST_FOUND` when a chest either carries the loot-table key
`tcuhc:bonus_chest/bonus` (which only `BonusChestGenerator` sets) or has the
custom name `"奖励宝箱"` / `"空宝箱"`. The live `BonusChestFeature` path fills
item stacks directly and sets **neither**. Its own
`BONUS_CHEST_NAME = Text.literal("Bonus Chest")` / `EMPTY_CHEST_NAME` constants
are declared but never used — and being English, would not have matched the
mixin's Chinese literals anyway.

So chest-discovery stats stay at 0 regardless of how many bonus chests are
opened.

Pick one implementation. If `BonusChestFeature` stays, make it set the loot-table
key (preferred — it also makes the chest re-rollable) or a name the mixin
matches.

#### B4. Enchantment registry lookup uses the root registry — **inferred, high confidence**

Two places still resolve enchantments like this:

```java
Registry<Enchantment> r = (Registry<Enchantment>) Registries.REGISTRIES.get(RegistryKeys.ENCHANTMENT.getValue());
if (r == null) throw new IllegalStateException("Missing enchantment registry");
```

- `util/PlayerItems.java:143` — moral items for `Keviince`, `Lancet_Corgi`,
  `youngdao`
- `task/TaskTitleCountDown.java:144` — ICARUS elytra, KING crown, hunter compass

`Registries.REGISTRIES` is the **root** registry, which holds static registries.
Enchantment became a *dynamic* registry in 1.21, so this lookup is expected to
return null and throw. `BonusChestFeature.getEnchantment` already uses the
correct form:

```java
world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(key)
```

If confirmed this breaks the ICARUS starting elytra, the KING crown, and the
hunter compass. All three are applied inside `TaskFindPlayer.onFindPlayer`, where
a thrown exception is swallowed by `UhcGameManager.tick()`'s catch-all — so it
fails *quietly*. `CONTINUATION_DRAFT.md` still lists "Fix 1.21.1 enchantment
registry lookup" as an open item.

**Verify at runtime first** (start an ICARUS match, check for the elytra), then
switch both sites to the registry-manager form. Both have a player in scope, so
`player.getWorld().getRegistryManager()` works.

#### B6. Switching `battleType` on an existing world — **inferred**

`adjustOverworldBiomes` reads the live option value and returns early unless the
battle type is MARINE (or `disableOceanBiomes` is set), so a NORMAL match does
get the vanilla generator.

The trap is that `battleType` lives in `uhc.properties` (server CWD, survives
regen) while terrain lives in the world folder, which is only wiped when the
`preload` marker is absent. **Switching battle type requires `/uhc regen`**;
otherwise you get old terrain with new rules, which is the likeliest explanation
for "normal mode seems using the marine world generator".

Worth making explicit: record the generating battle type in `uhc.json` and warn
on mismatch at startup.

Note `1.2.7` added a related but distinct knob — `disableOceanBiomes` rebuilds
the vanilla overworld biome source with ocean entries swapped for same-temperature
land biomes *and* remaps `continents`, so basins are raised rather than merely
relabelled. Same caveat applies: it only affects newly generated chunks.

#### B7. `automaticFormTeams` ignores colour choice; empty teams can crash KING — **verified by reading**

With `randomTeams = true`, `colorSelected` is used only to decide observer
(`WHITE`) vs combatant. Any other colour is discarded and teams are shuffled.
That is the intended meaning of "random teams", but the player book still
presents colour choices in some modes, which reads as a bug to players.

Unchanged since the last audit: if `teamCount` exceeds the number of combat
players, empty `UhcGameTeam`s are created, and
`UhcGameTeam.getKing()` does `players.get(0)` unguarded
(`UhcGameTeam.java:30`). **KING mode with more teams than players throws
`IndexOutOfBoundsException`.** Either guard `getKing()` or reject the
configuration in `formTeams()` with a reason string.

#### B8. Smaller items

- `TaskTitleCountDown.onFinish()` — `case GHOSTHUNTER:` still has no `break;` and
  falls through into `case KING:`. Harmless today because `isKing()` returns
  false unless the mode is KING, but it is a trap for the next edit.
- `UhcRegistry.registerIntProviderType` calls `Registry.register(..., null)`,
  which would NPE. Unreferenced; `util/AverageIntProvider` likewise. Dead
  1.18-era code.
- `block/ChestBlockEntityMixin.playerCloseChestHook` contains an empty `if` body.
- `mixins/entity/PlayerEntityMixin.modifyAndRecordDamage` calls `getGamePlayer(...)`
  without a null check on either target or attacker; an entity that is not a
  tracked `UhcGamePlayer` would NPE inside the damage path.
- *"No player inventory drops when they died"* from the user report is still
  unexplained by static reading — `entity.PlayerEntityMixin` and
  `entity.PlayerInventoryMixin` are both registered and implement
  drop-without-clear. Needs an in-game repro with the server log.

### B9. Follow-up requests from the user report

From `docs/user_report_issue/2026_9_6.md`:

- ~~Presets for the whole option set~~ — **done** in 1.2.8 (`/uhc preset`).
  The "3 buffer slots per preset" part of the request is not implemented;
  presets are free-form named files instead.
- An easier post-match reconfiguration flow (today it needs creative mode and the
  book) — still open.
- New game modes: Bingo + UHC + Bedwars, Mine war — still open.
