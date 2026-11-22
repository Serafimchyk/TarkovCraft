package com.tarkovcraft.loot;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovArmorRepairItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovFoodItem;
import com.tarkovcraft.item.TarkovFunctionalItem;
import com.tarkovcraft.item.TarkovGrenadeItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovMeleeItem;
import com.tarkovcraft.item.TarkovStimulatorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.registry.TarkovItemCatalog;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rich catalog-driven loot generator spanning all TarkovCraft items, tiered TaCZ ammunition,
 * real TaCZ firearms, TaCZ weapon attachments, and war-torn city zone loot tables.
 */
public final class TarkovLootGenerator {
	private static boolean poolsInitialized = false;

	private static final List<Item> ARMOR_1_TO_3 = new ArrayList<>();
	private static final List<Item> ARMOR_2_TO_5 = new ArrayList<>();
	private static final List<Item> ARMOR_4_TO_6 = new ArrayList<>();
	private static final List<Item> HELMETS_1_TO_3 = new ArrayList<>();
	private static final List<Item> HELMETS_2_TO_5 = new ArrayList<>();
	private static final List<Item> HELMETS_4_TO_6 = new ArrayList<>();
	private static final List<Item> COSMETIC_MASKS = new ArrayList<>();
	private static final List<Item> TACTICAL_MASKS = new ArrayList<>();
	private static final List<Item> PLATES_REPAIR = new ArrayList<>();
	private static final List<Item> ALL_RIGS = new ArrayList<>();
	private static final List<Item> LIGHT_RIGS = new ArrayList<>();
	private static final List<Item> LARGE_RIGS = new ArrayList<>();
	private static final List<Item> ALL_BACKPACKS = new ArrayList<>();
	private static final List<Item> LIGHT_BACKPACKS = new ArrayList<>();
	private static final List<Item> MEDIUM_BACKPACKS = new ArrayList<>();
	private static final List<Item> LARGE_BACKPACKS = new ArrayList<>();
	private static final List<Item> ALL_MEDS = new ArrayList<>();
	private static final List<Item> BASIC_MEDS = new ArrayList<>();
	private static final List<Item> ONE_BY_ONE_MEDS = new ArrayList<>();
	private static final List<Item> HIGH_TIER_MEDS = new ArrayList<>();
	private static final List<Item> ALL_STIMS = new ArrayList<>();
	private static final List<Item> TOP_STIMS = new ArrayList<>();
	private static final List<Item> ONE_BY_ONE_STIMS = new ArrayList<>();
	private static final List<Item> ALL_FOOD = new ArrayList<>();
	private static final List<Item> SMALL_FOOD = new ArrayList<>();
	private static final List<Item> ALL_GRENADES = new ArrayList<>();
	private static final List<Item> ONE_BY_ONE_GRENADES = new ArrayList<>();
	private static final List<Item> ALL_CASES = new ArrayList<>();
	private static final List<Item> MED_CASES = new ArrayList<>();
	private static final List<Item> SMALL_CASES = new ArrayList<>();
	private static final List<Item> LARGE_CASES = new ArrayList<>();
	private static final List<Item> MELEE_WEAPONS = new ArrayList<>();
	private static final List<Item> HEADSETS = new ArrayList<>();

	private TarkovLootGenerator() {
	}

	private static synchronized void ensurePools() {
		if (poolsInitialized) {
			return;
		}
		poolsInitialized = true;

		List<Item> all = new ArrayList<>();
		all.add(ModItems.SLING_BACKPACK);
		all.add(ModItems.BERKUT_BACKPACK);
		all.add(ModItems.TRIZIP_BACKPACK);
		all.add(ModItems.SCAV_VEST);
		all.add(ModItems.BANK_ROBBER_RIG);
		all.add(ModItems.TRITON_RIG);
		all.add(ModItems.SSH68_HELMET);
		all.add(ModItems.RATNIK_6B47_HELMET);
		all.add(ModItems.ALTYN_HELMET);
		all.add(ModItems.BALACLAVA_MASK);
		all.add(ModItems.DEATH_SHADOW_MASK);
		all.add(ModItems.PACA_ARMOR);
		all.add(ModItems.PRESS_ARMOR);
		all.add(ModItems.ZABRALO_ARMOR);
		all.add(ModItems.BANDAGE);
		all.add(ModItems.CAT_TOURNIQUET);
		all.add(ModItems.SPLINT);
		all.add(ModItems.ALU_SPLINT);
		all.add(ModItems.ANALGIN);
		all.add(ModItems.AI2_MEDKIT);
		all.add(ModItems.SALEWA_MEDKIT);
		all.add(ModItems.CMS_KIT);
		all.add(ModItems.SURV12_KIT);
		all.addAll(TarkovItemCatalog.getArmorItems());
		all.addAll(TarkovItemCatalog.getRigItems());
		all.addAll(TarkovItemCatalog.getBackpackItems());
		all.addAll(TarkovItemCatalog.getMedicalItems());
		all.addAll(TarkovItemCatalog.getFoodItems());
		all.addAll(TarkovItemCatalog.getTacticalItems());

		for (Item item : all) {
			ItemSize sz = ItemSizeRegistry.getSize(new ItemStack(item));
			boolean is1x1 = sz.width() == 1 && sz.height() == 1;

			if (item instanceof TarkovArmorItem armor) {
				int ac = armor.getArmorClass();
				if (armor.getTargetSlot() == EquipmentSlotType.BODY_ARMOR) {
					if (ac >= 1 && ac <= 3) ARMOR_1_TO_3.add(item);
					if (ac >= 2 && ac <= 5) ARMOR_2_TO_5.add(item);
					if (ac >= 4) ARMOR_4_TO_6.add(item);
				} else if (armor.getTargetSlot() == EquipmentSlotType.HELMET) {
					if (ac >= 1 && ac <= 3) HELMETS_1_TO_3.add(item);
					if (ac >= 2 && ac <= 5) HELMETS_2_TO_5.add(item);
					if (ac >= 4) HELMETS_4_TO_6.add(item);
				} else if (armor.getTargetSlot() == EquipmentSlotType.FACE_MASK) {
					if (ac <= 1) COSMETIC_MASKS.add(item);
					if (ac >= 2) TACTICAL_MASKS.add(item);
				}
			} else if (item instanceof TarkovArmorRepairItem) {
				PLATES_REPAIR.add(item);
			} else if (item instanceof TacticalRigItem rig) {
				ALL_RIGS.add(item);
				int cells = rig.getCompartmentLayouts().stream().mapToInt(s -> s.width() * s.height()).sum();
				if (cells <= 20) LIGHT_RIGS.add(item);
				if (cells >= 16) LARGE_RIGS.add(item);
			} else if (item instanceof BackpackItem bp) {
				ALL_BACKPACKS.add(item);
				int cells = bp.getGridWidth() * bp.getGridHeight();
				if (cells <= 20) LIGHT_BACKPACKS.add(item);
				if (cells >= 12 && cells <= 30) MEDIUM_BACKPACKS.add(item);
				if (cells >= 24) LARGE_BACKPACKS.add(item);
			} else if (item instanceof TarkovMedicalItem med) {
				ALL_MEDS.add(item);
				if (is1x1) ONE_BY_ONE_MEDS.add(item);
				switch (med.getMedicalType()) {
					case BANDAGE, ARMY_BANDAGE, CAT_TOURNIQUET, ESMARCH, SPLINT, ANALGIN, AI2_MEDKIT, CAR_MEDKIT ->
						BASIC_MEDS.add(item);
					case CMS_KIT, SURV12_KIT, GRIZZLY, AFAK, IFAK, SALEWA_MEDKIT ->
						HIGH_TIER_MEDS.add(item);
					default -> {
					}
				}
			} else if (item instanceof TarkovStimulatorItem stim) {
				ALL_STIMS.add(item);
				if (is1x1) ONE_BY_ONE_STIMS.add(item);
				switch (stim.getPreset()) {
					case PROPITAL, ETG_CHANGE, SJ6, MULE, OBDOLBOS_2, PNB, TRIMADOL, ZAGUSTIN, PERFOTORAN ->
						TOP_STIMS.add(item);
					default -> {
					}
				}
			} else if (item instanceof TarkovFoodItem) {
				ALL_FOOD.add(item);
				if (is1x1) SMALL_FOOD.add(item);
			} else if (item instanceof TarkovGrenadeItem) {
				ALL_GRENADES.add(item);
				if (is1x1) ONE_BY_ONE_GRENADES.add(item);
			} else if (item instanceof TarkovCaseItem caze) {
				ALL_CASES.add(item);
				if (caze.isMedicalCase()) MED_CASES.add(item);
				if (sz.width() * sz.height() <= 2) SMALL_CASES.add(item);
				if (caze.getGridWidth() * caze.getGridHeight() >= 25) LARGE_CASES.add(item);
			} else if (item instanceof TarkovMeleeItem) {
				MELEE_WEAPONS.add(item);
			} else if (item instanceof TarkovFunctionalItem fn && "headset".equalsIgnoreCase(fn.getCategory())) {
				HEADSETS.add(item);
			}
		}
	}

