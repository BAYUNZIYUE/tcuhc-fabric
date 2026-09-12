# 01 — Architecture

## 1. Shape of the mod

TC-UHC is a **server-only** Fabric mod (`"environment": "server"` in
`fabric.mod.json`). It has no client entrypoint and no client mixins. Everything
the player sees is built out of vanilla server→client packets: written books,
scoreboards, titles, boss bars, the player-list header, and chat.

That constraint is load-bearing. It is why the config UI is a *written book* with
`ClickEvent`s rather than a screen, why the "hunter compass" is implemented by
sending `PlayerSpawnPositionS2CPacket`, and why the HUD is a
`PlayerListHeaderS2CPacket`. **Vanilla clients must be able to join.** Any new
feature has to be expressible through vanilla packets.

## 2. Ownership graph

```
MinecraftServer  (vanilla)
└── MinecraftServerMixin          ← owns the instance, drives the tick
    └── UhcGameManager.instance   ← process-global singleton, extends Taskable
        ├── UhcPlayerManager      ← players, teams, death/respawn/spectate
        │   ├── List<UhcGamePlayer>  all / combat / observer
        │   └── List<UhcGameTeam>    formed at game start
        ├── UhcConfigManager      ← who is configuring, chat-input state machine
        ├── Options.instance      ← typed options, uhc.properties persistence
        ├── UhcWorldData          ← per-save JSON (uhc.json)
        ├── LastWinnerList        ← lastwinners.txt
        └── MsptRecorder          ← rolling 100-tick MSPT window
```

`UhcGamePlayer` holds a `UUID`, not a `ServerPlayerEntity`. The real entity is
resolved on demand through `getRealPlayer()` returning `Optional`. This is what
makes rejoin-mid-game work. **Never cache a `ServerPlayerEntity` across ticks.**

`Options.instance` is initialised by a *static field initialiser*, so it exists
before `UhcGameManager` does. That is deliberate: mixins such as the spawn-group
cap need option values before the game manager is constructed.

## 3. Lifecycle, anchored to mixin injection points

All of it lives in `mixins/core/MinecraftServerMixin.java`:

| Vanilla point | Injection | What happens |
| --- | --- | --- |
| `MinecraftServer.<init>` TAIL | `constructUhcGameManager` | `new UhcGameManager(server)` |
| `createWorlds` @ `ServerWorld.<init>` | `adjustOverworldBiomes` (`@ModifyArgs`) | swaps the overworld `ChunkGenerator` for MARINE, or for the `disableOceanBiomes` land generator |
| `createWorlds` after first `Map.put` | `setSpawnPosTo00` | forces world spawn to (0, top, 0) |
| `loadWorld` RETURN | `postInitUhcGameManager` | `onServerInited()` — scoreboard, pregeneration, spawn platform |
| `tick` HEAD | `tickDurationSamplingStart` | MSPT sampling |
| `tick` @ constant `"tallying"` | `tickUhcGameManager` | **the tick pump** |
| `runTasksTillTickEnd` @ `runTasks` | `tickDurationSamplingEnd` | MSPT sampling |

`LevelStorageMixin` hooks `createSession` and calls
`UhcGameManager.tryUpdateSaveFolder`, which **deletes the whole world folder**
unless a `preload` marker file exists. This is how "regenerate terrain" works —
see §6.

## 4. The Task scheduler

`task/Task.java` and `task/Taskable.java` are ~90 lines and are the *only*
scheduling mechanism in the mod.

```java
class Task {
    void onAdd()            {}
    void onUpdate()         {}
    boolean hasFinished()   { return true; }   // default: one-shot
    void onFinish()         {}
    void cancel()           {}
}
```

`Taskable.updateTasks()` runs every task's `onUpdate()`, then removes and
finishes the ones reporting `hasFinished()`. Owners of task lists:

- `UhcGameManager` — global tasks, pumped once per server tick.
- `UhcGamePlayer` — per-player tasks, pumped from `UhcGameManager.tick()`.
- `Option` — tasks fired on *value change* (e.g. `taskSaveProperties`,
  `taskReselectTeam`). This is the mod's change-notification mechanism.

