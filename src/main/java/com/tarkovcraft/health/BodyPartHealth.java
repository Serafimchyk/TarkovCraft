package com.tarkovcraft.health;

import com.tarkovcraft.loadout.PlayerLoadout;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-player Tarkov body-part health pool (435 base HP total across 7 parts) with:
 * - Unprotected HEAD_EYES ("Голова — глаза") -> instant fatal oneshot.
 * - Limb hits reduce limb HP to 0 first, then overflow directly to THORAX.
 * - Injuries: LIGHT_BLEED, HEAVY_BLEED, FRACTURE (limbs only) + effectiveMaxHp surgery limits.
 * - Painkiller duration, harsh dual-leg fall damage with weight scaling, and server tick penalties.
 */
public final class BodyPartHealth {
	private static final int MAX_HIT_LOG = 6;
	public static final int MAX_PAINKILLER_TICKS = 6000;

	private final Map<BodyPart, Float> hpMap = new EnumMap<>(BodyPart.class);
	private final Map<BodyPart, Float> effectiveMaxHpMap = new EnumMap<>(BodyPart.class);
	private final Map<BodyPart, EnumSet<InjuryType>> injuries = new EnumMap<>(BodyPart.class);
	private final Deque<DamageReport> recentHits = new ArrayDeque<>();
	@Nullable
	private DamageReport lastFatalReport;

	private int painkillerTicks = 0;
	private int bleedTickCounter = 0;
	private int sprintPainTickCounter = 0;
	private int stomachDrainTickCounter = 0;

	public record TickResult(
		boolean stateChanged,
		boolean killedByBloodLoss,
		boolean bleedDamaged,
		boolean heavyBleedActive,
		boolean sprintPainTriggered,
		int impairedLegs,
		int impairedArms,
		boolean stomachBlacked,
		boolean painkillerActive,
		float exhaustionToAdd
	) {
	}

	public BodyPartHealth() {
		resetFull();
	}

	public void resetFull() {
		for (BodyPart part : BodyPart.values()) {
			effectiveMaxHpMap.put(part, part.getMaxHp());
			hpMap.put(part, part.getMaxHp());
			injuries.put(part, EnumSet.noneOf(InjuryType.class));
		}
		painkillerTicks = 0;
		bleedTickCounter = 0;
		sprintPainTickCounter = 0;
		stomachDrainTickCounter = 0;
		recentHits.clear();
		lastFatalReport = null;
	}

	public float getHp(BodyPart part) {
		return hpMap.getOrDefault(part, getEffectiveMaxHp(part));
	}

	public float getEffectiveMaxHp(BodyPart part) {
		return effectiveMaxHpMap.getOrDefault(part, part.getMaxHp());
	}

	public void setEffectiveMaxHp(BodyPart part, float value) {
		float clamped = Math.max(1.0f, Math.min(part.getMaxHp(), value));
		effectiveMaxHpMap.put(part, clamped);
		if (getHp(part) > clamped) {
			hpMap.put(part, clamped);
		}
	}

	public float getTotalEffectiveMaxHp() {
		float sum = 0.0f;
		for (BodyPart part : BodyPart.values()) {
			sum += getEffectiveMaxHp(part);
		}
		return sum;
	}

	public float getRatio(BodyPart part) {
		float eff = Math.max(1.0f, getEffectiveMaxHp(part));
		return Math.max(0.0f, Math.min(1.0f, getHp(part) / eff));
	}

	public float getBaseRatio(BodyPart part) {
		return Math.max(0.0f, Math.min(1.0f, getHp(part) / part.getMaxHp()));
	}

	public float getEffectiveMaxRatio(BodyPart part) {
		return Math.max(0.0f, Math.min(1.0f, getEffectiveMaxHp(part) / part.getMaxHp()));
	}

	public void setHp(BodyPart part, float value) {
		float effMax = getEffectiveMaxHp(part);
		hpMap.put(part, Math.max(0.0f, Math.min(effMax, value)));
	}

	public float getTotalHp() {
		float sum = 0.0f;
		for (BodyPart part : BodyPart.values()) {
			sum += getHp(part);
		}
		return sum;
	}

