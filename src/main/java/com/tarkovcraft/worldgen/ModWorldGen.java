package com.tarkovcraft.worldgen;

import com.tarkovcraft.TarkovCraftMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

/**
 * Registers Overworld surface loot structures (roadblock, med_tent, military_camp, forest_stash).
 */
public final class ModWorldGen {
	public static final Feature<DefaultFeatureConfig> ROADBLOCK = registerFeature("roadblock",
		new TarkovStructureFeature(DefaultFeatureConfig.CODEC, TarkovStructureFeature.StructureKind.ROADBLOCK));
	public static final Feature<DefaultFeatureConfig> MED_TENT = registerFeature("med_tent",
		new TarkovStructureFeature(DefaultFeatureConfig.CODEC, TarkovStructureFeature.StructureKind.MED_TENT));
	public static final Feature<DefaultFeatureConfig> MILITARY_CAMP = registerFeature("military_camp",
		new TarkovStructureFeature(DefaultFeatureConfig.CODEC, TarkovStructureFeature.StructureKind.MILITARY_CAMP));
	public static final Feature<DefaultFeatureConfig> FOREST_STASH = registerFeature("forest_stash",
		new TarkovStructureFeature(DefaultFeatureConfig.CODEC, TarkovStructureFeature.StructureKind.FOREST_STASH));

	private static boolean initialized = false;

	private ModWorldGen() {
	}

	private static Feature<DefaultFeatureConfig> registerFeature(String name, Feature<DefaultFeatureConfig> feature) {
		return Registry.register(Registries.FEATURE, new Identifier(TarkovCraftMod.MOD_ID, name), feature);
	}

	public static synchronized void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		var nonWaterOverworld = BiomeSelectors.foundInOverworld()
			.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
			.and(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN).negate())
			.and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate())
			.and(BiomeSelectors.tag(BiomeTags.IS_BEACH).negate());

		addStructure("roadblock", nonWaterOverworld);
		addStructure("med_tent", nonWaterOverworld);
		addStructure("military_camp", nonWaterOverworld);
		addStructure("forest_stash", BiomeSelectors.tag(BiomeTags.IS_FOREST).or(BiomeSelectors.tag(BiomeTags.IS_TAIGA)));
	}

	private static void addStructure(String name, java.util.function.Predicate<net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext> selector) {
		BiomeModifications.addFeature(
			selector,
			GenerationStep.Feature.SURFACE_STRUCTURES,
			RegistryKey.of(RegistryKeys.PLACED_FEATURE, new Identifier(TarkovCraftMod.MOD_ID, name))
		);
	}
}
