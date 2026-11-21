package com.tarkovcraft.worldgen;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent world registry of generated TarkovCraft structures so /tarkov locate can find them.
 */
public final class TarkovStructureRegistry extends PersistentState {
	private static final String STATE_KEY = "tarkovcraft_structures";
	private static final int MAX_PER_KIND = 512;

	public record FoundStructure(TarkovStructureFeature.StructureKind kind, BlockPos pos) {
	}

	private final Map<TarkovStructureFeature.StructureKind, List<BlockPos>> positions =
		new EnumMap<>(TarkovStructureFeature.StructureKind.class);

	public TarkovStructureRegistry() {
		for (TarkovStructureFeature.StructureKind kind : TarkovStructureFeature.StructureKind.values()) {
			positions.put(kind, new ArrayList<>());
		}
	}

	public static TarkovStructureRegistry get(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(
			TarkovStructureRegistry::fromNbt,
			TarkovStructureRegistry::new,
			STATE_KEY
		);
	}

	public synchronized void record(TarkovStructureFeature.StructureKind kind, BlockPos pos) {
		if (kind == null || pos == null) {
			return;
		}
		List<BlockPos> list = positions.get(kind);
		BlockPos imm = pos.toImmutable();
		if (list.contains(imm)) {
			return;
		}
		if (list.size() >= MAX_PER_KIND) {
			list.remove(0);
		}
		list.add(imm);
		markDirty();
	}

	@Nullable
	public synchronized FoundStructure findNearest(
		@Nullable TarkovStructureFeature.StructureKind filterKind,
		BlockPos origin
	) {
		FoundStructure best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (TarkovStructureFeature.StructureKind kind : TarkovStructureFeature.StructureKind.values()) {
			if (filterKind != null && kind != filterKind) {
				continue;
			}
			for (BlockPos pos : positions.get(kind)) {
				double distSq = pos.getSquaredDistance(origin);
				if (distSq < bestDistSq) {
					bestDistSq = distSq;
					best = new FoundStructure(kind, pos);
				}
			}
		}
		return best;
	}

	@Override
	public synchronized NbtCompound writeNbt(NbtCompound nbt) {
		for (TarkovStructureFeature.StructureKind kind : TarkovStructureFeature.StructureKind.values()) {
			NbtList list = new NbtList();
			for (BlockPos pos : positions.get(kind)) {
				NbtCompound entry = new NbtCompound();
				entry.putInt("x", pos.getX());
				entry.putInt("y", pos.getY());
				entry.putInt("z", pos.getZ());
				list.add(entry);
			}
			nbt.put(kind.name(), list);
		}
		return nbt;
	}

	public static TarkovStructureRegistry fromNbt(NbtCompound nbt) {
		TarkovStructureRegistry reg = new TarkovStructureRegistry();
		for (TarkovStructureFeature.StructureKind kind : TarkovStructureFeature.StructureKind.values()) {
			if (!nbt.contains(kind.name(), NbtElement.LIST_TYPE)) {
				continue;
			}
			NbtList list = nbt.getList(kind.name(), NbtElement.COMPOUND_TYPE);
			List<BlockPos> target = reg.positions.get(kind);
			for (int i = 0; i < list.size() && target.size() < MAX_PER_KIND; i++) {
				NbtCompound c = list.getCompound(i);
				target.add(new BlockPos(c.getInt("x"), c.getInt("y"), c.getInt("z")));
			}
		}
		return reg;
	}
}
