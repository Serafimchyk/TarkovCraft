package com.tarkovcraft.mixin;

import com.tarkovcraft.audio.TarkovSoundNetworking;
import com.tarkovcraft.audio.TarkovSoundType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.sound.SoundManager", remap = false)
abstract class TaCZSoundMixin {
	@Inject(method = "sendSoundToNearby", at = @At("HEAD"), cancellable = true)
	private static void tarkovcraft$sendPositionedGunfire(LivingEntity source, int range, Identifier gunId,
			Identifier displayId, String sound, float volume, float pitch, CallbackInfo callback) {
		if (!FabricLoader.getInstance().isModLoaded("tacz")) return;
		if (TarkovSoundType.classify(new Identifier("tacz", sound), SoundCategory.PLAYERS)
				== TarkovSoundType.GUNFIRE_EXPLOSION) {
			TarkovSoundNetworking.sendGunfire(source, displayId, gunId, sound, volume, pitch);
			callback.cancel();
		}
	}
}
