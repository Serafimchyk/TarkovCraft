package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.search.ItemRarityTier;

/**
 * Specifications for all TarkovCraft EFT medical items.
 */
public enum MedicalType {
	BANDAGE("bandage", 1, 1, CapacityMode.USES, 2, 0, true, false, false, 0.0f, 0, 0.0f, 0.05f, ItemRarityTier.COMMON, 15),
	CAT_TOURNIQUET("cat_tourniquet", 1, 1, CapacityMode.USES, 1, 0, false, true, false, 0.0f, 0, 0.0f, 0.12f, ItemRarityTier.COMMON, 20),
	SPLINT("splint", 1, 1, CapacityMode.USES, 1, 0, false, false, true, 0.0f, 0, 0.0f, 0.20f, ItemRarityTier.COMMON, 20),
	ALU_SPLINT("alu_splint", 1, 1, CapacityMode.USES, 5, 0, false, false, true, 0.0f, 0, 0.0f, 0.35f, ItemRarityTier.UNCOMMON, 20),
	ANALGIN("analgin", 1, 1, CapacityMode.USES, 4, 0, false, false, false, 0.0f, 1800, 1.5f, 0.06f, ItemRarityTier.COMMON, 15),
	AI2_MEDKIT("ai2_medkit", 1, 1, CapacityMode.HP_POOL, 100, 50, false, false, false, 0.0f, 0, 0.0f, 0.15f, ItemRarityTier.COMMON, 20),
	SALEWA_MEDKIT("salewa_medkit", 1, 2, CapacityMode.HP_POOL, 400, 85, true, true, false, 0.0f, 0, 0.0f, 0.60f, ItemRarityTier.UNCOMMON, 25),
	CMS_KIT("cms_kit", 2, 1, CapacityMode.USES, 5, 0, false, false, false, 0.55f, 0, 0.8f, 0.65f, ItemRarityTier.RARE, 40),
	SURV12_KIT("surv12_kit", 3, 1, CapacityMode.USES, 15, 0, false, false, true, 0.80f, 0, 1.0f, 1.00f, ItemRarityTier.EPIC, 45),
	// 12 additional EFT medical presets
	ARMY_BANDAGE("army_bandage", 1, 1, CapacityMode.USES, 2, 0, true, false, false, 0.0f, 0, 0.0f, 0.05f, ItemRarityTier.COMMON, 15),
	ESMARCH("esmarch", 1, 1, CapacityMode.USES, 1, 0, false, true, false, 0.0f, 0, 0.0f, 0.16f, ItemRarityTier.COMMON, 25),
	CALOK_B("calok_b", 1, 1, CapacityMode.USES, 3, 0, false, true, false, 0.0f, 0, 0.0f, 0.14f, ItemRarityTier.UNCOMMON, 20),
	TIGZRESQ_SPLINT("tigzresq_splint", 1, 1, CapacityMode.USES, 3, 0, false, false, true, 0.0f, 0, 0.0f, 0.25f, ItemRarityTier.UNCOMMON, 20),
	VASELINE("vaseline", 1, 1, CapacityMode.USES, 6, 0, false, false, false, 0.0f, 3600, 1.0f, 0.08f, ItemRarityTier.RARE, 25),
	GOLDEN_STAR("golden_star", 1, 1, CapacityMode.USES, 10, 0, false, false, false, 0.0f, 4200, 1.2f, 0.05f, ItemRarityTier.EPIC, 30),
	IBUPROFEN("ibuprofen", 1, 1, CapacityMode.USES, 15, 0, false, false, false, 0.0f, 3400, 1.0f, 0.07f, ItemRarityTier.RARE, 20),
	AUGMENTIN("augmentin", 1, 1, CapacityMode.USES, 1, 0, false, false, false, 0.0f, 2200, 0.5f, 0.08f, ItemRarityTier.UNCOMMON, 20),
	CAR_MEDKIT("car_medkit", 2, 1, CapacityMode.HP_POOL, 220, 70, true, false, false, 0.0f, 0, 0.0f, 0.35f, ItemRarityTier.COMMON, 20),
	IFAK("ifak", 1, 1, CapacityMode.HP_POOL, 300, 60, true, true, false, 0.0f, 0, 0.0f, 0.25f, ItemRarityTier.UNCOMMON, 25),
	AFAK("afak", 1, 1, CapacityMode.HP_POOL, 400, 75, true, true, false, 0.0f, 0, 0.0f, 0.30f, ItemRarityTier.RARE, 25),
	GRIZZLY("grizzly", 2, 2, CapacityMode.HP_POOL, 1800, 175, true, true, true, 0.0f, 0, 0.0f, 1.60f, ItemRarityTier.EPIC, 30);

