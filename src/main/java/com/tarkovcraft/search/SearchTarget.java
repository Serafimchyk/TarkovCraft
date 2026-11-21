package com.tarkovcraft.search;

import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Represents a searchable container (one or more grids + its per-player search memory).
 */
public final class SearchTarget {
	private final UUID targetId;
	private final List<GridContainer> grids;
	private final ContainerSearchMemory memory;
	private final Runnable onDirty;

	public SearchTarget(UUID targetId, List<GridContainer> grids, ContainerSearchMemory memory, Runnable onDirty) {
		this.targetId = targetId;
		this.grids = grids;
		this.memory = memory;
		this.onDirty = onDirty;
	}

	public UUID getTargetId() {
		return targetId;
	}

	public List<GridContainer> getGrids() {
		return grids;
	}

	public ContainerSearchMemory getMemory() {
		return memory;
	}

	public void markDirty() {
		if (onDirty != null) {
			onDirty.run();
		}
	}

	public boolean isGridRevealed(UUID playerId) {
		return memory.isGridRevealed(playerId);
	}

	@Nullable
	public GridEntry findNextUndiscoveredEntry(UUID playerId) {
		for (GridContainer grid : grids) {
			for (GridEntry entry : grid.getEntries()) {
				if (!entry.getStack().isEmpty() && !memory.isEntryDiscovered(playerId, entry.getEntryId())) {
					return entry;
				}
			}
		}
		return null;
	}

	public boolean isFullySearched(UUID playerId) {
		return isGridRevealed(playerId) && findNextUndiscoveredEntry(playerId) == null;
	}
}
