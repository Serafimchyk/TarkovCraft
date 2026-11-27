package com.tarkovcraft.lobby;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.item.AdminItemCatalog.TaCZKind;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovArmorRepairItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovFoodItem;
import com.tarkovcraft.item.TarkovGrenadeItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovStimulatorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TraderCatalogue {
	public enum Category {
		ALL, GUNS, AMMO, ATTACHMENTS, GRENADES, MEDICINE, STIMULANTS, FOOD, CASES,
		ARMOR, HELMETS, MASKS, HEADSETS, RIGS, BACKPACKS, REPAIR
	}

	public enum Trader {
		PRAPOR(Category.GUNS, Category.AMMO, Category.ATTACHMENTS, Category.GRENADES),
		THERAPIST(Category.MEDICINE, Category.STIMULANTS, Category.FOOD, Category.CASES),
		RAGMAN(Category.ARMOR, Category.HELMETS, Category.MASKS, Category.HEADSETS,
			Category.RIGS, Category.BACKPACKS, Category.REPAIR),
		FENCE;

		private final List<Category> categories;

		Trader(Category... categories) { this.categories = List.of(categories); }

		public List<Category> categories() { return categories; }
	}

	public record Offer(Trader trader, Category category, ItemStack stack) { }

	private static final Set<String> GUNS = Set.of("glock_17", "m1911", "cz75", "m9a4", "uzi", "ump45",
		"hk_mp5a5", "hk_mp7a1", "m870", "m1014", "sks_tactical", "ak47", "ak74m", "ak12", "m16a1",
		"m16a4", "g36k", "m4a1", "aug", "fn_fal", "p90", "scar_l", "scar_h", "m700", "svd",
		"spas_12", "vector45", "hk416d", "deagle", "hk_mk23", "p320");

	private TraderCatalogue() { }

	public static List<Offer> offers() {
		List<Offer> offers = new ArrayList<>();
		for (var gun : TaCZKind.Gun.commonEntries()) {
			if (GUNS.contains(gun.variant().getPath())) addPrapor(offers, Category.GUNS, gun.create());
		}
		for (var ammo : TaCZKind.Ammo.commonEntries()) {
			String id = ammo.variant().toString();
			if (!TaCZCaliberCatalog.isBallisticAmmoId(id)) continue;
			ItemStack base = ammo.create();
			if (base.isEmpty()) continue;
			int packSize = TaCZCaliberCatalog.getSpecOrDynamic(id).getBatchSize();
			for (AmmoTier tier : List.of(AmmoTier.HP, AmmoTier.FMJ, AmmoTier.AP)) {
				if (!TaCZCaliberCatalog.isTierAllowed(id, tier)) continue;
				ItemStack stack = base.copyWithCount(Math.min(base.getMaxCount(), packSize));
				AmmoTier.setOnAmmoStack(stack, tier);
				addPrapor(offers, Category.AMMO, stack);
			}
		}
		for (var attachment : TaCZKind.Attachment.commonEntries()) {
			String id = attachment.variant().getPath();
			if (!id.startsWith("ammo_mod_") && !id.startsWith("oem_")) {
				addPrapor(offers, Category.ATTACHMENTS, attachment.create());
			}
		}
		Registries.ITEM.getIds().stream().filter(id -> id.getNamespace().equals(TarkovCraftMod.MOD_ID))
			.sorted().map(id -> new ItemStack(Registries.ITEM.get(id))).forEach(stack -> addTarkov(offers, stack));
		return offers;
	}

	private static void addPrapor(List<Offer> offers, Category category, ItemStack stack) {
		if (!stack.isEmpty()) offers.add(new Offer(Trader.PRAPOR, category, stack));
	}

	private static void addTarkov(List<Offer> offers, ItemStack stack) {
		var item = stack.getItem();
		if (item instanceof TarkovGrenadeItem) addPrapor(offers, Category.GRENADES, stack);
		else if (item instanceof TarkovMedicalItem) offers.add(new Offer(Trader.THERAPIST, Category.MEDICINE, stack));
		else if (item instanceof TarkovStimulatorItem) offers.add(new Offer(Trader.THERAPIST, Category.STIMULANTS, stack));
		else if (item instanceof TarkovFoodItem) offers.add(new Offer(Trader.THERAPIST, Category.FOOD, stack));
		else if (item instanceof TarkovCaseItem) offers.add(new Offer(Trader.THERAPIST, Category.CASES, stack));
		else if (item instanceof TarkovArmorItem armor && armor.getArmorClass() <= 4) {
			Category category = switch (armor.getTargetSlot()) {
				case HELMET -> Category.HELMETS;
				case FACE_MASK -> Category.MASKS;
				default -> Category.ARMOR;
			};
			offers.add(new Offer(Trader.RAGMAN, category, stack));
		} else if (EquipmentSlotType.HEADSET.accepts(stack)) offers.add(new Offer(Trader.RAGMAN, Category.HEADSETS, stack));
		else if (item instanceof TacticalRigItem) offers.add(new Offer(Trader.RAGMAN, Category.RIGS, stack));
		else if (item instanceof BackpackItem bag && bag.getGridWidth() * bag.getGridHeight() <= 30)
			offers.add(new Offer(Trader.RAGMAN, Category.BACKPACKS, stack));
		else if (item instanceof TarkovArmorRepairItem kit && kit.getMaxArmorClass() <= 4)
			offers.add(new Offer(Trader.RAGMAN, Category.REPAIR, stack));
	}
}
