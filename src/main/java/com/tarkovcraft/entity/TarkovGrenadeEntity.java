package com.tarkovcraft.entity;

import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.health.InjuryType;
import com.tarkovcraft.item.TarkovGrenadeItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

/**
 * Thrown TarkovCraft tactical grenade with bounce physics, impact/timed fuses,
 * block-safe fragmentation with zonal BodyPartHealth damage, flashbang LOS checks,
 * smoke screen (M18), and tear gas cloud (CS Model 8230).
 */
public class TarkovGrenadeEntity extends ThrownItemEntity {
	private static final HitZone[] FRAG_ZONES = {
		HitZone.THORAX,
		HitZone.STOMACH,
		HitZone.LEFT_ARM,
		HitZone.RIGHT_ARM,
		HitZone.LEFT_LEG,
		HitZone.RIGHT_LEG,
		HitZone.HEAD_JAWS
	};

	private static final DustParticleEffect SMOKE_GREEN_DUST =
		new DustParticleEffect(new Vector3f(0.28f, 0.72f, 0.34f), 4.0f);
	private static final DustParticleEffect SMOKE_WHITE_DUST =
		new DustParticleEffect(new Vector3f(0.78f, 0.82f, 0.78f), 4.0f);
	private static final DustParticleEffect GAS_CS_DUST =
		new DustParticleEffect(new Vector3f(0.68f, 0.78f, 0.22f), 2.8f);

	private TarkovGrenadeItem.GrenadeType grenadeType = TarkovGrenadeItem.GrenadeType.FRAG_F1;
	private int fuseRemaining = 40;
	private int cloudRemaining = 0;
	private boolean detonated = false;
	private boolean emitting = false;
	private boolean resting = false;

	public TarkovGrenadeEntity(EntityType<? extends ThrownItemEntity> type, World world) {
		super(type, world);
	}

	public TarkovGrenadeEntity(
		World world,
		LivingEntity owner,
		ItemStack sourceStack,
		TarkovGrenadeItem.GrenadeType grenadeType
	) {
		super(ModEntities.GRENADE, owner, world);
		this.grenadeType = grenadeType != null ? grenadeType : TarkovGrenadeItem.GrenadeType.FRAG_F1;
		this.fuseRemaining = this.grenadeType.getFuseTicks();
		if (sourceStack != null && !sourceStack.isEmpty()) {
			this.setItem(sourceStack.copyWithCount(1));
		}
	}

	@Override
	protected Item getDefaultItem() {
		return Items.SNOWBALL;
	}

	@Override
	protected float getGravity() {
		return 0.045f;
	}

	@Override
	protected void onBlockHit(BlockHitResult hit) {
		if (emitting) {
			setVelocity(Vec3d.ZERO);
			return;
		}
		if (!getWorld().isClient && !detonated && grenadeType.isImpactFuse() && age >= grenadeType.getArmingTicks()) {
			detonate();
			return;
		}

		Vec3d vel = getVelocity();
		Direction.Axis axis = hit.getSide().getAxis();
		double vx = axis == Direction.Axis.X ? -vel.x * 0.55 : vel.x * 0.72;
		double vy = axis == Direction.Axis.Y ? -vel.y * 0.42 : vel.y * 0.72;
		double vz = axis == Direction.Axis.Z ? -vel.z * 0.55 : vel.z * 0.72;
		Vec3d nextVel = new Vec3d(vx, vy, vz);

		if (hit.getSide() == Direction.UP && nextVel.lengthSquared() < 0.015) {
			resting = true;
			setNoGravity(true);
			setVelocity(Vec3d.ZERO);
		} else {
			setVelocity(nextVel);
		}
		getWorld().playSound(null, getX(), getY(), getZ(),
			SoundEvents.BLOCK_METAL_HIT, SoundCategory.PLAYERS, 0.45f, 1.25f);
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		if (emitting) {
			return;
		}
		if (!getWorld().isClient && !detonated && grenadeType.isImpactFuse() && age >= grenadeType.getArmingTicks()) {
			detonate();
			return;
		}
		Entity target = hit.getEntity();
		if (!getWorld().isClient) {
			target.damage(getDamageSources().thrown(this, getOwner()), 1.0f);
		}
		Vec3d vel = getVelocity();
		setVelocity(vel.x * -0.35, Math.abs(vel.y) * 0.35, vel.z * -0.35);
		getWorld().playSound(null, getX(), getY(), getZ(),
			SoundEvents.BLOCK_METAL_HIT, SoundCategory.PLAYERS, 0.45f, 1.25f);
	}

