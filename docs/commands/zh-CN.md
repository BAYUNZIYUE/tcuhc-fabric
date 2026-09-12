# TC UHC 命令参考

本文档对应当前分支中的命令实现。`<值>` 表示必填参数，`[值]` 表示可选参数。除非另有说明，管理命令需要 Minecraft 权限等级 2。配置项、玩家名、预设名以及固定操作均支持 Tab 补全。

## 玩家命令

| 命令 | 用途 | 示例 |
|---|---|---|
| `/uhc` | 显示 TC UHC 与 Minecraft 版本。 | `/uhc` |
| `/uhc version` | 明确显示相同的版本信息。 | `/uhc version` |
| `/uhc select <team_id>` | 开局前选择队伍或身份。`0`–`7` 依次为红、蓝、黄、绿、橙、紫、青、棕队；`8` 为观察者；`9` 为参战或随机分队。 | `/uhc select 2` |
| `/uhc deathpos` | 查询已记录的死亡位置。 | `/uhc deathpos` |

## 配置与对局流程

| 命令 | 用途 | 示例 |
|---|---|---|
| `/uhc config` | 让所有玩家返回大厅并恢复生存模式，同时发放/打开下一局配置物品。 | `/uhc config` |
| `/uhc configPage <page>` | 打开从 `0` 开始编号的配置书页面（`0`–`4`），主要供书内导航使用。 | `/uhc configPage 2` |
| `/uhc configPageJump <page>` | 打开正常编号的配置页（`1`–`5`）。 | `/uhc configPageJump 3` |
| `/uhc configPagePrompt` | 提示执行玩家在聊天栏输入页码。 | `/uhc configPagePrompt` |
| `/uhc reset` | 解释可用的重置范围；单独执行不会修改任何配置。 | `/uhc reset` |
| `/uhc reset gameplay` | 把玩法、时间、队伍及启动选项恢复为模组默认值。旧写法：`/uhc reset 0`。 | `/uhc reset gameplay` |
| `/uhc reset generation` | 把世界生成频率配置恢复为默认值，需要重新生成世界后生效。旧写法：`/uhc reset 1`。 | `/uhc reset generation` |
| `/uhc regen` | 请求删除并重新生成当前比赛世界；再次输入相同命令确认。 | 连续两次 `/uhc regen` |
| `/uhc cancelRegen` | 取消等待确认的世界重新生成。 | `/uhc cancelRegen` |
| `/uhc start` | 完成就绪检查后请求正常开局；再次输入确认。 | 连续两次 `/uhc start` |
| `/uhc forceStart` | 跳过预生成就绪条件请求开局；再次输入确认。 | 连续两次 `/uhc forceStart` |
| `/uhc cancelStart` | 取消任一种等待确认的开局操作。 | `/uhc cancelStart` |
| `/uhc stop` | 结束当前对局。玩家保持旁观模式以检查战场；执行 `/uhc config` 后所有人返回大厅。 | `/uhc stop` |

`/uhc regen` 会删除并重建比赛世界，请先备份需要保留的数据。`forceStart` 适合诊断或有意跳过预生成，地形不完整时会影响比赛体验。

## 修改配置项

语法为 `/uhc option <name> <add|sub|set>`。

- `add`：枚举切到下一项、布尔值开启、数字增加一个步长。
- `sub`：枚举切到上一项、布尔值关闭、数字减少一个步长。
- `set`：提示游戏内玩家在聊天栏输入值；没有在线玩家时无法从控制台完成交互输入。

示例：

```text
/uhc option teamCount add
/uhc option battleType set
# 然后在聊天栏输入 MARINE
/uhc option pregenerateOnStart sub
```

枚举常量名不区分大小写。主要取值为：`gameMode`：`NORMAL`、`SOLO`、`BOSS`、`GHOST`、`BOMBER`、`KING`、`HUNTER`、`GHOSTHUNTER`；`battleType`：`NORMAL`、`MARINE`、`ICARUS`；`levelType`：`DEFAULT`、`AMPLIFIED`、`LARGEBIOMES`；`difficulty`：`PEACEFUL`、`EASY`、`NORMAL`、`HARD`；`weather`：`NORMAL`、`CLEAR`、`RAIN`、`THUNDER`。布尔值可输入 `true`、`false` 或界面显示的中文值。

### 配置项列表

