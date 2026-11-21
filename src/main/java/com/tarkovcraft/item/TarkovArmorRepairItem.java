package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Ballistic armor plates and armor repair kits that restore durability on equipped TarkovArmorItem pieces.
 */
public class TarkovArmorRepairItem extends Item implements TarkovSizedItem {
	private static final EquipmentSlotType[] REPAIR_PRIORITY = {
		EquipmentSlotType.BODY_ARMOR,
		EquipmentSlotType.HELMET,
		EquipmentSlotType.FACE_MASK
	};

	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;
	private final int repairAmount;

	public TarkovArmorRepairItem(
		Settings settings,
		int repairAmount,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		super(settings.maxCount(1));
		this.repairAmount = Math.max(1, repairAmount);
		this.storedItemSize = storedItemSize != null ? storedItemSize : ItemSize.ONE_BY_ONE;
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.UNCOMMON;
		this.weightKg = Math.max(0.0f, weightKg);
	}

	public int getRepairAmount() {
		return repairAmount;
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
		if (!(user instanceof ServerPlayerEntity sp)) {
			return TypedActionResult.fail(stack);
		}
		if (!applyRepair(sp, stack)) {
			return TypedActionResult.fail(stack);
		}
		if (stack.isEmpty() && !sp.isCreative()) {
			sp.setStackInHand(hand, ItemStack.EMPTY);
		}
		return TypedActionResult.success(sp.getStackInHand(hand), false);
	}

	public static boolean canRepairAny(ServerPlayerEntity player) {
		if (player == null || !(player instanceof PlayerLoadoutHolder holder)) {
			return false;
		}
		return findFirstDamagedArmor(holder.tarkovcraft$getLoadout()) != null;
	}

	@Nullable
	private static ItemStack findFirstDamagedArmor(PlayerLoadout loadout) {
		if (loadout == null) {
			return null;
		}
		for (EquipmentSlotType slot : REPAIR_PRIORITY) {
			ItemStack eq = loadout.getEquipment(slot);
			if (!eq.isEmpty()
				&& eq.getItem() instanceof TarkovArmorItem armor
				&& armor.getArmorClass() > 0
				&& eq.isDamageable()
				&& eq.getDamage() > 0) {
				return eq;
			}
		}
		return null;
	}

	public static boolean applyRepair(ServerPlayerEntity player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty() || !(stack.getItem() instanceof TarkovArmorRepairItem repair)) {
			return false;
		}
		if (player.isSpectator() || !player.isAlive() || !(player instanceof PlayerLoadoutHolder holder)) {
			return false;
		}

		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		ItemStack targetArmor = findFirstDamagedArmor(loadout);
		if (targetArmor == null) {
			player.sendMessage(Text.translatable("message.tarkovcraft.repair_not_needed"), true);
			return false;
		}

		int curDamage = targetArmor.getDamage();
		int repaired = Math.min(curDamage, repair.repairAmount);
		targetArmor.setDamage(curDamage - repaired);

		if (!player.isCreative()) {
			stack.decrement(1);
		}
		holder.tarkovcraft$syncLoadout();
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.55f, 1.3f);
		player.sendMessage(Text.translatable("message.tarkovcraft.repair_done", repaired), true);
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.tarkovcraft.repair_use", repairAmount).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.item_size",
			storedItemSize.width(), storedItemSize.height()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_weight",
			String.format(Locale.ROOT, "%.2f", weightKg)).formatted(Formatting.DARK_GRAY));
	}
}
