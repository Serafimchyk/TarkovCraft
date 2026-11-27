package com.tarkovcraft.screen;

import com.tarkovcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class AdminMenuScreenHandler extends ScreenHandler {
	private final PlayerEntity owner;

	public AdminMenuScreenHandler(int syncId, PlayerInventory inventory) {
		super(ModScreenHandlers.ADMIN_MENU_SCREEN_HANDLER, syncId);
		owner = inventory.player;
	}

	public static boolean open(ServerPlayerEntity player) {
		if (!player.hasPermissionLevel(2)) return false;
		return player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
			(syncId, inventory, viewer) -> new AdminMenuScreenHandler(syncId, inventory),
			Text.translatableWithFallback("screen.tarkovcraft.admin", "TarkovCraft admin")
		)).isPresent();
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return player == owner && (!(player instanceof ServerPlayerEntity serverPlayer) || serverPlayer.hasPermissionLevel(2));
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}
}
