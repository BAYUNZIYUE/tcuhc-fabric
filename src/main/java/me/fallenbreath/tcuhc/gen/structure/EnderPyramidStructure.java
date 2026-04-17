package me.fallenbreath.tcuhc.gen.structure;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import me.fallenbreath.tcuhc.TcUhcMod;
import me.fallenbreath.tcuhc.util.UhcRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
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

public class EnderPyramidStructure extends SinglePieceLandStructure
{
	public static final MapCodec<EnderPyramidStructure> CODEC = createCodec(EnderPyramidStructure::new);
	private static final StructureType<EnderPyramidStructure> TYPE = UhcRegistry.registerStructureType("ender_pyramid", CODEC);
	private static final StructurePieceType PIECE_TYPE = UhcRegistry.registerStructurePieceType("ender_pyramid_piece", new StructurePieceType.ManagerAware() {
		@Override
		public net.minecraft.structure.StructurePiece load(StructureTemplateManager structureTemplateManager, NbtCompound nbtCompound)
		{
			return new Piece(structureTemplateManager, nbtCompound);
		}
	});

	private static final Identifier MAIN_TEMPLATE = TcUhcMod.id("ender_pyramid/main");
	private static final Identifier CHEST_LOOT_TABLE = TcUhcMod.id("ender_pyramid/chest");
	private static final List<Block> BASE_BLOCKS = ImmutableList.of(
			Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
			Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.INFESTED_COBBLESTONE, Blocks.MOSSY_COBBLESTONE
	);

	public EnderPyramidStructure(Config config)
	{
		super(config);
	}

	@Override
	protected boolean canGenerate(Context context)
	{
		return isBiomeValid(context, context.chunkPos().getStartPos());
	}

	@Override
	protected void addPieces(StructurePiecesCollector collector, Context context)
	{
		BlockRotation rotation = BlockRotation.random(context.random());
		collector.addPiece(new Piece(context.structureTemplateManager(), MAIN_TEMPLATE, shiftStartPosRandomly(context), rotation));
	}

	@Override
	public void postPlace(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox, ChunkPos chunkPos, StructurePiecesList children)
	{
		super.postPlace(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, children);
		fillBottomAirGap(world, random, chunkBox, children, (pos, blockState) -> BASE_BLOCKS.contains(blockState.getBlock()), rnd -> BASE_BLOCKS.get(random.nextInt(BASE_BLOCKS.size())).getDefaultState(), 0);
	}

	@Override
	public StructureType<?> getType()
	{
		return TYPE;
	}

	private static class Piece extends SinglePieceLandStructure.Piece
	{
		public Piece(StructureTemplateManager manager, Identifier identifier, BlockPos pos, BlockRotation rotation)
		{
			super(PIECE_TYPE, manager, identifier, pos, rotation);
		}

		public Piece(StructureTemplateManager manager, NbtCompound nbt)
		{
			super(PIECE_TYPE, manager, nbt);
		}

		@Override
		protected void handleMetadata(String metadata, BlockPos pos, ServerWorldAccess world, Random random, BlockBox boundingBox)
		{
			if ("chest".equals(metadata))
			{
				this.clearMetadataMarker(world, pos);
				this.setChestLoot(world, pos.up(), random, CHEST_LOOT_TABLE);
			}
		}
	}
}
