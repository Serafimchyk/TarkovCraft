package com.tarkovcraft.client.screen;

final class LobbyLayoutMetrics {
	static final int MARGIN = 8;
	static final int GAP = 6;
	static final int ROW_HEIGHT = 18;

	final Rect squad;
	final Rect preview;
	final Rect raid;

	LobbyLayoutMetrics(int width, int height) {
		int contentWidth = width - MARGIN * 2 - GAP * 2;
		int sideWidth = Math.min(240, Math.max(100, contentWidth * 30 / 100));
		int previewWidth = Math.max(1, contentWidth - sideWidth * 2);
		int top = LobbyTopNavBar.HEIGHT + MARGIN;
		int bottom = height - MARGIN;
		squad = new Rect(MARGIN, top, sideWidth, bottom - top);
		preview = new Rect(squad.right() + GAP, top, previewWidth, bottom - top);
		raid = new Rect(preview.right() + GAP, top, sideWidth, bottom - top);
	}

	static CharacterColumns characterColumns(int contentWidth) {
		int stash = Math.min(contentWidth * 2 / 5, Math.max(158, contentWidth * 37 / 100));
		int equipment = (contentWidth - stash) * 48 / 100;
		return new CharacterColumns(equipment, contentWidth - stash - equipment, stash);
	}

	static int stashCell(int columnWidth, int columns) {
		return Math.max(1, Math.min(26, (columnWidth - 18) / columns));
	}

	record CharacterColumns(int equipment, int storage, int stash) { }

	record Rect(int x, int y, int width, int height) {
		int right() {
			return x + width;
		}

		int bottom() {
			return y + height;
		}

		boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
		}
	}
}