Subclasses you should reuse rather than reinvent:

| Class | Use for |
| --- | --- |
| `Task.TaskTimer(delay, interval)` | anything periodic; `interval <= 0` means fire once |
| `TaskOnce(parent)` | run an existing `Task`'s body exactly once, next tick |
| `TaskFindPlayer(gamePlayer)` | "do X as soon as this player's entity exists" — the standard way to act on a player who may still be connecting |

`TaskFindPlayer` is the idiom for deferred per-player work; `spreadPlayers()` and
`TaskTitleCountDown.onFinish()` both use anonymous subclasses of it.

### Rule
Do not use `java.util.Timer`, executor services, or raw tick counters in new
code. Wrap it in a `Task` so `cancelTasks()` on regen/stop actually stops it.

## 5. The hook surface

`UhcGameManager` exposes a small set of `onXxx` entry points, each wrapped in
`try/catch { e.printStackTrace(); }`. Mixins call *these*, not the managers
directly. The catch-all is intentional: a thrown exception inside a mixin
callback during a death or a join would otherwise take down the server
mid-match.

| Entry point | Called from |
| --- | --- |
| `onPlayerJoin` | `PlayerManagerMixin.onPlayerConnect` |
| `onPlayerChat` | `ServerPlayNetworkHandlerMixin.onChatMessage` (**cancels vanilla chat entirely**) |
| `onPlayerDeath` | `ServerPlayerEntityMixin.onDeath`, `compat.carpet.EntityPlayerMPFakeMixin` |
| `onPlayerRespawn` | `PlayerManagerMixin.respawnPlayer` |
| `onPlayerDamaged` | `entity.PlayerEntityMixin.damage` |
| `onPlayerSpectate` | `ServerPlayNetworkHandlerMixin`, `ServerPlayerEntityMixin.setCameraEntity` |

### Rule
New gameplay hooks follow the same shape: a thin mixin that delegates to a
guarded `UhcGameManager.onXxx(...)`, which delegates to the owning manager.
Keep logic out of mixin bodies.

## 6. Persistence and the regen protocol

Three files, three scopes:

| File | Scope | Written by |
| --- | --- | --- |
| `uhc.properties` (server CWD) | operator settings, survives regen | `Options.savePropertiesFile()`, fired by `Option`'s task on every change |
| `<world>/uhc.json` | per-save derived data (`spawnPlatformHeight`, nether-fortress choice) | `UhcWorldData.save()` |
| `<world>/preload` | marker: "this world is fully pregenerated, do not wipe" | `TaskPregenerate.onFinish()` |
| `lastwinners.txt` (server CWD) | winner particles across restarts | `LastWinnerList` |

**Terrain regeneration** (`/uhc regen`, two-step confirm) cannot restart a JVM
from inside a mod, so it delegates: `regenerateTerrain()` locates
`restart-server.sh` (POSIX) or `restart-server.bat`/`.cmd` (Windows) beside the
world folder, launches it with the current PID, deletes the `preload` marker, and
calls `mcServer.stop(false)`. On the next boot `LevelStorageMixin` sees no
`preload` marker and deletes the world folder, so the server regenerates.

`deleteFolder` deliberately preserves `carpet.conf`.

### Consequence
A dev/test environment **must** ship a restart helper next to the world folder,
or `/uhc regen` throws `IllegalStateException`. Note that both helpers delegate
to a `start-server.sh` / `start-server.bat` that **the repository does not
provide** — the launch command is environment-specific, so the operator supplies
it. Without it the server stops and never restarts. See `AGENT.md`.

## 7. Localisation

All player-facing strings are hardcoded Simplified Chinese in Java source. There
is no `lang` file and no translation key indirection. Follow the existing style
(Chinese user text, English log messages) rather than introducing a partial i18n
layer — a half-migrated string table would be worse than the current consistency.
