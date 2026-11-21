package com.tarkovcraft.health;

import com.tarkovcraft.item.MedicalType;
import com.tarkovcraft.item.TarkovMedicalItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * Pure common-side medical application & target priority logic.
 */
public final class MedicalHelper {
	private static final BodyPart[] BLEED_PRIORITY_ORDER = {
		BodyPart.THORAX, BodyPart.HEAD, BodyPart.STOMACH,
		BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG, BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM
	};
	private static final BodyPart[] LIMB_PRIORITY_ORDER = {
		BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG, BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM
	};
	private static final BodyPart[] SURGERY_PRIORITY_ORDER = {
		BodyPart.STOMACH, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG, BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM
	};

	private MedicalHelper() {
	}

	public record ApplyResult(
		boolean applied,
		@Nullable BodyPart targetPart,
		float exhaustionToAdd,
		boolean depleted
	) {
	}

	public static boolean canApplyToPart(BodyPartHealth health, ItemStack stack, BodyPart part) {
		if (health == null || stack == null || stack.isEmpty() || part == null) {
			return false;
		}
		if (!(stack.getItem() instanceof TarkovMedicalItem med)) {
			return false;
		}
		int remaining = TarkovMedicalItem.getRemaining(stack);
		if (remaining <= 0) {
			return false;
		}
		MedicalType type = med.getMedicalType();
		if (type.isPainkillerOnly()) {
			return health.getPainkillerTicks() < type.getPainkillerTicks() - 20;
		}
		if (type.isCuresHeavyBleed() && health.hasHeavyBleed(part) && remaining >= type.getHeavyBleedCost()) {
			return true;
		}
		if (type.isCuresLightBleed() && health.hasLightBleed(part) && remaining >= type.getLightBleedCost()) {
			return true;
		}
		if (type.isCuresFracture() && health.hasFracture(part) && remaining >= type.getFractureCost()) {
			return true;
		}
		if (type.isSurgicalKit() && !part.isVital() && health.isBlacked(part)) {
			return true;
		}
		return type.getMaxHealPerUse() > 0
			&& !health.isBlacked(part)
			&& health.getHp(part) + 0.01f < health.getEffectiveMaxHp(part);
	}

	@Nullable
	public static BodyPart selectAutoTarget(BodyPartHealth health, ItemStack stack) {
		if (health == null || stack == null || stack.isEmpty() || !(stack.getItem() instanceof TarkovMedicalItem med)) {
			return null;
		}
		int remaining = TarkovMedicalItem.getRemaining(stack);
		if (remaining <= 0) {
			return null;
		}
		MedicalType type = med.getMedicalType();

		if (type.isPainkillerOnly()) {
			return health.getPainkillerTicks() < type.getPainkillerTicks() - 20 ? BodyPart.HEAD : null;
		}

		if (type.isCuresHeavyBleed() && remaining >= type.getHeavyBleedCost()) {
			for (BodyPart part : BLEED_PRIORITY_ORDER) {
				if (health.hasHeavyBleed(part)) {
					return part;
				}
			}
		}

		if (type.isCuresLightBleed() && remaining >= type.getLightBleedCost()) {
			for (BodyPart part : BLEED_PRIORITY_ORDER) {
				if (health.hasLightBleed(part)) {
					return part;
				}
			}
		}

		if (type.isSurgicalKit()) {
			for (BodyPart part : SURGERY_PRIORITY_ORDER) {
				if (health.isBlacked(part)) {
					return part;
				}
			}
		}

		if (type.isCuresFracture() && remaining >= type.getFractureCost()) {
			for (BodyPart part : LIMB_PRIORITY_ORDER) {
				if (health.hasFracture(part)) {
					return part;
				}
			}
		}

		if (type.getMaxHealPerUse() <= 0) {
			return null;
		}

		// HP healing: prioritize vital parts (HEAD, THORAX) by lowest ratio, then non-vital
		BodyPart bestVital = null;
		float bestVitalRatio = 2.0f;
		BodyPart bestOther = null;
		float bestOtherRatio = 2.0f;

		for (BodyPart part : BodyPart.values()) {
			if (!health.isBlacked(part) && health.getHp(part) + 0.01f < health.getEffectiveMaxHp(part)) {
				float ratio = health.getRatio(part);
				if (part.isVital()) {
					if (ratio < bestVitalRatio) {
						bestVitalRatio = ratio;
						bestVital = part;
					}
				} else {
					if (ratio < bestOtherRatio) {
						bestOtherRatio = ratio;
						bestOther = part;
					}
				}
			}
		}
		return bestVital != null ? bestVital : bestOther;
	}

