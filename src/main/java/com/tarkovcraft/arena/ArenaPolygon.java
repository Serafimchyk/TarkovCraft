package com.tarkovcraft.arena;

import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class ArenaPolygon {
	private ArenaPolygon() { }

	// Old saves treated the four slots as an unordered set.
	static List<BlockPos> migrateCorners(List<BlockPos> slots) {
		List<BlockPos> corners = slots.stream().filter(Objects::nonNull).toList();
		if (corners.size() != 4) return corners;
		double x = corners.stream().mapToInt(BlockPos::getX).average().orElseThrow();
		double z = corners.stream().mapToInt(BlockPos::getZ).average().orElseThrow();
		return corners.stream().sorted(Comparator.comparingDouble(p -> Math.atan2(p.getZ() - z, p.getX() - x))).toList();
	}

	static boolean isSimple(List<BlockPos> points) {
		int n = points.size();
		if (n < 3) return false;
		long area = 0;
		BlockPos origin = points.get(0);
		for (int i = 0; i < n; i++) {
			BlockPos a = points.get(i);
			BlockPos b = points.get((i + 1) % n);
			BlockPos c = points.get((i + 2) % n);
			if (a.getX() == b.getX() && a.getZ() == b.getZ()) return false;
			if (cross(a, b, c) == 0 && (onSegment(a, b, c) || onSegment(b, c, a))) return false;
			area += cross(origin, a, b);
			for (int j = i + 2; j < n; j++) {
				if (i == 0 && j == n - 1) continue;
				if (intersects(a, b, points.get(j), points.get((j + 1) % n))) return false;
			}
		}
		return area != 0;
	}

	private static boolean intersects(BlockPos a, BlockPos b, BlockPos c, BlockPos d) {
		long abc = cross(a, b, c);
		long abd = cross(a, b, d);
		long cda = cross(c, d, a);
		long cdb = cross(c, d, b);
		if (abc == 0 && onSegment(a, b, c) || abd == 0 && onSegment(a, b, d)
			|| cda == 0 && onSegment(c, d, a) || cdb == 0 && onSegment(c, d, b)) return true;
		return Long.signum(abc) * Long.signum(abd) < 0 && Long.signum(cda) * Long.signum(cdb) < 0;
	}

	private static long cross(BlockPos a, BlockPos b, BlockPos p) {
		return ((long) b.getX() - a.getX()) * ((long) p.getZ() - a.getZ())
			- ((long) b.getZ() - a.getZ()) * ((long) p.getX() - a.getX());
	}

	private static boolean onSegment(BlockPos a, BlockPos b, BlockPos p) {
		return between(p.getX(), a.getX(), b.getX()) && between(p.getZ(), a.getZ(), b.getZ());
	}

	static boolean contains(List<BlockPos> points, double x, double z) {
		boolean inside = false;
		for (int i = 0; i < points.size(); i++) {
			BlockPos a = points.get(i);
			BlockPos b = points.get((i + 1) % points.size());
			double dx = b.getX() - (double) a.getX();
			double dz = b.getZ() - (double) a.getZ();
			double side = dx * (z - a.getZ()) - dz * (x - a.getX());
			if (Math.abs(side) < 1.0e-8 && between(x, a.getX(), b.getX()) && between(z, a.getZ(), b.getZ())) return true;
			if ((a.getZ() > z) != (b.getZ() > z) && (side > 0) == (dz > 0)) inside = !inside;
		}
		return inside;
	}

	private static boolean between(double value, int first, int second) {
		return value >= Math.min(first, second) && value <= Math.max(first, second);
	}
}
