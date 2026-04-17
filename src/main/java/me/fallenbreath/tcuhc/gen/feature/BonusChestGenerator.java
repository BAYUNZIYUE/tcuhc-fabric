package me.fallenbreath.tcuhc.gen.feature;

import me.fallenbreath.tcuhc.TcUhcMod;
import me.fallenbreath.tcuhc.UhcGameManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;

public final class BonusChestGenerator
{
	private static final Identifier BONUS_LOOT_TABLE = TcUhcMod.id("bonus_chest/bonus");
	private static final Identifier EMPTY_LOOT_TABLE = TcUhcMod.id("bonus_chest/empty");
	private static final int MAX_PLACE_RETRIES = 3;
	private static final int SUMMARY_LOG_INTERVAL = 2000;
	private static final ConcurrentLinkedQueue<PendingChestPlacement> PENDING_CHESTS = new ConcurrentLinkedQueue<>();
	private static final Set<Long> PROCESSED_OVERWORLD_CHUNKS = ConcurrentHashMap.newKeySet();
	private static int processedChunkCount;
	private static int chanceHitCount;
	private static int scheduledChestCount;
	private static int placedChestCount;
	private static int retryChestCount;
	private static int rolledBackChestCount;

	private static class PendingChestPlacement
	{
		private final ServerWorld world;
		private final BlockPos chestPos;
		private final long randomSeed;
		private final boolean empty;
		private final int attempts;

		private PendingChestPlacement(ServerWorld world, BlockPos chestPos, long randomSeed, boolean empty, int attempts)
		{
			this.world = world;
			this.chestPos = chestPos.toImmutable();
			this.randomSeed = randomSeed;
			this.empty = empty;
			this.attempts = attempts;
		}

		private PendingChestPlacement retry()
		{
			return new PendingChestPlacement(this.world, this.chestPos, this.randomSeed, this.empty, this.attempts + 1);
		}
	}

	private enum PlacementResult
	{
		PLACED,
		RETRY,
		SKIP
	}

	private BonusChestGenerator()
	{
	}

	public static void register()
	{
		ServerChunkEvents.CHUNK_GENERATE.register(BonusChestGenerator::onChunkGenerate);
		ServerChunkEvents.CHUNK_LOAD.register(BonusChestGenerator::onChunkLoad);
		ServerTickEvents.END_SERVER_TICK.register(server -> flushPendingChests());
	}

	private static void onChunkGenerate(ServerWorld world, WorldChunk chunk)
	{
		scheduleChestPlacement(world, chunk);
	}

	private static void onChunkLoad(ServerWorld world, WorldChunk chunk)
	{
		scheduleChestPlacement(world, chunk);
	}

	private static void scheduleChestPlacement(ServerWorld world, WorldChunk chunk)
	{
		if (UhcGameManager.instance == null || world.getRegistryKey() != World.OVERWORLD)
		{
			return;
		}
		ChunkPos chunkPos = chunk.getPos();
		if (!PROCESSED_OVERWORLD_CHUNKS.add(chunkPos.toLong()))
		{
			return;
		}
		if (Math.abs(chunkPos.x) <= 1 || Math.abs(chunkPos.z) <= 1)
		{
			return;
		}
		processedChunkCount++;
		maybeLogSummary();
		double chestChance = UhcGameManager.instance.getOptions().getFloatOptionValue("chestFrequency");
		double emptyChestChance = UhcGameManager.instance.getOptions().getFloatOptionValue("trappedChestFrequency");
		double biomeChance = getBiomeChance(world, chunk);
		if (biomeChance <= 0.0D || chestChance <= 0.0D)
		{
			return;
		}
		Random random = Random.create(world.getSeed() ^ chunkPos.toLong() * 341873128712L);
		if (random.nextDouble() >= biomeChance * chestChance)
		{
			return;
		}
		chanceHitCount++;
		BlockPos groundPos = findChestPos(world, chunk, random);
		if (groundPos == null)
		{
			return;
		}
		BlockPos chestPos = groundPos;
		boolean empty = random.nextDouble() < emptyChestChance;
		// Defer block-entity mutation until the end of a server tick so we are outside the active chunk generation call stack.
		PENDING_CHESTS.add(new PendingChestPlacement(world, chestPos, random.nextLong(), empty, 0));
		scheduledChestCount++;
	}

	private static void flushPendingChests()
	{
		PendingChestPlacement pending;
		List<PendingChestPlacement> retryPlacements = new ArrayList<>();
		while ((pending = PENDING_CHESTS.poll()) != null)
		{
			PlacementResult result = placeChest(pending.world, pending.chestPos, Random.create(pending.randomSeed), pending.empty, pending.attempts);
			if (result == PlacementResult.RETRY && pending.attempts < MAX_PLACE_RETRIES)
			{
				retryPlacements.add(pending.retry());
			}
		}
		retryPlacements.forEach(PENDING_CHESTS::add);
	}

