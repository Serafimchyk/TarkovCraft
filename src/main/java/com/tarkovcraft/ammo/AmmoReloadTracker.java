package com.tarkovcraft.ammo;

import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side fallback tracker for TaCZ firearm reloads:
 * - In Creative mode (where TaCZ does not call PlayerInventory.removeStack), detects GunCurrentAmmoCount
 *   increases and stamps the matching AmmoTier from the player's inventory/loadout onto the held gun.
 * - In Survival mode, ensures guns without TarkovLoadedTier receive FMJ when ammo count increases.
 */
public final class AmmoReloadTracker {
	private static final Map<UUID, Integer> LAST_AMMO_COUNTS = new HashMap<>();

	private AmmoReloadTracker() {
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
				for (GridContainer g : ContainerItemHelper.readRigGrids(rig)) {
					for (GridEntry entry : g.getEntries()) {
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
