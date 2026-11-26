package com.tarkovcraft.mixin;

import com.tarkovcraft.audio.HearingProfile;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.PlayerManager;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
abstract class PlayerManagerSoundMixin {
	@Inject(method = "sendToAround", at = @At("TAIL"))
	private void tarkovcraft$extendTacticalSounds(PlayerEntity source, double x, double y, double z, double distance,
			RegistryKey<World> world, Packet<?> packet, CallbackInfo callback) {
		boolean tactical = packet instanceof PlaySoundS2CPacket positioned
			&& HearingProfile.isQuietTactical(positioned.getSound().value().getId(), positioned.getCategory())
			|| packet instanceof PlaySoundFromEntityS2CPacket attached
			&& HearingProfile.isQuietTactical(attached.getSound().value().getId(), attached.getCategory());
		if (!tactical || distance <= 0) return;

		double baseSq = distance * distance;
		double boostedSq = baseSq * HearingProfile.ACTIVE_RANGE * HearingProfile.ACTIVE_RANGE;
		for (var player : ((PlayerManager) (Object) this).getPlayerList()) {
			if (player == source || player.getWorld().getRegistryKey() != world
				|| !(player instanceof PlayerLoadoutHolder holder)) continue;
			double distanceSq = player.squaredDistanceTo(x, y, z);
			if (distanceSq >= baseSq && distanceSq < boostedSq
				&& HearingProfile.forLoadout(holder.tarkovcraft$getLoadout()).range() == HearingProfile.ACTIVE_RANGE) {
				player.networkHandler.sendPacket(packet);
			}
		}
	}
}
