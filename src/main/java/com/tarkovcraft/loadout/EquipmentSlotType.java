package com.tarkovcraft.loadout;

import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovHeadsetItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public enum EquipmentSlotType {
	PRIMARY_1("primary_1", "Primary 1"),
	PRIMARY_2("primary_2", "Primary 2"),
	PISTOL("pistol", "Pistol"),
	MELEE("melee", "Melee"),
	HELMET("helmet", "Helmet"),
	FACE_MASK("face_mask", "Face Mask"),
	BODY_ARMOR("body_armor", "Body Armor"),
	RIG("rig", "Tactical Rig"),
	BACKPACK("backpack", "Backpack"),
	HEADSET("headset", "Headset");

	private final String id;
	private final String displayName;

	EquipmentSlotType(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public boolean accepts(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return true;
		}
		Item item = stack.getItem();
		Identifier itemId = Registries.ITEM.getId(item);
		boolean isHeadset = item instanceof TarkovHeadsetItem;
		boolean isTaczGun = "tacz".equals(itemId.getNamespace()) && itemId.getPath().contains("gun");

		return switch (this) {
			case BACKPACK -> item instanceof BackpackItem;
			case RIG -> item instanceof TacticalRigItem;
			case HELMET -> item instanceof TarkovArmorItem a && a.getTargetSlot() == HELMET;
			case FACE_MASK -> item instanceof TarkovArmorItem a && a.getTargetSlot() == FACE_MASK;
			case HEADSET -> isHeadset;
			case BODY_ARMOR -> item instanceof TarkovArmorItem a && a.getTargetSlot() == BODY_ARMOR;
			case PISTOL -> isTaczGun && ItemSizeRegistry.isPistolLike(stack)
				|| (!isTaczGun && !(item instanceof BackpackItem) && !(item instanceof TacticalRigItem) && !(item instanceof TarkovArmorItem) && !isHeadset && ItemSizeRegistry.isPistolLike(stack));
			case PRIMARY_1, PRIMARY_2 -> isTaczGun
				|| (!(item instanceof BackpackItem) && !(item instanceof TacticalRigItem) && !(item instanceof TarkovArmorItem) && !isHeadset);
			case MELEE -> item instanceof SwordItem || item instanceof AxeItem
				|| itemId.getPath().contains("knife") || itemId.getPath().contains("melee");
		};
	}

	public static EquipmentSlotType byId(String id) {
		for (EquipmentSlotType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}
}
