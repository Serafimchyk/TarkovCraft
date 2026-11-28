package com.tarkovcraft.network;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.item.AdminItemCatalog;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.screen.AdminMenuScreenHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class AdminItemNetworking {
	public static final Identifier C2S_ADMIN_GIVE_ITEM = new Identifier(TarkovCraftMod.MOD_ID, "admin_give_item");

	private AdminItemNetworking() { }

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(C2S_ADMIN_GIVE_ITEM, (server, player, handler, buf, sender) -> {
			if (!player.hasPermissionLevel(2) || buf.readableBytes() > 414) return;
			try {
				int syncId = buf.readVarInt();
				Identifier item = Identifier.tryParse(buf.readString(192));
				String rawVariant = buf.readString(192);
				Identifier variant = rawVariant.isEmpty() ? null : Identifier.tryParse(rawVariant);
				int count = buf.readUnsignedByte();
				boolean equip = buf.readBoolean();
				String tierId = buf.isReadable() ? buf.readString(3) : AmmoTier.FMJ.getId();
				AmmoTier tier = AmmoTier.byId(tierId);
				if (buf.isReadable() || item == null || (!rawVariant.isEmpty() && variant == null)
					|| !tier.getId().equals(tierId)
					|| count < 1 || count > 30 || (equip && count != 1)) return;
				server.execute(() -> give(player, syncId, new AdminItemCatalog.Entry(item, variant, tier), count, equip));
			} catch (RuntimeException ignored) {
				// Discard truncated or malformed requests.
			}
		});
	}

	private static void give(ServerPlayerEntity player, int syncId, AdminItemCatalog.Entry entry, int count, boolean equip) {
		if (!player.hasPermissionLevel(2) || !(player.currentScreenHandler instanceof AdminMenuScreenHandler menu)
			|| menu.syncId != syncId || !menu.canUse(player) || !(player instanceof PlayerLoadoutHolder holder)) return;

		ItemStack item = entry.create();
		if (item.isEmpty()) {
			player.sendMessage(Text.literal("Предмет недоступен на сервере."), true);
			return;
		}
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		if (equip) {
			List<EquipmentSlotType> slots = AdminItemCatalog.equipmentSlots(item).stream()
				.filter(slot -> loadout.canEquip(slot, item)).toList();
			if (slots.isEmpty()) {
				player.sendMessage(Text.literal("Нет подходящего слота снаряжения."), true);
				return;
			}
			EquipmentSlotType slot = slots.stream().filter(candidate -> loadout.getEquipment(candidate).isEmpty())
				.findFirst().orElse(slots.get(0));
			ItemStack previous = loadout.getEquipment(slot);
			loadout.setEquipment(slot, item);
			store(player, loadout, previous);
		} else {
			for (int remaining = count; remaining > 0; ) {
				int size = Math.min(remaining, item.getMaxCount());
				ItemStack stack = item.copy();
				stack.setCount(size);
				store(player, loadout, stack);
				remaining -= size;
			}
		}
		holder.tarkovcraft$syncLoadout();
		player.playerScreenHandler.sendContentUpdates();
		Text message = Text.literal(equip ? "Экипировано · " : "Выдано ×" + count + " · ").append(item.getName());
		if (entry.tier() != AmmoTier.FMJ) message = message.copy().append(" · " + entry.tier().getBadge());
		player.sendMessage(message, true);
	}

	private static void store(ServerPlayerEntity player, PlayerLoadout loadout, ItemStack stack) {
		if (stack.isEmpty()) return;
		if (player.isCreative()) storeInCreativeInventory(player.getInventory(), stack);
		loadout.tryInsertItem(stack, player.getUuid());
		if (!stack.isEmpty()) player.dropItem(stack, false);
	}

	private static void storeInCreativeInventory(PlayerInventory inventory, ItemStack stack) {
		while (!stack.isEmpty()) {
			int slot = inventory.getOccupiedSlotWithRoomForStack(stack);
			if (slot < 0) {
				int empty = inventory.getEmptySlot();
				if (empty >= 0) inventory.setStack(empty, stack.split(stack.getCount()));
				break;
			}
			ItemStack present = inventory.getStack(slot);
			int moved = Math.min(stack.getCount(), Math.min(present.getMaxCount(), inventory.getMaxCountPerStack()) - present.getCount());
			present.increment(moved);
			stack.decrement(moved);
		}
		inventory.markDirty();
	}
}
