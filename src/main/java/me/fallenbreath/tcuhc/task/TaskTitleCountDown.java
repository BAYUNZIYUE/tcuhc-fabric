/*
 From Gamepiaynmo: https://github.com/Gamepiaynmo/TC-UHC
 */

package me.fallenbreath.tcuhc.task;

import me.fallenbreath.tcuhc.UhcGameColor;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.UhcGameTeam;
import me.fallenbreath.tcuhc.UhcPlayerManager;
import me.fallenbreath.tcuhc.task.Task.TaskTimer;
import me.fallenbreath.tcuhc.util.TitleUtil;
import net.minecraft.advancement.AdvancementProgress;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collections;
import java.util.Optional;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

public class TaskTitleCountDown extends TaskTimer {
	
	private int count;

	public TaskTitleCountDown(int init, int delay, int interval) {
		super(delay, interval);
		count = init;
	}
	
	@Override
	public void onTimer() {
		TitleUtil.sendTitleToAllPlayers(Formatting.GOLD + String.valueOf(--count), null);
		if (count == 0) this.setCanceled();
	}
	
	@Override
	public void onFinish() {
		TitleUtil.sendTitleToAllPlayers("游戏开始！", "祝你玩得开心！");
		UhcGameManager.instance.getUhcPlayerManager().getCombatPlayers().forEach(player -> player.addTask(new TaskFindPlayer(player) {
			@SuppressWarnings("ConstantConditions")
			@Override
			public void onFindPlayer(ServerPlayerEntity player) {
				player.changeGameMode(GameMode.SURVIVAL);
				player.setInvulnerable(false);
				player.clearStatusEffects();
				UhcGameManager.instance.getUhcPlayerManager().resetHealthAndFood(player);
				player.resetStat(Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST));  // no free phantom
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 4));  // 10s Resistance V
				if(UhcGameManager.getBattleType() == UhcGameManager.EnumBattleType.ICARUS) {
					ItemStack elytra = new ItemStack(Items.ELYTRA);
					NbtCompound elytraNbt = new NbtCompound();
					NbtList enchantmentsNbt = new NbtList();
					NbtCompound mendingNbt = new NbtCompound();
					mendingNbt.putString("id", "minecraft:mending");
					mendingNbt.putInt("lvl", 1);
					enchantmentsNbt.add(mendingNbt);
					NbtCompound bindingNbt = new NbtCompound();
					bindingNbt.putString("id", "minecraft:binding_curse");
					bindingNbt.putInt("lvl", 1);
					enchantmentsNbt.add(bindingNbt);
					elytraNbt.put("Enchantments", enchantmentsNbt);
					elytra.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(elytraNbt));
					player.equipStack(EquipmentSlot.CHEST, elytra);
				} else if(UhcGameManager.getBattleType() == UhcGameManager.EnumBattleType.MARINE) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 1200, 0));
				}

				// revoke all advancements
				player.getServer().getAdvancementLoader().getAdvancements().forEach(advancement -> {
					AdvancementProgress advancementProgress = player.getAdvancementTracker().getProgress(advancement);
					if (advancementProgress.isAnyObtained()) {
						for(String string : advancementProgress.getObtainedCriteria()) {
							player.getAdvancementTracker().revokeCriterion(advancement, string);
						}
					}
				});

				// give invisibility and shiny potion to player for ghost mode
				switch (UhcGameManager.getGameMode()) {
					case BOMBER:
						this.getGamePlayer().addBomberModeEffect();
					case GHOST:
						this.getGamePlayer().addGhostModeEffect();
						ItemStack shinyPotion = new ItemStack(Items.SPLASH_POTION);
						shinyPotion.set(DataComponentTypes.CUSTOM_NAME, Text.literal("闪耀喷溅药水"));
						NbtCompound shinyNbt = new NbtCompound();
						shinyNbt.putInt("CustomPotionColor", 0x00FFFF);
						shinyPotion.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(shinyNbt));
						player.getInventory().insertStack(shinyPotion);
						break;
					case HUNTER:
						if(this.getGamePlayer().getTeam().getTeamColor() == UhcGameColor.RED) {
							ItemStack speedPotion = new ItemStack(Items.SPLASH_POTION);
							speedPotion.set(DataComponentTypes.CUSTOM_NAME, Text.literal("疾速喷溅药水"));
							NbtCompound speedNbt = new NbtCompound();
							speedNbt.putInt("CustomPotionColor", 0x7FC07F);
							speedPotion.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(speedNbt));
							player.getInventory().insertStack(speedPotion);
						} else {
							ItemStack compass = new ItemStack(Items.COMPASS);
							compass.set(DataComponentTypes.CUSTOM_NAME, Text.of("猎人指南针"));
							NbtCompound compassNbt = new NbtCompound();
							NbtList enchantList = new NbtList();
							NbtCompound vanishNbt = new NbtCompound();
							vanishNbt.putString("id", "minecraft:vanishing_curse");
							vanishNbt.putInt("lvl", 1);
							enchantList.add(vanishNbt);
							compassNbt.put("Enchantments", enchantList);
							compass.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compassNbt));
							player.getInventory().insertStack(compass);
						}
						break;
					case GHOSTHUNTER:
						if(this.getGamePlayer().getTeam().getTeamColor() == UhcGameColor.RED) {
							this.getGamePlayer().addGhostModeEffect();
						} else {
							ItemStack shinyPotion2 = new ItemStack(Items.SPLASH_POTION);
							shinyPotion2.set(DataComponentTypes.CUSTOM_NAME, Text.literal("闪耀喷溅药水"));
							NbtCompound shiny2Nbt = new NbtCompound();
							shiny2Nbt.putInt("CustomPotionColor", 0x00FFFF);
							shinyPotion2.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(shiny2Nbt));
							player.getInventory().insertStack(shinyPotion2);
							ItemStack compass = new ItemStack(Items.COMPASS);
							compass.set(DataComponentTypes.CUSTOM_NAME, Text.of("猎人指南针"));
							NbtCompound compassNbt = new NbtCompound();
							NbtList enchantList = new NbtList();
							NbtCompound vanishNbt = new NbtCompound();
							vanishNbt.putString("id", "minecraft:vanishing_curse");
							vanishNbt.putInt("lvl", 1);
							enchantList.add(vanishNbt);
							compassNbt.put("Enchantments", enchantList);
							compass.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compassNbt));
							player.getInventory().insertStack(compass);
						}
					case KING:
						if (this.getGamePlayer().isKing()) {
							DyeColor dyeColor = this.getGamePlayer().getTeam().getTeamColor().dyeColor;
							ItemStack kingsHelmet = new ItemStack(Items.LEATHER_HELMET);
							kingsHelmet.set(DataComponentTypes.CUSTOM_NAME, Text.literal(String.format("%s之冠", dyeColor.getName())));
							NbtCompound helmetNbt = new NbtCompound();
							NbtList helmetEnchants = new NbtList();
							NbtCompound protNbt = new NbtCompound();
							protNbt.putString("id", "minecraft:protection");
							protNbt.putInt("lvl", 6);
							helmetEnchants.add(protNbt);
							NbtCompound bindNbt = new NbtCompound();
							bindNbt.putString("id", "minecraft:binding_curse");
							bindNbt.putInt("lvl", 1);
							helmetEnchants.add(bindNbt);
							NbtCompound vanishNbt = new NbtCompound();
							vanishNbt.putString("id", "minecraft:vanishing_curse");
							vanishNbt.putInt("lvl", 1);
							helmetEnchants.add(vanishNbt);
							helmetNbt.put("Enchantments", helmetEnchants);
							kingsHelmet.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(helmetNbt));
							player.equipStack(EquipmentSlot.HEAD, kingsHelmet);
						}
						break;
				}
			}
		}));
		if (UhcGameManager.getGameMode() == UhcGameManager.EnumMode.KING) {
			UhcGameManager.instance.addTask(new TaskKingEffectField());
		}
		UhcGameManager.instance.addTask(new TaskScoreboard());
	}

}
