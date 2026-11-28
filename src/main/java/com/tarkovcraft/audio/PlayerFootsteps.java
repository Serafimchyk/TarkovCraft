package com.tarkovcraft.audio;

import com.tarkovcraft.audio.TarkovFootstepSounds.Gait;
import com.tarkovcraft.entity.TarkovAiFirearmController;
import com.tarkovcraft.audio.TarkovFootstepSounds.Surface;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Set;

public final class PlayerFootsteps {
	private static final Set<BlockSoundGroup> METAL = Set.of(BlockSoundGroup.METAL, BlockSoundGroup.ANVIL,
		BlockSoundGroup.CHAIN, BlockSoundGroup.LANTERN, BlockSoundGroup.COPPER, BlockSoundGroup.NETHERITE);
	private static final Set<BlockSoundGroup> WOOD = Set.of(BlockSoundGroup.WOOD, BlockSoundGroup.NETHER_STEM,
		BlockSoundGroup.NETHER_WOOD, BlockSoundGroup.BAMBOO_WOOD, BlockSoundGroup.CHERRY_WOOD,
		BlockSoundGroup.LADDER, BlockSoundGroup.SCAFFOLDING, BlockSoundGroup.BAMBOO, BlockSoundGroup.CHISELED_BOOKSHELF);
	private static final Set<BlockSoundGroup> SOFT = Set.of(BlockSoundGroup.GRASS, BlockSoundGroup.MOSS_BLOCK,
		BlockSoundGroup.MOSS_CARPET, BlockSoundGroup.ROOTED_DIRT, BlockSoundGroup.MUD, BlockSoundGroup.PACKED_MUD,
		BlockSoundGroup.MUDDY_MANGROVE_ROOTS, BlockSoundGroup.WOOL, BlockSoundGroup.WET_GRASS);

	private World previousWorld;
	private Vec3d previousPosition;
	private boolean wasSupported;
	private double airPeakY;
	private double stepDistance;
	private int stepCooldown;
	private int idleTicks;

	public void tick(ServerPlayerEntity player, PlayerLoadout loadout) {
		World world = player.getWorld();
		Vec3d position = player.getPos();
		BlockState feet = world.getBlockState(player.getBlockPos());
		boolean climbable = feet.isIn(BlockTags.CLIMBABLE);
		boolean supported = player.isOnGround() || climbable;
		boolean canMakeSteps = player.isAlive() && !player.isSilent() && !player.isSpectator()
			&& !player.hasVehicle() && !player.getAbilities().flying && !player.isFallFlying()
			&& !player.isSwimming() && !player.isSubmergedInWater() && !player.isInTeleportationState();
		Vec3d delta = previousWorld == world && previousPosition != null ? position.subtract(previousPosition) : Vec3d.ZERO;
		boolean hadPosition = previousPosition != null && previousWorld == world;
		previousPosition = canMakeSteps ? position : null;
		previousWorld = world;
		if (stepCooldown > 0) stepCooldown--;

		if (!canMakeSteps || !hadPosition || delta.horizontalLengthSquared() > 1.0 || Math.abs(delta.y) > 2.0) {
			wasSupported = supported;
			airPeakY = position.y;
			stepDistance = 0.0;
			idleTicks = 0;
			return;
		}

		if (!supported) {
			if (wasSupported && delta.y > 0.12) {
				playGear(player, loadout, true);
			}
			airPeakY = wasSupported ? Math.max(position.y - delta.y, position.y) : Math.max(airPeakY, position.y);
			wasSupported = false;
			stepDistance = 0.0;
			return;
		}
		if (!wasSupported && player.isOnGround() && airPeakY - position.y >= 0.45) {
			playGear(player, loadout, true);
			stepCooldown = Math.max(stepCooldown, 4);
		}
		wasSupported = true;

		double distance = climbable ? delta.length() : delta.horizontalLength();
		if (distance <= 0.01) {
			if (++idleTicks >= 5) stepDistance = 0.0;
			return;
		}
		idleTicks = 0;
		Gait gait = player.isSneaking() ? Gait.SNEAK : player.isSprinting() ? Gait.SPRINT : Gait.WALK;
		if (gait == Gait.SNEAK) {
			stepDistance = 0.0;
			if (!player.isOnGround() || stepCooldown != 0) return;
			stepCooldown = 14;
		} else {
			double stride = gait == Gait.SPRINT ? 1.55 : 1.35;
			stepDistance = Math.min(stride, stepDistance + distance);
			if (stepCooldown != 0 || stepDistance < stride) return;
			stepDistance = 0.0;
			stepCooldown = 4;
		}

		Surface surface = surfaceAt(player, feet, climbable);
		float variation = player.getRandom().nextFloat();
		switch (gait) {
			case WALK -> play(player, surface.sound(gait), MathHelper.lerp(variation, 0.85f, 1.0f),
				randomPitch(player, 0.94f, 1.06f));
			case SPRINT -> play(player, surface.sound(gait), MathHelper.lerp(variation, 1.3f, 1.4f),
				randomPitch(player, 0.90f, 0.98f));
			case SNEAK -> play(player, surface.sound(gait), 0.22f, randomPitch(player, 0.90f, 0.99f));
		}
		if (gait != Gait.SNEAK) {
			playGear(player, loadout, gait == Gait.SPRINT);
			TarkovAiFirearmController.reportNoise(player, gait == Gait.SPRINT ? 20 : 11);
		}
	}

