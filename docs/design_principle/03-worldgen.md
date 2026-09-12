# 03 — World generation

## 1. The split: code registers types, JSON configures them

Since the 1.21.1 port, worldgen follows the modern Fabric split strictly:

| Layer | Lives in | Example |
| --- | --- | --- |
| **Type registration** | Java, via `util/UhcRegistry` | `tcuhc:ender_pyramid` structure type + piece type |
| **Placement / configuration** | `src/main/resources/data/tcuhc/worldgen/**` | `structure/ender_pyramid.json`, `structure_set/ender_pyramid.json` |
| **Biome eligibility** | biome tags | `data/tcuhc/tags/worldgen/biome/has_structure/*.json` |
| **Loot** | `data/tcuhc/loot_tables/**` | `ender_pyramid/chest.json` |
| **Vanilla overrides** | `data/minecraft/**` | retuned `structure_set/shipwrecks.json`, `buried_treasures.json` |

`UhcRegistry.registerConfiguredFeature` / `registerPlacedFeature` are
intentional **no-ops that return their argument** — those objects are now
datapack-defined and must not be registered from code.

### Rule

New structures and features: register the *type* in Java, describe *everything
else* in JSON. Do not reintroduce hard-coded `ConfiguredFeature` construction.

## 2. Registry freezing, and why `RegistriesMixin` exists

In 1.21.1 the worldgen registries are frozen during `Bootstrap`. Static
initialisers on `EnderPyramidStructure` and friends never run unless something
touches those classes first, and by the time the datapack is parsed it is too
late to register `tcuhc:ender_pyramid`.

`mixins/core/RegistriesMixin` solves this with a `static {}` block on
`net.minecraft.registry.Registries` that forces class loading:

```java
static {
    EnderPyramidStructure.CODEC.codec();
    GreenhouseStructure.SNOW_CODEC.codec();
    // ... every custom structure, plus UhcFeatures.BONUS_CHEST.getCodec()
}
```

`TcUhcMod.onInitialize()` additionally calls `UhcFeatures.register()`.

### Rule

**Every new custom structure or feature type must be added to `RegistriesMixin`'s
static block.** Forgetting it produces a datapack parse error at world load
("Unknown structure type"), not a compile error.

## 3. Custom structures

`gen/structure/SinglePieceLandStructure` is the shared base for all six custom
structures (ender pyramid, greenhouse ×2, honey workshop, plain cottage, villain
house). It provides:

- `getStructurePosition` → `canGenerate(context)` gate, then
  `Heightmap.Type.WORLD_SURFACE_WG` placement.
- Placement predicates: `isBiomeValid`, `isBiomeValidInChunk`,
  `isSurroundingFlat(range, maxDelta)`, `shiftStartPosRandomly`.
- `fillBottomAirGap` / `fillBottomAirGapInAutoBox` — fills the gap under a
  structure so it does not float on sloped terrain.
