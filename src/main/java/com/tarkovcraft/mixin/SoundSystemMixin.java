package com.tarkovcraft.mixin;

import com.tarkovcraft.audio.TarkovFootstepSounds;
import com.tarkovcraft.client.TarkovSoundEngine;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundInstanceListener;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.client.gui.hud.SubtitlesHud;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
abstract class SoundSystemMixin {
	@Shadow
	private float getAdjustedVolume(float volume, SoundCategory category) {
		throw new AssertionError();
	}

	@Redirect(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/sound/SoundInstanceListener;onSoundPlayed(Lnet/minecraft/client/sound/SoundInstance;Lnet/minecraft/client/sound/WeightedSoundSet;)V"))
	private void tarkovcraft$notifySoundListener(SoundInstanceListener listener, SoundInstance sound, WeightedSoundSet set) {
		if (!(listener instanceof SubtitlesHud) || !TarkovSoundEngine.isTactical(sound)) listener.onSoundPlayed(sound, set);
	}

	@Redirect(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/sound/SoundInstance;getAttenuationType()Lnet/minecraft/client/sound/SoundInstance$AttenuationType;"))
	private SoundInstance.AttenuationType tarkovcraft$attenuation(SoundInstance sound) {
		// Distance gain is calculated in game units. OpenAL still receives the source position.
		return TarkovSoundEngine.mix(sound).positional() ? SoundInstance.AttenuationType.NONE : sound.getAttenuationType();
	}

	@ModifyVariable(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At(value = "STORE"), ordinal = 2)
	private float tarkovcraft$initialVolume(float volume, SoundInstance sound) {
		return tarkovcraft$mixVolume(volume, sound);
	}

	@ModifyVariable(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("STORE"), ordinal = 1)
	private float tarkovcraft$ambientRange(float range, SoundInstance sound) {
		return range * TarkovSoundEngine.ambientRange(sound);
	}

	@Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
	private void tarkovcraft$volume(SoundInstance sound, CallbackInfoReturnable<Float> callback) {
		callback.setReturnValue(tarkovcraft$mixVolume(callback.getReturnValueF(), sound));
	}

	@Unique
	private float tarkovcraft$mixVolume(float adjustedVolume, SoundInstance sound) {
		float gain = TarkovSoundEngine.mix(sound).gain();
		if (TarkovFootstepSounds.isPlayerMovement(sound.getId())) {
			// Keep the sprint gain through distance attenuation before applying the output ceiling.
			return getAdjustedVolume(sound.getVolume() * gain, sound.getCategory());
		}
		return adjustedVolume * gain;
	}

	@Inject(method = "getAdjustedPitch", at = @At("RETURN"), cancellable = true)
	private void tarkovcraft$pitch(SoundInstance sound, CallbackInfoReturnable<Float> callback) {
		callback.setReturnValue(callback.getReturnValueF() * TarkovSoundEngine.mix(sound).pitch());
	}
}
