package com.tarkovcraft.arena;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.tarkovcraft.TarkovCraftMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ArenaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
		.registerTypeAdapter(BlockPos.class, new PositionAdapter().nullSafe()).create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tarkovcraft_arenas.json");

	private ArenaConfig() { }

	static boolean load(MinecraftServer server) {
		if (!Files.exists(FILE)) return save(server);
		try {
			Config config = readSaves(server).get(saveName(server));
			if (config == null) return save(server);
			if (config.dimensions() == null) throw new IOException("Missing dimensions");
			Map<ServerWorld, Map<String, ArenaEventsState.Arena>> configured = new LinkedHashMap<>();
			for (ServerWorld world : server.getWorlds()) {
				Map<String, Definition> definitions = config.dimensions().get(world.getRegistryKey().getValue().toString());
				if (definitions == null) continue;
				var old = ArenaEventsState.get(world).definitions();
				Map<String, ArenaEventsState.Arena> converted = new LinkedHashMap<>();
				definitions.forEach((id, definition) -> {
					var arena = definition.toArena();
					var previous = old.get(id);
					if (previous != null) {
						arena.cratePos = previous.cratePos;
						arena.activeBoss = previous.activeBoss;
						arena.bossSpawned = previous.bossSpawned;
					}
					converted.put(id, arena);
				});
				configured.put(world, converted);
			}
			configured.forEach((world, definitions) -> {
				var state = ArenaEventsState.get(world);
				state.definitions().clear();
				state.definitions().putAll(definitions);
				state.markDirty();
			});
			if (config.lobby() != null) {
				PlayerProfileState.get(server).setLobby(config.lobby().dimension(), config.lobby().position());
			}
			return true;
		} catch (IOException | RuntimeException e) {
			TarkovCraftMod.LOGGER.error("Could not load {}", FILE, e);
			return false;
		}
	}

	static boolean save(MinecraftServer server) {
		Map<String, Map<String, Definition>> worlds = new LinkedHashMap<>();
		for (ServerWorld world : server.getWorlds()) {
			Map<String, Definition> arenas = new LinkedHashMap<>();
			ArenaEventsState.get(world).definitions().forEach((id, arena) -> arenas.put(id, Definition.from(arena)));
			worlds.put(world.getRegistryKey().getValue().toString(), arenas);
		}
		var profile = PlayerProfileState.get(server);
		Lobby lobby = profile.lobbyPos() == null ? null : new Lobby(profile.lobbyDimension(), profile.lobbyPos());
		Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
		try {
			Map<String, Config> saves = readSaves(server);
			saves.put(saveName(server), new Config(worlds, lobby));
			Files.createDirectories(FILE.getParent());
			Files.writeString(temporary, GSON.toJson(new SaveFile(saves)));
			Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (IOException | RuntimeException e) {
			TarkovCraftMod.LOGGER.error("Could not save {}", FILE, e);
			return false;
		}
	}

	private static String saveName(MinecraftServer server) {
		return server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().getFileName().toString();
	}

	private static Map<String, Config> readSaves(MinecraftServer server) throws IOException {
		if (!Files.exists(FILE)) return new LinkedHashMap<>();
		try (var reader = Files.newBufferedReader(FILE)) {
			var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
			if (json.has("dimensions")) return new LinkedHashMap<>(Map.of(saveName(server), GSON.fromJson(json, Config.class)));
			var file = GSON.fromJson(json, SaveFile.class);
			if (file.saves() == null) throw new IOException("Missing saves");
			return new LinkedHashMap<>(file.saves());
		}
	}

	private static final class PositionAdapter extends TypeAdapter<BlockPos> {
		@Override
		public void write(JsonWriter out, BlockPos pos) throws IOException {
			out.beginObject().name("x").value(pos.getX()).name("y").value(pos.getY()).name("z").value(pos.getZ()).endObject();
		}

		@Override
		public BlockPos read(JsonReader in) throws IOException {
			Integer x = null;
			Integer y = null;
			Integer z = null;
			in.beginObject();
			while (in.hasNext()) {
				switch (in.nextName()) {
					case "x" -> x = in.nextInt();
					case "y" -> y = in.nextInt();
					case "z" -> z = in.nextInt();
					default -> in.skipValue();
				}
			}
			in.endObject();
			if (x == null || y == null || z == null) throw new IOException("Incomplete position");
			return new BlockPos(x, y, z);
		}
	}

	private record SaveFile(Map<String, Config> saves) { }
	private record Config(Map<String, Map<String, Definition>> dimensions, Lobby lobby) { }
	private record Lobby(String dimension, BlockPos position) { }

	private record Definition(String displayName, BlockPos anchor, List<BlockPos> vertices, List<BlockPos> corners, int durationMinutes,
		boolean airdropsEnabled, int maxGroundY, boolean bossEnabled, Boolean scavBotsEnabled, Integer scavBotCount,
		Set<BlockPos> airdropPoints, Set<BlockPos> pointsOfInterest,
		Set<BlockPos> pmcSpawns, Set<BlockPos> scavSpawns, Map<String, ArenaEventsState.Extract> extracts, Set<BlockPos> lootPoints) {
		static Definition from(ArenaEventsState.Arena arena) {
			return new Definition(arena.displayName, arena.anchor, arena.corners(), null, arena.durationMinutes,
				arena.airdropsEnabled, arena.maxGroundY, arena.bossEnabled, arena.scavBotsEnabled, arena.scavBotCount,
				arena.airdropPoints, arena.pointsOfInterest,
				arena.pmcSpawns, arena.scavSpawns, arena.extracts, arena.lootPoints);
		}

		ArenaEventsState.Arena toArena() {
			var arena = new ArenaEventsState.Arena(anchor);
			arena.displayName = displayName == null ? "" : displayName;
			arena.durationMinutes = durationMinutes == 0 ? 15 : Math.max(1, Math.min(60, durationMinutes));
			arena.airdropsEnabled = airdropsEnabled;
			arena.maxGroundY = maxGroundY;
			arena.bossEnabled = bossEnabled;
			if (scavBotsEnabled != null) arena.scavBotsEnabled = scavBotsEnabled;
			if (scavBotCount != null) arena.scavBotCount = Math.max(0, Math.min(32, scavBotCount));
			if (vertices != null) arena.setVertices(vertices);
			else if (corners != null) arena.setVertices(ArenaPolygon.migrateCorners(corners));
			if (airdropPoints != null) arena.airdropPoints.addAll(airdropPoints);
			if (pointsOfInterest != null) arena.pointsOfInterest.addAll(pointsOfInterest);
			if (pmcSpawns != null) arena.pmcSpawns.addAll(pmcSpawns);
			if (scavSpawns != null) arena.scavSpawns.addAll(scavSpawns);
			if (extracts != null) arena.extracts.putAll(extracts);
			if (lootPoints != null) arena.lootPoints.addAll(lootPoints);
			return arena;
		}
	}
}
