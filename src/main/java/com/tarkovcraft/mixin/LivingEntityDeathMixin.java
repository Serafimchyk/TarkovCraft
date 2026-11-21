package com.tarkovcraft.mixin;

import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.entity.MobGearSpawner;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spawns a lootable CorpseEntity when humanoid hostile mobs die so players
 * can loot them and strip their unsearched backpacks whole.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {
	@Inject(method = "onDeath", at = @At("TAIL"))
	private void tarkovcraft$spawnMobCorpse(DamageSource damageSource, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getWorld().isClient) {
			return;
		}
		if (self instanceof ZombieEntity
			|| self instanceof SkeletonEntity
			|| self instanceof IllagerEntity
			|| self instanceof PiglinEntity
			|| (self instanceof HostileEntity && self.getHeight() >= 1.6f)) {
			CorpseEntity corpse = null;
			if (self.getCommandTags().contains(MobGearSpawner.TAG_GEARED_MOB)) {
				corpse = CorpseEntity.fromGearedMob(self.getWorld(), self);
			} else if (self.getWorld().random.nextFloat() < TarkovConfig.get().corpseBaseLootChance) {
				corpse = CorpseEntity.fromRegularMob(self.getWorld(), self);
			}
			if (corpse != null) {
				self.getWorld().spawnEntity(corpse);
			}
		}
	}
}
