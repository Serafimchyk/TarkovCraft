package com.tarkovcraft.health;

import net.minecraft.nbt.NbtCompound;

/**
 * Immutable record of a single tactical hit (used for recent hit log and the Tarkov KIA Death Screen).
 */
public record DamageReport(
	HitZone hitZone,
	BodyPart primaryPart,
	float rawDamage,
	float finalDamage,
	boolean overflowedToThorax,
	boolean armorBlocked,
	boolean ricocheted,
	boolean headEyesOneshot,
	String killerName,
	String weaponName,
	String ammoName,
	float distanceMeters
) {
	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("Zone", hitZone.getId());
		nbt.putString("Part", primaryPart.getId());
		nbt.putFloat("Raw", rawDamage);
		nbt.putFloat("Final", finalDamage);
		nbt.putBoolean("Overflow", overflowedToThorax);
		nbt.putBoolean("Blocked", armorBlocked);
		nbt.putBoolean("Ricochet", ricocheted);
		nbt.putBoolean("Oneshot", headEyesOneshot);
		nbt.putString("Killer", killerName == null ? "" : killerName);
		nbt.putString("Weapon", weaponName == null ? "" : weaponName);
		nbt.putString("Ammo", ammoName == null ? "" : ammoName);
		nbt.putFloat("Dist", distanceMeters);
		return nbt;
	}

	public static DamageReport fromNbt(NbtCompound nbt) {
		return new DamageReport(
			HitZone.byId(nbt.getString("Zone")),
			BodyPart.byId(nbt.getString("Part")),
			nbt.getFloat("Raw"),
			nbt.getFloat("Final"),
			nbt.getBoolean("Overflow"),
			nbt.getBoolean("Blocked"),
			nbt.getBoolean("Ricochet"),
			nbt.getBoolean("Oneshot"),
			nbt.getString("Killer"),
			nbt.getString("Weapon"),
			nbt.getString("Ammo"),
			nbt.getFloat("Dist")
		);
	}
}
