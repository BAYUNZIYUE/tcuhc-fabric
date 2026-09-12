# Automation and documentation update

Date: 2026-09-12  
Target: Minecraft 1.21.1 / TC UHC 1.2.9

## Delivered

- Added the root `tests/` suite. It runs repository metadata/resource checks, command and option documentation coverage, mixin auditing, and all regression suites developed during this agent run.
- Expanded GitHub CI to run on every push and pull request, compile with Java 21, package the mod, and start a disposable dedicated server as a smoke test.
- Added a **Build or Release** workflow. Its manual `create_release` input selects artifact-only compilation or publication, while a matching `v<version>` tag publishes automatically after all checks pass.
- Added strict `major.minor.patch` version and built-JAR metadata validation.
- Added a local GitHub CLI dispatcher with explicit `--build-only` and `--release` choices.
- Replaced the historical root README with a formal project README.
- Added complete English and Simplified Chinese command manuals, including all 33 configuration options.
- Updated the project version from 1.2.8 to 1.2.9.

## Verification

```text
python tests/run_all.py
  17/17 repository checks passed
  46/46 mixins registered
  26 Java structure checks passed
  62 issue-regression checks passed
  9 Marine terrain numerical checks passed

.\gradlew.bat clean build --console=plain
  BUILD SUCCESSFUL

python scripts/validate_release.py --artifacts
  Valid release version 1.2.9 for Minecraft 1.21.1
```

The deployable output is `build/libs/tcuhc-fabric-mc1.21.1-1.2.9.jar`. The CI smoke test operates only in a fresh GitHub checkout; its local entry point deliberately requires `--allow-local` because it replaces files in `run/`.
