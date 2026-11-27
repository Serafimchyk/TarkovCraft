package com.tarkovcraft.client.screen;

import com.tarkovcraft.client.screen.AdminUi.Rect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

final class AdminSearchField extends TextFieldWidget {
	private final TextRenderer font;
	private final String placeholder;
	private int firstVisible;
	private int anchor;

	AdminSearchField(TextRenderer font, Rect rect, String title, String placeholder) {
		super(font, rect.x(), rect.y(), rect.w(), rect.h(), Text.literal(title));
		this.font = font;
		this.placeholder = placeholder;
		setDrawsBackground(false);
	}

	@Override
	public void setSelectionEnd(int index) {
		super.setSelectionEnd(index);
		anchor = MathHelper.clamp(index, 0, getText().length());
	}

	@Override
	public void onClick(double x, double y) {
		String text = getText();
		int start = Math.min(firstVisible, text.length());
		int position = start + font.trimToWidth(text.substring(start), Math.max(0, (int) x - getX())).length();
		setSelectionStart(position);
		setSelectionEnd(position);
	}

	@Override
	public void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
		String value = getText();
		int cursor = getCursor();
		firstVisible = Math.min(firstVisible, cursor);
		if (font.getWidth(value.substring(firstVisible, cursor)) >= getWidth()) {
			firstVisible = cursor - font.trimToWidth(value.substring(0, cursor), getWidth() - 2, true).length();
		}
		String shown = font.trimToWidth(value.substring(firstVisible), getWidth() - 1);
		int x = getX();
		int y = getY() + Math.floorDiv(getHeight() - font.fontHeight, 2) + 1;
		if (value.isEmpty()) {
			ctx.drawText(font, AdminUi.trim(font, placeholder, getWidth()), x, y, AdminUi.MUTED, false);
		}
		int cursorX = x + offset(shown, cursor);
		if (isFocused() && cursor != anchor) {
			int anchorX = x + offset(shown, anchor);
			ctx.fill(Math.min(cursorX, anchorX), y - 1, Math.max(cursorX, anchorX), y + font.fontHeight, AdminUi.ACTIVE);
		}
		ctx.drawText(font, shown, x, y, AdminUi.INK, false);
		if (isFocused() && Util.getMeasuringTimeMs() % 1000 < 550) {
			if (value.isEmpty()) cursorX -= 2;
			ctx.fill(cursorX, y - 1, cursorX + 1, y + font.fontHeight, AdminUi.AMBER);
		}
	}

	private int offset(String shown, int index) {
		return font.getWidth(shown.substring(0, MathHelper.clamp(index - firstVisible, 0, shown.length())));
	}
}
