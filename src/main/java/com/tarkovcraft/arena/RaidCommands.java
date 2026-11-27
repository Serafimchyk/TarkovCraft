package com.tarkovcraft.arena;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tarkovcraft.lobby.LobbyManager;
import com.tarkovcraft.screen.RaidScreenHandler;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class RaidCommands {
	private RaidCommands() { }

	public static LiteralArgumentBuilder<ServerCommandSource> admin() {
		return literal("raid")
			.then(literal("start").then(argument("arena", StringArgumentType.word())
				.suggests((ctx, builder) -> CommandSource.suggestMatching(ArenaEventsState.get(ctx.getSource().getWorld())
					.definitions().keySet(), builder))
				.then(argument("players", EntityArgumentType.players()).executes(ctx -> {
					var source = ctx.getSource();
					String failure = RaidSession.start(source.getWorld(), StringArgumentType.getString(ctx, "arena"),
						EntityArgumentType.getPlayers(ctx, "players"));
					if (failure != null) {
						source.sendError(Text.literal(failure));
						return 0;
					}
					source.sendFeedback(() -> Text.literal("Рейд запущен."), true);
					return 1;
				}))));
	}

	public static void registerPlayerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("raid")
			.executes(ctx -> {
				var player = ctx.getSource().getPlayerOrThrow();
				if (LobbyManager.returnFromSpectating(player)) return 1;
				if (RaidSession.isLobby(player)) RaidScreenHandler.openLobby(player);
				else player.sendMessage(Text.literal(RaidSession.status(player)), false);
				return 1;
			})
			.then(literal("scav").executes(ctx -> RaidSession.joinScav(ctx.getSource().getPlayerOrThrow()) ? 1 : 0)));
	}

	public static LiteralArgumentBuilder<ServerCommandSource> lobby() {
		return literal("lobby").requires(source -> source.hasPermissionLevel(2))
			.then(literal("toggle").executes(ctx -> LobbyManager.toggleEditor(ctx.getSource().getPlayerOrThrow()) ? 1 : 0));
	}
}
