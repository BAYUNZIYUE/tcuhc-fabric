package me.fallenbreath.tcuhc.mixins.core;

import com.mojang.datafixers.util.Pair;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.UhcGameTeam;
import me.fallenbreath.tcuhc.options.Options;
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
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.noise.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin
{
	/**
	 * Vanilla switches away from ocean biomes around this continentalness value; the river band
	 * sits directly above it. Everything at or above this value is left completely alone, which is
	 * what keeps rivers - and the existing land - untouched.
	 */
	private static final double OCEAN_EDGE = -0.19D;
	/** Width of the smooth transition band just below {@link #OCEAN_EDGE}, in continentalness units. */
	private static final double OCEAN_BLEND_WIDTH = 0.15D;
	/**
	 * Base height of the surface that replaces the former sea floor. Sea level is 63, so 78 is
	 * comfortably dry; the noise below brings the lowest hollows down to roughly 64, which leaves a
	 * scattering of small ponds instead of the old open ocean.
	 */
	private static final double LAND_SURFACE_BASE_HEIGHT = 78.0D;
	/** Half-thickness of the density ramp around the replacement surface, in blocks. */
	private static final double LAND_SURFACE_RAMP = 6.0D;

	private UhcGameManager uhcGameManager;
	private boolean serverInited = false;
	private boolean wasGamePlaying = false;
	private static boolean marineGeneratorSwapLogged = false;
	private static boolean landGeneratorSwapLogged = false;

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
	private void adjustOverworldBiomes(Args args)
	{
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

		if (UhcGameManager.getBattleType() == UhcGameManager.EnumBattleType.MARINE)
		{
			args.set(5, new DimensionOptions(
					dimensionOptions.dimensionTypeEntry(),
					createMarineChunkGenerator((MinecraftServer)(Object)this, noiseChunkGenerator)
			));
			if (!marineGeneratorSwapLogged)
			{
				marineGeneratorSwapLogged = true;
				UhcGameManager.LOG.info("MARINE overworld generator swapped to {} with ocean-family multi-noise biomes", NoiseChunkGenerator.class.getSimpleName());
			}
			return;
		}

		if (!Options.instance.getBooleanOptionValue("disableOceanBiomes"))
		{
			return;
		}

		args.set(5, new DimensionOptions(
				dimensionOptions.dimensionTypeEntry(),
				createLandChunkGenerator((MinecraftServer)(Object)this, noiseChunkGenerator)
		));
		if (!landGeneratorSwapLogged)
		{
			landGeneratorSwapLogged = true;
			UhcGameManager.LOG.info("Overworld ocean biomes replaced by same-temperature land biomes (disableOceanBiomes)");
		}
	}

	/**
	 * Rebuilds the vanilla overworld biome source with every ocean-family entry swapped for a
	 * land biome of the same temperature slot, and remaps the {@code continents} density function
	 * so the former basins are raised into land as well.
	 *
	 * <p>Relabelling biomes alone is not enough: the terrain <em>shape</em> comes from the noise
	 * router, which would stay vanilla, so the former ocean basins keep their deep sea floor and
	 * the sea level of 63 fills them with water - which is exactly the "large body of water in the
	 * middle of a desert" symptom. The noise parameter ranges are kept untouched, so the regions
	 * that used to be ocean now generate land with identical climate placement.
	 *
	 * <p>Note the ordering of the temperature checks: "lukewarm" contains "warm".
	 */
	private static NoiseChunkGenerator createLandChunkGenerator(MinecraftServer server, NoiseChunkGenerator noiseChunkGenerator)
	{
		Registry<Biome> biomeRegistry = server.getRegistryManager().get(RegistryKeys.BIOME);
		Map<MultiNoiseBiomeSourceParameterList.Preset, MultiNoiseUtil.Entries<RegistryKey<Biome>>> presets =
				MultiNoiseBiomeSourceParameterList.getPresetToEntriesMap();
		MultiNoiseUtil.Entries<RegistryKey<Biome>> overworldEntries =
				presets.get(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD);
		if (overworldEntries == null)
		{
			UhcGameManager.LOG.warn("Overworld multi-noise preset not found, keeping vanilla biomes");
			return noiseChunkGenerator;
		}

		List<Pair<MultiNoiseUtil.NoiseHypercube, RegistryEntry<Biome>>> entries = new ArrayList<>();
		int replacedCount = 0;
		int skippedCount = 0;
		for (Pair<MultiNoiseUtil.NoiseHypercube, RegistryKey<Biome>> pair : overworldEntries.getEntries())
		{
			RegistryKey<Biome> biomeKey = pair.getSecond();
			RegistryKey<Biome> replacement = getLandReplacement(biomeKey);
			if (replacement != null)
			{
				biomeKey = replacement;
				replacedCount++;
			}
			RegistryEntry<Biome> biomeEntry = biomeRegistry.getEntry(biomeKey).orElse(null);
			if (biomeEntry == null)
			{
				skippedCount++;
				continue;
			}
			entries.add(Pair.of(pair.getFirst(), biomeEntry));
		}

		UhcGameManager.LOG.info(
				"Overworld biome source rebuilt: {} ocean entries replaced, {} entries skipped, {} kept",
				replacedCount, skippedCount, entries.size()
		);

		MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.create(new MultiNoiseUtil.Entries<>(entries));

		ChunkGeneratorSettings originalSettings = noiseChunkGenerator.getSettings().value();
		NoiseRouter originalRouter = originalSettings.noiseRouter();

		// final_density and initial_density_without_jaggedness are declared inline in
		// noise_settings, so they are separate objects holding their own copies of the depth/offset
		// subtrees. Replacing router.depth() or router.continents() therefore changes nothing - the
		// sampler keeps using the untouched trees. The replacement has to be applied to the two
		// density functions themselves, which is where the isosurface is actually decided.
		//
		// A constant added to the density cannot do the job: the former basins have more internal
		// relief than the gap up to the sea level, so any lift big enough to drain the deepest
		// hollows pushes the high spots through the build height limit. Substituting a surface of
		// our own instead replaces the relief rather than shifting it.
		DensityFunction weight = buildOceanWeight(originalRouter.continents());
		DensityFunction landSurface = new LandSurfaceDensityFunction();

		NoiseRouter landRouter = new NoiseRouter(
				originalRouter.barrierNoise(),
				originalRouter.fluidLevelFloodednessNoise(),
				originalRouter.fluidLevelSpreadNoise(),
				originalRouter.lavaNoise(),
				originalRouter.temperature(),
				originalRouter.vegetation(),
				originalRouter.continents(),
				originalRouter.erosion(),
				originalRouter.depth(),
				originalRouter.ridges(),
				DensityFunctionTypes.lerp(weight, originalRouter.initialDensityWithoutJaggedness(), landSurface),
				DensityFunctionTypes.lerp(weight, originalRouter.finalDensity(), landSurface),
				originalRouter.veinToggle(),
				originalRouter.veinRidged(),
				originalRouter.veinGap()
		);

		ChunkGeneratorSettings landSettings = new ChunkGeneratorSettings(
				originalSettings.generationShapeConfig(),
				originalSettings.defaultBlock(),
				originalSettings.defaultFluid(),
				landRouter,
				originalSettings.surfaceRule(),
				originalSettings.spawnTarget(),
				originalSettings.seaLevel(),
				originalSettings.mobGenerationDisabled(),
				originalSettings.aquifers(),
				originalSettings.oreVeins(),
				originalSettings.usesLegacyRandom()
		);

		UhcGameManager.LOG.info(
				"Landified terrain installed: ocean edge {}, blend width {}, replacement surface {} +/- 14 blocks",
				OCEAN_EDGE, OCEAN_BLEND_WIDTH, LAND_SURFACE_BASE_HEIGHT
		);

		return new NoiseChunkGenerator(biomeSource, RegistryEntry.of(landSettings));
	}

	/**
	 * Builds the weight that decides how much of the ocean lift applies at a given position: 0 at
	 * the river/land boundary and for everything above it, smoothly reaching 1 once the position is
	 * {@link #OCEAN_BLEND_WIDTH} below that boundary.
	 *
	 * <p>Two things make this more subtle than it looks:
	 *
	 * <ul>
	 * <li>{@code continents} is a {@code flat_cache} node. Its value only exists while the
	 * {@code ChunkNoiseSampler} drives the tree; a hand-written {@code DensityFunction} that calls
	 * {@code continents.sample(pos)} out of band reads 0.0 and therefore never matches anything.
	 * That is why this is built out of the stock {@link DensityFunctionTypes} combinators instead
	 * of a custom class - the sampler understands and caches the whole expression itself.</li>
	 * <li>The weight is a smoothstep, so the derivative is continuous where the lifted region meets
	 * the untouched river band. A hard threshold would leave a visible scarp along the coast.</li>
	 * </ul>
	 */
	private static DensityFunction buildOceanWeight(DensityFunction continents)
	{
		DensityFunction zero = DensityFunctionTypes.constant(0.0D);
		DensityFunction one = DensityFunctionTypes.constant(1.0D);

		// m = clamp((OCEAN_EDGE - c) / OCEAN_BLEND_WIDTH, 0, 1)
		DensityFunction m = DensityFunctionTypes.min(
				DensityFunctionTypes.max(
						DensityFunctionTypes.add(
								DensityFunctionTypes.mul(continents, DensityFunctionTypes.constant(-1.0D / OCEAN_BLEND_WIDTH)),
								DensityFunctionTypes.constant(OCEAN_EDGE / OCEAN_BLEND_WIDTH)
						),
						zero
				),
				one
		);

		// smoothstep(m) = m * m * (3 - 2m)
		return DensityFunctionTypes.mul(
				DensityFunctionTypes.mul(m, m),
				DensityFunctionTypes.add(
						DensityFunctionTypes.constant(3.0D),
						DensityFunctionTypes.mul(m, DensityFunctionTypes.constant(-2.0D))
				)
		);
	}

	/** Returns the land biome an ocean-family biome should become, or null when it is not an ocean. */
	private static RegistryKey<Biome> getLandReplacement(RegistryKey<Biome> biomeKey)
	{
		String path = biomeKey.getValue().getPath();
		if (!path.contains("ocean"))
		{
			return null;
		}
		String landBiome;
		if (path.contains("frozen"))
		{
			landBiome = "snowy_plains";
		}
		else if (path.contains("cold"))
		{
			landBiome = "taiga";
		}
		else if (path.contains("lukewarm"))
		{
			landBiome = "forest";
		}
		else if (path.contains("warm"))
		{
			landBiome = "desert";
		}
		else
		{
			landBiome = "plains";
		}
		return RegistryKey.of(RegistryKeys.BIOME, Identifier.of("minecraft", landBiome));
	}

	private static NoiseChunkGenerator createMarineChunkGenerator(MinecraftServer server, NoiseChunkGenerator noiseChunkGenerator)
	{
		RegistryEntry<ChunkGeneratorSettings> originalSettingsEntry = noiseChunkGenerator.getSettings();
		ChunkGeneratorSettings originalSettings = originalSettingsEntry.value();
		NoiseRouter originalRouter = originalSettings.noiseRouter();

		// Keep this even though the 1.2.7 note says replacing router.continents() cannot change the
		// terrain shape (final_density and initial_density_without_jaggedness are inline copies of
		// their own subtrees). It is not dead here: continents is still what the multi-noise
		// sampler reads for biome placement, and pinning it to -1.0 is what keeps
		// createMarineBiomeSource's continentalness ranges resolving to open-ocean entries.
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

	/**
	 * Smooth value noise over a hashed lattice, used by the MARINE sea floor below. Deliberately
	 * not derived from the world seed: the generated shape only has to be plausible, and a fixed
	 * function keeps terrain generation reproducible between runs.
	 *
	 * <p>Do not use this for a large open land surface: smoothstep interpolation flattens the field
	 * around every lattice point and piles all the slope onto the cell mid-lines, which reads as
	 * quadrilateral plateaus with straight edges. {@link #gradientNoise} is the one to use there.
	 */
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

	/**
	 * Classic Perlin gradient noise. This replaced {@link #valueNoise} for the replacement surface,
	 * because value noise was the reason the terrain came out faceted: smoothstep interpolation has
	 * a zero derivative at the lattice points, so the field is flat around every lattice point and
	 * all of the slope is squeezed onto the cell mid-lines. The result is a quilt of quadrilateral
	 * plateaus with straight edges - the reported "angular terrain".
	 *
	 * <p>Gradient noise does not have that problem: the value at a lattice point is always zero and
	 * the extrema sit *between* the lattice points, so there are no flat spots. The quintic fade
	 * makes it C2, so there are no creases either.
	 */
	private static double gradientNoise(double x, double z, long seed)
	{
		int x0 = (int)Math.floor(x);
		int z0 = (int)Math.floor(z);
		double fx = x - x0;
		double fz = z - z0;
		double u = fade(fx);
		double v = fade(fz);

		double n00 = gradientDot(x0, z0, fx, fz, seed);
		double n10 = gradientDot(x0 + 1, z0, fx - 1.0D, fz, seed);
		double n01 = gradientDot(x0, z0 + 1, fx, fz - 1.0D, seed);
		double n11 = gradientDot(x0 + 1, z0 + 1, fx - 1.0D, fz - 1.0D, seed);

		double a = n00 + (n10 - n00) * u;
		double b = n01 + (n11 - n01) * u;
		// 2D Perlin peaks at sqrt(2)/2, so this scales the result back to roughly [-1, 1].
		return (a + (b - a) * v) * 1.4142135623730951D;
	}

	/** Quintic fade, a.k.a. smootherstep: C2 at the lattice points, unlike the cubic smoothstep. */
	private static double fade(double t)
	{
		return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
	}

	/**
	 * Dot product between the pseudo-random gradient at a lattice point and the offset vector.
	 * Eight evenly spread directions; the diagonals are pre-scaled by 1/sqrt(2) so that all eight
	 * have the same length, which is what keeps the noise isotropic instead of showing a 45 degree
	 * preference.
	 */
	private static double gradientDot(int x, int z, double dx, double dz, long seed)
	{
		int index = (int)((hash64(x, z, seed) >>> 24) & 7L);
		double gx;
		double gz;
		switch (index)
		{
			case 0: gx = 1.0D; gz = 0.0D; break;
			case 1: gx = -1.0D; gz = 0.0D; break;
			case 2: gx = 0.0D; gz = 1.0D; break;
			case 3: gx = 0.0D; gz = -1.0D; break;
			case 4: gx = 0.70710678D; gz = 0.70710678D; break;
			case 5: gx = -0.70710678D; gz = 0.70710678D; break;
			case 6: gx = 0.70710678D; gz = -0.70710678D; break;
			default: gx = -0.70710678D; gz = -0.70710678D; break;
		}
		return gx * dx + gz * dz;
	}

	/** Murmur3 finalizer over a lattice coordinate; the cheap 16 bit version visibly bands. */
	private static long hash64(int x, int z, long seed)
	{
		long h = (long)x * 0x9E3779B97F4A7C15L ^ (long)z * 0xC2B2AE3D27D4EB4FL ^ seed * 0x165667B19E3779F9L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return h;
	}

	/**
	 * The surface that takes over inside the former ocean basins: positive below
	 * {@link #surfaceHeight}, negative above it, so it reads as ordinary ground to the rest of the
	 * chunk pipeline (surface rules, aquifers, carvers all work off the density sign).
	 *
	 * <p>It is deliberately self-contained - it only uses its own noise and the block position,
	 * never another density function. Sampling another density function from a custom class does
	 * not work, because those only produce real values while the ChunkNoiseSampler drives the tree.
	 *
	 * <p>Five octaves of gradient noise, domain warped and rotated relative to each other, are what
	 * break the former sea floor - one huge flat basin each - into hills and hollows that do not
	 * repeat or line up with any grid.
	 */
	private static class LandSurfaceDensityFunction implements DensityFunction.Base
	{
		private static double surfaceHeight(int blockX, int blockZ)
		{
			double x = blockX;
			double z = blockZ;

			// Domain warp: displacing the sample point with another noise field is what finally
			// hides the lattice. The octaves stop lining up with each other, so ridges meander
			// instead of running along grid lines and no two hills come out the same shape.
			double warpX = x + gradientNoise(x / 240.0D, z / 240.0D, 0x51AB1L) * 54.0D;
			double warpZ = z + gradientNoise(x / 240.0D + 11.3D, z / 240.0D - 7.9D, 0x51AB2L) * 54.0D;

			double height = LAND_SURFACE_BASE_HEIGHT;
			height += octave(warpX, warpZ, 1.0D / 230.0D, 1.0D, 0.0D, 0x5EED01L) * 8.0D;
			height += octave(warpX, warpZ, 1.0D / 97.0D, 0.8253356D, 0.5646425D, 0x5EED02L) * 4.0D;
			height += octave(warpX, warpZ, 1.0D / 43.0D, 0.2674988D, 0.9635582D, 0x5EED03L) * 2.0D;
			height += octave(warpX, warpZ, 1.0D / 18.0D, -0.5048461D, 0.8632094D, 0x5EED04L) * 1.2D;
			height += octave(warpX, warpZ, 1.0D / 7.0D, 0.6967067D, -0.7173561D, 0x5EED05L) * 0.8D;
			return height;
		}

		/** One noise octave, rotated so that the octaves do not share a lattice orientation. */
		private static double octave(double x, double z, double inverseScale, double cos, double sin, long seed)
		{
			double rx = (x * cos - z * sin) * inverseScale;
			double rz = (x * sin + z * cos) * inverseScale;
			return gradientNoise(rx, rz, seed);
		}

		@Override
		public double sample(DensityFunction.NoisePos pos)
		{
			double diff = surfaceHeight(pos.blockX(), pos.blockZ()) - pos.blockY();
			if (diff > LAND_SURFACE_RAMP)
			{
				return 1.0D;
			}
			if (diff < -LAND_SURFACE_RAMP)
			{
				return -1.0D;
			}
			return diff / LAND_SURFACE_RAMP;
		}

		@Override
		public double minValue()
		{
			return -1.0D;
		}

		@Override
		public double maxValue()
		{
			return 1.0D;
		}

		@Override
		public net.minecraft.util.dynamic.CodecHolder<? extends DensityFunction> getCodecHolder()
		{
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Note: a custom DensityFunction must be self-contained - it may not sample another density
	 * function, because those only produce real values while the ChunkNoiseSampler drives them.
	 * These two build their shape from their own noise, which is why they work.
	 */
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

	/**
	 * The MARINE sea floor: positive below {@link #floorHeight}, negative above it, so the rest of
	 * the chunk pipeline reads it as ordinary ground.
	 *
	 * <p>Built on the same technique as {@link LandSurfaceDensityFunction}, for the same reasons.
	 * It used to use {@link #valueNoise} over three axis-aligned octaves, which produced the
	 * reported ocean-floor problems: smoothstep interpolation is flat at every lattice point and
	 * piles the whole slope onto the cell mid-lines, so the floor came out as a quilt of
	 * quadrilateral plateaus with straight edges running along the noise grid. Gradient noise has
	 * its extrema between lattice points and no flat spots, the domain warp stops the octaves from
	 * lining up, and rotating each octave keeps them off a shared lattice orientation.
	 *
	 * <p>Self-contained by necessity: a custom DensityFunction may not sample another density
	 * function, because those only produce real values while the ChunkNoiseSampler drives the tree.
	 */
	private static class SubmergedDensityFunction implements DensityFunction.Base
	{
		/**
		 * Centre height and peak deviation of the sea floor. Sea level is 63, so the floor is
		 * submerged almost everywhere but its highest peaks still breach the surface as small
		 * islands - MARINE seeds oak logs and saplings in its bonus chests, so somewhere to plant
		 * them is part of the mode.
		 *
		 * <p>These are not the old 48/23. Gradient noise summed over octaves is far more
		 * concentrated around its mean than the old value-noise sum was, so keeping 48/23 would
		 * have produced a floor that never breached the surface at all - measured at 0.000% island
		 * coverage against the old 0.81%. 50/32 reproduces the old envelope
		 * (max ~69, ~0.84% of the area above sea level) without reintroducing the faceting.
		 */
		private static final double CENTER_Y = 50.0;
		private static final double AMPLITUDE = 32.0;
		/** Half-thickness of the density ramp around the floor, in blocks. */
		private static final double FLOOR_RAMP = 4.0;
		/** Sum of the octave weights below; used to normalise them back to +/-1 before scaling. */
		private static final double OCTAVE_WEIGHT_SUM = 1.0D + 0.5D + 0.25D + 0.145D + 0.08D;

		private static double floorHeight(int blockX, int blockZ)
		{
			double x = blockX;
			double z = blockZ;

			double warpX = x + gradientNoise(x / 210.0D, z / 210.0D, 0x0CEA41L) * 48.0D;
			double warpZ = z + gradientNoise(x / 210.0D + 5.7D, z / 210.0D - 3.1D, 0x0CEA42L) * 48.0D;

			double h = 0.0D;
			h += octave(warpX, warpZ, 1.0D / 190.0D, 1.0D, 0.0D, 0x0CEA01L) * 1.0D;
			h += octave(warpX, warpZ, 1.0D / 86.0D, 0.8253356D, 0.5646425D, 0x0CEA02L) * 0.5D;
			h += octave(warpX, warpZ, 1.0D / 39.0D, 0.2674988D, 0.9635582D, 0x0CEA03L) * 0.25D;
			h += octave(warpX, warpZ, 1.0D / 17.0D, -0.5048461D, 0.8632094D, 0x0CEA04L) * 0.145D;
			h += octave(warpX, warpZ, 1.0D / 6.0D, 0.6967067D, -0.7173561D, 0x0CEA05L) * 0.08D;

			return CENTER_Y + h / OCTAVE_WEIGHT_SUM * AMPLITUDE;
		}

		/** One noise octave, rotated so that the octaves do not share a lattice orientation. */
		private static double octave(double x, double z, double inverseScale, double cos, double sin, long seed)
		{
			double rx = (x * cos - z * sin) * inverseScale;
			double rz = (x * sin + z * cos) * inverseScale;
			return gradientNoise(rx, rz, seed);
		}

		@Override
		public double sample(DensityFunction.NoisePos pos)
		{
			double diff = floorHeight(pos.blockX(), pos.blockZ()) - pos.blockY();
			if (diff > FLOOR_RAMP)
			{
				return 1.0D;
			}
			if (diff < -FLOOR_RAMP)
			{
				return -1.0D;
			}
			return diff / FLOOR_RAMP;
		}

		// These must bound sample() honestly. They used to declare -0.5/0.5 while sample() ranged
		// over [-1, 1]; the ChunkNoiseSampler uses the declared bounds to skip interpolation cells,
		// so under-declaring them lets it discard cells that actually contained terrain.
		@Override
		public double minValue()
		{
			return -1.0D;
		}

		@Override
		public double maxValue()
		{
			return 1.0D;
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
