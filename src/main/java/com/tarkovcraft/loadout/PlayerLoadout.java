package com.tarkovcraft.loadout;

import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovSizedItem;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.item.ItemStack;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.item.TarkovArmorRepairItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovFunctionalItem;
import com.tarkovcraft.item.TarkovStimulatorItem;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds a player's Tarkov equipment slots, 6x (1x1) pockets, and hotbar mapping:
 * - Hotbar 0..2 (Keys 1..3): PRIMARY_1, PRIMARY_2, PISTOL
 * - Hotbar 3..8 (Keys 4..9): Pockets 0..5 (strictly 1x1)
 * - Melee is separate and only overrides the active hand when meleeSelected == true.
 */
public final class PlayerLoadout {
	public static final int POCKET_COUNT = 6;
	public static final int HOTBAR_SIZE = 9;

	private final Map<EquipmentSlotType, ItemStack> equipment = new EnumMap<>(EquipmentSlotType.class);
	private final ItemStack[] pockets = new ItemStack[POCKET_COUNT];
	private boolean meleeSelected = false;
	private GridContainer secureContainer = new GridContainer(2, 2);

	private transient long exposedCacheTick = Long.MIN_VALUE;
	private transient boolean exposedCacheIncludeBp = false;
	@Nullable
	private transient List<ItemStack> exposedCache = null;

