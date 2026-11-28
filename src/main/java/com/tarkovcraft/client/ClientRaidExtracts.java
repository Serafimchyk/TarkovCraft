package com.tarkovcraft.client;

import com.tarkovcraft.network.ArenaAdminPackets;
import com.tarkovcraft.network.RaidExtractPackets;
import com.tarkovcraft.network.RaidExtractPackets.Exit;
import com.tarkovcraft.network.RaidExtractPackets.Part;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientRaidExtracts {
	public static final int GREEN = 0xFF42F47B;
	public static final int AMBER = 0xFFFFAF42;
	private static final DustParticleEffect GREEN_SMOKE = new DustParticleEffect(new Vector3f(0.15f, 0.92f, 0.35f), 2.9f);
	private static final DustParticleEffect AMBER_SMOKE = new DustParticleEffect(new Vector3f(1f, 0.59f, 0.12f), 2.9f);
	private static final Map<String, Zone> ZONES = new LinkedHashMap<>();
	private static final Map<Placed, Long> RECENT = new LinkedHashMap<>();
	private static Identifier dimension;
	private static boolean directions;
	private static Progress progress;
	private static long listOpenedAt = -1;
	private static int listPage;

	public record Zone(Exit exit, boolean activated) {
		public boolean signalOn() { return !exit.loud() || activated; }
	}

	public record Progress(String name, int remaining, int duration, long syncedAt, long startedAt) { }

	private record Placed(Identifier dimension, Exit exit) { }

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(RaidExtractPackets.PLACED, (client, handler, buf, sender) -> {
			var placed = new Placed(buf.readIdentifier(), Exit.read(buf));
			client.execute(() -> {
				if (client.getNetworkHandler() == handler) RECENT.put(placed, Util.getMeasuringTimeMs() + 15_000);
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(RaidExtractPackets.STATE, (client, handler, buf, sender) -> {
			Part part = buf.readEnumConstant(Part.class);
			Runnable apply;
			switch (part) {
				case START -> {
					Identifier nextDimension = buf.readIdentifier();
					boolean showDirections = buf.readBoolean();
					apply = () -> {
						clear();
						dimension = nextDimension;
						directions = showDirections;
					};
				}
				case EXITS -> {
					int count = buf.readVarInt();
					if (count < 0 || count > ArenaAdminPackets.BATCH_SIZE) return;
					List<Zone> batch = new ArrayList<>(count);
					for (int i = 0; i < count; i++) batch.add(new Zone(Exit.read(buf), buf.readBoolean()));
					apply = () -> batch.forEach(zone -> ZONES.put(zone.exit().name(), zone));
				}
				case ACTIVATION -> {
					String name = buf.readString(256);
					boolean active = buf.readBoolean();
					apply = () -> ZONES.computeIfPresent(name, (key, old) -> new Zone(old.exit(), active));
				}
				case PROGRESS -> {
					String name = buf.readBoolean() ? buf.readString(256) : null;
					int remaining = name == null ? 0 : buf.readVarInt();
					int duration = name == null ? 0 : buf.readVarInt();
					apply = () -> applyProgress(client, name, remaining, duration);
				}
				case END -> apply = ClientRaidExtracts::clear;
				default -> throw new IllegalStateException("Unexpected extract packet " + part);
			}
			client.execute(() -> {
				if (client.getNetworkHandler() == handler) apply.run();
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			clear();
			RECENT.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(ClientRaidExtracts::tick);
	}

	private static void applyProgress(MinecraftClient client, String name, int remaining, int duration) {
		if (name == null) {
			if (progress != null && client.player != null && client.player.isAlive()) {
				client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.7f));
			}
			progress = null;
		} else if (client.world != null && ZONES.containsKey(name) && remaining > 0 && remaining <= duration) {
			long now = client.world.getTime();
			long started = progress != null && name.equals(progress.name()) ? progress.startedAt() : now;
			progress = new Progress(name, remaining, duration, now, started);
		}
	}

	private static void clear() {
		dimension = null;
		directions = false;
		ZONES.clear();
		progress = null;
		listOpenedAt = -1;
		listPage = 0;
	}

	public static boolean inRaidWorld(ClientWorld world) {
		return dimension != null && world != null && dimension.equals(world.getRegistryKey().getValue());
	}

	public static Collection<Zone> zones() { return ZONES.values(); }

	public static List<Exit> recent(ClientWorld world) {
		long now = Util.getMeasuringTimeMs();
		return RECENT.entrySet().stream()
			.filter(entry -> entry.getValue() > now && entry.getKey().dimension().equals(world.getRegistryKey().getValue()))
			.map(entry -> entry.getKey().exit()).toList();
	}

	public static Progress progressAt(Vec3d feet) {
		Zone zone = progress == null ? null : ZONES.get(progress.name());
		return zone != null && RaidExtractPackets.contains(zone.exit().pos(), zone.exit().radius(), feet) ? progress : null;
	}

	public static Exit activationPrompt(Vec3d feet) {
		if (progressAt(feet) != null) return null;
		return ZONES.values().stream().filter(zone -> !zone.signalOn())
			.map(Zone::exit).filter(exit -> RaidExtractPackets.contains(exit.pos(), exit.radius(), feet))
			.min(Comparator.comparingDouble((Exit exit) -> feet.squaredDistanceTo(Vec3d.ofBottomCenter(exit.pos())))
				.thenComparing(Exit::name)).orElse(null);
	}

	public static void showList(MinecraftClient client) {
		if (client.player == null || !client.player.isAlive() || !inRaidWorld(client.world)) return;
		long now = Util.getMeasuringTimeMs();
		listPage = listOpenedAt >= 0 && now - listOpenedAt < 5_000 ? listPage + 1 : 0;
		listOpenedAt = now;
	}

	public static long listOpenedAt() { return listOpenedAt; }
	public static int listPage() { return listPage; }
	public static boolean showDirections() { return directions; }

	private static void tick(MinecraftClient client) {
		RECENT.values().removeIf(expiry -> expiry <= Util.getMeasuringTimeMs());
		ClientWorld world = client.world;
		if (!inRaidWorld(world) || client.player == null || !client.player.isAlive() || client.isPaused()) return;
		long time = world.getTime();
		if (time % 4 != 0) return;
		Vec3d feet = client.player.getPos();
		ZONES.values().stream()
			.filter(zone -> feet.squaredDistanceTo(Vec3d.ofBottomCenter(zone.exit().pos())) <= 96 * 96)
			.sorted(Comparator.comparingDouble(zone -> feet.squaredDistanceTo(Vec3d.ofBottomCenter(zone.exit().pos()))))
			.limit(12).forEach(zone -> smoke(world, zone, time));
	}

	private static void smoke(ClientWorld world, Zone zone, long time) {
		var random = world.random;
		var center = Vec3d.ofBottomCenter(zone.exit().pos());
		var dust = zone.signalOn() ? GREEN_SMOKE : AMBER_SMOKE;
		int count = zone.activated() ? 8 : 5;
		for (int i = 0; i < count; i++) {
			double height = 0.3 + 9 * random.nextDouble();
			double spread = 0.15 + height * 0.055;
			world.addParticle(dust, true, center.x + random.nextGaussian() * spread, center.y + height,
				center.z + random.nextGaussian() * spread, 0, 0.035, 0);
		}
		if (time % 8 == 0) {
			world.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, center.x, center.y + 0.4, center.z,
				random.nextGaussian() * 0.016, 0.09, random.nextGaussian() * 0.016);
		}
		world.addParticle(ParticleTypes.FLAME, true, center.x, center.y + 0.12, center.z,
			random.nextGaussian() * 0.02, 0.055, random.nextGaussian() * 0.02);
	}

	private ClientRaidExtracts() { }
}
