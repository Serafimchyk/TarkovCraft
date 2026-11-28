package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.MedicalHelper;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * TarkovCraft medical item (bandages, tourniquets, splints, painkillers, medkits, surgical kits).
 * Tracks remaining uses or HP pool via NBT_REMAINING without item stacking.
 */
public class TarkovMedicalItem extends ModelledItem {
	public static final String NBT_REMAINING = "TarkovMedRemaining";

	private final MedicalType medicalType;
	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;

	public TarkovMedicalItem(Settings settings, MedicalType medicalType) {
		this(settings, medicalType, medicalType.getGridSize(), medicalType.getRarityTier(), medicalType.getWeightKg());
	}

	public TarkovMedicalItem(
		Settings settings,
		MedicalType medicalType,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		super(settings.maxCount(1));
		this.medicalType = medicalType;
		this.storedItemSize = storedItemSize != null ? storedItemSize : medicalType.getGridSize();
		this.rarityTier = rarityTier != null ? rarityTier : medicalType.getRarityTier();
		this.weightKg = weightKg >= 0.0f ? weightKg : medicalType.getWeightKg();
	}

	public MedicalType getMedicalType() {
		return medicalType;
	}

	@Override
	public ItemSize getStoredItemSize() {
		return storedItemSize;
	}

	@Override
	public float getWeightKg() {
		return weightKg;
	}

	@Override
	public ItemRarityTier getRarityTier() {
		return rarityTier;
	}

	public static int getRemaining(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof TarkovMedicalItem med)) {
			return 0;
		}
		int max = med.getMedicalType().getMaxCapacity();
		NbtCompound nbt = stack.getNbt();
		if (nbt == null || !nbt.contains(NBT_REMAINING)) {
			return max;
		}
		return Math.max(0, Math.min(max, nbt.getInt(NBT_REMAINING)));
	}

	public static void setRemaining(ItemStack stack, int value) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof TarkovMedicalItem med)) {
			return;
		}
		if (value <= 0) {
			stack.setCount(0);
			return;
		}
		int clamped = Math.min(med.getMedicalType().getMaxCapacity(), value);
		stack.getOrCreateNbt().putInt(NBT_REMAINING, clamped);
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		int max = medicalType.getMaxCapacity();
		return max > 1 && getRemaining(stack) < max;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		int max = Math.max(1, medicalType.getMaxCapacity());
		int step = Math.round(13.0f * getRemaining(stack) / (float) max);
		return Math.max(0, Math.min(13, step));
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		float ratio = getRemaining(stack) / (float) Math.max(1, medicalType.getMaxCapacity());
		if (ratio >= 0.50f) return 0x55CC55;
		if (ratio >= 0.25f) return 0xE0B23B;
		return 0xE53935;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.pass(stack);
		}
		if (!(user instanceof ServerPlayerEntity sp) || sp.isSpectator() || !sp.isAlive()) {
			return TypedActionResult.fail(stack);
		}
		if (com.tarkovcraft.health.MedicalUseManager.startFromHand(sp, hand, stack)) {
			return TypedActionResult.success(stack, false);
		}
		return TypedActionResult.fail(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		int rem = getRemaining(stack);
		int max = medicalType.getMaxCapacity();
		float ratio = rem / (float) Math.max(1, max);
		Formatting resCol = ratio >= 0.50f ? Formatting.GREEN : (ratio >= 0.25f ? Formatting.YELLOW : Formatting.RED);

		if (medicalType.getCapacityMode() == MedicalType.CapacityMode.HP_POOL) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.med_pool", rem, max).formatted(resCol));
			tooltip.add(Text.translatable("tooltip.tarkovcraft.med_max_heal", medicalType.getMaxHealPerUse())
				.formatted(Formatting.GRAY));
		} else {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.med_uses", rem, max).formatted(resCol));
		}

		if (medicalType.isCuresLightBleed()) {
			if (medicalType.getCapacityMode() == MedicalType.CapacityMode.HP_POOL) {
				tooltip.add(Text.translatable("tooltip.tarkovcraft.med_cures_light_bleed_cost",
					medicalType.getLightBleedCost()).formatted(Formatting.GOLD));
			} else {
				tooltip.add(Text.translatable("tooltip.tarkovcraft.med_cures_light_bleed").formatted(Formatting.GOLD));
			}
		}
		if (medicalType.isCuresHeavyBleed()) {
			if (medicalType.getCapacityMode() == MedicalType.CapacityMode.HP_POOL) {
				tooltip.add(Text.translatable("tooltip.tarkovcraft.med_cures_heavy_bleed_cost",
					medicalType.getHeavyBleedCost()).formatted(Formatting.RED));
			} else {
				tooltip.add(Text.translatable("tooltip.tarkovcraft.med_cures_heavy_bleed").formatted(Formatting.RED));
			}
		}
		if (medicalType.isCuresFracture()) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.med_cures_fracture").formatted(Formatting.YELLOW));
		}
		if (medicalType.getPainkillerTicks() > 0) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.med_painkiller", medicalType.getPainkillerTicks() / 20)
				.formatted(Formatting.AQUA));
		}
		if (medicalType.isSurgicalKit()) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.med_surgery",
				Math.round(medicalType.getSurgeryRetention() * 100.0f)).formatted(Formatting.LIGHT_PURPLE));
		}

		float secs = medicalType.getUseDurationTicks() / 20.0f;
		String secStr = Math.abs(secs - Math.round(secs)) < 0.05f
			? String.valueOf(Math.round(secs))
			: String.format(Locale.ROOT, "%.2f", secs).replaceAll("0+$", "").replaceAll("\\.$", "");
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_use_time", secStr).formatted(Formatting.DARK_AQUA));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_weight",
			String.format(Locale.ROOT, "%.2f", weightKg)).formatted(Formatting.DARK_GRAY));
	}
}
