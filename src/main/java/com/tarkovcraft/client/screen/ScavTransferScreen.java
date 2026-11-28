package com.tarkovcraft.client.screen;

import com.tarkovcraft.arena.PlayerProfileState;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.screen.RaidScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import static com.tarkovcraft.client.screen.TarkovUiTheme.*;
import static com.tarkovcraft.screen.RaidScreenHandler.*;

final class ScavTransferScreen extends RaidScreen {
	private static final int COLUMNS = PlayerProfileState.STASH_WIDTH;
	private static final int ROWS = PlayerProfileState.STASH_HEIGHT;
	private static final int LOOT_ROW_HEIGHT = 28;

	private int left, top, panelWidth, panelHeight, listWidth, cell, gridX, gridY;
	private int selection = -1;
	private boolean rotated;

	ScavTransferScreen(RaidScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		panelWidth = Math.min(width - 16, 570);
		cell = Math.max(5, Math.min(17, (height - 65) / ROWS));
		panelHeight = Math.max(194, cell * ROWS + 56);
		top = Math.max(3, (height - panelHeight) / 2);
		left = (width - panelWidth) / 2;
		gridX = left + panelWidth - COLUMNS * cell - 10;
		gridY = top + 25;
		listWidth = gridX - left - 21;
		for (int row = 0; row < handler.items().size(); row++) {
			button(label("store", "Store"), TAKE_FIRST + row, left + listWidth - 56, lootY(row) + 3, 64);
		}
		int footer = top + panelHeight - 25;
		button(label("store_all", "Store all"), CLAIM_ALL, left + 10, footer, 90).active = handler.totalItems() > 0;
		button(label("finish", "Finish"), BACK, left + panelWidth - 100, footer, 90);
		if (handler.pageCount() > 1) {
			int right = left + 10 + listWidth;
			button(Text.literal("‹"), PREVIOUS_PAGE, right - 81, footer, 20).active = handler.page() > 0;
			button(Text.literal("›"), NEXT_PAGE, right - 20, footer, 20).active = handler.page() + 1 < handler.pageCount();
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		drawLobbyBackground(context, width, height);
		panel(context, left, top, panelWidth, panelHeight);
		text(context, title, left + 10, top + 9, listWidth, ACCENT_SAND);
		text(context, label("stash", "Stash · 10×24"), gridX, top + 9, COLUMNS * cell, ACCENT_SAND);
		drawLoot(context);
		drawStash(context, mouseX, mouseY);
		if (handler.pageCount() > 1) {
			context.drawCenteredTextWithShadow(textRenderer, (handler.page() + 1) + "/" + handler.pageCount(),
				left + 10 + listWidth - 41, top + panelHeight - 19, TEXT_SECONDARY);
		}
	}

	private void drawLoot(DrawContext context) {
		if (handler.items().isEmpty()) {
			text(context, label("empty_transfer", "No Scav loot to store."), left + 10, top + 29, listWidth, TEXT_SECONDARY);
			return;
		}
		for (int row = 0; row < handler.items().size(); row++) {
			ItemStack stack = handler.items().get(row);
			int y = lootY(row);
			context.fill(left + 10, y, left + 10 + listWidth, y + LOOT_ROW_HEIGHT - 2, BG_SLOT);
			context.drawBorder(left + 10, y, listWidth, LOOT_ROW_HEIGHT - 2, row == selection ? ACCENT_SAND : BORDER_CELL);
			context.drawItem(stack, left + 13, y + 4);
			context.drawItemInSlot(textRenderer, stack, left + 13, y + 4);
			text(context, stack.getName(), left + 33, y + 9, listWidth - 93, TEXT_PRIMARY);
		}
		text(context, label("place_hint", "Select loot, then a stash cell. R to rotate."),
			left + 10, lootY(PAGE_SIZE) + 3, listWidth, TEXT_SECONDARY);
	}

	private void drawStash(DrawContext context, int mouseX, int mouseY) {
		for (int y = 0; y < ROWS; y++) {
			for (int x = 0; x < COLUMNS; x++) {
				int sx = gridX + x * cell;
				int sy = gridY + y * cell;
				context.fill(sx, sy, sx + cell, sy + cell, BG_SLOT);
				context.drawBorder(sx, sy, cell + 1, cell + 1, BORDER_CELL);
			}
		}
		for (StashItem item : handler.stash()) {
			int sx = gridX + item.x() * cell;
			int sy = gridY + item.y() * cell;
			int w = item.width() * cell;
			int h = item.height() * cell;
			context.fill(sx + 1, sy + 1, sx + w, sy + h, BG_CELL_OCCUPIED_DEFAULT);
			context.drawBorder(sx, sy, w + 1, h + 1, BORDER_OCCUPIED);
			drawScaledItem(context, item.stack(), sx + 1, sy + 1, w - 1, h - 1, item.rotated());
		}
		if (selection < 0 || !overGrid(mouseX, mouseY)) return;
		var size = ItemSizeRegistry.getSize(handler.items().get(selection)).oriented(rotated);
		int x = (mouseX - gridX) / cell;
		int y = (mouseY - gridY) / cell;
		int endX = Math.min(COLUMNS, x + size.width());
		int endY = Math.min(ROWS, y + size.height());
		context.fill(gridX + x * cell, gridY + y * cell, gridX + endX * cell, gridY + endY * cell, 0x6698B878);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		if (!overGrid(mouseX, mouseY) || selection >= 0) return;
		int x = (mouseX - gridX) / cell;
		int y = (mouseY - gridY) / cell;
		for (StashItem item : handler.stash()) {
			if (x >= item.x() && x < item.x() + item.width() && y >= item.y() && y < item.y() + item.height()) {
				context.drawItemTooltip(textRenderer, item.stack(), mouseX, mouseY);
				break;
			}
		}
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (button == 0 && x >= left + 10 && x < left + listWidth - 56 && y >= lootY(0)) {
			int row = (int) (y - lootY(0)) / LOOT_ROW_HEIGHT;
			if (row < handler.items().size()) {
				selection = row == selection ? -1 : row;
				rotated = false;
				send(SELECT_FIRST + row);
				return true;
			}
		}
		if (button == 0 && selection >= 0 && overGrid(x, y)) {
			int index = (int) (x - gridX) / cell + (int) (y - gridY) / cell * COLUMNS;
			send(CELL_FIRST + index);
			return true;
		}
		return super.mouseClicked(x, y, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_R && selection >= 0) {
			rotated = !rotated;
			send(ROTATE);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private int lootY(int row) { return top + 24 + row * LOOT_ROW_HEIGHT; }

	private boolean overGrid(double x, double y) {
		return x >= gridX && x < gridX + COLUMNS * cell && y >= gridY && y < gridY + ROWS * cell;
	}
}
