package com.tarkovcraft.item;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.loadout.EquipmentSlotType;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AdminItemCatalog {
	private AdminItemCatalog() { }

	public record Entry(Identifier itemId, Identifier variant) {
		public ItemStack create() {
			if (variant == null) {
				return TarkovCraftMod.MOD_ID.equals(itemId.getNamespace())
					? Registries.ITEM.getOrEmpty(itemId).map(ItemStack::new).orElse(ItemStack.EMPTY)
					: ItemStack.EMPTY;
			}
			for (TaCZKind kind : TaCZKind.values()) {
				if (kind.itemId.equals(itemId)) return kind.create(variant);
			}
			return ItemStack.EMPTY;
		}
	}

	public static List<EquipmentSlotType> equipmentSlots(ItemStack stack) {
		if (stack.getItem() instanceof TarkovArmorItem armor) return List.of(armor.getTargetSlot());
		if (stack.getItem() instanceof BackpackItem) return List.of(EquipmentSlotType.BACKPACK);
		if (stack.getItem() instanceof TacticalRigItem) return List.of(EquipmentSlotType.RIG);
		if (stack.getItem() instanceof TarkovFunctionalItem item && item.isHeadset()) return List.of(EquipmentSlotType.HEADSET);
		if (stack.getItem() instanceof TarkovMeleeItem) return List.of(EquipmentSlotType.MELEE);
		if (TaCZKind.Gun.itemId.equals(Registries.ITEM.getId(stack.getItem()))) {
			return List.of(EquipmentSlotType.PISTOL, EquipmentSlotType.PRIMARY_1, EquipmentSlotType.PRIMARY_2)
				.stream().filter(slot -> slot.accepts(stack)).toList();
		}
		return List.of();
	}

	public enum TaCZKind {
		Gun("modern_kinetic_gun"), Ammo("ammo"), Attachment("attachment");

		private final Identifier itemId;
		private final Method list, commonList, find, builder, setId, build;

		TaCZKind(String path) {
			itemId = new Identifier("tacz", path);
			Method list = null, commonList = null, find = null, builder = null, setId = null, build = null;
			if (FabricLoader.getInstance().isModLoaded("tacz")) {
				try {
					Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
					Class<?> type = Class.forName("com.tacz.guns.api.item.builder." + name() + "ItemBuilder");
					find = api.getMethod("getCommon" + name() + "Index", Identifier.class);
					builder = type.getMethod("create");
					setId = type.getMethod("setId", Identifier.class);
					build = type.getMethod("build");
					commonList = api.getMethod("getAllCommon" + name() + "Index");
					if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
						list = api.getMethod("getAllClient" + name() + "Index");
					}
				} catch (ReflectiveOperationException | LinkageError ignored) {
					// The optional TaCZ integration may be absent or have a different API.
				}
			}
			this.list = list;
			this.commonList = commonList;
			this.find = find;
			this.builder = builder;
			this.setId = setId;
			this.build = build;
		}

		public List<Entry> clientEntries() {
			return entries(list);
		}

		public List<Entry> commonEntries() {
			return entries(commonList);
		}

		private List<Entry> entries(Method source) {
			if (source == null || build == null) return List.of();
			try {
				Collection<?> indexes = (Collection<?>) source.invoke(null);
				return indexes.stream().map(Map.Entry.class::cast)
					.map(index -> new Entry(itemId, (Identifier) index.getKey()))
					.sorted(java.util.Comparator.comparing(Entry::variant)).toList();
			} catch (ReflectiveOperationException | LinkageError ignored) {
				return List.of();
			}
		}

		public ItemStack createPreview(Identifier id) {
			return buildStack(id);
		}

		private ItemStack create(Identifier id) {
			if (find == null || build == null) return ItemStack.EMPTY;
			try {
				if (((Optional<?>) find.invoke(null, id)).isEmpty()) return ItemStack.EMPTY;
				return buildStack(id);
			} catch (ReflectiveOperationException | LinkageError ignored) {
				return ItemStack.EMPTY;
			}
		}

		private ItemStack buildStack(Identifier id) {
			if (build == null) return ItemStack.EMPTY;
			try {
				return (ItemStack) build.invoke(setId.invoke(builder.invoke(null), id));
			} catch (ReflectiveOperationException | LinkageError ignored) {
				return ItemStack.EMPTY;
			}
		}
	}
}
