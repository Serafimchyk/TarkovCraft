package com.tarkovcraft.client.renderer;

import com.mojang.authlib.GameProfile;
import com.tarkovcraft.entity.SleeperPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.WeakHashMap;

public final class SleeperPlayerRenderer extends LivingEntityRenderer<SleeperPlayerEntity, PlayerEntityModel<SleeperPlayerEntity>> {
	private final PlayerEntityModel<SleeperPlayerEntity> wideModel;
	private final PlayerEntityModel<SleeperPlayerEntity> slimModel;
	private final Map<SleeperPlayerEntity, PlayerListEntry> skins = new WeakHashMap<>();

	public SleeperPlayerRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
		wideModel = model;
		slimModel = new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER_SLIM), true);
		addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
		addFeature(new TarkovEquipmentFeatureRenderer<>(this, SleeperPlayerEntity::getVisibleGear));
	}

	@Override
	public void render(SleeperPlayerEntity sleeper, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertices, int light) {
		model = "slim".equals(skin(sleeper).getModel()) ? slimModel : wideModel;
		super.render(sleeper, yaw, tickDelta, matrices, vertices, light);
	}

	@Override
	protected void scale(SleeperPlayerEntity sleeper, MatrixStack matrices, float tickDelta) {
		matrices.scale(0.9375f, 0.9375f, 0.9375f);
	}

	@Override
	public Identifier getTexture(SleeperPlayerEntity sleeper) {
		return skin(sleeper).getSkinTexture();
	}

	private PlayerListEntry skin(SleeperPlayerEntity sleeper) {
		GameProfile profile = sleeper.getOwnerProfile();
		PlayerListEntry cached = skins.get(sleeper);
		if (cached == null || cached.getProfile() != profile) {
			cached = new PlayerListEntry(profile, false);
			skins.put(sleeper, cached);
		}
		return cached;
	}
}
