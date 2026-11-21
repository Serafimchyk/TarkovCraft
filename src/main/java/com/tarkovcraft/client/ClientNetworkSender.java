package com.tarkovcraft.client;

import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.network.TarkovNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client-only sender helpers for TarkovCraft C2S packets introduced in the EFT UI refactor.
 * Keeps client networking imports strictly inside com.tarkovcraft.client.*.
 */
@Environment(EnvType.CLIENT)
public final class ClientNetworkSender {
	private ClientNetworkSender() {
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
		ClientPlayNetworking.send(TarkovNetworking.C2S_CLOSE_CASE, PacketByteBufs.empty());
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
