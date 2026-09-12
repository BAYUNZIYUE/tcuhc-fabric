# Continuation Draft

- User instruction: "编译使用github编译 你自己记录一个草稿 每次压缩会话后自己看看"
- Build policy: treat GitHub build as the authoritative compilation/verification path.
- Local build may still be used for quick signal, but not as the final acceptance gate.

## Current focus

- Highest priority: restore custom structures and their marker-based gameplay behavior.
- In parallel: fix book interaction, team scoreboard behavior, and legacy enchantment migration.

## Current code state

- `src/main/java/me/fallenbreath/tcuhc/UhcPlayerManager.java`
  - `setupIngameTeams()` now adds players into scoreboard teams.
  - `refreshConfigBook()` now updates UHC books in place instead of re-giving the whole config inventory.
  - If the player is holding a UHC book in main hand, the server now sends `OpenWrittenBookS2CPacket` after refresh.
- `src/main/java/me/fallenbreath/tcuhc/task/TaskTitleCountDown.java`
  - KING helmet enchantments were migrated from fake NBT enchant data to real enchantment application.
- `src/main/java/me/fallenbreath/tcuhc/gen/structure/SinglePieceLandStructure.java`
  - Added terrain Y adjustment support, auto bottom-fill helper, and `YOffsetPiece`.
- `src/main/java/me/fallenbreath/tcuhc/gen/structure/VillainHouseStructure.java`
  - Added as the second migrated custom structure on the new 1.21.1 structure/piece path.
- `src/main/java/me/fallenbreath/tcuhc/gen/structure/PlainCottageStructure.java`
  - Added as the third migrated custom structure on the new 1.21.1 structure/piece path.
- `src/main/java/me/fallenbreath/tcuhc/gen/structure/HoneyWorkshopStructure.java`
  - Added as the fourth migrated custom structure on the new 1.21.1 structure/piece path.
- `src/main/java/me/fallenbreath/tcuhc/gen/structure/GreenhouseStructure.java`
  - Added greenhouse desert/snow on the new 1.21.1 structure/piece path without relying on the old weighted-list accessor mixin.
- `src/main/resources/data/tcuhc/worldgen/structure/villain_house.json`
  - Switched from `minecraft:jigsaw` to `tcuhc:villain_house`.
- `src/main/resources/data/tcuhc/worldgen/structure/plain_cottage.json`
  - Switched from `minecraft:jigsaw` to `tcuhc:plain_cottage`.
- `src/main/resources/data/tcuhc/worldgen/structure/honey_workshop.json`
  - Switched from `minecraft:jigsaw` to `tcuhc:honey_workshop`.
- `src/main/resources/data/tcuhc/worldgen/structure/greenhouse_desert.json`
  - Switched from `minecraft:jigsaw` to `tcuhc:greenhouse_desert`.
- `src/main/resources/data/tcuhc/worldgen/structure/greenhouse_snow.json`
  - Switched from `minecraft:jigsaw` to `tcuhc:greenhouse_snow`.
- `src/main/resources/tcuhc.mixins.json`
  - Removed the temporary structure marker mixins now that tcuhc structures are no longer using the jigsaw fallback path.
- `GITHUB_BUILD_CHECKLIST.md`
  - Added a focused checklist for the GitHub Actions build and the first post-build runtime verification pass.
- `src/main/java/me/fallenbreath/tcuhc/TcUhcMod.java`
  - Forces class loading for custom structure registration.
- Enchantment lookup
  - Adjusted away from the removed `Registries.ENCHANTMENT` field toward the 1.21.1 registry-key path.

## Immediate next steps

- Fix 1.21.1 enchantment registry lookup so the new enchantment helpers compile cleanly.
- Structure migration set is now broadly in place; next checkpoint is GitHub build verification and then runtime validation on server.
- After GitHub build, verify structure generation/marker behavior, team coloring, and written-book refresh behavior in game.

---

## 2026-09-12 — mixin 大批量恢复（1.2.4 → 1.2.5）

起始状态：50 个 mixin 只注册了 23 个，一批 1.21.1 API 迁移欠账。
下面每一步都用「本地构建 + 带 `-Dmixin.debug.export=true` 启动 `server/`」实证过。

### 阶段 1 — 启用 15 个编译期即干净的 mixin
`entity.{AbstractMinecartEntity,BoatEntity,DrownedEntity,EntityCategory,MobEntity,PersistentProjectileEntity,SpectralArrowEntity,TradeOffersEnchantBookFactory,WitchEntity}Mixin`、
`item.{ChorusFruit,Item,ItemStack,LivingEntity,PlayerEntity}Mixin`、`item.StatusEffectInstanceAccessor`。

