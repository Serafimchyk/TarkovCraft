package com.tarkovcraft.health;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Pure 3D geometric hit-zone classifier relative to victim's position, height, and body yaw.
 * Separates Head into HEAD_EYES, HEAD_JAWS, HEAD_EARS, HEAD_TOP; includes abstract NECK strip
 * and ARMPIT_LEFT / ARMPIT_RIGHT flank gaps under the arms.
 */
public final class HitZoneResolver {
	private HitZoneResolver() {
	}

	/**
	 * Resolves exact HitZone from a live Minecraft DamageSource hitting a LivingEntity.
	 */
	public static HitZone resolveFromDamage(LivingEntity victim, DamageSource source) {
		String msgId = source.getName();
		if (source.isIn(DamageTypeTags.IS_FALL) || "fall".equals(msgId) || "stalagmite".equals(msgId)) {
			return victim.getRandom().nextBoolean() ? HitZone.LEFT_LEG : HitZone.RIGHT_LEG;
		}
		if (source.isIn(DamageTypeTags.IS_DROWNING)
			|| source.isIn(DamageTypeTags.IS_FIRE)
			|| source.isIn(DamageTypeTags.IS_FREEZING)
			|| "drown".equals(msgId)
			|| "inWall".equals(msgId)
			|| "starve".equals(msgId)
			|| "magic".equals(msgId)
			|| "wither".equals(msgId)) {
			return HitZone.THORAX;
		}

		List<HitZoneBoxes.ZoneBox> boxes = HitZoneBoxes.computeForEntity(victim);
		Entity direct = source.getSource();
		Entity attacker = source.getAttacker();

		// 1. Projectile / Bullet trajectory raycast against 3D OBB HitZoneBoxes
		if (direct != null && direct != attacker) {
			Vec3d directPos = direct.getPos();
			Vec3d vel = direct.getVelocity();
			Vec3d dir = null;

			if (vel.lengthSquared() > 1e-6) {
				dir = vel.normalize();
				Vec3d origin = directPos.subtract(dir.multiply(0.5));
				double maxDist = Math.max(16.0, vel.length() * 2.0 + 6.0);
				HitZoneBoxes.RayHit hit = HitZoneBoxes.raycastZones(boxes, origin, dir, maxDist);
				if (hit != null) {
					return hit.zone();
				}
			}

			Vec3d prevPos = new Vec3d(direct.prevX, direct.prevY, direct.prevZ);
			Vec3d delta = directPos.subtract(prevPos);
			if (delta.lengthSquared() > 1e-8) {
				Vec3d prevDir = delta.normalize();
				HitZoneBoxes.RayHit hit = HitZoneBoxes.raycastZones(boxes, prevPos, prevDir, delta.length() + 6.0);
				if (hit != null) {
					return hit.zone();
				}
				if (dir == null) {
					dir = prevDir;
				}
			}

			if (attacker != null) {
				Vec3d aEye = attacker.getEyePos();
				Vec3d aLook = attacker.getRotationVec(1.0f);
				HitZoneBoxes.RayHit hit = HitZoneBoxes.raycastZones(boxes, aEye, aLook, attacker.distanceTo(victim) + 6.0);
				if (hit != null) {
					return hit.zone();
				}
			}

			// Geometric fallback along projectile line (never raw source.getPosition() feet-height trap)
			Vec3d fallbackPos;
			if (dir != null) {
				Vec3d center = victim.getPos().add(0, victim.getHeight() * 0.5, 0);
				double projT = center.subtract(directPos).dotProduct(dir);
				fallbackPos = directPos.add(dir.multiply(projT));
			} else {
				Vec3d srcPos = source.getPosition();
				fallbackPos = srcPos != null ? srcPos : directPos;
			}
			return resolveGeometric(
				victim.getPos(), victim.getHeight(),
				victim.getBodyYaw(), victim.getHeadYaw(),
				fallbackPos, dir
			);
		}

		// 2. Melee / direct attacker raycast against 3D OBB HitZoneBoxes
		if (attacker != null) {
			Vec3d aEye = attacker.getEyePos();
			Vec3d aLook = attacker.getRotationVec(1.0f);
			HitZoneBoxes.RayHit hit = HitZoneBoxes.raycastZones(boxes, aEye, aLook, 8.0);
			if (hit != null) {
				return hit.zone();
			}
			Vec3d hitPos = raycastMeleeHitPos(victim, attacker);
			return resolveGeometric(
				victim.getPos(), victim.getHeight(),
				victim.getBodyYaw(), victim.getHeadYaw(),
				hitPos, aLook
			);
		}

		Vec3d hitPos = source.getPosition();
		if (hitPos == null) {
			return HitZone.THORAX;
		}
		return resolveGeometric(
			victim.getPos(), victim.getHeight(),
			victim.getBodyYaw(), victim.getHeadYaw(),
			hitPos, null
		);
	}

	private static Vec3d raycastMeleeHitPos(LivingEntity victim, Entity attacker) {
		Vec3d aEye = attacker.getEyePos();
		Vec3d aLook = attacker.getRotationVec(1.0f);
		Optional<Vec3d> boxHit = victim.getBoundingBox().expand(0.15).raycast(aEye, aEye.add(aLook.multiply(6.0)));
		if (boxHit.isPresent()) {
			return boxHit.get();
		}
		double dist = aEye.distanceTo(victim.getPos().add(0, victim.getHeight() * 0.6, 0));
		return aEye.add(aLook.multiply(Math.max(0.5, dist)));
	}

