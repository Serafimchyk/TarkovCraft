package com.tarkovcraft.client.screen;

import com.tarkovcraft.screen.RaidScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import static com.tarkovcraft.client.screen.TarkovUiTheme.*;
import static com.tarkovcraft.screen.RaidScreenHandler.BACK;

public abstract class RaidScreen extends HandledScreen<RaidScreenHandler> {
	protected RaidScreen(RaidScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	public static RaidScreen create(RaidScreenHandler handler, PlayerInventory inventory, Text title) {
		return switch (handler.mode()) {
			case SUMMARY -> new RaidSummaryScreen(handler, inventory, title);
			case TRANSFER -> new ScavTransferScreen(handler, inventory, title);
		};
	}

	protected ButtonWidget button(Text label, int id, int x, int y, int width) {
		return addDrawableChild(ButtonWidget.builder(label, button -> send(id)).dimensions(x, y, width, 20).build());
	}

	protected void send(int id) {
		if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id);
	}

	@Override
	public final void close() { send(BACK); }

	@Override
	protected final void drawForeground(DrawContext context, int mouseX, int mouseY) { }

	protected void panel(DrawContext context, int x, int y, int w, int h) {
		context.fill(x, y, x + w, y + h, BG_PANEL);
		context.drawBorder(x, y, w, h, BORDER_PANEL);
	}

	protected void text(DrawContext context, Text text, int x, int y, int availableWidth, int color) {
		LobbyTopNavBar.line(context, textRenderer, text.getString(), x, y, availableWidth, color, 0.9f);
	}
}
