package com.tarkovcraft.loadout;

import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovSizedItem;
import net.minecraft.item.ItemStack;
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

	public void setEquipment(EquipmentSlotType slot, ItemStack stack) {
		equipment.put(slot, stack == null ? ItemStack.EMPTY : stack);
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
					if (grid.tryAutoPlace(incoming)) {
						changed = true;
						break;
					}
				}
				if (changed || incoming.isEmpty()) {
					ContainerItemHelper.writeRigGrids(rigStack, rigGrids);
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
				if (bpGrid.tryAutoPlace(incoming)) {
					ContainerItemHelper.writeBackpackGrid(bpStack, bpGrid);
					return true;
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

	public ItemStack getExposedStorageStack(int slotIndex, @Nullable UUID playerId, boolean includeBackpack) {
		if (slotIndex < 0) return ItemStack.EMPTY;
		int cursor = 0;

		ItemStack rig = getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
			boolean ok = !ContainerItemHelper.requiresSearch(rig)
				|| (playerId != null && ContainerItemHelper.getSearchMemory(rig).isGridRevealed(playerId));
			if (ok) {
				for (GridContainer g : ContainerItemHelper.readRigGrids(rig)) {
					for (var entry : g.getEntries()) {
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
				boolean ok = !ContainerItemHelper.requiresSearch(bp)
					|| (playerId != null && ContainerItemHelper.getSearchMemory(bp).isGridRevealed(playerId));
				if (ok) {
					GridContainer g = ContainerItemHelper.readBackpackGrid(bp);
					for (var entry : g.getEntries()) {
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

		ItemStack rig = getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
			List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
			for (GridContainer g : grids) {
				for (var entry : g.getEntries()) {
					if (cursor == slotIndex) {
						ItemStack st = entry.getStack();
						ItemStack extracted = st.split(amount);
						if (st.isEmpty()) {
							g.remove(entry.getEntryId());
						}
						ContainerItemHelper.writeRigGrids(rig, grids);
						return extracted;
					}
					cursor++;
				}
			}
		}

		if (includeBackpack) {
			ItemStack bp = getEquipment(EquipmentSlotType.BACKPACK);
			if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem) {
				GridContainer g = ContainerItemHelper.readBackpackGrid(bp);
				for (var entry : g.getEntries()) {
					if (cursor == slotIndex) {
						ItemStack st = entry.getStack();
						ItemStack extracted = st.split(amount);
						if (st.isEmpty()) {
							g.remove(entry.getEntryId());
						}
						ContainerItemHelper.writeBackpackGrid(bp, g);
						return extracted;
					}
					cursor++;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	public boolean setRigExposedStack(int rigSlotIndex, ItemStack incoming) {
		if (rigSlotIndex < 0) return false;
		ItemStack rig = getEquipment(EquipmentSlotType.RIG);
		if (rig.isEmpty() || !(rig.getItem() instanceof TacticalRigItem)) return false;
		List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
		int cursor = 0;
		for (GridContainer g : grids) {
			for (var entry : g.getEntries()) {
				if (cursor == rigSlotIndex) {
					if (incoming == null || incoming.isEmpty()) {
						g.remove(entry.getEntryId());
					} else {
						entry.setStack(incoming.copy());
					}
					ContainerItemHelper.writeRigGrids(rig, grids);
					return true;
				}
				cursor++;
			}
		}
		// New item pushed back into an empty rig slot (e.g. TaCZ unloading rounds)
		if (incoming != null && !incoming.isEmpty()) {
			ItemStack copy = incoming.copy();
			for (GridContainer g : grids) {
				if (g.tryAutoPlace(copy)) {
					ContainerItemHelper.writeRigGrids(rig, grids);
					return true;
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
		for (ItemStack s : equipment.values()) {
			if (!s.isEmpty()) return false;
		}
		for (ItemStack s : pockets) {
			if (!s.isEmpty()) return false;
		}
		return true;
	}

	public void clear() {
		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			equipment.put(slot, ItemStack.EMPTY);
		}
		for (int i = 0; i < POCKET_COUNT; i++) {
			pockets[i] = ItemStack.EMPTY;
		}
		meleeSelected = false;
	}

	public PlayerLoadout copy() {
		PlayerLoadout clone = new PlayerLoadout();
		clone.readNbt(this.writeNbt());
		return clone;
	}

	public NbtCompound writeNbt() {
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
		meleeSelected = root.getBoolean("MeleeSelected");
	}
}
