package com.tarkovcraft.mixin;

import com.tarkovcraft.client.model.GearModel;
import com.tarkovcraft.client.renderer.GearAppearance;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	@Inject(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$renderGroundedGear(
		ItemEntity entity, float yaw, float tickDelta,
		MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
		CallbackInfo ci
	) {
		ItemStack stack = entity.getStack();
		GearModel model = GearAppearance.ground(stack);
		if (model == null) {
			return;
		}

		ci.cancel();
		matrices.push();
		// Stable orientation while the entity is resting or moving.
		float groundYaw = (entity.getUuid().hashCode() & 0xFF) * (360.0f / 256.0f);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(groundYaw));
		model.renderOnGround(matrices, vertexConsumers, light, stack.getItem());
		matrices.pop();
	}
}
