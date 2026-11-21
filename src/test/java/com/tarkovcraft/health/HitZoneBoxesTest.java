package com.tarkovcraft.health;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HitZoneBoxesTest {

	private static HitZone raycast(float bodyYaw, float headYaw, Vec3d origin, Vec3d dir) {
		List<HitZoneBoxes.ZoneBox> boxes = HitZoneBoxes.compute(new Vec3d(0, 0, 0), 1.80f, 0.60f, bodyYaw, headYaw);
		HitZoneBoxes.RayHit hit = HitZoneBoxes.raycastZones(boxes, origin, dir, 32.0);
		assertNotNull(hit, "Expected ray to hit a zone box");
		return hit.zone();
	}

	@Test
	void frontalCenterMassShotsHitThoraxAndStomachNotLegs() {
		// 8 blocks in front (+Z -> -Z) like a TaCZ kinetic bullet trajectory
		assertEquals(HitZone.THORAX, raycast(0.0f, 0.0f, new Vec3d(0, 1.15, 8.0), new Vec3d(0, 0, -1)));
		assertEquals(HitZone.STOMACH, raycast(0.0f, 0.0f, new Vec3d(0, 0.85, 8.0), new Vec3d(0, 0, -1)));
	}

	@Test
	void headSubzonesDistinguishEyesJawsEarsAndTop() {
		assertEquals(HitZone.HEAD_EYES, raycast(0.0f, 0.0f, new Vec3d(0, 1.60, 5.0), new Vec3d(0, 0, -1)));
		assertEquals(HitZone.HEAD_JAWS, raycast(0.0f, 0.0f, new Vec3d(0, 1.46, 5.0), new Vec3d(0, 0, -1)));
		// Side shot at ear height (right or left)
		assertEquals(HitZone.HEAD_EARS, raycast(0.0f, 0.0f, new Vec3d(5.0, 1.56, 0.0), new Vec3d(-1, 0, 0)));
		// Back of head and top of crown
		assertEquals(HitZone.HEAD_TOP, raycast(0.0f, 0.0f, new Vec3d(0, 1.56, -5.0), new Vec3d(0, 0, 1)));
		assertEquals(HitZone.HEAD_TOP, raycast(0.0f, 0.0f, new Vec3d(0, 3.0, 0.0), new Vec3d(0, -1, 0)));
	}

	@Test
	void independentHeadYawRotatesHeadZonesWithoutRotatingThorax() {
		// bodyYaw = 0 (South +Z), headYaw = 90 (West -X)
		float bodyYaw = 0.0f;
		float headYaw = 90.0f;

		assertEquals(HitZone.HEAD_EYES, raycast(bodyYaw, headYaw, new Vec3d(-5.0, 1.60, 0.0), new Vec3d(1, 0, 0)));
		assertEquals(HitZone.HEAD_EARS, raycast(bodyYaw, headYaw, new Vec3d(0.0, 1.56, 5.0), new Vec3d(0, 0, -1)));
		assertEquals(HitZone.THORAX, raycast(bodyYaw, headYaw, new Vec3d(0.0, 1.15, 5.0), new Vec3d(0, 0, -1)));
	}

	@Test
	void neckArmsArmpitsAndLegsAreDistinct() {
		assertEquals(HitZone.NECK, raycast(0.0f, 0.0f, new Vec3d(0.0, 1.37, 5.0), new Vec3d(0, 0, -1)));

		// yaw = 0 -> +X is player's LEFT, -X is player's RIGHT
		assertEquals(HitZone.LEFT_ARM, raycast(0.0f, 0.0f, new Vec3d(0.32, 1.10, 5.0), new Vec3d(0, 0, -1)));
		assertEquals(HitZone.RIGHT_ARM, raycast(0.0f, 0.0f, new Vec3d(-0.32, 1.10, 5.0), new Vec3d(0, 0, -1)));

		// Oblique flank shot under the arm (localFwd = +0.13 skips arm's [-0.11..+0.11] and hits armpit!)
		assertEquals(HitZone.ARMPIT_LEFT, raycast(0.0f, 0.0f, new Vec3d(5.0, 1.18, 0.13), new Vec3d(-1, 0, 0)));
		assertEquals(HitZone.ARMPIT_RIGHT, raycast(0.0f, 0.0f, new Vec3d(-5.0, 1.18, 0.13), new Vec3d(1, 0, 0)));

		assertEquals(HitZone.LEFT_LEG, raycast(0.0f, 0.0f, new Vec3d(0.10, 0.40, 5.0), new Vec3d(0, 0, -1)));
		assertEquals(HitZone.RIGHT_LEG, raycast(0.0f, 0.0f, new Vec3d(-0.10, 0.40, 5.0), new Vec3d(0, 0, -1)));
	}

	@Test
	void allBoxesProduceValidEightWorldCorners() {
		List<HitZoneBoxes.ZoneBox> boxes = HitZoneBoxes.compute(new Vec3d(10.0, 64.0, -5.0), 1.80f, 0.60f, 35.0f, -45.0f);
		assertEquals(15, boxes.size());

		Set<HitZone> seen = EnumSet.noneOf(HitZone.class);
		for (HitZoneBoxes.ZoneBox box : boxes) {
			seen.add(box.zone());
			Vec3d[] corners = box.getWorldCorners();
			assertEquals(8, corners.length);
			for (Vec3d c : corners) {
				assertTrue(Double.isFinite(c.x) && Double.isFinite(c.y) && Double.isFinite(c.z));
			}
		}
		assertEquals(HitZone.values().length, seen.size());
	}
}
