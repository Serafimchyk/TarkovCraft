package com.tarkovcraft.arena;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArenaEventsStateTest {
	@Test
	void concavePolygonExcludesTheNotchAndIncludesItsEdges() {
		ArenaEventsState.Arena arena = new ArenaEventsState.Arena(new BlockPos(5, 64, 5));
		arena.setCorner(0, new BlockPos(12, 64, 0));
		arena.setCorner(1, new BlockPos(0, 64, 12));
		arena.setCorner(2, new BlockPos(5, 64, 5));
		assertFalse(arena.ready());

		arena.setCorner(3, new BlockPos(0, 64, 0));
		assertTrue(arena.ready());
		assertTrue(arena.contains(1, 1));
		assertFalse(arena.contains(5, 6));
		assertTrue(arena.contains(5, 5));
		assertFalse(arena.contains(-1, 0));

		arena.setCorner(3, new BlockPos(12, 80, 0));
		assertFalse(arena.ready());
	}

	@Test
	void arenaSettingsAndEventOwnershipSurviveReload() {
		ArenaEventsState state = new ArenaEventsState();
		BlockPos anchor = new BlockPos(-18, 72, 32);
		assertTrue(state.create("factory", anchor));
		assertFalse(state.create("factory", anchor));
		ArenaEventsState.Arena arena = state.getArena("factory");
		arena.setCorner(0, anchor.add(-12, 0, -12));
		arena.setCorner(1, anchor.add(12, 0, 12));
		arena.setCorner(2, anchor.add(-12, 0, 12));
		arena.setCorner(3, anchor.add(12, 0, -12));
		arena.airdropsEnabled = false;
		arena.airdropPoints.add(anchor.east());
		arena.pointsOfInterest.add(anchor.west());
		arena.maxGroundY = 76;
		arena.bossEnabled = true;
		arena.bossSpawned = true;
		arena.activeBoss = UUID.randomUUID();
		arena.cratePos = anchor.north();
		arena.displayName = "Factory";
		arena.durationMinutes = 12;
		arena.pmcSpawns.add(anchor.south());
		arena.scavSpawns.add(anchor.west());
		arena.lootPoints.add(anchor.down());
		arena.extracts.put("Gate 3", new ArenaEventsState.Extract(anchor.east(), true));
		state.retiredCrates.add(anchor.south());

		ArenaEventsState loaded = ArenaEventsState.fromNbt(state.writeNbt(new NbtCompound()));
		ArenaEventsState.Arena restored = loaded.getArena("factory");
		assertTrue(restored.contains(anchor.getX(), anchor.getZ()));
		assertFalse(restored.contains(anchor.getX() - 20, anchor.getZ()));
		assertEquals(anchor, restored.center());
		assertFalse(restored.airdropsEnabled());
		assertEquals(arena.airdropPoints(), restored.airdropPoints());
		assertEquals(arena.pointsOfInterest, restored.pointsOfInterest);
		assertEquals(76, restored.maxGroundY());
		assertTrue(restored.bossEnabled);
		assertTrue(restored.bossSpawned);
		assertEquals(arena.activeBoss, restored.activeBoss);
		assertEquals(arena.cratePos, restored.cratePos);
		assertEquals(state.retiredCrates, loaded.retiredCrates);
		assertEquals(arena.displayName, restored.displayName);
		assertEquals(arena.durationMinutes, restored.durationMinutes);
		assertEquals(arena.pmcSpawns, restored.pmcSpawns);
		assertEquals(arena.scavSpawns, restored.scavSpawns);
		assertEquals(arena.lootPoints, restored.lootPoints);
		assertEquals(arena.extracts, restored.extracts);
	}

	@Test
	void spreadingStartsWithTheFarthestPairAndMaximizesDistanceForTheThirdSpawn() {
		BlockPos west = new BlockPos(0, 64, 0);
		BlockPos east = new BlockPos(20, 64, 0);
		BlockPos middle = new BlockPos(10, 64, 0);
		assertEquals(List.of(west, east, middle), RaidSession.spread(
			List.of(middle, west, new BlockPos(2, 64, 0), east), 3));
	}
}
