package com.tarkovcraft.lobby;

import com.tarkovcraft.ammo.AmmoReloadTracker;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.arena.PlayerProfileState;
import com.tarkovcraft.arena.RaidSession;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovFunctionalItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.screen.LobbyPlayScreenHandler;
import com.tarkovcraft.screen.RaidScreenHandler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LobbyManager {
	public static final int WOUNDED = 1;
	public static final int NO_WEAPON = 2;
	public static final int NO_AMMO = 4;
	public static final int ONLINE_PAGE_SIZE = 4;
	private static final int PARTY_LIMIT = 4;
	private static final int RAID_LIMIT = 6;
	private static final int COUNTDOWN_TICKS = 200;
	private static final int INVITE_TICKS = 600;
	private static final List<EquipmentSlotType> WEAPONS = List.of(
		EquipmentSlotType.PRIMARY_1, EquipmentSlotType.PRIMARY_2, EquipmentSlotType.PISTOL);
	private static final Map<MinecraftServer, LobbyManager> LOBBIES = new IdentityHashMap<>();

	private final Map<UUID, WaitingPlayer> waiting = new LinkedHashMap<>();
	private final Set<UUID> editors = new LinkedHashSet<>();
	private final Set<UUID> spectators = new LinkedHashSet<>();
	private final Map<UUID, UUID> partyLeaders = new LinkedHashMap<>();
	private final Map<UUID, Invitation> invitations = new HashMap<>();
	private final Map<UUID, Integer> onlinePages = new HashMap<>();
	private final Set<UUID> ready = new LinkedHashSet<>();
	private List<UUID> countdownRoster = List.of();
	private RaidSession.ArenaChoice selectedArena;
	private RaidSession.LobbyConditions conditions;
	private boolean squads;
	private long clock;
	private long countdownEnd = -1;

	private LobbyManager() { }

	private static LobbyManager get(MinecraftServer server) {
		return LOBBIES.computeIfAbsent(server, ignored -> new LobbyManager());
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			var lobby = LOBBIES.get(server);
			if (lobby != null) lobby.tick(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			var lobby = LOBBIES.get(server);
			if (lobby != null) lobby.disconnect(handler.getPlayer());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			var lobby = LOBBIES.remove(server);
			if (lobby != null) server.getPlayerManager().getPlayerList().forEach(lobby::restoreWaitingFlags);
		});
	}

	@Nullable
	public static PlayerSessionState sessionState(ServerPlayerEntity player) {
		if (RaidSession.isParticipant(player)) return RaidSession.isScav(player)
			? PlayerSessionState.IN_RAID_SCAV : PlayerSessionState.IN_RAID_PMC;
		var lobby = LOBBIES.get(player.getServer());
		if (lobby == null) return null;
		UUID id = player.getUuid();
		if (lobby.editors.contains(id)) return PlayerSessionState.EDITOR;
		return lobby.waiting.containsKey(id) || lobby.spectators.contains(id) ? PlayerSessionState.LOBBY : null;
	}

	public static boolean isInLobby(ServerPlayerEntity player) {
		var lobby = LOBBIES.get(player.getServer());
		return lobby != null && lobby.waiting.containsKey(player.getUuid()) && sessionState(player) == PlayerSessionState.LOBBY;
	}

	public static void arrive(ServerPlayerEntity player) {
		var lobby = get(player.getServer());
		UUID id = player.getUuid();
		lobby.editors.remove(id);
		lobby.spectators.remove(id);
		lobby.waiting.computeIfAbsent(id, ignored -> new WaitingPlayer(player.isInvisible(), player.getHungerManager().getFoodLevel(),
			player.getHungerManager().getSaturationLevel()));
		lobby.maintainLobbyPlayer(player);
	}

	public static void leaveLobby(ServerPlayerEntity player) {
		var lobby = get(player.getServer());
		lobby.restoreWaitingFlags(player);
		UUID id = player.getUuid();
		lobby.editors.remove(id);
		lobby.spectators.remove(id);
		lobby.ready.remove(id);
		lobby.invitations.remove(id);
	}

	public static boolean toggleEditor(ServerPlayerEntity player) {
		if (!player.hasPermissionLevel(2) || RaidSession.isParticipant(player) || !player.isAlive()) return false;
		var lobby = get(player.getServer());
		UUID id = player.getUuid();
		if (lobby.editors.contains(id) || !isInLobby(player) && RaidSession.lobbyWorld(player.getServer()) != null) {
			if (!RaidSession.moveToLobby(player)) return false;
			LobbyPlayScreenHandler.open(player);
			return true;
		}
		if (lobby.spectators.contains(id)) return false;
		player.closeHandledScreen();
		leaveLobby(player);
		lobby.editors.add(id);
		player.changeGameMode(GameMode.CREATIVE);
		player.getAbilities().flying = true;
		player.sendAbilitiesUpdate();
		return true;
	}

	public static boolean spectate(ServerPlayerEntity player) {
		if (!isInLobby(player)) return false;
		var targets = RaidSession.spectatorTargets(player.getServer());
		if (targets.isEmpty()) {
			feedback(player, "no_spectators", "No active players to spectate.");
			return false;
		}
		player.closeHandledScreen();
		leaveLobby(player);
		get(player.getServer()).spectators.add(player.getUuid());
		player.changeGameMode(GameMode.SPECTATOR);
		player.setCameraEntity(targets.get(0));
		player.sendMessage(label("spectate_exit", "Sneak or use /raid to return to the lobby."), true);
		return true;
	}

	public static boolean returnFromSpectating(ServerPlayerEntity player) {
		var lobby = LOBBIES.get(player.getServer());
		if (lobby == null || !lobby.spectators.contains(player.getUuid())) return false;
		if (!RaidSession.moveToLobby(player)) return false;
		LobbyPlayScreenHandler.open(player);
		return true;
	}

	public static boolean isReady(ServerPlayerEntity player) {
		var lobby = LOBBIES.get(player.getServer());
		return lobby != null && lobby.ready.contains(player.getUuid());
	}

	public static boolean needsService(ServerPlayerEntity player) {
		var holder = (PlayerLoadoutHolder) player;
		var health = holder.tarkovcraft$getBodyHealth();
		if (health.needsTreatment()) return true;
		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			ItemStack stack = holder.tarkovcraft$getLoadout().getEquipment(slot);
			if (stack.getItem() instanceof TarkovArmorItem armor && armor.getArmorClass() > 0
				&& TarkovArmorItem.getRemainingDurability(stack) < TarkovArmorItem.getEffectiveMaxDurability(stack)) return true;
		}
		return false;
	}

	private void tick(MinecraftServer server) {
		clock++;
		invitations.values().removeIf(invite -> clock >= invite.expires());
		for (var player : server.getPlayerManager().getPlayerList()) {
			if (spectators.contains(player.getUuid()) && (!RaidSession.isActive(server)
				|| player.getCameraEntity() == player || !player.getCameraEntity().isAlive()
				|| player.getCameraEntity() instanceof ServerPlayerEntity target && target.isDisconnected())) {
				returnFromSpectating(player);
			}
			if (!isInLobby(player) || !player.isAlive()) continue;
			maintainLobbyPlayer(player);
			if (clock % 10 == 0) {
				if (player.currentScreenHandler == player.playerScreenHandler) {
					if (!RaidSession.showPendingResult(player)) LobbyPlayScreenHandler.open(player);
				} else if (player.currentScreenHandler instanceof LobbyPlayScreenHandler screen) screen.refresh(player);
			}
		}
		updateCountdown(server);
	}

	private void maintainLobbyPlayer(ServerPlayerEntity player) {
		var server = player.getServer();
		var anchorWorld = RaidSession.lobbyWorld(server);
		var anchor = PlayerProfileState.get(server).lobbyPos();
		if (anchorWorld == null || anchor == null) return;
		var waitingPlayer = waiting.get(player.getUuid());
		if (waitingPlayer == null) return;
		player.setInvisible(true);
		player.setVelocity(0, 0, 0);
		player.fallDistance = 0;
		player.getHungerManager().setFoodLevel(waitingPlayer.food());
		player.getHungerManager().setSaturationLevel(waitingPlayer.saturation());
		player.getHungerManager().setExhaustion(0);
		Vec3d pos = Vec3d.ofBottomCenter(anchor);
		if (player.getServerWorld() != anchorWorld || player.getPos().squaredDistanceTo(pos) > 0.01) {
			player.stopRiding();
			player.teleport(anchorWorld, pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
		}
	}

	private void restoreWaitingFlags(ServerPlayerEntity player) {
		var flags = waiting.remove(player.getUuid());
		if (flags == null) return;
		player.setInvisible(flags.invisible());
	}

	private void disconnect(ServerPlayerEntity player) {
		leaveLobby(player);
		var id = player.getUuid();
		leaveParty(id);
		invitations.values().removeIf(invite -> invite.leader().equals(id));
		onlinePages.remove(id);
	}

	private void updateCountdown(MinecraftServer server) {
		int needed = Math.min(RAID_LIMIT, waiting.size());
		if (needed == 0 || ready.size() < needed || RaidSession.isActive(server)) {
			cancelCountdown();
			return;
		}
		List<UUID> roster = ready.stream().filter(waiting::containsKey).limit(needed).toList();
		if (roster.size() < needed) {
			cancelCountdown();
			return;
		}
		if (!roster.equals(countdownRoster)) {
			countdownRoster = roster;
			countdownEnd = clock + COUNTDOWN_TICKS;
		}
		if (clock < countdownEnd) return;
		cancelCountdown();
		var players = new ArrayList<ServerPlayerEntity>();
		var groups = new HashMap<UUID, UUID>();
		for (UUID id : roster) {
			var player = server.getPlayerManager().getPlayer(id);
			if (player == null || !player.isAlive()) {
				ready.remove(id);
				return;
			}
			players.add(player);
			if (squads) groups.put(id, partyLeader(id));
		}
		selectCurrentArena(RaidSession.configuredArenas(server));
		String error = selectedArena == null || !selectedArena.ready() ? "Арена не готова."
			: RaidSession.startFromLobby(selectedArena, players, groups, conditions);
		ready.clear();
		if (error != null) players.forEach(player -> player.sendMessage(Text.literal(error), false));
	}

	private void cancelCountdown() {
		countdownRoster = List.of();
		countdownEnd = -1;
	}

	private void selectCurrentArena(List<RaidSession.ArenaChoice> arenas) {
		var previous = selectedArena;
		selectedArena = arenas.stream().filter(arena -> sameArena(arena, previous)).findFirst()
			.orElseGet(() -> arenas.stream().filter(RaidSession.ArenaChoice::ready).findFirst()
				.orElse(arenas.isEmpty() ? null : arenas.get(0)));
		if (!sameArena(selectedArena, previous)) conditions = defaults(selectedArena);
	}

	private static boolean sameArena(RaidSession.ArenaChoice left, RaidSession.ArenaChoice right) {
		return left != null && right != null && left.world() == right.world() && left.id().equals(right.id());
	}

	private static RaidSession.LobbyConditions defaults(RaidSession.ArenaChoice arena) {
		return arena == null ? null : new RaidSession.LobbyConditions(arena.boss(), arena.airdrops(), 0, null);
	}

	private boolean scavBotsEnabled() {
		return conditions.scavOverride() == null ? selectedArena.scavs() : conditions.scavOverride();
	}

	private boolean canConfigure(ServerPlayerEntity player) {
		return player.hasPermissionLevel(2) || !waiting.isEmpty() && waiting.keySet().iterator().next().equals(player.getUuid());
	}

	private UUID partyLeader(UUID member) {
		return partyLeaders.getOrDefault(member, member);
	}

	private List<UUID> party(UUID leader) {
		List<UUID> members = new ArrayList<>();
		members.add(leader);
		partyLeaders.forEach((member, owner) -> {
			if (!member.equals(leader) && leader.equals(owner)) members.add(member);
		});
		return members;
	}

	private void leaveParty(UUID member) {
		UUID leader = partyLeader(member);
		partyLeaders.remove(member);
		if (!member.equals(leader)) return;
		var others = party(leader);
		others.remove(leader);
		if (!others.isEmpty()) {
			UUID next = others.get(0);
			partyLeaders.remove(next);
			others.stream().skip(1).forEach(id -> partyLeaders.put(id, next));
		}
	}

	private List<PlayerView> inviteCandidates(ServerPlayerEntity player) {
		var server = player.getServer();
		return waiting.keySet().stream().filter(id -> !id.equals(player.getUuid()) && party(id).size() == 1
				&& partyLeader(id).equals(id))
			.map(server.getPlayerManager()::getPlayer).filter(other -> other != null && other.isAlive())
			.map(other -> new PlayerView(other.getUuid(), other.getGameProfile().getName(), false, false))
			.sorted(Comparator.comparing(PlayerView::name, String.CASE_INSENSITIVE_ORDER)).toList();
	}

	public static Snapshot snapshot(ServerPlayerEntity player) {
		return get(player.getServer()).view(player);
	}

	private Snapshot view(ServerPlayerEntity player) {
		var server = player.getServer();
		UUID id = player.getUuid();
		UUID leader = partyLeader(id);
		var arenas = RaidSession.configuredArenas(server);
		selectCurrentArena(arenas);
		int selected = selectedArena == null ? -1 : arenas.indexOf(selectedArena);
		var arena = selectedArena == null ? ArenaView.EMPTY : new ArenaView(selectedArena.name(), selectedArena.minutes(),
			conditions.boss(), conditions.airdrops(), selectedArena.exits(), conditions.timeOfDay(), selectedArena.ready(),
			scavBotsEnabled() && selectedArena.scavCount() > 0, selectedArena.scavCount());
		var party = party(leader).stream().map(server.getPlayerManager()::getPlayer).filter(member -> member != null)
			.map(member -> new PlayerView(member.getUuid(), member.getGameProfile().getName(), ready.contains(member.getUuid()),
				leader.equals(member.getUuid()))).toList();
		var candidates = inviteCandidates(player);
		int page = Math.min(onlinePages.getOrDefault(id, 0), Math.max(0, (candidates.size() - 1) / ONLINE_PAGE_SIZE));
		int from = page * ONLINE_PAGE_SIZE;
		int to = Math.min(candidates.size(), from + ONLINE_PAGE_SIZE);
		Invitation invite = invitations.get(id);
		var fromPlayer = invite == null ? null : server.getPlayerManager().getPlayer(invite.leader());
		String inviter = fromPlayer == null ? "" : fromPlayer.getGameProfile().getName();
		var profiles = PlayerProfileState.get(server);
		long delay = RaidSession.scavDelayMs(player);
		var holder = (PlayerLoadoutHolder) player;
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		var health = holder.tarkovcraft$getBodyHealth();
		return new Snapshot(arena, arenas.stream().map(RaidSession.ArenaChoice::name).toList(), selected, party,
			List.copyOf(candidates.subList(from, to)), inviter, squads, ready.contains(id), id.equals(leader), canConfigure(player),
			player.hasPermissionLevel(2), needsService(player), profiles.rubles(id), Math.round(health.getTotalHp()),
			Math.round(health.getTotalEffectiveMaxHp()), scavStatusSeconds(player, delay),
			delay == Long.MAX_VALUE ? -1 : seconds(delay), RaidSession.remainingSeconds(server),
			countdownEnd < 0 ? -1 : (int) Math.max(0, (countdownEnd - clock + 19) / 20),
			ready.size(), Math.min(RAID_LIMIT, waiting.size()), profiles.pendingLootCount(id),
			page > 0, to < candidates.size(), loadout.calculateWeightKg(), kitRubles(loadout), warnings(loadout, health.needsTreatment()));
	}

	public static int scavStatusSeconds(ServerPlayerEntity player) {
		return scavStatusSeconds(player, RaidSession.scavDelayMs(player));
	}

	private static int scavStatusSeconds(ServerPlayerEntity player, long delay) {
		var server = player.getServer();
		long remaining = Math.max(0, PlayerProfileState.get(server).scavCooldownEndMs(player.getUuid())
			- System.currentTimeMillis());
		if (delay != Long.MAX_VALUE) return seconds(delay);
		if (remaining == 0 && RaidSession.isActive(server)) return -1;
		return seconds(remaining);
	}

	private static int seconds(long millis) {
		return (int) Math.min(Short.MAX_VALUE, millis / 1000 + (millis % 1000 == 0 ? 0 : 1));
	}

	private static int warnings(PlayerLoadout loadout, boolean wounded) {
		int flags = wounded ? WOUNDED : 0;
		var weapons = WEAPONS.stream().map(loadout::getEquipment).filter(stack ->
			AmmoTier.isTaczGunStack(stack) || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem).toList();
		if (weapons.isEmpty()) return flags | NO_WEAPON;
		List<ItemStack> supplies = new ArrayList<>();
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) supplies.add(loadout.getPocket(i));
		for (EquipmentSlotType slot : List.of(EquipmentSlotType.RIG, EquipmentSlotType.BACKPACK)) {
			ContainerItemHelper.readAllGrids(loadout.getEquipment(slot)).forEach(grid ->
				grid.getEntries().forEach(entry -> supplies.add(entry.getStack())));
		}
		loadout.getSecureContainer().getEntries().forEach(entry -> supplies.add(entry.getStack()));
		for (ItemStack gun : weapons) {
			if (AmmoTier.isTaczGunStack(gun)) {
				String ammo = AmmoReloadTracker.getGunAmmoId(gun);
				if (gun.hasNbt() && gun.getNbt().getInt("GunCurrentAmmoCount") > 0
					|| !ammo.isBlank() && supplies.stream().anyMatch(stack -> AmmoTier.isTaczAmmoStack(stack)
						&& ammo.equals(AmmoTier.getAmmoId(stack)))) return flags;
			} else if (supplies.stream().anyMatch(stack -> stack.getItem() instanceof net.minecraft.item.ArrowItem
				|| gun.getItem() instanceof CrossbowItem && stack.isOf(Items.FIREWORK_ROCKET))) {
				return flags;
			}
		}
		return flags | NO_AMMO;
	}

	private static long kitRubles(PlayerLoadout loadout) {
		long worth = 0;
		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			ItemStack stack = loadout.getEquipment(slot);
			if (stack.isEmpty()) continue;
			if (stack.getItem() instanceof TarkovFunctionalItem functional && functional.getBasePrice() > 0) {
				worth += functional.getBasePrice();
			} else if (stack.getItem() instanceof TarkovArmorItem armor) {
				worth += 4_000L + armor.getArmorClass() * 6_000L;
			} else if (AmmoTier.isTaczGunStack(stack)) {
				worth += slot == EquipmentSlotType.PISTOL ? 12_000L : 32_000L;
			} else {
				worth += Math.round(1_000L + PlayerLoadout.estimateItemWeight(stack) * 2_000.0);
			}
		}
		return worth;
	}

	public static boolean toggleReady(ServerPlayerEntity player) {
		if (!isInLobby(player) || RaidSession.isActive(player.getServer())) return false;
		var lobby = get(player.getServer());
		UUID id = player.getUuid();
		if (lobby.ready.remove(id)) {
			lobby.cancelCountdown();
			return true;
		}
		lobby.selectCurrentArena(RaidSession.configuredArenas(player.getServer()));
		if (lobby.selectedArena == null || !lobby.selectedArena.ready()) return false;
		if (lobby.ready.size() >= RAID_LIMIT) {
			feedback(player, "queue_full", "The raid queue is full.");
			return false;
		}
		lobby.ready.add(id);
		return true;
	}

	public enum Configuration { PREVIOUS_ARENA, NEXT_ARENA, SQUADS, BOSS, AIRDROPS, TIME, SCAVS }

	public static boolean configure(ServerPlayerEntity player, Configuration option) {
		if (!isInLobby(player) || RaidSession.isActive(player.getServer())) return false;
		var lobby = get(player.getServer());
		if (!lobby.canConfigure(player)) return false;
		var arenas = RaidSession.configuredArenas(player.getServer());
		lobby.selectCurrentArena(arenas);
		if (lobby.selectedArena == null && option != Configuration.SQUADS) return false;
		switch (option) {
			case PREVIOUS_ARENA, NEXT_ARENA -> {
				int current = arenas.indexOf(lobby.selectedArena);
				int offset = option == Configuration.PREVIOUS_ARENA ? -1 : 1;
				lobby.selectedArena = arenas.get(Math.floorMod(current + offset, arenas.size()));
				lobby.conditions = defaults(lobby.selectedArena);
			}
			case SQUADS -> lobby.squads = !lobby.squads;
			case BOSS -> lobby.conditions = new RaidSession.LobbyConditions(!lobby.conditions.boss(),
				lobby.conditions.airdrops(), lobby.conditions.timeOfDay(), lobby.conditions.scavOverride());
			case AIRDROPS -> lobby.conditions = new RaidSession.LobbyConditions(lobby.conditions.boss(),
				!lobby.conditions.airdrops(), lobby.conditions.timeOfDay(), lobby.conditions.scavOverride());
			case TIME -> lobby.conditions = new RaidSession.LobbyConditions(lobby.conditions.boss(),
				lobby.conditions.airdrops(), (lobby.conditions.timeOfDay() + 1) % 3, lobby.conditions.scavOverride());
			case SCAVS -> {
				if (lobby.selectedArena.scavCount() <= 0) return false;
				lobby.conditions = new RaidSession.LobbyConditions(lobby.conditions.boss(),
					lobby.conditions.airdrops(), lobby.conditions.timeOfDay(), !lobby.scavBotsEnabled());
			}
		}
		lobby.ready.clear();
		lobby.cancelCountdown();
		return true;
	}

	public static boolean changeInvitePage(ServerPlayerEntity player, int offset) {
		if (!isInLobby(player)) return false;
		var lobby = get(player.getServer());
		int last = Math.max(0, (lobby.inviteCandidates(player).size() - 1) / ONLINE_PAGE_SIZE);
		UUID id = player.getUuid();
		int current = Math.min(last, lobby.onlinePages.getOrDefault(id, 0));
		lobby.onlinePages.put(id, Math.max(0, Math.min(last, current + offset)));
		return true;
	}

	public static boolean invite(ServerPlayerEntity player, UUID invitee) {
		if (!isInLobby(player)) return false;
		var lobby = get(player.getServer());
		UUID id = player.getUuid();
		if (!lobby.partyLeader(id).equals(id) || lobby.party(id).size() >= PARTY_LIMIT
			|| lobby.inviteCandidates(player).stream().noneMatch(candidate -> candidate.id().equals(invitee))) return false;
		Invitation current = lobby.invitations.get(invitee);
		if (current != null && lobby.clock < current.expires()) return false;
		lobby.invitations.put(invitee, new Invitation(id, lobby.clock + INVITE_TICKS));
		feedback(player, "invite_sent", "Invitation sent.");
		return true;
	}

	public static boolean answerInvite(ServerPlayerEntity player, String expectedInviter, boolean accept) {
		if (!isInLobby(player)) return false;
		var lobby = get(player.getServer());
		UUID id = player.getUuid();
		var invite = lobby.invitations.get(id);
		if (invite == null || lobby.clock >= invite.expires()) return false;
		var leader = player.getServer().getPlayerManager().getPlayer(invite.leader());
		if (leader == null || !leader.getGameProfile().getName().equals(expectedInviter)) return false;
		lobby.invitations.remove(id);
		if (!accept) return true;
		if (!isInLobby(leader) || !lobby.partyLeader(invite.leader()).equals(invite.leader())
			|| lobby.party(invite.leader()).size() >= PARTY_LIMIT || !lobby.partyLeader(id).equals(id)
			|| lobby.party(id).size() > 1) {
			feedback(player, "invite_closed", "This invitation is no longer available.");
			return false;
		}
		lobby.partyLeaders.put(id, invite.leader());
		lobby.cancelCountdown();
		return true;
	}

	public static boolean manageParty(ServerPlayerEntity player, UUID member, boolean transferLeadership) {
		if (!isInLobby(player)) return false;
		var lobby = get(player.getServer());
		UUID id = player.getUuid();
		if (member.equals(id) || !lobby.partyLeader(id).equals(id) || !lobby.partyLeader(member).equals(id)) return false;
		if (transferLeadership) {
			var party = lobby.party(id);
			lobby.partyLeaders.remove(member);
			party.stream().filter(other -> !other.equals(member)).forEach(other -> lobby.partyLeaders.put(other, member));
		} else {
			lobby.leaveParty(member);
		}
		lobby.cancelCountdown();
		return true;
	}

	public static boolean leaveParty(ServerPlayerEntity player) {
		if (!isInLobby(player)) return false;
		var lobby = get(player.getServer());
		lobby.leaveParty(player.getUuid());
		lobby.cancelCountdown();
		return true;
	}

	private static void feedback(ServerPlayerEntity player, String key, String fallback) {
		player.sendMessage(label(key, fallback), false);
	}

	public static Text label(String key, String fallback) {
		return Text.translatableWithFallback("screen.tarkovcraft.lobby." + key, fallback);
	}

	private record Invitation(UUID leader, long expires) { }
	private record WaitingPlayer(boolean invisible, int food, float saturation) { }

	public record ArenaView(String name, int minutes, boolean boss, boolean airdrops, int exits, int timeOfDay,
		boolean valid, boolean scavs, int scavCount) {
		private static final ArenaView EMPTY = new ArenaView("", 0, false, false, 0, 0, false, false, 0);
	}

	public record PlayerView(UUID id, String name, boolean ready, boolean leader) { }

	public record Snapshot(ArenaView arena, List<String> arenas, int arenaIndex, List<PlayerView> party, List<PlayerView> online,
		String inviter, boolean squads, boolean ready, boolean leader, boolean canConfigure, boolean editor, boolean service,
		long rubles, int health, int maxHealth, int scavStatusSeconds, int scavSeconds, int raidSeconds, int countdownSeconds,
		int readyCount, int queuedCount, int pendingLoot, boolean canPreviousPlayers, boolean canNextPlayers,
		float kitWeightKg, long kitRubles, int warningFlags) {
		public boolean warned() { return warningFlags != 0; }
	}
}
