package com.tarkovcraft.entity;

import com.mojang.authlib.GameProfile;
import com.tarkovcraft.arena.RaidSession;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.health.HitZoneResolver;
import com.tarkovcraft.health.TaCZBulletHelper;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.network.EquipmentAppearance;
import com.tarkovcraft.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/**
 * A server-owned combat target that holds the disconnected participant's raid state.
 * The raid owns its lifetime and is responsible for retiring it after a successful reconnect.
 */
public final class SleeperPlayerEntity extends LivingEntity {
	private static final float DAMAGE_SCALE = 4.5f;
	private static final TrackedData<NbtCompound> PROFILE =
		DataTracker.registerData(SleeperPlayerEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	private static final TrackedData<NbtCompound> GEAR =
		DataTracker.registerData(SleeperPlayerEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	private static final TrackedData<Boolean> LEFT_HANDED =
		DataTracker.registerData(SleeperPlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final List<EquipmentSlot> ARMOR_SLOTS =
		List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);

	private final PlayerLoadout loadout = new PlayerLoadout();
	private final BodyPartHealth bodyHealth = new BodyPartHealth();
	private final EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
	private final NbtCompound hunger = new NbtCompound();
	private UUID ownerUuid;
	private NbtList vanillaInventory = new NbtList();
	private int selectedSlot;
	private NbtCompound cachedProfileTag;
	private GameProfile cachedProfile;
	private NbtCompound cachedGearTag;
	private List<Item> cachedGear;

	public SleeperPlayerEntity(EntityType<? extends SleeperPlayerEntity> type, World world) {
		super(type, world);
	}

	public static SleeperPlayerEntity fromPlayer(ServerPlayerEntity player) {
		SleeperPlayerEntity sleeper = new SleeperPlayerEntity(ModEntities.SLEEPER, player.getWorld());
		PlayerLoadoutHolder holder = (PlayerLoadoutHolder) player;
		sleeper.ownerUuid = player.getUuid();
		sleeper.loadout.readNbt(holder.tarkovcraft$getLoadout().writeNbt());
		sleeper.bodyHealth.readNbt(holder.tarkovcraft$getBodyHealth().writeNbt());
		sleeper.bodyHealth.clearActiveMedUse();
		sleeper.vanillaInventory = player.getInventory().writeNbt(new NbtList());
		sleeper.selectedSlot = player.getInventory().selectedSlot;
		player.getHungerManager().writeNbt(sleeper.hunger);

		sleeper.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
		sleeper.setBodyYaw(player.getBodyYaw());
		sleeper.setHeadYaw(player.getHeadYaw());
		sleeper.setOnGround(player.isOnGround());
		sleeper.setVelocity(player.getVelocity());
		sleeper.fallDistance = player.fallDistance;
		sleeper.setAir(player.getAir());
		sleeper.setFireTicks(player.getFireTicks());
		player.getStatusEffects().forEach(effect -> sleeper.addStatusEffect(new StatusEffectInstance(effect)));
		sleeper.setAbsorptionAmount(player.getAbsorptionAmount());
		sleeper.updateVanillaHealth();
		sleeper.equipStack(EquipmentSlot.MAINHAND, player.getMainHandStack().copy());
		sleeper.equipStack(EquipmentSlot.OFFHAND, player.getOffHandStack().copy());

		GameProfile profile = new GameProfile(player.getUuid(), player.getGameProfile().getName());
		profile.getProperties().putAll("textures", player.getGameProfile().getProperties().get("textures"));
		sleeper.dataTracker.set(PROFILE, NbtHelper.writeGameProfile(new NbtCompound(), profile));
		sleeper.dataTracker.set(LEFT_HANDED, player.getMainArm() == Arm.LEFT);
		NbtCompound gear = new NbtCompound();
		int index = 0;
		for (Item item : EquipmentAppearance.capture(sleeper.loadout).items()) {
			gear.putInt(Integer.toString(index++), Registries.ITEM.getRawId(item));
		}
		sleeper.dataTracker.set(GEAR, gear);
		sleeper.setCustomName(player.getName());
		return sleeper;
	}

	public void restore(ServerPlayerEntity player) {
		if (!player.getUuid().equals(ownerUuid) || !isAlive() || !(getWorld() instanceof ServerWorld world)) {
			throw new IllegalStateException("Cannot restore player from this sleeper");
		}
		PlayerLoadoutHolder holder = (PlayerLoadoutHolder) player;
		holder.tarkovcraft$getLoadout().readNbt(loadout.writeNbt());
		holder.tarkovcraft$getBodyHealth().readNbt(bodyHealth.writeNbt());
		player.getInventory().readNbt(vanillaInventory);
		player.getInventory().selectedSlot = selectedSlot;
		player.getHungerManager().readNbt(hunger);
		player.teleport(world, getX(), getY(), getZ(), getYaw(), getPitch());
		player.setBodyYaw(getBodyYaw());
		player.setHeadYaw(getHeadYaw());
		player.setVelocity(getVelocity());
		player.velocityModified = true;
		player.fallDistance = fallDistance;
		player.setAir(getAir());
		player.setFireTicks(getFireTicks());
		player.clearStatusEffects();
		getStatusEffects().forEach(effect -> player.addStatusEffect(new StatusEffectInstance(effect)));
		player.setAbsorptionAmount(getAbsorptionAmount());
		player.setHealth(Math.max(1.0f, bodyHealth.getTotalRatio() * player.getMaxHealth()));
	}

	@Override
	protected void initDataTracker() {
		super.initDataTracker();
		dataTracker.startTracking(PROFILE, new NbtCompound());
		dataTracker.startTracking(GEAR, new NbtCompound());
		dataTracker.startTracking(LEFT_HANDED, false);
	}

	public GameProfile getOwnerProfile() {
		NbtCompound tag = dataTracker.get(PROFILE);
		if (tag != cachedProfileTag) {
			GameProfile profile = NbtHelper.toGameProfile(tag);
			cachedProfile = profile == null ? new GameProfile(getUuid(), "Sleeper") : profile;
			cachedProfileTag = tag;
		}
		return cachedProfile;
	}

	public UUID getOwnerUuid() {
		return ownerUuid;
	}

	public PlayerLoadout getLoadout() {
		return loadout;
	}

	public BodyPartHealth getBodyHealth() {
		return bodyHealth;
	}

	public List<Item> getVisibleGear() {
		NbtCompound tag = dataTracker.get(GEAR);
		if (tag != cachedGearTag) {
			List<Item> items = new ArrayList<>(tag.getSize());
			for (int i = 0; i < tag.getSize(); i++) {
				items.add(Registries.ITEM.get(tag.getInt(Integer.toString(i))));
			}
			cachedGear = List.copyOf(items);
			cachedGearTag = tag;
		}
		return cachedGear;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld) || !isAlive()) return;
		BodyPartHealth.TickResult result = bodyHealth.tickEffects(false);
		if (result.stateChanged()) updateVanillaHealth();
		if (bodyHealth.isDead()) {
			setHealth(0.0f);
			onDeath(getDamageSources().generic());
			return;
		}
		if (age % 10 == 0) tickHostileTargets();
	}

