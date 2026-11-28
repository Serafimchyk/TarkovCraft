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

public class TacticalRigItem extends ModelledItem {
	private final List<ItemSize> compartmentLayouts;
	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;

	public TacticalRigItem(Settings settings, List<ItemSize> compartmentLayouts, ItemSize storedItemSize) {
		this(settings, compartmentLayouts, storedItemSize, ItemRarityTier.UNCOMMON, -1.0f);
	}

	public TacticalRigItem(
		Settings settings,
		List<ItemSize> compartmentLayouts,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		super(settings.maxCount(1));
		this.compartmentLayouts = List.copyOf(compartmentLayouts);
		this.storedItemSize = storedItemSize;
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.UNCOMMON;
		this.weightKg = weightKg;
	}

	public List<ItemSize> getCompartmentLayouts() {
		return compartmentLayouts;
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
		int totalCells = compartmentLayouts.stream().mapToInt(s -> s.width() * s.height()).sum();
		tooltip.add(Text.translatable("tooltip.tarkovcraft.rig_capacity", totalCells, compartmentLayouts.size()).formatted(Formatting.GRAY));
	}
}
