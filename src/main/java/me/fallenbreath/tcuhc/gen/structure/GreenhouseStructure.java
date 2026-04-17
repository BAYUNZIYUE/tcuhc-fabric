package me.fallenbreath.tcuhc.gen.structure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import me.fallenbreath.tcuhc.TcUhcMod;
import me.fallenbreath.tcuhc.util.UhcRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructurePiecesList;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.StructureType;

import java.util.List;
import java.util.Map;

public class GreenhouseStructure extends SinglePieceLandStructure
{
	private static final String SNOW = "snow";
	private static final String DESERT = "desert";

	public static final MapCodec<GreenhouseStructure> SNOW_CODEC = createCodec(config -> new GreenhouseStructure(config, SNOW));
	public static final MapCodec<GreenhouseStructure> DESERT_CODEC = createCodec(config -> new GreenhouseStructure(config, DESERT));
	private static final StructureType<GreenhouseStructure> SNOW_TYPE = UhcRegistry.registerStructureType("greenhouse_snow", SNOW_CODEC);
	private static final StructureType<GreenhouseStructure> DESERT_TYPE = UhcRegistry.registerStructureType("greenhouse_desert", DESERT_CODEC);
	private static final StructurePieceType SNOW_PIECE_TYPE = UhcRegistry.registerStructurePieceType("greenhouse_piece_" + SNOW, new StructurePieceType.ManagerAware() {
		@Override
		public StructurePiece load(StructureTemplateManager structureTemplateManager, NbtCompound nbtCompound)
		{
			return new Piece(structureTemplateManager, nbtCompound);
		}
	});
	private static final StructurePieceType DESERT_PIECE_TYPE = UhcRegistry.registerStructurePieceType("greenhouse_piece_" + DESERT, new StructurePieceType.ManagerAware() {
		@Override
		public StructurePiece load(StructureTemplateManager structureTemplateManager, NbtCompound nbtCompound)
		{
			return new Piece(structureTemplateManager, nbtCompound);
		}
	});

	private static final List<Block> MUSHROOMS = ImmutableList.of(Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM);
	private static final List<Block> FLOWERS = ImmutableList.of(
			Blocks.DANDELION,
			Blocks.POPPY,
			Blocks.BLUE_ORCHID,
			Blocks.ALLIUM,
			Blocks.AZURE_BLUET,
			Blocks.ORANGE_TULIP,
			Blocks.WHITE_TULIP,
			Blocks.PINK_TULIP,
			Blocks.CORNFLOWER,
			Blocks.LILY_OF_THE_VALLEY,
			Blocks.BIG_DRIPLEAF,
			Blocks.AZALEA,
			Blocks.SHORT_GRASS,
			Blocks.OXEYE_DAISY,
			Blocks.OXEYE_DAISY,
			Blocks.OXEYE_DAISY,
			Blocks.OXEYE_DAISY,
			Blocks.OXEYE_DAISY,
			Blocks.OXEYE_DAISY,
			Blocks.OXEYE_DAISY,
			Blocks.OXEYE_DAISY,
			Blocks.OXEYE_DAISY,
			Blocks.OXEYE_DAISY
	);
	private static final Map<String, StructureType<GreenhouseStructure>> TYPES = ImmutableMap.of(SNOW, SNOW_TYPE, DESERT, DESERT_TYPE);
	private static final Map<String, StructurePieceType> PIECE_TYPES = ImmutableMap.of(SNOW, SNOW_PIECE_TYPE, DESERT, DESERT_PIECE_TYPE);
	private static final Map<String, Identifier> STRUCTURE_IDS = ImmutableMap.of(SNOW, TcUhcMod.id("greenhouse/snow"), DESERT, TcUhcMod.id("greenhouse/desert"));
	private static final Map<String, Identifier> CHEST_LOOT_TABLES = ImmutableMap.of(SNOW, TcUhcMod.id("greenhouse/chest_" + SNOW), DESERT, TcUhcMod.id("greenhouse/chest_" + DESERT));
	private static final Map<String, Block> GROUND_FILLER_BLOCK = ImmutableMap.of(SNOW, Blocks.DEEPSLATE_BRICKS, DESERT, Blocks.SANDSTONE);
	private static final Map<String, Integer> FLOOR_HEIGHT = ImmutableMap.of(SNOW, 1, DESERT, 3);

	private final String greenhouseType;

	private GreenhouseStructure(Config config, String greenhouseType)
	{
		super(config);
		this.greenhouseType = greenhouseType;
	}

	@Override
	protected boolean canGenerate(Context context)
	{
		return isBiomeValidInChunk(context);
	}

	@Override
	protected void addPieces(StructurePiecesCollector collector, Context context)
	{
		collector.addPiece(new Piece(context.structureTemplateManager(), shiftStartPosRandomly(context), this.greenhouseType, context.random()));
	}

