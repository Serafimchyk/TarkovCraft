package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.InjuryType;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Functional EFT combat stimulator injectors with 22 unique presets,
 * server-authoritative physiological & StatusEffect application, and auto-generated tooltips.
 */
public class TarkovStimulatorItem extends Item implements TarkovSizedItem {
	public static final int COOLDOWN_TICKS = 30;

	public record EffectSpec(
		Supplier<StatusEffect> effectSupplier,
		int durationSeconds,
		int amplifier,
		boolean negative
	) {
		public StatusEffect getEffect() {
			return effectSupplier.get();
		}

		public int getDurationTicks() {
			return durationSeconds * 20;
		}
	}

	public enum StimPreset {
		MORPHINE(6000, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.RESISTANCE, 60, 0),
			neg(() -> StatusEffects.HUNGER, 30, 0)
		)),
		PROPITAL(4800, 10.0f, false, false, List.of(
			pos(() -> StatusEffects.REGENERATION, 180, 0)
		)),
		ETG_CHANGE(1200, 40.0f, false, false, List.of(
			pos(() -> StatusEffects.REGENERATION, 45, 2),
			pos(() -> StatusEffects.ABSORPTION, 45, 0),
			neg(() -> StatusEffects.HUNGER, 45, 1)
		)),
		SJ1(0, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.STRENGTH, 180, 0),
			pos(() -> StatusEffects.SPEED, 180, 0),
			pos(() -> StatusEffects.HASTE, 180, 0),
			neg(() -> StatusEffects.HUNGER, 60, 0)
		)),
		SJ6(0, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.SPEED, 240, 1),
			pos(() -> StatusEffects.JUMP_BOOST, 240, 0)
		)),
		SJ9(0, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.FIRE_RESISTANCE, 300, 0),
			pos(() -> StatusEffects.NIGHT_VISION, 180, 0),
			pos(() -> StatusEffects.INVISIBILITY, 45, 0),
			neg(() -> StatusEffects.WEAKNESS, 60, 0)
		)),
		SJ12(0, 15.0f, false, false, List.of(
			pos(() -> StatusEffects.SATURATION, 30, 0),
			pos(() -> StatusEffects.NIGHT_VISION, 240, 0),
			pos(() -> StatusEffects.RESISTANCE, 180, 0),
			neg(() -> StatusEffects.SLOWNESS, 20, 0)
		)),
		MULE(0, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.STRENGTH, 300, 1),
			pos(() -> StatusEffects.HASTE, 300, 0),
			neg(() -> StatusEffects.HUNGER, 120, 0)
		)),
		ZAGUSTIN(0, 0.0f, true, false, List.of(
			pos(() -> StatusEffects.RESISTANCE, 120, 0),
			neg(() -> StatusEffects.HUNGER, 40, 0)
		)),
		XTG12(0, 10.0f, false, true, List.of(
			pos(() -> StatusEffects.REGENERATION, 45, 0),
			pos(() -> StatusEffects.RESISTANCE, 60, 0)
		)),
		ADRENALINE(1300, 12.0f, false, false, List.of(
			pos(() -> StatusEffects.SPEED, 60, 1),
			pos(() -> StatusEffects.STRENGTH, 60, 0),
			pos(() -> StatusEffects.REGENERATION, 15, 1),
			neg(() -> StatusEffects.HUNGER, 30, 0)
		)),
		L1(2400, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.SPEED, 120, 0),
			pos(() -> StatusEffects.STRENGTH, 120, 0),
			pos(() -> StatusEffects.JUMP_BOOST, 120, 0),
			neg(() -> StatusEffects.HUNGER, 40, 0)
		)),
		P22(0, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.RESISTANCE, 60, 1),
			pos(() -> StatusEffects.ABSORPTION, 60, 1),
			pos(() -> StatusEffects.REGENERATION, 45, 0),
			neg(() -> StatusEffects.WEAKNESS, 30, 0)
		)),
		BTG_3(0, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.NIGHT_VISION, 240, 0),
			pos(() -> StatusEffects.STRENGTH, 240, 0),
			pos(() -> StatusEffects.HASTE, 240, 1),
			neg(() -> StatusEffects.HUNGER, 60, 0)
		)),
		BTG_2A2(0, 0.0f, false, true, List.of(
			pos(() -> StatusEffects.STRENGTH, 240, 0),
			pos(() -> StatusEffects.SPEED, 240, 0),
			pos(() -> StatusEffects.NIGHT_VISION, 180, 0)
		)),
		AHF1_M(0, 20.0f, true, false, List.of(
			pos(() -> StatusEffects.ABSORPTION, 90, 0),
			pos(() -> StatusEffects.RESISTANCE, 60, 0),
			neg(() -> StatusEffects.HUNGER, 40, 0)
		)),
		MELDONIN(0, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.RESISTANCE, 300, 0),
			pos(() -> StatusEffects.STRENGTH, 300, 0),
			pos(() -> StatusEffects.SPEED, 300, 0),
			neg(() -> StatusEffects.HUNGER, 90, 1)
		)),
		TRIMADOL(3600, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.SPEED, 180, 1),
			pos(() -> StatusEffects.HASTE, 180, 1),
			pos(() -> StatusEffects.JUMP_BOOST, 180, 0),
			neg(() -> StatusEffects.HUNGER, 60, 1)
		)),
		PERFOTORAN(0, 18.0f, true, true, List.of(
			pos(() -> StatusEffects.REGENERATION, 60, 1),
			pos(() -> StatusEffects.WATER_BREATHING, 180, 0),
			neg(() -> StatusEffects.WEAKNESS, 25, 0)
		)),
		PNB(0, 25.0f, false, false, List.of(
			pos(() -> StatusEffects.STRENGTH, 40, 1),
			pos(() -> StatusEffects.REGENERATION, 40, 1),
			pos(() -> StatusEffects.RESISTANCE, 40, 1),
			pos(() -> StatusEffects.SPEED, 40, 0),
			neg(() -> StatusEffects.HUNGER, 60, 1),
			neg(() -> StatusEffects.NAUSEA, 12, 0)
		)),
		OBDOLBOS(0, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.SPEED, 180, 1),
			pos(() -> StatusEffects.STRENGTH, 180, 1),
			pos(() -> StatusEffects.JUMP_BOOST, 180, 1),
			pos(() -> StatusEffects.NIGHT_VISION, 180, 0),
			neg(() -> StatusEffects.NAUSEA, 20, 0),
			neg(() -> StatusEffects.HUNGER, 90, 1),
			neg(() -> StatusEffects.POISON, 8, 0)
		)),
		OBDOLBOS_2(0, 0.0f, false, false, List.of(
			pos(() -> StatusEffects.SPEED, 300, 1),
			pos(() -> StatusEffects.STRENGTH, 300, 2),
			pos(() -> StatusEffects.HASTE, 300, 1),
			pos(() -> StatusEffects.ABSORPTION, 180, 1),
			neg(() -> StatusEffects.POISON, 15, 0),
			neg(() -> StatusEffects.NAUSEA, 25, 0),
			neg(() -> StatusEffects.HUNGER, 120, 2)
		));

		private final int painkillerTicks;
		private final float healAmount;
		private final boolean stopsAllBleeds;
		private final boolean clearsNegativeEffects;
		private final List<EffectSpec> effects;

		StimPreset(
			int painkillerTicks,
			float healAmount,
			boolean stopsAllBleeds,
			boolean clearsNegativeEffects,
			List<EffectSpec> effects
		) {
			this.painkillerTicks = painkillerTicks;
			this.healAmount = healAmount;
			this.stopsAllBleeds = stopsAllBleeds;
			this.clearsNegativeEffects = clearsNegativeEffects;
			this.effects = effects;
		}

		private static EffectSpec pos(Supplier<StatusEffect> effect, int seconds, int amplifier) {
			return new EffectSpec(effect, seconds, amplifier, false);
		}

		private static EffectSpec neg(Supplier<StatusEffect> effect, int seconds, int amplifier) {
			return new EffectSpec(effect, seconds, amplifier, true);
		}

		public int getPainkillerTicks() {
			return painkillerTicks;
		}

		public float getHealAmount() {
			return healAmount;
		}

		public boolean isStopsAllBleeds() {
			return stopsAllBleeds;
		}

		public boolean isClearsNegativeEffects() {
			return clearsNegativeEffects;
		}

		public List<EffectSpec> getEffects() {
			return effects;
		}

		public static StimPreset fromString(String raw) {
			if (raw == null || raw.isBlank() || "COMBAT_STIM".equalsIgnoreCase(raw.trim())) {
				return MORPHINE;
			}
			for (StimPreset preset : values()) {
				if (preset.name().equalsIgnoreCase(raw.trim())) {
					return preset;
				}
			}
			return MORPHINE;
		}
	}

	private final StimPreset preset;
	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;

	public TarkovStimulatorItem(
		Settings settings,
		StimPreset preset,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		super(settings.maxCount(4));
		this.preset = preset != null ? preset : StimPreset.MORPHINE;
		this.storedItemSize = storedItemSize != null ? storedItemSize : ItemSize.ONE_BY_ONE;
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.RARE;
		this.weightKg = Math.max(0.0f, weightKg);
	}

	public StimPreset getPreset() {
		return preset;
	}

	@Override
	public ItemSize getStoredItemSize() {
		return storedItemSize;
	}

	@Override
	public ItemRarityTier getRarityTier() {
		return rarityTier;
	}

	@Override
	public float getWeightKg() {
		return weightKg;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.pass(stack);
		}
		if (!(user instanceof ServerPlayerEntity sp)) {
			return TypedActionResult.fail(stack);
		}
		if (!applyStimulator(sp, stack)) {
			return TypedActionResult.fail(stack);
		}
		if (stack.isEmpty() && !sp.isCreative()) {
			sp.setStackInHand(hand, ItemStack.EMPTY);
		}
		return TypedActionResult.success(sp.getStackInHand(hand), false);
	}

	public static boolean applyStimulator(ServerPlayerEntity player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty() || !(stack.getItem() instanceof TarkovStimulatorItem stim)) {
			return false;
		}
		if (player.isSpectator() || !player.isAlive() || !(player instanceof PlayerLoadoutHolder holder)) {
			return false;
		}
		if (player.getItemCooldownManager().isCoolingDown(stim)) {
			return false;
		}

		StimPreset preset = stim.getPreset();
		BodyPartHealth health = holder.tarkovcraft$getBodyHealth();

		// 1. Detox runs BEFORE applying this injector's own side effects
		if (preset.isClearsNegativeEffects()) {
			player.removeStatusEffect(StatusEffects.POISON);
			player.removeStatusEffect(StatusEffects.WITHER);
			player.removeStatusEffect(StatusEffects.HUNGER);
			player.removeStatusEffect(StatusEffects.NAUSEA);
			player.removeStatusEffect(StatusEffects.WEAKNESS);
			player.removeStatusEffect(StatusEffects.SLOWNESS);
			player.removeStatusEffect(StatusEffects.BLINDNESS);
			player.removeStatusEffect(StatusEffects.DARKNESS);
		}

		// 2. Tarkov body-part physiological effects
		if (preset.getPainkillerTicks() > 0) {
			health.setPainkillerTicks(Math.max(health.getPainkillerTicks(), preset.getPainkillerTicks()));
		}
		if (preset.isStopsAllBleeds()) {
			for (BodyPart part : BodyPart.values()) {
				health.removeInjury(part, InjuryType.LIGHT_BLEED);
				health.removeInjury(part, InjuryType.HEAVY_BLEED);
			}
		}
		if (preset.getHealAmount() > 0.0f) {
			health.healEvenly(preset.getHealAmount());
			player.setHealth(Math.max(1.0f, health.getTotalRatio() * player.getMaxHealth()));
		}

		// 3. Preset StatusEffects
		for (EffectSpec spec : preset.getEffects()) {
			player.addStatusEffect(new StatusEffectInstance(spec.getEffect(), spec.getDurationTicks(), spec.amplifier()));
		}

		player.getItemCooldownManager().set(stim, COOLDOWN_TICKS);
		if (!player.isCreative()) {
			stack.decrement(1);
		}

		holder.tarkovcraft$syncBodyHealth();
		holder.tarkovcraft$syncLoadout();
		player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.7f, 1.35f);
		player.sendMessage(Text.translatable("message.tarkovcraft.stim_used", stim.getName()), true);
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.tarkovcraft.stim_use").formatted(Formatting.AQUA));
		if (preset.getPainkillerTicks() > 0) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.med_painkiller", preset.getPainkillerTicks() / 20)
				.formatted(Formatting.GREEN));
		}
		if (preset.getHealAmount() > 0.0f) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.stim_heal", Math.round(preset.getHealAmount()))
				.formatted(Formatting.GREEN));
		}
		if (preset.isStopsAllBleeds()) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.stim_stop_bleeds").formatted(Formatting.GREEN));
		}
		if (preset.isClearsNegativeEffects()) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.stim_detox").formatted(Formatting.GREEN));
		}
		for (EffectSpec spec : preset.getEffects()) {
			MutableText effText = spec.getEffect().getName().copy();
			if (spec.amplifier() > 0) {
				effText.append(" " + toRoman(spec.amplifier() + 1));
			}
			String key = spec.negative() ? "tooltip.tarkovcraft.stim_effect_neg" : "tooltip.tarkovcraft.stim_effect_pos";
			Formatting color = spec.negative() ? Formatting.RED : Formatting.BLUE;
			tooltip.add(Text.translatable(key, effText, spec.durationSeconds()).formatted(color));
		}
		tooltip.add(Text.translatable("tooltip.tarkovcraft.item_size",
			storedItemSize.width(), storedItemSize.height()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_weight",
			String.format(Locale.ROOT, "%.2f", weightKg)).formatted(Formatting.DARK_GRAY));
	}

	private static String toRoman(int level) {
		return switch (level) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(level);
		};
	}
}
