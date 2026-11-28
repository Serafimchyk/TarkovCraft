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

public class BackpackItem extends ModelledItem {
	private final int gridWidth;
	private final int gridHeight;
	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;

	public BackpackItem(Settings settings, int gridWidth, int gridHeight, ItemSize storedItemSize) {
		this(settings, gridWidth, gridHeight, storedItemSize, ItemRarityTier.UNCOMMON, -1.0f);
	}

	public BackpackItem(
		Settings settings,
		int gridWidth,
		int gridHeight,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		super(settings.maxCount(1));
		this.gridWidth = gridWidth;
		this.gridHeight = gridHeight;
		this.storedItemSize = storedItemSize;
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.UNCOMMON;
		this.weightKg = weightKg;
	}

	public int getGridWidth() {
		return gridWidth;
	}

	public int getGridHeight() {
		return gridHeight;
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
		tooltip.add(Text.translatable("tooltip.tarkovcraft.backpack_grid", gridWidth, gridHeight).formatted(Formatting.GRAY));
	}
}
