package com.tarkovcraft.search;

import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SearchSessionTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
	}

	@Test
	void twoStageSearchRespectsOneSecondRevealAndRarityTimers() {
		UUID playerA = UUID.randomUUID();
		UUID playerB = UUID.randomUUID();

		GridContainer grid = new GridContainer(4, 4);
		GridEntry commonItem = grid.place(new ItemStack(Items.IRON_INGOT, 2), 0, 0, false); // COMMON = 10 ticks
		GridEntry epicItem = grid.place(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1), 1, 0, false); // EPIC = 56 ticks
		assertNotNull(commonItem);
		assertNotNull(epicItem);

		ContainerSearchMemory memory = new ContainerSearchMemory();
		SearchTarget target = new SearchTarget(grid.getContainerId(), List.of(grid), memory, () -> {});
		SearchSession sessionA = new SearchSession(playerA);

		// Tick 1..19: grid still NOT revealed, items CANNOT be taken
		for (int t = 0; t < SearchSession.GRID_REVEAL_TICKS - 1; t++) {
			sessionA.tick(true, target, null);
			assertFalse(memory.isGridRevealed(playerA));
			assertFalse(SearchSession.canTakeEntry(playerA, target, commonItem.getEntryId()));
		}

		// Tick 20 (1.0s): grid reveals -> silhouettes visible, items still undiscovered
		assertTrue(sessionA.tick(true, target, null));
		assertTrue(memory.isGridRevealed(playerA));
		assertFalse(SearchSession.canTakeEntry(playerA, target, commonItem.getEntryId()));
		assertFalse(SearchSession.canTakeEntry(playerA, target, epicItem.getEntryId()));

		// Next 10 ticks: COMMON item discovered, EPIC still locked
		for (int t = 0; t < ItemRarityTier.COMMON.getSearchTicks(); t++) {
			sessionA.tick(true, target, null);
		}
		assertTrue(SearchSession.canTakeEntry(playerA, target, commonItem.getEntryId()));
		assertFalse(SearchSession.canTakeEntry(playerA, target, epicItem.getEntryId()));

		// Next 55 ticks: EPIC still locked
		for (int t = 0; t < ItemRarityTier.EPIC.getSearchTicks() - 1; t++) {
			sessionA.tick(true, target, null);
		}
		assertFalse(SearchSession.canTakeEntry(playerA, target, epicItem.getEntryId()));

		// 56th tick: EPIC discovered
		assertTrue(sessionA.tick(true, target, null));
		assertTrue(SearchSession.canTakeEntry(playerA, target, epicItem.getEntryId()));

		// Per-player memory check: Player B still sees the container as completely unsearched
		assertFalse(memory.isGridRevealed(playerB));
		assertFalse(SearchSession.canTakeEntry(playerB, target, commonItem.getEntryId()));
	}

	@Test
	void takenBackpackOnlySearchesWhileOwnInventoryIsOpen() {
		UUID playerId = UUID.randomUUID();
		GridContainer bpGrid = new GridContainer(3, 3);
		GridEntry entry = bpGrid.place(new ItemStack(Items.BREAD, 1), 0, 0, false);
		assertNotNull(entry);

		ContainerSearchMemory bpMemory = new ContainerSearchMemory();
		SearchTarget ownBpTarget = new SearchTarget(bpGrid.getContainerId(), List.of(bpGrid), bpMemory, () -> {});
		SearchSession session = new SearchSession(playerId);

		// Inventory CLOSED -> 200 ticks MUST produce zero progress
		for (int t = 0; t < 200; t++) {
			session.tick(false, null, ownBpTarget);
		}
		assertFalse(bpMemory.isGridRevealed(playerId));
		assertFalse(bpMemory.isEntryDiscovered(playerId, entry.getEntryId()));
		assertEquals(SearchSession.Stage.IDLE, session.getStage());

		// Inventory OPEN -> reveals after 20 ticks and discovers item after 10 ticks
		for (int t = 0; t < SearchSession.GRID_REVEAL_TICKS; t++) {
			session.tick(true, null, ownBpTarget);
		}
		assertTrue(bpMemory.isGridRevealed(playerId));
		assertFalse(bpMemory.isEntryDiscovered(playerId, entry.getEntryId()));

		for (int t = 0; t < ItemRarityTier.COMMON.getSearchTicks(); t++) {
			session.tick(true, null, ownBpTarget);
		}
		assertTrue(bpMemory.isEntryDiscovered(playerId, entry.getEntryId()));
	}

	@Test
	void externalContainerSearchesBeforeOwnTakenBackpack() {
		UUID playerId = UUID.randomUUID();
		GridContainer extGrid = new GridContainer(2, 2);
		GridEntry extEntry = extGrid.place(new ItemStack(Items.BREAD, 1), 0, 0, false);
		ContainerSearchMemory extMem = new ContainerSearchMemory();
		SearchTarget extTarget = new SearchTarget(extGrid.getContainerId(), List.of(extGrid), extMem, () -> {});

		GridContainer bpGrid = new GridContainer(2, 2);
		bpGrid.place(new ItemStack(Items.APPLE, 1), 0, 0, false);
		ContainerSearchMemory bpMem = new ContainerSearchMemory();
		SearchTarget bpTarget = new SearchTarget(bpGrid.getContainerId(), List.of(bpGrid), bpMem, () -> {});

		SearchSession session = new SearchSession(playerId);

		// 20 ticks -> external container reveals, own backpack still unrevealed
		for (int t = 0; t < SearchSession.GRID_REVEAL_TICKS; t++) {
			session.tick(true, extTarget, bpTarget);
		}
		assertTrue(extMem.isGridRevealed(playerId));
		assertFalse(bpMem.isGridRevealed(playerId));

		// 10 ticks -> external item discovered, own backpack still untouched until external finishes
		for (int t = 0; t < ItemRarityTier.COMMON.getSearchTicks(); t++) {
			session.tick(true, extTarget, bpTarget);
		}
		assertTrue(extMem.isEntryDiscovered(playerId, extEntry.getEntryId()));
		assertFalse(bpMem.isGridRevealed(playerId));

		// Next 20 ticks -> now own backpack reveals!
		for (int t = 0; t < SearchSession.GRID_REVEAL_TICKS; t++) {
			session.tick(true, extTarget, bpTarget);
		}
		assertTrue(bpMem.isGridRevealed(playerId));
	}

	@Test
	void corpseSearchQueueRevealsPocketsThenRigThenBackpackAutomatically() {
		UUID playerId = UUID.randomUUID();
		GridContainer pockets = new GridContainer(6, 1);
		GridEntry pItem = pockets.place(new ItemStack(Items.APPLE, 1), 0, 0, false);
		ContainerSearchMemory pMem = new ContainerSearchMemory();
		SearchTarget tPockets = new SearchTarget(pockets.getContainerId(), List.of(pockets), pMem, () -> {});

		GridContainer rig = new GridContainer(1, 2);
		GridEntry rItem = rig.place(new ItemStack(Items.BREAD, 1), 0, 0, false);
		ContainerSearchMemory rMem = new ContainerSearchMemory();
		SearchTarget tRig = new SearchTarget(rig.getContainerId(), List.of(rig), rMem, () -> {});

		GridContainer bp = new GridContainer(2, 2);
		GridEntry bItem = bp.place(new ItemStack(Items.IRON_INGOT, 1), 0, 0, false);
		ContainerSearchMemory bMem = new ContainerSearchMemory();
		SearchTarget tBp = new SearchTarget(bp.getContainerId(), List.of(bp), bMem, () -> {});

		SearchSession session = new SearchSession(playerId);
		List<SearchTarget> queue = List.of(tPockets, tRig, tBp);

		// 30 ticks -> pockets revealed & item discovered; rig still unrevealed
		for (int t = 0; t < 30; t++) session.tickQueue(true, queue, List.of());
		assertTrue(pMem.isEntryDiscovered(playerId, pItem.getEntryId()));
		assertFalse(rMem.isGridRevealed(playerId));

		// Next 30 ticks -> rig revealed & item discovered; backpack still unrevealed
		for (int t = 0; t < 30; t++) session.tickQueue(true, queue, List.of());
		assertTrue(rMem.isEntryDiscovered(playerId, rItem.getEntryId()));
		assertFalse(bMem.isGridRevealed(playerId));

		// Next 30 ticks -> backpack automatically revealed & item discovered!
		for (int t = 0; t < 30; t++) session.tickQueue(true, queue, List.of());
		assertTrue(bMem.isEntryDiscovered(playerId, bItem.getEntryId()));
	}

	@Test
	void prioritizeTargetSwitchesToRequestedContainerAndResumesQueue() {
		UUID playerId = UUID.randomUUID();
		GridContainer pockets = new GridContainer(6, 1);
		GridEntry pItem = pockets.place(new ItemStack(Items.APPLE, 1), 0, 0, false);
		ContainerSearchMemory pMem = new ContainerSearchMemory();
		SearchTarget tPockets = new SearchTarget(pockets.getContainerId(), List.of(pockets), pMem, () -> {});

		GridContainer backpack = new GridContainer(3, 3);
		GridEntry bItem = backpack.place(new ItemStack(Items.BREAD, 1), 0, 0, false);
		ContainerSearchMemory bMem = new ContainerSearchMemory();
		SearchTarget tBackpack = new SearchTarget(backpack.getContainerId(), List.of(backpack), bMem, () -> {});

		SearchSession session = new SearchSession(playerId);
		List<SearchTarget> queue = List.of(tPockets, tBackpack);

		// Prioritize backpack before pockets!
		session.prioritizeTarget(tBackpack.getTargetId());

		// 20 ticks reveal + 10 ticks common item = 30 ticks -> backpack completes while pockets stays unrevealed!
		for (int t = 0; t < 30; t++) {
			session.tickQueue(true, queue, List.of());
		}
		assertTrue(bMem.isGridRevealed(playerId));
		assertTrue(bMem.isEntryDiscovered(playerId, bItem.getEntryId()));
		assertFalse(pMem.isGridRevealed(playerId));

		// Next 30 ticks -> automatically resumes queue and searches pockets!
		for (int t = 0; t < 30; t++) {
			session.tickQueue(true, queue, List.of());
		}
		assertTrue(pMem.isGridRevealed(playerId));
		assertTrue(pMem.isEntryDiscovered(playerId, pItem.getEntryId()));
	}
}
