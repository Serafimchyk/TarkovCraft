package com.tarkovcraft.grid;

import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Helper for reading and writing GridContainers, container UUIDs, and per-player
 * ContainerSearchMemory on wearable container ItemStacks (Backpacks & Tactical Rigs).
 */
public final class ContainerItemHelper {
	private static final String KEY_ROOT = "TarkovContainer";
	private static final String KEY_UUID = "ContainerUuid";
	private static final String KEY_GRID = "Grid";
	private static final String KEY_GRIDS = "Grids";
	private static final String KEY_SEARCH = "SearchMemory";
	private static final String KEY_REQUIRES_SEARCH = "RequiresSearch";

	private ContainerItemHelper() {
	}

	public static boolean isContainerItem(ItemStack stack) {
		return !stack.isEmpty()
			&& (stack.getItem() instanceof BackpackItem
			|| stack.getItem() instanceof TacticalRigItem
			|| stack.getItem() instanceof TarkovCaseItem);
	}

	public static UUID getOrCreateContainerUuid(ItemStack stack) {
		NbtCompound root = stack.getOrCreateSubNbt(KEY_ROOT);
		if (!root.containsUuid(KEY_UUID)) {
			root.putUuid(KEY_UUID, UUID.randomUUID());
		}
		return root.getUuid(KEY_UUID);
	}

