package com.tarkovcraft.compat.voicechat;

import com.tarkovcraft.audio.HearingProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Creates the Simple Voice Chat entrypoint only when its API is available. */
public final class VoicechatAdapter implements LanguageAdapter {
	private static final String API = "de.maxhenkel.voicechat.api.";
	private static final String SIMPLE_VOICE_CHAT = "voicechat";
	private static final String VOICE_PHYSICS = "vc-audio-distance";
	private static final Map<UUID, HearingProfile> SERVER_PROFILES = new ConcurrentHashMap<>();
	private static final Map<UUID, Float> FILTER_STATE = new ConcurrentHashMap<>();
	private static volatile HearingProfile clientProfile = HearingProfile.DEFAULT;
	private static final ClassValue<Method> VOICE_PHYSICS_PLAYER_ID = new ClassValue<>() {
		@Override
		protected Method computeValue(Class<?> type) {
			try {
				return type.getMethod("id");
			} catch (NoSuchMethodException exception) {
				throw new IllegalStateException("Voice Physics listener API has changed", exception);
			}
		}
	};

	public static void updateServerProfile(UUID player, HearingProfile profile) {
		if (FabricLoader.getInstance().isModLoaded(SIMPLE_VOICE_CHAT)) SERVER_PROFILES.put(player, profile);
	}

	public static float voicePhysicsRange(Object listener) {
		if (listener == null) return 1.0f;
		try {
			UUID id = (UUID) VOICE_PHYSICS_PLAYER_ID.get(listener.getClass()).invoke(listener);
			return SERVER_PROFILES.getOrDefault(id, HearingProfile.DEFAULT).range();
		} catch (ReflectiveOperationException | IllegalStateException exception) {
			return 1.0f;
		}
	}

	public static void removePlayer(UUID player) {
		SERVER_PROFILES.remove(player);
	}

