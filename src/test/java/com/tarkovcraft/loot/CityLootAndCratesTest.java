package com.tarkovcraft.loot;

import com.tarkovcraft.block.SearchableLootBlock;
import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.container.VanillaContainerState;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CityLootAndCratesTest {

	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModBlocks.init();
		ModItems.init();
	}

	@Test
	void allLootPoolsParseAndPopulateGrids() {
		Random rng = Random.create(20261122L);
		assertTrue(TarkovLootPool.values().length >= 16, "Expected 5 base + 11 war-torn city loot pools");

		for (TarkovLootPool pool : TarkovLootPool.values()) {
			assertEquals(pool, TarkovLootPool.byId(pool.getId()), "byId must resolve " + pool.getId());
			assertEquals(pool, TarkovLootPool.byId(pool.name()), "byId must resolve enum name " + pool.name());
			assertTrue(pool.getDefaultGridWidth() >= 3 && pool.getDefaultGridHeight() >= 3);

			GridContainer grid = new GridContainer(pool.getDefaultGridWidth(), pool.getDefaultGridHeight());
			TarkovLootGenerator.populateLootGrid(grid, pool, rng);
			assertFalse(grid.getEntries().isEmpty(), "Pool " + pool.getId() + " must populate at least one item");
		}
	}

	@Test
	void taczAttachmentCatalogIsNonEmptyAndUnique() {
		assertFalse(TarkovLootGenerator.COMMON_ATTACHMENTS.isEmpty());
		assertFalse(TarkovLootGenerator.TACTICAL_ATTACHMENTS.isEmpty());
		assertFalse(TarkovLootGenerator.ELITE_ATTACHMENTS.isEmpty());

		Set<String> seen = new HashSet<>();
		for (String id : TarkovLootGenerator.COMMON_ATTACHMENTS) {
			assertTrue(id.startsWith("tacz:"), "Invalid attachment id: " + id);
			assertTrue(seen.add(id), "Duplicate attachment id: " + id);
		}
		for (String id : TarkovLootGenerator.TACTICAL_ATTACHMENTS) {
			assertTrue(id.startsWith("tacz:"), "Invalid attachment id: " + id);
			assertTrue(seen.add(id), "Duplicate attachment id: " + id);
		}
		for (String id : TarkovLootGenerator.ELITE_ATTACHMENTS) {
			assertTrue(id.startsWith("tacz:"), "Invalid attachment id: " + id);
			assertTrue(seen.add(id), "Duplicate attachment id: " + id);
		}
		// Without TaCZ registered in plain JUnit, createTaczAttachmentStack safely returns EMPTY
		assertTrue(TarkovLootGenerator.createTaczAttachmentStack("tacz:sight_okp7").isEmpty());
	}

	@Test
	void lootCrateItemAndBlockEntityRoundTripNbt() {
		ItemStack crate = SearchableLootBlock.createLootCrateStack(
			ModBlocks.TECHNICAL_CRATE, TarkovLootPool.FACTORY, 5, 4
		);
		NbtCompound beTag = BlockItem.getBlockEntityNbt(crate);
		assertNotNull(beTag);
		assertEquals("factory", beTag.getString("LootPool"));
		assertTrue(beTag.getBoolean("PendingLoot"));
		assertEquals(5, beTag.getInt("DefaultW"));
		assertEquals(4, beTag.getInt("DefaultH"));

		SearchableLootBlockEntity be = new SearchableLootBlockEntity(BlockPos.ORIGIN, ModBlocks.TECHNICAL_CRATE.getDefaultState());
		be.readNbt(beTag);
		assertEquals(TarkovLootPool.FACTORY, be.getLootPool());
		assertTrue(be.isPendingLootGeneration());
		assertEquals(5, be.getGrid().getWidth());
		assertEquals(4, be.getGrid().getHeight());

		be.refillLoot(Random.create(99L));
		assertFalse(be.isPendingLootGeneration());
		assertFalse(be.getGrid().getEntries().isEmpty());
		assertTrue(be.getLastLootEpochMs() > 0L);
	}

	@Test
	void hourlyRealTimeRespawnAndVanillaStatePersistence() {
		TarkovConfig cfg = new TarkovConfig();
		cfg.lootRespawnMinutes = 60;
		TarkovConfig.setInstanceForTest(cfg);

		long now = System.currentTimeMillis();
		assertFalse(SearchableLootBlockEntity.isRespawnDue(0L));
		assertFalse(SearchableLootBlockEntity.isRespawnDue(now - 30 * 60_000L));
		assertTrue(SearchableLootBlockEntity.isRespawnDue(now - 61 * 60_000L));

		VanillaContainerState.StoredVanillaContainer stored = new VanillaContainerState.StoredVanillaContainer(
			new GridContainer(6, 5), new ContainerSearchMemory()
		);
		stored.setAssignedPool(TarkovLootPool.PORT);
		stored.setLastLootEpochMs(now - 65 * 60_000L);
		stored.setPlayerPlaced(false);

		NbtCompound nbt = stored.toNbt();
		VanillaContainerState.StoredVanillaContainer restored = VanillaContainerState.StoredVanillaContainer.fromNbt(nbt, 6, 5);
		assertEquals(TarkovLootPool.PORT, restored.getAssignedPool());
		assertFalse(restored.isPlayerPlaced());
		assertTrue(SearchableLootBlockEntity.isRespawnDue(restored.getLastLootEpochMs()));
	}
}
