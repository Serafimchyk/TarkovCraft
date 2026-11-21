package com.tarkovcraft.grid;

public record ItemSize(int width, int height) {
	public static final ItemSize ONE_BY_ONE = new ItemSize(1, 1);

	public ItemSize {
		if (width < 1 || height < 1) {
			throw new IllegalArgumentException("ItemSize dimensions must be >= 1, got " + width + "x" + height);
		}
	}

	public ItemSize rotated() {
		return new ItemSize(height, width);
	}

	public ItemSize oriented(boolean rotated) {
		return rotated ? rotated() : this;
	}
}
