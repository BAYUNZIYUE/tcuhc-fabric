#!/usr/bin/env python3
"""Dispatch the GitHub build/release workflow for the current committed branch."""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def capture(*command: str) -> str:
    return subprocess.check_output(command, cwd=ROOT, text=True).strip()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--build-only", action="store_true", help="compile and upload an Actions artifact only")
    action.add_argument("--release", action="store_true", help="compile and publish a GitHub Release")
    parser.add_argument("--ref", help="committed branch or tag to build (default: current branch)")
    args = parser.parse_args()

    if not shutil.which("gh"):
        parser.error("GitHub CLI (gh) is required and must be authenticated")
    if capture("git", "status", "--porcelain"):
        parser.error("working tree is not clean; commit the version and code first")
    ref = args.ref or capture("git", "branch", "--show-current")
    if not ref:
        parser.error("detached HEAD: provide --ref")
    validation = subprocess.run([sys.executable, "scripts/validate_release.py"], cwd=ROOT)
    if validation.returncode:
        return validation.returncode

    create_release = "true" if args.release else "false"
    result = subprocess.run(
        ["gh", "workflow", "run", "release.yml", "--ref", ref, "-f", f"create_release={create_release}"],
        cwd=ROOT,
    )
    if result.returncode == 0:
        print(f"Dispatched {('release' if args.release else 'build-only')} workflow for {ref}.")
        print("Monitor it with: gh run watch")
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
