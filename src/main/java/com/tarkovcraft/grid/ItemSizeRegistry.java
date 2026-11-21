package com.tarkovcraft.grid;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tarkovcraft.item.TarkovSizedItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves Tarkov grid dimensions (width x height) for any ItemStack.
 * Defaults to 1x1 for all unconfigured vanilla or modded items.
 * Includes smart TaCZ gun & magazine size rules:
 * - Pistol magazines: 1x1
 * - Standard rifle/SMG magazines: 1x2
 * - Drum / high-capacity magazines: 2x2
 */
public final class ItemSizeRegistry {
	private static final Logger LOGGER = LoggerFactory.getLogger("TarkovCraft/ItemSize");
	private static final Map<Identifier, ItemSize> ITEM_SIZES = new HashMap<>();
	private static final Map<String, ItemSize> TACZ_GUN_ID_SIZES = new HashMap<>();
	private static final Map<String, ItemSize> TACZ_ATTACHMENT_ID_SIZES = new HashMap<>();

	static {
		registerDefaults();
	}

	private ItemSizeRegistry() {
	}

	private static void registerDefaults() {
		ITEM_SIZES.clear();
		TACZ_GUN_ID_SIZES.clear();
		TACZ_ATTACHMENT_ID_SIZES.clear();

		// TaCZ base items
		ITEM_SIZES.put(new Identifier("tacz", "modern_kinetic_gun"), new ItemSize(4, 2));
		ITEM_SIZES.put(new Identifier("tacz", "ammo"), new ItemSize(1, 1));
		ITEM_SIZES.put(new Identifier("tacz", "ammo_box"), new ItemSize(2, 2));
		ITEM_SIZES.put(new Identifier("tacz", "attachment"), new ItemSize(1, 1));

		// Pistols (2x1)
		TACZ_GUN_ID_SIZES.put("tacz:glock_17", new ItemSize(2, 1));
		TACZ_GUN_ID_SIZES.put("tacz:deagle", new ItemSize(2, 1));
		TACZ_GUN_ID_SIZES.put("tacz:deagle_golden", new ItemSize(2, 1));
		TACZ_GUN_ID_SIZES.put("tacz:cz75", new ItemSize(2, 1));
		TACZ_GUN_ID_SIZES.put("tacz:p320", new ItemSize(2, 1));
		TACZ_GUN_ID_SIZES.put("tacz:m1911", new ItemSize(2, 1));
		// SMGs (3x2)
		TACZ_GUN_ID_SIZES.put("tacz:mp5", new ItemSize(3, 2));
		TACZ_GUN_ID_SIZES.put("tacz:ump45", new ItemSize(3, 2));
		TACZ_GUN_ID_SIZES.put("tacz:vector45", new ItemSize(3, 2));
		TACZ_GUN_ID_SIZES.put("tacz:p90", new ItemSize(3, 2));
		// Snipers / LMGs (5x2)
		TACZ_GUN_ID_SIZES.put("tacz:ai_awp", new ItemSize(5, 2));
		TACZ_GUN_ID_SIZES.put("tacz:m95", new ItemSize(5, 2));
		TACZ_GUN_ID_SIZES.put("tacz:m249", new ItemSize(5, 2));
		TACZ_GUN_ID_SIZES.put("tacz:rpk", new ItemSize(5, 2));

		// TaCZ Magazines: pistol 1x1, rifle 1x2, drum 2x2
		TACZ_ATTACHMENT_ID_SIZES.put("tacz:light_extended_mag_1", new ItemSize(1, 1));
		TACZ_ATTACHMENT_ID_SIZES.put("tacz:light_extended_mag_2", new ItemSize(1, 1));
		TACZ_ATTACHMENT_ID_SIZES.put("tacz:light_extended_mag_3", new ItemSize(1, 2));
		TACZ_ATTACHMENT_ID_SIZES.put("tacz:extended_mag_1", new ItemSize(1, 2));
		TACZ_ATTACHMENT_ID_SIZES.put("tacz:extended_mag_2", new ItemSize(1, 2));
		TACZ_ATTACHMENT_ID_SIZES.put("tacz:extended_mag_3", new ItemSize(2, 2));
		TACZ_ATTACHMENT_ID_SIZES.put("tacz:sniper_extended_mag_1", new ItemSize(1, 2));
		TACZ_ATTACHMENT_ID_SIZES.put("tacz:sniper_extended_mag_2", new ItemSize(1, 2));
		TACZ_ATTACHMENT_ID_SIZES.put("tacz:sniper_extended_mag_3", new ItemSize(2, 2));
	}

