package com.tarkovcraft.client.renderer;

import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.health.HitZoneBoxes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders live 3D OBB hitboxes from HitZoneBoxes when F3+B OR F8 is active.
 */
@Environment(EnvType.CLIENT)
public final class HitZoneDebugRenderer {
	private static final double MAX_RENDER_DIST_SQ = 64.0 * 64.0;
	private static boolean userToggled = false;

	private HitZoneDebugRenderer() {
	}

	public static boolean toggle() {
		userToggled = !userToggled;
		return userToggled;
	}

	public static boolean isUserToggled() {
		return userToggled;
	}

	public static void render(WorldRenderContext ctx) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || ctx.world() == null || client.player == null) {
			return;
		}
		boolean enabled = userToggled || client.getEntityRenderDispatcher().shouldRenderHitboxes();
		if (!enabled) {
			return;
		}
		VertexConsumerProvider consumers = ctx.consumers();
		if (consumers == null) {
			return;
		}

		MatrixStack matrices = ctx.matrixStack();
		Vec3d camPos = ctx.camera().getPos();
		float tickDelta = ctx.tickDelta();
		boolean firstPerson = client.options.getPerspective().isFirstPerson();

		VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
		matrices.push();
		matrices.translate(-camPos.x, -camPos.y, -camPos.z);
		Matrix4f posMat = matrices.peek().getPositionMatrix();
		Matrix3f normMat = matrices.peek().getNormalMatrix();

		for (Entity e : ctx.world().getEntities()) {
			if (!(e instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			if (!shouldRenderFor(living, client.player, firstPerson)) {
				continue;
			}
			if (living.squaredDistanceTo(camPos.x, camPos.y, camPos.z) > MAX_RENDER_DIST_SQ) {
				continue;
			}

			Vec3d lerpedPos = living.getLerpedPos(tickDelta);
			float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, living.prevBodyYaw, living.bodyYaw);
			float headYaw = MathHelper.lerpAngleDegrees(tickDelta, living.prevHeadYaw, living.headYaw);

			List<HitZoneBoxes.ZoneBox> boxes = HitZoneBoxes.compute(
				lerpedPos, living.getHeight(), living.getWidth(), bodyYaw, headYaw
			);
			for (HitZoneBoxes.ZoneBox box : boxes) {
				float[] rgba = colorForZone(box.zone());
				Vec3d[] c = box.getWorldCorners();
				for (int[] edge : HitZoneBoxes.EDGE_INDICES) {
					drawEdge(lines, posMat, normMat, c[edge[0]], c[edge[1]], rgba[0], rgba[1], rgba[2], rgba[3]);
				}
			}
		}

		matrices.pop();
	}

	private static boolean shouldRenderFor(LivingEntity living, PlayerEntity localPlayer, boolean firstPerson) {
		if (living instanceof PlayerEntity) {
			return !(living == localPlayer && firstPerson);
		}
		if (living instanceof ArmorStandEntity) {
			return true;
		}
		if (living instanceof MobEntity) {
			return living.getHeight() >= 1.35f && living.getWidth() <= 0.85f;
		}
		return false;
	}

	private static void drawEdge(
		VertexConsumer vc, Matrix4f posMat, Matrix3f normMat,
		Vec3d p0, Vec3d p1, float r, float g, float b, float a
	) {
		float dx = (float) (p1.x - p0.x);
		float dy = (float) (p1.y - p0.y);
		float dz = (float) (p1.z - p0.z);
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float nx = len > 1e-6f ? dx / len : 0.0f;
		float ny = len > 1e-6f ? dy / len : 1.0f;
		float nz = len > 1e-6f ? dz / len : 0.0f;

		vc.vertex(posMat, (float) p0.x, (float) p0.y, (float) p0.z)
			.color(r, g, b, a)
			.normal(normMat, nx, ny, nz)
			.next();
		vc.vertex(posMat, (float) p1.x, (float) p1.y, (float) p1.z)
			.color(r, g, b, a)
			.normal(normMat, nx, ny, nz)
			.next();
	}

	private static float[] colorForZone(HitZone zone) {
		return switch (zone) {
			case HEAD_EYES -> new float[]{1.00f, 0.12f, 0.12f, 1.0f};
			case HEAD_EARS -> new float[]{0.95f, 0.15f, 0.90f, 1.0f};
			case HEAD_JAWS -> new float[]{1.00f, 0.55f, 0.10f, 1.0f};
			case HEAD_TOP -> new float[]{0.25f, 0.78f, 1.00f, 1.0f};
			case NECK -> new float[]{1.00f, 0.92f, 0.15f, 1.0f};
			case THORAX -> new float[]{0.15f, 0.90f, 0.30f, 1.0f};
			case STOMACH -> new float[]{0.62f, 0.96f, 0.22f, 1.0f};
			case ARMPIT_LEFT, ARMPIT_RIGHT -> new float[]{0.65f, 0.25f, 1.00f, 1.0f};
			case LEFT_ARM, RIGHT_ARM -> new float[]{0.20f, 0.48f, 1.00f, 1.0f};
			case LEFT_LEG, RIGHT_LEG -> new float[]{0.12f, 0.88f, 0.82f, 1.0f};
		};
	}
}
