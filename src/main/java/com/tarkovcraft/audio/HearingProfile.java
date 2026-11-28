package com.tarkovcraft.audio;

import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import net.minecraft.item.ItemStack;

public record HearingProfile(float ambientGain, float range, float impulseGain, float pitch, boolean muffled) {
	public static final float ACTIVE_RANGE = 1.35f;
	public static final HearingProfile DEFAULT = new HearingProfile(1.0f, 1.0f, 1.0f, 1.0f, false);
	private static final HearingProfile HEADSET = new HearingProfile(1.40f, ACTIVE_RANGE, 0.70f, 1.0f, false);

	public static HearingProfile forLoadout(PlayerLoadout loadout) {
		if (!loadout.blocksHeadset() && !loadout.getEquipment(EquipmentSlotType.HEADSET).isEmpty()) return HEADSET;

		float hearing = Math.min(hearing(loadout.getEquipment(EquipmentSlotType.HELMET)),
			hearing(loadout.getEquipment(EquipmentSlotType.FACE_MASK)));
		return hearing < 1.0f ? new HearingProfile(hearing, hearing, 1.0f, 0.85f, true) : DEFAULT;
	}

	private static float hearing(ItemStack stack) {
		return stack.getItem() instanceof TarkovArmorItem armor ? armor.getHearingMultiplier() : 1.0f;
	}
}
