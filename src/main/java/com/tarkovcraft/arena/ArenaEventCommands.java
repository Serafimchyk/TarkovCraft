package com.tarkovcraft.arena;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tarkovcraft.airdrop.AirdropManager;
import com.tarkovcraft.network.RaidExtractPackets;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ArenaEventCommands {
	private ArenaEventCommands() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> commands() {
		return literal("arena")
			.then(literal("create").then(argument("id", StringArgumentType.word())
				.executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "id")))
				.then(argument("displayName", StringArgumentType.greedyString())
					.executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "displayName"))))))
			.then(literal("set_duration").then(argument("id", StringArgumentType.word())
				.then(argument("minutes", IntegerArgumentType.integer(1, 60)).executes(ctx -> update(ctx, arena -> {
					arena.durationMinutes = IntegerArgumentType.getInteger(ctx, "minutes");
					return "Raid duration saved.";
				})))))
			.then(addPoint("add_spawn", arena -> arena.pmcSpawns))
			.then(addPoint("add_scav_spawn", arena -> arena.scavSpawns))
			.then(literal("spawn_scav").then(argument("pos", BlockPosArgumentType.blockPos()).executes(ctx -> {
				var source = ctx.getSource();
				var pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
				var failure = ArenaScavSpawner.spawnManual(source.getWorld(), pos);
				if (failure != null) {
					source.sendError(failure);
					return 0;
				}
				source.sendFeedback(() -> Text.translatable("arena.tarkovcraft.scav.spawned",
					pos.getX(), pos.getY(), pos.getZ()), true);
				return 1;
			})))
			.then(addPoint("add_loot_point", arena -> arena.lootPoints))
			.then(literal("add_extract").then(argument("id", StringArgumentType.word())
				.then(argument("name", StringArgumentType.string())
					.then(argument("type", StringArgumentType.word())
						.suggests((ctx, builder) -> CommandSource.suggestMatching(java.util.List.of("quiet", "loud"), builder))
						.executes(ctx -> addExtract(ctx, RaidExtractPackets.DEFAULT_RADIUS))
						.then(argument("radius", DoubleArgumentType.doubleArg(1, 32))
							.executes(ctx -> addExtract(ctx, DoubleArgumentType.getDouble(ctx, "radius"))))))))
			.then(literal("set_lobby_anchor").executes(ctx -> {
				var source = ctx.getSource();
				if (RaidSession.isActive(source.getServer())) return error(ctx, "Wait until the raid ends.");
				BlockPos pos = position(ctx);
				if (ArenaEventsState.get(source.getWorld()).arenas().stream()
					.anyMatch(arena -> arena.contains(pos.getX() + 0.5, pos.getZ() + 0.5))) {
					return error(ctx, "The lobby anchor must be outside the arenas.");
				}
				PlayerProfileState.get(source.getServer()).setLobby(source.getWorld().getRegistryKey().getValue().toString(), pos);
				return save(ctx, "Lobby anchor saved.");
			}))
			.then(literal("set_corner").then(argument("id", StringArgumentType.word())
				.then(argument("corner", IntegerArgumentType.integer(1)).executes(ctx -> update(ctx, arena -> {
					if (!arena.setCorner(IntegerArgumentType.getInteger(ctx, "corner") - 1, position(ctx))) return null;
					return arena.ready() ? "Arena boundary saved." : "Vertex saved. A simple polygon needs at least three vertices.";
				}, "Add vertices in order without skipping an index.")))))
			.then(literal("wand").then(argument("id", StringArgumentType.word())
				.suggests((ctx, builder) -> CommandSource.suggestMatching(
					ArenaEventsState.get(ctx.getSource().getWorld()).definitions().keySet(), builder))
				.executes(ctx -> {
					String id = StringArgumentType.getString(ctx, "id");
					var player = ctx.getSource().getPlayerOrThrow();
					var world = player.getServerWorld();
					if (ArenaEventsState.get(world).getArena(id) == null) return error(ctx, "Unknown arena in this dimension.");
					ArenaBoundaryEditing.give(player, ArenaBoundaryEditing.key(world, id));
					return 1;
				})))
			.then(literal("add_airdrop_point").then(argument("id", StringArgumentType.word()).executes(ctx -> update(ctx, arena -> {
				BlockPos pos = position(ctx);
				if (!AirdropManager.isSafeArenaPoint(ctx.getSource().getWorld(), arena, pos)) return null;
				arena.airdropPoints.add(pos);
				return "Airdrop point saved.";
			}, "Stand at a clear, level 5×5 site inside the arena, below max_ground_y and open to the sky."))))
			.then(literal("add_poi").then(argument("id", StringArgumentType.word()).executes(ctx -> update(ctx, arena -> {
				BlockPos pos = position(ctx);
				if (!arena.contains(pos.getX(), pos.getZ())) return null;
				arena.pointsOfInterest.add(pos);
				return "Crate POI saved.";
			}, "The POI must be inside the arena."))))
			.then(literal("set_max_ground_y").then(argument("id", StringArgumentType.word())
				.then(argument("y", IntegerArgumentType.integer()).executes(ctx -> update(ctx, arena -> {
					int y = IntegerArgumentType.getInteger(ctx, "y");
					var world = ctx.getSource().getWorld();
					if (y < world.getBottomY() || y >= world.getTopY()) return null;
					arena.maxGroundY = y;
					return "Maximum airdrop ground height set to " + y + ".";
				}, "Height must be within the world's build limits.")))))
			.then(toggle("set_airdrops", (arena, enabled) -> {
				arena.airdropsEnabled = enabled;
				arena.airdropTicks = 0;
			}))
			.then(toggle("set_boss", (arena, enabled) -> arena.bossEnabled = enabled))
			.then(literal("reset_boss").then(argument("id", StringArgumentType.word()).executes(ctx -> update(ctx, arena -> {
				if (arena.activeBoss != null) return null;
				arena.bossSpawned = false;
				arena.bossRetryTicks = 0;
				return "Boss spawn reset.";
			}, "The previous boss is still active."))));
	}

	private static int create(CommandContext<ServerCommandSource> ctx, String displayName) {
		String id = StringArgumentType.getString(ctx, "id");
		if (!ArenaInspectorServer.validName(id, displayName)) {
			return error(ctx, "Use an ID of 1–48 lowercase letters, digits, underscores or hyphens and a name up to 80 characters.");
		}
		var state = ArenaEventsState.get(ctx.getSource().getWorld());
		if (!state.create(id, position(ctx))) return error(ctx, "Arena already exists in this dimension.");
		state.getArena(id).displayName = displayName;
		return save(ctx, "Arena created. Get a boundary marker with /tarkov arena wand " + id + ".");
	}

	private static LiteralArgumentBuilder<ServerCommandSource> addPoint(String command,
		java.util.function.Function<ArenaEventsState.Arena, java.util.Set<BlockPos>> points) {
		return literal(command).then(argument("id", StringArgumentType.word()).executes(ctx -> update(ctx, arena -> {
			BlockPos pos = position(ctx);
			if (!arena.contains(pos.getX() + 0.5, pos.getZ() + 0.5)) return null;
			points.apply(arena).add(pos);
			return "Point saved.";
		}, "The point must be inside the arena.")));
	}

	private static LiteralArgumentBuilder<ServerCommandSource> toggle(
		String command, java.util.function.BiConsumer<ArenaEventsState.Arena, Boolean> setter
	) {
		return literal(command).then(argument("id", StringArgumentType.word())
			.then(argument("enabled", StringArgumentType.word())
				.suggests((ctx, builder) -> CommandSource.suggestMatching(java.util.List.of("on", "off"), builder))
				.executes(ctx -> {
					String value = StringArgumentType.getString(ctx, "enabled");
					if (!value.equals("on") && !value.equals("off")) return error(ctx, "Expected on or off.");
					return update(ctx, arena -> {
						setter.accept(arena, value.equals("on"));
						return command + " → " + value;
					});
				})));
	}

	private static int addExtract(CommandContext<ServerCommandSource> ctx, double radius) {
		var source = ctx.getSource();
		var world = source.getWorld();
		var state = ArenaEventsState.get(world);
		var arena = state.getArena(StringArgumentType.getString(ctx, "id"));
		if (arena == null) return error(ctx, "Unknown arena in this dimension.");
		if (RaidSession.isActiveArena(source.getServer(), arena)) return error(ctx, "Wait until the raid ends.");
		String type = StringArgumentType.getString(ctx, "type");
		String name = StringArgumentType.getString(ctx, "name");
		BlockPos pos = position(ctx);
		if (!(type.equals("quiet") || type.equals("loud")) || name.isBlank() || name.length() > 48
			|| !arena.contains(pos.getX() + 0.5, pos.getZ() + 0.5)) {
			return error(ctx, "Use quiet or loud, a name up to 48 characters, and stand inside the arena.");
		}
		var exit = new ArenaEventsState.Extract(pos, type.equals("loud"), radius);
		var previous = arena.extracts.put(name, exit);
		if (!ArenaConfig.save(source.getServer())) {
			if (previous == null) arena.extracts.remove(name);
			else arena.extracts.put(name, previous);
			return error(ctx, "The arena JSON could not be saved. Check the server log.");
		}
		state.markDirty();
		var message = ArenaExtractFeedback.confirm(world, source.getPlayer(), name, exit);
		source.sendFeedback(() -> message, false);
		return 1;
	}

	private static int update(
		CommandContext<ServerCommandSource> ctx, java.util.function.Function<ArenaEventsState.Arena, String> edit
	) {
		return update(ctx, edit, "Invalid arena setting.");
	}

	private static int update(
		CommandContext<ServerCommandSource> ctx, java.util.function.Function<ArenaEventsState.Arena, String> edit, String reason
	) {
		ArenaEventsState state = ArenaEventsState.get(ctx.getSource().getWorld());
		ArenaEventsState.Arena arena = state.getArena(StringArgumentType.getString(ctx, "id"));
		if (arena == null) return error(ctx, "Unknown arena in this dimension.");
		if (RaidSession.isActiveArena(ctx.getSource().getServer(), arena)) return error(ctx, "Wait until the raid ends.");
		String message = edit.apply(arena);
		if (message == null) return error(ctx, reason);
		state.markDirty();
		return save(ctx, message);
	}

	private static int save(CommandContext<ServerCommandSource> ctx, String message) {
		return ArenaConfig.save(ctx.getSource().getServer()) ? success(ctx, message)
			: error(ctx, "The world state was updated, but the arena JSON could not be saved. Check the server log.");
	}

	private static BlockPos position(CommandContext<ServerCommandSource> ctx) {
		return BlockPos.ofFloored(ctx.getSource().getPosition());
	}

	private static int success(CommandContext<ServerCommandSource> ctx, String message) {
		ctx.getSource().sendFeedback(() -> Text.literal(message), true);
		return 1;
	}

	private static int error(CommandContext<ServerCommandSource> ctx, String message) {
		ctx.getSource().sendError(Text.literal(message));
		return 0;
	}
}
