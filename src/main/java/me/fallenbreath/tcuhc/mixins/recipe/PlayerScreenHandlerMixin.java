package me.fallenbreath.tcuhc.mixins.recipe;

import me.fallenbreath.tcuhc.recipe.ArmorRepairCrafting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerScreenHandler.class)
public abstract class PlayerScreenHandlerMixin extends ScreenHandler
{
	@Shadow @Final private RecipeInputInventory craftingInput;
	@Shadow @Final private CraftingResultInventory craftingResult;
	@Shadow @Final private PlayerEntity owner;

	protected PlayerScreenHandlerMixin(ScreenHandlerType<?> type, int syncId)
	{
		super(type, syncId);
	}

	@Inject(method = "onContentChanged", at = @At("TAIL"))
	private void tcuhc$showArmorRepairResult(Inventory inventory, CallbackInfo ci)
	{
		if (this.owner.getWorld().isClient() || inventory != this.craftingInput || !this.craftingResult.getStack(0).isEmpty())
		{
			return;
		}

		ItemStack result = ArmorRepairCrafting.craft(this.craftingInput);
		if (!result.isEmpty())
		{
			this.craftingResult.setStack(0, result);
			this.sendContentUpdates();
		}
	}
}
