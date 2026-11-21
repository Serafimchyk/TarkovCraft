package com.tarkovcraft.health;

import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.screen.TarkovScreenHandler;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MedicalAndInjuryTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
	}

	@Test
	void fallDamageScalesWithHeightAndWeightAndBlacksBothLegs() {
		BodyPartHealth health = new BodyPartHealth();

		// 1. Light fall (1.5, 10 kg) -> 16.5 dmg to each leg, no fractures
		assertFalse(health.applyFallDamage(1.5f, 10.0f, 0.9f));
		assertEquals(48.5f, health.getHp(BodyPart.LEFT_LEG), 0.01f);
		assertEquals(48.5f, health.getHp(BodyPart.RIGHT_LEG), 0.01f);
		assertFalse(health.hasFracture(BodyPart.LEFT_LEG));
		assertFalse(health.hasFracture(BodyPart.RIGHT_LEG));

		// 2. Medium fall (4.0, 10 kg) -> 44 dmg to each leg + FRACTURE on BOTH legs
		health.resetFull();
		assertFalse(health.applyFallDamage(4.0f, 10.0f, 0.9f));
		assertEquals(21.0f, health.getHp(BodyPart.LEFT_LEG), 0.01f);
		assertEquals(21.0f, health.getHp(BodyPart.RIGHT_LEG), 0.01f);
		assertTrue(health.hasFracture(BodyPart.LEFT_LEG));
		assertTrue(health.hasFracture(BodyPart.RIGHT_LEG));

		// 3. High fall (6.5, 10 kg) -> 71.5 dmg/leg -> BOTH legs blacked (0 HP), both fractured, LIGHT_BLEED
		health.resetFull();
		assertFalse(health.applyFallDamage(6.5f, 10.0f, 0.2f));
		assertEquals(0.0f, health.getHp(BodyPart.LEFT_LEG), 0.01f);
		assertEquals(0.0f, health.getHp(BodyPart.RIGHT_LEG), 0.01f);
		assertTrue(health.hasFracture(BodyPart.LEFT_LEG));
		assertTrue(health.hasFracture(BodyPart.RIGHT_LEG));
		assertTrue(health.hasLightBleed(BodyPart.LEFT_LEG));
		assertTrue(health.getHp(BodyPart.THORAX) < 85.0f);

		// 4. Encumbrance overload (5.0, 50 kg -> weightMult=1.6 -> effectiveFall=8.0 -> 88 dmg/leg) -> overflows to THORAX
		health.resetFull();
		assertFalse(health.applyFallDamage(5.0f, 50.0f, 0.5f));
		assertEquals(0.0f, health.getHp(BodyPart.LEFT_LEG), 0.01f);
		assertEquals(0.0f, health.getHp(BodyPart.RIGHT_LEG), 0.01f);
		assertEquals(85.0f - (88.0f - 65.0f) * 2.0f, health.getHp(BodyPart.THORAX), 0.01f);
		assertTrue(health.hasHeavyBleed(BodyPart.LEFT_LEG));

		// 5. Extreme fall (11.0, 10 kg -> 121 dmg/leg -> excess 56*2=112 > 85) -> THORAX = 0 -> KIA
		health.resetFull();
		assertTrue(health.applyFallDamage(11.0f, 10.0f, 0.5f));
		assertTrue(health.isDead());
		assertEquals(0.0f, health.getHp(BodyPart.THORAX), 0.01f);
		assertNotNull(health.getLastFatalReport());
		assertEquals("Fall Impact", health.getLastFatalReport().killerName());
	}

	@Test
	void bleedingDrainsOverTimeOverflowsAndCanKill() {
		BodyPartHealth health = new BodyPartHealth();
		health.addInjury(BodyPart.LEFT_ARM, InjuryType.LIGHT_BLEED);
		for (int t = 0; t < 40; t++) {
			health.tickEffects(false);
		}
		assertEquals(58.5f, health.getHp(BodyPart.LEFT_ARM), 0.01f);

		// Heavy bleed on blacked leg overflows 4.0 HP directly into THORAX every 40 ticks
		health.resetFull();
		health.setHp(BodyPart.LEFT_LEG, 0.0f);
		health.addInjury(BodyPart.LEFT_LEG, InjuryType.HEAVY_BLEED);
		for (int t = 0; t < 40; t++) {
			health.tickEffects(false);
		}
		assertEquals(81.0f, health.getHp(BodyPart.THORAX), 0.01f);

		// Long heavy bleed eventually kills the player via Blood Loss
		BodyPartHealth.TickResult fatalRes = null;
		for (int cycle = 0; cycle < 30 && !health.isDead(); cycle++) {
			for (int t = 0; t < 40; t++) {
				fatalRes = health.tickEffects(false);
				if (fatalRes.killedByBloodLoss()) break;
			}
		}
		assertNotNull(fatalRes);
		assertTrue(fatalRes.killedByBloodLoss());
		assertTrue(health.isDead());
		assertNotNull(health.getLastFatalReport());
		assertEquals("Blood Loss", health.getLastFatalReport().killerName());
	}

	@Test
	void limbPenaltiesAndPainkillerSprintDamage() {
		BodyPartHealth health = new BodyPartHealth();
		health.addInjury(BodyPart.LEFT_LEG, InjuryType.FRACTURE);
		assertEquals(1, health.tickEffects(false).impairedLegs());

		health.setHp(BodyPart.RIGHT_LEG, 0.0f);
		assertEquals(2, health.tickEffects(false).impairedLegs());

		// Painkiller active + sprinting on impaired legs -> 1.5 HP pain every 20 ticks (overflows from blacked leg to thorax)
		health.setPainkillerTicks(1800);
		float leftLegBefore = health.getHp(BodyPart.LEFT_LEG);
		float thoraxBefore = health.getHp(BodyPart.THORAX);
		BodyPartHealth.TickResult sprintRes = null;
		for (int t = 0; t < 20; t++) {
			sprintRes = health.tickEffects(true);
		}
		assertNotNull(sprintRes);
		assertTrue(sprintRes.painkillerActive());
		assertTrue(sprintRes.sprintPainTriggered());
		assertEquals(leftLegBefore - 0.75f, health.getHp(BodyPart.LEFT_LEG), 0.01f);
		assertEquals(thoraxBefore - 0.75f, health.getHp(BodyPart.THORAX), 0.01f);

		// Arms impairment & blacked stomach exhaustion
		health.addInjury(BodyPart.LEFT_ARM, InjuryType.FRACTURE);
		health.setHp(BodyPart.RIGHT_ARM, 0.0f);
		assertEquals(2, health.tickEffects(false).impairedArms());

		health.setHp(BodyPart.STOMACH, 0.0f);
		float exhaust = 0.0f;
		for (int t = 0; t < 40; t++) {
			exhaust += health.tickEffects(false).exhaustionToAdd();
		}
		assertTrue(exhaust > 0.0f);
	}

	@Test
	void surgeryCmsAndSurv12Rules() {
		BodyPartHealth health = new BodyPartHealth();
		health.setHp(BodyPart.LEFT_LEG, 0.0f);
		health.setHp(BodyPart.HEAD, 20.0f);

		ItemStack cms = new ItemStack(ModItems.CMS_KIT);
		// CMS rejects HEAD, THORAX, and non-blacked RIGHT_LEG without spending uses
		assertFalse(MedicalHelper.applyMedical(health, cms, BodyPart.HEAD, false).applied());
		assertFalse(MedicalHelper.applyMedical(health, cms, BodyPart.THORAX, false).applied());
		assertFalse(MedicalHelper.applyMedical(health, cms, BodyPart.RIGHT_LEG, false).applied());
		assertEquals(5, TarkovMedicalItem.getRemaining(cms));

		// CMS restores blacked LEFT_LEG (0 -> 1 HP) and cuts effectiveMaxHp to 55% (65 -> 36)
		MedicalHelper.ApplyResult cmsRes = MedicalHelper.applyMedical(health, cms, BodyPart.LEFT_LEG, false);
		assertTrue(cmsRes.applied());
		assertEquals(1.0f, health.getHp(BodyPart.LEFT_LEG), 0.01f);
		assertEquals(36.0f, health.getEffectiveMaxHp(BodyPart.LEFT_LEG), 0.01f);
		assertEquals(4, TarkovMedicalItem.getRemaining(cms));

		// Subsequent healing cannot exceed effectiveMaxHp (36)
		ItemStack salewa = new ItemStack(ModItems.SALEWA_MEDKIT);
		assertTrue(MedicalHelper.applyMedical(health, salewa, BodyPart.LEFT_LEG, false).applied());
		assertEquals(36.0f, health.getHp(BodyPart.LEFT_LEG), 0.01f);

		// Surv12 restores blacked fractured RIGHT_LEG (0 -> 1 HP), cuts max HP to 80% (65 -> 52), AND cures fracture
		health.setHp(BodyPart.RIGHT_LEG, 0.0f);
		health.addInjury(BodyPart.RIGHT_LEG, InjuryType.FRACTURE);
		ItemStack surv12 = new ItemStack(ModItems.SURV12_KIT);
		MedicalHelper.ApplyResult survRes = MedicalHelper.applyMedical(health, surv12, BodyPart.RIGHT_LEG, false);
		assertTrue(survRes.applied());
		assertEquals(1.0f, health.getHp(BodyPart.RIGHT_LEG), 0.01f);
		assertEquals(52.0f, health.getEffectiveMaxHp(BodyPart.RIGHT_LEG), 0.01f);
		assertFalse(health.hasFracture(BodyPart.RIGHT_LEG));
		assertEquals(14, TarkovMedicalItem.getRemaining(surv12));
	}

	@Test
	void medkitPoolsAi2AndSalewaAndAutoTargetPriority() {
		BodyPartHealth health = new BodyPartHealth();
		health.setHp(BodyPart.LEFT_LEG, 10.0f);
		health.setHp(BodyPart.THORAX, 20.0f);
		health.addInjury(BodyPart.LEFT_LEG, InjuryType.LIGHT_BLEED);

		// AI-2 prioritizes vital THORAX over leg, heals max 50 HP/use, does not stop bleeds
		ItemStack ai2 = new ItemStack(ModItems.AI2_MEDKIT);
		MedicalHelper.ApplyResult ai2Res = MedicalHelper.applyMedical(health, ai2, null, false);
		assertTrue(ai2Res.applied());
		assertEquals(BodyPart.THORAX, ai2Res.targetPart());
		assertEquals(70.0f, health.getHp(BodyPart.THORAX), 0.01f);
		assertEquals(50, TarkovMedicalItem.getRemaining(ai2));
		assertTrue(health.hasLightBleed(BodyPart.LEFT_LEG));

		// Salewa priority: HEAVY_BLEED (-135) -> LIGHT_BLEED (-45) -> HP heal (up to 85/use)
		health.addInjury(BodyPart.THORAX, InjuryType.HEAVY_BLEED);
		ItemStack salewa = new ItemStack(ModItems.SALEWA_MEDKIT);
		assertTrue(TarkovScreenHandler.isConsumableItem(salewa));

		MedicalHelper.ApplyResult r1 = MedicalHelper.applyMedical(health, salewa, null, false);
		assertTrue(r1.applied());
		assertEquals(BodyPart.THORAX, r1.targetPart());
		assertFalse(health.hasHeavyBleed(BodyPart.THORAX));
		assertEquals(265, TarkovMedicalItem.getRemaining(salewa));

		MedicalHelper.ApplyResult r2 = MedicalHelper.applyMedical(health, salewa, null, false);
		assertTrue(r2.applied());
		assertEquals(BodyPart.LEFT_LEG, r2.targetPart());
		assertFalse(health.hasLightBleed(BodyPart.LEFT_LEG));
		assertEquals(220, TarkovMedicalItem.getRemaining(salewa));

		// Drain remaining pool -> item becomes empty
		TarkovMedicalItem.setRemaining(salewa, 10);
		health.setHp(BodyPart.LEFT_LEG, 10.0f);
		MedicalHelper.ApplyResult r3 = MedicalHelper.applyMedical(health, salewa, BodyPart.LEFT_LEG, false);
		assertTrue(r3.applied());
		assertTrue(r3.depleted());
		assertTrue(salewa.isEmpty());
	}

	@Test
	void bandagesTourniquetSplintsAndAnalginUses() {
		BodyPartHealth healthy = new BodyPartHealth();
		ItemStack bandage = new ItemStack(ModItems.BANDAGE);
		assertFalse(MedicalHelper.applyMedical(healthy, bandage, null, false).applied());
		assertEquals(2, TarkovMedicalItem.getRemaining(bandage));

		healthy.addInjury(BodyPart.LEFT_ARM, InjuryType.LIGHT_BLEED);
		assertTrue(MedicalHelper.applyMedical(healthy, bandage, null, false).applied());
		assertFalse(healthy.hasLightBleed(BodyPart.LEFT_ARM));
		assertEquals(1, TarkovMedicalItem.getRemaining(bandage));

		ItemStack cat = new ItemStack(ModItems.CAT_TOURNIQUET);
		healthy.addInjury(BodyPart.THORAX, InjuryType.HEAVY_BLEED);
		MedicalHelper.ApplyResult catRes = MedicalHelper.applyMedical(healthy, cat, null, false);
		assertTrue(catRes.applied());
		assertTrue(catRes.depleted());
		assertTrue(cat.isEmpty());
		assertFalse(healthy.hasHeavyBleed(BodyPart.THORAX));

		ItemStack splint = new ItemStack(ModItems.SPLINT);
		ItemStack alu = new ItemStack(ModItems.ALU_SPLINT);
		healthy.addInjury(BodyPart.LEFT_LEG, InjuryType.FRACTURE);
		healthy.addInjury(BodyPart.RIGHT_LEG, InjuryType.FRACTURE);
		assertTrue(MedicalHelper.applyMedical(healthy, splint, BodyPart.LEFT_LEG, false).depleted());
		assertTrue(splint.isEmpty());
		assertTrue(MedicalHelper.applyMedical(healthy, alu, BodyPart.RIGHT_LEG, false).applied());
		assertEquals(4, TarkovMedicalItem.getRemaining(alu));

		ItemStack analgin = new ItemStack(ModItems.ANALGIN);
		MedicalHelper.ApplyResult pkRes = MedicalHelper.applyMedical(healthy, analgin, null, false);
		assertTrue(pkRes.applied());
		assertEquals(1800, healthy.getPainkillerTicks());
		assertEquals(1.5f, pkRes.exhaustionToAdd(), 0.01f);
		assertEquals(3, TarkovMedicalItem.getRemaining(analgin));
	}

	@Test
	void bodyPartHealthNbtRoundTripAndLegacyCompatibility() {
		BodyPartHealth original = new BodyPartHealth();
		original.setEffectiveMaxHp(BodyPart.LEFT_LEG, 36.0f);
		original.setHp(BodyPart.LEFT_LEG, 22.5f);
		original.addInjury(BodyPart.THORAX, InjuryType.HEAVY_BLEED);
		original.addInjury(BodyPart.RIGHT_LEG, InjuryType.FRACTURE);
		original.setPainkillerTicks(1400);

		NbtCompound saved = original.writeNbt();
		BodyPartHealth restored = new BodyPartHealth();
		restored.readNbt(saved);

		assertEquals(36.0f, restored.getEffectiveMaxHp(BodyPart.LEFT_LEG), 0.01f);
		assertEquals(22.5f, restored.getHp(BodyPart.LEFT_LEG), 0.01f);
		assertTrue(restored.hasHeavyBleed(BodyPart.THORAX));
		assertTrue(restored.hasFracture(BodyPart.RIGHT_LEG));
		assertEquals(1400, restored.getPainkillerTicks());

		// Legacy NBT without EffectiveMaxHp / Injuries / PainkillerTicks must load cleanly
		NbtCompound legacy = new NbtCompound();
		legacy.putFloat(BodyPart.HEAD.getId(), 28.0f);
		legacy.putFloat(BodyPart.THORAX.getId(), 60.0f);
		BodyPartHealth fromLegacy = new BodyPartHealth();
		fromLegacy.readNbt(legacy);

		assertEquals(BodyPart.HEAD.getMaxHp(), fromLegacy.getEffectiveMaxHp(BodyPart.HEAD), 0.01f);
		assertEquals(28.0f, fromLegacy.getHp(BodyPart.HEAD), 0.01f);
		assertEquals(60.0f, fromLegacy.getHp(BodyPart.THORAX), 0.01f);
		assertEquals(0, fromLegacy.getPainkillerTicks());
		assertFalse(fromLegacy.hasAnyBleed());
	}
}
