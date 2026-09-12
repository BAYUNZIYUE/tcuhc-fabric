package me.fallenbreath.tcuhc.interfaces;

import net.minecraft.loot.entry.LootPoolEntry;

import java.util.List;

public interface LootPoolAccessor
{
	List<LootPoolEntry> getEntries();

	void setEntries(List<LootPoolEntry> entries);
}
