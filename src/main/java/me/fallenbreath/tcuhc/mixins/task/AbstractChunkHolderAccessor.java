package me.fallenbreath.tcuhc.mixins.task;

import net.minecraft.world.chunk.AbstractChunkHolder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractChunkHolder.class)
public interface AbstractChunkHolderAccessor
{
	@Invoker
	Chunk invokeGetUncheckedOrNull(ChunkStatus chunkStatus);

	@Invoker
	ChunkStatus invokeGetActualStatus();
}