	private void tickHostileTargets() {
		for (MobEntity hostile : getWorld().getEntitiesByClass(MobEntity.class,
			getBoundingBox().expand(48.0), mob -> mob instanceof Monster && mob.isAlive() && !mob.isAiDisabled())) {
			LivingEntity target = hostile.getTarget();
			if (target != this && target != null && target.isAlive()
				&& (!(target instanceof SleeperPlayerEntity) || hostile.squaredDistanceTo(target) <= hostile.squaredDistanceTo(this))) {
				continue;
			}
			double followRange = hostile.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
			if (hostile.squaredDistanceTo(this) > followRange * followRange
				|| !hostile.canTarget(this) || !hostile.canSee(this)) continue;

			if (target != this) hostile.setTarget(this);
			var brain = hostile.getBrain();
			if (brain.isMemoryInState(MemoryModuleType.ATTACK_TARGET, MemoryModuleState.REGISTERED)
				&& brain.getOptionalRegisteredMemory(MemoryModuleType.ATTACK_TARGET)
					.filter(candidate -> candidate != this && candidate.isAlive()).isEmpty()) {
				brain.remember(MemoryModuleType.ATTACK_TARGET, this, 20);
			}
		}
	}

	@Override
	protected void applyDamage(DamageSource source, float amount) {
		if (getWorld().isClient || amount <= 0.0f) return;
		String sourceName = source.getName();
		if ("outOfWorld".equals(sourceName) || "genericKill".equals(sourceName)) {
			bodyHealth.applyHit(loadout, HitZone.THORAX, 9999.0f, 100, 0.0f, true, false, 1.0f, sourceName, "", "", 0.0f);
			updateVanillaHealth();
			return;
		}

		StatusEffectInstance resistance = getStatusEffect(StatusEffects.RESISTANCE);
		if (resistance != null && !source.isIn(DamageTypeTags.BYPASSES_RESISTANCE)) {
			amount *= Math.max(0, 25 - (resistance.getAmplifier() + 1) * 5) / 25.0f;
			if (amount <= 0.001f) return;
		}
		if (source.isIn(DamageTypeTags.IS_FALL)) {
			bodyHealth.applyFallDamage(amount, loadout.calculateWeightKg(), random.nextFloat());
			updateVanillaHealth();
			return;
		}

		TaCZBulletHelper.BulletInfo bullet = TaCZBulletHelper.inspectDamageSource(source);
		boolean bypassArmor = source.isIn(DamageTypeTags.IS_DROWNING)
			|| source.isIn(DamageTypeTags.IS_FIRE)
			|| source.isIn(DamageTypeTags.IS_FREEZING)
			|| !bullet.isTaczBullet() && source.isIn(DamageTypeTags.BYPASSES_ARMOR);
		boolean canRicochet = !bypassArmor && (bullet.isTaczBullet() || source.isIn(DamageTypeTags.IS_PROJECTILE));
		float damage = amount * DAMAGE_SCALE * bullet.damageMultiplier();
		if (getAbsorptionAmount() > 0 && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			float absorbed = Math.min(damage, getAbsorptionAmount() * DAMAGE_SCALE);
			setAbsorptionAmount(Math.max(0, getAbsorptionAmount() - absorbed / DAMAGE_SCALE));
			damage -= absorbed;
			if (damage <= 0.001f) return;
		}
		Entity attacker = source.getAttacker();
		String killer = attacker == null ? sourceName : attacker.getDisplayName().getString();
		bodyHealth.applyHit(loadout, HitZoneResolver.resolveFromDamage(this, source),
			damage, bullet.penetrationPower(), bullet.armorWearMultiplier(), bypassArmor, canRicochet,
			random.nextFloat(), random.nextFloat(), random.nextFloat(), killer, bullet.weaponName(),
			bullet.ammoName(), attacker == null ? 0.0f : distanceTo(attacker));
		updateVanillaHealth();
	}

