package me.fallenbreath.tcuhc.gen.feature;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import me.fallenbreath.tcuhc.UhcGameManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PaneBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;

import java.util.List;
import java.util.Optional;

public class MerchantsFeature extends Feature<DefaultFeatureConfig>
{
	private static final int LOG_INTERVAL = 4;
	private static int placedCount;
	private static int attemptCount;
	private static final List<UhcRecipe> RANDOM_RECIPES = new ImmutableList.Builder<UhcRecipe>()
			.add(new UhcRecipe(Items.EXPERIENCE_BOTTLE, 2, 4, 1, 1, true))
			.add(new UhcRecipe(Items.NETHER_WART, Items.BLAZE_POWDER, 1, 2, 2, 4, true))
			.add(new UhcRecipe(Items.COAL, 3, 6, 1, 1, false))
			.add(new UhcRecipe(Items.REDSTONE, 3, 6, 1, 1, false))
			.add(new UhcRecipe(Items.IRON_INGOT, 1, 2, 1, 1, false))
			.add(new UhcRecipe(Items.GOLD_INGOT, 1, 1, 1, 2, false))
			.add(new UhcRecipe(Items.ENDER_PEARL, 1, 1, 10, 20, false))
			.add(new UhcRecipe(Items.EMERALD, 1, 1, 3, 6, false))
			.add(new UhcRecipe(Items.DIAMOND, 1, 1, 2, 4, false))
			.add(new UhcRecipe(Items.NETHERITE_SCRAP, 1, 1, 20, 32, false))
			.build();

	private static final List<UhcRecipe> STATIC_RECIPES = new ImmutableList.Builder<UhcRecipe>()
			.add(new UhcRecipe(Items.GOLDEN_APPLE, Items.APPLE, 1, 1, 18, 30, true))
			.add(new UhcRecipe(Items.DIAMOND_CHESTPLATE, Items.IRON_CHESTPLATE, 1, 1, 36, 48, true))
			.add(new UhcRecipe(Items.DIAMOND_LEGGINGS, Items.IRON_LEGGINGS, 1, 1, 30, 42, true))
			.add(new UhcRecipe(Items.DIAMOND_HELMET, Items.IRON_HELMET, 1, 1, 22, 30, true))
			.add(new UhcRecipe(Items.DIAMOND_BOOTS, Items.IRON_BOOTS, 1, 1, 18, 24, true))
			.build();

	public MerchantsFeature(Codec<DefaultFeatureConfig> configCodec)
	{
		super(configCodec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context)
	{
		if (UhcGameManager.instance == null)
		{
			return false;
		}

		StructureWorldAccess world = context.getWorld();
		BlockPos origin = context.getOrigin();
		int chunkX = origin.getX() >> 4;
		int chunkZ = origin.getZ() >> 4;
		if (Math.abs(chunkX) < 2 || Math.abs(chunkZ) < 2)
		{
			return false;
		}

		float merchantChance = UhcGameManager.instance.getOptions().getFloatOptionValue("merchantFrequency");
		Random random = context.getRandom();
		if (merchantChance <= 0.0F || chunkX % 4 != 0 || chunkZ % 4 != 0 || random.nextFloat() >= 0.3F * merchantChance)
		{
			return false;
		}

		BlockPos floorPos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, origin).down();
		if (floorPos.getY() <= world.getBottomY() || !world.getFluidState(floorPos).isEmpty())
		{
			return false;
		}

		buildMerchantBooth(world, floorPos);
		VillagerEntity villager = createMerchant(world, floorPos, random);
		attemptCount++;
		if (!world.spawnEntity(villager))
		{
			if (attemptCount % LOG_INTERVAL == 0)
			{
				UhcGameManager.LOG.warn("Merchant feature attempted {} placements but spawnEntity rejected the latest at {}", attemptCount, floorPos);
			}
			return false;
		}
		placedCount++;
		if (placedCount % LOG_INTERVAL == 0)
		{
			UhcGameManager.LOG.info("Merchant feature placed {} merchants so far; latest at {}", placedCount, floorPos);
		}
		return true;
	}

