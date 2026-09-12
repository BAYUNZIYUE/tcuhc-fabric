#!/usr/bin/env python3
"""Structural regression tests for the 2026-09-12 issue-fix run.

These read the source tree. They cannot prove the mod works — only a JDK 21 and
a live server can do that — but they do catch every defect fixed in this run
being silently reintroduced, which is exactly the failure mode this codebase
suffers from: missing switch branches, unregistered mixins, and options that
exist but are unreachable all compile perfectly.

Run:  python docs/agent_run/2026-09-12-issue-fixes/tests/test_structural.py
"""

from __future__ import annotations

import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
SRC = os.path.join(REPO, "src", "main", "java", "me", "fallenbreath", "tcuhc")
RES = os.path.join(REPO, "src", "main", "resources")


def read(*parts: str) -> str:
    with open(os.path.join(SRC, *parts), encoding="utf-8") as handle:
        return handle.read()


def without_comments(text: str) -> str:
    """Strip // and /* */ comments, so a check cannot match its own explanation."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def section(text: str, start_marker: str) -> str:
    """Everything from `start_marker` to the closing brace at its indent level."""
    i = text.index(start_marker)
    depth = 0
    started = False
    for j in range(i, len(text)):
        if text[j] == "{":
            depth += 1
            started = True
        elif text[j] == "}":
            depth -= 1
            if started and depth == 0:
                return text[i:j + 1]
    return text[i:]


class Results:
    def __init__(self) -> None:
        self.passed = 0
        self.failed = 0

    def check(self, name: str, condition: bool, detail: str = "") -> None:
        if condition:
            self.passed += 1
            print(f"  PASS  {name}")
        else:
            self.failed += 1
            print(f"  FAIL  {name}")
        if detail:
            print(f"        {detail}")


def main() -> int:
    r = Results()

    # ---------------------------------------------------------------- T1
    print("T1 (issue 5) — inventory must drop on death")
    pm = read("UhcPlayerManager.java")
    death = section(pm, "public void onPlayerDeath(")
    r.check(
        "onPlayerDeath does not switch to SPECTATOR inline",
        "changeGameMode(GameMode.SPECTATOR)" not in death,
        "an inline switch makes vanilla's `if (!isSpectator()) drop(...)` skip the inventory drop",
    )
    r.check(
        "onPlayerDeath defers the switch",
        "enterSpectatorAfterDeathProcessing" in death,
        "",
    )
    deferred = section(pm, "private void enterSpectatorAfterDeathProcessing(")
    r.check(
        "the deferred path uses a Task and delegates to enterSpectatorNow",
        "TaskOnce" in deferred and "enterSpectatorNow" in deferred,
        "",
    )
    spectate_now = section(pm, "private void enterSpectatorNow(")
    r.check(
        "enterSpectatorNow is what actually switches to SPECTATOR",
        "GameMode.SPECTATOR" in spectate_now,
        "",
    )

    # --------------------------------------------------------------- T10
    # Reported in game: death left the vanilla "Respawn / Title Screen" panel up
    # instead of going straight to spectator.
    print("T10 (in-game feedback) — no death screen, spectate where you died")
    gm = read("UhcGameManager.java")
    init_worlds = without_comments(section(gm, "private void initWorlds()"))
    r.check(
        "a match runs with doImmediateRespawn on",
        "DO_IMMEDIATE_RESPAWN" in init_worlds and "set(true, mcServer)" in init_worlds,
        "without it the client shows the death panel and waits for a click",
    )
    lobby = without_comments(section(gm, "public void returnToLobby()"))
    r.check(
        "returning to the lobby restores vanilla respawn behaviour",
        "DO_IMMEDIATE_RESPAWN" in lobby and "set(false, mcServer)" in lobby,
        "the gamerule is a match-time setting, not a permanent one",
    )
    respawn = without_comments(section(pm, "public void onPlayerRespawn("))
    r.check(
        "a dead player's respawn lands in spectator",
        "enterSpectatorNow" in respawn,
        "an immediate respawn otherwise hands back a survival player at world spawn",
    )
    r.check(
        "a dead player's respawn returns them to the death position",
        "returnToDeathPos" in respawn,
        "",
    )
    r.check(
        "enterSpectatorNow is idempotent",
        "isSpectator()" in spectate_now and "isSpectateTaskArmed()" in spectate_now,
        "it is reachable from both the deferred task and the respawn, in either order",
    )
    gp = read("UhcGamePlayer.java")
    r.check(
        "the spectate-task guard is cleared between matches",
        "spectateTaskArmed = false" in section(gp, "public void resetForNextGame()"),
        "",
    )
    end_game = without_comments(section(gm, "public void endGame()"))
    r.check(
        "game end immediately puts every online player in spectator",
        "getPlayerList()" in end_game and "GameMode.SPECTATOR" in end_game,
        "survivors stay free to inspect the battlefield until /uhc config",
    )

    # --------------------------------------------------------------- T11
    # Reported in game: /uhc stop did nothing visible and said nothing.
    print("T11 (in-game feedback) — /uhc stop must end the match and answer")
    cmd = read("UhcGameCommand.java")
    stop_cmd = without_comments(section(cmd, "private static int executeStop("))
    r.check(
        "executeStop no longer calls the silent endGame() directly",
        "endGame()" not in stop_cmd,
        "endGame alone tears down the HUD without a message or a score board",
    )
    r.check(
        "executeStop goes through stopGameByOperator",
        "stopGameByOperator()" in stop_cmd,
        "",
    )
    r.check(
        "executeStop answers on both branches",
        stop_cmd.count("sendFeedback") >= 2,
        "silence is what made this look broken in the first place",
    )
    stop_mgr = without_comments(section(gm, "public boolean stopGameByOperator()"))
    for needed in ("finalizeAliveTimes", "printFinalScores", "endGame()", "winnerList.setWinner"):
        r.check(
            f"stopGameByOperator settles the match: {needed}",
            needed in stop_mgr,
            "",
        )
    r.check(
        "stopGameByOperator refuses when no match is running",
        "!isGamePlaying" in stop_mgr and "return false" in stop_mgr,
        "",
    )

    # ---------------------------------------------------------------- T2
    print("\nT2 (issue 4) — every game mode must be selectable")
    game_manager = read("UhcGameManager.java")
    enum_block = section(game_manager, "public static enum EnumMode")
    modes = re.findall(r"^\s*([A-Z][A-Z_]*)\s*\(", enum_block, re.MULTILINE)
    book = read("util", "BookNBT.java")
    player_book = section(book, "public static ItemStack getPlayerBook(")
    missing = [m for m in modes if f"case {m}:" not in player_book]
    r.check(
        "BookNBT.getPlayerBook handles every EnumMode",
        not missing,
        f"{len(modes)} modes found: {', '.join(modes)}"
        + (f" | MISSING: {', '.join(missing)}" if missing else " | none missing"),
    )

    # every mode must also be handled where a missing branch is silent
    for label, file_parts, marker in (
        ("spreadPlayers", ("UhcPlayerManager.java",), "public void spreadPlayers("),
        ("automaticFormTeams", ("UhcPlayerManager.java",), "private boolean automaticFormTeams("),
        ("manuallyFormTeams", ("UhcPlayerManager.java",), "private boolean manuallyFormTeams("),
        ("getAdjustBook", ("util", "BookNBT.java"), "public static ItemStack getAdjustBook("),
    ):
        body = section(read(*file_parts), marker)
        gone = [m for m in modes if f"case {m}:" not in body]
        r.check(
            f"{label} handles every EnumMode",
            not gone,
            f"MISSING: {', '.join(gone)}" if gone else "none missing",
        )

    # ---------------------------------------------------------------- T3
    print("\nT3 (issue 6) — no duplicate config books")
    start_config = section(game_manager, "public void startConfiguration(")
    r.check(
        "startConfiguration does not blind-insert a config book",
        "insertStack(BookNBT.getConfigBook" not in start_config,
        "a blind insert is what produced a second book on the second /uhc config",
    )
    r.check(
        "startConfiguration uses the give-or-refresh helper",
        "giveOrRefreshConfigBook" in start_config,
        "",
    )
    regive = section(pm, "public void regiveConfigItems(")
    r.check(
        "regiveConfigItems also uses give-or-refresh",
        "insertStack(BookNBT.get" not in regive,
        "",
    )
    give_or_refresh = section(pm, "public void giveOrRefreshConfigBook(")
    r.check(
        "giveOrRefreshConfigBook replaces in place before inserting",
        "setStack(slot" in give_or_refresh and "insertStack" in give_or_refresh,
        "",
    )

    # ---------------------------------------------------------------- T4
    print("\nT4 (issue 7) — reconfiguring after a match needs no creative mode")
    r.check(
        "UhcGameManager exposes returnToLobby()",
        "public void returnToLobby()" in game_manager,
        "",
    )
    r.check(
        "startConfiguration returns to the lobby when the match has ended",
        "isGameEnded" in start_config and "returnToLobby" in start_config,
        "",
    )
    lobby = section(game_manager, "public void returnToLobby()")
    for needed, why in (
        ("isGameEnded = false", "clears the ended flag so joins stop forcing spectator"),
        ("resetForNextGame", "resets players and teams"),
        ("generateSpawnPlatform", "rebuilds the lobby platform"),
    ):
        r.check(f"returnToLobby {why}", needed in lobby, "")
    reset = section(pm, "public void resetForNextGame()")
    r.check(
        "resetForNextGame restores SURVIVAL mode",
        "GameMode.SURVIVAL" in reset,
        "a spectator cannot click the config book",
    )
    join = section(pm, "public void onPlayerJoin(")
    r.check(
        "players joining during configuration also enter SURVIVAL mode",
        "isConfiguring()" in join and "GameMode.SURVIVAL" in join,
        "reconnecting must not put a lobby player back into adventure mode",
    )

    # ---------------------------------------------------------------- T5
    print("\nT5 (issue 10) — MARINE sea floor")
    mixin = read("mixins", "core", "MinecraftServerMixin.java")
    submerged = section(mixin, "private static class SubmergedDensityFunction")
    r.check(
        "SubmergedDensityFunction no longer uses valueNoise",
        "valueNoise(" not in submerged,
        "valueNoise is flat at every lattice point -> faceted plateaus",
    )
    r.check(
        "SubmergedDensityFunction uses gradientNoise",
        "gradientNoise(" in submerged,
        "",
    )
    r.check(
        "SubmergedDensityFunction domain-warps its input",
        "warpX" in submerged and "warpZ" in submerged,
        "",
    )
    mins = re.findall(r"minValue\(\)\s*\{\s*return\s*(-?[\d.]+)", submerged)
    maxs = re.findall(r"maxValue\(\)\s*\{\s*return\s*(-?[\d.]+)", submerged)
    r.check(
        "declared bounds are [-1.0, 1.0]",
        mins == ["-1.0"] and maxs == ["1.0"],
        f"minValue={mins}, maxValue={maxs} (sample() ranges over [-1, 1])",
    )

    # ---------------------------------------------------------------- T6
    print("\nT6 (issue 2) — stale terrain must be reported")
    world_data = read("util", "UhcWorldData.java")
    r.check("UhcWorldData records a generator identity", "generatorIdentity" in world_data)
    r.check("UhcWorldData can compare it", "checkGeneratorIdentity" in world_data)
    r.check("the identity covers battleType", "battleType" in game_manager and "getGeneratorIdentity" in game_manager)
    inited = section(game_manager, "public void onServerInited()")
    r.check("startup checks for stale terrain", "warnOnStaleTerrain" in inited)
    warn = section(game_manager, "private void warnOnStaleTerrain()")
    r.check("the warning tells the operator to regen", "/uhc regen" in warn)

    # ---------------------------------------------------------------- T7
    print("\nT7 (issue 3) — golden apple stat")
    gapple = read("mixins", "item", "PlayerEntityMixin.java")
    r.check("enchanted golden apples count too", "ENCHANTED_GOLDEN_APPLE" in gapple)
    r.check("the game player is null-checked", "gamePlayer == null" in gapple)
    r.check("UhcGameManager.instance is null-checked", "UhcGameManager.instance == null" in gapple)

    # ---------------------------------------------------------------- T8
    print("\nT8 (issues 8, 9) — pregeneration parallelism is configurable")
    options = read("options", "Options.java")
    pregen = read("task", "TaskPregenerate.java")
    r.check("the option is registered", '"pregenerateParallelism"' in options)
    r.check("it is grouped as a server-start option", "pregenerateParallelism" in section(options, "SERVER_START_OPTIONS"))
    r.check("TaskPregenerate reads it", '"pregenerateParallelism"' in pregen)
    r.check("the hard-coded constant is gone", "PARALLELISM_LIMIT" not in pregen)
    r.check("there is a safe fallback", "DEFAULT_PARALLELISM" in pregen)

    # ---------------------------------------------------------------- T9
    print("\nT9 (found during in-game testing) - /uhc debug terrain must survive MARINE")
    command = read("UhcGameCommand.java")
    debug_terrain = section(command, "private static int debugTerrain(")
    r.check(
        "debugTerrain does not call getSimpleName()",
        "getSimpleName()" not in without_comments(debug_terrain),
        "under MARINE the density functions ARE mixin-merged nested classes; getSimpleName() "
        "reads the InnerClasses attribute and throws IncompatibleClassChangeError",
    )
    r.check(
        "debugTerrain uses the getName()-based helper",
        "simpleClassName(" in debug_terrain
        and "private static String simpleClassName(" in command,
        "",
    )

    # --------------------------------------------------------------- T12
    print("\nT12 (operator feedback) - /uhc reset must explain itself")
    reset_registration = without_comments(section(command, 'then(literal("reset")'))
    r.check("bare /uhc reset has an executable help response", "explainReset" in reset_registration)
    r.check("readable gameplay reset is registered", 'literal("gameplay")' in reset_registration)
    r.check("readable generation reset is registered", 'literal("generation")' in reset_registration)
    r.check(
        "legacy numeric reset forms remain compatible",
        'argument("value", integer(0, 1))' in reset_registration,
    )
    reset_help = section(command, "private static int explainReset(")
    r.check("reset help states that defaults are restored", "默认值" in reset_help)

    # --------------------------------------------------------------- T13
    print("\nT13 (operator feedback) - bonus chests must not roll Unbreaking books")
    bonus_chest = read("gen", "feature", "BonusChestFeature.java")
    r.check(
        "Unbreaking is absent from every live bonus-chest enchantment pool",
        "Enchantments.UNBREAKING" not in without_comments(bonus_chest),
        "the live BonusChestFeature builds these books directly rather than using a loot table",
    )

    # ------------------------------------------------ cross-cutting invariants
    print("\nInvariants — must hold regardless of this run")
    with open(os.path.join(RES, "tcuhc.mixins.json"), encoding="utf-8") as handle:
        config = json.load(handle)
    registered = set(config.get("mixins", [])) | set(config.get("client", []))
    on_disk = set()
    mixin_root = os.path.join(SRC, "mixins")
    for dirpath, _dirs, files in os.walk(mixin_root):
        for name in files:
            if name.endswith(".java"):
                rel = os.path.relpath(os.path.join(dirpath, name), mixin_root)
                on_disk.add(rel.replace(os.sep, ".")[:-5])
    r.check(
        "every mixin on disk is registered",
        on_disk == registered,
        f"{len(registered)} registered / {len(on_disk)} on disk"
        + (f" | unregistered: {sorted(on_disk - registered)}" if on_disk - registered else ""),
    )

    declared = re.findall(r'new Option\("([A-Za-z]+)"', options)
    book_page = section(book, "private static Text getConfigBookPage(")
    unreachable = [o for o in declared if f'getOption("{o}")' not in book_page]
    r.check(
        "every option appears in the config book",
        not unreachable,
        f"{len(declared)} options"
        + (f" | NOT IN BOOK: {', '.join(unreachable)}" if unreachable else " | all reachable"),
    )

    print(f"\n{r.passed} passed, {r.failed} failed")
    return 1 if r.failed else 0


if __name__ == "__main__":
    sys.exit(main())