	public static ApplyResult applyMedical(
		BodyPartHealth health,
		ItemStack stack,
		@Nullable BodyPart requestedPart,
		boolean infiniteCreative
	) {
		if (health == null || stack == null || stack.isEmpty() || !(stack.getItem() instanceof TarkovMedicalItem med)) {
			return new ApplyResult(false, null, 0.0f, false);
		}
		BodyPart target = requestedPart != null ? requestedPart : selectAutoTarget(health, stack);
		if (target == null || !canApplyToPart(health, stack, target)) {
			return new ApplyResult(false, null, 0.0f, false);
		}

		MedicalType type = med.getMedicalType();
		int remaining = TarkovMedicalItem.getRemaining(stack);
		int cost = 1;

		if (type.isPainkillerOnly()) {
			health.setPainkillerTicks(Math.max(health.getPainkillerTicks(), type.getPainkillerTicks()));
		} else if (type.isCuresHeavyBleed() && health.hasHeavyBleed(target) && remaining >= type.getHeavyBleedCost()) {
			health.removeInjury(target, InjuryType.HEAVY_BLEED);
			cost = type.getHeavyBleedCost();
		} else if (type.isCuresLightBleed() && health.hasLightBleed(target) && remaining >= type.getLightBleedCost()) {
			health.removeInjury(target, InjuryType.LIGHT_BLEED);
			cost = type.getLightBleedCost();
		} else if (type.isSurgicalKit() && !target.isVital() && health.isBlacked(target)) {
			float newEff = Math.min(
				health.getEffectiveMaxHp(target),
				Math.max(1.0f, Math.round(target.getMaxHp() * type.getSurgeryRetention()))
			);
			health.setEffectiveMaxHp(target, newEff);
			health.setHp(target, 1.0f);
			if (type.isCuresFracture()) {
				health.removeInjury(target, InjuryType.FRACTURE);
			}
		} else if (type.isCuresFracture() && health.hasFracture(target) && remaining >= type.getFractureCost()) {
			health.removeInjury(target, InjuryType.FRACTURE);
			cost = type.getFractureCost();
		} else if (type.getMaxHealPerUse() > 0 && !health.isBlacked(target)
			&& health.getHp(target) + 0.01f < health.getEffectiveMaxHp(target)) {
			float missing = health.getEffectiveMaxHp(target) - health.getHp(target);
			float heal = Math.min(missing, Math.min(type.getMaxHealPerUse(), remaining));
			health.setHp(target, health.getHp(target) + heal);
			cost = Math.min(remaining, Math.max(1, Math.round(heal)));
		} else {
			return new ApplyResult(false, null, 0.0f, false);
		}

		if (type.getPainkillerTicks() > 0 && !type.isPainkillerOnly()) {
			health.setPainkillerTicks(Math.max(health.getPainkillerTicks(), type.getPainkillerTicks()));
		}

		if (!infiniteCreative) {
			TarkovMedicalItem.setRemaining(stack, remaining - cost);
		}
		return new ApplyResult(true, target, type.getExhaustionCost(), stack.isEmpty());
	}

	public static void playMedicalSound(ServerPlayerEntity player, MedicalType type) {
		if (type.isSurgicalKit()) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 0.7f, 0.95f);
		} else if (type.getPainkillerTicks() > 0) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.7f, 1.3f);
		} else if (type.getCapacityMode() == MedicalType.CapacityMode.HP_POOL) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_HONEYCOMB_WAX_ON, SoundCategory.PLAYERS, 0.75f, 1.15f);
		} else if (type.isCuresFracture()) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.8f, 1.2f);
		} else {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 0.8f, 1.1f);
		}
	}
}
