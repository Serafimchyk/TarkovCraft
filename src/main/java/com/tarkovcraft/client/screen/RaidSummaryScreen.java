package com.tarkovcraft.client.screen;

import com.tarkovcraft.health.DamageReport;
import com.tarkovcraft.screen.RaidScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import static com.tarkovcraft.client.screen.LobbyTopNavBar.*;
import static com.tarkovcraft.client.screen.TarkovUiTheme.*;
import static com.tarkovcraft.screen.RaidScreenHandler.*;

final class RaidSummaryScreen extends RaidScreen {
	private static final int PANEL_HEIGHT = 192;
	private int left;
	private int top;
	private int panelWidth;

	RaidSummaryScreen(RaidScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		panelWidth = Math.min(width - 20, 390);
		left = (width - panelWidth) / 2;
		top = Math.max(4, (height - PANEL_HEIGHT) / 2);
		button(RaidScreenHandler.label("continue", "Continue"), BACK, left + 10, top + PANEL_HEIGHT - 30, panelWidth - 20);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		drawLobbyBackground(context, width, height);
		panel(context, left, top, panelWidth, PANEL_HEIGHT);
		text(context, title, left + 10, top + 10, panelWidth - 20, ACCENT_SAND);
		boolean survived = switch (handler.result()) {
			case "EXTRACTED", "SURVIVED", "SCAV_EXTRACTED" -> true;
			default -> false;
		};
		Text outcome = switch (handler.result()) {
			case "EXTRACTED", "SURVIVED", "SCAV_EXTRACTED" -> RaidScreenHandler.label("survived", "Survived");
			case "KIA" -> RaidScreenHandler.label("kia", "Killed in action");
			case "KIA_OFFLINE" -> RaidScreenHandler.label("kia_offline", "Killed while offline");
			case "MIA" -> RaidScreenHandler.label("mia", "Missing in action");
			default -> Text.literal(handler.result());
		};
		text(context, outcome, left + 10, top + 28, panelWidth - 20, survived ? GREEN : ALERT);
		text(context, Text.literal(handler.arena()), left + 10, top + 43, panelWidth - 20, TEXT_SECONDARY);
		drawStats(context);
		DamageReport report = handler.report();
		if (report != null) drawFatalHit(context, report);
	}

	private void drawStats(DrawContext context) {
		var stats = handler.stats();
		int w = (panelWidth - 20) / 4;
		stat(context, 0, w, RaidScreenHandler.label("duration", "Duration"), time(stats.seconds()));
		stat(context, 1, w, RaidScreenHandler.label("kills", "Kills"), Integer.toString(stats.kills()));
		stat(context, 2, w, RaidScreenHandler.label("damage", "Damage dealt"), Integer.toString(stats.damage()));
		stat(context, 3, w, RaidScreenHandler.label("loot_value", "Loot value"), rubles(stats.lootValue()));
	}

	private void stat(DrawContext context, int index, int w, Text name, String value) {
		int x = left + 10 + index * w;
		context.fill(x, top + 60, x + w - 3, top + 103, BG_SLOT);
		text(context, name, x + 5, top + 68, w - 11, TEXT_SECONDARY);
		text(context, Text.literal(value), x + 5, top + 84, w - 11, TEXT_PRIMARY);
	}

	private void drawFatalHit(DrawContext context, DamageReport report) {
		Text zone = Text.translatable(report.hitZone().getTranslationKey());
		if (!report.killerName().isBlank()) zone = zone.copy().append("  /  ").append(report.killerName());
		text(context, zone, left + 10, top + 114, panelWidth - 20, ALERT);
		String weapon = report.weaponName();
		if (!report.ammoName().isBlank()) weapon = weapon.isBlank() ? report.ammoName() : weapon + "  /  " + report.ammoName();
		text(context, Text.literal(weapon), left + 10, top + 127, panelWidth - 20, TEXT_SECONDARY);
		if (report.distanceMeters() > 0)
			text(context, RaidScreenHandler.label("distance", "%s m", Math.round(report.distanceMeters())),
				left + 10, top + 140, panelWidth - 20, TEXT_SECONDARY);
	}
}
