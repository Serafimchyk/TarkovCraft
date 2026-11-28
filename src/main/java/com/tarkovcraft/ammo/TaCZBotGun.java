package com.tarkovcraft.ammo;

import com.tarkovcraft.TarkovCraftMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;

/** Resolves the installed TaCZ pack's server-side gun data without a hard dependency. */
public record TaCZBotGun(Identifier id, Identifier ammo, Identifier display, int capacity, int shotTicks,
		String fireMode, boolean openBolt, int pellets, float damage, double speed, double gravity, double friction) {
	private static final Bridge BRIDGE = loadBridge();

	@Nullable
	public static TaCZBotGun find(String gunId) {
		if (BRIDGE == null) return null;
		Identifier id = new Identifier(gunId);
		try {
			return BRIDGE.read(id);
		} catch (ReflectiveOperationException | LinkageError error) {
			TarkovCraftMod.LOGGER.warn("Could not read TaCZ gun {}", id, error);
			return null;
		}
	}

	@Nullable
	private static Bridge loadBridge() {
		if (!FabricLoader.getInstance().isModLoaded("tacz")) return null;
		try {
			var api = Class.forName("com.tacz.guns.api.TimelessAPI");
			var index = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
			var gun = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
			var bullet = Class.forName("com.tacz.guns.resource.pojo.data.gun.BulletData");
			var assets = Class.forName("com.tacz.guns.api.DefaultAssets");
			return new Bridge(api.getMethod("getCommonGunIndex", Identifier.class), index.getMethod("getGunData"),
				index.getMethod("getBulletData"), gun.getMethod("getAmmoId"), gun.getMethod("getAmmoAmount"),
				gun.getMethod("getRoundsPerMinute"), gun.getMethod("getFireModeSet"), gun.getMethod("getBolt"),
				bullet.getMethod("getBulletAmount"), bullet.getMethod("getDamageAmount"), bullet.getMethod("getSpeed"),
				bullet.getMethod("getGravity"), bullet.getMethod("getFriction"),
				(Identifier) assets.getField("DEFAULT_GUN_DISPLAY_ID").get(null));
		} catch (ReflectiveOperationException | LinkageError error) {
			TarkovCraftMod.LOGGER.warn("TaCZ server gun data API is unavailable", error);
			return null;
		}
	}

	private record Bridge(Method lookup, Method gun, Method bullet, Method ammo, Method capacity, Method rpm,
			Method fireModes, Method bolt, Method pellets, Method damage, Method speed, Method gravity, Method friction,
			Identifier display) {
		@Nullable
		TaCZBotGun read(Identifier id) throws ReflectiveOperationException {
			var result = (Optional<?>) lookup.invoke(null, id);
			if (result.isEmpty()) return null;
			Object index = result.get();
			Object gunData = gun.invoke(index);
			Object bulletData = bullet.invoke(index);
			var modes = (java.util.List<?>) fireModes.invoke(gunData);
			if (modes.isEmpty()) return null;
			String mode = modes.stream().map(value -> ((Enum<?>) value).name())
				.filter("AUTO"::equals).findFirst().orElse(((Enum<?>) modes.get(0)).name());
			String boltType = ((Enum<?>) bolt.invoke(gunData)).name();
			int interval = Math.max(1, (int) Math.ceil(1200.0 / Math.max(1, (int) rpm.invoke(gunData))));
			if ("MANUAL_ACTION".equals(boltType)) interval = Math.max(8, interval);
			int count = Math.max(1, Math.min(16, (int) pellets.invoke(bulletData)));
			return new TaCZBotGun(id, (Identifier) ammo.invoke(gunData), display, (int) capacity.invoke(gunData),
				interval, mode, "OPEN_BOLT".equals(boltType), count, (float) damage.invoke(bulletData) / count,
				Math.max(1.0, (float) speed.invoke(bulletData) / 20.0), Math.max(0.0, (float) gravity.invoke(bulletData)) / 20.0,
				Math.max(0.0, Math.min(0.2, (float) friction.invoke(bulletData))));
		}
	}
}
