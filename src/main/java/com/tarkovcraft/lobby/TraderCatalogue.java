package com.tarkovcraft.lobby;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.item.AdminItemCatalog;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovArmorRepairItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovFoodItem;
import com.tarkovcraft.item.TarkovGrenadeItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovStimulatorItem;
import com.tarkovcraft.search.ItemRarityRegistry;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class TraderCatalogue {
	public enum Trader { PRAPOR, THERAPIST, RAGMAN, FENCE }

	public record Offer(Trader trader, String key, ItemStack stack, double demandIncrease) { }

	private static final List<String> GUNS = List.of("glock_17", "m1911", "cz75", "m9a4", "uzi", "ump45",
		"hk_mp5a5", "m870", "m1014", "sks_tactical", "ak47", "ak74m", "m16a1", "m16a4", "g36k", "m4a1");
	private static final List<String> ATTACHMENTS = List.of("sight_552", "sight_coyote", "grip_rk0",
		"grip_magpul_afg", "laser_compact", "extended_mag_1", "muzzle_brake_trex");

	private TraderCatalogue() { }

	public static List<Offer> offers() {
		List<Offer> offers = new ArrayList<>();
		addTacz(offers, "modern_kinetic_gun", GUNS, 0.03);
		for (var caliber : TaCZCaliberCatalog.getAllKnownCalibers()) {
			Identifier ammoId = Identifier.tryParse(caliber.ammoId());
			if (ammoId == null || new AdminItemCatalog.Entry(new Identifier("tacz", "ammo"), ammoId).create().isEmpty()) continue;
			for (AmmoTier tier : List.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP)) {
				if (TaCZCaliberCatalog.isTierAllowed(caliber.ammoId(), tier)) {
					ItemStack stack = AmmoTier.createTaczAmmoStack(caliber.ammoId(), tier, 30);
					if (!stack.isEmpty()) offers.add(new Offer(Trader.PRAPOR, caliber.ammoId() + "/" + tier.getId(), stack, 0.01));
				}
			}
		}
		addTacz(offers, "attachment", ATTACHMENTS, 0.03);
		Registries.ITEM.getIds().stream().filter(id -> id.getNamespace().equals(TarkovCraftMod.MOD_ID))
			.sorted().forEach(id -> addTarkov(offers, id));
		return offers;
	}

	private static void addTacz(List<Offer> offers, String item, List<String> variants, double increase) {
		Identifier itemId = new Identifier("tacz", item);
		for (String variant : variants) {
			Identifier variantId = new Identifier("tacz", variant);
			ItemStack stack = new AdminItemCatalog.Entry(itemId, variantId).create();
			if (!stack.isEmpty()) offers.add(new Offer(Trader.PRAPOR, item + "/" + variantId, stack, increase));
		}
	}

	private static void addTarkov(List<Offer> offers, Identifier id) {
		var item = Registries.ITEM.get(id);
		ItemStack stack = new ItemStack(item);
		if (item instanceof TarkovGrenadeItem) {
			offers.add(new Offer(Trader.PRAPOR, id.toString(), stack, 0.03));
		} else if (item instanceof TarkovMedicalItem || item instanceof TarkovFoodItem || item instanceof TarkovCaseItem
				|| item instanceof TarkovStimulatorItem && ItemRarityRegistry.getTier(stack).ordinal() <= ItemRarityTier.UNCOMMON.ordinal()) {
			offers.add(new Offer(Trader.THERAPIST, id.toString(), stack, 0.01));
		} else if (item instanceof TarkovArmorItem armor && armor.getArmorClass() <= 4
				|| EquipmentSlotType.HEADSET.accepts(stack) || item instanceof TacticalRigItem
				|| item instanceof BackpackItem bag && bag.getGridWidth() * bag.getGridHeight() <= 30
				|| item instanceof TarkovArmorRepairItem kit && kit.getMaxArmorClass() <= 4) {
			offers.add(new Offer(Trader.RAGMAN, id.toString(), stack, 0.03));
		}
	}
}
