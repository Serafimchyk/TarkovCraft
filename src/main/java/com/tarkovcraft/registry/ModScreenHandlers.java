package com.tarkovcraft.registry;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.screen.TarkovScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {
	public static final ScreenHandlerType<TarkovScreenHandler> TARKOV_SCREEN_HANDLER = Registry.register(
		Registries.SCREEN_HANDLER,
		new Identifier(TarkovCraftMod.MOD_ID, "tarkov_inventory"),
		new ExtendedScreenHandlerType<>(TarkovScreenHandler::fromClientBuf)
	);

	private ModScreenHandlers() {
	}

	public static void init() {
	}
}
