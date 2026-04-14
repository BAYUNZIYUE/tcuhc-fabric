# Continuation Draft

- User instruction: "编译使用github编译 你自己记录一个草稿 每次压缩会话后自己看看"
- Build policy: treat GitHub build as the authoritative compilation/verification path.
- Local build may still be used for quick signal, but not as the final acceptance gate.

## Current focus

- Highest priority: restore custom structures and their marker-based gameplay behavior.
- In parallel: fix book interaction, team scoreboard behavior, and legacy enchantment migration.

## Current code state

- `src/main/java/me/fallenbreath/tcuhc/UhcPlayerManager.java`
  - `setupIngameTeams()` now adds players into scoreboard teams.
  - `refreshConfigBook()` now updates UHC books in place instead of re-giving the whole config inventory.
  - If the player is holding a UHC book in main hand, the server now sends `OpenWrittenBookS2CPacket` after refresh.
- `src/main/java/me/fallenbreath/tcuhc/task/TaskTitleCountDown.java`
  - KING helmet enchantments were migrated from fake NBT enchant data to real enchantment application.
- `src/main/java/me/fallenbreath/tcuhc/gen/structure/SinglePieceLandStructure.java`
  - Added terrain Y adjustment support, auto bottom-fill helper, and `YOffsetPiece`.
- `src/main/java/me/fallenbreath/tcuhc/gen/structure/VillainHouseStructure.java`
  - Added as the second migrated custom structure on the new 1.21.1 structure/piece path.
- `src/main/java/me/fallenbreath/tcuhc/gen/structure/PlainCottageStructure.java`
  - Added as the third migrated custom structure on the new 1.21.1 structure/piece path.
- `src/main/java/me/fallenbreath/tcuhc/gen/structure/HoneyWorkshopStructure.java`
  - Added as the fourth migrated custom structure on the new 1.21.1 structure/piece path.
- `src/main/java/me/fallenbreath/tcuhc/gen/structure/GreenhouseStructure.java`
  - Added greenhouse desert/snow on the new 1.21.1 structure/piece path without relying on the old weighted-list accessor mixin.
- `src/main/resources/data/tcuhc/worldgen/structure/villain_house.json`
  - Switched from `minecraft:jigsaw` to `tcuhc:villain_house`.
- `src/main/resources/data/tcuhc/worldgen/structure/plain_cottage.json`
  - Switched from `minecraft:jigsaw` to `tcuhc:plain_cottage`.
- `src/main/resources/data/tcuhc/worldgen/structure/honey_workshop.json`
  - Switched from `minecraft:jigsaw` to `tcuhc:honey_workshop`.
- `src/main/resources/data/tcuhc/worldgen/structure/greenhouse_desert.json`
  - Switched from `minecraft:jigsaw` to `tcuhc:greenhouse_desert`.
- `src/main/resources/data/tcuhc/worldgen/structure/greenhouse_snow.json`
  - Switched from `minecraft:jigsaw` to `tcuhc:greenhouse_snow`.
- `src/main/resources/tcuhc.mixins.json`
  - Removed the temporary structure marker mixins now that tcuhc structures are no longer using the jigsaw fallback path.
- `GITHUB_BUILD_CHECKLIST.md`
  - Added a focused checklist for the GitHub Actions build and the first post-build runtime verification pass.
- `src/main/java/me/fallenbreath/tcuhc/TcUhcMod.java`
  - Forces class loading for custom structure registration.
- Enchantment lookup
  - Adjusted away from the removed `Registries.ENCHANTMENT` field toward the 1.21.1 registry-key path.

## Immediate next steps

- Fix 1.21.1 enchantment registry lookup so the new enchantment helpers compile cleanly.
- Structure migration set is now broadly in place; next checkpoint is GitHub build verification and then runtime validation on server.
- After GitHub build, verify structure generation/marker behavior, team coloring, and written-book refresh behavior in game.