	private static Surface surfaceAt(ServerPlayerEntity player, BlockState feet, boolean climbable) {
		if (player.isTouchingWater()) return Surface.WATER;
		if (climbable || feet.isIn(BlockTags.INSIDE_STEP_SOUND_BLOCKS)
			|| feet.isIn(BlockTags.COMBINATION_STEP_SOUND_BLOCKS)) return material(feet);
		BlockPos support = player.getSteppingPos();
		return material(player.getWorld().getBlockState(support));
	}

	private static Surface material(BlockState state) {
		BlockSoundGroup group = state.getSoundGroup();
		if (group == BlockSoundGroup.SNOW || group == BlockSoundGroup.POWDER_SNOW
			|| state.isIn(BlockTags.SNOW)) return Surface.SNOW;
		if (METAL.contains(group) || state.isOf(Blocks.IRON_BLOCK) || state.isOf(Blocks.IRON_BARS)
			|| state.isOf(Blocks.IRON_DOOR) || state.isOf(Blocks.IRON_TRAPDOOR)) return Surface.METAL;
		if (WOOD.contains(group) || state.isIn(BlockTags.LOGS)) return Surface.WOOD;
		if (SOFT.contains(group) || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.DIRT)) return Surface.GRASS;
		if (group == BlockSoundGroup.GRAVEL || group == BlockSoundGroup.SAND || group == BlockSoundGroup.SOUL_SAND
			|| group == BlockSoundGroup.SUSPICIOUS_SAND || group == BlockSoundGroup.SUSPICIOUS_GRAVEL) return Surface.GRAVEL;
		String path = Registries.BLOCK.getId(state.getBlock()).getPath();
		if (group == BlockSoundGroup.GLASS || group == BlockSoundGroup.DEEPSLATE_TILES
			|| group == BlockSoundGroup.DECORATED_POT || state.isIn(BlockTags.TERRACOTTA)
			|| state.isIn(BlockTags.ICE) || path.endsWith("_terracotta") || path.contains("ceramic")
			|| path.contains("_tile")) return Surface.GLASS;
		return Surface.CONCRETE;
	}

	private static void playGear(ServerPlayerEntity player, PlayerLoadout loadout, boolean heavy) {
		boolean armor = !loadout.getEquipment(EquipmentSlotType.BODY_ARMOR).isEmpty();
		boolean rig = !loadout.getEquipment(EquipmentSlotType.RIG).isEmpty();
		float load = MathHelper.clamp(loadout.calculateWeightKg() / 45.0f + (armor && rig ? 0.15f : 0.0f), 0.0f, 1.0f);
		float volume;
		if (heavy) {
			volume = armor || rig ? MathHelper.lerp(load, 0.45f, 0.75f) : MathHelper.lerp(load, 0.12f, 0.29f);
		} else {
			volume = armor || rig ? MathHelper.lerp(load, 0.15f, 0.25f) : MathHelper.lerp(load, 0.05f, 0.10f);
		}
		play(player, heavy ? TarkovFootstepSounds.GEAR_HEAVY : TarkovFootstepSounds.GEAR_WALK,
			volume, randomPitch(player, 0.95f, 1.05f));
	}

	private static float randomPitch(ServerPlayerEntity player, float min, float max) {
		return MathHelper.lerp(player.getRandom().nextFloat(), min, max);
	}

	private static void play(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
	}
}
