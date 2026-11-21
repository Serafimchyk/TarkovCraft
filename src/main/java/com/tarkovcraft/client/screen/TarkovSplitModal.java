package com.tarkovcraft.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

/**
 * Compact modal for choosing an exact split count (1 .. count - 1) before sending C2S_SPLIT_STACK.
 */
@Environment(EnvType.CLIENT)
public final class TarkovSplitModal {
	private static final int WIN_W = 164;
	private static final int WIN_H = 84;

	private final int maxSplit;
	private final String itemName;
	private final IntConsumer onConfirm;

	private int x;
	private int y;
	private int splitAmount;
	private boolean draggingSlider = false;

	public TarkovSplitModal(ItemStack stack, int anchorX, int anchorY, int screenW, int screenH, IntConsumer onConfirm) {
		this.maxSplit = Math.max(1, stack.getCount() - 1);
		this.itemName = stack.getName().getString();
		this.splitAmount = Math.max(1, stack.getCount() / 2);
		this.onConfirm = onConfirm;
		this.x = Math.max(6, Math.min(screenW - WIN_W - 6, anchorX - WIN_W / 2));
		this.y = Math.max(6, Math.min(screenH - WIN_H - 6, anchorY - WIN_H / 2));
	}

	public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0.0f, 0.0f, TarkovUiTheme.Z_MODAL);

		ctx.fill(x, y, x + WIN_W, y + WIN_H, TarkovUiTheme.BG_MODAL);
		ctx.drawBorder(x, y, WIN_W, WIN_H, TarkovUiTheme.BORDER_POPUP);

		ctx.fill(x + 1, y + 1, x + WIN_W - 1, y + 16, TarkovUiTheme.BG_MODAL_HEADER);
		String title = Text.translatable("ui.tarkovcraft.split_title").getString() + " // " + itemName;
		TarkovUiTheme.drawFlatText(ctx, tr, tr.trimToWidth(title, Math.round((WIN_W - 10) / TarkovUiTheme.TEXT_SCALE_SMALL)),
			x + 5, y + 5, TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);

		int total = maxSplit + 1;
		int remain = total - splitAmount;
		TarkovUiTheme.drawCenteredFlatText(ctx, tr, splitAmount + " / " + remain,
			x + WIN_W / 2, y + 22, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);

		// Slider track
		int trackX = x + 10;
		int trackY = y + 36;
		int trackW = WIN_W - 20;
		ctx.fill(trackX, trackY, trackX + trackW, trackY + 6, 0xFF1A1E23);
		ctx.drawBorder(trackX, trackY, trackW, 6, TarkovUiTheme.BORDER_SLOT);

		float ratio = maxSplit <= 1 ? 1.0f : (splitAmount - 1) / (float) (maxSplit - 1);
		int fillW = Math.max(4, Math.round(ratio * (trackW - 2)));
		ctx.fill(trackX + 1, trackY + 1, trackX + 1 + fillW, trackY + 5, TarkovUiTheme.ACCENT_SAND);

		int knobX = trackX + Math.round(ratio * (trackW - 6));
		ctx.fill(knobX, trackY - 2, knobX + 6, trackY + 8, TarkovUiTheme.TEXT_PRIMARY);

		// Buttons: 50% | OK | Cancel
		drawButton(ctx, tr, x + 8, y + 56, 36, 18, "50%", mouseX, mouseY, false);
		drawButton(ctx, tr, x + 50, y + 56, 52, 18, Text.translatable("ui.tarkovcraft.confirm").getString(), mouseX, mouseY, true);
		drawButton(ctx, tr, x + 108, y + 56, 48, 18, Text.translatable("ui.tarkovcraft.cancel").getString(), mouseX, mouseY, false);

		ctx.getMatrices().pop();
	}

	private void drawButton(DrawContext ctx, TextRenderer tr, int bx, int by, int bw, int bh, String label, int mx, int my, boolean accent) {
		boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
		int bg = hov ? TarkovUiTheme.HOVER_MENU_BG : (accent ? 0xFF283226 : 0xFF1A1E24);
		int textCol = hov ? TarkovUiTheme.HOVER_MENU_TEXT : (accent ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.TEXT_PRIMARY);
		ctx.fill(bx, by, bx + bw, by + bh, bg);
		ctx.drawBorder(bx, by, bw, bh, hov ? TarkovUiTheme.BORDER_HOVER : TarkovUiTheme.BORDER_SLOT);
		TarkovUiTheme.drawCenteredFlatText(ctx, tr, label, bx + bw / 2, by + 6, textCol, TarkovUiTheme.TEXT_SCALE_SMALL);
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx < x + WIN_W && my >= y && my < y + WIN_H;
	}

	/**
	 * Returns true if the modal should close after handling the click.
	 */
	public boolean handleMouseClicked(double mx, double my, int button) {
		if (button != 0) {
			return false;
		}
		if (!contains(mx, my)) {
			return true;
		}

		// Slider hit
		int trackX = x + 10;
		int trackY = y + 32;
		int trackW = WIN_W - 20;
		if (mx >= trackX && mx <= trackX + trackW && my >= trackY && my <= trackY + 14) {
			draggingSlider = true;
			updateSliderFromMouse(mx);
			return false;
		}

		// 50% button
		if (mx >= x + 8 && mx < x + 44 && my >= y + 56 && my < y + 74) {
			splitAmount = Math.max(1, Math.min(maxSplit, (maxSplit + 1) / 2));
			return false;
		}

		// OK button
		if (mx >= x + 50 && mx < x + 102 && my >= y + 56 && my < y + 74) {
			confirm();
			return true;
		}

		// Cancel button
		if (mx >= x + 108 && mx < x + 156 && my >= y + 56 && my < y + 74) {
			return true;
		}

		return false;
	}

	public boolean handleMouseDragged(double mx) {
		if (!draggingSlider) {
			return false;
		}
		updateSliderFromMouse(mx);
		return true;
	}

	public void handleMouseReleased() {
		draggingSlider = false;
	}

	public void confirm() {
		onConfirm.accept(Math.max(1, Math.min(maxSplit, splitAmount)));
	}

	private void updateSliderFromMouse(double mx) {
		int trackX = x + 10;
		int trackW = WIN_W - 20;
		float ratio = (float) Math.max(0.0, Math.min(1.0, (mx - trackX) / trackW));
		splitAmount = 1 + Math.round(ratio * Math.max(0, maxSplit - 1));
		splitAmount = Math.max(1, Math.min(maxSplit, splitAmount));
	}
}
