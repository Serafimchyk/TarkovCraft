package com.tarkovcraft.client.screen;

import com.tarkovcraft.client.screen.AdminUi.Rect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

import static com.tarkovcraft.client.screen.AdminUi.*;

final class TarkovDropdownWidget {
	private static final int ROW_HEIGHT = 22;

	record Option(String value, String label, ItemStack icon) {
		Option(String value, String label) {
			this(value, label, ItemStack.EMPTY);
		}
	}

	private final TextRenderer font;
	private final List<Option> options;
	private final String selected;
	private final Consumer<String> onSelect;
	private final Rect panel;
	private final int listTop;
	private final int rows;
	private final AdminSearchField search;
	private List<Option> filtered;
	private int offset;
	private int cursor = -1;

	TarkovDropdownWidget(TextRenderer font, Rect anchor, int screenHeight, List<Option> options,
		String selected, Consumer<String> onSelect) {
		this.font = font;
		this.options = this.filtered = options;
		this.selected = selected;
		this.onSelect = onSelect;
		boolean searchable = options.size() > 7;
		int header = searchable ? 25 : 0;
		int below = screenHeight - anchor.bottom() - 10;
		int above = anchor.y() - 10;
		int wanted = Math.min(Math.max(1, options.size()), 7);
		boolean opensBelow = below >= wanted * ROW_HEIGHT + header + 8 || below >= above;
		rows = MathHelper.clamp(((opensBelow ? below : above) - header - 8) / ROW_HEIGHT, 1, wanted);
		int h = rows * ROW_HEIGHT + header + 8;
		panel = new Rect(anchor.x(), opensBelow ? anchor.bottom() + 3 : anchor.y() - h - 3, anchor.w(), h);
		listTop = panel.y() + header + 4;
		if (searchable) {
			search = new AdminSearchField(font, new Rect(panel.x() + 10, panel.y() + 5, panel.w() - 20, 17),
				"Фильтр списка", "Фильтр списка…");
			search.setMaxLength(80);
			search.setFocused(true);
			search.setChangedListener(this::filter);
		} else {
			search = null;
		}
	}

	private void filter(String query) {
		String normalized = AdminCommands.searchable(query);
		filtered = options.stream().filter(option ->
			AdminCommands.searchable(option.label() + " " + option.value()).contains(normalized)).toList();
		offset = 0;
		cursor = -1;
	}

	void render(DrawContext ctx, int mx, int my, float delta) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 400);
		fillRoundedRect(ctx, panel, 4, CONTROL);
		drawRoundedBorder(ctx, panel, 4, AMBER);
		if (search != null) {
			fillRoundedRect(ctx, new Rect(panel.x() + 4, panel.y() + 4, panel.w() - 8, 20), 4, ACTIVE);
			search.render(ctx, mx, my, delta);
		}
		if (filtered.isEmpty()) center(ctx, font, "Нет вариантов", row(0), MUTED);
		for (int i = 0; i < rows && offset + i < filtered.size(); i++) {
			int index = offset + i;
			Option option = filtered.get(index);
			Rect row = row(i);
			if (row.contains(mx, my) || index == cursor || option.value().equals(selected)) fillRoundedRect(ctx, row, 4, ACTIVE);
			int left = row.x() + 6;
			if (!option.icon().isEmpty()) {
				ctx.drawItem(option.icon(), left, row.y() + 3);
				left += 22;
			}
			ctx.drawText(font, trim(font, option.label(), row.right() - left - 7), left, row.y() + 7,
				option.value().equals(selected) ? AMBER : INK, false);
		}
		if (filtered.size() > rows) {
			int h = Math.max(6, rows * ROW_HEIGHT * rows / filtered.size());
			int y = listTop + (rows * ROW_HEIGHT - h) * offset / (filtered.size() - rows);
			ctx.fill(panel.right() - 4, y, panel.right() - 2, y + h, AMBER);
		}
		ctx.getMatrices().pop();
	}

	private Rect row(int index) {
		return new Rect(panel.x() + 4, listTop + index * ROW_HEIGHT, panel.w() - 10, ROW_HEIGHT);
	}

	boolean mouseClicked(double x, double y, int button) {
		if (!panel.contains(x, y)) return false;
		if (button != 0 || search != null && search.mouseClicked(x, y, button)) return true;
		for (int i = 0; i < rows && offset + i < filtered.size(); i++) {
			if (row(i).contains(x, y)) {
				onSelect.accept(filtered.get(offset + i).value());
				break;
			}
		}
		return true;
	}

	void scroll(double amount) {
		offset = MathHelper.clamp(offset + (amount > 0 ? -1 : 1), 0, Math.max(0, filtered.size() - rows));
		cursor = -1;
	}

	boolean keyPressed(int key, int scan, int modifiers) {
		if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
			if (!filtered.isEmpty()) {
				cursor = MathHelper.clamp(cursor < 0 ? offset : cursor + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1),
					0, filtered.size() - 1);
				offset = MathHelper.clamp(offset, Math.max(0, cursor - rows + 1), cursor);
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			if (!filtered.isEmpty()) onSelect.accept(filtered.get(cursor < 0 ? offset : cursor).value());
			return true;
		}
		return search != null && search.keyPressed(key, scan, modifiers);
	}

	boolean charTyped(char ch, int modifiers) {
		return search != null && search.charTyped(ch, modifiers);
	}

	void tick() {
		if (search != null) search.tick();
	}
}
