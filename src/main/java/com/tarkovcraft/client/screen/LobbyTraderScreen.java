package com.tarkovcraft.client.screen;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.lobby.TraderCatalogue.Trader;
import com.tarkovcraft.screen.LobbyTraderScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import static com.tarkovcraft.client.screen.LobbyTopNavBar.*;
import static com.tarkovcraft.client.screen.TarkovUiTheme.*;
import static com.tarkovcraft.screen.LobbyTraderScreenHandler.*;

public final class LobbyTraderScreen extends LobbyCommerceScreen<LobbyTraderScreenHandler> {
	public LobbyTraderScreen(LobbyTraderScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title, Tab.TRADERS);
	}

	@Override
	protected void drawContent(DrawContext context) {
		int w = Math.min(700, width - 16);
		int x = (width - w) / 2;
		int y = HEIGHT + 8;
		Trader[] traders = Trader.values();
		Text[] names = {label("trade.prapor", "Prapor"), label("trade.therapist", "Therapist"),
			label("trade.ragman", "Ragman"), label("trade.fence", "Fence")};
		for (int i = 0; i < traders.length; i++) {
			int left = x + i * w / traders.length;
			int right = x + (i + 1) * w / traders.length;
			button(context, names[i], left, y, right - left - 2, 19, i, true, handler.trader() == traders[i]);
		}

		y += 24;
		int bottom = height - 8;
		boolean fence = handler.trader() == Trader.FENCE;
		int basketW = fence ? Math.min(170, w * 43 / 100) : 0;
		if (fence) drawBasket(context, x, y, basketW, bottom - y);
		int listX = fence ? x + basketW + 6 : x;
		int listW = x + w - listX;
		panel(context, listX, y, listW, bottom - y);

		if (fence) {
			int half = (listW - 14) / 2;
			button(context, label("trade.stash", "Stash"), listX + 6, y + 6, half, 16, STASH, true, !handler.carried());
			button(context, label("trade.carried", "Character"), listX + 8 + half, y + 6, half, 16, CARRIED, true, handler.carried());
		} else {
			line(context, textRenderer, label("trade.delivery", "Purchases go to the stash").getString(),
				listX + 7, y + 10, listW - 14, TEXT_SECONDARY, 0.85f);
		}
		int firstY = y + 27;
		int rowHeight = Math.max(12, Math.min(25, (bottom - 26 - firstY) / ROWS));
		for (int i = 0; i < handler.rows().size(); i++)
			drawRow(context, handler.rows().get(i), listX + 6, firstY + i * rowHeight, listW - 12, rowHeight, i, fence);
		if (handler.rows().isEmpty())
			line(context, textRenderer, label("trade.empty", "No items available").getString(), listX + 7, firstY + 4,
				listW - 14, TEXT_MUTED, 0.85f);
		pagination(context, x + w - 6, bottom - 21, handler.page(), handler.pages(), PREVIOUS, NEXT);
	}

	private void drawRow(DrawContext context, Row row, int x, int y, int w, int h, int index, boolean fence) {
		boolean affordable = fence || handler.status().rubles() >= row.price();
		boolean hovered = hit(x, y, w, h);
		context.fill(x, y, x + w, y + h - 1, hovered && affordable ? HOVER_MENU_BG : BG_SLOT);
		int icon = Math.min(16, h - 2);
		drawScaledItem(context, row.stack(), x + 2, y + 1, icon, icon);
		String name = row.stack().getName().getString();
		if (AmmoTier.isTaczAmmoStack(row.stack())) name += " " + AmmoTier.getFromAmmoStack(row.stack()).getBadge();
		if (row.stack().getCount() > 1) name += " ×" + row.stack().getCount();
		String price = rubles(row.price());
		int nameX = x + icon + 6;
		int priceWidth = (int) Math.ceil(textRenderer.getWidth(price) * 0.88f);
		int textY = y + (h - 7) / 2;
		int badgeWidth = row.percent() == 0 ? 0 : 36;
		line(context, textRenderer, name, nameX, textY, x + w - priceWidth - badgeWidth - 10 - nameX, TEXT_PRIMARY, 0.83f);
		if (row.percent() != 0) {
			String percent = (row.percent() > 0 ? "+" : "") + row.percent() + "%";
			drawRightAlignedFlatText(context, textRenderer, percent, x + w - priceWidth - 10, textY,
				row.percent() < 0 ? GREEN : ALERT, 0.8f);
		}
		drawRightAlignedFlatText(context, textRenderer, price, x + w - 4, textY, affordable ? ACCENT_SAND : ALERT, 0.88f);
		if (affordable) clickArea(x, y, w, h, ROW_FIRST + index);
		if (hovered) hover(row.stack());
	}

	private void drawBasket(DrawContext context, int x, int y, int w, int h) {
		panel(context, x, y, w, h);
		line(context, textRenderer, label("trade.sell_grid", "Sale grid").getString(), x + 6, y + 8, w - 12, ACCENT_SAND, 0.9f);
		int cell = Math.max(5, Math.min((w - 12) / 6, (h - 50) / 6));
		int gx = x + (w - cell * 6) / 2;
		int gy = y + 23;
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 6; col++) {
				int sx = gx + col * cell;
				int sy = gy + row * cell;
				context.fill(sx, sy, sx + cell, sy + cell, BG_SLOT);
				context.drawBorder(sx, sy, cell, cell, BORDER_CELL);
			}
		}
		for (int i = 0; i < handler.basket().size(); i++) {
			BasketCell item = handler.basket().get(i);
			int sx = gx + item.x() * cell;
			int sy = gy + item.y() * cell;
			int sw = item.width() * cell;
			int sh = item.height() * cell;
			boolean hover = hit(sx, sy, sw, sh);
			context.fill(sx + 1, sy + 1, sx + sw - 1, sy + sh - 1, hover ? HOVER_MENU_BG : BG_PANEL);
			context.drawBorder(sx, sy, sw, sh, ACCENT_SAND);
			drawScaledItem(context, item.stack(), sx + 2, sy + 2, sw - 4, sh - 4);
			clickArea(sx, sy, sw, sh, BASKET_FIRST + i);
			if (hover) hover(item.stack());
		}
		long total = handler.basket().stream().mapToLong(BasketCell::price).sum();
		button(context, label("trade.sell", "Sell all · %s", rubles(total)), x + 6, y + h - 22, w - 12, 16,
			SELL, total > 0, false);
	}
}
