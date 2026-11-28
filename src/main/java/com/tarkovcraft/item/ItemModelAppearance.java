package com.tarkovcraft.item;

import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public record ItemModelAppearance(@Nullable String modelSilhouette, @Nullable Identifier modelTexture,
		@Nullable String groundSilhouette) {
	public static final ItemModelAppearance NONE = new ItemModelAppearance(null, null, null);
}
