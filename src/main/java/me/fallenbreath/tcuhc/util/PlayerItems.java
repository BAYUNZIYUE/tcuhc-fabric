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
import java.util.function.Supplier;

public class PlayerItems
{
	private static final Map<String, Supplier<ItemStack>> items = Maps.newLinkedHashMap();
	private static final ItemStack DEFAULT_MORAL = new ItemStack(Items.PAPER);

	public static Collection<String> getAvailableNames()
	{
		return items.keySet();
	}

	public static ItemStack getPlayerItem(String playerName, boolean onFire)
	{
		ItemStack stack = items.getOrDefault(playerName, () -> DEFAULT_MORAL.copy()).get();
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
		register("hungryartist_", Items.POTION);
		register("_Flag_E_", Items.POPPY);
		register("Spring0809", Items.TNT);
		register("fire_duang_duang", Items.COOKED_COD);
		registerEnchanted("Keviince", Items.WOODEN_SWORD, 10);
		register("Dazo66", Items.DANDELION);
		register("Gamepiaynmo", Items.COAL);
		register("CCS_Covenant", Items.COOKIE);
		registerEnchanted("Lancet_Corgi", Items.IRON_SWORD, 5);
		register("ajisai_iii", Items.CHICKEN);
		register("Aschin", Items.WHEAT);
		register("Dou_Bi_Long", Items.DRAGON_EGG);
		register("minamotosan", Items.ROTTEN_FLESH);
		register("zi_nv", Items.TALL_GRASS);
		register("CallMeLecten", Items.GLASS_BOTTLE);
		register("HG_Fei", Items.POTION);
		register("hai_dan", Items.TURTLE_EGG);
		register("Fallen_Breath", Items.LEATHER_CHESTPLATE);
		register("Sanluli36li", Items.TNT_MINECART);
		register("shamreltuim", Items.PUFFERFISH);
		register("YtonE", Items.POTION);
		register("DawNemo", Items.SPYGLASS);
		register("Van_Nya", Items.RABBIT_STEW);
		registerEnchanted("youngdao", Items.STONE_SWORD, 10);
		register("ql_Lwi", Items.COD);
		register("Azulene0907", Items.SPLASH_POTION);
		register("LUZaLID", Items.CYAN_DYE);
		register("U_ruby", Items.FEATHER);
		register("Do1phin_jump", Items.TROPICAL_FISH);
		register("kuritsirolf", Items.CAKE);
		register("acaciachan", Items.ACACIA_SAPLING);
		register("Lei_Feng_", Items.GUNPOWDER);
		register("Ra1ny_Yuki", Items.SNOW);
		register("hsds", Items.END_CRYSTAL);
		register("ayjinyt", Items.FLOWERING_AZALEA_LEAVES);
		register("north_82", Items.SHULKER_SHELL);
		register("Runaway_Fancy", Items.SPYGLASS);
		register("zhihan233", Items.PINK_DYE);
		register("LINHUA_24k", Items.GOLD_INGOT);
		register("Xiang_Q1u", Items.WATER_BUCKET);
		register("M0n3tr", Items.GLASS_BOTTLE);
		register("WEIKAN", Items.POWDER_SNOW_BUCKET);
		register("Tou_Beichuan", Items.GOLD_NUGGET);
		register("kaniol", Items.ELYTRA);
		register("Qungrn", Items.FIRE_CHARGE);
		register("REMS_Eula", Items.DIAMOND);
		register("yue_szk", Items.AMETHYST_SHARD);
		register("xiao_6", Items.WARPED_FUNGUS_ON_A_STICK);
		register("liangxi__", Items.SUGAR);
	}

	private static void register(String playerName, Item item)
	{
		items.put(playerName, () -> new ItemStack(item));
	}

	private static void registerEnchanted(String playerName, Item item, int level)
	{
		items.put(playerName, () -> createEnchantedItem(item, level));
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
