package com.tarkovcraft.item;

import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
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
public class TarkovArmorItem extends Item implements TarkovSizedItem {
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
	private final Set<HitZone> coveredZones;
	private final ItemRarityTier rarityTier;

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
			coveredZones, ItemRarityTier.UNCOMMON);
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
		super(armorClass > 0
			? settings.maxCount(1).maxDamage(Math.max(1, maxDurability))
			: settings.maxCount(1));
		this.targetSlot = targetSlot;
		this.storedItemSize = storedItemSize;
		this.armorClass = armorClass;
		this.material = material;
		this.ricochetChance = Math.max(0.0f, Math.min(0.65f, ricochetChance));
		this.weightKg = Math.max(0.0f, weightKg);
		Set<HitZone> zones = (coveredZones == null || coveredZones.isEmpty())
			? EnumSet.noneOf(HitZone.class)
			: EnumSet.copyOf(coveredZones);
		this.coveredZones = Collections.unmodifiableSet(zones);
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.UNCOMMON;
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
		return Math.max(0, stack.getMaxDamage() - stack.getDamage());
	}

	public static boolean isBroken(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof TarkovArmorItem armor)) {
			return false;
		}
		return armor.getArmorClass() > 0 && getRemainingDurability(stack) <= 0;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		if (armorClass > 0) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_class", armorClass).formatted(Formatting.AQUA));
			tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_material",
				Text.translatable(material.getTranslationKey())).formatted(Formatting.GRAY));

			int maxDur = Math.max(1, stack.getMaxDamage());
			int curDur = getRemainingDurability(stack);
			if (curDur <= 0) {
				tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_broken", maxDur).formatted(Formatting.RED));
			} else {
				float ratio = curDur / (float) maxDur;
				Formatting durColor = ratio < 0.25f ? Formatting.RED
					: (ratio < 0.50f ? Formatting.YELLOW : Formatting.GRAY);
				tooltip.add(Text.translatable("tooltip.tarkovcraft.armor_durability", curDur, maxDur).formatted(durColor));
			}

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
	}
}