	private record GunPreset(String gunId, String ammoId, int magSize, String fireMode) {
	}

	private static final Identifier TACZ_GUN_ITEM_ID = new Identifier("tacz", "modern_kinetic_gun");
	private static final Identifier TACZ_ATTACHMENT_ITEM_ID = new Identifier("tacz", "attachment");

	private static final List<GunPreset> CIVILIAN_POLICE_GUNS = List.of(
		new GunPreset("tacz:glock_17", "tacz:9mm", 17, "SEMI"),
		new GunPreset("tacz:cz75", "tacz:9mm", 16, "SEMI"),
		new GunPreset("tacz:m1911", "tacz:45acp", 7, "SEMI"),
		new GunPreset("tacz:hk_mk23", "tacz:45acp", 12, "SEMI"),
		new GunPreset("tacz:m870", "tacz:12g", 6, "SEMI"),
		new GunPreset("tacz:m1014", "tacz:12g", 7, "SEMI"),
		new GunPreset("tacz:hk_mp5a5", "tacz:9mm", 30, "AUTO")
	);

	private static final List<GunPreset> MILITARY_GUNS = List.of(
		new GunPreset("tacz:glock_17", "tacz:9mm", 17, "SEMI"),
		new GunPreset("tacz:cz75", "tacz:9mm", 16, "SEMI"),
		new GunPreset("tacz:m1911", "tacz:45acp", 7, "SEMI"),
		new GunPreset("tacz:hk_mk23", "tacz:45acp", 12, "SEMI"),
		new GunPreset("tacz:hk_mp5a5", "tacz:9mm", 30, "AUTO"),
		new GunPreset("tacz:hk_mp7a1", "tacz:46x30", 30, "AUTO"),
		new GunPreset("tacz:m870", "tacz:12g", 6, "SEMI"),
		new GunPreset("tacz:m1014", "tacz:12g", 7, "SEMI"),
		new GunPreset("tacz:ak74m", "tacz:545x39", 30, "AUTO"),
		new GunPreset("tacz:ak47", "tacz:762x39", 30, "AUTO"),
		new GunPreset("tacz:m16a4", "tacz:556x45", 30, "BURST"),
		new GunPreset("tacz:g36k", "tacz:556x45", 30, "AUTO"),
		new GunPreset("tacz:aug", "tacz:556x45", 30, "AUTO"),
		new GunPreset("tacz:kar98", "tacz:792x57", 5, "SEMI")
	);

	private static final List<GunPreset> AIRDROP_GUNS = List.of(
		new GunPreset("tacz:ak12", "tacz:545x39", 30, "AUTO"),
		new GunPreset("tacz:m4a1", "tacz:556x45", 30, "AUTO"),
		new GunPreset("tacz:hk416d", "tacz:556x45", 30, "AUTO"),
		new GunPreset("tacz:fn_fal", "tacz:308", 20, "AUTO"),
		new GunPreset("tacz:hk_g3", "tacz:308", 20, "AUTO"),
		new GunPreset("tacz:aa12", "tacz:12g", 20, "AUTO"),
		new GunPreset("tacz:deagle", "tacz:50ae", 7, "SEMI"),
		new GunPreset("tacz:m700", "tacz:30_06", 5, "SEMI"),
		new GunPreset("tacz:ai_awp", "tacz:338", 5, "SEMI"),
		new GunPreset("tacz:m107", "tacz:50bmg", 10, "SEMI"),
		new GunPreset("tacz:m249", "tacz:556x45", 60, "AUTO"),
		new GunPreset("tacz:fn_evolys", "tacz:556x45", 60, "AUTO")
	);

