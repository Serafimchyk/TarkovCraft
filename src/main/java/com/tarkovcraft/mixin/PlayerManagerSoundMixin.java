package com.tarkovcraft.mixin;

import com.tarkovcraft.audio.HearingProfile;
import com.tarkovcraft.audio.TarkovSoundType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
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
		Identifier id;
		SoundCategory category;
		if (packet instanceof PlaySoundS2CPacket positioned) {
			id = positioned.getSound().value().getId();
			category = positioned.getCategory();
		} else if (packet instanceof PlaySoundFromEntityS2CPacket attached) {
			id = attached.getSound().value().getId();
			category = attached.getCategory();
		} else {
			return;
		}

		float range = switch (TarkovSoundType.classify(id, category)) {
			case GUNFIRE_EXPLOSION -> TarkovSoundType.IMPULSE_RANGE;
			case TACTICAL_MOVEMENT -> TarkovSoundType.movementRange(id) * HearingProfile.ACTIVE_RANGE;
			case OTHER -> 0.0f;
		};
		if (range <= distance) return;

		for (var player : ((PlayerManager) (Object) this).getPlayerList()) {
			if (player == source || !player.getWorld().getRegistryKey().equals(world)) continue;
			double distanceSq = player.squaredDistanceTo(x, y, z);
			if (distanceSq >= distance * distance && distanceSq < range * range) {
				player.networkHandler.sendPacket(packet);
			}
		}
	}
}
