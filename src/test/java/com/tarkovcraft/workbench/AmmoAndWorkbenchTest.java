package com.tarkovcraft.workbench;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.health.TaCZBulletHelper;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AmmoAndWorkbenchTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModBlocks.init();
		ModItems.init();
	}

	@Test
	void caliberCatalogAndAmmoTierNbtContract() {
		List<TaCZCaliberCatalog.CaliberSpec> defaults = TaCZCaliberCatalog.getDefaultCalibers();
		assertEquals(23, defaults.size());
		for (TaCZCaliberCatalog.CaliberSpec spec : defaults) {
			assertTrue(TaCZCaliberCatalog.isBallisticAmmoId(spec.ammoId()), "Invalid ammoId: " + spec.ammoId());
			assertTrue(spec.ammoId().startsWith("tacz:"), "Expected tacz namespace: " + spec.ammoId());
			assertTrue(spec.getBatchSize() >= 10, "Batch too small for " + spec.ammoId());
		}

		assertFalse(TaCZCaliberCatalog.isBallisticAmmoId("tacz:40mm"));
		assertFalse(TaCZCaliberCatalog.isBallisticAmmoId("tacz:rpg_rocket"));

		ItemStack ign = AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.IGN, 24);
		assertEquals("tacz:545x39", AmmoTier.getAmmoId(ign));
		assertEquals("ign", ign.getNbt().getString(AmmoTier.NBT_AMMO_TIER));
		assertEquals(AmmoTier.IGN, AmmoTier.getFromAmmoStack(ign));

		ItemStack fmj = AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.FMJ, 24);
		assertFalse(fmj.getNbt().contains(AmmoTier.NBT_AMMO_TIER));
		assertEquals(AmmoTier.FMJ, AmmoTier.getFromAmmoStack(fmj));

		ItemStack alias = AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.FMJ, 10);
		alias.getOrCreateNbt().putString(AmmoTier.NBT_AMMO_TIER_ALIAS, "ap");
		assertEquals(AmmoTier.AP, AmmoTier.getFromAmmoStack(alias));

		int penHp = TaCZBulletHelper.computePenetration("tacz:545x39", AmmoTier.HP);
		int penFmj = TaCZBulletHelper.computePenetration("tacz:545x39", AmmoTier.FMJ);
		int penAp = TaCZBulletHelper.computePenetration("tacz:545x39", AmmoTier.AP);
		int penIgn = TaCZBulletHelper.computePenetration("tacz:545x39", AmmoTier.IGN);
		assertTrue(penHp < penFmj && penFmj < penAp && penAp < penIgn,
			"Penetration must strictly increase: " + penHp + " < " + penFmj + " < " + penAp + " < " + penIgn);
	}

	@Test
	void workbenchRecipesForAmmoAndGearAreValidAndSafe() {
		for (TaCZCaliberCatalog.CaliberSpec spec : TaCZCaliberCatalog.getDefaultCalibers()) {
			for (AmmoTier tier : AmmoTier.values()) {
				TarkovWorkbenchRecipes.AmmoRecipe scratch = TarkovWorkbenchRecipes.getAmmoRecipe(spec.ammoId(), tier, false);
				assertFalse(scratch.materials().isEmpty());
				assertEquals(spec.getBatchSize(), scratch.batchSize());
				assertEquals(0, scratch.requiredFmjCount());

				if (tier != AmmoTier.FMJ) {
					TarkovWorkbenchRecipes.AmmoRecipe upgrade = TarkovWorkbenchRecipes.getAmmoRecipe(spec.ammoId(), tier, true);
					assertTrue(upgrade.upgradeFromFmj());
					assertEquals(spec.getBatchSize(), upgrade.requiredFmjCount());
					assertFalse(upgrade.materials().isEmpty());
					int scratchMetalGunpowder = countItem(scratch.materials(), Items.IRON_INGOT)
						+ countItem(scratch.materials(), Items.GUNPOWDER);
					int upgradeMetalGunpowder = countItem(upgrade.materials(), Items.IRON_INGOT)
						+ countItem(upgrade.materials(), Items.GUNPOWDER);
					assertTrue(upgradeMetalGunpowder < scratchMetalGunpowder,
						"Upgrade must save iron/gunpowder over scratch for " + spec.ammoId() + " " + tier);
				}
			}
		}

		for (TarkovWorkbenchRecipes.GearCategory cat : TarkovWorkbenchRecipes.GearCategory.values()) {
			List<TarkovWorkbenchRecipes.GearRecipe> list = TarkovWorkbenchRecipes.getGearRecipes(cat);
			assertFalse(list.isEmpty(), "Gear category must not be empty: " + cat);
			for (TarkovWorkbenchRecipes.GearRecipe r : list) {
				assertTrue(TarkovWorkbenchRecipes.isCraftableGear(r.resultItem()));
				assertFalse(r.materials().isEmpty());
			}
		}

		assertFalse(TarkovWorkbenchRecipes.isCraftableGear(Items.DIAMOND_BLOCK));
		assertFalse(TarkovWorkbenchRecipes.isCraftableGear(ModItems.SALEWA_MEDKIT));

		List<TarkovWorkbenchRecipes.Ingredient> class0Cost = TarkovWorkbenchRecipes.computeGearIngredients(ModItems.BALACLAVA_MASK);
		List<TarkovWorkbenchRecipes.Ingredient> class6Cost = TarkovWorkbenchRecipes.computeGearIngredients(ModItems.ZABRALO_ARMOR);
		assertEquals(0, countItem(class0Cost, Items.NETHERITE_SCRAP));
		assertTrue(countItem(class6Cost, Items.NETHERITE_SCRAP) >= 1);
	}

	@Test
	void atomicResourceConsumptionAcrossPocketsRigAndBackpack() {
		PlayerLoadout loadout = new PlayerLoadout();
		loadout.setEquipment(EquipmentSlotType.RIG, new ItemStack(ModItems.TRITON_RIG));
		loadout.setEquipment(EquipmentSlotType.BACKPACK, new ItemStack(ModItems.BERKUT_BACKPACK));

		// Put 2 Iron in pocket 0, 2 Iron in Rig, 2 Iron in Backpack -> 6 Iron total
		loadout.setPocket(0, new ItemStack(Items.IRON_INGOT, 2));
		ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
		List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rig);
		assertTrue(rigGrids.get(0).tryAutoPlace(new ItemStack(Items.IRON_INGOT, 2)));
		ContainerItemHelper.writeRigGrids(rig, rigGrids);

		ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
		GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
		assertTrue(bpGrid.tryAutoPlace(new ItemStack(Items.IRON_INGOT, 2)));
		assertTrue(bpGrid.tryAutoPlace(AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.FMJ, 24)));
		assertTrue(bpGrid.tryAutoPlace(AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.AP, 10)));
		ContainerItemHelper.writeBackpackGrid(bp, bpGrid);

		// Insufficient materials must fail atomically without modifying pockets/rig/backpack
		List<TarkovWorkbenchRecipes.Ingredient> tooExpensive = List.of(
			new TarkovWorkbenchRecipes.Ingredient(Items.IRON_INGOT, 5),
			new TarkovWorkbenchRecipes.Ingredient(Items.DIAMOND, 1)
		);
		assertFalse(WorkbenchInventoryHelper.consumeMaterials(loadout, null, tooExpensive, null, 0));
		assertEquals(6, WorkbenchInventoryHelper.collectSyncPayload(loadout, null).itemCounts().get(Items.IRON_INGOT));

		// Exact consumption across pockets + rig + backpack + FMJ ammo (leaving AP stack untouched)
		List<TarkovWorkbenchRecipes.Ingredient> validCost = List.of(
			new TarkovWorkbenchRecipes.Ingredient(Items.IRON_INGOT, 5)
		);
		assertTrue(WorkbenchInventoryHelper.consumeMaterials(loadout, null, validCost, "tacz:545x39", 24));
		WorkbenchInventoryHelper.SyncPayload after = WorkbenchInventoryHelper.collectSyncPayload(loadout, null);
		assertEquals(1, after.itemCounts().getOrDefault(Items.IRON_INGOT, 0));
		assertEquals(0, after.fmjAmmoCounts().getOrDefault("tacz:545x39", 0));

		// Verify AP stack was NOT consumed when deducting FMJ
		GridContainer bpAfter = ContainerItemHelper.readBackpackGrid(bp);
		boolean apPreserved = bpAfter.getEntries().stream()
			.anyMatch(e -> AmmoTier.getFromAmmoStack(e.getStack()) == AmmoTier.AP && e.getStack().getCount() == 10);
		assertTrue(apPreserved, "Non-FMJ tier ammo must not be consumed as FMJ");
	}

	@Test
	void workbenchBlockAndAssetsAreRegistered() {
		Identifier wbId = new Identifier(TarkovCraftMod.MOD_ID, "tarkov_workbench");
		assertEquals(ModBlocks.TARKOV_WORKBENCH, Registries.BLOCK.get(wbId));
		assertNotEquals(Items.AIR, Registries.ITEM.get(wbId));
		assertTrue(ModItems.TARKOV_TACTICAL_GROUP.getDisplayStacks().isEmpty()
			|| ModBlocks.TARKOV_WORKBENCH.asItem() != Items.AIR);

		assertNotNull(ModBlocks.class.getResource("/assets/tarkovcraft/blockstates/tarkov_workbench.json"));
		assertNotNull(ModBlocks.class.getResource("/assets/tarkovcraft/models/block/tarkov_workbench.json"));
		assertNotNull(ModBlocks.class.getResource("/assets/tarkovcraft/models/item/tarkov_workbench.json"));
		assertNotNull(ModBlocks.class.getResource("/data/tarkovcraft/recipes/tarkov_workbench.json"));
		assertNotNull(ModBlocks.class.getResource("/data/tarkovcraft/loot_tables/blocks/tarkov_workbench.json"));
	}

	private static int countItem(List<TarkovWorkbenchRecipes.Ingredient> list, Item item) {
		int sum = 0;
		for (TarkovWorkbenchRecipes.Ingredient ing : list) {
			if (ing.item() == item) sum += ing.count();
		}
		return sum;
	}
}
