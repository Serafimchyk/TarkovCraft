package com.tarkovcraft.loot;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Loot table categories for TarkovCraft crates, structures, airdrops, scav pockets,
 * and abandoned war-torn city locations.
 */
public enum TarkovLootPool {
	MILITARY("military", "weapon_crate", 6, 4),
	MEDICAL("medical", "medbag", 3, 3),
	CIVILIAN("civilian", "technical_crate", 4, 4),
	AIRDROP("airdrop", "airdrop_crate", 6, 6),
	SCAV_POCKET("scav_pocket", "technical_crate", 3, 3),

	// Abandoned war-torn city zones
	CAFE("cafe", "technical_crate", 4, 4),
	SCHOOL("school", "technical_crate", 4, 4),
	FACTORY("factory", "technical_crate", 5, 4),
	PORT("port", "weapon_crate", 6, 4),
	RESIDENTIAL("residential", "technical_crate", 4, 4),
	SUPERMARKET("supermarket", "technical_crate", 5, 4),
	PHARMACY("pharmacy", "medbag", 4, 3),
	POLICE("police", "weapon_crate", 5, 4),
	OFFICE("office", "technical_crate", 4, 4),
	GARAGE("garage", "technical_crate", 5, 4),
	BATTLEFIELD("battlefield", "weapon_crate", 6, 4);

	private final String id;
	private final String defaultBlockKey;
	private final int defaultGridWidth;
	private final int defaultGridHeight;

	TarkovLootPool(String id, String defaultBlockKey, int defaultGridWidth, int defaultGridHeight) {
		this.id = id;
		this.defaultBlockKey = defaultBlockKey;
		this.defaultGridWidth = defaultGridWidth;
		this.defaultGridHeight = defaultGridHeight;
	}

	public String getId() {
		return id;
	}

	public String getDefaultBlockKey() {
		return defaultBlockKey;
	}

	public int getDefaultGridWidth() {
		return defaultGridWidth;
	}

	public int getDefaultGridHeight() {
		return defaultGridHeight;
	}

	public String getTranslationKey() {
		return "lootpool.tarkovcraft." + id;
	}

	public boolean isCityZone() {
		return switch (this) {
			case CAFE, SCHOOL, FACTORY, PORT, RESIDENTIAL, SUPERMARKET, PHARMACY, POLICE, OFFICE, GARAGE, BATTLEFIELD -> true;
			default -> false;
		};
	}

	@Nullable
	public static TarkovLootPool byId(@Nullable String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String norm = raw.trim().toLowerCase(Locale.ROOT);
		for (TarkovLootPool pool : values()) {
			if (pool.id.equals(norm) || pool.name().equalsIgnoreCase(norm)) {
				return pool;
			}
		}
		return null;
	}

	public static List<String> allIds() {
		return Arrays.stream(values()).map(TarkovLootPool::getId).toList();
	}
}
