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

---

## 2026-09-12 — 预生成选项 + 非海战无海洋群系（未提交）

### 需求 1：预生成可选开关

新增两个配置项：

| 选项 | 默认 | 作用 |
|---|---|---|
| `netherPregenerate` | `true` | 关闭后预生成只跑主世界，跳过地狱 |
| `pregenerateOnStart` | `true` | 关闭后世界创建完即视为就绪，不自动预生成 |

⚠️ **前置重构（必须做，否则选项会毁数据）**：`preload` 标记原本写在
`TaskPregenerate.onFinish()` 的「非 overworld 分支」里，语义实际是「**地狱生成完了**」。
任何「跳过某阶段」的选项都会让这条链断掉——不写 `preload` → 下次启动
`tryUpdateSaveFolder()` 判定世界未就绪 → **删掉整个世界目录**。
已把写入归位到 `UhcGameManager.setPregenerateComplete()`，让「预生成完成」只有一个出口。

### 需求 2：非海战模式下不生成海洋群系

`MinecraftServerMixin.forceMarineOverworldBiomes` 扩成三分支：

```
MARINE                      → 现有纯海洋逻辑（不动）
非 MARINE + disableOceanBiomes → 陆地化
非 MARINE + 关闭选项         → 原版行为
```

陆地化实现要点：

- 用 `MultiNoiseBiomeSourceParameterList.getPresetToEntriesMap()` 取**原版 overworld 参数列表**
  （public static，不需要 accessor / invoker）。
- 只替换 biome key，**参数区间原样保留** → 原本是海洋的噪声区间现在生成陆地，气候分布不变。
- 温度映射按群系名判断：`frozen → snowy_plains`、`cold → taiga`、`lukewarm → forest`、
  `warm → desert`、其余 `ocean/deep_ocean → plains`。
  ⚠️ **判断顺序必须 frozen → cold → lukewarm → warm** —— `"lukewarm"` 字符串里包含 `"warm"`。

新配置项 `disableOceanBiomes`（默认 `true`，仅海战以外生效）。

### 新增调试命令

`/uhc debug biome [radius]` —— 在半径 N 个区块的网格上采样 biome source，输出群系分布与海洋占比。
**支持控制台执行**（无玩家时以世界出生区块为中心），所以可以纯 RCON 自动化验证。
这个命令是环境 2 唯一可靠的验收手段——光看日志只能知道「替换了 20 个条目」，
不知道生成出来到底有没有海洋。

### 实测（4 组隔离实例，`--universe` 指向独立目录，主世界与用户配置全程未受影响）

| 场景 | 配置 | 结果 |
|---|---|---|
| T1 | NORMAL + 陆地化 + `pregenerateOnStart=false` | 海洋 **0/289 = 0.0%**；世界秒级就绪；`preload` 正常创建 |
| T2 | MARINE | 海洋 **289/289 = 100.0%**，`warm_ocean` 单一种；海战分支未受影响 |
| T3 | `netherPregenerate=false` | `the_nether` 出现 **0 次**，主世界 15 秒跑完，`preload` 创建 |
| T4 | `netherPregenerate=true`（对照） | `the_nether` 正常预生成 18 秒，`preload` 创建 |

T1 的群系分布（9 种，非单一群系）：
`flower_forest 29.1% / river 17.6% / meadow 17.0% / stony_shore 15.6% / plains 13.8% / sunflower_plains 3.5% / dripstone_caves 2.1% / beach 0.7%`

> **观察点**：`river`、`beach`、`stony_shore` 仍会出现。它们本身不是海洋群系（未被过滤），
> 但在「海洋变陆地」之后，海岸类群系会显得孤立。如果后续想去掉，把过滤条件从
> `contains("ocean")` 扩展到 `river/beach/stony_shore` 即可。

> ⚠️ **改动只对新生成的区块生效**。已生成的世界必须 `/uhc regen` 才能看到效果，
> 否则新旧地形会出现硬边界。`levelType` 改动同理。

---

## 无海洋地形的地形部分修复（2026-09-12 19:00，未提交）

**背景**：上一版只把群系换成陆地，地形仍是原版海盆 —— `sea_level=63` 直接把深海填成水，
表现是「沙漠里一大片水域」（`warm_ocean` → `desert` 的那片最显眼）。

**三条必须知道的结论**（详见 skill `fabric-mod-build-env-check` 的 5.16）：

1. **换 `NoiseRouter` 的字段没用。** `continents`/`depth` 是命名引用，
   而 `final_density`/`initial_density_without_jaggedness` 是**内联**的、各持有
   depth/offset 子树的独立副本。**只能包裹那两棵函数树本身。**
2. **自定义 `DensityFunction` 不能 `sample()` 别的 density function。**
   `continents` 是 `flat_cache(...)`，外部直接采样恒返回 0.0（实测 1850 万次 `patched=0`）。
   需要读别的函数时必须用 `DensityFunctionTypes` 原生算子拼装。
