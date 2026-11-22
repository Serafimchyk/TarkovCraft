package com.tarkovcraft.block;

import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.registry.ModBlocks;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

	/**
	 * Creates a placeable loot crate ItemStack configured for the given TarkovLootPool.
	 * When placed in the world, loot generates lazily on first open so map builders can reuse/copy crates.
	 */
	public static ItemStack createLootCrateStack(TarkovLootPool pool) {
		SearchableLootBlock block = defaultBlockForPool(pool);
		return createLootCrateStack(block, pool, pool.getDefaultGridWidth(), pool.getDefaultGridHeight());
	}

	public static ItemStack createLootCrateStack(
		SearchableLootBlock block,
		TarkovLootPool pool,
		int width,
		int height
	) {
		SearchableLootBlock targetBlock = block != null ? block : defaultBlockForPool(pool);
		int clampedW = Math.max(1, Math.min(10, width > 0 ? width : targetBlock.getGridWidth()));
		int clampedH = Math.max(1, Math.min(10, height > 0 ? height : targetBlock.getGridHeight()));
		ItemStack stack = new ItemStack(targetBlock, 1);
		NbtCompound beTag = new NbtCompound();
		beTag.putString("LootPool", pool.getId());
		beTag.putBoolean("PendingLoot", true);
		beTag.putInt("DefaultW", clampedW);
		beTag.putInt("DefaultH", clampedH);
		BlockItem.setBlockEntityNbt(stack, ModBlocks.LOOT_CONTAINER_ENTITY, beTag);
		stack.setCustomName(
			Text.translatable("item.tarkovcraft.loot_crate_preset", Text.translatable(pool.getTranslationKey()))
				.styled(s -> s.withItalic(false).withColor(Formatting.GOLD))
		);
		return stack;
	}

	public static SearchableLootBlock defaultBlockForPool(TarkovLootPool pool) {
		if (pool == null) {
			return ModBlocks.TECHNICAL_CRATE;
		}
		return switch (pool.getDefaultBlockKey()) {
			case "weapon_crate" -> ModBlocks.WEAPON_CRATE;
			case "medbag" -> ModBlocks.MEDBAG;
			case "airdrop_crate" -> ModBlocks.AIRDROP_CRATE;
			default -> ModBlocks.TECHNICAL_CRATE;
		};
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
		super.appendTooltip(stack, world, tooltip, options);
		NbtCompound beTag = BlockItem.getBlockEntityNbt(stack);
		TarkovLootPool pool = null;
		int w = gridWidth;
		int h = gridHeight;
		boolean pending = false;
		if (beTag != null) {
			if (beTag.contains("LootPool", NbtElement.STRING_TYPE)) {
				pool = TarkovLootPool.byId(beTag.getString("LootPool"));
			}
			if (beTag.contains("DefaultW", NbtElement.NUMBER_TYPE)) {
				w = Math.max(1, Math.min(10, beTag.getInt("DefaultW")));
			}
			if (beTag.contains("DefaultH", NbtElement.NUMBER_TYPE)) {
				h = Math.max(1, Math.min(10, beTag.getInt("DefaultH")));
			}
			pending = beTag.getBoolean("PendingLoot");
		}
		if (pool != null) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.crate_pool", Text.translatable(pool.getTranslationKey()))
				.formatted(Formatting.YELLOW));
		}
		tooltip.add(Text.translatable("tooltip.tarkovcraft.crate_size", w, h).formatted(Formatting.GRAY));
		if (pending) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.crate_pending").formatted(Formatting.DARK_GRAY));
		}
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
