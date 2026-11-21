package com.tarkovcraft.client.screen;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovGrenadeItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovStimulatorItem;
import com.tarkovcraft.screen.TarkovScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

/**
 * Shared EFT-inspired flat translucent visual theme, typography, Z-layers, and render helpers.
 */
@Environment(EnvType.CLIENT)
public final class TarkovUiTheme {
	// Backgrounds & Translucency
	public static final int BG_WORLD_OVERLAY = 0x85000000;
	public static final int BG_TOP_BAR = 0xE00C0D0F;
	public static final int BG_PANEL = 0xC80D0E10;
	public static final int BG_QUICK_BAR = 0xCC0D0E10;
	public static final int BG_SLOT = 0x99121417;
	public static final int BG_CELL_EMPTY = 0x99101215;
	public static final int BG_CELL_OCCUPIED_DEFAULT = 0xCC1C2026;
	public static final int BG_CELL_SILHOUETTE = 0xDD090B0D;
	public static final int BG_DIM_DRAG_SOURCE = 0x9907080A;
	public static final int BG_TOOLTIP = 0xE60B0C0E;
	public static final int BG_CONTEXT_MENU = 0xF0111316;
	public static final int BG_MODAL = 0xEE0E1013;
	public static final int BG_MODAL_HEADER = 0xF515181C;

	// Borders & Accents
	public static final int BORDER_PANEL = 0xFF2D3035;
	public static final int BORDER_SLOT = 0xFF32363C;
	public static final int BORDER_CELL = 0xFF272B30;
	public static final int BORDER_OCCUPIED = 0xFF3E454E;
	public static final int BORDER_HOVER = 0xFF8C877B;
	public static final int BORDER_TOOLTIP = 0xFF3E4249;
	public static final int BORDER_POPUP = 0xFF42464D;
	public static final int BORDER_BROKEN = 0xFFC93838;
	public static final int ACCENT_SAND = 0xFFD4B46A;
	public static final int HOVER_MENU_BG = 0xFFDCD6C8;
	public static final int HOVER_MENU_TEXT = 0xFF111214;
	public static final int DROP_VALID_FILL = 0x553B7A3B;
	public static final int DROP_VALID_BORDER = 0xFF4CAF50;
	public static final int DROP_INVALID_FILL = 0x558F2B2B;
	public static final int DROP_INVALID_BORDER = 0xFFE53935;

	// Text & Typography
	public static final int TEXT_PRIMARY = 0xFFE6E1D6;
	public static final int TEXT_SECONDARY = 0xB8CFCAC0;
	public static final int TEXT_MUTED = 0xFF848B94;
	public static final float TEXT_SCALE_SMALL = 0.72f;
	public static final float TEXT_SCALE_HEADER = 0.85f;

	// Layout Proportions & Drag
	public static final int TOP_BAR_H = 26;
	public static final int QUICK_BAR_H = 30;
	public static final int STATS_BAR_H = 18;
	public static final int OUTER_PAD = 6;
	public static final int COL_GAP = 6;
	public static final double DRAG_THRESHOLD_PX = 4.0;

	// Z Layers
	public static final int Z_PLAYER = 0;
	public static final int Z_SLOTS = 130;
	public static final int Z_DRAG_HIGHLIGHT = 260;
	public static final int Z_DRAG_GHOST = 320;
	public static final int Z_MODAL = 420;
	public static final int Z_POPUP = 500;

	private TarkovUiTheme() {
	}

	public static void drawFlatText(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color, float scale) {
		ctx.getMatrices().push();
		ctx.getMatrices().scale(scale, scale, 1.0f);
		ctx.drawText(tr, text, Math.round(x / scale), Math.round(y / scale), color, false);
		ctx.getMatrices().pop();
	}

	public static void drawCenteredFlatText(DrawContext ctx, TextRenderer tr, String text, int cx, int y, int color, float scale) {
		int w = Math.round(tr.getWidth(text) * scale);
		drawFlatText(ctx, tr, text, cx - w / 2, y, color, scale);
	}

	public static void drawRightAlignedFlatText(DrawContext ctx, TextRenderer tr, String text, int rightX, int y, int color, float scale) {
		int w = Math.round(tr.getWidth(text) * scale);
		drawFlatText(ctx, tr, text, rightX - w, y, color, scale);
	}

	public static void drawCellCornerBadge(
		DrawContext ctx, TextRenderer tr, String text,
		int cellX, int cellY, int cellW, int cellH, int color
	) {
		if (text == null || text.isEmpty()) {
			return;
		}
		int rawW = Math.max(1, tr.getWidth(text));
		int maxW = Math.max(8, cellW - 4);
		float scale = Math.min(0.62f, maxW / (float) rawW);
		int drawnW = Math.round(rawW * scale);
		int drawnH = Math.max(4, Math.round(7.0f * scale));

		int tx = cellX + cellW - 2 - drawnW;
		int ty = cellY + cellH - 2 - drawnH;

		ctx.getMatrices().push();
		ctx.getMatrices().translate(0.0f, 0.0f, 165.0f);
		ctx.fill(tx - 1, ty - 1, tx + drawnW + 1, ty + drawnH + 1, 0xA608090C);
		ctx.getMatrices().translate(tx, ty, 1.0f);
		ctx.getMatrices().scale(scale, scale, 1.0f);
		ctx.drawText(tr, text, 0, 0, color, false);
		ctx.getMatrices().pop();
	}