	public static final int SALEWA_LIGHT_BLEED_COST = 45;
	public static final int SALEWA_HEAVY_BLEED_COST = 135;
	public static final int MEDKIT_FRACTURE_COST = 50;

	public enum CapacityMode {
		USES,
		HP_POOL
	}

	private final String id;
	private final int gridWidth;
	private final int gridHeight;
	private final CapacityMode capacityMode;
	private final int maxCapacity;
	private final int maxHealPerUse;
	private final boolean curesLightBleed;
	private final boolean curesHeavyBleed;
	private final boolean curesFracture;
	private final float surgeryRetention;
	private final int painkillerTicks;
	private final float exhaustionCost;
	private final float weightKg;
	private final ItemRarityTier rarityTier;
	private final int cooldownTicks;

	MedicalType(
		String id,
		int gridWidth,
		int gridHeight,
		CapacityMode capacityMode,
		int maxCapacity,
		int maxHealPerUse,
		boolean curesLightBleed,
		boolean curesHeavyBleed,
		boolean curesFracture,
		float surgeryRetention,
		int painkillerTicks,
		float exhaustionCost,
		float weightKg,
		ItemRarityTier rarityTier,
		int cooldownTicks
	) {
		this.id = id;
		this.gridWidth = gridWidth;
		this.gridHeight = gridHeight;
		this.capacityMode = capacityMode;
		this.maxCapacity = maxCapacity;
		this.maxHealPerUse = maxHealPerUse;
		this.curesLightBleed = curesLightBleed;
		this.curesHeavyBleed = curesHeavyBleed;
		this.curesFracture = curesFracture;
		this.surgeryRetention = surgeryRetention;
		this.painkillerTicks = painkillerTicks;
		this.exhaustionCost = exhaustionCost;
		this.weightKg = weightKg;
		this.rarityTier = rarityTier;
		this.cooldownTicks = cooldownTicks;
	}

	public String getId() {
		return id;
	}

	public ItemSize getGridSize() {
		return new ItemSize(gridWidth, gridHeight);
	}

	public CapacityMode getCapacityMode() {
		return capacityMode;
	}

	public int getMaxCapacity() {
		return maxCapacity;
	}

	public int getMaxHealPerUse() {
		return maxHealPerUse;
	}

	public boolean isCuresLightBleed() {
		return curesLightBleed;
	}

	public boolean isCuresHeavyBleed() {
		return curesHeavyBleed;
	}

	public boolean isCuresFracture() {
		return curesFracture;
	}

	public float getSurgeryRetention() {
		return surgeryRetention;
	}

	public boolean isSurgicalKit() {
		return surgeryRetention > 0.0f;
	}

	public int getPainkillerTicks() {
		return painkillerTicks;
	}

	public float getExhaustionCost() {
		return exhaustionCost;
	}

	public float getWeightKg() {
		return weightKg;
	}

	public ItemRarityTier getRarityTier() {
		return rarityTier;
	}

	public int getCooldownTicks() {
		return cooldownTicks;
	}

	public int getLightBleedCost() {
		return capacityMode == CapacityMode.HP_POOL ? SALEWA_LIGHT_BLEED_COST : 1;
	}

	public int getHeavyBleedCost() {
		return capacityMode == CapacityMode.HP_POOL ? SALEWA_HEAVY_BLEED_COST : 1;
	}

	public int getFractureCost() {
		return capacityMode == CapacityMode.HP_POOL ? MEDKIT_FRACTURE_COST : 1;
	}

	public boolean isPainkillerOnly() {
		return painkillerTicks > 0
			&& !curesLightBleed
			&& !curesHeavyBleed
			&& !curesFracture
			&& !isSurgicalKit()
			&& maxHealPerUse <= 0;
	}

	public static MedicalType fromPreset(String preset) {
		if (preset == null || preset.isBlank()) {
			return null;
		}
		for (MedicalType type : values()) {
			if (type.name().equalsIgnoreCase(preset) || type.id.equalsIgnoreCase(preset)) {
				return type;
			}
		}
		return null;
	}
}
