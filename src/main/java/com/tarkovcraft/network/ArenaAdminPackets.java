package com.tarkovcraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class ArenaAdminPackets {
	public static final Identifier ACTION = id("arena_admin_action");
	public static final Identifier SNAPSHOT = id("arena_admin_snapshot");
	public static final Identifier WAND = id("arena_wand_action");
	public static final Identifier DRAFT = id("arena_wand_draft");
	public static final int BATCH_SIZE = 128;

	public enum Action { REFRESH, CREATE, GIVE_WAND, ADD, REMOVE, TELEPORT, SET }
	public enum Section { BOUNDARY, PMC, SCAV, EXTRACT, AIRDROP, LOOT, POI }
	public enum Setting { DURATION, AIRDROPS, MAX_Y, BOSS, SCAVS, SCAV_COUNT }
	public enum WandAction { SYNC, UNDO, CYCLE_NEXT, CYCLE_PREVIOUS }
	public enum SnapshotPart { START, ARENA, POINTS, END, MESSAGE }
	public record Key(Identifier dimension, String id) {
		public void write(PacketByteBuf out) {
			out.writeIdentifier(dimension).writeString(id);
		}

		public static Key read(PacketByteBuf in) {
			return new Key(in.readIdentifier(), in.readString(48));
		}
	}

	public record Point(Section section, BlockPos pos, String name, boolean loud, double radius) {
		public Point(Section section, BlockPos pos, String name, boolean loud) {
			this(section, pos, name, loud, RaidExtractPackets.DEFAULT_RADIUS);
		}

		public void write(PacketByteBuf out) {
			out.writeEnumConstant(section).writeBlockPos(pos).writeString(name).writeBoolean(loud).writeDouble(radius);
		}

		public static Point read(PacketByteBuf in) {
			return new Point(in.readEnumConstant(Section.class), in.readBlockPos(), in.readString(256), in.readBoolean(),
				in.readDouble());
		}
	}

	public record Details(Key key, String name, boolean boundaryReady, boolean ready, boolean active,
		int duration, boolean airdrops, int maxY, boolean boss, boolean scavs, int scavCount, int bottomY, int topY) {
		public void write(PacketByteBuf out) {
			key.write(out);
			out.writeString(name).writeBoolean(boundaryReady).writeBoolean(ready).writeBoolean(active);
			out.writeVarInt(duration).writeBoolean(airdrops).writeInt(maxY).writeBoolean(boss).writeBoolean(scavs);
			out.writeVarInt(scavCount).writeInt(bottomY).writeInt(topY);
		}

		public static Details read(PacketByteBuf in) {
			return new Details(Key.read(in), in.readString(256), in.readBoolean(), in.readBoolean(), in.readBoolean(),
				in.readVarInt(), in.readBoolean(), in.readInt(), in.readBoolean(), in.readBoolean(), in.readVarInt(),
				in.readInt(), in.readInt());
		}
	}

	private ArenaAdminPackets() { }

	private static Identifier id(String path) {
		return new Identifier("tarkovcraft", path);
	}
}
