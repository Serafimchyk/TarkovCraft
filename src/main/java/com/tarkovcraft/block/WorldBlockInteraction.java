package com.tarkovcraft.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WoodType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

public final class WorldBlockInteraction {
	private WorldBlockInteraction() { }

	public static boolean isOpenable(Block block) {
		return block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock;
	}

	public static boolean canOpenByHand(BlockState state) {
		Block block = state.getBlock();
		return block instanceof FenceGateBlock
			|| block instanceof DoorBlock && DoorBlock.canOpenByHand(state)
			|| block instanceof TrapdoorBlock && !state.isOf(Blocks.IRON_TRAPDOOR);
	}

	public static void useOpenable(ServerPlayerEntity player, BlockState state, BlockHitResult hit) {
		var world = player.getServerWorld();
		var pos = hit.getBlockPos();
		if (!world.getWorldBorder().contains(pos) || !world.canPlayerModifyAt(player, pos)) return;
		if (state.getBlock().onUse(state, world, pos, player, Hand.MAIN_HAND, hit).isAccepted()) {
			BlockState updated = world.getBlockState(pos);
			if (!updated.isOf(state.getBlock()) || updated.get(Properties.OPEN).equals(state.get(Properties.OPEN))) return;
			// The vanilla action plays for nearby players but expects the clicker's client to predict its own sound.
			SoundEvent sound = toggleSound(state.getBlock(), updated.get(Properties.OPEN));
			player.playSound(sound, SoundCategory.BLOCKS, 1.0f, world.getRandom().nextFloat() * 0.1f + 0.9f);
		}
	}

	private static SoundEvent toggleSound(Block block, boolean open) {
		if (block instanceof DoorBlock door) {
			var type = door.getBlockSetType();
			return open ? type.doorOpen() : type.doorClose();
		}
		String path = Registries.BLOCK.getId(block).getPath();
		if (block instanceof TrapdoorBlock) {
			var type = BlockSetType.stream().filter(candidate -> path.equals(candidate.name() + "_trapdoor"))
				.findFirst().orElse(BlockSetType.OAK);
			return open ? type.trapdoorOpen() : type.trapdoorClose();
		}
		var type = WoodType.stream().filter(candidate -> path.equals(candidate.name() + "_fence_gate"))
			.findFirst().orElse(WoodType.OAK);
		return open ? type.fenceGateOpen() : type.fenceGateClose();
	}
}
