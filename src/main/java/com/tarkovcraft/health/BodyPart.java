package com.tarkovcraft.health;

/**
 * The 7 Tarkov anatomical health pools (Total = 435 HP).
 * Reducing HEAD or THORAX to 0 HP is immediately fatal.
 */
public enum BodyPart {
	HEAD("head", 35.0f, true),
	THORAX("thorax", 85.0f, true),
	STOMACH("stomach", 70.0f, false),
	LEFT_ARM("left_arm", 60.0f, false),
	RIGHT_ARM("right_arm", 60.0f, false),
	LEFT_LEG("left_leg", 65.0f, false),
	RIGHT_LEG("right_leg", 65.0f, false);

	public static final float TOTAL_MAX_HP = 435.0f;

	private final String id;
	private final float maxHp;
	private final boolean vital;

	BodyPart(String id, float maxHp, boolean vital) {
		this.id = id;
		this.maxHp = maxHp;
		this.vital = vital;
	}

	public String getId() {
		return id;
	}

	public float getMaxHp() {
		return maxHp;
	}

	public boolean isVital() {
		return vital;
	}

	public String getTranslationKey() {
		return "bodypart.tarkovcraft." + id;
	}

	public static BodyPart byId(String id) {
		for (BodyPart part : values()) {
			if (part.id.equalsIgnoreCase(id)) {
				return part;
			}
		}
		return THORAX;
	}
}
