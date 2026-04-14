package me.fallenbreath.tcuhc.util;

import com.google.common.collect.Maps;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class PlayerItems
{
	private static final Map<String, ItemStack> items = Maps.newLinkedHashMap();
	private static final ItemStack DEFAULT_MORAL = new ItemStack(Items.PAPER);

	public static Collection<String> getAvailableNames()
	{
		return items.keySet();
	}

	public static ItemStack getPlayerItem(String playerName, boolean onFire)
	{
		ItemStack stack = items.getOrDefault(playerName, DEFAULT_MORAL).copy();
		String moralDescription = playerName + "'s moral";
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(moralDescription));
		NbtCompound nbt = new NbtCompound();
		nbt.putString("MoralOwner", playerName);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
		return stack;
	}

	@Nullable
	public static String getMoralOwner(ItemStack itemStack) {
		NbtComponent nbtComponent = itemStack.get(DataComponentTypes.CUSTOM_DATA);
		if (nbtComponent != null) {
			NbtCompound nbt = nbtComponent.copyNbt();
			return nbt.contains("MoralOwner") ? nbt.getString("MoralOwner") : null;
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
		items.put("hungryartist_", new ItemStack(Items.POTION));
		items.put("_Flag_E_", new ItemStack(Items.POPPY));
		items.put("Spring0809", new ItemStack(Items.TNT));
		items.put("fire_duang_duang", new ItemStack(Items.COOKED_COD));
		items.put("Keviince", createEnchantedItem(Items.WOODEN_SWORD, 10));
		items.put("Dazo66", new ItemStack(Items.DANDELION));
		items.put("Gamepiaynmo", new ItemStack(Items.COAL));
		items.put("CCS_Covenant", new ItemStack(Items.COOKIE));
		items.put("Lancet_Corgi", createEnchantedItem(Items.IRON_SWORD, 5));
		items.put("ajisai_iii", new ItemStack(Items.CHICKEN));
		items.put("Aschin", new ItemStack(Items.WHEAT));
		items.put("Dou_Bi_Long", new ItemStack(Items.DRAGON_EGG));
		items.put("minamotosan", new ItemStack(Items.ROTTEN_FLESH));
		items.put("zi_nv", new ItemStack(Items.TALL_GRASS));
		items.put("CallMeLecten", new ItemStack(Items.GLASS_BOTTLE));
		items.put("HG_Fei", new ItemStack(Items.POTION));
		items.put("hai_dan", new ItemStack(Items.TURTLE_EGG));
		items.put("Fallen_Breath", new ItemStack(Items.LEATHER_CHESTPLATE));
		items.put("Sanluli36li", new ItemStack(Items.TNT_MINECART));
		items.put("shamreltuim", new ItemStack(Items.PUFFERFISH));
		items.put("YtonE", new ItemStack(Items.POTION));
		items.put("DawNemo", new ItemStack(Items.SPYGLASS));
		items.put("Van_Nya", new ItemStack(Items.RABBIT_STEW));
		items.put("youngdao", createEnchantedItem(Items.STONE_SWORD, 10));
		items.put("ql_Lwi", new ItemStack(Items.COD));
		items.put("Azulene0907", new ItemStack(Items.SPLASH_POTION));
		items.put("LUZaLID", new ItemStack(Items.CYAN_DYE));
		items.put("U_ruby", new ItemStack(Items.FEATHER));
		items.put("Do1phin_jump", new ItemStack(Items.TROPICAL_FISH));
		items.put("kuritsirolf", new ItemStack(Items.CAKE));
		items.put("acaciachan", new ItemStack(Items.ACACIA_SAPLING));
		items.put("Lei_Feng_", new ItemStack(Items.GUNPOWDER));
		items.put("Ra1ny_Yuki", new ItemStack(Items.SNOW));
		items.put("hsds", new ItemStack(Items.END_CRYSTAL));
		items.put("ayjinyt", new ItemStack(Items.FLOWERING_AZALEA_LEAVES));
		items.put("north_82", new ItemStack(Items.SHULKER_SHELL));
		items.put("Runaway_Fancy", new ItemStack(Items.SPYGLASS));
		items.put("zhihan233", new ItemStack(Items.PINK_DYE));
		items.put("LINHUA_24k", new ItemStack(Items.GOLD_INGOT));
		items.put("Xiang_Q1u", new ItemStack(Items.WATER_BUCKET));
		items.put("M0n3tr", new ItemStack(Items.GLASS_BOTTLE));
		items.put("WEIKAN", new ItemStack(Items.POWDER_SNOW_BUCKET));
		items.put("Tou_Beichuan", new ItemStack(Items.GOLD_NUGGET));
		items.put("kaniol", new ItemStack(Items.ELYTRA));
		items.put("Qungrn", new ItemStack(Items.FIRE_CHARGE));
		items.put("REMS_Eula", new ItemStack(Items.DIAMOND));
		items.put("yue_szk", new ItemStack(Items.AMETHYST_SHARD));
		items.put("xiao_6", new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK));
		items.put("liangxi__", new ItemStack(Items.SUGAR));
	}

	private static ItemStack createEnchantedItem(Item item, int level)
	{
		ItemStack stack = new ItemStack(item);
		Registry<Enchantment> enchantmentRegistry = (Registry<Enchantment>) Registries.REGISTRIES.get(RegistryKeys.ENCHANTMENT.getValue());
		if (enchantmentRegistry == null) {
			throw new IllegalStateException("Missing enchantment registry");
		}
		RegistryEntry<Enchantment> sharpness = enchantmentRegistry.getEntry(Enchantments.SHARPNESS).orElseThrow(RuntimeException::new);
		EnchantmentHelper.apply(stack, builder -> builder.set(sharpness, level));
		return stack;
	}
}
