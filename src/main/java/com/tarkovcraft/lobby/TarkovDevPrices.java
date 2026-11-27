package com.tarkovcraft.lobby;

import com.google.gson.JsonParser;
import com.tarkovcraft.ammo.AmmoTier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TarkovDevPrices {
	public enum Source { TRADER, OTHER_TRADER, FLEA, HANDBOOK, LOCAL }

	public record Reference(String eftId, long unitPrice, Source source, long fencePrice) { }

	private static final String RESOURCE = "/data/tarkovcraft/catalog/trader_prices.json";
	private static final Map<String, Reference> PRICES = load();

	private TarkovDevPrices() { }

	public static Reference forStack(ItemStack stack) {
		if (AmmoTier.isTaczAmmoStack(stack)) {
			return PRICES.get(AmmoTier.getAmmoId(stack) + "/" + AmmoTier.getFromAmmoStack(stack).getId());
		}
		if (AmmoTier.isTaczGunStack(stack)) return PRICES.get("gun/" + stack.getNbt().getString("GunId"));
		var itemId = Registries.ITEM.getId(stack.getItem());
		if (itemId.getNamespace().equals("tacz") && itemId.getPath().equals("attachment") && stack.hasNbt()) {
			return PRICES.get("attachment/" + stack.getNbt().getString("AttachmentId"));
		}
		return PRICES.get(itemId.toString());
	}

	private static Map<String, Reference> load() {
		var stream = TarkovDevPrices.class.getResourceAsStream(RESOURCE);
		if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
		try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			Map<String, Reference> prices = new HashMap<>();
			var data = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("prices");
			for (var entry : data.entrySet()) {
				var values = entry.getValue().getAsJsonArray();
				prices.put(entry.getKey(), new Reference(values.get(0).getAsString(), values.get(1).getAsLong(),
					Source.valueOf(values.get(2).getAsString().toUpperCase(Locale.ROOT)), values.get(3).getAsLong()));
			}
			return Map.copyOf(prices);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to read " + RESOURCE, exception);
		}
	}
}
