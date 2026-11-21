package com.tarkovcraft.worldgen;

import com.tarkovcraft.TarkovCraftMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
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
		addOverworldSurfaceStructure("roadblock");
		addOverworldSurfaceStructure("med_tent");
		addOverworldSurfaceStructure("military_camp");
		addOverworldSurfaceStructure("forest_stash");
	}

	private static void addOverworldSurfaceStructure(String name) {
		BiomeModifications.addFeature(
			BiomeSelectors.foundInOverworld(),
			GenerationStep.Feature.SURFACE_STRUCTURES,
			RegistryKey.of(RegistryKeys.PLACED_FEATURE, new Identifier(TarkovCraftMod.MOD_ID, name))
		);
	}
}
