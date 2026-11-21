package com.tarkovcraft.client;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.client.renderer.CorpseEntityRenderer;
import com.tarkovcraft.client.renderer.HitZoneDebugRenderer;
import com.tarkovcraft.client.screen.TarkovInventoryScreen;
import com.tarkovcraft.client.screen.TarkovWorkbenchScreen;
import com.tarkovcraft.health.TaCZBulletHelper;
import com.tarkovcraft.network.TarkovNetworking;
import com.tarkovcraft.registry.ModEntities;
import com.tarkovcraft.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.FoodComponent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class TarkovCraftClient implements ClientModInitializer {
	public static KeyBinding toggleMeleeKey;
	public static KeyBinding interactKey;
	public static KeyBinding toggleHitboxesKey;

	@Override
	public void onInitializeClient() {
		TarkovNetworking.registerClientReceivers();
		ClientNetworkSender.registerWorkbenchClientReceiver();
		HandledScreens.register(ModScreenHandlers.TARKOV_SCREEN_HANDLER, TarkovInventoryScreen::new);
		HandledScreens.register(ModScreenHandlers.TARKOV_WORKBENCH_SCREEN_HANDLER, TarkovWorkbenchScreen::new);
		EntityRendererRegistry.register(ModEntities.CORPSE, CorpseEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.GRENADE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.AIRDROP, com.tarkovcraft.client.renderer.AirdropEntityRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(HitZoneDebugRenderer::render);

		ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
			if (stack == null || stack.isEmpty()) {
				return;
			}
			if (AmmoTier.isTaczAmmoStack(stack)) {
				AmmoTier tier = AmmoTier.getFromAmmoStack(stack);
				if (!lines.isEmpty()) {
					lines.set(0, lines.get(0).copy().append(" ")
						.append(Text.literal("[" + tier.getBadge() + "]").formatted(tier.getColor())));
				}
				String ammoId = AmmoTier.getAmmoId(stack);
				int pen = TaCZBulletHelper.computePenetration(ammoId, tier);
				int idx = Math.min(1, lines.size());
				lines.add(idx++, Text.translatable("tooltip.tarkovcraft.ammo_tier",
					Text.translatable(tier.getTranslationKey())).formatted(tier.getColor()));
				lines.add(idx++, Text.translatable("tooltip.tarkovcraft.ammo_pen",
					pen, AmmoTier.formatArmorClassTarget(pen)).formatted(pen >= 35 ? Formatting.YELLOW : Formatting.GRAY));
				lines.add(idx++, Text.translatable("tooltip.tarkovcraft.ammo_dmg_wear",
					String.format(Locale.ROOT, "%.2f", tier.getDamageMultiplier()),
					String.format(Locale.ROOT, "%.2f", tier.getArmorWearMultiplier())).formatted(Formatting.GRAY));
				if (tier == AmmoTier.FMJ) {
					lines.add(idx, Text.translatable("tooltip.tarkovcraft.ammo_upgrade_hint").formatted(Formatting.DARK_GRAY));
				}
				return;
			}
			if (AmmoTier.isTaczGunStack(stack)) {
				AmmoTier loaded = AmmoTier.getLoadedTierFromGun(stack);
				int idx = Math.min(1, lines.size());
				lines.add(idx, Text.translatable("tooltip.tarkovcraft.gun_loaded_tier", loaded.getBadge())
					.formatted(loaded.getColor()));
				return;
			}
			if (!stack.isFood()) {
				return;
			}
			FoodComponent fc = stack.getItem().getFoodComponent();
			if (fc == null) {
				return;
			}
			int hunger = fc.getHunger();
			float saturation = hunger * fc.getSaturationModifier() * 2.0f;
			String satStr = Math.abs(saturation - Math.round(saturation)) < 0.05f
				? String.valueOf(Math.round(saturation))
				: String.format(Locale.ROOT, "%.1f", saturation);
			int insertIdx = Math.min(1, lines.size());
			lines.add(insertIdx, Text.translatable("tooltip.tarkovcraft.food_stats", hunger, satStr)
				.formatted(Formatting.GREEN));
		});

		toggleMeleeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.tarkovcraft.toggle_melee",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_GRAVE_ACCENT,
			"category.tarkovcraft"
		));

		interactKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.tarkovcraft.interact",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_F,
			"category.tarkovcraft"
		));

		toggleHitboxesKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.tarkovcraft.toggle_hitboxes",
			InputUtil.Type.KEYSYM,
			GLFW.GLFW_KEY_F8,
			"category.tarkovcraft"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleMeleeKey.wasPressed()) {
				if (client.player != null && !client.player.isCreative()) {
					TarkovNetworking.sendToggleMelee();
				}
			}
			while (interactKey.wasPressed()) {
				if (client.player != null && client.currentScreen == null) {
					TarkovNetworking.sendInteract((byte) WorldInteractState.getSelectedAction());
				}
			}
			while (toggleHitboxesKey.wasPressed()) {
				boolean on = HitZoneDebugRenderer.toggle();
				if (client.player != null) {
					client.player.sendMessage(Text.translatable(
						on ? "message.tarkovcraft.hitboxes_on" : "message.tarkovcraft.hitboxes_off"
					), true);
				}
			}
		});
	}

	public static String getShortKeyLabel(KeyBinding key) {
		if (key == null || key.isUnbound()) {
			return "-";
		}
		String raw = key.getBoundKeyLocalizedText().getString().trim();
		String lower = raw.toLowerCase();
		if (lower.contains("кнопка ") || lower.contains("button ")) {
			String digits = raw.replaceAll("[^0-9]", "");
			if (!digits.isEmpty()) {
				return "M" + digits;
			}
		}
		if (lower.equals("left button") || lower.equals("лкм")) return "LMB";
		if (lower.equals("right button") || lower.equals("пкм")) return "RMB";
		if (lower.equals("middle button") || lower.equals("скм")) return "MMB";
		if (raw.length() > 4) {
			return raw.substring(0, 3).toUpperCase();
		}
		return raw.toUpperCase();
	}
}
