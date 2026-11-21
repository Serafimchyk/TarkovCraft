package com.tarkovcraft.worldgen;

import com.mojang.serialization.Codec;
import com.tarkovcraft.block.SearchableLootBlock;
import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Overworld surface structures for TarkovCraft:
 * ROADBLOCK, MED_TENT, MILITARY_CAMP, FOREST_STASH.
 */
public class TarkovStructureFeature extends Feature<DefaultFeatureConfig> {
	public enum StructureKind {
		ROADBLOCK(2, 2),
		MED_TENT(2, 2),
		MILITARY_CAMP(3, 2),
		FOREST_STASH(1, 1);

		private final int halfW;
		private final int halfD;

		StructureKind(int halfW, int halfD) {
			this.halfW = halfW;
			this.halfD = halfD;
		}
	}

	private final StructureKind kind;

	public TarkovStructureFeature(Codec<DefaultFeatureConfig> codec, StructureKind kind) {
		super(codec);
		this.kind = kind;
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		if (!TarkovConfig.get().worldgenStructuresEnabled) {
			return false;
		}
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		BlockPos origin = context.getOrigin();

		if (!isFootprintSafe(world, origin, kind.halfW, kind.halfD)) {
			return false;
		}

		switch (kind) {
			case ROADBLOCK -> buildRoadblock(world, origin, random);
			case MED_TENT -> buildMedTent(world, origin, random);
			case MILITARY_CAMP -> buildMilitaryCamp(world, origin, random);
			case FOREST_STASH -> buildForestStash(world, origin, random);
		}
		return true;
	}

