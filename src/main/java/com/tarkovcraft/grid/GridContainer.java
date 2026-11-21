package com.tarkovcraft.grid;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure 2D Tetris-style container supporting multi-cell items, rotation, stacking,
 * auto-placement, anti-recursion container nesting checks, and full NBT serialization.
 */
public final class GridContainer {
	public static final int MAX_NESTING_DEPTH = 2;

	private final int width;
	private final int height;
	private final UUID containerId;
	private final Map<UUID, GridEntry> entries = new LinkedHashMap<>();
	private final GridEntry[][] cells;

	public GridContainer(int width, int height) {
		this(width, height, UUID.randomUUID());
	}

	public GridContainer(int width, int height, UUID containerId) {
		if (width < 1 || height < 1) {
			throw new IllegalArgumentException("Grid dimensions must be >= 1, got " + width + "x" + height);
		}
		this.width = width;
		this.height = height;
		this.containerId = containerId;
		this.cells = new GridEntry[width][height];
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public UUID getContainerId() {
		return containerId;
	}

	public List<GridEntry> getEntries() {
		return Collections.unmodifiableList(new ArrayList<>(entries.values()));
	}

	@Nullable
	public GridEntry getEntry(UUID entryId) {
		return entries.get(entryId);
	}

	@Nullable
	public GridEntry getAt(int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height) {
			return null;
		}
		return cells[x][y];
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public boolean canPlace(ItemStack stack, int x, int y, boolean rotated) {
		return canPlace(stack, x, y, rotated, null);
	}

	public boolean canPlace(ItemStack stack, int x, int y, boolean rotated, @Nullable UUID ignoredEntryId) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (wouldCauseRecursion(stack)) {
			return false;
		}
		ItemSize size = ItemSizeRegistry.getSize(stack).oriented(rotated);
		if (x < 0 || y < 0 || x + size.width() > width || y + size.height() > height) {
			return false;
		}
		for (int dx = 0; dx < size.width(); dx++) {
			for (int dy = 0; dy < size.height(); dy++) {
				GridEntry existing = cells[x + dx][y + dy];
				if (existing != null && !existing.getEntryId().equals(ignoredEntryId)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Prevents placing a container inside itself or exceeding MAX_NESTING_DEPTH.
	 */
	public boolean wouldCauseRecursion(ItemStack candidate) {
		UUID candidateContainerId = ContainerItemHelper.getContainerUuid(candidate);
		if (candidateContainerId != null && candidateContainerId.equals(this.containerId)) {
			return true;
		}
		if (ContainerItemHelper.containsContainerId(candidate, this.containerId)) {
			return true;
		}
		return ContainerItemHelper.getNestingDepth(candidate) >= MAX_NESTING_DEPTH;
	}

	/**
	 * Attempts to place stack at (x, y, rotated). If the target cell has a matching stackable item,
	 * merges into it first. Returns the created/updated GridEntry or null if placement failed.
	 */
	@Nullable
	public GridEntry place(ItemStack stack, int x, int y, boolean rotated) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		// Try stacking onto an existing entry at (x, y)
		GridEntry existing = getAt(x, y);
		if (existing != null && canMerge(existing.getStack(), stack)) {
			int max = existing.getStack().getMaxCount();
			int space = max - existing.getStack().getCount();
			if (space >= stack.getCount()) {
				existing.getStack().increment(stack.getCount());
				return existing;
			}
		}

		if (!canPlace(stack, x, y, rotated, null)) {
			return null;
		}
		GridEntry entry = GridEntry.create(stack.copy(), x, y, rotated);
		insertInternal(entry);
		return entry;
	}

	/**
	 * Places an entry preserving a specific UUID (e.g., when moving within the same container).
	 */
	@Nullable
	public GridEntry placeWithId(UUID entryId, ItemStack stack, int x, int y, boolean rotated) {
		if (!canPlace(stack, x, y, rotated, null)) {
			return null;
		}
		GridEntry entry = new GridEntry(entryId, stack.copy(), x, y, rotated);
		insertInternal(entry);
		return entry;
	}

	/**
	 * Auto-places stack into the first available space (trying stacking first, then normal, then rotated).
	 * Mutates the passed stack's count if partially/fully stacked, returning true if completely inserted.
	 */
	public boolean tryAutoPlace(ItemStack stack) {
		if (stack == null || stack.isEmpty() || wouldCauseRecursion(stack)) {
			return false;
		}
		// 1. Try merging into existing stacks
		if (stack.isStackable()) {
			for (GridEntry entry : entries.values()) {
				if (canMerge(entry.getStack(), stack)) {
					int space = entry.getStack().getMaxCount() - entry.getStack().getCount();
					if (space > 0) {
						int transfer = Math.min(space, stack.getCount());
						entry.getStack().increment(transfer);
						stack.decrement(transfer);
						if (stack.isEmpty()) {
							return true;
						}
					}
				}
			}
		}

		// 2. Try free cells (normal orientation, then rotated if non-square)
		ItemSize baseSize = ItemSizeRegistry.getSize(stack);
		boolean[] orientations = baseSize.width() == baseSize.height()
			? new boolean[]{false}
			: new boolean[]{false, true};

		for (boolean rot : orientations) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (canPlace(stack, x, y, rot)) {
						GridEntry entry = GridEntry.create(stack.copy(), x, y, rot);
						insertInternal(entry);
						stack.setCount(0);
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Places stack into the first completely free cell (normal orientation first, then rotated),
	 * NEVER merging into existing stacks. Returns the created GridEntry or null if full.
	 */
	@Nullable
	public GridEntry tryPlaceInFreeCell(ItemStack stack) {
		if (stack == null || stack.isEmpty() || wouldCauseRecursion(stack)) {
			return null;
		}
		ItemSize baseSize = ItemSizeRegistry.getSize(stack);
		boolean[] orientations = baseSize.width() == baseSize.height()
			? new boolean[]{false}
			: new boolean[]{false, true};

		for (boolean rot : orientations) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (canPlace(stack, x, y, rot)) {
						GridEntry entry = GridEntry.create(stack.copy(), x, y, rot);
						insertInternal(entry);
						return entry;
					}
				}
			}
		}
		return null;
	}

	@Nullable
	public GridEntry remove(UUID entryId) {
		GridEntry entry = entries.remove(entryId);
		if (entry == null) {
			return null;
		}
		clearCells(entry);
		return entry;
	}

	public void clear() {
		entries.clear();
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				cells[x][y] = null;
			}
		}
	}

