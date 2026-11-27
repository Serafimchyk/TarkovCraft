package com.tarkovcraft.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.IntFunction;

public abstract class LobbyScreenHandler extends ScreenHandler {
	private final PlayerEntity owner;
	private final LobbyScreenStatus status = new LobbyScreenStatus(this::addProperty);

	protected LobbyScreenHandler(ScreenHandlerType<?> type, int syncId, PlayerEntity owner) {
		super(type, syncId);
		this.owner = owner;
		if (owner instanceof ServerPlayerEntity player) status.refresh(player);
	}

	protected static void open(ServerPlayerEntity player, Text title, Consumer<PacketByteBuf> write,
			IntFunction<ScreenHandler> create) {
		if (player.currentScreenHandler instanceof LobbyScreenHandler) player.onHandledScreenClosed();
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity viewer, PacketByteBuf buf) { write.accept(buf); }

			@Override
			public Text getDisplayName() { return title; }

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity viewer) {
				return create.apply(syncId);
			}
		});
	}

	public final LobbyScreenStatus status() { return status; }

	@Override
	public final boolean canUse(PlayerEntity player) {
		return player == owner && (!(player instanceof ServerPlayerEntity serverPlayer) || LobbyTabNavigation.canUse(serverPlayer));
	}

	@Override
	public final ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

	@Override
	public final void sendContentUpdates() {
		if (owner instanceof ServerPlayerEntity player) status.refresh(player);
		super.sendContentUpdates();
	}

	@Override
	public final boolean onButtonClick(PlayerEntity player, int button) {
		return player instanceof ServerPlayerEntity serverPlayer && serverPlayer.currentScreenHandler == this
			&& canUse(player) && (LobbyTabNavigation.handle(serverPlayer, button) || handleButton(serverPlayer, button));
	}

	protected abstract boolean handleButton(ServerPlayerEntity player, int button);
}