	public float getTotalRatio() {
		return Math.max(0.0f, Math.min(1.0f, getTotalHp() / BodyPart.TOTAL_MAX_HP));
	}

	public boolean isDead() {
		return getHp(BodyPart.HEAD) <= 0.001f || getHp(BodyPart.THORAX) <= 0.001f;
	}

	public boolean isBlacked(BodyPart part) {
		return getHp(part) <= 0.001f;
	}

	public static boolean isLimb(BodyPart part) {
		return part == BodyPart.LEFT_ARM
			|| part == BodyPart.RIGHT_ARM
			|| part == BodyPart.LEFT_LEG
			|| part == BodyPart.RIGHT_LEG;
	}

	public boolean hasInjury(BodyPart part, InjuryType type) {
		EnumSet<InjuryType> set = injuries.get(part);
		return set != null && set.contains(type);
	}

	public void addInjury(BodyPart part, InjuryType type) {
		if (part == null || type == null) return;
		if (type == InjuryType.FRACTURE && !isLimb(part)) {
			return;
		}
		EnumSet<InjuryType> set = injuries.computeIfAbsent(part, p -> EnumSet.noneOf(InjuryType.class));
		if (type == InjuryType.HEAVY_BLEED) {
			set.remove(InjuryType.LIGHT_BLEED);
			set.add(InjuryType.HEAVY_BLEED);
		} else if (type == InjuryType.LIGHT_BLEED) {
			if (!set.contains(InjuryType.HEAVY_BLEED)) {
				set.add(InjuryType.LIGHT_BLEED);
			}
		} else {
			set.add(type);
		}
	}

	public boolean removeInjury(BodyPart part, InjuryType type) {
		EnumSet<InjuryType> set = injuries.get(part);
		return set != null && set.remove(type);
	}

	public EnumSet<InjuryType> getInjuries(BodyPart part) {
		EnumSet<InjuryType> set = injuries.get(part);
		return (set == null || set.isEmpty()) ? EnumSet.noneOf(InjuryType.class) : EnumSet.copyOf(set);
	}

	public boolean hasLightBleed(BodyPart part) {
		return hasInjury(part, InjuryType.LIGHT_BLEED);
	}

	public boolean hasHeavyBleed(BodyPart part) {
		return hasInjury(part, InjuryType.HEAVY_BLEED);
	}

	public boolean hasFracture(BodyPart part) {
		return hasInjury(part, InjuryType.FRACTURE);
	}

	public int countInjuries(InjuryType type) {
		int count = 0;
		for (BodyPart part : BodyPart.values()) {
			if (hasInjury(part, type)) {
				count++;
			}
		}
		return count;
	}

	public boolean hasAnyBleed() {
		return countInjuries(InjuryType.LIGHT_BLEED) > 0 || countInjuries(InjuryType.HEAVY_BLEED) > 0;
	}

	public int getImpairedLegCount() {
		int count = 0;
		if (isBlacked(BodyPart.LEFT_LEG) || hasFracture(BodyPart.LEFT_LEG)) count++;
		if (isBlacked(BodyPart.RIGHT_LEG) || hasFracture(BodyPart.RIGHT_LEG)) count++;
		return count;
	}

	public int getImpairedArmCount() {
		int count = 0;
		if (isBlacked(BodyPart.LEFT_ARM) || hasFracture(BodyPart.LEFT_ARM)) count++;
		if (isBlacked(BodyPart.RIGHT_ARM) || hasFracture(BodyPart.RIGHT_ARM)) count++;
		return count;
	}

	public boolean isStomachBlacked() {
		return isBlacked(BodyPart.STOMACH);
	}

	public int getPainkillerTicks() {
		return painkillerTicks;
	}

	public boolean hasPainkiller() {
		return painkillerTicks > 0;
	}

	public void setPainkillerTicks(int ticks) {
		this.painkillerTicks = Math.max(0, Math.min(MAX_PAINKILLER_TICKS, ticks));
	}

	public void addPainkillerTicks(int delta) {
		setPainkillerTicks(this.painkillerTicks + delta);
	}

	public List<DamageReport> getRecentHits() {
		return new ArrayList<>(recentHits);
	}

	@Nullable
	public DamageReport getLastFatalReport() {
		return lastFatalReport;
	}

