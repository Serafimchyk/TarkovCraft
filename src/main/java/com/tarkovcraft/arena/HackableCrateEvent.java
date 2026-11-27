package com.tarkovcraft.arena;

import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.UUID;

public final class HackableCrateEvent {
	private static final int HACK_TICKS = 45 * 20;
	private UUID hacker;
	private int remaining;

	public static boolean activate(ServerWorld world, ArenaEventsState.Arena arena, List<ServerPlayerEntity> players) {
		BlockPos oldPos = arena.cratePos;
		boolean oldChunkLoaded = oldPos != null && world.isChunkLoaded(oldPos.getX() >> 4, oldPos.getZ() >> 4);
		SearchableLootBlockEntity oldCrate = oldChunkLoaded
			&& world.getBlockEntity(oldPos) instanceof SearchableLootBlockEntity crate && crate.isEventCrate() ? crate : null;
		if (oldCrate != null && oldCrate.isHackInProgress()) return false;

		List<BlockPos> points = arena.pointsOfInterest.stream()
			.map(pos -> crateAtPoi(world, arena, pos, oldCrate)).filter(pos -> pos != null).distinct().toList();
		BlockPos pos = points.isEmpty() ? findCentralSite(world, arena, oldCrate) : points.get(world.random.nextInt(points.size()));
		if (pos == null) return false;
		if (pos.equals(oldPos) && oldCrate != null) world.removeBlock(oldPos, false);
		if (!world.setBlockState(pos, ModBlocks.AIRDROP_CRATE.getDefaultState(), Block.NOTIFY_ALL)) return false;
		if (!(world.getBlockEntity(pos) instanceof SearchableLootBlockEntity crate)) return false;
		crate.lockForEvent();
		if (oldCrate != null && !pos.equals(oldPos)) world.removeBlock(oldPos, false);
		ArenaEventsState state = ArenaEventsState.get(world);
		if (oldPos != null && !oldChunkLoaded) state.retiredCrates.add(oldPos);
		arena.cratePos = pos;
		state.markDirty();

		world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 5.0f, 0.55f);
		Text announcement = Text.translatable("message.tarkovcraft.hack_crate_activated", pos.getX(), pos.getY(), pos.getZ())
			.append(". ").append(Text.translatable("message.tarkovcraft.hack_crate_prompt"));
		players.forEach(player -> player.sendMessage(announcement, false));
		return true;
	}

	static boolean clearRetiredCrate(ServerWorld world, BlockPos pos) {
		if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
		if (world.getBlockEntity(pos) instanceof SearchableLootBlockEntity crate && crate.isEventCrate()) {
			world.removeBlock(pos, false);
		}
		return true;
	}

	public void start(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		if (!canHack(world, pos, player)) return;
		if (hacker != null && !hacker.equals(player.getUuid())) {
			player.sendMessage(Text.translatable("message.tarkovcraft.hack_crate_busy"), true);
			return;
		}
		if (hacker == null) {
			hacker = player.getUuid();
			remaining = HACK_TICKS;
			reportProgress(world, pos, player);
		}
	}

	public boolean isInProgress() {
		return hacker != null;
	}

	public void tick(ServerWorld world, BlockPos pos, SearchableLootBlockEntity crate) {
		if (hacker == null) return;
		ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(hacker);
		if (player == null || !canHack(world, pos, player)) {
			hacker = null;
			if (player != null) player.sendMessage(Text.translatable("message.tarkovcraft.hack_crate_interrupted"), true);
			return;
		}
		if (--remaining == 0) {
			hacker = null;
			crate.unlockEventCrate(world.random);
			world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 0.9f, 1.25f);
			player.sendMessage(Text.translatable("message.tarkovcraft.hack_crate_complete"), true);
		} else if (remaining % 20 == 0) {
			reportProgress(world, pos, player);
		}
	}

	private void reportProgress(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		player.sendMessage(Text.translatable("message.tarkovcraft.hack_crate_progress", remaining / 20), true);
		world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS, 0.8f, 1.2f);
	}

	private static boolean canHack(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		if (player.getServerWorld() != world || !player.isAlive() || player.isSpectator()
			|| player.squaredDistanceTo(pos.toCenterPos()) > 4.5 * 4.5) return false;
		var hit = world.raycast(new RaycastContext(player.getEyePos(), pos.toCenterPos(),
			RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
	}

	private static BlockPos crateAtPoi(ServerWorld world, ArenaEventsState.Arena arena, BlockPos pos, SearchableLootBlockEntity old) {
		if (ArenaEvents.canPlaceAt(world, arena, pos, old)) return pos;
		if (ArenaEvents.canPlaceAt(world, arena, pos.up(), old)) return pos.up();
		return null;
	}

	private static BlockPos findCentralSite(ServerWorld world, ArenaEventsState.Arena arena, SearchableLootBlockEntity old) {
		BlockPos center = arena.center();
		for (int attempt = 0; attempt < 36; attempt++) {
			BlockPos column = attempt == 0 ? center : center.add(world.random.nextInt(25) - 12, 0, world.random.nextInt(25) - 12);
			for (int dy = 0; dy <= 8; dy++) {
				BlockPos down = column.down(dy);
				if (ArenaEvents.canPlaceAt(world, arena, down, old)) return down;
				BlockPos up = column.up(dy);
				if (dy != 0 && ArenaEvents.canPlaceAt(world, arena, up, old)) return up;
			}
		}
		return null;
	}
}
