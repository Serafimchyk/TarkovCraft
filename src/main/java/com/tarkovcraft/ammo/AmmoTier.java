package com.tarkovcraft.ammo;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Tarkov ballistic ammo tiers for TaCZ ammunition:
 * - HP  (Hollow Point / Экспансивные): +30% flesh damage, -12 penetration (stops on Class 2-3, shreds limbs/face)
 * - FMJ (Standard / Стандартные PS/FMJ): baseline damage & penetration (Class 2-3)
 * - AP  (Armor-Piercing / Бронебойные BP/M855A1/M80): -8% flesh damage, +16 penetration (Class 4-5)
 * - IGN (Ultra-AP / Сверхбронебойные BS/Igolnik/M995/M61): -15% flesh damage, +28 penetration (Class 5-6)
 */
public enum AmmoTier {
	HP("hp", "HP", 1.30f, -12, 0.70f, Formatting.GREEN),
	FMJ("fmj", "PS/FMJ", 1.00f, 0, 1.00f, Formatting.GRAY),
	AP("ap", "BP/AP", 0.92f, 16, 1.25f, Formatting.GOLD),
	IGN("ign", "BS/M995", 0.85f, 28, 1.50f, Formatting.RED);

	public static final String NBT_AMMO_TIER = "TarkovAmmoTier";
	public static final String NBT_LOADED_TIER = "TarkovLoadedTier";

	private final String id;
	private final String badge;
	private final float damageMultiplier;
	private final int penetrationDelta;
	private final float armorWearMultiplier;
	private final Formatting color;

	AmmoTier(String id, String badge, float damageMultiplier, int penetrationDelta, float armorWearMultiplier, Formatting color) {
		this.id = id;
		this.badge = badge;
		this.damageMultiplier = damageMultiplier;
		this.penetrationDelta = penetrationDelta;
		this.armorWearMultiplier = armorWearMultiplier;
		this.color = color;
	}

	public String getId() {
		return id;
	}

	public String getBadge() {
		return badge;
	}

	public float getDamageMultiplier() {
		return damageMultiplier;
	}

	public int getPenetrationDelta() {
		return penetrationDelta;
	}

	public float getArmorWearMultiplier() {
		return armorWearMultiplier;
	}

	public Formatting getColor() {
		return color;
	}

	public String getTranslationKey() {
		return "ammotier.tarkovcraft." + id;
	}

	public static AmmoTier byId(String id) {
		if (id == null || id.isBlank()) return FMJ;
		for (AmmoTier tier : values()) {
			if (tier.id.equalsIgnoreCase(id) || tier.badge.equalsIgnoreCase(id)) {
				return tier;
			}
		}
		return FMJ;
	}

	public static boolean isTaczAmmoStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		Identifier id = Registries.ITEM.getId(stack.getItem());
		if ("tacz".equals(id.getNamespace()) && "ammo".equals(id.getPath())) {
			return true;
		}
		return stack.hasNbt() && stack.getNbt() != null && stack.getNbt().contains("AmmoId");
	}

	public static AmmoTier getFromAmmoStack(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasNbt() || stack.getNbt() == null) {
			return FMJ;
		}
		NbtCompound nbt = stack.getNbt();
		if (nbt.contains(NBT_AMMO_TIER)) {
			return byId(nbt.getString(NBT_AMMO_TIER));
		}
		return FMJ;
	}

	public static void setOnAmmoStack(ItemStack stack, AmmoTier tier) {
		if (stack == null || stack.isEmpty()) return;
		if (tier == FMJ) {
			if (stack.hasNbt() && stack.getNbt() != null) {
				stack.getNbt().remove(NBT_AMMO_TIER);
			}
		} else {
			stack.getOrCreateNbt().putString(NBT_AMMO_TIER, tier.id);
		}
	}

	public static AmmoTier getLoadedTierFromGun(ItemStack gunStack) {
		if (gunStack == null || gunStack.isEmpty() || !gunStack.hasNbt() || gunStack.getNbt() == null) {
			return FMJ;
		}
		NbtCompound nbt = gunStack.getNbt();
		if (nbt.contains(NBT_LOADED_TIER)) {
			return byId(nbt.getString(NBT_LOADED_TIER));
		}
		return FMJ;
	}

	public static void stampLoadedTierOnGun(ItemStack gunStack, AmmoTier tier) {
		if (gunStack == null || gunStack.isEmpty()) return;
		gunStack.getOrCreateNbt().putString(NBT_LOADED_TIER, tier.id);
	}

	public static ItemStack createTaczAmmoStack(String ammoId, AmmoTier tier, int count) {
		Identifier taczAmmoItemId = new Identifier("tacz", "ammo");
		Item item = Registries.ITEM.containsId(taczAmmoItemId)
			? Registries.ITEM.get(taczAmmoItemId)
			: Items.IRON_NUGGET;
		ItemStack stack = new ItemStack(item, Math.max(1, count));
		String fullAmmoId = ammoId.contains(":") ? ammoId : ("tacz:" + ammoId);
		stack.getOrCreateNbt().putString("AmmoId", fullAmmoId);
		if (tier != FMJ) {
			stack.getOrCreateNbt().putString(NBT_AMMO_TIER, tier.id);
		}
		return stack;
	}
}
