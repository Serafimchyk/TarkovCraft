package com.tarkovcraft.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents vanilla ArmorItem right-click equipAndSwap in Survival/Adventure mode
 * so players do not lose items into hidden vanilla armor slots.
 */
@Mixin(ArmorItem.class)
public abstract class ArmorItemMixin {
	@Inject(
		method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/TypedActionResult;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void tarkovcraft$disableVanillaArmorUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		if (user != null && !user.isCreative() && !user.isSpectator()) {
			if (!world.isClient) {
				user.sendMessage(Text.translatable("message.tarkovcraft.vanilla_armor_disabled"), true);
			}
			cir.setReturnValue(TypedActionResult.fail(user.getStackInHand(hand)));
		}
	}
}
