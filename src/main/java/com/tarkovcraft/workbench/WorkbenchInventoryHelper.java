package com.tarkovcraft.workbench;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side inventory scanning, atomic resource deduction (Pockets -> Rig -> Backpack),
 * and crafting execution for the Tarkov Workbench.
 */
public final class WorkbenchInventoryHelper {
	public record SyncPayload(
		Map<Item, Integer> itemCounts,
		Map<String, Integer> fmjAmmoCounts,
		List<String> extraCalibers
	) {
	}

	private WorkbenchInventoryHelper() {
	}

	public static SyncPayload collectSyncPayload(ServerPlayerEntity player) {
		if (player == null || !(player instanceof PlayerLoadoutHolder holder)) {
			return new SyncPayload(Map.of(), Map.of(), List.of());
		}
		if (player.isCreative()) {
			// Scan creative vanilla inventory for any extra custom gunpack calibers
			Set<String> extras = new LinkedHashSet<>();
			for (ItemStack st : player.getInventory().main) {
				collectExtraCaliber(st, extras);
			}
			for (ItemStack st : player.getInventory().offHand) {
				collectExtraCaliber(st, extras);
			}
			return new SyncPayload(Map.of(), Map.of(), List.copyOf(extras));
		}
		return collectSyncPayload(holder.tarkovcraft$getLoadout(), player.getUuid());
	}

