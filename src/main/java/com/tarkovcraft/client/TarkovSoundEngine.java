package com.tarkovcraft.client;

import com.tarkovcraft.audio.HearingProfile;
import com.tarkovcraft.audio.TarkovSoundType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.WeakHashMap;

public final class TarkovSoundEngine {
	public record Mix(float gain, float pitch, boolean positional) {}
	private record Occlusion(long tick, int blocks) {}

	private static final Mix UNCHANGED = new Mix(1.0f, 1.0f, false);
	private static final Map<SoundInstance, Occlusion> OCCLUSION = new WeakHashMap<>();
	private static final Map<SoundInstance, Boolean> GUNFIRE = new WeakHashMap<>();

	private TarkovSoundEngine() {}

	public static HearingProfile profile(MinecraftClient client) {
		return client.player == null ? HearingProfile.DEFAULT : HearingProfile.forLoadout(ClientLoadoutCache.getLoadout());
	}

	public static void markGunfire(SoundInstance sound) {
		GUNFIRE.put(sound, Boolean.TRUE);
	}

	public static boolean isTactical(SoundInstance sound) {
		return classify(sound) != TarkovSoundType.OTHER;
	}

	public static float ambientRange(SoundInstance sound) {
		return !sound.isRelative() && !isTactical(sound)
			&& sound.getCategory() != SoundCategory.MUSIC && sound.getCategory() != SoundCategory.RECORDS
			&& sound.getCategory() != SoundCategory.VOICE ? Math.min(1.0f, profile(MinecraftClient.getInstance()).range()) : 1.0f;
	}

	public static Mix mix(SoundInstance sound) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.world == null) return UNCHANGED;
		TarkovSoundType type = classify(sound);
		HearingProfile profile = profile(client);
		if (sound.isRelative()) {
			return switch (type) {
				case GUNFIRE_EXPLOSION -> new Mix(profile.impulseGain(), 1.0f, false);
				case TACTICAL_MOVEMENT -> new Mix(profile.ambientGain(), profile.pitch(), false);
				case OTHER -> UNCHANGED;
			};
		}
		if (type == TarkovSoundType.OTHER) {
			SoundCategory category = sound.getCategory();
			if (!profile.muffled() || category == SoundCategory.MUSIC || category == SoundCategory.RECORDS
				|| category == SoundCategory.VOICE) return UNCHANGED;
			return new Mix(profile.ambientGain(), profile.pitch(), false);
		}

		Vec3d listener = client.gameRenderer.getCamera().getPos();
		Vec3d source = new Vec3d(sound.getX(), sound.getY(), sound.getZ());
		float distance = (float) listener.distanceTo(source);
		float gain;
		float pitch;
		if (type == TarkovSoundType.GUNFIRE_EXPLOSION) {
			gain = impulseFalloff(distance);
			if (distance < 25.0f) gain *= profile.impulseGain();
			pitch = distance < 100.0f ? 1.0f
				: MathHelper.lerp(MathHelper.clamp((distance - 100.0f) / 120.0f, 0.0f, 1.0f), 0.92f, 0.86f);
		} else {
			float range = TarkovSoundType.movementRange(sound.getId()) * profile.range();
			gain = range > 0.0f ? (1.0f - MathHelper.clamp(distance / range, 0.0f, 1.0f)) * profile.ambientGain() : 0.0f;
			pitch = profile.pitch();
		}
		if (gain > 0.0f && (type == TarkovSoundType.TACTICAL_MOVEMENT || distance < 25.0f)) {
			int blocks = obstruction(client, sound, listener, source, distance);
			gain *= (float) Math.pow(0.72f, blocks);
			if (blocks > 0) pitch *= 0.88f;
		}
		return new Mix(gain, pitch, true);
	}

	private static TarkovSoundType classify(SoundInstance sound) {
		if (GUNFIRE.containsKey(sound)) return TarkovSoundType.GUNFIRE_EXPLOSION;
		TarkovSoundType type = TarkovSoundType.classify(sound.getId(), sound.getCategory());
		if (sound.getSound() == null || type == TarkovSoundType.GUNFIRE_EXPLOSION) return type;
		TarkovSoundType sample = TarkovSoundType.classify(sound.getSound().getIdentifier(), sound.getCategory());
		return sample == TarkovSoundType.OTHER ? type : sample;
	}

	private static float impulseFalloff(float distance) {
		if (distance < 30.0f) return MathHelper.lerp(distance / 30.0f, 1.0f, 0.78f);
		if (distance < 100.0f) return MathHelper.lerp((distance - 30.0f) / 70.0f, 0.78f, 0.37f);
		return 0.37f * (1.0f - MathHelper.clamp((distance - 100.0f) / 120.0f, 0.0f, 1.0f));
	}

	private static int obstruction(MinecraftClient client, SoundInstance sound, Vec3d listener, Vec3d source, float distance) {
		long tick = client.world.getTime();
		Occlusion cached = OCCLUSION.get(sound);
		if (cached != null && tick >= cached.tick() && tick - cached.tick() < 4) return cached.blocks();

		int probes = MathHelper.clamp(MathHelper.ceil(distance) - 1, 0, 8);
		BlockPos start = BlockPos.ofFloored(listener);
		BlockPos end = BlockPos.ofFloored(source);
		BlockPos previous = start;
		int blocks = 0;
		for (int i = 1; i <= probes; i++) {
			BlockPos pos = BlockPos.ofFloored(listener.lerp(source, i / (double) (probes + 1)));
			if (!pos.equals(previous) && !pos.equals(start) && !pos.equals(end)
				&& client.world.getBlockState(pos).isOpaqueFullCube(client.world, pos)) {
				blocks++;
			}
			previous = pos;
		}
		OCCLUSION.put(sound, new Occlusion(tick, blocks));
		return blocks;
	}
}
