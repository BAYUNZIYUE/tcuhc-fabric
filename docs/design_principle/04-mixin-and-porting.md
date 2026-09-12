# 04 — Mixin conventions and 1.18 → 1.21.1 porting rules

## 1. Package layout mirrors intent, not vanilla packages

```
mixins/
  core/       server bootstrap, command registration, save-folder lifecycle, registry freezing
  entity/     player/mob behaviour and stat collection
  item/       item behaviour (golden apples, chorus fruit, crossbow, moral items)
  block/      block-entity behaviour (chest open/close stats)
  loot/       loot table/pool mutation accessors
  recipe/     custom crafting via screen-handler hooks
  network/    packet-level interception (chat, spectator teleport)
  task/       accessors needed by the Task layer (chunk holders)
  worldgen/   vanilla structure tuning
  util/       miscellaneous accessors
  compat/     third-party mod compatibility (@Pseudo)
```

Two classes may share a simple name across sub-packages when they target the
same vanilla class for different reasons — `entity.PlayerEntityMixin` (damage and
inventory) and `item.PlayerEntityMixin` (golden-apple stat) both exist and that
is deliberate.

## 2. Conventions

- **`compatibilityLevel: "JAVA_17"`** in `tcuhc.mixins.json`.
- **`defaultRequire: 1`** — every injector must apply or the game fails fast at
  launch. This is the project's early-warning system for a broken port; do not
  weaken it to 0 to make a build pass.
- **Accessors and invokers** are suffixed `Accessor`; they use `@Accessor` /
  `@Invoker` and live next to the mixin that needs them.
- `LootTableMixin` / `LootPoolMixin` implement interfaces from `interfaces/`
  rather than using `@Accessor`; the comment records that `@Accessor` produced an
  `IllegalAccessError` there. Leave them as they are.
