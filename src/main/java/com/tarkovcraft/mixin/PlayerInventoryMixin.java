package com.tarkovcraft.mixin;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.client.WorldInteractState;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.screen.TarkovScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bridges PlayerInventory (slots 0..35) to TarkovCraft's Loadout in Survival/Adventure mode:
 * - Slots 0..2: Primary 1, Primary 2, Pistol
 * - Slots 3..8: 6x (1x1) Pockets
 * - Slots 9..35:
 *     * In combat (no external workbench open): ONLY Tactical Rig items (NEVER Backpack!) ->
 *       TaCZ fast-reload pulls ammo/magazines strictly from Pockets and Tactical Rig!
 *     * While a workbench screen (e.g. TaCZ Gunsmith Table) is open: also exposes Backpack
 *       materials so crafting can use ingots/gunpowder stored in the Backpack!
 * - Whenever TaCZ consumes an ammo stack during reload, stamps its AmmoTier onto the held gun!
 */
@Mixin(PlayerInventory.class)
public abstract class PlayerInventoryMixin {
	@Shadow
	@Final
	public PlayerEntity player;

	@Shadow
	public int selectedSlot;

	@Unique
	private boolean isExternalWorkbenchOpen() {
		return player.currentScreenHandler != null
			&& player.currentScreenHandler != player.playerScreenHandler
			&& !(player.currentScreenHandler instanceof TarkovScreenHandler);
	}