	private static BlockPos findChestPos(ServerWorld world, WorldChunk chunk, Random random)
	{
		ChunkPos chunkPos = chunk.getPos();
		int localX = random.nextInt(16);
		int localZ = random.nextInt(16);
		int x = chunkPos.getStartX() + localX;
		int z = chunkPos.getStartZ() + localZ;
		int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, localX, localZ);
		BlockPos supportPos = new BlockPos(x, y - 1, z);
		while (!chunk.getBlockState(supportPos).isSolidBlock(world, supportPos) && supportPos.getY() > world.getBottomY())
		{
			supportPos = supportPos.down();
		}
		if (supportPos.getY() <= world.getBottomY())
		{
			return null;
		}
		BlockPos chestPos = supportPos.up();
		BlockState supportState = chunk.getBlockState(supportPos);
		BlockState targetState = chunk.getBlockState(chestPos);
		if (!supportState.isSideSolidFullSquare(world, supportPos, Direction.UP) || supportState.hasBlockEntity())
		{
			return null;
		}
		if (targetState.hasBlockEntity())
		{
			return null;
		}
		if (!targetState.isAir() && !targetState.isReplaceable() && !targetState.isOf(Blocks.SNOW) && !world.getFluidState(chestPos).isStill())
		{
			return null;
		}
		return chestPos;
	}

	private static PlacementResult placeChest(ServerWorld world, BlockPos chestPos, Random random, boolean empty, int attempts)
	{
		WorldChunk chunk = world.getWorldChunk(chestPos);
		BlockState oldState = chunk.getBlockState(chestPos);
		if (oldState.hasBlockEntity())
		{
			return PlacementResult.SKIP;
		}
		boolean waterlogged = world.getFluidState(chestPos).isStill();
		BlockState chestState = (empty ? Blocks.TRAPPED_CHEST : Blocks.CHEST)
				.getDefaultState()
				.rotate(BlockRotation.random(random))
				.with(ChestBlock.WATERLOGGED, waterlogged);
		// Place bonus chests through ServerWorld on the main thread so the block entity is created
		// and persisted just like the original worldgen feature path.
		if (!world.setBlockState(chestPos, chestState, Block.NOTIFY_LISTENERS))
		{
			return PlacementResult.RETRY;
		}
		if (chunk.getBlockState(chestPos.up()).isOf(Blocks.SNOW))
		{
			world.setBlockState(chestPos.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		BlockEntity blockEntity = world.getBlockEntity(chestPos);
		if (blockEntity instanceof LootableContainerBlockEntity)
		{
			LootableContainerBlockEntity container = (LootableContainerBlockEntity)blockEntity;
			container.setLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, empty ? EMPTY_LOOT_TABLE : BONUS_LOOT_TABLE), random.nextLong());
			world.getChunkManager().markForUpdate(chestPos);
			placedChestCount++;
			return PlacementResult.PLACED;
		}
		if (attempts >= MAX_PLACE_RETRIES)
		{
			chunk.setBlockState(chestPos, oldState, false);
			world.updateListeners(chestPos, chestState, oldState, Block.NOTIFY_LISTENERS);
			rolledBackChestCount++;
			UhcGameManager.LOG.warn("Bonus chest block entity never became ready at {} after {} attempts; rolled back placement", chestPos, attempts + 1);
			return PlacementResult.SKIP;
		}
		retryChestCount++;
		UhcGameManager.LOG.warn("Bonus chest block entity was not ready at {} on attempt {}", chestPos, attempts + 1);
		return PlacementResult.RETRY;
	}

	private static void maybeLogSummary()
	{
		if (processedChunkCount > 0 && processedChunkCount % SUMMARY_LOG_INTERVAL == 0)
		{
			UhcGameManager.LOG.info(
					"Bonus chest summary: processed={} chance_hits={} scheduled={} placed={} retries={} rolled_back={}",
					processedChunkCount,
					chanceHitCount,
					scheduledChestCount,
					placedChestCount,
					retryChestCount,
					rolledBackChestCount
			);
		}
	}

	private static double getBiomeChance(ServerWorld world, WorldChunk chunk)
	{
		ChunkPos chunkPos = chunk.getPos();
		int localCenterX = 8;
		int localCenterZ = 8;
		BlockPos center = new BlockPos(
				chunkPos.getStartX() + localCenterX,
				chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, localCenterX, localCenterZ),
				chunkPos.getStartZ() + localCenterZ
		);
		RegistryEntry<Biome> biome = world.getChunkManager().getChunkGenerator().getBiomeSource().getBiome(
				BiomeCoords.fromBlock(center.getX()),
				BiomeCoords.fromBlock(center.getY()),
				BiomeCoords.fromBlock(center.getZ()),
				world.getChunkManager().getNoiseConfig().getMultiNoiseSampler()
		);
		String biomeId = biome.getKey().map(key -> key.getValue().getPath()).orElse("");
		if (biomeId.contains("river") || biomeId.contains("beach"))
		{
			return 0.0D;
		}
		if (biomeId.contains("mushroom"))
		{
			return 0.10D;
		}
		if (biomeId.contains("swamp") || biomeId.contains("mangrove"))
		{
			return 0.10D;
		}
		if (biomeId.contains("desert"))
		{
			return 0.06D;
		}
		if (biomeId.contains("mesa") || biomeId.contains("badlands"))
		{
			return 0.12D;
		}
		if (biomeId.contains("jungle"))
		{
			return 0.12D;
		}
		if (biomeId.contains("savanna"))
		{
			return 0.12D;
		}
		if (biomeId.contains("taiga") || biomeId.contains("forest") || biomeId.contains("grove"))
		{
			return 0.12D;
		}
		if (biomeId.contains("snow") || biomeId.contains("ice") || biomeId.contains("frozen"))
		{
			return 0.20D;
		}
		if (biomeId.contains("mountain") || biomeId.contains("windswept") || biomeId.contains("peak") || biomeId.contains("hill"))
		{
			return 0.12D;
		}
		if (biomeId.contains("plains") || biomeId.contains("meadow") || biomeId.contains("sunflower"))
		{
			return 0.06D;
		}
		if (biomeId.contains("ocean"))
		{
			return UhcGameManager.getBattleType() == UhcGameManager.EnumBattleType.MARINE ? 0.20D : 0.0D;
		}
		return 0.0D;
	}
}
