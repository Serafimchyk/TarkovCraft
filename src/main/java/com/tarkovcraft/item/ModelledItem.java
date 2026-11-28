package com.tarkovcraft.item;

import net.minecraft.item.Item;

public abstract class ModelledItem extends Item implements TarkovSizedItem {
	private ItemModelAppearance modelAppearance = ItemModelAppearance.NONE;

	protected ModelledItem(Settings settings) {
		super(settings);
	}

	public final ItemModelAppearance getModelAppearance() {
		return modelAppearance;
	}

	/** Called during item registration. */
	public final void setModelAppearance(ItemModelAppearance appearance) {
		this.modelAppearance = appearance;
	}
}
