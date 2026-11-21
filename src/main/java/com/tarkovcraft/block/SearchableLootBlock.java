package com.tarkovcraft.block;

import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.registry.ModBlocks;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
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
	private final TarkovLootPool defaultLootPool;

	public SearchableLootBlock(Settings settings, int gridWidth, int gridHeight, String containerTitleKey) {
		this(settings, gridWidth, gridHeight, containerTitleKey, TarkovLootPool.MILITARY);
	}

	public SearchableLootBlock(
		Settings settings,
		int gridWidth,
		int gridHeight,
		String containerTitleKey,
		TarkovLootPool defaultLootPool
	) {
		super(settings);
		this.gridWidth = gridWidth;
		this.gridHeight = gridHeight;
		this.containerTitleKey = containerTitleKey;
		this.defaultLootPool = defaultLootPool != null ? defaultLootPool : TarkovLootPool.MILITARY;
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

	public TarkovLootPool getDefaultLootPool() {
		return defaultLootPool;
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

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient ? null : checkType(type, ModBlocks.LOOT_CONTAINER_ENTITY, SearchableLootBlockEntity::serverTick);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		// Opening loot containers is strictly via [F] interact, NOT right-click
		return ActionResult.PASS;
	}
}
