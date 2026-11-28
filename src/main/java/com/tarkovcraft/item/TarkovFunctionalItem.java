package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Catalog item with Tarkov grid size, weight and rarity.
 */
public class TarkovFunctionalItem extends Item implements TarkovSizedItem {
	private final String category;
	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;
	private final long basePrice;

	public TarkovFunctionalItem(
		Settings settings,
		String category,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		this(settings, category, storedItemSize, rarityTier, weightKg, 0);
	}

	public TarkovFunctionalItem(
		Settings settings, String category, ItemSize storedItemSize, ItemRarityTier rarityTier, float weightKg,
		long basePrice
	) {
		super(settings.maxCount("barter".equals(category) ? 64 : "grenade".equalsIgnoreCase(category) ? 4 : 1));
		this.category = category != null ? category : "gear";
		this.storedItemSize = storedItemSize != null ? storedItemSize : ItemSize.ONE_BY_ONE;
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.UNCOMMON;
		this.weightKg = Math.max(0.0f, weightKg);
		this.basePrice = Math.max(0L, basePrice);
	}

	public String getCategory() {
		return category;
	}

	public boolean isValuable() {
		return "valuable".equals(category);
	}

	public long getBasePrice() {
		return basePrice;
	}

	@Override
	public ItemSize getStoredItemSize() {
		return storedItemSize;
	}

	@Override
	public ItemRarityTier getRarityTier() {
		return rarityTier;
	}

	@Override
	public float getWeightKg() {
		return weightKg;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.tarkovcraft.category", formatCategory(category)).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.item_size",
			storedItemSize.width(), storedItemSize.height()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_weight",
			String.format(Locale.ROOT, "%.2f", weightKg)).formatted(Formatting.DARK_GRAY));
		if (basePrice > 0) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.value_rubles", basePrice).formatted(Formatting.GOLD));
		}
	}

	private static String formatCategory(String raw) {
		if (raw == null || raw.isBlank()) {
			return "Gear";
		}
		String spaced = raw.replace('_', ' ');
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}
}