	private void recordReport(DamageReport report, boolean fatal) {
		recentHits.addFirst(report);
		while (recentHits.size() > MAX_HIT_LOG) {
			recentHits.removeLast();
		}
		if (fatal) {
			lastFatalReport = report;
		}
	}

	public boolean applyHit(
		PlayerLoadout loadout,
		HitZone zone,
		float rawDamage,
		int penetrationPower,
		String killerName,
		String weaponName,
		String ammoName,
		float distanceMeters
	) {
		return applyHit(
			loadout, zone, rawDamage, penetrationPower,
			1.0f, false, false, 1.0f, 1.0f,
			killerName, weaponName, ammoName, distanceMeters
		);
	}

	public boolean applyHit(
		PlayerLoadout loadout,
		HitZone zone,
		float rawDamage,
		int penetrationPower,
		float armorWearMultiplier,
		boolean bypassesArmor,
		boolean canRicochet,
		float ricochetRoll,
		String killerName,
		String weaponName,
		String ammoName,
		float distanceMeters
	) {
		return applyHit(
			loadout, zone, rawDamage, penetrationPower,
			armorWearMultiplier, bypassesArmor, canRicochet, ricochetRoll, 1.0f,
			killerName, weaponName, ammoName, distanceMeters
		);
	}

	public boolean applyHit(
		PlayerLoadout loadout,
		HitZone zone,
		float rawDamage,
		int penetrationPower,
		float armorWearMultiplier,
		boolean bypassesArmor,
		boolean canRicochet,
		float ricochetRoll,
		float injuryRoll,
		String killerName,
		String weaponName,
		String ammoName,
		float distanceMeters
	) {
		if (rawDamage <= 0.0f) {
			return isDead();
		}

		ZonalArmorHelper.ArmorResult armorRes = ZonalArmorHelper.evaluateHit(
			loadout, zone, rawDamage, penetrationPower,
			armorWearMultiplier, bypassesArmor, canRicochet, ricochetRoll
		);
		BodyPart targetPart = zone.getBodyPart();
		float dmg = armorRes.remainingDamage();
		boolean oneshot = false;
		boolean overflowed = false;

		// Rule 1: Unprotected HEAD_EYES ("Голова — глаза") -> instant oneshot (only for non-bypassing hits)
		if (zone.isUnprotectedOneshot() && !bypassesArmor && !armorRes.protectedZone()) {
			setHp(BodyPart.HEAD, 0.0f);
			dmg = Math.max(dmg, BodyPart.HEAD.getMaxHp());
			oneshot = true;
		} else if (!targetPart.isVital()) {
			float currentLimbHp = getHp(targetPart);
			if (currentLimbHp >= dmg) {
				setHp(targetPart, currentLimbHp - dmg);
			} else {
				float excess = dmg - currentLimbHp;
				setHp(targetPart, 0.0f);
				if (excess > 0.0f) {
					setHp(BodyPart.THORAX, getHp(BodyPart.THORAX) - excess);
					overflowed = true;
				}
			}
		} else {
			setHp(targetPart, getHp(targetPart) - dmg);
		}

		boolean killed = isDead();

		// Rule 3: Combat injuries (only for penetrating/unblocked non-bypassing alive hits)
		if (!killed && !bypassesArmor && !armorRes.ricocheted() && !armorRes.stoppedByArmor() && dmg > 0.0f) {
			float heavyChance = dmg >= 25.0f ? Math.min(0.45f, 0.12f + (dmg - 25.0f) * 0.008f) : 0.0f;
			if (dmg >= 25.0f && injuryRoll < heavyChance) {
				addInjury(targetPart, InjuryType.HEAVY_BLEED);
			} else if (dmg >= 10.0f) {
				float lightChance = Math.min(0.50f, 0.18f + (dmg - 10.0f) * 0.010f);
				float threshold = heavyChance + lightChance;
				if (injuryRoll < threshold) {
					addInjury(targetPart, InjuryType.LIGHT_BLEED);
				}
			}

			if (isLimb(targetPart)) {
				float fractureChance = 0.0f;
				if (isBlacked(targetPart) && dmg >= 20.0f) {
					fractureChance = 0.70f;
				} else if (dmg >= 18.0f) {
					fractureChance = Math.min(0.45f, 0.15f + (dmg - 18.0f) * 0.008f);
				}
				if (fractureChance > 0.0f && injuryRoll < fractureChance) {
					addInjury(targetPart, InjuryType.FRACTURE);
				}
			}
		}

		DamageReport report = new DamageReport(
			zone, targetPart, rawDamage, dmg, overflowed,
			armorRes.stoppedByArmor(), armorRes.ricocheted(), oneshot,
			killerName, weaponName, ammoName, distanceMeters
		);
		recordReport(report, killed);
		return killed;
	}

