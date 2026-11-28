package com.tarkovcraft.client.model;

import com.tarkovcraft.item.ModelledItem;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Resolves generated textures and falls back to the pack texture for the silhouette. */
public final class GearPalette {
	private static final Map<Identifier, Boolean> AVAILABLE = new HashMap<>();

	private GearPalette() {
	}

	public static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return new Identifier("tarkovcraft", "gear_palette");
			}

			@Override
			public void reload(ResourceManager manager) {
				AVAILABLE.clear();
				GearModel.clearBakedParts();
			}
		});
	}

	static Identifier path(Identifier name, boolean dropped) {
		return new Identifier(name.getNamespace(),
			"textures/models/" + (dropped ? "ground/" : "armor/") + name.getPath() + ".png");
	}

	static Identifier textureFor(Item item, Identifier baseTexture, boolean dropped) {
		if (!(item instanceof ModelledItem modelled) || modelled.getModelAppearance().modelTexture() == null) {
			return baseTexture;
		}
		Identifier texture = path(modelled.getModelAppearance().modelTexture(), dropped);
		ResourceManager manager = MinecraftClient.getInstance().getResourceManager();
		return AVAILABLE.computeIfAbsent(texture, id -> manager.getResource(id).isPresent()) ? texture : baseTexture;
	}
}
