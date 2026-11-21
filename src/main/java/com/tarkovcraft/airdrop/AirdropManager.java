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
			ServerPlayerEntity target = activePlayers.get(overworld.random.nextInt(activePlayers.size()));
			spawnAirdropNear(target);
		}
	}

	public static AirdropEntity spawnAirdropNear(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		TarkovConfig cfg = TarkovConfig.get();
		int minR = cfg.airdropMinRadius;
		int maxR = Math.max(minR, cfg.airdropMaxRadius);
		double angle = world.random.nextDouble() * Math.PI * 2.0;
		double radius = minR + world.random.nextDouble() * (maxR - minR);
		int x = (int) Math.round(player.getX() + Math.cos(angle) * radius);
		int z = (int) Math.round(player.getZ() + Math.sin(angle) * radius);
		return spawnAirdropAt(world, x, z, true);
	}

	public static AirdropEntity spawnAirdropAt(ServerWorld world, int x, int z, boolean announce) {
		int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		int spawnY = Math.min(world.getTopY() - 8, Math.max(surfaceY + 48, world.getSeaLevel() + 48));

		AirdropEntity airdrop = new AirdropEntity(ModEntities.AIRDROP, world);
		airdrop.refreshPositionAndAngles(x + 0.5, spawnY, z + 0.5, 0.0f, 0.0f);
		world.spawnEntity(airdrop);

		for (ServerPlayerEntity p : world.getPlayers()) {
			world.playSound(null, p.getX(), p.getY(), p.getZ(),
				SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 0.9f, 0.85f);
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
