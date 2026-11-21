package com.tarkovcraft.search;

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
		Item item = stack.getItem();
		Identifier id = Registries.ITEM.getId(item);
		ItemRarityTier override = OVERRIDES.get(id);
		if (override != null) {
			return override;
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

	public static int getSearchTicks(ItemStack stack) {
		return getTier(stack).getSearchTicks();
	}
}
