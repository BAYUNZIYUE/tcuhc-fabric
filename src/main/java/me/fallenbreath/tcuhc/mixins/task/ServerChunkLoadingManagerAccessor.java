package me.fallenbreath.tcuhc.mixins.task;

import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkLoadingManager.class)
public interface ServerChunkLoadingManagerAccessor
{
	@Invoker
	ChunkHolder invokeGetChunkHolder(long pos);
}
