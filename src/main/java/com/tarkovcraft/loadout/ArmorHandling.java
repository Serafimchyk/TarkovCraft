package com.tarkovcraft.loadout;

import com.tarkovcraft.item.TarkovArmorItem;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public record ArmorHandling(int movementPenalty, int ergonomicsPenalty) {
	private static final ArmorHandling NONE = new ArmorHandling(0, 0);
	private static final UUID MOVEMENT_MODIFIER_ID = UUID.fromString("9026eecf-82ed-4745-ae0a-c56cb8aef943");

	public static ArmorHandling forArmor(EquipmentSlotType slot, int armorClass, float weight) {
		if (armorClass <= 0) return NONE;
		if (slot == EquipmentSlotType.HELMET) {
			if (weight <= 1.3f) return NONE;
			float progress = Math.min(1.0f, (weight - 1.3f) / (4.1f - 1.3f));
			return new ArmorHandling(Math.round(6 * progress), Math.round(10 * progress));
		}
		if (slot != EquipmentSlotType.BODY_ARMOR || weight <= 5.2f) return NONE;
		if (weight <= 7.2f) {
			float progress = (weight - 5.2f) / 2.0f;
			return new ArmorHandling(Math.round(2 * progress), Math.round(3 * progress));
		}
		if (weight <= 11.5f) {
			float progress = (weight - 7.2f) / 4.3f;
			return new ArmorHandling(2 + Math.round(3 * progress), 3 + Math.round(6 * progress));
		}
		float progress = Math.min(1.0f, (weight - 11.5f) / 3.0f);
		return new ArmorHandling(5 + Math.round(7 * progress), 9 + Math.round(11 * progress));
	}

	public static ArmorHandling equipped(PlayerLoadout loadout) {
		ArmorHandling vest = equippedAt(loadout, EquipmentSlotType.BODY_ARMOR);
		ArmorHandling helmet = equippedAt(loadout, EquipmentSlotType.HELMET);
		if (vest == NONE) return helmet;
		if (helmet == NONE) return vest;
		return new ArmorHandling(vest.movementPenalty + helmet.movementPenalty,
			vest.ergonomicsPenalty + helmet.ergonomicsPenalty);
	}

	private static ArmorHandling equippedAt(PlayerLoadout loadout, EquipmentSlotType slot) {
		return loadout.getEquipment(slot).getItem() instanceof TarkovArmorItem armor
			? armor.getHandling() : NONE;
	}

	public float aimDuration(float seconds) {
		return seconds * (1.0f + ergonomicsPenalty / 100.0f);
	}

	public static void updateMovement(ServerPlayerEntity player, PlayerLoadout loadout) {
		var speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null) return;
		int penalty = player.isCreative() || player.isSpectator() ? 0 : equipped(loadout).movementPenalty();
		double modifier = -penalty / 100.0;
		var previous = speed.getModifier(MOVEMENT_MODIFIER_ID);
		if (previous != null && previous.getValue() == modifier) return;
		if (previous != null) speed.removeModifier(MOVEMENT_MODIFIER_ID);
		if (penalty > 0) {
			speed.addTemporaryModifier(new EntityAttributeModifier(MOVEMENT_MODIFIER_ID, "TarkovCraft armor weight",
				modifier, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
		}
	}
}
