package com.tarkovcraft.client;

import com.tarkovcraft.audio.TarkovSoundNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

public final class TaCZPositionedSound {
	private TaCZPositionedSound() {}

	public static void register() {
		if (!FabricLoader.getInstance().isModLoaded("tacz")) return;
		try {
			Bridge bridge = Bridge.create();
			ClientPlayNetworking.registerGlobalReceiver(TarkovSoundNetworking.GUNFIRE, (client, handler, buffer, sender) -> {
				double x = buffer.readDouble();
				double y = buffer.readDouble();
				double z = buffer.readDouble();
				Identifier displayId = buffer.readIdentifier();
				Identifier gunId = buffer.readIdentifier();
				String sound = buffer.readString(64);
				float volume = buffer.readFloat();
				float pitch = buffer.readFloat();
				client.execute(() -> {
					if (client.getNetworkHandler() == handler) bridge.play(client, x, y, z, displayId, gunId, sound, volume, pitch);
				});
			});
		} catch (ReflectiveOperationException exception) {
			LoggerFactory.getLogger("TarkovCraft Sound").warn("TaCZ positioned sound API is unavailable", exception);
		}
	}

	private record Bridge(Method gunDisplay, Method sounds, Method sample, Method resolveVolume, Method resolvePitch,
			Method hasTaczResource, Constructor<?> constructor, SoundEvent gunEvent) {
		private static Bridge create() throws ReflectiveOperationException {
			Class<?> data = Class.forName("com.tacz.guns.client.resource.pojo.display.SoundData");
			Method hasTaczResource = Class.forName("com.tacz.guns.client.sound.SoundPlayManager")
				.getDeclaredMethod("hasTaczSoundResource", MinecraftClient.class, Identifier.class);
			hasTaczResource.setAccessible(true);
			return new Bridge(
				Class.forName("com.tacz.guns.api.TimelessAPI").getMethod("getGunDisplay", Identifier.class, Identifier.class),
				Class.forName("com.tacz.guns.client.resource.GunDisplayInstance").getMethod("getSounds", String.class),
				data.getMethod("getSound"), data.getMethod("resolveVolume", Random.class, float.class),
				data.getMethod("resolvePitch", Random.class, float.class), hasTaczResource,
				Class.forName("com.tacz.guns.client.sound.GunSoundInstance").getConstructor(SoundEvent.class,
					SoundCategory.class, float.class, float.class, Entity.class, int.class, Identifier.class,
					boolean.class, boolean.class, boolean.class),
				(SoundEvent) Class.forName("com.tacz.guns.init.ModSounds").getField("GUN").get(null));
		}

		private void play(MinecraftClient client, double x, double y, double z, Identifier displayId, Identifier gunId,
				String sound, float volume, float pitch) {
			if (client.world == null) return;
			try {
				var display = (Optional<?>) gunDisplay.invoke(null, displayId, gunId);
				if (display.isEmpty()) return;
				Object data = sounds.invoke(display.get(), sound);
				if (data == null) return;
				Identifier id = (Identifier) sample.invoke(data);
				if (id == null) return;
				float gain = (float) resolveVolume.invoke(data, client.world.random, volume);
				float tone = (float) resolvePitch.invoke(data, client.world.random, pitch);
				boolean taczResource = (boolean) hasTaczResource.invoke(null, client, id);
				SoundEvent event = taczResource ? gunEvent : SoundEvent.of(id);
				// TaCZ squares positional gain and uses the supplied entity only to copy its coordinates.
				Entity position = new ArmorStandEntity(client.world, x, y, z);
				SoundInstance instance = (SoundInstance) constructor.newInstance(event, SoundCategory.PLAYERS,
					(float) Math.sqrt(Math.max(0.0f, gain)), tone, position, Integer.MAX_VALUE, id, true, false, taczResource);
				TarkovSoundEngine.markGunfire(instance);
				client.getSoundManager().play(instance);
			} catch (ReflectiveOperationException exception) {
				LoggerFactory.getLogger("TarkovCraft Sound").warn("Could not play a positioned TaCZ sound", exception);
			}
		}
	}
}
