package com.tarkovcraft.loot;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Context-aware classifier that determines the most fitting war-torn city loot pool
 * for any container based on surrounding interior/building blocks.
 */
public final class CityZoneClassifier {
	private CityZoneClassifier() {
	}

	public static TarkovLootPool classify(ServerWorld world, BlockPos pos, Block containerBlock) {
		if (world == null || pos == null) {
			return TarkovLootPool.RESIDENTIAL;
		}

		int medicalScore = 0;
		int factoryScore = 0;
		int garageScore = 0;
		int schoolScore = 0;
		int officeScore = 0;
		int cafeScore = 0;
		int supermarketScore = 0;
		int residentialScore = 0;
		int policeScore = 0;
		int militaryScore = 0;
		int portScore = 0;

		if (containerBlock instanceof ShulkerBoxBlock) {
			militaryScore += 8;
			portScore += 4;
		}

		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -6; dx <= 6; dx++) {
			for (int dy = -2; dy <= 3; dy++) {
				for (int dz = -6; dz <= 6; dz++) {
					m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					BlockState st = world.getBlockState(m);
					Block b = st.getBlock();
					if (st.isAir()) {
						continue;
					}

					if (b == Blocks.BREWING_STAND) {
						medicalScore += 10;
					} else if (b == Blocks.BLAST_FURNACE || b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.DAMAGED_ANVIL) {
						factoryScore += 7;
						garageScore += 4;
					} else if (b == Blocks.HOPPER || b == Blocks.IRON_BLOCK || b == Blocks.COPPER_BLOCK || b == Blocks.GRINDSTONE || b == Blocks.STONECUTTER) {
						factoryScore += 4;
						garageScore += 3;
					} else if (b == Blocks.SMITHING_TABLE || b == Blocks.COAL_BLOCK || b == Blocks.RAIL || b == Blocks.POWERED_RAIL) {
						garageScore += 5;
						factoryScore += 3;
					} else if (b == Blocks.LECTERN) {
						schoolScore += 9;
						officeScore += 6;
					} else if (b == Blocks.BOOKSHELF || b == Blocks.CHISELED_BOOKSHELF) {
						schoolScore += 2;
						officeScore += 2;
					} else if (b == Blocks.SMOKER || b == Blocks.CAMPFIRE || b == Blocks.SOUL_CAMPFIRE || b == Blocks.CAKE) {
						cafeScore += 7;
						supermarketScore += 3;
					} else if (b == Blocks.CAULDRON || b == Blocks.WATER_CAULDRON) {
						cafeScore += 3;
						medicalScore += 2;
					} else if (b instanceof BedBlock) {
						residentialScore += 6;
					} else if (b == Blocks.FURNACE || b == Blocks.JUKEBOX || b instanceof CarpetBlock) {
						residentialScore += 2;
					} else if (b == Blocks.IRON_BARS || b == Blocks.IRON_DOOR) {
						policeScore += 4;
						militaryScore += 2;
					} else if (b == Blocks.TNT || b == Blocks.OBSIDIAN || b == Blocks.DISPENSER || b == Blocks.DROPPER || b == Blocks.SPAWNER) {
						militaryScore += 6;
						policeScore += 3;
					} else if (b == Blocks.BARREL) {
						supermarketScore += 1;
						portScore += 1;
					} else if (b == Blocks.WHITE_CONCRETE || b == Blocks.RED_CONCRETE) {
						medicalScore += 1;
					} else if (b == Blocks.BLACK_CONCRETE || b == Blocks.GRAY_CONCRETE) {
						garageScore += 1;
					}
				}
			}
		}

		// Harbor / Port water proximity check (8 cardinal & diagonal rays at 12..24 blocks)
		int waterHits = 0;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
		for (int[] d : dirs) {
			for (int dist = 12; dist <= 24; dist += 6) {
				m.set(pos.getX() + d[0] * dist, Math.max(world.getBottomY() + 1, pos.getY() - 2), pos.getZ() + d[1] * dist);
				if (world.getFluidState(m).isIn(FluidTags.WATER)) {
					waterHits++;
					break;
				}
			}
		}
		if (waterHits >= 3) {
			portScore += waterHits * 4;
		}

		// Open sky / street barricade check
		boolean underSky = world.isSkyVisible(pos.up());
		if (underSky && militaryScore < 6 && portScore < 12) {
			militaryScore += 6;
		}

		// Pick highest-scoring zone if clear signal exists
		int bestScore = 8;
		TarkovLootPool best = null;

		if (medicalScore >= bestScore) {
			bestScore = medicalScore;
			best = residentialScore >= 6 ? TarkovLootPool.MEDICAL : TarkovLootPool.PHARMACY;
		}
		if (militaryScore > bestScore) {
			bestScore = militaryScore;
			best = underSky ? TarkovLootPool.BATTLEFIELD : TarkovLootPool.MILITARY;
		}
		if (policeScore > bestScore) {
			bestScore = policeScore;
			best = TarkovLootPool.POLICE;
		}
		if (portScore > bestScore) {
			bestScore = portScore;
			best = TarkovLootPool.PORT;
		}
		if (factoryScore > bestScore) {
			bestScore = factoryScore;
			best = TarkovLootPool.FACTORY;
		}
		if (garageScore > bestScore) {
			bestScore = garageScore;
			best = TarkovLootPool.GARAGE;
		}
		if (cafeScore > bestScore) {
			bestScore = cafeScore;
			best = supermarketScore >= 12 ? TarkovLootPool.SUPERMARKET : TarkovLootPool.CAFE;
		}
		if (schoolScore > bestScore && residentialScore < 6) {
			bestScore = schoolScore;
			best = officeScore > schoolScore ? TarkovLootPool.OFFICE : TarkovLootPool.SCHOOL;
		}
		if (residentialScore > bestScore) {
			bestScore = residentialScore;
			best = TarkovLootPool.RESIDENTIAL;
		}

		if (best != null) {
			return best;
		}

		// Deterministic per-building-block fallback (32x32 block districts) so one building shares a coherent theme
		int districtX = pos.getX() >> 5;
		int districtZ = pos.getZ() >> 5;
		int hash = Math.abs(districtX * 73856093 ^ districtZ * 19349663) % 100;
		if (hash < 32) return TarkovLootPool.RESIDENTIAL;
		if (hash < 46) return TarkovLootPool.OFFICE;
		if (hash < 58) return TarkovLootPool.CAFE;
		if (hash < 68) return TarkovLootPool.SUPERMARKET;
		if (hash < 78) return TarkovLootPool.GARAGE;
		if (hash < 86) return TarkovLootPool.SCHOOL;
		if (hash < 93) return TarkovLootPool.POLICE;
		return TarkovLootPool.BATTLEFIELD;
	}
}
