package com.tarkovcraft.screen;

import com.tarkovcraft.arena.PlayerProfileState;
import com.tarkovcraft.arena.RaidSession;
import com.tarkovcraft.lobby.LobbyManager;
import net.minecraft.screen.Property;

import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.container.VanillaContainerBridge;
import com.tarkovcraft.container.VanillaContainerState;
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
import com.tarkovcraft.loadout.SecureContainerValidator;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.network.TarkovNetworking;
import com.tarkovcraft.registry.ModScreenHandlers;
import com.tarkovcraft.search.ContainerSearchMemory;
import com.tarkovcraft.search.SearchSession;
import com.tarkovcraft.search.SearchTarget;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TarkovScreenHandler extends ScreenHandler {
	public static final byte MODE_OWN_INVENTORY = 0;
	public static final byte MODE_LOOT_BLOCK = 1;
	public static final byte MODE_CORPSE = 2;
	public static final byte MODE_GROUND_ITEM = 3;
	public static final byte MODE_VANILLA_CONTAINER = 4;
	public static final byte MODE_STASH = 5;
	public static final int STASH_PLAY = 0;
	public static final int STASH_EDITOR = 1;
	public static final int STASH_STORE_ALL = 2;
	public static final int STASH_SORT = 3;

	public static final int STASH_READY = 1;
	public static final int STASH_SERVICE = 2;
	public static final int STASH_CAN_EDIT = 4;

	public static final int MAX_OPEN_WINDOWS = 4;

	private final PlayerEntity player;
	private final byte mode;
	@Nullable
	private final SearchableLootBlockEntity lootBlock;
	@Nullable
	private final CorpseEntity corpse;
	@Nullable
	private final ItemEntity groundItem;
	@Nullable
	private final BlockPos vanillaBlockPos;
	@Nullable
	private final String vanillaTitle;
	@Nullable
	private final Inventory vanillaInventory;
	@Nullable
	private final VanillaContainerState.StoredVanillaContainer vanillaStored;
	@Nullable
	private final VanillaContainerState vanillaState;
	private final boolean vanillaIsShulker;
	@Nullable
	private final EnderChestBlockEntity vanillaEnderChestBe;
	private boolean vanillaOpened = false;
	private final SearchSession searchSession;
	private final Property stashFlags;
	private final Property stashScavSeconds;
	private final Property stashRaidSeconds;
	private final Property[] stashRubles;
	private int syncCooldown = 0;
	private final List<UUID> openContainerUuids = new ArrayList<>();

	private TarkovScreenHandler(
		int syncId,
		PlayerInventory playerInventory,
		byte mode,
		@Nullable SearchableLootBlockEntity lootBlock,
		@Nullable CorpseEntity corpse,
		@Nullable ItemEntity groundItem
	) {
		this(syncId, playerInventory, mode, lootBlock, corpse, groundItem, null, null, null, null, null, false, null);
	}

	private TarkovScreenHandler(
		int syncId,
		PlayerInventory playerInventory,
		byte mode,
		@Nullable SearchableLootBlockEntity lootBlock,
		@Nullable CorpseEntity corpse,
		@Nullable ItemEntity groundItem,
		@Nullable BlockPos vanillaBlockPos,
		@Nullable String vanillaTitle,
		@Nullable Inventory vanillaInventory,
		@Nullable VanillaContainerState.StoredVanillaContainer vanillaStored,
		@Nullable VanillaContainerState vanillaState,
		boolean vanillaIsShulker,
		@Nullable EnderChestBlockEntity vanillaEnderChestBe
	) {
		super(ModScreenHandlers.TARKOV_SCREEN_HANDLER, syncId);
		this.player = playerInventory.player;
		this.mode = mode;
		this.lootBlock = lootBlock;
		this.corpse = corpse;
		this.groundItem = groundItem;
		this.vanillaBlockPos = vanillaBlockPos;
		this.vanillaTitle = vanillaTitle;
		this.vanillaInventory = vanillaInventory;
		this.vanillaStored = vanillaStored;
		this.vanillaState = vanillaState;
		this.vanillaIsShulker = vanillaIsShulker;
		this.vanillaEnderChestBe = vanillaEnderChestBe;
		this.searchSession = new SearchSession(player.getUuid());
		this.stashFlags = mode == MODE_STASH ? addProperty(Property.create()) : Property.create();
		this.stashScavSeconds = mode == MODE_STASH ? addProperty(Property.create()) : Property.create();
		this.stashRaidSeconds = mode == MODE_STASH ? addProperty(Property.create()) : Property.create();
		this.stashRubles = new Property[mode == MODE_STASH ? 4 : 0];
		for (int i = 0; i < stashRubles.length; i++) stashRubles[i] = addProperty(Property.create());
		stashRaidSeconds.set(-1);
		if (mode == MODE_STASH && player instanceof ServerPlayerEntity serverPlayer) updateStashProperties(serverPlayer);
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

	public static TarkovScreenHandler forVanillaContainer(
		int syncId,
		PlayerInventory inv,
		BlockPos pos,
		String title,
		Inventory vanillaInventory,
		VanillaContainerState.StoredVanillaContainer stored,
		VanillaContainerState state,
		boolean isShulker,
		@Nullable EnderChestBlockEntity enderChestBe
	) {
		return new TarkovScreenHandler(
			syncId, inv, MODE_VANILLA_CONTAINER, null, null, null,
			pos, title, vanillaInventory, stored, state, isShulker, enderChestBe
		);
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
		if (mode == MODE_VANILLA_CONTAINER) {
			BlockPos pos = buf.readBlockPos();
			return new TarkovScreenHandler(syncId, inv, mode, null, null, null, pos, null, null, null, null, false, null);
		}
		if (mode == MODE_STASH) {
			var handler = new TarkovScreenHandler(syncId, inv, mode, null, null, null);
			handler.setStashRubles(buf.readLong());
			return handler;
		}
		return new TarkovScreenHandler(syncId, inv, MODE_OWN_INVENTORY, null, null, null);
	}

	public static void openOwnInventory(ServerPlayerEntity player) {
		openPersonalInventory(player, RaidSession.isLobby(player) ? MODE_STASH : MODE_OWN_INVENTORY, null);
	}

	public static void openStash(ServerPlayerEntity player) {
		if (RaidSession.isLobby(player) && player.isAlive()) openPersonalInventory(player, MODE_STASH, null);
	}

	private static void openPersonalInventory(ServerPlayerEntity player, byte mode, @Nullable UUID caseUuid) {
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
				buf.writeByte(mode);
				if (mode == MODE_STASH) buf.writeLong(PlayerProfileState.get(p.getServer()).rubles(p.getUuid()));
			}

			@Override
			public Text getDisplayName() {
				return mode == MODE_STASH ? Text.translatableWithFallback("screen.tarkovcraft.lobby.character", "Character")
					: Text.translatable("screen.tarkovcraft.inventory");
			}

			@Nullable
			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				var handler = new TarkovScreenHandler(syncId, inv, mode, null, null, null);
				if (caseUuid != null) handler.openContainerUuids.add(caseUuid);
				return handler;
			}
		});
		if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
			tsh.syncToClient(player);
		}
	}

	public static void openCaseScreen(ServerPlayerEntity player, UUID caseUuid) {
		openPersonalInventory(player, RaidSession.isLobby(player) ? MODE_STASH : MODE_OWN_INVENTORY, caseUuid);
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

	public static void openVanillaContainer(
		ServerPlayerEntity player,
		BlockPos pos,
		Text title,
		Inventory vanillaInventory,
		VanillaContainerState.StoredVanillaContainer stored,
		VanillaContainerState vcState,
		boolean isShulker,
		@Nullable EnderChestBlockEntity enderChestBe
	) {
		String titleStr = title.getString();
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
				buf.writeByte(MODE_VANILLA_CONTAINER);
				buf.writeBlockPos(pos);
			}

			@Override
			public Text getDisplayName() {
				return title;
			}

			@Nullable
			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return forVanillaContainer(syncId, inv, pos, titleStr, vanillaInventory, stored, vcState, isShulker, enderChestBe);
			}
		});
		if (player.currentScreenHandler instanceof TarkovScreenHandler tsh && tsh.mode == MODE_VANILLA_CONTAINER) {
			tsh.notifyVanillaContainerOpened(player);
			tsh.syncToClient(player);
		}
	}

	private void notifyVanillaContainerOpened(ServerPlayerEntity serverPlayer) {
		if (mode == MODE_VANILLA_CONTAINER && !vanillaOpened && vanillaInventory != null) {
			if (vanillaEnderChestBe != null) {
				serverPlayer.getEnderChestInventory().setActiveBlockEntity(vanillaEnderChestBe);
			}
			vanillaInventory.onOpen(serverPlayer);
			vanillaOpened = true;
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
		if (mode == MODE_STASH) {
			return this.player == player && player.isAlive()
				&& (!(player instanceof ServerPlayerEntity serverPlayer) || RaidSession.isLobby(serverPlayer));
		}
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
		if (mode == MODE_VANILLA_CONTAINER) {
			if (vanillaBlockPos == null || vanillaInventory == null || vanillaStored == null) {
				return false;
			}
			if (player.squaredDistanceTo(vanillaBlockPos.toCenterPos()) > 36.0) {
				return false;
			}
			if (!VanillaContainerBridge.isSupportedBlock(player.getWorld().getBlockState(vanillaBlockPos).getBlock())) {
				return false;
			}
			return vanillaInventory.canPlayerUse(player);
		}
		return true;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (mode != MODE_STASH || !(player instanceof ServerPlayerEntity owner)
			|| owner.currentScreenHandler != this || !canUse(owner)) return false;
		if (LobbyTabNavigation.handle(owner, id)) return true;
		switch (id) {
			case STASH_PLAY -> RaidScreenHandler.openLobby(owner);
			case STASH_EDITOR -> { return LobbyManager.toggleEditor(owner); }
			case STASH_STORE_ALL -> storeAll(owner);
			case STASH_SORT -> sortStash(owner);
			default -> { return false; }
		}
		return true;
	}

	public long stashRubles() {
		long balance = 0;
		for (int i = 0; i < stashRubles.length; i++) balance |= (stashRubles[i].get() & 0xffffL) << (i * 16);
		return balance;
	}

	private void setStashRubles(long balance) {
		for (int i = 0; i < stashRubles.length; i++) stashRubles[i].set((short) (balance >>> (i * 16)));
	}

	public int stashFlags() { return stashFlags.get(); }
	public int stashScavSeconds() { return stashScavSeconds.get(); }
	public int stashRaidSeconds() { return stashRaidSeconds.get(); }

	@Override
	public void sendContentUpdates() {
		if (mode == MODE_STASH && player instanceof ServerPlayerEntity owner) updateStashProperties(owner);
		super.sendContentUpdates();
	}

	private void updateStashProperties(ServerPlayerEntity owner) {
		int flags = 0;
		if (LobbyManager.isReady(owner)) flags |= STASH_READY;
		if (LobbyManager.needsService(owner)) flags |= STASH_SERVICE;
		if (owner.hasPermissionLevel(2)) flags |= STASH_CAN_EDIT;
		stashFlags.set(flags);
		PlayerProfileState profiles = PlayerProfileState.get(owner.getServer());
		setStashRubles(profiles.rubles(owner.getUuid()));
		stashScavSeconds.set(LobbyManager.scavStatusSeconds(owner));
		stashRaidSeconds.set(Math.min(Short.MAX_VALUE, RaidSession.remainingSeconds(owner.getServer())));
	}

	private void storeAll(ServerPlayerEntity owner) {
		GridContainer stash = getPrimaryExternalGrid();
		PlayerLoadout loadout = getPlayerLoadout(owner);
		boolean complete = true;
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT && complete; i++) {
			ItemStack stack = loadout.getPocket(i);
			if (!stack.isEmpty()) {
				complete = stash.tryAutoPlace(stack);
				if (stack.isEmpty()) loadout.setPocket(i, ItemStack.EMPTY);
			}
		}
		for (EquipmentSlotType slot : List.of(EquipmentSlotType.RIG, EquipmentSlotType.BACKPACK)) {
			if (!complete) break;
			ContainerHandle handle = wearableHandle(loadout.getEquipment(slot));
			if (handle == null) continue;
			List<GridContainer> grids = handle.readGrids();
			for (GridContainer grid : grids) {
				for (GridEntry entry : grid.getEntries()) {
					complete = stash.tryAutoPlace(entry.getStack());
					if (entry.getStack().isEmpty()) grid.remove(entry.getEntryId());
					if (!complete) break;
				}
				if (!complete) break;
			}
			handle.saveGrids(grids);
		}
		loadout.invalidateExposedCache();
		markExternalDirty();
		syncToClient(owner);
		if (!complete) owner.sendMessage(Text.translatableWithFallback("screen.tarkovcraft.lobby.stash_full",
			"The stash is full. Remaining items stayed in your inventory."), true);
	}

	private void sortStash(ServerPlayerEntity owner) {
		GridContainer stash = getPrimaryExternalGrid();
		var entries = new ArrayList<>(stash.getEntries());
		entries.sort(java.util.Comparator.<GridEntry>comparingInt(entry -> {
			var size = entry.getOccupiedSize();
			return size.width() * size.height();
		}).reversed().thenComparing(entry -> net.minecraft.registry.Registries.ITEM.getId(entry.getStack().getItem())));
		var sorted = new GridContainer(stash.getWidth(), stash.getHeight(), stash.getContainerId());
		for (GridEntry entry : entries) {
			GridEntry placed = sorted.tryPlaceInFreeCell(entry.getStack());
			if (placed == null) {
				owner.sendMessage(Text.translatableWithFallback("screen.tarkovcraft.lobby.stash_sort_full",
					"The current layout fits more items than the sorted layout."), true);
				return;
			}
		}
		var placements = sorted.getEntries();
		stash.clear();
		for (int i = 0; i < entries.size(); i++) {
			GridEntry placed = placements.get(i);
			stash.placeWithId(entries.get(i).getEntryId(), placed.getStack(), placed.getX(), placed.getY(), placed.isRotated());
		}
		markExternalDirty();
		syncToClient(owner);
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		if (mode == MODE_VANILLA_CONTAINER && vanillaOpened && vanillaInventory != null) {
			vanillaOpened = false;
			vanillaInventory.onClose(player);
		}
		searchSession.reset();
		openContainerUuids.clear();
	}

	public void tickServer(ServerPlayerEntity serverPlayer) {
		if (!canUse(serverPlayer)) {
			serverPlayer.closeHandledScreen();
			return;
		}

		boolean stateChanged = false;
		// Validate all open floating containers (drop stale ones if moved away/deleted)
		java.util.Iterator<UUID> it = openContainerUuids.iterator();
		while (it.hasNext()) {
			UUID uuid = it.next();
			if (resolveContainerHandle(uuid) == null) {
				it.remove();
				stateChanged = true;
			}
		}

		List<SearchTarget> externalTargets = getExternalSearchTargets();
		List<SearchTarget> ownTargets = getOwnSearchTargets(serverPlayer);

		boolean ownInventoryOpen = mode == MODE_OWN_INVENTORY || mode == MODE_STASH;
		if (searchSession.tickQueue(ownInventoryOpen, externalTargets, ownTargets)) {
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
		for (SearchTarget extT : externalTargets) {
			if (openContainerUuids.contains(extT.getTargetId()) && extT.isFullySearched(serverPlayer.getUuid())) {
				ContainerHandle handle = resolveContainerHandle(extT.getTargetId());
				if (handle != null && ContainerItemHelper.requiresSearch(handle.getStack())) {
					ContainerItemHelper.setRequiresSearch(handle.getStack(), false);
					handle.persistStackMutation();
					stateChanged = true;
				}
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
		List<SearchTarget> out = new ArrayList<>();
		java.util.Set<UUID> seen = new java.util.HashSet<>();
		if (mode == MODE_LOOT_BLOCK && lootBlock != null) {
			SearchTarget t = lootBlock.asSearchTarget();
			out.add(t);
			seen.add(t.getTargetId());
		} else if (mode == MODE_VANILLA_CONTAINER && vanillaStored != null) {
			GridContainer vg = vanillaStored.getGrid();
			ContainerSearchMemory vMem = vanillaStored.getSearchMemory();
			SearchTarget t = new SearchTarget(vg.getContainerId(), List.of(vg), vMem, () -> {
				if (vanillaState != null) vanillaState.markDirty();
			});
			out.add(t);
			seen.add(t.getTargetId());
		} else if (mode == MODE_CORPSE && corpse != null) {
			for (SearchTarget t : corpse.buildCorpseSearchTargets()) {
				out.add(t);
				seen.add(t.getTargetId());
			}
		} else if (mode == MODE_GROUND_ITEM && groundItem != null && !groundItem.getStack().isEmpty()) {
			ItemStack st = groundItem.getStack();
			if (ContainerItemHelper.requiresSearch(st)) {
				UUID id = ContainerItemHelper.getOrCreateContainerUuid(st);
				List<GridContainer> grids = ContainerItemHelper.readAllGrids(st);
				ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(st);
				out.add(new SearchTarget(id, grids, mem, () -> ContainerItemHelper.saveSearchMemory(st, mem)));
				seen.add(id);
			}
		}
		for (UUID openId : openContainerUuids) {
			if (seen.contains(openId)) continue;
			ContainerHandle handle = resolveContainerHandle(openId);
			if (handle != null && ContainerItemHelper.requiresSearch(handle.getStack())) {
				ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(handle.getStack());
				out.add(new SearchTarget(openId, handle.readGrids(), mem, () -> handle.saveSearchMemory(mem)));
				seen.add(openId);
			}
		}
		return out;
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
		PlayerLoadoutHolder holder = (PlayerLoadoutHolder) serverPlayer;
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		holder.tarkovcraft$syncLoadout();

		List<TarkovNetworking.GridView> ownRigViews = buildRigViews(loadout.getEquipment(EquipmentSlotType.RIG), playerId);
		TarkovNetworking.GridView ownBpView = buildBackpackView(loadout.getEquipment(EquipmentSlotType.BACKPACK), playerId);

		TarkovNetworking.GridView extView = null;
		String extTitle = "";
		PlayerLoadout corpseLoadout = null;
		List<TarkovNetworking.GridView> corpseRigViews = List.of();
		TarkovNetworking.GridView corpseBpView = null;

		if (mode == MODE_STASH) {
			GridContainer stash = getPrimaryExternalGrid();
			extView = TarkovNetworking.GridView.fromContainer(stash, stash.getContainerId(), null, playerId, false);
		} else if (mode == MODE_LOOT_BLOCK && lootBlock != null) {
			extTitle = lootBlock.getDisplayTitle().getString();
			extView = TarkovNetworking.GridView.fromContainer(
				lootBlock.getGrid(), lootBlock.getGrid().getContainerId(),
				lootBlock.getSearchMemory(), playerId, true
			);
		} else if (mode == MODE_VANILLA_CONTAINER && vanillaStored != null) {
			extTitle = vanillaTitle != null ? vanillaTitle : "";
			GridContainer vg = vanillaStored.getGrid();
			extView = TarkovNetworking.GridView.fromContainer(
				vg, vg.getContainerId(), vanillaStored.getSearchMemory(), playerId, true
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
		}

		List<TarkovNetworking.OpenContainerView> openViews = new ArrayList<>();
		var openIds = openContainerUuids.iterator();
		while (openIds.hasNext()) {
			UUID openId = openIds.next();
			ContainerHandle handle = resolveContainerHandle(openId);
			if (handle == null) {
				openIds.remove();
				continue;
			}
			ItemStack st = handle.getStack();
			boolean reqSearch = ContainerItemHelper.requiresSearch(st);
			ContainerSearchMemory mem = reqSearch ? ContainerItemHelper.getSearchMemory(st) : null;
			List<TarkovNetworking.GridView> gViews = new ArrayList<>();
			for (GridContainer gc : handle.readGrids()) {
				gViews.add(TarkovNetworking.GridView.fromContainer(gc, openId, mem, playerId, reqSearch));
			}
			openViews.add(new TarkovNetworking.OpenContainerView(openId, st.getName().getString(), gViews));
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
			searchSession.getProgressRatio(),
			openViews
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
			|| srcLoc == TarkovNetworking.LOC_CORPSE_BACKPACK_GRID
			|| srcLoc == TarkovNetworking.LOC_FLOATING_CONTAINER;

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

		if (mode == MODE_STASH && isOwnStorage(srcLoc)) {
			ItemStack stack = peek.copy();
			getPrimaryExternalGrid().tryAutoPlace(stack);
			int moved = peek.getCount() - stack.getCount();
			if (moved > 0) {
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
			ContainerSearchMemory mem = lootBlock.getSearchMemory();
			int moved = autoPlaceInRevealedGrids(List.of(lootBlock.getGrid()), copy, mem, playerId);
			if (moved > 0) {
				extractSource(loadout, srcLoc, srcIdx, srcEntryId, moved);
				lootBlock.markDirty();
				syncToClient(serverPlayer);
			}
			return;
		}
		if (mode == MODE_VANILLA_CONTAINER && vanillaStored != null && vanillaStored.getSearchMemory().isGridRevealed(playerId)) {
			if (isVanillaNestingBlocked(peek)) {
				return;
			}
			GridContainer vg = vanillaStored.getGrid();
			ContainerSearchMemory mem = vanillaStored.getSearchMemory();
			ItemStack copy = peek.copy();
			int before = copy.getCount();
			if (copy.isStackable()) {
				for (GridEntry entry : vg.getEntries()) {
					if (!mem.isEntryDiscovered(playerId, entry.getEntryId())) continue;
					if (ItemStack.canCombine(entry.getStack(), copy)) {
						int space = entry.getStack().getMaxCount() - entry.getStack().getCount();
						if (space > 0) {
							int transfer = Math.min(space, copy.getCount());
							entry.getStack().increment(transfer);
							copy.decrement(transfer);
							if (copy.isEmpty()) break;
						}
					}
				}
			}
			if (!copy.isEmpty() && canAddNewEntryToVanillaGrid(vg)) {
				GridEntry placed = vg.tryPlaceInFreeCell(copy);
				if (placed != null) {
					mem.markEntryDiscovered(playerId, placed.getEntryId());
					copy.setCount(0);
				}
			}
			int moved = before - copy.getCount();
			if (moved > 0) {
				extractSource(loadout, srcLoc, srcIdx, srcEntryId, moved);
				markExternalDirty();
				syncToClient(serverPlayer);
			} else if (!canAddNewEntryToVanillaGrid(vg)) {
				serverPlayer.sendMessage(Text.translatable("message.tarkovcraft.container_slot_limit"), true);
			}
			return;
		}
		if (mode == MODE_GROUND_ITEM && groundItem != null && ContainerItemHelper.isContainerItem(groundItem.getStack())) {
			ItemStack groundStack = groundItem.getStack();
			ContainerSearchMemory memory = searchMemoryFor(groundStack);
			if (memory == null || memory.isGridRevealed(playerId)) {
				ContainerHandle handle = directStackHandle(groundStack, false, () -> {});
				int moved = quickPlaceIntoContainer(handle, ContainerItemHelper.getContainerUuid(groundStack), peek, memory, playerId);
				if (moved > 0) {
					extractSource(loadout, srcLoc, srcIdx, srcEntryId, moved);
					syncToClient(serverPlayer);
				}
				return;
			}
		}

		if ((mode == MODE_OWN_INVENTORY || mode == MODE_STASH) && !openContainerUuids.isEmpty()
			&& (srcLoc == TarkovNetworking.LOC_POCKET || srcLoc == TarkovNetworking.LOC_RIG_GRID || srcLoc == TarkovNetworking.LOC_BACKPACK_GRID)) {
			UUID topUuid = openContainerUuids.get(openContainerUuids.size() - 1);
			UUID sourceContainer = srcLoc == TarkovNetworking.LOC_POCKET ? null : ContainerItemHelper.getContainerUuid(
				loadout.getEquipment(srcLoc == TarkovNetworking.LOC_RIG_GRID ? EquipmentSlotType.RIG : EquipmentSlotType.BACKPACK));
			ContainerHandle handle = topUuid.equals(sourceContainer) ? null : resolveContainerHandle(topUuid);
			if (handle != null) {
				int moved = quickPlaceIntoContainer(handle, topUuid, peek, searchMemoryFor(handle.getStack()), playerId);
				if (moved > 0) {
					extractSource(loadout, srcLoc, srcIdx, srcEntryId, moved);
					syncToClient(serverPlayer);
					return;
				}
			}
		}

		// 2. Own inventory transfer: Backpack <-> Rig/Pockets
		ItemStack transferCopy = peek.copy();
		int before = transferCopy.getCount();
		boolean movedBetweenContainers = false;
		if (srcLoc == TarkovNetworking.LOC_SECURE_CONTAINER) {
			loadout.tryInsertItem(transferCopy, playerId);
			movedBetweenContainers = transferCopy.getCount() < before;
		} else if (srcLoc == TarkovNetworking.LOC_BACKPACK_GRID) {
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

	private int quickPlaceIntoContainer(ContainerHandle handle, @Nullable UUID containerId, ItemStack source,
			@Nullable ContainerSearchMemory memory, UUID playerId) {
		if (!canPlaceIntoFloatingContainer(handle, containerId, source)) return 0;
		List<GridContainer> grids = handle.readGrids();
		int moved = autoPlaceInRevealedGrids(grids, source.copy(), memory, playerId);
		if (moved > 0) {
			handle.saveGrids(grids);
			if (memory != null) handle.saveSearchMemory(memory);
		}
		return moved;
	}

	static int autoPlaceInRevealedGrids(List<GridContainer> grids, ItemStack incoming,
			@Nullable ContainerSearchMemory memory, UUID playerId) {
		if (memory != null && !memory.isGridRevealed(playerId)) return 0;
		int count = incoming.getCount();
		for (GridContainer grid : grids) {
			Set<UUID> existingIds = new HashSet<>();
			if (memory != null) {
				for (GridEntry entry : grid.getEntries()) existingIds.add(entry.getEntryId());
			}
			grid.tryAutoPlace(incoming, memory == null ? null : id -> memory.isEntryDiscovered(playerId, id));
			if (incoming.getCount() < count) {
				if (memory != null) {
					for (GridEntry entry : grid.getEntries()) {
						if (!existingIds.contains(entry.getEntryId())) memory.markEntryDiscovered(playerId, entry.getEntryId());
					}
				}
				return count - incoming.getCount();
			}
		}
		return 0;
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

		if (EquipmentSlotType.HEADSET.accepts(peek) && loadout.blocksHeadset()) {
			serverPlayer.sendMessage(Text.translatable("message.tarkovcraft.headset_blocked"), true);
			return;
		}
		EquipmentSlotType targetSlot = findEquipOrHotswapSlotFor(loadout, peek);
		if (targetSlot == null) return;

		ItemStack removed = extractSource(loadout, srcLoc, srcIdx, srcEntryId, peek.getCount());
		if (!removed.isEmpty()) {
			ItemStack droppedOld = equipWithHotswap(loadout, playerId, targetSlot, removed);
			if (!droppedOld.isEmpty()) {
				serverPlayer.dropItem(droppedOld, false);
				serverPlayer.sendMessage(
					Text.translatable("message.tarkovcraft.hotswap_dropped", droppedOld.getName())
						.formatted(net.minecraft.util.Formatting.YELLOW),
					true
				);
			}
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
		if (peek.getItem() instanceof TarkovMedicalItem || peek.getItem() instanceof TarkovStimulatorItem) {
			com.tarkovcraft.health.MedicalUseManager.startFromInventory(serverPlayer, srcLoc, srcIdx, srcEntryId, null);
			return;
		}
		if (peek.getItem() instanceof TarkovArmorRepairItem) {
			TarkovArmorRepairItem.start(serverPlayer,
				() -> isOwnStorage(srcLoc) || serverPlayer.currentScreenHandler == this && canUse(serverPlayer)
					? peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId) : ItemStack.EMPTY,
				result -> replaceRepairKit(loadout, srcLoc, srcIdx, srcEntryId, result));
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
		if (!canUse(serverPlayer)) return;
		UUID playerId = serverPlayer.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);
		// peekSource enforces isGridRevealed + isEntryDiscovered on all external/unsearched containers
		ItemStack peek = peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId);
		if (!ContainerItemHelper.isContainerItem(peek)) {
			return;
		}
		UUID foundUuid = ensureAndFetchContainerUuid(loadout, srcLoc, srcIdx, srcEntryId);
		if (foundUuid == null) return;

		openContainerUuids.remove(foundUuid);
		openContainerUuids.add(foundUuid);
		while (openContainerUuids.size() > MAX_OPEN_WINDOWS) {
			openContainerUuids.remove(0);
		}
		syncToClient(serverPlayer);
	}

	@Nullable
	private UUID ensureAndFetchContainerUuid(PlayerLoadout loadout, byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		return switch (srcLoc) {
			case TarkovNetworking.LOC_POCKET -> {
				ItemStack st = loadout.getPocket(srcIdx);
				yield ContainerItemHelper.isContainerItem(st) ? ContainerItemHelper.getOrCreateContainerUuid(st) : null;
			}
			case TarkovNetworking.LOC_EQUIPMENT -> {
				EquipmentSlotType slot = slotByOrdinal(srcIdx);
				if (slot == null) yield null;
				ItemStack st = loadout.getEquipment(slot);
				yield ContainerItemHelper.isContainerItem(st) ? ContainerItemHelper.getOrCreateContainerUuid(st) : null;
			}
			case TarkovNetworking.LOC_CORPSE_EQUIPMENT -> {
				if (mode != MODE_CORPSE || corpse == null) yield null;
				EquipmentSlotType slot = slotByOrdinal(srcIdx);
				if (slot == null) yield null;
				ItemStack st = corpse.getLoadout().getEquipment(slot);
				yield ContainerItemHelper.isContainerItem(st) ? ContainerItemHelper.getOrCreateContainerUuid(st) : null;
			}
			case TarkovNetworking.LOC_SECURE_CONTAINER -> {
				GridEntry entry = srcIdx == 0 && srcEntryId != null ? loadout.getSecureContainer().getEntry(srcEntryId) : null;
				yield entry != null && ContainerItemHelper.isContainerItem(entry.getStack())
					? ContainerItemHelper.getOrCreateContainerUuid(entry.getStack()) : null;
			}
			case TarkovNetworking.LOC_RIG_GRID -> ensureUuidInRig(loadout.getEquipment(EquipmentSlotType.RIG), srcIdx, srcEntryId);
			case TarkovNetworking.LOC_BACKPACK_GRID -> ensureUuidInSingleContainer(loadout.getEquipment(EquipmentSlotType.BACKPACK), srcEntryId);
			case TarkovNetworking.LOC_CORPSE_RIG_GRID -> {
				ItemStack rig = (mode == MODE_CORPSE && corpse != null)
					? corpse.getLoadout().getEquipment(EquipmentSlotType.RIG)
					: (mode == MODE_GROUND_ITEM && groundItem != null ? groundItem.getStack() : ItemStack.EMPTY);
				yield ensureUuidInRig(rig, srcIdx, srcEntryId);
			}
			case TarkovNetworking.LOC_CORPSE_BACKPACK_GRID -> (mode == MODE_CORPSE && corpse != null)
				? ensureUuidInSingleContainer(corpse.getLoadout().getEquipment(EquipmentSlotType.BACKPACK), srcEntryId)
				: null;
			case TarkovNetworking.LOC_EXTERNAL_GRID -> {
				if (srcEntryId == null) yield null;
				if (mode == MODE_GROUND_ITEM && groundItem != null) {
					yield ensureUuidInSingleContainer(groundItem.getStack(), srcEntryId);
				}
				GridContainer grid = getPrimaryExternalGrid();
				if (grid == null) yield null;
				GridEntry entry = grid.getEntry(srcEntryId);
				if (entry == null || !ContainerItemHelper.isContainerItem(entry.getStack())) yield null;
				UUID id = ContainerItemHelper.getOrCreateContainerUuid(entry.getStack());
				markExternalDirty();
				yield id;
			}
			case TarkovNetworking.LOC_FLOATING_CONTAINER -> {
				if (srcEntryId == null) yield null;
				int winIdx = srcIdx >> 8;
				int subIdx = srcIdx & 0xFF;
				ContainerHandle handle = resolveOpenWindowHandle(winIdx);
				if (handle == null) yield null;
				List<GridContainer> grids = handle.readGrids();
				if (subIdx < 0 || subIdx >= grids.size()) yield null;
				GridEntry entry = grids.get(subIdx).getEntry(srcEntryId);
				if (entry == null || !ContainerItemHelper.isContainerItem(entry.getStack())) yield null;
				boolean had = ContainerItemHelper.getContainerUuid(entry.getStack()) != null;
				UUID id = ContainerItemHelper.getOrCreateContainerUuid(entry.getStack());
				if (!had) {
					handle.saveGrids(grids);
				}
				yield id;
			}
			default -> null;
		};
	}

	@Nullable
	private static UUID ensureUuidInRig(ItemStack rig, int idx, @Nullable UUID entryId) {
		if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem) || entryId == null) return null;
		List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
		if (idx < 0 || idx >= grids.size()) return null;
		GridEntry entry = grids.get(idx).getEntry(entryId);
		if (entry == null || !ContainerItemHelper.isContainerItem(entry.getStack())) return null;
		boolean had = ContainerItemHelper.getContainerUuid(entry.getStack()) != null;
		UUID id = ContainerItemHelper.getOrCreateContainerUuid(entry.getStack());
		if (!had) {
			ContainerItemHelper.writeRigGrids(rig, grids);
		}
		return id;
	}

	@Nullable
	private static UUID ensureUuidInSingleContainer(ItemStack container, @Nullable UUID entryId) {
		if (!isSingleGridContainer(container) || entryId == null) return null;
		GridContainer grid = readSingleContainerGrid(container);
		GridEntry entry = grid.getEntry(entryId);
		if (entry == null || !ContainerItemHelper.isContainerItem(entry.getStack())) return null;
		boolean had = ContainerItemHelper.getContainerUuid(entry.getStack()) != null;
		UUID id = ContainerItemHelper.getOrCreateContainerUuid(entry.getStack());
		if (!had) {
			writeSingleContainerGrid(container, grid);
		}
		return id;
	}

	public void handleCloseCase(ServerPlayerEntity serverPlayer, @Nullable UUID containerUuid) {
		if (containerUuid != null) {
			openContainerUuids.remove(containerUuid);
		} else if (!openContainerUuids.isEmpty()) {
			openContainerUuids.remove(openContainerUuids.size() - 1);
		}
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
		if (!canUse(serverPlayer) || serverPlayer.isSpectator() || !serverPlayer.isAlive()) return;
		if (!isOwnStorage(srcLoc) && !(mode == MODE_STASH && srcLoc == TarkovNetworking.LOC_EXTERNAL_GRID)) return;
		com.tarkovcraft.health.MedicalUseManager.startFromInventory(serverPlayer, srcLoc, srcIdx, srcEntryId, targetPart);
	}

	private static boolean isOwnStorage(byte loc) {
		return loc == TarkovNetworking.LOC_POCKET || loc == TarkovNetworking.LOC_RIG_GRID
			|| loc == TarkovNetworking.LOC_BACKPACK_GRID || loc == TarkovNetworking.LOC_SECURE_CONTAINER;
	}

	private void replaceRepairKit(PlayerLoadout loadout, byte loc, int idx,
			@Nullable UUID entryId, ItemStack result) {
		if (loc == TarkovNetworking.LOC_POCKET) {
			loadout.setPocket(idx, result);
		} else if (loc == TarkovNetworking.LOC_EQUIPMENT || loc == TarkovNetworking.LOC_CORPSE_EQUIPMENT) {
			PlayerLoadout owner = loc == TarkovNetworking.LOC_EQUIPMENT ? loadout : corpse.getLoadout();
			owner.setEquipment(slotByOrdinal(idx), result);
		} else if (loc == TarkovNetworking.LOC_SECURE_CONTAINER) {
			replaceGridStack(loadout.getSecureContainer(), entryId, result);
		} else if (loc == TarkovNetworking.LOC_EXTERNAL_GRID && mode != MODE_GROUND_ITEM) {
			replaceGridStack(getPrimaryExternalGrid(), entryId, result);
		} else {
			ContainerHandle handle = switch (loc) {
				case TarkovNetworking.LOC_CORPSE_RIG_GRID -> mode == MODE_CORPSE
					? wearableHandle(corpse.getLoadout().getEquipment(EquipmentSlotType.RIG))
					: resolveContainerDestination(loadout, loc, idx);
				case TarkovNetworking.LOC_CORPSE_BACKPACK_GRID -> wearableHandle(corpse.getLoadout().getEquipment(EquipmentSlotType.BACKPACK));
				default -> resolveContainerDestination(loadout, loc, idx);
			};
			if (handle == null) return;
			List<GridContainer> grids = handle.readGrids();
			replaceGridStack(grids.get(getDestinationGridIndex(loc, idx)), entryId, result);
			handle.saveGrids(grids);
		}
		loadout.invalidateExposedCache();
		if (!isOwnStorage(loc)) markExternalDirty();
	}

	private static void replaceGridStack(GridContainer grid, UUID id, ItemStack result) {
		if (result.isEmpty()) {
			grid.remove(id);
		} else {
			grid.getEntry(id).setStack(result);
		}
	}

	public ItemStack peekSourceForMed(ServerPlayerEntity serverPlayer, byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		if (!canUse(serverPlayer)) return ItemStack.EMPTY;
		return peekSource(getPlayerLoadout(serverPlayer), serverPlayer.getUuid(), srcLoc, srcIdx, srcEntryId);
	}

	public void completeTimedMedUse(
		ServerPlayerEntity serverPlayer,
		byte srcLoc, int srcIdx, @Nullable UUID srcEntryId,
		@Nullable BodyPart targetPart,
		boolean isStimulator
	) {
		if (!canUse(serverPlayer) || serverPlayer.isSpectator() || !serverPlayer.isAlive()) return;
		if (!(serverPlayer instanceof PlayerLoadoutHolder holder)) return;

		UUID playerId = serverPlayer.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(serverPlayer);
		BodyPartHealth health = holder.tarkovcraft$getBodyHealth();

		if (isStimulator) {
			ItemStack peek = peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId);
			if (peek.getItem() instanceof TarkovStimulatorItem) {
				ItemStack toUse = peek.copyWithCount(1);
				if (TarkovStimulatorItem.applyStimulator(serverPlayer, toUse)) {
					if (!serverPlayer.isCreative()) {
						extractSource(loadout, srcLoc, srcIdx, srcEntryId, 1);
					}
					markExternalDirty();
					syncToClient(serverPlayer);
				}
			}
			return;
		}

		ItemStack peek = peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId);
		if (!(peek.getItem() instanceof TarkovMedicalItem medItem)) return;

		MedicalHelper.ApplyResult res = mutateMedicalInContainer(
			loadout, playerId, health, srcLoc, srcIdx, srcEntryId, targetPart, true, serverPlayer.isCreative()
		);
		if (!res.applied()) {
			holder.tarkovcraft$syncBodyHealth();
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
			case TarkovNetworking.LOC_SECURE_CONTAINER -> {
				GridContainer grid = loadout.getSecureContainer();
				GridEntry entry = srcIdx == 0 && srcEntryId != null ? grid.getEntry(srcEntryId) : null;
				if (entry == null) yield failed;
				MedicalHelper.ApplyResult res = MedicalHelper.applyMedical(health, entry.getStack(), targetPart, isCreative);
				if (res.applied() && entry.getStack().isEmpty()) grid.remove(srcEntryId);
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
			case TarkovNetworking.LOC_FLOATING_CONTAINER -> {
				if (!allowExternal || srcEntryId == null) yield failed;
				int winIdx = srcIdx >> 8;
				int subIdx = srcIdx & 0xFF;
				ContainerHandle handle = resolveOpenWindowHandle(winIdx);
				if (handle == null) yield failed;
				ItemStack st = handle.getStack();
				if (ContainerItemHelper.requiresSearch(st)) {
					ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(st);
					if (!mem.isGridRevealed(playerId) || !mem.isEntryDiscovered(playerId, srcEntryId)) yield failed;
				}
				List<GridContainer> grids = handle.readGrids();
				if (subIdx < 0 || subIdx >= grids.size()) yield failed;
				GridEntry entry = grids.get(subIdx).getEntry(srcEntryId);
				if (entry == null) yield failed;
				MedicalHelper.ApplyResult res = MedicalHelper.applyMedical(health, entry.getStack(), targetPart, isCreative);
				if (res.applied()) {
					if (entry.getStack().isEmpty()) {
						grids.get(subIdx).remove(srcEntryId);
					}
					handle.saveGrids(grids);
				}
				yield res;
			}
			case TarkovNetworking.LOC_EXTERNAL_GRID -> {
				if (!allowExternal || srcEntryId == null) yield failed;
				if (mode == MODE_GROUND_ITEM && groundItem != null) {
					yield applyMedicalInSingleContainer(groundItem.getStack(), playerId, health, srcEntryId, targetPart, isCreative);
				}
				GridContainer grid = getPrimaryExternalGrid();
				ContainerSearchMemory mem = getPrimaryExternalMemory();
				if (grid == null || !isEntryVisible(mem, playerId, srcEntryId)) {
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
		if (srcLoc == TarkovNetworking.LOC_SECURE_CONTAINER && PlayerLoadout.canPlaceInSecureContainer(splitCopy)) {
			if (loadout.getSecureContainer().tryPlaceInFreeCell(splitCopy) != null) {
				loadout.invalidateExposedCache();
				return true;
			}
		} else if (srcLoc == TarkovNetworking.LOC_RIG_GRID) {
			if (tryPlaceSplitInRig(loadout.getEquipment(EquipmentSlotType.RIG), playerId, srcIdx, splitCopy)) return true;
		} else if (srcLoc == TarkovNetworking.LOC_BACKPACK_GRID) {
			if (tryPlaceSplitInBackpack(loadout.getEquipment(EquipmentSlotType.BACKPACK), playerId, splitCopy)) return true;
		} else if (srcLoc == TarkovNetworking.LOC_FLOATING_CONTAINER) {
			int winIdx = srcIdx >> 8;
			int subIdx = srcIdx & 0xFF;
			ContainerHandle handle = resolveOpenWindowHandle(winIdx);
			if (handle != null && canPlaceIntoFloatingContainer(handle, openContainerUuids.get(winIdx), splitCopy)) {
				List<GridContainer> grids = handle.readGrids();
				if (subIdx >= 0 && subIdx < grids.size()) {
					GridEntry created = grids.get(subIdx).tryPlaceInFreeCell(splitCopy);
					if (created != null) {
						handle.saveGrids(grids);
						if (ContainerItemHelper.requiresSearch(handle.getStack())) {
							ContainerSearchMemory mem = ContainerItemHelper.getSearchMemory(handle.getStack());
							mem.markEntryDiscovered(playerId, created.getEntryId());
							handle.saveSearchMemory(mem);
						}
						return true;
					}
				}
			}
		} else if (srcLoc == TarkovNetworking.LOC_EXTERNAL_GRID) {
			if (mode == MODE_GROUND_ITEM && groundItem != null) {
				if (tryPlaceSplitInSingleContainer(groundItem.getStack(), playerId, splitCopy)) return true;
			} else {
				GridContainer g = getPrimaryExternalGrid();
				ContainerSearchMemory mem = getPrimaryExternalMemory();
				if (g != null && (mode == MODE_STASH || mem != null && mem.isGridRevealed(playerId))
					&& !isVanillaNestingBlocked(splitCopy)
					&& canAddNewEntryToVanillaGrid(g)) {
					GridEntry created = g.tryPlaceInFreeCell(splitCopy);
					if (created != null) {
						if (mem != null) mem.markEntryDiscovered(playerId, created.getEntryId());
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
			EquipmentSlotType.HELMET, EquipmentSlotType.FACE_MASK, EquipmentSlotType.HEADSET, EquipmentSlotType.BODY_ARMOR,
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
	private static EquipmentSlotType findEquipOrHotswapSlotFor(PlayerLoadout loadout, ItemStack stack) {
		if (stack.getItem() instanceof BackpackItem) {
			return EquipmentSlotType.BACKPACK;
		}
		if (stack.getItem() instanceof TacticalRigItem) {
			return EquipmentSlotType.RIG;
		}
		return findEmptyEquipSlotFor(loadout, stack);
	}

	/**
	 * Equips {@code newStack} into {@code slot}. If {@code slot} already holds an item,
	 * tries to place the previous item safely into the player's inventory (Pockets -> Rig -> new Backpack,
	 * enforcing container nesting/recursion rules). Returns the previous stack if it could not fit
	 * (caller must drop it with all its contents), or {@link ItemStack#EMPTY} if it fit or slot was empty.
	 */
	public static ItemStack equipWithHotswap(
		PlayerLoadout loadout,
		@Nullable UUID playerId,
		EquipmentSlotType slot,
		ItemStack newStack
	) {
		ItemStack oldStack = loadout.getEquipment(slot).copy();
		loadout.setEquipment(slot, newStack);
		if (oldStack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack toStore = oldStack.copy();
		if (tryInsertContainerSafely(loadout, playerId, toStore) && toStore.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return oldStack;
	}

	/**
	 * Safely inserts an unequipped item (including a backpack or rig with its loot) into
	 * Pockets -> Rig -> Backpack while enforcing anti-recursion and max nesting depth (&lt; 2 inside worn containers).
	 */
	public static boolean tryInsertContainerSafely(
		PlayerLoadout loadout,
		@Nullable UUID playerId,
		ItemStack incoming
	) {
		if (incoming == null || incoming.isEmpty()) {
			return false;
		}
		if (!ContainerItemHelper.isContainerItem(incoming)) {
			return loadout.tryInsertItem(incoming, playerId);
		}

		// 1. Pockets (if 1x1)
		if (loadout.canPlaceInPocket(incoming)) {
			for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
				if (loadout.getPocket(i).isEmpty()) {
					loadout.setPocket(i, incoming.copy());
					incoming.setCount(0);
					return true;
				}
			}
		}

		// Anti-nesting rule: only containers of depth < 2 (i.e. not already holding another container) can sit inside Rig/Backpack
		if (ContainerItemHelper.getNestingDepth(incoming) >= 2) {
			return false;
		}

		// 2. Equipped Rig
		ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
			UUID rigId = ContainerItemHelper.getContainerUuid(rig);
			boolean revealed = !ContainerItemHelper.requiresSearch(rig)
				|| (playerId != null && ContainerItemHelper.getSearchMemory(rig).isGridRevealed(playerId));
			if (revealed && (rigId == null || !ContainerItemHelper.containsContainerId(incoming, rigId))) {
				List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
				for (GridContainer grid : grids) {
					if (grid.tryAutoPlace(incoming)) {
						ContainerItemHelper.writeRigGrids(rig, grids);
						if (incoming.isEmpty()) {
							return true;
						}
						break;
					}
				}
			}
		}

		// 3. Equipped Backpack
		ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
		if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem) {
			UUID bpId = ContainerItemHelper.getContainerUuid(bp);
			boolean revealed = !ContainerItemHelper.requiresSearch(bp)
				|| (playerId != null && ContainerItemHelper.getSearchMemory(bp).isGridRevealed(playerId));
			if (revealed && (bpId == null || !ContainerItemHelper.containsContainerId(incoming, bpId))) {
				GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
				if (bpGrid.tryAutoPlace(incoming)) {
					ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
					if (incoming.isEmpty()) {
						return true;
					}
				}
			}
		}

		return incoming.isEmpty();
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
			EquipmentSlotType.HELMET, EquipmentSlotType.FACE_MASK, EquipmentSlotType.HEADSET, EquipmentSlotType.BODY_ARMOR,
			EquipmentSlotType.PISTOL, EquipmentSlotType.PRIMARY_1, EquipmentSlotType.PRIMARY_2, EquipmentSlotType.MELEE
		}) {
			if (loadout.getEquipment(slot).isEmpty() && loadout.canEquip(slot, stack)) {
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

		GridEntry sourceEntry = isGridLocation(srcLoc) ? findSourceGridEntry(loadout, playerId, srcLoc, srcIdx, srcEntryId) : null;
		ItemStack sourcePeek = sourceEntry != null ? sourceEntry.getStack() : peekNonGridSource(loadout, srcLoc, srcIdx);
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

		if (dstLoc == TarkovNetworking.LOC_EQUIPMENT
			&& (isPartialSplit || (srcLoc == TarkovNetworking.LOC_EQUIPMENT && srcIdx == dstIdx))) {
			return;
		}

		// 3. Validate destination BEFORE mutating source
		if (dstLoc == TarkovNetworking.LOC_EQUIPMENT && slotByOrdinal(dstIdx) == EquipmentSlotType.HEADSET
			&& loadout.blocksHeadset()) {
			serverPlayer.sendMessage(Text.translatable("message.tarkovcraft.headset_blocked"), true);
			return;
		}

		if (!canInsertIntoDestination(loadout, playerId, movingPreview, dstLoc, dstIdx, dstX, dstY, dstRotated)) {
			if (mode == MODE_VANILLA_CONTAINER && dstLoc == TarkovNetworking.LOC_EXTERNAL_GRID && vanillaStored != null) {
				GridContainer vg = vanillaStored.getGrid();
				if (!canAddNewEntryToVanillaGrid(vg)
					&& !isVanillaNestingBlocked(movingPreview)
					&& vg.canPlace(movingPreview, dstX, dstY, dstRotated)) {
					serverPlayer.sendMessage(Text.translatable("message.tarkovcraft.container_slot_limit"), true);
				}
			}
			return;
		}

		// 4. Execute atomic transfer (partial split or full move)
		ItemStack removed = extractSource(loadout, srcLoc, srcIdx, srcEntryId, moveCount);
		if (removed.isEmpty()) {
			return;
		}
		if (dstLoc == TarkovNetworking.LOC_EQUIPMENT) {
			EquipmentSlotType dstSlot = slotByOrdinal(dstIdx);
			ItemStack previous = loadout.getEquipment(dstSlot);
			loadout.setEquipment(dstSlot, removed);
			if (!previous.isEmpty() && !returnSwappedItem(loadout, playerId, previous, dstSlot, srcLoc, srcIdx, sourceEntry)) {
				ItemStack remainder = previous.copy();
				tryInsertContainerSafely(loadout, playerId, remainder);
				if (!remainder.isEmpty()) {
					serverPlayer.dropItem(remainder, false);
					serverPlayer.sendMessage(Text.translatable("message.tarkovcraft.hotswap_dropped", remainder.getName())
						.formatted(net.minecraft.util.Formatting.YELLOW), true);
				}
			}
		} else if (!insertIntoDestination(loadout, playerId, removed, dstLoc, dstIdx, dstX, dstY, dstRotated)) {
			serverPlayer.dropItem(removed, false);
		}

		markExternalDirty();
		syncToClient(serverPlayer);
	}

	private boolean returnSwappedItem(PlayerLoadout loadout, UUID playerId, ItemStack previous, EquipmentSlotType replacedSlot,
			byte sourceLoc, int sourceIdx, @Nullable GridEntry sourceEntry) {
		if (sourceLoc == TarkovNetworking.LOC_EQUIPMENT || sourceLoc == TarkovNetworking.LOC_CORPSE_EQUIPMENT) {
			EquipmentSlotType sourceSlot = slotByOrdinal(sourceIdx);
			PlayerLoadout sourceLoadout = sourceLoc == TarkovNetworking.LOC_EQUIPMENT ? loadout : corpse.getLoadout();
			if (sourceSlot != null && sourceLoadout.getEquipment(sourceSlot).isEmpty() && sourceLoadout.canEquip(sourceSlot, previous)) {
				sourceLoadout.setEquipment(sourceSlot, previous);
				return true;
			}
			return false;
		}
		if (sourceLoc == TarkovNetworking.LOC_POCKET) {
			return loadout.getPocket(sourceIdx).isEmpty() && loadout.setPocket(sourceIdx, previous);
		}
		if (sourceEntry == null
			|| (sourceLoc == TarkovNetworking.LOC_RIG_GRID && replacedSlot == EquipmentSlotType.RIG)
			|| (sourceLoc == TarkovNetworking.LOC_BACKPACK_GRID && replacedSlot == EquipmentSlotType.BACKPACK)) {
			// The original worn compartment can belong to the item being taken off.
			return false;
		}
		int x = sourceEntry.getX();
		int y = sourceEntry.getY();
		boolean rotated = sourceEntry.isRotated();
		if (mode == MODE_CORPSE && corpse != null
			&& (sourceLoc == TarkovNetworking.LOC_CORPSE_RIG_GRID || sourceLoc == TarkovNetworking.LOC_CORPSE_BACKPACK_GRID)) {
			boolean rig = sourceLoc == TarkovNetworking.LOC_CORPSE_RIG_GRID;
			ItemStack container = corpse.getLoadout().getEquipment(rig ? EquipmentSlotType.RIG : EquipmentSlotType.BACKPACK);
			ContainerHandle handle = wearableHandle(container);
			int gridIndex = rig ? sourceIdx : 0;
			return placeInContainerGrid(handle, gridIndex, previous, playerId, x, y, rotated)
				|| placeInContainerGrid(handle, gridIndex, previous, playerId, x, y, !rotated);
		}
		if (canInsertIntoDestination(loadout, playerId, previous, sourceLoc, sourceIdx, x, y, rotated)) {
			return insertIntoDestination(loadout, playerId, previous, sourceLoc, sourceIdx, x, y, rotated);
		}
		return canInsertIntoDestination(loadout, playerId, previous, sourceLoc, sourceIdx, x, y, !rotated)
			&& insertIntoDestination(loadout, playerId, previous, sourceLoc, sourceIdx, x, y, !rotated);
	}

	private boolean isGridLocation(byte loc) {
		return loc == TarkovNetworking.LOC_RIG_GRID
			|| loc == TarkovNetworking.LOC_BACKPACK_GRID
			|| loc == TarkovNetworking.LOC_EXTERNAL_GRID
			|| loc == TarkovNetworking.LOC_CORPSE_RIG_GRID
			|| loc == TarkovNetworking.LOC_CORPSE_BACKPACK_GRID
			|| loc == TarkovNetworking.LOC_FLOATING_CONTAINER
			|| loc == TarkovNetworking.LOC_SECURE_CONTAINER;
	}

	private ItemStack peekSource(PlayerLoadout loadout, UUID playerId, byte loc, int idx, @Nullable UUID entryId) {
		if (isGridLocation(loc)) {
			GridEntry entry = findSourceGridEntry(loadout, playerId, loc, idx, entryId);
			return entry != null ? entry.getStack() : ItemStack.EMPTY;
		}
		return peekNonGridSource(loadout, loc, idx);
	}

	private ItemStack peekNonGridSource(PlayerLoadout loadout, byte loc, int idx) {
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
			default -> ItemStack.EMPTY;
		};
	}

	@Nullable
	private GridEntry findSourceGridEntry(PlayerLoadout loadout, UUID playerId, byte loc, int idx, @Nullable UUID entryId) {
		if (entryId == null) return null;
		return switch (loc) {
			case TarkovNetworking.LOC_SECURE_CONTAINER -> idx == 0 ? loadout.getSecureContainer().getEntry(entryId) : null;
			case TarkovNetworking.LOC_RIG_GRID -> findRigEntry(loadout.getEquipment(EquipmentSlotType.RIG), playerId, idx, entryId);
			case TarkovNetworking.LOC_BACKPACK_GRID -> findBackpackEntry(loadout.getEquipment(EquipmentSlotType.BACKPACK), playerId, entryId);
			case TarkovNetworking.LOC_CORPSE_RIG_GRID -> {
				if (mode == MODE_CORPSE && corpse != null) {
					yield findRigEntry(corpse.getLoadout().getEquipment(EquipmentSlotType.RIG), playerId, idx, entryId);
				}
				if (mode == MODE_GROUND_ITEM && groundItem != null) {
					yield findRigEntry(groundItem.getStack(), playerId, idx, entryId);
				}
				yield null;
			}
			case TarkovNetworking.LOC_CORPSE_BACKPACK_GRID -> (mode == MODE_CORPSE && corpse != null)
				? findBackpackEntry(corpse.getLoadout().getEquipment(EquipmentSlotType.BACKPACK), playerId, entryId)
				: null;
			case TarkovNetworking.LOC_FLOATING_CONTAINER -> {
				int winIdx = idx >> 8;
				int subIdx = idx & 0xFF;
				ContainerHandle handle = resolveOpenWindowHandle(winIdx);
				if (handle == null || !isEntryVisible(searchMemoryFor(handle.getStack()), playerId, entryId)) yield null;
				List<GridContainer> grids = handle.readGrids();
				yield subIdx < grids.size() ? grids.get(subIdx).getEntry(entryId) : null;
			}
			case TarkovNetworking.LOC_EXTERNAL_GRID -> {
				if (mode == MODE_GROUND_ITEM && groundItem != null) {
					yield findSingleContainerEntry(groundItem.getStack(), playerId, entryId);
				}
				GridContainer grid = getPrimaryExternalGrid();
				ContainerSearchMemory mem = getPrimaryExternalMemory();
				yield grid != null && isEntryVisible(mem, playerId, entryId) ? grid.getEntry(entryId) : null;
			}
			default -> null;
		};
	}

	@Nullable
	private static GridEntry findRigEntry(ItemStack rig, UUID playerId, int idx, UUID entryId) {
		if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem)
			|| !isEntryVisible(searchMemoryFor(rig), playerId, entryId)) return null;
		List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
		return idx >= 0 && idx < grids.size() ? grids.get(idx).getEntry(entryId) : null;
	}

	@Nullable
	private static GridEntry findBackpackEntry(ItemStack bp, UUID playerId, UUID entryId) {
		return bp.getItem() instanceof BackpackItem ? findSingleContainerEntry(bp, playerId, entryId) : null;
	}

	@Nullable
	private static GridEntry findSingleContainerEntry(ItemStack container, UUID playerId, UUID entryId) {
		return isSingleGridContainer(container) && isEntryVisible(searchMemoryFor(container), playerId, entryId)
			? readSingleContainerGrid(container).getEntry(entryId) : null;
	}

	@Nullable
	private static ContainerSearchMemory searchMemoryFor(ItemStack container) {
		return ContainerItemHelper.requiresSearch(container) ? ContainerItemHelper.getSearchMemory(container) : null;
	}

	private static boolean isEntryVisible(@Nullable ContainerSearchMemory memory, UUID playerId, UUID entryId) {
		return memory == null || (memory.isGridRevealed(playerId) && memory.isEntryDiscovered(playerId, entryId));
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
			case TarkovNetworking.LOC_SECURE_CONTAINER -> {
				GridContainer grid = loadout.getSecureContainer();
				GridEntry entry = idx == 0 && entryId != null ? grid.getEntry(entryId) : null;
				if (entry == null) yield ItemStack.EMPTY;
				ItemStack removed = entry.getStack().split(count);
				if (entry.getStack().isEmpty()) grid.remove(entryId);
				loadout.invalidateExposedCache();
				yield removed;
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
			case TarkovNetworking.LOC_FLOATING_CONTAINER -> {
				if (entryId == null) yield ItemStack.EMPTY;
				int winIdx = idx >> 8;
				int subIdx = idx & 0xFF;
				ContainerHandle handle = resolveOpenWindowHandle(winIdx);
				if (handle == null) yield ItemStack.EMPTY;
				List<GridContainer> grids = handle.readGrids();
				if (subIdx < 0 || subIdx >= grids.size()) yield ItemStack.EMPTY;
				GridEntry entry = grids.get(subIdx).getEntry(entryId);
				if (entry == null) yield ItemStack.EMPTY;
				ItemStack taken = entry.getStack().split(count);
				if (entry.getStack().isEmpty()) {
					grids.get(subIdx).remove(entryId);
				}
				handle.saveGrids(grids);
				yield taken;
			}
			case TarkovNetworking.LOC_EXTERNAL_GRID -> {
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
		if (loc == TarkovNetworking.LOC_SECURE_CONTAINER) {
			GridContainer grid = loadout.getSecureContainer();
			GridEntry entry = idx == 0 ? grid.getEntry(entryId) : null;
			if (entry == null || !PlayerLoadout.canPlaceInSecureContainer(entry.getStack())) return false;
			GridEntry target = grid.getAt(dstX, dstY);
			if (target != null && target != entry && canStackInto(target.getStack(), entry.getStack())) {
				target.getStack().increment(entry.getStack().getCount());
				grid.remove(entryId);
			} else {
				if (!grid.canPlace(entry.getStack(), dstX, dstY, dstRotated, entryId)) return false;
				grid.remove(entryId);
				grid.placeWithId(entryId, entry.getStack(), dstX, dstY, dstRotated);
			}
			loadout.invalidateExposedCache();
			return true;
		}
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
		if (loc == TarkovNetworking.LOC_FLOATING_CONTAINER) {
			int winIdx = idx >> 8;
			int subIdx = idx & 0xFF;
			ContainerHandle handle = resolveOpenWindowHandle(winIdx);
			if (handle == null) return false;
			List<GridContainer> grids = handle.readGrids();
			if (subIdx < 0 || subIdx >= grids.size()) return false;
			GridContainer grid = grids.get(subIdx);
			GridEntry entry = grid.getEntry(entryId);
			if (entry == null || !grid.canPlace(entry.getStack(), dstX, dstY, dstRotated, entryId)) return false;
			grid.remove(entryId);
			grid.placeWithId(entryId, entry.getStack(), dstX, dstY, dstRotated);
			handle.saveGrids(grids);
			return true;
		}
		if (loc == TarkovNetworking.LOC_EXTERNAL_GRID) {
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
			if (grid == null || !isEntryVisible(mem, playerId, entryId)) return false;
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
		if (loc == TarkovNetworking.LOC_EQUIPMENT) {
			EquipmentSlotType slot = slotByOrdinal(idx);
			return slot != null && loadout.canEquip(slot, stack);
		}
		if (loc == TarkovNetworking.LOC_POCKET) {
			return idx >= 0 && idx < PlayerLoadout.POCKET_COUNT && loadout.canPlaceInPocket(stack)
				&& (loadout.getPocket(idx).isEmpty() || canStackInto(loadout.getPocket(idx), stack));
		}
		if (loc == TarkovNetworking.LOC_SECURE_CONTAINER) {
			return idx == 0 && PlayerLoadout.canPlaceInSecureContainer(stack)
				&& canPlaceInVisibleGrid(loadout.getSecureContainer(), null, playerId, stack, dstX, dstY, dstRotated, true);
		}
		if (loc == TarkovNetworking.LOC_EXTERNAL_GRID && mode != MODE_GROUND_ITEM) {
			GridContainer grid = getPrimaryExternalGrid();
			ContainerSearchMemory memory = getPrimaryExternalMemory();
			return grid != null && (mode == MODE_STASH || memory != null) && !isVanillaNestingBlocked(stack)
				&& canPlaceInVisibleGrid(grid, memory, playerId, stack, dstX, dstY, dstRotated, canAddNewEntryToVanillaGrid(grid));
		}

		ContainerHandle handle = resolveContainerDestination(loadout, loc, idx);
		if (!acceptsContainerStack(handle, stack)) return false;
		List<GridContainer> grids = handle.readGrids();
		int gridIndex = getDestinationGridIndex(loc, idx);
		return gridIndex >= 0 && gridIndex < grids.size() && canPlaceInVisibleGrid(
			grids.get(gridIndex), searchMemoryFor(handle.getStack()), playerId, stack, dstX, dstY, dstRotated, true);
	}

	private boolean insertIntoDestination(
		PlayerLoadout loadout, UUID playerId, ItemStack stack,
		byte loc, int idx, int dstX, int dstY, boolean dstRotated
	) {
		if (loc == TarkovNetworking.LOC_POCKET) {
			ItemStack current = loadout.getPocket(idx);
			if (current.isEmpty()) return loadout.setPocket(idx, stack);
			if (!canStackInto(current, stack)) return false;
			current.increment(stack.getCount());
			return true;
		}
		if (loc == TarkovNetworking.LOC_SECURE_CONTAINER) {
			if (idx != 0 || !PlayerLoadout.canPlaceInSecureContainer(stack)) return false;
			boolean placed = placeInVisibleGrid(loadout.getSecureContainer(), null, playerId, stack, dstX, dstY, dstRotated, true);
			if (placed) loadout.invalidateExposedCache();
			return placed;
		}
		if (loc == TarkovNetworking.LOC_EXTERNAL_GRID && mode != MODE_GROUND_ITEM) {
			GridContainer grid = getPrimaryExternalGrid();
			ContainerSearchMemory memory = getPrimaryExternalMemory();
			return grid != null && (mode == MODE_STASH || memory != null) && !isVanillaNestingBlocked(stack)
				&& placeInVisibleGrid(grid, memory, playerId, stack, dstX, dstY, dstRotated, canAddNewEntryToVanillaGrid(grid));
		}
		return placeInContainerGrid(resolveContainerDestination(loadout, loc, idx), getDestinationGridIndex(loc, idx),
			stack, playerId, dstX, dstY, dstRotated);
	}

	@Nullable
	private ContainerHandle resolveContainerDestination(PlayerLoadout loadout, byte loc, int idx) {
		return switch (loc) {
			case TarkovNetworking.LOC_RIG_GRID -> {
				ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
				yield rig.getItem() instanceof TacticalRigItem ? wearableHandle(rig) : null;
			}
			case TarkovNetworking.LOC_BACKPACK_GRID -> {
				ItemStack backpack = loadout.getEquipment(EquipmentSlotType.BACKPACK);
				yield backpack.getItem() instanceof BackpackItem ? wearableHandle(backpack) : null;
			}
			case TarkovNetworking.LOC_FLOATING_CONTAINER -> resolveOpenWindowHandle(idx >> 8);
			case TarkovNetworking.LOC_EXTERNAL_GRID -> {
				ItemStack groundStack = mode == MODE_GROUND_ITEM && groundItem != null ? groundItem.getStack() : ItemStack.EMPTY;
				yield isSingleGridContainer(groundStack) ? wearableHandle(groundStack) : null;
			}
			case TarkovNetworking.LOC_CORPSE_RIG_GRID -> {
				ItemStack groundStack = mode == MODE_GROUND_ITEM && groundItem != null ? groundItem.getStack() : ItemStack.EMPTY;
				yield groundStack.getItem() instanceof TacticalRigItem ? wearableHandle(groundStack) : null;
			}
			default -> null;
		};
	}

	private static int getDestinationGridIndex(byte loc, int idx) {
		return switch (loc) {
			case TarkovNetworking.LOC_RIG_GRID, TarkovNetworking.LOC_CORPSE_RIG_GRID -> idx;
			case TarkovNetworking.LOC_FLOATING_CONTAINER -> idx & 0xFF;
			default -> 0;
		};
	}

	@Nullable
	private static ContainerHandle wearableHandle(ItemStack container) {
		return ContainerItemHelper.isContainerItem(container) ? directStackHandle(container, false, () -> {}) : null;
	}

	private boolean acceptsContainerStack(@Nullable ContainerHandle handle, ItemStack stack) {
		return handle != null && canPlaceIntoFloatingContainer(handle, ContainerItemHelper.getContainerUuid(handle.getStack()), stack);
	}

	private boolean placeInContainerGrid(@Nullable ContainerHandle handle, int gridIndex, ItemStack stack, UUID playerId,
			int x, int y, boolean rotated) {
		if (!acceptsContainerStack(handle, stack)) return false;
		List<GridContainer> grids = handle.readGrids();
		if (gridIndex < 0 || gridIndex >= grids.size()) return false;
		ContainerSearchMemory memory = searchMemoryFor(handle.getStack());
		if (!placeInVisibleGrid(grids.get(gridIndex), memory, playerId, stack, x, y, rotated, true)) return false;
		handle.saveGrids(grids);
		if (memory != null) handle.saveSearchMemory(memory);
		return true;
	}

	private static boolean canPlaceInVisibleGrid(GridContainer grid, @Nullable ContainerSearchMemory memory, UUID playerId,
			ItemStack stack, int x, int y, boolean rotated, boolean allowNewEntry) {
		if (memory != null && !memory.isGridRevealed(playerId)) return false;
		GridEntry existing = grid.getAt(x, y);
		if (existing != null) {
			return isEntryVisible(memory, playerId, existing.getEntryId()) && canStackInto(existing.getStack(), stack);
		}
		return allowNewEntry && grid.canPlace(stack, x, y, rotated);
	}

	private static boolean placeInVisibleGrid(GridContainer grid, @Nullable ContainerSearchMemory memory, UUID playerId,
			ItemStack stack, int x, int y, boolean rotated, boolean allowNewEntry) {
		if (!canPlaceInVisibleGrid(grid, memory, playerId, stack, x, y, rotated, allowNewEntry)) return false;
		GridEntry placed = grid.place(stack, x, y, rotated);
		if (placed == null) return false;
		if (memory != null) memory.markEntryDiscovered(playerId, placed.getEntryId());
		return true;
	}

	@Nullable
	private GridContainer getPrimaryExternalGrid() {
		if (mode == MODE_STASH && player instanceof ServerPlayerEntity owner) {
			return PlayerProfileState.get(owner.getServer()).stash(owner.getUuid());
		}
		if (mode == MODE_LOOT_BLOCK && lootBlock != null) return lootBlock.getGrid();
		if (mode == MODE_VANILLA_CONTAINER && vanillaStored != null) return vanillaStored.getGrid();
		if (mode == MODE_CORPSE && corpse != null) return corpse.getPocketsGrid();
		return null;
	}

	@Nullable
	private ContainerSearchMemory getPrimaryExternalMemory() {
		if (mode == MODE_LOOT_BLOCK && lootBlock != null) return lootBlock.getSearchMemory();
		if (mode == MODE_VANILLA_CONTAINER && vanillaStored != null) return vanillaStored.getSearchMemory();
		if (mode == MODE_CORPSE && corpse != null) return corpse.getPocketsSearchMemory();
		return null;
	}

	private boolean isVanillaNestingBlocked(ItemStack stack) {
		return mode == MODE_VANILLA_CONTAINER && vanillaIsShulker && !stack.getItem().canBeNested();
	}

	private boolean canAddNewEntryToVanillaGrid(GridContainer grid) {
		return mode != MODE_VANILLA_CONTAINER || vanillaInventory == null || grid.getEntries().size() < vanillaInventory.size();
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
		if (mode == MODE_STASH && player instanceof ServerPlayerEntity owner) {
			PlayerProfileState.get(owner.getServer()).markDirty();
		} else if (mode == MODE_LOOT_BLOCK && lootBlock != null) {
			lootBlock.markDirty();
		} else if (mode == MODE_VANILLA_CONTAINER && vanillaStored != null && vanillaInventory != null) {
			VanillaContainerBridge.syncGridToInventory(vanillaStored.getGrid(), vanillaInventory);
			if (vanillaState != null) {
				vanillaState.markDirty();
			}
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

	private static List<GridContainer> readContainerGrids(ItemStack stack) {
		if (stack.getItem() instanceof TacticalRigItem) {
			return ContainerItemHelper.readRigGrids(stack);
		}
		if (stack.getItem() instanceof BackpackItem) {
			return List.of(ContainerItemHelper.readBackpackGrid(stack));
		}
		if (stack.getItem() instanceof TarkovCaseItem) {
			return List.of(ContainerItemHelper.readCaseGrid(stack));
		}
		return List.of();
	}

	private static void writeContainerGrids(ItemStack stack, List<GridContainer> grids) {
		if (stack.getItem() instanceof TacticalRigItem) {
			ContainerItemHelper.writeRigGrids(stack, grids);
		} else if (stack.getItem() instanceof BackpackItem && !grids.isEmpty()) {
			ContainerItemHelper.writeBackpackGrid(stack, grids.get(0));
		} else if (stack.getItem() instanceof TarkovCaseItem && !grids.isEmpty()) {
			ContainerItemHelper.writeCaseGrid(stack, grids.get(0));
		}
	}

	private boolean canPlaceIntoFloatingContainer(ContainerHandle handle, UUID targetUuid, ItemStack incoming) {
		ItemStack target = handle.getStack();
		if (target.getItem() instanceof TarkovCaseItem caseItem) {
			if (!caseItem.canAcceptItem(incoming)) return false;
			GridContainer secure = getPlayerLoadout(player).getSecureContainer();
			if (secure.getEntries().stream().anyMatch(entry -> entry.getStack() == target)
				&& !SecureContainerValidator.allowsCaseInsertion(target, incoming)) return false;
		}
		if (targetUuid != null && ContainerItemHelper.containsContainerId(incoming, targetUuid)) {
			return false;
		}
		if (handle.isNestedInsideContainer()) {
			return !ContainerItemHelper.isContainerItem(incoming);
		}
		return ContainerItemHelper.getNestingDepth(incoming) < 2;
	}

	private interface ContainerHandle {
		ItemStack getStack();
		boolean isNestedInsideContainer();
		List<GridContainer> readGrids();
		void saveGrids(List<GridContainer> grids);
		default void saveSearchMemory(ContainerSearchMemory mem) {
			ContainerItemHelper.saveSearchMemory(getStack(), mem);
			persistStackMutation();
		}
		default void persistStackMutation() {
		}
	}

	@Nullable
	private ContainerHandle resolveOpenWindowHandle(int winIdx) {
		if (winIdx < 0 || winIdx >= openContainerUuids.size()) {
			return null;
		}
		return resolveContainerHandle(openContainerUuids.get(winIdx));
	}

	@Nullable
	private ContainerHandle resolveContainerHandle(@Nullable UUID targetUuid) {
		if (targetUuid == null) {
			return null;
		}
		UUID playerId = player.getUuid();
		PlayerLoadout loadout = getPlayerLoadout(player);

		// 1a. Own equipment slots
		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			ItemStack eq = loadout.getEquipment(slot);
			if (ContainerItemHelper.isContainerItem(eq) && targetUuid.equals(ContainerItemHelper.getContainerUuid(eq))) {
				return directStackHandle(eq, false, () -> {});
			}
		}

		// 1b. Own pockets (0..5)
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			ItemStack pocket = loadout.getPocket(i);
			if (ContainerItemHelper.isContainerItem(pocket) && targetUuid.equals(ContainerItemHelper.getContainerUuid(pocket))) {
				return directStackHandle(pocket, false, () -> {});
			}
		}

		// 1c. Inside own Tactical Rig
		ContainerHandle fromOwnRig = findInsideRig(loadout.getEquipment(EquipmentSlotType.RIG), targetUuid, playerId, () -> {});
		if (fromOwnRig != null) return fromOwnRig;

		// 1d. Inside own Backpack
		ContainerHandle fromOwnBp = findInsideBackpack(loadout.getEquipment(EquipmentSlotType.BACKPACK), targetUuid, playerId, () -> {});
		if (fromOwnBp != null) return fromOwnBp;

		ContainerHandle fromSecure = findInsideGrid(loadout.getSecureContainer(), null, targetUuid, playerId,
			false, grid -> loadout.invalidateExposedCache(), loadout::invalidateExposedCache);
		if (fromSecure != null) return fromSecure;

		// 2. Creative vanilla inventory
		if (player.isCreative()) {
			PlayerInventory inv = player.getInventory();
			for (int i = 0; i < inv.main.size(); i++) {
				ItemStack st = inv.main.get(i);
				if (ContainerItemHelper.isContainerItem(st) && targetUuid.equals(ContainerItemHelper.getContainerUuid(st))) {
					final int slotIdx = i;
					return directStackHandle(inv.main.get(slotIdx), false, inv::markDirty);
				}
			}
			for (int i = 0; i < inv.offHand.size(); i++) {
				ItemStack st = inv.offHand.get(i);
				if (ContainerItemHelper.isContainerItem(st) && targetUuid.equals(ContainerItemHelper.getContainerUuid(st))) {
					final int slotIdx = i;
					return directStackHandle(inv.offHand.get(slotIdx), false, inv::markDirty);
				}
			}
		}

		if (mode == MODE_STASH) {
			ContainerHandle inStash = findInsideGrid(getPrimaryExternalGrid(), null, targetUuid, playerId,
				false, grid -> markExternalDirty(), this::markExternalDirty);
			if (inStash != null) return inStash;
		}

		// 3. Inside open Loot Block
		if (mode == MODE_LOOT_BLOCK && lootBlock != null && lootBlock.getSearchMemory().isGridRevealed(playerId)) {
			ContainerHandle fromBlock = findInsideGrid(
				lootBlock.getGrid(), lootBlock.getSearchMemory(), targetUuid, playerId, false,
				g -> lootBlock.markDirty(), lootBlock::markDirty
			);
			if (fromBlock != null) return fromBlock;
		}

		// 3b. Inside open Vanilla Container
		if (mode == MODE_VANILLA_CONTAINER && vanillaStored != null && vanillaStored.getSearchMemory().isGridRevealed(playerId)) {
			ContainerHandle fromVanilla = findInsideGrid(
				vanillaStored.getGrid(), vanillaStored.getSearchMemory(), targetUuid, playerId, false,
				g -> markExternalDirty(), this::markExternalDirty
			);
			if (fromVanilla != null) return fromVanilla;
		}

		// 4. Inside open Corpse
		if (mode == MODE_CORPSE && corpse != null) {
			PlayerLoadout cLoadout = corpse.getLoadout();
			for (EquipmentSlotType slot : EquipmentSlotType.values()) {
				ItemStack eq = cLoadout.getEquipment(slot);
				if (ContainerItemHelper.isContainerItem(eq) && targetUuid.equals(ContainerItemHelper.getContainerUuid(eq))) {
					return directStackHandle(eq, false, corpse::syncVisualFlags);
				}
			}
			if (corpse.getPocketsSearchMemory().isGridRevealed(playerId)) {
				ContainerHandle fromCPockets = findInsideGrid(
					corpse.getPocketsGrid(), corpse.getPocketsSearchMemory(), targetUuid, playerId, false,
					g -> corpse.syncVisualFlags(), corpse::syncVisualFlags
				);
				if (fromCPockets != null) return fromCPockets;
			}
			ContainerHandle fromCRig = findInsideRig(cLoadout.getEquipment(EquipmentSlotType.RIG), targetUuid, playerId, corpse::syncVisualFlags);
			if (fromCRig != null) return fromCRig;
			ContainerHandle fromCBp = findInsideBackpack(cLoadout.getEquipment(EquipmentSlotType.BACKPACK), targetUuid, playerId, corpse::syncVisualFlags);
			if (fromCBp != null) return fromCBp;
		}

		// 5. Inside open Ground Container
		if (mode == MODE_GROUND_ITEM && groundItem != null && !groundItem.getStack().isEmpty()) {
			ItemStack gStack = groundItem.getStack();
			if (gStack.getItem() instanceof TacticalRigItem) {
				ContainerHandle fromGRig = findInsideRig(gStack, targetUuid, playerId, () -> {});
				if (fromGRig != null) return fromGRig;
			} else if (isSingleGridContainer(gStack)) {
				ContainerSearchMemory mem = ContainerItemHelper.requiresSearch(gStack) ? ContainerItemHelper.getSearchMemory(gStack) : null;
				if (mem == null || mem.isGridRevealed(playerId)) {
					GridContainer g = readSingleContainerGrid(gStack);
					ContainerHandle fromG = findInsideGrid(g, mem, targetUuid, playerId, true,
						updated -> writeSingleContainerGrid(gStack, updated), () -> {});
					if (fromG != null) return fromG;
				}
			}
		}

		return null;
	}

	private static ContainerHandle directStackHandle(ItemStack stack, boolean nested, Runnable onSave) {
		return new ContainerHandle() {
			@Override
			public ItemStack getStack() {
				return stack;
			}

			@Override
			public boolean isNestedInsideContainer() {
				return nested;
			}

			@Override
			public List<GridContainer> readGrids() {
				return readContainerGrids(stack);
			}

			@Override
			public void saveGrids(List<GridContainer> grids) {
				writeContainerGrids(stack, grids);
				onSave.run();
			}

			@Override
			public void persistStackMutation() {
				onSave.run();
			}
		};
	}

	@Nullable
	private static ContainerHandle findInsideRig(ItemStack rig, UUID targetUuid, UUID playerId, Runnable onParentSave) {
		if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem)) return null;
		ContainerSearchMemory mem = ContainerItemHelper.requiresSearch(rig) ? ContainerItemHelper.getSearchMemory(rig) : null;
		if (mem != null && !mem.isGridRevealed(playerId)) return null;

		List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rig);
		for (int gi = 0; gi < rigGrids.size(); gi++) {
			for (GridEntry entry : rigGrids.get(gi).getEntries()) {
				ItemStack st = entry.getStack();
				if (ContainerItemHelper.isContainerItem(st) && targetUuid.equals(ContainerItemHelper.getContainerUuid(st))) {
					if (mem != null && !mem.isEntryDiscovered(playerId, entry.getEntryId())) {
						return null;
					}
					final int gridIndex = gi;
					final UUID entryId = entry.getEntryId();
					return new ContainerHandle() {
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
						public List<GridContainer> readGrids() {
							return readContainerGrids(getStack());
						}

						@Override
						public void saveGrids(List<GridContainer> grids) {
							List<GridContainer> fresh = ContainerItemHelper.readRigGrids(rig);
							if (gridIndex >= 0 && gridIndex < fresh.size()) {
								GridEntry fe = fresh.get(gridIndex).getEntry(entryId);
								if (fe != null) {
									writeContainerGrids(fe.getStack(), grids);
									ContainerItemHelper.writeRigGrids(rig, fresh);
									onParentSave.run();
								}
							}
						}

						@Override
						public void saveSearchMemory(ContainerSearchMemory childMem) {
							List<GridContainer> fresh = ContainerItemHelper.readRigGrids(rig);
							if (gridIndex >= 0 && gridIndex < fresh.size()) {
								GridEntry fe = fresh.get(gridIndex).getEntry(entryId);
								if (fe != null) {
									ContainerItemHelper.saveSearchMemory(fe.getStack(), childMem);
									ContainerItemHelper.writeRigGrids(rig, fresh);
									onParentSave.run();
								}
							}
						}
					};
				}
			}
		}
		return null;
	}

	@Nullable
	private static ContainerHandle findInsideBackpack(ItemStack bp, UUID targetUuid, UUID playerId, Runnable onParentSave) {
		if (bp.isEmpty() || !(bp.getItem() instanceof BackpackItem)) return null;
		ContainerSearchMemory mem = ContainerItemHelper.requiresSearch(bp) ? ContainerItemHelper.getSearchMemory(bp) : null;
		if (mem != null && !mem.isGridRevealed(playerId)) return null;
		GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
		return findInsideGrid(bpGrid, mem, targetUuid, playerId, true,
			updated -> ContainerItemHelper.writeBackpackGrid(bp, updated), onParentSave);
	}

	@Nullable
	private static ContainerHandle findInsideGrid(
		GridContainer grid,
		@Nullable ContainerSearchMemory mem,
		UUID targetUuid,
		UUID playerId,
		boolean nested,
		java.util.function.Consumer<GridContainer> gridWriter,
		Runnable onParentSave
	) {
		for (GridEntry entry : grid.getEntries()) {
			ItemStack st = entry.getStack();
			if (ContainerItemHelper.isContainerItem(st) && targetUuid.equals(ContainerItemHelper.getContainerUuid(st))) {
				if (mem != null && !mem.isEntryDiscovered(playerId, entry.getEntryId())) {
					return null;
				}
				final UUID entryId = entry.getEntryId();
				return new ContainerHandle() {
					@Override
					public ItemStack getStack() {
						GridEntry fe = grid.getEntry(entryId);
						return fe != null ? fe.getStack() : st;
					}

					@Override
					public boolean isNestedInsideContainer() {
						return nested;
					}

					@Override
					public List<GridContainer> readGrids() {
						return readContainerGrids(getStack());
					}

					@Override
					public void saveGrids(List<GridContainer> grids) {
						GridEntry fe = grid.getEntry(entryId);
						if (fe != null) {
							writeContainerGrids(fe.getStack(), grids);
							gridWriter.accept(grid);
							onParentSave.run();
						}
					}

					@Override
					public void saveSearchMemory(ContainerSearchMemory childMem) {
						GridEntry fe = grid.getEntry(entryId);
						if (fe != null) {
							ContainerItemHelper.saveSearchMemory(fe.getStack(), childMem);
							gridWriter.accept(grid);
							onParentSave.run();
						}
					}
				};
			}
		}
		return null;
	}
}