	public static SyncPayload collectSyncPayload(PlayerLoadout loadout, @Nullable UUID playerId) {
		Map<Item, Integer> items = new LinkedHashMap<>();
		Map<String, Integer> fmj = new LinkedHashMap<>();
		Set<String> extras = new LinkedHashSet<>();

		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			tallyStack(loadout.getPocket(i), items, fmj, extras);
		}

		ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem && isContainerAccessible(rig, playerId)) {
			for (GridContainer g : ContainerItemHelper.readRigGrids(rig)) {
				for (GridEntry entry : g.getEntries()) {
					tallyStack(entry.getStack(), items, fmj, extras);
				}
			}
		}

		ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
		if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem && isContainerAccessible(bp, playerId)) {
			GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
			for (GridEntry entry : bpGrid.getEntries()) {
				tallyStack(entry.getStack(), items, fmj, extras);
			}
		}

		return new SyncPayload(items, fmj, List.copyOf(extras));
	}

	private static void tallyStack(
		ItemStack stack,
		Map<Item, Integer> items,
		Map<String, Integer> fmj,
		Set<String> extras
	) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (AmmoTier.isTaczAmmoStack(stack)) {
			String ammoId = AmmoTier.getAmmoId(stack);
			if (TaCZCaliberCatalog.isBallisticAmmoId(ammoId)) {
				collectExtraCaliber(stack, extras);
				if (AmmoTier.getFromAmmoStack(stack) == AmmoTier.FMJ) {
					fmj.merge(ammoId, stack.getCount(), Integer::sum);
				}
			}
			return;
		}
		items.merge(stack.getItem(), stack.getCount(), Integer::sum);
	}

	private static void collectExtraCaliber(ItemStack stack, Set<String> extras) {
		if (stack == null || stack.isEmpty() || !AmmoTier.isTaczAmmoStack(stack)) {
			return;
		}
		String ammoId = AmmoTier.getAmmoId(stack);
		if (TaCZCaliberCatalog.isBallisticAmmoId(ammoId)) {
			boolean isDefault = TaCZCaliberCatalog.getDefaultCalibers().stream()
				.anyMatch(c -> c.ammoId().equals(ammoId));
			if (!isDefault) {
				extras.add(ammoId);
			}
		}
	}

	public static boolean hasMaterials(
		PlayerLoadout loadout,
		@Nullable UUID playerId,
		List<TarkovWorkbenchRecipes.Ingredient> materials,
		@Nullable String requiredFmjAmmoId,
		int requiredFmjCount
	) {
		SyncPayload payload = collectSyncPayload(loadout, playerId);
		for (TarkovWorkbenchRecipes.Ingredient ing : materials) {
			if (payload.itemCounts().getOrDefault(ing.item(), 0) < ing.count()) {
				return false;
			}
		}
		if (requiredFmjCount > 0 && requiredFmjAmmoId != null) {
			if (payload.fmjAmmoCounts().getOrDefault(requiredFmjAmmoId, 0) < requiredFmjCount) {
				return false;
			}
		}
		return true;
	}

	public static boolean consumeMaterials(
		PlayerLoadout loadout,
		@Nullable UUID playerId,
		List<TarkovWorkbenchRecipes.Ingredient> materials,
		@Nullable String requiredFmjAmmoId,
		int requiredFmjCount
	) {
		if (!hasMaterials(loadout, playerId, materials, requiredFmjAmmoId, requiredFmjCount)) {
			return false;
		}

		for (TarkovWorkbenchRecipes.Ingredient ing : materials) {
			consumeFromLoadout(loadout, playerId, ing.item(), null, ing.count());
		}
		if (requiredFmjCount > 0 && requiredFmjAmmoId != null) {
			consumeFromLoadout(loadout, playerId, null, requiredFmjAmmoId, requiredFmjCount);
		}
		return true;
	}

	private static void consumeFromLoadout(
		PlayerLoadout loadout,
		@Nullable UUID playerId,
		@Nullable Item targetItem,
		@Nullable String targetFmjAmmoId,
		int amount
	) {
		int remaining = amount;

		// 1. Pockets
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT && remaining > 0; i++) {
			ItemStack p = loadout.getPocket(i);
			if (matchesConsumptionTarget(p, targetItem, targetFmjAmmoId)) {
				int take = Math.min(remaining, p.getCount());
				p.decrement(take);
				remaining -= take;
				if (p.isEmpty()) {
					loadout.setPocket(i, ItemStack.EMPTY);
				}
			}
		}

		// 2. Equipped Rig
		if (remaining > 0) {
			ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
			if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem && isContainerAccessible(rig, playerId)) {
				List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
				boolean modified = false;
				for (GridContainer g : grids) {
					List<GridEntry> entries = new ArrayList<>(g.getEntries());
					for (GridEntry entry : entries) {
						if (remaining <= 0) break;
						ItemStack st = entry.getStack();
						if (matchesConsumptionTarget(st, targetItem, targetFmjAmmoId)) {
							int take = Math.min(remaining, st.getCount());
							st.decrement(take);
							remaining -= take;
							if (st.isEmpty()) {
								g.remove(entry.getEntryId());
							}
							modified = true;
						}
					}
					if (remaining <= 0) break;
				}
				if (modified) {
					ContainerItemHelper.writeRigGrids(rig, grids);
				}
			}
		}

		// 3. Equipped Backpack
		if (remaining > 0) {
			ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
			if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem && isContainerAccessible(bp, playerId)) {
				GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
				boolean modified = false;
				List<GridEntry> entries = new ArrayList<>(bpGrid.getEntries());
				for (GridEntry entry : entries) {
					if (remaining <= 0) break;
					ItemStack st = entry.getStack();
					if (matchesConsumptionTarget(st, targetItem, targetFmjAmmoId)) {
						int take = Math.min(remaining, st.getCount());
						st.decrement(take);
						remaining -= take;
						if (st.isEmpty()) {
							bpGrid.remove(entry.getEntryId());
						}
						modified = true;
					}
				}
				if (modified) {
					ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
				}
			}
		}
		if (remaining != amount) {
			loadout.invalidateExposedCache();
		}
	}

	private static boolean matchesConsumptionTarget(
		ItemStack stack,
		@Nullable Item targetItem,
		@Nullable String targetFmjAmmoId
	) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (targetFmjAmmoId != null) {
			return AmmoTier.isTaczAmmoStack(stack)
				&& targetFmjAmmoId.equals(AmmoTier.getAmmoId(stack))
				&& AmmoTier.getFromAmmoStack(stack) == AmmoTier.FMJ;
		}
		return targetItem != null && !AmmoTier.isTaczAmmoStack(stack) && stack.getItem() == targetItem;
	}

	private static boolean isContainerAccessible(ItemStack container, @Nullable UUID playerId) {
		return !ContainerItemHelper.requiresSearch(container)
			|| (playerId != null && ContainerItemHelper.getSearchMemory(container).isGridRevealed(playerId));
	}

	public static boolean craftAmmo(ServerPlayerEntity player, String ammoId, AmmoTier tier, boolean upgradeFromFmj) {
		if (!TaCZCaliberCatalog.isBallisticAmmoId(ammoId) || tier == null || !TaCZCaliberCatalog.isTierAllowed(ammoId, tier)) {
			return false;
		}
		TarkovWorkbenchRecipes.AmmoRecipe recipe = TarkovWorkbenchRecipes.getAmmoRecipe(ammoId, tier, upgradeFromFmj);
		ItemStack result = AmmoTier.createTaczAmmoStack(recipe.ammoId(), recipe.tier(), recipe.batchSize());

		if (!executeCraftTransaction(player, recipe.materials(), recipe.upgradeFromFmj() ? recipe.ammoId() : null, recipe.requiredFmjCount(), result)) {
			return false;
		}

		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.7f, 1.15f);
		return true;
	}

	public static boolean craftGear(ServerPlayerEntity player, Item gearItem) {
		if (!TarkovWorkbenchRecipes.isCraftableGear(gearItem)) {
			return false;
		}
		List<TarkovWorkbenchRecipes.Ingredient> mats = TarkovWorkbenchRecipes.computeGearIngredients(gearItem);
		ItemStack result = new ItemStack(gearItem);

		if (!executeCraftTransaction(player, mats, null, 0, result)) {
			return false;
		}

		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.6f, 1.2f);
		return true;
	}

	public static boolean giveOrDrop(ServerPlayerEntity player, PlayerLoadout loadout, ItemStack crafted) {
		ItemStack toInsert = crafted.copy();
		loadout.tryInsertItem(toInsert, player.getUuid());
		boolean droppedAny = !toInsert.isEmpty();
		if (droppedAny) {
			player.dropItem(toInsert, false);
		}
		return droppedAny;
	}

	private static boolean executeCraftTransaction(
		ServerPlayerEntity player,
		List<TarkovWorkbenchRecipes.Ingredient> materials,
		@Nullable String requiredFmjAmmoId,
		int requiredFmjCount,
		ItemStack result
	) {
		if (player.isCreative()) {
			ItemStack creativeCopy = result.copy();
			boolean inserted = player.getInventory().insertStack(creativeCopy) && creativeCopy.isEmpty();
			if (!inserted && !creativeCopy.isEmpty()) {
				player.dropItem(creativeCopy, false);
			}
			if (inserted) {
				player.sendMessage(Text.translatable("message.tarkovcraft.workbench_crafted", result.getName()), true);
			} else {
				Text warn = Text.translatable("message.tarkovcraft.wb_crafted_dropped", result.getName())
					.formatted(Formatting.YELLOW);
				player.sendMessage(warn, false);
				player.sendMessage(warn, true);
			}
			return true;
		}

		if (!(player instanceof PlayerLoadoutHolder holder)) {
			return false;
		}
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		if (!consumeMaterials(loadout, player.getUuid(), materials, requiredFmjAmmoId, requiredFmjCount)) {
			player.sendMessage(Text.translatable("message.tarkovcraft.workbench_no_materials"), true);
			return false;
		}

		boolean droppedAny = giveOrDrop(player, loadout, result);
		holder.tarkovcraft$syncLoadout();

		if (!droppedAny) {
			player.sendMessage(Text.translatable("message.tarkovcraft.workbench_crafted", result.getName()), true);
		} else {
			Text warn = Text.translatable("message.tarkovcraft.wb_crafted_dropped", result.getName())
				.formatted(Formatting.YELLOW);
			player.sendMessage(warn, false);
			player.sendMessage(warn, true);
		}
		return true;
	}
}
