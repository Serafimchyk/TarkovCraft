package com.tarkovcraft.screen;

import com.tarkovcraft.arena.RaidSession;
import com.tarkovcraft.lobby.LobbyManager;
import net.minecraft.server.network.ServerPlayerEntity;

public final class LobbyTabNavigation {
	public static final int PLAY = 100;
	public static final int CHARACTER = 101;
	public static final int TRADERS = 102;
	public static final int WORKBENCH = 103;
	public static final int SERVICE = 104;
	public static final int EDITOR = 105;
	public static final int LOADOUT = 106;

	private LobbyTabNavigation() { }

	public static boolean canUse(ServerPlayerEntity player) {
		return player.isAlive() && !player.isSpectator() && RaidSession.isLobby(player);
	}

	public static boolean handle(ServerPlayerEntity player, int button) {
		if (button < PLAY || button > LOADOUT || !canUse(player)) return false;
		if (button == EDITOR) return LobbyManager.toggleEditor(player);
		player.onHandledScreenClosed();
		switch (button) {
			case PLAY -> RaidScreenHandler.openLobby(player);
			case CHARACTER -> TarkovScreenHandler.openStash(player);
			case TRADERS -> LobbyTraderScreenHandler.open(player);
			case WORKBENCH -> TarkovWorkbenchScreenHandler.openForPlayer(player, null);
			case LOADOUT -> LobbyLoadoutScreenHandler.open(player);
			case SERVICE -> LobbyServiceScreenHandler.open(player);
			default -> throw new IllegalArgumentException("Unknown lobby tab " + button);
		}
		return true;
	}
}
