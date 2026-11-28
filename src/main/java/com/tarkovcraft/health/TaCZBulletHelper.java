package com.tarkovcraft.health;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.entity.TarkovAiBulletEntity;
import com.tarkovcraft.entity.TarkovAiFirearmController;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Zero-hard-dependency bridge to TaCZ Refabricated's EntityKineticBullet.
 * Extracts gun ID, ammo ID, and the AmmoTier (HP / FMJ / AP / IGN) captured when the projectile spawns,
 * computing effective penetration power and flesh damage multiplier.
 */
public final class TaCZBulletHelper {
	private record BulletGetters(@Nullable Method getGunId, @Nullable Method getAmmoId) {
	}

	private static final ConcurrentHashMap<Class<?>, BulletGetters> GETTER_CACHE = new ConcurrentHashMap<>();
	private static final Map<Integer, AmmoTier> PROJECTILE_TIERS = new ConcurrentHashMap<>();

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

	public static void onEntityLoad(Entity entity) {
		if (!(entity instanceof ProjectileEntity projectile) || !isTaczProjectile(entity)
				|| !(projectile.getOwner() instanceof LivingEntity shooter)) {
			return;
		}
		ItemStack gun = shooter.getMainHandStack();
		if (AmmoTier.isTaczGunStack(gun) && gun.getNbt() != null
				&& gun.getNbt().contains("GunId") && gun.getNbt().getString("GunId").equals(invokeCachedGetter(entity, true))) {
			PROJECTILE_TIERS.put(entity.getId(), AmmoTier.getLoadedTierFromGun(gun));
		}
		if (shooter instanceof ServerPlayerEntity player) {
			MedicalUseManager.interruptForCombat(player);
			TarkovAiFirearmController.reportNoise(player, 48);
		}
	}

	public static void onEntityUnload(Entity entity) {
		PROJECTILE_TIERS.remove(entity.getId());
	}

	public static void clearProjectiles() {
		PROJECTILE_TIERS.clear();
	}

	private static boolean isTaczProjectile(@Nullable Entity entity) {
		return entity != null && entity.getClass().getName().contains("EntityKineticBullet");
	}

	public static BulletInfo inspectDamageSource(DamageSource source) {
		Entity direct = source.getSource();
		Entity attacker = source.getAttacker();
		if (direct instanceof TarkovAiBulletEntity bullet && bullet.bulletInfo() != null) return bullet.bulletInfo();

		boolean isTacz = isTaczProjectile(direct)
			|| (source.getName() != null && source.getName().toLowerCase(Locale.ROOT).contains("tacz.bullet"));
		if (isTacz) {
			AmmoTier loadedTier = direct != null ? PROJECTILE_TIERS.getOrDefault(direct.getId(), AmmoTier.FMJ) : AmmoTier.FMJ;
			String gunStr = direct != null ? invokeCachedGetter(direct, true) : "";
			String ammoStr = direct != null ? invokeCachedGetter(direct, false) : "";
			return ballisticInfo(gunStr, ammoStr, loadedTier);
		}

		if (attacker instanceof LivingEntity living) {
			ItemStack main = living.getMainHandStack();
			String wep = !main.isEmpty() ? main.getName().getString() : source.getName();
			String ammo = (direct != null && direct != attacker) ? direct.getName().getString() : "Melee";
			return new BulletInfo(false, wep, ammo, AmmoTier.FMJ, 15, 1.0f, 1.0f);
		}

		return new BulletInfo(false, source.getName(), "", AmmoTier.FMJ, 0, 1.0f, 0.0f);
	}

	public static BulletInfo ballisticInfo(String gun, String ammo, AmmoTier tier) {
		return new BulletInfo(true, prettifyId(gun, "Firearm"), prettifyId(ammo, "Ballistic") + " [" + tier.getBadge() + "]",
			tier, computePenetration(ammo, tier), tier.getDamageMultiplier(), tier.getArmorWearMultiplier());
	}

	private static BulletGetters resolveGetters(Class<?> bulletClass) {
		return GETTER_CACHE.computeIfAbsent(bulletClass, cls -> {
			Method gun = null;
			Method ammo = null;
			try {
				gun = cls.getMethod("getGunId");
			} catch (NoSuchMethodException ignored) {
			}
			try {
				ammo = cls.getMethod("getAmmoId");
			} catch (NoSuchMethodException ignored) {
			}
			return new BulletGetters(gun, ammo);
		});
	}

	private static String invokeCachedGetter(Entity bullet, boolean isGun) {
		try {
			BulletGetters getters = resolveGetters(bullet.getClass());
			Method m = isGun ? getters.getGunId() : getters.getAmmoId();
			if (m == null) {
				return "";
			}
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
		if (lower.contains("50bmg")) return 54;
		if (lower.contains("338")) return 48;
		if (lower.contains("762x54")) return 39;
		if (lower.contains("308") || lower.contains("30_06") || lower.contains("68x51") || lower.contains("792x57")) return 38;
		if (lower.contains("45_70")) return 34;
		if (lower.contains("762x39")) return 31;
		if (lower.contains("58x42") || lower.contains("300blk")) return 30;
		if (lower.contains("556") || lower.contains("545")) return 28;
		if (lower.contains("500mag")) return 28;
		if (lower.contains("57x28") || lower.contains("46x30") || lower.contains("50ae")) return 26;
		if (lower.contains("357")) return 25;
		if (lower.contains("762x25")) return 19;
		if (lower.contains("9mm") || lower.contains("45acp")) return 18;
		if (lower.contains("12g")) return 15;
		if (lower.contains("22wmr") || lower.contains("22lr") || lower.contains("22")) return 14;
		return 22;
	}
}
