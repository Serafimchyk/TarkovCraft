package com.tarkovcraft.arena;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

final class ArenaBoundaryTicker {
	private ArenaBoundaryTicker() { }

	static void warn(ServerWorld world, ArenaEventsState.Arena arena, ServerPlayerEntity player) {
		List<BlockPos> corners = arena.corners();
		for (int i = 0; i < corners.size(); i++) {
			BlockPos a = corners.get(i);
			BlockPos b = corners.get((i + 1) % corners.size());
			double dx = b.getX() - (double) a.getX();
			double dz = b.getZ() - (double) a.getZ();
			double lengthSquared = dx * dx + dz * dz;
			double along = ((player.getX() - a.getX()) * dx + (player.getZ() - a.getZ()) * dz) / lengthSquared;
			double fraction = Math.max(0, Math.min(1, along));
			double x = a.getX() + dx * fraction;
			double z = a.getZ() + dz * fraction;
			double playerDx = x - player.getX();
			double playerDz = z - player.getZ();
			if (playerDx * playerDx + playerDz * playerDz <= 25) {
				world.spawnParticles(player, DustParticleEffect.DEFAULT, false, x, player.getY() + 0.9, z,
					9, 0.3, 0.5, 0.3, 0);
			}
		}
	}
}
