package com.tarkovcraft.entity;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZBotGun;
import com.tarkovcraft.audio.TarkovSoundNetworking;
import com.tarkovcraft.loadout.PlayerLoadout;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;

public final class TarkovAiFirearmController {
	private static final int AIM_HOLD = 24;
	private static final double RETALIATION_RANGE = 120;
	private static final double RETALIATION_RANGE_SQ = RETALIATION_RANGE * RETALIATION_RANGE;
	private static final double CONE_DOT = Math.cos(Math.toRadians(55));

	private final TarkovScavEntity bot;
	private final TaCZBotGun gun;
	private LivingEntity target;
	private LivingEntity retaliateAgainst;
	private int retaliationUntil;
	private int lastSeen;
	private int shootAfter;
	private int roundsInBurst;
	private int reloadUntil;
	private int shoutAfter;
	private int investigateUntil;
	private int noiseShoutAt;
	private int patrolAfter;
	private int navigateAfter;
	private Vec3d lastKnownPosition;
	private float aimProgress;
	private float engagementSeconds;

	TarkovAiFirearmController(TarkovScavEntity bot, TaCZBotGun gun) {
		this.bot = bot;
		this.gun = gun;
	}

	public static void reportNoise(LivingEntity source, double range) {
		if (!(source.getWorld() instanceof ServerWorld world)) return;
		for (var bot : world.getEntitiesByClass(TarkovScavEntity.class, source.getBoundingBox().expand(range),
				entity -> entity.isAlive() && entity.canTargetInArena(source)
					&& entity.squaredDistanceTo(source) <= range * range)) {
			if (bot.firearm() != null) bot.firearm().hear(source.getPos());
		}
	}

	void hurtBy(LivingEntity attacker) {
		if (!bot.canTargetInArena(attacker)) return;
		double distance = bot.squaredDistanceTo(attacker);
		if (distance > RETALIATION_RANGE_SQ) return;
		retaliationUntil = bot.age + random(500, 700);
		retaliateAgainst = attacker;
		acquire(attacker, ScavVoice.WOUNDED);
		lastKnownPosition = attacker.getPos();
		investigateUntil = retaliationUntil;
		turn(lastKnownPosition.add(0, attacker.getHeight() * 0.65, 0));
	}

	private void hear(Vec3d position) {
		if (target != null && (target == retaliateAgainst || bot.age - lastSeen <= AIM_HOLD)) return;
		lastKnownPosition = position;
		investigateUntil = bot.age + 100;
		turn(position.add(0, 1.2, 0));
		if (noiseShoutAt == 0) noiseShoutAt = bot.age + 8;
	}

	void tick(ServerWorld world) {
		if (!bot.isInsideArena(bot.getPos())) {
			bot.getNavigation().startMovingTo(bot.home().x, bot.home().y, bot.home().z, 1.0);
			return;
		}
		if (bot.age >= retaliationUntil || !bot.canTargetInArena(retaliateAgainst)
				|| bot.squaredDistanceTo(retaliateAgainst) > RETALIATION_RANGE_SQ) {
			retaliateAgainst = null;
		}
		if (!bot.canTargetInArena(target)) forgetTarget();
		if (target == null && bot.age % 4 == 0) {
			var candidate = detect(world);
			if (candidate != null) acquire(candidate, ScavVoice.DETECTED);
		}

		if (noiseShoutAt != 0 && bot.age >= noiseShoutAt) {
			noiseShoutAt = 0;
			if (target == null) shout(ScavVoice.NOISE);
		}
		if (reloadUntil != 0 && bot.age >= reloadUntil) {
			reloadUntil = 0;
			reload();
		}
		if (target != null && canSee(world, target, target == retaliateAgainst ? RETALIATION_RANGE : sightRange())) {
			lastSeen = bot.age;
			lastKnownPosition = target.getPos();
			investigateUntil = bot.age + 120;
			aimProgress = Math.min(1.0f, aimProgress + (bot.isBoss() ? 0.28f : 0.16f) / 20.0f);
			engagementSeconds = Math.min(bot.isBoss() ? 60 : 42, engagementSeconds + 0.05f);
			bot.getNavigation().stop();
			turn(target.getPos().add(0, target.getHeight() * 0.68, 0));
			if (reloadUntil == 0 && bot.age >= shootAfter) attack(world);
			return;
		}

		if (target != null && bot.age - lastSeen > AIM_HOLD) {
			if (target != retaliateAgainst && bot.age - lastSeen > 40 && bot.age % 4 == 0) {
				var replacement = detect(world);
				if (replacement != null) {
					acquire(replacement, ScavVoice.DETECTED);
					return;
				}
			}
			aimProgress = Math.max(0.0f, aimProgress - 0.12f / 20.0f);
			engagementSeconds = Math.max(0.0f, engagementSeconds - 0.025f);
			if (target != retaliateAgainst && bot.age - lastSeen > 160
					&& aimProgress == 0 && engagementSeconds == 0) forgetTarget();
		}
		if (target != null && bot.age - lastSeen <= AIM_HOLD) {
			bot.getNavigation().stop();
			if (lastKnownPosition != null) turn(lastKnownPosition.add(0, target.getHeight() * 0.68, 0));
		} else {
			investigateOrPatrol();
		}
	}