	/**
	 * Verified non-hidden TaCZ default gunpack attachments split into 3 progression tiers.
	 */
	public static final List<String> COMMON_ATTACHMENTS = List.of(
		// Red dots & basic optics
		"tacz:sight_coyote",
		"tacz:sight_okp7",
		"tacz:sight_pk06_pistol",
		"tacz:sight_pk06_rifle",
		"tacz:sight_rmr_dot",
		"tacz:sight_sro_dot",
		"tacz:sight_fastfire_pistol",
		"tacz:sight_fastfire_rifle",
		"tacz:sight_deltapoint_pistol",
		"tacz:sight_deltapoint_rifle",
		"tacz:sight_t1",
		"tacz:sight_552",
		"tacz:scope_retro_2x",
		"tacz:scope_98k",
		// Basic foregrips
		"tacz:grip_magpul_afg",
		"tacz:grip_magpul_afg_2",
		"tacz:grip_rk0",
		"tacz:grip_rk6",
		"tacz:grip_td",
		"tacz:grip_cobra",
		// Basic muzzles & bayonets
		"tacz:muzzle_brake_pioneer",
		"tacz:muzzle_brake_cthulhu",
		"tacz:muzzle_compensator_trident",
		"tacz:muzzle_choke_sg",
		"tacz:muzzle_duckbill_sg",
		"tacz:bayonet_6h3",
		"tacz:bayonet_m9",
		// Basic stocks
		"tacz:stock_moe",
		"tacz:stock_m4ss",
		"tacz:stock_sba3",
		"tacz:stock_hk_slim_line",
		"tacz:stock_ak12",
		// Tier 1 extended mags
		"tacz:light_extended_mag_1",
		"tacz:extended_mag_1",
		"tacz:shotgun_extended_mag_1",
		"tacz:sniper_extended_mag_1",
		// Compact lasers
		"tacz:laser_compact",
		"tacz:laser_nightstick"
	);

	public static final List<String> TACTICAL_ATTACHMENTS = List.of(
		// Holographic & mid-range tactical optics
		"tacz:sight_exp3",
		"tacz:sight_uh1",
		"tacz:sight_1p87",
		"tacz:sight_t2",
		"tacz:sight_acro_pistol",
		"tacz:sight_acro_rifle",
		"tacz:sight_srs_02",
		"tacz:sight_apm4s",
		"tacz:scope_acog_ta31",
		"tacz:scope_elcan_4x",
		"tacz:scope_c79",
		"tacz:scope_hamr",
		"tacz:scope_lpvo_1_6",
		"tacz:scope_qmk152",
		// Suppressors & heavy brakes
		"tacz:muzzle_brake_cyclone_d2",
		"tacz:muzzle_brake_trex",
		"tacz:muzzle_brake_mastiff_sg",
		"tacz:muzzle_silencer_mirage",
		"tacz:muzzle_silencer_phantom_s1",
		"tacz:muzzle_silencer_ptilopsis",
		"tacz:muzzle_silencer_sg",
		"tacz:muzzle_silencer_ursus",
		// Tactical foregrips
		"tacz:grip_osovets_black",
		"tacz:grip_rk1_b25u",
		"tacz:grip_se_5",
		"tacz:grip_troy",
		"tacz:grip_vertical_military",
		"tacz:grip_vertical_ranger",
		"tacz:grip_vertical_talon",
		// Tactical stocks
		"tacz:stock_tactical_ar",
		"tacz:stock_militech_b5",
		"tacz:stock_heavy_ctr",
		"tacz:stock_ripstock",
		"tacz:stock_carbon_bone_c5",
		// Tier 2 extended mags
		"tacz:light_extended_mag_2",
		"tacz:extended_mag_2",
		"tacz:shotgun_extended_mag_2",
		"tacz:sniper_extended_mag_2",
		// Military lasers
		"tacz:laser_lopro",
		"tacz:laser_peq6"
	);

	public static final List<String> ELITE_ATTACHMENTS = List.of(
		// High-end LPVO, sniper & thermal optics
		"tacz:scope_vudu",
		"tacz:scope_mk4ert",
		"tacz:scope_mk5hd",
		"tacz:scope_standard_8x",
		"tacz:scope_amg_10",
		"tacz:scope_xl50",
		"tacz:scope_xm157",
		"tacz:scope_reap_ir",
		"tacz:sight_infitac_fmp13",
		// Top-tier suppressors & .50 brake
		"tacz:muzzle_silencer_knight_qd",
		"tacz:muzzle_silencer_socom_rc1",
		"tacz:muzzle_silencer_vulture",
		"tacz:muzzle_silencer_wraith",
		"tacz:muzzle_brake_timeless50",
		// Tier 3 drum / high-cap mags
		"tacz:light_extended_mag_3",
		"tacz:extended_mag_3",
		"tacz:shotgun_extended_mag_3",
		"tacz:sniper_extended_mag_3",
		// Top lasers & sniper stock
		"tacz:laser_ngal",
		"tacz:stock_psg"
	);

	public static ItemStack createTaczGunStack(GunPreset preset, AmmoTier loadedTier, Random random) {
		if (preset == null || !Registries.ITEM.containsId(TACZ_GUN_ITEM_ID)) {
			return ItemStack.EMPTY;
		}
		Item gunItem = Registries.ITEM.get(TACZ_GUN_ITEM_ID);
		ItemStack stack = new ItemStack(gunItem, 1);
		NbtCompound nbt = stack.getOrCreateNbt();
		nbt.putString("GunId", preset.gunId());
		nbt.putString("GunFireMode", preset.fireMode());
		int minAmmo = Math.max(1, preset.magSize() / 2);
		int loadedCount = minAmmo + random.nextInt(Math.max(1, preset.magSize() - minAmmo + 1));
		nbt.putInt("GunCurrentAmmoCount", loadedCount);
		nbt.putBoolean("HasBulletInBarrel", true);
		AmmoTier.stampLoadedTierOnGun(stack, loadedTier != null ? loadedTier : AmmoTier.FMJ);
		return stack;
	}

