package com.tarkovcraft.health;

import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyPartHealthTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
	}

	@Test
	void hitZoneResolverSeparatesEyesEarsTopNeckArmpitAndLimbs() {
		Vec3d feet = new Vec3d(0, 0, 0);
		float h = 1.8f;
		float yaw = 0.0f; // Facing +Z (South); player's RIGHT is -X, LEFT is +X

		// Shot from front into eye level (relY = 0.88 -> y = 1.584) -> HEAD_EYES
		assertEquals(HitZone.HEAD_EYES,
			HitZoneResolver.resolveGeometric(feet, h, yaw, new Vec3d(0, 1.58, 0.25), new Vec3d(0, 0, -1)));

		// Shot from left/right side at ear level (relY = 0.86 -> y = 1.55) -> HEAD_EARS
		assertEquals(HitZone.HEAD_EARS,
			HitZoneResolver.resolveGeometric(feet, h, yaw, new Vec3d(0.25, 1.55, 0), new Vec3d(-1, 0, 0)));

		// Shot to crown (relY = 0.96 -> y = 1.73) -> HEAD_TOP
		assertEquals(HitZone.HEAD_TOP,
			HitZoneResolver.resolveGeometric(feet, h, yaw, new Vec3d(0, 1.73, 0), new Vec3d(0, -1, 0)));

		// Abstract thin neck strip (relY = 0.76 -> y = 1.37) -> NECK
		assertEquals(HitZone.NECK,
			HitZoneResolver.resolveGeometric(feet, h, yaw, new Vec3d(0, 1.37, 0.15), new Vec3d(0, 0, -1)));

		// Side-angle shot under arm (relY = 0.65 -> y = 1.17, side offset = +0.18 -> Left side) -> ARMPIT_LEFT
		assertEquals(HitZone.ARMPIT_LEFT,
			HitZoneResolver.resolveGeometric(feet, h, yaw, new Vec3d(0.18, 1.17, 0), new Vec3d(-1, 0, 0)));

		// Left leg (relY = 0.25 -> y = 0.45, +X is player's LEFT when facing +Z) -> LEFT_LEG
		assertEquals(HitZone.LEFT_LEG,
			HitZoneResolver.resolveGeometric(feet, h, yaw, new Vec3d(0.15, 0.45, 0), new Vec3d(0, 0, -1)));
	}

	@Test
	void unprotectedHeadEyesIsInstantOneshotEvenWithSmallDamage() {
		PlayerLoadout loadout = new PlayerLoadout();
		// Wearing SSh-68 helmet (covers ONLY HEAD_TOP) does NOT protect HEAD_EYES!
		loadout.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.SSH68_HELMET));
		BodyPartHealth health = new BodyPartHealth();

		boolean killed = health.applyHit(loadout, HitZone.HEAD_EYES, 5.0f, 10, "Scav", "PM", "9x18", 12.0f);
		assertTrue(killed);
		assertEquals(0.0f, health.getHp(BodyPart.HEAD), 0.01f);
		assertNotNull(health.getLastFatalReport());
		assertTrue(health.getLastFatalReport().headEyesOneshot());
	}

	@Test
	void altynVisorProtectsHeadEyesAgainstLowPenetrationShot() {
		PlayerLoadout loadout = new PlayerLoadout();
		loadout.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.ALTYN_HELMET)); // Class 5, covers EYES
		BodyPartHealth health = new BodyPartHealth();

		boolean killed = health.applyHit(loadout, HitZone.HEAD_EYES, 35.0f, 20, "Scav", "MP5", "9x19", 15.0f);
		assertFalse(killed);
		assertTrue(health.getHp(BodyPart.HEAD) > 25.0f);
		assertTrue(health.getRecentHits().get(0).armorBlocked());
	}

	@Test
	void openEarsTakeFullDamageDespiteSsh68Helmet() {
		PlayerLoadout loadout = new PlayerLoadout();
		loadout.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.SSH68_HELMET)); // Only HEAD_TOP
		BodyPartHealth health = new BodyPartHealth();

		health.applyHit(loadout, HitZone.HEAD_EARS, 20.0f, 15, "Scav", "PM", "9x18", 10.0f);
		// Full 20 damage taken on HEAD (35 -> 15), NOT blocked by SSh-68!
		assertEquals(15.0f, health.getHp(BodyPart.HEAD), 0.01f);
		assertFalse(health.getRecentHits().get(0).armorBlocked());

		// Now equip 6B47 Ratnik (covers HEAD_EARS) -> low-pen hit IS blocked!
		loadout.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.RATNIK_6B47_HELMET));
		health.resetFull();
		health.applyHit(loadout, HitZone.HEAD_EARS, 20.0f, 15, "Scav", "PM", "9x18", 10.0f);
		assertTrue(health.getRecentHits().get(0).armorBlocked());
		assertTrue(health.getHp(BodyPart.HEAD) > 30.0f);
	}

	@Test
	void armHitReducesArmToZeroThenOverflowsToThorax() {
		PlayerLoadout loadout = new PlayerLoadout();
		BodyPartHealth health = new BodyPartHealth();

		// First hit: 40 dmg to LEFT_ARM (60 -> 20), THORAX untouched (85)
		assertFalse(health.applyHit(loadout, HitZone.LEFT_ARM, 40.0f, 20, "Scav", "AK", "5.45", 20.0f));
		assertEquals(20.0f, health.getHp(BodyPart.LEFT_ARM), 0.01f);
		assertEquals(85.0f, health.getHp(BodyPart.THORAX), 0.01f);

		// Second hit: 45 dmg to LEFT_ARM -> remaining 20 blacks arm to 0, excess 25 overflows to THORAX (85 -> 60)!
		assertFalse(health.applyHit(loadout, HitZone.LEFT_ARM, 45.0f, 20, "Scav", "AK", "5.45", 20.0f));
		assertEquals(0.0f, health.getHp(BodyPart.LEFT_ARM), 0.01f);
		assertEquals(60.0f, health.getHp(BodyPart.THORAX), 0.01f);
		assertTrue(health.getRecentHits().get(0).overflowedToThorax());

		// Third hit: 65 dmg to already-blacked LEFT_ARM -> full 65 overflows to THORAX (60 -> 0) -> FATAL!
		assertTrue(health.applyHit(loadout, HitZone.LEFT_ARM, 65.0f, 20, "Scav", "AK", "5.45", 20.0f));
		assertEquals(0.0f, health.getHp(BodyPart.THORAX), 0.01f);
		assertTrue(health.isDead());
	}

	@Test
	void armpitAndNeckBypassPacaButAreProtectedByZabralo() {
		PlayerLoadout loadout = new PlayerLoadout();
		loadout.setEquipment(EquipmentSlotType.BODY_ARMOR, new ItemStack(ModItems.PACA_ARMOR)); // Class 2, Thorax + Stomach only
		BodyPartHealth health = new BodyPartHealth();

		// Armpit shot bypasses PACA completely (full 30 dmg to THORAX: 85 -> 55)
		health.applyHit(loadout, HitZone.ARMPIT_LEFT, 30.0f, 10, "Scav", "MP5", "9mm", 8.0f);
		assertEquals(55.0f, health.getHp(BodyPart.THORAX), 0.01f);
		assertFalse(health.getRecentHits().get(0).armorBlocked());

		// Neck shot bypasses PACA completely (full 30 dmg to THORAX: 55 -> 25)
		health.applyHit(loadout, HitZone.NECK, 30.0f, 10, "Scav", "MP5", "9mm", 8.0f);
		assertEquals(25.0f, health.getHp(BodyPart.THORAX), 0.01f);

		// Equip 6B43 Zabralo (Class 6, covers Neck & Armpits) -> blocked!
		loadout.setEquipment(EquipmentSlotType.BODY_ARMOR, new ItemStack(ModItems.ZABRALO_ARMOR));
		health.resetFull();
		health.applyHit(loadout, HitZone.ARMPIT_LEFT, 30.0f, 35, "PMC", "M4", "5.56", 25.0f);
		assertTrue(health.getRecentHits().get(0).armorBlocked());
		assertTrue(health.getHp(BodyPart.THORAX) > 80.0f);
	}
}