	private void acquire(LivingEntity newTarget, ScavVoice voice) {
		if (target == newTarget) {
			if (voice == ScavVoice.WOUNDED) shout(voice);
			return;
		}
		forgetTarget();
		target = newTarget;
		lastSeen = bot.age;
		shootAfter = bot.age + (bot.isBoss() ? random(9, 15) : random(16, 25));
		shout(voice);
	}

	private void forgetTarget() {
		target = null;
		aimProgress = 0;
		engagementSeconds = 0;
		roundsInBurst = 0;
	}

	private void investigateOrPatrol() {
		boolean investigating = lastKnownPosition != null && bot.age < investigateUntil;
		if (investigating) {
			Vec3d point = lastKnownPosition;
			turn(point.add(0, 1.2, 0));
			// Keep checking long-range shooters from cover instead of leaving the position blindly.
			if (bot.age >= navigateAfter && (retaliateAgainst == null || target != retaliateAgainst) && bot.squaredDistanceTo(point) > 4) {
				bot.getNavigation().startMovingTo(point.x, point.y, point.z, 0.92);
				navigateAfter = bot.age + 20;
			}
		} else if (bot.getNavigation().isIdle() && bot.age >= patrolAfter) {
			patrolAfter = bot.age + random(65, 125);
			var next = net.minecraft.entity.ai.NoPenaltyTargeting.find(bot, 9, 4);
			if (next != null && bot.isInsideArena(next)) {
				bot.getNavigation().startMovingTo(next.x, next.y, next.z, 0.75);
			}
		}
	}

	private void turn(Vec3d towards) {
		Vec3d delta = towards.subtract(bot.getEyePos());
		bot.setHeadYaw((float) (Math.toDegrees(Math.atan2(-delta.x, delta.z))));
		bot.getLookControl().lookAt(towards.x, towards.y, towards.z, 70, 50);
	}

	private double sightRange() {
		return bot.isBoss() ? 42 : 34;
	}

	private LivingEntity detect(ServerWorld world) {
		return world.getEntitiesByClass(LivingEntity.class, bot.getBoundingBox().expand(sightRange()),
			entity -> bot.canTargetInArena(entity) && inCone(entity) && canSee(world, entity, sightRange())).stream()
			.min(Comparator.comparingDouble(bot::squaredDistanceTo)).orElse(null);
	}

	private boolean inCone(LivingEntity candidate) {
		Vec3d towards = candidate.getPos().subtract(bot.getPos());
		Vec3d horizontal = new Vec3d(towards.x, 0, towards.z).normalize();
		return horizontal.lengthSquared() == 0 || Vec3d.fromPolar(0, bot.getHeadYaw()).dotProduct(horizontal) >= CONE_DOT;
	}

	private boolean canSee(ServerWorld world, LivingEntity candidate, double range) {
		Vec3d eye = bot.getEyePos();
		Vec3d mid = candidate.getPos().add(0, candidate.getHeight() * 0.65, 0);
		double distance = bot.squaredDistanceTo(candidate);
		if (distance > range * range) return false;
		if (distance > 36 && world.getLightLevel(BlockPos.ofFloored(mid), world.getAmbientDarkness()) < 7) return false;
		return clearSight(world, eye, mid, distance) || clearSight(world, eye, candidate.getEyePos(), distance);
	}

