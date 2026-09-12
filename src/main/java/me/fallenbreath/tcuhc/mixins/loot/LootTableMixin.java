package me.fallenbreath.tcuhc.mixins.loot;

import me.fallenbreath.tcuhc.interfaces.LootTableAccessor;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

// cannot use an Accessor or IllegalAccessError will appear, idk why
@Mixin(LootTable.class)
public abstract class LootTableMixin implements LootTableAccessor
{
	// 1.21.1 changed this field from LootPool[] to List<LootPool>
	@Mutable
	@Shadow @Final List<LootPool> pools;

	@Override
	public List<LootPool> getPools()
	{
		return this.pools;
	}

	@Override
	public void setPools(List<LootPool> lootPools)
	{
		this.pools = lootPools;
	}
}
