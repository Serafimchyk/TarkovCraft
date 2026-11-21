package com.tarkovcraft.health;

import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.joml.Vector3f;

/**
 * Server-side tick orchestrator for bleeding, painkiller timer, broken-leg sprint pain,
 * limb impairment penalties, and blacked-stomach exhaustion.
 */
public final class BodyHealthTicker {
	private static final Vector3f BLOOD_COLOR = new Vector3f(0.78f, 0.05f, 0.05f);

	private BodyHealthTicker() {
	}

	public static void tickServer(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			tickPlayer(player);
		}
	}

	public static void tickPlayer(ServerPlayerEntity player) {
		if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		if (!(player instanceof PlayerLoadoutHolder holder)) {
			return;
		}

		BodyPartHealth health = holder.tarkovcraft$getBodyHealth();
		BodyPartHealth.TickResult res = health.tickEffects(player.isSprinting());

		if (res.bleedDamaged()) {
			ServerWorld sw = (ServerWorld) player.getWorld();
			DustParticleEffect blood = new DustParticleEffect(BLOOD_COLOR, res.heavyBleedActive() ? 1.15f : 0.75f);
			sw.spawnParticles(blood, player.getX(), player.getY() + 0.9, player.getZ(),
				res.heavyBleedActive() ? 8 : 3, 0.22, 0.35, 0.22, 0.01);
			if (res.heavyBleedActive()) {
				sw.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.35f, 0.75f);
			}
		}

		if (res.sprintPainTriggered()) {
			player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.55f, 0.95f);
		}

		if (res.killedByBloodLoss() || health.isDead()) {
			holder.tarkovcraft$syncBodyHealth();
			player.setHealth(0.0f);
			player.onDeath(player.getDamageSources().generic());
			return;
		}

		if (res.impairedLegs() > 0 && !res.painkillerActive()) {
			if (player.isSprinting()) {
				player.setSprinting(false);
			}
			if (player.age % 10 == 0) {
				player.addStatusEffect(new StatusEffectInstance(
					StatusEffects.SLOWNESS, 25, res.impairedLegs() >= 2 ? 2 : 1, true, false, true));
			}
		}

		if (res.impairedArms() > 0 && !res.painkillerActive() && player.age % 10 == 0) {
			int amp = res.impairedArms() >= 2 ? 1 : 0;
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 25, amp, true, false, true));
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 25, amp, true, false, true));
		}

		if (res.exhaustionToAdd() > 0.0f) {
			player.addExhaustion(res.exhaustionToAdd());
		}

		if (res.stateChanged()) {
			player.setHealth(Math.max(1.0f, health.getTotalRatio() * player.getMaxHealth()));
			holder.tarkovcraft$syncBodyHealth();
		}
	}
}
