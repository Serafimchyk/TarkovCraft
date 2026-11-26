package com.tarkovcraft.ammo;

import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AmmoSelectionTest {
	private static final String CALIBER = "tacz:545x39";

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
	}

	@Test
	void rigAmmoOnlyCountsAndExtractsDiscoveredEntries() {
		PlayerLoadout loadout = new PlayerLoadout();
		UUID playerId = UUID.randomUUID();
		ItemStack rig = new ItemStack(ModItems.SCAV_VEST);
		loadout.setEquipment(EquipmentSlotType.RIG, rig);
		List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
		GridEntry hidden = grids.get(0).place(AmmoTier.createTaczAmmoStack(CALIBER, AmmoTier.AP, 10), 0, 0, false);
		GridEntry visible = grids.get(1).place(AmmoTier.createTaczAmmoStack(CALIBER, AmmoTier.FMJ, 20), 0, 0, false);
		ContainerItemHelper.writeRigGrids(rig, grids);
		ContainerItemHelper.setRequiresSearch(rig, true);

		assertTrue(AmmoReloadTracker.countAvailableAmmo(loadout, playerId, CALIBER).isEmpty());
		assertTrue(loadout.removeRigExposedStack(0, 1, playerId).isEmpty());

		ContainerSearchMemory search = ContainerItemHelper.getSearchMemory(rig);
		search.markGridRevealed(playerId);
		search.markEntryDiscovered(playerId, visible.getEntryId());
		ContainerItemHelper.saveSearchMemory(rig, search);
		assertEquals(Map.of(AmmoTier.FMJ, 20), AmmoReloadTracker.countAvailableAmmo(loadout, playerId, CALIBER));
		assertFalse(AmmoReloadTracker.prioritizeAmmoTier(loadout, playerId, CALIBER, AmmoTier.AP));
		assertEquals(AmmoTier.FMJ, AmmoTier.getFromAmmoStack(loadout.getRigExposedStack(0, playerId)));
		assertEquals(AmmoTier.FMJ, AmmoTier.getFromAmmoStack(loadout.removeRigExposedStack(0, 1, playerId)));
		assertEquals(10, ContainerItemHelper.readRigGrids(rig).get(0).getEntry(hidden.getEntryId()).getStack().getCount());
	}

	@Test
	void priorityReordersOnlyMatchingVisibleStacksAndInvalidatesExposureCache() {
		PlayerLoadout loadout = new PlayerLoadout();
		UUID playerId = UUID.randomUUID();
		ItemStack rig = new ItemStack(ModItems.SCAV_VEST);
		loadout.setEquipment(EquipmentSlotType.RIG, rig);
		List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
		GridEntry first = grids.get(0).place(AmmoTier.createTaczAmmoStack(CALIBER, AmmoTier.FMJ, 17), 0, 0, false);
		GridEntry other = grids.get(1).place(AmmoTier.createTaczAmmoStack("tacz:9x19", AmmoTier.HP, 9), 0, 0, false);
		GridEntry preferred = grids.get(2).place(AmmoTier.createTaczAmmoStack(CALIBER, AmmoTier.AP, 8), 0, 0, false);
		ContainerItemHelper.writeRigGrids(rig, grids);

		assertEquals(AmmoTier.FMJ, AmmoTier.getFromAmmoStack(loadout.getExposedStorageStackCached(0, playerId, false, 5)));
		assertTrue(AmmoReloadTracker.prioritizeAmmoTier(loadout, playerId, CALIBER, AmmoTier.AP));
		assertEquals(AmmoTier.AP, AmmoTier.getFromAmmoStack(loadout.getExposedStorageStackCached(0, playerId, false, 5)));
		assertEquals(Map.of(AmmoTier.AP, 8, AmmoTier.FMJ, 17), AmmoReloadTracker.countAvailableAmmo(loadout, playerId, CALIBER));

		List<GridContainer> result = ContainerItemHelper.readRigGrids(rig);
		assertEquals(AmmoTier.AP, AmmoTier.getFromAmmoStack(result.get(0).getEntry(first.getEntryId()).getStack()));
		assertEquals("tacz:9x19", AmmoTier.getAmmoId(result.get(1).getEntry(other.getEntryId()).getStack()));
		assertEquals(AmmoTier.FMJ, AmmoTier.getFromAmmoStack(result.get(2).getEntry(preferred.getEntryId()).getStack()));
	}
}
