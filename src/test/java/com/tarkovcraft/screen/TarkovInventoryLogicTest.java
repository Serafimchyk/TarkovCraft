package com.tarkovcraft.screen;

import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.audio.HearingProfile;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovFunctionalItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.registry.TarkovItemCatalog;
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
	void secureContainerSurvivesDeathIndependentlyOfCorpseLoot() {
		PlayerLoadout original = new PlayerLoadout();
		ItemStack bread = new ItemStack(Items.BREAD, 4);
		var entry = original.getSecureContainer().place(bread, 0, 0, false);
		assertNotNull(entry);
		original.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.ALTYN_HELMET));
		PlayerLoadout corpse = original.copyLoot();
		original.clearLoot();
		PlayerLoadout respawned = new PlayerLoadout();
		respawned.copySecureContainerFrom(original);

		assertTrue(corpse.getSecureContainer().isEmpty());
		assertFalse(corpse.getEquipment(EquipmentSlotType.HELMET).isEmpty());
		assertTrue(respawned.isLootEmpty());
		assertEquals(4, respawned.getSecureContainer().getEntry(entry.getEntryId()).getStack().getCount());
		assertEquals(4, respawned.copy().getSecureContainer().getEntry(entry.getEntryId()).getStack().getCount());
		assertTrue(PlayerLoadout.canPlaceInSecureContainer(bread));
		assertFalse(PlayerLoadout.canPlaceInSecureContainer(new ItemStack(ModItems.ALTYN_HELMET)));
		assertFalse(PlayerLoadout.canPlaceInSecureContainer(new ItemStack(ModItems.BERKUT_BACKPACK)));
	}

	@Test
	void repairedArmorKeepsItsReducedMaximumAfterSerialization() {
		ItemStack armor = new ItemStack(ModItems.PRESS_ARMOR);
		armor.setDamage(30);
		assertEquals(60, TarkovArmorItem.getEffectiveMaxDurability(armor));
		assertEquals(24, TarkovArmorItem.repair(armor, 45, 0.22f));
		assertEquals("54/55 (60)", TarkovArmorItem.formatDurability(armor));
		assertEquals(6, armor.getDamage());
		ItemStack loaded = ItemStack.fromNbt(armor.writeNbt(new net.minecraft.nbt.NbtCompound()));
		assertEquals("54/55 (60)", TarkovArmorItem.formatDurability(loaded));
		assertEquals(1, TarkovArmorItem.repair(loaded, 45, 0.22f));
		assertEquals(0, TarkovArmorItem.repair(loaded, 45, 0.22f));
	}

	@Test
	void headsetAndClosedHelmetProduceDistinctProfiles() {
		var headset = TarkovItemCatalog.getTacticalItems().stream()
			.filter(item -> item instanceof TarkovFunctionalItem functional && functional.isHeadset())
			.findFirst().orElseThrow();
		PlayerLoadout loadout = new PlayerLoadout();
		ItemStack stack = new ItemStack(headset);
		assertTrue(loadout.trySmartPickup(stack, UUID.randomUUID()));
		assertSame(headset, loadout.getEquipment(EquipmentSlotType.HEADSET).getItem());
		assertEquals(1.35f, HearingProfile.forLoadout(loadout).range());
		loadout.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.ALTYN_HELMET));
		assertFalse(loadout.canEquip(EquipmentSlotType.HEADSET, new ItemStack(headset)));
		assertFalse(PlayerLoadout.canPlaceInSecureContainer(new ItemStack(headset)));
		assertEquals(0.50f, HearingProfile.forLoadout(loadout).range());
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
