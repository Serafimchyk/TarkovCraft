package com.tarkovcraft.item;

import com.tarkovcraft.ammo.AmmoTier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/** Lets right-click items use the normal air-use path when a block captures the click. */
public final class HeldItemInteraction {
	private HeldItemInteraction() {
	}

	public static boolean takesPriority(PlayerEntity player, Hand hand) {
		if (player.isSpectator() || player.isCreative()) {
			return false;
		}
		var stack = player.getStackInHand(hand);
		if (hand == Hand.MAIN_HAND && stack.isEmpty()) {
			stack = player.getOffHandStack();
		}
		return stack.getItem() instanceof TarkovGrenadeItem
			|| stack.getItem() instanceof TarkovMedicalItem
			|| stack.getItem() instanceof TarkovStimulatorItem
			|| AmmoTier.isTaczGunStack(stack);
	}
}
