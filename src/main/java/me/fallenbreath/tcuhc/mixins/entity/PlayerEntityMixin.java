package me.fallenbreath.tcuhc.mixins.entity;

import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.UhcGamePlayer;
import me.fallenbreath.tcuhc.interfaces.IPlayerInventory;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity
{
	@Shadow @Final private PlayerInventory inventory;
	private float modifiedDamageAmount;

	protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world)
	{
		super(entityType, world);
	}

	/**
	 * TC Plugin: Kill entity hook
	 */
	@Inject(method = "onKilledOther", at = @At("TAIL"))
	private void onKill(ServerWorld serverWorld, LivingEntity livingEntity, CallbackInfoReturnable<Boolean> cir)
	{
		UhcGameManager.instance.getUhcPlayerManager().getGamePlayer((PlayerEntity)(Object)this).getStat().addStat(UhcGamePlayer.EnumStat.ENTITY_KILLED, 1);
	}

	/**
	 * TC Plugin: Kill entity hook
	 */
	@Redirect(
			method = "dropInventory",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/player/PlayerInventory;dropAll()V"
			)
	)
	private void dropInventoryWithoutClear(PlayerInventory playerInventory)
	{
		((IPlayerInventory)playerInventory).dropAllItemsWithoutClear();
	}

	@Redirect(
			method = "damage",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/entity/LivingEntity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
			)
	)
	private boolean modifyAndRecordDamage(LivingEntity livingEntity, DamageSource source, float amount)
	{
		float modified = UhcGameManager.instance.modifyPlayerDamage(amount);

		PlayerEntity self = (PlayerEntity)(Object)this;
		// reduce flying into wall damage under icarus
		if (UhcGameManager.getBattleType() == UhcGameManager.EnumBattleType.ICARUS &&
				source.isOf(DamageTypes.FLY_INTO_WALL) && modified > 0.0F) {
			modified *= 0.5F;
		}

		Entity sourceEntity = source.getSource();
		if (!(sourceEntity instanceof ServerPlayerEntity)) sourceEntity = source.getAttacker();
		if (sourceEntity instanceof ServerPlayerEntity && modified > 0.0F) {
			// the same logic in net.minecraft.entity.LivingEntity.damage
			boolean blocked = this.blockedByShield(source);

			// reduce player melee attack under bomber
			if (UhcGameManager.getGameMode() == UhcGameManager.EnumMode.BOMBER && !blocked) {
				if (!source.isIn(DamageTypeTags.IS_EXPLOSION) && !source.isIn(DamageTypeTags.IS_PROJECTILE))
					modified *= 0.5F;
			}

			// target player
			UhcGamePlayer.PlayerStatistics targetStat = UhcGameManager.instance.getUhcPlayerManager().getGamePlayer(self).getStat();
			targetStat.addStat(blocked ? UhcGamePlayer.EnumStat.DAMAGE_BLOCKED : UhcGamePlayer.EnumStat.DAMAGE_TAKEN, amount);

			// source player
			UhcGamePlayer.PlayerStatistics sourceStat = UhcGameManager.instance.getUhcPlayerManager().getGamePlayer((ServerPlayerEntity)sourceEntity).getStat();
			sourceStat.addStat(blocked ? UhcGamePlayer.EnumStat.DAMAGE_BEING_BLOCKED : UhcGamePlayer.EnumStat.DAMAGE_DEALT, amount);

			if (this.getScoreboardTeam() != null && this.getScoreboardTeam().isEqual(sourceEntity.getScoreboardTeam()) && !blocked) {
				sourceStat.addStat(UhcGamePlayer.EnumStat.FRIENDLY_FIRE, amount);
			}
		}

		this.modifiedDamageAmount = modified;
		return super.damage(source, modified);
	}

	@Inject(method = "damage", at = @At("RETURN"))
	private void afterDamageCalc(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir)
	{
		PlayerEntity self = (PlayerEntity)(Object)this;
		if (cir.getReturnValue() && self instanceof ServerPlayerEntity)
		{
			UhcGameManager.instance.onPlayerDamaged((ServerPlayerEntity)self, source, this.modifiedDamageAmount);
		}
	}
}
