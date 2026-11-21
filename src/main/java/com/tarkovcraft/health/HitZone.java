package com.tarkovcraft.health;

/**
 * Granular Tarkov hit zones mapped onto BodyPart health pools.
 * Armor protection is evaluated strictly against the exact HitZone struck —
 * e.g. a helmet covering only HEAD_TOP does NOT protect HEAD_EARS or HEAD_EYES.
 */
public enum HitZone {
	HEAD_TOP("head_top", BodyPart.HEAD, false),
	HEAD_EYES("head_eyes", BodyPart.HEAD, true),
	HEAD_JAWS("head_jaws", BodyPart.HEAD, false),
	HEAD_EARS("head_ears", BodyPart.HEAD, false),
	NECK("neck", BodyPart.THORAX, false),
	THORAX("thorax", BodyPart.THORAX, false),
	ARMPIT_LEFT("armpit_left", BodyPart.THORAX, false),
	ARMPIT_RIGHT("armpit_right", BodyPart.THORAX, false),
	STOMACH("stomach", BodyPart.STOMACH, false),
	LEFT_ARM("left_arm", BodyPart.LEFT_ARM, false),
	RIGHT_ARM("right_arm", BodyPart.RIGHT_ARM, false),
	LEFT_LEG("left_leg", BodyPart.LEFT_LEG, false),
	RIGHT_LEG("right_leg", BodyPart.RIGHT_LEG, false);

	private final String id;
	private final BodyPart bodyPart;
	private final boolean unprotectedOneshot;

	HitZone(String id, BodyPart bodyPart, boolean unprotectedOneshot) {
		this.id = id;
		this.bodyPart = bodyPart;
		this.unprotectedOneshot = unprotectedOneshot;
	}

	public String getId() {
		return id;
	}

	public BodyPart getBodyPart() {
		return bodyPart;
	}

	/**
	 * True for HEAD_EYES ("Голова — глаза"): an unprotected hit is an instant lethal oneshot.
	 */
	public boolean isUnprotectedOneshot() {
		return unprotectedOneshot;
	}

	/**
	 * True for helmet dome/ear zones eligible for ballistic ricochet.
	 */
	public boolean isHelmetRicochetZone() {
		return this == HEAD_TOP || this == HEAD_EARS;
	}

	public String getTranslationKey() {
		return "hitzone.tarkovcraft." + id;
	}

	public static HitZone byId(String id) {
		for (HitZone zone : values()) {
			if (zone.id.equalsIgnoreCase(id)) {
				return zone;
			}
		}
		return THORAX;
	}
}