	/**
	 * Harsh EFT fall impact across BOTH legs with encumbrance weight multiplier and leg-to-thorax overflow.
	 */
	public boolean applyFallDamage(float vanillaAmount, float weightKg, float randomRoll) {
		if (vanillaAmount <= 0.0f) {
			return isDead();
		}

		double excessKg = Math.max(0.0, weightKg - 20.0);
		double weightMult = 1.0 + Math.min(0.80, excessKg * 0.02);
		float effectiveFall = (float) (vanillaAmount * weightMult);
		float perLegDamage = effectiveFall * 11.0f;

		float leftBefore = getHp(BodyPart.LEFT_LEG);
		float rightBefore = getHp(BodyPart.RIGHT_LEG);
		float leftExcess = Math.max(0.0f, perLegDamage - leftBefore);
		float rightExcess = Math.max(0.0f, perLegDamage - rightBefore);

		setHp(BodyPart.LEFT_LEG, leftBefore - perLegDamage);
		setHp(BodyPart.RIGHT_LEG, rightBefore - perLegDamage);

		float thoraxOverflow = leftExcess + rightExcess;
		if (thoraxOverflow > 0.0f) {
			setHp(BodyPart.THORAX, getHp(BodyPart.THORAX) - thoraxOverflow);
		}

		if (effectiveFall >= 6.0f) {
			addInjury(BodyPart.LEFT_LEG, InjuryType.FRACTURE);
			addInjury(BodyPart.RIGHT_LEG, InjuryType.FRACTURE);
			if (effectiveFall >= 8.0f) {
				addInjury(BodyPart.LEFT_LEG, InjuryType.HEAVY_BLEED);
				addInjury(BodyPart.RIGHT_LEG, InjuryType.LIGHT_BLEED);
			} else {
				addInjury(BodyPart.LEFT_LEG, InjuryType.LIGHT_BLEED);
				if (randomRoll < 0.50f) {
					addInjury(BodyPart.RIGHT_LEG, InjuryType.LIGHT_BLEED);
				}
			}
		} else if (effectiveFall >= 3.0f) {
			addInjury(BodyPart.LEFT_LEG, InjuryType.FRACTURE);
			if (effectiveFall >= 4.0f || randomRoll < 0.50f) {
				addInjury(BodyPart.RIGHT_LEG, InjuryType.FRACTURE);
			}
		}

		boolean killed = isDead();
		DamageReport report = new DamageReport(
			HitZone.LEFT_LEG,
			BodyPart.LEFT_LEG,
			perLegDamage * 2.0f,
			perLegDamage * 2.0f,
			thoraxOverflow > 0.0f,
			false,
			false,
			false,
			"Fall Impact",
			"Gravity",
			String.format(Locale.ROOT, "%.1f kg", weightKg),
			vanillaAmount + 3.0f
		);
		recordReport(report, killed);
		return killed;
	}

