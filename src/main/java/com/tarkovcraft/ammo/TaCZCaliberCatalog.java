package com.tarkovcraft.ammo;

import com.tarkovcraft.health.TaCZBulletHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Catalog of all 23 TaCZ default ballistic calibers plus zero-hard-dependency runtime
 * discovery of custom gunpack calibers via TimelessAPI.getAllCommonAmmoIndex().
 */
public final class TaCZCaliberCatalog {
	private static final Pattern VALID_AMMO_ID = Pattern.compile("^[a-z0-9_]+:[a-z0-9_]+$");

	public enum CaliberGroup {
		PISTOL_SMG(30, 1),
		MAGNUM_PISTOL(20, 1),
		SHOTGUN(16, 1),
		ASSAULT_RIFLE(24, 2),
		BATTLE_SNIPER(16, 2),
		HEAVY_SNIPER(10, 3);

		private final int batchSize;
		private final int materialMultiplier;

		CaliberGroup(int batchSize, int materialMultiplier) {
			this.batchSize = batchSize;
			this.materialMultiplier = materialMultiplier;
		}

		public int getBatchSize() {
			return batchSize;
		}

		public int getMaterialMultiplier() {
			return materialMultiplier;
		}
	}

	public record CaliberSpec(
		String ammoId,
		String displayName,
		CaliberGroup group,
		int basePenetration
	) {
		public int getBatchSize() {
			return group.getBatchSize();
		}
	}

	private static final Map<String, CaliberSpec> DEFAULT_SPECS = new LinkedHashMap<>();
	private static final Map<String, CaliberSpec> RUNTIME_SPECS = new LinkedHashMap<>();
	private static boolean runtimeDiscovered = false;

	static {
		// Pistol / SMG (batch = 30)
		registerDefault("tacz:9mm", "9x19mm Parabellum", CaliberGroup.PISTOL_SMG, 18);
		registerDefault("tacz:45acp", ".45 ACP", CaliberGroup.PISTOL_SMG, 18);
		registerDefault("tacz:762x25", "7.62x25mm Tokarev", CaliberGroup.PISTOL_SMG, 19);
		registerDefault("tacz:57x28", "5.7x28mm", CaliberGroup.PISTOL_SMG, 26);
		registerDefault("tacz:46x30", "4.6x30mm", CaliberGroup.PISTOL_SMG, 26);
		registerDefault("tacz:22wmr", ".22 WMR", CaliberGroup.PISTOL_SMG, 14);

		// Magnum Pistol (batch = 20)
		registerDefault("tacz:357mag", ".357 Magnum", CaliberGroup.MAGNUM_PISTOL, 25);
		registerDefault("tacz:50ae", ".50 AE", CaliberGroup.MAGNUM_PISTOL, 26);
		registerDefault("tacz:500mag", ".500 S&W Magnum", CaliberGroup.MAGNUM_PISTOL, 28);

		// Shotgun (batch = 16)
		registerDefault("tacz:12g", "12 Gauge", CaliberGroup.SHOTGUN, 15);

		// Assault Rifle (batch = 24)
		registerDefault("tacz:545x39", "5.45x39mm", CaliberGroup.ASSAULT_RIFLE, 28);
		registerDefault("tacz:556x45", "5.56x45mm NATO", CaliberGroup.ASSAULT_RIFLE, 28);
		registerDefault("tacz:58x42", "5.8x42mm", CaliberGroup.ASSAULT_RIFLE, 30);
		registerDefault("tacz:300blk", ".300 Blackout", CaliberGroup.ASSAULT_RIFLE, 30);
		registerDefault("tacz:762x39", "7.62x39mm", CaliberGroup.ASSAULT_RIFLE, 31);

		// Battle / Sniper (batch = 16)
		registerDefault("tacz:68x51fury", "6.8x51mm Fury", CaliberGroup.BATTLE_SNIPER, 38);
		registerDefault("tacz:308", "7.62x51mm (.308 Win)", CaliberGroup.BATTLE_SNIPER, 38);
		registerDefault("tacz:762x54", "7.62x54mmR", CaliberGroup.BATTLE_SNIPER, 39);
		registerDefault("tacz:30_06", ".30-06 Springfield", CaliberGroup.BATTLE_SNIPER, 38);
		registerDefault("tacz:45_70", ".45-70 Government", CaliberGroup.BATTLE_SNIPER, 34);
		registerDefault("tacz:792x57", "7.92x57mm Mauser", CaliberGroup.BATTLE_SNIPER, 38);

		// Heavy Sniper (batch = 10)
		registerDefault("tacz:338", ".338 Lapua Magnum", CaliberGroup.HEAVY_SNIPER, 48);
		registerDefault("tacz:50bmg", ".50 BMG", CaliberGroup.HEAVY_SNIPER, 54);
	}

