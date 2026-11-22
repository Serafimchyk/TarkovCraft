package com.tarkovcraft.container;

import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.loot.CityZoneClassifier;
import com.tarkovcraft.loot.TarkovLootGenerator;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.screen.TarkovScreenHandler;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Nameable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bridge between vanilla storage containers (Chest, Trapped Chest, Barrel, Shulker Box, Ender Chest)
 * and TarkovCraft's Tetris grid & §6 search pipeline.
 */
public final class VanillaContainerBridge {
	public static final int SINGLE_WIDTH = 6;
	public static final int SINGLE_HEIGHT = 5;
	public static final int DOUBLE_WIDTH = 9;
	public static final int DOUBLE_HEIGHT = 7;
	public static final int MAX_AUTO_HEIGHT = 60;

	private VanillaContainerBridge() {
	}

	/**
	 * Returns true for the 5 supported vanilla storage block hierarchies
	 * (ChestBlock covers both normal and TrappedChestBlock).
	 */
	public static boolean isSupportedBlock(Block block) {
		return block instanceof ChestBlock
			|| block instanceof BarrelBlock
			|| block instanceof ShulkerBoxBlock
			|| block instanceof EnderChestBlock;
	}

	public static String canonicalBlockKey(BlockPos pos) {
		return "block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public static String canonicalDoubleKey(BlockPos a, BlockPos b) {
		boolean aLess = a.getX() != b.getX()
			? a.getX() < b.getX()
			: (a.getY() != b.getY() ? a.getY() < b.getY() : a.getZ() <= b.getZ());
		return canonicalBlockKey(aLess ? a : b);
	}

	public static String canonicalEnderKey(UUID playerId) {
		return "ender:" + playerId;
	}

	public static String resolveCanonicalKey(BlockPos pos, BlockState state, UUID playerId) {
		Block block = state.getBlock();
		if (block instanceof EnderChestBlock) {
			return canonicalEnderKey(playerId);
		}
		if (block instanceof ChestBlock
			&& state.contains(ChestBlock.CHEST_TYPE)
			&& state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
			BlockPos otherPos = pos.offset(ChestBlock.getFacing(state));
			return canonicalDoubleKey(pos, otherPos);
		}
		return canonicalBlockKey(pos);
	}

	public static void removeForBrokenBlock(ServerWorld world, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		if (block instanceof EnderChestBlock || !isSupportedBlock(block)) {
			return;
		}
		VanillaContainerState vcState = VanillaContainerState.get(world);
		vcState.removeEntry(canonicalBlockKey(pos));
		if (block instanceof ChestBlock
			&& state.contains(ChestBlock.CHEST_TYPE)
			&& state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
			BlockPos otherPos = pos.offset(ChestBlock.getFacing(state));
			vcState.removeEntry(canonicalDoubleKey(pos, otherPos));
		}
	}

