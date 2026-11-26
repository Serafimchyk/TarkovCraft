package com.tarkovcraft.mixin;

import com.tarkovcraft.audio.HearingProfile;
import com.tarkovcraft.client.ClientLoadoutCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
abstract class SoundSystemMixin {
	@ModifyVariable(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("STORE"), ordinal = 1)
	private float tarkovcraft$attenuation(float range, SoundInstance sound) {
		HearingProfile profile = tarkovcraft$profile(sound);
		return profile.muffled() || tarkovcraft$isTactical(sound) && !tarkovcraft$isLoud(sound)
			? range * profile.range() : range;
	}

	@ModifyVariable(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("STORE"), ordinal = 2)
	private float tarkovcraft$initialVolume(float volume, SoundInstance sound) {
		return tarkovcraft$volume(volume, sound);
	}

	@Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
	private void tarkovcraft$tickingVolume(SoundInstance sound, CallbackInfoReturnable<Float> callback) {
		callback.setReturnValue(tarkovcraft$volume(callback.getReturnValueF(), sound));
	}

	@Inject(method = "getAdjustedPitch", at = @At("RETURN"), cancellable = true)
	private void tarkovcraft$pitch(SoundInstance sound, CallbackInfoReturnable<Float> callback) {
		callback.setReturnValue(callback.getReturnValueF() * tarkovcraft$profile(sound).pitch());
	}

	@Unique
	private static float tarkovcraft$volume(float volume, SoundInstance sound) {
		HearingProfile profile = tarkovcraft$profile(sound);
		if (profile == HearingProfile.DEFAULT) return volume;
		if (!profile.muffled()) {
			if (tarkovcraft$isLoud(sound)) return Math.min(volume, profile.loudClamp());
			if (!tarkovcraft$isTactical(sound)) return volume;
		}
		return Math.min(1.0f, volume * profile.ambientGain());
	}

	@Unique
	private static HearingProfile tarkovcraft$profile(SoundInstance sound) {
		if (MinecraftClient.getInstance().player == null || sound.getCategory() == SoundCategory.MUSIC
			|| sound.getCategory() == SoundCategory.RECORDS || sound.getCategory() == SoundCategory.VOICE
			|| sound.getCategory() == SoundCategory.MASTER && sound.isRelative()) return HearingProfile.DEFAULT;
		return HearingProfile.forLoadout(ClientLoadoutCache.getLoadout());
	}

	@Unique
	private static boolean tarkovcraft$isTactical(SoundInstance sound) {
		return HearingProfile.isQuietTactical(sound.getId(), sound.getCategory())
			|| sound.getSound() != null
				&& HearingProfile.isQuietTactical(sound.getSound().getIdentifier(), sound.getCategory());
	}

	@Unique
	private static boolean tarkovcraft$isLoud(SoundInstance sound) {
		return HearingProfile.isLoud(sound.getId().getPath())
			|| sound.getSound() != null && HearingProfile.isLoud(sound.getSound().getIdentifier().getPath());
	}
}
