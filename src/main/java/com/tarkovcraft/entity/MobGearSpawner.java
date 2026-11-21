package com.tarkovcraft.entity;

import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.loot.TarkovLootGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

/**
 * Assigns Tarkov Scav/Raider gear tags and visible armor pieces to eligible Overworld/hostile mobs on spawn.
 */
public final class MobGearSpawner {
	public static final String TAG_GEAR_ROLLED = "tarkovcraft:gear_rolled";
	public static final String TAG_GEARED_MOB = "tarkovcraft:geared_mob";
	public static final String TAG_GEARED_RIG = "tarkovcraft:geared_rig";
	public static final String TAG_GEARED_BACKPACK = "tarkovcraft:geared_backpack";

	private MobGearSpawner() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(MobGearSpawner::onEntityLoad);
	}

	public static boolean isEligibleMob(Entity entity) {
		if (!(entity instanceof MobEntity mob) || mob.isBaby()) {
			return false;
		}
		return mob instanceof ZombieEntity
			|| mob instanceof HuskEntity
			|| mob instanceof SkeletonEntity
			|| mob instanceof StrayEntity
			|| mob instanceof PillagerEntity
			|| mob instanceof VindicatorEntity;
	}

	private static void onEntityLoad(Entity entity, ServerWorld world) {
		if (!isEligibleMob(entity)) {
			return;
		}
		MobEntity mob = (MobEntity) entity;
		if (mob.getCommandTags().contains(TAG_GEAR_ROLLED)) {
			return;
		}
		mob.addCommandTag(TAG_GEAR_ROLLED);

		TarkovConfig cfg = TarkovConfig.get();
		Random random = world.random;
		if (random.nextFloat() >= cfg.mobGearSpawnChance) {
			return;
		}

		mob.addCommandTag(TAG_GEARED_MOB);
		if (mob.getEquippedStack(EquipmentSlot.HEAD).isEmpty() && random.nextFloat() < 0.65f) {
			ItemStack helmet = TarkovLootGenerator.pickRandomLightHelmet(random);
			if (!helmet.isEmpty()) {
				mob.equipStack(EquipmentSlot.HEAD, helmet);
				mob.setEquipmentDropChance(EquipmentSlot.HEAD, 0.0f);
			}
		}
		if (mob.getEquippedStack(EquipmentSlot.CHEST).isEmpty() && random.nextFloat() < 0.75f) {
			ItemStack armor = TarkovLootGenerator.pickRandomLightArmor(random);
			if (!armor.isEmpty()) {
				mob.equipStack(EquipmentSlot.CHEST, armor);
				mob.setEquipmentDropChance(EquipmentSlot.CHEST, 0.0f);
			}
		}
		if (random.nextFloat() < 0.65f) {
			mob.addCommandTag(TAG_GEARED_RIG);
		}
		if (random.nextFloat() < cfg.mobBackpackChance) {
			mob.addCommandTag(TAG_GEARED_BACKPACK);
		}
	}
}