- `Piece` — a `SimpleStructurePiece` that keeps embedded entities
  (`setIgnoreEntities(false)`, needed for the pyramid's end crystals), averages
  terrain height in `adjustPosByTerrain()`, and offers
  `setChestLoot` / `clearMetadataMarker` / `clearResidualBlockEntity` /
  `placeEntity` helpers.
- `YOffsetPiece` — a `Piece` that sinks itself by a fixed floor height.

Structure content comes from `.nbt` templates in `data/tcuhc/structure/**`, with
behaviour driven by **structure block metadata markers** handled in each piece's
`handleMetadata(...)` (e.g. `"chest"`, `"plant1"`, `"plant2"`).

Custom structure JSON uses `"terrain_adaptation": "beard_thin"` and points
`"biomes"` at the mod's own tag.

## 4. Features

Two features registered through `UhcRegistry.registerFeature` and attached to
every overworld biome by `UhcFeatures.register()` using Fabric's
`BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), SURFACE_STRUCTURES, key)`:

- `tcuhc:bonus_chest` — `BonusChestFeature`
- `tcuhc:merchants` — `MerchantsFeature`

Bonus-chest density is biome-weighted in `getBiomeChance(...)`: rivers and
beaches are excluded outright; snow/ice biomes are the richest (0.20); plains and
desert the poorest (0.06); ocean is 0 unless the battle type is MARINE.

## 5. Deferred placement — the most important worldgen rule

Chunk generation runs on worker threads and, critically, **`BlockEntity`
instances are not available while a feature is generating**. Writing a chest with
loot directly from `Feature.generate` either silently loses the loot table or
deadlocks the chunk pipeline.

The mod therefore uses a uniform three-step pattern:

1. During generation, compute the position and a `randomSeed`, and push a
   *pending placement* record onto a `ConcurrentLinkedQueue`.
2. Listen for the chunk becoming available (`ServerChunkEvents.CHUNK_LOAD`).
3. Flush the queue in `ServerTickEvents.END_SERVER_TICK`, on the server thread,
   where `world.getBlockEntity(...)` works.

Implemented in:

- `BonusChestFeature` — `PENDING_PLACEMENTS` keyed by chunk, `READY_CHUNKS`,
  `registerDeferredPlacementHook()` (called from `TcUhcMod.onInitialize`).
- `SinglePieceLandStructure` — `PENDING_LOOT_ATTACHMENTS` with
  `MAX_CHEST_LOOT_RETRIES = 3`, `registerLootRetryHook()`.

Both store a seed rather than a `Random`, so the deferred result is
deterministic.

### Rule

Any new worldgen code that needs a `BlockEntity`, an entity, or a `ServerWorld`
API must go through this deferred queue. Never call `world.getBlockEntity` from
inside `Feature.generate` or `StructurePiece.generate`.

## 6. Generator swaps

`MinecraftServerMixin.adjustOverworldBiomes` uses `@ModifyArgs` on the
`ServerWorld` constructor call inside `createWorlds` and can replace the overworld
`DimensionOptions` in two different ways. It is the single entry point for both;
MARINE wins if both would apply.

### 6a. MARINE

When the battle type is MARINE it substitutes a synthesised
`NoiseChunkGenerator`:

- **Biome source** — a hand-built `MultiNoiseBiomeSource` containing only
  ocean-family biomes, arranged by temperature band with deep/shallow variants.
- **Noise router** — the vanilla router with `continents` replaced by a constant
  `-1.0` (`ConstantDensityFunction`) and both `initialDensityWithoutJaggedness`
  and `finalDensity` replaced by `SubmergedDensityFunction`, a self-contained
  value-noise sea floor centred at y=48 with ±23 amplitude.

`SubmergedDensityFunction` implements its own hash/value-noise rather than using
vanilla `PerlinNoiseSampler`, and both custom density functions throw from
`getCodecHolder()` — they are runtime-only and **must never be serialised**.
That is why the generator is swapped at `createWorlds` time rather than being
declared in a dimension JSON.

MARINE also reprioritises pregeneration around the computed team spawn ring when
the game starts (`onGameStarted` → `TaskPregenerate.reprioritizeOverworld`).

### 6b. `disableOceanBiomes` (added in 1.2.7)

The inverse operation. `createLandChunkGenerator` rebuilds the vanilla overworld
multi-noise entry list (`MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD`),
swapping every ocean-family biome for a land biome **of the same temperature
slot** while leaving the noise parameter ranges untouched — so climate placement
is unchanged and only the biome identity moves.

Relabelling alone is not enough, and the comment in the source says why: terrain
*shape* comes from the noise router, so a relabelled basin keeps its deep sea
floor and sea level 63 fills it with water — "a large body of water in the middle
of a desert". So `continents` is also remapped: everything at or above
`OCEAN_EDGE = -0.19` is left completely alone (this is what preserves rivers and
existing land), and below it a `OCEAN_BLEND_WIDTH = 0.15` band smoothly raises
the floor to `LAND_SURFACE_BASE_HEIGHT = 78` with a `LAND_SURFACE_RAMP = 6`
density ramp. The result leaves scattered small ponds rather than open ocean.

Watch the ordering of the temperature checks in `getLandReplacement` —
`"lukewarm"` contains `"warm"`.

Both swaps only affect **newly generated chunks**. Changing either setting on an
existing world requires `/uhc regen`.

## 7. Pregeneration

`task/TaskPregenerate` walks a spiral of chunk positions out to
`borderStart / 32 + 5` (overworld) or `/8 + 10` (nether) and forces them to
`ChunkStatus.FULL`.

Design constraints that were tuned against a real server and should not be
casually relaxed:

| Constant | Value | Why |
| --- | --- | --- |
| `PARALLELISM_LIMIT` | 2 | real servers are far more sensitive to chunk-pipeline pressure than a dev run |
| `ENQUEUE_THRESHOLD` | 1 | refill only when nearly drained |
| `TICKET_RADIUS` | 1 | a 3×3 ticket block so the centre chunk can actually reach FULL |
| `RETRY_DELAY_TICKS` | 20 | poll once a second, not every tick |
| `RETRY_LOG_INTERVAL` | 20 | log a slow chunk roughly every 20 s |
| `CHUNK_TIMEOUT_TICKS` | 300 | 15 s per chunk. Merely-slow chunks finish in well under a second, so this only catches a chunk that can *never* finalise |
| `TICKET_RELOAD_RETRY_STEP` | 3 | re-add the chunk's tickets every ~3 s, in case the ticket itself died |
| `MAX_TICKET_RELOADS` | 5 | after this many reloads, conclude the chunk cannot load |
| `STALL_TIMEOUT_TICKS` | 1200 | if *no* chunk completes for 60 s the pipeline itself is wedged; abandon rather than hang forever. Measured from the last success **or** failure, so a legitimately large map never trips it |

The three-layer guard (per-chunk timeout, ticket reload, global stall) replaced a
single `MAX_RETRY_COUNT = 300` budget in 1.2.6, which could hold the task at ~99%
for five minutes on one wedged chunk.

It **polls** chunk status through `ServerChunkLoadingManagerAccessor` and
`AbstractChunkHolderAccessor` instead of blocking on a `CompletableFuture` —
blocking the server thread on chunk futures is what caused the original
"stuck at 99%" hangs. A null chunk holder counts as a failed attempt so the last
pending chunk can never wedge the task forever.

On overworld completion it chains into the nether; on nether completion it writes
the `preload` marker and clears `isPregenerating`.

## 8. Vanilla worldgen overrides

- `data/minecraft/worldgen/structure_set/*.json` — retuned spacing/separation for
  shipwrecks, buried treasure, ocean ruins, ruined portals.
- `data/minecraft/tags/worldgen/biome/has_structure/*.json` — widened or narrowed
  biome eligibility (notably village_plains and ocean_monument).
- `worldgen/ShipwreckStructureMixin` — rejects shipwrecks in river biomes above
  `seaLevel - 7`, so they stop spawning half-beached in rivers.
- `worldgen/BuriedTreasureStructureMixin` — `@ModifyConstant` shrinks the
  treasure offset from 9 to 4 so the map marker is closer to the chest.
