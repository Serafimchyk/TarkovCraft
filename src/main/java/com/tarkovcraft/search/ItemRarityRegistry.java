package com.tarkovcraft.search;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovSizedItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves search duration tier for any ItemStack based on item rarity.
 */
public final class ItemRarityRegistry {
	private static final Map<Identifier, ItemRarityTier> OVERRIDES = new HashMap<>();

	static {
		OVERRIDES.put(new Identifier("tacz", "modern_kinetic_gun"), ItemRarityTier.RARE);
		OVERRIDES.put(new Identifier("tacz", "ammo_box"), ItemRarityTier.UNCOMMON);
		OVERRIDES.put(new Identifier("tacz", "attachment"), ItemRarityTier.UNCOMMON);
	}

	private ItemRarityRegistry() {
	}

	public static void register(Identifier itemId, ItemRarityTier tier) {
		OVERRIDES.put(itemId, tier);
	}

	public static ItemRarityTier getTier(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ItemRarityTier.COMMON;
		}
		if (AmmoTier.isTaczAmmoStack(stack)) {
			return switch (AmmoTier.getFromAmmoStack(stack)) {
				case IGN -> ItemRarityTier.EPIC;
				case AP -> ItemRarityTier.RARE;
				case FMJ -> ItemRarityTier.UNCOMMON;
				case HP -> ItemRarityTier.COMMON;
			};
		}
		Item item = stack.getItem();
		Identifier id = Registries.ITEM.getId(item);
		ItemRarityTier override = OVERRIDES.get(id);
		if (override != null) {
			return override;
		}
		if (item instanceof TarkovArmorItem armor) {
			ItemRarityTier base = armor.getRarityTier();
			int ac = armor.getArmorClass();
			if (ac >= 5) {
				return maxTier(base, ItemRarityTier.EPIC);
			}
			if (ac == 4) {
				return maxTier(base, ItemRarityTier.RARE);
			}
			if (ac >= 2) {
				return maxTier(base, ItemRarityTier.UNCOMMON);
			}
			return base;
		}
		if (item instanceof TarkovCaseItem caseItem) {
			int slots = caseItem.getGridWidth() * caseItem.getGridHeight();
			if (slots >= 25) {
				return maxTier(caseItem.getRarityTier(), ItemRarityTier.EPIC);
			}
			if (slots >= 9) {
				return maxTier(caseItem.getRarityTier(), ItemRarityTier.RARE);
			}
			return caseItem.getRarityTier();
		}
		if (item instanceof TarkovSizedItem sized) {
			return sized.getRarityTier();
		}
		Rarity rarity = stack.getRarity();
		return switch (rarity) {
			case COMMON -> ItemRarityTier.COMMON;
			case UNCOMMON -> ItemRarityTier.UNCOMMON;
			case RARE -> ItemRarityTier.RARE;
			case EPIC -> ItemRarityTier.EPIC;
		};
	}

	private static ItemRarityTier maxTier(ItemRarityTier a, ItemRarityTier b) {
		return a.ordinal() >= b.ordinal() ? a : b;
	}

	public static int getSearchTicks(ItemStack stack) {
		int base = getTier(stack).getSearchTicks();
		return Math.max(4, Math.round(base * TarkovConfig.get().searchTimeMultiplier));
	}
}
