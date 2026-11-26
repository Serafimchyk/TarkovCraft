package com.tarkovcraft.screen;

import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TarkovInventoryLogicTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
	}

	@Test
	void isConsumableItemIdentifiesFoodAndDrinksOnly() {
		assertTrue(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.BREAD)));
		assertTrue(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.GOLDEN_APPLE)));
		assertTrue(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.POTION)));
		assertTrue(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.MILK_BUCKET)));
		assertTrue(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.HONEY_BOTTLE)));

		assertFalse(TarkovScreenHandler.isConsumableItem(new ItemStack(ModItems.PACA_ARMOR)));
		assertFalse(TarkovScreenHandler.isConsumableItem(new ItemStack(ModItems.BERKUT_BACKPACK)));
		assertFalse(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.IRON_SWORD)));
		assertFalse(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.COBBLESTONE)));
		assertFalse(TarkovScreenHandler.isConsumableItem(ItemStack.EMPTY));
	}

	@Test
	void canEquipItemMatchesExpectedSlots() {
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(ModItems.PACA_ARMOR)));
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(ModItems.SSH68_HELMET)));
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(ModItems.BALACLAVA_MASK)));
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(ModItems.BERKUT_BACKPACK)));
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(ModItems.SCAV_VEST)));
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(Items.IRON_SWORD)));

		// Vanilla armor remains rejected
		assertFalse(TarkovScreenHandler.canEquipItem(new ItemStack(Items.DIAMOND_HELMET)));
		assertFalse(TarkovScreenHandler.canEquipItem(new ItemStack(Items.NETHERITE_CHESTPLATE)));
		assertFalse(TarkovScreenHandler.canEquipItem(new ItemStack(Items.BREAD)));
		assertFalse(TarkovScreenHandler.canEquipItem(ItemStack.EMPTY));
	}

	@Test
	void quickTransferSkipsUndiscoveredStacksAndRevealsTheNewEntry() {
		GridContainer grid = new GridContainer(2, 1);
		var hiddenEntry = grid.place(new ItemStack(Items.IRON_NUGGET), 0, 0, false);
		assertNotNull(hiddenEntry);
		var memory = new ContainerSearchMemory();
		UUID player = UUID.randomUUID();
		ItemStack incoming = new ItemStack(Items.IRON_NUGGET, 4);

		assertEquals(0, TarkovScreenHandler.autoPlaceInRevealedGrids(List.of(grid), incoming, memory, player));
		assertEquals(4, incoming.getCount());
		assertNull(grid.getAt(1, 0));

		memory.markGridRevealed(player);
		assertEquals(4, TarkovScreenHandler.autoPlaceInRevealedGrids(List.of(grid), incoming, memory, player));
		assertTrue(incoming.isEmpty());
		assertEquals(1, hiddenEntry.getStack().getCount());
		assertFalse(memory.isEntryDiscovered(player, hiddenEntry.getEntryId()));
		var newEntry = grid.getAt(1, 0);
		assertNotNull(newEntry);
		assertEquals(4, newEntry.getStack().getCount());
		assertTrue(memory.isEntryDiscovered(player, newEntry.getEntryId()));

		ItemStack more = new ItemStack(Items.IRON_NUGGET, 2);
		assertEquals(2, TarkovScreenHandler.autoPlaceInRevealedGrids(List.of(grid), more, memory, player));
		assertEquals(1, hiddenEntry.getStack().getCount());
		assertEquals(6, newEntry.getStack().getCount());
	}
}
