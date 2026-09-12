package me.fallenbreath.tcuhc.mixins.loot;

import me.fallenbreath.tcuhc.interfaces.LootPoolAccessor;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.LootPoolEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

// cannot use an Accessor or IllegalAccessError will appear, idk why
@Mixin(LootPool.class)
public abstract class LootPoolMixin implements LootPoolAccessor
{
	// 1.21.1 changed this field from LootPoolEntry[] to List<LootPoolEntry>
	@Mutable
	@Shadow @Final List<LootPoolEntry> entries;

	@Override
	public List<LootPoolEntry> getEntries()
	{
		return this.entries;
	}

	@Override
	public void setEntries(List<LootPoolEntry> entries)
	{
		this.entries = entries;
	}
}
