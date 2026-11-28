package com.tarkovcraft.arena;

import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerProfileStateTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
	}

	@Test
	void legacyRaidDataLoadsAndStashUpdatesSurviveSaving() {
		UUID player = UUID.randomUUID();
		GridContainer stash = new GridContainer(10, 24);
		var legacyEntry = stash.place(new ItemStack(Items.APPLE, 5), 0, 23, false);

		NbtCompound legacyProfile = new NbtCompound();
		legacyProfile.put("Stash", stash.toNbt());
		legacyProfile.putLong("ScavCooldownEndMs", 1234567);
		legacyProfile.putString("Result", "KIA_OFFLINE");
		NbtCompound players = new NbtCompound();
		players.put(player.toString(), legacyProfile);
		NbtCompound legacy = new NbtCompound();
		legacy.put("Players", players);
		legacy.putString("LobbyDimension", "minecraft:overworld");
		legacy.putLong("LobbyPos", new BlockPos(8, 70, 12).asLong());

		PlayerProfileState profile = PlayerProfileState.read(legacy);
		assertEquals(500_000L, profile.rubles(player));
		assertEquals(1234567L, profile.scavCooldownEndMs(player));
		assertEquals(new BlockPos(8, 70, 12), profile.lobbyPos());
		assertEquals(5, profile.stash(player).getEntry(legacyEntry.getEntryId()).getStack().getCount());
		assertTrue(profile.stash(UUID.randomUUID()).isEmpty());

		profile.stash(player).getEntry(legacyEntry.getEntryId()).getStack().decrement(2);
		profile.setRubles(player, 60_000_000_000L);
		NbtCompound kit = new NbtCompound();
		kit.putString("Name", "Custom kit");
		profile.setPreset(player, 2, new PlayerProfileState.KitPreset(kit));
		kit.putString("Name", "Edited input");

		PlayerProfileState reloaded = PlayerProfileState.read(profile.writeNbt(new NbtCompound()));
		assertEquals(60_000_000_000L, reloaded.rubles(player));
		assertEquals(3, reloaded.stash(player).getEntry(legacyEntry.getEntryId()).getStack().getCount());
		assertEquals("KIA_OFFLINE", reloaded.get(player).getString("Result"));
		assertEquals("Custom kit", reloaded.preset(player, 2).equipment().getString("Name"));
		assertTrue(reloaded.preset(player, 0).equipment().isEmpty());
	}

	@Test
	void suppliesAreGrantedOnceAndSurviveAFullStashAndReload() {
		UUID player = UUID.randomUUID();
		PlayerProfileState profile = new PlayerProfileState();
		profile.setRubles(player, 440);
		profile.replaceStash(player, new GridContainer(1, 1));
		assertNotNull(profile.stash(player).place(new ItemStack(Items.APPLE), 0, 0, false));
		assertFalse(profile.deliverStartingSupplies(player));
		assertEquals(500_000L, profile.rubles(player));
		assertEquals(Items.APPLE, profile.stash(player).getAt(0, 0).getStack().getItem());

		profile.setRubles(player, 450_000);
		PlayerProfileState reloaded = PlayerProfileState.read(profile.writeNbt(new NbtCompound()));
		reloaded.replaceStash(player, new GridContainer(10, 24));
		assertTrue(reloaded.deliverStartingSupplies(player));
		assertEquals(450_000L, reloaded.rubles(player));
		var entries = reloaded.stash(player).getEntries();
		assertEquals(2, entries.stream().filter(entry -> entry.getStack().isOf(ModItems.PACA_ARMOR)).count());
		assertTrue(reloaded.deliverStartingSupplies(player));
		assertEquals(entries.size(), reloaded.stash(player).getEntries().size());
	}
}
