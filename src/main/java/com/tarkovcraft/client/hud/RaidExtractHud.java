package com.tarkovcraft.client.hud;

import com.tarkovcraft.client.ClientRaidExtracts;
import com.tarkovcraft.client.TarkovCraftClient;
import com.tarkovcraft.client.screen.TarkovUiTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Locale;

public final class RaidExtractHud {
	private static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	public static boolean render(DrawContext context, MinecraftClient client, int width, int height, int listTop, float tickDelta) {
		if (client.currentScreen != null || client.player == null || !client.player.isAlive()
			|| !ClientRaidExtracts.inRaidWorld(client.world)) return false;
		Vec3d feet = client.player.getPos();
		var progress = ClientRaidExtracts.progressAt(feet);
		if (progress != null) drawCountdown(context, client, width, progress, tickDelta);
		drawList(context, client, feet, width, height, listTop);
		var exit = ClientRaidExtracts.activationPrompt(feet);
		if (exit == null) return false;
		String prompt = Text.translatable("hud.tarkovcraft.extract.activate",
			TarkovCraftClient.getShortKeyLabel(TarkovCraftClient.interactKey), exit.name()).getString();
		var renderer = client.textRenderer;
		prompt = TarkovUiTheme.trimWithEllipsis(renderer, prompt, width - 34, 1);
		int textWidth = renderer.getWidth(prompt);
		int y = height / 2 + 13;
		rounded(context, (width - textWidth) / 2 - 8, y - 4, textWidth + 16, 17, 0xE32A2015);
		context.drawCenteredTextWithShadow(renderer, prompt, width / 2, y, 0xFFFFCA7B);
		return true;
	}

	private static void drawCountdown(DrawContext context, MinecraftClient client, int width,
		ClientRaidExtracts.Progress progress, float tickDelta) {
		double now = client.world.getTime() + tickDelta;
		float open = MathHelper.clamp((float) (now - progress.startedAt()) / 5f, 0f, 1f);
		double ticks = MathHelper.clamp(progress.remaining() - (now - progress.syncedAt()), 0, progress.duration());
		int w = Math.min(194, width - 16);
		int x = (width - w) / 2;
		int y = 8 - Math.round((1 - open) * 39);
		rounded(context, x, y, w, 42, 0xF03F6B4C);
		rounded(context, x + 1, y + 1, w - 2, 40, 0xF0192C20);
		String label = Text.translatable("hud.tarkovcraft.extract.countdown", progress.name()).getString();
		label = TarkovUiTheme.trimWithEllipsis(client.textRenderer, label, w - 16, 1);
		context.drawCenteredTextWithShadow(client.textRenderer, label, width / 2, y + 6, 0xFFB8EAC6);
		TarkovUiTheme.drawCenteredFlatText(context, client.textRenderer, String.format(Locale.ROOT, "%04.1f", ticks / 20),
			width / 2, y + 19, 0xFFEBFFF0, 1.5f);
		context.fill(x + 8, y + 36, x + w - 8, y + 38, 0xFF2E4935);
		int filled = (int) Math.round((w - 16) * ticks / progress.duration());
		if (filled > 0) context.fill(x + 8, y + 36, x + 8 + filled, y + 38, 0xFF5ADC81);
	}

	private static void drawList(DrawContext context, MinecraftClient client, Vec3d feet, int width, int height, int y) {
		long age = Util.getMeasuringTimeMs() - ClientRaidExtracts.listOpenedAt();
		if (ClientRaidExtracts.listOpenedAt() < 0 || age >= 5_000) return;
		List<ClientRaidExtracts.Zone> exits = List.copyOf(ClientRaidExtracts.zones());
		if (exits.isEmpty()) return;
		int w = Math.min(169, width - 16);
		int rows = Math.max(1, Math.min(7, (height - y - 54) / 25));
		int pages = (exits.size() + rows - 1) / rows;
		int page = Math.floorMod(ClientRaidExtracts.listPage(), pages);
		int start = page * rows;
		int end = Math.min(start + rows, exits.size());
		float show = Math.min(1f, Math.min(age, 5_000 - age) / 180f);
		int x = width - w - 8 + Math.round((w + 8) * (1 - show) * (1 - show));
		rounded(context, x, y, w, 22 + (end - start) * 25, 0xEE141C18);
		String title = Text.translatable("hud.tarkovcraft.extract.list").getString();
		context.drawText(client.textRenderer, title, x + 8, y + 7, 0xFFE4F1E8, false);
		if (pages > 1) {
			TarkovUiTheme.drawRightAlignedFlatText(context, client.textRenderer, (page + 1) + "/" + pages,
				x + w - 8, y + 8, 0xFF9DC1A8, 0.8f);
		}
		for (int i = start; i < end; i++) {
			var zone = exits.get(i);
			var exit = zone.exit();
			int rowY = y + 20 + (i - start) * 25;
			int color = zone.signalOn() ? 0xFF81DF9F : 0xFFF2B363;
			context.fill(x + 7, rowY, x + w - 7, rowY + 1, 0xFF344239);
			context.fill(x + 8, rowY + 5, x + 10, rowY + 19, color);
			String details = ClientRaidExtracts.showDirections() ? direction(feet, Vec3d.ofBottomCenter(exit.pos())) : "";
			int reserved = Math.round(client.textRenderer.getWidth(details) * 0.77f);
			String name = TarkovUiTheme.trimWithEllipsis(client.textRenderer, exit.name(), w - 23 - reserved, 0.93f);
			TarkovUiTheme.drawFlatText(context, client.textRenderer, name, x + 14, rowY + 4, 0xFFE8EFE9, 0.93f);
			TarkovUiTheme.drawRightAlignedFlatText(context, client.textRenderer, details, x + w - 8, rowY + 5, 0xFFADBEB1, 0.77f);
			String type = Text.translatable(exit.loud() ? "hud.tarkovcraft.extract.loud" : "hud.tarkovcraft.extract.quiet").getString();
			String status = Text.translatable(zone.activated() ? "hud.tarkovcraft.extract.active" : "hud.tarkovcraft.extract.available").getString();
			TarkovUiTheme.drawFlatText(context, client.textRenderer, type + "  ·  " + status, x + 14, rowY + 15, color, 0.77f);
		}
	}

	private static String direction(Vec3d from, Vec3d to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		long distance = Math.round(Math.hypot(dx, dz));
		if (distance == 0) return Text.translatable("hud.tarkovcraft.extract.here").getString();
		int index = Math.floorMod((int) Math.round(Math.atan2(dx, -dz) / (Math.PI / 4)), 8);
		String compass = Text.translatable("hud.tarkovcraft.extract.compass." + DIRECTIONS[index]).getString();
		return Text.translatable("hud.tarkovcraft.extract.distance", compass, distance).getString();
	}

	private static void rounded(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x, y + 3, x + w, y + h - 3, color);
		for (int row = 0; row < 3; row++) {
			int inset = 4 - row;
			context.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
			context.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
		}
	}

	private RaidExtractHud() { }
}
