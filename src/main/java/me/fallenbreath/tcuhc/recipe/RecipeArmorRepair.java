package me.fallenbreath.tcuhc.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class RecipeArmorRepair extends SpecialCraftingRecipe
{
	public RecipeArmorRepair(Identifier id, CraftingRecipeCategory category)
	{
		super(id, category);
	}

	@Override
	public boolean matches(RecipeInputInventory inv, World world)
	{
		return !this.craft(inv, world.getRegistryManager()).isEmpty();
	}

	@Override
	public ItemStack craft(RecipeInputInventory inv, DynamicRegistryManager registryManager)
	{
		ItemStack armor = null;
		for (int i = 0; i < inv.size(); ++i)
		{
			ItemStack itemstack = inv.getStack(i);
			if (itemstack.getItem() instanceof ArmorItem)
			{
				if (armor == null)
				{
					armor = itemstack;
				}
				else
				{
					return ItemStack.EMPTY;
				}
			}
		}
		if (armor == null)
		{
			return ItemStack.EMPTY;
		}
		ArmorItem armorItem = (ArmorItem)armor.getItem();
		int repairCnt = 0;
		for (int i = 0; i < inv.size(); ++i)
		{
			ItemStack itemstack = inv.getStack(i);
			if (!itemstack.isEmpty() && !(itemstack.getItem() instanceof ArmorItem))
			{
				if (armorItem.canRepair(armor, itemstack))
				{
					repairCnt++;
				}
				else
				{
					return ItemStack.EMPTY;
				}
			}
		}
		if (repairCnt == 0)
		{
			return ItemStack.EMPTY;
		}
		ItemStack result = armor.copy();
		result.setDamage(armor.getDamage() - repairCnt * armor.getMaxDamage() / 4);
		return result;
	}

	@Override
	public boolean fits(int width, int height)
	{
		return true;
	}

	@Override
	public RecipeSerializer<?> getSerializer()
	{
		return UhcRecipeSerializer.REPAIR_ARMOR;
	}
}
