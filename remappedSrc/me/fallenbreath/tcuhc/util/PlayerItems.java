/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc.util;

import com.google.common.collect.Maps;
import me.fallenbreath.tcuhc.UhcGameManager;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

public class PlayerItems
{
	private static final Map<String, ItemStack> items = Maps.newLinkedHashMap();
	private static final ItemStack DEFAULT_MORAL = new ItemStack(Items.PAPER);

	public static Collection<String> getAvailableNames()
	{
		return items.keySet();
	}

	private static void setSingleLore(ItemStack itemStack, String lore)
	{
		String jsonText = Text.Serializer.toJson(new LiteralText(lore));
		NbtList loreList = new NbtList();
		loreList.add(NbtString.of(jsonText));
		itemStack.getOrCreateSubNbt("display").put("Lore", loreList);
	}

	public static ItemStack getPlayerItem(String playerName, boolean onFire)
	{
		ItemStack stack = items.getOrDefault(playerName, DEFAULT_MORAL).copy();
		// drop smelted item if possible when the player is on fire
		if (onFire)
		{
			World world = UhcGameManager.instance.getOverWorld();
			Inventory inventory = new SimpleInventory(1);
			inventory.setStack(0, stack);
			Recipe<?> recipe = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, inventory, world).orElse(null);
			if (recipe != null)
			{
				ItemStack smeltResult = recipe.getOutput().copy();
				if (stack.hasCustomName())
				{
					smeltResult.setCustomName(stack.getName());
				}
				stack = smeltResult;
			}
		}
		// apply name
		String moralDescription = playerName + "'s moral(jie) integrity(cao)";
		if (!stack.hasCustomName())
		{
			stack.setCustomName(new LiteralText(moralDescription));
		}
		else
		{
			setSingleLore(stack, moralDescription);
		}
		// apply sharpness I if no enchantment
		if (stack.getEnchantments().size() == 0)
		{
			stack.addEnchantment(Enchantments.SHARPNESS, 1);
		}
		// added a special nbt to the item for identifying
		stack.getOrCreateNbt().putString("MoralOwner", playerName);
		return stack;
	}

	@Nullable
	public static String getMoralOwner(ItemStack itemStack) {
		NbtCompound nbt = itemStack.getNbt();
		if (nbt != null) {
			return nbt.getString("MoralOwner");
		}
		return null;
	}

	public static boolean isMoralItem(ItemStack itemStack) {
		return getMoralOwner(itemStack) != null;
	}

	public static ItemStack getPlayerItem(String playerName)
	{
		return getPlayerItem(playerName, false);
	}

	// for command /uhc givemorals [<targetName>]
	public static void dumpMoralsToPlayer(PlayerEntity player, String targetName)
	{
		if (targetName == null)
		{
			items.keySet().forEach(name -> player.getInventory().insertStack(getPlayerItem(name)));
		}
		else
			{
			player.getInventory().insertStack(getPlayerItem(targetName));
		}
	}

	static
	{
		items.put("hungryartist_", Builder.create(Items.POTION).potion(Potions.STRONG_POISON).named("hungryartist_'s holy water").get());
		items.put("_Flag_E_", Builder.create(Blocks.POPPY).get());
		items.put("Spring0809", Builder.create(Blocks.TNT).get());
		items.put("fire_duang_duang", Builder.create(Items.COOKED_COD).named("fire_duang_duang's salted fish").get());
		items.put("Keviince", Builder.create(Items.WOODEN_SWORD).enchant(Enchantments.SHARPNESS, 10).expensive().get());
		items.put("Dazo66", Builder.create(Blocks.DANDELION).get());
		items.put("Gamepiaynmo", Builder.create(Items.COAL).get());
		items.put("CCS_Covenant", Builder.create(Items.COOKIE).named("Crispy Crispy Shark!").get());
		items.put("Lancet_Corgi", Builder.create(Items.IRON_SWORD).named("Lancet").enchant(Enchantments.SHARPNESS, 5).expensive().get());
		items.put("ajisai_iii", Builder.create(Items.CHICKEN).named("rua aji").get());  // rua!
		items.put("Aschin", Builder.create(Items.WHEAT).named("comymy").get());
		items.put("Dou_Bi_Long", Builder.create(Blocks.DRAGON_EGG).named("longbao no egg").enchant(Enchantments.UNBREAKING, 10).enchant(Enchantments.MENDING, 1).get());
		items.put("minamotosan", Builder.create(Items.ROTTEN_FLESH).named("spicy strip").get());
		items.put("zi_nv", Builder.create(Blocks.TALL_GRASS).named("mY lIVe").enchant(Enchantments.EFFICIENCY, 11).get());
		items.put("CallMeLecten", Builder.create(Items.GLASS_BOTTLE).named("oxygen").get());
		items.put("HG_Fei", Builder.create(Items.POTION).potion(Potions.WATER).named("hydrofluoric acid").get());
		items.put("hai_dan", Builder.create(Items.TURTLE_EGG).named("sea egg").get());
		items.put("Fallen_Breath", Builder.create(Items.LEATHER_CHESTPLATE).mani(s -> ((DyeableItem)Items.LEATHER_CHESTPLATE).setColor(s, 16742436)).named("fox fur coat").get());
		items.put("Sanluli36li", Builder.create(Items.TNT_MINECART).named("36li's self-destruct-car").enchant(Enchantments.FORTUNE, 3).get());
		items.put("shamreltuim", Builder.create(Items.PUFFERFISH).enchant(Enchantments.BINDING_CURSE, 1).get());
		items.put("YtonE", Builder.create(Items.POTION).potion(Potions.EMPTY).named("liquid ketone").get());
		items.put("DawNemo", Builder.create(Items.SPYGLASS).named("Real Man Never Look Round").enchant(Enchantments.THORNS, 1).get());
		items.put("Van_Nya", Builder.create(Items.RABBIT_STEW).named("Van Nya Stew").get());  // Nya? Nya!
		items.put("youngdao", Builder.create(Items.STONE_SWORD).named("Murasame").enchant(Enchantments.SHARPNESS, 10).expensive().get());
		items.put("ql_Lwi", Builder.create(Items.COD).named("Dinner in the belly of a penguin").get());
		items.put("Azulene0907", Builder.create(Items.SPLASH_POTION).potion(Potions.EMPTY).named("ArBQ").get());  // uncraftable potion
		items.put("LUZaLID", Builder.create(Items.CYAN_DYE).named("cyanLu").get());
		items.put("U_ruby", Builder.create(Items.FEATHER).named("double u").get());
		items.put("Do1phin_jump", Builder.create(Items.TROPICAL_FISH).named("do1phin's food").enchant(Enchantments.UNBREAKING, 3).get());
		items.put("kuritsirolf", Builder.create(Items.CAKE).named("XiangSuLiRon cake").enchant(Enchantments.LUCK_OF_THE_SEA, 1).get());
		items.put("acaciachan", Builder.create(Items.ACACIA_SAPLING).named("si~ha~si~ha~").enchant(Enchantments.LOYALTY, 3).enchant(Enchantments.RIPTIDE, 3).get());
		items.put("Lei_Feng_", Builder.create(Items.GUNPOWDER).get());
		items.put("Ra1ny_Yuki", Builder.create(Items.SNOW).get());
		items.put("hsds", Builder.create(Items.END_CRYSTAL).get());
		items.put("ayjinyt", Builder.create(Items.FLOWERING_AZALEA_LEAVES).named("yt's_Cherry_leaves").enchant(Enchantments.CHANNELING, 1).get());
		items.put("north_82", Builder.create(Items.SHULKER_SHELL).named("\"North's\" Shark shel").enchant(Enchantments.SHARPNESS, 1).enchant(Enchantments.UNBREAKING, 3).get());
		items.put("Runaway_Fancy", Builder.create(Items.SPYGLASS).named("Let me See your dick").enchant(Enchantments.THORNS, 1).get());
		items.put("zhihan233",Builder.create(Items.PINK_DYE).named("Pink Dye").enchant(Enchantments.BINDING_CURSE, 1).get());
		items.put("LINHUA_24k", Builder.create(Items.GOLD_INGOT).named("2 4 K purity gold").enchant(Enchantments.KNOCKBACK, 2).get());
		items.put("Xiang_Q1u", Builder.create(Items.WATER_BUCKET).named("TURBULENCE").enchant(Enchantments.VANISHING_CURSE, 1).get());
		items.put("M0n3tr", Builder.create(Items.GLASS_BOTTLE).named("Ass S****r").enchant(Enchantments.SMITE, 1).get());
		items.put("WEIKAN", Builder.create(Items.POWDER_SNOW_BUCKET).named("来自八个克劳德的祝福").enchant(Enchantments.THORNS, 1).get());
		items.put("Tou_Beichuan", Builder.create(Items.GOLD_NUGGET).named("Let me See your dick").enchant(Enchantments.KNOCKBACK, 2).get());
		items.put("kaniol", Builder.create(Items.ELYTRA).named("Fly Fly ~").enchant(Enchantments.UNBREAKING, 3).get());
		items.put("Qungrn", Builder.create(Items.FIRE_CHARGE).named("然然...嘿嘿然然...").enchant(Enchantments.LOYALTY, 2).get());
		items.put("REMS_Eula", Builder.create(Items.DIAMOND).named("DI(x) Tu Hao").enchant(Enchantments.FORTUNE, 3).get());
		items.put("yue_szk", Builder.create(Items.AMETHYST_SHARD).named("s z k").enchant(Enchantments.MENDING, 1).get());
		items.put("xiao_6", Builder.create(Items.WARPED_FUNGUS_ON_A_STICK).named("6 6 6").enchant(Enchantments.UNBREAKING, 3).get());
		items.put("liangxi__", Builder.create(Items.SUGAR).named("嘉心糖").enchant(Enchantments.LOYALTY, 2).get());
	}

	private static class Builder
	{
		private final ItemStack itemStack;

		private Builder(ItemStack itemStack)
		{
			if (itemStack.isDamageable() && !(itemStack.getItem() instanceof ArmorItem))
			{
				itemStack.setDamage(itemStack.getMaxDamage());
			}
			this.itemStack = itemStack;
		}

		private static Builder create(Item item)
		{
			return new Builder(new ItemStack(item));
		}

		public static Builder create(ItemConvertible itemConvertible)
		{
			return create(itemConvertible.asItem());
		}

		private Builder mani(Consumer<ItemStack> consumer)
		{
			consumer.accept(this.itemStack);
			return this;
		}

		private Builder named(String name)
		{
			return this.mani(s -> s.setCustomName(new LiteralText(name)));
		}

		private Builder potion(Potion potionType)
		{
			return this.mani(s -> PotionUtil.setPotion(s, potionType));
		}

		private Builder enchant(Enchantment ench, int level)
		{
			return this.mani(s -> s.addEnchantment(ench, level));
		}

		private Builder expensive()
		{
			return this.mani(stack -> stack.setRepairCost(100));
		}

		private ItemStack get()
		{
			return this.itemStack;
		}
	}
}