	private void updateVanillaHealth() {
		setHealth(bodyHealth.isDead() ? 0.0f : Math.max(1.0f, bodyHealth.getTotalRatio() * getMaxHealth()));
	}

	void finishZonalDamage(DamageSource source) {
		updateVanillaHealth();
		if (bodyHealth.isDead()) onDeath(source);
	}

	@Override
	public void onDeath(DamageSource source) {
		if (dead) return;
		super.onDeath(source);
		if (!(getWorld() instanceof ServerWorld world) || ownerUuid == null) return;

		List<ItemStack> extras = new ArrayList<>(vanillaInventory.size());
		for (int i = 0; i < vanillaInventory.size(); i++) {
			extras.add(ItemStack.fromNbt(vanillaInventory.getCompound(i)));
		}
		List<ItemStack> overflow = new ArrayList<>();
		CorpseEntity corpse = CorpseEntity.fromSnapshot(world, getPos(), getYaw(),
			ownerUuid, getName().getString(), loadout, extras, overflow);
		if (world.spawnEntity(corpse)) {
			overflow.forEach(this::dropStack);
		}
		RaidSession.onSleeperDeath(this, source);
	}

	@Override
	protected boolean shouldDropLoot() {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_PLAYER_DEATH;
	}

	@Override
	protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
		return Math.min(1.62f, dimensions.height);
	}

	@Override
	public Iterable<ItemStack> getArmorItems() {
		return ARMOR_SLOTS.stream().map(this::getEquippedStack).toList();
	}

	@Override
	public ItemStack getEquippedStack(EquipmentSlot slot) {
		return equipment.getOrDefault(slot, ItemStack.EMPTY);
	}

	@Override
	public void equipStack(EquipmentSlot slot, ItemStack stack) {
		equipment.put(slot, stack);
	}

	@Override
	public Arm getMainArm() {
		return dataTracker.get(LEFT_HANDED) ? Arm.LEFT : Arm.RIGHT;
	}
}
