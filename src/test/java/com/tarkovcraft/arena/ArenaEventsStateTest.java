package com.tarkovcraft.arena;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArenaEventsStateTest {
	@Test
	void concavePolygonExcludesTheNotchAndIncludesItsEdges() {
		ArenaEventsState.Arena arena = new ArenaEventsState.Arena(new BlockPos(2, 64, 2));
		List<BlockPos> vertices = List.of(new BlockPos(0, 64, 0), new BlockPos(10, 65, 0),
			new BlockPos(10, 66, 4), new BlockPos(4, 67, 4), new BlockPos(4, 68, 10), new BlockPos(0, 69, 10));
		arena.setVertices(vertices);
		assertTrue(arena.ready());
		assertTrue(arena.contains(1, 1));
		assertTrue(arena.contains(8.5, 2.5));
		assertTrue(arena.contains(2.5, 8.5));
		assertFalse(arena.contains(5, 6));
		assertTrue(arena.contains(4, 5));
		assertFalse(arena.contains(-1, 0));
		assertEquals(vertices, ArenaEventsState.Arena.readNbt(arena.writeNbt()).corners());
	}

	@Test
	void acceptsThreeVerticesAndRejectsIntersectionsAndDuplicateColumns() {
		ArenaEventsState.Arena arena = new ArenaEventsState.Arena(BlockPos.ORIGIN);
		BlockPos a = BlockPos.ORIGIN;
		BlockPos b = new BlockPos(10, 0, 0);
		BlockPos c = new BlockPos(10, 0, 10);
		BlockPos d = new BlockPos(0, 0, 10);
		arena.setCorner(0, a);
		arena.setCorner(1, b);
		assertFalse(arena.ready());
		arena.setCorner(2, c);
		assertTrue(arena.ready());
		arena.setVertices(List.of(a, c, b, d));
		assertFalse(arena.ready());
		arena.setVertices(List.of(a, b, a.up(), d));
		assertFalse(arena.ready());
		arena.setVertices(List.of(a, new BlockPos(5, 0, 0), b, c, d));
		assertTrue(arena.ready());
	}

	@Test
	void legacyCornerSlotsKeepThePreviouslyPlayableOutline() {
		var tag = new NbtCompound();
		tag.putLong("Corner0", new BlockPos(10, 64, 10).asLong());
		tag.putLong("Corner1", new BlockPos(0, 64, 0).asLong());
		tag.putLong("Corner2", new BlockPos(0, 64, 10).asLong());
		tag.putLong("Corner3", new BlockPos(10, 64, 0).asLong());
		var arena = ArenaEventsState.Arena.readNbt(tag);
		assertTrue(arena.ready());
		assertTrue(arena.contains(5, 5));
		assertEquals(arena.corners(), ArenaEventsState.Arena.readNbt(arena.writeNbt()).corners());
		assertTrue(arena.writeNbt().contains("Vertices"));
	}

	@Test
	void boundaryClosureUsesAllThreeCoordinates() {
		assertTrue(ArenaBoundaryEditing.closes(BlockPos.ORIGIN, new BlockPos(1, 1, 0)));
		assertFalse(ArenaBoundaryEditing.closes(BlockPos.ORIGIN, new BlockPos(1, 1, 1)));
		assertFalse(ArenaBoundaryEditing.closes(BlockPos.ORIGIN, new BlockPos(0, 2, 0)));
	}

	@Test
	void arenaSettingsAndEventOwnershipSurviveReload() {
		ArenaEventsState state = new ArenaEventsState();
		BlockPos anchor = new BlockPos(-18, 72, 32);
		assertTrue(state.create("factory", anchor));
		assertFalse(state.create("factory", anchor));
		ArenaEventsState.Arena arena = state.getArena("factory");
		arena.setCorner(0, anchor.add(-12, 0, -12));
		arena.setCorner(1, anchor.add(12, 0, -12));
		arena.setCorner(2, anchor.add(12, 0, 12));
		arena.setCorner(3, anchor.add(-12, 0, 12));
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
		arena.extracts.put("Gate 3", new ArenaEventsState.Extract(anchor.east(), true, 5.5));
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
	void legacyExtractUsesDefaultRadiusAndChecksTheHorizontalRing() {
		var exit = new NbtCompound();
		exit.putLong("Pos", new BlockPos(10, 64, 20).asLong());
		var exits = new NbtCompound();
		exits.put("gate", exit);
		var tag = new NbtCompound();
		tag.put("Extracts", exits);
		var restored = ArenaEventsState.Arena.readNbt(tag).extracts.get("gate");
		assertEquals(3.5, restored.radius());
		assertTrue(restored.contains(new Vec3d(14.0, 65, 20.5)));
		assertFalse(restored.contains(new Vec3d(14.001, 64, 20.5)));
		assertFalse(restored.contains(new Vec3d(10.5, 67, 20.5)));
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
