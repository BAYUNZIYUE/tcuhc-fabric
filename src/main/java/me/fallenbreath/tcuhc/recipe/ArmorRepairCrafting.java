package me.fallenbreath.tcuhc.recipe;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;

public class ArmorRepairCrafting
{
	private ArmorRepairCrafting()
	{
	}

	public static ItemStack craft(RecipeInputInventory input)
	{
		ItemStack armor = ItemStack.EMPTY;
		for (int i = 0; i < input.size(); i++)
		{
			ItemStack stack = input.getStack(i);
			if (stack.getItem() instanceof ArmorItem)
			{
				if (!armor.isEmpty())
				{
					return ItemStack.EMPTY;
				}
				armor = stack;
			}
		}

		if (armor.isEmpty())
		{
			return ItemStack.EMPTY;
		}

		ArmorItem armorItem = (ArmorItem)armor.getItem();
		int repairCount = 0;
		for (int i = 0; i < input.size(); i++)
		{
			ItemStack stack = input.getStack(i);
			if (!stack.isEmpty() && !(stack.getItem() instanceof ArmorItem))
			{
				if (!armorItem.canRepair(armor, stack))
				{
					return ItemStack.EMPTY;
				}
				repairCount++;
			}
		}

		if (repairCount == 0)
		{
			return ItemStack.EMPTY;
		}

		ItemStack result = armor.copy();
		int repairedDamage = armor.getDamage() - repairCount * armor.getMaxDamage() / 4;
		result.setDamage(Math.max(repairedDamage, 0));
		return result;
	}

	public static void consumeInputs(RecipeInputInventory input)
	{
		for (int i = 0; i < input.size(); i++)
		{
			ItemStack stack = input.getStack(i);
			if (!stack.isEmpty())
			{
				input.removeStack(i, 1);
			}
		}
	}
}
