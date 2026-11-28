package com.tarkovcraft.arena;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZBotGun;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.loot.TarkovLootGenerator;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerProfileState extends PersistentState {
	public static final long STARTING_RUBLES = 500_000L;
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

	boolean deliverStartingSupplies(UUID player) {
		NbtCompound profile = get(player);
		boolean firstDelivery = !profile.getBoolean("StartingSuppliesGranted");
		NbtList supplies = firstDelivery ? startingSupplies()
			: profile.getList("StartingSuppliesPending", NbtElement.COMPOUND_TYPE);
		if (!firstDelivery && supplies.isEmpty()) return true;

		if (firstDelivery) {
			profile.putBoolean("StartingSuppliesGranted", true);
			profile.putLong("Rubles", Math.max(STARTING_RUBLES, rubles(player)));
		}
		GridContainer destination = stash(player);
		NbtList remaining = new NbtList();
		for (int i = 0; i < supplies.size(); i++) {
			ItemStack stack = ItemStack.fromNbt(supplies.getCompound(i));
			if (!stack.isEmpty() && !destination.tryAutoPlace(stack)) {
				remaining.add(stack.writeNbt(new NbtCompound()));
			}
		}
		if (remaining.isEmpty()) profile.remove("StartingSuppliesPending");
		else profile.put("StartingSuppliesPending", remaining);
		put(player, profile);
		return remaining.isEmpty();
	}

	private static NbtList startingSupplies() {
		NbtList supplies = new NbtList();
		for (String id : new String[]{"tacz:glock_17", "tacz:hk_mp5a5"}) {
			TaCZBotGun gun = TaCZBotGun.find(id);
			if (gun == null) continue;
			ItemStack weapon = TarkovLootGenerator.createLoadedGunStack(gun, AmmoTier.FMJ);
			if (weapon.isEmpty()) continue;
			supplies.add(weapon.writeNbt(new NbtCompound()));
			ItemStack ammo = AmmoTier.createTaczAmmoStack(gun.ammo().toString(), AmmoTier.FMJ, 30);
			supplies.add(ammo.writeNbt(new NbtCompound()));
			supplies.add(ammo.writeNbt(new NbtCompound()));
		}
		addSupplies(supplies, ModItems.PACA_ARMOR, 2);
		addSupplies(supplies, ModItems.SSH68_HELMET, 2);
		addSupplies(supplies, ModItems.BANK_ROBBER_RIG, 2);
		addSupplies(supplies, ModItems.BERKUT_BACKPACK, 2);
		addSupplies(supplies, ModItems.AI2_MEDKIT, 3);
		addSupplies(supplies, ModItems.BANDAGE, 3);
		addSupplies(supplies, ModItems.CAT_TOURNIQUET, 2);
		addSupplies(supplies, ModItems.SPLINT, 2);
		addSupplies(supplies, ModItems.ANALGIN, 2);
		Registries.ITEM.getOrEmpty(new Identifier("tarkovcraft:butylka_vody_0_6l_5448fe"))
			.ifPresent(item -> addSupplies(supplies, item, 2));
		Registries.ITEM.getOrEmpty(new Identifier("tarkovcraft:sukhoy_paek_iskra_590c5d"))
			.ifPresent(item -> addSupplies(supplies, item, 2));
		return supplies;
	}

	private static void addSupplies(NbtList supplies, Item item, int count) {
		while (count > 0) {
			int amount = Math.min(count, item.getMaxCount());
			supplies.add(new ItemStack(item, amount).writeNbt(new NbtCompound()));
			count -= amount;
		}
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

	public void replaceStash(UUID player, GridContainer stash) {
		stashes.put(player, stash);
		markDirty();
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
