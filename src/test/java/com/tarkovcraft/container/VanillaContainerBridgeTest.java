package com.tarkovcraft.container;

import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModEntities;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VanillaContainerBridgeTest {

	@BeforeAll
	static void setupMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModBlocks.init();
		ModItems.init();
		ModEntities.init();
	}

	@Test
	void testLosslessConversionSingleDoubleAndAutoGrow() {
		// 1. Single chest (27x 1x1 stacks -> 6x5)
		SimpleInventory singleInv = new SimpleInventory(27);
		for (int i = 0; i < 27; i++) {
			singleInv.setStack(i, new ItemStack(Items.IRON_INGOT, (i % 16) + 1));
		}
		VanillaContainerState.StoredVanillaContainer singleStored = new VanillaContainerState.StoredVanillaContainer(
			new GridContainer(VanillaContainerBridge.SINGLE_WIDTH, VanillaContainerBridge.SINGLE_HEIGHT),
			new ContainerSearchMemory()
		);
		assertTrue(VanillaContainerBridge.reconcile(singleStored, singleInv, 6, 5));
		assertEquals(27, singleStored.getGrid().getEntries().size());

		SimpleInventory singleOut = new SimpleInventory(27);
		VanillaContainerBridge.syncGridToInventory(singleStored.getGrid(), singleOut);
		assertEquals(27, countNonEmpty(singleOut));

		// 2. Double chest (54x 1x1 stacks -> 9x7)
		SimpleInventory doubleInv = new SimpleInventory(54);
		for (int i = 0; i < 54; i++) {
			doubleInv.setStack(i, new ItemStack(Items.GOLD_NUGGET, (i % 32) + 1));
		}
		VanillaContainerState.StoredVanillaContainer doubleStored = new VanillaContainerState.StoredVanillaContainer(
			new GridContainer(VanillaContainerBridge.DOUBLE_WIDTH, VanillaContainerBridge.DOUBLE_HEIGHT),
			new ContainerSearchMemory()
		);
		assertTrue(VanillaContainerBridge.reconcile(doubleStored, doubleInv, 9, 7));
		assertEquals(54, doubleStored.getGrid().getEntries().size());

		SimpleInventory doubleOut = new SimpleInventory(54);
		VanillaContainerBridge.syncGridToInventory(doubleStored.getGrid(), doubleOut);
		assertEquals(54, countNonEmpty(doubleOut));

		// 3. 15 large 3x3 items in a 27-slot chest -> dynamic height growth with zero loss
		Item largeArmor = findLargeArmorItem();
		ItemSize armorSize = ItemSizeRegistry.getSize(new ItemStack(largeArmor));
		assertTrue(armorSize.width() >= 3 && armorSize.height() >= 3, "Expected >=3x3 armor item");

		SimpleInventory armorInv = new SimpleInventory(27);
		for (int i = 0; i < 15; i++) {
			armorInv.setStack(i, new ItemStack(largeArmor));
		}
		VanillaContainerState.StoredVanillaContainer armorStored = new VanillaContainerState.StoredVanillaContainer(
			new GridContainer(6, 5),
			new ContainerSearchMemory()
		);
		assertTrue(VanillaContainerBridge.reconcile(armorStored, armorInv, 6, 5));
		assertEquals(15, armorStored.getGrid().getEntries().size());
		assertTrue(armorStored.getGrid().getHeight() > 5, "Grid height should auto-grow beyond 5");

		SimpleInventory armorOut = new SimpleInventory(27);
		VanillaContainerBridge.syncGridToInventory(armorStored.getGrid(), armorOut);
		assertEquals(15, countNonEmpty(armorOut));
	}

	@Test
	void testDeltaReconciliationPreservesDiscoveredAndMarksNewUndiscovered() {
		UUID playerId = UUID.randomUUID();
		SimpleInventory inv = new SimpleInventory(27);
		inv.setStack(0, new ItemStack(Items.BREAD, 3));

		VanillaContainerState.StoredVanillaContainer stored = new VanillaContainerState.StoredVanillaContainer(
			new GridContainer(6, 5),
			new ContainerSearchMemory()
		);
		assertTrue(VanillaContainerBridge.reconcile(stored, inv, 6, 5));
		List<GridEntry> initialEntries = stored.getGrid().getEntries();
		assertEquals(1, initialEntries.size());
		GridEntry breadEntry = initialEntries.get(0);
		UUID breadId = breadEntry.getEntryId();
		int bx = breadEntry.getX();
		int by = breadEntry.getY();
		boolean bRot = breadEntry.isRotated();

		stored.getSearchMemory().markEntryDiscovered(playerId, breadId);
		assertTrue(stored.getSearchMemory().isEntryDiscovered(playerId, breadId));

		// Unchanged reopen -> fast-path (false), exact coordinates & UUID preserved
		assertFalse(VanillaContainerBridge.reconcile(stored, inv, 6, 5));
		GridEntry unchanged = stored.getGrid().getEntry(breadId);
		assertNotNull(unchanged);
		assertEquals(bx, unchanged.getX());
		assertEquals(by, unchanged.getY());
		assertEquals(bRot, unchanged.isRotated());

		// Hopper adds a new item -> old bread stays discovered, new diamond is undiscovered
		inv.setStack(5, new ItemStack(Items.DIAMOND, 2));
		assertTrue(VanillaContainerBridge.reconcile(stored, inv, 6, 5));
		assertEquals(2, stored.getGrid().getEntries().size());
		assertNotNull(stored.getGrid().getEntry(breadId));
		assertTrue(stored.getSearchMemory().isEntryDiscovered(playerId, breadId));

		GridEntry diamondEntry = stored.getGrid().getEntries().stream()
			.filter(e -> !e.getEntryId().equals(breadId))
			.findFirst()
			.orElseThrow();
		assertEquals(Items.DIAMOND, diamondEntry.getStack().getItem());
		assertFalse(stored.getSearchMemory().isEntryDiscovered(playerId, diamondEntry.getEntryId()));
	}

	@Test
	void testShulkerNestingBanAndStackLimit() {
		assertFalse(Items.SHULKER_BOX.canBeNested());
		assertFalse(Items.BLACK_SHULKER_BOX.canBeNested());
		assertTrue(Items.DIAMOND.canBeNested());

		SimpleInventory inv = new SimpleInventory(27);
		for (int i = 0; i < 27; i++) {
			inv.setStack(i, new ItemStack(Items.APPLE, 1));
		}
		VanillaContainerState.StoredVanillaContainer stored = new VanillaContainerState.StoredVanillaContainer(
			new GridContainer(6, 5),
			new ContainerSearchMemory()
		);
		VanillaContainerBridge.reconcile(stored, inv, 6, 5);
		GridContainer grid = stored.getGrid();
		assertEquals(27, grid.getEntries().size());
		// A vanilla 27-slot container cannot accept a 28th distinct stack entry
		assertFalse(grid.getEntries().size() < inv.size());
	}

	@Test
	void testCanonicalDoubleKeyAndStateNbtRoundTrip() {
		BlockPos posA = new BlockPos(10, 64, -5);
		BlockPos posB = new BlockPos(11, 64, -5);
		assertEquals(
			VanillaContainerBridge.canonicalDoubleKey(posA, posB),
			VanillaContainerBridge.canonicalDoubleKey(posB, posA)
		);

		BlockPos posC = new BlockPos(10, 64, -6);
		assertEquals(
			VanillaContainerBridge.canonicalDoubleKey(posA, posC),
			VanillaContainerBridge.canonicalDoubleKey(posC, posA)
		);

		VanillaContainerState state = new VanillaContainerState();
		String key = VanillaContainerBridge.canonicalDoubleKey(posA, posB);
		VanillaContainerState.StoredVanillaContainer stored = state.getOrCreateEntry(key, 9, 7);
		GridEntry placed = stored.getGrid().tryPlaceInFreeCell(new ItemStack(Items.EMERALD, 4));
		assertNotNull(placed);
		UUID playerId = UUID.randomUUID();
		stored.getSearchMemory().markEntryDiscovered(playerId, placed.getEntryId());

		NbtCompound nbt = state.writeNbt(new NbtCompound());
		VanillaContainerState restored = VanillaContainerState.fromNbt(nbt);
		VanillaContainerState.StoredVanillaContainer restoredEntry = restored.getEntry(key);
		assertNotNull(restoredEntry);
		assertEquals(1, restoredEntry.getGrid().getEntries().size());
		assertEquals(Items.EMERALD, restoredEntry.getGrid().getEntries().get(0).getStack().getItem());
		assertEquals(4, restoredEntry.getGrid().getEntries().get(0).getStack().getCount());
		assertTrue(restoredEntry.getSearchMemory().isGridRevealed(playerId));
		assertTrue(restoredEntry.getSearchMemory().isEntryDiscovered(playerId, placed.getEntryId()));
	}

	private static int countNonEmpty(SimpleInventory inv) {
		int count = 0;
		for (int i = 0; i < inv.size(); i++) {
			if (!inv.getStack(i).isEmpty()) {
				count++;
			}
		}
		return count;
	}

	private static Item findLargeArmorItem() {
		return ModItems.PACA_ARMOR;
	}
}
