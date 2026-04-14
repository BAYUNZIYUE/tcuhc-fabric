# GitHub Build Checklist

Use `.github/workflows/gradle.yml` as the authoritative compile path.

## Compile focus

- Enchantment lookup changes in:
  - `src/main/java/me/fallenbreath/tcuhc/util/PlayerItems.java`
  - `src/main/java/me/fallenbreath/tcuhc/task/TaskTitleCountDown.java`
- Written book refresh/reopen changes in:
  - `src/main/java/me/fallenbreath/tcuhc/UhcPlayerManager.java`
- Custom structure migration and registration in:
  - `src/main/java/me/fallenbreath/tcuhc/gen/structure/SinglePieceLandStructure.java`
  - `src/main/java/me/fallenbreath/tcuhc/gen/structure/EnderPyramidStructure.java`
  - `src/main/java/me/fallenbreath/tcuhc/gen/structure/VillainHouseStructure.java`
  - `src/main/java/me/fallenbreath/tcuhc/gen/structure/PlainCottageStructure.java`
  - `src/main/java/me/fallenbreath/tcuhc/gen/structure/HoneyWorkshopStructure.java`
  - `src/main/java/me/fallenbreath/tcuhc/gen/structure/GreenhouseStructure.java`
  - `src/main/java/me/fallenbreath/tcuhc/TcUhcMod.java`
  - `src/main/java/me/fallenbreath/tcuhc/util/UhcRegistry.java`

## Resource focus

- Structure JSON type switches:
  - `src/main/resources/data/tcuhc/worldgen/structure/ender_pyramid.json`
  - `src/main/resources/data/tcuhc/worldgen/structure/villain_house.json`
  - `src/main/resources/data/tcuhc/worldgen/structure/plain_cottage.json`
  - `src/main/resources/data/tcuhc/worldgen/structure/honey_workshop.json`
  - `src/main/resources/data/tcuhc/worldgen/structure/greenhouse_desert.json`
  - `src/main/resources/data/tcuhc/worldgen/structure/greenhouse_snow.json`
- Mixin cleanup:
  - `src/main/resources/tcuhc.mixins.json`

## If GitHub build passes

- Deploy the built jar and verify in game:
  - config written book click behavior
  - time-setting input flow
  - team color in TAB and scoreboard membership
  - `/team join 红队` behavior
  - structure generation and marker effects for all migrated structures
  - custom death-drop enchantments

## If GitHub build fails

- Prioritize errors from migrated structure classes first.
- Then fix enchantment registry access errors.
- Then fix book reopen packet or API mismatches.
