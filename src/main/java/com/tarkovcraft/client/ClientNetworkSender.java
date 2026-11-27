package com.tarkovcraft.client;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.network.TarkovNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-only sender helpers for TarkovCraft C2S packets introduced in the EFT UI refactor.
 * Keeps client networking imports strictly inside com.tarkovcraft.client.*.
 */
@Environment(EnvType.CLIENT)
public final class ClientNetworkSender {
	private ClientNetworkSender() {
	}

	public static void sendStashSale(int syncId, UUID entryId) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(syncId);
		buf.writeUuid(entryId);
		ClientPlayNetworking.send(TarkovNetworking.C2S_STASH_SELL, buf);
	}

	public static void sendQuickEquip(byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		ClientPlayNetworking.send(TarkovNetworking.C2S_QUICK_EQUIP, buildSourceBuf(srcLoc, srcIdx, srcEntryId));
	}

	public static void sendUseItem(byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		ClientPlayNetworking.send(TarkovNetworking.C2S_USE_ITEM, buildSourceBuf(srcLoc, srcIdx, srcEntryId));
	}

	public static void sendSplitStack(byte srcLoc, int srcIdx, @Nullable UUID srcEntryId, int splitAmount) {
		PacketByteBuf buf = buildSourceBuf(srcLoc, srcIdx, srcEntryId);
		buf.writeVarInt(splitAmount);
		ClientPlayNetworking.send(TarkovNetworking.C2S_SPLIT_STACK, buf);
	}

	public static void sendRequestSearch(UUID targetId) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeUuid(targetId);
		ClientPlayNetworking.send(TarkovNetworking.C2S_REQUEST_SEARCH, buf);
	}

	public static void sendHealPart(byte srcLoc, int srcIdx, @Nullable UUID srcEntryId, @Nullable BodyPart part) {
		PacketByteBuf buf = buildSourceBuf(srcLoc, srcIdx, srcEntryId);
		buf.writeByte(part == null ? -1 : part.ordinal());
		ClientPlayNetworking.send(TarkovNetworking.C2S_HEAL_PART, buf);
	}

	public static void sendOpenCase(byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		ClientPlayNetworking.send(TarkovNetworking.C2S_OPEN_CASE, buildSourceBuf(srcLoc, srcIdx, srcEntryId));
	}

	public static void sendCloseCase() {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(false);
		ClientPlayNetworking.send(TarkovNetworking.C2S_CLOSE_CASE, buf);
	}

	public static void sendCloseCase(UUID containerUuid) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(containerUuid != null);
		if (containerUuid != null) {
			buf.writeUuid(containerUuid);
		}
		ClientPlayNetworking.send(TarkovNetworking.C2S_CLOSE_CASE, buf);
	}

	public static void sendWorkbenchCraftAmmo(String ammoId, AmmoTier tier, boolean upgradeFromFmj) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(ammoId, 64);
		buf.writeByte(tier.ordinal());
		buf.writeBoolean(upgradeFromFmj);
		ClientPlayNetworking.send(TarkovNetworking.C2S_WORKBENCH_CRAFT_AMMO, buf);
	}

	public static void sendWorkbenchCraftGear(Identifier targetItemId) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeIdentifier(targetItemId);
		ClientPlayNetworking.send(TarkovNetworking.C2S_WORKBENCH_CRAFT_GEAR, buf);
	}

	public static void registerWorkbenchClientReceiver() {
		ClientPlayNetworking.registerGlobalReceiver(TarkovNetworking.S2C_WORKBENCH_SYNC, (client, handler, buf, responseSender) -> {
			int itemCount = buf.readVarInt();
			Map<Identifier, Integer> items = new HashMap<>(itemCount);
			for (int i = 0; i < itemCount; i++) {
				items.put(buf.readIdentifier(), buf.readVarInt());
			}
			int fmjCount = buf.readVarInt();
			Map<String, Integer> fmj = new HashMap<>(fmjCount);
			for (int i = 0; i < fmjCount; i++) {
				fmj.put(buf.readString(64), buf.readVarInt());
			}
			int extraCount = buf.readVarInt();
			List<String> extras = new ArrayList<>(extraCount);
			for (int i = 0; i < extraCount; i++) {
				extras.add(buf.readString(64));
			}
			client.execute(() -> ClientWorkbenchCache.update(items, fmj, extras));
		});
	}

	private static PacketByteBuf buildSourceBuf(byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeByte(srcLoc);
		buf.writeVarInt(srcIdx);
		buf.writeBoolean(srcEntryId != null);
		if (srcEntryId != null) {
			buf.writeUuid(srcEntryId);
		}
		return buf;
	}
}
