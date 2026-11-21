package com.tarkovcraft.health;

/**
 * Anatomical trauma statuses tracked per BodyPart.
 */
public enum InjuryType {
	LIGHT_BLEED("light_bleed", 1 << 0),
	HEAVY_BLEED("heavy_bleed", 1 << 1),
	FRACTURE("fracture", 1 << 2);

	private final String id;
	private final int bitMask;

	InjuryType(String id, int bitMask) {
		this.id = id;
		this.bitMask = bitMask;
	}

	public String getId() {
		return id;
	}

	public int getBitMask() {
		return bitMask;
	}

	public String getTranslationKey() {
		return "injury.tarkovcraft." + id;
	}
}
