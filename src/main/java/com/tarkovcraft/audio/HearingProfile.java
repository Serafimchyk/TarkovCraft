package com.tarkovcraft.audio;

import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;

public record HearingProfile(float ambientGain, float range, float loudClamp, float pitch, boolean muffled) {
	public static final float ACTIVE_RANGE = 1.35f;
	public static final HearingProfile DEFAULT = new HearingProfile(1.0f, 1.0f, 1.0f, 1.0f, false);
	private static final HearingProfile HEADSET = new HearingProfile(1.35f, ACTIVE_RANGE, 0.72f, 1.0f, false);

	public static HearingProfile forLoadout(PlayerLoadout loadout) {
		float hearing = Math.min(hearing(loadout.getEquipment(EquipmentSlotType.HELMET)),
			hearing(loadout.getEquipment(EquipmentSlotType.FACE_MASK)));
		if (hearing < 1.0f) return new HearingProfile(hearing, hearing, 1.0f, 0.90f + 0.10f * hearing, true);
		return loadout.getEquipment(EquipmentSlotType.HEADSET).isEmpty() ? DEFAULT : HEADSET;
	}

	public static boolean isQuietTactical(Identifier id, SoundCategory category) {
		String path = id.getPath();
		if (isLoud(path)) return false;
		return category == SoundCategory.PLAYERS || path.contains("step") || path.contains("door")
			|| path.contains("gate") || path.contains("chest") || path.contains("barrel")
			|| path.contains("reload") || path.contains("search") || path.contains("grass") || path.contains("bush");
	}

	public static boolean isLoud(String path) {
		return path.contains("explode") || path.contains("explosion") || path.contains("shoot")
			|| path.contains("gunshot") || path.contains("firework") || path.contains("grenade");
	}

	private static float hearing(ItemStack stack) {
		return stack.getItem() instanceof TarkovArmorItem armor ? armor.getHearingMultiplier() : 1.0f;
	}
}
