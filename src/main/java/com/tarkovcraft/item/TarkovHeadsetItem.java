package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class TarkovHeadsetItem extends ModelledItem {
	private final ItemSize size;
	private final ItemRarityTier rarity;
	private final float weightKg;

	public TarkovHeadsetItem(Settings settings, ItemSize size, ItemRarityTier rarity, float weightKg) {
		super(settings.maxCount(1));
		this.size = size;
		this.rarity = rarity;
		this.weightKg = Math.max(0.0f, weightKg);
	}

	@Override
	public ItemSize getStoredItemSize() {
		return size;
	}

	@Override
	public ItemRarityTier getRarityTier() {
		return rarity;
	}

	@Override
	public float getWeightKg() {
		return weightKg;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.tarkovcraft.headset.description").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.headset.range").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.headset.gain").formatted(Formatting.GREEN));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.headset.compression").formatted(Formatting.GREEN));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.headset.compatibility").formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.item_size", size.width(), size.height()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_weight",
			String.format(Locale.ROOT, "%.2f", weightKg)).formatted(Formatting.DARK_GRAY));
	}
}
