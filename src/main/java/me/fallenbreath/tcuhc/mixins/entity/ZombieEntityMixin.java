package me.fallenbreath.tcuhc.mixins.entity;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZombieEntity.class)
public abstract class ZombieEntityMixin
{
	/**
	 * DrownedEntity#initialize handles the natural spawn path, but it is not enough on its own:
	 * in 1.21 SummonCommand only calls MobEntity#initialize when an internal flag is set, and the
	 * /summon command passes false. A drowned created by /summon therefore keeps the default 0.085
	 * drop chance and never drops its trident.
	 * <p>
	 * Deciding right before the equipment drop covers every spawn path there is (natural spawn,
	 * spawn egg, structure, /summon, ...), so the "a drowned holding a trident always drops it"
	 * rule actually holds.
	 */
	@Inject(method = "dropEquipment", at = @At("HEAD"))
	private void drownedAlwaysDropsTridentIfHolding(ServerWorld world, DamageSource source, boolean causedByPlayer, CallbackInfo ci)
	{
		if (!((Object)this instanceof DrownedEntity drowned))
		{
			return;
		}
		if (drowned.getEquippedStack(EquipmentSlot.MAINHAND).getItem() == Items.TRIDENT)
		{
			drowned.setEquipmentDropChance(EquipmentSlot.MAINHAND, 2.0F);
		}
	}
}
