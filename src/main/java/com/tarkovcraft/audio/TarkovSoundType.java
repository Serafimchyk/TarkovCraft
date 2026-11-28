package com.tarkovcraft.audio;

import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public enum TarkovSoundType {
	TACTICAL_MOVEMENT, GUNFIRE_EXPLOSION, OTHER;

	public static final Identifier SNEAK_STEP_ID = new Identifier("tarkovcraft", "sneak_step");
	public static final SoundEvent SNEAK_STEP = SoundEvent.of(SNEAK_STEP_ID);
	public static final float MOVEMENT_RANGE = 24.0f;
	public static final float SNEAK_RANGE = 9.0f;
	public static final float IMPULSE_RANGE = 220.0f;

	public static TarkovSoundType classify(Identifier id, SoundCategory category) {
		if (category == SoundCategory.MUSIC || category == SoundCategory.RECORDS || category == SoundCategory.VOICE) {
			return OTHER;
		}
		String path = id.getPath();
		if (path.contains("explode") || path.contains("explosion") || path.contains("gunshot")
			|| path.contains("gunfire") || path.contains("shoot") || "tacz".equals(id.getNamespace()) && path.contains("silence")) {
			return GUNFIRE_EXPLOSION;
		}
		if (path.contains("step") || path.contains("fall") || path.contains("land") || path.contains("armor.equip")
			|| path.contains("door") || path.contains("gate") || path.contains("chest") || path.contains("barrel")
			|| path.contains("reload") || path.contains("search") || path.contains("rustle") || path.contains("bandage")
			|| path.contains("heal") || path.contains("medical") || path.contains("stim")
			|| category == SoundCategory.PLAYERS && (path.contains("drink") || path.contains("eat"))) {
			return TACTICAL_MOVEMENT;
		}
		return OTHER;
	}

	public static float movementRange(Identifier id) {
		return SNEAK_STEP_ID.equals(id) ? SNEAK_RANGE : TarkovFootstepSounds.movementRange(id);
	}
}
