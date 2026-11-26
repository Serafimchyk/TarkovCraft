package com.tarkovcraft.ammo;

import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.search.ContainerSearchMemory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-side fallback tracker for TaCZ firearm reloads:
 * - In Creative mode (where TaCZ does not call PlayerInventory.removeStack), detects GunCurrentAmmoCount
 *   increases and stamps the matching AmmoTier from the player's inventory/loadout onto the held gun.
 * - In Survival mode, ensures guns without TarkovLoadedTier receive FMJ when ammo count increases.
 */
public final class AmmoReloadTracker {
	private static final Map<UUID, Integer> LAST_AMMO_COUNTS = new HashMap<>();

	private record AmmoSlot(ItemStack stack, Consumer<ItemStack> replace) {}

	private AmmoReloadTracker() {
	}

	public static String getGunAmmoId(ItemStack gun) {
		if (!AmmoTier.isTaczGunStack(gun) || gun.getNbt() == null) {
			return "";
		}
		Identifier gunId = Identifier.tryParse(gun.getNbt().getString("GunId"));
		if (gunId != null && GunLookup.GET_GUN_INDEX != null) {
			try {
				Object result = GunLookup.GET_GUN_INDEX.invoke(null, gunId);
				if (result instanceof Optional<?> index && index.isPresent()) {
					Object gunIndex = index.get();
					Object data = gunIndex.getClass().getMethod("getGunData").invoke(gunIndex);
					Object ammoId = data.getClass().getMethod("getAmmoId").invoke(data);
					if (ammoId instanceof Identifier id) return id.toString();
				}
			} catch (ReflectiveOperationException | LinkageError ignored) {
				// Optional bridge. A gun may instead expose its caliber directly in the stack NBT.
			}
		}
		return gun.getNbt().getString("AmmoId");
	}

	private static final class GunLookup {
		@Nullable
		private static final Method GET_GUN_INDEX = findGunIndexMethod();

		@Nullable
		private static Method findGunIndexMethod() {
			try {
				return Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getCommonGunIndex", Identifier.class);
			} catch (ReflectiveOperationException | LinkageError ignored) {
				return null;
			}
		}
	}

	public static Map<AmmoTier, Integer> countAvailableAmmo(PlayerEntity player, String ammoId) {
		if (player.isCreative()) {
			return countAvailableAmmo(creativeAmmoSlots(player), ammoId);
		}
		if (player instanceof PlayerLoadoutHolder holder) {
			return countAvailableAmmo(holder.tarkovcraft$getLoadout(), player.getUuid(), ammoId);
		}
		return Map.of();
	}

	static Map<AmmoTier, Integer> countAvailableAmmo(PlayerLoadout loadout, UUID playerId, String ammoId) {
		ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
		return countAvailableAmmo(loadoutAmmoSlots(loadout, playerId, rig, readRevealedRig(rig, playerId)), ammoId);
	}

