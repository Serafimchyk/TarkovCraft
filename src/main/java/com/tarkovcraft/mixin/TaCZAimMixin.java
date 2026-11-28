package com.tarkovcraft.mixin;

import com.tarkovcraft.loadout.ArmorHandling;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityAim", remap = false)
abstract class TaCZAimMixin {
	@Shadow @Final private LivingEntity shooter;

	// TaCZ clamps the aim duration here after applying its attachment modifiers.
	@ModifyArg(method = "tickAimingProgress()V", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"),
		index = 1, require = 0)
	private float tarkovcraft$applyArmorToAimTime(float seconds) {
		if (shooter instanceof ServerPlayerEntity player && !player.isCreative() && !player.isSpectator()
			&& player instanceof PlayerLoadoutHolder holder) {
			return ArmorHandling.equipped(holder.tarkovcraft$getLoadout()).aimDuration(seconds);
		}
		return seconds;
	}
}
