package com.tarkovcraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class RaidExtractPackets {
	public static final Identifier STATE = new Identifier("tarkovcraft", "raid_extract_state");
	public static final Identifier PLACED = new Identifier("tarkovcraft", "arena_extract_placed");
	public static final double DEFAULT_RADIUS = 3.5;
	public static final double VERTICAL_REACH = 2;

	public enum Part { START, EXITS, ACTIVATION, PROGRESS, END }

	public record Exit(String name, BlockPos pos, boolean loud, double radius) {
		public void write(PacketByteBuf buf) {
			buf.writeString(name, 256).writeBlockPos(pos).writeBoolean(loud).writeDouble(radius);
		}

		public static Exit read(PacketByteBuf buf) {
			return new Exit(buf.readString(256), buf.readBlockPos(), buf.readBoolean(), buf.readDouble());
		}
	}

	public static double validRadius(double radius) {
		return Double.isFinite(radius) && radius >= 1 && radius <= 32 ? radius : DEFAULT_RADIUS;
	}

	public static boolean contains(BlockPos pos, double radius, Vec3d feet) {
		double x = feet.x - (pos.getX() + 0.5);
		double z = feet.z - (pos.getZ() + 0.5);
		return Math.abs(feet.y - pos.getY()) <= VERTICAL_REACH && x * x + z * z <= radius * radius;
	}

	private RaidExtractPackets() { }
}
