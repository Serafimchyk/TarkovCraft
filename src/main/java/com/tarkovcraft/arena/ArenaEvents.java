package com.tarkovcraft.arena;

import com.tarkovcraft.airdrop.AirdropManager;
import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.config.TarkovConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public final class ArenaEvents {
	private ArenaEvents() {
	}

	public static void tick(MinecraftServer server) {
		TarkovConfig config = TarkovConfig.get();
		for (ServerWorld world : server.getWorlds()) {
			ArenaEventsState state = ArenaEventsState.get(world);
			if (state.retiredCrates.removeIf(pos -> HackableCrateEvent.clearRetiredCrate(world, pos))) state.markDirty();
			RaidSession raid = RaidSession.running(world);
			if (PlayerProfileState.get(server).lobbyPos() != null && raid == null) continue;
			for (ArenaEventsState.Arena arena : raid == null ? state.arenas() : List.of(raid.arena)) {
				if (!arena.ready()) continue;
				List<ServerPlayerEntity> players = raid != null ? raid.livingPlayers() : world.getPlayers(p -> p.isAlive() && !p.isSpectator()
					&& arena.contains(p.getBlockX(), p.getBlockZ()));
				if (players.isEmpty()) continue;

				if (config.airdropEnabled && (raid == null ? arena.airdropsEnabled : raid.airdropsEnabled())
					&& ++arena.airdropTicks >= config.getAirdropIntervalTicks()) {
					arena.airdropTicks = AirdropManager.spawnForArena(world, arena) != null
						? 0 : Math.max(0, config.getAirdropIntervalTicks() - 20 * 10);
				}
				if (arena.crateTicks == 0) arena.crateTicks = (240 + world.random.nextInt(61)) * 20;
				if (--arena.crateTicks == 0 && !HackableCrateEvent.activate(world, arena, players)) {
					arena.crateTicks = 20 * 10;
				}
				if ((raid == null ? arena.bossEnabled : raid.bossEnabled()) && !arena.bossSpawned) {
					if (arena.bossRetryTicks == 0) {
						arena.bossRetryTicks = ArenaBossSpawner.spawn(world, arena, players) ? 0 : 20 * 10;
					} else {
						arena.bossRetryTicks--;
					}
				}
			}
		}
	}

	static boolean canPlaceAt(ServerWorld world, ArenaEventsState.Arena arena, BlockPos pos, SearchableLootBlockEntity oldCrate) {
		if (!arena.contains(pos.getX(), pos.getZ()) || !world.isInBuildLimit(pos)
			|| !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
		BlockPos floor = pos.down();
		return world.getBlockState(floor).isSolidBlock(world, floor)
			&& (world.getBlockState(pos).isAir() || oldCrate != null && pos.equals(oldCrate.getPos()))
			&& world.getBlockState(pos.up()).isAir();
	}
}
