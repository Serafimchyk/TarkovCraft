package com.tarkovcraft.entity;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZBotGun;
import com.tarkovcraft.health.HitZoneResolver;
import com.tarkovcraft.health.HitZoneBoxes;
import com.tarkovcraft.health.TaCZBulletHelper;
import com.tarkovcraft.registry.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

public final class TarkovAiBulletEntity extends ProjectileEntity {
	private static final DustParticleEffect TRACER = new DustParticleEffect(new Vector3f(1.0f, 0.67f, 0.21f), 0.55f);
	private static final double MAX_RANGE = 160.0;
	private static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
		new Identifier("tarkovcraft", "ai_bullet"));

	private TaCZBulletHelper.BulletInfo info;
	private double gravity;
	private double friction;
	private double remaining = MAX_RANGE;
	private float damage;
	private boolean tracer;
	private HitZoneBoxes.RayHit impact;

	public TarkovAiBulletEntity(EntityType<? extends TarkovAiBulletEntity> type, World world) {
		super(type, world);
	}

	public TarkovAiBulletEntity(TarkovScavEntity shooter, TaCZBotGun gun, AmmoTier tier, Vec3d muzzle,
			Vec3d direction, boolean tracer) {
		this(ModEntities.AI_BULLET, shooter.getWorld());
		setOwner(shooter);
		setPosition(muzzle);
		setVelocity(direction.multiply(gun.speed()));
		this.tracer = tracer;
		damage = gun.damage();
		gravity = gun.gravity();
		friction = gun.friction();
		info = TaCZBulletHelper.ballisticInfo(gun.id().toString(), gun.ammo().toString(), tier);
	}

	public TaCZBulletHelper.BulletInfo bulletInfo() {
		return info;
	}

	public HitZoneBoxes.RayHit impact() {
		return impact;
	}

	@Override
	protected void initDataTracker() {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(getWorld() instanceof ServerWorld world)) return;
		if (age > 80 || remaining <= 0 || getVelocity().lengthSquared() < 0.01
				|| !(getOwner() instanceof TarkovScavEntity shooter) || !shooter.isInsideArena(getPos())) {
			discard();
			return;
		}

		Vec3d start = getPos();
		Vec3d velocity = getVelocity();
		Vec3d direction = velocity.normalize();
		Vec3d end = start.add(direction.multiply(Math.min(remaining, velocity.length())));
		var block = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
			RaycastContext.FluidHandling.NONE, this));
		boolean hasBlock = block.getType() == HitResult.Type.BLOCK;
		if (hasBlock) end = block.getPos();
		double nearest = start.distanceTo(end);
		LivingEntity victim = null;
		for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class, new Box(start, end).expand(0.8),
				entity -> entity != shooter && entity.isAlive() && entity.canHit()
					&& (shooter.canTargetInArena(entity)
						|| entity instanceof TarkovScavEntity && shooter.isInsideArena(entity.getPos())))) {
			var hit = HitZoneResolver.traceProjectileImpact(candidate, start, direction, nearest);
			if (hit != null && (hit.distance() < nearest || (victim == null && !hasBlock))) {
				nearest = hit.distance();
				impact = hit;
				victim = candidate;
			}
		}

		Vec3d pointOfImpact = impact == null ? end : impact.hitPos();
		if (tracer) {
			for (double offset = 0.25; offset < nearest; offset += 0.9) {
				Vec3d point = start.add(direction.multiply(offset));
				world.spawnParticles(TRACER, point.x, point.y, point.z, 1, 0, 0, 0, 0);
			}
		}
		setPosition(pointOfImpact);
		remaining -= nearest;
		if (victim != null) {
			double distance = MAX_RANGE - remaining;
			float falloff = (float) Math.max(0.55, 1.0 - distance / 240.0);
			var type = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE);
			victim.damage(new DamageSource(type, this, shooter), damage * falloff);
			discard();
		} else if (hasBlock) {
			world.spawnParticles(ParticleTypes.CRIT, pointOfImpact.x, pointOfImpact.y, pointOfImpact.z, 2, 0.04, 0.04, 0.04, 0.015);
			discard();
		} else {
			setVelocity(velocity.multiply(1.0 - friction).add(0, -gravity, 0));
		}
	}

	@Override
	public boolean canHit() {
		return false;
	}
}
