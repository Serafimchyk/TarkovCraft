package com.tarkovcraft.arena;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.entity.TarkovScavEntity;
import com.tarkovcraft.registry.ModEntities;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class ArenaScavSpawner {
	private static final int SAFE_DISTANCE = 22;
	private static final int MAX_SAMPLE_COLUMNS = 512;
	private static final int MAX_NEW_CHUNKS = 12;

	private final ServerWorld world;
	private final ArenaEventsState.Arena arena;
	private final List<BlockPos> playerSpawns;
	private final List<BlockPos> used = new ArrayList<>();

	private ArenaScavSpawner(ServerWorld world, ArenaEventsState.Arena arena, List<BlockPos> playerSpawns) {
		this.world = world;
		this.arena = arena;
		this.playerSpawns = playerSpawns;
	}

	static void spawn(ServerWorld world, ArenaEventsState.Arena arena, List<BlockPos> playerSpawns) {
		if (arena.scavBotCount == 0 || world.getDifficulty() == Difficulty.PEACEFUL) return;
		if (!FabricLoader.getInstance().isModLoaded("tacz")) {
			TarkovCraftMod.LOGGER.warn("Skipping arena scav bots because TaCZ is unavailable");
			return;
		}
		var spawner = new ArenaScavSpawner(world, arena, playerSpawns);
		spawner.spawn();
		if (spawner.used.size() < arena.scavBotCount) {
			TarkovCraftMod.LOGGER.warn("Spawned {} of {} AI scavs in arena {}",
				spawner.used.size(), arena.scavBotCount, arena.displayName);
		}
	}

	private void spawn() {
		for (BlockPos pos : arena.scavSpawns) {
			if (used.size() >= arena.scavBotCount) return;
			if (!safeColumn(pos)) continue;
			world.getChunk(pos);
			trySpawn(pos);
		}
		if (used.size() >= arena.scavBotCount) return;

		var corners = arena.corners();
		int minX = corners.stream().mapToInt(BlockPos::getX).min().orElseThrow();
		int maxX = corners.stream().mapToInt(BlockPos::getX).max().orElseThrow();
		int minZ = corners.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
		int maxZ = corners.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
		int loaded = 0;
		for (int attempt = 0; attempt < MAX_SAMPLE_COLUMNS && used.size() < arena.scavBotCount; attempt++) {
			BlockPos column = new BlockPos(minX + world.random.nextInt(maxX - minX + 1), arena.anchor.getY(),
				minZ + world.random.nextInt(maxZ - minZ + 1));
			if (!safeColumn(column)) continue;
			if (!world.isChunkLoaded(column.getX() >> 4, column.getZ() >> 4)) {
				if (loaded >= MAX_NEW_CHUNKS) continue;
				world.getChunk(column);
				loaded++;
			}
			boolean placed = false;
			for (int dy = 0; dy <= 6 && !placed; dy++) {
				placed = trySpawn(column.down(dy)) || (dy != 0 && trySpawn(column.up(dy)));
			}
			if (!placed) trySpawn(world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column));
		}
	}

	private boolean safeColumn(BlockPos pos) {
		return arena.contains(pos.getX() + 0.5, pos.getZ() + 0.5)
			&& playerSpawns.stream().noneMatch(spawn -> nearby(pos, spawn, SAFE_DISTANCE))
			&& used.stream().noneMatch(spawn -> nearby(pos, spawn, 4));
	}

	private static boolean nearby(BlockPos first, BlockPos second, int radius) {
		return Math.hypot(first.getX() - (double) second.getX(), first.getZ() - (double) second.getZ()) < radius;
	}

	private boolean trySpawn(BlockPos pos) {
		if (!ArenaEvents.canPlaceAt(world, arena, pos, null) || !world.getWorldBorder().contains(pos)) return false;
		if (spawnAt(world, pos, point -> arena.contains(point.x, point.z), true) != null) return false;
		used.add(pos.toImmutable());
		return true;
	}

	static Text spawnManual(ServerWorld world, BlockPos pos) {
		if (world.getDifficulty() == Difficulty.PEACEFUL) {
			return Text.translatable("arena.tarkovcraft.scav.peaceful");
		}
		var active = RaidSession.running(world);
		var center = Vec3d.ofBottomCenter(pos);
		boolean inRaid = active != null && active.arena.contains(center.x, center.z);
		Predicate<Vec3d> bounds = inRaid ? point -> active.arena.contains(point.x, point.z)
			: point -> center.squaredDistanceTo(point) <= 32 * 32;
		return spawnAt(world, pos, bounds, inRaid);
	}

	private static Text spawnAt(ServerWorld world, BlockPos pos, Predicate<Vec3d> bounds, boolean keepActive) {
		if (!world.isInBuildLimit(pos) || !world.isInBuildLimit(pos.up()) || !world.getWorldBorder().contains(pos)
			|| world.getBlockState(pos.down()).getCollisionShape(world, pos.down()).isEmpty()
			|| !world.getFluidState(pos).isEmpty() || !world.getFluidState(pos.up()).isEmpty()) {
			return Text.translatable("arena.tarkovcraft.scav.bad_position");
		}
		TarkovScavEntity bot = new TarkovScavEntity(ModEntities.SCAV, world);
		bot.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360, 0);
		if (!world.isSpaceEmpty(bot)
			|| !world.getEntitiesByClass(LivingEntity.class, bot.getBoundingBox(), LivingEntity::isAlive).isEmpty()) {
			return Text.translatable("arena.tarkovcraft.scav.bad_position");
		}
		if (!bot.prepare(false, bounds)) return Text.translatable("lobby.tarkovcraft.scav_guns_unavailable");
		if (!world.spawnEntity(bot)) return Text.translatable("arena.tarkovcraft.scav.spawn_failed");
		if (keepActive) bot.keepActive(world);
		return null;
	}
}
