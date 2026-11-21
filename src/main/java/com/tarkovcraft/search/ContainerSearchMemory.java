package com.tarkovcraft.search;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks per-player search progress for a container (or wearable backpack/rig):
 * - Whether the initial 1-second grid reveal is finished for a specific player.
 * - Which specific GridEntry UUIDs have been discovered by that player.
 * Already-searched containers and items stay revealed permanently for that player,
 * while other players must search them independently.
 */
public final class ContainerSearchMemory {
	private final Map<UUID, PlayerProgress> byPlayer = new HashMap<>();

	public static final class PlayerProgress {
		private boolean gridRevealed;
		private final Set<UUID> discoveredEntries = new HashSet<>();

		public boolean isGridRevealed() {
			return gridRevealed;
		}

		public void setGridRevealed(boolean gridRevealed) {
			this.gridRevealed = gridRevealed;
		}

		public Set<UUID> getDiscoveredEntries() {
			return discoveredEntries;
		}

		public boolean isEntryDiscovered(UUID entryId) {
			return discoveredEntries.contains(entryId);
		}

		public void markEntryDiscovered(UUID entryId) {
			discoveredEntries.add(entryId);
		}
	}

	public PlayerProgress getOrCreate(UUID playerId) {
		return byPlayer.computeIfAbsent(playerId, k -> new PlayerProgress());
	}

	public boolean isGridRevealed(UUID playerId) {
		PlayerProgress progress = byPlayer.get(playerId);
		return progress != null && progress.isGridRevealed();
	}

	public void markGridRevealed(UUID playerId) {
		getOrCreate(playerId).setGridRevealed(true);
	}

	public boolean isEntryDiscovered(UUID playerId, UUID entryId) {
		PlayerProgress progress = byPlayer.get(playerId);
		return progress != null && progress.isEntryDiscovered(entryId);
	}

	public void markEntryDiscovered(UUID playerId, UUID entryId) {
		PlayerProgress progress = getOrCreate(playerId);
		progress.setGridRevealed(true);
		progress.markEntryDiscovered(entryId);
	}

	public void markAllDiscovered(UUID playerId, Iterable<UUID> entryIds) {
		PlayerProgress progress = getOrCreate(playerId);
		progress.setGridRevealed(true);
		for (UUID id : entryIds) {
			progress.markEntryDiscovered(id);
		}
	}

	public void resetForPlayer(UUID playerId) {
		byPlayer.remove(playerId);
	}

	public void resetAll() {
		byPlayer.clear();
	}

	public NbtCompound toNbt() {
		NbtCompound root = new NbtCompound();
		NbtList playersList = new NbtList();
		for (Map.Entry<UUID, PlayerProgress> entry : byPlayer.entrySet()) {
			NbtCompound pNbt = new NbtCompound();
			pNbt.putUuid("PlayerId", entry.getKey());
			pNbt.putBoolean("GridRevealed", entry.getValue().isGridRevealed());
			NbtList ids = new NbtList();
			for (UUID itemId : entry.getValue().getDiscoveredEntries()) {
				ids.add(NbtString.of(itemId.toString()));
			}
			pNbt.put("Discovered", ids);
			playersList.add(pNbt);
		}
		root.put("Players", playersList);
		return root;
	}

	public static ContainerSearchMemory fromNbt(NbtCompound nbt) {
		ContainerSearchMemory memory = new ContainerSearchMemory();
		if (nbt == null) {
			return memory;
		}
		NbtList playersList = nbt.getList("Players", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < playersList.size(); i++) {
			NbtCompound pNbt = playersList.getCompound(i);
			if (!pNbt.containsUuid("PlayerId")) {
				continue;
			}
			UUID playerId = pNbt.getUuid("PlayerId");
			PlayerProgress progress = memory.getOrCreate(playerId);
			progress.setGridRevealed(pNbt.getBoolean("GridRevealed"));
			NbtList ids = pNbt.getList("Discovered", NbtElement.STRING_TYPE);
			for (int j = 0; j < ids.size(); j++) {
				try {
					progress.markEntryDiscovered(UUID.fromString(ids.getString(j)));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		return memory;
	}
}