	/**
	 * Advances bleeding, painkiller timer, broken-leg sprint pain, and blacked-stomach exhaustion by 1 server tick.
	 */
	public TickResult tickEffects(boolean isSprinting) {
		boolean stateChanged = false;
		boolean killedByBloodLoss = false;
		boolean bleedDamaged = false;
		boolean heavyBleedActive = countInjuries(InjuryType.HEAVY_BLEED) > 0;
		boolean sprintPainTriggered = false;
		float exhaustionToAdd = 0.0f;

		if (painkillerTicks > 0) {
			painkillerTicks--;
			if (painkillerTicks == 0 || painkillerTicks % 20 == 0) {
				stateChanged = true;
			}
		}

		// 1. Bleeding ticks (every 40 ticks = 2.0s)
		if (hasAnyBleed()) {
			bleedTickCounter++;
			if (bleedTickCounter >= 40) {
				bleedTickCounter = 0;
				bleedDamaged = true;
				stateChanged = true;

				float totalBleedDmg = 0.0f;
				boolean overflowed = false;
				boolean anyHeavy = false;
				for (BodyPart part : BodyPart.values()) {
					float drain = 0.0f;
					if (hasHeavyBleed(part)) {
						drain = 4.0f;
						anyHeavy = true;
					} else if (hasLightBleed(part)) {
						drain = 1.5f;
					}
					if (drain > 0.0f) {
						totalBleedDmg += drain;
						if (applyInternalDamage(part, drain)) {
							overflowed = true;
						}
					}
				}

				if (isDead()) {
					killedByBloodLoss = true;
					recordReport(new DamageReport(
						HitZone.THORAX, BodyPart.THORAX, totalBleedDmg, totalBleedDmg,
						overflowed, false, false, false,
						"Blood Loss", anyHeavy ? "Heavy Bleeding" : "Light Bleeding", "", 0.0f
					), true);
				}
			}
		} else {
			bleedTickCounter = 0;
		}

		// 2. Sprinting on impaired legs while painkiller is active (1.5 HP / sec)
		int impairedLegs = getImpairedLegCount();
		if (impairedLegs > 0 && hasPainkiller() && isSprinting && !isDead()) {
			sprintPainTickCounter++;
			if (sprintPainTickCounter >= 20) {
				sprintPainTickCounter = 0;
				sprintPainTriggered = true;
				stateChanged = true;

				boolean leftImpaired = isBlacked(BodyPart.LEFT_LEG) || hasFracture(BodyPart.LEFT_LEG);
				boolean rightImpaired = isBlacked(BodyPart.RIGHT_LEG) || hasFracture(BodyPart.RIGHT_LEG);
				float perLeg = 1.5f / Math.max(1, (leftImpaired ? 1 : 0) + (rightImpaired ? 1 : 0));
				boolean overflowed = false;
				if (leftImpaired && applyInternalDamage(BodyPart.LEFT_LEG, perLeg)) {
					overflowed = true;
				}
				if (rightImpaired && applyInternalDamage(BodyPart.RIGHT_LEG, perLeg)) {
					overflowed = true;
				}
				if (isDead()) {
					killedByBloodLoss = true;
					recordReport(new DamageReport(
						HitZone.LEFT_LEG, BodyPart.LEFT_LEG, 1.5f, 1.5f,
						overflowed, false, false, false,
						"Pain Shock", "Broken Legs Sprint", "", 0.0f
					), true);
				}
			}
		} else {
			sprintPainTickCounter = 0;
		}

		// 3. Blacked stomach hunger/hydration drain (every 40 ticks)
		boolean stomachBlacked = isStomachBlacked();
		if (stomachBlacked && !isDead()) {
			stomachDrainTickCounter++;
			if (stomachDrainTickCounter >= 40) {
				stomachDrainTickCounter = 0;
				exhaustionToAdd = 0.45f;
			}
		} else {
			stomachDrainTickCounter = 0;
		}

		return new TickResult(
			stateChanged,
			killedByBloodLoss,
			bleedDamaged,
			heavyBleedActive,
			sprintPainTriggered,
			impairedLegs,
			getImpairedArmCount(),
			stomachBlacked,
			hasPainkiller(),
			exhaustionToAdd
		);
	}

	private boolean applyInternalDamage(BodyPart part, float amount) {
		if (!part.isVital()) {
			float cur = getHp(part);
			if (cur >= amount) {
				setHp(part, cur - amount);
				return false;
			}
			float excess = amount - cur;
			setHp(part, 0.0f);
			if (excess > 0.0f) {
				setHp(BodyPart.THORAX, getHp(BodyPart.THORAX) - excess);
				return true;
			}
			return false;
		}
		setHp(part, getHp(part) - amount);
		return false;
	}

	/**
	 * Gentle natural regeneration / food healing helper (only heals non-blacked parts up to effectiveMaxHp).
	 */
	public void healEvenly(float amount) {
		if (amount <= 0.0f || isDead()) return;
		for (BodyPart part : BodyPart.values()) {
			float cur = getHp(part);
			float effMax = getEffectiveMaxHp(part);
			if (cur > 0.001f && cur < effMax) {
				setHp(part, cur + amount);
			}
		}
	}

