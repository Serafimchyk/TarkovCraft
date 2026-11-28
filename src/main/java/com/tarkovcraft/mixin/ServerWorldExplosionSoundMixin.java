package com.tarkovcraft.mixin;

import com.tarkovcraft.audio.TarkovSoundType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
abstract class ServerWorldExplosionSoundMixin {
	@Inject(method = "createExplosion", at = @At("RETURN"))
	private void tarkovcraft$sendDistantExplosion(Entity entity, DamageSource damage, ExplosionBehavior behavior,
			double x, double y, double z, float power, boolean fire, World.ExplosionSourceType type,
			CallbackInfoReturnable<Explosion> callback) {
		ServerWorld world = (ServerWorld) (Object) this;
		float range = TarkovSoundType.IMPULSE_RANGE;
		var packet = new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_GENERIC_EXPLODE),
			SoundCategory.BLOCKS, x, y, z, 4.0f, 0.9f, world.random.nextLong());
		// Inside 64 blocks the vanilla explosion packet already triggers playback.
		for (var player : world.getPlayers()) {
			double distanceSq = player.squaredDistanceTo(x, y, z);
			if (distanceSq >= 64.0 * 64.0 && distanceSq < range * range) player.networkHandler.sendPacket(packet);
		}
	}
}
