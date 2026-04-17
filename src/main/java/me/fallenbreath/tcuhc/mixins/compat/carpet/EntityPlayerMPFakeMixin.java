package me.fallenbreath.tcuhc.mixins.compat.carpet;

import com.mojang.authlib.GameProfile;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.UhcGamePlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "carpet.patches.EntityPlayerMPFake")
public abstract class EntityPlayerMPFakeMixin extends PlayerEntity
{
	protected EntityPlayerMPFakeMixin(World world, BlockPos pos, float yaw, GameProfile profile)
	{
		super(world, pos, yaw, profile);
	}

	@Inject(method = "method_6078", at = @At("HEAD"))
	private void onFakePlayerDeath(DamageSource cause, CallbackInfo ci)
	{
		if (UhcGameManager.instance == null)
		{
			return;
		}
		Entity sourceEntity = cause.getSource();
		if (!(sourceEntity instanceof PlayerEntity)) sourceEntity = cause.getAttacker();
		if (!(sourceEntity instanceof PlayerEntity)) sourceEntity = this.getAttacker();
		if (sourceEntity instanceof PlayerEntity)
		{
			UhcGamePlayer killer = UhcGameManager.instance.getUhcPlayerManager().getGamePlayer((PlayerEntity)sourceEntity);
			if (killer != null)
			{
				killer.getStat().addStat(UhcGamePlayer.EnumStat.PLAYER_KILLED, 1);
			}
		}
		// Carpet fake players override onDeath, so they need their own hook to enter the same UHC death chain.
		UhcGameManager.instance.onPlayerDeath((ServerPlayerEntity)(Object)this, cause);
	}
}
