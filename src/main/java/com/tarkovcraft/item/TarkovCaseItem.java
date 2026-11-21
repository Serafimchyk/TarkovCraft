package com.tarkovcraft.item;

import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.screen.TarkovScreenHandler;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Openable TarkovCraft storage case (Wallet, Keytool, Docs, SICC, Medcase, THICC, etc.).
 * Persists an internal GridContainer in NBT via ContainerItemHelper.
 */
public class TarkovCaseItem extends Item implements TarkovSizedItem {
	private final int gridWidth;
	private final int gridHeight;
	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;

	public TarkovCaseItem(
		Settings settings,
		int gridWidth,
		int gridHeight,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		super(settings.maxCount(1));
		this.gridWidth = Math.max(1, gridWidth);
		this.gridHeight = Math.max(1, gridHeight);
		this.storedItemSize = storedItemSize != null ? storedItemSize : ItemSize.ONE_BY_ONE;
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.RARE;
		this.weightKg = Math.max(0.0f, weightKg);
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
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.pass(stack);
		}
		if (!(user instanceof ServerPlayerEntity sp) || sp.isSpectator()) {
			return TypedActionResult.fail(stack);
		}
		UUID caseUuid = ContainerItemHelper.getOrCreateContainerUuid(stack);
		if (sp instanceof PlayerLoadoutHolder holder && !sp.isCreative()) {
			holder.tarkovcraft$syncLoadout();
		}
		TarkovScreenHandler.openCaseScreen(sp, caseUuid);
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.tarkovcraft.case_grid",
			gridWidth, gridHeight, gridWidth * gridHeight).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.case_open_hint").formatted(Formatting.YELLOW));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.item_size",
			storedItemSize.width(), storedItemSize.height()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_weight",
			String.format(Locale.ROOT, "%.2f", weightKg)).formatted(Formatting.DARK_GRAY));
	}
}
