package com.tarkovcraft.arena;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArenaEventsState extends PersistentState {
	private final Map<String, Arena> arenas = new LinkedHashMap<>();
	final Set<BlockPos> retiredCrates = new LinkedHashSet<>();
	final Set<UUID> retiredEntities = new LinkedHashSet<>();

	public static ArenaEventsState get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(
			ArenaEventsState::fromNbt, ArenaEventsState::new, "tarkovcraft_arena_events");
	}

	public Arena getArena(String id) {
		return arenas.get(id);
	}

	public Collection<Arena> arenas() {
		return arenas.values();
	}

	Map<String, Arena> definitions() {
		return arenas;
	}

	public boolean create(String id, BlockPos anchor) {
		if (arenas.containsKey(id)) return false;
		arenas.put(id, new Arena(anchor.toImmutable()));
		markDirty();
		return true;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtCompound definitions = new NbtCompound();
		arenas.forEach((id, arena) -> definitions.put(id, arena.writeNbt()));
		nbt.put("Arenas", definitions);
		nbt.putLongArray("RetiredCrates", retiredCrates.stream().mapToLong(BlockPos::asLong).toArray());
		NbtList entities = new NbtList();
		retiredEntities.forEach(id -> entities.add(NbtHelper.fromUuid(id)));
		nbt.put("RetiredEntities", entities);
		return nbt;
	}

	static ArenaEventsState fromNbt(NbtCompound nbt) {
		ArenaEventsState state = new ArenaEventsState();
		NbtCompound definitions = nbt.getCompound("Arenas");
		for (String id : definitions.getKeys()) {
			state.arenas.put(id, Arena.readNbt(definitions.getCompound(id)));
		}
		for (long pos : nbt.getLongArray("RetiredCrates")) state.retiredCrates.add(BlockPos.fromLong(pos));
		for (NbtElement entity : nbt.getList("RetiredEntities", NbtElement.INT_ARRAY_TYPE)) {
			state.retiredEntities.add(NbtHelper.toUuid(entity));
		}
		return state;
	}

	public static final class Arena {
		final BlockPos anchor;
		final BlockPos[] corners = new BlockPos[4];
		private List<BlockPos> polygon = List.of();
		final Set<BlockPos> airdropPoints = new LinkedHashSet<>();
		final Set<BlockPos> pointsOfInterest = new LinkedHashSet<>();
		boolean airdropsEnabled = true;
		boolean bossEnabled;
		int maxGroundY;
		BlockPos cratePos;
		boolean bossSpawned;
		UUID activeBoss;
		int airdropTicks;
		int crateTicks;
		int bossRetryTicks;
		String displayName = "";
		int durationMinutes = 15;
		final Set<BlockPos> pmcSpawns = new LinkedHashSet<>();
		final Set<BlockPos> scavSpawns = new LinkedHashSet<>();
		final Map<String, Extract> extracts = new LinkedHashMap<>();
		final Set<BlockPos> lootPoints = new LinkedHashSet<>();

		Arena(BlockPos anchor) {
			this.anchor = anchor;
			this.maxGroundY = anchor.getY() + 8;
		}

		void setCorner(int index, BlockPos pos) {
			corners[index] = pos.toImmutable();
			rebuildPolygon();
		}

		private void rebuildPolygon() {
			polygon = List.of();
			if (Arrays.stream(corners).anyMatch(p -> p == null)) return;
			double centerX = Arrays.stream(corners).mapToInt(BlockPos::getX).average().orElseThrow();
			double centerZ = Arrays.stream(corners).mapToInt(BlockPos::getZ).average().orElseThrow();
			List<BlockPos> ordered = Arrays.stream(corners)
				.sorted(Comparator.comparingDouble(p -> Math.atan2(p.getZ() - centerZ, p.getX() - centerX)))
				.toList();
			for (int i = 0; i < ordered.size(); i++) {
				BlockPos a = ordered.get(i);
				BlockPos b = ordered.get((i + 1) % ordered.size());
				BlockPos c = ordered.get((i + 2) % ordered.size());
				if (cross(a, b, c.getX(), c.getZ()) == 0) return;
			}
			polygon = ordered;
		}

		private static long cross(BlockPos a, BlockPos b, int x, int z) {
			return ((long) b.getX() - a.getX()) * ((long) z - a.getZ())
				- ((long) b.getZ() - a.getZ()) * ((long) x - a.getX());
		}

		public BlockPos center() {
			if (Arrays.stream(corners).anyMatch(p -> p == null)) return anchor;
			long x = 0;
			long z = 0;
			for (BlockPos corner : corners) {
				x += corner.getX();
				z += corner.getZ();
			}
			return new BlockPos((int) Math.round(x / 4.0), anchor.getY(), (int) Math.round(z / 4.0));
		}

		public boolean ready() {
			return !polygon.isEmpty();
		}

		public boolean contains(int x, int z) {
			return contains((double) x, z);
		}

		public boolean contains(double x, double z) {
			if (!ready()) return false;
			boolean inside = false;
			for (int i = 0; i < polygon.size(); i++) {
				BlockPos a = polygon.get(i);
				BlockPos b = polygon.get((i + 1) % polygon.size());
				double side = (b.getX() - (double) a.getX()) * (z - a.getZ())
					- (b.getZ() - (double) a.getZ()) * (x - a.getX());
				if (Math.abs(side) < 1.0e-8 && between(x, a.getX(), b.getX()) && between(z, a.getZ(), b.getZ())) return true;
				if ((a.getZ() > z) != (b.getZ() > z) && (side > 0) == (b.getZ() > a.getZ())) inside = !inside;
			}
			return inside;
		}

		private static boolean between(double value, int first, int second) {
			return value >= Math.min(first, second) && value <= Math.max(first, second);
		}

		public List<BlockPos> corners() {
			return polygon;
		}

		public Set<BlockPos> airdropPoints() {
			return Set.copyOf(airdropPoints);
		}

		public boolean airdropsEnabled() {
			return airdropsEnabled;
		}

		public int maxGroundY() {
			return maxGroundY;
		}

		NbtCompound writeNbt() {
			NbtCompound tag = new NbtCompound();
			tag.putLong("Anchor", anchor.asLong());
			for (int i = 0; i < corners.length; i++) {
				if (corners[i] != null) tag.putLong("Corner" + i, corners[i].asLong());
			}
			tag.putLongArray("AirdropPoints", airdropPoints.stream().mapToLong(BlockPos::asLong).toArray());
			tag.putLongArray("Poi", pointsOfInterest.stream().mapToLong(BlockPos::asLong).toArray());
			tag.putBoolean("AirdropsEnabled", airdropsEnabled);
			tag.putInt("MaxGroundY", maxGroundY);
			tag.putBoolean("BossEnabled", bossEnabled);
			tag.putString("DisplayName", displayName);
			tag.putInt("DurationMinutes", durationMinutes);
			tag.putLongArray("PmcSpawns", pmcSpawns.stream().mapToLong(BlockPos::asLong).toArray());
			tag.putLongArray("ScavSpawns", scavSpawns.stream().mapToLong(BlockPos::asLong).toArray());
			tag.putLongArray("LootPoints", lootPoints.stream().mapToLong(BlockPos::asLong).toArray());
			NbtCompound exits = new NbtCompound();
			extracts.forEach((name, extract) -> {
				NbtCompound exit = new NbtCompound();
				exit.putLong("Pos", extract.pos().asLong());
				exit.putBoolean("Loud", extract.loud());
				exits.put(name, exit);
			});
			tag.put("Extracts", exits);
			tag.putBoolean("BossSpawned", bossSpawned);
			if (activeBoss != null) tag.putUuid("ActiveBoss", activeBoss);
			if (cratePos != null) tag.putLong("CratePos", cratePos.asLong());
			return tag;
		}

		static Arena readNbt(NbtCompound tag) {
			Arena arena = new Arena(BlockPos.fromLong(tag.getLong("Anchor")));
			for (int i = 0; i < arena.corners.length; i++) {
				if (tag.contains("Corner" + i)) arena.corners[i] = BlockPos.fromLong(tag.getLong("Corner" + i));
			}
			arena.rebuildPolygon();
			for (long point : tag.getLongArray("AirdropPoints")) arena.airdropPoints.add(BlockPos.fromLong(point));
			for (long point : tag.getLongArray("Poi")) arena.pointsOfInterest.add(BlockPos.fromLong(point));
			arena.airdropsEnabled = !tag.contains("AirdropsEnabled") || tag.getBoolean("AirdropsEnabled");
			if (tag.contains("MaxGroundY")) arena.maxGroundY = tag.getInt("MaxGroundY");
			arena.bossEnabled = tag.getBoolean("BossEnabled");
			arena.displayName = tag.getString("DisplayName");
			if (tag.contains("DurationMinutes")) arena.durationMinutes = tag.getInt("DurationMinutes");
			for (long point : tag.getLongArray("PmcSpawns")) arena.pmcSpawns.add(BlockPos.fromLong(point));
			for (long point : tag.getLongArray("ScavSpawns")) arena.scavSpawns.add(BlockPos.fromLong(point));
			for (long point : tag.getLongArray("LootPoints")) arena.lootPoints.add(BlockPos.fromLong(point));
			NbtCompound exits = tag.getCompound("Extracts");
			for (String name : exits.getKeys()) {
				NbtCompound exit = exits.getCompound(name);
				arena.extracts.put(name, new Extract(BlockPos.fromLong(exit.getLong("Pos")), exit.getBoolean("Loud")));
			}
			arena.bossSpawned = tag.getBoolean("BossSpawned");
			if (tag.containsUuid("ActiveBoss")) arena.activeBoss = tag.getUuid("ActiveBoss");
			if (tag.contains("CratePos")) arena.cratePos = BlockPos.fromLong(tag.getLong("CratePos"));
			return arena;
		}
	}

	record Extract(BlockPos pos, boolean loud) { }
}
