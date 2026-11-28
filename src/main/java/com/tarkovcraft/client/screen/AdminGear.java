package com.tarkovcraft.client.screen;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.item.AdminItemCatalog;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.MedicalType;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovFoodItem;
import com.tarkovcraft.item.TarkovHeadsetItem;
import com.tarkovcraft.item.TarkovGrenadeItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.item.TarkovMeleeItem;
import com.tarkovcraft.item.TarkovStimulatorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

final class AdminGear {
	enum Category {
		ALL("Все"), HELMET("Шлемы"), ARMOR("Бронежилеты"), HEAD("Маски и уши"), RIG("Разгрузки"),
		BACKPACK("Рюкзаки"), MEDICAL("Медицина"), STIM("Стимуляторы"), CASE("Гранаты и кейсы"),
		FOOD("Еда"), WEAPON("Оружие и БК"), AMMO("Патроны");

		final String title;

		Category(String title) {
			this.title = title;
		}
	}

	record Card(AdminItemCatalog.Entry entry, ItemStack stack, Category category, String title, String badge,
		String caliber, String searchText, boolean equip) {
		boolean matches(Category filter, String[] terms) {
			if (filter != Category.ALL && filter != category && !(filter == Category.WEAPON && category == Category.AMMO)) return false;
			for (String term : terms) {
				if (!searchText.contains(term)) return false;
			}
			return true;
		}
	}

	record Section(String title, List<Card> cards) { }

	private AdminGear() { }

	static List<Section> sections(List<Card> filteredCards) {
		List<Section> sections = new ArrayList<>();
		List<Card> items = filteredCards.stream().filter(card -> card.category() != Category.AMMO).toList();
		if (!items.isEmpty()) sections.add(new Section("", items));
		filteredCards.stream().filter(card -> card.category() == Category.AMMO)
			.collect(Collectors.groupingBy(card -> card.entry().variant(), LinkedHashMap::new, Collectors.toList()))
			.values().forEach(group -> sections.add(new Section(group.get(0).caliber(), group)));
		return sections;
	}

	static List<Card> cards() {
		List<Card> cards = new ArrayList<>();
		// ModItems and every loaded TarkovItemCatalog entry use the same item registry.
		for (var id : Registries.ITEM.getIds()) {
			if (!TarkovCraftMod.MOD_ID.equals(id.getNamespace())) continue;
			AdminItemCatalog.Entry entry = new AdminItemCatalog.Entry(id, null);
			ItemStack stack = entry.create();
			if (!stack.isEmpty()) cards.add(card(entry, stack, category(stack.getItem()), badge(stack), ""));
		}
		for (AdminItemCatalog.TaCZKind kind : AdminItemCatalog.TaCZKind.values()) {
			for (AdminItemCatalog.Entry entry : kind.clientEntries()) {
				ItemStack stack = kind.createPreview(entry.variant());
				if (stack.isEmpty()) continue;
				if (kind == AdminItemCatalog.TaCZKind.Ammo) {
					addAmmoCards(cards, entry, stack);
				} else {
					cards.add(card(entry, stack, Category.WEAPON, kind == AdminItemCatalog.TaCZKind.Gun ? "TaCZ" : "Обвес", ""));
				}
			}
		}
		cards.sort(Comparator.comparing((Card card) -> AdminCommands.searchable(card.caliber().isEmpty() ? card.title() : card.caliber()))
			.thenComparing(card -> card.category() == Category.AMMO ? card.entry().variant().toString() : ""));
		return cards;
	}

	private static void addAmmoCards(List<Card> cards, AdminItemCatalog.Entry entry, ItemStack stack) {
		String ammoId = entry.variant().toString();
		var spec = TaCZCaliberCatalog.getSpecOrDynamic(ammoId);
		String caliber = TaCZCaliberCatalog.getDefaultCalibers().contains(spec) ? spec.displayName() : stack.getName().getString();
		List<AmmoTier> tiers = TaCZCaliberCatalog.isBallisticAmmoId(ammoId) ? spec.getAllowedTiersList() : List.of(AmmoTier.FMJ);
		for (AmmoTier tier : tiers) {
			ItemStack preview = stack.copy();
			AmmoTier.setOnAmmoStack(preview, tier);
			cards.add(card(new AdminItemCatalog.Entry(entry.itemId(), entry.variant(), tier), preview, Category.AMMO, tier.name(), caliber));
		}
	}

	private static Card card(AdminItemCatalog.Entry entry, ItemStack stack, Category category, String badge, String caliber) {
		String title = stack.getName().getString();
		String search = AdminCommands.searchable(title + " " + entry.itemId() + " "
			+ (entry.variant() == null ? "" : entry.variant()) + " " + category.title + " " + badge + " " + caliber
			+ (category == Category.AMMO ? " " + entry.tier().getBadge() + " " + Category.WEAPON.title : ""));
		return new Card(entry, stack, category, title, badge, caliber, search, !AdminItemCatalog.equipmentSlots(stack).isEmpty());
	}

	private static Category category(Item item) {
		if (item instanceof TarkovArmorItem armor) return switch (armor.getTargetSlot()) {
			case HELMET -> Category.HELMET;
			case BODY_ARMOR -> Category.ARMOR;
			default -> Category.HEAD;
		};
		if (item instanceof TarkovHeadsetItem) return Category.HEAD;
		if (item instanceof TacticalRigItem) return Category.RIG;
		if (item instanceof BackpackItem) return Category.BACKPACK;
		if (item instanceof TarkovStimulatorItem) return Category.STIM;
		if (item instanceof TarkovMedicalItem) return Category.MEDICAL;
		if (item instanceof TarkovFoodItem) return Category.FOOD;
		if (item instanceof TarkovGrenadeItem || item instanceof TarkovCaseItem) return Category.CASE;
		if (item instanceof TarkovMeleeItem) return Category.WEAPON;
		return Category.ALL;
	}

	private static String badge(ItemStack stack) {
		Item item = stack.getItem();
		if (item instanceof TarkovArmorItem armor && armor.getArmorClass() > 0) return "CL." + armor.getArmorClass();
		if (item instanceof TacticalRigItem rig) {
			return rig.getCompartmentLayouts().stream().mapToInt(size -> size.width() * size.height()).sum() + " кл.";
		}
		if (item instanceof BackpackItem backpack) return backpack.getGridWidth() + "×" + backpack.getGridHeight() + " кл.";
		if (item instanceof TarkovCaseItem c) return c.getGridWidth() + "×" + c.getGridHeight() + " кл.";
		if (item instanceof TarkovMedicalItem med) {
			MedicalType type = med.getMedicalType();
			return type.getMaxCapacity() + (type.getCapacityMode() == MedicalType.CapacityMode.HP_POOL ? " HP" : " исп.");
		}
		var size = ItemSizeRegistry.getSize(stack);
		return size.width() + "×" + size.height();
	}
}
