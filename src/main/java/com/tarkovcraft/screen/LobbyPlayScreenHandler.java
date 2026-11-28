package com.tarkovcraft.screen;

import com.tarkovcraft.arena.RaidSession;
import com.tarkovcraft.lobby.LobbyManager;
import com.tarkovcraft.lobby.LobbyManager.ArenaView;
import com.tarkovcraft.lobby.LobbyManager.Configuration;
import com.tarkovcraft.lobby.LobbyManager.PlayerView;
import com.tarkovcraft.lobby.LobbyManager.Snapshot;
import com.tarkovcraft.registry.ModScreenHandlers;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LobbyPlayScreenHandler extends ScreenHandler {
	public static final int CHARACTER = 0;
	public static final int EDITOR = 1;
	public static final int JOIN_SCAV = 2;
	public static final int TRANSFER = 3;
	public static final int SPECTATE = 4;
	public static final int TOGGLE_READY = 5;
	public static final int TOGGLE_SQUADS = 6;
	public static final int PREVIOUS_ARENA = 7;
	public static final int NEXT_ARENA = 8;
	public static final int TOGGLE_BOSS = 9;
	public static final int TOGGLE_AIRDROPS = 10;
	public static final int NEXT_TIME = 11;
	public static final int ACCEPT = 12;
	public static final int DECLINE = 13;
	public static final int LEAVE = 14;
	public static final int PREVIOUS_PLAYERS = 15;
	public static final int NEXT_PLAYERS = 16;
	public static final int TOGGLE_SCAVS = 17;
	public static final int INVITE_FIRST = 32;
	public static final int KICK_FIRST = 48;
	public static final int TRANSFER_LEADER_FIRST = 64;

	private static final int READY = 1;
	private static final int EDITOR_ALLOWED = 2;
	private static final int SERVICE = 4;
	private static final int CONFIGURE = 8;
	private static final int SQUADS = 16;
	private static final int LEADER = 32;
	private static final int PREVIOUS_PAGE = 64;
	private static final int NEXT_PAGE = 128;
	private static final int ARENA_BOSS = 1;
	private static final int ARENA_AIRDROPS = 2;
	private static final int ARENA_VALID = 4;
	private static final int ARENA_SCAVS = 8;

	private enum Field {
		FLAGS, PARTY_READY, HEALTH, MAX_HEALTH, SCAV_STATUS, SCAV_DELAY, RAID, COUNTDOWN, READY_COUNT, QUEUED_COUNT,
		PENDING_LOOT, WEIGHT, WARNING, RUBLES_0, RUBLES_1, RUBLES_2, RUBLES_3, KIT_0, KIT_1, KIT_2, KIT_3,
		ARENA_INDEX, ARENA_FLAGS, ARENA_MINUTES, ARENA_EXITS, ARENA_TIME, ARENA_SCAV_COUNT
	}

	private final PlayerEntity owner;
	private final List<String> arenas;
	private final List<PlayerView> party;
	private final List<PlayerView> online;
	private final String inviter;
	private final Property[] live = new Property[Field.values().length];

	private LobbyPlayScreenHandler(int syncId, PlayerEntity owner, List<String> arenas,
			List<PlayerView> party, List<PlayerView> online, String inviter, int[] props) {
		super(ModScreenHandlers.LOBBY_PLAY_SCREEN_HANDLER, syncId);
		this.owner = owner;
		this.arenas = arenas;
		this.party = party;
		this.online = online;
		this.inviter = inviter;
		for (int i = 0; i < live.length; i++) {
			live[i] = addProperty(Property.create());
			live[i].set(props[i]);
		}
	}

	public static LobbyPlayScreenHandler fromClientBuf(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		var arenas = buf.readList(packet -> packet.readString(128));
		var party = buf.readList(LobbyPlayScreenHandler::readPlayer);
		var online = buf.readList(LobbyPlayScreenHandler::readPlayer);
		String inviter = buf.readString(16);
		int[] props = new int[Field.values().length];
		for (int i = 0; i < props.length; i++) props[i] = buf.readShort();
		return new LobbyPlayScreenHandler(syncId, inventory.player, arenas, party, online, inviter, props);
	}

	public static void open(ServerPlayerEntity player) {
		if (!player.isAlive() || !RaidSession.isLobby(player)) return;
		var snapshot = LobbyManager.snapshot(player);
		var props = encodeProperties(snapshot);
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity viewer, PacketByteBuf buf) {
				buf.writeCollection(snapshot.arenas(), (packet, name) -> packet.writeString(limit(name), 128));
				buf.writeCollection(snapshot.party(), LobbyPlayScreenHandler::writePlayer);
				buf.writeCollection(snapshot.online(), LobbyPlayScreenHandler::writePlayer);
				buf.writeString(snapshot.inviter(), 16);
				for (int property : props) buf.writeShort(property);
			}

			@Override
			public Text getDisplayName() {
				return LobbyManager.label("tab.play", "Play");
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity viewer) {
				return new LobbyPlayScreenHandler(syncId, viewer, snapshot.arenas(),
					snapshot.party(), snapshot.online(), snapshot.inviter(), props);
			}
		});
	}

	private static String limit(String text) {
		return text.length() <= 128 ? text : text.substring(0, 128);
	}

	private static void writePlayer(PacketByteBuf buf, PlayerView player) {
		buf.writeUuid(player.id());
		buf.writeString(player.name(), 16);
		buf.writeBoolean(player.leader());
	}

	private static PlayerView readPlayer(PacketByteBuf buf) {
		return new PlayerView(buf.readUuid(), buf.readString(16), false, buf.readBoolean());
	}

	private static int[] encodeProperties(Snapshot state) {
		int[] props = new int[Field.values().length];
		int flags = 0;
		if (state.ready()) flags |= READY;
		if (state.editor()) flags |= EDITOR_ALLOWED;
		if (state.service()) flags |= SERVICE;
		if (state.canConfigure()) flags |= CONFIGURE;
		if (state.squads()) flags |= SQUADS;
		if (state.leader()) flags |= LEADER;
		if (state.canPreviousPlayers()) flags |= PREVIOUS_PAGE;
		if (state.canNextPlayers()) flags |= NEXT_PAGE;
		props[Field.FLAGS.ordinal()] = flags;
		for (int i = 0; i < state.party().size(); i++) {
			if (state.party().get(i).ready()) props[Field.PARTY_READY.ordinal()] |= 1 << i;
		}
		props[Field.HEALTH.ordinal()] = state.health();
		props[Field.MAX_HEALTH.ordinal()] = state.maxHealth();
		props[Field.SCAV_STATUS.ordinal()] = state.scavStatusSeconds();
		props[Field.SCAV_DELAY.ordinal()] = state.scavSeconds();
		props[Field.RAID.ordinal()] = Math.min(Short.MAX_VALUE, state.raidSeconds());
		props[Field.COUNTDOWN.ordinal()] = state.countdownSeconds();
		props[Field.READY_COUNT.ordinal()] = state.readyCount();
		props[Field.QUEUED_COUNT.ordinal()] = state.queuedCount();
		props[Field.PENDING_LOOT.ordinal()] = Math.min(Short.MAX_VALUE, state.pendingLoot());
		props[Field.WEIGHT.ordinal()] = Math.min(Short.MAX_VALUE, Math.round(state.kitWeightKg() * 10));
		props[Field.WARNING.ordinal()] = state.warningFlags();
		var arena = state.arena();
		int arenaFlags = 0;
		if (arena.boss()) arenaFlags |= ARENA_BOSS;
		if (arena.airdrops()) arenaFlags |= ARENA_AIRDROPS;
		if (arena.valid()) arenaFlags |= ARENA_VALID;
		if (arena.scavs()) arenaFlags |= ARENA_SCAVS;
		props[Field.ARENA_INDEX.ordinal()] = state.arenaIndex();
		props[Field.ARENA_FLAGS.ordinal()] = arenaFlags;
		props[Field.ARENA_MINUTES.ordinal()] = arena.minutes();
		props[Field.ARENA_EXITS.ordinal()] = Math.min(Short.MAX_VALUE, arena.exits());
		props[Field.ARENA_TIME.ordinal()] = arena.timeOfDay();
		props[Field.ARENA_SCAV_COUNT.ordinal()] = arena.scavCount();
		writeMoney(props, Field.RUBLES_0, state.rubles());
		writeMoney(props, Field.KIT_0, state.kitRubles());
		return props;
	}

	private static void writeMoney(int[] props, Field start, long amount) {
		for (int i = 0; i < 4; i++) props[start.ordinal() + i] = (short) (amount >>> (i * 16));
	}

	private long readMoney(Field start) {
		long amount = 0;
		for (int i = 0; i < 4; i++) amount |= (live[start.ordinal() + i].get() & 0xffffL) << (i * 16);
		return amount;
	}

	private int get(Field field) {
		return live[field.ordinal()].get();
	}

	public Snapshot snapshot() {
		int flags = get(Field.FLAGS);
		int arenaIndex = get(Field.ARENA_INDEX);
		int arenaFlags = get(Field.ARENA_FLAGS);
		String name = arenaIndex >= 0 && arenaIndex < arenas.size() ? arenas.get(arenaIndex) : "";
		var arena = new ArenaView(name, get(Field.ARENA_MINUTES), (arenaFlags & ARENA_BOSS) != 0,
			(arenaFlags & ARENA_AIRDROPS) != 0, get(Field.ARENA_EXITS), get(Field.ARENA_TIME),
			(arenaFlags & ARENA_VALID) != 0, (arenaFlags & ARENA_SCAVS) != 0, get(Field.ARENA_SCAV_COUNT));
		int ready = get(Field.PARTY_READY);
		var members = new ArrayList<PlayerView>(party.size());
		for (int i = 0; i < party.size(); i++) {
			var member = party.get(i);
			members.add(new PlayerView(member.id(), member.name(), (ready & (1 << i)) != 0, member.leader()));
		}
		return new Snapshot(arena, arenas, arenaIndex, members, online, inviter, (flags & SQUADS) != 0,
			(flags & READY) != 0, (flags & LEADER) != 0, (flags & CONFIGURE) != 0, (flags & EDITOR_ALLOWED) != 0,
			(flags & SERVICE) != 0, readMoney(Field.RUBLES_0), get(Field.HEALTH), get(Field.MAX_HEALTH),
			get(Field.SCAV_STATUS), get(Field.SCAV_DELAY), get(Field.RAID), get(Field.COUNTDOWN), get(Field.READY_COUNT),
			get(Field.QUEUED_COUNT), get(Field.PENDING_LOOT), (flags & PREVIOUS_PAGE) != 0, (flags & NEXT_PAGE) != 0,
			get(Field.WEIGHT) / 10f, readMoney(Field.KIT_0), get(Field.WARNING));
	}

	public void refresh(ServerPlayerEntity player) {
		if (player.currentScreenHandler != this || !canUse(player)) return;
		var state = LobbyManager.snapshot(player);
		if (!arenas.equals(state.arenas()) || !sameParty(state.party())
			|| !online.equals(state.online()) || !inviter.equals(state.inviter())) {
			player.onHandledScreenClosed();
			open(player);
			return;
		}
		int[] properties = encodeProperties(state);
		for (int i = 0; i < live.length; i++) live[i].set(properties[i]);
		sendContentUpdates();
	}

	private boolean sameParty(List<PlayerView> updated) {
		if (party.size() != updated.size()) return false;
		for (int i = 0; i < party.size(); i++) {
			var current = party.get(i);
			var next = updated.get(i);
			if (!current.id().equals(next.id()) || current.leader() != next.leader()) return false;
		}
		return true;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return owner == player && (!(player instanceof ServerPlayerEntity serverPlayer)
			|| serverPlayer.isAlive() && RaidSession.isLobby(serverPlayer));
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || player.currentScreenHandler != this || !canUse(player)) return false;
		if (LobbyTabNavigation.handle(serverPlayer, id)) return true;
		boolean handled = switch (id) {
			case CHARACTER -> {
				// Clean up the handler without the close packet that recenters the cursor.
				serverPlayer.onHandledScreenClosed();
				TarkovScreenHandler.openStash(serverPlayer);
				yield true;
			}
			case EDITOR -> LobbyManager.toggleEditor(serverPlayer);
			case JOIN_SCAV -> RaidSession.joinScav(serverPlayer);
			case TRANSFER -> { RaidScreenHandler.openTransfer(serverPlayer); yield true; }
			case SPECTATE -> LobbyManager.spectate(serverPlayer);
			case TOGGLE_READY -> LobbyManager.toggleReady(serverPlayer);
			case TOGGLE_SQUADS -> LobbyManager.configure(serverPlayer, Configuration.SQUADS);
			case PREVIOUS_ARENA -> LobbyManager.configure(serverPlayer, Configuration.PREVIOUS_ARENA);
			case NEXT_ARENA -> LobbyManager.configure(serverPlayer, Configuration.NEXT_ARENA);
			case TOGGLE_BOSS -> LobbyManager.configure(serverPlayer, Configuration.BOSS);
			case TOGGLE_AIRDROPS -> LobbyManager.configure(serverPlayer, Configuration.AIRDROPS);
			case NEXT_TIME -> LobbyManager.configure(serverPlayer, Configuration.TIME);
			case TOGGLE_SCAVS -> LobbyManager.configure(serverPlayer, Configuration.SCAVS);
			case ACCEPT -> LobbyManager.answerInvite(serverPlayer, inviter, true);
			case DECLINE -> LobbyManager.answerInvite(serverPlayer, inviter, false);
			case LEAVE -> LobbyManager.leaveParty(serverPlayer);
			case PREVIOUS_PLAYERS -> LobbyManager.changeInvitePage(serverPlayer, -1);
			case NEXT_PLAYERS -> LobbyManager.changeInvitePage(serverPlayer, 1);
			default -> handleIndexedAction(serverPlayer, id);
		};
		if (handled && player.currentScreenHandler == this) refresh(serverPlayer);
		return handled;
	}

	private boolean handleIndexedAction(ServerPlayerEntity player, int id) {
		UUID invitee = at(online, id - INVITE_FIRST);
		if (invitee != null) return LobbyManager.invite(player, invitee);
		UUID kicked = at(party, id - KICK_FIRST);
		if (kicked != null) return LobbyManager.manageParty(player, kicked, false);
		UUID promoted = at(party, id - TRANSFER_LEADER_FIRST);
		return promoted != null && LobbyManager.manageParty(player, promoted, true);
	}

	private static UUID at(List<PlayerView> players, int index) {
		return index >= 0 && index < players.size() ? players.get(index).id() : null;
	}
}
