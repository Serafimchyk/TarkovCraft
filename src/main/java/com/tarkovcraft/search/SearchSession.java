package com.tarkovcraft.search;

import com.tarkovcraft.grid.GridEntry;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Server-authoritative 2-stage search state machine supporting ordered search queues:
 * - External targets (e.g. Loot Crate, or on a Corpse: Pockets -> Tactical Rig -> Backpack)
 *   are searched automatically in order while looting.
 * - Own taken containers (Backpack / Rig stripped from a corpse) ONLY search while
 *   ownInventoryOpen == true (never in the background!).
 *
 * Stage 1: Fixed 20-tick (1.0s) reveal of container slots -> exposes item silhouettes.
 * Stage 2: Searches each undiscovered item individually; duration depends on item rarity.
 */
public final class SearchSession {
	public static final int GRID_REVEAL_TICKS = 20; // 1.0 second at 20 TPS

	public enum Stage {
		IDLE,
		REVEALING_GRID,
		SEARCHING_ITEM
	}

	private final UUID playerId;
	private Stage stage = Stage.IDLE;
	@Nullable
	private UUID activeTargetId;
	@Nullable
	private UUID activeEntryId;
	@Nullable
	private UUID prioritizedTargetId;
	private int progressTicks;
	private int totalTicks;

	public SearchSession(UUID playerId) {
		this.playerId = playerId;
	}

	public Stage getStage() {
		return stage;
	}

	@Nullable
	public UUID getActiveTargetId() {
		return activeTargetId;
	}

	@Nullable
	public UUID getActiveEntryId() {
		return activeEntryId;
	}

	public int getProgressTicks() {
		return progressTicks;
	}

	public int getTotalTicks() {
		return totalTicks;
	}

	public float getProgressRatio() {
		if (totalTicks <= 0) {
			return 0.0f;
		}
		return Math.min(1.0f, (float) progressTicks / (float) totalTicks);
	}

	public void prioritizeTarget(@Nullable UUID targetId) {
		this.prioritizedTargetId = targetId;
	}

	public void reset() {
		this.stage = Stage.IDLE;
		this.activeTargetId = null;
		this.activeEntryId = null;
		this.prioritizedTargetId = null;
		this.progressTicks = 0;
		this.totalTicks = 0;
	}

	/**
	 * Convenience overload for single external + single own backpack target.
	 */
	public boolean tick(boolean ownInventoryOpen, @Nullable SearchTarget externalTarget, @Nullable SearchTarget ownBackpackTarget) {
		List<SearchTarget> ext = externalTarget == null ? List.of() : List.of(externalTarget);
		List<SearchTarget> own = ownBackpackTarget == null ? List.of() : List.of(ownBackpackTarget);
		return tickQueue(ownInventoryOpen, ext, own);
	}

	/**
	 * Advances search by 1 server tick across ordered external and own container queues.
	 * External targets always search before own taken containers; own taken containers
	 * ONLY search while ownInventoryOpen == true.
	 */
	public boolean tickQueue(boolean ownInventoryOpen, List<SearchTarget> externalTargets, List<SearchTarget> ownTargets) {
		SearchTarget target = selectActiveTarget(ownInventoryOpen, externalTargets, ownTargets);
		if (target == null) {
			if (stage != Stage.IDLE) {
				reset();
				return true;
			}
			return false;
		}

		if (!target.getTargetId().equals(activeTargetId)) {
			activeTargetId = target.getTargetId();
			activeEntryId = null;
			progressTicks = 0;
			totalTicks = 0;
		}

		// Stage 1: Reveal grid slots (fixed 1 second) -> silhouettes appear
		if (!target.isGridRevealed(playerId)) {
			if (stage != Stage.REVEALING_GRID) {
				stage = Stage.REVEALING_GRID;
				activeEntryId = null;
				progressTicks = 0;
				totalTicks = com.tarkovcraft.config.TarkovConfig.get().getContainerRevealTicks();
			}
			progressTicks++;
			if (progressTicks >= totalTicks) {
				target.getMemory().markGridRevealed(playerId);
				target.markDirty();
				progressTicks = 0;
				totalTicks = 0;
				stage = Stage.IDLE;
				return true;
			}
			return false;
		}

		// Stage 2: Search each undiscovered item individually by rarity
		GridEntry nextEntry = target.findNextUndiscoveredEntry(playerId);
		if (nextEntry == null) {
			reset();
			return false;
		}

		if (stage != Stage.SEARCHING_ITEM || !nextEntry.getEntryId().equals(activeEntryId)) {
			stage = Stage.SEARCHING_ITEM;
			activeEntryId = nextEntry.getEntryId();
			progressTicks = 0;
			totalTicks = ItemRarityRegistry.getSearchTicks(nextEntry.getStack());
		}

		progressTicks++;
		if (progressTicks >= totalTicks) {
			target.getMemory().markEntryDiscovered(playerId, nextEntry.getEntryId());
			target.markDirty();
			activeEntryId = null;
			progressTicks = 0;
			totalTicks = 0;
			stage = Stage.IDLE;
			return true;
		}
		return false;
	}

	@Nullable
	private SearchTarget selectActiveTarget(boolean ownInventoryOpen, List<SearchTarget> externalTargets, List<SearchTarget> ownTargets) {
		if (prioritizedTargetId != null) {
			for (SearchTarget t : externalTargets) {
				if (t != null && prioritizedTargetId.equals(t.getTargetId()) && !t.isFullySearched(playerId)) {
					return t;
				}
			}
			if (ownInventoryOpen) {
				for (SearchTarget t : ownTargets) {
					if (t != null && prioritizedTargetId.equals(t.getTargetId()) && !t.isFullySearched(playerId)) {
						return t;
					}
				}
			}
			prioritizedTargetId = null;
		}
		for (SearchTarget t : externalTargets) {
			if (t != null && !t.isFullySearched(playerId)) {
				return t;
			}
		}
		if (ownInventoryOpen) {
			for (SearchTarget t : ownTargets) {
				if (t != null && !t.isFullySearched(playerId)) {
					return t;
				}
			}
		}
		return null;
	}

	public static boolean canTakeEntry(UUID playerId, SearchTarget target, UUID entryId) {
		return target.isGridRevealed(playerId) && target.getMemory().isEntryDiscovered(playerId, entryId);
	}
}