	public static boolean isContainerBlocked(World world, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		if (block instanceof ChestBlock) {
			if (ChestBlock.isChestBlocked(world, pos)) {
				return true;
			}
			if (state.contains(ChestBlock.CHEST_TYPE) && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
				BlockPos otherPos = pos.offset(ChestBlock.getFacing(state));
				return ChestBlock.isChestBlocked(world, otherPos);
			}
			return false;
		}
		if (block instanceof EnderChestBlock) {
			BlockPos above = pos.up();
			return world.getBlockState(above).isSolidBlock(world, above);
		}
		if (block instanceof ShulkerBoxBlock) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof ShulkerBoxBlockEntity shulkerBe
				&& shulkerBe.getAnimationStage() == ShulkerBoxBlockEntity.AnimationStage.CLOSED) {
				Box box = ShulkerEntity.calculateBoundingBox(state.get(ShulkerBoxBlock.FACING), 0.0f, 0.5f)
					.offset(pos)
					.contract(1.0E-6);
				return !world.isSpaceEmpty(box);
			}
			return false;
		}
		return false;
	}

	/**
	 * Attempts to open a vanilla container at {@code pos} inside TarkovScreenHandler.
	 * Returns true if {@code pos} is a supported vanilla container (even if blocked).
	 */
	public static boolean tryOpenForPlayer(ServerPlayerEntity player, BlockPos pos) {
		if (!(player.getWorld() instanceof ServerWorld world)) {
			return false;
		}
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();
		if (!isSupportedBlock(block)) {
			return false;
		}

		if (isContainerBlocked(world, pos, state)) {
			player.sendMessage(Text.translatable("message.tarkovcraft.container_blocked"), true);
			return true;
		}

		EnderChestBlockEntity enderChestBe = null;
		Inventory liveInv;
		Text title = block.getName();

		if (block instanceof EnderChestBlock) {
			BlockEntity be = world.getBlockEntity(pos);
			if (!(be instanceof EnderChestBlockEntity ecBe)) {
				return false;
			}
			enderChestBe = ecBe;
			liveInv = player.getEnderChestInventory();
		} else if (block instanceof ChestBlock chestBlock) {
			BlockEntity be1 = world.getBlockEntity(pos);
			if (be1 instanceof LootableContainerBlockEntity loot1) {
				loot1.checkLootInteraction(player);
			}
			if (state.contains(ChestBlock.CHEST_TYPE) && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
				BlockPos otherPos = pos.offset(ChestBlock.getFacing(state));
				BlockEntity be2 = world.getBlockEntity(otherPos);
				if (be2 instanceof LootableContainerBlockEntity loot2) {
					loot2.checkLootInteraction(player);
				}
			}
			liveInv = ChestBlock.getInventory(chestBlock, state, world, pos, true);
			if (liveInv instanceof Nameable named) {
				title = named.getDisplayName();
			} else if (be1 instanceof Nameable named && named.hasCustomName()) {
				title = named.getDisplayName();
			}
		} else {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof LootableContainerBlockEntity lootBe) {
				lootBe.checkLootInteraction(player);
			}
			liveInv = be instanceof Inventory inv ? inv : null;
			if (be instanceof Nameable named && named.hasCustomName()) {
				title = named.getDisplayName();
			}
		}

		if (liveInv == null) {
			return false;
		}

		boolean isDouble = liveInv.size() > 27;
		int baseW = isDouble ? DOUBLE_WIDTH : SINGLE_WIDTH;
		int baseH = isDouble ? DOUBLE_HEIGHT : SINGLE_HEIGHT;

		String key = resolveCanonicalKey(pos, state, player.getUuid());
		VanillaContainerState vcState = VanillaContainerState.get(world);
		VanillaContainerState.StoredVanillaContainer stored = vcState.getOrCreateEntry(key, baseW, baseH);
		boolean isShulker = block instanceof ShulkerBoxBlock;

		boolean changed = false;
		if (!(block instanceof EnderChestBlock) && !stored.isPlayerPlaced() && TarkovConfig.get().cityAutoLootVanillaContainers) {
			if (stored.getLastLootEpochMs() == 0L) {
				if (!liveInv.isEmpty()) {
					// Already contains player-placed items before auto-loot ever touched it -> protect it
					stored.setPlayerPlaced(true);
					changed = true;
				} else {
					TarkovLootPool pool = stored.getAssignedPool() != null
						? stored.getAssignedPool()
						: CityZoneClassifier.classify(world, pos, block);
					stored.setAssignedPool(pool);
					populateAndSyncVanillaLoot(stored, liveInv, baseW, baseH, pool, isShulker, isDouble, world.getRandom());
					changed = true;
				}
			} else if (SearchableLootBlockEntity.isRespawnDue(stored.getLastLootEpochMs())) {
				TarkovLootPool pool = stored.getAssignedPool() != null
					? stored.getAssignedPool()
					: CityZoneClassifier.classify(world, pos, block);
				stored.setAssignedPool(pool);
				populateAndSyncVanillaLoot(stored, liveInv, baseW, baseH, pool, isShulker, isDouble, world.getRandom());
				changed = true;
			}
		}

		if (reconcile(stored, liveInv, baseW, baseH)) {
			changed = true;
		}
		if (stored.getGrid().isEmpty()) {
			stored.getSearchMemory().markGridRevealed(player.getUuid());
			changed = true;
		}
		if (changed) {
			vcState.markDirty();
		}

		if (!(block instanceof EnderChestBlock) && !stored.isPlayerPlaced() && stored.getAssignedPool() != null) {
			title = title.copy()
				.append(Text.literal(" ("))
				.append(Text.translatable(stored.getAssignedPool().getTranslationKey()))
				.append(Text.literal(")"));
		}

		TarkovScreenHandler.openVanillaContainer(
			player, pos, title, liveInv, stored, vcState, isShulker, enderChestBe
		);
		return true;
	}

	private static void populateAndSyncVanillaLoot(
		VanillaContainerState.StoredVanillaContainer stored,
		Inventory liveInv,
		int baseW,
		int baseH,
		TarkovLootPool pool,
		boolean isShulker,
		boolean isDouble,
		net.minecraft.util.math.random.Random random
	) {
		GridContainer freshGrid = new GridContainer(baseW, baseH);
		float chance = isShulker ? 1.0f : TarkovConfig.get().vanillaContainerLootChance;
		if (random.nextFloat() <= chance) {
			TarkovLootGenerator.populateVanillaContainerGrid(freshGrid, pool, isShulker, isDouble, random);
		}
		stored.setGrid(freshGrid);
		stored.setSearchMemory(new ContainerSearchMemory());
		stored.setLastLootEpochMs(System.currentTimeMillis());
		syncGridToInventory(freshGrid, liveInv);
	}

	/**
	 * Marks a vanilla container position as player-placed so city auto-loot & hourly respawn never overwrite it.
	 */
	public static void markPlayerPlaced(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();
		if (!isSupportedBlock(block) || block instanceof EnderChestBlock) {
			return;
		}
		String key = resolveCanonicalKey(pos, state, UUID.randomUUID());
		VanillaContainerState vcState = VanillaContainerState.get(world);
		VanillaContainerState.StoredVanillaContainer stored = vcState.getOrCreateEntry(key, SINGLE_WIDTH, SINGLE_HEIGHT);
		stored.setPlayerPlaced(true);
		vcState.markDirty();
	}

	/**
	 * Assigns a specific city loot pool to a vanilla container and forces an immediate refill.
	 */
	public static boolean assignZoneAndRefill(ServerWorld world, BlockPos pos, TarkovLootPool pool) {
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();
		if (!isSupportedBlock(block) || block instanceof EnderChestBlock) {
			return false;
		}
		Inventory liveInv;
		if (block instanceof ChestBlock chestBlock) {
			liveInv = ChestBlock.getInventory(chestBlock, state, world, pos, true);
		} else {
			BlockEntity be = world.getBlockEntity(pos);
			liveInv = be instanceof Inventory inv ? inv : null;
		}
		if (liveInv == null) {
			return false;
		}
		boolean isDouble = liveInv.size() > 27;
		boolean isShulker = block instanceof ShulkerBoxBlock;
		int baseW = isDouble ? DOUBLE_WIDTH : SINGLE_WIDTH;
		int baseH = isDouble ? DOUBLE_HEIGHT : SINGLE_HEIGHT;
		String key = resolveCanonicalKey(pos, state, UUID.randomUUID());
		VanillaContainerState vcState = VanillaContainerState.get(world);
		VanillaContainerState.StoredVanillaContainer stored = vcState.getOrCreateEntry(key, baseW, baseH);
		stored.setPlayerPlaced(false);
		stored.setAssignedPool(pool);
		populateAndSyncVanillaLoot(stored, liveInv, baseW, baseH, pool, isShulker, isDouble, world.getRandom());
		vcState.markDirty();
		return true;
	}

	/**
	 * Reconciles persisted Tetris GridContainer against the live vanilla Inventory.
	 * - Fast-path: 100% multiset match -> preserves exact coordinates, rotations, UUIDs, and search memory.
	 * - Delta-path: keeps matching entries untouched, removes missing entries, and places newly
	 *   arrived items (e.g. from hoppers) with auto-grow height so zero items are ever lost.
	 *
	 * @return true if stored grid was modified
	 */
	public static boolean reconcile(
		VanillaContainerState.StoredVanillaContainer stored,
		Inventory liveInv,
		int baseW,
		int baseH
	) {
		List<ItemStack> liveStacks = new ArrayList<>();
		for (int i = 0; i < liveInv.size(); i++) {
			ItemStack st = liveInv.getStack(i);
			if (!st.isEmpty()) {
				liveStacks.add(st.copy());
			}
		}

		GridContainer grid = stored.getGrid();
		List<GridEntry> currentEntries = grid.getEntries();

		boolean[] liveMatched = new boolean[liveStacks.size()];
		List<GridEntry> keptEntries = new ArrayList<>();
		List<GridEntry> removedEntries = new ArrayList<>();

		for (GridEntry entry : currentEntries) {
			int matchIdx = findMatchingUnused(entry.getStack(), liveStacks, liveMatched);
			if (matchIdx >= 0) {
				liveMatched[matchIdx] = true;
				keptEntries.add(entry);
			} else {
				removedEntries.add(entry);
			}
		}

		List<ItemStack> addedStacks = new ArrayList<>();
		for (int i = 0; i < liveStacks.size(); i++) {
			if (!liveMatched[i]) {
				addedStacks.add(liveStacks.get(i));
			}
		}

		// Fast-path: 100% match -> zero changes
		if (removedEntries.isEmpty() && addedStacks.isEmpty() && grid.getWidth() >= baseW) {
			return false;
		}

		for (GridEntry removed : removedEntries) {
			grid.remove(removed.getEntryId());
		}

		// Ensure width >= baseW and try placing additions in the existing grid first
		boolean needsRebuild = grid.getWidth() < baseW || grid.getHeight() < baseH;
		if (!needsRebuild) {
			List<GridEntry> newlyPlaced = new ArrayList<>();
			for (ItemStack added : addedStacks) {
				GridEntry placed = grid.tryPlaceInFreeCell(added);
				if (placed == null) {
					needsRebuild = true;
					break;
				}
				newlyPlaced.add(placed);
			}
			if (!needsRebuild) {
				return true;
			}
			// Roll back partial placements before rebuilding with taller height
			for (GridEntry np : newlyPlaced) {
				grid.remove(np.getEntryId());
			}
		}

		GridContainer grown = rebuildWithAutoGrow(
			Math.max(baseW, grid.getWidth()),
			Math.max(baseH, grid.getHeight()),
			grid.getContainerId(),
			keptEntries,
			addedStacks
		);
		stored.setGrid(grown);
		return true;
	}

	private static GridContainer rebuildWithAutoGrow(
		int width,
		int startHeight,
		UUID containerId,
		List<GridEntry> keptEntries,
		List<ItemStack> addedStacks
	) {
		for (int h = startHeight; h <= MAX_AUTO_HEIGHT; h += 3) {
			GridContainer candidate = new GridContainer(width, h, containerId);
			boolean allPlaced = true;

			for (GridEntry kept : keptEntries) {
				if (!placePreservingId(candidate, kept)) {
					allPlaced = false;
					break;
				}
			}
			if (allPlaced) {
				for (ItemStack added : addedStacks) {
					if (candidate.tryPlaceInFreeCell(added) == null) {
						allPlaced = false;
						break;
					}
				}
			}
			if (allPlaced) {
				return candidate;
			}
		}

		// Fallback (should never happen with MAX_AUTO_HEIGHT = 60, guarantees zero silent drops)
		GridContainer fallback = new GridContainer(width, MAX_AUTO_HEIGHT, containerId);
		for (GridEntry kept : keptEntries) {
			placePreservingId(fallback, kept);
		}
		for (ItemStack added : addedStacks) {
			fallback.tryPlaceInFreeCell(added);
		}
		return fallback;
	}

	private static boolean placePreservingId(GridContainer target, GridEntry old) {
		if (target.placeWithId(old.getEntryId(), old.getStack(), old.getX(), old.getY(), old.isRotated()) != null) {
			return true;
		}
		ItemSize baseSize = ItemSizeRegistry.getSize(old.getStack());
		boolean[] orientations = baseSize.width() == baseSize.height()
			? new boolean[]{false}
			: new boolean[]{false, true};
		for (boolean rot : orientations) {
			for (int y = 0; y < target.getHeight(); y++) {
				for (int x = 0; x < target.getWidth(); x++) {
					if (target.placeWithId(old.getEntryId(), old.getStack(), x, y, rot) != null) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static int findMatchingUnused(ItemStack target, List<ItemStack> liveStacks, boolean[] used) {
		for (int i = 0; i < liveStacks.size(); i++) {
			if (!used[i] && stacksEqual(target, liveStacks.get(i))) {
				return i;
			}
		}
		return -1;
	}

	private static boolean stacksEqual(ItemStack a, ItemStack b) {
		return a.getCount() == b.getCount() && ItemStack.canCombine(a, b);
	}

	/**
	 * Immediately mirrors every GridEntry in {@code grid} back into {@code inv} (write-through).
	 */
	public static void syncGridToInventory(GridContainer grid, Inventory inv) {
		int slot = 0;
		for (GridEntry entry : grid.getEntries()) {
			if (slot >= inv.size()) {
				break;
			}
			inv.setStack(slot++, entry.getStack().copy());
		}
		while (slot < inv.size()) {
			inv.setStack(slot++, ItemStack.EMPTY);
		}
		inv.markDirty();
	}
}
