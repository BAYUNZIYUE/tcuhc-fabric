package me.fallenbreath.tcuhc.recipe;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import net.minecraft.recipe.input.CraftingRecipeInput;

import java.util.List;

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
			if (level != 4)
			{
				NbtCompound nbt = new NbtCompound();
				nbt.putInt("level", level);
				res.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
				applyGoldenAppleEffects(res, level);
			}
			return res;
		}
		return ItemStack.EMPTY;
	}

	private static void applyGoldenAppleEffects(ItemStack stack, int level)
	{
		FoodComponent original = stack.get(DataComponentTypes.FOOD);
		if (original == null)
		{
			return;
		}

		FoodComponent component = new FoodComponent(
				original.nutrition(),
				original.saturation(),
				original.canAlwaysEat(),
				original.eatSeconds(),
				original.usingConvertsTo(),
				List.of(
						new FoodComponent.StatusEffectEntry(new StatusEffectInstance(StatusEffects.REGENERATION, (level + 1) * 20, 0), 1.0F),
						new FoodComponent.StatusEffectEntry(new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, level - 1), 1.0F)
				)
		);
		stack.set(DataComponentTypes.FOOD, component);
	}

	@Override
	public boolean fits(int width, int height)
	{
		return width >= 3 && height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer()
	{
		throw new UnsupportedOperationException("Golden apple variants now use vanilla recipe json outputs only");
	}
}
