package com.tarkovcraft.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.entity.TarkovGrenadeEntity;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.health.InjuryType;
import com.tarkovcraft.health.MedicalHelper;
import com.tarkovcraft.item.ArmorMaterialType;
import com.tarkovcraft.item.MedicalType;
import com.tarkovcraft.item.TarkovArmorRepairItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovFoodItem;
import com.tarkovcraft.item.TarkovFunctionalItem;
import com.tarkovcraft.item.TarkovHeadsetItem;
import com.tarkovcraft.item.TarkovGrenadeItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovMeleeItem;
import com.tarkovcraft.item.TarkovSizedItem;
import com.tarkovcraft.item.TarkovStimulatorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.screen.TarkovScreenHandler;
import com.tarkovcraft.search.ItemRarityRegistry;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TarkovItemCatalogTest {
	private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]+$");

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
		ModEntities.init();
	}

	@Test
	void catalogJsonIsValidAndAllAssetsAndEnumsResolve() throws Exception {
		JsonObject root = loadCatalogRoot();
		assertTrue(root.has("items") && root.get("items").isJsonArray(), "Catalog must contain items array");
		JsonArray items = root.getAsJsonArray("items");
		assertEquals(654, items.size(), "Catalog must contain 654 dynamic items");

		Set<String> seenIds = new HashSet<>();
		Set<String> validMaterials = Set.of("COSMETIC", "ARAMID", "UHMWPE", "COMBINED", "TITANIUM", "STEEL");
		Set<String> validRarities = Set.of("COMMON", "UNCOMMON", "RARE", "EPIC");
		Set<String> validZones = new HashSet<>();
		for (HitZone z : HitZone.values()) {
			validZones.add(z.name());
		}

		for (JsonElement elem : items) {
			assertTrue(elem.isJsonObject(), "Catalog entry must be a JSON object");
			JsonObject obj = elem.getAsJsonObject();
			String id = obj.get("id").getAsString();
			assertTrue(VALID_ID.matcher(id).matches(), "Invalid item id format: " + id);
			assertTrue(seenIds.add(id), "Duplicate item id in catalog: " + id);

			String category = obj.get("category").getAsString();
			assertFalse(category.isBlank(), "Category must not be blank for " + id);
			assertNotEquals("armor_plate", category);
			if ("repair_kit".equals(category)) {
				assertNotNull(TarkovArmorRepairItem.Tier.valueOf(obj.get("repairTier").getAsString()));
			}

			String rarity = obj.get("rarity").getAsString();
			assertTrue(validRarities.contains(rarity), "Invalid rarity '" + rarity + "' for " + id);

			String material = obj.has("material") ? obj.get("material").getAsString() : "";
			if (!material.isEmpty()) {
				assertTrue(validMaterials.contains(material), "Invalid material '" + material + "' for " + id);
				assertNotNull(ArmorMaterialType.valueOf(material));
			}

			if (obj.has("zones") && obj.get("zones").isJsonArray()) {
				for (JsonElement ze : obj.getAsJsonArray("zones")) {
					String zone = ze.getAsString();
					assertTrue(validZones.contains(zone), "Invalid zone '" + zone + "' for " + id);
				}
			}

			if ("medical".equals(category)) {
				String medPreset = obj.get("medPreset").getAsString();
				assertNotNull(MedicalType.fromPreset(medPreset), "Invalid medPreset '" + medPreset + "' for " + id);
			}

			if ("stimulator".equals(category)) {
				String stimPreset = obj.get("stimPreset").getAsString();
				assertTrue(
					Arrays.stream(TarkovStimulatorItem.StimPreset.values()).anyMatch(p -> p.name().equalsIgnoreCase(stimPreset)),
					"Invalid stimPreset '" + stimPreset + "' for " + id
				);
			}

			if ("face_mask".equals(category)) {
				int w = obj.get("width").getAsInt();
				int h = obj.get("height").getAsInt();
				int ac = obj.get("armorClass").getAsInt();
				if (id.startsWith("balaklava_")) {
					assertEquals(1, w, "Balaclava width must be 1: " + id);
					assertEquals(1, h, "Balaclava height must be 1: " + id);
				} else if (ac >= 2) {
					assertEquals(2, w, "Ballistic mask width must be 2: " + id);
					assertEquals(2, h, "Ballistic mask height must be 2: " + id);
				} else if (id.contains("ochki") || id.contains("vizor")) {
					assertEquals(2, w, "Glasses/visor width must be 2: " + id);
					assertEquals(1, h, "Glasses/visor height must be 1: " + id);
				}
			}

			String modelPath = "/assets/tarkovcraft/models/item/" + id + ".json";
			String texPath = "/assets/tarkovcraft/textures/item/" + id + ".png";
			assertNotNull(TarkovItemCatalog.class.getResource(modelPath), "Missing model JSON: " + modelPath);
			assertNotNull(TarkovItemCatalog.class.getResource(texPath), "Missing item PNG: " + texPath);

			Item registered = Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, id));
			assertNotEquals(Items.AIR, registered, "Catalog item not registered in Registries.ITEM: " + id);
			assertInstanceOf(TarkovSizedItem.class, registered, "Catalog item must implement TarkovSizedItem: " + id);
		}
	}

	@Test
	void sixCreativeTabBucketsAreNonEmptyDisjointAndCoverAllDynamicItems() {
		List<List<Item>> buckets = List.of(
			TarkovItemCatalog.getArmorItems(),
			TarkovItemCatalog.getRigItems(),
			TarkovItemCatalog.getBackpackItems(),
			TarkovItemCatalog.getMedicalItems(),
			TarkovItemCatalog.getFoodItems(),
			TarkovItemCatalog.getTacticalItems()
		);

		Set<Item> union = new HashSet<>();
		int sum = 0;
		for (List<Item> bucket : buckets) {
			assertFalse(bucket.isEmpty(), "Creative bucket must not be empty");
			sum += bucket.size();
			for (Item item : bucket) {
				assertTrue(union.add(item), "Duplicate item across creative buckets: " + Registries.ITEM.getId(item));
			}
		}
		assertEquals(654, sum);
		assertEquals(654, union.size());
	}

	@Test
	void newCraftingComponentsStackAndTrophiesFitInSecureContainer() {
		for (String id : List.of("gunpowder_hawk", "brass_casings", "kevlar_fabric",
			"ceramic_plate_raw", "military_electronics", "weapon_parts")) {
			var component = assertInstanceOf(TarkovFunctionalItem.class,
				Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, id)));
			assertEquals("barter", component.getCategory());
			assertEquals(64, component.getMaxCount());
		}

		var prices = Map.of("loot_gold_chain", 15_000L, "loot_rolex", 35_000L, "loot_intel_folder", 60_000L,
			"loot_gpu", 95_000L, "loot_ledx", 140_000L, "loot_bitcoin", 180_000L);
		prices.forEach((id, price) -> {
			var trophy = assertInstanceOf(TarkovFunctionalItem.class,
				Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, id)));
			assertTrue(trophy.isValuable());
			assertEquals(price.longValue(), trophy.getBasePrice());
			assertEquals(1, trophy.getMaxCount());
			assertTrue(PlayerLoadout.canPlaceInSecureContainer(new ItemStack(trophy)));
			assertNotNull(new GridContainer(2, 2).tryPlaceInFreeCell(new ItemStack(trophy)));
		});
	}

	@Test
	void all22StimulatorsHaveDistinctPresetsAndDetailedTooltips() throws Exception {
		JsonArray items = loadCatalogRoot().getAsJsonArray("items");
		Set<TarkovStimulatorItem.StimPreset> usedPresets = EnumSet.noneOf(TarkovStimulatorItem.StimPreset.class);

		for (JsonElement elem : items) {
			JsonObject obj = elem.getAsJsonObject();
			if (!"stimulator".equals(obj.get("category").getAsString())) {
				continue;
			}
			String id = obj.get("id").getAsString();
			Item item = Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, id));
			assertInstanceOf(TarkovStimulatorItem.class, item);
			TarkovStimulatorItem stim = (TarkovStimulatorItem) item;
			assertTrue(usedPresets.add(stim.getPreset()), "Duplicate stimPreset for " + id + ": " + stim.getPreset());

			TarkovStimulatorItem.StimPreset preset = stim.getPreset();
			assertTrue(
				preset.getPainkillerTicks() > 0
					|| preset.getHealAmount() > 0.0f
					|| preset.isStopsAllBleeds()
					|| preset.isClearsNegativeEffects()
					|| !preset.getEffects().isEmpty(),
				"StimPreset has no effects: " + preset
			);

			List<Text> tooltip = new ArrayList<>();
			stim.appendTooltip(new ItemStack(stim), null, tooltip, TooltipContext.BASIC);
			assertTrue(tooltip.size() >= 4, "Stimulator tooltip too short for " + id + ": " + tooltip.size());
		}

		assertEquals(22, usedPresets.size(), "All 22 stimulators must map to 22 distinct presets");
	}

	@Test
	void all33CasesSupportStorageNbtRoundTripWeightAndAntiRecursion() throws Exception {
		JsonArray items = loadCatalogRoot().getAsJsonArray("items");
		int caseCount = 0;
		TarkovCaseItem sampleCase = null;

		for (JsonElement elem : items) {
			JsonObject obj = elem.getAsJsonObject();
			if (!"case".equals(obj.get("category").getAsString())) {
				continue;
			}
			caseCount++;
			String id = obj.get("id").getAsString();
			Item item = Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, id));
			assertInstanceOf(TarkovCaseItem.class, item, "Case must be TarkovCaseItem: " + id);
			TarkovCaseItem caze = (TarkovCaseItem) item;
			assertTrue(caze.getGridWidth() >= 2 && caze.getGridHeight() >= 2, "Invalid case grid for " + id);
			if (sampleCase == null && caze.getGridWidth() >= 4 && caze.getGridHeight() >= 4) {
				sampleCase = caze;
			}
		}

		assertEquals(33, caseCount);
		assertNotNull(sampleCase);

		ItemStack caseStack = new ItemStack(sampleCase);
		assertEquals(1, caseStack.getMaxCount());
		assertTrue(ContainerItemHelper.isContainerItem(caseStack));

		GridContainer grid = ContainerItemHelper.readCaseGrid(caseStack);
		UUID caseUuid = ContainerItemHelper.getContainerUuid(caseStack);
		assertNotNull(caseUuid);

		// Anti-recursion: case cannot be placed inside itself
		assertFalse(grid.canPlace(caseStack, 0, 0, false));

		// Put a medical item inside case and verify NBT round-trip + weight inclusion
		ItemStack bandage = new ItemStack(ModItems.BANDAGE);
		assertTrue(grid.tryAutoPlace(bandage));
		ContainerItemHelper.writeCaseGrid(caseStack, grid);

		GridContainer reloaded = ContainerItemHelper.readCaseGrid(caseStack);
		assertEquals(1, reloaded.getEntries().size());
		assertEquals(ModItems.BANDAGE, reloaded.getEntries().get(0).getStack().getItem());

		float expectedWeight = sampleCase.getWeightKg() + ModItems.BANDAGE.getWeightKg();
		assertEquals(expectedWeight, PlayerLoadout.estimateItemWeight(caseStack), 0.01f);

		// Put case inside backpack and verify recursive containsContainerId
		ItemStack bpStack = new ItemStack(ModItems.BERKUT_BACKPACK);
		GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bpStack);
		assertTrue(bpGrid.tryAutoPlace(caseStack.copy()));
		ContainerItemHelper.writeBackpackGrid(bpStack, bpGrid);
		assertTrue(ContainerItemHelper.containsContainerId(bpStack, caseUuid));
	}

	@Test
	void all9GrenadesEntityAndGasMaskProtectionWork() throws Exception {
		assertNotNull(Registries.ENTITY_TYPE.getId(ModEntities.GRENADE));
		assertEquals(new Identifier(TarkovCraftMod.MOD_ID, "grenade"), Registries.ENTITY_TYPE.getId(ModEntities.GRENADE));

		JsonArray items = loadCatalogRoot().getAsJsonArray("items");
		int grenadeCount = 0;
		Set<TarkovGrenadeItem.GrenadeType> seenTypes = EnumSet.noneOf(TarkovGrenadeItem.GrenadeType.class);

		for (JsonElement elem : items) {
			JsonObject obj = elem.getAsJsonObject();
			if (!"grenade".equals(obj.get("category").getAsString())) {
				continue;
			}
			grenadeCount++;
			String id = obj.get("id").getAsString();
			Item item = Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, id));
			assertInstanceOf(TarkovGrenadeItem.class, item, "Grenade must be TarkovGrenadeItem: " + id);
			TarkovGrenadeItem grenade = (TarkovGrenadeItem) item;
			seenTypes.add(grenade.getGrenadeType());
		}

		assertEquals(9, grenadeCount);
		assertEquals(TarkovGrenadeItem.GrenadeType.values().length, seenTypes.size());

		PlayerLoadout loadout = new PlayerLoadout();
		assertFalse(TarkovGrenadeEntity.hasGasMaskProtection(loadout));

		loadout.setEquipment(EquipmentSlotType.FACE_MASK, new ItemStack(ModItems.BALACLAVA_MASK));
		assertFalse(TarkovGrenadeEntity.hasGasMaskProtection(loadout));

		Item gasMask = Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, "protivogaz_gp_5_5b432c"));
		assertNotEquals(Items.AIR, gasMask);
		loadout.setEquipment(EquipmentSlotType.FACE_MASK, new ItemStack(gasMask));
		assertTrue(TarkovGrenadeEntity.hasGasMaskProtection(loadout));

		Item respirator = Registries.ITEM.get(new Identifier(TarkovCraftMod.MOD_ID, "respirator_gentex_ops_core_sotr_689b40"));
		assertNotEquals(Items.AIR, respirator);
		loadout.setEquipment(EquipmentSlotType.FACE_MASK, new ItemStack(respirator));
		assertTrue(TarkovGrenadeEntity.hasGasMaskProtection(loadout));
	}

	@Test
	void functionalContractsAndSizesWorkAcrossAllCategories() {
		TarkovFoodItem food = TarkovItemCatalog.getFoodItems().stream()
			.filter(TarkovFoodItem.class::isInstance)
			.map(TarkovFoodItem.class::cast)
			.findFirst()
			.orElseThrow();
		ItemStack foodStack = new ItemStack(food);
		assertTrue(TarkovScreenHandler.isConsumableItem(foodStack));
		assertEquals(food.getStoredItemSize(), ItemSizeRegistry.getSize(foodStack));
		assertEquals(food.getRarityTier(), ItemRarityRegistry.getTier(foodStack));
		assertEquals(food.isDrink() ? UseAction.DRINK : UseAction.EAT, food.getUseAction(foodStack));
		assertTrue(PlayerLoadout.estimateItemWeight(foodStack) >= 0.0f);

		TarkovStimulatorItem stim = TarkovItemCatalog.getMedicalItems().stream()
			.filter(TarkovStimulatorItem.class::isInstance)
			.map(TarkovStimulatorItem.class::cast)
			.findFirst()
			.orElseThrow();
		ItemStack stimStack = new ItemStack(stim);
		assertTrue(TarkovScreenHandler.isConsumableItem(stimStack));
		assertEquals(stim.getStoredItemSize(), ItemSizeRegistry.getSize(stimStack));
		assertEquals(stim.getRarityTier(), ItemRarityRegistry.getTier(stimStack));

		TarkovArmorRepairItem repair = TarkovItemCatalog.getArmorItems().stream()
			.filter(TarkovArmorRepairItem.class::isInstance)
			.map(TarkovArmorRepairItem.class::cast)
			.findFirst()
			.orElseThrow();
		ItemStack repairStack = new ItemStack(repair);
		assertTrue(TarkovScreenHandler.isConsumableItem(repairStack));
		assertEquals(repair.getStoredItemSize(), ItemSizeRegistry.getSize(repairStack));

		TarkovMeleeItem melee = TarkovItemCatalog.getTacticalItems().stream()
			.filter(TarkovMeleeItem.class::isInstance)
			.map(TarkovMeleeItem.class::cast)
			.findFirst()
			.orElseThrow();
		ItemStack meleeStack = new ItemStack(melee);
		assertTrue(EquipmentSlotType.MELEE.accepts(meleeStack));
		assertEquals(melee.getStoredItemSize(), ItemSizeRegistry.getSize(meleeStack));

		TarkovHeadsetItem headset = TarkovItemCatalog.getTacticalItems().stream()
			.filter(TarkovHeadsetItem.class::isInstance)
			.map(TarkovHeadsetItem.class::cast)
			.findFirst()
			.orElseThrow();
		ItemStack hsStack = new ItemStack(headset);
		assertEquals(headset.getStoredItemSize(), ItemSizeRegistry.getSize(hsStack));
		assertEquals(headset.getRarityTier(), ItemRarityRegistry.getTier(hsStack));
	}

	@Test
	void newMedicalPresetsBehaveConsistentlyInMedicalHelper() {
		TarkovMedicalItem grizzly = new TarkovMedicalItem(new Item.Settings(), MedicalType.GRIZZLY);
		ItemStack grizzlyStack = new ItemStack(grizzly);
		assertEquals(new ItemSize(2, 2), ItemSizeRegistry.getSize(grizzlyStack));
		assertEquals(ItemRarityTier.EPIC, ItemRarityRegistry.getTier(grizzlyStack));

		BodyPartHealth health = new BodyPartHealth();
		health.addInjury(BodyPart.LEFT_LEG, InjuryType.HEAVY_BLEED);
		health.addInjury(BodyPart.LEFT_LEG, InjuryType.FRACTURE);
		health.setHp(BodyPart.LEFT_LEG, 20.0f);

		assertTrue(MedicalHelper.canApplyToPart(health, grizzlyStack, BodyPart.LEFT_LEG));
		assertTrue(MedicalHelper.applyMedical(health, grizzlyStack, BodyPart.LEFT_LEG, false).applied());
		assertFalse(health.hasHeavyBleed(BodyPart.LEFT_LEG));
		assertTrue(MedicalHelper.applyMedical(health, grizzlyStack, BodyPart.LEFT_LEG, false).applied());
		assertFalse(health.hasFracture(BodyPart.LEFT_LEG));
		assertTrue(MedicalHelper.applyMedical(health, grizzlyStack, BodyPart.LEFT_LEG, false).applied());
		assertEquals(65.0f, health.getHp(BodyPart.LEFT_LEG), 0.01f);

		TarkovMedicalItem car = new TarkovMedicalItem(new Item.Settings(), MedicalType.CAR_MEDKIT);
		ItemStack carStack = new ItemStack(car);
		health.addInjury(BodyPart.RIGHT_ARM, InjuryType.HEAVY_BLEED);
		assertFalse(MedicalHelper.canApplyToPart(health, carStack, BodyPart.RIGHT_ARM));

		TarkovMedicalItem vaseline = new TarkovMedicalItem(new Item.Settings(), MedicalType.VASELINE);
		ItemStack vasStack = new ItemStack(vaseline);
		assertTrue(MedicalHelper.applyMedical(health, vasStack, null, false).applied());
		assertEquals(MedicalType.VASELINE.getPainkillerTicks(), health.getPainkillerTicks());
		assertEquals(5, TarkovMedicalItem.getRemaining(vasStack));
	}

	private static JsonObject loadCatalogRoot() throws Exception {
		try (InputStream stream = TarkovItemCatalog.class.getResourceAsStream(TarkovItemCatalog.CATALOG_RESOURCE)) {
			assertNotNull(stream, "Catalog resource must exist: " + TarkovItemCatalog.CATALOG_RESOURCE);
			try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		}
	}
}