	private TaCZCaliberCatalog() {
	}

	private static void registerDefault(String ammoId, String displayName, CaliberGroup group, int basePen) {
		DEFAULT_SPECS.put(ammoId, new CaliberSpec(ammoId, displayName, group, basePen));
	}

	public static boolean isBallisticAmmoId(String ammoId) {
		if (ammoId == null || !VALID_AMMO_ID.matcher(ammoId).matches()) {
			return false;
		}
		String lower = ammoId.toLowerCase(Locale.ROOT);
		return !lower.contains("40mm") && !lower.contains("rpg_rocket");
	}

	public static List<CaliberSpec> getDefaultCalibers() {
		return List.copyOf(DEFAULT_SPECS.values());
	}

	public static synchronized void discoverRuntimeCalibers() {
		if (runtimeDiscovered) {
			return;
		}
		runtimeDiscovered = true;
		try {
			Class<?> apiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
			Method m = apiClass.getMethod("getAllCommonAmmoIndex");
			Object raw = m.invoke(null);
			if (raw instanceof Set<?> entries) {
				for (Object entryObj : entries) {
					if (entryObj instanceof Map.Entry<?, ?> entry && entry.getKey() != null) {
						String id = entry.getKey().toString();
						registerRuntimeCaliber(id);
					}
				}
			}
		} catch (Throwable ignored) {
			// Zero-hard-dependency: TaCZ is optional at compile/test time
		}
	}

	public static synchronized void registerRuntimeCaliber(String ammoId) {
		if (!isBallisticAmmoId(ammoId) || DEFAULT_SPECS.containsKey(ammoId) || RUNTIME_SPECS.containsKey(ammoId)) {
			return;
		}
		RUNTIME_SPECS.put(ammoId, buildDynamicSpec(ammoId));
	}

	public static synchronized List<CaliberSpec> getAllKnownCalibers() {
		discoverRuntimeCalibers();
		List<CaliberSpec> out = new ArrayList<>(DEFAULT_SPECS.values());
		out.addAll(RUNTIME_SPECS.values());
		return Collections.unmodifiableList(out);
	}

	public static synchronized List<CaliberSpec> getAllCalibersWithExtras(Collection<String> extraAmmoIds) {
		discoverRuntimeCalibers();
		Map<String, CaliberSpec> merged = new LinkedHashMap<>(DEFAULT_SPECS);
		merged.putAll(RUNTIME_SPECS);
		if (extraAmmoIds != null) {
			for (String id : extraAmmoIds) {
				if (isBallisticAmmoId(id) && !merged.containsKey(id)) {
					merged.put(id, buildDynamicSpec(id));
				}
			}
		}
		return List.copyOf(merged.values());
	}

	public static CaliberSpec getSpecOrDynamic(String ammoId) {
		CaliberSpec known = DEFAULT_SPECS.get(ammoId);
		if (known != null) {
			return known;
		}
		synchronized (TaCZCaliberCatalog.class) {
			CaliberSpec rt = RUNTIME_SPECS.get(ammoId);
			if (rt != null) {
				return rt;
			}
		}
		return buildDynamicSpec(ammoId);
	}

	public static CaliberSpec buildDynamicSpec(String ammoId) {
		String clean = ammoId == null ? "unknown" : ammoId;
		int colon = clean.indexOf(':');
		String path = colon >= 0 ? clean.substring(colon + 1) : clean;
		String label = path.replace('_', ' ').toUpperCase(Locale.ROOT);
		int basePen = TaCZBulletHelper.estimateBaseCaliberPenetration(clean);
		return new CaliberSpec(clean, label, CaliberGroup.ASSAULT_RIFLE, basePen);
	}
}