	@Nullable
	public static UUID getContainerUuid(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasNbt()) {
			return null;
		}
		NbtCompound root = stack.getSubNbt(KEY_ROOT);
		if (root != null && root.containsUuid(KEY_UUID)) {
			return root.getUuid(KEY_UUID);
		}
		return null;
	}

	public static boolean requiresSearch(ItemStack stack) {
		if (!isContainerItem(stack) || !stack.hasNbt()) {
			return false;
		}
		NbtCompound root = stack.getSubNbt(KEY_ROOT);
		return root != null && root.getBoolean(KEY_REQUIRES_SEARCH);
	}

	public static void setRequiresSearch(ItemStack stack, boolean requiresSearch) {
		if (!isContainerItem(stack)) {
			return;
		}
		NbtCompound root = stack.getOrCreateSubNbt(KEY_ROOT);
		root.putBoolean(KEY_REQUIRES_SEARCH, requiresSearch);
	}

	public static ContainerSearchMemory getSearchMemory(ItemStack stack) {
		if (!isContainerItem(stack) || !stack.hasNbt()) {
			return new ContainerSearchMemory();
		}
		NbtCompound root = stack.getSubNbt(KEY_ROOT);
		if (root != null && root.contains(KEY_SEARCH, NbtElement.COMPOUND_TYPE)) {
			return ContainerSearchMemory.fromNbt(root.getCompound(KEY_SEARCH));
		}
		return new ContainerSearchMemory();
	}

	public static void saveSearchMemory(ItemStack stack, ContainerSearchMemory memory) {
		if (!isContainerItem(stack)) {
			return;
		}
		NbtCompound root = stack.getOrCreateSubNbt(KEY_ROOT);
		root.put(KEY_SEARCH, memory.toNbt());
	}

	public static GridContainer readBackpackGrid(ItemStack stack) {
		if (!(stack.getItem() instanceof BackpackItem backpack)) {
			return new GridContainer(1, 1);
		}
		UUID uuid = getOrCreateContainerUuid(stack);
		NbtCompound root = stack.getOrCreateSubNbt(KEY_ROOT);
		if (root.contains(KEY_GRID, NbtElement.COMPOUND_TYPE)) {
			return GridContainer.fromNbt(root.getCompound(KEY_GRID), backpack.getGridWidth(), backpack.getGridHeight());
		}
		GridContainer grid = new GridContainer(backpack.getGridWidth(), backpack.getGridHeight(), uuid);
		root.put(KEY_GRID, grid.toNbt());
		return grid;
	}

	public static void writeBackpackGrid(ItemStack stack, GridContainer grid) {
		if (!(stack.getItem() instanceof BackpackItem)) {
			return;
		}
		NbtCompound root = stack.getOrCreateSubNbt(KEY_ROOT);
		root.putUuid(KEY_UUID, grid.getContainerId());
		root.put(KEY_GRID, grid.toNbt());
	}

	public static List<GridContainer> readRigGrids(ItemStack stack) {
		List<GridContainer> result = new ArrayList<>();
		if (!(stack.getItem() instanceof TacticalRigItem rig)) {
			return result;
		}
		getOrCreateContainerUuid(stack);
		NbtCompound root = stack.getOrCreateSubNbt(KEY_ROOT);
		List<ItemSize> layouts = rig.getCompartmentLayouts();
		NbtList list = root.getList(KEY_GRIDS, NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < layouts.size(); i++) {
			ItemSize sz = layouts.get(i);
			if (i < list.size()) {
				result.add(GridContainer.fromNbt(list.getCompound(i), sz.width(), sz.height()));
			} else {
				result.add(new GridContainer(sz.width(), sz.height()));
			}
		}
		return result;
	}

	public static void writeRigGrids(ItemStack stack, List<GridContainer> grids) {
		if (!(stack.getItem() instanceof TacticalRigItem)) {
			return;
		}
		NbtCompound root = stack.getOrCreateSubNbt(KEY_ROOT);
		NbtList list = new NbtList();
		for (GridContainer g : grids) {
			list.add(g.toNbt());
		}
		root.put(KEY_GRIDS, list);
	}

	public static GridContainer readCaseGrid(ItemStack stack) {
		if (!(stack.getItem() instanceof TarkovCaseItem caseItem)) {
			return new GridContainer(1, 1);
		}
		UUID uuid = getOrCreateContainerUuid(stack);
		NbtCompound root = stack.getOrCreateSubNbt(KEY_ROOT);
		if (root.contains(KEY_GRID, NbtElement.COMPOUND_TYPE)) {
			return GridContainer.fromNbt(root.getCompound(KEY_GRID), caseItem.getGridWidth(), caseItem.getGridHeight());
		}
		GridContainer grid = new GridContainer(caseItem.getGridWidth(), caseItem.getGridHeight(), uuid);
		root.put(KEY_GRID, grid.toNbt());
		return grid;
	}

	public static void writeCaseGrid(ItemStack stack, GridContainer grid) {
		if (!(stack.getItem() instanceof TarkovCaseItem)) {
			return;
		}
		NbtCompound root = stack.getOrCreateSubNbt(KEY_ROOT);
		root.putUuid(KEY_UUID, grid.getContainerId());
		root.put(KEY_GRID, grid.toNbt());
	}

	public static List<GridContainer> readAllGrids(ItemStack stack) {
		if (stack.getItem() instanceof BackpackItem) {
			return List.of(readBackpackGrid(stack));
		}
		if (stack.getItem() instanceof TacticalRigItem) {
			return readRigGrids(stack);
		}
		if (stack.getItem() instanceof TarkovCaseItem) {
			return List.of(readCaseGrid(stack));
		}
		return List.of();
	}

	public static boolean containsContainerId(ItemStack stack, UUID targetId) {
		if (!isContainerItem(stack) || targetId == null) {
			return false;
		}
		UUID selfId = getContainerUuid(stack);
		if (targetId.equals(selfId)) {
			return true;
		}
		for (GridContainer grid : readAllGrids(stack)) {
			if (targetId.equals(grid.getContainerId())) {
				return true;
			}
			for (GridEntry entry : grid.getEntries()) {
				if (containsContainerId(entry.getStack(), targetId)) {
					return true;
				}
			}
		}
		return false;
	}

	public static int getNestingDepth(ItemStack stack) {
		if (!isContainerItem(stack)) {
			return 0;
		}
		int maxChildDepth = 0;
		for (GridContainer grid : readAllGrids(stack)) {
			for (GridEntry entry : grid.getEntries()) {
				maxChildDepth = Math.max(maxChildDepth, getNestingDepth(entry.getStack()));
			}
		}
		return 1 + maxChildDepth;
	}
}
