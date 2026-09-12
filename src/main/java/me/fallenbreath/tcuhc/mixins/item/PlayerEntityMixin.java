package me.fallenbreath.tcuhc.mixins.item;

import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.UhcGamePlayer;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin
{
	@Inject(method = "eatFood", at = @At("HEAD"))
	private void checkAndAddUhcGAppleStat(World world, ItemStack stack, FoodComponent foodComponent, CallbackInfoReturnable<ItemStack> cir)
	{
		PlayerEntity self = (PlayerEntity)(Object)this;
		if (!(self instanceof ServerPlayerEntity))
		{
			return;
		}
		// Both tiers count towards 食用金苹果: the crafted variants are all GOLDEN_APPLE, and a
		// notch apple is still a golden apple as far as the score board is concerned.
		if (stack.getItem() != Items.GOLDEN_APPLE && stack.getItem() != Items.ENCHANTED_GOLDEN_APPLE)
		{
			return;
		}
		// Eating must never break just because the eater is not a tracked UHC player - that happens
		// for a player who joined mid-match before their game player exists, and for fake players.
		if (UhcGameManager.instance == null)
		{
			return;
		}
		UhcGamePlayer gamePlayer = UhcGameManager.instance.getUhcPlayerManager().getGamePlayer(self);
		if (gamePlayer == null)
		{
			return;
		}
		gamePlayer.getStat().addStat(UhcGamePlayer.EnumStat.GOLDEN_APPLE_EATEN, 1.0F);
	}
}