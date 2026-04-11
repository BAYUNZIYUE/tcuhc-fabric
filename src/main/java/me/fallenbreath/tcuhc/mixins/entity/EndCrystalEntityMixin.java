package me.fallenbreath.tcuhc.mixins.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(EndCrystalEntity.class)
public abstract class EndCrystalEntityMixin extends Entity
{
	@Unique
	private PlayerEntity target;

	@Unique
	private static final double MAX_RANGE_SQR = 32 * 32;

	@Unique
	private int attackCooldown;

	@Shadow
	public abstract void setBeamTarget(Optional<BlockPos> blockPos);

	public EndCrystalEntityMixin(EntityType<?> type, World world)
	{
		super(type, world);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void onTick(CallbackInfo ci)
	{
		if (!this.getWorld().isClient())
		{
			this.setBeamTarget(this.target == null ? Optional.empty() : Optional.of(BlockPos.ofFloored(this.target.getX(), this.target.getY() - 0.5, this.target.getZ())));

			double distanceSqrToTarget = this.target == null ? MAX_RANGE_SQR : this.target.squaredDistanceTo(this);
			if (this.target != null && distanceSqrToTarget < MAX_RANGE_SQR && this.target.canSee(this))  // has valid target
			{
				this.attackCooldown--;
				if (this.attackCooldown <= 0)
				{
					float amount;
					if (distanceSqrToTarget < 8 * 8)
					{
						amount = 2.0F;
						this.attackCooldown = 25;
					}
					else if (distanceSqrToTarget < 16 * 16)
					{
						amount = 1.5F;
						this.attackCooldown = 28;
					}
					else
					{
						amount = 1.0F;
						this.attackCooldown = 30;
					}
					DamageSource damageSource = new DamageSource(this.getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DamageTypes.MOB_ATTACK), this);
					this.target.damage(damageSource, amount);
				}
			}
			else  // no target
			{
				// reset
				this.target = null;
				this.attackCooldown = 40;

				if (this.age % 5 == 0)  // search target every 5gt
				{
					double maxDistance = MAX_RANGE_SQR;
					for (PlayerEntity player : this.getWorld().getNonSpectatingEntities(PlayerEntity.class, this.getBoundingBox().expand(32, 10, 32)))
					{
						if (player.isCreative() || player.isSpectator())
						{
							continue;
						}

						double newdis = player.squaredDistanceTo(this);
						if (newdis < maxDistance && player.canSee(this))
						{
							maxDistance = newdis;
							this.target = player;
						}
					}
					if (this.target != null)
					{
						this.getWorld().playSound(null, this.target.getX(), this.target.getY(), this.target.getZ(), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 1.0F, 1.0F);
					}
				}
			}
		}
	}
}
