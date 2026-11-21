package com.tarkovcraft.health;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Single source of truth for 3D oriented bounding boxes (OBB) of all 13 HitZones.
 * Used both by server-side HitZoneResolver raycasting and client-side HitZoneDebugRenderer.
 */
public final class HitZoneBoxes {
	public static final int[][] EDGE_INDICES = {
		{0, 1}, {1, 2}, {2, 3}, {3, 0},
		{4, 5}, {5, 6}, {6, 7}, {7, 4},
		{0, 4}, {1, 5}, {2, 6}, {3, 7}
	};

	private HitZoneBoxes() {
	}

	public record ZoneBox(
		HitZone zone,
		Vec3d origin,
		float yawDeg,
		double minRight, double maxRight,
		double minY, double maxY,
		double minFwd, double maxFwd
	) {
		/**
		 * Kay-Kajiya 3-axis OBB slab intersection. Returns entry t >= 0 along normalized rayDir, or Double.NaN on miss.
		 */
		public double intersectRay(Vec3d rayOrigin, Vec3d rayDir, double maxDist) {
			double yawRad = Math.toRadians(yawDeg);
			double fwdX = -Math.sin(yawRad);
			double fwdZ = Math.cos(yawRad);
			double rightX = -fwdZ;
			double rightZ = fwdX;

			double dx = rayOrigin.x - origin.x;
			double dy = rayOrigin.y - origin.y;
			double dz = rayOrigin.z - origin.z;

			double ox = dx * rightX + dz * rightZ;
			double oy = dy;
			double oz = dx * fwdX + dz * fwdZ;

			double rdx = rayDir.x * rightX + rayDir.z * rightZ;
			double rdy = rayDir.y;
			double rdz = rayDir.x * fwdX + rayDir.z * fwdZ;

			double tMin = 0.0;
			double tMax = maxDist;

			// Right axis
			if (Math.abs(rdx) < 1e-9) {
				if (ox < minRight || ox > maxRight) return Double.NaN;
			} else {
				double t1 = (minRight - ox) / rdx;
				double t2 = (maxRight - ox) / rdx;
				if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
				tMin = Math.max(tMin, t1);
				tMax = Math.min(tMax, t2);
				if (tMin > tMax) return Double.NaN;
			}

			// Y axis
			if (Math.abs(rdy) < 1e-9) {
				if (oy < minY || oy > maxY) return Double.NaN;
			} else {
				double t1 = (minY - oy) / rdy;
				double t2 = (maxY - oy) / rdy;
				if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
				tMin = Math.max(tMin, t1);
				tMax = Math.min(tMax, t2);
				if (tMin > tMax) return Double.NaN;
			}

			// Forward axis
			if (Math.abs(rdz) < 1e-9) {
				if (oz < minFwd || oz > maxFwd) return Double.NaN;
			} else {
				double t1 = (minFwd - oz) / rdz;
				double t2 = (maxFwd - oz) / rdz;
				if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
				tMin = Math.max(tMin, t1);
				tMax = Math.min(tMax, t2);
				if (tMin > tMax) return Double.NaN;
			}

			return (tMin <= tMax && tMin >= 0.0 && tMin <= maxDist) ? tMin : Double.NaN;
		}

		/**
		 * Returns the 8 world-space vertices of this OBB in standard box order (0..3 bottom, 4..7 top).
		 */
		public Vec3d[] getWorldCorners() {
			double yawRad = Math.toRadians(yawDeg);
			double fwdX = -Math.sin(yawRad);
			double fwdZ = Math.cos(yawRad);
			double rightX = -fwdZ;
			double rightZ = fwdX;

			return new Vec3d[]{
				corner(rightX, rightZ, fwdX, fwdZ, minRight, minY, minFwd),
				corner(rightX, rightZ, fwdX, fwdZ, maxRight, minY, minFwd),
				corner(rightX, rightZ, fwdX, fwdZ, maxRight, minY, maxFwd),
				corner(rightX, rightZ, fwdX, fwdZ, minRight, minY, maxFwd),
				corner(rightX, rightZ, fwdX, fwdZ, minRight, maxY, minFwd),
				corner(rightX, rightZ, fwdX, fwdZ, maxRight, maxY, minFwd),
				corner(rightX, rightZ, fwdX, fwdZ, maxRight, maxY, maxFwd),
				corner(rightX, rightZ, fwdX, fwdZ, minRight, maxY, maxFwd)
			};
		}

		private Vec3d corner(double rx, double rz, double fx, double fz, double r, double y, double f) {
			return new Vec3d(
				origin.x + rx * r + fx * f,
				origin.y + y,
				origin.z + rz * r + fz * f
			);
		}
	}

	public record RayHit(HitZone zone, double distance, Vec3d hitPos) {
	}

