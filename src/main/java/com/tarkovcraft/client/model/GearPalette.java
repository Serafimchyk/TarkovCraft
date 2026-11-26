package com.tarkovcraft.client.model;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Recolors the pack's materials from the inventory icon for each equipment item. */
public final class GearPalette {
	private static final Map<Identifier, Optional<Color>> ICON_COLORS = new HashMap<>();
	private static final Map<TextureKey, Optional<Identifier>> RECOLORED_TEXTURES = new HashMap<>();

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
				TextureManager textures = MinecraftClient.getInstance().getTextureManager();
				for (Optional<Identifier> texture : RECOLORED_TEXTURES.values()) {
					texture.ifPresent(textures::destroyTexture);
				}
				RECOLORED_TEXTURES.clear();
				ICON_COLORS.clear();
			}
		});
	}

	static Identifier textureFor(Item item, Identifier baseTexture) {
		Identifier itemId = Registries.ITEM.getId(item);
		TextureKey key = new TextureKey(itemId, baseTexture);
		return RECOLORED_TEXTURES.computeIfAbsent(key, GearPalette::createTexture).orElse(baseTexture);
	}

	private static Optional<Identifier> createTexture(TextureKey key) {
		MinecraftClient client = MinecraftClient.getInstance();
		ResourceManager resources = client.getResourceManager();
		Optional<Color> icon = ICON_COLORS.computeIfAbsent(key.item(), item -> readIconColor(resources, item));
		if (icon.isEmpty()) {
			return Optional.empty();
		}

		NativeImage image;
		try {
			image = readImage(resources, key.baseTexture());
		} catch (IOException | IllegalArgumentException e) {
			return Optional.empty();
		}
		NativeImageBackedTexture texture = null;
		boolean registered = false;
		try {
			Color material = materialColor(image, 255);
			if (material == null) {
				return Optional.empty();
			}
			recolor(image, material, icon.get());
			texture = new NativeImageBackedTexture(image);
			// Stable IDs let RenderLayer reuse its cached layer across reloads.
			Identifier id = new Identifier("tarkovcraft", "gear_palette/"
				+ key.baseTexture().getPath() + "/" + key.item().getNamespace() + "/" + key.item().getPath());
			client.getTextureManager().registerTexture(id, texture);
			registered = true;
			return Optional.of(id);
		} finally {
			if (!registered) {
				if (texture == null) image.close();
				else texture.close();
			}
		}
	}

	private static Optional<Color> readIconColor(ResourceManager resources, Identifier item) {
		// TarkovCraft's equipment item models use a matching textures/item/<id>.png layer.
		Identifier texture = new Identifier(item.getNamespace(), "textures/item/" + item.getPath() + ".png");
		try (NativeImage icon = readImage(resources, texture)) {
			return Optional.ofNullable(materialColor(icon, 128));
		} catch (IOException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static NativeImage readImage(ResourceManager resources, Identifier texture) throws IOException {
		try (InputStream stream = resources.open(texture)) {
			return NativeImage.read(NativeImage.Format.RGBA, stream);
		}
	}

	@Nullable
	private static Color materialColor(NativeImage image, int minAlpha) {
		int[] brightnessCounts = new int[256];
		int count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int pixel = image.getColor(x, y);
				if ((pixel >>> 24) >= minAlpha) {
					brightnessCounts[Math.round(Color.fromPixel(pixel).luminance())]++;
					count++;
				}
			}
		}
		if (count == 0) {
			return null;
		}
		int low = percentile(brightnessCounts, count * 15 / 100);
		int high = percentile(brightnessCounts, count * 85 / 100);
		int red = 0;
		int green = 0;
		int blue = 0;
		int included = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int pixel = image.getColor(x, y);
				if ((pixel >>> 24) < minAlpha) continue;
				Color color = Color.fromPixel(pixel);
				int shade = Math.round(color.luminance());
				if (shade < low || shade > high) continue;
				red += color.red();
				green += color.green();
				blue += color.blue();
				included++;
			}
		}
		return new Color(red / included, green / included, blue / included);
	}

	private static int percentile(int[] counts, int index) {
		for (int shade = 0; shade < counts.length; shade++) {
			index -= counts[shade];
			if (index < 0) return shade;
		}
		return 255;
	}

	private static void recolor(NativeImage image, Color material, Color target) {
		float baseShade = material.luminance();
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int pixel = image.getColor(x, y);
				// Preserve transparent UV padding, openings and translucent lenses exactly.
				if ((pixel >>> 24) != 255) continue;
				Color original = Color.fromPixel(pixel);
				float shade = original.luminance();
				float chromaDistance = original.chromaDistance(material, shade, baseShade);
				float materialWeight = 1.0F - MathHelper.clamp((chromaDistance - 0.18F) / 0.30F, 0.0F, 1.0F);
				float lightRatio = MathHelper.clamp(shade / Math.max(baseShade, 1.0F), 0.0F, 2.0F);
				int red = shadeChannel(original.red(), target.red(), lightRatio, materialWeight);
				int green = shadeChannel(original.green(), target.green(), lightRatio, materialWeight);
				int blue = shadeChannel(original.blue(), target.blue(), lightRatio, materialWeight);
				image.setColor(x, y, 0xFF000000 | blue << 16 | green << 8 | red);
			}
		}
	}

	private static int shadeChannel(int original, int target, float ratio, float weight) {
		return MathHelper.clamp(Math.round(MathHelper.lerp(weight, original, target * ratio)), 0, 255);
	}

	private record TextureKey(Identifier item, Identifier baseTexture) {}

	private record Color(int red, int green, int blue) {
		static Color fromPixel(int abgr) {
			return new Color(abgr & 255, abgr >>> 8 & 255, abgr >>> 16 & 255);
		}

		float luminance() {
			return (3 * red + 6 * green + blue) / 10.0F;
		}

		float chromaDistance(Color other, float shade, float otherShade) {
			float scale = Math.max(shade, 24.0F);
			float otherScale = Math.max(otherShade, 24.0F);
			float redGreen = (red - green) / scale - (other.red - other.green) / otherScale;
			float blueGreen = (blue - green) / scale - (other.blue - other.green) / otherScale;
			return Math.max(Math.abs(redGreen), Math.abs(blueGreen));
		}
	}
}
