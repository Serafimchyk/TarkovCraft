package com.tarkovcraft.health;

import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates strictly zonal, layered armor protection (outside-in: HELMET -> FACE_MASK -> BODY_ARMOR).
 * Only intact armor pieces whose coveredZones include the exact HitZone can ricochet, mitigate, or stop a hit.
 * Open zones and broken (0 durability) armor receive ZERO protection.
 */
public final class ZonalArmorHelper {
	private static final EquipmentSlotType[] ARMOR_LAYER_ORDER = {
		EquipmentSlotType.HELMET,
		EquipmentSlotType.FACE_MASK,
		EquipmentSlotType.BODY_ARMOR
	};

	private ZonalArmorHelper() {
	}

	public record ArmorResult(
		float remainingDamage,
		boolean protectedZone,
		boolean stoppedByArmor,
		boolean ricocheted,
		int absorbedArmorClass,
		int durabilityDamageApplied
	) {
	}

	private record ArmorLayer(ItemStack stack, TarkovArmorItem armor, int remainingDurability) {
	}

	public static ArmorResult evaluateHit(PlayerLoadout loadout, HitZone zone, float rawDamage, int penetrationPower) {
		return evaluateHit(loadout, zone, rawDamage, penetrationPower, 1.0f, false, false, 1.0f);
	}

	public static ArmorResult evaluateHit(
		PlayerLoadout loadout,
		HitZone zone,
		float rawDamage,
		int penetrationPower,
		float armorWearMultiplier,
		boolean bypassesArmor,
		boolean canRicochet,
		float ricochetRoll
	) {
		if (bypassesArmor || loadout == null || zone == null) {
			return new ArmorResult(rawDamage, false, false, false, 0, 0);
		}

		List<ArmorLayer> layers = new ArrayList<>(3);
		for (EquipmentSlotType slot : ARMOR_LAYER_ORDER) {
			ItemStack stack = loadout.getEquipment(slot);
			if (stack.isEmpty() || !(stack.getItem() instanceof TarkovArmorItem armor)) {
				continue;
			}
			int remainingDur = TarkovArmorItem.getRemainingDurability(stack);
			if (remainingDur > 0 && armor.coversZone(zone)) {
				layers.add(new ArmorLayer(stack, armor, remainingDur));
			}
		}

		if (layers.isEmpty()) {
			return new ArmorResult(rawDamage, false, false, false, 0, 0);
		}

		float currentDmg = rawDamage;
		int currentPen = Math.max(0, penetrationPower);
		float safeWearMult = Math.max(0.0f, armorWearMultiplier);
		int totalWear = 0;
		int highestClass = 0;

		for (ArmorLayer layer : layers) {
			ItemStack stack = layer.stack();
			TarkovArmorItem armor = layer.armor();
			int maxDur = Math.max(1, stack.getMaxDamage());
			float durRatio = layer.remainingDurability() / (float) maxDur;
			highestClass = Math.max(highestClass, armor.getArmorClass());

			if (canRicochet && zone.isHelmetRicochetZone() && armor.getRicochetChance() > 0.0f) {
				float effRicochet = Math.min(0.65f, armor.getRicochetChance() * (0.5f + 0.5f * durRatio));
				if (ricochetRoll < effRicochet) {
					int wear = Math.max(1, Math.round(currentDmg * 0.08f * armor.getMaterial().getDestructibility() * safeWearMult));
					applyWear(stack, maxDur, wear);
					totalWear += wear;
					return new ArmorResult(Math.max(1.0f, currentDmg * 0.05f), true, true, true, armor.getArmorClass(), totalWear);
				}
			}

			float effectiveThreshold = armor.getArmorClass() * 10.0f * (0.45f + 0.55f * durRatio);
			float baseWear = currentDmg * 0.25f * armor.getMaterial().getDestructibility() * safeWearMult;

			if (currentPen < effectiveThreshold) {
				int wear = Math.max(1, Math.round(baseWear));
				applyWear(stack, maxDur, wear);
				totalWear += wear;
				float blunt = Math.max(1.0f, currentDmg * armor.getMaterial().getBluntThroughput());
				return new ArmorResult(blunt, true, true, false, armor.getArmorClass(), totalWear);
			}

			int wear = Math.max(1, Math.round(baseWear * 1.20f));
			applyWear(stack, maxDur, wear);
			totalWear += wear;

			float overPen = currentPen - effectiveThreshold;
			float reduction = Math.max(0.10f, 0.30f - overPen * 0.01f);
			currentDmg = Math.max(1.0f, currentDmg * (1.0f - reduction));
			currentPen = Math.max(0, Math.round(currentPen - effectiveThreshold * 0.35f));
		}

		return new ArmorResult(currentDmg, true, false, false, highestClass, totalWear);
	}

	private static void applyWear(ItemStack stack, int maxDur, int wear) {
		stack.setDamage(Math.min(maxDur, stack.getDamage() + wear));
	}

	/**
	 * Returns the highest intact armor class covering the given zone, or 0 if exposed / broken.
	 */
	public static int getHighestProtectingClass(PlayerLoadout loadout, HitZone zone) {
		if (loadout == null || zone == null) {
			return 0;
		}
		int best = 0;
		for (EquipmentSlotType slot : ARMOR_LAYER_ORDER) {
			ItemStack stack = loadout.getEquipment(slot);
			if (stack.isEmpty() || !(stack.getItem() instanceof TarkovArmorItem armor)) {
				continue;
			}
			if (TarkovArmorItem.getRemainingDurability(stack) > 0 && armor.coversZone(zone)) {
				best = Math.max(best, armor.getArmorClass());
			}
		}
		return best;
	}
}