	private static Map<AmmoTier, Integer> countAvailableAmmo(List<AmmoSlot> slots, String ammoId) {
		Map<AmmoTier, Integer> counts = new EnumMap<>(AmmoTier.class);
		for (AmmoSlot slot : slots) {
			ItemStack stack = slot.stack();
			if (isMatchingAmmo(stack, ammoId)) {
				counts.merge(AmmoTier.getFromAmmoStack(stack), stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	public static boolean prioritizeAmmoTier(ServerPlayerEntity player, String ammoId, AmmoTier tier) {
		if (!TaCZCaliberCatalog.isBallisticAmmoId(ammoId) || !TaCZCaliberCatalog.isTierAllowed(ammoId, tier)) {
			return false;
		}
		if (player.isCreative()) {
			if (!prioritizeAmmoTier(creativeAmmoSlots(player), ammoId, tier)) {
				return false;
			}
			player.getInventory().markDirty();
			player.currentScreenHandler.sendContentUpdates();
			return true;
		}
		if (player instanceof PlayerLoadoutHolder holder
				&& prioritizeAmmoTier(holder.tarkovcraft$getLoadout(), player.getUuid(), ammoId, tier)) {
			holder.tarkovcraft$syncLoadout();
			return true;
		}
		return false;
	}

	static boolean prioritizeAmmoTier(PlayerLoadout loadout, UUID playerId, String ammoId, AmmoTier tier) {
		ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
		List<GridContainer> grids = readRevealedRig(rig, playerId);
		if (!prioritizeAmmoTier(loadoutAmmoSlots(loadout, playerId, rig, grids), ammoId, tier)) {
			return false;
		}
		if (!grids.isEmpty()) {
			ContainerItemHelper.writeRigGrids(rig, grids);
		}
		loadout.invalidateExposedCache();
		return true;
	}

	private static boolean prioritizeAmmoTier(List<AmmoSlot> slots, String ammoId, AmmoTier tier) {
		Map<ItemSize, List<AmmoSlot>> slotsBySize = new HashMap<>();
		boolean hasPreferred = false;
		for (AmmoSlot slot : slots) {
			if (isMatchingAmmo(slot.stack(), ammoId)) {
				slotsBySize.computeIfAbsent(ItemSizeRegistry.getSize(slot.stack()), size -> new ArrayList<>()).add(slot);
				hasPreferred |= AmmoTier.getFromAmmoStack(slot.stack()) == tier;
			}
		}
		if (!hasPreferred) return false;

		// TaCZ scans inventory slots in order. Reorder equal-footprint ammo for this caliber only.
		for (List<AmmoSlot> sameSize : slotsBySize.values()) {
			List<ItemStack> prioritized = new ArrayList<>(sameSize.size());
			for (AmmoSlot slot : sameSize) {
				if (AmmoTier.getFromAmmoStack(slot.stack()) == tier) prioritized.add(slot.stack());
			}
			for (AmmoSlot slot : sameSize) {
				if (AmmoTier.getFromAmmoStack(slot.stack()) != tier) prioritized.add(slot.stack());
			}
			for (int i = 0; i < sameSize.size(); i++) {
				AmmoSlot slot = sameSize.get(i);
				ItemStack next = prioritized.get(i);
				if (slot.stack() != next) slot.replace().accept(next);
			}
		}
		return true;
	}

	private static List<AmmoSlot> creativeAmmoSlots(PlayerEntity player) {
		List<AmmoSlot> slots = new ArrayList<>();
		addCreativeAmmoSlots(slots, player.getInventory().main);
		addCreativeAmmoSlots(slots, player.getInventory().offHand);
		return slots;
	}

	private static void addCreativeAmmoSlots(List<AmmoSlot> slots, List<ItemStack> inventory) {
		for (int i = 0; i < inventory.size(); i++) {
			int index = i;
			ItemStack stack = inventory.get(index);
			if (AmmoTier.isTaczAmmoStack(stack)) slots.add(new AmmoSlot(stack, next -> inventory.set(index, next)));
		}
	}

	private static List<AmmoSlot> loadoutAmmoSlots(PlayerLoadout loadout, UUID playerId, ItemStack rig,
			List<GridContainer> grids) {
		List<AmmoSlot> slots = new ArrayList<>();
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			int index = i;
			ItemStack stack = loadout.getPocket(index);
			if (AmmoTier.isTaczAmmoStack(stack)) slots.add(new AmmoSlot(stack, next -> loadout.setPocket(index, next)));
		}

		ContainerSearchMemory search = ContainerItemHelper.requiresSearch(rig) ? ContainerItemHelper.getSearchMemory(rig) : null;
		int exposed = 0;
		for (GridContainer grid : grids) {
			for (GridEntry entry : grid.getEntries()) {
				if (search != null && !search.isEntryDiscovered(playerId, entry.getEntryId())) continue;
				if (exposed++ == 36 - PlayerLoadout.HOTBAR_SIZE) return slots;
				if (AmmoTier.isTaczAmmoStack(entry.getStack())) slots.add(new AmmoSlot(entry.getStack(), entry::setStack));
			}
		}
		return slots;
	}

	private static List<GridContainer> readRevealedRig(ItemStack rig, UUID playerId) {
		if (!(rig.getItem() instanceof TacticalRigItem)
				|| (ContainerItemHelper.requiresSearch(rig)
					&& !ContainerItemHelper.getSearchMemory(rig).isGridRevealed(playerId))) {
			return List.of();
		}
		return ContainerItemHelper.readRigGrids(rig);
	}

	private static boolean isMatchingAmmo(ItemStack stack, String ammoId) {
		return !ammoId.isBlank() && AmmoTier.isTaczAmmoStack(stack) && ammoId.equals(AmmoTier.getAmmoId(stack));
	}

	public static void removePlayer(UUID playerId) {
		LAST_AMMO_COUNTS.remove(playerId);
	}

	public static void tickPlayer(ServerPlayerEntity player) {
		if (player == null || player.isSpectator()) {
			return;
		}
		ItemStack held = player.getMainHandStack();
		if (!AmmoTier.isTaczGunStack(held) || !held.hasNbt()) {
			LAST_AMMO_COUNTS.remove(player.getUuid());
			return;
		}

		NbtCompound nbt = held.getNbt();
		if (nbt == null || !nbt.contains("GunCurrentAmmoCount")) {
			return;
		}

		int curCount = nbt.getInt("GunCurrentAmmoCount");
		Integer prevCount = LAST_AMMO_COUNTS.put(player.getUuid(), curCount);
		if (prevCount == null || curCount <= prevCount) {
			return;
		}

		if (player.isCreative()) {
			AmmoTier found = findAmmoTierForCreativeReload(player);
			if (found != null) {
				AmmoTier.stampLoadedTierOnGun(held, found);
			} else if (!nbt.contains(AmmoTier.NBT_LOADED_TIER)) {
				AmmoTier.stampLoadedTierOnGun(held, AmmoTier.FMJ);
			}
		} else if (!nbt.contains(AmmoTier.NBT_LOADED_TIER)) {
			AmmoTier.stampLoadedTierOnGun(held, AmmoTier.FMJ);
		}
	}

	@Nullable
	private static AmmoTier findAmmoTierForCreativeReload(ServerPlayerEntity player) {
		String ammoId = getGunAmmoId(player.getMainHandStack());
		if (!ammoId.isBlank()) {
			for (AmmoSlot slot : creativeAmmoSlots(player)) {
				if (isMatchingAmmo(slot.stack(), ammoId)) return AmmoTier.getFromAmmoStack(slot.stack());
			}
			return null;
		}

		AmmoTier fallbackFmj = null;
		for (int i = 0; i < player.getInventory().main.size(); i++) {
			ItemStack st = player.getInventory().main.get(i);
			if (AmmoTier.isTaczAmmoStack(st)) {
				AmmoTier t = AmmoTier.getFromAmmoStack(st);
				if (t != AmmoTier.FMJ) return t;
				fallbackFmj = t;
			}
		}
		for (int i = 0; i < player.getInventory().offHand.size(); i++) {
			ItemStack st = player.getInventory().offHand.get(i);
			if (AmmoTier.isTaczAmmoStack(st)) {
				AmmoTier t = AmmoTier.getFromAmmoStack(st);
				if (t != AmmoTier.FMJ) return t;
				fallbackFmj = t;
			}
		}
		if (player instanceof PlayerLoadoutHolder holder) {
			PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
			for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
				ItemStack st = loadout.getPocket(i);
				if (AmmoTier.isTaczAmmoStack(st)) {
					AmmoTier t = AmmoTier.getFromAmmoStack(st);
					if (t != AmmoTier.FMJ) return t;
					fallbackFmj = t;
				}
			}
			ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
			if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
				ContainerSearchMemory search = ContainerItemHelper.requiresSearch(rig) ? ContainerItemHelper.getSearchMemory(rig) : null;
				for (GridContainer g : readRevealedRig(rig, player.getUuid())) {
					for (GridEntry entry : g.getEntries()) {
						if (search != null && !search.isEntryDiscovered(player.getUuid(), entry.getEntryId())) continue;
						ItemStack st = entry.getStack();
						if (AmmoTier.isTaczAmmoStack(st)) {
							AmmoTier t = AmmoTier.getFromAmmoStack(st);
							if (t != AmmoTier.FMJ) return t;
							fallbackFmj = t;
						}
					}
				}
			}
		}
		return fallbackFmj;
	}
}
