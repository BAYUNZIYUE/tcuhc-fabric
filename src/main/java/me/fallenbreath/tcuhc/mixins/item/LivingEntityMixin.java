package me.fallenbreath.tcuhc.mixins.item;

import com.google.common.collect.Lists;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin
{
	@Unique
	private int goldenAppleLevel;

	/**
	 * In 1.21.1 applyFoodEffects() only receives a FoodComponent, so the item stack's
	 * custom "level" tag can no longer be read there. Capture it earlier, in eatFood().
	 */
	@Inject(method = "eatFood", at = @At("HEAD"))
	private void captureGoldenAppleLevel(World world, ItemStack stack, FoodComponent foodComponent, CallbackInfoReturnable<ItemStack> cir)
	{
		if (stack.getItem() == Items.GOLDEN_APPLE)
		{
			NbtComponent nbtComponent = stack.get(DataComponentTypes.CUSTOM_DATA);
			if (nbtComponent != null)
			{
				NbtCompound nbt = nbtComponent.copyNbt();
				this.goldenAppleLevel = nbt.contains("level") ? nbt.getInt("level") : 0;
			}
			else
			{
				this.goldenAppleLevel = 0;
			}
		}
		else
		{
			this.goldenAppleLevel = 0;
		}
	}

	@SuppressWarnings("ConstantConditions")
	@Redirect(
			method = "applyFoodEffects",
			at = @At(
					value = "INVOKE",
					target = "Ljava/util/List;iterator()Ljava/util/Iterator;"
			)
	)
	private Iterator<FoodComponent.StatusEffectEntry> modifyIterator(List<FoodComponent.StatusEffectEntry> list)
	{
		int level = this.goldenAppleLevel;
		if (level > 0)
		{
			List<FoodComponent.StatusEffectEntry> newList = Lists.newArrayList(list);
			for (int i = 0; i < newList.size(); i++)
			{
				FoodComponent.StatusEffectEntry entry = newList.get(i);
				float chance = entry.probability();
				StatusEffectInstance newEffect = new StatusEffectInstance(entry.effect());
				// NOTE: StatusEffects.* are RegistryEntry<StatusEffect> since 1.20.5, so compare the
				// resolved StatusEffect values. Comparing the RegistryEntry directly against a
				// StatusEffect compiles (class vs interface) but is always false at runtime.
				StatusEffect effectType = newEffect.getEffectType().value();
				if (effectType == StatusEffects.REGENERATION.value())
				{
					((StatusEffectInstanceAccessor)newEffect).setDuration((level + 1) * 20);
				}
				else if (effectType == StatusEffects.ABSORPTION.value())
				{
					((StatusEffectInstanceAccessor)newEffect).setAmplifier(level - 1);
				}
				newList.set(i, new FoodComponent.StatusEffectEntry(newEffect, chance));
			}
			return newList.iterator();
		}
		return list.iterator();
	}
}
