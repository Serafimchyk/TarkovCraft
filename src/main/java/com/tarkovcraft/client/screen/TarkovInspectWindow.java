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
	private static final int WIN_W = 256;
	private static final int WIN_H = 224;
	private static final int HEADER_H = 20;

	private final ItemStack stack;
	private int x;
	private int y;
	private int scrollY = 0;
	private int maxScrollY = 0;
	private boolean draggingHeader = false;

	public TarkovInspectWindow(ItemStack stack, int screenW, int screenH) {
		this(stack, screenW, screenH, 0);
	}

	public TarkovInspectWindow(ItemStack stack, int screenW, int screenH, int cascadeIndex) {
		this.stack = stack.copy();
		int offset = cascadeIndex * 16;
		this.x = Math.max(8, (screenW - WIN_W) / 2 + offset);
		this.y = Math.max(8, (screenH - WIN_H) / 2 + offset);
		clampToScreen(screenW, screenH);
	}

	public void render(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY, int screenW, int screenH) {
		clampToScreen(screenW, screenH);
		TarkovUiTheme.clearDepthBuffer(ctx);

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

		// Clear OpenGL depth buffer after 3D item draw so labels/lines/subsequent windows never get clipped
		TarkovUiTheme.clearDepthBuffer(ctx);

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
			int energy = fc.getHunger() * 5;
			int sat = Math.round(fc.getHunger() * fc.getSaturationModifier() * 10.0f);
			TarkovUiTheme.drawFlatText(ctx, tr,
				Text.translatable("ui.tarkovcraft.inspect_food", energy, sat).getString(),
				metaX, metaY, 0xFF77B85A, TarkovUiTheme.TEXT_SCALE_SMALL);
		}

		// Divider & scrollable colored tooltip lines
		int detY = previewY + 76;
		ctx.fill(x + 8, detY, x + WIN_W - 8, detY + 1, TarkovUiTheme.BORDER_PANEL);
		detY += 5;

		int boxBottom = y + WIN_H - 6;
		int viewH = Math.max(12, boxBottom - detY);
		List<Text> details = collectDetailLines();
		int lineH = 10;
		maxScrollY = Math.max(0, details.size() * lineH - viewH);
		scrollY = Math.max(0, Math.min(scrollY, maxScrollY));

		ctx.enableScissor(x + 6, detY, x + WIN_W - 6, boxBottom);
		float s = TarkovUiTheme.TEXT_SCALE_SMALL;
		int maxUnscaledW = Math.round((WIN_W - 22) / s);
		for (int i = 0; i < details.size(); i++) {
			int ly = detY + i * lineH - scrollY;
			if (ly + lineH < detY || ly > boxBottom) {
				continue;
			}
			Text line = details.get(i);
			String plain = line.getString();
			if (tr.getWidth(plain) > maxUnscaledW) {
				String trimmed = tr.trimToWidth(plain, maxUnscaledW - tr.getWidth("...")) + "...";
				line = Text.literal(trimmed).setStyle(line.getStyle());
			}
			ctx.getMatrices().push();
			ctx.getMatrices().scale(s, s, 1.0f);
			ctx.drawText(tr, line, Math.round((x + 8) / s), Math.round(ly / s), TarkovUiTheme.TEXT_SECONDARY, false);
			ctx.getMatrices().pop();
		}
		ctx.disableScissor();

		if (maxScrollY > 0) {
			int sbX = x + WIN_W - 6;
			ctx.fill(sbX, detY, sbX + 2, boxBottom, 0x6620242A);
			int thumbH = Math.max(10, Math.round(viewH * (viewH / (float) (viewH + maxScrollY))));
			int thumbY = detY + Math.round((viewH - thumbH) * (scrollY / (float) maxScrollY));
			ctx.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, TarkovUiTheme.ACCENT_SAND);
		}

		ctx.getMatrices().pop();
	}

	private List<Text> collectDetailLines() {
		List<Text> out = new ArrayList<>();
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean advanced = mc != null && mc.options.advancedItemTooltips;
		TooltipContext ctx = advanced ? TooltipContext.ADVANCED : TooltipContext.BASIC;
		List<Text> tooltip = stack.getTooltip(mc != null ? mc.player : null, ctx);
		for (int i = 1; i < tooltip.size(); i++) {
			Text t = tooltip.get(i);
			String s = t.getString().trim();
			if (s.isEmpty()) {
				continue;
			}
			if (!advanced && isTechnicalDebugLine(s)) {
				continue;
			}
			out.add(t);
		}
		return out;
	}

	private static boolean isTechnicalDebugLine(String s) {
		String lower = s.toLowerCase(Locale.ROOT);
		if (lower.startsWith("nbt:") || lower.startsWith("gunid:") || lower.equals("tacz:modern_kinetic_gun")) {
			return true;
		}
		return lower.matches("^[a-z0-9_]+:[a-z0-9_./-]+$");
	}

	public boolean handleMouseScrolled(double mx, double my, double amount) {
		if (!contains(mx, my) || maxScrollY <= 0) {
			return false;
		}
		scrollY = Math.max(0, Math.min(maxScrollY, scrollY - (int) Math.round(amount * 14)));
		return true;
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
