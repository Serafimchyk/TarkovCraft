package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.search.ItemRarityTier;
import com.tarkovcraft.screen.TarkovScreenHandler;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TarkovArmorRepairItem extends Item implements TarkovSizedItem {
	public enum Tier {
		T1(25, 2, 0.30f, 60),
		T2(45, 4, 0.22f, 80),
		T3(65, 5, 0.15f, 90),
		T4(90, 6, 0.10f, 100);

		private final int repairPoints;
		private final int maxArmorClass;
		private final float maxDurabilityLossRatio;
		private final int durationTicks;

		Tier(int repairPoints, int maxArmorClass, float maxDurabilityLossRatio, int durationTicks) {
			this.repairPoints = repairPoints;
			this.maxArmorClass = maxArmorClass;
			this.maxDurabilityLossRatio = maxDurabilityLossRatio;
			this.durationTicks = durationTicks;
		}
	}

	private record Pending(ServerPlayerEntity player, int startTick, int duration, ItemStack expectedKit,
			EquipmentSlotType slot, ItemStack target, Supplier<ItemStack> source, Consumer<ItemStack> replace) {}

	private static final EquipmentSlotType[] REPAIR_PRIORITY = {
		EquipmentSlotType.BODY_ARMOR, EquipmentSlotType.HELMET, EquipmentSlotType.FACE_MASK
	};
	private static final String REPAIR_POINTS_KEY = "TarkovRepairPoints";
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;
	private final Tier tier;

	public TarkovArmorRepairItem(Settings settings, Tier tier, ItemSize storedItemSize, ItemRarityTier rarityTier, float weightKg) {
		super(settings.maxCount(1));
		this.tier = tier;
		this.storedItemSize = storedItemSize;
		this.rarityTier = rarityTier;
		this.weightKg = Math.max(0.0f, weightKg);
	}

	public int getRepairAmount() {
		return tier.repairPoints;
	}

	public int getMaxArmorClass() {
		return tier.maxArmorClass;
	}

	private int getRemainingPoints(ItemStack stack) {
		if (stack.getNbt() == null || !stack.getNbt().contains(REPAIR_POINTS_KEY)) return tier.repairPoints;
		return Math.max(0, Math.min(tier.repairPoints, stack.getNbt().getInt(REPAIR_POINTS_KEY)));
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
		if (world.isClient) return TypedActionResult.success(stack, true);
		if (user instanceof ServerPlayerEntity player
			&& start(player, () -> player.getStackInHand(hand), result -> player.setStackInHand(hand, result))) {
			return TypedActionResult.success(stack, false);
		}
		return TypedActionResult.fail(stack);
	}

	public static boolean start(ServerPlayerEntity player, Supplier<ItemStack> source, Consumer<ItemStack> replace) {
		if (!player.isAlive() || player.isSpectator() || PENDING.containsKey(player.getUuid())
			|| !(player instanceof PlayerLoadoutHolder holder) || holder.tarkovcraft$getBodyHealth().isMedUseActive()) return false;
		ItemStack kit = source.get();
		if (!(kit.getItem() instanceof TarkovArmorRepairItem repair) || repair.getRemainingPoints(kit) == 0) return false;
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		for (EquipmentSlotType slot : REPAIR_PRIORITY) {
			ItemStack armor = loadout.getEquipment(slot);
			if (!(armor.getItem() instanceof TarkovArmorItem piece) || !armor.isDamageable()
				|| piece.getArmorClass() > repair.tier.maxArmorClass
				|| TarkovArmorItem.getRemainingDurability(armor) >= TarkovArmorItem.getEffectiveMaxDurability(armor)) continue;
			PENDING.put(player.getUuid(), new Pending(player, player.getServer().getTicks(), repair.tier.durationTicks,
				kit.copy(), slot, armor, source, replace));
			holder.tarkovcraft$getBodyHealth().setActiveMedUse(Registries.ITEM.getId(kit.getItem()).toString(),
				0, repair.tier.durationTicks, null);
			holder.tarkovcraft$syncBodyHealth();
			return true;
		}
		player.sendMessage(Text.translatable("message.tarkovcraft.repair_not_needed"), true);
		return false;
	}

	public static void tickPlayer(ServerPlayerEntity player) {
		Pending pending = PENDING.get(player.getUuid());
		if (pending == null || !(player instanceof PlayerLoadoutHolder holder)) return;
		ItemStack kit = player == pending.player() && player.isAlive() ? pending.source().get() : ItemStack.EMPTY;
		ItemStack armor = holder.tarkovcraft$getLoadout().getEquipment(pending.slot());
		if (player != pending.player() || !player.isAlive() || player.isSpectator() || player.isSprinting()
			|| armor != pending.target() || !ItemStack.areEqual(kit, pending.expectedKit())) {
			finishTimer(player, holder);
			player.sendMessage(Text.translatable("message.tarkovcraft.repair_cancelled"), true);
			return;
		}
		int elapsed = player.getServer().getTicks() - pending.startTick();
		if (elapsed < pending.duration()) {
			holder.tarkovcraft$getBodyHealth().setActiveMedUse(Registries.ITEM.getId(kit.getItem()).toString(),
				elapsed, pending.duration(), null);
			if (elapsed % 2 == 0) holder.tarkovcraft$syncBodyHealth();
			return;
		}
		finishTimer(player, holder);
		TarkovArmorRepairItem repair = (TarkovArmorRepairItem) kit.getItem();
		int previous = TarkovArmorItem.getRemainingDurability(armor);
		int available = repair.getRemainingPoints(kit);
		int spent = TarkovArmorItem.repair(armor, available, repair.tier.maxDurabilityLossRatio);
		if (spent == 0) return;
		if (!player.isCreative()) {
			ItemStack updated = spent == available ? ItemStack.EMPTY : kit.copy();
			if (!updated.isEmpty()) updated.getOrCreateNbt().putInt(REPAIR_POINTS_KEY, available - spent);
			pending.replace().accept(updated);
		}
		if (player.currentScreenHandler instanceof TarkovScreenHandler handler) {
			handler.syncToClient(player);
		} else {
			holder.tarkovcraft$syncLoadout();
		}
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.55f, 1.3f);
		player.sendMessage(Text.translatable("message.tarkovcraft.repair_done",
			TarkovArmorItem.getRemainingDurability(armor) - previous), true);
	}

	private static void finishTimer(ServerPlayerEntity player, PlayerLoadoutHolder holder) {
		PENDING.remove(player.getUuid());
		holder.tarkovcraft$getBodyHealth().clearActiveMedUse();
		holder.tarkovcraft$syncBodyHealth();
	}

	public static void removePlayer(UUID playerId) {
		PENDING.remove(playerId);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.tarkovcraft.repair_points", getRemainingPoints(stack), tier.repairPoints,
			tier.maxArmorClass, Math.round(tier.maxDurabilityLossRatio * 100)).formatted(net.minecraft.util.Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.item_size", storedItemSize.width(), storedItemSize.height()));
	}
}
