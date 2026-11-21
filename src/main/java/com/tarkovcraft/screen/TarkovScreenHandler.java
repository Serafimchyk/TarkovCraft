package com.tarkovcraft.screen;

import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.MedicalHelper;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorRepairItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovStimulatorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.network.TarkovNetworking;
import com.tarkovcraft.registry.ModScreenHandlers;
import com.tarkovcraft.search.ContainerSearchMemory;
import com.tarkovcraft.search.SearchSession;
import com.tarkovcraft.search.SearchTarget;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.potion.PotionUtil;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TarkovScreenHandler extends ScreenHandler {
	public static final byte MODE_OWN_INVENTORY = 0;
	public static final byte MODE_LOOT_BLOCK = 1;
	public static final byte MODE_CORPSE = 2;
	public static final byte MODE_GROUND_ITEM = 3;

	private final PlayerEntity player;
	private final byte mode;
	@Nullable
	private final SearchableLootBlockEntity lootBlock;
	@Nullable
	private final CorpseEntity corpse;
	@Nullable
	private final ItemEntity groundItem;
	private final SearchSession searchSession;
	private int syncCooldown = 0;
	@Nullable
	private UUID activeCaseUuid = null;

	private TarkovScreenHandler(
		int syncId,
		PlayerInventory playerInventory,
		byte mode,
		@Nullable SearchableLootBlockEntity lootBlock,
		@Nullable CorpseEntity corpse,
		@Nullable ItemEntity groundItem
	) {
		super(ModScreenHandlers.TARKOV_SCREEN_HANDLER, syncId);
		this.player = playerInventory.player;
		this.mode = mode;
		this.lootBlock = lootBlock;
		this.corpse = corpse;
		this.groundItem = groundItem;
		this.searchSession = new SearchSession(player.getUuid());
	}

	public static TarkovScreenHandler forOwnInventory(int syncId, PlayerInventory inv) {
		return new TarkovScreenHandler(syncId, inv, MODE_OWN_INVENTORY, null, null, null);
	}

	public static TarkovScreenHandler forLootBlock(int syncId, PlayerInventory inv, SearchableLootBlockEntity block) {
		return new TarkovScreenHandler(syncId, inv, MODE_LOOT_BLOCK, block, null, null);
	}

	public static TarkovScreenHandler forCorpse(int syncId, PlayerInventory inv, CorpseEntity corpse) {
		return new TarkovScreenHandler(syncId, inv, MODE_CORPSE, null, corpse, null);
	}

	public static TarkovScreenHandler forGroundItem(int syncId, PlayerInventory inv, ItemEntity groundItem) {
		return new TarkovScreenHandler(syncId, inv, MODE_GROUND_ITEM, null, null, groundItem);
	}

	public static TarkovScreenHandler fromClientBuf(int syncId, PlayerInventory inv, PacketByteBuf buf) {
		byte mode = buf.readByte();
		if (mode == MODE_LOOT_BLOCK) {
			BlockPos pos = buf.readBlockPos();
			BlockEntity be = inv.player.getWorld().getBlockEntity(pos);
			return new TarkovScreenHandler(syncId, inv, mode, be instanceof SearchableLootBlockEntity lbe ? lbe : null, null, null);
		}
		if (mode == MODE_CORPSE) {
			int entityId = buf.readInt();
			Entity e = inv.player.getWorld().getEntityById(entityId);
			return new TarkovScreenHandler(syncId, inv, mode, null, e instanceof CorpseEntity ce ? ce : null, null);
		}
		if (mode == MODE_GROUND_ITEM) {
			int entityId = buf.readInt();
			Entity e = inv.player.getWorld().getEntityById(entityId);
			return new TarkovScreenHandler(syncId, inv, mode, null, null, e instanceof ItemEntity ie ? ie : null);
		}
		return new TarkovScreenHandler(syncId, inv, MODE_OWN_INVENTORY, null, null, null);
	}

	public static void openOwnInventory(ServerPlayerEntity player) {
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
				buf.writeByte(MODE_OWN_INVENTORY);
			}

			@Override
			public Text getDisplayName() {
				return Text.translatable("screen.tarkovcraft.inventory");
			}

			@Nullable
			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return forOwnInventory(syncId, inv);
			}
		});
		if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
			tsh.syncToClient(player);
		}
	}

	public static void openCaseScreen(ServerPlayerEntity player, UUID caseUuid) {
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
				buf.writeByte(MODE_OWN_INVENTORY);
			}

			@Override
			public Text getDisplayName() {
				return Text.translatable("screen.tarkovcraft.inventory");
			}

			@Nullable
			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return forOwnInventory(syncId, inv);
			}
		});
		if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
			tsh.activeCaseUuid = caseUuid;
			tsh.syncToClient(player);
		}
	}

	public static void openGroundContainer(ServerPlayerEntity player, ItemEntity groundItem) {
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
				buf.writeByte(MODE_GROUND_ITEM);
				buf.writeInt(groundItem.getId());
			}

			@Override
			public Text getDisplayName() {
				return groundItem.getStack().getName();
			}

			@Nullable
			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return forGroundItem(syncId, inv, groundItem);
			}
		});
		if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
			tsh.syncToClient(player);
		}
	}

	public byte getMode() {
		return mode;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		if (mode == MODE_LOOT_BLOCK) {
			return lootBlock != null && !lootBlock.isRemoved()
				&& player.squaredDistanceTo(lootBlock.getPos().toCenterPos()) <= 36.0;
		}
		if (mode == MODE_CORPSE) {
			return corpse != null && !corpse.isRemoved() && player.squaredDistanceTo(corpse) <= 36.0;
		}
		if (mode == MODE_GROUND_ITEM) {
			return groundItem != null && !groundItem.isRemoved() && !groundItem.getStack().isEmpty()
				&& player.squaredDistanceTo(groundItem) <= 25.0;
		}
		return true;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		searchSession.reset();
		activeCaseUuid = null;
	}

	public void tickServer(ServerPlayerEntity serverPlayer) {
		if (!canUse(serverPlayer)) {
			serverPlayer.closeHandledScreen();
			return;
		}

		List<SearchTarget> externalTargets = getExternalSearchTargets();
		List<SearchTarget> ownTargets = getOwnSearchTargets(serverPlayer);

		boolean stateChanged = searchSession.tickQueue(true, externalTargets, ownTargets);

		if (mode == MODE_OWN_INVENTORY && activeCaseUuid != null && resolveActiveCaseHandle() == null) {
			stateChanged = true;
		}

		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);
		for (SearchTarget ownT : ownTargets) {
			if (ownT.isFullySearched(serverPlayer.getUuid())) {
				clearRequiresSearchIfMatches(loadout.getEquipment(EquipmentSlotType.RIG), ownT.getTargetId());
				clearRequiresSearchIfMatches(loadout.getEquipment(EquipmentSlotType.BACKPACK), ownT.getTargetId());
				stateChanged = true;
			}
		}

		syncCooldown++;
		if (stateChanged || (searchSession.getStage() != SearchSession.Stage.IDLE && syncCooldown >= 2) || syncCooldown >= 10) {
			syncCooldown = 0;
			syncToClient(serverPlayer);
		}
	}

	private void clearRequiresSearchIfMatches(ItemStack stack, UUID targetId) {
		if (!stack.isEmpty() && targetId.equals(ContainerItemHelper.getContainerUuid(stack)) && ContainerItemHelper.requiresSearch(stack)) {
			ContainerItemHelper.setRequiresSearch(stack, false);
		}
	}

	private List<SearchTarget> getExternalSearchTargets() {
		if (mode == MODE_LOOT_BLOCK && lootBlock != null) {
			return List.of(lootBlock.asSearchTarget());
		}
		if (mode == MODE_CORPSE && corpse != null) {
			return corpse.buildCorpseSearchTargets();
		}
		if (mode == MODE_GROUND_ITEM && groundItem != null && !groundItem.getStack().isEmpty()) {
			ItemStack st = groundItem.getStack();
			if (ContainerItemHelper.requiresSearch(st)) {
				UUID id = ContainerItemHelper.getOrCreateContainerUuid(st);
				List<GridContainer> grids = ContainerItemHelper.readAllGrids(st);
				ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(st);
				return List.of(new SearchTarget(id, grids, mem, () -> ContainerItemHelper.saveSearchMemory(st, mem)));
			}
		}
		return List.of();
	}

	private List<SearchTarget> getOwnSearchTargets(ServerPlayerEntity serverPlayer) {
		List<SearchTarget> targets = new ArrayList<>();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);

		ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem && ContainerItemHelper.requiresSearch(rig)) {
			UUID rigId = ContainerItemHelper.getOrCreateContainerUuid(rig);
			List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rig);
			ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(rig);
			targets.add(new SearchTarget(rigId, rigGrids, mem, () -> ContainerItemHelper.saveSearchMemory(rig, mem)));
		}

		ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
		if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem && ContainerItemHelper.requiresSearch(bp)) {
			UUID bpId = ContainerItemHelper.getOrCreateContainerUuid(bp);
			GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
			ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(bp);
			targets.add(new SearchTarget(bpId, List.of(bpGrid), mem, () -> ContainerItemHelper.saveSearchMemory(bp, mem)));
		}

		return targets;
	}

	public void syncToClient(ServerPlayerEntity serverPlayer) {
		UUID playerId = serverPlayer.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);
		TarkovNetworking.sendLoadoutSync(serverPlayer, loadout);

		List<TarkovNetworking.GridView> ownRigViews = buildRigViews(loadout.getEquipment(EquipmentSlotType.RIG), playerId);
		TarkovNetworking.GridView ownBpView = buildBackpackView(loadout.getEquipment(EquipmentSlotType.BACKPACK), playerId);

		TarkovNetworking.GridView extView = null;
		String extTitle = "";
		PlayerLoadout corpseLoadout = null;
		List<TarkovNetworking.GridView> corpseRigViews = List.of();
		TarkovNetworking.GridView corpseBpView = null;

		if (mode == MODE_LOOT_BLOCK && lootBlock != null) {
			extTitle = lootBlock.getDisplayTitle().getString();
			extView = TarkovNetworking.GridView.fromContainer(
				lootBlock.getGrid(), lootBlock.getGrid().getContainerId(),
				lootBlock.getSearchMemory(), playerId, true
			);
		} else if (mode == MODE_CORPSE && corpse != null) {
			extTitle = corpse.getOwnerName();
			extView = TarkovNetworking.GridView.fromContainer(
				corpse.getPocketsGrid(), corpse.getPocketsGrid().getContainerId(),
				corpse.getPocketsSearchMemory(), playerId, true
			);
			corpseLoadout = corpse.getLoadout();
			corpseRigViews = buildRigViews(corpseLoadout.getEquipment(EquipmentSlotType.RIG), playerId);
			corpseBpView = buildBackpackView(corpseLoadout.getEquipment(EquipmentSlotType.BACKPACK), playerId);
		} else if (mode == MODE_GROUND_ITEM && groundItem != null && !groundItem.getStack().isEmpty()) {
			ItemStack gStack = groundItem.getStack();
			extTitle = gStack.getName().getString();
			if (gStack.getItem() instanceof BackpackItem) {
				extView = buildBackpackView(gStack, playerId);
			} else if (gStack.getItem() instanceof TarkovCaseItem) {
				extView = buildCaseView(gStack, playerId);
			} else if (gStack.getItem() instanceof TacticalRigItem) {
				corpseRigViews = buildRigViews(gStack, playerId);
			}
		} else if (mode == MODE_OWN_INVENTORY && activeCaseUuid != null) {
			CaseHandle handle = resolveActiveCaseHandle();
			if (handle != null) {
				extTitle = handle.getStack().getName().getString();
				extView = TarkovNetworking.GridView.fromContainer(
					handle.readGrid(), activeCaseUuid, null, playerId, false
				);
			}
		}

		TarkovNetworking.ScreenStateSnapshot snapshot = new TarkovNetworking.ScreenStateSnapshot(
			mode,
			extTitle,
			ownRigViews,
			ownBpView,
			extView,
			corpseLoadout,
			corpseRigViews,
			corpseBpView,
			TarkovNetworking.stageToByte(searchSession.getStage()),
			searchSession.getActiveTargetId(),
			searchSession.getActiveEntryId(),
			searchSession.getProgressRatio()
		);
		TarkovNetworking.sendScreenState(serverPlayer, snapshot);
	}

	private static List<TarkovNetworking.GridView> buildRigViews(ItemStack rig, UUID playerId) {
		List<TarkovNetworking.GridView> out = new ArrayList<>();
		if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem)) {
			return out;
		}
		UUID rigTargetId = ContainerItemHelper.getOrCreateContainerUuid(rig);
		boolean reqSearch = ContainerItemHelper.requiresSearch(rig);
		ContainerSearchMemory mem = reqSearch ? ContainerItemHelper.getSearchMemory(rig) : null;
		for (GridContainer g : ContainerItemHelper.readRigGrids(rig)) {
			out.add(TarkovNetworking.GridView.fromContainer(g, rigTargetId, mem, playerId, reqSearch));
		}
		return out;
	}

	@Nullable
	private static TarkovNetworking.GridView buildBackpackView(ItemStack bp, UUID playerId) {
		if (bp.isEmpty() || !(bp.getItem() instanceof BackpackItem)) {
			return null;
		}
		UUID bpTargetId = ContainerItemHelper.getOrCreateContainerUuid(bp);
		GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
		boolean reqSearch = ContainerItemHelper.requiresSearch(bp);
		ContainerSearchMemory mem = reqSearch ? ContainerItemHelper.getSearchMemory(bp) : null;
		return TarkovNetworking.GridView.fromContainer(bpGrid, bpTargetId, mem, playerId, reqSearch);
	}

	@Nullable
	private static TarkovNetworking.GridView buildCaseView(ItemStack caseStack, UUID playerId) {
		if (caseStack.isEmpty() || !(caseStack.getItem() instanceof TarkovCaseItem)) {
			return null;
		}
		UUID caseTargetId = ContainerItemHelper.getOrCreateContainerUuid(caseStack);
		GridContainer caseGrid = ContainerItemHelper.readCaseGrid(caseStack);
		boolean reqSearch = ContainerItemHelper.requiresSearch(caseStack);
		ContainerSearchMemory mem = reqSearch ? ContainerItemHelper.getSearchMemory(caseStack) : null;
		return TarkovNetworking.GridView.fromContainer(caseGrid, caseTargetId, mem, playerId, reqSearch);
	}

	public static boolean isConsumableItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (stack.getItem() instanceof TarkovMedicalItem
			|| stack.getItem() instanceof TarkovStimulatorItem
			|| stack.getItem() instanceof TarkovArmorRepairItem) {
			return true;
		}
		UseAction action = stack.getUseAction();
		return stack.isFood() || action == UseAction.DRINK || action == UseAction.EAT;
	}

	public static boolean canEquipItem(ItemStack stack) {
		return findFirstMatchingSlot(stack) != null;
	}

	/**
	 * Fast transfer (pressed [F] or Shift+LMB over any item in the inventory UI):
	 * - External / Corpse / Ground -> inserts into own storage (or equips gear if slot empty).
	 * - Own Equipment -> unequips into storage (Pockets -> Rig -> Backpack) if space exists.
	 * - Own Storage -> moves into open Loot Crate / Ground Container, or between Backpack <-> Rig/Pockets (with equip fallback).
	 */
	public void handleQuickTransfer(ServerPlayerEntity serverPlayer, byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		if (!canUse(serverPlayer)) return;
		UUID playerId = serverPlayer.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);

		ItemStack peek = peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId);
		if (peek.isEmpty()) return;

		boolean isExternal = srcLoc == TarkovNetworking.LOC_EXTERNAL_GRID
			|| srcLoc == TarkovNetworking.LOC_CORPSE_EQUIPMENT
			|| srcLoc == TarkovNetworking.LOC_CORPSE_RIG_GRID
			|| srcLoc == TarkovNetworking.LOC_CORPSE_BACKPACK_GRID;

		if (isExternal) {
			ItemStack candidate = peek.copy();
			int before = candidate.getCount();
			boolean isWearableContainer = candidate.getItem() instanceof BackpackItem || candidate.getItem() instanceof TacticalRigItem;
			boolean ok = isWearableContainer
				? loadout.trySmartPickup(candidate, playerId)
				: (loadout.tryInsertItem(candidate, playerId) || loadout.trySmartPickup(candidate, playerId));
			int moved = before - candidate.getCount();
			if (ok || moved > 0) {
				extractSource(loadout, srcLoc, srcIdx, srcEntryId, moved);
				markExternalDirty();
				syncToClient(serverPlayer);
			} else {
				serverPlayer.sendMessage(Text.translatable("message.tarkovcraft.no_space"), true);
			}
			return;
		}

		// From own Equipment -> unequip into Pockets -> Rig -> Backpack (never into itself!)
		if (srcLoc == TarkovNetworking.LOC_EQUIPMENT) {
			unequipToStorage(serverPlayer, loadout, playerId, srcIdx);
			return;
		}

		// From own Storage (Pocket / Rig / Backpack):
		// 1. If looting a crate or ground container, try moving into it
		if (mode == MODE_LOOT_BLOCK && lootBlock != null && lootBlock.getSearchMemory().isGridRevealed(playerId)) {
			ItemStack copy = peek.copy();
			int before = copy.getCount();
			if (lootBlock.getGrid().tryAutoPlace(copy) || copy.getCount() < before) {
				int moved = before - copy.getCount();
				extractSource(loadout, srcLoc, srcIdx, srcEntryId, moved);
				for (GridEntry e : lootBlock.getGrid().getEntries()) {
					lootBlock.getSearchMemory().markEntryDiscovered(playerId, e.getEntryId());
				}
				lootBlock.markDirty();
				syncToClient(serverPlayer);
			}
			return;
		}
		if (mode == MODE_GROUND_ITEM && groundItem != null && !groundItem.getStack().isEmpty()) {
			ItemStack gStack = groundItem.getStack();
			if ((gStack.getItem() instanceof BackpackItem || gStack.getItem() instanceof TarkovCaseItem)
				&& (!ContainerItemHelper.requiresSearch(gStack) || ContainerItemHelper.getSearchMemory(gStack).isGridRevealed(playerId))) {
				GridContainer g = readSingleContainerGrid(gStack);
				ItemStack copy = peek.copy();
				int before = copy.getCount();
				if (g.tryAutoPlace(copy) || copy.getCount() < before) {
					writeSingleContainerGrid(gStack, g);
					extractSource(loadout, srcLoc, srcIdx, srcEntryId, before - copy.getCount());
					syncToClient(serverPlayer);
				}
				return;
			}
			if (gStack.getItem() instanceof TacticalRigItem
				&& (!ContainerItemHelper.requiresSearch(gStack) || ContainerItemHelper.getSearchMemory(gStack).isGridRevealed(playerId))) {
				List<GridContainer> grids = ContainerItemHelper.readRigGrids(gStack);
				ItemStack copy = peek.copy();
				int before = copy.getCount();
				for (GridContainer g : grids) {
					if (g.tryAutoPlace(copy) || copy.getCount() < before) {
						break;
					}
				}
				if (copy.getCount() < before) {
					ContainerItemHelper.writeRigGrids(gStack, grids);
					extractSource(loadout, srcLoc, srcIdx, srcEntryId, before - copy.getCount());
					syncToClient(serverPlayer);
				}
				return;
			}
		}

		if (mode == MODE_OWN_INVENTORY && activeCaseUuid != null
			&& (srcLoc == TarkovNetworking.LOC_POCKET || srcLoc == TarkovNetworking.LOC_RIG_GRID || srcLoc == TarkovNetworking.LOC_BACKPACK_GRID)) {
			CaseHandle handle = resolveActiveCaseHandle();
			if (handle != null && canPlaceIntoCase(handle, peek)) {
				GridContainer g = handle.readGrid();
				ItemStack copy = peek.copy();
				int before = copy.getCount();
				if (g.tryAutoPlace(copy) || copy.getCount() < before) {
					handle.saveGrid(g);
					extractSource(loadout, srcLoc, srcIdx, srcEntryId, before - copy.getCount());
					syncToClient(serverPlayer);
					return;
				}
			}
		}

		// 2. Own inventory transfer: Backpack <-> Rig/Pockets
		ItemStack transferCopy = peek.copy();
		int before = transferCopy.getCount();
		boolean movedBetweenContainers = false;
		if (srcLoc == TarkovNetworking.LOC_BACKPACK_GRID) {
			movedBetweenContainers = insertIntoPocketsOrRig(loadout, playerId, transferCopy) > 0;
		} else if (srcLoc == TarkovNetworking.LOC_POCKET || srcLoc == TarkovNetworking.LOC_RIG_GRID) {
			movedBetweenContainers = insertIntoOwnBackpack(loadout, playerId, transferCopy) > 0;
		}
		if (movedBetweenContainers) {
			extractSource(loadout, srcLoc, srcIdx, srcEntryId, before - transferCopy.getCount());
			syncToClient(serverPlayer);
			return;
		}

		// 3. Fallback: equip into matching empty gear/weapon slot
		EquipmentSlotType autoEquipSlot = findEmptyEquipSlotFor(loadout, peek);
		if (autoEquipSlot != null) {
			ItemStack removed = extractSource(loadout, srcLoc, srcIdx, srcEntryId, peek.getCount());
			if (!removed.isEmpty()) {
				loadout.setEquipment(autoEquipSlot, removed);
				syncToClient(serverPlayer);
			}
		}
	}

	/**
	 * Quick equip/unequip (Ctrl+F or RMB Equip/Unequip).
	 */
	public void handleQuickEquip(ServerPlayerEntity serverPlayer, byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		if (!canUse(serverPlayer)) return;
		UUID playerId = serverPlayer.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);

		if (srcLoc == TarkovNetworking.LOC_EQUIPMENT) {
			unequipToStorage(serverPlayer, loadout, playerId, srcIdx);
			return;
		}

		ItemStack peek = peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId);
		if (peek.isEmpty()) return;

		EquipmentSlotType targetSlot = findEmptyEquipSlotFor(loadout, peek);
		if (targetSlot == null) return;

		ItemStack removed = extractSource(loadout, srcLoc, srcIdx, srcEntryId, peek.getCount());
		if (!removed.isEmpty()) {
			loadout.setEquipment(targetSlot, removed);
			markExternalDirty();
			syncToClient(serverPlayer);
		}
	}

	/**
	 * Server-authoritative consumption of food/drinks/healing items (RMB "Use").
	 */
	public void handleUseItem(ServerPlayerEntity serverPlayer, byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		if (!canUse(serverPlayer) || serverPlayer.isSpectator() || !serverPlayer.isAlive()) return;
		if (!(serverPlayer instanceof PlayerLoadoutHolder holder)) return;

		UUID playerId = serverPlayer.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);
		ItemStack peek = peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId);
		if (peek.getItem() instanceof TarkovMedicalItem) {
			applyMedicalFromSource(serverPlayer, srcLoc, srcIdx, srcEntryId, null, true);
			return;
		}
		if (peek.getItem() instanceof TarkovStimulatorItem stim) {
			if (serverPlayer.getItemCooldownManager().isCoolingDown(stim)) {
				return;
			}
			ItemStack toUse = serverPlayer.isCreative()
				? peek.copy()
				: extractSource(loadout, srcLoc, srcIdx, srcEntryId, 1);
			if (toUse.isEmpty()) return;
			if (TarkovStimulatorItem.applyStimulator(serverPlayer, toUse)) {
				markExternalDirty();
				syncToClient(serverPlayer);
			}
			return;
		}
		if (peek.getItem() instanceof TarkovArmorRepairItem) {
			if (!TarkovArmorRepairItem.canRepairAny(serverPlayer)) {
				serverPlayer.sendMessage(Text.translatable("message.tarkovcraft.repair_not_needed"), true);
				return;
			}
			ItemStack toUse = serverPlayer.isCreative()
				? peek.copy()
				: extractSource(loadout, srcLoc, srcIdx, srcEntryId, 1);
			if (toUse.isEmpty()) return;
			if (TarkovArmorRepairItem.applyRepair(serverPlayer, toUse)) {
				markExternalDirty();
				syncToClient(serverPlayer);
			}
			return;
		}
		if (!isConsumableItem(peek)) return;

		BodyPartHealth health = holder.tarkovcraft$getBodyHealth();
		FoodComponent fc = peek.getItem().getFoodComponent();
		boolean canConsume = fc == null || fc.isAlwaysEdible()
			|| serverPlayer.canConsume(false)
			|| health.getTotalHp() < BodyPart.TOTAL_MAX_HP;
		if (!canConsume) return;

		List<StatusEffectInstance> potionEffects = List.copyOf(PotionUtil.getPotionEffects(peek));
		ItemStack single = extractSource(loadout, srcLoc, srcIdx, srcEntryId, 1);
		if (single.isEmpty()) return;

		Item originalItem = single.getItem();
		UseAction useAction = single.getUseAction();
		ItemStack remainder = single.finishUsing(serverPlayer.getWorld(), serverPlayer);

		if (!remainder.isEmpty() && !serverPlayer.isCreative() && remainder.getItem() != originalItem) {
			if (!loadout.tryInsertItem(remainder, playerId) && !remainder.isEmpty()) {
				serverPlayer.dropItem(remainder, false);
			}
		}

		if (originalItem == Items.ENCHANTED_GOLDEN_APPLE) {
			health.healEvenly(80.0f);
		} else if (originalItem == Items.GOLDEN_APPLE) {
			health.healEvenly(25.0f);
		} else {
			boolean healedByPotion = false;
			for (StatusEffectInstance inst : potionEffects) {
				if (inst.getEffectType() == StatusEffects.INSTANT_HEALTH) {
					health.healEvenly(35.0f * (inst.getAmplifier() + 1));
					healedByPotion = true;
				}
			}
			if (!healedByPotion && fc != null && health.getTotalHp() < BodyPart.TOTAL_MAX_HP) {
				health.healEvenly(Math.max(2.0f, fc.getHunger() * 1.5f));
			}
		}

		serverPlayer.setHealth(Math.max(1.0f, health.getTotalRatio() * serverPlayer.getMaxHealth()));
		holder.tarkovcraft$syncBodyHealth();

		SoundEvent sound = useAction == UseAction.DRINK
			? SoundEvents.ENTITY_GENERIC_DRINK
			: SoundEvents.ENTITY_PLAYER_BURP;
		serverPlayer.getWorld().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
			sound, SoundCategory.PLAYERS, 0.7f, 1.0f);

		markExternalDirty();
		syncToClient(serverPlayer);
	}

	/**
	 * Splits splitAmount items from a stack into a guaranteed free cell without merging back.
	 */
	public void handleSplitStack(ServerPlayerEntity serverPlayer, byte srcLoc, int srcIdx, @Nullable UUID srcEntryId, int splitAmount) {
		if (!canUse(serverPlayer)) return;
		UUID playerId = serverPlayer.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);

		ItemStack peek = peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId);
		if (peek.isEmpty() || peek.getCount() <= 1 || splitAmount < 1 || splitAmount >= peek.getCount()) {
			return;
		}

		ItemStack splitCopy = peek.copy();
		splitCopy.setCount(splitAmount);

		boolean placed = placeSplitIntoSameOrOwnFreeCell(loadout, playerId, srcLoc, srcIdx, splitCopy);
		if (!placed) {
			serverPlayer.sendMessage(Text.translatable("message.tarkovcraft.no_space"), true);
			return;
		}

		extractSource(loadout, srcLoc, srcIdx, srcEntryId, splitAmount);
		markExternalDirty();
		syncToClient(serverPlayer);
	}

	public void handleRequestSearch(ServerPlayerEntity serverPlayer, @Nullable UUID targetId) {
		if (!canUse(serverPlayer) || targetId == null) return;
		searchSession.prioritizeTarget(targetId);
		syncToClient(serverPlayer);
	}

	public void handleOpenCase(ServerPlayerEntity serverPlayer, byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		if (!canUse(serverPlayer) || mode != MODE_OWN_INVENTORY) return;
		UUID playerId = serverPlayer.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);
		UUID foundUuid = switch (srcLoc) {
			case TarkovNetworking.LOC_POCKET -> {
				ItemStack st = loadout.getPocket(srcIdx);
				yield st.getItem() instanceof TarkovCaseItem ? ContainerItemHelper.getOrCreateContainerUuid(st) : null;
			}
			case TarkovNetworking.LOC_EQUIPMENT -> {
				EquipmentSlotType slot = slotByOrdinal(srcIdx);
				if (slot == null) yield null;
				ItemStack st = loadout.getEquipment(slot);
				yield st.getItem() instanceof TarkovCaseItem ? ContainerItemHelper.getOrCreateContainerUuid(st) : null;
			}
			case TarkovNetworking.LOC_RIG_GRID -> {
				ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
				if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem) || srcEntryId == null) yield null;
				if (ContainerItemHelper.requiresSearch(rig)) {
					ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(rig);
					if (!mem.isGridRevealed(playerId) || !mem.isEntryDiscovered(playerId, srcEntryId)) yield null;
				}
				List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
				if (srcIdx < 0 || srcIdx >= grids.size()) yield null;
				GridEntry entry = grids.get(srcIdx).getEntry(srcEntryId);
				if (entry == null || !(entry.getStack().getItem() instanceof TarkovCaseItem)) yield null;
				boolean hadUuid = ContainerItemHelper.getContainerUuid(entry.getStack()) != null;
				UUID id = ContainerItemHelper.getOrCreateContainerUuid(entry.getStack());
				if (!hadUuid) {
					ContainerItemHelper.writeRigGrids(rig, grids);
				}
				yield id;
			}
			case TarkovNetworking.LOC_BACKPACK_GRID -> {
				ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
				if (bp.isEmpty() || !(bp.getItem() instanceof BackpackItem) || srcEntryId == null) yield null;
				if (ContainerItemHelper.requiresSearch(bp)) {
					ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(bp);
					if (!mem.isGridRevealed(playerId) || !mem.isEntryDiscovered(playerId, srcEntryId)) yield null;
				}
				GridContainer grid = ContainerItemHelper.readBackpackGrid(bp);
				GridEntry entry = grid.getEntry(srcEntryId);
				if (entry == null || !(entry.getStack().getItem() instanceof TarkovCaseItem)) yield null;
				boolean hadUuid = ContainerItemHelper.getContainerUuid(entry.getStack()) != null;
				UUID id = ContainerItemHelper.getOrCreateContainerUuid(entry.getStack());
				if (!hadUuid) {
					ContainerItemHelper.writeBackpackGrid(bp, grid);
				}
				yield id;
			}
			default -> null;
		};
		if (foundUuid == null) return;
		this.activeCaseUuid = foundUuid;
		syncToClient(serverPlayer);
	}

	public void handleCloseCase(ServerPlayerEntity serverPlayer) {
		this.activeCaseUuid = null;
		syncToClient(serverPlayer);
	}

	/**
	 * Targeted or auto medical application from the HEALTH tab (C2S_HEAL_PART).
	 */
	public void handleHealPart(
		ServerPlayerEntity serverPlayer,
		byte srcLoc, int srcIdx, @Nullable UUID srcEntryId,
		@Nullable BodyPart targetPart
	) {
		applyMedicalFromSource(serverPlayer, srcLoc, srcIdx, srcEntryId, targetPart, false);
	}

	private void applyMedicalFromSource(
		ServerPlayerEntity serverPlayer,
		byte srcLoc, int srcIdx, @Nullable UUID srcEntryId,
		@Nullable BodyPart targetPart,
		boolean allowExternal
	) {
		if (!canUse(serverPlayer) || serverPlayer.isSpectator() || !serverPlayer.isAlive()) return;
		if (!(serverPlayer instanceof PlayerLoadoutHolder holder)) return;

		boolean isOwnStorage = srcLoc == TarkovNetworking.LOC_POCKET
			|| srcLoc == TarkovNetworking.LOC_RIG_GRID
			|| srcLoc == TarkovNetworking.LOC_BACKPACK_GRID;
		if (!allowExternal && !isOwnStorage) return;

		UUID playerId = serverPlayer.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);
		ItemStack peek = peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId);
		if (!(peek.getItem() instanceof TarkovMedicalItem medItem)) return;
		if (serverPlayer.getItemCooldownManager().isCoolingDown(medItem)) return;

		BodyPartHealth health = holder.tarkovcraft$getBodyHealth();
		MedicalHelper.ApplyResult res = mutateMedicalInContainer(
			loadout, playerId, health, srcLoc, srcIdx, srcEntryId, targetPart, allowExternal, serverPlayer.isCreative()
		);
		if (!res.applied()) {
			serverPlayer.sendMessage(Text.translatable(
				targetPart != null ? "message.tarkovcraft.med_invalid_target" : "message.tarkovcraft.med_not_needed"
			), true);
			return;
		}

		if (res.exhaustionToAdd() > 0.0f) {
			serverPlayer.addExhaustion(res.exhaustionToAdd());
		}
		serverPlayer.getItemCooldownManager().set(medItem, medItem.getMedicalType().getCooldownTicks());
		serverPlayer.setHealth(Math.max(1.0f, health.getTotalRatio() * serverPlayer.getMaxHealth()));
		MedicalHelper.playMedicalSound(serverPlayer, medItem.getMedicalType());
		holder.tarkovcraft$syncBodyHealth();
		markExternalDirty();
		syncToClient(serverPlayer);
	}

	private MedicalHelper.ApplyResult mutateMedicalInContainer(
		PlayerLoadout loadout,
		UUID playerId,
		BodyPartHealth health,
		byte srcLoc,
		int srcIdx,
		@Nullable UUID srcEntryId,
		@Nullable BodyPart targetPart,
		boolean allowExternal,
		boolean isCreative
	) {
		MedicalHelper.ApplyResult failed = new MedicalHelper.ApplyResult(false, null, 0.0f, false);
		return switch (srcLoc) {
			case TarkovNetworking.LOC_POCKET -> {
				ItemStack st = loadout.getPocket(srcIdx);
				if (st.isEmpty()) yield failed;
				MedicalHelper.ApplyResult res = MedicalHelper.applyMedical(health, st, targetPart, isCreative);
				if (res.applied() && st.isEmpty()) {
					loadout.setPocket(srcIdx, ItemStack.EMPTY);
				}
				yield res;
			}
			case TarkovNetworking.LOC_RIG_GRID -> applyMedicalInRig(
				loadout.getEquipment(EquipmentSlotType.RIG), playerId, health, srcIdx, srcEntryId, targetPart, isCreative
			);
			case TarkovNetworking.LOC_BACKPACK_GRID -> applyMedicalInBackpack(
				loadout.getEquipment(EquipmentSlotType.BACKPACK), playerId, health, srcEntryId, targetPart, isCreative
			);
			case TarkovNetworking.LOC_CORPSE_RIG_GRID -> {
				if (!allowExternal) yield failed;
				ItemStack rig = (mode == MODE_CORPSE && corpse != null)
					? corpse.getLoadout().getEquipment(EquipmentSlotType.RIG)
					: (mode == MODE_GROUND_ITEM && groundItem != null ? groundItem.getStack() : ItemStack.EMPTY);
				yield applyMedicalInRig(rig, playerId, health, srcIdx, srcEntryId, targetPart, isCreative);
			}
			case TarkovNetworking.LOC_CORPSE_BACKPACK_GRID -> {
				if (!allowExternal || mode != MODE_CORPSE || corpse == null) yield failed;
				yield applyMedicalInBackpack(
					corpse.getLoadout().getEquipment(EquipmentSlotType.BACKPACK),
					playerId, health, srcEntryId, targetPart, isCreative
				);
			}
			case TarkovNetworking.LOC_EXTERNAL_GRID -> {
				if (!allowExternal || srcEntryId == null) yield failed;
				if (mode == MODE_OWN_INVENTORY && activeCaseUuid != null) {
					CaseHandle handle = resolveActiveCaseHandle();
					if (handle == null) yield failed;
					GridContainer grid = handle.readGrid();
					GridEntry entry = grid.getEntry(srcEntryId);
					if (entry == null) yield failed;
					MedicalHelper.ApplyResult res = MedicalHelper.applyMedical(health, entry.getStack(), targetPart, isCreative);
					if (res.applied()) {
						if (entry.getStack().isEmpty()) {
							grid.remove(srcEntryId);
						}
						handle.saveGrid(grid);
					}
					yield res;
				}
				if (mode == MODE_GROUND_ITEM && groundItem != null) {
					yield applyMedicalInSingleContainer(groundItem.getStack(), playerId, health, srcEntryId, targetPart, isCreative);
				}
				GridContainer grid = getPrimaryExternalGrid();
				ContainerSearchMemory mem = getPrimaryExternalMemory();
				if (grid == null || mem == null || !mem.isGridRevealed(playerId) || !mem.isEntryDiscovered(playerId, srcEntryId)) {
					yield failed;
				}
				GridEntry entry = grid.getEntry(srcEntryId);
				if (entry == null) yield failed;
				MedicalHelper.ApplyResult res = MedicalHelper.applyMedical(health, entry.getStack(), targetPart, isCreative);
				if (res.applied() && entry.getStack().isEmpty()) {
					grid.remove(srcEntryId);
				}
				yield res;
			}
			default -> failed;
		};
	}

	private static MedicalHelper.ApplyResult applyMedicalInRig(
		ItemStack rig, UUID playerId, BodyPartHealth health,
		int idx, @Nullable UUID entryId, @Nullable BodyPart targetPart, boolean isCreative
	) {
		MedicalHelper.ApplyResult failed = new MedicalHelper.ApplyResult(false, null, 0.0f, false);
		if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem) || entryId == null) return failed;
		if (ContainerItemHelper.requiresSearch(rig)) {
			ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(rig);
			if (!mem.isGridRevealed(playerId) || !mem.isEntryDiscovered(playerId, entryId)) {
				return failed;
			}
		}
		List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
		if (idx < 0 || idx >= grids.size()) return failed;
		GridEntry entry = grids.get(idx).getEntry(entryId);
		if (entry == null) return failed;

		MedicalHelper.ApplyResult res = MedicalHelper.applyMedical(health, entry.getStack(), targetPart, isCreative);
		if (res.applied()) {
			if (entry.getStack().isEmpty()) {
				grids.get(idx).remove(entryId);
			}
			ContainerItemHelper.writeRigGrids(rig, grids);
		}
		return res;
	}

	private static MedicalHelper.ApplyResult applyMedicalInBackpack(
		ItemStack bp, UUID playerId, BodyPartHealth health,
		@Nullable UUID entryId, @Nullable BodyPart targetPart, boolean isCreative
	) {
		if (bp.isEmpty() || !(bp.getItem() instanceof BackpackItem)) {
			return new MedicalHelper.ApplyResult(false, null, 0.0f, false);
		}
		return applyMedicalInSingleContainer(bp, playerId, health, entryId, targetPart, isCreative);
	}

	private static MedicalHelper.ApplyResult applyMedicalInSingleContainer(
		ItemStack container, UUID playerId, BodyPartHealth health,
		@Nullable UUID entryId, @Nullable BodyPart targetPart, boolean isCreative
	) {
		MedicalHelper.ApplyResult failed = new MedicalHelper.ApplyResult(false, null, 0.0f, false);
		if (!isSingleGridContainer(container) || entryId == null) return failed;
		if (ContainerItemHelper.requiresSearch(container)) {
			ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(container);
			if (!mem.isGridRevealed(playerId) || !mem.isEntryDiscovered(playerId, entryId)) {
				return failed;
			}
		}
		GridContainer grid = readSingleContainerGrid(container);
		GridEntry entry = grid.getEntry(entryId);
		if (entry == null) return failed;

		MedicalHelper.ApplyResult res = MedicalHelper.applyMedical(health, entry.getStack(), targetPart, isCreative);
		if (res.applied()) {
			if (entry.getStack().isEmpty()) {
				grid.remove(entryId);
			}
			writeSingleContainerGrid(container, grid);
		}
		return res;
	}

	private void unequipToStorage(ServerPlayerEntity serverPlayer, PlayerLoadout loadout, UUID playerId, int srcIdx) {
		EquipmentSlotType slot = slotByOrdinal(srcIdx);
		if (slot == null) return;
		ItemStack eq = loadout.getEquipment(slot);
		if (eq.isEmpty()) return;

		loadout.setEquipment(slot, ItemStack.EMPTY);
		ItemStack copy = eq.copy();
		if (loadout.tryInsertItem(copy, playerId) && copy.isEmpty()) {
			syncToClient(serverPlayer);
		} else {
			loadout.setEquipment(slot, eq);
			serverPlayer.sendMessage(Text.translatable("message.tarkovcraft.no_space"), true);
		}
	}

	private static int insertIntoPocketsOrRig(PlayerLoadout loadout, UUID playerId, ItemStack stack) {
		int before = stack.getCount();
		if (loadout.canPlaceInPocket(stack)) {
			if (stack.isStackable()) {
				for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
					ItemStack pocket = loadout.getPocket(i);
					if (!pocket.isEmpty() && ItemStack.canCombine(pocket, stack) && pocket.getCount() < pocket.getMaxCount()) {
						int transfer = Math.min(pocket.getMaxCount() - pocket.getCount(), stack.getCount());
						pocket.increment(transfer);
						stack.decrement(transfer);
						if (stack.isEmpty()) return before;
					}
				}
			}
			for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
				if (loadout.getPocket(i).isEmpty()) {
					loadout.setPocket(i, stack.copy());
					stack.setCount(0);
					return before;
				}
			}
		}

		ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
			boolean revealed = !ContainerItemHelper.requiresSearch(rig)
				|| ContainerItemHelper.getSearchMemory(rig).isGridRevealed(playerId);
			if (revealed) {
				List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
				boolean changed = false;
				for (GridContainer g : grids) {
					int cBefore = stack.getCount();
					if (g.tryAutoPlace(stack) || stack.getCount() < cBefore) {
						changed = true;
						if (stack.isEmpty()) break;
					}
				}
				if (changed) {
					ContainerItemHelper.writeRigGrids(rig, grids);
				}
			}
		}
		return before - stack.getCount();
	}

	private static int insertIntoOwnBackpack(PlayerLoadout loadout, UUID playerId, ItemStack stack) {
		int before = stack.getCount();
		ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
		if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem) {
			boolean revealed = !ContainerItemHelper.requiresSearch(bp)
				|| ContainerItemHelper.getSearchMemory(bp).isGridRevealed(playerId);
			if (revealed) {
				GridContainer g = ContainerItemHelper.readBackpackGrid(bp);
				if (g.tryAutoPlace(stack) || stack.getCount() < before) {
					ContainerItemHelper.writeBackpackGrid(bp, g);
				}
			}
		}
		return before - stack.getCount();
	}

	private boolean placeSplitIntoSameOrOwnFreeCell(
		PlayerLoadout loadout, UUID playerId, byte srcLoc, int srcIdx, ItemStack splitCopy
	) {
		// 1. Try placing into the same grid first
		if (srcLoc == TarkovNetworking.LOC_RIG_GRID) {
			if (tryPlaceSplitInRig(loadout.getEquipment(EquipmentSlotType.RIG), playerId, srcIdx, splitCopy)) return true;
		} else if (srcLoc == TarkovNetworking.LOC_BACKPACK_GRID) {
			if (tryPlaceSplitInBackpack(loadout.getEquipment(EquipmentSlotType.BACKPACK), playerId, splitCopy)) return true;
		} else if (srcLoc == TarkovNetworking.LOC_EXTERNAL_GRID) {
			if (mode == MODE_OWN_INVENTORY && activeCaseUuid != null) {
				CaseHandle handle = resolveActiveCaseHandle();
				if (handle != null) {
					GridContainer g = handle.readGrid();
					GridEntry created = g.tryPlaceInFreeCell(splitCopy);
					if (created != null) {
						handle.saveGrid(g);
						return true;
					}
				}
			} else if (mode == MODE_GROUND_ITEM && groundItem != null) {
				if (tryPlaceSplitInSingleContainer(groundItem.getStack(), playerId, splitCopy)) return true;
			} else {
				GridContainer g = getPrimaryExternalGrid();
				ContainerSearchMemory mem = getPrimaryExternalMemory();
				if (g != null && mem != null && mem.isGridRevealed(playerId)) {
					GridEntry created = g.tryPlaceInFreeCell(splitCopy);
					if (created != null) {
						mem.markEntryDiscovered(playerId, created.getEntryId());
						return true;
					}
				}
			}
		} else if (srcLoc == TarkovNetworking.LOC_CORPSE_RIG_GRID) {
			ItemStack rig = (mode == MODE_CORPSE && corpse != null)
				? corpse.getLoadout().getEquipment(EquipmentSlotType.RIG)
				: (mode == MODE_GROUND_ITEM && groundItem != null ? groundItem.getStack() : ItemStack.EMPTY);
			if (tryPlaceSplitInRig(rig, playerId, srcIdx, splitCopy)) return true;
		} else if (srcLoc == TarkovNetworking.LOC_CORPSE_BACKPACK_GRID && mode == MODE_CORPSE && corpse != null) {
			if (tryPlaceSplitInBackpack(corpse.getLoadout().getEquipment(EquipmentSlotType.BACKPACK), playerId, splitCopy)) return true;
		}

		// 2. Try free 1x1 pocket
		if (loadout.canPlaceInPocket(splitCopy)) {
			for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
				if (loadout.getPocket(i).isEmpty()) {
					loadout.setPocket(i, splitCopy);
					return true;
				}
			}
		}

		// 3. Try own Rig free cell
		ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
			List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
			for (int i = 0; i < grids.size(); i++) {
				if (tryPlaceSplitInRig(rig, playerId, i, splitCopy)) return true;
			}
		}

		// 4. Try own Backpack free cell
		return tryPlaceSplitInBackpack(loadout.getEquipment(EquipmentSlotType.BACKPACK), playerId, splitCopy);
	}

	private static boolean tryPlaceSplitInRig(ItemStack rig, UUID playerId, int idx, ItemStack splitCopy) {
		if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem)) return false;
		if (ContainerItemHelper.requiresSearch(rig) && !ContainerItemHelper.getSearchMemory(rig).isGridRevealed(playerId)) {
			return false;
		}
		List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
		if (idx < 0 || idx >= grids.size()) return false;
		GridEntry created = grids.get(idx).tryPlaceInFreeCell(splitCopy);
		if (created == null) return false;
		ContainerItemHelper.writeRigGrids(rig, grids);
		if (ContainerItemHelper.requiresSearch(rig)) {
			ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(rig);
			mem.markEntryDiscovered(playerId, created.getEntryId());
			ContainerItemHelper.saveSearchMemory(rig, mem);
		}
		return true;
	}

	private static boolean tryPlaceSplitInBackpack(ItemStack bp, UUID playerId, ItemStack splitCopy) {
		if (bp.isEmpty() || !(bp.getItem() instanceof BackpackItem)) return false;
		return tryPlaceSplitInSingleContainer(bp, playerId, splitCopy);
	}

	private static boolean tryPlaceSplitInSingleContainer(ItemStack container, UUID playerId, ItemStack splitCopy) {
		if (!isSingleGridContainer(container)) return false;
		if (ContainerItemHelper.requiresSearch(container) && !ContainerItemHelper.getSearchMemory(container).isGridRevealed(playerId)) {
			return false;
		}
		GridContainer grid = readSingleContainerGrid(container);
		GridEntry created = grid.tryPlaceInFreeCell(splitCopy);
		if (created == null) return false;
		writeSingleContainerGrid(container, grid);
		if (ContainerItemHelper.requiresSearch(container)) {
			ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(container);
			mem.markEntryDiscovered(playerId, created.getEntryId());
			ContainerItemHelper.saveSearchMemory(container, mem);
		}
		return true;
	}

	@Nullable
	private static EquipmentSlotType findFirstMatchingSlot(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		if (stack.getItem() instanceof BackpackItem) return EquipmentSlotType.BACKPACK;
		if (stack.getItem() instanceof TacticalRigItem) return EquipmentSlotType.RIG;
		for (EquipmentSlotType slot : new EquipmentSlotType[]{
			EquipmentSlotType.HELMET, EquipmentSlotType.FACE_MASK, EquipmentSlotType.BODY_ARMOR,
			EquipmentSlotType.PISTOL, EquipmentSlotType.PRIMARY_1, EquipmentSlotType.PRIMARY_2, EquipmentSlotType.MELEE
		}) {
			if (slot.accepts(stack)) {
				if ((slot == EquipmentSlotType.PRIMARY_1 || slot == EquipmentSlotType.PRIMARY_2 || slot == EquipmentSlotType.PISTOL)
					&& !isWeaponLike(stack)) {
					continue;
				}
				return slot;
			}
		}
		return null;
	}

	@Nullable
	private static EquipmentSlotType findEmptyEquipSlotFor(PlayerLoadout loadout, ItemStack stack) {
		if (stack.getItem() instanceof BackpackItem && loadout.getEquipment(EquipmentSlotType.BACKPACK).isEmpty()) {
			return EquipmentSlotType.BACKPACK;
		}
		if (stack.getItem() instanceof TacticalRigItem && loadout.getEquipment(EquipmentSlotType.RIG).isEmpty()) {
			return EquipmentSlotType.RIG;
		}
		for (EquipmentSlotType slot : new EquipmentSlotType[]{
			EquipmentSlotType.HELMET, EquipmentSlotType.FACE_MASK, EquipmentSlotType.BODY_ARMOR,
			EquipmentSlotType.PISTOL, EquipmentSlotType.PRIMARY_1, EquipmentSlotType.PRIMARY_2, EquipmentSlotType.MELEE
		}) {
			if (loadout.getEquipment(slot).isEmpty() && slot.accepts(stack)) {
				if ((slot == EquipmentSlotType.PRIMARY_1 || slot == EquipmentSlotType.PRIMARY_2 || slot == EquipmentSlotType.PISTOL)
					&& !isWeaponLike(stack)) {
					continue;
				}
				return slot;
			}
		}
		return null;
	}

	private static boolean isWeaponLike(ItemStack stack) {
		String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
		return id.startsWith("tacz:") && id.contains("gun");
	}

	public void handleMoveAction(
		ServerPlayerEntity serverPlayer,
		byte srcLoc, int srcIdx, @Nullable UUID srcEntryId,
		byte dstLoc, int dstIdx, int dstX, int dstY, boolean dstRotated,
		int requestedCount
	) {
		if (!canUse(serverPlayer)) {
			return;
		}
		UUID playerId = serverPlayer.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);

		ItemStack sourcePeek = peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId);
		if (sourcePeek.isEmpty()) {
			return;
		}

		int moveCount = (requestedCount <= 0 || requestedCount >= sourcePeek.getCount())
			? sourcePeek.getCount()
			: requestedCount;
		boolean isPartialSplit = moveCount < sourcePeek.getCount();

		ItemStack movingPreview = sourcePeek.copy();
		movingPreview.setCount(moveCount);

		// 1. Drop to world (supports dropping 1 item OR full stack!)
		if (dstLoc == TarkovNetworking.LOC_DROP) {
			ItemStack removed = extractSource(loadout, srcLoc, srcIdx, srcEntryId, moveCount);
			if (!removed.isEmpty()) {
				serverPlayer.dropItem(removed, false);
				markExternalDirty();
				syncToClient(serverPlayer);
			}
			return;
		}

		// 2. Same-grid repositioning (only for full stack — partial split places a new entry!)
		if (!isPartialSplit && srcLoc == dstLoc && srcIdx == dstIdx && srcEntryId != null && isGridLocation(srcLoc)) {
			if (repositionInSameGrid(loadout, playerId, srcLoc, srcIdx, srcEntryId, dstX, dstY, dstRotated)) {
				markExternalDirty();
				syncToClient(serverPlayer);
			}
			return;
		}

		// 3. Validate destination BEFORE mutating source
		if (!canInsertIntoDestination(loadout, playerId, movingPreview, dstLoc, dstIdx, dstX, dstY, dstRotated)) {
			return;
		}

		// 4. Execute atomic transfer (partial split or full move)
		ItemStack removed = extractSource(loadout, srcLoc, srcIdx, srcEntryId, moveCount);
		if (removed.isEmpty()) {
			return;
		}
		boolean inserted = insertIntoDestination(loadout, playerId, removed, dstLoc, dstIdx, dstX, dstY, dstRotated);
		if (!inserted) {
			serverPlayer.dropItem(removed, false);
		}

		markExternalDirty();
		syncToClient(serverPlayer);
	}

	private boolean isGridLocation(byte loc) {
		return loc == TarkovNetworking.LOC_RIG_GRID
			|| loc == TarkovNetworking.LOC_BACKPACK_GRID
			|| loc == TarkovNetworking.LOC_EXTERNAL_GRID
			|| loc == TarkovNetworking.LOC_CORPSE_RIG_GRID
			|| loc == TarkovNetworking.LOC_CORPSE_BACKPACK_GRID;
	}

	private ItemStack peekSource(PlayerLoadout loadout, UUID playerId, byte loc, int idx, @Nullable UUID entryId) {
		return switch (loc) {
			case TarkovNetworking.LOC_EQUIPMENT -> {
				EquipmentSlotType slot = slotByOrdinal(idx);
				yield slot != null ? loadout.getEquipment(slot) : ItemStack.EMPTY;
			}
			case TarkovNetworking.LOC_CORPSE_EQUIPMENT -> {
				if (mode != MODE_CORPSE || corpse == null) yield ItemStack.EMPTY;
				EquipmentSlotType slot = slotByOrdinal(idx);
				yield slot != null ? corpse.getLoadout().getEquipment(slot) : ItemStack.EMPTY;
			}
			case TarkovNetworking.LOC_POCKET -> loadout.getPocket(idx);
			case TarkovNetworking.LOC_RIG_GRID -> peekFromRig(loadout.getEquipment(EquipmentSlotType.RIG), playerId, idx, entryId);
			case TarkovNetworking.LOC_BACKPACK_GRID -> peekFromBackpack(loadout.getEquipment(EquipmentSlotType.BACKPACK), playerId, entryId);
			case TarkovNetworking.LOC_CORPSE_RIG_GRID -> {
				if (mode == MODE_CORPSE && corpse != null) {
					yield peekFromRig(corpse.getLoadout().getEquipment(EquipmentSlotType.RIG), playerId, idx, entryId);
				}
				if (mode == MODE_GROUND_ITEM && groundItem != null) {
					yield peekFromRig(groundItem.getStack(), playerId, idx, entryId);
				}
				yield ItemStack.EMPTY;
			}
			case TarkovNetworking.LOC_CORPSE_BACKPACK_GRID -> (mode == MODE_CORPSE && corpse != null)
				? peekFromBackpack(corpse.getLoadout().getEquipment(EquipmentSlotType.BACKPACK), playerId, entryId)
				: ItemStack.EMPTY;
			case TarkovNetworking.LOC_EXTERNAL_GRID -> {
				if (entryId == null) yield ItemStack.EMPTY;
				if (mode == MODE_OWN_INVENTORY && activeCaseUuid != null) {
					CaseHandle handle = resolveActiveCaseHandle();
					if (handle == null) yield ItemStack.EMPTY;
					GridEntry entry = handle.readGrid().getEntry(entryId);
					yield entry != null ? entry.getStack() : ItemStack.EMPTY;
				}
				if (mode == MODE_GROUND_ITEM && groundItem != null) {
					yield peekFromSingleContainer(groundItem.getStack(), playerId, entryId);
				}
				GridContainer grid = getPrimaryExternalGrid();
				ContainerSearchMemory mem = getPrimaryExternalMemory();
				if (grid == null || mem == null) yield ItemStack.EMPTY;
				if (!mem.isGridRevealed(playerId) || !mem.isEntryDiscovered(playerId, entryId)) {
					yield ItemStack.EMPTY;
				}
				GridEntry entry = grid.getEntry(entryId);
				yield entry != null ? entry.getStack() : ItemStack.EMPTY;
			}
			default -> ItemStack.EMPTY;
		};
	}

	private static ItemStack peekFromRig(ItemStack rig, UUID playerId, int idx, @Nullable UUID entryId) {
		if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem) || entryId == null) return ItemStack.EMPTY;
		if (ContainerItemHelper.requiresSearch(rig)) {
			ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(rig);
			if (!mem.isGridRevealed(playerId) || !mem.isEntryDiscovered(playerId, entryId)) {
				return ItemStack.EMPTY;
			}
		}
		List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
		if (idx < 0 || idx >= grids.size()) return ItemStack.EMPTY;
		GridEntry entry = grids.get(idx).getEntry(entryId);
		return entry != null ? entry.getStack() : ItemStack.EMPTY;
	}

	private static ItemStack peekFromBackpack(ItemStack bp, UUID playerId, @Nullable UUID entryId) {
		if (bp.isEmpty() || !(bp.getItem() instanceof BackpackItem)) return ItemStack.EMPTY;
		return peekFromSingleContainer(bp, playerId, entryId);
	}

	private static ItemStack peekFromSingleContainer(ItemStack container, UUID playerId, @Nullable UUID entryId) {
		if (!isSingleGridContainer(container) || entryId == null) return ItemStack.EMPTY;
		if (ContainerItemHelper.requiresSearch(container)) {
			ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(container);
			if (!mem.isGridRevealed(playerId) || !mem.isEntryDiscovered(playerId, entryId)) {
				return ItemStack.EMPTY;
			}
		}
		GridEntry entry = readSingleContainerGrid(container).getEntry(entryId);
		return entry != null ? entry.getStack() : ItemStack.EMPTY;
	}

	private ItemStack extractSource(PlayerLoadout loadout, byte loc, int idx, @Nullable UUID entryId, int count) {
		if (count <= 0) return ItemStack.EMPTY;
		return switch (loc) {
			case TarkovNetworking.LOC_EQUIPMENT -> {
				EquipmentSlotType slot = slotByOrdinal(idx);
				if (slot == null) yield ItemStack.EMPTY;
				ItemStack st = loadout.getEquipment(slot);
				ItemStack taken = st.split(count);
				if (st.isEmpty()) loadout.setEquipment(slot, ItemStack.EMPTY);
				yield taken;
			}
			case TarkovNetworking.LOC_CORPSE_EQUIPMENT -> {
				if (mode != MODE_CORPSE || corpse == null) yield ItemStack.EMPTY;
				EquipmentSlotType slot = slotByOrdinal(idx);
				if (slot == null) yield ItemStack.EMPTY;
				ItemStack st = corpse.getLoadout().getEquipment(slot);
				ItemStack taken = st.split(count);
				if (st.isEmpty()) corpse.getLoadout().setEquipment(slot, ItemStack.EMPTY);
				corpse.syncVisualFlags();
				yield taken;
			}
			case TarkovNetworking.LOC_POCKET -> {
				ItemStack st = loadout.getPocket(idx);
				ItemStack taken = st.split(count);
				if (st.isEmpty()) loadout.setPocket(idx, ItemStack.EMPTY);
				yield taken;
			}
			case TarkovNetworking.LOC_RIG_GRID -> extractFromRig(loadout.getEquipment(EquipmentSlotType.RIG), idx, entryId, count);
			case TarkovNetworking.LOC_BACKPACK_GRID -> extractFromBackpack(loadout.getEquipment(EquipmentSlotType.BACKPACK), entryId, count);
			case TarkovNetworking.LOC_CORPSE_RIG_GRID -> {
				if (mode == MODE_CORPSE && corpse != null) {
					yield extractFromRig(corpse.getLoadout().getEquipment(EquipmentSlotType.RIG), idx, entryId, count);
				}
				if (mode == MODE_GROUND_ITEM && groundItem != null) {
					yield extractFromRig(groundItem.getStack(), idx, entryId, count);
				}
				yield ItemStack.EMPTY;
			}
			case TarkovNetworking.LOC_CORPSE_BACKPACK_GRID -> (mode == MODE_CORPSE && corpse != null)
				? extractFromBackpack(corpse.getLoadout().getEquipment(EquipmentSlotType.BACKPACK), entryId, count)
				: ItemStack.EMPTY;
			case TarkovNetworking.LOC_EXTERNAL_GRID -> {
				if (mode == MODE_OWN_INVENTORY && activeCaseUuid != null) {
					CaseHandle handle = resolveActiveCaseHandle();
					if (handle == null || entryId == null) yield ItemStack.EMPTY;
					GridContainer grid = handle.readGrid();
					GridEntry entry = grid.getEntry(entryId);
					if (entry == null) yield ItemStack.EMPTY;
					ItemStack taken = entry.getStack().split(count);
					if (entry.getStack().isEmpty()) {
						grid.remove(entryId);
					}
					handle.saveGrid(grid);
					yield taken;
				}
				if (mode == MODE_GROUND_ITEM && groundItem != null) {
					yield extractFromSingleContainer(groundItem.getStack(), entryId, count);
				}
				GridContainer grid = getPrimaryExternalGrid();
				if (grid == null || entryId == null) yield ItemStack.EMPTY;
				GridEntry entry = grid.getEntry(entryId);
				if (entry == null) yield ItemStack.EMPTY;
				ItemStack taken = entry.getStack().split(count);
				if (entry.getStack().isEmpty()) {
					grid.remove(entryId);
				}
				yield taken;
			}
			default -> ItemStack.EMPTY;
		};
	}

	private static ItemStack extractFromRig(ItemStack rig, int idx, @Nullable UUID entryId, int count) {
		if (rig.isEmpty() || entryId == null) return ItemStack.EMPTY;
		List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
		if (idx < 0 || idx >= grids.size()) return ItemStack.EMPTY;
		GridEntry entry = grids.get(idx).getEntry(entryId);
		if (entry == null) return ItemStack.EMPTY;
		ItemStack taken = entry.getStack().split(count);
		if (entry.getStack().isEmpty()) {
			grids.get(idx).remove(entryId);
		}
		ContainerItemHelper.writeRigGrids(rig, grids);
		return taken;
	}

	private static ItemStack extractFromBackpack(ItemStack bp, @Nullable UUID entryId, int count) {
		if (bp.isEmpty() || !(bp.getItem() instanceof BackpackItem)) return ItemStack.EMPTY;
		return extractFromSingleContainer(bp, entryId, count);
	}

	private static ItemStack extractFromSingleContainer(ItemStack container, @Nullable UUID entryId, int count) {
		if (!isSingleGridContainer(container) || entryId == null) return ItemStack.EMPTY;
		GridContainer grid = readSingleContainerGrid(container);
		GridEntry entry = grid.getEntry(entryId);
		if (entry == null) return ItemStack.EMPTY;
		ItemStack taken = entry.getStack().split(count);
		if (entry.getStack().isEmpty()) {
			grid.remove(entryId);
		}
		writeSingleContainerGrid(container, grid);
		return taken;
	}

	private boolean repositionInSameGrid(
		PlayerLoadout loadout, UUID playerId,
		byte loc, int idx, UUID entryId,
		int dstX, int dstY, boolean dstRotated
	) {
		if (loc == TarkovNetworking.LOC_RIG_GRID) {
			ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
			List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
			if (idx < 0 || idx >= grids.size()) return false;
			GridContainer grid = grids.get(idx);
			GridEntry entry = grid.getEntry(entryId);
			if (entry == null || !grid.canPlace(entry.getStack(), dstX, dstY, dstRotated, entryId)) return false;
			grid.remove(entryId);
			grid.placeWithId(entryId, entry.getStack(), dstX, dstY, dstRotated);
			ContainerItemHelper.writeRigGrids(rig, grids);
			return true;
		}
		if (loc == TarkovNetworking.LOC_BACKPACK_GRID) {
			ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
			GridContainer grid = ContainerItemHelper.readBackpackGrid(bp);
			GridEntry entry = grid.getEntry(entryId);
			if (entry == null || !grid.canPlace(entry.getStack(), dstX, dstY, dstRotated, entryId)) return false;
			grid.remove(entryId);
			grid.placeWithId(entryId, entry.getStack(), dstX, dstY, dstRotated);
			ContainerItemHelper.writeBackpackGrid(bp, grid);
			return true;
		}
		if (loc == TarkovNetworking.LOC_EXTERNAL_GRID) {
			if (mode == MODE_OWN_INVENTORY && activeCaseUuid != null) {
				CaseHandle handle = resolveActiveCaseHandle();
				if (handle == null) return false;
				GridContainer grid = handle.readGrid();
				GridEntry entry = grid.getEntry(entryId);
				if (entry == null || !grid.canPlace(entry.getStack(), dstX, dstY, dstRotated, entryId)) return false;
				grid.remove(entryId);
				grid.placeWithId(entryId, entry.getStack(), dstX, dstY, dstRotated);
				handle.saveGrid(grid);
				return true;
			}
			if (mode == MODE_GROUND_ITEM && groundItem != null) {
				ItemStack container = groundItem.getStack();
				if (!isSingleGridContainer(container)) return false;
				GridContainer grid = readSingleContainerGrid(container);
				GridEntry entry = grid.getEntry(entryId);
				if (entry == null || !grid.canPlace(entry.getStack(), dstX, dstY, dstRotated, entryId)) return false;
				grid.remove(entryId);
				grid.placeWithId(entryId, entry.getStack(), dstX, dstY, dstRotated);
				writeSingleContainerGrid(container, grid);
				return true;
			}
			GridContainer grid = getPrimaryExternalGrid();
			ContainerSearchMemory mem = getPrimaryExternalMemory();
			if (grid == null || mem == null || !mem.isEntryDiscovered(playerId, entryId)) return false;
			GridEntry entry = grid.getEntry(entryId);
			if (entry == null || !grid.canPlace(entry.getStack(), dstX, dstY, dstRotated, entryId)) return false;
			grid.remove(entryId);
			grid.placeWithId(entryId, entry.getStack(), dstX, dstY, dstRotated);
			return true;
		}
		return false;
	}

	private boolean canInsertIntoDestination(
		PlayerLoadout loadout, UUID playerId, ItemStack stack,
		byte loc, int idx, int dstX, int dstY, boolean dstRotated
	) {
		return switch (loc) {
			case TarkovNetworking.LOC_EQUIPMENT -> {
				EquipmentSlotType slot = slotByOrdinal(idx);
				yield slot != null && slot.accepts(stack) && loadout.getEquipment(slot).isEmpty();
			}
			case TarkovNetworking.LOC_POCKET ->
				idx >= 0 && idx < PlayerLoadout.POCKET_COUNT
					&& loadout.canPlaceInPocket(stack)
					&& (loadout.getPocket(idx).isEmpty() || canStackInto(loadout.getPocket(idx), stack));
			case TarkovNetworking.LOC_RIG_GRID -> {
				ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
				if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem)) yield false;
				UUID rigId = ContainerItemHelper.getContainerUuid(rig);
				if (rigId != null && ContainerItemHelper.containsContainerId(stack, rigId)) yield false;
				if (ContainerItemHelper.getNestingDepth(stack) >= 2) yield false;
				if (ContainerItemHelper.requiresSearch(rig) && !ContainerItemHelper.getSearchMemory(rig).isGridRevealed(playerId)) yield false;
				List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
				if (idx < 0 || idx >= grids.size()) yield false;
				GridContainer g = grids.get(idx);
				GridEntry at = g.getAt(dstX, dstY);
				yield (at != null && canStackInto(at.getStack(), stack)) || g.canPlace(stack, dstX, dstY, dstRotated);
			}
			case TarkovNetworking.LOC_BACKPACK_GRID -> {
				ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
				if (bp.isEmpty() || !(bp.getItem() instanceof BackpackItem)) yield false;
				UUID bpId = ContainerItemHelper.getContainerUuid(bp);
				if (bpId != null && ContainerItemHelper.containsContainerId(stack, bpId)) yield false;
				if (ContainerItemHelper.getNestingDepth(stack) >= 2) yield false;
				if (ContainerItemHelper.requiresSearch(bp) && !ContainerItemHelper.getSearchMemory(bp).isGridRevealed(playerId)) yield false;
				GridContainer g = ContainerItemHelper.readBackpackGrid(bp);
				GridEntry at = g.getAt(dstX, dstY);
				yield (at != null && canStackInto(at.getStack(), stack)) || g.canPlace(stack, dstX, dstY, dstRotated);
			}
			case TarkovNetworking.LOC_EXTERNAL_GRID -> {
				if (mode == MODE_OWN_INVENTORY && activeCaseUuid != null) {
					CaseHandle handle = resolveActiveCaseHandle();
					if (handle == null || !canPlaceIntoCase(handle, stack)) yield false;
					GridContainer g = handle.readGrid();
					GridEntry at = g.getAt(dstX, dstY);
					yield (at != null && canStackInto(at.getStack(), stack)) || g.canPlace(stack, dstX, dstY, dstRotated);
				}
				if (mode == MODE_GROUND_ITEM && groundItem != null && isSingleGridContainer(groundItem.getStack())) {
					ItemStack container = groundItem.getStack();
					UUID containerId = ContainerItemHelper.getContainerUuid(container);
					if (containerId != null && ContainerItemHelper.containsContainerId(stack, containerId)) yield false;
					if (ContainerItemHelper.requiresSearch(container) && !ContainerItemHelper.getSearchMemory(container).isGridRevealed(playerId)) yield false;
					GridContainer g = readSingleContainerGrid(container);
					GridEntry at = g.getAt(dstX, dstY);
					yield (at != null && canStackInto(at.getStack(), stack)) || g.canPlace(stack, dstX, dstY, dstRotated);
				}
				GridContainer g = getPrimaryExternalGrid();
				ContainerSearchMemory mem = getPrimaryExternalMemory();
				if (g == null || mem == null || !mem.isGridRevealed(playerId)) yield false;
				GridEntry at = g.getAt(dstX, dstY);
				yield (at != null && mem.isEntryDiscovered(playerId, at.getEntryId()) && canStackInto(at.getStack(), stack))
					|| g.canPlace(stack, dstX, dstY, dstRotated);
			}
			case TarkovNetworking.LOC_CORPSE_RIG_GRID -> {
				if (mode == MODE_GROUND_ITEM && groundItem != null && groundItem.getStack().getItem() instanceof TacticalRigItem) {
					ItemStack rig = groundItem.getStack();
					if (ContainerItemHelper.requiresSearch(rig) && !ContainerItemHelper.getSearchMemory(rig).isGridRevealed(playerId)) yield false;
					List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
					if (idx < 0 || idx >= grids.size()) yield false;
					GridContainer g = grids.get(idx);
					GridEntry at = g.getAt(dstX, dstY);
					yield (at != null && canStackInto(at.getStack(), stack)) || g.canPlace(stack, dstX, dstY, dstRotated);
				}
				yield false;
			}
			default -> false;
		};
	}

	private boolean insertIntoDestination(
		PlayerLoadout loadout, UUID playerId, ItemStack stack,
		byte loc, int idx, int dstX, int dstY, boolean dstRotated
	) {
		return switch (loc) {
			case TarkovNetworking.LOC_EQUIPMENT -> {
				EquipmentSlotType slot = slotByOrdinal(idx);
				if (slot == null) yield false;
				loadout.setEquipment(slot, stack);
				yield true;
			}
			case TarkovNetworking.LOC_POCKET -> {
				ItemStack cur = loadout.getPocket(idx);
				if (cur.isEmpty()) {
					yield loadout.setPocket(idx, stack);
				}
				if (canStackInto(cur, stack)) {
					cur.increment(stack.getCount());
					yield true;
				}
				yield false;
			}
			case TarkovNetworking.LOC_RIG_GRID -> {
				ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
				List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
				GridEntry placed = grids.get(idx).place(stack, dstX, dstY, dstRotated);
				if (placed != null) {
					ContainerItemHelper.writeRigGrids(rig, grids);
					if (ContainerItemHelper.requiresSearch(rig)) {
						ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(rig);
						mem.markEntryDiscovered(playerId, placed.getEntryId());
						ContainerItemHelper.saveSearchMemory(rig, mem);
					}
					yield true;
				}
				yield false;
			}
			case TarkovNetworking.LOC_BACKPACK_GRID -> {
				ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
				GridContainer grid = ContainerItemHelper.readBackpackGrid(bp);
				GridEntry placed = grid.place(stack, dstX, dstY, dstRotated);
				if (placed != null) {
					ContainerItemHelper.writeBackpackGrid(bp, grid);
					if (ContainerItemHelper.requiresSearch(bp)) {
						ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(bp);
						mem.markEntryDiscovered(playerId, placed.getEntryId());
						ContainerItemHelper.saveSearchMemory(bp, mem);
					}
					yield true;
				}
				yield false;
			}
			case TarkovNetworking.LOC_EXTERNAL_GRID -> {
				if (mode == MODE_OWN_INVENTORY && activeCaseUuid != null) {
					CaseHandle handle = resolveActiveCaseHandle();
					if (handle == null) yield false;
					GridContainer grid = handle.readGrid();
					GridEntry existing = grid.getAt(dstX, dstY);
					if (existing != null && canStackInto(existing.getStack(), stack)) {
						existing.getStack().increment(stack.getCount());
						handle.saveGrid(grid);
						yield true;
					}
					GridEntry placed = grid.place(stack, dstX, dstY, dstRotated);
					if (placed != null) {
						handle.saveGrid(grid);
						yield true;
					}
					yield false;
				}
				if (mode == MODE_GROUND_ITEM && groundItem != null && isSingleGridContainer(groundItem.getStack())) {
					ItemStack container = groundItem.getStack();
					GridContainer grid = readSingleContainerGrid(container);
					GridEntry placed = grid.place(stack, dstX, dstY, dstRotated);
					if (placed != null) {
						writeSingleContainerGrid(container, grid);
						if (ContainerItemHelper.requiresSearch(container)) {
							ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(container);
							mem.markEntryDiscovered(playerId, placed.getEntryId());
							ContainerItemHelper.saveSearchMemory(container, mem);
						}
						yield true;
					}
					yield false;
				}
				GridContainer grid = getPrimaryExternalGrid();
				ContainerSearchMemory mem = getPrimaryExternalMemory();
				if (grid == null || mem == null) yield false;
				GridEntry placed = grid.place(stack, dstX, dstY, dstRotated);
				if (placed != null) {
					mem.markEntryDiscovered(playerId, placed.getEntryId());
					yield true;
				}
				yield false;
			}
			case TarkovNetworking.LOC_CORPSE_RIG_GRID -> {
				if (mode == MODE_GROUND_ITEM && groundItem != null && groundItem.getStack().getItem() instanceof TacticalRigItem) {
					ItemStack rig = groundItem.getStack();
					List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
					GridEntry placed = grids.get(idx).place(stack, dstX, dstY, dstRotated);
					if (placed != null) {
						ContainerItemHelper.writeRigGrids(rig, grids);
						if (ContainerItemHelper.requiresSearch(rig)) {
							ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(rig);
							mem.markEntryDiscovered(playerId, placed.getEntryId());
							ContainerItemHelper.saveSearchMemory(rig, mem);
						}
						yield true;
					}
				}
				yield false;
			}
			default -> false;
		};
	}

	@Nullable
	private GridContainer getPrimaryExternalGrid() {
		if (mode == MODE_LOOT_BLOCK && lootBlock != null) return lootBlock.getGrid();
		if (mode == MODE_CORPSE && corpse != null) return corpse.getPocketsGrid();
		return null;
	}

	@Nullable
	private ContainerSearchMemory getPrimaryExternalMemory() {
		if (mode == MODE_LOOT_BLOCK && lootBlock != null) return lootBlock.getSearchMemory();
		if (mode == MODE_CORPSE && corpse != null) return corpse.getPocketsSearchMemory();
		return null;
	}

	private static boolean canStackInto(ItemStack target, ItemStack incoming) {
		return target.isStackable()
			&& ItemStack.canCombine(target, incoming)
			&& target.getCount() + incoming.getCount() <= target.getMaxCount();
	}

	@Nullable
	private static EquipmentSlotType slotByOrdinal(int ordinal) {
		EquipmentSlotType[] vals = EquipmentSlotType.values();
		return (ordinal >= 0 && ordinal < vals.length) ? vals[ordinal] : null;
	}

	private void markExternalDirty() {
		if (mode == MODE_LOOT_BLOCK && lootBlock != null) {
			lootBlock.markDirty();
		} else if (mode == MODE_CORPSE && corpse != null) {
			corpse.syncVisualFlags();
		}
	}

	private static PlayerLoadout getPlayerLoadout(PlayerEntity p) {
		return ((PlayerLoadoutHolder) p).tarkovcraft$getLoadout();
	}

	private static boolean isSingleGridContainer(ItemStack stack) {
		return !stack.isEmpty() && (stack.getItem() instanceof BackpackItem || stack.getItem() instanceof TarkovCaseItem);
	}

	private static GridContainer readSingleContainerGrid(ItemStack stack) {
		return stack.getItem() instanceof TarkovCaseItem
			? ContainerItemHelper.readCaseGrid(stack)
			: ContainerItemHelper.readBackpackGrid(stack);
	}

	private static void writeSingleContainerGrid(ItemStack stack, GridContainer grid) {
		if (stack.getItem() instanceof TarkovCaseItem) {
			ContainerItemHelper.writeCaseGrid(stack, grid);
		} else if (stack.getItem() instanceof BackpackItem) {
			ContainerItemHelper.writeBackpackGrid(stack, grid);
		}
	}

	private boolean canPlaceIntoCase(CaseHandle handle, ItemStack incoming) {
		if (activeCaseUuid != null && ContainerItemHelper.containsContainerId(incoming, activeCaseUuid)) {
			return false;
		}
		if (handle.isNestedInsideContainer()) {
			return !ContainerItemHelper.isContainerItem(incoming);
		}
		return ContainerItemHelper.getNestingDepth(incoming) < 2;
	}

	private interface CaseHandle {
		ItemStack getStack();
		boolean isNestedInsideContainer();
		GridContainer readGrid();
		void saveGrid(GridContainer grid);
	}

	@Nullable
	private CaseHandle resolveActiveCaseHandle() {
		if (activeCaseUuid == null || mode != MODE_OWN_INVENTORY) {
			return null;
		}
		UUID playerId = player.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(player);

		// 1. Equipment slots
		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			ItemStack eq = loadout.getEquipment(slot);
			if (!eq.isEmpty() && eq.getItem() instanceof TarkovCaseItem && activeCaseUuid.equals(ContainerItemHelper.getContainerUuid(eq))) {
				return new CaseHandle() {
					@Override
					public ItemStack getStack() {
						return eq;
					}

					@Override
					public boolean isNestedInsideContainer() {
						return false;
					}

					@Override
					public GridContainer readGrid() {
						return ContainerItemHelper.readCaseGrid(eq);
					}

					@Override
					public void saveGrid(GridContainer grid) {
						ContainerItemHelper.writeCaseGrid(eq, grid);
					}
				};
			}
		}

		// 2. Pockets (0..5)
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			ItemStack pocket = loadout.getPocket(i);
			if (!pocket.isEmpty() && pocket.getItem() instanceof TarkovCaseItem && activeCaseUuid.equals(ContainerItemHelper.getContainerUuid(pocket))) {
				return new CaseHandle() {
					@Override
					public ItemStack getStack() {
						return pocket;
					}

					@Override
					public boolean isNestedInsideContainer() {
						return false;
					}

					@Override
					public GridContainer readGrid() {
						return ContainerItemHelper.readCaseGrid(pocket);
					}

					@Override
					public void saveGrid(GridContainer grid) {
						ContainerItemHelper.writeCaseGrid(pocket, grid);
					}
				};
			}
		}

		// 3. Inside equipped Tactical Rig
		ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem
			&& (!ContainerItemHelper.requiresSearch(rig) || ContainerItemHelper.getSearchMemory(rig).isGridRevealed(playerId))) {
			List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rig);
			for (int gi = 0; gi < rigGrids.size(); gi++) {
				for (GridEntry entry : rigGrids.get(gi).getEntries()) {
					ItemStack st = entry.getStack();
					if (!st.isEmpty() && st.getItem() instanceof TarkovCaseItem && activeCaseUuid.equals(ContainerItemHelper.getContainerUuid(st))) {
						final int gridIndex = gi;
						final UUID entryId = entry.getEntryId();
						return new CaseHandle() {
							@Override
							public ItemStack getStack() {
								List<GridContainer> fresh = ContainerItemHelper.readRigGrids(rig);
								if (gridIndex >= 0 && gridIndex < fresh.size()) {
									GridEntry fe = fresh.get(gridIndex).getEntry(entryId);
									if (fe != null) return fe.getStack();
								}
								return st;
							}

							@Override
							public boolean isNestedInsideContainer() {
								return true;
							}

							@Override
							public GridContainer readGrid() {
								return ContainerItemHelper.readCaseGrid(getStack());
							}

							@Override
							public void saveGrid(GridContainer grid) {
								List<GridContainer> fresh = ContainerItemHelper.readRigGrids(rig);
								if (gridIndex >= 0 && gridIndex < fresh.size()) {
									GridEntry fe = fresh.get(gridIndex).getEntry(entryId);
									if (fe != null) {
										ContainerItemHelper.writeCaseGrid(fe.getStack(), grid);
										ContainerItemHelper.writeRigGrids(rig, fresh);
									}
								}
							}
						};
					}
				}
			}
		}

		// 4. Inside equipped Backpack
		ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
		if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem
			&& (!ContainerItemHelper.requiresSearch(bp) || ContainerItemHelper.getSearchMemory(bp).isGridRevealed(playerId))) {
			GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
			for (GridEntry entry : bpGrid.getEntries()) {
				ItemStack st = entry.getStack();
				if (!st.isEmpty() && st.getItem() instanceof TarkovCaseItem && activeCaseUuid.equals(ContainerItemHelper.getContainerUuid(st))) {
					final UUID entryId = entry.getEntryId();
					return new CaseHandle() {
						@Override
						public ItemStack getStack() {
							GridEntry fe = ContainerItemHelper.readBackpackGrid(bp).getEntry(entryId);
							return fe != null ? fe.getStack() : st;
						}

						@Override
						public boolean isNestedInsideContainer() {
							return true;
						}

						@Override
						public GridContainer readGrid() {
							return ContainerItemHelper.readCaseGrid(getStack());
						}

						@Override
						public void saveGrid(GridContainer grid) {
							GridContainer fresh = ContainerItemHelper.readBackpackGrid(bp);
							GridEntry fe = fresh.getEntry(entryId);
							if (fe != null) {
								ContainerItemHelper.writeCaseGrid(fe.getStack(), grid);
								ContainerItemHelper.writeBackpackGrid(bp, fresh);
							}
						}
					};
				}
			}
		}

		// 5. Creative vanilla inventory
		if (player.isCreative()) {
			PlayerInventory inv = player.getInventory();
			for (int i = 0; i < inv.main.size(); i++) {
				ItemStack st = inv.main.get(i);
				if (!st.isEmpty() && st.getItem() instanceof TarkovCaseItem && activeCaseUuid.equals(ContainerItemHelper.getContainerUuid(st))) {
					final int slotIdx = i;
					return new CaseHandle() {
						@Override
						public ItemStack getStack() {
							return inv.main.get(slotIdx);
						}

						@Override
						public boolean isNestedInsideContainer() {
							return false;
						}

						@Override
						public GridContainer readGrid() {
							return ContainerItemHelper.readCaseGrid(getStack());
						}

						@Override
						public void saveGrid(GridContainer grid) {
							ContainerItemHelper.writeCaseGrid(getStack(), grid);
							inv.markDirty();
						}
					};
				}
			}
			for (int i = 0; i < inv.offHand.size(); i++) {
				ItemStack st = inv.offHand.get(i);
				if (!st.isEmpty() && st.getItem() instanceof TarkovCaseItem && activeCaseUuid.equals(ContainerItemHelper.getContainerUuid(st))) {
					final int slotIdx = i;
					return new CaseHandle() {
						@Override
						public ItemStack getStack() {
							return inv.offHand.get(slotIdx);
						}

						@Override
						public boolean isNestedInsideContainer() {
							return false;
						}

						@Override
						public GridContainer readGrid() {
							return ContainerItemHelper.readCaseGrid(getStack());
						}

						@Override
						public void saveGrid(GridContainer grid) {
							ContainerItemHelper.writeCaseGrid(getStack(), grid);
							inv.markDirty();
						}
					};
				}
			}
		}

		this.activeCaseUuid = null;
		return null;
	}
}
