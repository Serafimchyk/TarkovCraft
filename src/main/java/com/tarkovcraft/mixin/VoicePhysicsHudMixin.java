package com.tarkovcraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.kasper.vcdistance.b.mc1_20_1.client.HudHook", remap = false)
abstract class VoicePhysicsHudMixin {
	@Inject(method = "register()V", at = @At("HEAD"), cancellable = true, require = 0)
	private static void tarkovcraft$disableSpeakerOverlay(CallbackInfo callback) {
		callback.cancel();
	}
}
