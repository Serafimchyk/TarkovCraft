package com.tarkovcraft.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Disables automatic vacuum pickup in Survival/Adventure mode so dropped backpacks,
 * rigs, weapons, and loot remain lying on the ground until picked up / equipped with [F].
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {
	@Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$disableAutoVacuumInSurvival(PlayerEntity player, CallbackInfo ci) {
		if (!player.isCreative() && !player.isSpectator()) {
			ci.cancel();
		}
	}
}
