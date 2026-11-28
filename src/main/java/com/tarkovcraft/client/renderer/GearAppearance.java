package com.tarkovcraft.client.renderer;

import com.tarkovcraft.client.model.GearModel;
import com.tarkovcraft.item.ModelledItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class GearAppearance {
	private GearAppearance() {
	}

	@Nullable
	public static GearModel worn(Item item) {
		return item instanceof ModelledItem modelled
			? GearModel.find(modelled.getModelAppearance().modelSilhouette()) : null;
	}

	@Nullable
	public static GearModel ground(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof ModelledItem modelled
			? GearModel.find(modelled.getModelAppearance().groundSilhouette()) : null;
	}
}
