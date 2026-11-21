package com.tarkovcraft.item;

/**
 * Ballistic material classification for TarkovCraft armor.
 * Controls durability wear rate (destructibility) and blunt trauma percentage when a round is stopped.
 */
public enum ArmorMaterialType {
	COSMETIC("cosmetic", 1.0f, 1.0f),
	ARAMID("aramid", 0.55f, 0.12f),
	UHMWPE("uhmwpe", 0.45f, 0.11f),
	COMBINED("combined", 0.65f, 0.13f),
	TITANIUM("titanium", 0.70f, 0.14f),
	STEEL("steel", 0.85f, 0.15f);

	private final String id;
	private final float destructibility;
	private final float bluntThroughput;

	ArmorMaterialType(String id, float destructibility, float bluntThroughput) {
		this.id = id;
		this.destructibility = destructibility;
		this.bluntThroughput = bluntThroughput;
	}

	public String getId() {
		return id;
	}

	public float getDestructibility() {
		return destructibility;
	}

	public float getBluntThroughput() {
		return bluntThroughput;
	}

	public String getTranslationKey() {
		return "armormaterial.tarkovcraft." + id;
	}
}
