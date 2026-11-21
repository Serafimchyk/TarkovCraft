package com.tarkovcraft.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side cache of workbench crafting material counts, FMJ ammo stocks, and extra gunpack calibers.
 */
@Environment(EnvType.CLIENT)
public final class ClientWorkbenchCache {
	private static Map<Identifier, Integer> itemCounts = new HashMap<>();
	private static Map<String, Integer> fmjAmmoCounts = new HashMap<>();
	private static List<String> extraCalibers = List.of();

	private ClientWorkbenchCache() {
	}

	public static void update(
		Map<Identifier, Integer> items,
		Map<String, Integer> fmjCounts,
		List<String> extras
	) {
		itemCounts = new HashMap<>(items);
		fmjAmmoCounts = new HashMap<>(fmjCounts);
		extraCalibers = List.copyOf(extras);
	}

	public static int getItemCount(Item item) {
		if (item == null) return 0;
		return itemCounts.getOrDefault(Registries.ITEM.getId(item), 0);
	}

	public static int getFmjCount(String ammoId) {
		if (ammoId == null) return 0;
		return fmjAmmoCounts.getOrDefault(ammoId, 0);
	}

	public static List<String> getExtraCalibers() {
		return Collections.unmodifiableList(extraCalibers);
	}
}
