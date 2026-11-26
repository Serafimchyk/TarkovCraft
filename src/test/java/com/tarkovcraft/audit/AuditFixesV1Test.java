package com.tarkovcraft.audit;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.health.InjuryType;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditFixesV1Test {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModBlocks.init();
		ModItems.init();
	}

	@Test
	void corpseTransferPreservesAllEquipmentSlotsAndExtrasWithoutLoss() {
		PlayerLoadout source = new PlayerLoadout();
		source.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.SSH68_HELMET));
		source.setEquipment(EquipmentSlotType.FACE_MASK, new ItemStack(ModItems.BALACLAVA_MASK));
		Item headset = Registries.ITEM.get(new Identifier("tarkovcraft", "aktivnye_naushniki_msa_sordin_suprem_5aa2ba"));
		assertNotEquals(Items.AIR, headset);
		source.setEquipment(EquipmentSlotType.HEADSET, new ItemStack(headset));
		source.setEquipment(EquipmentSlotType.BODY_ARMOR, new ItemStack(ModItems.PACA_ARMOR));
		source.setEquipment(EquipmentSlotType.RIG, new ItemStack(ModItems.SCAV_VEST));
		source.setEquipment(EquipmentSlotType.BACKPACK, new ItemStack(ModItems.SLING_BACKPACK));
		source.setEquipment(EquipmentSlotType.PRIMARY_1, new ItemStack(Items.BOW));
		source.setEquipment(EquipmentSlotType.PRIMARY_2, new ItemStack(Items.CROSSBOW));
		source.setEquipment(EquipmentSlotType.PISTOL, new ItemStack(Items.SPYGLASS));
		source.setEquipment(EquipmentSlotType.MELEE, new ItemStack(Items.IRON_SWORD));

		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			source.setPocket(i, new ItemStack(Items.APPLE, 1));
		}

		// Extras: 2x2 items that won't fit in 1x1 pockets and must go to backpack/rig or overflow
		List<ItemStack> extras = List.of(
			new ItemStack(Items.SHIELD),
			new ItemStack(Items.DIAMOND_AXE),
			new ItemStack(Items.TORCH, 5),
			new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.OBSIDIAN, 64),
			new ItemStack(Items.IRON_INGOT, 64),
			new ItemStack(Items.GOLD_INGOT, 64),
			new ItemStack(Items.DIAMOND, 64),
			new ItemStack(Items.EMERALD, 64),
			new ItemStack(Items.COAL, 64)
		);

		int beforeExtrasCount = extras.stream().mapToInt(ItemStack::getCount).sum();

		GridContainer corpsePockets = new GridContainer(6, 1);
		List<ItemStack> overflow = new ArrayList<>();
		PlayerLoadout corpseLoadout = CorpseEntity.transferPlayerInventoryToCorpse(source, corpsePockets, extras, overflow);

		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			assertFalse(corpseLoadout.getEquipment(slot).isEmpty(), "Missing slot on corpse: " + slot);
		}
		assertEquals(Items.BOW, corpseLoadout.getEquipment(EquipmentSlotType.PRIMARY_1).getItem());
		assertEquals(Items.CROSSBOW, corpseLoadout.getEquipment(EquipmentSlotType.PRIMARY_2).getItem());
		assertEquals(Items.SPYGLASS, corpseLoadout.getEquipment(EquipmentSlotType.PISTOL).getItem());
		assertEquals(Items.IRON_SWORD, corpseLoadout.getEquipment(EquipmentSlotType.MELEE).getItem());
		assertEquals(headset, corpseLoadout.getEquipment(EquipmentSlotType.HEADSET).getItem());

		int afterPocketsCount = corpsePockets.getEntries().stream().mapToInt(e -> e.getStack().getCount()).sum();
		int afterRigCount = ContainerItemHelper.readRigGrids(corpseLoadout.getEquipment(EquipmentSlotType.RIG)).stream()
			.flatMap(g -> g.getEntries().stream()).mapToInt(e -> e.getStack().getCount()).sum();
		int afterBpCount = ContainerItemHelper.readBackpackGrid(corpseLoadout.getEquipment(EquipmentSlotType.BACKPACK))
			.getEntries().stream().mapToInt(e -> e.getStack().getCount()).sum();
		int afterOverflowCount = overflow.stream().mapToInt(ItemStack::getCount).sum();

		assertEquals(PlayerLoadout.POCKET_COUNT + beforeExtrasCount,
			afterPocketsCount + afterRigCount + afterBpCount + afterOverflowCount,
			"Total item count across pockets + rig + backpack + overflow must match original");
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			assertTrue(corpseLoadout.getPocket(i).isEmpty(), "Corpse loadout pockets must remain empty (moved to pocketsGrid)");
		}
	}

	@Test
	void ammoCreativeTabIsRegisteredWithoutCrashing() {
		assertTrue(Registries.ITEM_GROUP.containsId(new Identifier(TarkovCraftMod.MOD_ID, "tarkov_ammo_tab")));
		assertNotNull(ModItems.TARKOV_AMMO_GROUP);
	}

	@Test
	void partialStackInsertPersistsNbtInRigAndBackpack() {
		UUID playerId = UUID.randomUUID();
		PlayerLoadout loadout = new PlayerLoadout();
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			loadout.setPocket(i, new ItemStack(Items.COBBLESTONE, 64));
		}

		// Fill a Scav Vest (4 cells? 2x(1x2) + 2x(1x1) = 6 cells) so only partial merge space remains in the last cell
		ItemStack rig = new ItemStack(ModItems.SCAV_VEST);
		List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rig);
		for (int gi = 0; gi < rigGrids.size(); gi++) {
			GridContainer g = rigGrids.get(gi);
			// Fill every cell except leave 10 space in one existing apple stack
			for (int y = 0; y < g.getHeight(); y++) {
				for (int x = 0; x < g.getWidth(); x++) {
					if (gi == 0 && x == 0 && y == 0) {
						g.place(new ItemStack(Items.APPLE, 54), x, y, false);
					} else {
						g.place(new ItemStack(Items.STICK, 64), x, y, false);
					}
				}
			}
		}
		ContainerItemHelper.writeRigGrids(rig, rigGrids);
		loadout.setEquipment(EquipmentSlotType.RIG, rig);

		ItemStack incomingRig = new ItemStack(Items.APPLE, 20);
		assertFalse(loadout.tryInsertItem(incomingRig, playerId), "Partial insert should return false since 10 remain");
		assertEquals(10, incomingRig.getCount(), "10 items should remain uninserted");
		int rigApples = ContainerItemHelper.readRigGrids(loadout.getEquipment(EquipmentSlotType.RIG)).get(0)
			.getEntries().stream().filter(e -> e.getStack().getItem() == Items.APPLE)
			.mapToInt(e -> e.getStack().getCount()).sum();
		assertEquals(64, rigApples, "Rig NBT must persist the 10 merged apples!");

		// Same test for a 2x3 Sling Backpack
		loadout.setEquipment(EquipmentSlotType.RIG, ItemStack.EMPTY);
		ItemStack bp = new ItemStack(ModItems.SLING_BACKPACK);
		GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
		for (int y = 0; y < bpGrid.getHeight(); y++) {
			for (int x = 0; x < bpGrid.getWidth(); x++) {
				if (x == 0 && y == 0) {
					bpGrid.place(new ItemStack(Items.ARROW, 60), x, y, false);
				} else {
					bpGrid.place(new ItemStack(Items.STICK, 64), x, y, false);
				}
			}
		}
		ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
		loadout.setEquipment(EquipmentSlotType.BACKPACK, bp);

		ItemStack incomingBp = new ItemStack(Items.ARROW, 12);
		assertFalse(loadout.tryInsertItem(incomingBp, playerId));
		assertEquals(8, incomingBp.getCount());
		int bpArrows = ContainerItemHelper.readBackpackGrid(loadout.getEquipment(EquipmentSlotType.BACKPACK))
			.getEntries().stream().filter(e -> e.getStack().getItem() == Items.ARROW)
			.mapToInt(e -> e.getStack().getCount()).sum();
		assertEquals(64, bpArrows, "Backpack NBT must persist the 4 merged arrows!");
	}

	@Test
	void gridAutoPlacePredicatePreventsMergingIntoUndiscoveredEntries() {
		UUID playerId = UUID.randomUUID();
		GridContainer crate = new GridContainer(4, 4);
		GridEntry undiscovered = crate.place(new ItemStack(Items.APPLE, 10), 0, 0, false);
		assertNotNull(undiscovered);

		ContainerSearchMemory mem = new ContainerSearchMemory();
		mem.markGridRevealed(playerId);

		ItemStack incoming = new ItemStack(Items.APPLE, 5);
		assertTrue(crate.tryAutoPlace(incoming, id -> mem.isEntryDiscovered(playerId, id)));
		assertEquals(2, crate.getEntries().size(), "Incoming stack must NOT merge into undiscovered entry");
		assertEquals(10, undiscovered.getStack().getCount());
		assertFalse(mem.isEntryDiscovered(playerId, undiscovered.getEntryId()));
	}

	@Test
	void setExposedStorageStackSymmetricWithBackpackAndInvalidatesCache() {
		UUID playerId = UUID.randomUUID();
		PlayerLoadout loadout = new PlayerLoadout();

		ItemStack rig = new ItemStack(ModItems.SCAV_VEST);
		List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rig);
		rigGrids.get(0).place(new ItemStack(Items.IRON_INGOT, 4), 0, 0, false);
		ContainerItemHelper.writeRigGrids(rig, rigGrids);
		loadout.setEquipment(EquipmentSlotType.RIG, rig);

		ItemStack bp = new ItemStack(ModItems.SLING_BACKPACK);
		GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
		bpGrid.place(new ItemStack(Items.GOLD_INGOT, 8), 0, 0, false);
		ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
		loadout.setEquipment(EquipmentSlotType.BACKPACK, bp);

		// Populate tick-scoped cache on tick 100
		assertEquals(Items.IRON_INGOT, loadout.getExposedStorageStackCached(0, playerId, true, 100L).getItem());
		assertEquals(Items.GOLD_INGOT, loadout.getExposedStorageStackCached(1, playerId, true, 100L).getItem());

		// Mutate Backpack slot (index 1) via setExposedStorageStack on SAME tick 100
		assertTrue(loadout.setExposedStorageStack(1, new ItemStack(Items.DIAMOND, 3), playerId, true));
		ItemStack cachedAfterUpdate = loadout.getExposedStorageStackCached(1, playerId, true, 100L);
		assertEquals(Items.DIAMOND, cachedAfterUpdate.getItem());
		assertEquals(3, cachedAfterUpdate.getCount());

		// Delete Backpack slot (index 1)
		assertTrue(loadout.setExposedStorageStack(1, ItemStack.EMPTY, playerId, true));
		assertTrue(loadout.getExposedStorageStackCached(1, playerId, true, 100L).isEmpty());
	}

	@Test
	void bodyPartHealthUsesIndependentBleedAndFractureRolls() {
		PlayerLoadout empty = new PlayerLoadout();

		BodyPartHealth bleedOnly = new BodyPartHealth();
		bleedOnly.applyHit(empty, HitZone.LEFT_ARM, 30.0f, 0, 1.0f, false, false, 1.0f,
			0.01f, 0.99f, "Test", "Test", "Test", 1.0f);
		assertTrue(bleedOnly.hasHeavyBleed(BodyPart.LEFT_ARM) || bleedOnly.hasLightBleed(BodyPart.LEFT_ARM));
		assertFalse(bleedOnly.hasFracture(BodyPart.LEFT_ARM));

		BodyPartHealth fractureOnly = new BodyPartHealth();
		fractureOnly.applyHit(empty, HitZone.LEFT_ARM, 30.0f, 0, 1.0f, false, false, 1.0f,
			0.99f, 0.01f, "Test", "Test", "Test", 1.0f);
		assertFalse(fractureOnly.hasHeavyBleed(BodyPart.LEFT_ARM) || fractureOnly.hasLightBleed(BodyPart.LEFT_ARM));
		assertTrue(fractureOnly.hasFracture(BodyPart.LEFT_ARM));
	}

	@Test
	void semanticCatalogFlagsReplaceTranslitHeuristics() {
		int gasMaskCount = 0;
		for (String id : List.of(
			"protivogaz_avon_m53a1_689b88",
			"protivogaz_msa_millenium_riot_contro_6a8316",
			"protivogaz_gp_5_5b432c",
			"protivogaz_gp_7_60363c",
			"respirator_gentex_ops_core_sotr_689b40",
			"respiratornaya_maska_59e771"
		)) {
			Item item = Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, id));
			assertInstanceOf(TarkovArmorItem.class, item, "Expected TarkovArmorItem for " + id);
			assertTrue(((TarkovArmorItem) item).isGasProtection(), "Expected gasProtection=true for " + id);
			gasMaskCount++;
		}
		assertEquals(6, gasMaskCount);
		assertFalse(ModItems.BALACLAVA_MASK.isGasProtection());

		int medCaseCount = 0;
		for (String id : List.of(
			"keys_dlya_medikamentov_5aafbc",
			"keys_dlya_medikamentov_6a997c",
			"keys_dlya_inektorov_619cbf"
		)) {
			Item item = Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, id));
			assertInstanceOf(TarkovCaseItem.class, item, "Expected TarkovCaseItem for " + id);
			assertTrue(((TarkovCaseItem) item).isMedicalCase(), "Expected MEDICAL caseRole for " + id);
			medCaseCount++;
		}
		assertEquals(3, medCaseCount);
	}
}
