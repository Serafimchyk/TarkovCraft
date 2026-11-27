package com.tarkovcraft.entity;

import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class AirdropEntity extends Entity {
	public static final double DESCENT_SPEED = -0.065;
	public static final int MAX_AIR_TICKS = 3600;
	private static final DustParticleEffect RED_FLARE_DUST =
		new DustParticleEffect(new Vector3f(0.92f, 0.14f, 0.14f), 2.2f);

	private int ticksInAir = 0;
	private BlockPos landingPoint;

	public AirdropEntity(EntityType<? extends AirdropEntity> type, World world) {
		super(type, world);
		this.intersectionChecked = true;
		this.setNoGravity(true);
	}

	@Override
	protected void initDataTracker() {
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		this.ticksInAir = nbt.getInt("TicksInAir");
		landingPoint = nbt.contains("LandingPoint") ? BlockPos.fromLong(nbt.getLong("LandingPoint")) : null;
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("TicksInAir", this.ticksInAir);
		if (landingPoint != null) nbt.putLong("LandingPoint", landingPoint.asLong());
	}

	public void setLandingPoint(BlockPos landingPoint) {
		this.landingPoint = landingPoint.toImmutable();
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 256.0 * 256.0;
	}

	@Override
	public void tick() {
		super.tick();
		this.ticksInAir++;

		this.setVelocity(new Vec3d(0.0, DESCENT_SPEED, 0.0));
		this.move(MovementType.SELF, this.getVelocity());

		if (!(getWorld() instanceof ServerWorld sw)) {
			return;
		}

		if (this.ticksInAir % 3 == 0) {
			double cx = getX();
			double cy = getY() + 1.0;
			double cz = getZ();
			sw.spawnParticles(RED_FLARE_DUST, cx, cy + 0.6, cz, 4, 0.22, 0.45, 0.22, 0.01);
			sw.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, cx, cy + 1.1, cz, 1, 0.12, 0.2, 0.12, 0.04);
		}

		BlockPos below = BlockPos.ofFloored(getX(), getY() - 0.08, getZ());
		BlockState belowState = sw.getBlockState(below);
		boolean hitGround = this.isOnGround()
			|| (!belowState.isAir() && !belowState.isReplaceable())
			|| !sw.getFluidState(getBlockPos()).isEmpty()
			|| this.ticksInAir > MAX_AIR_TICKS;

		if (landingPoint != null) {
			if (getY() <= landingPoint.getY() + 0.08) {
				BlockState support = sw.getBlockState(landingPoint.down());
				if (sw.getBlockState(landingPoint).isAir()
					&& sw.getTopY(Heightmap.Type.MOTION_BLOCKING, landingPoint.getX(), landingPoint.getZ()) == landingPoint.getY()
					&& support.isSolidBlock(sw, landingPoint.down())
					&& !support.isIn(BlockTags.LEAVES) && !support.isIn(BlockTags.LOGS)) {
					placeCrate(sw, landingPoint);
				}
				discard();
			} else if (hitGround) {
				discard();
			}
		} else if (hitGround) {
			land(sw);
		}
	}

	private void land(ServerWorld sw) {
		BlockPos placePos = getBlockPos();
		if (this.ticksInAir > MAX_AIR_TICKS && sw.getBlockState(placePos.down()).isReplaceable()) {
			placePos = sw.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, placePos);
		}
		for (int i = 0; i < 4; i++) {
			BlockState current = sw.getBlockState(placePos);
			if (current.isAir() || current.isReplaceable()) {
				break;
			}
			placePos = placePos.up();
		}

		if (sw.getBlockState(placePos).isReplaceable()) placeCrate(sw, placePos);
		discard();
	}

	private static void placeCrate(ServerWorld sw, BlockPos placePos) {
		if (!sw.setBlockState(placePos, ModBlocks.AIRDROP_CRATE.getDefaultState(), Block.NOTIFY_ALL)) return;
		if (sw.getBlockEntity(placePos) instanceof SearchableLootBlockEntity be) {
			be.populateFromPool(TarkovLootPool.AIRDROP, sw.random);
			be.setSmokeTicksRemaining(TarkovConfig.get().airdropSmokeSeconds * 20);
		}
		sw.playSound(null, placePos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.0f, 0.85f);
		sw.spawnParticles(ParticleTypes.CLOUD, placePos.getX() + 0.5, placePos.getY() + 0.4, placePos.getZ() + 0.5,
			16, 0.6, 0.15, 0.6, 0.04);
	}
}
