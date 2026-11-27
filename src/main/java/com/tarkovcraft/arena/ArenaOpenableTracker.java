package com.tarkovcraft.arena;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.event.BlockPositionSource;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.event.listener.GameEventListener;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

final class ArenaOpenableTracker {
	private static final Map<ServerWorld, Set<WorldChunk>> LOADED = new IdentityHashMap<>();
	private final ServerWorld world;
	private final ArenaEventsState.Arena arena;
	private final Set<BlockPos> interacted;
	private final Map<WorldChunk, Map<Integer, Listener>> listening = new IdentityHashMap<>();
	private final int minX;
	private final int maxX;
	private final int minZ;
	private final int maxZ;

	ArenaOpenableTracker(ServerWorld world, ArenaEventsState.Arena arena, Set<BlockPos> interacted) {
		this.world = world;
		this.arena = arena;
		this.interacted = interacted;
		minX = arena.corners().stream().mapToInt(BlockPos::getX).min().orElseThrow() >> 4;
		maxX = arena.corners().stream().mapToInt(BlockPos::getX).max().orElseThrow() >> 4;
		minZ = arena.corners().stream().mapToInt(BlockPos::getZ).min().orElseThrow() >> 4;
		maxZ = arena.corners().stream().mapToInt(BlockPos::getZ).max().orElseThrow() >> 4;
	}

	static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
			LOADED.computeIfAbsent(world, unused -> java.util.Collections.newSetFromMap(new IdentityHashMap<>())).add(chunk);
			RaidSession raid = RaidSession.running(world);
			if (raid != null) raid.openables.add(chunk);
		});
		ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
			var chunks = LOADED.get(world);
			if (chunks != null) chunks.remove(chunk);
			RaidSession raid = RaidSession.running(world);
			if (raid != null) raid.openables.remove(chunk);
		});
		ServerWorldEvents.UNLOAD.register((server, world) -> LOADED.remove(world));
	}

	void start() {
		LOADED.getOrDefault(world, Set.of()).forEach(this::add);
	}

	void stop() {
		for (var chunk : java.util.List.copyOf(listening.keySet())) remove(chunk);
	}

	private void add(WorldChunk chunk) {
		var pos = chunk.getPos();
		if (pos.x < minX || pos.x > maxX || pos.z < minZ || pos.z > maxZ || listening.containsKey(chunk)) return;
		var sections = new HashMap<Integer, Listener>();
		for (int y = world.getBottomSectionCoord(); y < world.getTopSectionCoord(); y++) {
			var center = new BlockPos(pos.getStartX() + 8, (y << 4) + 8, pos.getStartZ() + 8);
			var listener = new Listener(new BlockPositionSource(center), this);
			chunk.getGameEventDispatcher(y).addListener(listener);
			sections.put(y, listener);
		}
		listening.put(chunk, sections);
	}

	private void remove(WorldChunk chunk) {
		var sections = listening.remove(chunk);
		if (sections != null) sections.forEach((y, listener) -> chunk.getGameEventDispatcher(y).removeListener(listener));
	}

	private record Listener(BlockPositionSource getPositionSource, ArenaOpenableTracker tracker) implements GameEventListener {
		@Override
		public int getRange() {
			return 16;
		}

		@Override
		public boolean listen(ServerWorld world, GameEvent event, GameEvent.Emitter emitter, Vec3d position) {
			if (event != GameEvent.BLOCK_OPEN && event != GameEvent.BLOCK_CLOSE) return false;
			BlockPos pos = BlockPos.ofFloored(position);
			if (!tracker.arena.contains(pos.getX() + 0.5, pos.getZ() + 0.5)) return false;
			BlockPos canonical = ArenaResetService.openable(pos, world.getBlockState(pos));
			if (canonical == null) return false;
			tracker.interacted.add(canonical);
			return true;
		}
	}
}
