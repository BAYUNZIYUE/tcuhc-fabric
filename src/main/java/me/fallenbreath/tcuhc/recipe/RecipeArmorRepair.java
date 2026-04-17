package me.fallenbreath.tcuhc.recipe;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import net.minecraft.recipe.input.CraftingRecipeInput;

public class RecipeArmorRepair extends SpecialCraftingRecipe
{
	public RecipeArmorRepair(CraftingRecipeCategory category)
	{
		super(category);
	}

	@Override
	public boolean matches(CraftingRecipeInput input, World world)
	{
		return !this.craft(input, world.getRegistryManager()).isEmpty();
	}

	@Override
	public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup)
	{
		ItemStack armor = null;
		for (int i = 0; i < input.getStackCount(); ++i)
		{
			ItemStack itemstack = input.getStackInSlot(i);
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
		for (int i = 0; i < input.getStackCount(); ++i)
		{
			ItemStack itemstack = input.getStackInSlot(i);
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
		throw new UnsupportedOperationException("Custom armor repair now runs through screen handler hooks only");
	}
}
