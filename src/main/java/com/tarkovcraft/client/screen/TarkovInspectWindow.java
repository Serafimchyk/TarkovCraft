package com.tarkovcraft.client.screen;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.search.ItemRarityRegistry;
import com.tarkovcraft.search.ItemRarityTier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draggable EFT-style item inspection window rendered above the inventory on z = 420.
 */
@Environment(EnvType.CLIENT)
public final class TarkovInspectWindow {
	private static final int WIN_W = 232;
	private static final int WIN_H = 192;
	private static final int HEADER_H = 20;

	private final ItemStack stack;
	private int x;
	private int y;
	private boolean draggingHeader = false;

	public TarkovInspectWindow(ItemStack stack, int screenW, int screenH) {
		this.stack = stack.copy();
		this.x = Math.max(8, (screenW - WIN_W) / 2);
		this.y = Math.max(8, (screenH - WIN_H) / 2);
	}

	public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, int screenW, int screenH) {
		clampToScreen(screenW, screenH);

		ctx.getMatrices().push();
		ctx.getMatrices().translate(0.0f, 0.0f, TarkovUiTheme.Z_MODAL);

		ctx.fill(x, y, x + WIN_W, y + WIN_H, TarkovUiTheme.BG_MODAL);
		ctx.drawBorder(x, y, WIN_W, WIN_H, TarkovUiTheme.BORDER_POPUP);

		// Header
		ctx.fill(x + 1, y + 1, x + WIN_W - 1, y + HEADER_H, TarkovUiTheme.BG_MODAL_HEADER);
		ctx.fill(x + 1, y + HEADER_H, x + WIN_W - 1, y + HEADER_H + 1, TarkovUiTheme.BORDER_PANEL);
		String title = tr.trimToWidth(stack.getName().getString(), WIN_W - 32);
		ctx.drawText(tr, title, x + 6, y + 6, TarkovUiTheme.TEXT_PRIMARY, false);

		// Close [X] button
		int closeX = x + WIN_W - 18;
		int closeY = y + 3;
		boolean closeHov = mouseX >= closeX && mouseX < closeX + 14 && mouseY >= closeY && mouseY < closeY + 14;
		ctx.fill(closeX, closeY, closeX + 14, closeY + 14, closeHov ? TarkovUiTheme.HOVER_MENU_BG : 0xFF1D2025);
		TarkovUiTheme.drawCenteredFlatText(ctx, tr, "X", closeX + 7, closeY + 4,
			closeHov ? TarkovUiTheme.HOVER_MENU_TEXT : TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);

		// Left 70x70 large item preview
		int previewX = x + 8;
		int previewY = y + HEADER_H + 8;
		ctx.fill(previewX, previewY, previewX + 70, previewY + 70, TarkovUiTheme.itemCategoryTint(stack));
		ctx.drawBorder(previewX, previewY, 70, 70, TarkovUiTheme.BORDER_OCCUPIED);

		ctx.getMatrices().push();
		ctx.getMatrices().translate(previewX + 35.0f, previewY + 35.0f, 10.0f);
		ctx.getMatrices().scale(3.0f, 3.0f, 1.0f);
		ctx.drawItem(stack, -8, -8);
		ctx.getMatrices().pop();

		ItemSize size = ItemSizeRegistry.getSize(stack);
		TarkovUiTheme.drawRightAlignedFlatText(ctx, tr, size.width() + "x" + size.height(),
			previewX + 67, previewY + 60, TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);

		// Right metadata column
		int metaX = previewX + 78;
		int metaY = previewY + 2;
		float weight = (stack.getItem() instanceof TarkovArmorItem armor)
			? armor.getWeightKg()
			: PlayerLoadout.estimateItemWeight(stack);
		ItemRarityTier rarity = ItemRarityRegistry.getTier(stack);

		TarkovUiTheme.drawFlatText(ctx, tr,
			Text.translatable("ui.tarkovcraft.inspect_weight", String.format(Locale.ROOT, "%.2f", weight)).getString(),
			metaX, metaY, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		metaY += 12;
		TarkovUiTheme.drawFlatText(ctx, tr,
			Text.translatable("ui.tarkovcraft.inspect_count", stack.getCount() + "/" + stack.getMaxCount()).getString(),
			metaX, metaY, TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		metaY += 12;
		TarkovUiTheme.drawFlatText(ctx, tr,
			Text.translatable("ui.tarkovcraft.inspect_size", size.width(), size.height()).getString(),
			metaX, metaY, TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		metaY += 12;
		TarkovUiTheme.drawFlatText(ctx, tr,
			Text.translatable("ui.tarkovcraft.inspect_rarity", rarity.name()).getString(),
			metaX, metaY, TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
		metaY += 12;

		FoodComponent fc = stack.getItem().getFoodComponent();
		if (fc != null) {
			int sat = Math.round(fc.getHunger() * fc.getSaturationModifier() * 2.0f);
			TarkovUiTheme.drawFlatText(ctx, tr,
				Text.translatable("ui.tarkovcraft.inspect_food", fc.getHunger(), sat).getString(),
				metaX, metaY, 0xFF77B85A, TarkovUiTheme.TEXT_SCALE_SMALL);
		}

		// Divider & detailed tooltip lines
		int detY = previewY + 76;
		ctx.fill(x + 8, detY, x + WIN_W - 8, detY + 1, TarkovUiTheme.BORDER_PANEL);
		detY += 5;

		List<String> details = collectDetailLines();
		int maxLines = Math.max(1, (y + WIN_H - 6 - detY) / 9);
		for (int i = 0; i < Math.min(details.size(), maxLines); i++) {
			String line = tr.trimToWidth(details.get(i), Math.round((WIN_W - 18) / TarkovUiTheme.TEXT_SCALE_SMALL));
			TarkovUiTheme.drawFlatText(ctx, tr, line, x + 8, detY + i * 9, TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		}

		ctx.getMatrices().pop();
	}

	private List<String> collectDetailLines() {
		List<String> out = new ArrayList<>();
		MinecraftClient mc = MinecraftClient.getInstance();
		TooltipContext ctx = (mc != null && mc.options.advancedItemTooltips)
			? TooltipContext.ADVANCED
			: TooltipContext.BASIC;
		List<Text> tooltip = stack.getTooltip(mc != null ? mc.player : null, ctx);
		for (int i = 1; i < tooltip.size(); i++) {
			String s = tooltip.get(i).getString().trim();
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}

	public boolean contains(double mx, double my) {
		return mx >= x && mx < x + WIN_W && my >= y && my < y + WIN_H;
	}

	/**
	 * Returns true if the window was closed by clicking [X].
	 */
	public boolean handleMouseClicked(double mx, double my, int button) {
		if (button != 0 || !contains(mx, my)) {
			return false;
		}
		int closeX = x + WIN_W - 18;
		int closeY = y + 3;
		if (mx >= closeX && mx < closeX + 14 && my >= closeY && my < closeY + 14) {
			return true;
		}
		if (my >= y && my < y + HEADER_H) {
			draggingHeader = true;
		}
		return false;
	}

	public boolean handleMouseDragged(double dx, double dy, int screenW, int screenH) {
		if (!draggingHeader) {
			return false;
		}
		x += Math.round(dx);
		y += Math.round(dy);
		clampToScreen(screenW, screenH);
		return true;
	}

	public void handleMouseReleased() {
		draggingHeader = false;
	}

	private void clampToScreen(int screenW, int screenH) {
		x = Math.max(4, Math.min(screenW - WIN_W - 4, x));
		y = Math.max(4, Math.min(screenH - WIN_H - 4, y));
	}
}
