package com.tarkovcraft.client.renderer;

import com.tarkovcraft.entity.CorpseEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * Renders CorpseEntity as a proper lying humanoid character model on the ground
 * (using Minecraft's PlayerEntityModel) with a single clean nameplate (no overlapping text!).
 */
public class CorpseEntityRenderer extends EntityRenderer<CorpseEntity> {
	private static final Identifier SKIN_TEXTURE = new Identifier("minecraft", "textures/entity/player/wide/steve.png");
	private final PlayerEntityModel<net.minecraft.entity.LivingEntity> model;

	public CorpseEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.55f;
		this.model = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false);
		this.model.child = false;
	}

	@Override
	public void render(CorpseEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		matrices.push();

		// Position & lay the biped flat on the ground facing entity's yaw
		matrices.translate(0.0, 0.14, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw() + 180.0f));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
		matrices.scale(-0.9375f, -0.9375f, 0.9375f);
		matrices.translate(0.0, -0.75, 0.0);

		// Slight natural fallen pose for arms and legs
		model.head.setAngles(0.05f, 0.18f, 0.0f);
		model.hat.copyTransform(model.head);
		model.body.setAngles(0.0f, 0.0f, 0.0f);
		model.jacket.copyTransform(model.body);
		model.leftArm.setAngles(-0.15f, 0.0f, -0.35f);
		model.leftSleeve.copyTransform(model.leftArm);
		model.rightArm.setAngles(0.10f, 0.0f, 0.42f);
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftLeg.setAngles(0.0f, 0.0f, -0.12f);
		model.leftPants.copyTransform(model.leftLeg);
		model.rightLeg.setAngles(0.0f, 0.0f, 0.14f);
		model.rightPants.copyTransform(model.rightLeg);

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(SKIN_TEXTURE));
		model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0.85f, 0.85f, 0.85f, 1.0f);

		matrices.pop();

		// Render ONLY ONE clean nameplate (do NOT call super.render which rendered a duplicate!)
		if (this.hasLabel(entity)) {
			this.renderLabelIfPresent(entity, Text.literal(entity.getOwnerName()), matrices, vertexConsumers, light);
		}
	}

	@Override
	protected boolean hasLabel(CorpseEntity entity) {
		return this.dispatcher.getSquaredDistanceToCamera(entity) < 36.0;
	}

	@Override
	public Identifier getTexture(CorpseEntity entity) {
		return SKIN_TEXTURE;
	}
}
