package com.tarkovcraft.client.screen;

import com.tarkovcraft.health.DamageReport;
import com.tarkovcraft.screen.RaidScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import static com.tarkovcraft.screen.RaidScreenHandler.*;

@Environment(EnvType.CLIENT)
public final class RaidScreen extends HandledScreen<RaidScreenHandler> {
	private static final int ROW_HEIGHT = 24;
	private int left;
	private int top;
	private int panelWidth;
	private int panelHeight;

	public RaidScreen(RaidScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		panelWidth = Math.min(width - 20, 390);
		panelHeight = switch (handler.mode()) {
			case SUMMARY -> 148;
			case TRANSFER -> 184;
		};
		left = (width - panelWidth) / 2;
		top = Math.max(4, (height - panelHeight) / 2);
		switch (handler.mode()) {
			case SUMMARY -> button(label("continue", "Continue"), BACK, left + 10, top + panelHeight - 29, panelWidth - 20);
			case TRANSFER -> initLoot();
		}
	}

	private void initLoot() {
		for (int row = 0; row < handler.items().size(); row++) {
			button(label("store", "Store"), TAKE_FIRST + row,
				left + panelWidth - 70, top + 34 + row * ROW_HEIGHT, 60);
		}
		int navigationY = top + panelHeight - 28;
		button(label("back", "Back"), BACK, left + 10, navigationY, 52);
		if (handler.pageCount() > 1) {
			int center = left + panelWidth / 2;
			button(Text.literal("‹"), PREVIOUS_PAGE, center - 43, navigationY, 20).active = handler.page() > 0;
			button(Text.literal("›"), NEXT_PAGE, center + 23, navigationY, 20).active = handler.page() + 1 < handler.pageCount();
		}
		button(label("store_all", "Store all"), CLAIM_ALL, left + panelWidth - 84, navigationY, 74).active =
			handler.totalItems() > 0;
	}

	private ButtonWidget button(Text label, int id, int x, int y, int width) {
		return addDrawableChild(ButtonWidget.builder(label, button -> send(id)).dimensions(x, y, width, 20).build());
	}

	private void send(int id) {
		if (client != null && client.interactionManager != null) {
			client.interactionManager.clickButton(handler.syncId, id);
		}
	}

	@Override
	public void close() {
		send(BACK);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		TarkovUiTheme.drawLobbyBackground(context, width, height);
		context.fill(left, top, left + panelWidth, top + panelHeight, 0xF013181C);
		context.drawBorder(left, top, panelWidth, panelHeight, TarkovUiTheme.BORDER_PANEL);
		context.drawHorizontalLine(left + 10, left + panelWidth - 11, top + 25, TarkovUiTheme.BORDER_PANEL);
		line(context, title, 10, 10, TarkovUiTheme.ACCENT_SAND, panelWidth - 20);
		switch (handler.mode()) {
			case SUMMARY -> drawSummary(context);
			case TRANSFER -> drawLoot(context, mouseX, mouseY);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		// The shared panel already renders the title and labels in screen coordinates.
	}

	private void drawSummary(DrawContext context) {
		Text result = switch (handler.result()) {
			case "EXTRACTED", "SURVIVED" -> label("extracted", "Extracted");
			case "SCAV_EXTRACTED" -> label("scav_extracted", "Scav extracted");
			case "KIA" -> label("kia", "Killed in action");
			case "KIA_OFFLINE" -> label("kia_offline", "Killed while offline");
			case "MIA" -> label("mia", "Missing in action");
			default -> Text.literal(handler.result());
		};
		int availableWidth = panelWidth - 20;
		line(context, result, 10, 34, TarkovUiTheme.TEXT_PRIMARY, availableWidth);
		line(context, Text.literal(handler.arena()), 10, 49, TarkovUiTheme.TEXT_SECONDARY, availableWidth);
		DamageReport report = handler.report();
		if (report != null) {
			Text details = Text.translatable(report.hitZone().getTranslationKey());
			if (!report.killerName().isBlank()) details = details.copy().append("  /  ").append(report.killerName());
			line(context, details, 10, 71, 0xFFE38479, availableWidth);
			String weapon = report.weaponName();
			if (!report.ammoName().isBlank()) weapon = weapon.isBlank() ? report.ammoName() : weapon + "  /  " + report.ammoName();
			line(context, Text.literal(weapon), 10, 86, TarkovUiTheme.TEXT_SECONDARY, availableWidth);
		}
	}

	private void drawLoot(DrawContext context, int mouseX, int mouseY) {
		if (handler.items().isEmpty()) {
			Text empty = label("empty_transfer", "No Scav loot to store.");
			line(context, empty, 10, 38, TarkovUiTheme.TEXT_SECONDARY, panelWidth - 20);
		}
		for (int row = 0; row < handler.items().size(); row++) {
			ItemStack stack = handler.items().get(row);
			int y = top + 34 + row * ROW_HEIGHT;
			context.fill(left + 10, y, left + panelWidth - 76, y + 20, TarkovUiTheme.BG_SLOT);
			context.drawItem(stack, left + 13, y + 2);
			context.drawItemInSlot(textRenderer, stack, left + 13, y + 2);
			line(context, stack.getName(), 34, y - top + 6, TarkovUiTheme.TEXT_PRIMARY, panelWidth - 113);
		}
		if (handler.pageCount() > 1) {
			context.drawCenteredTextWithShadow(textRenderer, (handler.page() + 1) + "/" + handler.pageCount(),
				left + panelWidth / 2, top + panelHeight - 22, TarkovUiTheme.TEXT_SECONDARY);
		}
	}

	private void line(DrawContext context, Text text, int x, int y, int color, int availableWidth) {
		String label = TarkovUiTheme.trimWithEllipsis(textRenderer, text.getString(), availableWidth, 1.0f);
		context.drawTextWithShadow(textRenderer, label, left + x, top + y, color);
	}
}
