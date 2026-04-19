package me.fallenbreath.tcuhc.mixins.core;

import com.mojang.datafixers.util.Pair;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.task.MsptRecorder;
import net.minecraft.class_1937;
import net.minecraft.class_1959;
import net.minecraft.class_2338;
import net.minecraft.class_2794;
import net.minecraft.class_2960;
import net.minecraft.class_3215;
import net.minecraft.class_3218;
import net.minecraft.class_3754;
import net.minecraft.class_4766;
import net.minecraft.class_5321;
import net.minecraft.class_5363;
import net.minecraft.class_5284;
import net.minecraft.class_5455;
import net.minecraft.class_6544;
import net.minecraft.class_6910;
import net.minecraft.class_6880;
import net.minecraft.class_6953;
import net.minecraft.class_7924;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin
{
	private UhcGameManager uhcGameManager;
	private boolean serverInited = false;
	private static boolean marineGeneratorSwapLogged = false;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void constructUhcGameManager(CallbackInfo ci)
	{
		this.uhcGameManager = new UhcGameManager((MinecraftServer)(Object)this);
	}

	@ModifyArgs(
			method = "createWorlds",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/world/ServerWorld;<init>(Lnet/minecraft/server/MinecraftServer;Ljava/util/concurrent/Executor;Lnet/minecraft/world/level/storage/LevelStorage$Session;Lnet/minecraft/world/level/ServerWorldProperties;Lnet/minecraft/class_5321;Lnet/minecraft/class_5363;Lnet/minecraft/server/WorldGenerationProgressListener;ZJLjava/util/List;ZLnet/minecraft/util/math/random/RandomSequencesState;)V"
			)
	)
	private void forceMarineOverworldBiomes(Args args)
	{
		if (UhcGameManager.getBattleType() != UhcGameManager.EnumBattleType.MARINE)
		{
			return;
		}

		class_5321<class_1937> worldKey = args.get(4);
		if (worldKey != class_1937.field_25179)
		{
			return;
		}

		class_5363 dimensionOptions = args.get(5);
		class_2794 chunkGenerator = dimensionOptions.comp_1013();
		if (!(chunkGenerator instanceof class_3754))
		{
			return;
		}

		class_3754 noiseChunkGenerator = (class_3754)chunkGenerator;
		args.set(5, new class_5363(
				dimensionOptions.comp_1012(),
				createMarineChunkGenerator((MinecraftServer)(Object)this, noiseChunkGenerator)
		));
		if (!marineGeneratorSwapLogged)
		{
			marineGeneratorSwapLogged = true;
			UhcGameManager.LOG.info("MARINE overworld generator swapped to {} with ocean-family multi-noise biomes", class_3754.class.getSimpleName());
		}
	}

	private static class_3754 createMarineChunkGenerator(MinecraftServer server, class_3754 noiseChunkGenerator)
	{
		class_6880<class_5284> originalSettingsEntry = noiseChunkGenerator.method_41541();
		class_5284 originalSettings = originalSettingsEntry.comp_349();
		class_6953 originalRouter = originalSettings.comp_477();

		ConstantDensityFunction deepOcean = new ConstantDensityFunction(-1.0);
		SubmergedDensityFunction oceanFloor = new SubmergedDensityFunction();

		class_6953 marineRouter = new class_6953(
				originalRouter.comp_414(),
				originalRouter.comp_415(),
				originalRouter.comp_416(),
				originalRouter.comp_417(),
				originalRouter.comp_420(),
				originalRouter.comp_539(),
				deepOcean,
				originalRouter.comp_423(),
				originalRouter.comp_424(),
				originalRouter.comp_485(),
				oceanFloor,
				oceanFloor,
				originalRouter.comp_428(),
				originalRouter.comp_429(),
				originalRouter.comp_430()
		);

		class_5284 marineSettings = new class_5284(
				originalSettings.comp_474(),
				originalSettings.comp_475(),
				originalSettings.comp_476(),
				marineRouter,
				originalSettings.comp_478(),
				originalSettings.comp_538(),
				originalSettings.comp_479(),
				originalSettings.comp_480(),
				originalSettings.comp_481(),
				originalSettings.comp_482(),
				originalSettings.comp_483()
		);

		class_6880<class_5284> marineSettingsEntry = class_6880.method_40223(marineSettings);

		return new class_3754(createMarineBiomeSource(server), marineSettingsEntry);
	}

	private static class ConstantDensityFunction implements class_6910.class_6913
	{
		private final double value;

		ConstantDensityFunction(double value)
		{
			this.value = value;
		}

		@Override
		public double method_40464(class_6910.class_6912 pos)
		{
			return value;
		}

		@Override
		public void method_40470(double[] densities, class_6910.class_6911 filler)
		{
			java.util.Arrays.fill(densities, value);
		}

		@Override
		public class_6910 method_40469(class_6910.class_6915 visitor)
		{
			return this;
		}

		@Override
		public double comp_377()
		{
			return value;
		}

		@Override
		public double comp_378()
		{
			return value;
		}

		@Override
		public net.minecraft.class_7243<? extends class_6910> method_41062()
		{
			throw new UnsupportedOperationException();
		}
	}

	private static class SubmergedDensityFunction implements class_6910.class_6913
	{
		private static final double CENTER_Y = 48.0;
		private static final double AMPLITUDE = 23.0;

		@Override
		public double method_40464(class_6910.class_6912 pos)
		{
			int x = pos.comp_371();
			int y = pos.comp_372();
			int z = pos.comp_373();

			double floorY = CENTER_Y + terrainHeight(x, z);
			double diff = floorY - y;
			if (diff > 4.0) return 1.0;
			if (diff < -4.0) return -1.0;
			return diff / 4.0;
		}

		private static double terrainHeight(int x, int z)
		{
			double h = 0;
			h += valueNoise(x / 80.0, z / 80.0, 0x5DEECE66DL) * 12.0;
			h += valueNoise(x / 40.0, z / 40.0, 0xBEEFDEADL) * 4.0;
			h += valueNoise(x / 20.0, z / 20.0, 0xCAFEBABEL) * 2.0;
			return h * AMPLITUDE / 18.0;
		}

		private static double valueNoise(double x, double z, long seed)
		{
			int ix = (int)Math.floor(x);
			int iz = (int)Math.floor(z);
			double fx = x - ix;
			double fz = z - iz;
			fx = fx * fx * (3.0 - 2.0 * fx);
			fz = fz * fz * (3.0 - 2.0 * fz);
			double v00 = hash2d(ix, iz, seed);
			double v10 = hash2d(ix + 1, iz, seed);
			double v01 = hash2d(ix, iz + 1, seed);
			double v11 = hash2d(ix + 1, iz + 1, seed);
			double i0 = v00 + (v10 - v00) * fx;
			double i1 = v01 + (v11 - v01) * fx;
			return i0 + (i1 - i0) * fz;
		}

		private static double hash2d(int x, int z, long seed)
		{
			long h = (long)x * 341873128712L ^ (long)z * 132897987541L ^ seed;
			h = (h ^ (h >>> 17)) * 0x68E31DA4L;
			h = h ^ (h >>> 13);
			return ((double)(h & 0xFFFFL) / 65535.0) * 2.0 - 1.0;
		}

		@Override
		public void method_40470(double[] densities, class_6910.class_6911 filler)
		{
			for (int i = 0; i < densities.length; i++)
			{
				densities[i] = method_40464(filler.method_40477(i));
			}
		}

		@Override
		public class_6910 method_40469(class_6910.class_6915 visitor)
		{
			return this;
		}

		@Override
		public double comp_377()
		{
			return -0.5;
		}

		@Override
		public double comp_378()
		{
			return 0.5;
		}

		@Override
		public net.minecraft.class_7243<? extends class_6910> method_41062()
		{
			throw new UnsupportedOperationException();
		}
	}

	private static class_4766 createMarineBiomeSource(MinecraftServer server)
	{
		List<Pair<class_6544.class_4762, class_6880<class_1959>>> entries = new ArrayList<>();

		addMarineBiome(entries, server, -1.0F, -0.6F, "deep_frozen_ocean", true);
		addMarineBiome(entries, server, -1.0F, -0.6F, "frozen_ocean", false);
		addMarineBiome(entries, server, -0.6F, -0.3F, "deep_cold_ocean", true);
		addMarineBiome(entries, server, -0.6F, -0.3F, "cold_ocean", false);
		addMarineBiome(entries, server, -0.3F, -0.05F, "deep_ocean", true);
		addMarineBiome(entries, server, -0.3F, -0.05F, "ocean", false);
		addMarineBiome(entries, server, -0.05F, 0.25F, "deep_lukewarm_ocean", true);
		addMarineBiome(entries, server, -0.05F, 0.25F, "lukewarm_ocean", false);
		addMarineBiome(entries, server, 0.25F, 0.55F, "deep_lukewarm_ocean", true);
		addMarineBiome(entries, server, 0.25F, 0.55F, "warm_ocean", false);
		addMarineBiome(entries, server, 0.55F, 1.0F, "warm_ocean", false);

		return class_4766.method_49501(new class_6544.class_6547(entries));
	}

	private static void addShoreBiome(List<Pair<class_6544.class_4762, class_6880<class_1959>>> entries, MinecraftServer server, float minTemperature, float maxTemperature, String biomeId)
	{
		entries.add(Pair.of(
				class_6544.method_38118(
						class_6544.class_6546.method_38121(minTemperature, maxTemperature),
						class_6544.class_6546.method_38121(-1.0F, 1.0F),
						class_6544.class_6546.method_38121(-0.15F, 0.35F),
						class_6544.class_6546.method_38121(-1.0F, 1.0F),
						class_6544.class_6546.method_38121(0.2F, 1.0F),
						class_6544.class_6546.method_38121(-1.0F, 1.0F),
						0.0F
				),
				getBiome(server, biomeId)
		));
	}

	private static void addMarineBiome(List<Pair<class_6544.class_4762, class_6880<class_1959>>> entries, MinecraftServer server, float minTemperature, float maxTemperature, String biomeId, boolean deep)
	{
		entries.add(Pair.of(
				class_6544.method_38118(
						class_6544.class_6546.method_38121(minTemperature, maxTemperature),
						class_6544.class_6546.method_38121(-1.0F, 1.0F),
						class_6544.class_6546.method_38121(-1.2F, deep ? -0.45F : 0.05F),
						class_6544.class_6546.method_38121(-1.0F, 1.0F),
						class_6544.class_6546.method_38121(deep ? -1.0F : 0.0F, deep ? -0.2F : 1.0F),
						class_6544.class_6546.method_38121(-1.0F, 1.0F),
						0.0F
				),
				getBiome(server, biomeId)
		));
	}

	private static class_6880<class_1959> getBiome(MinecraftServer server, String biomeId)
	{
		Optional<?> biome = server.method_30611()
				.method_30530(class_7924.field_41236)
				.method_40264(class_5321.method_29179(class_7924.field_41236, class_2960.method_60655("minecraft", biomeId)));
		return (class_6880<class_1959>)biome.orElseThrow(IllegalStateException::new);
	}

	@Inject(
			method = "createWorlds",
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
					ordinal = 0,
					shift = At.Shift.AFTER
			)
	)
	private void setSpawnPosTo00(CallbackInfo ci)
	{
		class_3218 world = this.uhcGameManager.getOverWorld();
		class_2338 spawnPos = class_2338.field_10980.method_10086(world.method_14178().method_12129().method_12100(world));
		float spawnAngle = world.method_43127();
		world.method_8554(spawnPos, spawnAngle);
	}

	@Inject(method = "tick", at = @At(value = "HEAD"))
	private void tickDurationSamplingStart(CallbackInfo ci)
	{
		this.uhcGameManager.msptRecorder.startTick();
	}

	@Inject(
			method = "runTasksTillTickEnd",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/MinecraftServer;runTasks(Ljava/util/function/BooleanSupplier;)V"
			)
	)
	private void tickDurationSamplingEnd(CallbackInfo ci)
	{
		if (this.serverInited)
		{
			this.uhcGameManager.msptRecorder.endTick();
		}
	}

	@Inject(method = "loadWorld", at = @At("RETURN"))
	private void postInitUhcGameManager(CallbackInfo ci)
	{
		if (!this.serverInited)
		{
			this.uhcGameManager.onServerInited();
			this.serverInited = true;
		}
	}

	@Inject(
			method = "tick",
			at = @At(
					value = "CONSTANT",
					args = "stringValue=tallying"
			)
	)
	private void tickUhcGameManager(CallbackInfo ci)
	{
		this.uhcGameManager.tick();
	}
}
