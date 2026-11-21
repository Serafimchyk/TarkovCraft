package com.tarkovcraft.grid;

import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GridContainerTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
	}

	@Test
	void placementCollisionAndRotationWork() {
		GridContainer grid = new GridContainer(4, 3);
		ItemStack sling = new ItemStack(ModItems.SLING_BACKPACK); // 2x3

		// Normal 2x3 fits at (0,0)
		GridEntry placed = grid.place(sling, 0, 0, false);
		assertNotNull(placed);
		assertEquals(placed, grid.getAt(0, 0));
		assertEquals(placed, grid.getAt(1, 2));

		// Overlapping placement fails
		assertFalse(grid.canPlace(new ItemStack(Items.APPLE), 1, 1, false));

		// Rotated 3x2 does NOT fit in remaining 2x3 area (width 2 < 3)
		ItemStack anotherSling = new ItemStack(ModItems.SLING_BACKPACK);
		assertFalse(grid.canPlace(anotherSling, 2, 0, true));

		// Normal 2x3 DOES fit at (2,0)
		assertTrue(grid.canPlace(anotherSling, 2, 0, false));
	}

	@Test
	void autoPlaceUsesRotationFallback() {
		// 3 wide x 2 tall grid cannot fit a 2x3 item upright, MUST rotate it to 3x2
		GridContainer grid = new GridContainer(3, 2);
		ItemStack sling = new ItemStack(ModItems.SLING_BACKPACK); // 2x3

		assertTrue(grid.tryAutoPlace(sling));
		assertTrue(sling.isEmpty());
		assertEquals(1, grid.getEntries().size());
		assertTrue(grid.getEntries().get(0).isRotated());
	}

	@Test
	void stackingMergesIdenticalItems() {
		GridContainer grid = new GridContainer(2, 2);
		assertNotNull(grid.place(new ItemStack(Items.IRON_INGOT, 10), 0, 0, false));

		ItemStack more = new ItemStack(Items.IRON_INGOT, 15);
		assertTrue(grid.tryAutoPlace(more));
		assertTrue(more.isEmpty());
		assertEquals(1, grid.getEntries().size());
		assertEquals(25, grid.getEntries().get(0).getStack().getCount());
	}

	@Test
	void preventsPlacingBackpackInsideItself() {
		ItemStack bp = new ItemStack(ModItems.BERKUT_BACKPACK);
		GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);

		// Putting the same backpack (same ContainerUuid) inside its own grid must be rejected
		assertFalse(bpGrid.canPlace(bp, 0, 0, false));
		assertFalse(bpGrid.tryAutoPlace(bp.copy()));
	}

	@Test
	void nbtRoundTripPreservesEntriesAndIds() {
		GridContainer grid = new GridContainer(5, 5);
		GridEntry e1 = grid.place(new ItemStack(Items.DIAMOND, 3), 1, 2, false);
		assertNotNull(e1);

		NbtCompound nbt = grid.toNbt();
		GridContainer restored = GridContainer.fromNbt(nbt, 5, 5);

		assertEquals(grid.getContainerId(), restored.getContainerId());
		assertEquals(1, restored.getEntries().size());
		GridEntry r1 = restored.getEntry(e1.getEntryId());
		assertNotNull(r1);
		assertEquals(1, r1.getX());
		assertEquals(2, r1.getY());
		assertEquals(3, r1.getStack().getCount());
	}

	@Test
	void meleeSelectionDoesNotOverwriteHotbarSlotsAndSmartPickupEquipsBackpack() {
		var loadout = new com.tarkovcraft.loadout.PlayerLoadout();
		loadout.setEquipment(com.tarkovcraft.loadout.EquipmentSlotType.MELEE, new ItemStack(Items.IRON_SWORD));
		loadout.setPocket(0, new ItemStack(Items.APPLE, 2));
		loadout.setMeleeSelected(true);

		// Active hand is the melee sword, but hotbar display slots remain intact!
		assertEquals(Items.IRON_SWORD, loadout.getActiveHandStack(0).getItem());
		assertTrue(loadout.getHotbarDisplayStack(0).isEmpty());
		assertEquals(Items.APPLE, loadout.getHotbarDisplayStack(3).getItem());

		// Dropped backpack equips straight into empty BACKPACK slot on [F] pickup
		ItemStack droppedBp = new ItemStack(ModItems.BERKUT_BACKPACK);
		assertTrue(loadout.trySmartPickup(droppedBp, null));
		assertTrue(droppedBp.isEmpty());
		assertEquals(ModItems.BERKUT_BACKPACK, loadout.getEquipment(com.tarkovcraft.loadout.EquipmentSlotType.BACKPACK).getItem());
	}

	@Test
	void taczFastReloadExposesRigAndPocketsButNeverBackpack() {
		var loadout = new com.tarkovcraft.loadout.PlayerLoadout();
		ItemStack rig = new ItemStack(ModItems.SCAV_VEST);
		var rigGrids = ContainerItemHelper.readRigGrids(rig);
		rigGrids.get(0).tryAutoPlace(new ItemStack(Items.ARROW, 30));
		ContainerItemHelper.writeRigGrids(rig, rigGrids);
		loadout.setEquipment(com.tarkovcraft.loadout.EquipmentSlotType.RIG, rig);

		ItemStack bp = new ItemStack(ModItems.BERKUT_BACKPACK);
		var bpGrid = ContainerItemHelper.readBackpackGrid(bp);
		bpGrid.tryAutoPlace(new ItemStack(Items.DIAMOND, 10));
		ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
		loadout.setEquipment(com.tarkovcraft.loadout.EquipmentSlotType.BACKPACK, bp);

		// Rig slot 0 is exposed to TaCZ reload and supports consuming exact rounds
		ItemStack exposed = loadout.getRigExposedStack(0, null);
		assertEquals(Items.ARROW, exposed.getItem());
		assertEquals(30, exposed.getCount());

		ItemStack consumed = loadout.removeRigExposedStack(0, 12, null);
		assertEquals(12, consumed.getCount());
		assertEquals(18, loadout.getRigExposedStack(0, null).getCount());

		// Backpack items are NEVER exposed in rig slots!
		assertTrue(loadout.getRigExposedStack(1, null).isEmpty());
	}

	@Test
	void tryPlaceInFreeCellDoesNotMergeIntoExistingStack() {
		GridContainer grid = new GridContainer(3, 2);
		GridEntry first = grid.place(new ItemStack(Items.APPLE, 16), 0, 0, false);
		assertNotNull(first);

		GridEntry second = grid.tryPlaceInFreeCell(new ItemStack(Items.APPLE, 8));
		assertNotNull(second);
		assertNotEquals(first.getEntryId(), second.getEntryId());
		assertEquals(2, grid.getEntries().size());
		assertEquals(16, first.getStack().getCount());
		assertEquals(8, second.getStack().getCount());
	}

	@Test
	void tryPlaceInFreeCellRotatesAndFailsWhenFull() {
		// 3x2 grid: fill left 2x2 so only a 1x2 column at x=2 remains
		GridContainer grid = new GridContainer(3, 2);
		assertNotNull(grid.place(new ItemStack(ModItems.DEATH_SHADOW_MASK), 0, 0, false)); // 2x2

		// 2x1 item (SLING? no, let's use a 1x2 or 2x1 item — e.g. SCAV_VEST is 2x2; let's test with 2x1 tacz pistol or register test size / use 1x2!)
		// Register a deterministic 2x1 item size for testing rotation into the 1x2 column
		ItemSizeRegistry.register(net.minecraft.registry.Registries.ITEM.getId(Items.BLAZE_ROD), new ItemSize(2, 1));
		GridEntry rotatedEntry = grid.tryPlaceInFreeCell(new ItemStack(Items.BLAZE_ROD, 1));
		assertNotNull(rotatedEntry);
		assertTrue(rotatedEntry.isRotated(), "2x1 item must rotate to 1x2 to fit into remaining column");
		assertEquals(2, rotatedEntry.getX());
		assertEquals(0, rotatedEntry.getY());

		// Grid is now completely full -> next tryPlaceInFreeCell returns null
		assertNull(grid.tryPlaceInFreeCell(new ItemStack(Items.APPLE, 1)));
	}
}
