package com.tarkovcraft.workbench;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovArmorRepairItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.registry.TarkovItemCatalog;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic recipe engine for the Tarkov Workbench:
 * - AMMO tab: scratch crafting & FMJ -> HP/AP/IGN reloading for any TaCZ ballistic caliber.
 * - ARMOR & GEAR tab: auto-generated recipes for all Helmets, Body Armor, Face Masks, Repair Kits, Rigs, and Backpacks.
 */
public final class TarkovWorkbenchRecipes {
	private static final Map<GearCategory, List<GearRecipe>> GEAR_CACHE = new EnumMap<>(GearCategory.class);
	public record Ingredient(Item item, int count) {
	}

	public record AmmoRecipe(
		String ammoId,
		AmmoTier tier,
		boolean upgradeFromFmj,
		int batchSize,
		int requiredFmjCount,
		List<Ingredient> materials
	) {
	}

	public enum GearCategory {
		HELMET("ui.tarkovcraft.wb_cat_helmets"),
		BODY_ARMOR("ui.tarkovcraft.wb_cat_armor"),
		FACE_MASK("ui.tarkovcraft.wb_cat_masks"),
		REPAIR_KITS("ui.tarkovcraft.wb_cat_repair_kits"),
		RIG("ui.tarkovcraft.wb_cat_rigs"),
		BACKPACK("ui.tarkovcraft.wb_cat_backpacks");

		private final String translationKey;

		GearCategory(String translationKey) {
			this.translationKey = translationKey;
		}

		public String getTranslationKey() {
			return translationKey;
		}
	}

	public record GearRecipe(
		Item resultItem,
		GearCategory category,
		List<Ingredient> materials
	) {
	}

	private TarkovWorkbenchRecipes() {
	}

	public static AmmoRecipe getAmmoRecipe(String ammoId, AmmoTier tier, boolean upgradeFromFmj) {
		TaCZCaliberCatalog.CaliberSpec spec = TaCZCaliberCatalog.getSpecOrDynamic(ammoId);
		int m = spec.group().getMaterialMultiplier();
		int batch = spec.getBatchSize();
		AmmoTier safeTier = tier != null ? tier : AmmoTier.FMJ;
		boolean isUpgrade = upgradeFromFmj && safeTier != AmmoTier.FMJ;

		List<Ingredient> materials = isUpgrade ? switch (safeTier) {
			case HP -> List.of(material("brass_casings", m), material("gunpowder_hawk", m));
			case AP -> List.of(material("ceramic_plate_raw", 2 * m), material("gunpowder_hawk", m));
			case IGN -> List.of(material("military_electronics", m), material("gunpowder_hawk", 2 * m));
			case FMJ -> throw new IllegalStateException("FMJ cannot be upgraded from FMJ");
		} : switch (safeTier) {
			case FMJ -> List.of(material("brass_casings", 4 * m), material("gunpowder_hawk", 2 * m));
			case HP -> List.of(material("brass_casings", 5 * m), material("gunpowder_hawk", 2 * m));
			case AP -> List.of(material("brass_casings", 4 * m), material("ceramic_plate_raw", 2 * m),
				material("gunpowder_hawk", 3 * m));
			case IGN -> List.of(material("brass_casings", 4 * m), material("military_electronics", m),
				material("gunpowder_hawk", 4 * m));
		};
		return new AmmoRecipe(spec.ammoId(), safeTier, isUpgrade, batch, isUpgrade ? batch : 0, materials);
	}

	public static boolean isCraftableGear(Item item) {
		if (item == null || item == Items.AIR) {
			return false;
		}
		Identifier id = Registries.ITEM.getId(item);
		if (!TarkovCraftMod.MOD_ID.equals(id.getNamespace())) {
			return false;
		}
		return item instanceof TarkovArmorItem
			|| item instanceof TarkovArmorRepairItem
			|| item instanceof TacticalRigItem
			|| item instanceof BackpackItem;
	}

