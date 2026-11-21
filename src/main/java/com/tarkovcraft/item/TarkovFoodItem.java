package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * TarkovCraft food and drink provisions with FoodComponent, grid size, rarity, and weight.
 */
public class TarkovFoodItem extends Item implements TarkovSizedItem {
	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;
	private final boolean drink;

	public TarkovFoodItem(
		Settings settings,
		int hunger,
		float saturation,
		boolean drink,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		super(buildSettings(settings, hunger, saturation, drink));
		this.storedItemSize = storedItemSize != null ? storedItemSize : ItemSize.ONE_BY_ONE;
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.COMMON;
		this.weightKg = Math.max(0.0f, weightKg);
		this.drink = drink;
	}

	private static Settings buildSettings(Settings settings, int hunger, float saturation, boolean drink) {
		FoodComponent.Builder builder = new FoodComponent.Builder()
			.hunger(Math.max(0, hunger))
			.saturationModifier(Math.max(0.0f, saturation));
		if (drink || hunger <= 0) {
			builder.alwaysEdible();
		}
		return settings.maxCount(16).food(builder.build());
	}

	public boolean isDrink() {
		return drink;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return drink ? UseAction.DRINK : UseAction.EAT;
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
		tooltip.add(Text.translatable("tooltip.tarkovcraft.item_size",
			storedItemSize.width(), storedItemSize.height()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_weight",
			String.format(Locale.ROOT, "%.2f", weightKg)).formatted(Formatting.DARK_GRAY));
	}
}
