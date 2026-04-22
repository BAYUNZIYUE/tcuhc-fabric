package me.fallenbreath.tcuhc.mixins.worldgen;

import net.minecraft.world.gen.structure.BuriedTreasureStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(BuriedTreasureStructure.class)
public abstract class BuriedTreasureStructureMixin
{
	@ModifyConstant(method = "addPieces", constant = @Constant(intValue = 9, ordinal = 0))
	private static int reduceTreasureXOffset(int offset)
	{
		return 4;
	}

	@ModifyConstant(method = "addPieces", constant = @Constant(intValue = 9, ordinal = 1))
	private static int reduceTreasureZOffset(int offset)
	{
		return 4;
	}
}
