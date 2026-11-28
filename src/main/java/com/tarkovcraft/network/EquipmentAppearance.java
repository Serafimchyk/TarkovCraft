package com.tarkovcraft.network;

import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;

/** Public appearance only. The private loadout packet carries the actual stacks. */
public record EquipmentAppearance(Item armor, Item rig, Item backpack, Item mask, Item helmet, Item headset) {
	public static final Identifier CHANNEL = new Identifier("tarkovcraft", "equipment_appearance");
	static final EquipmentAppearance EMPTY = new EquipmentAppearance(Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.AIR, Items.AIR);

	public static EquipmentAppearance capture(PlayerLoadout loadout) {
		return new EquipmentAppearance(
			loadout.getEquipment(EquipmentSlotType.BODY_ARMOR).getItem(),
			loadout.getEquipment(EquipmentSlotType.RIG).getItem(),
			loadout.getEquipment(EquipmentSlotType.BACKPACK).getItem(),
			loadout.getEquipment(EquipmentSlotType.FACE_MASK).getItem(),
			loadout.getEquipment(EquipmentSlotType.HELMET).getItem(),
			loadout.blocksHeadset() ? Items.AIR : loadout.getEquipment(EquipmentSlotType.HEADSET).getItem());
	}

	public List<Item> items() {
		return List.of(armor, rig, backpack, mask, helmet, headset);
	}

	public boolean isEmpty() {
		return equals(EMPTY);
	}

	public NbtCompound toTrackedNbt() {
		NbtList items = new NbtList();
		for (Item item : items()) {
			items.add(NbtString.of(Registries.ITEM.getId(item).toString()));
		}
		NbtCompound tag = new NbtCompound();
		tag.put("items", items);
		return tag;
	}

	public static EquipmentAppearance fromTrackedNbt(NbtCompound tag) {
		NbtList items = tag.getList("items", NbtElement.STRING_TYPE);
		if (items.size() != EMPTY.items().size()) return EMPTY;
		return new EquipmentAppearance(trackedItem(items, 0), trackedItem(items, 1),
			trackedItem(items, 2), trackedItem(items, 3), trackedItem(items, 4), trackedItem(items, 5));
	}

	private static Item trackedItem(NbtList items, int slot) {
		Identifier id = Identifier.tryParse(items.getString(slot));
		return id == null ? Items.AIR : Registries.ITEM.get(id);
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
			Registries.ITEM.get(buf.readIdentifier()), Registries.ITEM.get(buf.readIdentifier()));
	}
}
