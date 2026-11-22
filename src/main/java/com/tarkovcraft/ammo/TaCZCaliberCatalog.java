package com.tarkovcraft.ammo;

import com.tarkovcraft.health.TaCZBulletHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
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
		int basePenetration,
		EnumSet<AmmoTier> allowedTiers
	) {
		public CaliberSpec(String ammoId, String displayName, CaliberGroup group, int basePenetration) {
			this(ammoId, displayName, group, basePenetration, resolveAllowedTiers(ammoId, group));
		}

		public int getBatchSize() {
			return group.getBatchSize();
		}

		public boolean isTierAllowed(AmmoTier tier) {
			return tier != null && allowedTiers.contains(tier);
		}

		public List<AmmoTier> getAllowedTiersList() {
			List<AmmoTier> ordered = new ArrayList<>(4);
			for (AmmoTier tier : AmmoTier.values()) {
				if (allowedTiers.contains(tier)) {
					ordered.add(tier);
				}
			}
			return Collections.unmodifiableList(ordered);
		}

		public AmmoTier clampTier(AmmoTier requested) {
			AmmoTier target = requested != null ? requested : AmmoTier.FMJ;
			if (allowedTiers.contains(target)) {
				return target;
			}
			if (target == AmmoTier.IGN && allowedTiers.contains(AmmoTier.AP)) {
				return AmmoTier.AP;
			}
			if (target == AmmoTier.HP && allowedTiers.contains(AmmoTier.FMJ)) {
				return AmmoTier.FMJ;
			}
			return allowedTiers.contains(AmmoTier.FMJ) ? AmmoTier.FMJ : allowedTiers.iterator().next();
		}
	}

	private static final Map<String, CaliberSpec> DEFAULT_SPECS = new LinkedHashMap<>();
	private static final Map<String, CaliberSpec> RUNTIME_SPECS = new LinkedHashMap<>();
	private static boolean runtimeDiscovered = false;

	static {
		// Pistol / SMG (batch = 30)
		registerDefault("tacz:9mm", "9x19mm Parabellum", CaliberGroup.PISTOL_SMG, 18, EnumSet.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP));
		registerDefault("tacz:45acp", ".45 ACP", CaliberGroup.PISTOL_SMG, 18, EnumSet.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP));
		registerDefault("tacz:762x25", "7.62x25mm Tokarev", CaliberGroup.PISTOL_SMG, 19, EnumSet.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP));
		registerDefault("tacz:57x28", "5.7x28mm", CaliberGroup.PISTOL_SMG, 26, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:46x30", "4.6x30mm", CaliberGroup.PISTOL_SMG, 26, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:22wmr", ".22 WMR", CaliberGroup.PISTOL_SMG, 14, EnumSet.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP));

		// Magnum Pistol (batch = 20)
		registerDefault("tacz:357mag", ".357 Magnum", CaliberGroup.MAGNUM_PISTOL, 25, EnumSet.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP));
		registerDefault("tacz:50ae", ".50 AE", CaliberGroup.MAGNUM_PISTOL, 26, EnumSet.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP));
		registerDefault("tacz:500mag", ".500 S&W Magnum", CaliberGroup.MAGNUM_PISTOL, 28, EnumSet.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP));

		// Shotgun (batch = 16)
		registerDefault("tacz:12g", "12 Gauge", CaliberGroup.SHOTGUN, 15, EnumSet.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP));

		// Assault Rifle (batch = 24)
		registerDefault("tacz:545x39", "5.45x39mm", CaliberGroup.ASSAULT_RIFLE, 28, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:556x45", "5.56x45mm NATO", CaliberGroup.ASSAULT_RIFLE, 28, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:58x42", "5.8x42mm", CaliberGroup.ASSAULT_RIFLE, 30, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:300blk", ".300 Blackout", CaliberGroup.ASSAULT_RIFLE, 30, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:762x39", "7.62x39mm", CaliberGroup.ASSAULT_RIFLE, 31, EnumSet.allOf(AmmoTier.class));

		// Battle / Sniper (batch = 16)
		registerDefault("tacz:68x51fury", "6.8x51mm Fury", CaliberGroup.BATTLE_SNIPER, 38, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:308", "7.62x51mm (.308 Win)", CaliberGroup.BATTLE_SNIPER, 38, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:762x54", "7.62x54mmR", CaliberGroup.BATTLE_SNIPER, 39, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:30_06", ".30-06 Springfield", CaliberGroup.BATTLE_SNIPER, 38, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:45_70", ".45-70 Government", CaliberGroup.BATTLE_SNIPER, 34, EnumSet.allOf(AmmoTier.class));
		registerDefault("tacz:792x57", "7.92x57mm Mauser", CaliberGroup.BATTLE_SNIPER, 38, EnumSet.allOf(AmmoTier.class));

		// Heavy Sniper (batch = 10)
		registerDefault("tacz:338", ".338 Lapua Magnum", CaliberGroup.HEAVY_SNIPER, 48, EnumSet.of(AmmoTier.FMJ, AmmoTier.AP, AmmoTier.IGN));
		registerDefault("tacz:50bmg", ".50 BMG", CaliberGroup.HEAVY_SNIPER, 54, EnumSet.of(AmmoTier.FMJ, AmmoTier.AP, AmmoTier.IGN));
	}

	private TaCZCaliberCatalog() {
	}

	private static void registerDefault(String ammoId, String displayName, CaliberGroup group, int basePen, EnumSet<AmmoTier> allowedTiers) {
		DEFAULT_SPECS.put(ammoId, new CaliberSpec(ammoId, displayName, group, basePen, EnumSet.copyOf(allowedTiers)));
	}

	public static boolean isShotgunCaliber(String ammoId) {
		if (ammoId == null) return false;
		String l = ammoId.toLowerCase(Locale.ROOT);
		return l.contains("12g") || l.contains("20g") || l.contains("410") || l.contains("gauge") || l.contains("shot");
	}

	public static EnumSet<AmmoTier> resolveAllowedTiers(String ammoId, CaliberGroup group) {
		String l = ammoId == null ? "" : ammoId.toLowerCase(Locale.ROOT);
		if (isShotgunCaliber(l) || group == CaliberGroup.SHOTGUN) {
			return EnumSet.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP);
		}
		if (group == CaliberGroup.HEAVY_SNIPER || l.contains("50bmg") || l.contains("338")
			|| l.contains("127") || l.contains("12_7") || l.contains("145")) {
			return EnumSet.of(AmmoTier.FMJ, AmmoTier.AP, AmmoTier.IGN);
		}
		if (l.contains("57x28") || l.contains("46x30")) {
			return EnumSet.allOf(AmmoTier.class);
		}
		if (group == CaliberGroup.PISTOL_SMG || group == CaliberGroup.MAGNUM_PISTOL
			|| l.contains("9x18") || l.contains("9x19") || l.contains("380") || l.contains("45acp") || l.contains("9mm")) {
			return EnumSet.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP);
		}
		return EnumSet.allOf(AmmoTier.class);
	}

	public static boolean isTierAllowed(String ammoId, AmmoTier tier) {
		return getSpecOrDynamic(ammoId).isTierAllowed(tier);
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
