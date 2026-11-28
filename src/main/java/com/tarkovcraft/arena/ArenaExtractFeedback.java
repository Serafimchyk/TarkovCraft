package com.tarkovcraft.arena;

import com.tarkovcraft.network.RaidExtractPackets;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

final class ArenaExtractFeedback {
	static Text confirm(ServerWorld world, ServerPlayerEntity player, String name, ArenaEventsState.Extract exit) {
		var pos = exit.pos();
		var message = Text.translatable("arena.tarkovcraft.extract.placed", name, exit.loud() ? "loud" : "quiet",
			Double.toString(exit.radius()), pos.getX(), pos.getY(), pos.getZ());
		world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 1f, 1.25f);
		var center = Vec3d.ofBottomCenter(pos);
		var dust = new DustParticleEffect(new Vector3f(0.15f, 0.92f, 0.35f), 1.5f);
		int segments = Math.max(32, (int) Math.ceil(exit.radius() * 4));
		for (int i = 0; i < segments; i++) {
			double a = Math.PI * 2 * i / segments;
			world.spawnParticles(dust, center.x + exit.radius() * Math.cos(a), center.y + 0.12,
				center.z + exit.radius() * Math.sin(a), 3, 0.05, 0.09, 0.05, 0);
		}
		if (player != null) {
			player.sendMessage(message, true);
			if (ServerPlayNetworking.canSend(player, RaidExtractPackets.PLACED)) {
				var buf = PacketByteBufs.create();
				buf.writeIdentifier(world.getRegistryKey().getValue());
				new RaidExtractPackets.Exit(name, pos, exit.loud(), exit.radius()).write(buf);
				ServerPlayNetworking.send(player, RaidExtractPackets.PLACED, buf);
			}
		}
		return message;
	}

	private ArenaExtractFeedback() { }
}
