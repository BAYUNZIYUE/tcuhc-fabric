package me.fallenbreath.tcuhc.mixins.loot;

import net.minecraft.item.Item;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 1.21.1 stores the item as RegistryEntry&lt;Item&gt; instead of a plain Item.
 */
@Mixin(ItemEntry.class)
public interface ItemEntryAccessor
{
	@Accessor
	RegistryEntry<Item> getItem();
}
