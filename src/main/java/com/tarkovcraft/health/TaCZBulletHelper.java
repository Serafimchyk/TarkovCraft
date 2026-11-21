package com.tarkovcraft.health;

import com.tarkovcraft.ammo.AmmoTier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;

/**
 * Zero-hard-dependency bridge to TaCZ Refabricated's EntityKineticBullet.
 * Extracts gun ID, ammo ID, and loaded AmmoTier (HP / FMJ / AP / IGN) from the shooter's firearm,
 * computing effective penetration power and flesh damage multiplier.
 */
public final class TaCZBulletHelper {
	private TaCZBulletHelper() {
	}

	public record BulletInfo(
		boolean isTaczBullet,
		String weaponName,
		String ammoName,
		AmmoTier tier,
		int penetrationPower,
		float damageMultiplier,
		float armorWearMultiplier
	) {
	}

	public static BulletInfo inspectDamageSource(DamageSource source) {
		Entity direct = source.getSource();
		Entity attacker = source.getAttacker();

		AmmoTier loadedTier = AmmoTier.FMJ;
		if (attacker instanceof LivingEntity living) {
			ItemStack held = living.getMainHandStack();
			loadedTier = AmmoTier.getLoadedTierFromGun(held);
		}

		boolean isTacz = (direct != null && direct.getClass().getName().contains("EntityKineticBullet"))
			|| (source.getName() != null && source.getName().toLowerCase().contains("tacz.bullet"));
		if (isTacz) {
			String gunStr = direct != null ? invokeIdGetter(direct, "getGunId") : "";
			String ammoStr = direct != null ? invokeIdGetter(direct, "getAmmoId") : "";
			String cleanGun = prettifyId(gunStr, "Firearm");
			String cleanAmmo = prettifyId(ammoStr, "Ballistic") + " [" + loadedTier.getBadge() + "]";
			int pen = computePenetration(ammoStr, loadedTier);
			return new BulletInfo(true, cleanGun, cleanAmmo, loadedTier, pen,
				loadedTier.getDamageMultiplier(), loadedTier.getArmorWearMultiplier());
		}

		if (attacker instanceof LivingEntity living) {
			ItemStack main = living.getMainHandStack();
			String wep = !main.isEmpty() ? main.getName().getString() : source.getName();
			String ammo = (direct != null && direct != attacker) ? direct.getName().getString() : "Melee";
			return new BulletInfo(false, wep, ammo, AmmoTier.FMJ, 15, 1.0f, 1.0f);
		}

		return new BulletInfo(false, source.getName(), "", AmmoTier.FMJ, 0, 1.0f, 0.0f);
	}

	private static String invokeIdGetter(Entity bullet, String methodName) {
		try {
			Method m = bullet.getClass().getMethod(methodName);
			Object res = m.invoke(bullet);
			if (res instanceof Identifier id) {
				return id.toString();
			}
			return res != null ? res.toString() : "";
		} catch (Exception ignored) {
			return "";
		}
	}

	private static String prettifyId(String id, String fallback) {
		if (id == null || id.isBlank()) return fallback;
		int colon = id.indexOf(':');
		String path = colon >= 0 ? id.substring(colon + 1) : id;
		return path.replace('_', ' ').toUpperCase();
	}

	/**
	 * Computes final penetration power (0..75 scale against Armor Class 1..6 thresholds 10..60)
	 * by combining base caliber penetration with the AmmoTier delta.
	 */
	public static int computePenetration(String ammoId, AmmoTier tier) {
		int base = estimateBaseCaliberPenetration(ammoId);
		return Math.max(2, base + tier.getPenetrationDelta());
	}

	public static int estimateBaseCaliberPenetration(String ammoId) {
		if (ammoId == null) return 22;
		String lower = ammoId.toLowerCase();
		if (lower.contains("50bmg") || lower.contains("338")) return 50;
		if (lower.contains("308") || lower.contains("762x54") || lower.contains("30_06") || lower.contains("68x51") || lower.contains("45_70")) return 38;
		if (lower.contains("762x39") || lower.contains("58x42") || lower.contains("300blk")) return 30;
		if (lower.contains("556") || lower.contains("545")) return 28;
		if (lower.contains("57x28") || lower.contains("46x30") || lower.contains("50ae") || lower.contains("357") || lower.contains("500mag")) return 25;
		if (lower.contains("9mm") || lower.contains("45acp") || lower.contains("762x25")) return 18;
		if (lower.contains("12g") || lower.contains("22")) return 14;
		return 22;
	}
}
