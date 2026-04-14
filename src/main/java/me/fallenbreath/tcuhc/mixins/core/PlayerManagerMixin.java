package me.fallenbreath.tcuhc.mixins.core;

import me.fallenbreath.tcuhc.UhcGameManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.entity.Entity;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin
{
	@Inject(
			method = "onPlayerConnect",
			at = @At("TAIL")
	)
	private void playerJoinHook(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci)
	{
		UhcGameManager.instance.onPlayerJoin(player);
	}

	@Inject(
			method = "respawnPlayer",
			at = @At("RETURN")
	)
	private void playerRespawnHook(ServerPlayerEntity player, boolean alive, Entity.RemovalReason removalReason, CallbackInfoReturnable<ServerPlayerEntity> cir)
	{
		UhcGameManager.instance.onPlayerRespawn(cir.getReturnValue());
	}
}
