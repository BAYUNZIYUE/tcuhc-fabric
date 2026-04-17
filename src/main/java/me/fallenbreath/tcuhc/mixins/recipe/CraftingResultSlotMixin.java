package me.fallenbreath.tcuhc.mixins.recipe;

import me.fallenbreath.tcuhc.recipe.ArmorRepairCrafting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultSlotMixin
{
	@Shadow @Final private RecipeInputInventory input;
	@Shadow @Final private PlayerEntity player;
	@Shadow protected abstract void onCrafted(ItemStack stack);

	@Inject(method = "onTakeItem", at = @At("HEAD"), cancellable = true)
	private void tcuhc$consumeArmorRepairInputs(PlayerEntity player, ItemStack stack, CallbackInfo ci)
	{
		ItemStack expected = ArmorRepairCrafting.craft(this.input);
		if (expected.isEmpty() || !ItemStack.areItemsAndComponentsEqual(expected, stack))
		{
			return;
		}

		this.onCrafted(stack);
		ArmorRepairCrafting.consumeInputs(this.input);
		this.player.currentScreenHandler.onContentChanged(this.input);
		ci.cancel();
	}
}
