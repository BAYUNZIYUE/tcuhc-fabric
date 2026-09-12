#!/usr/bin/env python3
"""Cheap syntactic sanity check over the Java files this run touched.

This is not a compiler and does not pretend to be one — a JDK 21 is required for
that and is not installed here. It only catches the class of mistake a
hand-edit can introduce and a reviewer can miss: unbalanced braces or
parentheses, a reference to a helper that was never added, and a missing import
for a type the edit introduced.

Run:  python docs/agent_run/2026-09-12-issue-fixes/tests/test_java_syntax.py
"""

from __future__ import annotations

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
SRC = os.path.join(REPO, "src", "main", "java", "me", "fallenbreath", "tcuhc")

TOUCHED = [
    "UhcGameManager.java",
    "UhcPlayerManager.java",
    "UhcGamePlayer.java",
	"UhcGameCommand.java",
	os.path.join("gen", "feature", "BonusChestFeature.java"),
    os.path.join("util", "BookNBT.java"),
    os.path.join("util", "UhcWorldData.java"),
    os.path.join("options", "Options.java"),
    os.path.join("task", "TaskPregenerate.java"),
    os.path.join("mixins", "core", "MinecraftServerMixin.java"),
    os.path.join("mixins", "item", "PlayerEntityMixin.java"),
]

BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")
STRING_LIT = re.compile(r'"(?:\\.|[^"\\])*"')
CHAR_LIT = re.compile(r"'(?:\\.|[^'\\])*'")


def strip_code(source: str) -> str:
    source = BLOCK_COMMENT.sub("", source)
    source = LINE_COMMENT.sub("", source)
    source = STRING_LIT.sub('""', source)
    source = CHAR_LIT.sub("''", source)
    return source


def read(rel: str) -> str:
    with open(os.path.join(SRC, rel), encoding="utf-8") as handle:
        return handle.read()


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

    print("Delimiter balance in every file this run touched")
    for rel in TOUCHED:
        code = strip_code(read(rel))
        braces = code.count("{") - code.count("}")
        parens = code.count("(") - code.count(")")
        r.check(
            os.path.basename(rel),
            braces == 0 and parens == 0,
            f"braces {braces:+d}, parens {parens:+d}",
        )

    print("\nEvery helper introduced by this run is actually declared")
    manager = read("UhcGameManager.java")
    player_manager = read("UhcPlayerManager.java")
    game_player = read("UhcGamePlayer.java")
    world_data = read(os.path.join("util", "UhcWorldData.java"))
    pregen = read(os.path.join("task", "TaskPregenerate.java"))

    for label, caller, callee_source, decl in (
        ("UhcPlayerManager.giveOrRefreshConfigBook", manager, player_manager,
         "public void giveOrRefreshConfigBook("),
        ("UhcPlayerManager.resetForNextGame", manager, player_manager,
         "public void resetForNextGame("),
        ("UhcWorldData.checkGeneratorIdentity", manager, world_data,
         "public String checkGeneratorIdentity("),
        ("UhcGamePlayer.resetForNextGame", player_manager, game_player,
         "public void resetForNextGame("),
        ("UhcPlayerManager.giveOrRefreshPlayerBook", player_manager, player_manager,
         "public void giveOrRefreshPlayerBook("),
        ("UhcPlayerManager.enterSpectatorAfterDeathProcessing", player_manager, player_manager,
         "private void enterSpectatorAfterDeathProcessing("),
        ("UhcGameManager.returnToLobby", manager, manager, "public void returnToLobby("),
        ("UhcGameManager.warnOnStaleTerrain", manager, manager, "private void warnOnStaleTerrain("),
        ("UhcGameManager.getGeneratorIdentity", manager, manager,
         "public static String getGeneratorIdentity("),
        ("TaskPregenerate.resolveParallelism", pregen, pregen,
         "private static int resolveParallelism("),
    ):
        name = label.split(".")[-1]
        r.check(
            f"{label} is declared where it is called",
            decl in callee_source and (name + "(") in caller,
            "",
        )

    print("\nTypes newly referenced are imported")
    for label, source, type_name, import_line in (
        ("UhcGameManager uses Team", manager, "Team)", "import net.minecraft.scoreboard.Team;"),
		("UhcGameManager uses GameMode", manager, "GameMode.SPECTATOR",
		 "import net.minecraft.world.GameMode;"),
        ("TaskPregenerate uses Options", pregen, "Options.instance",
         "import me.fallenbreath.tcuhc.options.Options;"),
    ):
        r.check(label, type_name not in source or import_line in source, "")

    print("\nNo dangling references to constants this run removed")
    r.check(
        "PARALLELISM_LIMIT / ENQUEUE_THRESHOLD fully replaced",
        "PARALLELISM_LIMIT" not in pregen.replace("DEFAULT_PARALLELISM", "")
        and "ENQUEUE_THRESHOLD" not in pregen,
        "",
    )
    mixin = read(os.path.join("mixins", "core", "MinecraftServerMixin.java"))
    r.check(
        "valueNoise is still defined (LandSurface docs reference it) but unused by SubmergedDensityFunction",
        "private static double valueNoise(" in mixin,
        "",
    )

    print(f"\n{r.passed} passed, {r.failed} failed")
    return 1 if r.failed else 0


if __name__ == "__main__":
    sys.exit(main())
