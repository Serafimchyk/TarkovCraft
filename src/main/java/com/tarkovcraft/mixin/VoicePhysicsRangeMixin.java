package com.tarkovcraft.mixin;

import com.tarkovcraft.compat.voicechat.VoicechatAdapter;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.kasper.vcdistance.ServerRange", remap = false)
abstract class VoicePhysicsRangeMixin {
	@Inject(method = "rangeTo", at = @At("RETURN"), cancellable = true, require = 0)
	private static void tarkovcraft$rangeForListener(@Coerce Object settings, @Coerce Object source,
			@Coerce Object listener, boolean whisper, double voiceRange, double whisperRange,
			CallbackInfoReturnable<Double> callback) {
		if (FabricLoader.getInstance().isModLoaded("vc-audio-distance")) {
			callback.setReturnValue(callback.getReturnValueD() * VoicechatAdapter.voicePhysicsRange(listener));
		}
	}
}