	public static List<ZoneBox> computeForEntity(LivingEntity entity) {
		return compute(entity.getPos(), entity.getHeight(), entity.getWidth(), entity.getBodyYaw(), entity.getHeadYaw());
	}

	public static List<ZoneBox> compute(Vec3d feetPos, float height, float width, float bodyYawDeg, float headYawDeg) {
		double h = Math.max(0.4, height);
		double ws = Math.max(0.5, width / 0.60);

		return List.of(
			// Head zones (use headYawDeg)
			box(HitZone.HEAD_TOP, feetPos, headYawDeg, -0.23 * ws, 0.23 * ws, 0.92 * h, 1.02 * h, -0.23 * ws, 0.23 * ws),
			box(HitZone.HEAD_TOP, feetPos, headYawDeg, -0.22 * ws, 0.22 * ws, 0.78 * h, 0.92 * h, -0.23 * ws, 0.02 * ws),
			box(HitZone.HEAD_EYES, feetPos, headYawDeg, -0.19 * ws, 0.19 * ws, 0.85 * h, 0.92 * h, 0.02 * ws, 0.24 * ws),
			box(HitZone.HEAD_JAWS, feetPos, headYawDeg, -0.19 * ws, 0.19 * ws, 0.78 * h, 0.85 * h, 0.02 * ws, 0.24 * ws),
			box(HitZone.HEAD_EARS, feetPos, headYawDeg, -0.25 * ws, -0.19 * ws, 0.81 * h, 0.92 * h, -0.12 * ws, 0.12 * ws),
			box(HitZone.HEAD_EARS, feetPos, headYawDeg, 0.19 * ws, 0.25 * ws, 0.81 * h, 0.92 * h, -0.12 * ws, 0.12 * ws),
			// Body & limbs (use bodyYawDeg)
			box(HitZone.NECK, feetPos, bodyYawDeg, -0.13 * ws, 0.13 * ws, 0.74 * h, 0.78 * h, -0.13 * ws, 0.13 * ws),
			box(HitZone.THORAX, feetPos, bodyYawDeg, -0.20 * ws, 0.20 * ws, 0.54 * h, 0.74 * h, -0.15 * ws, 0.15 * ws),
			box(HitZone.STOMACH, feetPos, bodyYawDeg, -0.20 * ws, 0.20 * ws, 0.40 * h, 0.54 * h, -0.15 * ws, 0.15 * ws),
			box(HitZone.ARMPIT_LEFT, feetPos, bodyYawDeg, -0.23 * ws, -0.17 * ws, 0.60 * h, 0.72 * h, -0.14 * ws, 0.14 * ws),
			box(HitZone.ARMPIT_RIGHT, feetPos, bodyYawDeg, 0.17 * ws, 0.23 * ws, 0.60 * h, 0.72 * h, -0.14 * ws, 0.14 * ws),
			box(HitZone.LEFT_ARM, feetPos, bodyYawDeg, -0.42 * ws, -0.23 * ws, 0.40 * h, 0.74 * h, -0.11 * ws, 0.11 * ws),
			box(HitZone.RIGHT_ARM, feetPos, bodyYawDeg, 0.23 * ws, 0.42 * ws, 0.40 * h, 0.74 * h, -0.11 * ws, 0.11 * ws),
			box(HitZone.LEFT_LEG, feetPos, bodyYawDeg, -0.20 * ws, -0.005 * ws, 0.00 * h, 0.40 * h, -0.14 * ws, 0.14 * ws),
			box(HitZone.RIGHT_LEG, feetPos, bodyYawDeg, 0.005 * ws, 0.20 * ws, 0.00 * h, 0.40 * h, -0.14 * ws, 0.14 * ws)
		);
	}

	private static ZoneBox box(
		HitZone zone, Vec3d origin, float yawDeg,
		double minR, double maxR, double minY, double maxY, double minF, double maxF
	) {
		return new ZoneBox(zone, origin, yawDeg, minR, maxR, minY, maxY, minF, maxF);
	}

	@Nullable
	public static RayHit raycastZones(List<ZoneBox> boxes, Vec3d rayOrigin, Vec3d rayDir, double maxDist) {
		double lenSq = rayDir.lengthSquared();
		if (lenSq < 1e-9 || maxDist <= 0.0) {
			return null;
		}
		Vec3d normDir = rayDir.multiply(1.0 / Math.sqrt(lenSq));

		HitZone bestZone = null;
		double bestT = Double.MAX_VALUE;
		for (ZoneBox box : boxes) {
			double t = box.intersectRay(rayOrigin, normDir, maxDist);
			if (!Double.isNaN(t) && t >= 0.0 && t < bestT) {
				bestT = t;
				bestZone = box.zone();
			}
		}

		if (bestZone == null) {
			return null;
		}
		return new RayHit(bestZone, bestT, rayOrigin.add(normDir.multiply(bestT)));
	}
}
