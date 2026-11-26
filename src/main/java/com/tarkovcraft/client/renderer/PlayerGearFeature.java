package com.tarkovcraft.client.renderer;

import com.tarkovcraft.client.model.GearModel;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.network.EquipmentAppearance;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerGearFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	private static final Map<UUID, EquipmentAppearance> REMOTE_APPEARANCES = new HashMap<>();

	private PlayerGearFeature(PlayerEntityRenderer renderer) {
		super(renderer);
	}

	public static void register() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer playerRenderer) {
				helper.register(new PlayerGearFeature(playerRenderer));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(EquipmentAppearance.CHANNEL, (client, handler, buf, sender) -> {
			UUID playerId = buf.readUuid();
			EquipmentAppearance appearance = EquipmentAppearance.read(buf);
			client.execute(() -> {
				if (client.getNetworkHandler() != handler) return;
				if (appearance.isEmpty()) {
					REMOTE_APPEARANCES.remove(playerId);
				} else {
					REMOTE_APPEARANCES.put(playerId, appearance);
				}
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> REMOTE_APPEARANCES.clear());
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, AbstractClientPlayerEntity player,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (player.isInvisible() || player.isSpectator()) return;
		EquipmentAppearance appearance = REMOTE_APPEARANCES.get(player.getUuid());
		if (player == MinecraftClient.getInstance().player && player instanceof PlayerLoadoutHolder holder) {
			appearance = EquipmentAppearance.capture(holder.tarkovcraft$getLoadout());
		}
		if (appearance == null) return;

		for (Item item : appearance.items()) {
			GearModel model = GearAppearance.worn(item);
			if (model != null) {
				model.renderWorn(matrices, vertices, light, getContextModel(), item);
			}
		}
	}
}