	public static ItemStack createTaczAttachmentStack(String attachmentId) {
		if (attachmentId == null || attachmentId.isBlank() || !Registries.ITEM.containsId(TACZ_ATTACHMENT_ITEM_ID)) {
			return ItemStack.EMPTY;
		}
		Item attItem = Registries.ITEM.get(TACZ_ATTACHMENT_ITEM_ID);
		ItemStack stack = new ItemStack(attItem, 1);
		stack.getOrCreateNbt().putString("AttachmentId", attachmentId);
		return stack;
	}

	public static ItemStack rollAttachment(Random random, int wCommon, int wTactical, int wElite) {
		int total = Math.max(1, wCommon + wTactical + wElite);
		int r = random.nextInt(total);
		List<String> list;
		if (r < wCommon) {
			list = COMMON_ATTACHMENTS;
		} else if (r < wCommon + wTactical) {
			list = TACTICAL_ATTACHMENTS;
		} else {
			list = ELITE_ATTACHMENTS;
		}
		if (list.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return createTaczAttachmentStack(list.get(random.nextInt(list.size())));
	}

	public static void populateLootGrid(GridContainer grid, TarkovLootPool pool, Random random) {
		if (grid == null || pool == null || random == null) {
			return;
		}
		ensurePools();

		// Zone-specific guaranteed / bonus firearms & weapon attachments
		switch (pool) {
			case MILITARY -> {
				if (random.nextFloat() < 0.45f) {
					tryPlaceGunWithAmmo(grid, MILITARY_GUNS, random.nextFloat() < 0.35f ? AmmoTier.AP : AmmoTier.FMJ, 10, 30, random);
				}
				if (random.nextFloat() < 0.55f) {
					tryPlaceNonEmpty(grid, rollAttachment(random, 40, 48, 12));
				}
			}
			case AIRDROP -> {
				int gunCount = random.nextBoolean() ? 2 : 1;
				for (int g = 0; g < gunCount; g++) {
					tryPlaceGunWithAmmo(grid, AIRDROP_GUNS, random.nextFloat() < 0.45f ? AmmoTier.IGN : AmmoTier.AP, 15, 40, random);
				}
				int attCount = 1 + random.nextInt(2);
				for (int a = 0; a < attCount; a++) {
					tryPlaceNonEmpty(grid, rollAttachment(random, 10, 45, 45));
				}
			}
			case POLICE -> {
				if (random.nextFloat() < 0.40f) {
					tryPlaceGunWithAmmo(grid, CIVILIAN_POLICE_GUNS, random.nextFloat() < 0.25f ? AmmoTier.AP : AmmoTier.FMJ, 10, 24, random);
				}
				if (random.nextFloat() < 0.45f) {
					tryPlaceNonEmpty(grid, rollAttachment(random, 55, 40, 5));
				}
			}
			case PORT -> {
				if (random.nextFloat() < 0.32f) {
					tryPlaceGunWithAmmo(grid, MILITARY_GUNS, random.nextFloat() < 0.35f ? AmmoTier.AP : AmmoTier.FMJ, 10, 28, random);
				}
				if (random.nextFloat() < 0.50f) {
					tryPlaceNonEmpty(grid, rollAttachment(random, 30, 50, 20));
				}
			}
			case BATTLEFIELD -> {
				if (random.nextFloat() < 0.50f) {
					tryPlaceGunWithAmmo(grid, MILITARY_GUNS, random.nextFloat() < 0.40f ? AmmoTier.AP : AmmoTier.FMJ, 8, 25, random);
				}
				if (random.nextFloat() < 0.55f) {
					tryPlaceNonEmpty(grid, rollAttachment(random, 35, 50, 15));
				}
			}
			case FACTORY -> {
				if (random.nextFloat() < 0.22f) {
					tryPlaceGunWithAmmo(grid, CIVILIAN_POLICE_GUNS, AmmoTier.FMJ, 8, 20, random);
				}
				if (random.nextFloat() < 0.30f) {
					tryPlaceNonEmpty(grid, rollAttachment(random, 65, 30, 5));
				}
			}
			case GARAGE -> {
				if (random.nextFloat() < 0.18f) {
					tryPlaceGunWithAmmo(grid, CIVILIAN_POLICE_GUNS, random.nextBoolean() ? AmmoTier.FMJ : AmmoTier.HP, 6, 18, random);
				}
				if (random.nextFloat() < 0.28f) {
					tryPlaceNonEmpty(grid, rollAttachment(random, 70, 28, 2));
				}
			}
			case OFFICE -> {
				if (random.nextFloat() < 0.15f) {
					tryPlaceGunWithAmmo(grid, CIVILIAN_POLICE_GUNS, AmmoTier.FMJ, 8, 16, random);
				}
				if (random.nextFloat() < 0.25f) {
					tryPlaceNonEmpty(grid, rollAttachment(random, 55, 40, 5));
				}
			}
			case RESIDENTIAL, SCHOOL -> {
				if (random.nextFloat() < 0.10f) {
					tryPlaceGunWithAmmo(grid, CIVILIAN_POLICE_GUNS, AmmoTier.HP, 6, 14, random);
				}
				if (random.nextFloat() < 0.15f) {
					tryPlaceNonEmpty(grid, rollAttachment(random, 85, 15, 0));
				}
			}
			default -> {
			}
		}

		int baseRolls = switch (pool) {
			case MILITARY, MEDICAL, BATTLEFIELD, PORT -> 4 + random.nextInt(4);
			case CIVILIAN, CAFE, SCHOOL, RESIDENTIAL, OFFICE -> 3 + random.nextInt(4);
			case FACTORY, SUPERMARKET, PHARMACY, POLICE, GARAGE -> 4 + random.nextInt(3);
			case AIRDROP -> 8 + random.nextInt(5);
			case SCAV_POCKET -> 1 + random.nextInt(3);
		};
		float mult = TarkovConfig.get().lootDensityMultiplier;
		int rolls = Math.max(1, Math.round(baseRolls * mult));

		for (int i = 0; i < rolls; i++) {
			ItemStack candidate = rollItem(pool, random);
			if (!candidate.isEmpty()) {
				grid.tryAutoPlace(candidate);
			}
		}
	}

	/**
	 * Balanced loot population for vanilla city containers (barrels, chests, shulker boxes)
	 * so dense city interiors (10k+ containers) feel authentic like EFT rather than oversaturated.
	 */
	public static void populateVanillaContainerGrid(
		GridContainer grid,
		TarkovLootPool pool,
		boolean isShulker,
		boolean isDouble,
		Random random
	) {
		if (grid == null || pool == null || random == null) {
			return;
		}
		if (isShulker || isDouble) {
			populateLootGrid(grid, pool, random);
			return;
		}
		ensurePools();

		// Smaller chance for a firearm/attachment in single barrels/chests
		if (pool == TarkovLootPool.MILITARY || pool == TarkovLootPool.BATTLEFIELD || pool == TarkovLootPool.PORT) {
			if (random.nextFloat() < 0.22f) {
				tryPlaceGunWithAmmo(grid, MILITARY_GUNS, random.nextFloat() < 0.30f ? AmmoTier.AP : AmmoTier.FMJ, 8, 22, random);
			}
			if (random.nextFloat() < 0.35f) {
				tryPlaceNonEmpty(grid, rollAttachment(random, 40, 48, 12));
			}
		} else if (pool == TarkovLootPool.POLICE) {
			if (random.nextFloat() < 0.22f) {
				tryPlaceGunWithAmmo(grid, CIVILIAN_POLICE_GUNS, AmmoTier.FMJ, 8, 20, random);
			}
			if (random.nextFloat() < 0.30f) {
				tryPlaceNonEmpty(grid, rollAttachment(random, 55, 40, 5));
			}
		} else if ((pool == TarkovLootPool.FACTORY || pool == TarkovLootPool.GARAGE) && random.nextFloat() < 0.12f) {
			tryPlaceGunWithAmmo(grid, CIVILIAN_POLICE_GUNS, AmmoTier.FMJ, 6, 16, random);
		}

		int baseRolls = 2 + random.nextInt(3); // 2..4 items per barrel/single chest
		float mult = TarkovConfig.get().lootDensityMultiplier;
		int rolls = Math.max(1, Math.round(baseRolls * mult));
		for (int i = 0; i < rolls; i++) {
			ItemStack candidate = rollItem(pool, random);
			if (!candidate.isEmpty()) {
				grid.tryAutoPlace(candidate);
			}
		}
	}

	private static void tryPlaceGunWithAmmo(
		GridContainer grid,
		List<GunPreset> pool,
		AmmoTier requestedTier,
		int minAmmo,
		int maxAmmo,
		Random random
	) {
		GunPreset gun = pool.get(random.nextInt(pool.size()));
		AmmoTier tier = TaCZCaliberCatalog.getSpecOrDynamic(gun.ammoId()).clampTier(requestedTier);
		ItemStack gunStack = createTaczGunStack(gun, tier, random);
		if (!gunStack.isEmpty()) {
			grid.tryAutoPlace(gunStack);
			int count = Math.min(maxAmmo, Math.max(minAmmo, gun.magSize()));
			tryPlaceNonEmpty(grid, AmmoTier.createTaczAmmoStack(gun.ammoId(), tier, count));
		}
	}

	private static void tryPlaceNonEmpty(GridContainer grid, ItemStack stack) {
		if (!stack.isEmpty()) {
			grid.tryAutoPlace(stack);
		}
	}

	public static ItemStack rollItem(TarkovLootPool pool, Random random) {
		ensurePools();
		return switch (pool) {
			case MILITARY -> rollMilitary(random);
			case MEDICAL -> rollMedical(random);
			case CIVILIAN -> rollCivilian(random);
			case AIRDROP -> rollAirdrop(random);
			case SCAV_POCKET -> rollScavPocket(random);
			case CAFE -> rollCafe(random);
			case SCHOOL -> rollSchool(random);
			case FACTORY -> rollFactory(random);
			case PORT -> rollPort(random);
			case RESIDENTIAL -> rollResidential(random);
			case SUPERMARKET -> rollSupermarket(random);
			case PHARMACY -> rollPharmacy(random);
			case POLICE -> rollPolice(random);
			case OFFICE -> rollOffice(random);
			case GARAGE -> rollGarage(random);
			case BATTLEFIELD -> rollBattlefield(random);
		};
	}

	private static ItemStack rollMilitary(Random random) {
		int p = random.nextInt(100);
		if (p < 25) {
			return rollAmmo(random, 45, 20, 30, 5, 12, 30);
		}
		if (p < 43) {
			return rollAttachmentOrFallback(random, 40, 48, 12, PLATES_REPAIR);
		}
		if (p < 60) {
			return random.nextBoolean() ? pickStack(ARMOR_2_TO_5, random, 1) : pickStack(HELMETS_2_TO_5, random, 1);
		}
		if (p < 70) {
			return pickStack(PLATES_REPAIR, random, 1);
		}
		if (p < 82) {
			return random.nextBoolean() ? pickStack(ALL_RIGS, random, 1) : pickStack(MEDIUM_BACKPACKS, random, 1);
		}
		if (p < 92) {
			return pickStack(ALL_GRENADES, random, 1 + random.nextInt(2));
		}
		int sub = random.nextInt(3);
		if (sub == 0) return pickStack(HEADSETS, random, 1);
		if (sub == 1) return pickStack(TACTICAL_MASKS, random, 1);
		return pickStack(MELEE_WEAPONS, random, 1);
	}

	private static ItemStack rollMedical(Random random) {
		int p = random.nextInt(100);
		if (p < 48) {
			return pickStack(ALL_MEDS, random, 1);
		}
		if (p < 82) {
			return pickStack(ALL_STIMS, random, 1);
		}
		if (p < 96) {
			return pickStack(ALL_FOOD, random, 1 + random.nextInt(2));
		}
		return pickStack(MED_CASES.isEmpty() ? ALL_CASES : MED_CASES, random, 1);
	}

	private static ItemStack rollCivilian(Random random) {
		int p = random.nextInt(100);
		if (p < 35) {
			return pickStack(ALL_FOOD, random, 1 + random.nextInt(3));
		}
		if (p < 55) {
			return pickStack(COSMETIC_MASKS, random, 1);
		}
		if (p < 67) {
			return random.nextBoolean() ? pickStack(LIGHT_RIGS, random, 1) : pickStack(LIGHT_BACKPACKS, random, 1);
		}
		if (p < 81) {
			return pickStack(BASIC_MEDS, random, 1);
		}
		if (p < 91) {
			Item[] mats = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.LEATHER, Items.GUNPOWDER, Items.GOLD_INGOT};
			return new ItemStack(mats[random.nextInt(mats.length)], 2 + random.nextInt(5));
		}
		if (p < 96) {
			return rollAttachmentOrFallback(random, 85, 15, 0, BASIC_MEDS);
		}
		return pickStack(SMALL_CASES.isEmpty() ? ALL_CASES : SMALL_CASES, random, 1);
	}

