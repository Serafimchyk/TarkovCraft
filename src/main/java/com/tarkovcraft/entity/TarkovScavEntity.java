package com.tarkovcraft.entity;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZBotGun;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.arena.RaidSession;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loot.TarkovLootGenerator;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.function.Predicate;

public final class TarkovScavEntity extends HostileEntity {
	private static final TrackedData<Boolean> BOSS = DataTracker.registerData(TarkovScavEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<ItemStack> RIG = DataTracker.registerData(TarkovScavEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
	private static final List<String> SCAV_GUNS = List.of("glock_17", "m870", "hk_mp5a5", "ak74m");
	private static final List<String> BOSS_GUNS = List.of("ak12", "m249");
	private static final List<String> SCAV_HATS = List.of("ssh68_helmet", "vyazanaya_shapka_572b7f",
		"shapka_ushanka_59e770", "kepka_armeyskaya_cadpat_6040de");

	private final PlayerLoadout loadout = new PlayerLoadout();
	private TarkovAiFirearmController firearm;
	private Predicate<Vec3d> insideArena;
	private Vec3d home;
	private net.minecraft.util.math.ChunkPos ticket;
	private boolean lootDropped;
	private List<ItemStack> trophies = List.of();
	private static final net.minecraft.server.world.ChunkTicketType<java.util.UUID> BOT_TICKET =
		net.minecraft.server.world.ChunkTicketType.create("tarkovcraft_raid_bot", java.util.UUID::compareTo);

	public TarkovScavEntity(EntityType<? extends TarkovScavEntity> type, World world) {
		super(type, world);
	}

	public static void registerSounds() {
		ScavVoice.initialize();
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26);
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		dataTracker.startTracking(BOSS, false);
		dataTracker.startTracking(RIG, ItemStack.EMPTY);
	}

	public static TaCZBotGun rollGun(net.minecraft.util.math.random.Random random, boolean boss) {
		var guns = boss ? BOSS_GUNS : SCAV_GUNS;
		int offset = random.nextInt(guns.size());
		for (int i = 0; i < guns.size(); i++) {
			var gun = TaCZBotGun.find("tacz:" + guns.get((offset + i) % guns.size()));
			if (gun != null && gun.capacity() > 0) return gun;
		}
		return null;
	}

	public boolean prepare(boolean boss, Predicate<Vec3d> arenaBounds) {
		var gun = rollGun(random, boss);
		if (gun == null) return false;
		AmmoTier requestedTier = boss ? AmmoTier.AP : random.nextBoolean() ? AmmoTier.HP : AmmoTier.FMJ;
		AmmoTier tier = TaCZCaliberCatalog.getSpecOrDynamic(gun.ammo().toString()).clampTier(requestedTier);
		ItemStack weapon = TarkovLootGenerator.createLoadedGunStack(gun, tier);
		if (weapon.isEmpty()) return false;
		loadout.setEquipment(gun.id().getPath().equals("glock_17") ? EquipmentSlotType.PISTOL : EquipmentSlotType.PRIMARY_1, weapon);
		equipStack(EquipmentSlot.MAINHAND, weapon);
		setEquipmentDropChance(EquipmentSlot.MAINHAND, 0);

		dataTracker.set(BOSS, boss);
		insideArena = arenaBounds;
		home = getPos();
		setPersistent();
		setCanPickUpLoot(false);
		setCustomName(Text.translatable(boss ? "entity.tarkovcraft.arena_boss" : "entity.tarkovcraft.scav"));
		setCustomNameVisible(boss);
		if (boss) {
			var loot = TarkovLootGenerator.createBossLoot(random);
			equipGear(EquipmentSlotType.HELMET, EquipmentSlot.HEAD, loot.helmet());
			equipGear(EquipmentSlotType.BODY_ARMOR, EquipmentSlot.CHEST, loot.bodyArmor());
			getAttributeInstance(EntityAttributes.GENERIC_ARMOR_TOUGHNESS).setBaseValue(6);
			getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(80);
			trophies = loot.trophies();
		} else {
			var hat = Registries.ITEM.get(new Identifier("tarkovcraft", SCAV_HATS.get(random.nextInt(SCAV_HATS.size()))));
			equipGear(EquipmentSlotType.HELMET, EquipmentSlot.HEAD, new ItemStack(hat));
			if (random.nextInt(4) != 0) {
				ItemStack armor = new ItemStack(random.nextBoolean() ? ModItems.PACA_ARMOR : ModItems.PRESS_ARMOR);
				equipGear(EquipmentSlotType.BODY_ARMOR, EquipmentSlot.CHEST, armor);
			}
			ItemStack rig = new ItemStack(ModItems.SCAV_VEST);
			loadout.setEquipment(EquipmentSlotType.RIG, rig);
			dataTracker.set(RIG, rig.copy());
			loadout.setPocket(0, new ItemStack(ModItems.BANDAGE));
		}
		int armor = armorClass(getEquippedStack(EquipmentSlot.HEAD)) + armorClass(getEquippedStack(EquipmentSlot.CHEST));
		getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(armor * (boss ? 1.5 : 1.0));
		setHealth(getMaxHealth());
		for (int i = 1; i <= (boss ? 4 : 3); i++) {
			var rounds = AmmoTier.createTaczAmmoStack(gun.ammo().toString(), tier, gun.capacity());
			rounds.setCount(Math.min(rounds.getCount(), rounds.getMaxCount()));
			loadout.setPocket(i, rounds);
		}
		firearm = new TarkovAiFirearmController(this, gun);
		return true;
	}

	private void equipGear(EquipmentSlotType type, EquipmentSlot slot, ItemStack stack) {
		loadout.setEquipment(type, stack);
		equipStack(slot, stack);
		setEquipmentDropChance(slot, 0);
	}

	private static int armorClass(ItemStack stack) {
		return stack.getItem() instanceof TarkovArmorItem armor ? armor.getArmorClass() : 0;
	}

	public boolean isBoss() {
		return dataTracker.get(BOSS);
	}

	public ItemStack rig() {
		return dataTracker.get(RIG);
	}

	PlayerLoadout loadout() {
		return loadout;
	}

	TarkovAiFirearmController firearm() {
		return firearm;
	}

	public Vec3d home() {
		return home == null ? getPos() : home;
	}

	public boolean isInsideArena(Vec3d pos) {
		return insideArena != null && insideArena.test(pos);
	}

	boolean canTargetInArena(LivingEntity entity) {
		if (entity == null || !entity.isAlive() || entity.isSpectator() || entity.getWorld() != getWorld()
				|| !isInsideArena(entity.getPos())) return false;
		if (entity instanceof SleeperPlayerEntity) return true;
		if (!(entity instanceof ServerPlayerEntity player) || player.isCreative()) return false;
		return !RaidSession.isActive(player.getServer()) || RaidSession.isParticipant(player);
	}

	@Override
	protected void mobTick() {
		super.mobTick();
		if (firearm != null && isAlive() && getWorld() instanceof ServerWorld world) {
			keepActive(world);
			firearm.tick(world);
		}
	}

	public void keepActive(ServerWorld world) {
		var next = getChunkPos();
		if (next.equals(ticket)) return;
		world.getChunkManager().addTicket(BOT_TICKET, next, 2, getUuid());
		releaseTicket(world);
		ticket = next;
	}

	private void releaseTicket(ServerWorld world) {
		if (ticket == null) return;
		world.getChunkManager().removeTicket(BOT_TICKET, ticket, 2, getUuid());
		ticket = null;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (getWorld() instanceof ServerWorld world) releaseTicket(world);
		super.remove(reason);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean applied = super.damage(source, amount);
		if (applied && isAlive() && firearm != null && source.getAttacker() instanceof LivingEntity attacker) {
			firearm.hurtBy(attacker);
		}
		return applied;
	}

	@Override
	public void onDeath(DamageSource source) {
		if (!lootDropped && getWorld() instanceof ServerWorld world && firearm != null) {
			lootDropped = true;
			var overflow = new java.util.ArrayList<ItemStack>();
			var corpse = CorpseEntity.fromBot(world, this, loadout, overflow);
			if (world.spawnEntity(corpse)) {
				overflow.forEach(this::dropStack);
			} else {
				for (var slot : EquipmentSlotType.values()) dropStack(loadout.getEquipment(slot).copy());
				for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) dropStack(loadout.getPocket(i).copy());
			}
			trophies.forEach(this::dropStack);
		}
		super.onDeath(source);
	}
}
