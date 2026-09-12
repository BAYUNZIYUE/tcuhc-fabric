---
name: tcuhc-worldgen
description: Add or change TC-UHC world generation on Minecraft 1.21.1 - custom structures, features, loot tables, biome tags, the MARINE ocean generator, or chunk pregeneration. Use when touching gen/, data/tcuhc/worldgen, data/minecraft overrides, TaskPregenerate, or anything that places blocks during chunk generation.
---

# TC-UHC world generation

Architecture reference: `docs/design_principle/03-worldgen.md`.

## The split

Java registers **types**. JSON configures **everything else**.

```
src/main/java/.../gen/structure/*.java   structure + piece types, placement predicates
src/main/java/.../gen/feature/*.java     Feature implementations
src/main/java/.../util/UhcRegistry.java  the registration helpers
src/main/resources/data/tcuhc/worldgen/  structure, structure_set, template_pool,
                                         configured_feature, placed_feature
src/main/resources/data/tcuhc/tags/      biome eligibility (has_structure/*)
src/main/resources/data/tcuhc/loot_tables/
src/main/resources/data/tcuhc/structure/ .nbt templates
src/main/resources/data/minecraft/       vanilla structure_set / biome tag overrides
```

`UhcRegistry.registerConfiguredFeature` and `registerPlacedFeature` are
deliberate no-ops returning their argument. Those objects are datapack-defined
now; do not reintroduce code registration.

## The rule that breaks world load

Worldgen registries freeze during `Bootstrap`. A custom type whose class is
never loaded is never registered, and the datapack then fails with
`Unknown structure type tcuhc:...` at world load — not at compile time.

**Every new structure or feature type must be force-loaded in the `static {}`
block of `mixins/core/RegistriesMixin`:**

```java
static {
    MyNewStructure.CODEC.codec();
    UhcFeatures.MY_FEATURE.getCodec();
}
```

## Adding a structure

1. Build the `.nbt` template in-game with structure blocks. Use **structure
   block metadata markers** for anything dynamic (`chest`, `plant1`, ...).
   Save to `src/main/resources/data/tcuhc/structure/<name>/main.nbt`.
2. Extend `SinglePieceLandStructure`:
   - `public static final MapCodec<T> CODEC = createCodec(T::new);`
   - register the type and piece type through `UhcRegistry`.
   - implement `canGenerate(Context)` using the provided predicates:
     `isBiomeValid`, `isBiomeValidInChunk`,
     `isSurroundingFlat(context, heightmap, range, maxDelta)`.
   - implement `addPieces(...)`, normally one `Piece` at
     `shiftStartPosRandomly(context)` with `BlockRotation.random(...)`.
   - override `postPlace(...)` and call `fillBottomAirGap` /
     `fillBottomAirGapInAutoBox` so the structure does not float on slopes.
   - nested `Piece extends SinglePieceLandStructure.Piece` implementing
     `handleMetadata(metadata, pos, world, random, box)`.
   - extend `YOffsetPiece` instead if the build has a floor below ground level.
3. JSON:
   - `worldgen/structure/<name>.json` — `"type": "tcuhc:<name>"`,
     `"biomes": "#tcuhc:has_structure/<name>"`, `"step": "surface_structures"`,
     `"terrain_adaptation": "beard_thin"`.
   - `worldgen/structure_set/<name>.json` — `random_spread` with a **unique
     salt**, plus `spacing` / `separation` (separation must be < spacing).
   - `tags/worldgen/biome/has_structure/<name>.json` — the biome list.
   - `loot_tables/<name>/chest.json` if it has loot.
4. Add the codec to `RegistriesMixin`.

Chest loot is attached with `Piece.setChestLoot(world, pos, random, lootTableId)`
from `handleMetadata` — it already handles the deferred-retry path below.

## Deferred placement — non-negotiable

Chunk generation runs off the server thread and **`BlockEntity` instances do not
exist yet**. Writing loot or spawning entities directly from `Feature.generate`
or `StructurePiece.generate` loses data or wedges the chunk pipeline.

The pattern used throughout:

1. During generation, compute the position plus a `long randomSeed`, and push a
   pending record onto a `ConcurrentLinkedQueue`.
2. Register `ServerChunkEvents.CHUNK_LOAD` to mark chunks ready.
3. Flush the queue in `ServerTickEvents.END_SERVER_TICK`, on the server thread,
   with a bounded retry count.

Existing implementations to copy:
`BonusChestFeature.registerDeferredPlacementHook()` and
`SinglePieceLandStructure.registerLootRetryHook()`. Both are wired from
`TcUhcMod.onInitialize()`.

