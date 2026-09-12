#!/usr/bin/env python3
"""Runs every automated check in this agent run and reports a single verdict.

    python docs/agent_run/2026-09-12-issue-fixes/tests/run_all.py

Exit status is 0 only when every suite passes. These suites verify source
structure and pure algorithms; they do **not** compile the mod or run it. See
TEST_PLAN.md for the in-game procedure that still requires a JDK 21.
"""

from __future__ import annotations

import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))

SUITES = [
    ("test_java_syntax.py", "Java delimiter balance and helper/import wiring"),
    ("test_structural.py", "source invariants for every fixed issue"),
    ("test_noise.py", "MARINE sea-floor numerics (T5)"),
]


def main() -> int:
    failures = []
    for script, blurb in SUITES:
        header = f"{script} — {blurb}"
        print("=" * 78)
        print(header)
        print("=" * 78)
        # Flush before handing stdout to the child, or the headers land at the end of a redirect.
        sys.stdout.flush()
        result = subprocess.run(
            [sys.executable, os.path.join(HERE, script)],
            cwd=REPO,
        )
        if result.returncode != 0:
            failures.append(script)
        print()

    print("=" * 78)
    if failures:
        print(f"FAILED: {', '.join(failures)}")
        return 1
    print(f"All {len(SUITES)} suites passed.")
    print("NOTE: compilation and in-game behaviour are NOT covered here -")
    print("      they need a JDK 21. See TEST_PLAN.md.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
