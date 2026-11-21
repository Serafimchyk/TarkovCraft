package com.tarkovcraft.grid;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

public final class GridEntry {
	private final UUID entryId;
	private ItemStack stack;
	private int x;
	private int y;
	private boolean rotated;

	public GridEntry(UUID entryId, ItemStack stack, int x, int y, boolean rotated) {
		this.entryId = entryId;
		this.stack = stack;
		this.x = x;
		this.y = y;
		this.rotated = rotated;
	}

	public static GridEntry create(ItemStack stack, int x, int y, boolean rotated) {
		return new GridEntry(UUID.randomUUID(), stack, x, y, rotated);
	}

	public UUID getEntryId() {
		return entryId;
	}

	public ItemStack getStack() {
		return stack;
	}

	public void setStack(ItemStack stack) {
		this.stack = stack;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public boolean isRotated() {
		return rotated;
	}

	public void setRotated(boolean rotated) {
		this.rotated = rotated;
	}

	public ItemSize getOccupiedSize() {
		return ItemSizeRegistry.getSize(stack).oriented(rotated);
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putUuid("EntryId", entryId);
		nbt.put("Stack", stack.writeNbt(new NbtCompound()));
		nbt.putInt("X", x);
		nbt.putInt("Y", y);
		nbt.putBoolean("Rotated", rotated);
		return nbt;
	}

	public static GridEntry fromNbt(NbtCompound nbt) {
		UUID id = nbt.containsUuid("EntryId") ? nbt.getUuid("EntryId") : UUID.randomUUID();
		ItemStack stack = ItemStack.fromNbt(nbt.getCompound("Stack"));
		int x = nbt.getInt("X");
		int y = nbt.getInt("Y");
		boolean rotated = nbt.getBoolean("Rotated");
		return new GridEntry(id, stack, x, y, rotated);
	}
}
