package com.tarkovcraft.health;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZonalArmorHelperTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
	}

	@Test
	void brokenArmorProvidesZeroProtectionAndRestoresHeadEyesOneshot() {
		PlayerLoadout loadout = new PlayerLoadout();
		ItemStack altyn = new ItemStack(ModItems.ALTYN_HELMET);
		altyn.setDamage(altyn.getMaxDamage()); // 0 remaining durability
		assertTrue(TarkovArmorItem.isBroken(altyn));
		loadout.setEquipment(EquipmentSlotType.HELMET, altyn);

		ItemStack mask = new ItemStack(ModItems.DEATH_SHADOW_MASK);
		mask.setDamage(mask.getMaxDamage()); // 0 remaining durability
		assertTrue(TarkovArmorItem.isBroken(mask));
		loadout.setEquipment(EquipmentSlotType.FACE_MASK, mask);

		assertEquals(0, ZonalArmorHelper.getHighestProtectingClass(loadout, HitZone.HEAD_EYES));

		BodyPartHealth health = new BodyPartHealth();
		boolean killed = health.applyHit(loadout, HitZone.HEAD_EYES, 5.0f, 10, "Scav", "PM", "9x18", 12.0f);
		assertTrue(killed);
		assertEquals(0.0f, health.getHp(BodyPart.HEAD), 0.01f);
		assertNotNull(health.getLastFatalReport());
		assertTrue(health.getLastFatalReport().headEyesOneshot());
		assertFalse(health.getLastFatalReport().armorBlocked());
	}

	@Test
	void wearDependsOnAmmoTierAndArmorMaterial() {
		// Compare AmmoTier wear: IGN (1.5x) vs HP (0.7x) on identical armor
		PlayerLoadout ignLoadout = new PlayerLoadout();
		ItemStack vestIgn = new ItemStack(ModItems.PRESS_ARMOR);
		ignLoadout.setEquipment(EquipmentSlotType.BODY_ARMOR, vestIgn);
		ZonalArmorHelper.ArmorResult ignRes = ZonalArmorHelper.evaluateHit(
			ignLoadout, HitZone.THORAX, 40.0f, 20,
			AmmoTier.IGN.getArmorWearMultiplier(), false, false, 1.0f
		);

		PlayerLoadout hpLoadout = new PlayerLoadout();
		ItemStack vestHp = new ItemStack(ModItems.PRESS_ARMOR);
		hpLoadout.setEquipment(EquipmentSlotType.BODY_ARMOR, vestHp);
		ZonalArmorHelper.ArmorResult hpRes = ZonalArmorHelper.evaluateHit(
			hpLoadout, HitZone.THORAX, 40.0f, 20,
			AmmoTier.HP.getArmorWearMultiplier(), false, false, 1.0f
		);

		assertTrue(ignRes.durabilityDamageApplied() > hpRes.durabilityDamageApplied(),
			"IGN (1.5x) should wear armor faster than HP (0.7x)");
		assertTrue(vestIgn.getDamage() > vestHp.getDamage());

		// Compare Material wear: UHMWPE (Press, 0.45) vs STEEL (SSh-68, 0.85) at equal damage & pen
		PlayerLoadout uhmwpeLoadout = new PlayerLoadout();
		ItemStack press = new ItemStack(ModItems.PRESS_ARMOR);
		uhmwpeLoadout.setEquipment(EquipmentSlotType.BODY_ARMOR, press);
		ZonalArmorHelper.ArmorResult uhmwpeRes = ZonalArmorHelper.evaluateHit(
			uhmwpeLoadout, HitZone.THORAX, 40.0f, 15, 1.0f, false, false, 1.0f
		);

		PlayerLoadout steelLoadout = new PlayerLoadout();
		ItemStack ssh68 = new ItemStack(ModItems.SSH68_HELMET);
		steelLoadout.setEquipment(EquipmentSlotType.HELMET, ssh68);
		ZonalArmorHelper.ArmorResult steelRes = ZonalArmorHelper.evaluateHit(
			steelLoadout, HitZone.HEAD_TOP, 40.0f, 15, 1.0f, false, false, 1.0f
		);

		assertTrue(uhmwpeRes.durabilityDamageApplied() < steelRes.durabilityDamageApplied(),
			"UHMWPE (0.45) should lose less durability than STEEL (0.85) at equal raw damage");
	}

	@Test
	void helmetRicochetWorksOnHelmetZonesAndNeverOnUncoveredEarsOrBodyArmor() {
		PlayerLoadout loadout = new PlayerLoadout();
		ItemStack ssh68 = new ItemStack(ModItems.SSH68_HELMET); // Class 3, ricochet 0.45, HEAD_TOP only
		loadout.setEquipment(EquipmentSlotType.HELMET, ssh68);

		// Low roll on covered HEAD_TOP -> ricochet!
		ZonalArmorHelper.ArmorResult ricochet = ZonalArmorHelper.evaluateHit(
			loadout, HitZone.HEAD_TOP, 50.0f, 60, 1.0f, false, true, 0.10f
		);
		assertTrue(ricochet.ricocheted());
		assertTrue(ricochet.stoppedByArmor());
		assertTrue(ricochet.protectedZone());
		assertEquals(2.5f, ricochet.remainingDamage(), 0.01f);
		assertTrue(ricochet.durabilityDamageApplied() <= 4);

		// Same low roll on uncovered HEAD_EARS -> no ricochet, no protection
		ZonalArmorHelper.ArmorResult ears = ZonalArmorHelper.evaluateHit(
			loadout, HitZone.HEAD_EARS, 50.0f, 20, 1.0f, false, true, 0.10f
		);
		assertFalse(ears.ricocheted());
		assertFalse(ears.protectedZone());
		assertEquals(50.0f, ears.remainingDamage(), 0.01f);

		// Body armor (PACA) never ricochets even with roll = 0.0
		loadout.setEquipment(EquipmentSlotType.BODY_ARMOR, new ItemStack(ModItems.PACA_ARMOR));
		ZonalArmorHelper.ArmorResult body = ZonalArmorHelper.evaluateHit(
			loadout, HitZone.THORAX, 40.0f, 10, 1.0f, false, true, 0.0f
		);
		assertFalse(body.ricocheted());
		assertTrue(body.stoppedByArmor());
	}

	@Test
	void layeredProtectionHelmetAndFaceMaskEvaluatesOutsideIn() {
		// Case 1: Outer ALTYN stops round -> inner DEATH_SHADOW_MASK takes 0 durability wear
		PlayerLoadout stoppedLoadout = new PlayerLoadout();
		ItemStack altyn1 = new ItemStack(ModItems.ALTYN_HELMET);
		ItemStack mask1 = new ItemStack(ModItems.DEATH_SHADOW_MASK);
		stoppedLoadout.setEquipment(EquipmentSlotType.HELMET, altyn1);
		stoppedLoadout.setEquipment(EquipmentSlotType.FACE_MASK, mask1);

		ZonalArmorHelper.ArmorResult stopped = ZonalArmorHelper.evaluateHit(
			stoppedLoadout, HitZone.HEAD_EYES, 40.0f, 25, 1.0f, false, false, 1.0f
		);
		assertTrue(stopped.stoppedByArmor());
		assertTrue(altyn1.getDamage() > 0);
		assertEquals(0, mask1.getDamage(), "Inner mask must not lose durability when outer helmet stops the round");

		// Case 2: Outer ALTYN (threshold 50) penetrated by pen=55 -> remaining pen ~38 exceeds mask threshold (20),
		// so inner DEATH_SHADOW_MASK additionally attenuates damage and also wears down!
		PlayerLoadout penLoadout = new PlayerLoadout();
		ItemStack altyn2 = new ItemStack(ModItems.ALTYN_HELMET);
		ItemStack mask2 = new ItemStack(ModItems.DEATH_SHADOW_MASK);
		penLoadout.setEquipment(EquipmentSlotType.HELMET, altyn2);
		penLoadout.setEquipment(EquipmentSlotType.FACE_MASK, mask2);

		ZonalArmorHelper.ArmorResult penetratedBoth = ZonalArmorHelper.evaluateHit(
			penLoadout, HitZone.HEAD_EYES, 60.0f, 55, 1.0f, false, false, 1.0f
		);
		assertFalse(penetratedBoth.stoppedByArmor());
		assertTrue(altyn2.getDamage() > 0);
		assertTrue(mask2.getDamage() > 0, "Inner mask must wear when outer helmet is penetrated");
		assertTrue(penetratedBoth.remainingDamage() < 60.0f * 0.75f,
			"Both layers should reduce penetrated damage cumulatively");

		// Case 3: Worn Altyn penetrated by pen=35 -> remaining pen ~35-17=18 < Death Shadow threshold (20) -> mask STOPS it!
		PlayerLoadout comboStopLoadout = new PlayerLoadout();
		ItemStack wornAltyn = new ItemStack(ModItems.ALTYN_HELMET);
		wornAltyn.setDamage(45); // 10/55 dur -> durRatio ~0.1818 -> threshold ~27.5
		ItemStack freshMask = new ItemStack(ModItems.DEATH_SHADOW_MASK); // threshold 20
		comboStopLoadout.setEquipment(EquipmentSlotType.HELMET, wornAltyn);
		comboStopLoadout.setEquipment(EquipmentSlotType.FACE_MASK, freshMask);

		ZonalArmorHelper.ArmorResult caughtByMask = ZonalArmorHelper.evaluateHit(
			comboStopLoadout, HitZone.HEAD_EYES, 40.0f, 29, 1.0f, false, false, 1.0f
		);
		assertTrue(caughtByMask.stoppedByArmor(), "Inner mask should stop round after outer worn helmet reduces pen");
		assertTrue(freshMask.getDamage() > 0);
	}

	@Test
	void bypassingDamageDealsFullDamageWithoutArmorWearAndWithoutHeadEyesOneshot() {
		PlayerLoadout loadout = new PlayerLoadout();
		ItemStack zabralo = new ItemStack(ModItems.ZABRALO_ARMOR);
		loadout.setEquipment(EquipmentSlotType.BODY_ARMOR, zabralo);

		BodyPartHealth health = new BodyPartHealth();
		boolean killed = health.applyHit(
			loadout, HitZone.THORAX, 12.0f, 0, 0.0f, true, false, 1.0f,
			"drown", "", "", 0.0f
		);
		assertFalse(killed);
		assertEquals(73.0f, health.getHp(BodyPart.THORAX), 0.01f);
		assertEquals(0, zabralo.getDamage(), "Bypassing damage must apply 0 armor wear");
		assertFalse(health.getRecentHits().get(0).armorBlocked());

		// Bypassing damage to unprotected HEAD_EYES must NOT trigger ballistic oneshot
		health.resetFull();
		boolean eyesBypassKilled = health.applyHit(
			loadout, HitZone.HEAD_EYES, 5.0f, 0, 0.0f, true, false, 1.0f,
			"onFire", "", "", 0.0f
		);
		assertFalse(eyesBypassKilled);
		assertEquals(30.0f, health.getHp(BodyPart.HEAD), 0.01f);
		assertFalse(health.getRecentHits().get(0).headEyesOneshot());
	}

	@Test
	void hitZoneResolverUsesHeadYawSeparatelyFromBodyYaw() {
		Vec3d feet = new Vec3d(0, 0, 0);
		float h = 1.8f;
		float bodyYaw = 0.0f;  // Body faces +Z (South)
		float headYaw = 90.0f; // Head turned 90 deg right -> faces -X (West)

		// Shot coming from front of body (+Z -> -Z) hits the side (ear) of the turned head!
		HitZone fromBodyFront = HitZoneResolver.resolveGeometric(
			feet, h, bodyYaw, headYaw,
			new Vec3d(0, 1.55, 0.25), new Vec3d(0, 0, -1)
		);
		assertEquals(HitZone.HEAD_EARS, fromBodyFront);

		// Shot coming from where the head is looking (-X -> +X) hits HEAD_EYES!
		HitZone fromHeadFront = HitZoneResolver.resolveGeometric(
			feet, h, bodyYaw, headYaw,
			new Vec3d(-0.25, 1.58, 0), new Vec3d(1, 0, 0)
		);
		assertEquals(HitZone.HEAD_EYES, fromHeadFront);
	}

	@Test
	void vanillaArmorIsRejectedByTarkovSlotsAndSmartPickup() {
		ItemStack diamondHelmet = new ItemStack(Items.DIAMOND_HELMET);
		ItemStack netheriteChest = new ItemStack(Items.NETHERITE_CHESTPLATE);

		assertFalse(EquipmentSlotType.HELMET.accepts(diamondHelmet));
		assertFalse(EquipmentSlotType.BODY_ARMOR.accepts(netheriteChest));

		PlayerLoadout loadout = new PlayerLoadout();
		// Without storage containers, smart pickup places into 1x1 pockets, NEVER into HELMET/BODY_ARMOR!
		loadout.trySmartPickup(diamondHelmet.copy(), null);
		loadout.trySmartPickup(netheriteChest.copy(), null);
		assertTrue(loadout.getEquipment(EquipmentSlotType.HELMET).isEmpty());
		assertTrue(loadout.getEquipment(EquipmentSlotType.BODY_ARMOR).isEmpty());

		// Cosmetic balaclava has no durability bar and weighs 0.1 kg; Altyn weighs 4.1 kg
		ItemStack balaclava = new ItemStack(ModItems.BALACLAVA_MASK);
		assertFalse(balaclava.isDamageable());
		loadout.clear();
		loadout.setEquipment(EquipmentSlotType.FACE_MASK, balaclava);
		loadout.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.ALTYN_HELMET));
		assertEquals(4.2f, loadout.calculateWeightKg(), 0.01f);
	}
}