- `@Unique` is used for injected fields on newer mixins. Older ones (e.g.
  `MinecraftServerMixin`'s `uhcGameManager`) predate that habit; new fields
  should carry `@Unique`.
- Third-party targets use `@Pseudo` + `@Mixin(targets = "...")` —
  `compat/carpet/EntityPlayerMPFakeMixin` targets Carpet's fake players by string
  so the mod still loads without Carpet.
- `tcuhc.accesswidener` exists and is wired up, but is currently **empty** (just
  the `accessWidener v1 named` header). Prefer an accessor mixin; widen only when
  a mixin cannot express the access.

### Rule

A mixin file that is not listed in `tcuhc.mixins.json` **does nothing**. It still
compiles, so the compiler will not tell you. Whenever you add, rename, or move a
mixin, update `tcuhc.mixins.json` in the same commit and run:

```bash
python scripts/audit_mixins.py
```

As of v1.2.8 the repo is clean — 46 classes on disk, 46 registered. It spent most
of the port with roughly half the mixins inert, so keep the audit green.

## 3. Migration rules already applied (1.18.1 → 1.21.1)

These are the substitutions this codebase has settled on. Follow them; do not
reintroduce the left-hand column.

### Identifiers and registries

| 1.18 | 1.21.1 |
| --- | --- |
| `new Identifier(ns, path)` | `Identifier.of(ns, path)` — see `TcUhcMod.id(...)` |
| `net.minecraft.util.registry.Registry.X` | `net.minecraft.registry.Registries.X` |
| `net.minecraft.util.registry.RegistryKey` | `net.minecraft.registry.RegistryKey` |
| raw `Biome`, `Enchantment` objects | `RegistryEntry<T>` everywhere |
| `Registry.BIOME` static access | `server.getRegistryManager().get(RegistryKeys.BIOME)` |

**Dynamic registries** (biome, enchantment, damage type, loot table) are *not* on
`Registries`. They must be resolved through a `DynamicRegistryManager`, reachable
from `MinecraftServer`, `World`, or `StructureWorldAccess.getRegistryManager()`.
`BonusChestFeature.getEnchantment` shows the correct shape:

```java
world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).entryOf(key)
```

### Items and NBT

| 1.18 | 1.21.1 |
| --- | --- |
| `stack.getOrCreateTag()` / `setTag` | `DataComponentTypes` + `stack.set(...)` / `stack.get(...)` |
| custom NBT | `DataComponentTypes.CUSTOM_DATA` holding an `NbtComponent` |
| `stack.setCustomName(text)` | `stack.set(DataComponentTypes.CUSTOM_NAME, text)` |
| leather dye NBT | `DataComponentTypes.DYED_COLOR` + `DyedColorComponent` |
| potion NBT | `DataComponentTypes.POTION_CONTENTS` + `PotionContentsComponent` |
| written book NBT pages | `DataComponentTypes.WRITTEN_BOOK_CONTENT` + `WrittenBookContentComponent` with `RawFilteredPair<Text>` |
| `stack.addEnchantment(e, lvl)` | `EnchantmentHelper.apply(stack, b -> b.set(entry, lvl))` |
| `FoodComponent` with mutable builder | immutable `FoodComponent` record + `FoodComponent.StatusEffectEntry` |

### Text

| 1.18 | 1.21.1 |
| --- | --- |
| `new LiteralText(s)` | `Text.literal(s)` |
| `sendFeedback(text, bool)` | `sendFeedback(() -> text, bool)` — supplier |

### Damage

| 1.18 | 1.21.1 |
| --- | --- |
| `DamageSource.FALL` etc. | `DamageTypes.FALL` + `source.isOf(...)` |
| `source.isExplosive()` | `source.isIn(DamageTypeTags.IS_EXPLOSION)` |
| constructing a `DamageSource` | `new DamageSource(registryManager.get(RegistryKeys.DAMAGE_TYPE).entryOf(DamageTypes.X), attacker)` |
| `player.damage(DamageSource.IN_WALL, 1f)` | `player.damage(player.getDamageSources().inWall(), 1f)` |

### Scoreboard

| 1.18 | 1.21.1 |
| --- | --- |
| `scoreboard.getPlayerScore(name, obj)` | `scoreboard.getOrCreateScore(ScoreHolder.fromName(name), obj)` returning `ScoreAccess` |
| int display slot constants | `ScoreboardDisplaySlot.SIDEBAR` / `LIST` / `BELOW_NAME` |
| `addObjective(...)` 4-arg | 7-arg with `RenderType`, `displayAutoUpdate`, `numberFormat` |
| `scoreboard.addPlayerToTeam(name, team)` | `scoreboard.addScoreHolderToTeam(name, team)` |

### Worldgen

| 1.18 | 1.21.1 |
| --- | --- |
| `StructureFeature` + `StructuresConfig` mixins | `Structure` + `StructureType` + datapack `structure_set` JSON |
| `ConfiguredStructureFeatures` code registration | `data/<ns>/worldgen/structure/*.json` |
| `ConfiguredFeature`/`PlacedFeature` registered in code | datapack JSON; code registers only the `Feature` |
| biome feature injection via mixin | `BiomeModifications.addFeature(BiomeSelectors..., step, placedFeatureKey)` |
| `ChunkStatus` futures | poll via `AbstractChunkHolder` accessors |
| `new BlockPos(double,double,double)` | `BlockPos.ofFloored(...)` |
| `LootTable` `Identifier` field on containers | `RegistryKey<LootTable>` via `setLootTable(RegistryKey, seed)` |

### Recipes

Custom `SpecialCraftingRecipe` serialisers proved brittle against 1.21.1's
recipe sync. The port replaced them with **screen-handler hooks**:
`CraftingScreenHandlerMixin` / `PlayerScreenHandlerMixin` populate the result
slot in `onContentChanged`, and `CraftingResultSlotMixin` consumes the inputs in
`onTakeItem`. Pure logic lives in `recipe/ArmorRepairCrafting`.

### Loot

Loot mixins (`loot/LootTableMixin`, `loot/LootPoolMixin`, `loot/ItemEntryAccessor`)
now back `util/LootInjector`, which applies the mod's loot changes from
`ServerLifecycleEvents.SERVER_STARTED` and `END_DATA_PACK_RELOAD` rather than
mutating tables during class load. New loot tweaks belong in `LootInjector`, not
in a fresh mixin.

### A worked porting example

`mixins/ServerPropertiesHandlerMixin` is the clearest before/after in the repo.
On 1.18 it did `@ModifyArg` on
`GeneratorOptions.fromProperties(DynamicRegistryManager, Properties)` and rewrote
the `level-type` property. That method no longer exists, so the mixin sat inert
for the whole port and the `levelType` option was silently wired to nothing.

The 1.21.1 version targets the record that replaced it and modifies the
`String levelType` component of its canonical constructor instead — and logs the
resolved value at startup so a silent regression is visible. When a 1.18 mixin
looks unportable, check whether the data it manipulated simply moved into a
record.

### Recipes

`RecipeArmorRepair` and `RecipeGoldenApple` still exist but their
`getSerializer()` throws `UnsupportedOperationException` — they are kept as
reference logic, not wired into the recipe manager. Tiered golden apples are
plain JSON recipes (`golden_apple_level_1..4.json`) plus the
`item/LivingEntityMixin` effect override.

## 4. Build settings worth knowing

- `build.gradle` still declares `sourceCompatibility = JavaVersion.VERSION_1_8`
  while the mixin config is `JAVA_17` and CI builds on JDK 21. It currently
  works, but it is inconsistent; if you hit a "source release 8" error when using
  a newer language feature, raise both `sourceCompatibility` and
  `targetCompatibility` to 21 rather than working around it.
- Fabric API modules are both `modImplementation` **and** `include`d (JiJ):
  `fabric-api-base`, `fabric-biome-api-v1`, `fabric-resource-loader-v0`,
  `fabric-lifecycle-events-v1`. Adding a new Fabric API call means adding its
  module to the `fabricApiModules` list in `build.gradle`, or it will be missing
  at runtime on a server without the full Fabric API.
- `remappedSrc/` at the repository root is **stale decompiler output from the
  1.18 era**. It is not a source set and is not compiled. Ignore it; never edit
  it and never copy from it as a 1.21.1 reference.