| 配置 ID | 默认值 | 范围或取值 | 说明 |
|---|---:|---|---|
| `gameMode` | `NORMAL` | 游戏模式枚举 | 对局规则与分队方式。 |
| `battleType` | `NORMAL` | `NORMAL`、`MARINE`、`ICARUS` | 经典、海战或鞘翅战斗。 |
| `levelType` | `DEFAULT` | `DEFAULT`、`AMPLIFIED`、`LARGEBIOMES` | 主世界地形类型。 |
| `disableOceanBiomes` | `true` | 布尔值 | 非海战模式下把海洋群系替换为陆地。 |
| `randomTeams` | `true` | 布尔值 | 随机分队或手动选队。 |
| `teamCount` | `4` | 2–8，步长 1 | 普通模式队伍数量。 |
| `difficulty` | `HARD` | 原版难度 | 对局难度。 |
| `weather` | `NORMAL` | 天气枚举 | 强制天气或保持正常天气。 |
| `daylightCycle` | `true` | 布尔值 | 是否启用昼夜循环。 |
| `friendlyFire` | `false` | 布尔值 | 是否允许队友互相伤害。 |
| `teamCollision` | `true` | 布尔值 | 是否允许队友碰撞。 |
| `greenhandProtect` | `false` | 布尔值 | 是否降低前期受到的伤害。 |
| `forceViewport` | `true` | 布尔值 | 死亡后是否强制旁观队友。 |
| `deathBonus` | `true` | 布尔值 | 队友死亡后是否给予存活队员短暂增益。 |
| `TNTBomber` | `false` | 布尔值 | 小天才模式是否给予初始 TNT。 |
| `borderStart` | `2000` | 100–2,000,000，步长 100 | 初始世界边界大小。 |
| `borderEnd` | `200` | 10–2,000,000，步长 10 | 第一次收缩目标。 |
| `borderFinal` | `50` | 10–2,000,000，步长 10 | 最终边界大小。 |
| `gameTime` | `5400` | 0–1,000,000 秒，步长 100 | 对局总时长。 |
| `borderStartTime` | `1800` | 0–1,000,000 秒，步长 100 | 边界开始收缩的时间。 |
| `borderEndTime` | `4800` | 0–1,000,000 秒，步长 100 | 第一次收缩结束的时间。 |
| `netherCloseTime` | `4800` | 0–1,000,000 秒，步长 100 | 地狱和末地关闭时间。 |
| `caveCloseTime` | `5100` | 0–1,000,000 秒，步长 100 | 洞穴关闭时间。 |
| `greenhandTime` | `4800` | 0–1,000,000 秒，步长 100 | 新手保护持续时间。 |
| `merchantFrequency` | `1.0` | 0–10，步长 0.05 | 商人生成倍率。 |
| `oreFrequency` | `4` | 0–100，步长 1 | 可变矿物生成频率。 |
| `chestFrequency` | `1.0` | 0–10，步长 0.1 | 奖励宝箱生成频率。 |
| `trappedChestFrequency` | `0.2` | 0–1，步长 0.05 | 空/陷阱奖励宝箱比例。 |
| `chestItemFrequency` | `1.0` | 0–10，步长 0.1 | 宝箱可变物品倍率。 |
| `mobCount` | `70` | 10–300，步长 10 | 怪物数量目标。 |
| `netherPregenerate` | `true` | 布尔值 | 预生成时是否包括地狱。 |
| `pregenerateOnStart` | `true` | 布尔值 | 服务器启动后是否自动预生成。 |
| `pregenerateParallelism` | `2` | 1–16，步长 1 | 并行预生成数量；越高占用越多 CPU。 |

世界生成类选项需要 `/uhc regen` 后生效；三个预生成选项在服务器启动时读取；其他配置通常在下一局生效。

## 配置预设

| 命令 | 用途 |
|---|---|
| `/uhc preset` 或 `/uhc preset list` | 列出预设，并标记与当前配置完全一致的预设。 |
| `/uhc preset save <name>` | 保存当前全部配置；名称可使用字母、数字、`_`、`-`。 |
| `/uhc preset save <name> overwrite` | 覆盖同名预设。 |
| `/uhc preset load <name>` | 加载预设；对局进行中需要再加 `confirm`。 |
| `/uhc preset load <name> confirm` | 确认在对局进行中加载同一预设。 |
| `/uhc preset show <name>` | 显示预设内全部值。 |
| `/uhc preset diff <name>` | 比较预设与当前配置。 |
| `/uhc preset delete <name>` | 请求删除预设。 |
| `/uhc preset delete <name> confirm` | 确认删除同一预设。 |

示例：先执行 `/uhc preset save tournament`，以后可用 `/uhc preset diff tournament` 比较，再用 `/uhc preset load tournament` 加载。

## 对局调整与诊断

| 命令 | 用途 |
|---|---|
| `/uhc adjust` | 发放/打开对局调整书。 |
| `/uhc adjust end` | 移除调整书。 |
| `/uhc adjust kill <player>` | 管理员判定一名比赛玩家死亡。 |
| `/uhc adjust resu <player>` | 把已淘汰玩家重新标记为存活。 |
| `/uhc adjust respawn <player>` | 复活并重新生成该玩家。 |
| `/uhc givemorals [owner]` | 给执行玩家全部预定义节操物品，或指定人物的一件物品。 |
| `/uhc debug biome [radius]` | 统计附近群系；半径默认 4，可取 1–16 个区块。 |
| `/uhc debug terrain [radius]` | 输出附近地形诊断；半径默认 4，可取 1–24 个区块。 |
| `/uhc debug terrain <radius> <chunkX> <chunkZ>` | 围绕指定区块坐标执行地形诊断。 |

发放物品、打开书或使用执行者位置的命令应由游戏内玩家执行。从服务器控制台执行时，实现会尽可能使用第一名在线玩家；没有合适玩家时会返回错误提示。
