package com.tarkovcraft;

import com.tarkovcraft.ammo.AmmoReloadTracker;
import com.tarkovcraft.command.TarkovCommand;
import com.tarkovcraft.container.VanillaContainerBridge;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.health.BodyHealthTicker;
import com.tarkovcraft.health.TaCZBulletHelper;
import com.tarkovcraft.item.HeldItemInteraction;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.network.AmmoSelectionNetworking;
import com.tarkovcraft.network.EquipmentAppearanceSync;
import com.tarkovcraft.network.TarkovNetworking;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModEntities;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.registry.ModScreenHandlers;
import com.tarkovcraft.screen.TarkovScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.BlockState;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
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
		AmmoSelectionNetworking.register();
		EquipmentAppearanceSync.register();
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> TaCZBulletHelper.onEntityLoad(entity));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> TaCZBulletHelper.onEntityUnload(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TaCZBulletHelper.clearProjectiles());

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

		// Tick open TarkovScreenHandlers + bleeding/injury penalties + TaCZ reload tracking + Airdrops + MedUse
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			com.tarkovcraft.airdrop.AirdropManager.tickServer(server);
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				BodyHealthTicker.tickPlayer(player);
				com.tarkovcraft.health.MedicalUseManager.tickPlayer(player);
				AmmoReloadTracker.tickPlayer(player);
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.tickServer(player);
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			AmmoReloadTracker.removePlayer(handler.getPlayer().getUuid());
			com.tarkovcraft.health.MedicalUseManager.removePlayer(handler.getPlayer().getUuid());
		});

		// Sync loadout & body health when player joins
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			if (player instanceof PlayerLoadoutHolder holder) {
				holder.tarkovcraft$syncLoadout();
				holder.tarkovcraft$syncBodyHealth();
			}
		});

		// Keep loadout & health on dimension change or KEEP_INVENTORY; reset body health on respawn
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (oldPlayer instanceof PlayerLoadoutHolder oldHolder && newPlayer instanceof PlayerLoadoutHolder newHolder) {
				boolean keepInv = oldPlayer.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY);
				if (alive || keepInv) {
					newHolder.tarkovcraft$getLoadout().readNbt(oldHolder.tarkovcraft$getLoadout().writeNbt());
				}
				if (alive) {
					newHolder.tarkovcraft$getBodyHealth().readNbt(oldHolder.tarkovcraft$getBodyHealth().writeNbt());
				} else {
					newHolder.tarkovcraft$getBodyHealth().resetFull();
				}
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> com.tarkovcraft.airdrop.AirdropManager.resetTimer());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> com.tarkovcraft.airdrop.AirdropManager.resetTimer());

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (newPlayer instanceof PlayerLoadoutHolder holder) {
				holder.tarkovcraft$syncLoadout();
				holder.tarkovcraft$syncBodyHealth();
			}
		});

		// Vanilla storage containers: track player-placed containers so hourly city auto-loot never overwrites personal storage,
		// and intercept Survival/Adventure RMB into EFT search UI (respecting Shift+RMB, Creative, and TaCZ ADS)
		java.util.Queue<java.util.Map.Entry<ServerWorld, BlockPos>> pendingPlayerPlaced = new java.util.concurrent.ConcurrentLinkedQueue<>();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			java.util.Map.Entry<ServerWorld, BlockPos> placed;
			while ((placed = pendingPlayerPlaced.poll()) != null) {
				VanillaContainerBridge.markPlayerPlaced(placed.getKey(), placed.getValue());
			}
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (player.isSpectator()) {
				return ActionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!world.isClient && world instanceof ServerWorld sw) {
				net.minecraft.item.ItemStack held = player.getStackInHand(hand);
				if (held.getItem() instanceof net.minecraft.item.BlockItem bi
					&& VanillaContainerBridge.isSupportedBlock(bi.getBlock())
					&& (!VanillaContainerBridge.isSupportedBlock(state.getBlock()) || player.shouldCancelInteraction() || player.isCreative())) {
					BlockPos placePos = state.isReplaceable() ? pos : pos.offset(hitResult.getSide());
					pendingPlayerPlaced.add(java.util.Map.entry(sw, placePos.toImmutable()));
				}
			}
			if (!VanillaContainerBridge.isSupportedBlock(state.getBlock())) {
				return ActionResult.PASS;
			}
			if (player.shouldCancelInteraction() || player.isCreative()) {
				return ActionResult.PASS;
			}
			if (HeldItemInteraction.takesPriority(player, hand)) {
				return ActionResult.PASS;
			}
			if (!world.isClient && hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity sp) {
				VanillaContainerBridge.tryOpenForPlayer(sp, pos);
			}
			return ActionResult.SUCCESS;
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerWorld serverWorld) {
				VanillaContainerBridge.removeForBrokenBlock(serverWorld, pos, state);
			}
		});

		LOGGER.info("TarkovCraft initialized (Grid Inventory, Zonal Armor, Ballistic Ammo, Workbench, Worldgen & Airdrops).");
	}
}