	private void insertInternal(GridEntry entry) {
		entries.put(entry.getEntryId(), entry);
		ItemSize size = entry.getOccupiedSize();
		for (int dx = 0; dx < size.width(); dx++) {
			for (int dy = 0; dy < size.height(); dy++) {
				cells[entry.getX() + dx][entry.getY() + dy] = entry;
			}
		}
	}

	private void clearCells(GridEntry entry) {
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if (cells[x][y] == entry) {
					cells[x][y] = null;
				}
			}
		}
	}

	private static boolean canMerge(ItemStack target, ItemStack incoming) {
		return target.isStackable()
			&& ItemStack.canCombine(target, incoming)
			&& target.getCount() < target.getMaxCount();
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Width", width);
		nbt.putInt("Height", height);
		nbt.putUuid("ContainerId", containerId);
		NbtList list = new NbtList();
		for (GridEntry entry : entries.values()) {
			if (!entry.getStack().isEmpty()) {
				list.add(entry.toNbt());
			}
		}
		nbt.put("Entries", list);
		return nbt;
	}

	public static GridContainer fromNbt(NbtCompound nbt, int defaultWidth, int defaultHeight) {
		int w = nbt.contains("Width") ? Math.max(1, nbt.getInt("Width")) : defaultWidth;
		int h = nbt.contains("Height") ? Math.max(1, nbt.getInt("Height")) : defaultHeight;
		UUID id = nbt.containsUuid("ContainerId") ? nbt.getUuid("ContainerId") : UUID.randomUUID();
		GridContainer container = new GridContainer(w, h, id);
		NbtList list = nbt.getList("Entries", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			GridEntry entry = GridEntry.fromNbt(list.getCompound(i));
			if (!entry.getStack().isEmpty() && container.canPlace(entry.getStack(), entry.getX(), entry.getY(), entry.isRotated())) {
				container.insertInternal(entry);
			}
		}
		return container;
	}
}
