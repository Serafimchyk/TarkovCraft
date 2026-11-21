package com.tarkovcraft.search;

public enum ItemRarityTier {
	COMMON(10),    // 0.5s
	UNCOMMON(20),  // 1.0s
	RARE(36),      // 1.8s
	EPIC(56);      // 2.8s

	private final int searchTicks;

	ItemRarityTier(int searchTicks) {
		this.searchTicks = searchTicks;
	}

	public int getSearchTicks() {
		return searchTicks;
	}
}