	private static ItemStack rollAirdrop(Random random) {
		int p = random.nextInt(100);
		if (p < 22) {
			return random.nextBoolean() ? pickStack(ARMOR_4_TO_6, random, 1) : pickStack(HELMETS_4_TO_6, random, 1);
		}
		if (p < 40) {
			return rollAmmo(random, 0, 0, 55, 45, 20, 40);
		}
		if (p < 56) {
			return rollAttachmentOrFallback(random, 10, 45, 45, PLATES_REPAIR);
		}
		if (p < 68) {
			return pickStack(TOP_STIMS.isEmpty() ? ALL_STIMS : TOP_STIMS, random, 1);
		}
		if (p < 80) {
			return random.nextBoolean() ? pickStack(LARGE_BACKPACKS, random, 1) : pickStack(LARGE_RIGS, random, 1);
		}
		if (p < 89) {
			return pickStack(LARGE_CASES.isEmpty() ? ALL_CASES : LARGE_CASES, random, 1);
		}
		int sub = random.nextInt(3);
		if (sub == 0) return pickStack(ALL_GRENADES, random, 1 + random.nextInt(3));
		if (sub == 1) return pickStack(HIGH_TIER_MEDS.isEmpty() ? ALL_MEDS : HIGH_TIER_MEDS, random, 1);
		return pickStack(PLATES_REPAIR, random, 1);
	}

