package com.tarkovcraft.airdrop;

import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.entity.AirdropEntity;
import com.tarkovcraft.registry.ModEntities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.Heightmap;

import java.util.List;

public final class AirdropManager {
	private static int tickCounter = 0;

	private AirdropManager() {
	}

	public static void resetTimer() {
		tickCounter = 0;
	}

	public static int getTickCounter() {
		return tickCounter;
	}

	public static void onConfigReloaded() {
		int maxTicks = TarkovConfig.get().getAirdropIntervalTicks();
		if (tickCounter >= maxTicks) {
			tickCounter = 0;
		}
	}

	public static void tickServer(MinecraftServer server) {
		TarkovConfig cfg = TarkovConfig.get();
		if (!cfg.airdropEnabled) {
			return;
		}
		ServerWorld overworld = server.getOverworld();
		if (overworld == null) {
			return;
		}
		List<ServerPlayerEntity> activePlayers = overworld.getPlayers(p -> p.isAlive() && !p.isSpectator());
		if (activePlayers.isEmpty()) {
			return;
		}
		tickCounter++;
		if (tickCounter >= cfg.getAirdropIntervalTicks()) {
			tickCounter = 0;
			if (activePlayers.size() >= 2) {
				spawnAirdropBetweenPlayers(overworld, activePlayers);
			} else {
				ServerPlayerEntity target = activePlayers.get(overworld.random.nextInt(activePlayers.size()));
				spawnAirdropNear(target);
			}
		}
	}

	public static AirdropEntity spawnAirdropBetweenPlayers(ServerWorld world, List<ServerPlayerEntity> players) {
		if (players == null || players.isEmpty()) {
			return null;
		}
		if (players.size() == 1) {
			return spawnAirdropNear(players.get(0));
		}

		// Collect pairs within 1200 blocks XZ
		List<int[]> validPairs = new java.util.ArrayList<>();
		for (int i = 0; i < players.size(); i++) {
			for (int j = i + 1; j < players.size(); j++) {
				ServerPlayerEntity a = players.get(i);
				ServerPlayerEntity b = players.get(j);
				double dx = a.getX() - b.getX();
				double dz = a.getZ() - b.getZ();
				if (dx * dx + dz * dz <= 1200.0 * 1200.0) {
					validPairs.add(new int[]{i, j});
				}
			}
		}
		if (validPairs.isEmpty()) {
			return spawnAirdropNear(players.get(world.random.nextInt(players.size())));
		}

		int[] pair = validPairs.get(world.random.nextInt(validPairs.size()));
		ServerPlayerEntity a = players.get(pair[0]);
		ServerPlayerEntity b = players.get(pair[1]);

		double[] tSteps = {0.50, 0.42, 0.58, 0.34, 0.66, 0.25, 0.75, 0.15, 0.85};
		for (double t : tSteps) {
			double baseX = a.getX() + (b.getX() - a.getX()) * t;
			double baseZ = a.getZ() + (b.getZ() - a.getZ()) * t;
			for (int s = 0; s < 6; s++) {
				double offsetAngle = world.random.nextDouble() * Math.PI * 2.0;
				double offsetDist = s == 0 ? 0.0 : (12.0 + world.random.nextDouble() * 24.0);
				int cx = (int) Math.round(baseX + Math.cos(offsetAngle) * offsetDist);
				int cz = (int) Math.round(baseZ + Math.sin(offsetAngle) * offsetDist);
				if (!world.isChunkLoaded(cx >> 4, cz >> 4)) {
					continue;
				}
				int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz);
				net.minecraft.util.math.BlockPos surfacePos = new net.minecraft.util.math.BlockPos(cx, surfaceY - 1, cz);
				if (world.getFluidState(surfacePos).isEmpty() && world.getFluidState(surfacePos.up()).isEmpty()) {
					return spawnAirdropAt(world, cx, cz, true);
				}
			}
		}

		return spawnAirdropNear(players.get(world.random.nextInt(players.size())));
	}

	public static AirdropEntity spawnAirdropNear(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		TarkovConfig cfg = TarkovConfig.get();
		int minR = cfg.airdropMinRadius;
		int maxR = Math.max(minR, cfg.airdropMaxRadius);

		int chosenX = player.getBlockX();
		int chosenZ = player.getBlockZ();
		boolean found = false;
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = world.random.nextDouble() * Math.PI * 2.0;
			double radius = minR + world.random.nextDouble() * (maxR - minR);
			int cx = (int) Math.round(player.getX() + Math.cos(angle) * radius);
			int cz = (int) Math.round(player.getZ() + Math.sin(angle) * radius);
			if (!world.isChunkLoaded(cx >> 4, cz >> 4)) {
				continue;
			}
			int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz);
			net.minecraft.util.math.BlockPos surfacePos = new net.minecraft.util.math.BlockPos(cx, surfaceY - 1, cz);
			if (world.getFluidState(surfacePos).isEmpty() && world.getFluidState(surfacePos.up()).isEmpty()) {
				chosenX = cx;
				chosenZ = cz;
				found = true;
				break;
			}
		}
		if (!found) {
			chosenX = player.getBlockX();
			chosenZ = player.getBlockZ();
		}
		return spawnAirdropAt(world, chosenX, chosenZ, true);
	}

	@org.jetbrains.annotations.Nullable
	public static AirdropEntity spawnAirdropAt(ServerWorld world, int x, int z, boolean announce) {
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return null;
		}
		int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		int spawnY = Math.min(world.getTopY() - 8, Math.max(surfaceY + 120, world.getSeaLevel() + 110));

		AirdropEntity airdrop = new AirdropEntity(ModEntities.AIRDROP, world);
		airdrop.refreshPositionAndAngles(x + 0.5, spawnY, z + 0.5, 0.0f, 0.0f);
		world.spawnEntity(airdrop);

		for (ServerPlayerEntity p : world.getPlayers()) {
			double dx = p.getX() - x;
			double dz = p.getZ() - z;
			if (dx * dx + dz * dz <= 400.0 * 400.0) {
				world.playSound(null, p.getX(), p.getY(), p.getZ(),
					SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 0.9f, 0.85f);
			}
		}

		if (announce) {
			Text msg = TarkovConfig.get().airdropAnnounceCoords
				? Text.translatable("message.tarkovcraft.airdrop_incoming_coords", x, z)
				: Text.translatable("message.tarkovcraft.airdrop_incoming");
			MinecraftServer server = world.getServer();
			if (server != null) {
				server.getPlayerManager().broadcast(msg, false);
			}
		}
		return airdrop;
	}
}
