package com.tarkovcraft.client.renderer;

import com.tarkovcraft.entity.AirdropEntity;
import com.tarkovcraft.registry.ModBlocks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

@Environment(EnvType.CLIENT)
public class AirdropEntityRenderer extends EntityRenderer<AirdropEntity> {
	private final BlockRenderManager blockRenderManager;

	public AirdropEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.blockRenderManager = ctx.getBlockRenderManager();
		this.shadowRadius = 0.6f;
	}

	@Override
	public void render(AirdropEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);

		float sway = (float) Math.sin((entity.age + tickDelta) * 0.08f) * 3.5f;

		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sway));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sway * 0.5f));

		// Crate body centered around origin
		matrices.push();
		matrices.translate(-0.5, 0.0, -0.5);
		blockRenderManager.renderBlockAsEntity(
			ModBlocks.AIRDROP_CRATE.getDefaultState(),
			matrices,
			vertexConsumers,
			light,
			OverlayTexture.DEFAULT_UV
		);
		matrices.pop();

		// 4 shroud cords connecting crate corners to canopy
		BlockState cordState = Blocks.GRAY_CONCRETE.getDefaultState();
		float[][] corners = {
			{-0.42f, -0.42f, 12f, -12f},
			{0.42f, -0.42f, 12f, 12f},
			{-0.42f, 0.42f, -12f, -12f},
			{0.42f, 0.42f, -12f, 12f}
		};
		for (float[] c : corners) {
			matrices.push();
			matrices.translate(c[0], 0.92f, c[1]);
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(c[2]));
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(c[3]));
			matrices.scale(0.05f, 1.55f, 0.05f);
			matrices.translate(-0.5, 0.0, -0.5);
			blockRenderManager.renderBlockAsEntity(cordState, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}

		// Parachute canopy: 3 striped panels (red / white / red)
		renderCanopyPanel(matrices, vertexConsumers, light, Blocks.RED_WOOL.getDefaultState(), -0.82f, 2.38f, 0.82f, 0.22f, 2.4f, -10f);
		renderCanopyPanel(matrices, vertexConsumers, light, Blocks.WHITE_WOOL.getDefaultState(), 0.0f, 2.48f, 0.84f, 0.24f, 2.45f, 0f);
		renderCanopyPanel(matrices, vertexConsumers, light, Blocks.RED_WOOL.getDefaultState(), 0.82f, 2.38f, 0.82f, 0.22f, 2.4f, 10f);

		matrices.pop();
	}

	private void renderCanopyPanel(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
			BlockState state, float xOffset, float yOffset, float width, float height, float depth, float tiltDeg) {
		matrices.push();
		matrices.translate(xOffset, yOffset, 0.0f);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-tiltDeg));
		matrices.scale(width, height, depth);
		matrices.translate(-0.5, 0.0, -0.5);
		blockRenderManager.renderBlockAsEntity(state, matrices, vertexConsumers, light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
	}

	@Override
	public Identifier getTexture(AirdropEntity entity) {
		return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
	}
}
