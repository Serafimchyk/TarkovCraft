package com.tarkovcraft.network;

import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.IdentityHashMap;
import java.util.Map;

public final class EquipmentAppearanceSync {
	private static final Map<ServerPlayerEntity, EquipmentAppearance> LAST_SENT = new IdentityHashMap<>();

	private EquipmentAppearanceSync() {
	}

	public static void register() {
		// Players may already be tracked by the time the client's channel is registered.
		S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
			if (!channels.contains(EquipmentAppearance.CHANNEL)) return;
			ServerPlayerEntity observer = handler.player;
			for (ServerPlayerEntity player : PlayerLookup.world(observer.getServerWorld())) {
				if (player != observer && PlayerLookup.tracking(player).contains(observer)) {
					sendCurrent(observer, player);
				}
			}
		});
		EntityTrackingEvents.START_TRACKING.register((entity, observer) -> {
			if (entity instanceof ServerPlayerEntity player) {
				sendCurrent(observer, player);
			}
		});
		EntityTrackingEvents.STOP_TRACKING.register((entity, observer) -> {
			if (entity instanceof ServerPlayerEntity player) {
				send(observer, player, EquipmentAppearance.EMPTY);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_SENT.remove(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> LAST_SENT.remove(oldPlayer));
	}

	private static void sendCurrent(ServerPlayerEntity observer, ServerPlayerEntity player) {
		if (player instanceof PlayerLoadoutHolder holder) {
			send(observer, player, EquipmentAppearance.capture(holder.tarkovcraft$getLoadout()));
		}
	}

	static void update(ServerPlayerEntity player, PlayerLoadout loadout) {
		EquipmentAppearance appearance = EquipmentAppearance.capture(loadout);
		if (!appearance.equals(LAST_SENT.put(player, appearance))) {
			for (ServerPlayerEntity observer : PlayerLookup.tracking(player)) {
				send(observer, player, appearance);
			}
		}
	}

	private static void send(ServerPlayerEntity observer, ServerPlayerEntity player, EquipmentAppearance appearance) {
		if (observer == player || !ServerPlayNetworking.canSend(observer, EquipmentAppearance.CHANNEL)) return;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeUuid(player.getUuid());
		appearance.write(buf);
		ServerPlayNetworking.send(observer, EquipmentAppearance.CHANNEL, buf);
	}
}
