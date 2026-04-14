package me.fallenbreath.tcuhc.util;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import me.fallenbreath.tcuhc.TcUhcMod;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.intprovider.IntProviderType;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import com.mojang.serialization.MapCodec;

import java.util.Set;

public class UhcRegistry {
	private static final Set<RecipeSerializer<?>> RECIPE_SERIALIZERS = Sets.newLinkedHashSet();

	public static Set<RecipeSerializer<?>> getRecipeSerializers() {
		return RECIPE_SERIALIZERS;
	}

	public static <C extends FeatureConfig, F extends Feature<C>> F registerFeature(String name, F feature) {
		return Registry.register(Registries.FEATURE, TcUhcMod.id(name), feature);
	}

	public static <S extends Structure> StructureType<S> registerStructureType(String name, MapCodec<S> codec) {
		return Registry.register(Registries.STRUCTURE_TYPE, TcUhcMod.id(name), new StructureType<S>() {
			@Override
			public MapCodec<S> codec() {
				return codec;
			}
		});
	}

	public static <P extends StructurePiece> StructurePieceType registerStructurePieceType(String name, StructurePieceType.ManagerAware loader) {
		return Registry.register(Registries.STRUCTURE_PIECE, TcUhcMod.id(name), loader);
	}

	public static <FC extends FeatureConfig> ConfiguredFeature<FC, ?> registerConfiguredFeature(String name, ConfiguredFeature<FC, ?> configuredFeature) {
		return configuredFeature;
	}

	public static PlacedFeature registerPlacedFeature(String name, PlacedFeature placedFeature) {
		return placedFeature;
	}

	public static <S extends RecipeSerializer<T>, T extends Recipe<?>> S registerRecipeSerializer(String name, S serializer) {
		RECIPE_SERIALIZERS.add(serializer);
		return Registry.register(Registries.RECIPE_SERIALIZER, TcUhcMod.id(name), serializer);
	}

	public static <P extends IntProvider> IntProviderType<P> registerIntProviderType(String name, Codec<P> codec) {
		return Registry.register(Registries.INT_PROVIDER_TYPE, TcUhcMod.id(name), null);
	}
}
