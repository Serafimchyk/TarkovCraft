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
 * Rich catalog-driven loot generator spanning all 701 TarkovCraft items and tiered TaCZ ammunition.
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
			String path = Registries.ITEM.getId(item).getPath().toLowerCase(Locale.ROOT);
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

	public static void populateLootGrid(GridContainer grid, TarkovLootPool pool, Random random) {
		if (grid == null || pool == null || random == null) {
			return;
		}
		ensurePools();

		// Guarantee / chance for real TaCZ firearms + matching tiered ammo in military crates and airdrops
		if (pool == TarkovLootPool.MILITARY && random.nextFloat() < 0.45f) {
			GunPreset gun = MILITARY_GUNS.get(random.nextInt(MILITARY_GUNS.size()));
			AmmoTier tier = random.nextFloat() < 0.35f ? AmmoTier.AP : AmmoTier.FMJ;
			ItemStack gunStack = createTaczGunStack(gun, tier, random);
			if (!gunStack.isEmpty()) {
				grid.tryAutoPlace(gunStack);
				grid.tryAutoPlace(AmmoTier.createTaczAmmoStack(gun.ammoId(), tier, Math.min(30, Math.max(10, gun.magSize()))));
			}
		} else if (pool == TarkovLootPool.AIRDROP) {
			int gunCount = random.nextBoolean() ? 2 : 1;
			for (int g = 0; g < gunCount; g++) {
				GunPreset gun = AIRDROP_GUNS.get(random.nextInt(AIRDROP_GUNS.size()));
				AmmoTier tier = random.nextFloat() < 0.45f ? AmmoTier.IGN : AmmoTier.AP;
				ItemStack gunStack = createTaczGunStack(gun, tier, random);
				if (!gunStack.isEmpty()) {
					grid.tryAutoPlace(gunStack);
					grid.tryAutoPlace(AmmoTier.createTaczAmmoStack(gun.ammoId(), tier, Math.min(40, Math.max(15, gun.magSize()))));
				}
			}
		}

		int baseRolls = switch (pool) {
			case MILITARY, MEDICAL -> 4 + random.nextInt(4);
			case CIVILIAN -> 3 + random.nextInt(4);
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

	public static ItemStack rollItem(TarkovLootPool pool, Random random) {
		ensurePools();
		return switch (pool) {
			case MILITARY -> rollMilitary(random);
			case MEDICAL -> rollMedical(random);
			case CIVILIAN -> rollCivilian(random);
			case AIRDROP -> rollAirdrop(random);
			case SCAV_POCKET -> rollScavPocket(random);
		};
	}

	private static ItemStack rollMilitary(Random random) {
		int p = random.nextInt(100);
		if (p < 30) {
			return rollAmmo(random, 45, 20, 30, 5, 12, 30);
		}
		if (p < 50) {
			return random.nextBoolean() ? pickStack(ARMOR_2_TO_5, random, 1) : pickStack(HELMETS_2_TO_5, random, 1);
		}
		if (p < 62) {
			return pickStack(PLATES_REPAIR, random, 1);
		}
		if (p < 76) {
			return random.nextBoolean() ? pickStack(ALL_RIGS, random, 1) : pickStack(MEDIUM_BACKPACKS, random, 1);
		}
		if (p < 88) {
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
		if (p < 57) {
			return pickStack(COSMETIC_MASKS, random, 1);
		}
		if (p < 69) {
			return random.nextBoolean() ? pickStack(LIGHT_RIGS, random, 1) : pickStack(LIGHT_BACKPACKS, random, 1);
		}
		if (p < 84) {
			return pickStack(BASIC_MEDS, random, 1);
		}
		if (p < 96) {
			Item[] mats = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.LEATHER, Items.GUNPOWDER, Items.GOLD_INGOT};
			return new ItemStack(mats[random.nextInt(mats.length)], 2 + random.nextInt(5));
		}
		return pickStack(SMALL_CASES.isEmpty() ? ALL_CASES : SMALL_CASES, random, 1);
	}

	private static ItemStack rollAirdrop(Random random) {
		int p = random.nextInt(100);
		if (p < 24) {
			return random.nextBoolean() ? pickStack(ARMOR_4_TO_6, random, 1) : pickStack(HELMETS_4_TO_6, random, 1);
		}
		if (p < 46) {
			return rollAmmo(random, 0, 0, 55, 45, 20, 40);
		}
		if (p < 60) {
			return pickStack(TOP_STIMS.isEmpty() ? ALL_STIMS : TOP_STIMS, random, 1);
		}
		if (p < 74) {
			return random.nextBoolean() ? pickStack(LARGE_BACKPACKS, random, 1) : pickStack(LARGE_RIGS, random, 1);
		}
		if (p < 84) {
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
		if (p < 56) {
			return pickStack(ONE_BY_ONE_MEDS.isEmpty() ? BASIC_MEDS : ONE_BY_ONE_MEDS, random, 1);
		}
		if (p < 76) {
			return rollAmmo(random, 70, 30, 0, 0, 8, 16);
		}
		if (p < 86) {
			return pickStack(ONE_BY_ONE_STIMS.isEmpty() ? ALL_STIMS : ONE_BY_ONE_STIMS, random, 1);
		}
		if (p < 94) {
			return pickStack(ONE_BY_ONE_GRENADES.isEmpty() ? ALL_GRENADES : ONE_BY_ONE_GRENADES, random, 1);
		}
		Item[] mats = {Items.IRON_INGOT, Items.COPPER_INGOT, Items.GUNPOWDER};
		return new ItemStack(mats[random.nextInt(mats.length)], 1 + random.nextInt(3));
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