	private boolean clearSight(ServerWorld world, Vec3d eye, Vec3d point, double distance) {
		return world.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, bot)).getType() == HitResult.Type.MISS
			&& (distance <= 36 || world.getEntitiesByClass(TarkovGrenadeEntity.class, new Box(eye, point).expand(6),
				smoke -> smoke.obscures(eye, point)).isEmpty());
	}

	private void attack(ServerWorld world) {
		ItemStack stack = bot.getMainHandStack();
		var nbt = stack.getOrCreateNbt();
		int magazine = nbt.getInt("GunCurrentAmmoCount");
		boolean chambered = nbt.getBoolean("HasBulletInBarrel");
		if (magazine == 0 && !chambered) {
			startReload();
			return;
		}
		if (roundsInBurst == 0) {
			roundsInBurst = bot.isBoss() ? random(3, 5) : "SEMI".equals(gun.fireMode()) ? 1 : random(1, 3);
		}

		Vec3d point = aimPoint();
		Vec3d eye = bot.getEyePos();
		Vec3d forward = point.subtract(eye).normalize();
		Vec3d muzzle = eye.add(forward.multiply(0.42)).subtract(0, 0.10, 0);
		if (world.raycast(new RaycastContext(eye, muzzle, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, bot)).getType() != HitResult.Type.MISS) muzzle = eye;
		double distance = muzzle.distanceTo(point);
		double time = distance / gun.speed();
		double drop = 0.0;
		double flightDistance = distance;
		for (int step = 0; step < 3; step++) {
			double fraction = gun.friction() * flightDistance / gun.speed();
			if (gun.friction() > 1.0e-6 && fraction < 0.98) {
				time = Math.log1p(-fraction) / Math.log1p(-gun.friction());
				drop = gun.gravity() * (time - flightDistance / gun.speed()) / gun.friction();
			} else {
				drop = 0.5 * gun.gravity() * time * Math.max(0.0, time - 1.0);
			}
			flightDistance = muzzle.distanceTo(point.add(0, drop, 0));
		}
		Vec3d direction = point.add(0, drop, 0).subtract(muzzle).normalize();
		double spread = ((bot.isBoss() ? 0.75 : 1.12) + distance * (bot.isBoss() ? 0.020 : 0.027))
			* MathHelper.lerp(aimProgress, 1.65, 0.55);
		if (!chambered || magazine > 0) nbt.putInt("GunCurrentAmmoCount", Math.max(0, magazine - 1));
		nbt.putBoolean("HasBulletInBarrel", !gun.openBolt() && magazine > 0);
		AmmoTier tier = AmmoTier.getLoadedTierFromGun(stack);
		for (int i = 0; i < gun.pellets(); i++) {
			Vec3d shot = spread(direction, spread);
			if (gun.pellets() > 1) shot = spread(shot, 1.7);
			world.spawnEntity(new TarkovAiBulletEntity(bot, gun, tier, muzzle, shot, i == 0));
		}
		world.spawnParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
		TarkovSoundNetworking.sendGunfire(bot, muzzle, gun.display(), gun.id(), "shoot_3p", 1.0f, 1.0f);
		bot.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		shootAfter = bot.age + (--roundsInBurst == 0 ? random(12, 24) : gun.shotTicks());
		if (nbt.getInt("GunCurrentAmmoCount") == 0 && !nbt.getBoolean("HasBulletInBarrel")) startReload();
	}

	private Vec3d aimPoint() {
		double headChance = Math.min(bot.isBoss() ? 0.35 : 0.25, 0.05 + 0.015 * (int) (engagementSeconds / 3));
		if (bot.getRandom().nextDouble() < headChance) {
			return target.getPos().add(0, target.getHeight() * 0.88, 0);
		}
		double roll = bot.getRandom().nextDouble();
		if (roll < 0.52) return target.getPos().add(0, target.getHeight() * 0.65, 0);
		if (roll < 0.76) return target.getPos().add(0, target.getHeight() * 0.46, 0);
		Vec3d lateral = Vec3d.fromPolar(0, target.getBodyYaw() + (bot.getRandom().nextBoolean() ? 90 : -90));
		return target.getPos().add(lateral.multiply(target.getWidth() * (roll < 0.88 ? 0.42 : 0.19)))
			.add(0, target.getHeight() * (roll < 0.88 ? 0.60 : 0.23), 0);
	}

	private Vec3d spread(Vec3d direction, double degrees) {
		Vec3d right = direction.crossProduct(Math.abs(direction.y) > 0.99 ? new Vec3d(0, 0, 1) : new Vec3d(0, 1, 0)).normalize();
		Vec3d up = right.crossProduct(direction).normalize();
		double theta = bot.getRandom().nextDouble() * Math.PI * 2.0;
		double radius = Math.tan(Math.toRadians(degrees)) * Math.sqrt(bot.getRandom().nextDouble());
		return direction.add(right.multiply(Math.cos(theta) * radius)).add(up.multiply(Math.sin(theta) * radius)).normalize();
	}

	private void startReload() {
		roundsInBurst = 0;
		if (reserve() == 0) {
			shootAfter = bot.age + 20;
			return;
		}
		reloadUntil = bot.age + random(50, 70);
		shout(ScavVoice.RELOAD);
	}

	private int reserve() {
		int count = 0;
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			ItemStack pocket = bot.loadout().getPocket(i);
			if (isMatchingAmmo(pocket)) count += pocket.getCount();
		}
		return count;
	}

	private boolean isMatchingAmmo(ItemStack pocket) {
		return !pocket.isEmpty() && pocket.getNbt() != null
			&& gun.ammo().toString().equals(pocket.getNbt().getString("AmmoId"));
	}

	private void reload() {
		int loaded = 0;
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT && loaded < gun.capacity(); i++) {
			ItemStack pocket = bot.loadout().getPocket(i);
			if (!isMatchingAmmo(pocket)) continue;
			int used = Math.min(pocket.getCount(), gun.capacity() - loaded);
			pocket.decrement(used);
			loaded += used;
		}
		var nbt = bot.getMainHandStack().getOrCreateNbt();
		boolean chamber = !gun.openBolt() && loaded > 0;
		nbt.putBoolean("HasBulletInBarrel", chamber);
		nbt.putInt("GunCurrentAmmoCount", loaded - (chamber ? 1 : 0));
	}

	private int random(int min, int max) {
		return min + bot.getRandom().nextInt(max - min + 1);
	}

	private void shout(ScavVoice voice) {
		if (bot.age < shoutAfter) return;
		shoutAfter = bot.age + random(100, 160);
		voice.play(bot);
	}
}