	private static void buildMerchantBooth(StructureWorldAccess world, BlockPos floorPos)
	{
		for (int x = floorPos.getX() - 1; x <= floorPos.getX() + 1; x++)
		{
			for (int z = floorPos.getZ() - 1; z <= floorPos.getZ() + 1; z++)
			{
				world.setBlockState(new BlockPos(x, floorPos.getY(), z), Blocks.STONE_BRICKS.getDefaultState(), 3);
				world.setBlockState(new BlockPos(x, floorPos.getY() + 3, z), Blocks.STONE_BRICKS.getDefaultState(), 3);
			}
		}

		BlockPos barCenter = floorPos.up();
		for (Direction direction : Direction.Type.HORIZONTAL)
		{
			Direction cornerDirection = direction.rotateYClockwise();
			world.setBlockState(barCenter.offset(direction), createBoothBarState(direction, direction.rotateYClockwise(), direction.rotateYCounterclockwise()), 3);
			world.setBlockState(barCenter.offset(direction).offset(cornerDirection), createBoothBarState(cornerDirection, cornerDirection.rotateYClockwise(), direction.rotateYCounterclockwise()), 3);
		}
		world.setBlockState(floorPos.up(4), Blocks.SMOOTH_STONE_SLAB.getDefaultState(), 3);
	}

	private static BlockState createBoothBarState(Direction primaryDirection, Direction connectionA, Direction connectionB)
	{
		// Worldgen placement can skip neighbor callbacks, so write the final pane connections directly.
		BlockState state = Blocks.IRON_BARS.getDefaultState()
				.with(PaneBlock.NORTH, false)
				.with(PaneBlock.SOUTH, false)
				.with(PaneBlock.EAST, false)
				.with(PaneBlock.WEST, false);
		state = setPaneConnection(state, primaryDirection, false);
		state = setPaneConnection(state, connectionA, true);
		return setPaneConnection(state, connectionB, true);
	}

	private static BlockState setPaneConnection(BlockState state, Direction direction, boolean connected)
	{
		switch (direction)
		{
			case NORTH:
				return state.with(PaneBlock.NORTH, connected);
			case SOUTH:
				return state.with(PaneBlock.SOUTH, connected);
			case EAST:
				return state.with(PaneBlock.EAST, connected);
			case WEST:
				return state.with(PaneBlock.WEST, connected);
			default:
				return state;
		}
	}

	private static VillagerEntity createMerchant(StructureWorldAccess world, BlockPos floorPos, Random random)
	{
		VillagerEntity villager = new VillagerEntity(EntityType.VILLAGER, world.toServerWorld());
		villager.setAiDisabled(true);
		villager.setInvulnerable(true);
		villager.refreshPositionAndAngles(floorPos.getX() + 0.5D, floorPos.getY() + 1.1D, floorPos.getZ() + 0.5D, 0.0F, 0.0F);

		TradeOfferList offers = new TradeOfferList();
		int recipeCount = random.nextInt(3) + 2;
		for (int i = 0; i < recipeCount; i++)
		{
			offers.add(RANDOM_RECIPES.get(random.nextInt(RANDOM_RECIPES.size())).create(random));
		}
		for (UhcRecipe recipe : STATIC_RECIPES)
		{
			offers.add(recipe.create(random));
		}
		villager.setOffers(offers);
		return villager;
	}

	private static class UhcRecipe
	{
		private final Item primary;
		private final Item secondary;
		private final int itemMin;
		private final int itemMax;
		private final int quartzMin;
		private final int quartzMax;
		private final boolean sell;

		private UhcRecipe(Item primary, Item secondary, int itemMin, int itemMax, int quartzMin, int quartzMax, boolean sell)
		{
			this.primary = primary;
			this.secondary = secondary;
			this.itemMin = itemMin;
			this.itemMax = itemMax;
			this.quartzMin = quartzMin;
			this.quartzMax = quartzMax;
			this.sell = sell;
		}

		private UhcRecipe(Item primary, int itemMin, int itemMax, int quartzMin, int quartzMax, boolean sell)
		{
			this(primary, null, itemMin, itemMax, quartzMin, quartzMax, sell);
		}

		private TradeOffer create(Random random)
		{
			ItemStack tradedItem = createStack(this.primary, this.itemMin, this.itemMax, random);
			ItemStack quartz = createStack(Items.QUARTZ, this.quartzMin, this.quartzMax, random);
			if (this.sell)
			{
				Optional<TradedItem> secondaryItem = this.secondary == null ? Optional.empty() : Optional.of(new TradedItem(this.secondary));
				return new TradeOffer(new TradedItem(Items.QUARTZ, quartz.getCount()), secondaryItem, tradedItem, 10000, 0, 1.0F);
			}
			return new TradeOffer(new TradedItem(this.primary, tradedItem.getCount()), quartz, 10000, 0, 1.0F);
		}

		private static ItemStack createStack(Item item, int min, int max, Random random)
		{
			return new ItemStack(item, min == max ? max : random.nextInt(max - min + 1) + min);
		}
	}
}