	/**
	 * Pure deterministic geometric classifier — fully unit-testable without Minecraft world state.
	 */
	public static HitZone resolveGeometric(
		Vec3d victimFeetPos,
		float victimHeight,
		float bodyYawDeg,
		Vec3d hitPos,
		@Nullable Vec3d incomingDir
	) {
		return resolveGeometric(victimFeetPos, victimHeight, bodyYawDeg, bodyYawDeg, hitPos, incomingDir);
	}

	/**
	 * Pure deterministic geometric classifier with independent body and head yaw.
	 *
	 * @param victimFeetPos feet world position
	 * @param victimHeight  entity bounding box height (1.80m standing)
	 * @param bodyYawDeg    Minecraft body yaw in degrees (0 = +Z South, 90 = -X West, 180 = -Z North, -90 = +X East)
	 * @param headYawDeg    Minecraft head yaw in degrees (used for head zones relY >= 0.78)
	 * @param hitPos        world impact point
	 * @param incomingDir   normalized projectile travel vector (attacker -> victim), nullable
	 */
	public static HitZone resolveGeometric(
		Vec3d victimFeetPos,
		float victimHeight,
		float bodyYawDeg,
		float headYawDeg,
		Vec3d hitPos,
		@Nullable Vec3d incomingDir
	) {
		double h = Math.max(0.4, victimHeight);
		double relY = (hitPos.y - victimFeetPos.y) / h;

		float activeYawDeg = relY >= 0.78 ? headYawDeg : bodyYawDeg;

		// Victim forward & right horizontal unit vectors (Minecraft yaw convention)
		double yawRad = Math.toRadians(activeYawDeg);
		double fwdX = -Math.sin(yawRad);
		double fwdZ = Math.cos(yawRad);
		// Right vector = cross(up, forward) -> (-fwdZ, fwdX)
		// Check: yaw=0 -> fwd=(0, 1) South -> player's RIGHT is West (-1, 0) -> (-fwdZ, fwdX) = (-1, 0)!
		double rightX = -fwdZ;
		double rightZ = fwdX;

		double dx = hitPos.x - victimFeetPos.x;
		double dz = hitPos.z - victimFeetPos.z;

		// Local signed coordinates: forward > 0 means front of player, right > 0 means player's right side
		double localFwd = dx * fwdX + dz * fwdZ;
		double localRight = dx * rightX + dz * rightZ;

		// Incoming shot direction relative to victim facing (direction FROM victim TO shooter = -incomingDir)
		double shotFromFrontDot;
		double shotFromSideAbs;
		if (incomingDir != null && (incomingDir.x * incomingDir.x + incomingDir.z * incomingDir.z) > 1e-6) {
			Vec3d horiz = new Vec3d(incomingDir.x, 0, incomingDir.z).normalize();
			// If bullet travels in -forward direction, it hit the FRONT face
			shotFromFrontDot = -(horiz.x * fwdX + horiz.z * fwdZ);
			shotFromSideAbs = Math.abs(horiz.x * rightX + horiz.z * rightZ);
		} else {
			double len = Math.hypot(localFwd, localRight);
			shotFromFrontDot = len > 1e-5 ? (localFwd / len) : 1.0;
			shotFromSideAbs = len > 1e-5 ? Math.abs(localRight / len) : 0.0;
		}

		// 1. HEAD ZONE (relY >= 0.78)
		if (relY >= 0.78) {
			// Crown / top of helmet
			if (relY >= 0.93) {
				return HitZone.HEAD_TOP;
			}
			// Side hit in ear height band -> HEAD_EARS (exposed unless helmet covers ears!)
			if ((shotFromSideAbs >= 0.62 || Math.abs(localRight) > Math.abs(localFwd) * 1.15)
				&& relY >= 0.81 && relY <= 0.92) {
				return HitZone.HEAD_EARS;
			}
			// Front face -> EYES vs JAWS
			if (shotFromFrontDot > 0.25) {
				if (relY >= 0.85) {
					return HitZone.HEAD_EYES; // "Голова — глаза"
				}
				return HitZone.HEAD_JAWS;
			}
			// Back of head -> HEAD_TOP / nape
			return HitZone.HEAD_TOP;
		}

		// 2. ABSTRACT NECK STRIP (thin gap between head & thorax: relY in [0.74, 0.78))
		if (relY >= 0.74) {
			return HitZone.NECK;
		}

		// 3. UPPER TORSO + ARMPITS + ARMS (relY in [0.54, 0.74))
		if (relY >= 0.54) {
			// Side-angle shot into upper flank gap -> ARMPIT (bypasses front/back chest plates!)
			if (relY >= 0.59 && relY <= 0.72 && shotFromSideAbs >= 0.72 && Math.abs(localRight) >= 0.12 && Math.abs(localRight) <= 0.24) {
				return localRight >= 0 ? HitZone.ARMPIT_RIGHT : HitZone.ARMPIT_LEFT;
			}
			if (Math.abs(localRight) > 0.19) {
				return localRight >= 0 ? HitZone.RIGHT_ARM : HitZone.LEFT_ARM;
			}
			return HitZone.THORAX;
		}

		// 4. LOWER TORSO / STOMACH + LOWER ARMS (relY in [0.40, 0.54))
		if (relY >= 0.40) {
			if (Math.abs(localRight) > 0.21) {
				return localRight >= 0 ? HitZone.RIGHT_ARM : HitZone.LEFT_ARM;
			}
			return HitZone.STOMACH;
		}

		// 5. LEGS (relY < 0.40)
		return localRight >= 0 ? HitZone.RIGHT_LEG : HitZone.LEFT_LEG;
	}
}
