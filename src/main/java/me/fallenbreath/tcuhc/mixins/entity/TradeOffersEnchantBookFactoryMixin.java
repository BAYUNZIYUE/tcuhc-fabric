package me.fallenbreath.tcuhc.mixins.entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.village.TradeOffers$EnchantBookFactory")
public abstract class TradeOffersEnchantBookFactoryMixin
{
	// 1.21.1 changed TradeOffer's constructor to
	//   (TradedItem, Optional<TradedItem>, ItemStack, int maxUses, int, float)
	// The old (ItemStack, ItemStack, ItemStack, int, int, float) descriptor no longer exists,
	// so this @ModifyArg matched 0 targets and crashed the moment a villager trade was created.
	@ModifyArg(
			method = "create",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/village/TradeOffer;<init>(Lnet/minecraft/village/TradedItem;Ljava/util/Optional;Lnet/minecraft/item/ItemStack;IIF)V"
			),
			index = 3
	)
	private int modifyEnchantBookMaxUse(int maxUses)
	{
		return 1;
	}
}
