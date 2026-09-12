package me.fallenbreath.tcuhc.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryOps;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Loads the small UHC loot snippets under data/tcuhc/lootpools and data/tcuhc/lootentries.
 *
 * <p>These used to be parsed with plain Gson reflection, which no longer works in 1.21.1:
 * {@code LootPoolEntry} is an abstract class and Gson refuses to instantiate it
 * ("Abstract classes can't be instantiated! ... Class name: net.minecraft.class_79").
 * They are now parsed with the vanilla codecs, which also resolves the item/enchantment
 * registry references those snippets contain.</p>
 */
public class LootTableUtil
{
	private static JsonElement readUhcLootJson(String type, String dir)
	{
		String filePath = String.format("data/tcuhc/%s/%s.json", type, dir);
		InputStream inputStream = LootTableUtil.class.getClassLoader().getResourceAsStream(filePath);
		if (inputStream == null)
		{
			throw new RuntimeException("Unable to load loot data from " + filePath);
		}
		try (Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)))
		{
			return JsonParser.parseReader(reader);
		}
		catch (Exception e)
		{
			throw new RuntimeException("Unable to read loot data from " + filePath, e);
		}
	}

	private static RegistryOps<JsonElement> ops(DynamicRegistryManager registryManager)
	{
		return RegistryOps.of(JsonOps.INSTANCE, registryManager);
	}

	public static LootPool getUhcLootPool(String name, DynamicRegistryManager registryManager)
	{
		return LootPool.CODEC.parse(ops(registryManager), readUhcLootJson("lootpools", name)).getOrThrow();
	}

	/**
	 * Both shipped lootentries are "minecraft:item" entries, and LootPoolEntry has no dispatch
	 * codec of its own, so parse them directly as ItemEntry (the redundant "type" field is ignored).
	 */
	public static LootPoolEntry getUhcLootEntry(String name, DynamicRegistryManager registryManager)
	{
		return ItemEntry.CODEC.codec().parse(ops(registryManager), readUhcLootJson("lootentries", name)).getOrThrow();
	}
}