	private static ItemStack rollScavPocket(Random random) {
		int p = random.nextInt(100);
		if (p < 32) {
			return pickStack(SMALL_FOOD.isEmpty() ? ALL_FOOD : SMALL_FOOD, random, 1 + random.nextInt(2));
		}
		if (p < 54) {
			return pickStack(ONE_BY_ONE_MEDS.isEmpty() ? BASIC_MEDS : ONE_BY_ONE_MEDS, random, 1);
		}
		if (p < 74) {
			return rollAmmo(random, 70, 30, 0, 0, 8, 16);
		}
		if (p < 82) {
			return pickStack(ONE_BY_ONE_STIMS.isEmpty() ? ALL_STIMS : ONE_BY_ONE_STIMS, random, 1);
		}
		if (p < 89) {
			return pickStack(ONE_BY_ONE_GRENADES.isEmpty() ? ALL_GRENADES : ONE_BY_ONE_GRENADES, random, 1);
		}
		if (p < 95) {
			return rollAttachmentOrFallback(random, 85, 15, 0, ONE_BY_ONE_MEDS);
		}
		Item[] mats = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.GUNPOWDER};
		return new ItemStack(mats[random.nextInt(mats.length)], 1 + random.nextInt(3));
	}

	// ==================== Abandoned War-Torn City Zones ====================

	/** Abandoned cafe / diner: food, drinks, light bags, occasional scav leftovers. */
	private static ItemStack rollCafe(Random random) {
		int p = random.nextInt(100);
		if (p < 56) {
			return pickStack(ALL_FOOD, random, 1 + random.nextInt(3));
		}
		if (p < 70) {
			return pickStack(BASIC_MEDS, random, 1);
		}
		if (p < 80) {
			return pickStack(LIGHT_BACKPACKS.isEmpty() ? ALL_BACKPACKS : LIGHT_BACKPACKS, random, 1);
		}
		if (p < 88) {
			return pickStack(COSMETIC_MASKS, random, 1);
		}
		if (p < 94) {
			return rollAmmo(random, 60, 40, 0, 0, 6, 14);
		}
		if (p < 98) {
			return pickStack(SMALL_CASES.isEmpty() ? ALL_CASES : SMALL_CASES, random, 1);
		}
		return pickStack(MELEE_WEAPONS, random, 1);
	}

	/** Abandoned school (former evacuation shelter): backpacks, first aid, rations, stray militia gear. */
	private static ItemStack rollSchool(Random random) {
		int p = random.nextInt(100);
		if (p < 28) {
			return pickStack(ALL_FOOD, random, 1 + random.nextInt(2));
		}
		if (p < 48) {
			return random.nextBoolean()
				? pickStack(LIGHT_BACKPACKS.isEmpty() ? ALL_BACKPACKS : LIGHT_BACKPACKS, random, 1)
				: pickStack(MEDIUM_BACKPACKS.isEmpty() ? ALL_BACKPACKS : MEDIUM_BACKPACKS, random, 1);
		}
		if (p < 66) {
			return pickStack(BASIC_MEDS, random, 1);
		}
		if (p < 76) {
			return random.nextBoolean() ? pickStack(ARMOR_1_TO_3, random, 1) : pickStack(HELMETS_1_TO_3, random, 1);
		}
		if (p < 85) {
			return rollAmmo(random, 60, 35, 5, 0, 8, 18);
		}
		if (p < 92) {
			return rollAttachmentOrFallback(random, 80, 20, 0, BASIC_MEDS);
		}
		if (p < 96) {
			return pickStack(ALL_GRENADES, random, 1);
		}
		Item[] mats = {Items.PAPER, Items.LEATHER, Items.STRING, Items.IRON_INGOT};
		return new ItemStack(mats[random.nextInt(mats.length)], 2 + random.nextInt(4));
	}

	/** Industrial factory: crafting metals, armor plates/repair kits, rigs, melee tools, attachments & ammo. */
	private static ItemStack rollFactory(Random random) {
		int p = random.nextInt(100);
		if (p < 26) {
			Item[] mats = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.GUNPOWDER, Items.REDSTONE, Items.COAL, Items.GOLD_INGOT};
			return new ItemStack(mats[random.nextInt(mats.length)], 3 + random.nextInt(6));
		}
		if (p < 42) {
			return pickStack(PLATES_REPAIR, random, 1);
		}
		if (p < 56) {
			return rollAttachmentOrFallback(random, 60, 35, 5, PLATES_REPAIR);
		}
		if (p < 68) {
			return rollAmmo(random, 50, 25, 25, 0, 10, 24);
		}
		if (p < 78) {
			return random.nextBoolean() ? pickStack(ALL_RIGS, random, 1) : pickStack(MEDIUM_BACKPACKS, random, 1);
		}
		if (p < 86) {
			return pickStack(MELEE_WEAPONS, random, 1);
		}
		if (p < 93) {
			return random.nextBoolean() ? pickStack(HEADSETS, random, 1) : pickStack(TACTICAL_MASKS, random, 1);
		}
		return pickStack(ALL_CASES, random, 1);
	}

	/** Harbor / shipping port: smuggling & military cargo crates, attachments, cases, large bags, AP ammo. */
	private static ItemStack rollPort(Random random) {
		int p = random.nextInt(100);
		if (p < 22) {
			return rollAttachmentOrFallback(random, 30, 50, 20, PLATES_REPAIR);
		}
		if (p < 40) {
			return rollAmmo(random, 35, 15, 42, 8, 14, 30);
		}
		if (p < 54) {
			return random.nextBoolean() ? pickStack(LARGE_BACKPACKS, random, 1) : pickStack(LARGE_RIGS, random, 1);
		}
		if (p < 68) {
			return random.nextBoolean() ? pickStack(ARMOR_2_TO_5, random, 1) : pickStack(HELMETS_2_TO_5, random, 1);
		}
		if (p < 78) {
			return pickStack(ALL_CASES, random, 1);
		}
		if (p < 88) {
			return pickStack(ALL_GRENADES, random, 1 + random.nextInt(2));
		}
		if (p < 94) {
			return pickStack(ALL_FOOD, random, 1 + random.nextInt(2));
		}
		Item[] cargo = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.EMERALD, Items.GUNPOWDER, Items.DIAMOND};
		return new ItemStack(cargo[random.nextInt(cargo.length)], 2 + random.nextInt(5));
	}

	/** Residential apartments: food, basic meds, clothes/masks, light bags, occasional civilian defense gear. */
	private static ItemStack rollResidential(Random random) {
		int p = random.nextInt(100);
		if (p < 34) {
			return pickStack(ALL_FOOD, random, 1 + random.nextInt(2));
		}
		if (p < 52) {
			return pickStack(BASIC_MEDS, random, 1);
		}
		if (p < 66) {
			return pickStack(COSMETIC_MASKS, random, 1);
		}
		if (p < 78) {
			return random.nextBoolean() ? pickStack(LIGHT_BACKPACKS, random, 1) : pickStack(LIGHT_RIGS, random, 1);
		}
		if (p < 86) {
			return rollAmmo(random, 55, 45, 0, 0, 6, 15);
		}
		if (p < 92) {
			return rollAttachmentOrFallback(random, 85, 15, 0, BASIC_MEDS);
		}
		if (p < 97) {
			Item[] household = {Items.LEATHER, Items.IRON_INGOT, Items.COPPER_INGOT, Items.GOLD_INGOT};
			return new ItemStack(household[random.nextInt(household.length)], 1 + random.nextInt(4));
		}
		return pickStack(SMALL_CASES.isEmpty() ? ALL_CASES : SMALL_CASES, random, 1);
	}

	/** Looted supermarket / grocery store: heavy food & drink supply, small bags, basic meds. */
	private static ItemStack rollSupermarket(Random random) {
		int p = random.nextInt(100);
		if (p < 64) {
			return pickStack(ALL_FOOD, random, 1 + random.nextInt(3));
		}
		if (p < 76) {
			return pickStack(LIGHT_BACKPACKS.isEmpty() ? ALL_BACKPACKS : LIGHT_BACKPACKS, random, 1);
		}
		if (p < 86) {
			return pickStack(BASIC_MEDS, random, 1);
		}
		if (p < 94) {
			Item[] mats = {Items.LEATHER, Items.COPPER_INGOT, Items.IRON_INGOT, Items.PAPER};
			return new ItemStack(mats[random.nextInt(mats.length)], 2 + random.nextInt(4));
		}
		return random.nextBoolean() ? pickStack(COSMETIC_MASKS, random, 1) : pickStack(MELEE_WEAPONS, random, 1);
	}

	/** Abandoned pharmacy / clinic: abundant meds, stimulators, med cases. */
	private static ItemStack rollPharmacy(Random random) {
		int p = random.nextInt(100);
		if (p < 54) {
			return random.nextFloat() < 0.72f ? pickStack(BASIC_MEDS, random, 1) : pickStack(HIGH_TIER_MEDS, random, 1);
		}
		if (p < 84) {
			return pickStack(ALL_STIMS, random, 1);
		}
		if (p < 94) {
			return pickStack(SMALL_FOOD.isEmpty() ? ALL_FOOD : SMALL_FOOD, random, 1 + random.nextInt(2));
		}
		return pickStack(MED_CASES.isEmpty() ? ALL_CASES : MED_CASES, random, 1);
	}

	/** Police station / checkpoint: tactical armor 2-4, rigs, attachments, ammo, grenades, first aid. */
	private static ItemStack rollPolice(Random random) {
		int p = random.nextInt(100);
		if (p < 24) {
			return rollAmmo(random, 55, 20, 25, 0, 12, 26);
		}
		if (p < 44) {
			return rollAttachmentOrFallback(random, 55, 40, 5, PLATES_REPAIR);
		}
		if (p < 62) {
			return random.nextBoolean() ? pickStack(ARMOR_1_TO_3, random, 1) : pickStack(HELMETS_2_TO_5, random, 1);
		}
		if (p < 74) {
			return random.nextBoolean() ? pickStack(ALL_RIGS, random, 1) : pickStack(TACTICAL_MASKS, random, 1);
		}
		if (p < 85) {
			return pickStack(ALL_GRENADES, random, 1 + random.nextInt(2));
		}
		if (p < 94) {
			return pickStack(BASIC_MEDS, random, 1);
		}
		return pickStack(HEADSETS, random, 1);
	}

	/** Offices / administration / HQ: valuables, cases, headsets, stims, tactical attachments. */
	private static ItemStack rollOffice(Random random) {
		int p = random.nextInt(100);
		if (p < 26) {
			Item[] valuables = {Items.GOLD_INGOT, Items.EMERALD, Items.DIAMOND, Items.IRON_INGOT, Items.PAPER};
			return new ItemStack(valuables[random.nextInt(valuables.length)], 1 + random.nextInt(4));
		}
		if (p < 42) {
			return pickStack(ALL_FOOD, random, 1 + random.nextInt(2));
		}
		if (p < 56) {
			return pickStack(SMALL_CASES.isEmpty() ? ALL_CASES : SMALL_CASES, random, 1);
		}
		if (p < 70) {
			return rollAttachmentOrFallback(random, 55, 40, 5, HEADSETS);
		}
		if (p < 82) {
			return pickStack(ALL_STIMS, random, 1);
		}
		if (p < 92) {
			return random.nextBoolean() ? pickStack(HEADSETS, random, 1) : pickStack(LIGHT_BACKPACKS, random, 1);
		}
		return rollAmmo(random, 60, 20, 20, 0, 8, 18);
	}

	/** Garages / auto-repair / storage lockups: repair plates, scrap metals, melee tools, attachments, ammo. */
	private static ItemStack rollGarage(Random random) {
		int p = random.nextInt(100);
		if (p < 28) {
			Item[] mats = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.GUNPOWDER, Items.COAL, Items.LEATHER};
			return new ItemStack(mats[random.nextInt(mats.length)], 2 + random.nextInt(6));
		}
		if (p < 46) {
			return pickStack(PLATES_REPAIR, random, 1);
		}
		if (p < 62) {
			return rollAttachmentOrFallback(random, 70, 28, 2, PLATES_REPAIR);
		}
		if (p < 74) {
			return pickStack(MELEE_WEAPONS, random, 1);
		}
		if (p < 85) {
			return rollAmmo(random, 60, 30, 10, 0, 8, 20);
		}
		if (p < 93) {
			return random.nextBoolean() ? pickStack(LIGHT_RIGS, random, 1) : pickStack(MEDIUM_BACKPACKS, random, 1);
		}
		return pickStack(ALL_FOOD, random, 1 + random.nextInt(2));
	}

	/** Urban frontline / trenches / barricades: combat ammo, tactical attachments, grenades, field meds, military armor. */
	private static ItemStack rollBattlefield(Random random) {
		int p = random.nextInt(100);
		if (p < 24) {
			return rollAmmo(random, 35, 10, 45, 10, 14, 30);
		}
		if (p < 44) {
			return rollAttachmentOrFallback(random, 30, 52, 18, PLATES_REPAIR);
		}
		if (p < 60) {
			return random.nextBoolean() ? pickStack(ARMOR_2_TO_5, random, 1) : pickStack(HELMETS_2_TO_5, random, 1);
		}
		if (p < 74) {
			return pickStack(ALL_GRENADES, random, 1 + random.nextInt(2));
		}
		if (p < 86) {
			return random.nextBoolean() ? pickStack(HIGH_TIER_MEDS, random, 1) : pickStack(ALL_STIMS, random, 1);
		}
		if (p < 94) {
			return random.nextBoolean() ? pickStack(ALL_RIGS, random, 1) : pickStack(PLATES_REPAIR, random, 1);
		}
		return random.nextBoolean() ? pickStack(HEADSETS, random, 1) : pickStack(TACTICAL_MASKS, random, 1);
	}

	private static ItemStack rollAttachmentOrFallback(
		Random random,
		int wCommon,
		int wTactical,
		int wElite,
		List<Item> fallbackPool
	) {
		ItemStack att = rollAttachment(random, wCommon, wTactical, wElite);
		if (!att.isEmpty()) {
			return att;
		}
		return pickStack(fallbackPool, random, 1);
	}

	private static ItemStack rollAmmo(
		Random random,
		int wFmj,
		int wHp,
		int wAp,
		int wIgn,
		int minCount,
		int maxCount
	) {
		List<TaCZCaliberCatalog.CaliberSpec> calibers = TaCZCaliberCatalog.getDefaultCalibers();
		TaCZCaliberCatalog.CaliberSpec spec = calibers.get(random.nextInt(calibers.size()));
		int total = Math.max(1, wFmj + wHp + wAp + wIgn);
		int roll = random.nextInt(total);
		AmmoTier tier;
		if (roll < wFmj) {
			tier = AmmoTier.FMJ;
		} else if (roll < wFmj + wHp) {
			tier = AmmoTier.HP;
		} else if (roll < wFmj + wHp + wAp) {
			tier = AmmoTier.AP;
		} else {
			tier = AmmoTier.IGN;
		}
		tier = spec.clampTier(tier);
		int count = minCount + random.nextInt(Math.max(1, maxCount - minCount + 1));
		return AmmoTier.createTaczAmmoStack(spec.ammoId(), tier, count);
	}

	private static ItemStack pickStack(List<Item> pool, Random random, int count) {
		if (pool.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Item item = pool.get(random.nextInt(pool.size()));
		int clampedCount = Math.min(count, item.getMaxCount());
		return new ItemStack(item, Math.max(1, clampedCount));
	}

	public static ItemStack pickRandomLightHelmet(Random random) {
		ensurePools();
		return pickStack(HELMETS_1_TO_3, random, 1);
	}

	public static ItemStack pickRandomLightArmor(Random random) {
		ensurePools();
		return pickStack(ARMOR_1_TO_3, random, 1);
	}

	public static ItemStack pickRandomScavRig(Random random) {
		ensurePools();
		return pickStack(LIGHT_RIGS.isEmpty() ? ALL_RIGS : LIGHT_RIGS, random, 1);
	}

	public static ItemStack pickRandomScavBackpack(Random random) {
		ensurePools();
		return pickStack(LIGHT_BACKPACKS.isEmpty() ? ALL_BACKPACKS : LIGHT_BACKPACKS, random, 1);
	}
}
