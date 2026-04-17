package me.fallenbreath.tcuhc.mixins.recipe;

import me.fallenbreath.tcuhc.recipe.ArmorRepairCrafting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin extends ScreenHandler
{
	@Shadow @Final private RecipeInputInventory input;
	@Shadow @Final private CraftingResultInventory result;
	@Shadow @Final private PlayerEntity player;

	protected CraftingScreenHandlerMixin(ScreenHandlerType<?> type, int syncId)
	{
		super(type, syncId);
	}

	@Inject(method = "onContentChanged", at = @At("TAIL"))
	private void tcuhc$showArmorRepairResult(Inventory inventory, CallbackInfo ci)
	{
		if (this.player.getWorld().isClient() || inventory != this.input || !this.result.getStack(0).isEmpty())
		{
			return;
		}

		ItemStack result = ArmorRepairCrafting.craft(this.input);
		if (!result.isEmpty())
		{
			this.result.setStack(0, result);
			this.sendContentUpdates();
		}
	}
}
