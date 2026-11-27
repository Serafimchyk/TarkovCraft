package com.tarkovcraft.arena;

import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.container.VanillaContainerBridge;
import com.tarkovcraft.entity.AirdropEntity;
import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.entity.SleeperPlayerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

final class ArenaResetService {
	private ArenaResetService() { }

	static boolean isTemporary(Entity entity) {
		return entity instanceof CorpseEntity || entity instanceof SleeperPlayerEntity || entity instanceof ItemEntity
			|| entity instanceof AirdropEntity || entity instanceof MobEntity;
	}

	static BlockPos openable(BlockPos pos, BlockState state) {
		if (state.getBlock() instanceof DoorBlock) {
			return state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos.toImmutable();
		}
		return state.getBlock() instanceof TrapdoorBlock || state.getBlock() instanceof FenceGateBlock
			? pos.toImmutable() : null;
	}

	static void reset(ServerWorld world, ArenaEventsState.Arena arena, Set<BlockPos> interactedOpenables) {
		for (BlockPos pos : interactedOpenables) {
			BlockState state = world.getBlockState(pos);
			if (openable(pos, state) != null && state.get(Properties.OPEN)) {
				world.setBlockState(pos, state.with(Properties.OPEN, false), Block.NOTIFY_ALL);
			}
		}
		interactedOpenables.clear();

		var state = ArenaEventsState.get(world);
		if (arena.cratePos != null) {
			if (!HackableCrateEvent.clearRetiredCrate(world, arena.cratePos)) state.retiredCrates.add(arena.cratePos);
			arena.cratePos = null;
		}
		var containers = new java.util.HashSet<String>();
		for (BlockPos pos : arena.lootPoints) {
			if (!arena.contains(pos.getX() + 0.5, pos.getZ() + 0.5)) continue;
			if (world.getBlockEntity(pos) instanceof SearchableLootBlockEntity crate) {
				if (!crate.isEventCrate()) crate.refillLoot(world.random);
			} else {
				var blockState = world.getBlockState(pos);
				if (VanillaContainerBridge.isSupportedBlock(blockState.getBlock())
					&& containers.add(VanillaContainerBridge.resolveCanonicalKey(pos, blockState, new UUID(0, 0)))) {
					VanillaContainerBridge.refillArenaContainer(world, pos);
				}
			}
		}
		var obsolete = new ArrayList<Entity>();
		if (arena.activeBoss != null && world.getEntity(arena.activeBoss) == null) {
			state.retiredEntities.add(arena.activeBoss);
		}
		for (Entity entity : world.iterateEntities()) {
			if (entity.getUuid().equals(arena.activeBoss)
				|| isTemporary(entity) && arena.contains(entity.getX(), entity.getZ())) obsolete.add(entity);
		}
		obsolete.forEach(Entity::discard);
		arena.airdropTicks = 0;
		arena.crateTicks = 0;
		arena.bossRetryTicks = 0;
		arena.bossSpawned = false;
		arena.activeBoss = null;
		state.markDirty();
	}
}
