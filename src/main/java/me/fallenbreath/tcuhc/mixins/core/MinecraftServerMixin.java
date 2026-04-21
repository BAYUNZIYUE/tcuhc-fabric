package me.fallenbreath.tcuhc.mixins.core;

import com.mojang.datafixers.util.Pair;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.UhcGameTeam;
import me.fallenbreath.tcuhc.task.TaskPregenerate;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseRouter;
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
	private boolean wasGamePlaying = false;
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
					target = "Lnet/minecraft/server/world/ServerWorld;<init>(Lnet/minecraft/server/MinecraftServer;Ljava/util/concurrent/Executor;Lnet/minecraft/world/level/storage/LevelStorage$Session;Lnet/minecraft/world/level/ServerWorldProperties;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/world/dimension/DimensionOptions;Lnet/minecraft/server/WorldGenerationProgressListener;ZJLjava/util/List;ZLnet/minecraft/util/math/random/RandomSequencesState;)V"
			)
	)
	private void forceMarineOverworldBiomes(Args args)
	{
		if (UhcGameManager.getBattleType() != UhcGameManager.EnumBattleType.MARINE)
		{
			return;
		}

		RegistryKey<World> worldKey = args.get(4);
		if (worldKey != World.OVERWORLD)
		{
			return;
		}

		DimensionOptions dimensionOptions = args.get(5);
		ChunkGenerator chunkGenerator = dimensionOptions.chunkGenerator();
		if (!(chunkGenerator instanceof NoiseChunkGenerator))
		{
			return;
		}

		NoiseChunkGenerator noiseChunkGenerator = (NoiseChunkGenerator)chunkGenerator;
		args.set(5, new DimensionOptions(
				dimensionOptions.dimensionTypeEntry(),
				createMarineChunkGenerator((MinecraftServer)(Object)this, noiseChunkGenerator)
		));
		if (!marineGeneratorSwapLogged)
		{
			marineGeneratorSwapLogged = true;
			UhcGameManager.LOG.info("MARINE overworld generator swapped to {} with ocean-family multi-noise biomes", NoiseChunkGenerator.class.getSimpleName());
		}
	}

	private static NoiseChunkGenerator createMarineChunkGenerator(MinecraftServer server, NoiseChunkGenerator noiseChunkGenerator)
	{
		RegistryEntry<ChunkGeneratorSettings> originalSettingsEntry = noiseChunkGenerator.getSettings();
		ChunkGeneratorSettings originalSettings = originalSettingsEntry.value();
		NoiseRouter originalRouter = originalSettings.noiseRouter();

		ConstantDensityFunction deepOcean = new ConstantDensityFunction(-1.0);
		SubmergedDensityFunction oceanFloor = new SubmergedDensityFunction();

		NoiseRouter marineRouter = new NoiseRouter(
				originalRouter.barrierNoise(),
				originalRouter.fluidLevelFloodednessNoise(),
				originalRouter.fluidLevelSpreadNoise(),
				originalRouter.lavaNoise(),
				originalRouter.temperature(),
				originalRouter.vegetation(),
				deepOcean,
				originalRouter.erosion(),
				originalRouter.depth(),
				originalRouter.ridges(),
				oceanFloor,
				oceanFloor,
				originalRouter.veinToggle(),
				originalRouter.veinRidged(),
				originalRouter.veinGap()
		);

		ChunkGeneratorSettings marineSettings = new ChunkGeneratorSettings(
				originalSettings.generationShapeConfig(),
				originalSettings.defaultBlock(),
				originalSettings.defaultFluid(),
				marineRouter,
				originalSettings.surfaceRule(),
				originalSettings.spawnTarget(),
				originalSettings.seaLevel(),
				originalSettings.mobGenerationDisabled(),
				originalSettings.aquifers(),
				originalSettings.oreVeins(),
				originalSettings.usesLegacyRandom()
		);

		RegistryEntry<ChunkGeneratorSettings> marineSettingsEntry = RegistryEntry.of(marineSettings);

		return new NoiseChunkGenerator(createMarineBiomeSource(server), marineSettingsEntry);
	}

	private static class ConstantDensityFunction implements DensityFunction.Base
	{
		private final double value;

		ConstantDensityFunction(double value)
		{
			this.value = value;
		}

		@Override
		public double sample(DensityFunction.NoisePos pos)
		{
			return value;
		}

		@Override
		public double minValue()
		{
			return value;
		}

		@Override
		public double maxValue()
		{
			return value;
		}

		@Override
		public net.minecraft.util.dynamic.CodecHolder<? extends DensityFunction> getCodecHolder()
		{
			throw new UnsupportedOperationException();
		}
	}

	private static class SubmergedDensityFunction implements DensityFunction.Base
	{
		private static final double CENTER_Y = 48.0;
		private static final double AMPLITUDE = 23.0;

		@Override
		public double sample(DensityFunction.NoisePos pos)
		{
			int x = pos.blockX();
			int y = pos.blockY();
			int z = pos.blockZ();

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
		public double minValue()
		{
			return -0.5;
		}

		@Override
		public double maxValue()
		{
			return 0.5;
		}

		@Override
		public net.minecraft.util.dynamic.CodecHolder<? extends DensityFunction> getCodecHolder()
		{
			throw new UnsupportedOperationException();
		}
	}

	private static MultiNoiseBiomeSource createMarineBiomeSource(MinecraftServer server)
	{
		List<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> entries = new ArrayList<>();

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

		return MultiNoiseBiomeSource.create(new MultiNoiseUtil.Entries<>(entries));
	}

	private static void addShoreBiome(List<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> entries, MinecraftServer server, float minTemperature, float maxTemperature, String biomeId)
	{
		entries.add(Pair.of(
				MultiNoiseUtil.createNoiseHypercube(
						MultiNoiseUtil.ParameterRange.of(minTemperature, maxTemperature),
						MultiNoiseUtil.ParameterRange.of(-1.0F, 1.0F),
						MultiNoiseUtil.ParameterRange.of(-0.15F, 0.35F),
						MultiNoiseUtil.ParameterRange.of(-1.0F, 1.0F),
						MultiNoiseUtil.ParameterRange.of(0.2F, 1.0F),
						MultiNoiseUtil.ParameterRange.of(-1.0F, 1.0F),
						0.0F
				),
				getBiome(server, biomeId)
		));
	}

	private static void addMarineBiome(List<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> entries, MinecraftServer server, float minTemperature, float maxTemperature, String biomeId, boolean deep)
	{
		entries.add(Pair.of(
				MultiNoiseUtil.createNoiseHypercube(
						MultiNoiseUtil.ParameterRange.of(minTemperature, maxTemperature),
						MultiNoiseUtil.ParameterRange.of(-1.0F, 1.0F),
						MultiNoiseUtil.ParameterRange.of(-1.2F, deep ? -0.45F : 0.05F),
						MultiNoiseUtil.ParameterRange.of(-1.0F, 1.0F),
						MultiNoiseUtil.ParameterRange.of(deep ? -1.0F : 0.0F, deep ? -0.2F : 1.0F),
						MultiNoiseUtil.ParameterRange.of(-1.0F, 1.0F),
						0.0F
				),
				getBiome(server, biomeId)
		));
	}

	private static RegistryEntry<Biome> getBiome(MinecraftServer server, String biomeId)
	{
		Registry<Biome> biomeRegistry = server.getRegistryManager().get(RegistryKeys.BIOME);
		RegistryKey<Biome> biomeKey = RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", biomeId));
		return biomeRegistry.getEntry(biomeKey).orElseThrow(IllegalStateException::new);
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
		ServerWorld world = this.uhcGameManager.getOverWorld();
		BlockPos spawnPos = BlockPos.ORIGIN.up(world.getChunkManager().getChunkGenerator().getSpawnHeight(world));
		float spawnAngle = world.getSpawnAngle();
		world.setSpawnPos(spawnPos, spawnAngle);
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

		boolean isGamePlaying = this.uhcGameManager.isGamePlaying();
		if (isGamePlaying && !this.wasGamePlaying)
		{
			onGameStarted();
		}
		this.wasGamePlaying = isGamePlaying;
	}

	private void onGameStarted()
	{
		if (UhcGameManager.getBattleType() != UhcGameManager.EnumBattleType.MARINE)
		{
			return;
		}

		int borderStart = this.uhcGameManager.getOptions().getIntegerOptionValue("borderStart");
		int border = (int)(borderStart * 0.45);
		java.util.List<UhcGameTeam> teams = new java.util.ArrayList<>();
		for (UhcGameTeam team : this.uhcGameManager.getUhcPlayerManager().getTeams())
		{
			teams.add(team);
		}
		int teamCount = teams.size();
		if (teamCount <= 0)
		{
			return;
		}

		java.util.List<BlockPos> spawnPositions = new java.util.ArrayList<>();
		for (int i = 0; i < teamCount; i++)
		{
			double angle = i * (360.0 / teamCount) * Math.PI / 180;
			int x = (int)(border * Math.sin(angle));
			int z = (int)(border * Math.cos(angle));
			spawnPositions.add(new BlockPos(x, 64, z));
		}

		int radius = borderStart / 32 + 5;
		ServerWorld overworld = this.uhcGameManager.getOverWorld();
		TaskPregenerate.reprioritizeOverworld((MinecraftServer)(Object)this, radius, overworld, spawnPositions);
	}
}