	public static String trimWithEllipsis(TextRenderer tr, String text, int maxPx, float scale) {
		int maxRaw = Math.max(6, Math.round(maxPx / scale));
		if (tr.getWidth(text) <= maxRaw) {
			return text;
		}
		int dotsW = tr.getWidth("...");
		if (maxRaw <= dotsW) {
			return tr.trimToWidth(text, maxRaw);
		}
		return tr.trimToWidth(text, maxRaw - dotsW) + "...";
	}

	public static void drawScaledItem(DrawContext ctx, ItemStack stack, int x, int y, int w, int h) {
		drawScaledItem(ctx, stack, x, y, w, h, false);
	}

	public static void drawScaledItem(DrawContext ctx, ItemStack stack, int x, int y, int w, int h, boolean rotated) {
		if (stack == null || stack.isEmpty() || w <= 0 || h <= 0) {
			return;
		}
		Identifier id = Registries.ITEM.getId(stack.getItem());
		int minDim = Math.min(w, h);
		int maxDim = Math.max(w, h);
		float targetPx;
		if ("tarkovcraft".equals(id.getNamespace())) {
			ItemSize itemSize = ItemSizeRegistry.getSize(stack);
			boolean rectangularItem = itemSize.width() != itemSize.height();
			if (rectangularItem && maxDim > minDim * 1.25f) {
				targetPx = Math.min(maxDim * 0.86f, minDim * 2.0f);
			} else {
				targetPx = Math.min(38.0f, minDim * 0.80f);
			}
		} else {
			targetPx = Math.min(38.0f, minDim * 0.82f);
		}
		float scale = targetPx / 16.0f;
		ctx.enableScissor(x, y, x + w, y + h);
		try {
			ctx.getMatrices().push();
			ctx.getMatrices().translate(x + w * 0.5f, y + h * 0.5f, 0.0f);
			if (rotated) {
				ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0f));
			}
			ctx.getMatrices().scale(scale, scale, 1.0f);
			ctx.drawItem(stack, -8, -8);
			ctx.getMatrices().pop();
		} finally {
			ctx.disableScissor();
		}
	}

	public static int itemCategoryTint(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return BG_CELL_OCCUPIED_DEFAULT;
		}
		Item item = stack.getItem();
		if (item instanceof TarkovArmorItem) {
			return 0xCC18242C;
		}
		if (item instanceof TarkovMedicalItem || item instanceof TarkovStimulatorItem) {
			return 0xCC182824;
		}
		if (item instanceof BackpackItem || item instanceof TacticalRigItem || item instanceof TarkovCaseItem) {
			return 0xCC1E241C;
		}
		Identifier id = Registries.ITEM.getId(item);
		boolean isWeapon = ("tacz".equals(id.getNamespace()) && id.getPath().contains("gun"))
			|| item instanceof SwordItem
			|| item instanceof AxeItem
			|| item instanceof TarkovGrenadeItem;
		if (isWeapon || AmmoTier.isTaczAmmoStack(stack)) {
			return 0xCC26221A;
		}
		if (TarkovScreenHandler.isConsumableItem(stack)) {
			return 0xCC182620;
		}
		return BG_CELL_OCCUPIED_DEFAULT;
	}

	public static void drawFlatTooltip(
		DrawContext ctx,
		TextRenderer tr,
		List<Text> lines,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		int pad = 5;
		int lineH = 10;
		int maxW = 0;
		for (Text line : lines) {
			maxW = Math.max(maxW, tr.getWidth(line));
		}
		int boxW = maxW + pad * 2;
		int boxH = lines.size() * lineH + pad * 2 - 2;

		int bx = mouseX + 10;
		int by = mouseY - 6;
		if (bx + boxW > screenW - 4) {
			bx = Math.max(4, mouseX - boxW - 8);
		}
		if (by + boxH > screenH - 4) {
			by = Math.max(4, screenH - boxH - 4);
		}
		if (by < 4) {
			by = 4;
		}

		ctx.getMatrices().push();
		ctx.getMatrices().translate(0.0f, 0.0f, Z_POPUP);
		ctx.fill(bx, by, bx + boxW, by + boxH, BG_TOOLTIP);
		ctx.drawBorder(bx, by, boxW, boxH, BORDER_TOOLTIP);

		int ty = by + pad;
		for (int i = 0; i < lines.size(); i++) {
			Text line = lines.get(i);
			int col = i == 0 ? TEXT_PRIMARY : TEXT_SECONDARY;
			ctx.drawText(tr, line, bx + pad, ty, col, false);
			ty += lineH;
		}
		ctx.getMatrices().pop();
	}
}
