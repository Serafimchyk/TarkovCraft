package com.tarkovcraft.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

final class AdminUi {
	static final int BACKGROUND = 0xD6090C10;
	static final int PANEL = 0xEE12161F;
	static final int HOVER = 0xF5181E2A;
	static final int CONTROL = 0xFF0C0F16;
	static final int BORDER = 0xFF222A38;
	static final int ACTIVE = 0xFF1E2636;
	static final int INK = 0xFFF1F5F9;
	static final int MUTED = 0xFF8892A4;
	static final int AMBER = 0xFFD4B46A;
	static final int TEAL = 0xFF2DD4BF;
	static final int DARK = 0xFF0E1117;
	private static final int ARC_STEPS = 8;

	private AdminUi() { }

	record Rect(int x, int y, int w, int h) {
		int right() { return x + w; }
		int bottom() { return y + h; }
		boolean contains(double px, double py) { return px >= x && py >= y && px < right() && py < bottom(); }
	}

	static void fillRoundedRect(DrawContext ctx, Rect rect, float radius, int color) {
		drawRounded(ctx, rect, radius, color, false);
	}

	static void drawRoundedBorder(DrawContext ctx, Rect rect, float radius, int color) {
		drawRounded(ctx, rect, radius, color, true);
	}

	private static void drawRounded(DrawContext ctx, Rect rect, float radius, int color, boolean border) {
		if (rect.w() < 2 || rect.h() < 2) return;
		ctx.draw();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder buffer = Tessellator.getInstance().getBuffer();
		buffer.begin(border ? VertexFormat.DrawMode.TRIANGLE_STRIP : VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
		Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
		radius = Math.min(radius, Math.min(rect.w(), rect.h()) / 2f);
		if (!border) vertex(buffer, matrix, rect.x() + rect.w() / 2f, rect.y() + rect.h() / 2f, color);
		for (int corner = 0; corner <= 4; corner++) {
			float cx = corner % 4 < 2 ? rect.right() - radius : rect.x() + radius;
			float cy = corner % 4 == 1 || corner % 4 == 2 ? rect.bottom() - radius : rect.y() + radius;
			int lastStep = corner == 4 ? 0 : ARC_STEPS;
			for (int step = 0; step <= lastStep; step++) {
				double angle = Math.PI / 2 * (corner - 1 + (double) step / ARC_STEPS);
				float dx = (float) Math.cos(angle);
				float dy = (float) Math.sin(angle);
				vertex(buffer, matrix, cx + dx * radius, cy + dy * radius, color);
				if (border) vertex(buffer, matrix, cx + dx * (radius - 1), cy + dy * (radius - 1), color);
			}
		}
		BufferRenderer.drawWithGlobalProgram(buffer.end());
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
	}

	private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, int color) {
		buffer.vertex(matrix, x, y, 0).color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24).next();
	}

	static void center(DrawContext ctx, TextRenderer font, String label, Rect rect, int color) {
		label = trim(font, label, rect.w() - 4);
		ctx.drawText(font, label, rect.x() + (rect.w() - font.getWidth(label)) / 2,
			rect.y() + (rect.h() - font.fontHeight) / 2 + 1, color, false);
	}

	static String trim(TextRenderer font, String text, int width) {
		if (width <= 0) return "";
		if (font.getWidth(text) <= width) return text;
		return font.trimToWidth(text, Math.max(0, width - font.getWidth("…"))) + "…";
	}
}
