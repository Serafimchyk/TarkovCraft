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
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class TarkovEquipmentFeatureRenderer<T extends LivingEntity>
		extends FeatureRenderer<T, PlayerEntityModel<T>> {
	private static final Map<UUID, EquipmentAppearance> REMOTE_APPEARANCES = new HashMap<>();

	private final Function<T, List<Item>> visibleGear;

	public TarkovEquipmentFeatureRenderer(FeatureRendererContext<T, PlayerEntityModel<T>> renderer,
			Function<T, List<Item>> visibleGear) {
		super(renderer);
		this.visibleGear = visibleGear;
	}

	public static void register() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer playerRenderer) {
				helper.register(new TarkovEquipmentFeatureRenderer<>(playerRenderer,
					TarkovEquipmentFeatureRenderer::playerGear));
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

	private static List<Item> playerGear(AbstractClientPlayerEntity player) {
		if (player.isSpectator()) return List.of();
		EquipmentAppearance appearance = REMOTE_APPEARANCES.get(player.getUuid());
		if (player == MinecraftClient.getInstance().player && player instanceof PlayerLoadoutHolder holder) {
			appearance = EquipmentAppearance.capture(holder.tarkovcraft$getLoadout());
		}
		return appearance == null ? List.of() : appearance.items();
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light, T entity,
			float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		if (entity.isInvisible()) return;
		renderGear(matrices, vertices, light, getContextModel(), visibleGear.apply(entity));
	}

	static void renderGear(MatrixStack matrices, VertexConsumerProvider vertices, int light,
			BipedEntityModel<?> parent, List<Item> gear) {
		for (Item item : gear) {
			GearModel model = GearAppearance.worn(item);
			if (model != null) model.renderWorn(matrices, vertices, light, parent, item);
		}
	}
}