3. **加常数抬升是错的**：海盆相对起伏（42 格）大于它到海平面的距离，抬够了会把高处顶到 320。
   正解是**替换**起伏。

**最终实现**（`MinecraftServerMixin.createLandChunkGenerator`）：
```
weight   = smoothstep(clamp((−0.19 − continents) / 0.15, 0, 1))   // 原生算子
landSurf = 自带多倍频 value noise 的地表（基准 78，振幅 8+4+2，RAMP 6）
newFinal = DensityFunctionTypes.lerp(weight, originalFinal, landSurf)
newInit  = DensityFunctionTypes.lerp(weight, originalInitDensity, landSurf)
```
`weight` 在 `continents ≥ -0.19`（河流与陆地）**恒为 0** → 那部分一字不动。

**实测（固定 `level-seed=20260912`，脚本 `verify/ab_terrain.py`）**

| 位置 | 原版 | 陆地化 |
|---|---|---|
| 深海盆地 (20,-31) | 水 100%，地面 y=11~53 均 41.2 | **水 0%，y=68~82 均 74.8**，柱顶 `Grass Block/Dirt` |
| 过渡带 (10,-16) | — | 72% 干燥，y=54~79，柱顶 `Birch Leaves` |
| 近出生点 (-6,-5) | 水 77.6% | 79.6%（河流/海滩，**未被动**） |

放大化（`levelType=AMPLIFIED`）同样成立（`continents` 与原版共用）。

**新增**：`/uhc debug terrain [radius] [chunkX chunkZ]` —— 口径为 `OCEAN_FLOOR`，
输出地面高度 min/max/avg、高差、高出海平面比例、**中心柱顶部 4 格方块**；
控制台不带坐标固定采样区块 (0,0)，便于同种子 A/B。

**顺带**：`ServerPropertiesHandlerMixin` 加了一行永久诊断日志，实测确认 level-type 改写生效
（`option=放大化, server.properties='minecraft:normal' -> 'minecraft:amplified'`）。
注意 1.21 的 server.properties 默认值是 `minecraft:normal`。

**调参入口**：`MinecraftServerMixin` 顶部四个常量 —— `OCEAN_EDGE = -0.19`、
`OCEAN_BLEND_WIDTH = 0.15`（过渡带宽度）、`LAND_SURFACE_BASE_HEIGHT = 78`、
`LAND_SURFACE_RAMP = 6`；地表噪声振幅写在 `LandSurfaceDensityFunction.surfaceHeight()`。

---

## 地表噪声换成 Perlin（2026-09-12 19:25，未提交）

**反馈**：地形「有棱有角」，希望能更随机。

**根因**：`LandSurfaceDensityFunction.surfaceHeight()` 原来用 **value noise**
（方格点阵 + smoothstep 插值）。smoothstep 在格点处导数为 0 → 格点附近整片是平的、
坡度全挤在格子中线上 → 一块块带直边的四边形平台。同一 64×16 方块窗口实测：
**等高连续段平均 16.8 格（最长 34 格）、2×2 完全等高占 90.2%** —— 就是那种豆腐块感。

**改法**：

- `gradientNoise()` —— 经典 Perlin（格点处值恒为 0，极值落在格点之间，无平区）；
  quintic fade 保证 C2。`gradientDot()` 用 8 个等长梯度方向（对角线预缩放 1/√2）。
  `hash64()` 用 murmur3 finalizer（原来的 16 bit hash 会分块）。
- **域扭曲**：`warpX = x + gradientNoise(x/240, z/240) * 54`（Z 同理，换种子与偏移）。
  这是把点阵彻底藏起来的关键。
- 5 个倍频，各自带固定旋转角：230 / 97 / 43 / 18 / 7 方块，幅度 8 / 4 / 2 / 1.2 / 0.8。
- `valueNoise()` 保留给海战的 `SubmergedDensityFunction`，注释已注明不要用于大面积地表。

**结果**（同一窗口）：等高连续段平均 **16.8 → 6.2 格**，2×2 等高占比 **90.2% → 71.1%**，
局部起伏 **2 → 8 格**。引擎实测（T5，放大化+陆地化，区块 20/-31）：y=74~84 均 79.5、
高出海平面 100%、中心柱 `Grass Block/Dirt`。

**新增调试输出**：`/uhc debug terrain` 现在会打印 **64×16 ASCII 地形剖面图（每格 1 方块，
高度自动拉伸到本图 min/max，`~`=水）**。数字看不出「棱角」，图能。

**离线对比工具**：`verify/noise_compare.py` —— 纯 Python 复刻新旧两套噪声，
用引擎实测高度校验过精度，可快速迭代噪声参数而不用反复重启服务器。

**调参入口**：`LandSurfaceDensityFunction.surfaceHeight()` 里的倍频表
（波长/幅度/旋转角/种子），以及 `MinecraftServerMixin` 顶部的 `LAND_SURFACE_BASE_HEIGHT`。