	public PlayerLoadout() {
		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			equipment.put(slot, ItemStack.EMPTY);
		}
		for (int i = 0; i < POCKET_COUNT; i++) {
			pockets[i] = ItemStack.EMPTY;
		}
	}

	public ItemStack getEquipment(EquipmentSlotType slot) {
		return equipment.getOrDefault(slot, ItemStack.EMPTY);
	}

	public GridContainer getSecureContainer() {
		return secureContainer;
	}

	public void copySecureContainerFrom(PlayerLoadout old) {
		secureContainer = GridContainer.fromNbt(old.secureContainer.toNbt(), 2, 2);
		invalidateExposedCache();
	}

	public static boolean canPlaceInSecureContainer(ItemStack stack) {
		if (stack.isEmpty()) return false;
		var item = stack.getItem();
		if (item instanceof TarkovCaseItem container) {
			var role = container.getCaseRole();
			return (role == TarkovCaseItem.CaseRole.INJECTOR || role == TarkovCaseItem.CaseRole.MONEY
				|| role == TarkovCaseItem.CaseRole.MEDICAL)
				&& ContainerItemHelper.readCaseGrid(stack).getEntries().stream()
					.allMatch(entry -> container.canAcceptItem(entry.getStack()));
		}
		Identifier id = Registries.ITEM.getId(item);
		return item instanceof TarkovMedicalItem || item instanceof TarkovStimulatorItem
			|| item instanceof TarkovArmorRepairItem || ("tacz".equals(id.getNamespace()) && "ammo".equals(id.getPath()))
			|| stack.isFood()
			|| item == Items.EMERALD || item == Items.DIAMOND || item == Items.GOLD_INGOT
			|| item == Items.GOLD_NUGGET || item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP
			|| item == Items.AMETHYST_SHARD || item == Items.PAPER || item == Items.NETHER_STAR
			|| item == Items.HEART_OF_THE_SEA || item == Items.TOTEM_OF_UNDYING;
	}

	public boolean blocksHeadset() {
		return blocksHeadset(getEquipment(EquipmentSlotType.HELMET))
			|| blocksHeadset(getEquipment(EquipmentSlotType.FACE_MASK));
	}

	private static boolean blocksHeadset(ItemStack stack) {
		return stack.getItem() instanceof TarkovArmorItem armor && armor.blocksHeadset();
	}

	public boolean canEquip(EquipmentSlotType slot, ItemStack stack) {
		return slot.accepts(stack) && (slot != EquipmentSlotType.HEADSET || stack.isEmpty() || !blocksHeadset());
	}

	public void invalidateExposedCache() {
		exposedCache = null;
		exposedCacheTick = Long.MIN_VALUE;
	}

	public void setEquipment(EquipmentSlotType slot, ItemStack stack) {
		equipment.put(slot, stack == null ? ItemStack.EMPTY : stack);
		invalidateExposedCache();
	}

	public ItemStack getPocket(int index) {
		if (index < 0 || index >= POCKET_COUNT) {
			return ItemStack.EMPTY;
		}
		return pockets[index];
	}

	public boolean canPlaceInPocket(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return true;
		}
		ItemSize size = ItemSizeRegistry.getSize(stack);
		return size.width() == 1 && size.height() == 1;
	}

	public boolean setPocket(int index, ItemStack stack) {
		if (index < 0 || index >= POCKET_COUNT) {
			return false;
		}
		if (stack != null && !stack.isEmpty() && !canPlaceInPocket(stack)) {
			return false;
		}
		pockets[index] = stack == null ? ItemStack.EMPTY : stack;
		return true;
	}

	public boolean isMeleeSelected() {
		return meleeSelected;
	}

	public void setMeleeSelected(boolean meleeSelected) {
		this.meleeSelected = meleeSelected;
	}

	/**
	 * Always returns the stack for hotbar slot 0..8 for HUD display,
	 * NEVER overridden by meleeSelected (fixes the bug where all 9 slots showed the melee weapon).
	 */
	public ItemStack getHotbarDisplayStack(int hotbarSlot) {
		return switch (hotbarSlot) {
			case 0 -> getEquipment(EquipmentSlotType.PRIMARY_1);
			case 1 -> getEquipment(EquipmentSlotType.PRIMARY_2);
			case 2 -> getEquipment(EquipmentSlotType.PISTOL);
			default -> {
				int pocketIdx = hotbarSlot - 3;
				yield (pocketIdx >= 0 && pocketIdx < POCKET_COUNT) ? getPocket(pocketIdx) : ItemStack.EMPTY;
			}
		};
	}

	/**
	 * Returns what the player is actually holding in their main hand.
	 */
	public ItemStack getActiveHandStack(int hotbarSlot) {
		if (meleeSelected) {
			return getEquipment(EquipmentSlotType.MELEE);
		}
		return getHotbarDisplayStack(hotbarSlot);
	}

	/**
	 * Smart pickup (used when pressing F on a world item):
	 * 1. If item is wearable gear (Backpack / Rig / Armor) and that equipment slot is empty -> equip immediately!
	 * 2. If item is a gun/melee weapon and matching weapon slot is empty -> equip immediately!
	 * 3. Otherwise auto-fit into Pockets -> Rig -> Backpack.
	 */
	public boolean trySmartPickup(ItemStack incoming, @Nullable UUID playerId) {
		if (incoming == null || incoming.isEmpty()) {
			return false;
		}

		// Direct equip for wearable gear if slot is empty
		if (incoming.getItem() instanceof BackpackItem && getEquipment(EquipmentSlotType.BACKPACK).isEmpty()) {
			setEquipment(EquipmentSlotType.BACKPACK, incoming.copy());
			incoming.setCount(0);
			return true;
		}
		if (incoming.getItem() instanceof TacticalRigItem && getEquipment(EquipmentSlotType.RIG).isEmpty()) {
			setEquipment(EquipmentSlotType.RIG, incoming.copy());
			incoming.setCount(0);
			return true;
		}
		if (incoming.getItem() instanceof TarkovArmorItem armor) {
			EquipmentSlotType slot = armor.getTargetSlot();
			if (getEquipment(slot).isEmpty()) {
				setEquipment(slot, incoming.copy());
				incoming.setCount(0);
				return true;
			}
		}

		if (incoming.getItem() instanceof TarkovFunctionalItem functional && functional.isHeadset()
			&& getEquipment(EquipmentSlotType.HEADSET).isEmpty() && canEquip(EquipmentSlotType.HEADSET, incoming)) {
			setEquipment(EquipmentSlotType.HEADSET, incoming.copy());
			incoming.setCount(0);
			return true;
		}

		// Direct equip for TaCZ guns into empty weapon slots
		Identifier id = Registries.ITEM.getId(incoming.getItem());
		boolean isTaczGun = "tacz".equals(id.getNamespace()) && id.getPath().contains("gun");
		if (isTaczGun) {
			if (ItemSizeRegistry.isPistolLike(incoming) && getEquipment(EquipmentSlotType.PISTOL).isEmpty()) {
				setEquipment(EquipmentSlotType.PISTOL, incoming.copy());
				incoming.setCount(0);
				return true;
			}
			if (getEquipment(EquipmentSlotType.PRIMARY_1).isEmpty()) {
				setEquipment(EquipmentSlotType.PRIMARY_1, incoming.copy());
				incoming.setCount(0);
				return true;
			}
			if (getEquipment(EquipmentSlotType.PRIMARY_2).isEmpty()) {
				setEquipment(EquipmentSlotType.PRIMARY_2, incoming.copy());
				incoming.setCount(0);
				return true;
			}
		}

		// Direct equip for melee weapons if melee slot is empty
		if (EquipmentSlotType.MELEE.accepts(incoming) && getEquipment(EquipmentSlotType.MELEE).isEmpty()) {
			setEquipment(EquipmentSlotType.MELEE, incoming.copy());
			incoming.setCount(0);
			return true;
		}

		// Fallback to storage grids
		return tryInsertItem(incoming, playerId);
	}

	/**
	 * Attempts to auto-insert an ItemStack into Pockets -> Rig -> Backpack.
	 */
	public boolean tryInsertItem(ItemStack incoming, @Nullable UUID playerId) {
		if (incoming == null || incoming.isEmpty()) {
			return false;
		}

		// 1. Try 1x1 pockets first (stacking, then empty slot)
		if (canPlaceInPocket(incoming)) {
			if (incoming.isStackable()) {
				for (int i = 0; i < POCKET_COUNT; i++) {
					ItemStack pocket = pockets[i];
					if (!pocket.isEmpty() && ItemStack.canCombine(pocket, incoming) && pocket.getCount() < pocket.getMaxCount()) {
						int transfer = Math.min(pocket.getMaxCount() - pocket.getCount(), incoming.getCount());
						pocket.increment(transfer);
						incoming.decrement(transfer);
						if (incoming.isEmpty()) {
							return true;
						}
					}
				}
			}
			for (int i = 0; i < POCKET_COUNT; i++) {
				if (pockets[i].isEmpty()) {
					pockets[i] = incoming.copy();
					incoming.setCount(0);
					return true;
				}
			}
		}

		// 2. Try Equipped Tactical Rig sub-grids (only if not blocked by unsearched state)
		ItemStack rigStack = getEquipment(EquipmentSlotType.RIG);
		if (!rigStack.isEmpty() && rigStack.getItem() instanceof TacticalRigItem) {
			boolean canPutInRig = !ContainerItemHelper.requiresSearch(rigStack)
				|| (playerId != null && ContainerItemHelper.getSearchMemory(rigStack).isGridRevealed(playerId));
			if (canPutInRig) {
				List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rigStack);
				boolean changed = false;
				for (GridContainer grid : rigGrids) {
					int beforeGrid = incoming.getCount();
					boolean full = grid.tryAutoPlace(incoming);
					if (full || incoming.getCount() < beforeGrid) {
						changed = true;
						if (incoming.isEmpty()) {
							break;
						}
					}
				}
				if (changed) {
					ContainerItemHelper.writeRigGrids(rigStack, rigGrids);
					invalidateExposedCache();
					if (incoming.isEmpty()) {
						return true;
					}
				}
			}
		}

		// 3. Try Equipped Backpack grid (only if not blocked by unsearched state)
		ItemStack bpStack = getEquipment(EquipmentSlotType.BACKPACK);
		if (!bpStack.isEmpty() && bpStack.getItem() instanceof BackpackItem) {
			boolean canPutInBp = !ContainerItemHelper.requiresSearch(bpStack)
				|| (playerId != null && ContainerItemHelper.getSearchMemory(bpStack).isGridRevealed(playerId));
			if (canPutInBp) {
				GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bpStack);
				int beforeBp = incoming.getCount();
				boolean full = bpGrid.tryAutoPlace(incoming);
				if (full || incoming.getCount() < beforeBp) {
					ContainerItemHelper.writeBackpackGrid(bpStack, bpGrid);
					invalidateExposedCache();
					if (incoming.isEmpty()) {
						return true;
					}
				}
			}
		}

		return incoming.isEmpty();
	}

	/**
	 * Exposes live stacks inside the equipped Tactical Rig (and ONLY the Rig in combat;
	 * when crafting at a workbench, includeBackpack = true also appends Backpack items so
	 * the TaCZ Gunsmith Table can consume crafting materials from the Backpack too!).
	 */
	public ItemStack getRigExposedStack(int rigSlotIndex, @Nullable UUID playerId) {
		return getExposedStorageStack(rigSlotIndex, playerId, false);
	}

	@Nullable
	private static ContainerSearchMemory requiredSearch(ItemStack container) {
		return ContainerItemHelper.requiresSearch(container) ? ContainerItemHelper.getSearchMemory(container) : null;
	}

	private static boolean isGridExposed(@Nullable ContainerSearchMemory search, @Nullable UUID playerId) {
		return search == null || (playerId != null && search.isGridRevealed(playerId));
	}

	private static boolean isEntryExposed(@Nullable ContainerSearchMemory search, @Nullable UUID playerId, GridEntry entry) {
		return search == null || search.isEntryDiscovered(playerId, entry.getEntryId());
	}

	private static boolean tryAutoPlaceInExposedGrid(GridContainer grid, ItemStack stack,
			@Nullable ContainerSearchMemory search, @Nullable UUID playerId) {
		if (search == null) return grid.tryAutoPlace(stack);
		List<GridEntry> previous = grid.getEntries();
		if (!grid.tryAutoPlace(stack, id -> search.isEntryDiscovered(playerId, id))) return false;
		for (GridEntry entry : grid.getEntries()) {
			if (!previous.contains(entry)) search.markEntryDiscovered(playerId, entry.getEntryId());
		}
		return true;
	}

	public ItemStack getExposedStorageStackCached(int slotIndex, @Nullable UUID playerId, boolean includeBackpack, long worldTick) {
		if (slotIndex < 0) return ItemStack.EMPTY;
		if (exposedCache == null || exposedCacheTick != worldTick || exposedCacheIncludeBp != includeBackpack) {
			java.util.ArrayList<ItemStack> rebuilt = new java.util.ArrayList<>();
			for (GridEntry entry : secureContainer.getEntries()) {
				if (AmmoTier.isTaczAmmoStack(entry.getStack())) rebuilt.add(entry.getStack());
			}
			ItemStack rig = getEquipment(EquipmentSlotType.RIG);
			if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
				ContainerSearchMemory search = requiredSearch(rig);
				if (isGridExposed(search, playerId)) {
					for (GridContainer g : ContainerItemHelper.readRigGrids(rig)) {
						for (var entry : g.getEntries()) {
							if (isEntryExposed(search, playerId, entry)) rebuilt.add(entry.getStack());
						}
					}
				}
			}
			if (includeBackpack) {
				ItemStack bp = getEquipment(EquipmentSlotType.BACKPACK);
				if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem) {
					ContainerSearchMemory search = requiredSearch(bp);
					if (isGridExposed(search, playerId)) {
						GridContainer g = ContainerItemHelper.readBackpackGrid(bp);
						for (var entry : g.getEntries()) {
							if (isEntryExposed(search, playerId, entry)) rebuilt.add(entry.getStack());
						}
					}
				}
			}
			exposedCache = rebuilt;
			exposedCacheTick = worldTick;
			exposedCacheIncludeBp = includeBackpack;
		}
		return slotIndex < exposedCache.size() ? exposedCache.get(slotIndex) : ItemStack.EMPTY;
	}

	public ItemStack getExposedStorageStack(int slotIndex, @Nullable UUID playerId, boolean includeBackpack) {
		if (slotIndex < 0) return ItemStack.EMPTY;
		int cursor = 0;
		for (GridEntry entry : secureContainer.getEntries()) {
			if (AmmoTier.isTaczAmmoStack(entry.getStack()) && cursor++ == slotIndex) return entry.getStack();
		}

		ItemStack rig = getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
			ContainerSearchMemory search = requiredSearch(rig);
			if (isGridExposed(search, playerId)) {
				for (GridContainer g : ContainerItemHelper.readRigGrids(rig)) {
					for (var entry : g.getEntries()) {
						if (!isEntryExposed(search, playerId, entry)) continue;
						if (cursor == slotIndex) {
							return entry.getStack();
						}
						cursor++;
					}
				}
			}
		}

		if (includeBackpack) {
			ItemStack bp = getEquipment(EquipmentSlotType.BACKPACK);
			if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem) {
				ContainerSearchMemory search = requiredSearch(bp);
				if (isGridExposed(search, playerId)) {
					GridContainer g = ContainerItemHelper.readBackpackGrid(bp);
					for (var entry : g.getEntries()) {
						if (!isEntryExposed(search, playerId, entry)) continue;
						if (cursor == slotIndex) {
							return entry.getStack();
						}
						cursor++;
					}
				}
			}
		}
		return ItemStack.EMPTY;
	}

	public ItemStack removeRigExposedStack(int rigSlotIndex, int amount, @Nullable UUID playerId) {
		return removeExposedStorageStack(rigSlotIndex, amount, playerId, false);
	}

	public ItemStack removeExposedStorageStack(int slotIndex, int amount, @Nullable UUID playerId, boolean includeBackpack) {
		if (slotIndex < 0 || amount <= 0) return ItemStack.EMPTY;
		int cursor = 0;
		for (GridEntry entry : secureContainer.getEntries()) {
			if (!AmmoTier.isTaczAmmoStack(entry.getStack()) || cursor++ != slotIndex) continue;
			ItemStack removed = entry.getStack().split(amount);
			if (entry.getStack().isEmpty()) secureContainer.remove(entry.getEntryId());
			invalidateExposedCache();
			return removed;
		}

		ItemStack rig = getEquipment(EquipmentSlotType.RIG);
		ContainerSearchMemory rigSearch = requiredSearch(rig);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem && isGridExposed(rigSearch, playerId)) {
			List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
			for (GridContainer g : grids) {
				for (var entry : g.getEntries()) {
					if (!isEntryExposed(rigSearch, playerId, entry)) continue;
					if (cursor == slotIndex) {
						ItemStack st = entry.getStack();
						ItemStack extracted = st.split(amount);
						if (st.isEmpty()) {
							g.remove(entry.getEntryId());
						}
						ContainerItemHelper.writeRigGrids(rig, grids);
						invalidateExposedCache();
						return extracted;
					}
					cursor++;
				}
			}
		}

		if (includeBackpack) {
			ItemStack bp = getEquipment(EquipmentSlotType.BACKPACK);
			ContainerSearchMemory backpackSearch = requiredSearch(bp);
			if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem && isGridExposed(backpackSearch, playerId)) {
				GridContainer g = ContainerItemHelper.readBackpackGrid(bp);
				for (var entry : g.getEntries()) {
					if (!isEntryExposed(backpackSearch, playerId, entry)) continue;
					if (cursor == slotIndex) {
						ItemStack st = entry.getStack();
						ItemStack extracted = st.split(amount);
						if (st.isEmpty()) {
							g.remove(entry.getEntryId());
						}
						ContainerItemHelper.writeBackpackGrid(bp, g);
						invalidateExposedCache();
						return extracted;
					}
					cursor++;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	public boolean setRigExposedStack(int rigSlotIndex, ItemStack incoming) {
		return setExposedStorageStack(rigSlotIndex, incoming, null, false);
	}

	public boolean setExposedStorageStack(int slotIndex, ItemStack incoming, @Nullable UUID playerId, boolean includeBackpack) {
		if (slotIndex < 0) return false;
		int cursor = 0;
		for (GridEntry entry : secureContainer.getEntries()) {
			if (!AmmoTier.isTaczAmmoStack(entry.getStack()) || cursor++ != slotIndex) continue;
			if (incoming == null || incoming.isEmpty()) {
				secureContainer.remove(entry.getEntryId());
			} else {
				if (!AmmoTier.isTaczAmmoStack(incoming)
					|| !ItemSizeRegistry.getSize(incoming).equals(ItemSizeRegistry.getSize(entry.getStack()))) return false;
				entry.setStack(incoming.copy());
			}
			invalidateExposedCache();
			return true;
		}

		ItemStack rig = getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
			ContainerSearchMemory search = requiredSearch(rig);
			if (isGridExposed(search, playerId)) {
				List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
				for (GridContainer g : grids) {
					for (var entry : g.getEntries()) {
						if (!isEntryExposed(search, playerId, entry)) continue;
						if (cursor == slotIndex) {
							if (incoming == null || incoming.isEmpty()) {
								g.remove(entry.getEntryId());
							} else {
								entry.setStack(incoming.copy());
							}
							ContainerItemHelper.writeRigGrids(rig, grids);
							invalidateExposedCache();
							return true;
						}
						cursor++;
					}
				}
			}
		}

		if (includeBackpack) {
			ItemStack bp = getEquipment(EquipmentSlotType.BACKPACK);
			if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem) {
				ContainerSearchMemory search = requiredSearch(bp);
				if (isGridExposed(search, playerId)) {
					GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
					for (var entry : bpGrid.getEntries()) {
						if (!isEntryExposed(search, playerId, entry)) continue;
						if (cursor == slotIndex) {
							if (incoming == null || incoming.isEmpty()) {
								bpGrid.remove(entry.getEntryId());
							} else {
								entry.setStack(incoming.copy());
							}
							ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
							invalidateExposedCache();
							return true;
						}
						cursor++;
					}
				}
			}
		}

		// New item pushed back into an empty exposed slot (e.g. TaCZ unloading or workbench crafting output)
		if (incoming != null && !incoming.isEmpty()) {
			ItemStack copy = incoming.copy();
			if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
				ContainerSearchMemory search = requiredSearch(rig);
				if (isGridExposed(search, playerId)) {
					List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
					for (GridContainer g : grids) {
						if (tryAutoPlaceInExposedGrid(g, copy, search, playerId)) {
							ContainerItemHelper.writeRigGrids(rig, grids);
							if (search != null) ContainerItemHelper.saveSearchMemory(rig, search);
							invalidateExposedCache();
							return true;
						}
					}
				}
			}
			if (includeBackpack) {
				ItemStack bp = getEquipment(EquipmentSlotType.BACKPACK);
				if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem) {
					ContainerSearchMemory search = requiredSearch(bp);
					if (isGridExposed(search, playerId)) {
						GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
						if (tryAutoPlaceInExposedGrid(bpGrid, copy, search, playerId)) {
							ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
							if (search != null) ContainerItemHelper.saveSearchMemory(bp, search);
							invalidateExposedCache();
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/**
	 * Estimates rough carried weight in kg for the Tarkov UI stats bar.
	 */
	public float calculateWeightKg() {
		float kg = 0.0f;
		for (ItemStack st : equipment.values()) {
			kg += estimateItemWeight(st);
		}
		for (ItemStack st : pockets) {
			kg += estimateItemWeight(st);
		}
		for (GridEntry entry : secureContainer.getEntries()) {
			kg += estimateItemWeight(entry.getStack());
		}
		return kg;
	}

	public static float estimateItemWeight(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0.0f;
		if (stack.getItem() instanceof TarkovArmorItem armor) {
			return armor.getWeightKg();
		}
		if (stack.getItem() instanceof TarkovMedicalItem med) {
			return med.getWeightKg();
		}
		boolean isContainer = ContainerItemHelper.isContainerItem(stack);
		float base;
		if (stack.getItem() instanceof TarkovSizedItem sized && sized.getWeightKg() >= 0.0f) {
			base = sized.getWeightKg() * Math.max(1, stack.getCount());
		} else {
			ItemSize sz = ItemSizeRegistry.getSize(stack);
			base = sz.width() * sz.height() * 0.35f + (stack.getCount() - 1) * 0.05f;
		}
		if (isContainer) {
			for (GridContainer g : ContainerItemHelper.readAllGrids(stack)) {
				for (var e : g.getEntries()) {
					base += estimateItemWeight(e.getStack());
				}
			}
		}
		return base;
	}

	public boolean isEmpty() {
		return isLootEmpty() && secureContainer.isEmpty();
	}

	public boolean isLootEmpty() {
		for (ItemStack s : equipment.values()) {
			if (!s.isEmpty()) return false;
		}
		for (ItemStack s : pockets) {
			if (!s.isEmpty()) return false;
		}
		return true;
	}

	public void clear() {
		clearLoot();
		secureContainer = new GridContainer(2, 2);
	}

	public void clearLoot() {
		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			equipment.put(slot, ItemStack.EMPTY);
		}
		for (int i = 0; i < POCKET_COUNT; i++) {
			pockets[i] = ItemStack.EMPTY;
		}
		meleeSelected = false;
		invalidateExposedCache();
	}

	public PlayerLoadout copy() {
		PlayerLoadout clone = new PlayerLoadout();
		clone.readNbt(writeNbt());
		return clone;
	}

	public PlayerLoadout copyLoot() {
		PlayerLoadout clone = new PlayerLoadout();
		clone.readNbt(writeLootNbt());
		return clone;
	}

	public NbtCompound writeNbt() {
		NbtCompound root = writeLootNbt();
		root.put("SecureContainer", secureContainer.toNbt());
		return root;
	}

	public NbtCompound writeLootNbt() {
		NbtCompound root = new NbtCompound();
		NbtCompound eqNbt = new NbtCompound();
		for (Map.Entry<EquipmentSlotType, ItemStack> entry : equipment.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				eqNbt.put(entry.getKey().getId(), entry.getValue().writeNbt(new NbtCompound()));
			}
		}
		root.put("Equipment", eqNbt);

		NbtList pocketList = new NbtList();
		for (int i = 0; i < POCKET_COUNT; i++) {
			NbtCompound slotNbt = new NbtCompound();
			slotNbt.putInt("Slot", i);
			if (!pockets[i].isEmpty()) {
				slotNbt.put("Stack", pockets[i].writeNbt(new NbtCompound()));
			}
			pocketList.add(slotNbt);
		}
		root.put("Pockets", pocketList);
		root.putBoolean("MeleeSelected", meleeSelected);
		return root;
	}

	public void readNbt(NbtCompound root) {
		clear();
		if (root == null) {
			return;
		}
		if (root.contains("Equipment", NbtElement.COMPOUND_TYPE)) {
			NbtCompound eqNbt = root.getCompound("Equipment");
			for (EquipmentSlotType slot : EquipmentSlotType.values()) {
				if (eqNbt.contains(slot.getId(), NbtElement.COMPOUND_TYPE)) {
					equipment.put(slot, ItemStack.fromNbt(eqNbt.getCompound(slot.getId())));
				}
			}
		}
		if (root.contains("Pockets", NbtElement.LIST_TYPE)) {
			NbtList pocketList = root.getList("Pockets", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < pocketList.size(); i++) {
				NbtCompound slotNbt = pocketList.getCompound(i);
				int idx = slotNbt.getInt("Slot");
				if (idx >= 0 && idx < POCKET_COUNT && slotNbt.contains("Stack", NbtElement.COMPOUND_TYPE)) {
					pockets[idx] = ItemStack.fromNbt(slotNbt.getCompound("Stack"));
				}
			}
		}
		ItemStack oldMask = getEquipment(EquipmentSlotType.FACE_MASK);
		if (EquipmentSlotType.HEADSET.accepts(oldMask) && !oldMask.isEmpty() && getEquipment(EquipmentSlotType.HEADSET).isEmpty()) {
			setEquipment(EquipmentSlotType.HEADSET, oldMask);
			setEquipment(EquipmentSlotType.FACE_MASK, ItemStack.EMPTY);
		}
		if (root.contains("SecureContainer", NbtElement.COMPOUND_TYPE)) {
			secureContainer = GridContainer.fromNbt(root.getCompound("SecureContainer"), 2, 2);
		}
		meleeSelected = root.getBoolean("MeleeSelected");
	}
}
