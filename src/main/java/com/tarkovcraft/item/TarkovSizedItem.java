package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.search.ItemRarityTier;

/**
 * Common contract for TarkovCraft items that define grid dimensions, rarity tier, and optional weight.
 */
public interface TarkovSizedItem {
	ItemSize getStoredItemSize();

	ItemRarityTier getRarityTier();

	default float getWeightKg() {
		return -1.0f;
	}
}
