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
		private List<BlockPos> vertices = List.of();
		private boolean ready;
		final Set<BlockPos> airdropPoints = new LinkedHashSet<>();
		final Set<BlockPos> pointsOfInterest = new LinkedHashSet<>();
		boolean airdropsEnabled = true;
		boolean bossEnabled;
		boolean scavBotsEnabled = true;
		int scavBotCount = 8;
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

		boolean setCorner(int index, BlockPos pos) {
			if (index < 0 || index > vertices.size()) return false;
			var updated = new java.util.ArrayList<>(vertices);
			if (index == updated.size()) updated.add(pos);
			else updated.set(index, pos);
			setVertices(updated);
			return true;
		}

		void setVertices(List<BlockPos> points) {
			setVertices(points, ArenaPolygon.isSimple(points));
		}

		void setVertices(List<BlockPos> points, boolean valid) {
			vertices = points.stream().map(BlockPos::toImmutable).toList();
			ready = valid;
		}

		public BlockPos center() {
			if (!ready) return anchor;
			long x = 0;
			long z = 0;
			for (BlockPos corner : vertices) {
				x += corner.getX();
				z += corner.getZ();
			}
			BlockPos mean = new BlockPos((int) Math.round((double) x / vertices.size()), anchor.getY(),
				(int) Math.round((double) z / vertices.size()));
			if (contains(mean.getX() + 0.5, mean.getZ() + 0.5)) return mean;
			if (contains(anchor.getX() + 0.5, anchor.getZ() + 0.5)) return anchor;
			// A point just inside an edge gives event spawners a search origin even in a deep concavity.
			for (int i = 0; i < vertices.size(); i++) {
				BlockPos a = vertices.get(i);
				BlockPos b = vertices.get((i + 1) % vertices.size());
				BlockPos middle = BlockPos.ofFloored((a.getX() + (double) b.getX()) / 2, anchor.getY(),
					(a.getZ() + (double) b.getZ()) / 2);
				for (BlockPos nearby : BlockPos.iterateOutwards(middle, 2, 0, 2)) {
					if (contains(nearby.getX() + 0.5, nearby.getZ() + 0.5)) return nearby.toImmutable();
				}
			}
			return mean;
		}

		public boolean ready() {
			return ready;
		}

		public boolean contains(int x, int z) {
			return contains((double) x, z);
		}

		public boolean contains(double x, double z) {
			return ready && ArenaPolygon.contains(vertices, x, z);
		}

		public List<BlockPos> corners() {
			return vertices;
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
			tag.putLongArray("Vertices", vertices.stream().mapToLong(BlockPos::asLong).toArray());
			tag.putLongArray("AirdropPoints", airdropPoints.stream().mapToLong(BlockPos::asLong).toArray());
			tag.putLongArray("Poi", pointsOfInterest.stream().mapToLong(BlockPos::asLong).toArray());
			tag.putBoolean("AirdropsEnabled", airdropsEnabled);
			tag.putInt("MaxGroundY", maxGroundY);
			tag.putBoolean("BossEnabled", bossEnabled);
			tag.putBoolean("ScavBotsEnabled", scavBotsEnabled);
			tag.putInt("ScavBotCount", scavBotCount);
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
				exit.putDouble("Radius", extract.radius());
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
			if (tag.contains("Vertices", NbtElement.LONG_ARRAY_TYPE)) {
				arena.setVertices(Arrays.stream(tag.getLongArray("Vertices")).mapToObj(BlockPos::fromLong).toList());
			} else {
				BlockPos[] slots = new BlockPos[4];
				for (int i = 0; i < slots.length; i++) {
					if (tag.contains("Corner" + i)) slots[i] = BlockPos.fromLong(tag.getLong("Corner" + i));
				}
				arena.setVertices(ArenaPolygon.migrateCorners(Arrays.asList(slots)));
			}
			for (long point : tag.getLongArray("AirdropPoints")) arena.airdropPoints.add(BlockPos.fromLong(point));
			for (long point : tag.getLongArray("Poi")) arena.pointsOfInterest.add(BlockPos.fromLong(point));
			arena.airdropsEnabled = !tag.contains("AirdropsEnabled") || tag.getBoolean("AirdropsEnabled");
			if (tag.contains("MaxGroundY")) arena.maxGroundY = tag.getInt("MaxGroundY");
			arena.bossEnabled = tag.getBoolean("BossEnabled");
			if (tag.contains("ScavBotsEnabled")) arena.scavBotsEnabled = tag.getBoolean("ScavBotsEnabled");
			if (tag.contains("ScavBotCount")) arena.scavBotCount = Math.max(0, Math.min(32, tag.getInt("ScavBotCount")));
			arena.displayName = tag.getString("DisplayName");
			if (tag.contains("DurationMinutes")) arena.durationMinutes = tag.getInt("DurationMinutes");
			for (long point : tag.getLongArray("PmcSpawns")) arena.pmcSpawns.add(BlockPos.fromLong(point));
			for (long point : tag.getLongArray("ScavSpawns")) arena.scavSpawns.add(BlockPos.fromLong(point));
			for (long point : tag.getLongArray("LootPoints")) arena.lootPoints.add(BlockPos.fromLong(point));
			NbtCompound exits = tag.getCompound("Extracts");
			for (String name : exits.getKeys()) {
				NbtCompound exit = exits.getCompound(name);
				arena.extracts.put(name, new Extract(BlockPos.fromLong(exit.getLong("Pos")), exit.getBoolean("Loud"),
					exit.getDouble("Radius")));
			}
			arena.bossSpawned = tag.getBoolean("BossSpawned");
			if (tag.containsUuid("ActiveBoss")) arena.activeBoss = tag.getUuid("ActiveBoss");
			if (tag.contains("CratePos")) arena.cratePos = BlockPos.fromLong(tag.getLong("CratePos"));
			return arena;
		}
	}

	record Extract(BlockPos pos, boolean loud, double radius) {
		Extract {
			radius = com.tarkovcraft.network.RaidExtractPackets.validRadius(radius);
		}

		Extract(BlockPos pos, boolean loud) {
			this(pos, loud, com.tarkovcraft.network.RaidExtractPackets.DEFAULT_RADIUS);
		}

		boolean contains(net.minecraft.util.math.Vec3d feet) {
			return com.tarkovcraft.network.RaidExtractPackets.contains(pos, radius, feet);
		}
	}
}
