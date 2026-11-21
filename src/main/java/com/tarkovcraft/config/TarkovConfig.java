package com.tarkovcraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Thread-safe, JUnit-safe Gson configuration stored in config/tarkovcraft.json.
 */
public final class TarkovConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("TarkovCraft/Config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static volatile TarkovConfig instance = new TarkovConfig();

	public boolean airdropEnabled = true;
	public int airdropIntervalMinutes = 30;
	public int airdropMinRadius = 48;
	public int airdropMaxRadius = 120;
	public int airdropSmokeSeconds = 180;
	public boolean airdropAnnounceCoords = true;
	public boolean worldgenStructuresEnabled = true;
	public float lootDensityMultiplier = 1.0f;
	public float mobGearSpawnChance = 0.08f;
	public float mobBackpackChance = 0.35f;
	public float corpseBaseLootChance = 0.25f;
	public int corpseLifetimeSeconds = 600;
	public float containerRevealSeconds = 1.0f;
	public float searchTimeMultiplier = 1.0f;
	public float grenadeDamageMultiplier = 1.0f;

	public TarkovConfig() {
	}

	public static TarkovConfig get() {
		return instance;
	}

	public static void setInstanceForTest(TarkovConfig config) {
		if (config != null) {
			config.validateAndClamp();
			instance = config;
		}
	}

	public static synchronized TarkovConfig loadOrCreate() {
		try {
			Path path = FabricLoader.getInstance().getConfigDir().resolve("tarkovcraft.json");
			return loadFromPath(path);
		} catch (Throwable t) {
			LOGGER.warn("Could not resolve Fabric config dir ({}); using in-memory defaults.", t.getMessage());
			TarkovConfig fallback = new TarkovConfig();
			fallback.validateAndClamp();
			instance = fallback;
			return fallback;
		}
	}

	public static synchronized void save() {
		try {
			Path path = FabricLoader.getInstance().getConfigDir().resolve("tarkovcraft.json");
			saveToPath(path, instance);
		} catch (Throwable t) {
			LOGGER.warn("Failed to save config/tarkovcraft.json: {}", t.getMessage());
		}
	}

	public static synchronized TarkovConfig loadFromPath(Path path) {
		TarkovConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, TarkovConfig.class);
			} catch (Exception e) {
				LOGGER.warn("Failed to parse config {}: {}; restoring defaults.", path, e.getMessage());
			}
		}
		if (loaded == null) {
			loaded = new TarkovConfig();
		}
		loaded.validateAndClamp();
		saveToPath(path, loaded);
		instance = loaded;
		return loaded;
	}

	public static synchronized void saveToPath(Path path, TarkovConfig cfg) {
		try {
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, writer);
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to write config {}: {}", path, e.getMessage());
		}
	}

	public void validateAndClamp() {
		airdropIntervalMinutes = clampInt(airdropIntervalMinutes, 1, 1440);
		airdropMinRadius = clampInt(airdropMinRadius, 8, 256);
		airdropMaxRadius = clampInt(airdropMaxRadius, airdropMinRadius, 512);
		airdropSmokeSeconds = clampInt(airdropSmokeSeconds, 0, 1800);
		lootDensityMultiplier = clampFloat(lootDensityMultiplier, 0.1f, 5.0f, 1.0f);
		mobGearSpawnChance = clampFloat(mobGearSpawnChance, 0.0f, 1.0f, 0.08f);
		mobBackpackChance = clampFloat(mobBackpackChance, 0.0f, 1.0f, 0.35f);
		corpseBaseLootChance = clampFloat(corpseBaseLootChance, 0.0f, 1.0f, 0.25f);
		corpseLifetimeSeconds = clampInt(corpseLifetimeSeconds, 30, 7200);
		containerRevealSeconds = clampFloat(containerRevealSeconds, 0.1f, 10.0f, 1.0f);
		searchTimeMultiplier = clampFloat(searchTimeMultiplier, 0.1f, 5.0f, 1.0f);
		grenadeDamageMultiplier = clampFloat(grenadeDamageMultiplier, 0.1f, 5.0f, 1.0f);
	}

	public int getAirdropIntervalTicks() {
		return airdropIntervalMinutes * 60 * 20;
	}

	public int getContainerRevealTicks() {
		return Math.max(2, Math.round(containerRevealSeconds * 20.0f));
	}

	public int getCorpseLifetimeTicks() {
		return corpseLifetimeSeconds * 20;
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static float clampFloat(float value, float min, float max, float fallback) {
		if (Float.isNaN(value) || Float.isInfinite(value)) {
			return fallback;
		}
		return Math.max(min, Math.min(max, value));
	}
}
