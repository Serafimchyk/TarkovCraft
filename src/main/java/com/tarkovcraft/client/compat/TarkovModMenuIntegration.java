package com.tarkovcraft.client.compat;

import com.tarkovcraft.airdrop.AirdropManager;
import com.tarkovcraft.config.TarkovConfig;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Mod Menu integration providing a live Cloth Config screen for config/tarkovcraft.json.
 * Gracefully degrades to no-op if Cloth Config is not installed.
 */
@Environment(EnvType.CLIENT)
public final class TarkovModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		if (!FabricLoader.getInstance().isModLoaded("cloth-config")
			&& !FabricLoader.getInstance().isModLoaded("cloth-config2")) {
			return parent -> null;
		}
		return ClothScreenFactory::create;
	}

	private static final class ClothScreenFactory {
		private static Screen create(Screen parent) {
			TarkovConfig cfg = TarkovConfig.get();
			TarkovConfig defaults = new TarkovConfig();

			ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable("config.tarkovcraft.title"))
				.setSavingRunnable(() -> {
					cfg.validateAndClamp();
					TarkovConfig.save();
					AirdropManager.onConfigReloaded();
				});

			ConfigEntryBuilder eb = builder.entryBuilder();

			// Category 1: Loot & City
			ConfigCategory lootCat = builder.getOrCreateCategory(Text.translatable("config.tarkovcraft.cat.loot"));
			lootCat.addEntry(eb.startIntField(Text.translatable("config.tarkovcraft.loot_respawn_minutes"), cfg.lootRespawnMinutes)
				.setDefaultValue(defaults.lootRespawnMinutes)
				.setMin(0).setMax(10080)
				.setTooltip(Text.translatable("config.tarkovcraft.loot_respawn_minutes.desc"))
				.setSaveConsumer(v -> cfg.lootRespawnMinutes = v)
				.build());
			lootCat.addEntry(eb.startBooleanToggle(Text.translatable("config.tarkovcraft.city_autoloot"), cfg.cityAutoLootVanillaContainers)
				.setDefaultValue(defaults.cityAutoLootVanillaContainers)
				.setTooltip(Text.translatable("config.tarkovcraft.city_autoloot.desc"))
				.setSaveConsumer(v -> cfg.cityAutoLootVanillaContainers = v)
				.build());
			lootCat.addEntry(eb.startFloatField(Text.translatable("config.tarkovcraft.vanilla_loot_chance"), cfg.vanillaContainerLootChance)
				.setDefaultValue(defaults.vanillaContainerLootChance)
				.setMin(0.05f).setMax(1.0f)
				.setSaveConsumer(v -> cfg.vanillaContainerLootChance = v)
				.build());
			lootCat.addEntry(eb.startFloatField(Text.translatable("config.tarkovcraft.loot_density"), cfg.lootDensityMultiplier)
				.setDefaultValue(defaults.lootDensityMultiplier)
				.setMin(0.1f).setMax(5.0f)
				.setSaveConsumer(v -> cfg.lootDensityMultiplier = v)
				.build());
			lootCat.addEntry(eb.startBooleanToggle(Text.translatable("config.tarkovcraft.worldgen_structures"), cfg.worldgenStructuresEnabled)
				.setDefaultValue(defaults.worldgenStructuresEnabled)
				.setSaveConsumer(v -> cfg.worldgenStructuresEnabled = v)
				.build());

			// Category 2: Airdrops
			ConfigCategory airCat = builder.getOrCreateCategory(Text.translatable("config.tarkovcraft.cat.airdrop"));
			airCat.addEntry(eb.startBooleanToggle(Text.translatable("config.tarkovcraft.airdrop_enabled"), cfg.airdropEnabled)
				.setDefaultValue(defaults.airdropEnabled)
				.setSaveConsumer(v -> cfg.airdropEnabled = v)
				.build());
			airCat.addEntry(eb.startIntField(Text.translatable("config.tarkovcraft.airdrop_interval"), cfg.airdropIntervalMinutes)
				.setDefaultValue(defaults.airdropIntervalMinutes)
				.setMin(1).setMax(1440)
				.setSaveConsumer(v -> cfg.airdropIntervalMinutes = v)
				.build());
			airCat.addEntry(eb.startIntField(Text.translatable("config.tarkovcraft.airdrop_min_radius"), cfg.airdropMinRadius)
				.setDefaultValue(defaults.airdropMinRadius)
				.setMin(8).setMax(256)
				.setSaveConsumer(v -> cfg.airdropMinRadius = v)
				.build());
			airCat.addEntry(eb.startIntField(Text.translatable("config.tarkovcraft.airdrop_max_radius"), cfg.airdropMaxRadius)
				.setDefaultValue(defaults.airdropMaxRadius)
				.setMin(16).setMax(512)
				.setSaveConsumer(v -> cfg.airdropMaxRadius = v)
				.build());
			airCat.addEntry(eb.startBooleanToggle(Text.translatable("config.tarkovcraft.airdrop_announce"), cfg.airdropAnnounceCoords)
				.setDefaultValue(defaults.airdropAnnounceCoords)
				.setSaveConsumer(v -> cfg.airdropAnnounceCoords = v)
				.build());

			// Category 3: Search, Combat & Corpses
			ConfigCategory combatCat = builder.getOrCreateCategory(Text.translatable("config.tarkovcraft.cat.combat"));
			combatCat.addEntry(eb.startFloatField(Text.translatable("config.tarkovcraft.container_reveal"), cfg.containerRevealSeconds)
				.setDefaultValue(defaults.containerRevealSeconds)
				.setMin(0.1f).setMax(10.0f)
				.setSaveConsumer(v -> cfg.containerRevealSeconds = v)
				.build());
			combatCat.addEntry(eb.startFloatField(Text.translatable("config.tarkovcraft.search_time_mult"), cfg.searchTimeMultiplier)
				.setDefaultValue(defaults.searchTimeMultiplier)
				.setMin(0.1f).setMax(5.0f)
				.setSaveConsumer(v -> cfg.searchTimeMultiplier = v)
				.build());
			combatCat.addEntry(eb.startFloatField(Text.translatable("config.tarkovcraft.grenade_dmg_mult"), cfg.grenadeDamageMultiplier)
				.setDefaultValue(defaults.grenadeDamageMultiplier)
				.setMin(0.1f).setMax(5.0f)
				.setSaveConsumer(v -> cfg.grenadeDamageMultiplier = v)
				.build());
			combatCat.addEntry(eb.startFloatField(Text.translatable("config.tarkovcraft.mob_gear_chance"), cfg.mobGearSpawnChance)
				.setDefaultValue(defaults.mobGearSpawnChance)
				.setMin(0.0f).setMax(1.0f)
				.setSaveConsumer(v -> cfg.mobGearSpawnChance = v)
				.build());
			combatCat.addEntry(eb.startIntField(Text.translatable("config.tarkovcraft.corpse_lifetime"), cfg.corpseLifetimeSeconds)
				.setDefaultValue(defaults.corpseLifetimeSeconds)
				.setMin(30).setMax(7200)
				.setSaveConsumer(v -> cfg.corpseLifetimeSeconds = v)
				.build());

			return builder.build();
		}
	}
}
