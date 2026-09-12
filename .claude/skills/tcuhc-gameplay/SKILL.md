---
name: tcuhc-gameplay
description: Add or change TC-UHC gameplay - a new EnumMode, battle type, config option, book page, command, or scheduled Task. Use when touching UhcGameManager, UhcPlayerManager, options/, task/, BookNBT, or UhcGameCommand, so the change lands in every place the codebase requires.
---

# Changing TC-UHC gameplay

Architecture reference: `docs/design_principle/02-gameplay-and-config.md`.

## Adding a config option

Two edits, both required:

1. `options/Options.java` constructor:

```java
addOption(new Option("myKnob", "中文名", new OptionType.BooleanType(), false)
        .setDescription("这个选项做什么。"));
```

   - Types: `IntegerType(min,max,step)`, `FloatType(min,max,step)`,
     `BooleanType()`, `EnumType(SomeEnum.class)`.
   - `.addTask(Options.instance.taskReselectTeam)` if changing it invalidates
     team selection (mode, battle type, team count...).
   - `.setNeedToSave()` marks it a *generation* option — it then resets with
     `/uhc reset 1` instead of `/uhc reset 0`, and survives a gameplay reset.

2. `util/BookNBT.getConfigBookPage(...)` — add
   `.append(createOptionText(options.getOption("myKnob")))` to the right page.
   An option that is registered but not on a page is invisible to operators.

Read it with `Options.instance.getBooleanOptionValue("myKnob")` (or
`getInteger` / `getFloat` / `getOptionValue` for enums, which needs a cast).

Options persist to `uhc.properties` in the server's working directory on every
change. Unknown keys in that file warn rather than crash, so renaming an option
orphans the old value silently.

## Adding an `EnumMode`

`EnumMode` has no safe default anywhere. A new constant must be handled in
**all** of these or the mode is silently broken:

| File | What it controls |
| --- | --- |
| `UhcGameManager.EnumMode` | the constant + `deathRegen` flag + `toString()` Chinese name |
| `UhcPlayerManager.automaticFormTeams()` | team layout when `randomTeams` is on |
| `UhcPlayerManager.manuallyFormTeams()` | team layout when `randomTeams` is off |
| `UhcPlayerManager.spreadPlayers()` | spawn houses, max health, initial gamemode |
| `UhcPlayerManager.addInitialEquipments()` | contents of the spawn chest |
| `TaskTitleCountDown.onFinish()` | start-of-match kit and permanent effects |
| `BookNBT.getPlayerBook()` | how players pick a side (**KING is currently missing here**) |
| `BookNBT.getAdjustBook()` | the operator's kill/resurrect page layout |
| `TaskScoreboard.onTimer()` | compass targeting and any timeout win condition |

Verify with:

```bash
grep -rn "getGameMode()\|EnumMode\." src/main/java --include=*.java
```

Mode-specific per-tick behaviour goes in a `Task` added from
`TaskTitleCountDown.onFinish()`, following `TaskKingEffectField` — which
cancels itself when the mode or game state no longer matches.

## Adding a scheduled behaviour

Never use timers, executors, or raw tick counters. Extend `Task`:

```java
// periodic
class MyTask extends Task.TaskTimer {
    MyTask() { super(0, 20); }           // delay, interval in ticks; interval<=0 == one-shot
    @Override public void onTimer() {
        if (!UhcGameManager.instance.isGamePlaying()) { this.setCanceled(); return; }
        ...
    }
}
UhcGameManager.instance.addTask(new MyTask());

// "as soon as this player's entity exists"
gameManager.addTask(new TaskFindPlayer(gamePlayer) {
    @Override public void onFindPlayer(ServerPlayerEntity p) { ... }
});
```

Owners: `UhcGameManager` for global tasks, `UhcGamePlayer` for per-player tasks,
`Option` for fire-on-change. Using a `Task` is what lets `cancelTasks()` stop
everything cleanly on `/uhc regen`.

## Adding a `/uhc` subcommand

In `UhcGameCommand.registerCommand`, chained off `rootNode`:

- `.requires(UhcGameCommand::isOp)` for anything operational (permission 2).
- `requirePlayer(source, "动作名")` to get a player, with a console fallback to
  the first online player; it returns null and reports if there is none.
- `requireGamePlayer(...)` when you need the `UhcGamePlayer` wrapper.
- Feedback is a **supplier**: `source.sendFeedback(() -> Text.literal("..."), false)`.
- Destructive commands use the static two-step confirm pattern
  (`regen_confirm` / `start_confirm`) plus a matching `cancelX`.
- Player-name arguments use
  `suggests((c, b) -> suggestMatching(getGamePlayerNameSuggestion(), b))`.

Anything the config book should be able to trigger must be a command — book
lines are `ClickEvent.RUN_COMMAND`.

## Player state rules

- `UhcGamePlayer` stores a `UUID`. Always go through `getRealPlayer()` →
  `Optional<ServerPlayerEntity>`. Never cache the entity.
- Stats only record while `isGamePlaying` is true.
- Player-facing strings are Simplified Chinese, hardcoded. Log messages are
  English. There is no lang file; do not start a partial i18n migration.
- New player-visible output must use vanilla packets only (title, scoreboard,
  boss bar, written book, player-list header, chat). This mod is server-only and
  vanilla clients must be able to join.

## Before you finish

- `python scripts/audit_mixins.py` if you touched `mixins/`.
- `./gradlew build` (needs JDK 21 — see the `tcuhc-dev` skill).
- Drive the change in a real match; there is no unit test harness here.
