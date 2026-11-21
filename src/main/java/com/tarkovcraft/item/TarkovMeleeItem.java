package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * TarkovCraft melee weapon compatible with EquipmentSlotType.MELEE.
 */
public class TarkovMeleeItem extends SwordItem implements TarkovSizedItem {
	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;

	public TarkovMeleeItem(
		Settings settings,
		float meleeDamage,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		super(
			ToolMaterials.IRON,
			Math.max(1, Math.round(meleeDamage - ToolMaterials.IRON.getAttackDamage() - 1.0f)),
			-2.0f,
			settings.maxDamage(2048)
		);
		this.storedItemSize = storedItemSize != null ? storedItemSize : new ItemSize(1, 2);
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.UNCOMMON;
		this.weightKg = Math.max(0.0f, weightKg);
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
		super.appendTooltip(stack, world, tooltip, context);
		tooltip.add(Text.translatable("tooltip.tarkovcraft.item_size",
			storedItemSize.width(), storedItemSize.height()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_weight",
			String.format(Locale.ROOT, "%.2f", weightKg)).formatted(Formatting.DARK_GRAY));
	}
}
