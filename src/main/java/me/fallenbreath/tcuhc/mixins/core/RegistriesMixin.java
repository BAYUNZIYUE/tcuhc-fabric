package me.fallenbreath.tcuhc.mixins.core;

import me.fallenbreath.tcuhc.gen.feature.UhcFeatures;
import me.fallenbreath.tcuhc.gen.structure.EnderPyramidStructure;
import me.fallenbreath.tcuhc.gen.structure.GreenhouseStructure;
import me.fallenbreath.tcuhc.gen.structure.HoneyWorkshopStructure;
import me.fallenbreath.tcuhc.gen.structure.PlainCottageStructure;
import me.fallenbreath.tcuhc.gen.structure.VillainHouseStructure;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Registries.class)
public abstract class RegistriesMixin
{
	// Force class loading so custom structure types/pieces and features register
	// before the worldgen registries are frozen (which happens during Bootstrap).
	static
	{
		EnderPyramidStructure.CODEC.codec();
		GreenhouseStructure.SNOW_CODEC.codec();
		GreenhouseStructure.DESERT_CODEC.codec();
		HoneyWorkshopStructure.CODEC.codec();
		PlainCottageStructure.CODEC.codec();
		VillainHouseStructure.CODEC.codec();
		UhcFeatures.BONUS_CHEST.getCodec();
	}
}
