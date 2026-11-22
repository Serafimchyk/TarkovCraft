package com.tarkovcraft.client;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.health.TaCZBulletHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only zero-hard-dependency helper that inspects TaCZ firearms via TimelessAPI reflection
 * and extracts caliber, fire mode, ammo count, barrel chamber state, loaded tier, and allowed tiers.
 */
@Environment(EnvType.CLIENT)
public final class TaCZGunInfoHelper {
	private record GunMeta(String ammoId, int baseMagCapacity, int rpm) {
	}

	public record GunSummary(
		String caliberName,
		String ammoId,
		String fireMode,
		int currentAmmo,
		int maxMag,
		boolean bulletInBarrel,
		AmmoTier loadedTier,
		int penetration,
		List<AmmoTier> allowedTiers
	) {
	}

	private static final ConcurrentHashMap<String, GunMeta> GUN_META_CACHE = new ConcurrentHashMap<>();
	private static final GunMeta EMPTY_META = new GunMeta("", 0, 0);

	private TaCZGunInfoHelper() {
	}

	public static GunSummary inspectGun(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return new GunSummary("Unknown", "", "", 0, 0, false, AmmoTier.FMJ, 22, List.of(AmmoTier.values()));
		}
		NbtCompound nbt = stack.getNbt();
		String gunId = nbt != null ? nbt.getString("GunId") : "";
		int curAmmo = nbt != null ? nbt.getInt("GunCurrentAmmoCount") : 0;
		boolean barrel = nbt != null && nbt.getBoolean("HasBulletInBarrel");
		String fireMode = nbt != null ? nbt.getString("GunFireMode").toUpperCase(Locale.ROOT) : "";
		AmmoTier loadedTier = AmmoTier.getLoadedTierFromGun(stack);

		GunMeta meta = resolveGunMeta(gunId);
		String ammoId = meta.ammoId();
		if (ammoId.isBlank() && nbt != null && nbt.contains("AmmoId")) {
			ammoId = nbt.getString("AmmoId");
		}

		String caliberName;
		List<AmmoTier> allowedTiers;
		int pen;
		if (!ammoId.isBlank()) {
			TaCZCaliberCatalog.CaliberSpec spec = TaCZCaliberCatalog.getSpecOrDynamic(ammoId);
			caliberName = spec.displayName();
			allowedTiers = spec.getAllowedTiersList();
			pen = TaCZBulletHelper.computePenetration(ammoId, loadedTier);
		} else {
			caliberName = "Ballistic";
			allowedTiers = List.of(AmmoTier.values());
			pen = TaCZBulletHelper.computePenetration("", loadedTier);
		}

		return new GunSummary(
			caliberName,
			ammoId,
			fireMode,
			Math.max(0, curAmmo),
			Math.max(curAmmo, meta.baseMagCapacity()),
			barrel,
			loadedTier,
			pen,
			allowedTiers
		);
	}

	private static GunMeta resolveGunMeta(String gunId) {
		if (gunId == null || gunId.isBlank()) {
			return EMPTY_META;
		}
		return GUN_META_CACHE.computeIfAbsent(gunId, TaCZGunInfoHelper::reflectGunMeta);
	}

	private static GunMeta reflectGunMeta(String gunId) {
		try {
			Identifier id = Identifier.tryParse(gunId);
			if (id == null) {
				return EMPTY_META;
			}
			Class<?> apiClass = Class.forName("com.tacz.guns.api.TimelessAPI");
			Method getGunIndex = apiClass.getMethod("getCommonGunIndex", Identifier.class);
			Object raw = getGunIndex.invoke(null, id);
			Object gunIndex = (raw instanceof Optional<?> opt) ? opt.orElse(null) : raw;
			if (gunIndex == null) {
				return EMPTY_META;
			}
			Object gunData = gunIndex.getClass().getMethod("getGunData").invoke(gunIndex);
			if (gunData == null) {
				return EMPTY_META;
			}

			String ammoId = "";
			try {
				Object rawAmmo = gunData.getClass().getMethod("getAmmoId").invoke(gunData);
				if (rawAmmo != null) {
					ammoId = rawAmmo.toString();
				}
			} catch (Throwable ignored) {
			}

			int magCap = 0;
			try {
				Object rawAmt = gunData.getClass().getMethod("getAmmoAmount").invoke(gunData);
				if (rawAmt instanceof Number n) {
					magCap = n.intValue();
				}
			} catch (Throwable ignored) {
			}

			int rpm = 0;
			try {
				Method mRpm = gunData.getClass().getMethod("getRoundsPerMinute");
				Object rawRpm = mRpm.invoke(gunData);
				if (rawRpm instanceof Number n) {
					rpm = n.intValue();
				}
			} catch (Throwable ignored) {
			}

			return new GunMeta(ammoId, Math.max(0, magCap), Math.max(0, rpm));
		} catch (Throwable ignored) {
			return EMPTY_META;
		}
	}
}
