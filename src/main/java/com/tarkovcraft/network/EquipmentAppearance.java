package com.tarkovcraft.network;

import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;

/** Public appearance only. The private loadout packet carries the actual stacks. */
public record EquipmentAppearance(Item armor, Item rig, Item backpack, Item mask, Item helmet) {
	public static final Identifier CHANNEL = new Identifier("tarkovcraft", "equipment_appearance");
	static final EquipmentAppearance EMPTY = new EquipmentAppearance(Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.AIR);

	public static EquipmentAppearance capture(PlayerLoadout loadout) {
		return new EquipmentAppearance(
			loadout.getEquipment(EquipmentSlotType.BODY_ARMOR).getItem(),
			loadout.getEquipment(EquipmentSlotType.RIG).getItem(),
			loadout.getEquipment(EquipmentSlotType.BACKPACK).getItem(),
			loadout.getEquipment(EquipmentSlotType.FACE_MASK).getItem(),
			loadout.getEquipment(EquipmentSlotType.HELMET).getItem());
	}

	public List<Item> items() {
		return List.of(armor, rig, backpack, mask, helmet);
	}

	public boolean isEmpty() {
		return equals(EMPTY);
	}

	void write(PacketByteBuf buf) {
		for (Item item : items()) {
			buf.writeIdentifier(Registries.ITEM.getId(item));
		}
	}

	public static EquipmentAppearance read(PacketByteBuf buf) {
		return new EquipmentAppearance(
			Registries.ITEM.get(buf.readIdentifier()), Registries.ITEM.get(buf.readIdentifier()),
			Registries.ITEM.get(buf.readIdentifier()), Registries.ITEM.get(buf.readIdentifier()),
			Registries.ITEM.get(buf.readIdentifier()));
	}
}
