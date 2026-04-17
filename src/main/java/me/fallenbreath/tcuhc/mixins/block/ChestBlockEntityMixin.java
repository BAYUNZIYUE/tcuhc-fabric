package me.fallenbreath.tcuhc.mixins.block;

import me.fallenbreath.tcuhc.TcUhcMod;
import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.UhcGamePlayer;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestBlockEntity.class)
public abstract class ChestBlockEntityMixin extends LootableContainerBlockEntity
{
	private static final String BONUS_CHEST_NAME = "奖励宝箱";
	private static final String EMPTY_CHEST_NAME = "空宝箱";
	private static final RegistryKey<net.minecraft.loot.LootTable> BONUS_CHEST_LOOT = RegistryKey.of(RegistryKeys.LOOT_TABLE, TcUhcMod.id("bonus_chest/bonus"));
	private static final RegistryKey<net.minecraft.loot.LootTable> EMPTY_CHEST_LOOT = RegistryKey.of(RegistryKeys.LOOT_TABLE, TcUhcMod.id("bonus_chest/empty"));

	protected ChestBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState)
	{
		super(blockEntityType, blockPos, blockState);
	}

	@Inject(method = "onOpen", at = @At("HEAD"))
	private void playerOpenChestHook(PlayerEntity player, CallbackInfo ci)
	{
		if (!player.isCreative() && !player.isSpectator()) {
			net.minecraft.text.Text customName = this.getCustomName();
			RegistryKey<net.minecraft.loot.LootTable> lootTable = this.getLootTable();
			UhcGamePlayer.EnumStat stat = null;
			if (lootTable != null && lootTable.equals(BONUS_CHEST_LOOT)) {
				stat = UhcGamePlayer.EnumStat.CHEST_FOUND;
			} else if (lootTable != null && lootTable.equals(EMPTY_CHEST_LOOT)) {
				stat = UhcGamePlayer.EnumStat.EMPTY_CHEST_FOUND;
			} else if (customName != null) {
				switch (customName.getString()) {
					case BONUS_CHEST_NAME:
						stat = UhcGamePlayer.EnumStat.CHEST_FOUND;
						break;
					case EMPTY_CHEST_NAME:
						stat = UhcGamePlayer.EnumStat.EMPTY_CHEST_FOUND;
						break;
					default:
						break;
				}
			}
			if (stat != null) {
				UhcGameManager.instance.getUhcPlayerManager().getGamePlayer(player).getStat().addStat(stat, 1);
			}
		}
	}

	@Inject(method = "onClose", at = @At("HEAD"))
	private void playerCloseChestHook(PlayerEntity player, CallbackInfo ci)
	{
		if (!player.isCreative() && !player.isSpectator() && this.getCustomName() != null)
		{
			String customName = this.getCustomName().getString();
			if (customName.equals(BONUS_CHEST_NAME) || customName.equals(EMPTY_CHEST_NAME))
			{
			}
		}
	}
}
