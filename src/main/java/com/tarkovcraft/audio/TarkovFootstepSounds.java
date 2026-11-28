package com.tarkovcraft.audio;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class TarkovFootstepSounds {
	public enum Gait {
		WALK(26.0f), SPRINT(34.0f), SNEAK(TarkovSoundType.SNEAK_RANGE);

		private final float range;

		Gait(float range) {
			this.range = range;
		}
	}

	public enum Surface {
		CONCRETE, METAL, WOOD, GRASS, GRAVEL, GLASS, SNOW, WATER;

		private final SoundEvent[] sounds = new SoundEvent[Gait.values().length];

		Surface() {
			for (Gait gait : Gait.values()) {
				sounds[gait.ordinal()] = event("step." + name().toLowerCase(Locale.ROOT) + "."
					+ gait.name().toLowerCase(Locale.ROOT), gait.range);
			}
		}

		public SoundEvent sound(Gait gait) {
			return sounds[gait.ordinal()];
		}
	}

	public static final SoundEvent GEAR_WALK = event("gear.rustle.walk", Gait.WALK.range);
	public static final SoundEvent GEAR_HEAVY = event("gear.rustle.heavy", Gait.SPRINT.range);

	private TarkovFootstepSounds() {}

	public static void register() {
		for (Surface surface : Surface.values()) {
			for (SoundEvent sound : surface.sounds) {
				Registry.register(Registries.SOUND_EVENT, sound.getId(), sound);
			}
		}
		Registry.register(Registries.SOUND_EVENT, GEAR_WALK.getId(), GEAR_WALK);
		Registry.register(Registries.SOUND_EVENT, GEAR_HEAVY.getId(), GEAR_HEAVY);
	}

	public static boolean isPlayerMovement(Identifier id) {
		return "tarkovcraft".equals(id.getNamespace())
			&& (id.getPath().startsWith("step.") || id.getPath().startsWith("gear.rustle."));
	}

	static float movementRange(Identifier id) {
		if ("tarkovcraft".equals(id.getNamespace())) {
			String path = id.getPath();
			if (path.startsWith("step.")) {
				if (path.endsWith(".sneak")) return Gait.SNEAK.range;
				return path.endsWith(".sprint") ? Gait.SPRINT.range : Gait.WALK.range;
			}
			if (GEAR_WALK.getId().equals(id)) return Gait.WALK.range;
			if (GEAR_HEAVY.getId().equals(id)) return Gait.SPRINT.range;
		}
		return TarkovSoundType.MOVEMENT_RANGE;
	}

	private static SoundEvent event(String path, float range) {
		// Deliver to the farthest possible listener. The client applies its own hearing profile.
		return SoundEvent.of(new Identifier("tarkovcraft", path), range * HearingProfile.ACTIVE_RANGE);
	}
}
