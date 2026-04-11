package me.fallenbreath.tcuhc.recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtElement;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import net.minecraft.recipe.input.CraftingRecipeInput;

public class RecipeGoldenApple extends SpecialCraftingRecipe
{
	public RecipeGoldenApple(CraftingRecipeCategory category)
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
		boolean hasApple = false;
		int goldCnt = 0;
		for (int i = 0; i < input.getStackCount(); ++i)
		{
			ItemStack itemstack = input.getStackInSlot(i);
			if (itemstack.getItem() == Items.APPLE)
			{
				if (!hasApple)
				{
					hasApple = true;
				}
				else
				{
					return ItemStack.EMPTY;
				}
			}
			else if (itemstack.getItem() == Items.GOLD_INGOT)
			{
				goldCnt++;
			}
		}
		if (hasApple && goldCnt > 0 && goldCnt % 2 == 0)
		{
			int level = goldCnt / 2;
			ItemStack res = new ItemStack(Items.GOLDEN_APPLE);
			return res;
		}
		return ItemStack.EMPTY;
	}

	@Override
	public boolean fits(int width, int height)
	{
		return width >= 3 && height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer()
	{
		return UhcRecipeSerializer.GOLDEN_APPLE;
	}
}