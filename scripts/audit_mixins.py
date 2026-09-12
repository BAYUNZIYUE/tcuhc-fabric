#!/usr/bin/env python3
"""Audit tcuhc.mixins.json against the mixin classes on disk.

A mixin class that is not listed in tcuhc.mixins.json still compiles but is
never applied, so the compiler cannot warn about it. Run this after adding,
renaming, or moving anything under src/main/java/me/fallenbreath/tcuhc/mixins.

    python scripts/audit_mixins.py

Exit status:
    0  every class on disk is registered
    1  one or more classes on disk are not registered
    2  tcuhc.mixins.json references a class that does not exist
"""

from __future__ import annotations

import json
import os
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG = os.path.join(REPO_ROOT, "src", "main", "resources", "tcuhc.mixins.json")
MIXIN_ROOT = os.path.join(
    REPO_ROOT, "src", "main", "java", "me", "fallenbreath", "tcuhc", "mixins"
)


def registered() -> set[str]:
    with open(CONFIG, encoding="utf-8") as handle:
        config = json.load(handle)
    return set(config.get("mixins", [])) | set(config.get("client", [])) | set(
        config.get("server", [])
    )


def on_disk() -> set[str]:
    found = set()
    for dirpath, _dirnames, filenames in os.walk(MIXIN_ROOT):
        for filename in filenames:
            if not filename.endswith(".java"):
                continue
            path = os.path.join(dirpath, filename)
            rel = os.path.relpath(path, MIXIN_ROOT)
            found.add(rel.replace(os.sep, ".")[: -len(".java")])
    return found


def main() -> int:
    listed = registered()
    present = on_disk()

    missing_file = sorted(listed - present)
    unregistered = sorted(present - listed)

    print(f"registered in tcuhc.mixins.json : {len(listed)}")
    print(f"mixin classes on disk           : {len(present)}")

    status = 0

    if missing_file:
        status = 2
        print(f"\nERROR - listed but no such file ({len(missing_file)}):")
        for name in missing_file:
            print(f"  {name}")

    if unregistered:
        status = status or 1
        print(f"\nNOT REGISTERED - compiles but never applies ({len(unregistered)}):")
        for name in unregistered:
            print(f"  {name}")
        print(
            "\nAdd each to the \"mixins\" array in src/main/resources/tcuhc.mixins.json,\n"
            "or delete the class if it is dead 1.18-era code.\n"
            "See docs/design_principle/05-invariants-and-findings.md section B1."
        )

    if status == 0:
        print("\nOK - every mixin class on disk is registered.")

    return status


if __name__ == "__main__":
    sys.exit(main())
