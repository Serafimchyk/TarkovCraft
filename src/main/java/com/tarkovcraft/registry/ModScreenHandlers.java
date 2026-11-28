package com.tarkovcraft.registry;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.screen.AdminMenuScreenHandler;
import com.tarkovcraft.screen.LobbyPlayScreenHandler;
import com.tarkovcraft.screen.LobbyLoadoutScreenHandler;
import com.tarkovcraft.screen.LobbyServiceScreenHandler;
import com.tarkovcraft.screen.LobbyTraderScreenHandler;
import com.tarkovcraft.screen.RaidScreenHandler;
import com.tarkovcraft.screen.TarkovScreenHandler;
import com.tarkovcraft.screen.TarkovWorkbenchScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {
	public static final ScreenHandlerType<TarkovScreenHandler> TARKOV_SCREEN_HANDLER = Registry.register(
		Registries.SCREEN_HANDLER,
		new Identifier(TarkovCraftMod.MOD_ID, "tarkov_inventory"),
		new ExtendedScreenHandlerType<>(TarkovScreenHandler::fromClientBuf)
	);

	public static final ScreenHandlerType<TarkovWorkbenchScreenHandler> TARKOV_WORKBENCH_SCREEN_HANDLER = Registry.register(
		Registries.SCREEN_HANDLER,
		new Identifier(TarkovCraftMod.MOD_ID, "tarkov_workbench"),
		new ExtendedScreenHandlerType<>(TarkovWorkbenchScreenHandler::fromClientBuf)
	);
	public static final ScreenHandlerType<LobbyPlayScreenHandler> LOBBY_PLAY_SCREEN_HANDLER = Registry.register(
		Registries.SCREEN_HANDLER,
		new Identifier(TarkovCraftMod.MOD_ID, "lobby_play"),
		new ExtendedScreenHandlerType<>(LobbyPlayScreenHandler::fromClientBuf)
	);
	public static final ScreenHandlerType<LobbyTraderScreenHandler> LOBBY_TRADER_SCREEN_HANDLER = Registry.register(
		Registries.SCREEN_HANDLER, new Identifier(TarkovCraftMod.MOD_ID, "lobby_trader"),
		new ExtendedScreenHandlerType<>(LobbyTraderScreenHandler::fromClientBuf)
	);
	public static final ScreenHandlerType<LobbyLoadoutScreenHandler> LOBBY_LOADOUT_SCREEN_HANDLER = Registry.register(
		Registries.SCREEN_HANDLER, new Identifier(TarkovCraftMod.MOD_ID, "lobby_loadout"),
		new ExtendedScreenHandlerType<>(LobbyLoadoutScreenHandler::fromClientBuf)
	);
	public static final ScreenHandlerType<LobbyServiceScreenHandler> LOBBY_SERVICE_SCREEN_HANDLER = Registry.register(
		Registries.SCREEN_HANDLER, new Identifier(TarkovCraftMod.MOD_ID, "lobby_service"),
		new ExtendedScreenHandlerType<>(LobbyServiceScreenHandler::fromClientBuf)
	);
	public static final ScreenHandlerType<RaidScreenHandler> RAID_SCREEN_HANDLER = Registry.register(
		Registries.SCREEN_HANDLER,
		new Identifier(TarkovCraftMod.MOD_ID, "raid"),
		new ExtendedScreenHandlerType<>(RaidScreenHandler::fromClientBuf)
	);
	public static final ScreenHandlerType<AdminMenuScreenHandler> ADMIN_MENU_SCREEN_HANDLER = Registry.register(
		Registries.SCREEN_HANDLER,
		new Identifier(TarkovCraftMod.MOD_ID, "admin_menu"),
		new ScreenHandlerType<>(AdminMenuScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
	);

	private ModScreenHandlers() {
	}

	public static void init() {
	}
}
