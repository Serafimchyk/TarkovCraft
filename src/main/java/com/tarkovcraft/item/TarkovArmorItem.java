package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.loadout.ArmorHandling;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Zonal armor piece (Helmet, Face Mask, or Body Armor) with Armor Class (0..6),
 * ballistic material, ricochet chance, weight, durability, and explicit HitZone coverage.
 * Zones NOT in coveredZones take full unmitigated damage.
 */
public class TarkovArmorItem extends ModelledItem {
	private static final List<HitZone> HELMET_VULNERABLE_ZONES = List.of(
		HitZone.HEAD_TOP, HitZone.HEAD_EARS, HitZone.HEAD_EYES, HitZone.HEAD_JAWS
	);
	private static final List<HitZone> BODY_ARMOR_VULNERABLE_ZONES = List.of(
		HitZone.THORAX, HitZone.STOMACH, HitZone.NECK, HitZone.ARMPIT_LEFT, HitZone.ARMPIT_RIGHT
	);

	private final EquipmentSlotType targetSlot;
	private final ItemSize storedItemSize;
	private final int armorClass;
	private final ArmorMaterialType material;
	private final float ricochetChance;
	private final float weightKg;
	private final ArmorHandling handling;
	private final Set<HitZone> coveredZones;
	private final ItemRarityTier rarityTier;
	private static final String MAX_DURABILITY_KEY = "TarkovMaxDurability";
	private final boolean gasProtection;
	private final boolean blocksHeadset;
	private final float hearingMultiplier;

	public TarkovArmorItem(
		Settings settings,
		EquipmentSlotType targetSlot,
		ItemSize storedItemSize,
		int armorClass,
		int maxDurability,
		ArmorMaterialType material,
		float ricochetChance,
		float weightKg,
		Set<HitZone> coveredZones
	) {
		this(settings, targetSlot, storedItemSize, armorClass, maxDurability, material, ricochetChance, weightKg,
			coveredZones, ItemRarityTier.UNCOMMON, false);
	}

	public TarkovArmorItem(
		Settings settings,
		EquipmentSlotType targetSlot,
		ItemSize storedItemSize,
		int armorClass,
		int maxDurability,
		ArmorMaterialType material,
		float ricochetChance,
		float weightKg,
		Set<HitZone> coveredZones,
		ItemRarityTier rarityTier
	) {
		this(settings, targetSlot, storedItemSize, armorClass, maxDurability, material, ricochetChance, weightKg,
			coveredZones, rarityTier, false);
	}

	public TarkovArmorItem(
		Settings settings,
		EquipmentSlotType targetSlot,
		ItemSize storedItemSize,
		int armorClass,
		int maxDurability,
		ArmorMaterialType material,
		float ricochetChance,
		float weightKg,
		Set<HitZone> coveredZones,
		ItemRarityTier rarityTier,
		boolean gasProtection
	) {
		this(settings, targetSlot, storedItemSize, armorClass, maxDurability, material, ricochetChance,
			weightKg, coveredZones, rarityTier, gasProtection, false, 1.0f);
	}

	public TarkovArmorItem(Settings settings, EquipmentSlotType targetSlot, ItemSize storedItemSize,
			int armorClass, int maxDurability, ArmorMaterialType material, float ricochetChance,
			float weightKg, Set<HitZone> coveredZones, ItemRarityTier rarityTier, boolean gasProtection,
			boolean blocksHeadset, float hearingMultiplier) {
		super(armorClass > 0
			? settings.maxCount(1).maxDamage(Math.max(1, maxDurability))
			: settings.maxCount(1));
		this.targetSlot = targetSlot;
		this.storedItemSize = storedItemSize;
		this.armorClass = armorClass;
		this.material = material;
		this.ricochetChance = Math.max(0.0f, Math.min(0.65f, ricochetChance));
		this.weightKg = Math.max(0.0f, weightKg);
		this.handling = ArmorHandling.forArmor(targetSlot, armorClass, this.weightKg);
		Set<HitZone> zones = (coveredZones == null || coveredZones.isEmpty())
			? EnumSet.noneOf(HitZone.class)
			: EnumSet.copyOf(coveredZones);
		this.coveredZones = Collections.unmodifiableSet(zones);
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.UNCOMMON;
		this.gasProtection = gasProtection;
		this.blocksHeadset = blocksHeadset;
		this.hearingMultiplier = Math.max(0.0f, Math.min(1.0f, hearingMultiplier));
	}

	public boolean blocksHeadset() {
		return blocksHeadset;
	}

	public float getHearingMultiplier() {
		return hearingMultiplier;
	}

	public boolean isGasProtection() {
		return gasProtection;
	}

	public EquipmentSlotType getTargetSlot() {
		return targetSlot;
	}

	@Override
	public ItemSize getStoredItemSize() {
		return storedItemSize;
	}

	@Override
	public ItemRarityTier getRarityTier() {
		return rarityTier;
	}

	public int getArmorClass() {
		return armorClass;
	}

	public ArmorMaterialType getMaterial() {
		return material;
	}

