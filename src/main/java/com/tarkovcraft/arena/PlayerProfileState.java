package com.tarkovcraft.arena;

import com.tarkovcraft.grid.GridContainer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerProfileState extends PersistentState {
	public static final long STARTING_RUBLES = 150_000L;
	public static final int STASH_WIDTH = 10;
	public static final int STASH_HEIGHT = 24;
	private static final int PRESET_COUNT = 3;

	private final Map<UUID, GridContainer> stashes = new HashMap<>();
	private final NbtCompound players = new NbtCompound();
	private String lobbyDimension = "";
	private BlockPos lobbyPos;

	public static PlayerProfileState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(
			PlayerProfileState::read, PlayerProfileState::new, "tarkovcraft_raid_profiles");
	}

	NbtCompound get(UUID player) {
		return stored(player).copy();
	}

	private NbtCompound stored(UUID player) {
		return players.getCompound(player.toString());
	}

	void put(UUID player, NbtCompound profile) {
		if (stashes.containsKey(player)) profile.remove("Stash");
		players.put(player.toString(), profile);
		markDirty();
	}

	public long rubles(UUID player) {
		var profile = stored(player);
		return profile.contains("Rubles", NbtElement.NUMBER_TYPE) ? profile.getLong("Rubles") : STARTING_RUBLES;
	}

	public void setRubles(UUID player, long rubles) {
		if (rubles < 0) throw new IllegalArgumentException("Negative balance");
		NbtCompound profile = get(player);
		profile.putLong("Rubles", rubles);
		put(player, profile);
	}

	public int pendingLootCount(UUID player) {
		return stored(player).getList("PendingLoot", NbtElement.COMPOUND_TYPE).size();
	}

	public long scavCooldownEndMs(UUID player) {
		return stored(player).getLong("ScavCooldownEndMs");
	}

	public GridContainer stash(UUID player) {
		return stashes.computeIfAbsent(player, id -> {
			NbtCompound profile = stored(id);
			GridContainer stash = GridContainer.fromNbt(profile.getCompound("Stash"), STASH_WIDTH, STASH_HEIGHT);
			profile.remove("Stash");
			return stash;
		});
	}

	public KitPreset preset(UUID player, int index) {
		checkPresetIndex(index);
		NbtList presets = stored(player).getList("KitPresets", NbtElement.COMPOUND_TYPE);
		return new KitPreset(presets.getCompound(index));
	}

	public void setPreset(UUID player, int index, KitPreset preset) {
		checkPresetIndex(index);
		NbtCompound profile = get(player);
		NbtList presets = profile.getList("KitPresets", NbtElement.COMPOUND_TYPE);
		while (presets.size() < PRESET_COUNT) presets.add(new NbtCompound());
		presets.set(index, preset.equipment());
		profile.put("KitPresets", presets);
		put(player, profile);
	}

	private static void checkPresetIndex(int index) {
		if (index < 0 || index >= PRESET_COUNT) throw new IndexOutOfBoundsException(index);
	}

	public record KitPreset(NbtCompound equipment) {
		public KitPreset {
			equipment = equipment.copy();
		}

		@Override
		public NbtCompound equipment() {
			return equipment.copy();
		}
	}

	public void setLobby(String dimension, BlockPos pos) {
		lobbyDimension = dimension;
		lobbyPos = pos.toImmutable();
		markDirty();
	}

	public String lobbyDimension() {
		return lobbyDimension;
	}

	public BlockPos lobbyPos() {
		return lobbyPos;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtCompound savedPlayers = players.copy();
		stashes.forEach((uuid, stash) -> {
			String id = uuid.toString();
			NbtCompound player = savedPlayers.getCompound(id);
			player.put("Stash", stash.toNbt());
			savedPlayers.put(id, player);
		});
		nbt.put("Players", savedPlayers);
		if (lobbyPos != null) {
			nbt.putString("LobbyDimension", lobbyDimension);
			nbt.putLong("LobbyPos", lobbyPos.asLong());
		}
		return nbt;
	}

	static PlayerProfileState read(NbtCompound nbt) {
		PlayerProfileState state = new PlayerProfileState();
		state.players.copyFrom(nbt.getCompound("Players"));
		if (nbt.contains("LobbyPos")) {
			state.setLobby(nbt.getString("LobbyDimension"), BlockPos.fromLong(nbt.getLong("LobbyPos")));
			state.setDirty(false);
		}
		return state;
	}
}
