#!/usr/bin/env python3
"""Validate the release version and, optionally, the compiled JAR metadata."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SEMVER = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)")


def properties() -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--artifacts", action="store_true", help="also validate the built binary and sources JARs")
    parser.add_argument("--github-output", help="write workflow outputs to this file")
    args = parser.parse_args()

    props = properties()
    version = props.get("mod_version", "")
    if not SEMVER.fullmatch(version):
        print(f"Invalid mod_version {version!r}: releases require stable semantic version x.y.z", file=sys.stderr)
        return 1

    minecraft = props["minecraft_version"]
    base = props["archives_base_name"]
    jar = Path("build/libs") / f"{base}-mc{minecraft}-{version}.jar"
    sources = Path("build/libs") / f"{base}-mc{minecraft}-{version}-sources.jar"
    if args.artifacts:
        missing = [str(path) for path in (jar, sources) if not (ROOT / path).is_file()]
        if missing:
            print("Missing release artifacts: " + ", ".join(missing), file=sys.stderr)
            return 1
        with zipfile.ZipFile(ROOT / jar) as archive:
            metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata.get("version") != version:
            print(f"JAR version is {metadata.get('version')!r}, expected {version!r}", file=sys.stderr)
            return 1

    outputs = {
        "version": version,
        "tag": f"v{version}",
        "jar": jar.as_posix(),
        "sources_jar": sources.as_posix(),
    }
    print(f"Valid release version {version} for Minecraft {minecraft}")
    if args.github_output:
        output_path = Path(args.github_output)
        with output_path.open("a", encoding="utf-8") as handle:
            for key, value in outputs.items():
                handle.write(f"{key}={value}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
