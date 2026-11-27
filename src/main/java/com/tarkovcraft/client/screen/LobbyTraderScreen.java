package com.tarkovcraft.client.screen;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.lobby.TraderCatalogue.Trader;
import com.tarkovcraft.lobby.TraderCatalogue.Category;
import net.minecraft.client.item.TooltipContext;
import java.util.Locale;
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
		if (handler.preview() >= 0) {
			drawPreview(context, handler.rows().get(handler.preview()));
			return;
		}
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
		if (!fence) y = drawCategories(context, x, y, w);
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
		int rowHeight = Math.max(10, Math.min(25, (bottom - 26 - firstY) / ROWS));
		for (int i = 0; i < handler.rows().size(); i++)
			drawRow(context, handler.rows().get(i), listX + 6, firstY + i * rowHeight, listW - 12, rowHeight, i, fence);
		if (handler.rows().isEmpty())
			line(context, textRenderer, label("trade.empty", "No items available").getString(), listX + 7, firstY + 4,
				listW - 14, TEXT_MUTED, 0.85f);
		if (!handler.notice().getString().isEmpty())
			line(context, textRenderer, handler.notice().getString(), listX + 7, bottom - 16, listW - 100, ACCENT_SAND, 0.83f);
		pagination(context, x + w - 6, bottom - 21, handler.page(), handler.pages(), PREVIOUS, NEXT);
	}

	@Override
	protected int closeAction() { return handler.preview() >= 0 ? CANCEL_PREVIEW : super.closeAction(); }

	private int drawCategories(DrawContext context, int x, int y, int w) {
		var categories = handler.trader().categories();
		int count = categories.size() + 1;
		int columns = count > 5 && w < 650 ? 4 : count;
		for (int i = 0; i < count; i++) {
			Category category = i == 0 ? Category.ALL : categories.get(i - 1);
			int left = x + (i % columns) * w / columns;
			int right = x + (i % columns + 1) * w / columns;
			button(context, label("trade.category." + category.name().toLowerCase(Locale.ROOT), category.name()),
				left, y + (i / columns) * 18, right - left - 2, 16, CATEGORY_FIRST + category.ordinal(),
				true, handler.category() == category);
		}
		return y + ((count + columns - 1) / columns) * 18 + 4;
	}

	private void drawPreview(DrawContext context, Row row) {
		int w = Math.min(360, width - 24);
		int h = Math.min(190, height - HEIGHT - 16);
		int x = (width - w) / 2;
		int y = HEIGHT + (height - HEIGHT - h) / 2;
		panel(context, x, y, w, h);
		line(context, textRenderer, label("trade.preview", "Purchase preview").getString(),
			x + 12, y + 12, w - 24, ACCENT_SAND, 1);
		int iconSize = Math.min(75, w / 4);
		context.fill(x + 12, y + 29, x + 12 + iconSize, y + 29 + iconSize, 0xFF171B1F);
		context.drawBorder(x + 12, y + 29, iconSize, iconSize, BORDER_OCCUPIED);
		drawScaledItem(context, row.stack(), x + 15, y + 32, iconSize - 6, iconSize - 6);
		int textX = x + 22 + iconSize;
		int textW = w - 34 - iconSize;
		String name = row.stack().getName().getString() + " ×" + row.stack().getCount();
		if (AmmoTier.isTaczAmmoStack(row.stack())) name += " · " + AmmoTier.getFromAmmoStack(row.stack()).getBadge();
		line(context, textRenderer, name, textX, y + 30, textW, TEXT_PRIMARY, 0.92f);
		var lines = row.stack().getTooltip(client.player, TooltipContext.BASIC);
		for (int i = 1; i < lines.size() && i <= 5 && y + 40 + i * 10 < y + h - 65; i++)
			line(context, textRenderer, lines.get(i).getString(), textX, y + 40 + i * 10, textW, TEXT_SECONDARY, 0.8f);

		long balance = handler.status().rubles();
		int footer = y + h - 51;
		String key = row.priceSource().name().toLowerCase(Locale.ROOT);
		line(context, textRenderer, label("trade.price_source." + key, key).getString(),
			x + 12, footer - 14, w - 24, TEXT_SECONDARY, 0.85f);
		line(context, textRenderer, label("trade.total", "Total %s", rubles(row.price())).getString(),
			x + 12, footer, w / 2 - 14, ACCENT_SAND, 0.95f);
		String remaining = balance >= row.price() ? label("trade.after_purchase", "Balance after purchase %s", rubles(balance - row.price())).getString()
			: label("insufficient_rubles", "Insufficient rubles").getString();
		line(context, textRenderer, remaining, x + w / 2, footer, w / 2 - 12,
			balance >= row.price() ? TEXT_PRIMARY : ALERT, 0.85f);
		int buttonW = (w - 30) / 2;
		button(context, Text.translatable("gui.cancel"), x + 12, y + h - 30, buttonW, 18, CANCEL_PREVIEW, true, false);
		button(context, label("trade.confirm", "Confirm purchase"), x + w - 12 - buttonW, y + h - 30,
			buttonW, 18, CONFIRM, balance >= row.price(), true);
	}

	private void drawRow(DrawContext context, Row row, int x, int y, int w, int h, int index, boolean fence) {
		boolean affordable = fence || handler.status().rubles() >= row.price();
		boolean hovered = hit(x, y, w, h);
		context.fill(x, y, x + w, y + h - 1, hovered ? 0xFF292D32 : 0xFF171B1F);
		int icon = Math.min(16, h - 2);
		drawScaledItem(context, row.stack(), x + 2, y + 1, icon, icon);
		String name = row.stack().getName().getString();
		if (AmmoTier.isTaczAmmoStack(row.stack())) name += " " + AmmoTier.getFromAmmoStack(row.stack()).getBadge();
		if (row.stack().getCount() > 1) name += " ×" + row.stack().getCount();
		String price = rubles(row.price());
		int nameX = x + icon + 6;
		int priceWidth = (int) Math.ceil(textRenderer.getWidth(price) * 0.88f);
		int textY = y + (h - 7) / 2;
		line(context, textRenderer, name, nameX, textY, x + w - priceWidth - 10 - nameX, TEXT_PRIMARY, 0.83f);
		drawRightAlignedFlatText(context, textRenderer, price, x + w - 4, textY, affordable ? ACCENT_SAND : ALERT, 0.88f);
		clickArea(x, y, w, h, ROW_FIRST + index);
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
				context.fill(sx, sy, sx + cell, sy + cell, opaque(BG_SLOT));
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
			context.fill(sx + 1, sy + 1, sx + sw - 1, sy + sh - 1, hover ? HOVER_MENU_BG : opaque(BG_PANEL));
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
