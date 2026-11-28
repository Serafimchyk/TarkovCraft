package com.tarkovcraft.client.screen;

import com.tarkovcraft.screen.LobbyScreenHandler;
import com.tarkovcraft.screen.LobbyTabNavigation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static com.tarkovcraft.client.screen.LobbyTopNavBar.*;
import static com.tarkovcraft.client.screen.TarkovUiTheme.*;

abstract class LobbyCommerceScreen<T extends LobbyScreenHandler> extends HandledScreen<T> {
	private final Tab tab;
	private final List<Hitbox> actions = new ArrayList<>();
	private ItemStack hoveredItem;
	protected int pointerX;
	protected int pointerY;

	protected LobbyCommerceScreen(T handler, PlayerInventory inventory, Text title, Tab tab) {
		super(handler, inventory, title);
		this.tab = tab;
	}

	@Override
	protected final void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) { }

	@Override
	public final void render(DrawContext context, int mouseX, int mouseY, float delta) {
		actions.clear();
		hoveredItem = ItemStack.EMPTY;
		pointerX = mouseX;
		pointerY = mouseY;
		drawLobbyBackground(context, width, height);
		clearDepthBuffer(context);
		drawContent(context);
		LobbyTopNavBar.render(context, textRenderer, width, mouseX, mouseY, tab, status(client, handler.status()));
		if (!hoveredItem.isEmpty()) context.drawItemTooltip(textRenderer, hoveredItem, mouseX, mouseY);
	}

	protected abstract void drawContent(DrawContext context);

	protected void panel(DrawContext context, int x, int y, int w, int h) {
		context.fill(x, y, x + w, y + h, opaque(BG_PANEL));
		context.drawBorder(x, y, w, h, BORDER_PANEL);
	}

	protected void button(DrawContext context, Text text, int x, int y, int w, int h, int id, boolean enabled, boolean selected) {
		boolean hover = hit(x, y, w, h);
		context.fill(x, y, x + w, y + h, hover && enabled ? HOVER_MENU_BG : opaque(BG_SLOT));
		context.drawBorder(x, y, w, h, selected ? ACCENT_SAND : BORDER_CELL);
		String label = trimWithEllipsis(textRenderer, text.getString(), w - 6, 0.85f);
		drawCenteredFlatText(context, textRenderer, label, x + w / 2, y + (h - 7) / 2,
			hover && enabled ? HOVER_MENU_TEXT : selected ? ACCENT_SAND : enabled ? TEXT_PRIMARY : TEXT_MUTED, 0.85f);
		if (enabled) clickArea(x, y, w, h, id);
	}

	protected void clickArea(int x, int y, int w, int h, int id) {
		actions.add(new Hitbox(new LobbyLayoutMetrics.Rect(x, y, w, h), id));
	}

	protected boolean hit(int x, int y, int w, int h) {
		return new LobbyLayoutMetrics.Rect(x, y, w, h).contains(pointerX, pointerY);
	}

	protected void hover(ItemStack stack) { hoveredItem = stack; }

	protected void pagination(DrawContext context, int right, int y, int page, int pages, int previous, int next) {
		button(context, Text.literal("‹"), right - 82, y, 22, 17, previous, page > 0, false);
		drawCenteredFlatText(context, textRenderer, (page + 1) + " / " + pages, right - 41, y + 5, TEXT_SECONDARY, 0.8f);
		button(context, Text.literal("›"), right - 22, y, 22, 17, next, page + 1 < pages, false);
	}

	@Override
	public final boolean mouseClicked(double x, double y, int button) {
		if (LobbyTopNavBar.mouseClicked(width, x, y, button, tab, status(client, handler.status()),
				action -> activate(client, this, handler.syncId, action))) return true;
		if (button != 0) return false;
		for (Hitbox action : actions) {
			if (action.bounds().contains(x, y)) {
				send(action.id());
				return true;
			}
		}
		return false;
	}

	@Override
	public final void close() { send(closeAction()); }

	protected int closeAction() { return LobbyTabNavigation.PLAY; }

	protected final void send(int id) {
		if (client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id);
	}

	private record Hitbox(LobbyLayoutMetrics.Rect bounds, int id) { }
}
