package com.tarkovcraft.mixin;

import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders dropped backpacks, tactical rigs, and armor lying grounded on the floor
 * as 3D gear models instead of tiny floating spinning 2D sprites.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	@Unique
	private static final Identifier GEAR_TEX = new Identifier("minecraft", "textures/block/white_wool.png");

	@Inject(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
		at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$renderGroundedGear(
		ItemEntity entity, float yaw, float tickDelta,
		MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
		CallbackInfo ci
	) {
		ItemStack stack = entity.getStack();
		if (stack.isEmpty()) return;
		Item item = stack.getItem();
		if (!(item instanceof BackpackItem || item instanceof TacticalRigItem || item instanceof TarkovArmorItem)) {
			return;
		}

		ci.cancel();
		matrices.push();
		// Fixed rotation based on entity UUID so it stays still on the ground
		float groundYaw = (entity.getUuid().hashCode() & 0xFF) * (360.0f / 256.0f);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(groundYaw));

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(GEAR_TEX));
		if (item instanceof BackpackItem bp) {
			float scale = Math.min(1.25f, 0.7f + (bp.getGridWidth() * bp.getGridHeight()) * 0.02f);
			// Main backpack body lying flat
			drawBox(matrices, vc, -0.22f * scale, 0.0f, -0.28f * scale, 0.22f * scale, 0.20f * scale, 0.28f * scale, light, 82, 96, 70);
			// Top pouch + straps
			drawBox(matrices, vc, -0.17f * scale, 0.20f * scale, -0.18f * scale, 0.17f * scale, 0.25f * scale, 0.18f * scale, light, 60, 72, 52);
			drawBox(matrices, vc, -0.20f * scale, 0.02f, -0.30f * scale, -0.12f * scale, 0.14f * scale, 0.30f * scale, light, 42, 46, 38);
			drawBox(matrices, vc, 0.12f * scale, 0.02f, -0.30f * scale, 0.20f * scale, 0.14f * scale, 0.30f * scale, light, 42, 46, 38);
		} else if (item instanceof TacticalRigItem) {
			// Chest rig flat with 3 magazine pouches
			drawBox(matrices, vc, -0.26f, 0.0f, -0.20f, 0.26f, 0.09f, 0.20f, light, 88, 82, 68);
			drawBox(matrices, vc, -0.22f, 0.09f, -0.12f, -0.10f, 0.16f, 0.12f, light, 68, 63, 52);
			drawBox(matrices, vc, -0.06f, 0.09f, -0.12f, 0.06f, 0.16f, 0.12f, light, 68, 63, 52);
			drawBox(matrices, vc, 0.10f, 0.09f, -0.12f, 0.22f, 0.16f, 0.12f, light, 68, 63, 52);
		} else if (item instanceof TarkovArmorItem armor) {
			if (armor.getTargetSlot() == EquipmentSlotType.HELMET) {
				drawBox(matrices, vc, -0.18f, 0.0f, -0.18f, 0.18f, 0.16f, 0.18f, light, 76, 90, 62);
				drawBox(matrices, vc, -0.14f, 0.16f, -0.14f, 0.14f, 0.22f, 0.14f, light, 76, 90, 62);
			} else if (armor.getTargetSlot() == EquipmentSlotType.FACE_MASK) {
				drawBox(matrices, vc, -0.15f, 0.0f, -0.15f, 0.15f, 0.06f, 0.15f, light, 45, 48, 52);
			} else {
				// Body armor vest flat
				drawBox(matrices, vc, -0.26f, 0.0f, -0.28f, 0.26f, 0.12f, 0.28f, light, 58, 64, 74);
				drawBox(matrices, vc, -0.18f, 0.12f, -0.20f, 0.18f, 0.15f, 0.20f, light, 44, 49, 58);
			}
		}

		matrices.pop();
	}

	@Unique
	private static void drawBox(
		MatrixStack matrices, VertexConsumer vc,
		float x1, float y1, float z1, float x2, float y2, float z2,
		int light, int r, int g, int b
	) {
		Matrix4f mat = matrices.peek().getPositionMatrix();
		Matrix3f norm = matrices.peek().getNormalMatrix();
		quad(vc, mat, norm, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, 0, 1, 0, light, r, g, b);
		quad(vc, mat, norm, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0, -1, 0, light, r, g, b);
		quad(vc, mat, norm, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, 0, 0, -1, light, r, g, b);
		quad(vc, mat, norm, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, 0, 0, 1, light, r, g, b);
		quad(vc, mat, norm, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, -1, 0, 0, light, r, g, b);
		quad(vc, mat, norm, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, 1, 0, 0, light, r, g, b);
	}

	@Unique
	private static void quad(
		VertexConsumer vc, Matrix4f mat, Matrix3f norm,
		float x1, float y1, float z1, float x2, float y2, float z2,
		float x3, float y3, float z3, float x4, float y4, float z4,
		float nx, float ny, float nz, int light, int r, int g, int b
	) {
		vc.vertex(mat, x1, y1, z1).color(r, g, b, 255).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(norm, nx, ny, nz).next();
		vc.vertex(mat, x2, y2, z2).color(r, g, b, 255).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(norm, nx, ny, nz).next();
		vc.vertex(mat, x3, y3, z3).color(r, g, b, 255).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(norm, nx, ny, nz).next();
		vc.vertex(mat, x4, y4, z4).color(r, g, b, 255).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(norm, nx, ny, nz).next();
	}
}