Store a **seed**, never a `Random` — the deferred result must be deterministic.

## Adding a feature

```java
// UhcFeatures
public static final Feature<DefaultFeatureConfig> MY = UhcRegistry.registerFeature("my", new MyFeature(DefaultFeatureConfig.CODEC));
public static final RegistryKey<PlacedFeature> MY_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, TcUhcMod.id("my"));

// UhcFeatures.register(), called from TcUhcMod.onInitialize()
BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.SURFACE_STRUCTURES, MY_PLACED_KEY);
```

plus `worldgen/configured_feature/my.json` and `worldgen/placed_feature/my.json`,
plus the codec in `RegistriesMixin`. Using a new Fabric API package means adding
its module to `fabricApiModules` in `build.gradle` (they are JiJ `include`d).

## Generator swaps

`MinecraftServerMixin.adjustOverworldBiomes` `@ModifyArgs` the `ServerWorld`
constructor in `createWorlds` and is the single entry point for both overworld
generator replacements. MARINE wins if both would apply.

### MARINE

When `battleType == MARINE` it swaps in a `NoiseChunkGenerator` built from:

- a hand-rolled `MultiNoiseBiomeSource` of ocean-family biomes by temperature
  band, deep and shallow variants;
- the vanilla `NoiseRouter` with `continents` forced to `-1.0` and both density
  functions replaced by `SubmergedDensityFunction` (own value noise, sea floor at
  y=48 ±23).

Both custom `DensityFunction`s throw from `getCodecHolder()`. They are
runtime-only and **must never be serialised** — that is exactly why the swap
happens at `createWorlds` rather than in a dimension JSON. Do not try to move
this into a datapack.

### `disableOceanBiomes`

The inverse. `createLandChunkGenerator` rebuilds the vanilla overworld multi-noise
entry list, swapping each ocean-family biome for a land biome of the **same
temperature slot**, and remaps `continents` so the basins are raised rather than
merely relabelled — otherwise the old sea floor stays and sea level 63 refills it.
Everything at or above `OCEAN_EDGE = -0.19` is untouched, which is what preserves
rivers and existing land.

Beware: the temperature string checks must test `"lukewarm"` before `"warm"`.

Because terrain is baked into the world folder, **changing `battleType` or
`disableOceanBiomes` requires `/uhc regen`**. Existing chunks keep the old
generator's output. `/uhc debug biome <radius>` and `/uhc debug terrain <radius>`
sample the live world, which is the quickest way to confirm a swap took effect.

## Pregeneration

`task/TaskPregenerate` spirals out to `borderStart/32 + 5` chunks and forces each
to `ChunkStatus.FULL` by adding a 3×3 block of `PRE_GENERATE` tickets and
**polling** holder status through `ServerChunkLoadingManagerAccessor` /
`AbstractChunkHolderAccessor`.

Never block the server thread on a chunk `CompletableFuture` — that was the
original cause of the pregeneration hangs.

Tuned constants, changed only with a reason:
`PARALLELISM_LIMIT = 2`, `ENQUEUE_THRESHOLD = 1`, `TICKET_RADIUS = 1`,
`RETRY_DELAY_TICKS = 20`, `RETRY_LOG_INTERVAL = 20`.

Three independent give-up guards (added in 1.2.6, replacing a single
`MAX_RETRY_COUNT = 300` budget that could hold the task at ~99% for five minutes):

- `CHUNK_TIMEOUT_TICKS = 300` — 15 s per chunk. Slow chunks finish in well under
  a second, so this only catches one that can never finalise.
- `TICKET_RELOAD_RETRY_STEP = 3` / `MAX_TICKET_RELOADS = 5` — re-add the chunk's
  tickets every ~3 s, up to 5 times, in case the ticket itself died.
- `STALL_TIMEOUT_TICKS = 1200` — if *no* chunk completes for 60 s the pipeline
  is wedged; abandon. Measured from the last success or failure, so a large map
  never trips it.

Log lines to grep when it stalls:

```
Pregenerate still waiting on chunk ... after N retries
Pregenerate permanently failed chunk ... after N retries
```

## Testing worldgen changes

Terrain is cached in the world folder, so **most worldgen edits need a wipe**:

```
/uhc regen      (twice to confirm; restarts the server via restart-server.sh)
```

or delete `run/world/` manually. Keep `borderStart` small (300–500) while
iterating so pregeneration takes seconds. `/locate structure tcuhc:<name>` is the
fastest way to confirm a structure registered and is generating.
