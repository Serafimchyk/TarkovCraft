package com.tarkovcraft.loadout;

import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.item.TarkovArmorRepairItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovFunctionalItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovStimulatorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class SecureContainerValidator {
	private SecureContainerValidator() {
	}

	public static boolean allows(ItemStack stack) {
		if (!(stack.getItem() instanceof TarkovCaseItem container)) {
			return allowsLooseItem(stack);
		}
		return switch (container.getCaseRole()) {
			case INJECTOR, MONEY, MEDICAL -> ContainerItemHelper.allCaseContentsMatch(stack,
				content -> allowsLooseItem(content) && container.canAcceptItem(content));
			default -> false;
		};
	}

	public static boolean allowsCaseInsertion(ItemStack caseStack, ItemStack incoming) {
		return caseStack.getItem() instanceof TarkovCaseItem container
			&& allowsLooseItem(incoming) && container.canAcceptItem(incoming) && allows(caseStack);
	}

	private static boolean allowsLooseItem(ItemStack stack) {
		if (stack.isEmpty() || ContainerItemHelper.isContainerItem(stack)) return false;
		var item = stack.getItem();
		Identifier id = Registries.ITEM.getId(item);
		return item instanceof TarkovMedicalItem || item instanceof TarkovStimulatorItem
			|| item instanceof TarkovFunctionalItem functional && functional.isValuable()
			|| item instanceof TarkovArmorRepairItem || ("tacz".equals(id.getNamespace()) && "ammo".equals(id.getPath()))
			|| stack.isFood()
			|| item == Items.EMERALD || item == Items.DIAMOND || item == Items.GOLD_INGOT
			|| item == Items.GOLD_NUGGET || item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP
			|| item == Items.AMETHYST_SHARD || item == Items.PAPER || item == Items.NETHER_STAR
			|| item == Items.HEART_OF_THE_SEA || item == Items.TOTEM_OF_UNDYING;
	}
}
