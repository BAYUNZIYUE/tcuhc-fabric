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

---

## 1.2.5 → 1.2.6：修掉 4 个实机 bug（2026-09-12）

用户在游戏内核验时暴露的问题，逐条修复并实测。

### 1. 预生成永远卡在 99.94%（`TaskPregenerate`）

靠"轮询 `ChunkHolder` 状态"判定完成的机制，在部分区块上永远等不到结果。诊断日志显示两种形态：

```
Pregenerate giving up on chunk [-12, 15] ... (holderPresent=false, actualStatus=null)
Pregenerate re-queued chunk [-6, -4] ... (holderPresent=true, actualStatus=minecraft:light)
```

即 **holder 根本没被创建**，或 **卡在光照阶段**。原代码要空等 `300 次 × 20 tick`（**5 分钟**）
才放弃，于是 overworld 停在 99.94% → nether 不启动 → `preload` 永不创建 → 下次启动又删世界。

**改为三层保护**：单区块 **15 秒**超时 → 超时前每 3 次重试**重新投递一次 ticket**（最多 5 次）
→ 整个任务加**"无进展 60 秒"**兜底；放弃时打印 holder 存在性与实际状态。
实测：`overworld 1分14秒 失败 0`、`the_nether 18秒 失败 0`、`preload ✅ 创建`。

### 2. 溺尸没有必掉三叉戟（`DrownedEntityMixin` + 新增 `ZombieEntityMixin`）

逻辑原本挂在 `DrownedEntity#initialize` 上。但 **1.21 的 `/summon` 根本不调用 `initialize`** ——
`SummonCommand.summon(...)` 里那个调用被命令传入的 `false` 挡在一个 `ifeq` 后面，
所以命令生成的溺尸永远保持默认 0.085 掉率（自然生成才会走 initialize）。

**新增 `ZombieEntityMixin`**，把判定挪到 `ZombieEntity#dropEquipment` 的 `@At("HEAD")`，
覆盖自然生成 / 刷怪蛋 / 结构生成 / `/summon` 全部路径。
实测：5 轮对照 **5/5** 掉落。

### 3. 树叶掉苹果在真实挖掘下完全失效（`apple.json`）

`match_tool` 谓词用的是 1.20 旧格式：

```json
"predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}
```

1.21 已把附魔谓词挪进 `predicates`：

```json
"predicate": {"predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", ...}]}}
```

旧字段**不被识别、也不报错**，谓词退化成"匹配任何物品" → `match_tool` 恒为 true
→ 外层 `inverted` 恒为 false → **真实挖掘时苹果永远不掉**。

**改为 1.21 格式**（并与原版 `oak_leaves.json` 一致，`items` 也改用字符串写法）。
实测（BLOCK context）真实挖掘 **15/1200 = 1.25%**，与配置吻合；修复前 **0/2000**。
剪刀 / 精准采集仍然正确拦截。

> ⚠️ **教训**：`/loot ... loot <table>` 走 **COMMAND context（没有 TOOL 参数）**，
> 会掩盖所有依赖工具的谓词 bug，测出假的"功能正常"；
> 必须用 `/loot ... mine <pos> [<tool>]`（**BLOCK context**，玩家挖方块走的正是这条）。

### 4. 附魔书交易注入失败（`TradeOffersEnchantBookFactoryMixin`）

1.21 的 `TradeOffer` 构造器从 `(ItemStack,ItemStack,ItemStack,IIF)` 改成
`(TradedItem, Optional<TradedItem>, ItemStack, I, I, F)`，旧的字符串 target 匹配到 0 个目标。
**改为新描述符**。这类"字符串 target"的 mixin 注解处理器**不校验**，只在运行时暴露。

### 顺带

- `LootInjector`：补 `ServerLifecycleEvents.END_DATA_PACK_RELOAD` 钩子。
  旧版注入挂在 `LootManager#apply`（每次数据包重载都会重跑）；移植后只挂 `SERVER_STARTED`，
  一旦有人 `/reload`，loot table 会从 JSON 重建，注入被**静默清掉**。并补回逐表诊断日志。
- `tcuhc.mixins.json`：注册数 45 → **46**（新增 `entity.ZombieEntityMixin`）。
