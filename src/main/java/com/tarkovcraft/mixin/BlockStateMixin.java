package com.tarkovcraft.mixin;

import com.tarkovcraft.block.WorldBlockInteraction;
import com.tarkovcraft.container.VanillaContainerBridge;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockStateMixin {
	@Shadow
	public abstract Block getBlock();

	@Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$reserveWorldInteraction(World world, PlayerEntity player, Hand hand, BlockHitResult hit,
			CallbackInfoReturnable<ActionResult> result) {
		Block block = getBlock();
		if (!player.isCreative() && !player.isSpectator()
			&& (WorldBlockInteraction.isOpenable(block) || VanillaContainerBridge.isSupportedBlock(block))) {
			// Skip the block action while letting vanilla continue to the item in hand.
			result.setReturnValue(ActionResult.PASS);
		}
	}
}
