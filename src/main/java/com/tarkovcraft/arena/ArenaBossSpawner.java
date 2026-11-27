package com.tarkovcraft.arena;

import com.tarkovcraft.entity.MobGearSpawner;
import com.tarkovcraft.entity.SleeperPlayerEntity;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loot.TarkovLootGenerator;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;

import java.util.List;

public final class ArenaBossSpawner {
	private static final String BOSS_TAG = "tarkovcraft:arena_boss";

	private ArenaBossSpawner() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity.getCommandTags().contains(BOSS_TAG)
				&& entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()) {
				clearActiveBoss(world, entity.getUuid());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof PillagerEntity boss) || !boss.getCommandTags().contains(BOSS_TAG)
				|| !(boss.getWorld() instanceof ServerWorld world)) return;
			clearActiveBoss(world, boss.getUuid());
			TarkovLootGenerator.BossLoot loot = TarkovLootGenerator.createBossLoot(world.random);
			boss.dropStack(boss.getEquippedStack(EquipmentSlot.CHEST).copy());
			boss.dropStack(boss.getEquippedStack(EquipmentSlot.HEAD).copy());
			boss.dropStack(loot.weapon());
			for (ItemStack trophy : loot.trophies()) boss.dropStack(trophy);
		});
	}

	private static void clearActiveBoss(ServerWorld world, java.util.UUID bossId) {
		for (ServerWorld dimension : world.getServer().getWorlds()) {
			ArenaEventsState state = ArenaEventsState.get(dimension);
			for (ArenaEventsState.Arena arena : state.arenas()) {
				if (bossId.equals(arena.activeBoss)) {
					arena.activeBoss = null;
					state.markDirty();
					return;
				}
			}
		}
	}

	public static boolean spawn(ServerWorld world, ArenaEventsState.Arena arena, List<ServerPlayerEntity> players) {
		if (world.getDifficulty() == Difficulty.PEACEFUL) return false;
		PillagerEntity boss = new ArenaBossPillager(world);
		BlockPos pos = findSpawn(world, arena, boss);
		if (pos == null) return false;

		TarkovLootGenerator.BossLoot loot = TarkovLootGenerator.createBossLoot(world.random);
		boss.setCustomName(Text.translatable("entity.tarkovcraft.arena_boss"));
		boss.setCustomNameVisible(true);
		boss.addCommandTag(BOSS_TAG);
		boss.addCommandTag(MobGearSpawner.TAG_GEAR_ROLLED);
		boss.setPersistent();
		boss.setCanPickUpLoot(false);
		equip(boss, EquipmentSlot.HEAD, loot.helmet());
		equip(boss, EquipmentSlot.CHEST, loot.bodyArmor());
		ItemStack crossbow = new ItemStack(Items.CROSSBOW);
		crossbow.addEnchantment(Enchantments.QUICK_CHARGE, 2);
		equip(boss, EquipmentSlot.MAINHAND, crossbow);

		int armor = armorClass(loot.bodyArmor()) + armorClass(loot.helmet());
		setAttribute(boss, EntityAttributes.GENERIC_ARMOR, armor * 1.5);
		setAttribute(boss, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, 6);
		setAttribute(boss, EntityAttributes.GENERIC_MAX_HEALTH, 80);
		boss.setHealth(boss.getMaxHealth());
		if (!world.spawnEntity(boss)) return false;

		arena.bossSpawned = true;
		arena.activeBoss = boss.getUuid();
		ArenaEventsState.get(world).markDirty();
		Text msg = Text.translatable("message.tarkovcraft.arena_boss_spawned", pos.getX(), pos.getY(), pos.getZ());
		players.forEach(player -> player.sendMessage(msg, false));
		return true;
	}

	private static int armorClass(ItemStack stack) {
		return stack.getItem() instanceof TarkovArmorItem armor ? armor.getArmorClass() : 0;
	}

	private static void equip(PillagerEntity boss, EquipmentSlot slot, ItemStack stack) {
		boss.equipStack(slot, stack);
		boss.setEquipmentDropChance(slot, 0);
	}

	private static void setAttribute(PillagerEntity boss, EntityAttribute attribute, double value) {
		var instance = boss.getAttributeInstance(attribute);
		if (instance != null) instance.setBaseValue(value);
	}

	private static BlockPos findSpawn(ServerWorld world, ArenaEventsState.Arena arena, PillagerEntity boss) {
		BlockPos center = arena.center();
		for (BlockPos column : BlockPos.iterateOutwards(center, 8, 0, 8)) {
			for (int dy = 0; dy <= 8; dy++) {
				BlockPos down = column.down(dy);
				if (tryPosition(world, arena, boss, down)) return down;
				BlockPos up = column.up(dy);
				if (dy != 0 && tryPosition(world, arena, boss, up)) return up;
			}
		}
		return null;
	}

	private static boolean tryPosition(ServerWorld world, ArenaEventsState.Arena arena, PillagerEntity boss, BlockPos pos) {
		if (!ArenaEvents.canPlaceAt(world, arena, pos, null)) return false;
		boss.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
		return world.isSpaceEmpty(boss);
	}

	private static final class ArenaBossPillager extends PillagerEntity {
		ArenaBossPillager(ServerWorld world) {
			super(EntityType.PILLAGER, world);
		}

		@Override
		protected void initGoals() {
			super.initGoals();
			targetSelector.add(2, new ActiveTargetGoal<>(this, SleeperPlayerEntity.class, true));
		}
	}
}
