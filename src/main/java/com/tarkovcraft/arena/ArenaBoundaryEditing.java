package com.tarkovcraft.arena;

import com.tarkovcraft.item.ArenaBoundaryWandItem;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.network.ArenaAdminPackets;
import com.tarkovcraft.network.ArenaAdminPackets.Key;
import com.tarkovcraft.network.ArenaAdminPackets.WandAction;
import com.tarkovcraft.registry.ModItems;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArenaBoundaryEditing {
	private static final Map<UUID, Map<Key, List<BlockPos>>> DRAFTS = new HashMap<>();

	private ArenaBoundaryEditing() { }

	public static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (!stack.isOf(ModItems.ARENA_BOUNDARY_WAND)) return ActionResult.PASS;
			if (!player.hasPermissionLevel(2) || player.isSpectator()) return ActionResult.FAIL;
			if (player instanceof ServerPlayerEntity admin) place(admin, stack, hit.getBlockPos());
			return ActionResult.SUCCESS;
		});
		AttackBlockCallback.EVENT.register((player, world, hand, pos, face) ->
			player.getStackInHand(hand).isOf(ModItems.ARENA_BOUNDARY_WAND) ? ActionResult.FAIL : ActionResult.PASS);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
			player.getStackInHand(hand).isOf(ModItems.ARENA_BOUNDARY_WAND) ? ActionResult.FAIL : ActionResult.PASS);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DRAFTS.remove(handler.player.getUuid()));
		ServerPlayNetworking.registerGlobalReceiver(ArenaAdminPackets.WAND, (server, player, handler, buf, sender) -> {
			if (!player.hasPermissionLevel(2) || buf.readableBytes() != 1) return;
			int raw = buf.readUnsignedByte();
			if (raw >= WandAction.values().length) return;
			WandAction action = WandAction.values()[raw];
			server.execute(() -> {
				if (!player.hasPermissionLevel(2) || player.isSpectator()) return;
				ItemStack stack = ArenaBoundaryWandItem.held(player);
				if (stack.isEmpty()) return;
				switch (action) {
					case SYNC -> syncCurrent(player, stack);
					case UNDO -> undo(player, stack);
					case CYCLE_NEXT -> cycle(player, stack, 1);
					case CYCLE_PREVIOUS -> cycle(player, stack, -1);
				}
			});
		});
	}

	static Key key(ServerWorld world, String id) {
		return new Key(world.getRegistryKey().getValue(), id);
	}

	static void give(ServerPlayerEntity player, Key key) {
		ItemStack stack = ArenaBoundaryWandItem.held(player);
		boolean existing = !stack.isEmpty();
		if (!existing) stack = new ItemStack(ModItems.ARENA_BOUNDARY_WAND);
		ArenaBoundaryWandItem.bind(stack, key);
		if (!existing) {
			if (player.isCreative()) {
				int slot = player.getInventory().getEmptySlot();
				if (slot >= 0) player.getInventory().setStack(slot, stack);
				else player.dropItem(stack, false);
			} else if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
		}
		syncInventory(player);
		sync(player, key, draft(player, key));
		feedback(player, "Маркер привязан к арене «" + key.id() + "».", SoundEvents.UI_BUTTON_CLICK.value());
	}

	public static void cycle(ServerPlayerEntity player, ItemStack stack, int direction) {
		if (!player.hasPermissionLevel(2) || player.isSpectator()) return;
		var world = player.getServerWorld();
		List<String> ids = List.copyOf(ArenaEventsState.get(world).definitions().keySet());
		if (ids.isEmpty()) {
			feedback(player, "В этом измерении нет арен.", null);
			return;
		}
		Key previous = ArenaBoundaryWandItem.binding(stack);
		int index = previous != null && previous.dimension().equals(world.getRegistryKey().getValue()) ? ids.indexOf(previous.id()) : -1;
		int nextIndex = index < 0 ? (direction > 0 ? 0 : ids.size() - 1) : Math.floorMod(index + direction, ids.size());
		Key next = key(world, ids.get(nextIndex));
		ArenaBoundaryWandItem.bind(stack, next);
		syncInventory(player);
		sync(player, next, draft(player, next));
		feedback(player, "Арена «" + next.id() + "».", SoundEvents.UI_BUTTON_CLICK.value());
	}

	static boolean closes(BlockPos first, BlockPos clicked) {
		return first.getSquaredDistance(clicked) <= 2.25;
	}

	private static void place(ServerPlayerEntity player, ItemStack stack, BlockPos pos) {
		Key key = editableKey(player, stack);
		if (key == null) return;
		List<BlockPos> points = DRAFTS.computeIfAbsent(player.getUuid(), unused -> new HashMap<>())
			.computeIfAbsent(key, unused -> new ArrayList<>());
		if (points.size() >= 3 && closes(points.get(0), pos)) {
			if (!ArenaPolygon.isSimple(points)) {
				feedback(player, "Граница пересекает себя или лежит на одной прямой. Отмените лишние точки.", null);
				return;
			}
			var state = ArenaEventsState.get(player.getServerWorld());
			var arena = state.getArena(key.id());
			List<BlockPos> previous = arena.corners();
			boolean previousReady = arena.ready();
			arena.setVertices(points, true);
			if (!ArenaConfig.save(player.getServer())) {
				arena.setVertices(previous, previousReady);
				feedback(player, "Не удалось сохранить границы. Черновик сохранён до выхода.", null);
				return;
			}
			state.markDirty();
			discard(player, key);
			sync(player, key, List.of());
			feedback(player, "Границы арены «" + key.id() + "» сохранены (" + points.size() + " вершин).",
				SoundEvents.ENTITY_PLAYER_LEVELUP);
			return;
		}
		if (points.stream().anyMatch(point -> point.getX() == pos.getX() && point.getZ() == pos.getZ())) {
			feedback(player, "В этой колонне уже есть вершина.", null);
			return;
		}
		points.add(pos.toImmutable());
		sync(player, key, points);
		feedback(player, "Точка #" + points.size() + " добавлена", SoundEvents.UI_BUTTON_CLICK.value());
	}

	private static void undo(ServerPlayerEntity player, ItemStack stack) {
		Key key = editableKey(player, stack);
		if (key == null) return;
		List<BlockPos> points = draft(player, key);
		if (points.isEmpty()) {
			feedback(player, "Черновик пуст.", null);
			return;
		}
		points.remove(points.size() - 1);
		if (points.isEmpty()) discard(player, key);
		sync(player, key, points);
		feedback(player, "Последняя точка отменена. Вершин: " + points.size(), SoundEvents.UI_BUTTON_CLICK.value());
	}

	public static void clear(ServerPlayerEntity player, ItemStack stack) {
		Key key = editableKey(player, stack);
		if (key == null) return;
		discard(player, key);
		sync(player, key, List.of());
		feedback(player, "Черновик очищен.", SoundEvents.UI_BUTTON_CLICK.value());
	}

	private static Key editableKey(ServerPlayerEntity player, ItemStack stack) {
		if (!player.hasPermissionLevel(2) || player.isSpectator()) return null;
		Key key = ArenaBoundaryWandItem.binding(stack);
		var world = player.getServerWorld();
		var arena = key != null && key.dimension().equals(world.getRegistryKey().getValue())
			? ArenaEventsState.get(world).getArena(key.id()) : null;
		if (arena == null) {
			feedback(player, "Маркер не привязан к арене в этом измерении. ПКМ по воздуху переключает арену.", null);
			return null;
		}
		if (RaidSession.isActiveArena(player.getServer(), arena)) {
			feedback(player, "Дождитесь окончания рейда.", null);
			return null;
		}
		return key;
	}

	private static List<BlockPos> draft(ServerPlayerEntity player, Key key) {
		return DRAFTS.getOrDefault(player.getUuid(), Map.of()).getOrDefault(key, List.of());
	}

	private static void discard(ServerPlayerEntity player, Key key) {
		Map<Key, List<BlockPos>> drafts = DRAFTS.get(player.getUuid());
		if (drafts != null) {
			drafts.remove(key);
			if (drafts.isEmpty()) DRAFTS.remove(player.getUuid());
		}
	}

	private static void syncCurrent(ServerPlayerEntity player, ItemStack stack) {
		Key key = ArenaBoundaryWandItem.binding(stack);
		if (key != null && key.dimension().equals(player.getWorld().getRegistryKey().getValue())) {
			sync(player, key, draft(player, key));
		}
	}

	private static void sync(ServerPlayerEntity player, Key key, List<BlockPos> points) {
		if (!ServerPlayNetworking.canSend(player, ArenaAdminPackets.DRAFT)) return;
		for (int start = 0; start == 0 || start < points.size(); start += ArenaAdminPackets.BATCH_SIZE) {
			var out = PacketByteBufs.create();
			key.write(out);
			int end = Math.min(points.size(), start + ArenaAdminPackets.BATCH_SIZE);
			out.writeBoolean(start == 0).writeBoolean(end == points.size());
			out.writeVarInt(end - start);
			for (int i = start; i < end; i++) out.writeBlockPos(points.get(i));
			ServerPlayNetworking.send(player, ArenaAdminPackets.DRAFT, out);
		}
	}

	private static void syncInventory(ServerPlayerEntity player) {
		player.getInventory().markDirty();
		if (!player.isCreative() && player instanceof PlayerLoadoutHolder holder) holder.tarkovcraft$syncLoadout();
		player.playerScreenHandler.sendContentUpdates();
	}

	private static void feedback(ServerPlayerEntity player, String message, SoundEvent sound) {
		player.sendMessage(Text.literal(message), true);
		if (sound != null) player.playSound(sound, 0.45f, 1.2f);
	}
}
