package com.tarkovcraft.mixin;

import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.DamageReport;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.health.HitZoneResolver;
import com.tarkovcraft.health.TaCZBulletHelper;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.network.TarkovNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements PlayerLoadoutHolder {
	@Unique
	private static final float TARKOV_DAMAGE_SCALE = 4.5f;

	@Unique
	private final PlayerLoadout tarkovcraft$loadout = new PlayerLoadout();

	@Unique
	private final BodyPartHealth tarkovcraft$bodyHealth = new BodyPartHealth();

	@Override
	public PlayerLoadout tarkovcraft$getLoadout() {
		return tarkovcraft$loadout;
	}

	@Override
	public void tarkovcraft$syncLoadout() {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self instanceof ServerPlayerEntity serverPlayer) {
			TarkovNetworking.sendLoadoutSync(serverPlayer, tarkovcraft$loadout);
		}
	}

	@Override
	public BodyPartHealth tarkovcraft$getBodyHealth() {
		return tarkovcraft$bodyHealth;
	}

	@Override
	public void tarkovcraft$syncBodyHealth() {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self instanceof ServerPlayerEntity serverPlayer) {
			TarkovNetworking.sendHealthSync(serverPlayer, tarkovcraft$bodyHealth);
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void tarkovcraft$writeNbt(NbtCompound nbt, CallbackInfo ci) {
		nbt.put("TarkovCraftLoadout", tarkovcraft$loadout.writeNbt());
		nbt.put("TarkovCraftHealth", tarkovcraft$bodyHealth.writeNbt());
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void tarkovcraft$readNbt(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains("TarkovCraftLoadout", NbtElement.COMPOUND_TYPE)) {
			tarkovcraft$loadout.readNbt(nbt.getCompound("TarkovCraftLoadout"));
		}
		if (nbt.contains("TarkovCraftHealth", NbtElement.COMPOUND_TYPE)) {
			tarkovcraft$bodyHealth.readNbt(nbt.getCompound("TarkovCraftHealth"));
		}
	}

	/**
	 * Disables vanilla armor slots in Survival/Adventure mode (dispensers, equip calls, attribute reads).
	 */
	@Inject(method = "canEquip(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$blockVanillaCanEquip(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.isCreative() && !self.isSpectator()
			&& MobEntity.getPreferredEquipmentSlot(stack).getType() == EquipmentSlot.Type.ARMOR) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "equipStack(Lnet/minecraft/entity/EquipmentSlot;Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$blockVanillaEquipStack(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.isCreative() && !self.isSpectator()
			&& slot.getType() == EquipmentSlot.Type.ARMOR && !stack.isEmpty()) {
			ci.cancel();
		}
	}

	@Inject(method = "getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$hideVanillaArmorStack(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.isCreative() && !self.isSpectator() && slot.getType() == EquipmentSlot.Type.ARMOR) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}

	/**
	 * Routes all Survival/Adventure damage through TarkovCraft's 3D HitZoneResolver,
	 * zonal armor check, unprotected head-eyes oneshot, and limb-to-thorax overflow!
	 */
	@Inject(method = "applyDamage", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$applyZonalBodyDamage(DamageSource source, float amount, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld().isClient || self.isCreative() || self.isSpectator() || self.isInvulnerableTo(source)) {
			return;
		}
		if (amount <= 0.0f) {
			return;
		}

		String msgId = source.getName();
		// Allow /kill and void death to kill immediately while still recording a clean KIA report
		if ("outOfWorld".equals(msgId) || "genericKill".equals(msgId)) {
			tarkovcraft$bodyHealth.applyHit(tarkovcraft$loadout, HitZone.THORAX, 9999.0f, 100, 0.0f, true, false, 1.0f, msgId, "", "", 0.0f);
			tarkovcraft$syncBodyHealth();
			return;
		}

		ci.cancel();

		StatusEffectInstance res = self.getStatusEffect(StatusEffects.RESISTANCE);
		if (res != null && !source.isIn(DamageTypeTags.BYPASSES_RESISTANCE)) {
			int reduction = Math.min(25, (res.getAmplifier() + 1) * 5);
			float mult = Math.max(0.0f, (25 - reduction) / 25.0f);
			amount *= mult;
			if (amount <= 0.001f) {
				return;
			}
		}

		if (source.isIn(DamageTypeTags.IS_FALL)) {
			float weightKg = tarkovcraft$loadout.calculateWeightKg();
			boolean killed = tarkovcraft$bodyHealth.applyFallDamage(amount, weightKg, self.getRandom().nextFloat());
			if (tarkovcraft$bodyHealth.hasFracture(BodyPart.LEFT_LEG) || tarkovcraft$bodyHealth.hasFracture(BodyPart.RIGHT_LEG)) {
				self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
					SoundEvents.BLOCK_BONE_BLOCK_BREAK, SoundCategory.PLAYERS, 0.9f, 0.85f);
			}
			self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
				SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.85f, 0.95f);
			tarkovcraft$syncBodyHealth();
			if (killed) {
				self.setHealth(0.0f);
				self.onDeath(source);
			} else {
				self.setHealth(Math.max(1.0f, tarkovcraft$bodyHealth.getTotalRatio() * self.getMaxHealth()));
			}
			return;
		}

		HitZone zone = HitZoneResolver.resolveFromDamage(self, source);
		TaCZBulletHelper.BulletInfo bulletInfo = TaCZBulletHelper.inspectDamageSource(source);

		boolean envBypass = source.isIn(DamageTypeTags.IS_FALL)
			|| source.isIn(DamageTypeTags.IS_DROWNING)
			|| source.isIn(DamageTypeTags.IS_FIRE)
			|| source.isIn(DamageTypeTags.IS_FREEZING);
		// TaCZ BULLET_IGNORE_ARMOR sets BYPASSES_ARMOR; force ZonalArmorHelper to evaluate ALL TaCZ bullets!
		boolean bypassesArmor = envBypass || (!bulletInfo.isTaczBullet() && source.isIn(DamageTypeTags.BYPASSES_ARMOR));
		boolean canRicochet = !bypassesArmor && (bulletInfo.isTaczBullet() || source.isIn(DamageTypeTags.IS_PROJECTILE));

		Entity attacker = source.getAttacker();
		String killerName = attacker != null ? attacker.getDisplayName().getString() : msgId;
		float distance = attacker != null ? self.distanceTo(attacker) : 0.0f;

		float tarkovDamage = amount * TARKOV_DAMAGE_SCALE * bulletInfo.damageMultiplier();
		float absorptionVanilla = self.getAbsorptionAmount();
		if (absorptionVanilla > 0.0f && tarkovDamage > 0.0f && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			float absorptionTarkov = absorptionVanilla * TARKOV_DAMAGE_SCALE;
			float absorbedTarkov = Math.min(tarkovDamage, absorptionTarkov);
			tarkovDamage -= absorbedTarkov;
			self.setAbsorptionAmount(Math.max(0.0f, absorptionVanilla - absorbedTarkov / TARKOV_DAMAGE_SCALE));
			if (tarkovDamage <= 0.001f) {
				self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
					SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.5f, 1.35f);
				return;
			}
		}

		boolean killed = tarkovcraft$bodyHealth.applyHit(
			tarkovcraft$loadout,
			zone,
			tarkovDamage,
			bulletInfo.penetrationPower(),
			bulletInfo.armorWearMultiplier(),
			bypassesArmor,
			canRicochet,
			self.getRandom().nextFloat(),
			self.getRandom().nextFloat(),
			self.getRandom().nextFloat(),
			killerName,
			bulletInfo.weaponName(),
			bulletInfo.ammoName(),
			distance
		);

		List<DamageReport> hits = tarkovcraft$bodyHealth.getRecentHits();
		if (!hits.isEmpty()) {
			DamageReport latest = hits.get(0);
			if (latest.ricocheted()) {
				self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
					SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.35f, 1.9f);
			} else if (latest.armorBlocked()) {
				self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
					SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.65f, 1.15f);
			}
		}

		tarkovcraft$syncBodyHealth();
		tarkovcraft$syncLoadout(); // Sync armor durability changes

		if (killed) {
			self.setHealth(0.0f);
			self.onDeath(source);
		} else {
			// Keep vanilla health proportional (>= 1.0f so vanilla never prematurely kills on limb black-out)
			float syncedVanillaHp = Math.max(1.0f, tarkovcraft$bodyHealth.getTotalRatio() * self.getMaxHealth());
			self.setHealth(syncedVanillaHp);
		}
	}

	@Inject(method = "dropInventory", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$spawnCorpseInsteadOfDropping(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (self.getWorld().isClient) {
			return;
		}
		if (self.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
			ci.cancel();
			return;
		}

		List<ItemStack> extras = new ArrayList<>();
		for (ItemStack st : self.getInventory().offHand) {
			if (!st.isEmpty()) {
				extras.add(st.copy());
			}
		}
		for (ItemStack st : self.getInventory().main) {
			if (!st.isEmpty()) {
				extras.add(st.copy());
			}
		}

		if (!tarkovcraft$loadout.isEmpty() || !extras.isEmpty()) {
			List<ItemStack> overflow = new ArrayList<>();
			CorpseEntity corpse = CorpseEntity.fromPlayer(self.getWorld(), self, tarkovcraft$loadout, extras, overflow);
			self.getWorld().spawnEntity(corpse);
			for (ItemStack st : overflow) {
				if (!st.isEmpty()) {
					self.dropItem(st, true, false);
				}
			}
			tarkovcraft$loadout.clear();
			self.getInventory().clear();
			tarkovcraft$syncLoadout();
			ci.cancel();
		}
	}
}