	@Override
	public void postPlace(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox, ChunkPos chunkPos, StructurePiecesList children)
	{
		super.postPlace(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, children);
		List<StructurePiece> pieces = children.pieces();
		if (!pieces.isEmpty() && pieces.get(0) instanceof Piece)
		{
			Piece piece = (Piece)pieces.get(0);
			BlockState filler = GROUND_FILLER_BLOCK.get(piece.getGreenhouseType()).getDefaultState();
			fillBottomAirGap(world, random, chunkBox, children, (pos, blockState) -> !blockState.isAir(), rnd -> filler, FLOOR_HEIGHT.get(piece.getGreenhouseType()));
		}
	}

	@Override
	public StructureType<?> getType()
	{
		return TYPES.get(this.greenhouseType);
	}

	private static StructurePieceType getPieceType(String type)
	{
		StructurePieceType pieceType = PIECE_TYPES.get(type);
		if (pieceType == null)
		{
			throw new IllegalArgumentException(type);
		}
		return pieceType;
	}

	private static Identifier getStructureId(String type)
	{
		Identifier identifier = STRUCTURE_IDS.get(type);
		if (identifier == null)
		{
			throw new IllegalArgumentException(type);
		}
		return identifier;
	}

	private static class Piece extends SinglePieceLandStructure.Piece
	{
		private final String type;
		private final Block plant1;
		private final Block plant2;
		private final BlockState dirt1;
		private final BlockState dirt2;

		public Piece(StructureTemplateManager manager, BlockPos pos, String type, Random random)
		{
			super(getPieceType(type), manager, getStructureId(type), pos, BlockRotation.random(random));
			this.type = type;
			this.plant1 = getRandomPlant(random);
			this.plant2 = getRandomPlant(random);
			this.dirt1 = getDirtFromPlant(this.plant1);
			this.dirt2 = getDirtFromPlant(this.plant2);
		}

		public Piece(StructureTemplateManager manager, NbtCompound nbt)
		{
			super(getPieceType(nbt.getString("GreenhouseType")), manager, nbt);
			this.type = nbt.getString("GreenhouseType");
			this.plant1 = Registries.BLOCK.get(Identifier.of(nbt.getString("Plant1")));
			this.plant2 = Registries.BLOCK.get(Identifier.of(nbt.getString("Plant2")));
			this.dirt1 = getDirtFromPlant(this.plant1);
			this.dirt2 = getDirtFromPlant(this.plant2);
		}

		public String getGreenhouseType()
		{
			return this.type;
		}

		@Override
		protected void writeNbt(StructureContext context, NbtCompound nbt)
		{
			super.writeNbt(context, nbt);
			nbt.putString("GreenhouseType", this.type);
			nbt.putString("Plant1", Registries.BLOCK.getId(this.plant1).toString());
			nbt.putString("Plant2", Registries.BLOCK.getId(this.plant2).toString());
		}

		@Override
		protected void handleMetadata(String metadata, BlockPos pos, ServerWorldAccess world, Random random, BlockBox boundingBox)
		{
			BlockState plantState = null;
			BlockState dirtState = null;
			switch (metadata)
			{
				case "plant1":
					plantState = getPlant(this.plant1, random);
					dirtState = this.dirt1;
					break;
				case "plant2":
					plantState = getPlant(this.plant2, random);
					dirtState = this.dirt2;
					break;
				case "chest":
					this.clearMetadataMarker(world, pos);
					this.setChestLoot(world, pos.down(), random, CHEST_LOOT_TABLES.get(this.type));
					return;
			}
			if (plantState != null && dirtState != null)
			{
				// Keep greenhouse marker replacement side-effect free during structure placement.
				world.setBlockState(pos, dirtState, Block.NOTIFY_LISTENERS);
				if (random.nextFloat() < 0.3F)
				{
					world.setBlockState(pos.up(), plantState, Block.NOTIFY_LISTENERS);
				}
			}
		}

		@Override
		protected void adjustPosByTerrain(StructureWorldAccess world)
		{
			super.adjustPosByTerrain(world);
			this.pos = this.pos.down(FLOOR_HEIGHT.getOrDefault(this.type, 0));
		}

		private static Block getRandomPlant(Random random)
		{
			List<Block> pool = random.nextFloat() < 0.4F ? MUSHROOMS : FLOWERS;
			return Util.getRandom(pool, random);
		}

		private static BlockState getDirtFromPlant(Block plant)
		{
			return MUSHROOMS.contains(plant) ? Blocks.MYCELIUM.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
		}

		private static BlockState getPlant(Block plant, Random random)
		{
			if (plant == Blocks.AZALEA && random.nextFloat() < 0.2F)
			{
				plant = Blocks.FLOWERING_AZALEA;
			}
			return plant.getDefaultState();
		}
	}
}
