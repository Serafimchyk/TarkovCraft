package com.tarkovcraft.health;

import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.MedicalType;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovStimulatorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.network.TarkovNetworking;
import com.tarkovcraft.screen.TarkovScreenHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative timer for applying EFT medical items and stimulators.
 * Prevents instant healing and double-use, verifies source presence each tick,
 * consumes charges only on completion, and syncs progress via BodyPartHealth.
 */
public final class MedicalUseManager {
	private static final Map<UUID, ActiveMedUse> ACTIVE = new ConcurrentHashMap<>();

	private static final class ActiveMedUse {
		final boolean fromHand;
		@Nullable
		final Hand hand;
		final byte srcLoc;
		final int srcIdx;
		@Nullable
		final UUID srcEntryId;
		final Item expectedItem;
		@Nullable
		final BodyPart targetPart;
		final boolean isStimulator;
		final int totalTicks;
		int elapsedTicks = 0;

		ActiveMedUse(
			boolean fromHand,
			@Nullable Hand hand,
			byte srcLoc,
			int srcIdx,
			@Nullable UUID srcEntryId,
			Item expectedItem,
			@Nullable BodyPart targetPart,
			boolean isStimulator,
			int totalTicks
		) {
			this.fromHand = fromHand;
			this.hand = hand;
			this.srcLoc = srcLoc;
			this.srcIdx = srcIdx;
			this.srcEntryId = srcEntryId;
			this.expectedItem = expectedItem;
			this.targetPart = targetPart;
			this.isStimulator = isStimulator;
			this.totalTicks = Math.max(1, totalTicks);
		}
	}

	private MedicalUseManager() {
	}

	public static boolean isUsing(UUID playerId) {
		return playerId != null && ACTIVE.containsKey(playerId);
	}

	public static void interruptForCombat(ServerPlayerEntity player) {
		if (isUsing(player.getUuid()) && player instanceof PlayerLoadoutHolder holder) {
			cancelInternal(player, holder.tarkovcraft$getBodyHealth(), holder, true);
		}
	}

	public static void removePlayer(UUID playerId) {
		if (playerId != null) {
			ACTIVE.remove(playerId);
		}
	}