	public static void updateClientProfile(HearingProfile profile) {
		if (!profile.muffled() && clientProfile.muffled()) FILTER_STATE.clear();
		clientProfile = profile;
	}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		if (!FabricLoader.getInstance().isModLoaded(SIMPLE_VOICE_CHAT) || !type.getName().equals(API + "VoicechatPlugin")) {
			throw new LanguageAdapterException("Simple Voice Chat API is unavailable");
		}
		Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, arguments) -> switch (method.getName()) {
				case "getPluginId", "toString" -> "tarkovcraft";
				case "hashCode" -> System.identityHashCode(instance);
				case "equals" -> instance == arguments[0];
				case "registerEvents" -> {
					try {
						registerEvents(arguments[0]);
					} catch (ReflectiveOperationException exception) {
						LoggerFactory.getLogger("TarkovCraft Voice").warn("Could not register the Simple Voice Chat integration", exception);
					}
					yield null;
				}
				default -> null;
			});
		return type.cast(proxy);
	}

	private static void registerEvents(Object registration) throws ReflectiveOperationException {
		Method register = api("events.EventRegistration").getMethod("registerEvent", Class.class, Consumer.class, int.class);
		registerRangeEvents(register, registration, FabricLoader.getInstance().isModLoaded(VOICE_PHYSICS));
		if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
			registerClientEvents(register, registration);
		}
	}

	private static void registerRangeEvents(Method register, Object registration, boolean voicePhysics) throws ReflectiveOperationException {
		Class<?> distanceEvent = api("events.VoiceDistanceEvent");
		Method getDistance = distanceEvent.getMethod("getDistance");
		Method setDistance = distanceEvent.getMethod("setDistance", float.class);
		register.invoke(registration, distanceEvent, guarded(event ->
			setDistance.invoke(event, (float) getDistance.invoke(event) * HearingProfile.ACTIVE_RANGE)), -1000);
		// Voice Physics adjusts its own per-listener packet in ServerRange.
		if (voicePhysics) return;
		Class<?> soundEvent = api("events.EntitySoundPacketEvent");
		Method source = soundEvent.getMethod("getSource");
		Method receiver = soundEvent.getMethod("getReceiverConnection");
		Method packet = soundEvent.getMethod("getPacket");
		Method voice = soundEvent.getMethod("getVoicechat");
		Method cancel = soundEvent.getMethod("cancel");
		Class<?> connection = api("VoicechatConnection");
		Method apiPlayer = connection.getMethod("getPlayer");
		Method nativePlayer = api("Player").getMethod("getPlayer");
		Class<?> entityPacket = api("packets.EntitySoundPacket");
		Method packetDistance = entityPacket.getMethod("getDistance");
		Method builderMethod = entityPacket.getMethod("entitySoundPacketBuilder");
		Class<?> builderType = api("packets.EntitySoundPacket$Builder");
		Method builderDistance = builderType.getMethod("distance", float.class);
		Method build = builderType.getMethod("build");
		Method send = api("VoicechatServerApi").getMethod("sendEntitySoundPacketTo", connection, entityPacket);
		register.invoke(registration, soundEvent, guarded(event -> {
			if (!"proximity".equals(source.invoke(event))) return;
			Object target = receiver.invoke(event);
			if (target == null) return;
			Object player = nativePlayer.invoke(apiPlayer.invoke(target));
			HearingProfile profile = player instanceof ServerPlayerEntity serverPlayer
				? SERVER_PROFILES.getOrDefault(serverPlayer.getUuid(), HearingProfile.DEFAULT)
				: HearingProfile.DEFAULT;
			if (profile.range() == HearingProfile.ACTIVE_RANGE) return;
			Object original = packet.invoke(event);
			float range = (float) packetDistance.invoke(original) / HearingProfile.ACTIVE_RANGE * profile.range();
			Object builder = builderMethod.invoke(original);
			builderDistance.invoke(builder, range);
			Object adjusted = build.invoke(builder);
			if (Boolean.TRUE.equals(cancel.invoke(event))) send.invoke(voice.invoke(event), target, adjusted);
		}), -1000);
	}

	private static void registerClientEvents(Method register, Object registration) throws ReflectiveOperationException {
		Class<?> pcm = api("events.ClientReceiveSoundEvent");
		Method read = pcm.getMethod("getRawAudio");
		Method write = pcm.getMethod("setRawAudio", short[].class);
		Method getId = pcm.getMethod("getId");
		Consumer<Object> process = guarded(event -> {
			HearingProfile profile = clientProfile;
			if (profile == HearingProfile.DEFAULT) return;
			short[] input = (short[]) read.invoke(event);
			if (input == null || input.length == 0) return;
			UUID id = (UUID) getId.invoke(event);
			short[] output = new short[input.length];
			float previous = id == null ? 0.0f : FILTER_STATE.getOrDefault(id, 0.0f);
			for (int i = 0; i < output.length; i++) {
				float sample = input[i];
				if (profile.muffled()) {
					previous += 0.16f * (sample - previous);
					sample = previous * profile.ambientGain();
				} else {
					float gained = Math.abs(sample * profile.ambientGain());
					float knee = 0.72f * Short.MAX_VALUE;
					sample = Math.copySign(gained <= knee ? gained : knee + (gained - knee) * 0.25f, sample);
				}
				output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample)));
			}
			if (profile.muffled() && id != null) FILTER_STATE.put(id, previous);
			write.invoke(event, (Object) output);
		});
		for (String subtype : new String[]{"EntitySound", "LocationalSound"}) {
			register.invoke(registration, api("events.ClientReceiveSoundEvent$" + subtype), process, -1000);
		}
		Class<?> icon = api("events.NameTagIconRenderEvent");
		Method cancel = icon.getMethod("cancel");
		register.invoke(registration, icon, guarded(event -> cancel.invoke(event)), 1000);
		register.invoke(registration, api("events.ClientVoicechatConnectionEvent"),
			(Consumer<Object>) event -> FILTER_STATE.clear(), 0);
	}

	private static Class<?> api(String name) throws ClassNotFoundException {
		return Class.forName(API + name, false, VoicechatAdapter.class.getClassLoader());
	}

	@FunctionalInterface
	private interface EventAction {
		void run(Object event) throws ReflectiveOperationException;
	}

	private static Consumer<Object> guarded(EventAction action) {
		return new Consumer<>() {
			private boolean failed;

			@Override
			public void accept(Object event) {
				if (failed) return;
				try {
					action.run(event);
				} catch (ReflectiveOperationException exception) {
					failed = true;
					LoggerFactory.getLogger("TarkovCraft Voice").warn("Disabled an incompatible Simple Voice Chat event handler", exception);
				}
			}
		};
	}
}
