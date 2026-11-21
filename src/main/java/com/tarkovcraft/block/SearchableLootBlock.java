package com.tarkovcraft.block;

import com.tarkovcraft.registry.ModBlocks;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class SearchableLootBlock extends BlockWithEntity {
	private final int gridWidth;
	private final int gridHeight;
	private final String containerTitleKey;

	public SearchableLootBlock(Settings settings, int gridWidth, int gridHeight, String containerTitleKey) {
		super(settings);
		this.gridWidth = gridWidth;
		this.gridHeight = gridHeight;
		this.containerTitleKey = containerTitleKey;
	}

	public int getGridWidth() {
		return gridWidth;
	}

	public int getGridHeight() {
		return gridHeight;
	}

	public String getContainerTitleKey() {
		return containerTitleKey;
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new SearchableLootBlockEntity(ModBlocks.LOOT_CONTAINER_ENTITY, pos, state, gridWidth, gridHeight);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		// Opening loot containers is strictly via [F] interact, NOT right-click
		return ActionResult.PASS;
	}
}