	public NbtCompound writeNbt() {
		NbtCompound root = new NbtCompound();

		NbtCompound effNbt = new NbtCompound();
		NbtCompound injNbt = new NbtCompound();
		for (BodyPart part : BodyPart.values()) {
			root.putFloat(part.getId(), getHp(part));
			effNbt.putFloat(part.getId(), getEffectiveMaxHp(part));
			int mask = 0;
			for (InjuryType t : getInjuries(part)) {
				mask |= t.getBitMask();
			}
			if (mask != 0) {
				injNbt.putInt(part.getId(), mask);
			}
		}
		root.put("EffectiveMaxHp", effNbt);
		if (!injNbt.isEmpty()) {
			root.put("Injuries", injNbt);
		}

		root.putInt("PainkillerTicks", painkillerTicks);
		root.putInt("BleedTick", bleedTickCounter);
		root.putInt("SprintPainTick", sprintPainTickCounter);
		root.putInt("StomachDrainTick", stomachDrainTickCounter);

		NbtList hits = new NbtList();
		for (DamageReport r : recentHits) {
			hits.add(r.toNbt());
		}
		root.put("RecentHits", hits);
		if (lastFatalReport != null) {
			root.put("Fatal", lastFatalReport.toNbt());
		}
		return root;
	}

	public void readNbt(NbtCompound root) {
		if (root == null) {
			resetFull();
			return;
		}

		// 1. Read EffectiveMaxHp BEFORE part HP so setHp clamps accurately
		NbtCompound effNbt = root.contains("EffectiveMaxHp", NbtElement.COMPOUND_TYPE)
			? root.getCompound("EffectiveMaxHp")
			: null;
		for (BodyPart part : BodyPart.values()) {
			float eff = (effNbt != null && effNbt.contains(part.getId()))
				? effNbt.getFloat(part.getId())
				: part.getMaxHp();
			effectiveMaxHpMap.put(part, Math.max(1.0f, Math.min(part.getMaxHp(), eff)));
		}

		// 2. Read HP per part
		for (BodyPart part : BodyPart.values()) {
			if (root.contains(part.getId())) {
				setHp(part, root.getFloat(part.getId()));
			} else {
				setHp(part, getEffectiveMaxHp(part));
			}
		}

		// 3. Read Injuries bitmask per part
		NbtCompound injNbt = root.contains("Injuries", NbtElement.COMPOUND_TYPE)
			? root.getCompound("Injuries")
			: null;
		for (BodyPart part : BodyPart.values()) {
			EnumSet<InjuryType> set = EnumSet.noneOf(InjuryType.class);
			if (injNbt != null && injNbt.contains(part.getId())) {
				int mask = injNbt.getInt(part.getId());
				for (InjuryType t : InjuryType.values()) {
					if ((mask & t.getBitMask()) != 0) {
						if (t != InjuryType.FRACTURE || isLimb(part)) {
							set.add(t);
						}
					}
				}
				if (set.contains(InjuryType.HEAVY_BLEED)) {
					set.remove(InjuryType.LIGHT_BLEED);
				}
			}
			injuries.put(part, set);
		}

		painkillerTicks = Math.max(0, Math.min(MAX_PAINKILLER_TICKS, root.getInt("PainkillerTicks")));
		bleedTickCounter = Math.max(0, root.getInt("BleedTick"));
		sprintPainTickCounter = Math.max(0, root.getInt("SprintPainTick"));
		stomachDrainTickCounter = Math.max(0, root.getInt("StomachDrainTick"));

		recentHits.clear();
		if (root.contains("RecentHits", NbtElement.LIST_TYPE)) {
			NbtList hits = root.getList("RecentHits", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < hits.size(); i++) {
				recentHits.addLast(DamageReport.fromNbt(hits.getCompound(i)));
			}
		}
		lastFatalReport = root.contains("Fatal", NbtElement.COMPOUND_TYPE)
			? DamageReport.fromNbt(root.getCompound("Fatal"))
			: null;
	}
}
