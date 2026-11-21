package com.tarkovcraft.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Allows multiple KeyBindings on the same physical key (e.g. F for TarkovCraft interact + vanilla swapOffhand,
 * or TaCZ shoot/aim alongside vanilla attack/use) without overwriting each other in KEY_TO_BINDINGS.
 */
@Environment(EnvType.CLIENT)
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
	@Shadow
	@Final
	private static Map<String, KeyBinding> KEYS_BY_ID;

	@Shadow
	@Final
	private static Map<InputUtil.Key, KeyBinding> KEY_TO_BINDINGS;

	@Shadow
	private InputUtil.Key boundKey;

	@Shadow
	private int timesPressed;

	@Unique
	private static final Map<InputUtil.Key, List<KeyBinding>> TARKOV_KEY_TO_BINDINGS = new HashMap<>();

	@Unique
	private static void tarkovcraft$registerKey(KeyBinding kb) {
		InputUtil.Key k = ((KeyBindingMixin) (Object) kb).boundKey;
		if (k == null || k.equals(InputUtil.UNKNOWN_KEY)) {
			return;
		}
		List<KeyBinding> list = TARKOV_KEY_TO_BINDINGS.computeIfAbsent(k, ignored -> new ArrayList<>(2));
		if (!list.contains(kb)) {
			list.add(kb);
		}
	}

	@Inject(method = "<init>(Ljava/lang/String;Lnet/minecraft/client/util/InputUtil$Type;ILjava/lang/String;)V", at = @At("TAIL"))
	private void tarkovcraft$onConstruct(String translationKey, InputUtil.Type type, int code, String category, CallbackInfo ci) {
		tarkovcraft$registerKey((KeyBinding) (Object) this);
	}

	@Inject(method = "updateKeysByCode", at = @At("TAIL"))
	private static void tarkovcraft$onUpdateKeysByCode(CallbackInfo ci) {
		TARKOV_KEY_TO_BINDINGS.clear();
		for (KeyBinding kb : KEYS_BY_ID.values()) {
			tarkovcraft$registerKey(kb);
		}
	}

	@Inject(method = "onKeyPressed", at = @At("TAIL"))
	private static void tarkovcraft$onKeyPressed(InputUtil.Key key, CallbackInfo ci) {
		if (key == null || key.equals(InputUtil.UNKNOWN_KEY)) {
			return;
		}
		List<KeyBinding> list = TARKOV_KEY_TO_BINDINGS.get(key);
		if (list == null || list.size() <= 1) {
			return;
		}
		KeyBinding vanillaHandled = KEY_TO_BINDINGS.get(key);
		for (KeyBinding kb : list) {
			if (kb != vanillaHandled) {
				((KeyBindingMixin) (Object) kb).timesPressed++;
			}
		}
	}

	@Inject(method = "setKeyPressed", at = @At("TAIL"))
	private static void tarkovcraft$onSetKeyPressed(InputUtil.Key key, boolean pressed, CallbackInfo ci) {
		if (key == null || key.equals(InputUtil.UNKNOWN_KEY)) {
			return;
		}
		List<KeyBinding> list = TARKOV_KEY_TO_BINDINGS.get(key);
		if (list == null || list.size() <= 1) {
			return;
		}
		KeyBinding vanillaHandled = KEY_TO_BINDINGS.get(key);
		for (KeyBinding kb : list) {
			if (kb != vanillaHandled) {
				kb.setPressed(pressed);
			}
		}
	}
}
