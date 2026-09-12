package me.fallenbreath.tcuhc.interfaces;

import net.minecraft.loot.LootPool;

import java.util.List;

public interface LootTableAccessor
{
	List<LootPool> getPools();

	void setPools(List<LootPool> lootPools);
}
