package com.tarkovcraft.arena;

import com.tarkovcraft.entity.TarkovScavEntity;
import com.tarkovcraft.registry.ModEntities;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;

import java.util.List;
import java.util.UUID;

public final class ArenaBossSpawner {
	private static final String LEGACY_BOSS_TAG = "tarkovcraft:arena_boss";

	private ArenaBossSpawner() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (isLegacyBoss(entity)) {
				clearActiveBoss(world, entity.getUuid(), true);
				world.getServer().send(new net.minecraft.server.ServerTask(world.getServer().getTicks(), entity::discard));
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof TarkovScavEntity bot && bot.isBoss()) {
				clearActiveBoss(world, bot.getUuid(), false);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof TarkovScavEntity bot && bot.isBoss() && bot.getWorld() instanceof ServerWorld world) {
				clearActiveBoss(world, bot.getUuid(), false);
			}
		});
	}

	public static boolean isLegacyBoss(Entity entity) {
		return entity instanceof PillagerEntity && entity.getCommandTags().contains(LEGACY_BOSS_TAG);
	}

	private static void clearActiveBoss(ServerWorld world, UUID id, boolean replace) {
		var state = ArenaEventsState.get(world);
		for (var arena : state.arenas()) {
			if (!id.equals(arena.activeBoss)) continue;
			arena.activeBoss = null;
			if (replace) arena.bossSpawned = false;
			state.markDirty();
			return;
		}
	}

	public static boolean spawn(ServerWorld world, ArenaEventsState.Arena arena, List<ServerPlayerEntity> players) {
		if (world.getDifficulty() == Difficulty.PEACEFUL
				|| !FabricLoader.getInstance().isModLoaded("tacz")) return false;
		TarkovScavEntity boss = new TarkovScavEntity(ModEntities.SCAV, world);
		BlockPos center = arena.center();
		for (BlockPos column : BlockPos.iterateOutwards(center, 8, 0, 8)) {
			for (int dy = 0; dy <= 8; dy++) {
				BlockPos down = column.down(dy);
				BlockPos up = column.up(dy);
				BlockPos pos = ArenaEvents.canPlaceAt(world, arena, down, null) ? down
					: dy != 0 && ArenaEvents.canPlaceAt(world, arena, up, null) ? up : null;
				if (pos == null) continue;
				boss.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360, 0);
				if (!world.isSpaceEmpty(boss) || !world.getOtherEntities(boss, boss.getBoundingBox()).isEmpty()) continue;
				if (!boss.prepare(true, point -> arena.contains(point.x, point.z)) || !world.spawnEntity(boss)) return false;
				boss.keepActive(world);
				arena.bossSpawned = true;
				arena.activeBoss = boss.getUuid();
				ArenaEventsState.get(world).markDirty();
				Text msg = Text.translatable("message.tarkovcraft.arena_boss_spawned", pos.getX(), pos.getY(), pos.getZ());
				players.forEach(player -> player.sendMessage(msg, false));
				return true;
			}
		}
		return false;
	}
}
