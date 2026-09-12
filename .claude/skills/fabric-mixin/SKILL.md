---
name: fabric-mixin
description: Write, register, and debug SpongePowered Mixins for the TC-UHC Fabric mod on Minecraft 1.21.1. Use when adding or editing anything under src/main/java/me/fallenbreath/tcuhc/mixins, when an injector fails to apply, when a mixin seems to have no effect, or when choosing between a mixin, an accessor, and an access widener.
---

# Mixins in TC-UHC

Config: `src/main/resources/tcuhc.mixins.json`
Access widener: `src/main/resources/tcuhc.accesswidener` (currently empty)
Compatibility level: `JAVA_17`. `injectors.defaultRequire: 1`.

## The rule that costs the most time

**A mixin class that is not listed in `tcuhc.mixins.json` compiles fine and does
absolutely nothing.** As of the last audit, 27 of the 50 mixin classes in this
repo are in that state. Before concluding a hook is broken, confirm it is
registered:

```bash
python scripts/audit_mixins.py
```

Adding a mixin is always two edits: the `.java` file **and** the `mixins` array
in `tcuhc.mixins.json`. The array entry is the path below
`me.fallenbreath.tcuhc.mixins` with `/` replaced by `.` and no `.java`, e.g.
`entity.PlayerEntityMixin`.

## Choosing a tool

| Need | Use |
| --- | --- |
| run code at a point in a vanilla method | `@Inject` |
| replace a call inside a method | `@Redirect` (one per target — conflicts with other mods) |
| change one argument of a call | `@ModifyArg` / `@ModifyArgs` |
| change a hard-coded number | `@ModifyConstant` |
| change a local before it is stored | `@ModifyVariable` |
| read/write a private field | `@Accessor` in an `interface` mixin |
| call a private method | `@Invoker` in an `interface` mixin |
| attach state to a vanilla object | interface in `interfaces/` + `implements` on the mixin |
| access something no mixin can reach | access widener — last resort |

Prefer `@Inject` over `@Redirect` where both work: `@Redirect` is exclusive, so
two mods redirecting the same call crash.

## House conventions

- Package by intent, not by vanilla package: `core/`, `entity/`, `item/`,
  `block/`, `loot/`, `recipe/`, `network/`, `task/`, `worldgen/`, `util/`,
  `compat/`. Two mixins may share a simple name in different sub-packages when
  they target the same class for different reasons.
- Suffix accessor/invoker interfaces with `Accessor`.
- Mark injected fields `@Unique`.
- Keep logic **out** of the mixin. Delegate to
  `UhcGameManager.onXxx(...)`, which is wrapped in try/catch, or to a helper
  class. Mixin bodies should be a null check plus a call.
- Null-check `UhcGameManager.instance` in anything that can run before the
  server constructor finishes (worldgen workers, `Registries` static blocks).
- Third-party targets: `@Pseudo` + `@Mixin(targets = "fully.qualified.Name")`,
  and reference obfuscated/intermediary method names as strings
  (`compat/carpet/EntityPlayerMPFakeMixin` uses `method_6078`). The mod must
  still load when the other mod is absent.

## Registry freezing

Custom structure and feature types must be force-loaded before the worldgen
registries freeze. `mixins/core/RegistriesMixin` has a `static {}` block that
touches every codec. **Add new types there** or the datapack will fail to parse
with an unknown-type error at world load.

## 1.21.1 target notes

- Yarn `1.21.1+build.1`. When an `@At` target signature is wrong, Mixin reports
  it at launch, not at compile time — always boot after changing a target.
- `@Inject` with `locals = LocalCapture.CAPTURE_FAILHARD` is used in
  `network/ServerPlayNetworkHandlerMixin`; it is brittle across versions, so
  check it first after any Minecraft bump.
- Dynamic registries (biome, enchantment, damage type, loot table) are **not**
  on `Registries`. Resolve them from a `DynamicRegistryManager`:
  `world.getRegistryManager().get(RegistryKeys.ENCHANTMENT)`.
- Items use `DataComponentTypes`, not NBT tags.

Full 1.18 → 1.21.1 substitution table:
`docs/design_principle/04-mixin-and-porting.md`.

## Debugging a mixin that will not apply

1. `./gradlew build --stacktrace` and read the Mixin error — it names the target
   and the reason (`target method not found`, `invalid descriptor`, ...).
2. Verify the target signature against decompiled 1.21.1 Yarn source. The
   `minecraft-dev` / `minecraft-modding` MCP servers configured in `.mcp.json`
   do this without leaving the session; otherwise use https://mcsrc.dev.
3. `-Dmixin.debug.export=true` as a JVM arg on `runServer` dumps the transformed
   classes to `run/.mixin.out/` so you can see what actually got applied.
4. Do **not** relax `defaultRequire` to 0 to get a green build. A silently
   skipped injector is the failure mode this project is trying to avoid.
