package me.fallenbreath.tcuhc.mixins;

import me.fallenbreath.tcuhc.UhcGameManager;
import me.fallenbreath.tcuhc.options.Options;
import net.minecraft.server.dedicated.AbstractPropertiesHandler;
import net.minecraft.server.dedicated.ServerPropertiesHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Properties;

@Mixin(ServerPropertiesHandler.class)
public abstract class ServerPropertiesHandlerMixin extends AbstractPropertiesHandler<ServerPropertiesHandler> {

    public ServerPropertiesHandlerMixin(Properties properties) {
        super(properties);
    }

    @ModifyArg(
            method = "getGeneratorOptions",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/gen/GeneratorOptions;fromProperties(Lnet/minecraft/util/registry/DynamicRegistryManager;Ljava/util/Properties;)Lnet/minecraft/world/gen/GeneratorOptions;"
            ),
            index = 1
    )
    public Properties changeLevelType(Properties properties)
    {
        UhcGameManager.EnumLevelType levelType = (UhcGameManager.EnumLevelType) Options.instance.getOptionValue("levelType");
        switch (levelType) {
            case DEFAULT:
                properties.put("level-type", "default");
                break;
            case AMPLIFIED:
                properties.put("level-type", "amplified");
                break;
            case LARGEBIOMES:
                properties.put("level-type", "largebiomes");
                break;
        }
        return properties;
    }
}