	@Inject(method = "getMainHandStack", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$getMainHandStack(CallbackInfoReturnable<ItemStack> cir) {
		if (!player.isCreative() && player instanceof PlayerLoadoutHolder holder) {
			cir.setReturnValue(holder.tarkovcraft$getLoadout().getActiveHandStack(selectedSlot));
		}
	}

	@Inject(method = "getStack", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$getStack(int slot, CallbackInfoReturnable<ItemStack> cir) {
		if (!player.isCreative() && player instanceof PlayerLoadoutHolder holder && slot >= 0 && slot < 36) {
			PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
			if (slot < 9) {
				cir.setReturnValue(loadout.getHotbarDisplayStack(slot));
			} else {
				cir.setReturnValue(loadout.getExposedStorageStack(slot - 9, player.getUuid(), isExternalWorkbenchOpen()));
			}
		}
	}

	@Inject(method = "removeStack(II)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$removeStackAmount(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
		if (!player.isCreative() && player instanceof PlayerLoadoutHolder holder && slot >= 0 && slot < 36) {
			PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
			ItemStack extracted = ItemStack.EMPTY;
			if (slot == 0) {
				extracted = splitEquipment(loadout, EquipmentSlotType.PRIMARY_1, amount);
			} else if (slot == 1) {
				extracted = splitEquipment(loadout, EquipmentSlotType.PRIMARY_2, amount);
			} else if (slot == 2) {
				extracted = splitEquipment(loadout, EquipmentSlotType.PISTOL, amount);
			} else if (slot < 9) {
				ItemStack p = loadout.getPocket(slot - 3);
				if (!p.isEmpty()) {
					extracted = p.split(amount);
					if (p.isEmpty()) loadout.setPocket(slot - 3, ItemStack.EMPTY);
				}
			} else {
				extracted = loadout.removeExposedStorageStack(slot - 9, amount, player.getUuid(), isExternalWorkbenchOpen());
			}

			// If TaCZ just consumed ammo during a reload, stamp the AmmoTier onto the held gun!
			if (!extracted.isEmpty() && AmmoTier.isTaczAmmoStack(extracted)) {
				ItemStack activeGun = loadout.getActiveHandStack(selectedSlot);
				if (!activeGun.isEmpty()) {
					AmmoTier.stampLoadedTierOnGun(activeGun, AmmoTier.getFromAmmoStack(extracted));
				}
			}

			if (!extracted.isEmpty() && !player.getWorld().isClient) {
				holder.tarkovcraft$syncLoadout();
			}
			cir.setReturnValue(extracted);
		}
	}

	@Inject(method = "removeStack(I)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$removeStackFull(int slot, CallbackInfoReturnable<ItemStack> cir) {
		if (!player.isCreative() && player instanceof PlayerLoadoutHolder holder && slot >= 0 && slot < 36) {
			PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
			ItemStack extracted = ItemStack.EMPTY;
			if (slot == 0) {
				extracted = splitEquipment(loadout, EquipmentSlotType.PRIMARY_1, Integer.MAX_VALUE);
			} else if (slot == 1) {
				extracted = splitEquipment(loadout, EquipmentSlotType.PRIMARY_2, Integer.MAX_VALUE);
			} else if (slot == 2) {
				extracted = splitEquipment(loadout, EquipmentSlotType.PISTOL, Integer.MAX_VALUE);
			} else if (slot < 9) {
				ItemStack p = loadout.getPocket(slot - 3);
				if (!p.isEmpty()) {
					extracted = p.copy();
					loadout.setPocket(slot - 3, ItemStack.EMPTY);
				}
			} else {
				extracted = loadout.removeExposedStorageStack(slot - 9, Integer.MAX_VALUE, player.getUuid(), isExternalWorkbenchOpen());
			}

			if (!extracted.isEmpty() && AmmoTier.isTaczAmmoStack(extracted)) {
				ItemStack activeGun = loadout.getActiveHandStack(selectedSlot);
				if (!activeGun.isEmpty()) {
					AmmoTier.stampLoadedTierOnGun(activeGun, AmmoTier.getFromAmmoStack(extracted));
				}
			}

			if (!extracted.isEmpty() && !player.getWorld().isClient) {
				holder.tarkovcraft$syncLoadout();
			}
			cir.setReturnValue(extracted);
		}
	}

	@Inject(method = "setStack", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$setStack(int slot, ItemStack stack, CallbackInfo ci) {
		if (!player.isCreative() && player instanceof PlayerLoadoutHolder holder && slot >= 0 && slot < 36) {
			PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
			ItemStack safe = stack == null ? ItemStack.EMPTY : stack;
			if (slot == 0) {
				loadout.setEquipment(EquipmentSlotType.PRIMARY_1, safe);
			} else if (slot == 1) {
				loadout.setEquipment(EquipmentSlotType.PRIMARY_2, safe);
			} else if (slot == 2) {
				loadout.setEquipment(EquipmentSlotType.PISTOL, safe);
			} else if (slot < 9) {
				loadout.setPocket(slot - 3, safe);
			} else {
				loadout.setRigExposedStack(slot - 9, safe);
			}
			if (!player.getWorld().isClient) {
				holder.tarkovcraft$syncLoadout();
			}
			ci.cancel();
		}
	}

	@Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$insertStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!player.isCreative() && player instanceof PlayerLoadoutHolder holder) {
			PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
			boolean inserted = loadout.trySmartPickup(stack, player.getUuid());
			if (inserted) {
				holder.tarkovcraft$syncLoadout();
			}
			cir.setReturnValue(inserted);
		}
	}

	@Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$insertStackAtSlot(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (!player.isCreative() && player instanceof PlayerLoadoutHolder holder) {
			if (stack == null || stack.isEmpty()) {
				cir.setReturnValue(false);
				return;
			}
			PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
			boolean inserted = false;
			if (slot >= 3 && slot < 9) {
				int pocketIdx = slot - 3;
				ItemStack existing = loadout.getPocket(pocketIdx);
				if (existing.isEmpty() && loadout.canPlaceInPocket(stack)) {
					loadout.setPocket(pocketIdx, stack.copy());
					stack.setCount(0);
					inserted = true;
				} else if (!existing.isEmpty() && existing.isStackable()
					&& ItemStack.canCombine(existing, stack) && existing.getCount() < existing.getMaxCount()) {
					int transfer = Math.min(existing.getMaxCount() - existing.getCount(), stack.getCount());
					existing.increment(transfer);
					stack.decrement(transfer);
					inserted = stack.isEmpty();
				}
			}
			if (!inserted && !stack.isEmpty()) {
				inserted = loadout.trySmartPickup(stack, player.getUuid());
			}
			if (inserted && !player.getWorld().isClient) {
				holder.tarkovcraft$syncLoadout();
			}
			cir.setReturnValue(inserted);
		}
	}

	@Inject(method = "scrollInHotbar", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$interceptScrollForWorldMenuOrClearMelee(double scrollAmount, CallbackInfo ci) {
		if (player.getWorld().isClient && WorldInteractState.hasMultiActionMenu()) {
			WorldInteractState.scrollAction(scrollAmount > 0 ? -1 : 1);
			ci.cancel();
			return;
		}
		if (!player.isCreative() && player instanceof PlayerLoadoutHolder holder) {
			PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
			if (loadout.isMeleeSelected()) {
				loadout.setMeleeSelected(false);
			}
		}
	}

	private static ItemStack splitEquipment(PlayerLoadout loadout, EquipmentSlotType slot, int amount) {
		ItemStack cur = loadout.getEquipment(slot);
		if (cur.isEmpty()) return ItemStack.EMPTY;
		ItemStack out = cur.split(amount);
		if (cur.isEmpty()) {
			loadout.setEquipment(slot, ItemStack.EMPTY);
		}
		return out;
	}
}
