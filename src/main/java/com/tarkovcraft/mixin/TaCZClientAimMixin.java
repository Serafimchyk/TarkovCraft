package com.tarkovcraft.mixin;

import com.tarkovcraft.client.ClientLoadoutCache;
import com.tarkovcraft.loadout.ArmorHandling;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerAim", remap = false)
abstract class TaCZClientAimMixin {
	@ModifyArg(method = "getAlphaProgress(Lcom/tacz/guns/resource/pojo/data/gun/GunData;)F",
		at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"), index = 1, require = 0)
	private float tarkovcraft$applyArmorToAimTime(float seconds) {
		var player = MinecraftClient.getInstance().player;
		if (player == null || player.isCreative() || player.isSpectator()) return seconds;
		return ArmorHandling.equipped(ClientLoadoutCache.getLoadout()).aimDuration(seconds);
	}
}
