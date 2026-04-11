package me.fallenbreath.tcuhc.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.util.JsonHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class LootTableUtil
{
	private static final Gson GSON = new GsonBuilder().create();

	public static <T> T getUhcLootData(String type, String dir, Class<T> class_)
	{
		String filePath = String.format("data/tcuhc/%s/%s.json", type, dir);
		InputStream inputStream = LootTableUtil.class.getClassLoader().getResourceAsStream(filePath);
		if (inputStream != null)
		{
			Reader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
			return JsonHelper.deserialize(GSON, reader, class_);
		}
		else
		{
			throw new RuntimeException("Unable to load loot data from " + filePath);
		}
	}

	public static LootPool getUhcLootPool(String name)
	{
		return getUhcLootData("lootpools", name, LootPool.class);
	}

	public static LootPoolEntry getUhcLootEntry(String name)
	{
		return getUhcLootData("lootentries", name, LootPoolEntry.class);
	}
}
