package me.fallenbreath.tcuhc.mixins.entity;

import com.google.common.collect.Sets;
import me.fallenbreath.tcuhc.UhcGameManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

@Mixin(ArrowEntity.class)
public abstract class ArrowEntityMixin extends PersistentProjectileEntity {

    private int airTime = 0;

    @Shadow private Potion potion;

    public ArrowEntityMixin(EntityType<? extends ArrowEntity> entityType, World world) {
        super(entityType, world);
    }

    @Unique
    private float getExplosionResistance(World world, BlockPos blockPos)
    {
        BlockState blockState = this.world.getBlockState(blockPos);
        FluidState fluidState = this.world.getFluidState(blockPos);
        return Math.max(blockState.getBlock().getBlastResistance(), fluidState.getBlastResistance());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void explodeOnTick(CallbackInfo ci)
    {
        if(!this.inGround && airTime++ > (this.isCritical()? 2 : 4) && this.potion == Potions.LUCK &&
                this.getVelocity().length() > 1.0F) {
            explode(0.7F * (float)Math.sqrt(1.0F + airTime * 0.2F + this.getVelocity().length() * 0.1F));
        }
    }

    @Unique
    private void explode(float power)
    {
        if (this.isRemoved()) return;
        if (this.airTime % (this.isCritical()? 2 : 3) != 0) return;
        this.world.createExplosion(this, this.getPos().getX(), this.getPos().getY(), this.getPos().getZ(), power, Explosion.DestructionType.DESTROY);
        BlockPos arrowpos = new BlockPos(this.getPos());
        if (getExplosionResistance(world, arrowpos) > 6.01f)
        {
            return;
        }
        for (int x = -1; x <= 1; x++)
        {
            for (int y = -2; y <= 1; y++)
            {
                for (int z = -1; z <= 1; z++)
                {
                    BlockPos pos = arrowpos.add(x, y, z);
                    if (getExplosionResistance(world, pos) < 6.01f)
                    {
                        this.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }
}
