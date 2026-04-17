package me.fallenbreath.tcuhc;

import me.fallenbreath.tcuhc.gen.structure.EnderPyramidStructure;
import me.fallenbreath.tcuhc.gen.feature.UhcFeatures;
import me.fallenbreath.tcuhc.gen.structure.GreenhouseStructure;
import me.fallenbreath.tcuhc.gen.structure.HoneyWorkshopStructure;
import me.fallenbreath.tcuhc.gen.structure.PlainCottageStructure;
import me.fallenbreath.tcuhc.gen.structure.SinglePieceLandStructure;
import me.fallenbreath.tcuhc.gen.structure.VillainHouseStructure;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.MinecraftVersion;
import net.minecraft.util.Identifier;

public class TcUhcMod implements ModInitializer
{
	private static final String MOD_ID = "tcuhc";
	private static String version;
	private static final String MINECRAFT_VERSION = MinecraftVersion.CURRENT.getName();

	@Override
	public void onInitialize()
	{
		version = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow(RuntimeException::new).getMetadata().getVersion().getFriendlyString();
		// Force class loading so custom structure types/pieces register before datapacks are read.
		EnderPyramidStructure.CODEC.codec();
		GreenhouseStructure.SNOW_CODEC.codec();
		GreenhouseStructure.DESERT_CODEC.codec();
		HoneyWorkshopStructure.CODEC.codec();
		PlainCottageStructure.CODEC.codec();
		VillainHouseStructure.CODEC.codec();
		SinglePieceLandStructure.registerLootRetryHook();
		UhcFeatures.register();
	}

	public static String getModId()
	{
		return MOD_ID;
	}

	public static String getModVersion()
	{
		return version;
	}

	public static String getMinecraftVersion()
	{
		return MINECRAFT_VERSION;
	}

	public static Identifier id(String name)
	{
		return Identifier.of(MOD_ID, name);
	}
}
