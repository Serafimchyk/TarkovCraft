package com.tarkovcraft.arena;

import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.entity.SleeperPlayerEntity;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.health.DamageReport;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.lobby.LobbyManager;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.screen.RaidScreenHandler;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.level.ServerWorldProperties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class RaidSession {
	private static final int TICKS_PER_SECOND = 20;
	private static final int LAST_SCAV_ENTRY = 120 * TICKS_PER_SECOND;
	private static final long SCAV_COOLDOWN_MS = 1_800_000L;
	private static final long PMC_DEATH_DELAY_MS = 45_000L;
	private static final ChunkTicketType<UUID> SLEEPER_TICKET = ChunkTicketType.create(
		"tarkovcraft_sleeper", Comparator.<UUID>naturalOrder());
	private static final Map<MinecraftServer, RaidSession> ACTIVE = new IdentityHashMap<>();
	private static final Map<ServerWorld, Set<Entity>> RETIRED_TO_DISCARD = new IdentityHashMap<>();

	private final ServerWorld world;
	final ArenaEventsState.Arena arena;
	private final String arenaName;
	private final Map<UUID, Member> roster = new LinkedHashMap<>();
	private final Map<UUID, Long> pmcDeaths = new HashMap<>();
	private final Set<BlockPos> interactedOpenables = new LinkedHashSet<>();
	private final Set<UUID> unloadedEntities = new LinkedHashSet<>();
	final ArenaOpenableTracker openables;
	private int remaining;
	private int elapsed;
	private LobbyConditions lobbyConditions;
	private Weather previousWeather;

	private RaidSession(ServerWorld world, String id, ArenaEventsState.Arena arena) {
		this.world = world;
		this.arena = arena;
		this.arenaName = arena.displayName.isBlank() ? id : arena.displayName;
		this.remaining = arena.durationMinutes * 60 * TICKS_PER_SECOND;
		this.openables = new ArenaOpenableTracker(world, arena, interactedOpenables);
	}

	public static void register() {
		LobbyManager.register();
		ArenaOpenableTracker.register();
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!ArenaResetService.isTemporary(entity)) return;
			RaidSession raid = running(world);
			if (raid != null) raid.unloadedEntities.remove(entity.getUuid());
			if (ArenaEventsState.get(world).retiredEntities.contains(entity.getUuid())) {
				RETIRED_TO_DISCARD.computeIfAbsent(world, key -> new LinkedHashSet<>()).add(entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			RaidSession raid = running(world);
			if (raid != null && ArenaResetService.isTemporary(entity)
				&& (entity.getRemovalReason() == null || !entity.getRemovalReason().shouldDestroy())
				&& raid.arena.contains(entity.getX(), entity.getZ())) raid.unloadedEntities.add(entity.getUuid());
		});
		ServerLifecycleEvents.SERVER_STARTED.register(ArenaConfig::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerWorld world : server.getWorlds()) discardRetired(world);
			RaidSession raid = ACTIVE.get(server);
			if (raid != null) raid.end(false);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(ACTIVE::remove);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerWorld world : server.getWorlds()) discardRetired(world);
			RaidSession raid = ACTIVE.get(server);
			if (raid != null) raid.tick();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RaidSession raid = ACTIVE.get(server);
			if (raid != null) raid.disconnect(handler.getPlayer());
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayerEntity player) || !isLobby(player));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayerEntity player) {
				RaidSession raid = ACTIVE.get(player.getServer());
				if (raid != null) raid.onPlayerDeath(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && profile(newPlayer).contains("Result")) completeLobbyArrival(newPlayer, true);
		});
	}

	private static void discardRetired(ServerWorld world) {
		var ready = RETIRED_TO_DISCARD.remove(world);
		if (ready == null) return;
		var state = ArenaEventsState.get(world);
		for (Entity entity : ready) {
			if (entity.isRemoved()) continue;
			entity.discard();
			if (state.retiredEntities.remove(entity.getUuid())) state.markDirty();
		}
	}

	public static boolean isActive(MinecraftServer server) {
		return ACTIVE.containsKey(server);
	}

	static boolean isActiveArena(MinecraftServer server, ArenaEventsState.Arena arena) {
		RaidSession raid = ACTIVE.get(server);
		return raid != null && raid.arena == arena;
	}

	static RaidSession running(ServerWorld world) {
		RaidSession raid = ACTIVE.get(world.getServer());
		return raid != null && raid.world == world ? raid : null;
	}

	List<ServerPlayerEntity> livingPlayers() {
		return world.getPlayers(player -> player.isAlive() && roster.containsKey(player.getUuid()));
	}

	public static boolean isScav(ServerPlayerEntity player) {
		RaidSession raid = ACTIVE.get(player.getServer());
		Member member = raid == null ? null : raid.roster.get(player.getUuid());
		return member != null && member.scav;
	}

	public static boolean isLobby(ServerPlayerEntity player) {
		return LobbyManager.isInLobby(player);
	}

	public static boolean isParticipant(ServerPlayerEntity player) {
		RaidSession raid = ACTIVE.get(player.getServer());
		return raid != null && raid.roster.containsKey(player.getUuid());
	}

	public static int remainingSeconds(MinecraftServer server) {
		RaidSession raid = ACTIVE.get(server);
		return raid == null ? -1 : (raid.remaining + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
	}

	public static List<ServerPlayerEntity> spectatorTargets(MinecraftServer server) {
		RaidSession raid = ACTIVE.get(server);
		return raid == null ? List.of() : raid.livingPlayers();
	}

	public static List<ArenaChoice> configuredArenas(MinecraftServer server) {
		List<ArenaChoice> result = new ArrayList<>();
		for (ServerWorld world : server.getWorlds()) {
			ArenaEventsState.get(world).definitions().forEach((id, arena) -> result.add(new ArenaChoice(world, id,
				arena.displayName.isBlank() ? id : arena.displayName, arena.durationMinutes, arena.bossEnabled,
				arena.airdropsEnabled, arena.extracts.size(), arena.ready() && !arena.pmcSpawns.isEmpty() && !arena.extracts.isEmpty())));
		}
		return result;
	}

	public record ArenaChoice(ServerWorld world, String id, String name, int minutes, boolean boss, boolean airdrops,
		int exits, boolean ready) { }

	public record LobbyConditions(boolean boss, boolean airdrops, int timeOfDay) { }

	static String start(ServerWorld world, String id, Collection<ServerPlayerEntity> players) {
		return start(world, id, players,
			player -> player.getScoreboardTeam() == null ? player.getUuid() : player.getScoreboardTeam(), null);
	}

	public static String startFromLobby(ArenaChoice arena, Collection<ServerPlayerEntity> players,
		Map<UUID, UUID> squads, LobbyConditions conditions) {
		if (players.stream().anyMatch(player -> !isLobby(player))) return "Состав игроков изменился.";
		return start(arena.world(), arena.id(), players, player -> squads.getOrDefault(player.getUuid(), player.getUuid()), conditions);
	}

	private static String start(ServerWorld world, String id, Collection<ServerPlayerEntity> players,
		Function<ServerPlayerEntity, Object> groupOf, LobbyConditions conditions) {
		MinecraftServer server = world.getServer();
		if (isActive(server)) return "Текущий рейд ещё идёт.";
		var arena = ArenaEventsState.get(world).getArena(id);
		if (arena == null || !arena.ready()) return "Арена не найдена или её границы ещё не размечены.";
		var lobby = PlayerProfileState.get(server);
		if (lobbyWorld(server) == null || lobby.lobbyPos() == null) return "Сначала укажи точку лобби.";
		if (lobbyWorld(server) == world && arena.contains(lobby.lobbyPos().getX() + 0.5, lobby.lobbyPos().getZ() + 0.5)) {
			return "Точка лобби находится внутри арены.";
		}
		if (players.isEmpty() || players.size() > 6 || players.stream().anyMatch(player -> !player.isAlive())) {
			return "Выбери от одного до шести живых игроков.";
		}
		var spawns = arena.pmcSpawns.stream().filter(pos -> validPosition(world, arena, pos)).toList();
		if (spawns.size() < players.size() || arena.extracts.values().stream().noneMatch(exit -> validPosition(world, arena, exit.pos()))) {
			return "Нужен рабочий выход и по одной безопасной точке спавна на каждого игрока.";
		}
		RaidSession raid = new RaidSession(world, id, arena);
		raid.lobbyConditions = conditions;
		ArenaResetService.reset(world, arena, raid.interactedOpenables);
		ACTIVE.put(server, raid);
		raid.openables.start();
		Map<Object, List<ServerPlayerEntity>> squads = new LinkedHashMap<>();
		for (ServerPlayerEntity player : players) {
			squads.computeIfAbsent(groupOf.apply(player), key -> new ArrayList<>()).add(player);
		}
		var anchors = spread(spawns, squads.size());
		var available = new ArrayList<>(spawns);
		available.removeAll(anchors);
		int index = 0;
		for (var squad : squads.values()) {
			BlockPos anchor = anchors.get(index++);
			raid.enter(squad.get(0), false, anchor);
			for (int member = 1; member < squad.size(); member++) {
				BlockPos nearby = available.stream().min(Comparator.comparingDouble(pos -> horizontalDistance(anchor, pos)))
					.orElseThrow();
				available.remove(nearby);
				raid.enter(squad.get(member), false, nearby);
			}
		}
		raid.applyWeather();
		return null;
	}

	boolean bossEnabled() {
		return lobbyConditions == null ? arena.bossEnabled : lobbyConditions.boss();
	}

	boolean airdropsEnabled() {
		return lobbyConditions == null ? arena.airdropsEnabled : lobbyConditions.airdrops();
	}

	private void applyWeather() {
		if (lobbyConditions == null || !(world.getLevelProperties() instanceof ServerWorldProperties properties)) return;
		previousWeather = new Weather(world.getTimeOfDay(), properties.getClearWeatherTime(), properties.getRainTime(),
			properties.getThunderTime(), properties.isRaining(), properties.isThundering());
		int time = lobbyConditions.timeOfDay();
		boolean overcast = time == 1;
		long day = world.getTimeOfDay() / 24000L * 24000L;
		world.setTimeOfDay(day + (time == 2 ? 18000L : 6000L));
		world.setWeather(overcast ? 0 : remaining + 20, overcast ? remaining + 20 : 0, overcast, false);
	}

	private void restoreWeather() {
		if (previousWeather == null || !(world.getLevelProperties() instanceof ServerWorldProperties properties)) return;
		world.setTimeOfDay(previousWeather.time() + (world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE) ? elapsed : 0));
		properties.setClearWeatherTime(previousWeather.clear());
		properties.setRainTime(previousWeather.rain());
		properties.setThunderTime(previousWeather.thunder());
		properties.setRaining(previousWeather.raining());
		properties.setThundering(previousWeather.thundering());
	}

	private record Weather(long time, int clear, int rain, int thunder, boolean raining, boolean thundering) { }

	static List<BlockPos> spread(List<BlockPos> spawns, int count) {
		List<BlockPos> result = new ArrayList<>();
		if (count == 0) return result;
		BlockPos first = spawns.get(0);
		BlockPos second = first;
		double maximum = -1;
		for (BlockPos left : spawns) {
			for (BlockPos right : spawns) {
				double distance = horizontalDistance(left, right);
				if (distance > maximum) {
					maximum = distance;
					first = left;
					second = right;
				}
			}
		}
		result.add(first);
		if (count > 1) result.add(second);
		while (result.size() < count) {
			BlockPos next = spawns.stream().filter(pos -> !result.contains(pos))
				.max(Comparator.comparingDouble(pos -> result.stream()
					.mapToDouble(chosen -> horizontalDistance(pos, chosen)).min().orElseThrow())).orElseThrow();
			result.add(next);
		}
		return result;
	}

	private static double horizontalDistance(BlockPos first, BlockPos second) {
		double dx = first.getX() - (double) second.getX();
		double dz = first.getZ() - (double) second.getZ();
		return dx * dx + dz * dz;
	}

	private static boolean validPosition(ServerWorld world, ArenaEventsState.Arena arena, BlockPos pos) {
		return arena.contains(pos.getX() + 0.5, pos.getZ() + 0.5) && world.isInBuildLimit(pos)
			&& world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
			&& world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
			&& world.getBlockState(pos).getFluidState().isEmpty()
			&& !world.getBlockState(pos.down()).getCollisionShape(world, pos.down()).isEmpty();
	}

	private void enter(ServerPlayerEntity player, boolean scav, BlockPos spawn) {
		player.closeHandledScreen();
		LobbyManager.leaveLobby(player);
		NbtCompound profile = profile(player);
		profile.remove("Result");
		profile.remove("Report");
		profile.putString("Mode", scav ? "SCAV" : "PMC");
		profile.putString("Arena", arenaName);
		profile.put("Secure", RaidEquipment.secure(player));
		if (scav) {
			profile.put("PmcEquipment", RaidEquipment.capture(player));
			RaidEquipment.clear(player, false);
			player.setExperienceLevel(0);
			player.totalExperience = 0;
			player.experienceProgress = 0;
			equipScav(player);
		}
		save(player, profile);
		roster.put(player.getUuid(), new Member(scav));
		player.changeGameMode(GameMode.ADVENTURE);
		teleport(player, world, spawn);
		RaidEquipment.sync(player);
		player.sendMessage(Text.literal("Рейд начался: " + arenaName), false);
	}

	private void equipScav(ServerPlayerEntity player) {
		var loadout = ((PlayerLoadoutHolder) player).tarkovcraft$getLoadout();
		loadout.setEquipment(EquipmentSlotType.BACKPACK, new ItemStack(ModItems.SLING_BACKPACK));
		loadout.setEquipment(EquipmentSlotType.RIG, new ItemStack(ModItems.SCAV_VEST));
		switch (world.random.nextInt(3)) {
			case 0 -> {
				loadout.setEquipment(EquipmentSlotType.PRIMARY_1, new ItemStack(Items.CROSSBOW));
				loadout.setPocket(1, new ItemStack(Items.ARROW, 12));
			}
			case 1 -> {
				loadout.setEquipment(EquipmentSlotType.PRIMARY_1, new ItemStack(Items.BOW));
				loadout.setPocket(1, new ItemStack(Items.ARROW, 16));
			}
			default -> loadout.setEquipment(EquipmentSlotType.MELEE, new ItemStack(Items.STONE_SWORD));
		}
		loadout.setPocket(0, new ItemStack(ModItems.BANDAGE));
	}

	public static String status(ServerPlayerEntity player) {
		RaidSession raid = ACTIVE.get(player.getServer());
		if (raid == null) return "Нет активного рейда";
		int seconds = (raid.remaining + 19) / 20;
		return raid.arenaName + "  ·  " + String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
	}

	public static long scavDelayMs(ServerPlayerEntity player) {
		RaidSession raid = ACTIVE.get(player.getServer());
		if (raid == null || raid.remaining <= LAST_SCAV_ENTRY || raid.arena.scavSpawns.isEmpty()
			|| !isLobby(player) || !player.isAlive()) return Long.MAX_VALUE;
		long unlock = Math.max(PlayerProfileState.get(player.getServer()).scavCooldownEndMs(player.getUuid()),
			raid.pmcDeaths.getOrDefault(player.getUuid(), 0L));
		return Math.max(0L, unlock - System.currentTimeMillis());
	}

	public static boolean joinScav(ServerPlayerEntity player) {
		long delay = scavDelayMs(player);
		if (delay != 0) {
			player.sendMessage(Text.literal(delay == Long.MAX_VALUE ? "Вход за Дикого сейчас закрыт."
				: "Вход за Дикого через " + ((delay + 999) / 1000) + " с."), true);
			return false;
		}
		RaidSession raid = ACTIVE.get(player.getServer());
		var competitors = raid.livingPlayers();
		BlockPos spawn = raid.arena.scavSpawns.stream().filter(pos -> validPosition(raid.world, raid.arena, pos))
			.max(Comparator.comparingDouble(pos -> competitors.stream().mapToDouble(other ->
				other.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(pos))).min().orElse(0))).orElse(null);
		if (spawn == null) {
			player.sendMessage(Text.literal("Нет безопасной точки спавна Дикого."), true);
			return false;
		}
		raid.enter(player, true, spawn);
		return true;
	}

	private void tick() {
		elapsed++;
		if (--remaining <= 0) {
			end(true);
			return;
		}
		for (UUID id : List.copyOf(roster.keySet())) {
			Member member = roster.get(id);
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(id);
			if (member == null) continue;
			if (member.sleeper != null) {
				ChunkPos next = member.sleeper.getChunkPos();
				if (!next.equals(member.sleeperChunk)) {
					world.getChunkManager().addTicket(SLEEPER_TICKET, next, 2, id);
					world.getChunkManager().removeTicket(SLEEPER_TICKET, member.sleeperChunk, 2, id);
					member.sleeperChunk = next;
				}
				continue;
			}
			if (player == null || !player.isAlive()) continue;
			if (elapsed % 5 == 0 && !checkBoundary(player, member)) continue;
			if (member.leftAt < 0 && player.getServerWorld() == world && arena.contains(player.getX(), player.getZ())) {
				checkExtract(player, member);
			} else {
				member.extract = null;
			}
			if (elapsed % 20 == 0 && member.extract == null && member.leftAt < 0) {
				player.sendMessage(Text.literal(status(player)), true);
			}
		}
	}

	private boolean checkBoundary(ServerPlayerEntity player, Member member) {
		boolean inside = player.getServerWorld() == world && arena.contains(player.getX(), player.getZ());
		if (inside) {
			member.leftAt = -1;
			if (elapsed % 10 == 0) ArenaBoundaryTicker.warn(world, arena, player);
			return true;
		}
		member.extract = null;
		if (member.leftAt < 0) member.leftAt = elapsed;
		int time = 100 - (elapsed - member.leftAt);
		if (time > 0) {
			player.sendMessage(Text.literal("Вернитесь в зону боевых действий  ·  " + ((time + 19) / 20) + " с"), true);
			return false;
		}
		var holder = (PlayerLoadoutHolder) player;
		holder.tarkovcraft$getBodyHealth().applyHit(holder.tarkovcraft$getLoadout(), HitZone.THORAX, 10000, 100,
			1f, true, false, 1f, "Снайпер периметра", "", "", 0f);
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
			SoundCategory.PLAYERS, 1f, 0.6f);
		player.setHealth(0f);
		player.onDeath(player.getDamageSources().genericKill());
		return false;
	}

	private void checkExtract(ServerPlayerEntity player, Member member) {
		var nearest = arena.extracts.entrySet().stream().filter(entry ->
			player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(entry.getValue().pos())) <= 9)
			.min(Comparator.comparingDouble(entry ->
				player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(entry.getValue().pos())))).orElse(null);
		if (nearest == null) {
			member.extract = null;
			return;
		}
		var exit = nearest.getValue();
		int duration = exit.loud() ? 500 : 160;
		if (!nearest.getKey().equals(member.extract)) {
			member.extract = nearest.getKey();
			member.extractAt = elapsed + duration;
		}
		int time = member.extractAt - elapsed;
		if (time <= 0) {
			finish(player, member, member.scav ? "SCAV_EXTRACTED" : "EXTRACTED", null, true, true);
		} else if ((duration - time) % 20 == 0) {
			player.sendMessage(Text.literal(member.extract + "  ·  " + ((time + 19) / 20) + " с"), true);
			if (exit.loud()) {
				BlockPos pos = exit.pos();
				world.playSound(null, pos, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 3f, 0.8f);
				world.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5,
					pos.getZ() + 0.5, 8, 0.65, 0.1, 0.65, 0.015);
			}
		}
	}

	private void onPlayerDeath(ServerPlayerEntity player) {
		Member member = roster.get(player.getUuid());
		if (member == null) return;
		var holder = (PlayerLoadoutHolder) player;
		if (player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
			var extras = RaidEquipment.rawItems(player).stream().map(ItemStack::copy).toList();
			var overflow = new ArrayList<ItemStack>();
			player.getWorld().spawnEntity(CorpseEntity.fromPlayer(
				player.getWorld(), player, holder.tarkovcraft$getLoadout(), extras, overflow));
			overflow.forEach(item -> player.dropItem(item, true, false));
		}
		finish(player, member, "KIA", holder.tarkovcraft$getBodyHealth().getLastFatalReport(), false, false);
	}

	private void disconnect(ServerPlayerEntity player) {
		Member member = roster.get(player.getUuid());
		if (member == null || !player.isAlive() || member.sleeper != null) return;
		if (player.getServerWorld() != world) {
			finish(player, member, "MIA", null, false, false);
			return;
		}
		player.closeHandledScreen();
		SleeperPlayerEntity sleeper = SleeperPlayerEntity.fromPlayer(player);
		if (!world.spawnEntity(sleeper)) {
			finish(player, member, "MIA", null, false, false);
			return;
		}
		member.sleeper = sleeper;
		member.sleeperChunk = sleeper.getChunkPos();
		member.extract = null;
		world.getChunkManager().addTicket(SLEEPER_TICKET, member.sleeperChunk, 2, player.getUuid());
		NbtCompound profile = profile(player);
		profile.put("Secure", RaidEquipment.secure(player));
		profile.putBoolean("Detached", true);
		save(player, profile);
		RaidEquipment.clear(player, false);
	}

	public static void onSleeperDeath(SleeperPlayerEntity sleeper, DamageSource source) {
		if (!(sleeper.getWorld() instanceof ServerWorld world)) return;
		RaidSession raid = running(world);
		if (raid == null) return;
		UUID owner = sleeper.getOwnerUuid();
		Member member = raid.roster.get(owner);
		if (member == null || member.sleeper != sleeper) return;
		raid.finishOffline(owner, member, "KIA_OFFLINE", sleeper.getBodyHealth().getLastFatalReport());
	}

	private static void onJoin(ServerPlayerEntity player) {
		RaidSession raid = ACTIVE.get(player.getServer());
		Member member = raid == null ? null : raid.roster.get(player.getUuid());
		if (member != null && member.sleeper != null && member.sleeper.isAlive()) {
			member.sleeper.restore(player);
			player.changeGameMode(GameMode.ADVENTURE);
			raid.releaseSleeper(player.getUuid(), member);
			NbtCompound profile = profile(player);
			profile.remove("Detached");
			save(player, profile);
			RaidEquipment.sync(player);
			return;
		}
		NbtCompound profile = profile(player);
		if (profile.contains("Mode")) {
			if (member != null) raid.finishOffline(player.getUuid(), member, "MIA", null);
			else {
				boolean scav = profile.getString("Mode").equals("SCAV");
				if (!scav && !profile.getBoolean("Detached")) profile.put("Secure", RaidEquipment.secure(player));
				recordResult(profile, scav, "MIA", null);
				save(player, profile);
			}
		}
		if (profile(player).contains("Result")) {
			completeLobbyArrival(player, true);
		} else if (lobbyWorld(player.getServer()) != null) {
			moveToLobby(player);
			RaidScreenHandler.openLobby(player);
		}
	}

	private void finish(ServerPlayerEntity player, Member member, String result, DamageReport report,
		boolean extracted, boolean showResult) {
		player.closeHandledScreen();
		NbtCompound profile = profile(player);
		if (member.scav && extracted) captureScavLoot(player, profile);
		if (!member.scav) profile.put("Secure", RaidEquipment.secure(player));
		recordResult(profile, member.scav, result, report);
		leave(player.getUuid(), member, result);
		save(player, profile);
		if (player.isAlive()) completeLobbyArrival(player, showResult);
	}

	private void finishOffline(UUID id, Member member, String result, DamageReport report) {
		PlayerProfileState profiles = PlayerProfileState.get(world.getServer());
		NbtCompound profile = profiles.get(id);
		recordResult(profile, member.scav, result, report);
		leave(id, member, result);
		profiles.put(id, profile);
	}

	private void leave(UUID id, Member member, String result) {
		roster.remove(id);
		releaseSleeper(id, member);
		if (!member.scav && (result.equals("KIA") || result.equals("KIA_OFFLINE"))) {
			pmcDeaths.put(id, System.currentTimeMillis() + PMC_DEATH_DELAY_MS);
		}
	}

	private void releaseSleeper(UUID id, Member member) {
		if (member.sleeper == null) return;
		world.getChunkManager().removeTicket(SLEEPER_TICKET, member.sleeperChunk, 2, id);
		member.sleeper.discard();
		member.sleeper = null;
	}

	private void end(boolean showResult) {
		ACTIVE.remove(world.getServer());
		openables.stop();
		restoreWeather();
		for (var entry : List.copyOf(roster.entrySet())) {
			ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
			if (player != null && entry.getValue().sleeper == null) {
				finish(player, entry.getValue(), "MIA", null, false, showResult);
			} else {
				finishOffline(entry.getKey(), entry.getValue(), "MIA", null);
			}
		}
		ArenaEventsState.get(world).retiredEntities.addAll(unloadedEntities);
		ArenaResetService.reset(world, arena, interactedOpenables);
	}

	private static void recordResult(NbtCompound profile, boolean scav, String result, DamageReport report) {
		profile.remove("Mode");
		profile.remove("Detached");
		profile.putString("Result", result);
		profile.remove("Report");
		if (report != null) profile.put("Report", report.toNbt());
		if (scav) profile.putLong("ScavCooldownEndMs", System.currentTimeMillis() + SCAV_COOLDOWN_MS);
	}

	private static void completeLobbyArrival(ServerPlayerEntity player, boolean showResult) {
		NbtCompound profile = profile(player);
		String result = profile.getString("Result");
		if (!profile.getBoolean("Restored")) {
			if (profile.contains("PmcEquipment")) {
				RaidEquipment.restore(player, profile.getCompound("PmcEquipment"));
			} else if (!result.equals("EXTRACTED")) {
				RaidEquipment.clear(player, false);
				((PlayerLoadoutHolder) player).tarkovcraft$getLoadout().readNbt(profile.getCompound("Secure"));
			}
			profile.remove("Secure");
			profile.remove("PmcEquipment");
			profile.putBoolean("Restored", true);
			RaidEquipment.sync(player);
		}
		boolean arrived = moveToLobby(player);
		if (showResult && arrived) {
			RaidScreenHandler.openSummary(player, result, profile.getString("Arena"),
				profile.contains("Report") ? profile.getCompound("Report") : null);
			profile.remove("Result");
			profile.remove("Restored");
			profile.remove("Report");
		}
		save(player, profile);
	}

	public static ServerWorld lobbyWorld(MinecraftServer server) {
		String dimension = PlayerProfileState.get(server).lobbyDimension();
		for (ServerWorld candidate : server.getWorlds()) {
			if (candidate.getRegistryKey().getValue().toString().equals(dimension)) return candidate;
		}
		return null;
	}

	public static boolean moveToLobby(ServerPlayerEntity player) {
		ServerWorld lobby = lobbyWorld(player.getServer());
		BlockPos pos = PlayerProfileState.get(player.getServer()).lobbyPos();
		if (lobby != null && pos != null) {
			player.setCameraEntity(player);
			player.changeGameMode(GameMode.ADVENTURE);
			teleport(player, lobby, pos);
			LobbyManager.arrive(player);
			return true;
		}
		return false;
	}

	private static void teleport(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
		player.stopRiding();
		player.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), player.getPitch());
		player.setVelocity(0, 0, 0);
		player.fallDistance = 0;
	}

	private static NbtCompound profile(ServerPlayerEntity player) {
		return PlayerProfileState.get(player.getServer()).get(player.getUuid());
	}

	private static void save(ServerPlayerEntity player, NbtCompound profile) {
		PlayerProfileState.get(player.getServer()).put(player.getUuid(), profile);
	}

	private static void captureScavLoot(ServerPlayerEntity player, NbtCompound profile) {
		PlayerLoadout loadout = ((PlayerLoadoutHolder) player).tarkovcraft$getLoadout();
		NbtList items = profile.getList("PendingLoot", NbtElement.COMPOUND_TYPE);
		for (EquipmentSlotType slot : EquipmentSlotType.values()) append(items, loadout.getEquipment(slot));
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) append(items, loadout.getPocket(i));
		RaidEquipment.rawItems(player).forEach(stack -> append(items, stack));
		loadout.getSecureContainer().getEntries().forEach(entry -> append(items, entry.getStack()));
		profile.put("PendingLoot", items);
	}

	private static void append(NbtList items, ItemStack stack) {
		if (!stack.isEmpty()) items.add(stack.writeNbt(new NbtCompound()));
	}

	public static List<ItemStack> pendingLoot(ServerPlayerEntity player) {
		return profile(player).getList("PendingLoot", NbtElement.COMPOUND_TYPE).stream()
			.map(tag -> ItemStack.fromNbt((NbtCompound) tag)).toList();
	}

	public static boolean claimLoot(ServerPlayerEntity player, int index) {
		if (!isLobby(player) || !player.isAlive()) return false;
		NbtCompound profile = profile(player);
		var items = pendingLoot(player);
		if (index < -1 || index >= items.size() || items.isEmpty()) return false;
		GridContainer stash = PlayerProfileState.get(player.getServer()).stash(player.getUuid());
		NbtList remainder = new NbtList();
		boolean changed = false;
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = items.get(i);
			int count = stack.getCount();
			if (index == -1 || i == index) stash.tryAutoPlace(stack);
			changed |= stack.getCount() != count;
			append(remainder, stack);
		}
		if (changed) {
			profile.put("PendingLoot", remainder);
			save(player, profile);
		}
		return changed;
	}

	private static final class Member {
		final boolean scav;
		int leftAt = -1;
		int extractAt;
		String extract;
		SleeperPlayerEntity sleeper;
		ChunkPos sleeperChunk;

		Member(boolean scav) {
			this.scav = scav;
		}
	}
}
