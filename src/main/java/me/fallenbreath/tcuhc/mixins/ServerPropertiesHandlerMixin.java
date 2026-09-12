package me.fallenbreath.tcuhc.mixins;

import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.options.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.21.1 completely reworked this area: GeneratorOptions no longer carries the terrain type.
 * The raw "level-type" string is now parsed into ServerPropertiesHandler$WorldGenProperties
 * (a record holding generatorSettings + levelType), which createDimensionsRegistryHolder()
 * later resolves to a WorldPreset registry key.
 *
 * So instead of rewriting the level-type entry of a Properties object, we rewrite the
 * levelType argument of that record's canonical constructor.
 */
@Mixin(targets = "net.minecraft.server.dedicated.ServerPropertiesHandler$WorldGenProperties")
public abstract class ServerPropertiesHandlerMixin
{
	@ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, index = 2)
	private static String changeLevelType(String levelType)
	{
		UhcGameManager.EnumLevelType type = (UhcGameManager.EnumLevelType) Options.instance.getOptionValue("levelType");
		switch (type)
		{
			case DEFAULT:
				// "default" / "largebiomes" are the two aliases in LEVEL_TYPE_TO_PRESET_KEY,
				// everything else is looked up as a WorldPreset registry id.
				return "default";
			case AMPLIFIED:
				return "minecraft:amplified";
			case LARGEBIOMES:
				return "largebiomes";
			default:
				return levelType;
		}
	}
}
