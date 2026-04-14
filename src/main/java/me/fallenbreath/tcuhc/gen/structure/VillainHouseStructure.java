package me.fallenbreath.tcuhc.gen.structure;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import me.fallenbreath.tcuhc.TcUhcMod;
import me.fallenbreath.tcuhc.util.UhcRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.StructureType;

import java.util.List;

public class VillainHouseStructure extends SinglePieceLandStructure
{
	public static final MapCodec<VillainHouseStructure> CODEC = createCodec(VillainHouseStructure::new);
	private static final StructureType<VillainHouseStructure> TYPE = UhcRegistry.registerStructureType("villain_house", CODEC);
	private static final StructurePieceType PIECE_TYPE = UhcRegistry.registerStructurePieceType("villain_house_piece", new StructurePieceType.ManagerAware() {
		@Override
		public net.minecraft.structure.StructurePiece load(StructureTemplateManager structureTemplateManager, NbtCompound nbtCompound)
		{
			return new Piece(structureTemplateManager, nbtCompound);
		}
	});

	private static final Identifier MAIN_TEMPLATE = TcUhcMod.id("villain_house/main");
	private static final Identifier CHEST_LOOT_TABLE = TcUhcMod.id("villain_house/chest");
	private static final List<Block> BASE_BLOCKS = ImmutableList.of(Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE, Blocks.ANDESITE);
	private static final List<EntityType<?>> VILLAINS = ImmutableList.of(EntityType.WITCH, EntityType.VINDICATOR, EntityType.PILLAGER);
	private static final int FLOOR_HEIGHT = 1;

	public VillainHouseStructure(Config config)
	{
		super(config);
	}

	@Override
	protected boolean canGenerate(Context context)
	{
		return isBiomeValid(context, context.chunkPos().getStartPos()) && isBiomeValidInChunk(context) && isSurroundingFlat(context, Heightmap.Type.WORLD_SURFACE_WG, 5, 3);
	}

	@Override
	protected void addPieces(StructurePiecesCollector collector, Context context)
	{
		collector.addPiece(new Piece(context.structureTemplateManager(), MAIN_TEMPLATE, shiftStartPosRandomly(context), BlockRotation.random(context.random())));
	}

	@Override
	public void postPlace(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox, ChunkPos chunkPos, StructurePiecesList children)
	{
		super.postPlace(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, children);
		fillBottomAirGapInAutoBox(world, random, chunkBox, children, BASE_BLOCKS, BASE_BLOCKS, FLOOR_HEIGHT);
	}

	@Override
	public StructureType<?> getType()
	{
		return TYPE;
	}

	private static class Piece extends SinglePieceLandStructure.YOffsetPiece
	{
		public Piece(StructureTemplateManager manager, Identifier identifier, BlockPos pos, BlockRotation rotation)
		{
			super(PIECE_TYPE, manager, identifier, pos, rotation, FLOOR_HEIGHT);
		}

		public Piece(StructureTemplateManager manager, NbtCompound nbt)
		{
			super(PIECE_TYPE, manager, nbt);
		}

		@Override
		protected void handleMetadata(String metadata, BlockPos pos, ServerWorldAccess world, Random random, BlockBox boundingBox)
		{
			switch (metadata)
			{
				case "villain":
					world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
					for (int i = 0; i < 2; i++)
					{
						this.placeEntity(VILLAINS.get(random.nextInt(VILLAINS.size())), pos, world, random);
					}
					break;
				case "chest":
					world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
					BlockPos chestPos = pos.down();
					BlockState chestBlock = world.getBlockState(chestPos);
					if (random.nextInt(2) == 0)
					{
						if (chestBlock.isOf(Blocks.CHEST))
						{
							world.toServerWorld().removeBlockEntity(chestPos);
							world.setBlockState(chestPos, Blocks.ENDER_CHEST.getDefaultState().with(EnderChestBlock.FACING, chestBlock.get(ChestBlock.FACING)), Block.NOTIFY_ALL);
						}
					}
					else
					{
						this.setChestLoot(world, chestPos, random, CHEST_LOOT_TABLE);
					}
					break;
			}
		}
	}
}