	@Override
	public void tick() {
		if (resting && getWorld().getBlockState(getBlockPos().down()).isAir()) {
			resting = false;
			setNoGravity(false);
		}
		if (resting || emitting) {
			setVelocity(Vec3d.ZERO);
		}
		super.tick();

		if (getWorld().isClient) {
			if (!detonated && age % 2 == 0) {
				getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.15, getZ(), 0.0, 0.02, 0.0);
			}
			return;
		}

		if (!detonated) {
			fuseRemaining--;
			if (fuseRemaining <= 0) {
				detonate();
			}
		} else if (emitting) {
			cloudRemaining--;
			tickCloud((ServerWorld) getWorld());
			if (cloudRemaining <= 0) {
				discard();
			}
		}
	}

	private void detonate() {
		if (detonated || !(getWorld() instanceof ServerWorld sw)) {
			return;
		}
		detonated = true;

		switch (grenadeType.getCategory()) {
			case FRAG, IMPACT -> detonateFrag(sw);
			case FLASH -> detonateFlash(sw);
			case SMOKE -> startSmokeCloud(sw);
			case GAS -> startGasCloud(sw);
		}
	}

	private void detonateFrag(ServerWorld sw) {
		sw.playSound(null, getX(), getY(), getZ(),
			SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 3.0f, 0.9f);
		sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.25, getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.25, getZ(), 12, 0.8, 0.5, 0.8, 0.05);
		sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.25, getZ(), 18, 1.2, 0.6, 1.2, 0.03);
		sw.spawnParticles(ParticleTypes.LAVA, getX(), getY() + 0.2, getZ(), 14, 0.8, 0.5, 0.8, 0.12);

		float radius = grenadeType.getBlastRadius();
		Vec3d origin = getPos().add(0.0, 0.2, 0.0);
		DamageSource source = getDamageSources().explosion(this, getOwner());
		String killerName = getOwner() != null ? getOwner().getName().getString() : "Grenade";
		String weaponName = getStack().isEmpty() ? grenadeType.name() : getStack().getName().getString();

		List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(radius),
			e -> e.isAlive() && !e.isSpectator());
		for (LivingEntity target : targets) {
			double dist = origin.distanceTo(target.getPos().add(0.0, target.getHeight() * 0.5, 0.0));
			if (dist > radius) {
				continue;
			}
			float exposure = computeExposure(sw, origin, target);
			if (exposure <= 0.0f) {
				continue;
			}

			float falloff = Math.max(0.0f, 1.0f - (float) (dist / radius));
			float effectiveDmg = grenadeType.getMaxDamage()
				* (0.25f + 0.75f * falloff * falloff)
				* exposure
				* com.tarkovcraft.config.TarkovConfig.get().grenadeDamageMultiplier;

			if (target instanceof ServerPlayerEntity sp && !sp.isCreative() && !sp.isSpectator()
				&& sp instanceof PlayerLoadoutHolder holder) {
				BodyPartHealth health = holder.tarkovcraft$getBodyHealth();
				float perZone = effectiveDmg / 4.0f;
				for (int i = 0; i < 4; i++) {
					HitZone zone = (i == 0) ? HitZone.THORAX : FRAG_ZONES[random.nextInt(FRAG_ZONES.length)];
					health.applyHit(
						holder.tarkovcraft$getLoadout(),
						zone,
						perZone,
						grenadeType.getPenetrationPower(),
						1.0f, false, false, 1.0f,
						random.nextFloat(),
						random.nextFloat(),
						killerName,
						weaponName,
						"Shrapnel",
						(float) dist
					);
				}

				if (random.nextFloat() < grenadeType.getBleedChance() * falloff * exposure) {
					HitZone bleedZone = FRAG_ZONES[random.nextInt(FRAG_ZONES.length)];
					health.addInjury(bleedZone.getBodyPart(),
						falloff > 0.6f ? InjuryType.HEAVY_BLEED : InjuryType.LIGHT_BLEED);
				}

				holder.tarkovcraft$syncBodyHealth();
				holder.tarkovcraft$syncLoadout();
				if (health.isDead()) {
					sp.setHealth(0.0f);
					sp.onDeath(source);
				} else {
					sp.setHealth(Math.max(1.0f, health.getTotalRatio() * sp.getMaxHealth()));
				}
			} else if (!(target instanceof PlayerEntity)) {
				target.damage(source, Math.max(1.0f, effectiveDmg / 9.0f));
			}

			Vec3d push = target.getPos().subtract(origin);
			if (push.lengthSquared() > 1.0E-4) {
				Vec3d knock = push.normalize().multiply(0.55 * falloff * exposure);
				target.addVelocity(knock.x, Math.max(0.15 * falloff, knock.y), knock.z);
				target.velocityModified = true;
			}
		}

		discard();
	}

	private void detonateFlash(ServerWorld sw) {
		sw.playSound(null, getX(), getY(), getZ(),
			SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, SoundCategory.PLAYERS, 3.0f, 1.4f);
		sw.playSound(null, getX(), getY(), getZ(),
			SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 1.8f);
		sw.spawnParticles(ParticleTypes.FLASH, getX(), getY() + 0.35, getZ(), 4, 0.2, 0.2, 0.2, 0.0);
		sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.35, getZ(), 3, 0.3, 0.2, 0.3, 0.02);

		float radius = grenadeType.getBlastRadius();
		Vec3d origin = getPos().add(0.0, 0.25, 0.0);
		List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(radius),
			e -> e.isAlive() && !e.isSpectator());
		for (LivingEntity target : targets) {
			double dist = origin.distanceTo(target.getEyePos());
			if (dist > radius) {
				continue;
			}
			float exposure = computeExposure(sw, origin, target);
			if (exposure <= 0.0f) {
				continue;
			}
			float falloff = Math.max(0.0f, 1.0f - (float) (dist / radius));
			Vec3d toFlash = origin.subtract(target.getEyePos());
			double facingMult = 0.5;
			if (toFlash.lengthSquared() > 1.0E-4) {
				double dot = target.getRotationVec(1.0f).dotProduct(toFlash.normalize());
				facingMult = dot > 0.25 ? 1.0 : 0.5;
			}

			int blindTicks = Math.max(40, (int) (220 * falloff * facingMult));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, blindTicks, 0));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 160, 0));
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
		}

		discard();
	}

	private void startSmokeCloud(ServerWorld sw) {
		emitting = true;
		resting = true;
		setNoGravity(true);
		setVelocity(Vec3d.ZERO);
		cloudRemaining = grenadeType.getCloudTicks();
		sw.playSound(null, getX(), getY(), getZ(),
			SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.2f, 0.8f);
	}

	private void startGasCloud(ServerWorld sw) {
		emitting = true;
		resting = true;
		setNoGravity(true);
		setVelocity(Vec3d.ZERO);
		cloudRemaining = grenadeType.getCloudTicks();
		sw.playSound(null, getX(), getY(), getZ(),
			SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.2f, 1.0f);
	}

	private void tickCloud(ServerWorld sw) {
		float radius = grenadeType.getBlastRadius();
		double spread = radius * 0.65;

		if (grenadeType.getCategory() == TarkovGrenadeItem.Category.SMOKE) {
			double smokeSpread = Math.max(2.6, radius * 0.72);
			sw.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX(), getY() + 0.45, getZ(), 10, smokeSpread, 0.8, smokeSpread, 0.012);
			sw.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX(), getY() + 1.85, getZ(), 8, smokeSpread * 0.85, 0.7, smokeSpread * 0.85, 0.01);
			sw.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 1.2, getZ(), 14, smokeSpread * 0.9, 1.15, smokeSpread * 0.9, 0.008);
			if (cloudRemaining % 2 == 0) {
				sw.spawnParticles(SMOKE_GREEN_DUST, getX(), getY() + 1.0, getZ(), 20, smokeSpread, 1.2, smokeSpread, 0.01);
				sw.spawnParticles(SMOKE_WHITE_DUST, getX(), getY() + 1.4, getZ(), 18, smokeSpread, 1.2, smokeSpread, 0.01);
				sw.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.8, getZ(), 12, smokeSpread, 1.0, smokeSpread, 0.015);
			}
			if (cloudRemaining % 8 == 0) {
				Box box = getBoundingBox().expand(radius, 3.8, radius);
				List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, box,
					e -> e.isAlive() && !e.isSpectator());
				Vec3d origin = getPos();
				for (LivingEntity target : targets) {
					double dx = target.getX() - origin.x;
					double dz = target.getZ() - origin.z;
					double dy = target.getY() - origin.y;
					if (dx * dx + dz * dz <= radius * radius && dy >= -1.0 && dy <= 4.0) {
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 45, 0, false, false, true));
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 55, 0, false, false, true));
					}
				}
			}
			if (cloudRemaining % 30 == 0) {
				sw.playSound(null, getX(), getY(), getZ(),
					SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.4f, 0.9f);
			}
			return;
		}

		if (grenadeType.getCategory() == TarkovGrenadeItem.Category.GAS) {
			if (cloudRemaining % 2 == 0) {
				sw.spawnParticles(GAS_CS_DUST, getX(), getY() + 0.8, getZ(), 12, spread, 0.9, spread, 0.01);
				sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.5, getZ(), 5, spread, 0.8, spread, 0.01);
				sw.spawnParticles(ParticleTypes.SNEEZE, getX(), getY() + 0.8, getZ(), 4, spread * 0.8, 0.7, spread * 0.8, 0.02);
			}
			if (cloudRemaining % 10 == 0) {
				Box box = getBoundingBox().expand(radius);
				List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, box,
					e -> e.isAlive() && !e.isSpectator());
				Vec3d origin = getPos();
				for (LivingEntity target : targets) {
					if (target.getPos().distanceTo(origin) > radius) {
						continue;
					}
					if (!hasGasMaskProtection(target)) {
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0));
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0));
						target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0));
					}
				}
			}
		}
	}

	private float computeExposure(ServerWorld sw, Vec3d origin, LivingEntity target) {
		Vec3d lowPoint = target.getPos().add(0.0, 0.5, 0.0);
		Vec3d eyePoint = target.getEyePos();
		boolean lowClear = sw.raycast(new RaycastContext(
			origin, lowPoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this
		)).getType() == HitResult.Type.MISS;
		boolean eyeClear = sw.raycast(new RaycastContext(
			origin, eyePoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this
		)).getType() == HitResult.Type.MISS;

		if (lowClear && eyeClear) return 1.0f;
		if (lowClear || eyeClear) return 0.5f;
		return 0.0f;
	}

	public static boolean hasGasMaskProtection(LivingEntity entity) {
		if (!(entity instanceof PlayerLoadoutHolder holder)) {
			return false;
		}
		return hasGasMaskProtection(holder.tarkovcraft$getLoadout());
	}

	public static boolean hasGasMaskProtection(PlayerLoadout loadout) {
		if (loadout == null) {
			return false;
		}
		ItemStack mask = loadout.getEquipment(EquipmentSlotType.FACE_MASK);
		return !mask.isEmpty()
			&& mask.getItem() instanceof com.tarkovcraft.item.TarkovArmorItem armor
			&& armor.isGasProtection();
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putString("GrenadeType", grenadeType.name());
		nbt.putInt("FuseRemaining", fuseRemaining);
		nbt.putInt("CloudRemaining", cloudRemaining);
		nbt.putBoolean("Detonated", detonated);
		nbt.putBoolean("Emitting", emitting);
		nbt.putBoolean("Resting", resting);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("GrenadeType")) {
			grenadeType = TarkovGrenadeItem.GrenadeType.fromString(nbt.getString("GrenadeType"));
		}
		if (nbt.contains("FuseRemaining")) {
			fuseRemaining = nbt.getInt("FuseRemaining");
		}
		cloudRemaining = nbt.getInt("CloudRemaining");
		detonated = nbt.getBoolean("Detonated");
		emitting = nbt.getBoolean("Emitting");
		resting = nbt.getBoolean("Resting");
		if (resting || emitting) {
			setNoGravity(true);
		}
	}
}