	public static void register(Identifier itemId, ItemSize size) {
		ITEM_SIZES.put(itemId, size);
	}

	public static void reloadFromResources(ResourceManager manager) {
		registerDefaults();
		Map<Identifier, Resource> resources = manager.findResources("item_sizes", id -> id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
			try (InputStreamReader reader = new InputStreamReader(entry.getValue().getInputStream(), StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				if (root.has("items") && root.get("items").isJsonObject()) {
					for (Map.Entry<String, JsonElement> itemEntry : root.getAsJsonObject("items").entrySet()) {
						JsonObject sz = itemEntry.getValue().getAsJsonObject();
						ITEM_SIZES.put(new Identifier(itemEntry.getKey()), new ItemSize(sz.get("width").getAsInt(), sz.get("height").getAsInt()));
					}
				}
				if (root.has("tacz_guns") && root.get("tacz_guns").isJsonObject()) {
					for (Map.Entry<String, JsonElement> gunEntry : root.getAsJsonObject("tacz_guns").entrySet()) {
						JsonObject sz = gunEntry.getValue().getAsJsonObject();
						TACZ_GUN_ID_SIZES.put(gunEntry.getKey(), new ItemSize(sz.get("width").getAsInt(), sz.get("height").getAsInt()));
					}
				}
				if (root.has("tacz_attachments") && root.get("tacz_attachments").isJsonObject()) {
					for (Map.Entry<String, JsonElement> attEntry : root.getAsJsonObject("tacz_attachments").entrySet()) {
						JsonObject sz = attEntry.getValue().getAsJsonObject();
						TACZ_ATTACHMENT_ID_SIZES.put(attEntry.getKey(), new ItemSize(sz.get("width").getAsInt(), sz.get("height").getAsInt()));
					}
				}
			} catch (Exception e) {
				LOGGER.warn("Failed to load item_sizes from {}: {}", entry.getKey(), e.getMessage());
			}
		}
	}

	public static ItemSize getSize(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ItemSize.ONE_BY_ONE;
		}
		Item item = stack.getItem();
		if (item instanceof TarkovSizedItem sized) {
			return sized.getStoredItemSize();
		}

		Identifier id = Registries.ITEM.getId(item);
		if ("tacz".equals(id.getNamespace()) && stack.hasNbt()) {
			NbtCompound nbt = stack.getNbt();
			if (nbt != null) {
				if (nbt.contains("GunId")) {
					ItemSize gunSize = TACZ_GUN_ID_SIZES.get(nbt.getString("GunId"));
					if (gunSize != null) return gunSize;
				}
				if (nbt.contains("AttachmentId")) {
					String attId = nbt.getString("AttachmentId");
					ItemSize attSize = TACZ_ATTACHMENT_ID_SIZES.get(attId);
					if (attSize != null) return attSize;
					// Heuristic for custom gunpack magazines
					String lower = attId.toLowerCase();
					if (lower.contains("drum")) return new ItemSize(2, 2);
					if (lower.contains("pistol") && lower.contains("mag")) return new ItemSize(1, 1);
					if (lower.contains("mag")) return new ItemSize(1, 2);
				}
			}
		}

		// Also support magazine-like item IDs from any TaCZ addon / gunpack
		String path = id.getPath().toLowerCase();
		if (path.contains("drum")) return new ItemSize(2, 2);
		if (path.contains("pistol") && path.contains("mag")) return new ItemSize(1, 1);
		if (path.contains("mag") && !path.contains("magma")) return new ItemSize(1, 2);

		return ITEM_SIZES.getOrDefault(id, ItemSize.ONE_BY_ONE);
	}

	public static boolean isPistolLike(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		ItemSize size = getSize(stack);
		return size.width() <= 2 && size.height() == 1;
	}
}
