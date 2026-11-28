package com.tarkovcraft.audio;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class TarkovSoundNetworking {
	public static final Identifier GUNFIRE = new Identifier("tarkovcraft", "positioned_gunfire");

	private TarkovSoundNetworking() {}

	public static void sendGunfire(LivingEntity source, Identifier displayId, Identifier gunId,
			String soundName, float volume, float pitch) {
		sendGunfire(source, source.getEyePos(), displayId, gunId, soundName, volume, pitch);
	}

	public static void sendGunfire(LivingEntity source, Vec3d position, Identifier displayId, Identifier gunId,
			String soundName, float volume, float pitch) {
		if (!(source.getWorld() instanceof ServerWorld world)) return;
		float range = TarkovSoundType.IMPULSE_RANGE;
		for (var player : world.getPlayers()) {
			if (player == source || player.squaredDistanceTo(source) >= range * range
				|| !ServerPlayNetworking.canSend(player, GUNFIRE)) continue;
			var buffer = PacketByteBufs.create();
			buffer.writeDouble(position.x).writeDouble(position.y).writeDouble(position.z);
			buffer.writeIdentifier(displayId).writeIdentifier(gunId).writeString(soundName);
			buffer.writeFloat(volume).writeFloat(pitch);
			ServerPlayNetworking.send(player, GUNFIRE, buffer);
		}
	}
}
