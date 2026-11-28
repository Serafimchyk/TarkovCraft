package com.tarkovcraft.screen;

import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.audio.HearingProfile;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovHeadsetItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.SecureContainerValidator;
import com.tarkovcraft.network.EquipmentAppearance;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.registry.TarkovItemCatalog;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
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
	void loadoutSyncDetectsInPlaceConsumptionAndDurabilityChanges() {
		PlayerLoadout loadout = new PlayerLoadout();
		long empty = loadout.computeSyncHash();
		ItemStack blocks = new ItemStack(Items.COBBLESTONE, 2);
		assertTrue(loadout.setPocket(0, blocks));
		long twoBlocks = loadout.computeSyncHash();

		blocks.decrement(1);
		long oneBlock = loadout.computeSyncHash();
		assertNotEquals(twoBlocks, oneBlock);

		ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
		loadout.setEquipment(EquipmentSlotType.PRIMARY_1, tool);
		long undamaged = loadout.computeSyncHash();
		tool.setDamage(1);
		assertNotEquals(undamaged, loadout.computeSyncHash());

		blocks.decrement(1);
		tool.decrement(1);
		loadout.sanitizeEmptyStacks();
		assertSame(ItemStack.EMPTY, loadout.getHotbarDisplayStack(3));
		assertSame(ItemStack.EMPTY, loadout.getHotbarDisplayStack(0));
		assertEquals(empty, loadout.computeSyncHash());
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
	void generalCaseRejectsAnotherCase() {
		TarkovCaseItem generalCase = TarkovItemCatalog.getTacticalItems().stream()
			.filter(TarkovCaseItem.class::isInstance)
			.map(TarkovCaseItem.class::cast)
			.filter(item -> item.getCaseRole() == TarkovCaseItem.CaseRole.GENERAL)
			.findFirst().orElseThrow();

		assertFalse(generalCase.canAcceptItem(wallet()));
		assertTrue(generalCase.canAcceptItem(new ItemStack(Items.GOLD_INGOT)));
	}

	@Test
	void secureCaseInsertionChecksExistingAndIncomingItems() {
		ItemStack wallet = wallet();
		ItemStack gold = new ItemStack(Items.GOLD_INGOT);
		assertTrue(PlayerLoadout.canPlaceInSecureContainer(wallet));
		assertFalse(wallet.hasNbt());

		GridContainer grid = ContainerItemHelper.readCaseGrid(wallet);
		assertNotNull(grid.place(gold, 0, 0, false));
		ContainerItemHelper.writeCaseGrid(wallet, grid);
		assertTrue(PlayerLoadout.canPlaceInSecureContainer(wallet));
		assertTrue(SecureContainerValidator.allowsCaseInsertion(wallet, gold));
		assertFalse(SecureContainerValidator.allowsCaseInsertion(wallet, new ItemStack(Items.BREAD)));
		assertFalse(SecureContainerValidator.allowsCaseInsertion(wallet, wallet()));

		assertNotNull(grid.place(new ItemStack(Items.COBBLESTONE), 1, 0, false));
		ContainerItemHelper.writeCaseGrid(wallet, grid);
		assertFalse(PlayerLoadout.canPlaceInSecureContainer(wallet));
		assertFalse(SecureContainerValidator.allowsCaseInsertion(wallet, gold));

		// An out-of-bounds entry is invisible in the loaded grid, but still lives in the saved case.
		NbtCompound storedGrid = wallet.getSubNbt("TarkovContainer").getCompound("Grid");
		storedGrid.getList("Entries", NbtElement.COMPOUND_TYPE).getCompound(1).putInt("X", 100);
		assertEquals(1, ContainerItemHelper.readCaseGrid(wallet).getEntries().size());
		assertFalse(PlayerLoadout.canPlaceInSecureContainer(wallet));
	}

	private static ItemStack wallet() {
		return new ItemStack(Registries.ITEM.get(new Identifier("tarkovcraft", "bumazhnik_wz_60b0f6")));
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
			.filter(TarkovHeadsetItem.class::isInstance)
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
	void ssh68BlocksHeadsetInBothOrdersAndLegacyStackCanBeRecovered() {
		var headset = TarkovItemCatalog.getTacticalItems().stream()
			.filter(TarkovHeadsetItem.class::isInstance).findFirst().orElseThrow();
		ItemStack earProtection = new ItemStack(headset);
		ItemStack helmet = new ItemStack(ModItems.SSH68_HELMET);
		PlayerLoadout loadout = new PlayerLoadout();

		loadout.setEquipment(EquipmentSlotType.HELMET, helmet.copy());
		assertFalse(loadout.canEquip(EquipmentSlotType.HEADSET, earProtection));
		assertFalse(loadout.trySmartPickup(earProtection, null));
		assertSame(headset, earProtection.getItem());

		loadout.setEquipment(EquipmentSlotType.HELMET, ItemStack.EMPTY);
		loadout.setEquipment(EquipmentSlotType.HEADSET, earProtection.copy());
		assertFalse(loadout.canEquip(EquipmentSlotType.HELMET, helmet));
		assertFalse(loadout.trySmartPickup(helmet, null));
		assertSame(ModItems.SSH68_HELMET, helmet.getItem());
		ItemStack airframe = new ItemStack(Registries.ITEM.get(
			new Identifier("tarkovcraft", "shlem_crye_precision_airframe_pesoch_5c17a7")));
		assertFalse(airframe.isEmpty());
		assertTrue(loadout.canEquip(EquipmentSlotType.HELMET, airframe));

		loadout.setEquipment(EquipmentSlotType.HELMET, helmet.copy());
		PlayerLoadout restored = new PlayerLoadout();
		restored.readNbt(loadout.writeNbt());
		assertSame(headset, restored.getEquipment(EquipmentSlotType.HEADSET).getItem());
		assertSame(Items.AIR, EquipmentAppearance.capture(restored).headset());
		assertTrue(restored.canEquip(EquipmentSlotType.HEADSET, ItemStack.EMPTY));
		restored.setEquipment(EquipmentSlotType.HELMET, airframe);
		assertSame(headset, EquipmentAppearance.capture(restored).headset());
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