	private static boolean isFootprintSafe(StructureWorldAccess world, BlockPos origin, int halfW, int halfD) {
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();

		for (int dx = -halfW; dx <= halfW; dx++) {
			for (int dz = -halfD; dz <= halfD; dz++) {
				int x = ox + dx;
				int z = oz + dz;
				int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (Math.abs(topY - oy) > 2) {
					return false;
				}
				BlockPos groundPos = new BlockPos(x, topY - 1, z);
				BlockState ground = world.getBlockState(groundPos);
				if (!ground.isOpaqueFullCube(world, groundPos)
					|| !ground.getFluidState().isEmpty()
					|| ground.isIn(BlockTags.LOGS)
					|| ground.isIn(BlockTags.LEAVES)
					|| ground.isOf(Blocks.ICE)
					|| ground.isOf(Blocks.PACKED_ICE)
					|| ground.isOf(Blocks.BLUE_ICE)) {
					return false;
				}
				for (int dy = 0; dy <= 3; dy++) {
					BlockPos above = new BlockPos(x, topY + dy, z);
					if (!world.getBlockState(above).isReplaceable()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static void buildRoadblock(StructureWorldAccess world, BlockPos origin, Random random) {
		int y = origin.getY();
		int ox = origin.getX();
		int oz = origin.getZ();

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				setFoundation(world, ox + dx, y - 1, oz + dz, Blocks.COBBLESTONE.getDefaultState());
			}
		}

		// Sandbag / cobblestone barricade on north and south edges
		for (int dx = -2; dx <= 2; dx++) {
			if (dx != 0) {
				BlockState wall = (dx % 2 == 0 ? Blocks.COBBLESTONE_WALL : Blocks.MOSSY_COBBLESTONE_WALL).getDefaultState();
				setBlock(world, ox + dx, y, oz - 2, wall);
				setBlock(world, ox + dx, y, oz + 2, Blocks.SANDSTONE.getDefaultState());
			}
		}
		setBlock(world, ox - 2, y, oz - 1, Blocks.HAY_BLOCK.getDefaultState());
		setBlock(world, ox - 2, y, oz, Blocks.IRON_BARS.getDefaultState());
		setBlock(world, ox + 2, y, oz, Blocks.IRON_BARS.getDefaultState());
		setBlock(world, ox + 2, y, oz - 1, Blocks.OAK_FENCE.getDefaultState());
		setBlock(world, ox + 2, y + 1, oz - 1, Blocks.LANTERN.getDefaultState());

		placeCrate(world, new BlockPos(ox - 1, y, oz), ModBlocks.WEAPON_CRATE, TarkovLootPool.MILITARY, random);
		if (random.nextBoolean()) {
			placeCrate(world, new BlockPos(ox + 1, y, oz + 1), ModBlocks.WEAPON_CRATE, TarkovLootPool.MILITARY, random);
		}
	}

	private static void buildMedTent(StructureWorldAccess world, BlockPos origin, Random random) {
		int y = origin.getY();
		int ox = origin.getX();
		int oz = origin.getZ();

		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				setFoundation(world, ox + dx, y - 1, oz + dz, Blocks.COARSE_DIRT.getDefaultState());
			}
		}

		// Corner fence posts (height 3)
		int[][] corners = {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}};
		for (int[] c : corners) {
			for (int dy = 0; dy < 3; dy++) {
				setBlock(world, ox + c[0], y + dy, oz + c[1], Blocks.OAK_FENCE.getDefaultState());
			}
		}

		// White wool roof with red cross in center
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean isCross = (dx == 0 && Math.abs(dz) <= 1) || (dz == 0 && Math.abs(dx) <= 1);
				Block roofBlock = isCross ? Blocks.RED_WOOL : Blocks.WHITE_WOOL;
				setBlock(world, ox + dx, y + 3, oz + dz, roofBlock.getDefaultState());
			}
		}

		setBlock(world, ox, y + 2, oz, Blocks.LANTERN.getDefaultState());
		placeCrate(world, new BlockPos(ox - 1, y, oz), ModBlocks.MEDBAG, TarkovLootPool.MEDICAL, random);
		if (random.nextFloat() < 0.65f) {
			placeCrate(world, new BlockPos(ox + 1, y, oz + 1), ModBlocks.MEDBAG, TarkovLootPool.MEDICAL, random);
		}
	}

	private static void buildMilitaryCamp(StructureWorldAccess world, BlockPos origin, Random random) {
		int y = origin.getY();
		int ox = origin.getX();
		int oz = origin.getZ();

		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				setFoundation(world, ox + dx, y - 1, oz + dz, Blocks.COARSE_DIRT.getDefaultState());
			}
		}

		// Campfire in center
		setBlock(world, ox, y, oz, Blocks.CAMPFIRE.getDefaultState());
		setBlock(world, ox - 1, y, oz + 1, Blocks.SPRUCE_STAIRS.getDefaultState());

		// Small green wool tent on left side
		for (int dy = 0; dy < 2; dy++) {
			setBlock(world, ox - 3, y + dy, oz - 2, Blocks.SPRUCE_FENCE.getDefaultState());
			setBlock(world, ox - 3, y + dy, oz + 2, Blocks.SPRUCE_FENCE.getDefaultState());
			setBlock(world, ox - 1, y + dy, oz - 2, Blocks.SPRUCE_FENCE.getDefaultState());
		}
		for (int dx = -3; dx <= -1; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				setBlock(world, ox + dx, y + 2, oz + dz, Blocks.GREEN_WOOL.getDefaultState());
			}
		}

		setBlock(world, ox + 2, y, oz - 1, Blocks.BARREL.getDefaultState());
		setBlock(world, ox + 3, y, oz + 1, Blocks.BARREL.getDefaultState());

		placeCrate(world, new BlockPos(ox - 2, y, oz), ModBlocks.WEAPON_CRATE, TarkovLootPool.MILITARY, random);
		placeCrate(world, new BlockPos(ox + 2, y, oz + 1), ModBlocks.TECHNICAL_CRATE, TarkovLootPool.CIVILIAN, random);
	}

	private static void buildForestStash(StructureWorldAccess world, BlockPos origin, Random random) {
		int y = origin.getY();
		int ox = origin.getX();
		int oz = origin.getZ();

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				setFoundation(world, ox + dx, y - 1, oz + dz, Blocks.MOSSY_COBBLESTONE.getDefaultState());
			}
		}

		setBlock(world, ox - 1, y, oz - 1, Blocks.OAK_LOG.getDefaultState());
		setBlock(world, ox + 1, y, oz - 1, Blocks.MOSSY_COBBLESTONE.getDefaultState());
		setBlock(world, ox - 1, y, oz + 1, Blocks.FERN.getDefaultState());

		boolean isMilitary = random.nextFloat() < 0.30f;
		SearchableLootBlock crate = isMilitary ? ModBlocks.WEAPON_CRATE : ModBlocks.TECHNICAL_CRATE;
		TarkovLootPool pool = isMilitary ? TarkovLootPool.MILITARY : TarkovLootPool.CIVILIAN;
		placeCrate(world, new BlockPos(ox, y, oz), crate, pool, random);
	}

	private static void setFoundation(StructureWorldAccess world, int x, int startY, int z, BlockState state) {
		for (int dy = 0; dy < 3; dy++) {
			BlockPos pos = new BlockPos(x, startY - dy, z);
			if (dy == 0 || world.getBlockState(pos).isReplaceable()) {
				world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
			} else {
				break;
			}
		}
	}

	private static void setBlock(StructureWorldAccess world, int x, int y, int z, BlockState state) {
		world.setBlockState(new BlockPos(x, y, z), state, Block.NOTIFY_LISTENERS);
	}

	private static void placeCrate(
		StructureWorldAccess world,
		BlockPos cratePos,
		SearchableLootBlock crateBlock,
		TarkovLootPool pool,
		Random random
	) {
		world.setBlockState(cratePos, crateBlock.getDefaultState(), Block.NOTIFY_LISTENERS);
		if (world.getBlockEntity(cratePos) instanceof SearchableLootBlockEntity be) {
			be.populateFromPool(pool, random);
		}
	}
}