	public float getRicochetChance() {
		return ricochetChance;
	}

	@Override
	public float getWeightKg() {
		return weightKg;
	}

	public ArmorHandling getHandling() {
		return handling;
	}

	public Set<HitZone> getCoveredZones() {
		return coveredZones;
	}

	public boolean coversZone(HitZone zone) {
		return armorClass > 0 && zone != null && coveredZones.contains(zone);
	}

	public static int getRemainingDurability(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof TarkovArmorItem armor)) {
			return 0;
		}
		if (armor.getArmorClass() <= 0 || !stack.isDamageable()) {
			return 0;
		}
		return Math.min(getEffectiveMaxDurability(stack), Math.max(0, stack.getMaxDamage() - stack.getDamage()));
	}

	public static int getEffectiveMaxDurability(ItemStack stack) {
		int base = stack.getMaxDamage();
		if (stack.getNbt() == null || !stack.getNbt().contains(MAX_DURABILITY_KEY, net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
			return base;
		}
		return Math.min(base, Math.max(1, stack.getNbt().getInt(MAX_DURABILITY_KEY)));
	}

	public static int repair(ItemStack stack, int points, float lossRatio) {
		int maximum = getEffectiveMaxDurability(stack);
		int current = getRemainingDurability(stack);
		int gap = maximum - current;
		int restored = Math.min(points, Math.round(gap / (1.0f + lossRatio)));
		int loss = Math.min(maximum - 1, Math.round(restored * lossRatio));
		if (restored + loss > gap) {
			restored--;
			loss = Math.min(maximum - 1, Math.round(restored * lossRatio));
		}
		if (restored <= 0) return 0;
		stack.getOrCreateNbt().putInt(MAX_DURABILITY_KEY, maximum - loss);
		stack.setDamage(stack.getMaxDamage() - current - restored);
		return restored;
	}

	public static String formatDurability(ItemStack stack) {
		return getRemainingDurability(stack) + "/" + getEffectiveMaxDurability(stack) + " (" + stack.getMaxDamage() + ")";
	}

	public static boolean isBroken(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof TarkovArmorItem armor)) {
			return false;
		}
		return armor.getArmorClass() > 0 && getRemainingDurability(stack) <= 0;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		if (targetSlot == EquipmentSlotType.HELMET || targetSlot == EquipmentSlotType.FACE_MASK) {
			if (hearingMultiplier < 1.0f) {
				tooltip.add(Text.translatable("tooltip.tarkovcraft.armor.hearing",
					Math.round((1.0f - hearingMultiplier) * 100.0f)).formatted(Formatting.RED));
			}
			if (blocksHeadset) {
				tooltip.add(Text.translatable("tooltip.tarkovcraft.armor.blocks_headset").formatted(Formatting.RED));
			}
		}
		if (armorClass > 0) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_class", armorClass).formatted(Formatting.AQUA));
			tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_material",
				Text.translatable(material.getTranslationKey())).formatted(Formatting.GRAY));

			int curDur = getRemainingDurability(stack);
			float ratio = curDur / (float) Math.max(1, getEffectiveMaxDurability(stack));
			Formatting durColor = ratio < 0.25f ? Formatting.RED
				: (ratio < 0.50f ? Formatting.YELLOW : Formatting.GRAY);
			tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_durability", formatDurability(stack)).formatted(durColor));

			if (ricochetChance > 0.0f) {
				tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_ricochet", Math.round(ricochetChance * 100.0f))
					.formatted(Formatting.DARK_AQUA));
			}

			if (!coveredZones.isEmpty()) {
				String zonesStr = coveredZones.stream()
					.map(z -> Text.translatable(z.getTranslationKey()).getString())
					.collect(Collectors.joining(", "));
				tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_zones", zonesStr).formatted(Formatting.DARK_GREEN));
			}

			List<HitZone> slotZones = switch (targetSlot) {
				case HELMET -> HELMET_VULNERABLE_ZONES;
				case BODY_ARMOR -> BODY_ARMOR_VULNERABLE_ZONES;
				default -> List.of();
			};
			if (!slotZones.isEmpty()) {
				String exposedStr = slotZones.stream()
					.filter(z -> !coveredZones.contains(z))
					.map(z -> Text.translatable(z.getTranslationKey()).getString())
					.collect(Collectors.joining(", "));
				if (!exposedStr.isEmpty()) {
					tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_exposed", exposedStr).formatted(Formatting.RED));
				}
			}
		} else {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_cosmetic").formatted(Formatting.DARK_GRAY));
		}

		tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_weight", String.format(Locale.ROOT, "%.1f", weightKg))
			.formatted(Formatting.DARK_GRAY));
		if (handling.movementPenalty() > 0) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_movement_penalty", "−" + handling.movementPenalty() + "%")
				.formatted(Formatting.RED));
		}
		if (handling.ergonomicsPenalty() > 0) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_ergonomics_penalty", "−" + handling.ergonomicsPenalty() + "%")
				.formatted(Formatting.RED));
		}
	}
}
