package com.tarkovcraft.mixin;

import com.tarkovcraft.container.VanillaContainerBridge;
import com.tarkovcraft.item.HeldItemInteraction;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
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
	private void tarkovcraft$passToHeldItem(World world, PlayerEntity player, Hand hand, BlockHitResult hit,
			CallbackInfoReturnable<ActionResult> result) {
		Block block = getBlock();
		if ((block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
				|| VanillaContainerBridge.isSupportedBlock(block)) && HeldItemInteraction.takesPriority(player, hand)) {
			result.setReturnValue(ActionResult.PASS);
		}
	}
}
