package com.tarkovcraft.arena;

import com.tarkovcraft.airdrop.AirdropManager;
import com.tarkovcraft.network.ArenaAdminPackets;
import com.tarkovcraft.network.ArenaAdminPackets.Action;
import com.tarkovcraft.network.ArenaAdminPackets.Details;
import com.tarkovcraft.network.ArenaAdminPackets.Key;
import com.tarkovcraft.network.ArenaAdminPackets.Point;
import com.tarkovcraft.network.ArenaAdminPackets.Section;
import com.tarkovcraft.network.ArenaAdminPackets.Setting;
import com.tarkovcraft.network.ArenaAdminPackets.SnapshotPart;
import com.tarkovcraft.screen.AdminMenuScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ArenaInspectorServer {
	private ArenaInspectorServer() { }

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(ArenaAdminPackets.ACTION, (server, player, handler, buf, sender) -> {
			if (!player.hasPermissionLevel(2) || buf.readableBytes() > 2048) return;
			try {
				int syncId = buf.readVarInt();
				Action action = buf.readEnumConstant(Action.class);
				Key key = action == Action.REFRESH || action == Action.CREATE ? null : Key.read(buf);
				Request request = switch (action) {
					case CREATE -> new Request(action, key, buf.readString(48), buf.readString(80), null, null, 0, null, false);
					case ADD -> new Request(action, key, "", "", buf.readEnumConstant(Section.class), null, 0, null, buf.readBoolean());
					case REMOVE, TELEPORT -> new Request(action, key, "", "", null, null, buf.readVarInt(), Point.read(buf), false);
					case SET -> new Request(action, key, "", "", null, buf.readEnumConstant(Setting.class), buf.readInt(), null, false);
					default -> new Request(action, key, "", "", null, null, 0, null, false);
				};
				if (!buf.isReadable()) server.execute(() -> handle(player, syncId, request));
			} catch (RuntimeException ignored) {
				// Discard truncated or malformed requests.
			}
		});
	}

	private record Request(Action action, Key key, String id, String name, Section section, Setting setting,
		int value, Point point, boolean loud) { }

	private static void handle(ServerPlayerEntity player, int syncId, Request request) {
		if (!player.hasPermissionLevel(2) || !(player.currentScreenHandler instanceof AdminMenuScreenHandler screen)
			|| screen.syncId != syncId || !screen.canUse(player)) return;
		if (request.action() == Action.REFRESH) {
			sendSnapshot(player, syncId);
			return;
		}
		if (request.action() == Action.CREATE) {
			var state = ArenaEventsState.get(player.getServerWorld());
			if (!validName(request.id(), request.name())) feedback(player, "Проверьте ID и название арены.");
			else if (!state.create(request.id(), player.getBlockPos())) feedback(player, "Арена с таким ID уже существует.");
			else {
				state.getArena(request.id()).displayName = request.name();
				save(player, state, "Арена создана.");
			}
			sendSnapshot(player, syncId);
			return;
		}
		ServerWorld world = player.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, request.key().dimension()));
		if (world == null) return;
		ArenaEventsState state = ArenaEventsState.get(world);
		ArenaEventsState.Arena arena = state.getArena(request.key().id());
		if (arena == null) {
			feedback(player, "Арена больше не существует.");
			sendSnapshot(player, syncId);
			return;
		}
		switch (request.action()) {
			case GIVE_WAND -> {
				ArenaBoundaryEditing.give(player, request.key());
				menuFeedback(player, "Маркер привязан к арене «" + request.key().id() + "».");
			}
			case TELEPORT -> {
				if (RaidSession.isParticipant(player)) feedback(player, "Телепорт недоступен во время рейда.");
				else if (hasPoint(arena, request.value(), request.point())) teleport(player, world, request.point().pos());
				else feedback(player, "Точка уже изменилась.");
			}
			case ADD, REMOVE, SET -> {
				if (RaidSession.isActiveArena(player.getServer(), arena)) {
					feedback(player, "Дождитесь окончания рейда.");
				} else if (request.action() == Action.ADD && request.section() == Section.EXTRACT) {
					addExtract(player, world, state, arena, request.loud());
				} else {
					String error = switch (request.action()) {
						case ADD -> addPoint(player, world, arena, request.section(), request.loud());
						case REMOVE -> removePoint(arena, request.value(), request.point());
						case SET -> set(world, arena, request.setting(), request.value()) ? null : "Недопустимое значение.";
						default -> throw new IllegalStateException();
					};
					if (error == null) save(player, state, "Изменения арены сохранены.");
					else feedback(player, error);
				}
				sendSnapshot(player, syncId);
			}
			default -> { }
		}
	}

	static boolean validName(String id, String name) {
		return id.matches("[a-z0-9_-]{1,48}") && !name.isBlank() && name.length() <= 80;
	}

	private static String addPoint(ServerPlayerEntity player, ServerWorld world, ArenaEventsState.Arena arena,
		Section section, boolean loud) {
		if (world != player.getServerWorld()) return "Перейдите в измерение выбранной арены.";
		if (section == Section.BOUNDARY) return "Для расстановки границ используйте маркер.";
		BlockPos pos = player.getBlockPos();
		if (!arena.contains(pos.getX() + 0.5, pos.getZ() + 0.5)) return "Встаньте внутри границ арены.";
		if (section == Section.AIRDROP && !AirdropManager.isSafeArenaPoint(world, arena, pos)) {
			return "Для аирдропа нужна ровная площадка 5×5 под открытым небом в пределах заданной высоты.";
		}
		if (!positions(arena, section).add(pos)) {
			return "Такая точка уже существует.";
		}
		return null;
	}

	private static void addExtract(ServerPlayerEntity player, ServerWorld world, ArenaEventsState state,
		ArenaEventsState.Arena arena, boolean loud) {
		if (world != player.getServerWorld()) {
			feedback(player, "Перейдите в измерение выбранной арены.");
			return;
		}
		BlockPos pos = player.getBlockPos();
		if (!containsPoint(arena, pos)) {
			feedback(player, "Встаньте внутри границ арены.");
			return;
		}
		String prefix = loud ? "loud_" : "quiet_";
		int number = 1;
		while (arena.extracts.containsKey(prefix + number)) number++;
		String name = prefix + number;
		var exit = new ArenaEventsState.Extract(pos, loud);
		arena.extracts.put(name, exit);
		if (!ArenaConfig.save(player.getServer())) {
			arena.extracts.remove(name);
			feedback(player, "JSON арены не сохранён. Проверьте журнал сервера.");
			return;
		}
		state.markDirty();
		var message = ArenaExtractFeedback.confirm(world, player, name, exit);
		player.sendMessage(message, false);
		menuFeedback(player, message.getString());
	}

	private static Set<BlockPos> positions(ArenaEventsState.Arena arena, Section section) {
		return switch (section) {
			case PMC -> arena.pmcSpawns;
			case SCAV -> arena.scavSpawns;
			case AIRDROP -> arena.airdropPoints;
			case LOOT -> arena.lootPoints;
			case POI -> arena.pointsOfInterest;
			default -> throw new IllegalArgumentException("Unexpected section " + section);
		};
	}

	private static boolean hasPoint(ArenaEventsState.Arena arena, int index, Point point) {
		return switch (point.section()) {
			case BOUNDARY -> index >= 0 && index < arena.corners().size() && arena.corners().get(index).equals(point.pos());
			case EXTRACT -> new ArenaEventsState.Extract(point.pos(), point.loud(), point.radius())
				.equals(arena.extracts.get(point.name()));
			default -> positions(arena, point.section()).contains(point.pos());
		};
	}

	private static String removePoint(ArenaEventsState.Arena arena, int index, Point point) {
		if (!hasPoint(arena, index, point)) return "Точка уже изменилась.";
		switch (point.section()) {
			case BOUNDARY -> {
				List<BlockPos> updated = new ArrayList<>(arena.corners());
				updated.remove(index);
				arena.setVertices(updated);
			}
			case EXTRACT -> arena.extracts.remove(point.name());
			default -> positions(arena, point.section()).remove(point.pos());
		}
		return null;
	}

	private static boolean set(ServerWorld world, ArenaEventsState.Arena arena, Setting setting, int value) {
		switch (setting) {
			case DURATION -> {
				if (value < 1 || value > 60) return false;
				arena.durationMinutes = value;
			}
			case MAX_Y -> {
				if (value < world.getBottomY() || value >= world.getTopY()) return false;
				arena.maxGroundY = value;
			}
			case SCAV_COUNT -> {
				if (value < 0 || value > 32) return false;
				arena.scavBotCount = value;
			}
			case AIRDROPS, BOSS, SCAVS -> {
				if (value < 0 || value > 1) return false;
				boolean enabled = value == 1;
				switch (setting) {
					case AIRDROPS -> {
						arena.airdropsEnabled = enabled;
						arena.airdropTicks = 0;
					}
					case BOSS -> arena.bossEnabled = enabled;
					case SCAVS -> arena.scavBotsEnabled = enabled;
					default -> throw new IllegalStateException();
				}
			}
		}
		return true;
	}

	private static void teleport(ServerPlayerEntity player, ServerWorld world, BlockPos point) {
		world.getChunk(point);
		for (BlockPos nearby : BlockPos.iterateOutwards(point, 2, 3, 2)) {
			if (!world.getWorldBorder().contains(nearby) || world.isOutOfHeightLimit(nearby.up())
				|| world.isOutOfHeightLimit(nearby)) continue;
			Vec3d feet = Vec3d.ofBottomCenter(nearby);
			var box = player.getDimensions(player.getPose()).getBoxAt(feet);
			if (!world.isSpaceEmpty(player, box) || !world.getFluidState(nearby).isEmpty()) continue;
			if (!player.isCreative() && !player.isSpectator() && !world.getBlockState(nearby.down()).isSolidBlock(world, nearby.down())) continue;
			player.teleport(world, feet.x, feet.y, feet.z, player.getYaw(), player.getPitch());
			return;
		}
		feedback(player, "Рядом с точкой нет свободного места для телепорта.");
	}

	private static void save(ServerPlayerEntity player, ArenaEventsState state, String message) {
		state.markDirty();
		feedback(player, ArenaConfig.save(player.getServer()) ? message
			: "Состояние мира изменено, но JSON арены не сохранён. Проверьте журнал сервера.");
	}

	private static void feedback(ServerPlayerEntity player, String message) {
		player.sendMessage(Text.literal(message), true);
		menuFeedback(player, message);
	}

	private static void menuFeedback(ServerPlayerEntity player, String message) {
		if (player.currentScreenHandler instanceof AdminMenuScreenHandler screen
			&& ServerPlayNetworking.canSend(player, ArenaAdminPackets.SNAPSHOT)) {
			send(player, part(screen.syncId, SnapshotPart.MESSAGE).writeString(message));
		}
	}

	private static void sendSnapshot(ServerPlayerEntity player, int syncId) {
		if (!ServerPlayNetworking.canSend(player, ArenaAdminPackets.SNAPSHOT)) return;
		send(player, part(syncId, SnapshotPart.START));
		for (ServerWorld world : player.getServer().getWorlds()) {
			ArenaEventsState.get(world).definitions().forEach((id, arena) -> {
				Key key = ArenaBoundaryEditing.key(world, id);
				var details = new Details(key, shortened(arena.displayName, 256), arena.ready(),
					arena.pmcSpawns.stream().anyMatch(pos -> containsPoint(arena, pos))
						&& arena.extracts.values().stream().anyMatch(exit -> containsPoint(arena, exit.pos())),
					RaidSession.isActiveArena(player.getServer(), arena), arena.durationMinutes, arena.airdropsEnabled,
					arena.maxGroundY, arena.bossEnabled, arena.scavBotsEnabled, arena.scavBotCount, world.getBottomY(), world.getTopY());
				var header = part(syncId, SnapshotPart.ARENA);
				details.write(header);
				send(player, header);
				List<Point> points = points(arena);
				for (int start = 0; start < points.size(); start += ArenaAdminPackets.BATCH_SIZE) {
					var out = part(syncId, SnapshotPart.POINTS);
					int end = Math.min(points.size(), start + ArenaAdminPackets.BATCH_SIZE);
					out.writeVarInt(end - start);
					for (int i = start; i < end; i++) points.get(i).write(out);
					send(player, out);
				}
			});
		}
		send(player, part(syncId, SnapshotPart.END));
	}

	private static boolean containsPoint(ArenaEventsState.Arena arena, BlockPos pos) {
		return arena.contains(pos.getX() + 0.5, pos.getZ() + 0.5);
	}

	private static List<Point> points(ArenaEventsState.Arena arena) {
		List<Point> points = new ArrayList<>();
		arena.corners().forEach(pos -> points.add(new Point(Section.BOUNDARY, pos, "", false)));
		arena.extracts.forEach((name, exit) -> points.add(new Point(Section.EXTRACT, exit.pos(), shortened(name, 256),
			exit.loud(), exit.radius())));
		for (Section section : List.of(Section.PMC, Section.SCAV, Section.AIRDROP, Section.LOOT, Section.POI)) {
			positions(arena, section).forEach(pos -> points.add(new Point(section, pos, "", false)));
		}
		return points;
	}

	private static String shortened(String text, int maxLength) {
		return text.length() <= maxLength ? text : text.substring(0, maxLength);
	}

	private static PacketByteBuf part(int syncId, SnapshotPart part) {
		return PacketByteBufs.create().writeVarInt(syncId).writeEnumConstant(part);
	}

	private static void send(ServerPlayerEntity player, PacketByteBuf out) {
		ServerPlayNetworking.send(player, ArenaAdminPackets.SNAPSHOT, out);
	}
}
