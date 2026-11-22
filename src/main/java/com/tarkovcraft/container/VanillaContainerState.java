package com.tarkovcraft.container;

import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-world persistent storage mapping canonical vanilla container keys
 * (single/double chest, barrel, shulker, player ender chest) to their
 * TarkovCraft Tetris GridContainer layout and per-player ContainerSearchMemory.
 */
public final class VanillaContainerState extends PersistentState {
	public static final String DATA_NAME = "tarkovcraft_vanilla_containers";

	private final Map<String, StoredVanillaContainer> containers = new LinkedHashMap<>();

	public static final class StoredVanillaContainer {
		private GridContainer grid;
		private ContainerSearchMemory searchMemory;
		@Nullable
		private TarkovLootPool assignedPool;
		private long lastLootEpochMs;
		private boolean playerPlaced;

		public StoredVanillaContainer(GridContainer grid, ContainerSearchMemory searchMemory) {
			this.grid = grid;
			this.searchMemory = searchMemory;
		}

		public GridContainer getGrid() {
			return grid;
		}

		public void setGrid(GridContainer grid) {
			this.grid = grid;
		}

		public ContainerSearchMemory getSearchMemory() {
			return searchMemory;
		}

		public void setSearchMemory(ContainerSearchMemory searchMemory) {
			this.searchMemory = searchMemory;
		}

		@Nullable
		public TarkovLootPool getAssignedPool() {
			return assignedPool;
		}

		public void setAssignedPool(@Nullable TarkovLootPool assignedPool) {
			this.assignedPool = assignedPool;
		}

		public long getLastLootEpochMs() {
			return lastLootEpochMs;
		}

		public void setLastLootEpochMs(long lastLootEpochMs) {
			this.lastLootEpochMs = Math.max(0L, lastLootEpochMs);
		}

		public boolean isPlayerPlaced() {
			return playerPlaced;
		}

		public void setPlayerPlaced(boolean playerPlaced) {
			this.playerPlaced = playerPlaced;
		}

		public NbtCompound toNbt() {
			NbtCompound tag = new NbtCompound();
			tag.put("Grid", grid.toNbt());
			tag.put("SearchMemory", searchMemory.toNbt());
			if (assignedPool != null) {
				tag.putString("AssignedPool", assignedPool.getId());
			}
			if (lastLootEpochMs > 0L) {
				tag.putLong("LastLootEpochMs", lastLootEpochMs);
			}
			if (playerPlaced) {
				tag.putBoolean("PlayerPlaced", true);
			}
			return tag;
		}

		public static StoredVanillaContainer fromNbt(NbtCompound tag, int defaultWidth, int defaultHeight) {
			GridContainer grid = tag.contains("Grid", NbtElement.COMPOUND_TYPE)
				? GridContainer.fromNbt(tag.getCompound("Grid"), defaultWidth, defaultHeight)
				: new GridContainer(defaultWidth, defaultHeight);
			ContainerSearchMemory mem = tag.contains("SearchMemory", NbtElement.COMPOUND_TYPE)
				? ContainerSearchMemory.fromNbt(tag.getCompound("SearchMemory"))
				: new ContainerSearchMemory();
			StoredVanillaContainer stored = new StoredVanillaContainer(grid, mem);
			if (tag.contains("AssignedPool", NbtElement.STRING_TYPE)) {
				stored.assignedPool = TarkovLootPool.byId(tag.getString("AssignedPool"));
			}
			stored.lastLootEpochMs = Math.max(0L, tag.getLong("LastLootEpochMs"));
			stored.playerPlaced = tag.getBoolean("PlayerPlaced");
			return stored;
		}
	}

	public static VanillaContainerState get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(
			VanillaContainerState::fromNbt,
			VanillaContainerState::new,
			DATA_NAME
		);
	}

	@Nullable
	public StoredVanillaContainer getEntry(String key) {
		return containers.get(key);
	}

	public StoredVanillaContainer getOrCreateEntry(String key, int baseWidth, int baseHeight) {
		StoredVanillaContainer existing = containers.get(key);
		if (existing != null) {
			return existing;
		}
		StoredVanillaContainer created = new StoredVanillaContainer(
			new GridContainer(baseWidth, baseHeight),
			new ContainerSearchMemory()
		);
		containers.put(key, created);
		markDirty();
		return created;
	}

	public void putEntry(String key, StoredVanillaContainer entry) {
		containers.put(key, entry);
		markDirty();
	}

	public void removeEntry(String key) {
		if (containers.remove(key) != null) {
			markDirty();
		}
	}

	public int size() {
		return containers.size();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList list = new NbtList();
		for (Map.Entry<String, StoredVanillaContainer> entry : containers.entrySet()) {
			NbtCompound row = entry.getValue().toNbt();
			row.putString("Key", entry.getKey());
			list.add(row);
		}
		nbt.put("Containers", list);
		return nbt;
	}

	public static VanillaContainerState fromNbt(NbtCompound nbt) {
		VanillaContainerState state = new VanillaContainerState();
		if (nbt == null || !nbt.contains("Containers", NbtElement.LIST_TYPE)) {
			return state;
		}
		NbtList list = nbt.getList("Containers", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			NbtCompound row = list.getCompound(i);
			String key = row.getString("Key");
			if (key == null || key.isEmpty()) {
				continue;
			}
			StoredVanillaContainer stored = StoredVanillaContainer.fromNbt(row, 6, 5);
			state.containers.put(key, stored);
		}
		return state;
	}
}
