package me.fallenbreath.tcuhc.mixins.worldgen;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.structure.ShipwreckStructure;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ShipwreckStructure.class)
public abstract class ShipwreckStructureMixin
{
	@Inject(method = "getStructurePosition", at = @At("HEAD"), cancellable = true)
	private void preventHighRiverShipwrecks(Structure.Context context, CallbackInfoReturnable<Optional<Structure.StructurePosition>> cir)
	{
		int x = context.chunkPos().getCenterX();
		int z = context.chunkPos().getCenterZ();
		int y = context.chunkGenerator().getHeightInGround(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
		RegistryEntry<Biome> biome = context.biomeSource().getBiome(
				BiomeCoords.fromBlock(x),
				BiomeCoords.fromBlock(y),
				BiomeCoords.fromBlock(z),
				context.noiseConfig().getMultiNoiseSampler()
		);
		if ((biome.matchesKey(BiomeKeys.RIVER) || biome.matchesKey(BiomeKeys.FROZEN_RIVER)) && y > context.chunkGenerator().getSeaLevel() - 7)
		{
			cir.setReturnValue(Optional.empty());
		}
	}
}
