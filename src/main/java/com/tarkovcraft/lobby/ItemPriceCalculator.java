package com.tarkovcraft.lobby;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovFunctionalItem;
import com.tarkovcraft.search.ItemRarityRegistry;
import net.minecraft.item.ItemStack;

public final class ItemPriceCalculator {
	private ItemPriceCalculator() { }

	public static long basePrice(ItemStack stack) {
		if (stack.isEmpty()) return 0;
		int rarity = ItemRarityRegistry.getTier(stack).ordinal();
		long price = switch (rarity) {
			case 1 -> 1400;
			case 2 -> 4200;
			case 3 -> 9500;
			default -> 400;
		};
		var item = stack.getItem();
		if (item instanceof TarkovFunctionalItem functional) price = Math.max(price, functional.getBasePrice());
		if (item instanceof TarkovArmorItem armor) price += 1800L * armor.getArmorClass() * armor.getArmorClass();
		if (item instanceof BackpackItem backpack) price += 220L * backpack.getGridWidth() * backpack.getGridHeight();
		if (item instanceof TacticalRigItem rig)
			price += 280L * rig.getCompartmentLayouts().stream().mapToInt(grid -> grid.width() * grid.height()).sum();
		if (item instanceof TarkovCaseItem storage) price += 320L * storage.getGridWidth() * storage.getGridHeight();
		if (AmmoTier.isTaczGunStack(stack)) price += 18_000;
		if (AmmoTier.isTaczAmmoStack(stack)) {
			price = switch (AmmoTier.getFromAmmoStack(stack)) {
				case HP -> 65;
				case FMJ -> 90;
				case AP -> 170;
				case IGN -> 320;
			};
		}
		return price;
	}

	public static long buyPrice(ItemStack stack, double demand) {
		return Math.max(1, Math.round(basePrice(stack) * stack.getCount() * durabilityRatio(stack) * demand));
	}

	public static long sellPrice(ItemStack stack) {
		return Math.round(basePrice(stack) * stack.getCount() * durabilityRatio(stack) * 0.52);
	}

	private static double durabilityRatio(ItemStack stack) {
		if (!stack.isDamageable()) return 1;
		if (stack.getItem() instanceof TarkovArmorItem) {
			int maximum = TarkovArmorItem.getEffectiveMaxDurability(stack);
			return (maximum / (double) stack.getMaxDamage())
				* (TarkovArmorItem.getRemainingDurability(stack) / (double) maximum);
		}
		return (stack.getMaxDamage() - stack.getDamage()) / (double) stack.getMaxDamage();
	}
}
