package me.fallenbreath.tcuhc.mixins.item;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.component.type.SuspiciousStewEffectsComponent;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(FlowerBlock.class)
public abstract class FlowerBlockMixin
{
	// 1.21.1 replaced the two "effectInStew" fields with a single SuspiciousStewEffectsComponent.
	@Mutable
	@Shadow @Final private SuspiciousStewEffectsComponent stewEffects;

	/**
	 * The regeneration of vanilla oxeye daisy is too op, regen 3 hp.
	 * We reduce the amount of hp to 1 by reducing the effect duration to 1/3.
	 */
	@Inject(
			method = "<init>(Lnet/minecraft/registry/entry/RegistryEntry;FLnet/minecraft/block/AbstractBlock$Settings;)V",
			at = @At("TAIL")
	)
	private void modifyOxeyeDaisyRegenerationDuration(RegistryEntry<StatusEffect> effect, float duration, AbstractBlock.Settings settings, CallbackInfo ci)
	{
		// NOTE: this runs during Blocks.<clinit>, i.e. while the status effect registry is still
		// unbound. Calling effect.value() / StatusEffects.X.value() here throws
		// "Trying to access unbound value ... from registry".
		// matchesKey() only inspects the RegistryEntry key, so it is safe at this point.
		if (effect.matchesKey(StatusEffects.REGENERATION.getKey().orElseThrow()))
		{
			List<SuspiciousStewEffectsComponent.StewEffect> shortened = this.stewEffects.effects().stream()
					.map(e -> new SuspiciousStewEffectsComponent.StewEffect(e.effect(), e.duration() / 3))
					.toList();
			this.stewEffects = new SuspiciousStewEffectsComponent(shortened);
		}
	}
}
