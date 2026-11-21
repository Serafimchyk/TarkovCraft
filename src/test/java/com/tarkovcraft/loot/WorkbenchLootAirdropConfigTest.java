package com.tarkovcraft.loot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModEntities;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.screen.TarkovScreenHandler;
import com.tarkovcraft.search.ContainerSearchMemory;
import com.tarkovcraft.search.ItemRarityRegistry;
import com.tarkovcraft.search.ItemRarityTier;
import com.tarkovcraft.search.SearchSession;
import com.tarkovcraft.search.SearchTarget;
import com.tarkovcraft.worldgen.ModWorldGen;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchLootAirdropConfigTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModBlocks.init();
		ModItems.init();
		ModEntities.init();
		// Ensure ModWorldGen static feature fields register without calling BiomeModifications in plain JUnit
		assertNotNull(ModWorldGen.ROADBLOCK);
	}

	@AfterEach
	void resetConfig() {
		TarkovConfig.setInstanceForTest(new TarkovConfig());
	}

	@Test
	void configCreatesParsesRecoversAndClamps(@TempDir Path tempDir) throws Exception {
		Path cfgPath = tempDir.resolve("tarkovcraft.json");
		TarkovConfig created = TarkovConfig.loadFromPath(cfgPath);
		assertTrue(Files.exists(cfgPath), "Config file should be created on first load");
		assertEquals(30, created.airdropIntervalMinutes);
		assertEquals(36000, created.getAirdropIntervalTicks());
		assertEquals(20, created.getContainerRevealTicks());
		assertEquals(12000, created.getCorpseLifetimeTicks());

		// Corrupt JSON recovery
		Files.writeString(cfgPath, "{not_valid_json", StandardCharsets.UTF_8);
		TarkovConfig recovered = TarkovConfig.loadFromPath(cfgPath);
		assertTrue(recovered.airdropEnabled);
		assertEquals(1.0f, recovered.lootDensityMultiplier, 0.001f);

		// Clamping out-of-range values
		TarkovConfig bad = new TarkovConfig();
		bad.airdropIntervalMinutes = -10;
		bad.airdropMinRadius = 100;
		bad.airdropMaxRadius = 40;
		bad.lootDensityMultiplier = 99.0f;
		bad.searchTimeMultiplier = Float.NaN;
		bad.grenadeDamageMultiplier = 0.01f;
		bad.validateAndClamp();

		assertEquals(1, bad.airdropIntervalMinutes);
		assertEquals(100, bad.airdropMaxRadius);
		assertEquals(5.0f, bad.lootDensityMultiplier, 0.001f);
		assertEquals(1.0f, bad.searchTimeMultiplier, 0.001f);
		assertEquals(0.1f, bad.grenadeDamageMultiplier, 0.001f);
	}

	@Test
	void lootGeneratorPopulatesAllPoolsAndRespectsDensityMultiplier() {
		Random random = Random.create(42L);

		for (TarkovLootPool pool : TarkovLootPool.values()) {
			int w = pool == TarkovLootPool.SCAV_POCKET ? 6 : 6;
			int h = pool == TarkovLootPool.SCAV_POCKET ? 1 : 6;
			GridContainer grid = new GridContainer(w, h);
			TarkovLootGenerator.populateLootGrid(grid, pool, random);
			assertFalse(grid.getEntries().isEmpty(), "Pool " + pool + " should place at least 1 item");
		}

		// Density multiplier check
		TarkovConfig lowCfg = new TarkovConfig();
		lowCfg.lootDensityMultiplier = 0.25f;
		TarkovConfig.setInstanceForTest(lowCfg);
		int lowTotal = 0;
		for (int i = 0; i < 12; i++) {
			GridContainer g = new GridContainer(6, 6);
			TarkovLootGenerator.populateLootGrid(g, TarkovLootPool.AIRDROP, Random.create(100L + i));
			lowTotal += g.getEntries().size();
		}

		TarkovConfig highCfg = new TarkovConfig();
		highCfg.lootDensityMultiplier = 3.0f;
		TarkovConfig.setInstanceForTest(highCfg);
		int highTotal = 0;
		for (int i = 0; i < 12; i++) {
			GridContainer g = new GridContainer(6, 6);
			TarkovLootGenerator.populateLootGrid(g, TarkovLootPool.AIRDROP, Random.create(100L + i));
			highTotal += g.getEntries().size();
		}
		assertTrue(highTotal > lowTotal, "Higher lootDensityMultiplier should yield more items (" + highTotal + " vs " + lowTotal + ")");

		// Mob gear helpers
		ItemStack helmet = TarkovLootGenerator.pickRandomLightHelmet(random);
		assertFalse(helmet.isEmpty());
		assertInstanceOf(TarkovArmorItem.class, helmet.getItem());
		assertEquals(EquipmentSlotType.HELMET, ((TarkovArmorItem) helmet.getItem()).getTargetSlot());

		ItemStack armor = TarkovLootGenerator.pickRandomLightArmor(random);
		assertFalse(armor.isEmpty());
		assertInstanceOf(TarkovArmorItem.class, armor.getItem());
		assertEquals(EquipmentSlotType.BODY_ARMOR, ((TarkovArmorItem) armor.getItem()).getTargetSlot());

		ItemStack rig = TarkovLootGenerator.pickRandomScavRig(random);
		assertFalse(rig.isEmpty());
		assertInstanceOf(TacticalRigItem.class, rig.getItem());

		ItemStack bp = TarkovLootGenerator.pickRandomScavBackpack(random);
		assertFalse(bp.isEmpty());
		assertInstanceOf(BackpackItem.class, bp.getItem());
	}

	@Test
	void itemRarityRegistryAndSearchSessionRespectTiersConfigAndOwnInventoryFlag() {
		ItemStack ign = AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.IGN, 20);
		ItemStack ap = AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.AP, 20);
		ItemStack fmj = AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.FMJ, 20);
		ItemStack hp = AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.HP, 20);

		assertEquals(ItemRarityTier.EPIC, ItemRarityRegistry.getTier(ign));
		assertEquals(ItemRarityTier.RARE, ItemRarityRegistry.getTier(ap));
		assertEquals(ItemRarityTier.UNCOMMON, ItemRarityRegistry.getTier(fmj));
		assertEquals(ItemRarityTier.COMMON, ItemRarityRegistry.getTier(hp));

		Item thiccWeapon = Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, "thicc_weapon_case"));
		if (thiccWeapon != Items.AIR) {
			assertEquals(ItemRarityTier.EPIC, ItemRarityRegistry.getTier(new ItemStack(thiccWeapon)));
		}

		TarkovConfig cfg = new TarkovConfig();
		cfg.searchTimeMultiplier = 2.0f;
		cfg.containerRevealSeconds = 0.5f; // 10 ticks
		TarkovConfig.setInstanceForTest(cfg);
		assertEquals(ItemRarityTier.EPIC.getSearchTicks() * 2, ItemRarityRegistry.getSearchTicks(ign));
		assertEquals(10, cfg.getContainerRevealTicks());

		// SearchSession ownInventoryOpen gating
		UUID playerId = UUID.randomUUID();
		GridContainer ownGrid = new GridContainer(3, 3);
		ownGrid.tryAutoPlace(new ItemStack(Items.APPLE));
		SearchTarget ownTarget = new SearchTarget(UUID.randomUUID(), List.of(ownGrid), new ContainerSearchMemory(), () -> {});

		SearchSession session = new SearchSession(playerId);
		assertFalse(session.tickQueue(false, List.of(), List.of(ownTarget)),
			"Own rig/backpack must NOT auto-search while ownInventoryOpen is false");
		assertFalse(ownTarget.isGridRevealed(playerId));

		for (int i = 0; i < 30; i++) {
			session.tickQueue(true, List.of(), List.of(ownTarget));
		}
		assertTrue(ownTarget.isFullySearched(playerId),
			"Own rig/backpack must complete searching once ownInventoryOpen is true");
	}

	@Test
	void airdropWorldgenRegistrationsAndAssetsExist() throws Exception {
		assertTrue(Registries.BLOCK.containsId(new Identifier(TarkovCraftMod.MOD_ID, "airdrop_crate")));
		assertTrue(Registries.ENTITY_TYPE.containsId(new Identifier(TarkovCraftMod.MOD_ID, "airdrop")));
		for (String structure : List.of("roadblock", "med_tent", "military_camp", "forest_stash")) {
			assertTrue(Registries.FEATURE.containsId(new Identifier(TarkovCraftMod.MOD_ID, structure)),
				"Missing Feature registration: " + structure);
			assertJsonResource("/data/tarkovcraft/worldgen/configured_feature/" + structure + ".json");
			assertJsonResource("/data/tarkovcraft/worldgen/placed_feature/" + structure + ".json");
		}
		assertJsonResource("/assets/tarkovcraft/blockstates/airdrop_crate.json");
		assertJsonResource("/assets/tarkovcraft/models/block/airdrop_crate.json");
		assertJsonResource("/assets/tarkovcraft/models/item/airdrop_crate.json");
		assertJsonResource("/data/tarkovcraft/loot_tables/blocks/airdrop_crate.json");

		JsonObject en = assertJsonResource("/assets/tarkovcraft/lang/en_us.json");
		JsonObject ru = assertJsonResource("/assets/tarkovcraft/lang/ru_ru.json");
		for (String key : List.of(
			"block.tarkovcraft.airdrop_crate",
			"entity.tarkovcraft.airdrop",
			"ui.tarkovcraft.wb_slot_preview",
			"ui.tarkovcraft.wb_only_available",
			"ui.tarkovcraft.wb_sort_default",
			"ui.tarkovcraft.wb_sort_armor_desc",
			"ui.tarkovcraft.wb_sort_armor_asc",
			"ui.tarkovcraft.wb_sort_cap_desc",
			"ui.tarkovcraft.wb_sort_cap_asc",
			"message.tarkovcraft.airdrop_incoming_coords",
			"message.tarkovcraft.airdrop_incoming",
			"message.tarkovcraft.config_reloaded",
			"message.tarkovcraft.wb_crafted_dropped",
			"message.tarkovcraft.hotswap_dropped"
		)) {
			assertTrue(en.has(key), "Missing en_us key: " + key);
			assertTrue(ru.has(key), "Missing ru_ru key: " + key);
		}
	}

	@Test
	void smartHotswapPreservesLootAndRespectsContainerNesting() {
		UUID playerId = UUID.randomUUID();
		PlayerLoadout loadout = new PlayerLoadout();

		// 1. Small empty sling backpack replaced by larger Berkut -> sling goes INSIDE Berkut
		ItemStack sling = new ItemStack(ModItems.SLING_BACKPACK);
		loadout.setEquipment(EquipmentSlotType.BACKPACK, sling);

		ItemStack berkut = new ItemStack(ModItems.BERKUT_BACKPACK);
		GridContainer berkutGrid = ContainerItemHelper.readBackpackGrid(berkut);
		berkutGrid.tryAutoPlace(new ItemStack(Items.APPLE, 2));
		ContainerItemHelper.writeBackpackGrid(berkut, berkutGrid);

		ItemStack dropped = TarkovScreenHandler.equipWithHotswap(loadout, playerId, EquipmentSlotType.BACKPACK, berkut);
		assertTrue(dropped.isEmpty(), "Empty small backpack should fit inside larger new backpack");
		assertSame(ModItems.BERKUT_BACKPACK, loadout.getEquipment(EquipmentSlotType.BACKPACK).getItem());
		GridContainer equippedBpGrid = ContainerItemHelper.readBackpackGrid(loadout.getEquipment(EquipmentSlotType.BACKPACK));
		assertEquals(2, equippedBpGrid.getEntries().size(), "New backpack should contain both its original apples and the swapped sling");

		// 2. Replacing a backpack that holds a nested backpack/case (depth >= 2) must NOT nest deeper -> returns oldStack intact
		ItemStack oldWithNested = new ItemStack(ModItems.BERKUT_BACKPACK);
		GridContainer oldGrid = ContainerItemHelper.readBackpackGrid(oldWithNested);
		oldGrid.tryAutoPlace(new ItemStack(ModItems.SLING_BACKPACK));
		oldGrid.tryAutoPlace(new ItemStack(Items.GOLD_INGOT, 3));
		ContainerItemHelper.writeBackpackGrid(oldWithNested, oldGrid);
		loadout.setEquipment(EquipmentSlotType.BACKPACK, oldWithNested);

		ItemStack newBp = new ItemStack(ModItems.BERKUT_BACKPACK);
		ItemStack droppedNested = TarkovScreenHandler.equipWithHotswap(loadout, playerId, EquipmentSlotType.BACKPACK, newBp);
		assertFalse(droppedNested.isEmpty(), "Nested backpack (depth >= 2) must be returned for dropping instead of nesting inside new backpack");
		GridContainer droppedGrid = ContainerItemHelper.readBackpackGrid(droppedNested);
		assertEquals(2, droppedGrid.getEntries().size(), "Dropped backpack must preserve all its contents intact");
	}

	private static JsonObject assertJsonResource(String resourcePath) throws Exception {
		try (InputStream is = WorkbenchLootAirdropConfigTest.class.getResourceAsStream(resourcePath)) {
			assertNotNull(is, "Missing classpath resource: " + resourcePath);
			try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		}
	}
}
