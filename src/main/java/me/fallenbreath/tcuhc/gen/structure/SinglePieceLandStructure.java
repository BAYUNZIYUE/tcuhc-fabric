package me.fallenbreath.tcuhc.gen.structure;

import com.mojang.serialization.MapCodec;
import me.fallenbreath.tcuhc.UhcGameManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.SimpleStructurePiece;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class SinglePieceLandStructure extends Structure
{
	protected SinglePieceLandStructure(Config config)
	{
		super(config);
	}

	@Override
	public GenerationStep.Feature getFeatureGenerationStep()
	{
		return GenerationStep.Feature.SURFACE_STRUCTURES;
	}

	protected abstract boolean canGenerate(Context context);

	protected abstract void addPieces(StructurePiecesCollector collector, Context context);

	@Override
	protected java.util.Optional<StructurePosition> getStructurePosition(Context context)
	{
		if (!this.canGenerate(context))
		{
			return java.util.Optional.empty();
		}
		return getStructurePosition(context, Heightmap.Type.WORLD_SURFACE_WG, new Consumer<StructurePiecesCollector>() {
			@Override
			public void accept(StructurePiecesCollector collector)
			{
				addPieces(collector, context);
			}
		});
	}

	public static boolean canGenerateIn(RegistryEntry<Biome> biome)
	{
		Biome b = biome.value();
		Biome.Precipitation precipitation = b.getPrecipitation(BlockPos.ORIGIN);
		return precipitation == Biome.Precipitation.NONE || precipitation == Biome.Precipitation.RAIN || precipitation == Biome.Precipitation.SNOW;
	}

	protected static BlockPos shiftStartPosRandomly(Context context)
	{
		return context.chunkPos().getStartPos().add(context.random().nextInt(16), 0, context.random().nextInt(16));
	}

	protected static boolean isBiomeValid(Context context, BlockPos pos)
	{
		int y = context.chunkGenerator().getHeightInGround(pos.getX(), pos.getZ(), Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig());
		RegistryEntry<Biome> biome = context.chunkGenerator().getBiomeSource().getBiome(
				net.minecraft.world.biome.source.BiomeCoords.fromBlock(pos.getX()),
				net.minecraft.world.biome.source.BiomeCoords.fromBlock(y),
				net.minecraft.world.biome.source.BiomeCoords.fromBlock(pos.getZ()),
				context.noiseConfig().getMultiNoiseSampler()
		);
		return context.biomePredicate().test(biome);
	}

	protected static boolean isBiomeValidInChunk(Context context)
	{
		for (int x = context.chunkPos().getStartX(); x <= context.chunkPos().getEndX(); x++)
		{
			for (int z = context.chunkPos().getStartZ(); z <= context.chunkPos().getEndZ(); z++)
			{
				if (!isBiomeValid(context, new BlockPos(x, 0, z)))
				{
					return false;
				}
			}
		}
		return true;
	}

	protected static boolean isSurroundingFlat(Context context, Heightmap.Type heightMapType, int range, int maxDelta)
	{
		BlockPos startPos = context.chunkPos().getStartPos();
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int x = -range; x <= range; x++)
		{
			for (int z = -range; z <= range; z++)
			{
				BlockPos pos = startPos.add(x, 0, z);
				int y = context.chunkGenerator().getHeightInGround(pos.getX(), pos.getZ(), heightMapType, context.world(), context.noiseConfig());
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
		}
		return maxY - minY <= maxDelta;
	}

	protected static void fillBottomAirGap(StructureWorldAccess world, Random random, BlockBox chunkBox, StructurePiecesList children, BiPredicate<BlockPos, BlockState> blockTester, Function<Random, BlockState> blockGetter, int yOffset)
	{
		int worldBottomY = world.getBottomY();
		BlockBox blockBox = children.getBoundingBox();
		int minY = blockBox.getMinY() + yOffset;
		BlockPos.Mutable blockPos = new BlockPos.Mutable();

		for (int x = chunkBox.getMinX(); x <= chunkBox.getMaxX(); x++)
		{
			for (int z = chunkBox.getMinZ(); z <= chunkBox.getMaxZ(); z++)
			{
				blockPos.set(x, minY, z);
				if (blockTester.test(blockPos, world.getBlockState(blockPos)) && blockBox.contains(blockPos) && children.contains(blockPos))
				{
					for (int y = minY - 1; y > worldBottomY; y--)
					{
						blockPos.setY(y);
						if (world.isAir(blockPos) || world.getBlockState(blockPos).isReplaceable())
						{
							world.setBlockState(blockPos, blockGetter.apply(random), Block.NOTIFY_ALL);
						}
						else if (y < blockBox.getMinY())
						{
							break;
						}
					}
				}
			}
		}
	}

	protected static void fillBottomAirGapInAutoBox(StructureWorldAccess world, Random random, BlockBox chunkBox, StructurePiecesList children, Collection<Block> baseBlocks, List<Block> fillerBlocks, int yOffset)
	{
		final BlockBox[] bottomBox = new BlockBox[]{null};
		fillBottomAirGap(
				world,
				random,
				chunkBox,
				children,
				(pos, blockState) -> {
					if (bottomBox[0] != null && bottomBox[0].contains(pos))
					{
						return true;
					}
					if (baseBlocks.contains(blockState.getBlock()))
					{
						bottomBox[0] = bottomBox[0] == null ? new BlockBox(pos) : bottomBox[0].encompass(pos);
					}
					return bottomBox[0] != null && bottomBox[0].contains(pos);
				},
				rnd -> fillerBlocks.get(rnd.nextInt(fillerBlocks.size())).getDefaultState(),
				yOffset
		);
	}

	protected abstract static class Piece extends SimpleStructurePiece
	{
		public Piece(StructurePieceType type, StructureTemplateManager manager, Identifier identifier, BlockPos pos, BlockRotation rotation)
		{
			super(type, 0, manager, identifier, identifier.toString(), createPlacementData(rotation), pos);
			this.ensureStructureDataExists();
		}

		public Piece(StructurePieceType type, StructureTemplateManager manager, NbtCompound nbt)
		{
			super(type, nbt, manager, new Function<Identifier, StructurePlacementData>() {
				@Override
				public StructurePlacementData apply(Identifier identifier)
				{
					return createPlacementData(BlockRotation.valueOf(nbt.getString("Rotation")));
				}
			});
			this.ensureStructureDataExists();
		}

		private static StructurePlacementData createPlacementData(BlockRotation rotation)
		{
			return new StructurePlacementData().setRotation(rotation).setMirror(BlockMirror.NONE).setIgnoreEntities(false);
		}

		private void ensureStructureDataExists()
		{
			Vec3i size = this.template.getSize();
			if (size.getX() * size.getY() * size.getZ() == 0)
			{
				UhcGameManager.LOG.error("Empty structure with template {}", this.templateIdString);
			}
		}

		@Override
		protected void writeNbt(StructureContext context, NbtCompound nbt)
		{
			super.writeNbt(context, nbt);
			nbt.putString("Rotation", this.placementData.getRotation().name());
		}

		@Override
		public void generate(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox, ChunkPos chunkPos, BlockPos pivot)
		{
			this.adjustPosByTerrain(world);
			super.generate(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, pivot);
		}

		protected void adjustPosByTerrain(StructureWorldAccess world)
		{
			int sumY = 0;
			int count = 0;
			for (int x = this.boundingBox.getMinX(); x <= this.boundingBox.getMaxX(); x++)
			{
				for (int z = this.boundingBox.getMinZ(); z <= this.boundingBox.getMaxZ(); z++)
				{
					sumY += world.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x, z);
					count++;
				}
			}
			if (count > 0)
			{
				this.pos = new BlockPos(this.pos.getX(), sumY / count, this.pos.getZ());
			}
		}

		protected void setChestLoot(ServerWorldAccess world, BlockPos chestPos, Random random, Identifier lootTableId)
		{
			BlockEntity blockEntity = world.getBlockEntity(chestPos);
			if (blockEntity instanceof ChestBlockEntity)
			{
				((ChestBlockEntity)blockEntity).setLootTable(RegistryKey.of(RegistryKeys.LOOT_TABLE, lootTableId), random.nextLong());
			}
		}

		protected void placeEntity(EntityType<?> entityType, BlockPos pos, ServerWorldAccess world, Random random)
		{
			Entity entity = entityType.create(world.toServerWorld());
			if (entity != null)
			{
				Vec3d vec3d = Vec3d.ofBottomCenter(pos);
				entity.refreshPositionAndAngles(vec3d.x + random.nextFloat() / 10.0F, vec3d.y, vec3d.z + random.nextFloat() / 10.0F, entity.getYaw(), entity.getPitch());
				if (entity instanceof MobEntity)
				{
					MobEntity mobEntity = (MobEntity)entity;
					mobEntity.initialize(world, world.getLocalDifficulty(pos), SpawnReason.STRUCTURE, null);
					mobEntity.setPersistent();
				}
				world.spawnEntity(entity);
			}
		}
	}

	protected abstract static class YOffsetPiece extends Piece
	{
		private final int floorHeight;

		public YOffsetPiece(StructurePieceType type, StructureTemplateManager manager, Identifier identifier, BlockPos pos, BlockRotation rotation, int floorHeight)
		{
			super(type, manager, identifier, pos, rotation);
			this.floorHeight = floorHeight;
		}

		public YOffsetPiece(StructurePieceType type, StructureTemplateManager manager, NbtCompound nbt)
		{
			super(type, manager, nbt);
			this.floorHeight = nbt.getInt("FloorHeight");
		}

		@Override
		protected void writeNbt(StructureContext context, NbtCompound nbt)
		{
			super.writeNbt(context, nbt);
			nbt.putInt("FloorHeight", this.floorHeight);
		}

		@Override
		protected void adjustPosByTerrain(StructureWorldAccess world)
		{
			super.adjustPosByTerrain(world);
			this.pos = this.pos.down(this.floorHeight);
		}
	}
}
