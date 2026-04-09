package me.fallenbreath.tcuhc.mixins.worldgen.feature;

import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.util.UhcWorldData;
import net.minecraft.structure.StructureGeneratorFactory;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.NetherFortressFeature;
import net.minecraft.world.gen.random.ChunkRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetherFortressFeature.class)
public abstract class NetherFortressFeatureMixin
{
	@Redirect(
			method = "canGenerate",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/gen/random/ChunkRandom;nextInt(I)I"
			)
	)
	private static int fortressGenerateIffXZAre0(ChunkRandom chunkRandom, int bound, /* parent method parameters -> */ StructureGeneratorFactory.Context<DefaultFeatureConfig> context)
	{
		if (UhcGameManager.instance.getWorldData().netherFortressType == UhcWorldData.StructureType.NETHER_FORTRESS)
		{
			// vanilla: chunkRandom.nextInt(5) >= 2 ? false: furtherTest()

			if (context.chunkPos().equals(new ChunkPos(0, 0)))
			{
				return 0;  // ok
			}
			else
			{
				return 3;  // nope
			}
		}

		return chunkRandom.nextInt(bound);
	}
}
