package me.fallenbreath.tcuhc.gen.structure;

import com.mojang.serialization.MapCodec;
import me.fallenbreath.tcuhc.TcUhcMod;
import me.fallenbreath.tcuhc.util.UhcRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
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

public class PlainCottageStructure extends SinglePieceLandStructure
{
	public static final MapCodec<PlainCottageStructure> CODEC = createCodec(PlainCottageStructure::new);
	private static final StructureType<PlainCottageStructure> TYPE = UhcRegistry.registerStructureType("plain_cottage", CODEC);
	private static final StructurePieceType PIECE_TYPE = UhcRegistry.registerStructurePieceType("plain_cottage_piece", new StructurePieceType.ManagerAware() {
		@Override
		public net.minecraft.structure.StructurePiece load(StructureTemplateManager structureTemplateManager, NbtCompound nbtCompound)
		{
			return new Piece(structureTemplateManager, nbtCompound);
		}
	});

	private static final Identifier MAIN_TEMPLATE = TcUhcMod.id("plain_cottage/main");
	private static final Identifier CHEST_LOOT_TABLE = TcUhcMod.id("plain_cottage/chest");
	private static final int FLOOR_HEIGHT = 1;
	private static final int CHEST_AMOUNT = 3;

	public PlainCottageStructure(Config config)
	{
		super(config);
	}

	@Override
	protected boolean canGenerate(Context context)
	{
		return isBiomeValid(context, context.chunkPos().getStartPos()) && isBiomeValidInChunk(context) && isSurroundingFlat(context, Heightmap.Type.WORLD_SURFACE_WG, 7, 3);
	}

	@Override
	protected void addPieces(StructurePiecesCollector collector, Context context)
	{
		collector.addPiece(new Piece(context.structureTemplateManager(), MAIN_TEMPLATE, shiftStartPosRandomly(context), BlockRotation.random(context.random()), context.random().nextInt(CHEST_AMOUNT) + 1));
	}

	@Override
	public void postPlace(StructureWorldAccess world, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random, BlockBox chunkBox, ChunkPos chunkPos, StructurePiecesList children)
	{
		super.postPlace(world, structureAccessor, chunkGenerator, random, chunkBox, chunkPos, children);
		fillBottomAirGap(world, random, chunkBox, children, (pos, state) -> !state.isAir(), rnd -> Blocks.DIRT.getDefaultState(), FLOOR_HEIGHT);
	}

	@Override
	public StructureType<?> getType()
	{
		return TYPE;
	}

	private static class Piece extends SinglePieceLandStructure.YOffsetPiece
	{
		private final int bonusChestIndex;
		private int chestCounter = 0;

		public Piece(StructureTemplateManager manager, Identifier identifier, BlockPos pos, BlockRotation rotation, int bonusChestIndex)
		{
			super(PIECE_TYPE, manager, identifier, pos, rotation, FLOOR_HEIGHT);
			this.bonusChestIndex = bonusChestIndex;
		}

		public Piece(StructureTemplateManager manager, NbtCompound nbt)
		{
			super(PIECE_TYPE, manager, nbt);
			this.bonusChestIndex = nbt.getInt("BonusChestIndex");
		}

		@Override
		protected void writeNbt(StructureContext context, NbtCompound nbt)
		{
			super.writeNbt(context, nbt);
			nbt.putInt("BonusChestIndex", this.bonusChestIndex);
		}

		@Override
		protected void handleMetadata(String metadata, BlockPos pos, ServerWorldAccess world, Random random, BlockBox boundingBox)
		{
			EntityType<?> entityType = null;
			int amount = 1;
			switch (metadata)
			{
				case "horse":
					entityType = random.nextInt(2) == 0 ? EntityType.HORSE : EntityType.DONKEY;
					break;
				case "chicken":
					entityType = EntityType.CHICKEN;
					amount = random.nextInt(2) + 3;
					break;
				case "chest":
					this.chestCounter++;
					this.clearMetadataMarker(world, pos);
					if (this.chestCounter == this.bonusChestIndex)
					{
						this.setChestLoot(world, pos.down(), random, CHEST_LOOT_TABLE);
					}
					break;
			}
			if (entityType != null)
			{
				this.clearMetadataMarker(world, pos);
				for (int i = 0; i < amount; i++)
				{
					this.placeEntity(entityType, pos.down(), world, random);
				}
			}
		}
	}
}