	public static synchronized List<GearRecipe> getGearRecipes(GearCategory category) {
		List<GearRecipe> cached = GEAR_CACHE.get(category);
		if (cached != null) {
			return cached;
		}
		List<Item> candidates = new ArrayList<>();
		switch (category) {
			case HELMET -> {
				candidates.add(ModItems.SSH68_HELMET);
				candidates.add(ModItems.RATNIK_6B47_HELMET);
				candidates.add(ModItems.ALTYN_HELMET);
				for (Item it : TarkovItemCatalog.getArmorItems()) {
					if (it instanceof TarkovArmorItem armor && armor.getTargetSlot() == EquipmentSlotType.HELMET) {
						candidates.add(it);
					}
				}
			}
			case BODY_ARMOR -> {
				candidates.add(ModItems.PACA_ARMOR);
				candidates.add(ModItems.PRESS_ARMOR);
				candidates.add(ModItems.ZABRALO_ARMOR);
				for (Item it : TarkovItemCatalog.getArmorItems()) {
					if (it instanceof TarkovArmorItem armor && armor.getTargetSlot() == EquipmentSlotType.BODY_ARMOR) {
						candidates.add(it);
					}
				}
			}
			case FACE_MASK -> {
				candidates.add(ModItems.BALACLAVA_MASK);
				candidates.add(ModItems.DEATH_SHADOW_MASK);
				for (Item it : TarkovItemCatalog.getArmorItems()) {
					if (it instanceof TarkovArmorItem armor && armor.getTargetSlot() == EquipmentSlotType.FACE_MASK) {
						candidates.add(it);
					}
				}
			}
			case REPAIR_KITS -> {
				for (Item it : TarkovItemCatalog.getArmorItems()) {
					if (it instanceof TarkovArmorRepairItem) {
						candidates.add(it);
					}
				}
			}
			case RIG -> {
				candidates.add(ModItems.SCAV_VEST);
				candidates.add(ModItems.BANK_ROBBER_RIG);
				candidates.add(ModItems.TRITON_RIG);
				candidates.addAll(TarkovItemCatalog.getRigItems());
			}
			case BACKPACK -> {
				candidates.add(ModItems.SLING_BACKPACK);
				candidates.add(ModItems.BERKUT_BACKPACK);
				candidates.add(ModItems.TRIZIP_BACKPACK);
				candidates.addAll(TarkovItemCatalog.getBackpackItems());
			}
		}

		List<GearRecipe> out = new ArrayList<>(candidates.size());
		for (Item item : candidates) {
			out.add(new GearRecipe(item, category, computeGearIngredients(item)));
		}
		List<GearRecipe> immutable = Collections.unmodifiableList(out);
		GEAR_CACHE.put(category, immutable);
		return immutable;
	}

	public static List<Ingredient> computeGearIngredients(Item item) {
		List<Ingredient> mats = new ArrayList<>();
		if (item instanceof TarkovArmorItem armor) {
			double s = switch (armor.getTargetSlot()) {
				case BODY_ARMOR -> 1.5;
				case HELMET -> 1.0;
				case FACE_MASK -> 0.75;
				default -> 1.0;
			};
			int armorClass = armor.getArmorClass();
			mats.add(material("kevlar_fabric", ceilMul(Math.min(4, armorClass + 2), s)));
			if (armorClass > 0) {
				mats.add(material("weapon_parts", ceilMul(2 * armorClass, s)));
			}
			if (armorClass >= 3) {
				mats.add(material("ceramic_plate_raw", ceilMul(2 * (armorClass - 2), s)));
			}
			if (armorClass >= 5 || armor.isGasProtection()) {
				mats.add(material("military_electronics", ceilMul(Math.max(1, armorClass - 4), s)));
			}
		} else if (item instanceof TarkovArmorRepairItem repair) {
			int rep = repair.getRepairAmount();
			mats.add(material("weapon_parts", Math.max(2, rep / 10)));
			mats.add(material("kevlar_fabric", Math.max(2, rep / 15)));
			if (rep >= 40) {
				mats.add(material("military_electronics", 1));
			} else if (rep >= 25) {
				mats.add(material("ceramic_plate_raw", 1));
			}
		} else if (item instanceof TacticalRigItem rig) {
			int totalCells = rig.getCompartmentLayouts().stream().mapToInt(sz -> sz.width() * sz.height()).sum();
			mats.add(material("kevlar_fabric", Math.max(3, totalCells / 2)));
			mats.add(material("weapon_parts", Math.max(1, totalCells / 8)));
			if (totalCells >= 18) {
				mats.add(material("ceramic_plate_raw", 2));
			}
		} else if (item instanceof BackpackItem bp) {
			int cells = bp.getGridWidth() * bp.getGridHeight();
			mats.add(material("kevlar_fabric", Math.max(4, cells / 3)));
			mats.add(material("weapon_parts", Math.max(1, cells / 12)));
			if (cells >= 30) {
				mats.add(material("ceramic_plate_raw", 2));
			}
		}
		return List.copyOf(mats);
	}

	private static Ingredient material(String id, int count) {
		Identifier itemId = new Identifier(TarkovCraftMod.MOD_ID, id);
		Item item = Registries.ITEM.get(itemId);
		if (item == Items.AIR) {
			throw new IllegalStateException("Missing workbench material " + itemId);
		}
		return new Ingredient(item, count);
	}

	private static int ceilMul(int base, double scale) {
		return Math.max(1, (int) Math.ceil(base * scale));
	}
}
