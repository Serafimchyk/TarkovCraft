package com.tarkovcraft.client;

import com.tarkovcraft.client.screen.AdminMenuScreen;
import com.tarkovcraft.item.ArenaBoundaryWandItem;
import com.tarkovcraft.network.ArenaAdminPackets;
import com.tarkovcraft.network.ArenaAdminPackets.Details;
import com.tarkovcraft.network.ArenaAdminPackets.Key;
import com.tarkovcraft.network.ArenaAdminPackets.Point;
import com.tarkovcraft.network.ArenaAdminPackets.Section;
import com.tarkovcraft.network.ArenaAdminPackets.SnapshotPart;
import com.tarkovcraft.network.ArenaAdminPackets.WandAction;
import com.tarkovcraft.registry.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ClientArenaEditor {
	public record ArenaView(Details details, List<Point> points) { }
	private static Key held;
	private static Key draftKey;
	private static List<BlockPos> draft = List.of();
	private static List<BlockPos> incomingDraft;
	private static ArenaView preview;

	private ClientArenaEditor() { }

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ArenaAdminPackets.SNAPSHOT, (client, handler, in, sender) -> {
			int syncId = in.readVarInt();
			SnapshotPart part = in.readEnumConstant(SnapshotPart.class);
			String message = part == SnapshotPart.MESSAGE ? in.readString(256) : null;
			Details details = part == SnapshotPart.ARENA ? Details.read(in) : null;
			List<Point> points = new ArrayList<>();
			if (part == SnapshotPart.POINTS) {
				int count = in.readVarInt();
				if (count < 0 || count > ArenaAdminPackets.BATCH_SIZE) return;
				for (int i = 0; i < count; i++) points.add(Point.read(in));
			}
			client.execute(() -> {
				if (client.getNetworkHandler() == handler && client.currentScreen instanceof AdminMenuScreen screen
					&& screen.getScreenHandler().syncId == syncId) screen.receiveArenaSnapshot(part, details, points, message);
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ArenaAdminPackets.DRAFT, (client, handler, in, sender) -> {
			Key key = Key.read(in);
			boolean start = in.readBoolean();
			boolean end = in.readBoolean();
			int count = in.readVarInt();
			if (count < 0 || count > ArenaAdminPackets.BATCH_SIZE) return;
			List<BlockPos> points = new ArrayList<>(count);
			for (int i = 0; i < count; i++) points.add(in.readBlockPos());
			client.execute(() -> {
				if (client.getNetworkHandler() != handler) return;
				if (start) incomingDraft = new ArrayList<>();
				if (incomingDraft == null) return;
				incomingDraft.addAll(points);
				if (end) {
					draftKey = key;
					draft = List.copyOf(incomingDraft);
					incomingDraft = null;
				}
			});
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Key next = heldKey(client);
			if (!Objects.equals(next, held)) {
				held = next;
				if (next != null) sendWand(WandAction.SYNC);
			}
			if (client.player == null || !client.player.hasPermissionLevel(2)) reset();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void reset() {
		held = null;
		draftKey = null;
		draft = List.of();
		incomingDraft = null;
		preview = null;
	}

	public static Key heldKey(MinecraftClient client) {
		if (client.player == null || client.world == null || client.player.isSpectator() || !client.player.hasPermissionLevel(2)) return null;
		Key binding = ArenaBoundaryWandItem.binding(ArenaBoundaryWandItem.held(client.player));
		return binding != null && binding.dimension().equals(client.world.getRegistryKey().getValue()) ? binding : null;
	}

	public static boolean interceptAttack(MinecraftClient client, boolean undo) {
		if (client.player == null || client.player.isSpectator()) return false;
		if (!client.player.getMainHandStack().isOf(ModItems.ARENA_BOUNDARY_WAND)
			&& !(client.player.getMainHandStack().isEmpty() && client.player.getOffHandStack().isOf(ModItems.ARENA_BOUNDARY_WAND))) return false;
		if (client.player.hasPermissionLevel(2) && undo) sendWand(WandAction.UNDO);
		return true;
	}

	public static boolean scroll(MinecraftClient client, double amount) {
		if (amount == 0 || client.currentScreen != null || client.player == null || client.player.isSpectator()
			|| !client.player.isSneaking() || !client.player.hasPermissionLevel(2)
			|| ArenaBoundaryWandItem.held(client.player).isEmpty()) return false;
		sendWand(amount > 0 ? WandAction.CYCLE_PREVIOUS : WandAction.CYCLE_NEXT);
		return true;
	}

	private static void sendWand(WandAction action) {
		if (!ClientPlayNetworking.canSend(ArenaAdminPackets.WAND)) return;
		var out = PacketByteBufs.create();
		out.writeByte(action.ordinal());
		ClientPlayNetworking.send(ArenaAdminPackets.WAND, out);
	}

	public static List<BlockPos> draft(Key key) {
		return key.equals(draftKey) ? draft : List.of();
	}

	public static ArenaView preview() {
		return preview;
	}

	public static boolean isPreviewing(Key key) {
		return preview != null && preview.details().key().equals(key);
	}

	public static void togglePreview(ArenaView arena) {
		preview = isPreviewing(arena.details().key()) ? null : arena;
	}

	public static void updatePreview(ArenaView arena) {
		if (isPreviewing(arena.details().key())) preview = arena;
	}

	public static String label(Section section) {
		return switch (section) {
			case BOUNDARY -> "Границы";
			case PMC -> "Спавны ЧВК";
			case SCAV -> "Спавны Диких";
			case EXTRACT -> "Экстракты";
			case AIRDROP -> "Аирдропы";
			case LOOT -> "Точки лута";
			case POI -> "Точки интереса";
		};
	}

	public static String pointLabel(Point point) {
		return switch (point.section()) {
			case BOUNDARY -> "Вершина границы";
			case PMC -> "Спавн ЧВК";
			case SCAV -> "Спавн Дикого";
			case EXTRACT -> "Экстракт " + point.name() + " (" + (point.loud() ? "loud" : "quiet") + ")";
			case AIRDROP -> "Аирдроп";
			case LOOT -> "Точка лута";
			case POI -> "POI";
		};
	}

	public static int color(Section section) {
		return switch (section) {
			case BOUNDARY -> 0xFFD4B46A;
			case PMC -> 0xFF38BDF8;
			case SCAV -> 0xFFFB923C;
			case EXTRACT -> 0xFF4ADE80;
			case AIRDROP -> 0xFFF472B6;
			case LOOT -> 0xFF2DD4BF;
			case POI -> 0xFFC4B5FD;
		};
	}
}
