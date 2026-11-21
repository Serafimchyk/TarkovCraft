package com.tarkovcraft;

import com.tarkovcraft.ammo.AmmoReloadTracker;
import com.tarkovcraft.command.TarkovCommand;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.health.BodyHealthTicker;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.network.TarkovNetworking;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModEntities;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.registry.ModScreenHandlers;
import com.tarkovcraft.screen.TarkovScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TarkovCraftMod implements ModInitializer {
	public static final String MOD_ID = "tarkovcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger("TarkovCraft");

	@Override
	public void onInitialize() {
		com.tarkovcraft.config.TarkovConfig.loadOrCreate();
		ModBlocks.init();
		ModItems.init();
		ModEntities.init();
		ModScreenHandlers.init();
		com.tarkovcraft.worldgen.ModWorldGen.init();
		com.tarkovcraft.entity.MobGearSpawner.register();
		TarkovNetworking.registerServerReceivers();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			TarkovCommand.register(dispatcher)
		);

		// Reload JSON item sizes from datapacks
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return new Identifier(MOD_ID, "item_sizes");
			}

			@Override
			public void reload(ResourceManager manager) {
				ItemSizeRegistry.reloadFromResources(manager);
			}
		});

		// Tick open TarkovScreenHandlers + bleeding/injury penalties + TaCZ reload tracking + Airdrops
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			com.tarkovcraft.airdrop.AirdropManager.tickServer(server);
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				BodyHealthTicker.tickPlayer(player);
				AmmoReloadTracker.tickPlayer(player);
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.tickServer(player);
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			AmmoReloadTracker.removePlayer(handler.getPlayer().getUuid())
		);

		// Sync loadout & body health when player joins
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			if (player instanceof PlayerLoadoutHolder holder) {
				holder.tarkovcraft$syncLoadout();
				holder.tarkovcraft$syncBodyHealth();
			}
		});

		// Keep loadout & health on dimension change; reset body health on respawn
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (oldPlayer instanceof PlayerLoadoutHolder oldHolder && newPlayer instanceof PlayerLoadoutHolder newHolder) {
				if (alive) {
					newHolder.tarkovcraft$getLoadout().readNbt(oldHolder.tarkovcraft$getLoadout().writeNbt());
					newHolder.tarkovcraft$getBodyHealth().readNbt(oldHolder.tarkovcraft$getBodyHealth().writeNbt());
				} else {
					newHolder.tarkovcraft$getBodyHealth().resetFull();
				}
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (newPlayer instanceof PlayerLoadoutHolder holder) {
				holder.tarkovcraft$syncLoadout();
				holder.tarkovcraft$syncBodyHealth();
			}
		});

		LOGGER.info("TarkovCraft initialized (Phase 1: Grid Inventory, Loadout, Corpses & Search).");
	}
}
