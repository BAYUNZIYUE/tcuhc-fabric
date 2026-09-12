package me.fallenbreath.tcuhc.util;

import com.google.common.collect.Lists;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.interfaces.LootPoolAccessor;
import me.fallenbreath.tcuhc.interfaces.LootTableAccessor;
import me.fallenbreath.tcuhc.mixins.loot.ItemEntryAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.ReloadableRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Ported from the pre-1.21 LootManagerMixin.
 *
 * <p>In 1.21.1 {@code net.minecraft.loot.LootManager} no longer exists — loot tables became a
 * datapack-backed dynamic registry loaded by {@code ReloadableRegistries}. So instead of hooking
 * {@code LootManager.apply()} we run once right after the server has started and rewrite the
 * already-loaded tables in place (via the LootTable/LootPool accessor mixins).</p>
 *
 * <p>This is what makes the {@code oreFrequency} and {@code chestItemFrequency} options meaningful
 * again; before this was ported those two options had no reader at all.</p>
 */
public class LootInjector
{
	public static void register()
	{
		ServerLifecycleEvents.SERVER_STARTED.register(LootInjector::onServerStarted);
		// Loot tables are rebuilt from the datapack on every /reload, which would silently undo the
		// injection below (the old LootManager#apply hook ran on every reload, this one did not).
		// Re-run after each successful data pack reload so the UHC drops always survive.
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) ->
		{
			if (success && server != null)
			{
				onServerStarted(server);
			}
		});
	}

	private static void onServerStarted(MinecraftServer server)
	{
		// 1.21.1 keeps loot tables in the RELOADABLE dynamic registry layer, which is NOT
		// reachable through MinecraftServer#getRegistryManager(). Go through the
		// ReloadableRegistries lookup instead.
		ReloadableRegistries.Lookup lookup;
		try
		{
			lookup = server.getReloadableRegistries();
		}
		catch (RuntimeException e)
		{
			UhcGameManager.LOG.warn("UHC: loot table registry unavailable, skipping loot injection", e);
			return;
		}
		if (lookup == null)
		{
			UhcGameManager.LOG.warn("UHC: reloadable registries unavailable, skipping loot injection");
			return;
		}

		DynamicRegistryManager registryManager = server.getRegistryManager();
		LootPool uhcAppleDrop = LootTableUtil.getUhcLootPool("apple", registryManager);
		LootPoolEntry uhcGlowStoneDrop = LootTableUtil.getUhcLootEntry("glowstone", registryManager);
		LootPoolEntry uhcLapisOreDrop = LootTableUtil.getUhcLootEntry("lapis_ore", registryManager);

		int leavesModified = 0;
		int oreModified = 0;

		for (Identifier id : lookup.getIds(RegistryKeys.LOOT_TABLE))
		{
			LootTable table = lookup.getLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, id));
			if (table == null)
			{
				continue;
			}

			// loot tables for blocks live at "minecraft:blocks/<block path>"
			Block block = Registries.BLOCK.get(Identifier.of(id.getNamespace(), id.getPath().replace("blocks/", "")));
			LootTableAccessor tableAccessor = (LootTableAccessor) table;

			if (block instanceof LeavesBlock)
			{
				List<LootPool> lootPools = Lists.newArrayList(tableAccessor.getPools());
				int replacedIndex = -1;
				for (int i = 0; i < lootPools.size(); i++)
				{
					List<LootPoolEntry> lootEntries = ((LootPoolAccessor) lootPools.get(i)).getEntries();
					if (lootEntries.size() == 1
							&& lootEntries.get(0) instanceof ItemEntry
							&& ((ItemEntryAccessor) lootEntries.get(0)).getItem().value() == Items.APPLE)
					{
						lootPools.set(i, uhcAppleDrop);
						replacedIndex = i;
					}
				}
				if (replacedIndex < 0)
				{
					lootPools.add(uhcAppleDrop);
				}
				tableAccessor.setPools(lootPools);
				leavesModified++;
				UhcGameManager.LOG.info("UHC loot: {} -> {} (pool index {}, {} pools now)",
						id, replacedIndex < 0 ? "APPENDED apple pool" : "REPLACED apple pool",
						replacedIndex, lootPools.size());
			}
			else if (block == Blocks.GLOWSTONE || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)
			{
				List<LootPool> pools = tableAccessor.getPools();
				if (pools.size() == 1)
				{
					LootPoolEntry lootEntry = block == Blocks.GLOWSTONE ? uhcGlowStoneDrop : uhcLapisOreDrop;
					((LootPoolAccessor) pools.get(0)).setEntries(Lists.newArrayList(lootEntry));
				}
				oreModified++;
			}
		}

		UhcGameManager.LOG.info("UHC loot injection done: {} leaves tables, {} ore tables", leavesModified, oreModified);
	}
}
