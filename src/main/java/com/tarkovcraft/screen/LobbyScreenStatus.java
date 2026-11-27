package com.tarkovcraft.screen;

import com.tarkovcraft.arena.PlayerProfileState;
import com.tarkovcraft.arena.RaidSession;
import com.tarkovcraft.lobby.LobbyManager;
import net.minecraft.screen.Property;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.UnaryOperator;

public final class LobbyScreenStatus {
	private final Property[] balance = new Property[4];
	private final Property flags;
	private final Property raid;
	private final Property scav;

	public LobbyScreenStatus(UnaryOperator<Property> register) {
		for (int i = 0; i < balance.length; i++) balance[i] = register.apply(Property.create());
		flags = register.apply(Property.create());
		raid = register.apply(Property.create());
		scav = register.apply(Property.create());
		raid.set(-1);
		scav.set(-1);
	}

	public void refresh(ServerPlayerEntity player) {
		long rubles = PlayerProfileState.get(player.getServer()).rubles(player.getUuid());
		for (int i = 0; i < balance.length; i++) balance[i].set((short) (rubles >>> (i * 16)));
		flags.set((LobbyManager.isReady(player) ? 1 : 0) | (LobbyManager.needsService(player) ? 2 : 0)
			| (player.hasPermissionLevel(2) ? 4 : 0));
		raid.set(Math.min(Short.MAX_VALUE, RaidSession.remainingSeconds(player.getServer())));
		scav.set(LobbyManager.scavStatusSeconds(player));
	}

	public long rubles() {
		long rubles = 0;
		for (int i = 0; i < balance.length; i++) rubles |= (balance[i].get() & 0xffffL) << (i * 16);
		return rubles;
	}

	public boolean ready() { return (flags.get() & 1) != 0; }
	public boolean needsService() { return (flags.get() & 2) != 0; }
	public boolean editorAllowed() { return (flags.get() & 4) != 0; }
	public int raidSeconds() { return raid.get(); }
	public int scavSeconds() { return scav.get(); }
}