	public static boolean startFromHand(ServerPlayerEntity player, Hand hand, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return false;
		}
		return startInternal(player, true, hand, (byte) -1, -1, null, stack, null);
	}

	public static boolean startFromInventory(
		ServerPlayerEntity player,
		byte srcLoc,
		int srcIdx,
		@Nullable UUID srcEntryId,
		@Nullable BodyPart targetPart
	) {
		if (player == null || !(player.currentScreenHandler instanceof TarkovScreenHandler tsh)) {
			return false;
		}
		ItemStack stack = tsh.peekSourceForMed(player, srcLoc, srcIdx, srcEntryId);
		if (stack.isEmpty()) {
			return false;
		}
		return startInternal(player, false, null, srcLoc, srcIdx, srcEntryId, stack, targetPart);
	}

	private static boolean startInternal(
		ServerPlayerEntity player,
		boolean fromHand,
		@Nullable Hand hand,
		byte srcLoc,
		int srcIdx,
		@Nullable UUID srcEntryId,
		ItemStack stack,
		@Nullable BodyPart targetPart
	) {
		if (player.isSpectator() || !player.isAlive() || !(player instanceof PlayerLoadoutHolder holder)) {
			return false;
		}
		UUID playerId = player.getUuid();
		if (ACTIVE.containsKey(playerId)) {
			player.sendMessage(Text.translatable("message.tarkovcraft.med_busy"), true);
			return false;
		}

		Item item = stack.getItem();
		if (player.getItemCooldownManager().isCoolingDown(item)) {
			return false;
		}

		BodyPartHealth health = holder.tarkovcraft$getBodyHealth();
		boolean isStim = item instanceof TarkovStimulatorItem;
		int durationTicks;
		if (item instanceof TarkovMedicalItem med) {
			MedicalType type = med.getMedicalType();
			boolean validTarget = targetPart != null
				? MedicalHelper.canApplyToPart(health, stack, targetPart)
				: MedicalHelper.selectAutoTarget(health, stack) != null;
			if (!validTarget) {
				player.sendMessage(Text.translatable(
					targetPart != null ? "message.tarkovcraft.med_invalid_target" : "message.tarkovcraft.med_not_needed"
				), true);
				return false;
			}
			durationTicks = type.getUseDurationTicks();
		} else if (isStim) {
			durationTicks = TarkovStimulatorItem.USE_DURATION_TICKS;
		} else {
			return false;
		}

		ActiveMedUse use = new ActiveMedUse(
			fromHand, hand, srcLoc, srcIdx, srcEntryId,
			item, targetPart, isStim, durationTicks
		);
		ACTIVE.put(playerId, use);
		health.setActiveMedUse(Registries.ITEM.getId(item).toString(), 0, durationTicks, targetPart);
		holder.tarkovcraft$syncBodyHealth();

		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.45f, 1.2f);
		return true;
	}

	public static void tickPlayer(ServerPlayerEntity player) {
		if (player == null) {
			return;
		}
		UUID playerId = player.getUuid();
		ActiveMedUse active = ACTIVE.get(playerId);
		if (active == null) {
			return;
		}
		if (!(player instanceof PlayerLoadoutHolder holder)) {
			ACTIVE.remove(playerId);
			return;
		}
		BodyPartHealth health = holder.tarkovcraft$getBodyHealth();
		if (!player.isAlive() || player.isSpectator()) {
			cancelInternal(player, health, holder, false);
			return;
		}
		if (player.isSprinting()) {
			cancelInternal(player, health, holder, true);
			return;
		}

		ItemStack current = resolveCurrentStack(player, holder.tarkovcraft$getLoadout(), active);
		if (current.isEmpty() || current.getItem() != active.expectedItem) {
			cancelInternal(player, health, holder, true);
			return;
		}

		active.elapsedTicks++;
		health.setActiveMedUse(
			Registries.ITEM.getId(active.expectedItem).toString(),
			active.elapsedTicks,
			active.totalTicks,
			active.targetPart
		);

		if (active.elapsedTicks >= active.totalTicks) {
			completeInternal(player, holder, health, active);
			return;
		}

		if (active.elapsedTicks % 2 == 0) {
			holder.tarkovcraft$syncBodyHealth();
		}
	}

	private static void cancelInternal(
		ServerPlayerEntity player,
		BodyPartHealth health,
		PlayerLoadoutHolder holder,
		boolean notify
	) {
		ACTIVE.remove(player.getUuid());
		health.clearActiveMedUse();
		holder.tarkovcraft$syncBodyHealth();
		if (notify) {
			player.sendMessage(Text.translatable("message.tarkovcraft.med_cancelled"), true);
		}
	}

	private static void completeInternal(
		ServerPlayerEntity player,
		PlayerLoadoutHolder holder,
		BodyPartHealth health,
		ActiveMedUse active
	) {
		ACTIVE.remove(player.getUuid());
		health.clearActiveMedUse();

		if (active.fromHand && active.hand != null) {
			ItemStack handStack = player.getStackInHand(active.hand);
			if (!handStack.isEmpty() && handStack.getItem() == active.expectedItem) {
				if (active.isStimulator) {
					TarkovStimulatorItem.applyStimulator(player, handStack);
					if (handStack.isEmpty() && !player.isCreative()) {
						player.setStackInHand(active.hand, ItemStack.EMPTY);
					}
				} else if (handStack.getItem() instanceof TarkovMedicalItem med) {
					MedicalHelper.ApplyResult res = MedicalHelper.applyMedical(health, handStack, active.targetPart, player.isCreative());
					if (res.applied()) {
						if (res.exhaustionToAdd() > 0.0f) {
							player.addExhaustion(res.exhaustionToAdd());
						}
						player.getItemCooldownManager().set(med, med.getMedicalType().getCooldownTicks());
						if (handStack.isEmpty() && !player.isCreative()) {
							player.setStackInHand(active.hand, ItemStack.EMPTY);
						}
						player.setHealth(Math.max(1.0f, health.getTotalRatio() * player.getMaxHealth()));
						MedicalHelper.playMedicalSound(player, med.getMedicalType());
					}
				}
			}
			holder.tarkovcraft$syncBodyHealth();
			holder.tarkovcraft$syncLoadout();
			if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
				tsh.syncToClient(player);
			}
			return;
		}

		// Inventory/container source
		if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
			tsh.completeTimedMedUse(player, active.srcLoc, active.srcIdx, active.srcEntryId, active.targetPart, active.isStimulator);
			return;
		}

		// Player closed inventory mid-heal; own storage (Pocket/Rig/Backpack) still completes safely
		completeInOwnLoadout(player, holder, health, active);
		holder.tarkovcraft$syncBodyHealth();
		holder.tarkovcraft$syncLoadout();
	}

	private static void completeInOwnLoadout(
		ServerPlayerEntity player,
		PlayerLoadoutHolder holder,
		BodyPartHealth health,
		ActiveMedUse active
	) {
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		UUID playerId = player.getUuid();
		boolean creative = player.isCreative();

		if (active.srcLoc == TarkovNetworking.LOC_POCKET) {
			ItemStack st = loadout.getPocket(active.srcIdx);
			if (st.isEmpty() || st.getItem() != active.expectedItem) return;
			applyOnStack(player, health, st, active, creative);
			if (st.isEmpty()) loadout.setPocket(active.srcIdx, ItemStack.EMPTY);
			loadout.invalidateExposedCache();
			return;
		}
		if (active.srcLoc == TarkovNetworking.LOC_RIG_GRID && active.srcEntryId != null) {
			ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
			if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem)) return;
			List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
			if (active.srcIdx < 0 || active.srcIdx >= grids.size()) return;
			GridEntry entry = grids.get(active.srcIdx).getEntry(active.srcEntryId);
			if (entry == null || entry.getStack().getItem() != active.expectedItem) return;
			if (applyOnStack(player, health, entry.getStack(), active, creative)) {
				if (entry.getStack().isEmpty()) {
					grids.get(active.srcIdx).remove(active.srcEntryId);
				}
				ContainerItemHelper.writeRigGrids(rig, grids);
				loadout.invalidateExposedCache();
			}
			return;
		}
		if (active.srcLoc == TarkovNetworking.LOC_BACKPACK_GRID && active.srcEntryId != null) {
			ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
			if (bp.isEmpty() || !(bp.getItem() instanceof BackpackItem)) return;
			GridContainer grid = ContainerItemHelper.readBackpackGrid(bp);
			GridEntry entry = grid.getEntry(active.srcEntryId);
			if (entry == null || entry.getStack().getItem() != active.expectedItem) return;
			if (applyOnStack(player, health, entry.getStack(), active, creative)) {
				if (entry.getStack().isEmpty()) {
					grid.remove(active.srcEntryId);
				}
				ContainerItemHelper.writeBackpackGrid(bp, grid);
				loadout.invalidateExposedCache();
			}
		}
	}

	private static boolean applyOnStack(
		ServerPlayerEntity player,
		BodyPartHealth health,
		ItemStack st,
		ActiveMedUse active,
		boolean creative
	) {
		if (active.isStimulator) {
			return TarkovStimulatorItem.applyStimulator(player, st);
		}
		if (st.getItem() instanceof TarkovMedicalItem med) {
			MedicalHelper.ApplyResult res = MedicalHelper.applyMedical(health, st, active.targetPart, creative);
			if (res.applied()) {
				if (res.exhaustionToAdd() > 0.0f) {
					player.addExhaustion(res.exhaustionToAdd());
				}
				player.getItemCooldownManager().set(med, med.getMedicalType().getCooldownTicks());
				player.setHealth(Math.max(1.0f, health.getTotalRatio() * player.getMaxHealth()));
				MedicalHelper.playMedicalSound(player, med.getMedicalType());
				return true;
			}
		}
		return false;
	}

	private static ItemStack resolveCurrentStack(
		ServerPlayerEntity player,
		PlayerLoadout loadout,
		ActiveMedUse active
	) {
		if (active.fromHand && active.hand != null) {
			return player.getStackInHand(active.hand);
		}
		if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
			return tsh.peekSourceForMed(player, active.srcLoc, active.srcIdx, active.srcEntryId);
		}
		return switch (active.srcLoc) {
			case TarkovNetworking.LOC_POCKET -> loadout.getPocket(active.srcIdx);
			case TarkovNetworking.LOC_RIG_GRID -> {
				if (active.srcEntryId == null) yield ItemStack.EMPTY;
				ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
				if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem)) yield ItemStack.EMPTY;
				List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
				if (active.srcIdx < 0 || active.srcIdx >= grids.size()) yield ItemStack.EMPTY;
				GridEntry entry = grids.get(active.srcIdx).getEntry(active.srcEntryId);
				yield entry != null ? entry.getStack() : ItemStack.EMPTY;
			}
			case TarkovNetworking.LOC_BACKPACK_GRID -> {
				if (active.srcEntryId == null) yield ItemStack.EMPTY;
				ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
				if (bp.isEmpty() || !(bp.getItem() instanceof BackpackItem)) yield ItemStack.EMPTY;
				GridEntry entry = ContainerItemHelper.readBackpackGrid(bp).getEntry(active.srcEntryId);
				yield entry != null ? entry.getStack() : ItemStack.EMPTY;
			}
			default -> ItemStack.EMPTY;
		};
	}
}