其中 3 个在运行期仍失败，已修：
- `item/PlayerEntityMixin#checkAndAddUhcGAppleStat` —— `eatFood` 多了参数，handler 必须是
  `(World, ItemStack, FoodComponent, CallbackInfoReturnable)`。
- `item/ItemMixin#modifyMaxUseTimeForGApples` —— `Item.getMaxUseTime` 多了 `LivingEntity` 参数。
- `item/LivingEntityMixin` —— `applyFoodEffects` 现在只收 `FoodComponent`，取不到 ItemStack，
  改为在 `eatFood` 里读 `level` 标签；效果列表类型变成 `List<FoodComponent.StatusEffectEntry>`。
  顺带修掉一个**静默 bug**：`StatusEffect` 与 `RegistryEntry<StatusEffect>` 比较能编译
  （class vs interface），但运行时永远 false。

> 教训：Mixin 注解处理器**不校验 `@Inject` 回调签名**，编译通过不代表运行时能应用。
> 用宽松模式（`required:false` + `defaultRequire:0`）启动一次，能一次性收集所有注入失败。

### 阶段 2 — 重写 6 个目标已失效的 mixin
- `ServerPropertiesHandlerMixin` —— 1.21.1 把 `level-type` 从 `GeneratorOptions` 挪进了
  `ServerPropertiesHandler$WorldGenProperties` record，现在改的是该 record 构造器的 `levelType` 参数。
- `entity/StatusEffectInstanceMixin` —— `type` 字段现在是 `RegistryEntry<StatusEffect>`。
- `item/FlowerBlockMixin` —— 两个 `effectInStew*` 字段合并成 `SuspiciousStewEffectsComponent`。
  ⚠️ 这里**不能**调 `effect.value()`：注入点在 `Blocks.<clinit>`，状态效果注册表尚未绑定，会抛
  `Trying to access unbound value ... from registry`。改用 `matchesKey`。
- `entity/EndCrystalEntityMixin` —— `setBeamTarget` 收可空 `BlockPos`，不再是 `Optional`。
- `loot/LootPoolMixin` + `loot/LootTableMixin` —— `entries` / `pools` 由数组变成 `List<...>`。

### 阶段 3 — 移植战利品注入链
1.21.1 **没有** `net.minecraft.loot.LootManager`（战利品表变成 datapack 支撑的 RELOADABLE 动态注册表），
所以旧的 `LootManagerMixin` 无法 1:1 移植。改为 `util/LootInjector` + `ServerLifecycleEvents.SERVER_STARTED`：
1. 用 `ReloadableRegistries.Lookup.getIds/getLootTable` 枚举（注意 `MinecraftServer#getRegistryManager()`
   **拿不到** loot table）。
2. 借 `LootTableAccessor`/`LootPoolAccessor` 原地改写树叶表（苹果掉落）与萤石/青金石表。
3. 新增 `mixins/loot/ItemEntryAccessor`（item 现在是 `RegistryEntry<Item>`）。
4. `util/LootTableUtil` 不再用 Gson 反射（`LootPoolEntry` 是抽象类，Gson 直接拒绝实例化），
   改用 `LootPool.CODEC` / `ItemEntry.CODEC` + `RegistryOps`。
5. `lootpools/apple.json`：`minecraft:alternative` → `minecraft:any_of`（1.21 改名）。

结果：`UHC loot injection done: 10 leaves tables, 3 ore tables`。
这也让 `oreFrequency` 与 `chestItemFrequency` 重新有了读取点（此前**完全没有**）。

### 阶段 4 — 删除死代码
`entity/ServerWorldMixin`（空实现）、`entity/MobEntityAccessor`、`task/PlayerListHeaderS2CPacketAccessor`、
`util/SheepEntityAccessor`（三者零引用）、`recipe/RegistryMixin` + `recipe/SynchronizeRecipesS2CPacketMixin`
（配方体系早前已改走 datapack）。

### 同时修改
- `build.gradle`：`VERSION_1_8` → `VERSION_21`；`tcuhc.mixins.json`：`JAVA_17` → `JAVA_21`
  （两者必须同步）。产物字节码现为 major 65。
- `tcuhc.mixins.json`：注册数 38 → **45**，恢复严格模式（`required: true` / `defaultRequire: 1`）。

### 仍未完成
- 新启用功能只验证到「45 个 mixin 全部应用成功、服务器正常启动」。
  游戏内实测（死亡掉落、分级金苹果、末影水晶索敌、女巫/溺尸掉落、地形类型、矿石宝箱频率）还需真人进服。
- `oreFrequency` 目前只驱动萤石/青金石片段；该项里「钻石/金矿」那部分**仍无读取点**。
