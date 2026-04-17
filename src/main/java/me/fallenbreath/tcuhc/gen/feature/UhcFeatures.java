package me.fallenbreath.tcuhc.gen.feature;

import me.fallenbreath.tcuhc.TcUhcMod;
import me.fallenbreath.tcuhc.util.UhcRegistry;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;

public class UhcFeatures
{
	public static final Feature<DefaultFeatureConfig> BONUS_CHEST = UhcRegistry.registerFeature("bonus_chest", new BonusChestFeature(DefaultFeatureConfig.CODEC));
	public static final Feature<DefaultFeatureConfig> MERCHANTS = UhcRegistry.registerFeature("merchants", new MerchantsFeature(DefaultFeatureConfig.CODEC));
	public static final RegistryKey<PlacedFeature> BONUS_CHEST_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, TcUhcMod.id("bonus_chest"));
	public static final RegistryKey<PlacedFeature> MERCHANTS_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, TcUhcMod.id("merchants"));

	private UhcFeatures()
	{
	}

	public static void register()
	{
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Feature.SURFACE_STRUCTURES,
				BONUS_CHEST_PLACED_KEY
		);
		BiomeModifications.addFeature(
				BiomeSelectors.foundInOverworld(),
				GenerationStep.Feature.SURFACE_STRUCTURES,
				MERCHANTS_PLACED_KEY
		);
	}
}
