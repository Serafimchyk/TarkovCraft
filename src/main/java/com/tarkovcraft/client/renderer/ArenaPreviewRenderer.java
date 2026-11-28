package com.tarkovcraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tarkovcraft.client.ClientArenaEditor;
import com.tarkovcraft.client.ClientRaidExtracts;
import com.tarkovcraft.network.ArenaAdminPackets.Section;
import com.tarkovcraft.network.RaidExtractPackets.Exit;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;

public final class ArenaPreviewRenderer {
	private static final int FIRST = 0xFF7CFC77;
	private static final int DRAFT = 0xFF62E7FF;
	private static final double MIN_TOP_Y = 150;
	private static final double ABOVE_VERTEX = 2.5;
	private static final double BASE_OFFSET = 0.06;
	private static final RenderLayer AIM_LINES = AimLayer.create();
	private static final VertexConsumerProvider.Immediate AIM_BUFFERS = VertexConsumerProvider.immediate(new BufferBuilder(256));

	private ArenaPreviewRenderer() { }

	public static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || context.consumers() == null) return;
		var mesh = new Mesh(context, context.consumers());
		if (client.player.isAlive() && ClientRaidExtracts.inRaidWorld(client.world)) {
			for (var zone : ClientRaidExtracts.zones()) {
				mesh.extract(zone.exit(), zone.signalOn() ? ClientRaidExtracts.GREEN : ClientRaidExtracts.AMBER, false);
			}
		}
		if (!client.player.hasPermissionLevel(2)) return;
		var shown = new HashSet<Exit>();
		var preview = ClientArenaEditor.preview();
		if (preview != null && preview.details().key().dimension().equals(context.world().getRegistryKey().getValue())) {
			List<BlockPos> border = preview.points().stream().filter(point -> point.section() == Section.BOUNDARY)
				.map(point -> point.pos()).toList();
			mesh.outline(border, preview.details().boundaryReady(), ClientArenaEditor.color(Section.BOUNDARY), false);
			for (var point : preview.points()) {
				if (point.section() == Section.BOUNDARY) continue;
				if (point.section() == Section.EXTRACT) {
					var exit = new Exit(point.name(), point.pos(), point.loud(), point.radius());
					mesh.extract(exit, ClientRaidExtracts.GREEN, true);
					shown.add(exit);
					continue;
				}
				Vec3d base = Vec3d.ofBottomCenter(point.pos()).add(0, 0.1, 0);
				if (base.squaredDistanceTo(mesh.camera) > 96 * 96) continue;
				int color = ClientArenaEditor.color(point.section());
				mesh.marker(base, color, 0.35, 1.65);
				mesh.label(ClientArenaEditor.pointLabel(point), base.add(0, 1.9, 0), color);
			}
		}
		for (var exit : ClientRaidExtracts.recent(client.world)) {
			if (!shown.contains(exit)) mesh.extract(exit, ClientRaidExtracts.GREEN, true);
		}
		var key = ClientArenaEditor.heldKey(client);
		if (key == null) return;
		List<BlockPos> draft = ClientArenaEditor.draft(key);
		if (draft.isEmpty()) return;
		mesh.outline(draft, false, DRAFT, true);
		Vec3d first = base(draft.get(0));
		mesh.marker(first, FIRST, 0.65, 3.05);
		if (first.squaredDistanceTo(mesh.camera) <= 96 * 96) {
			mesh.label(draft.size() >= 3 ? "#1 • ПКМ: замкнуть границу" : "#1 • начало границы", first.add(0, 3.25, 0), FIRST);
		}
	}

	public static void renderAim(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.currentScreen != null || !(client.crosshairTarget instanceof BlockHitResult hit)
			|| hit.getType() != HitResult.Type.BLOCK) return;
		var key = ClientArenaEditor.heldKey(client);
		if (key == null) return;
		List<BlockPos> draft = ClientArenaEditor.draft(key);
		if (draft.isEmpty()) return;

		boolean closing = draft.size() >= 3 && draft.get(0).getSquaredDistance(hit.getBlockPos()) <= 2.25;
		Vec3d start = base(draft.get(draft.size() - 1));
		Vec3d end = base(closing ? draft.get(0) : hit.getBlockPos());
		double topY = Math.max(outlineTopY(draft), end.y + ABOVE_VERTEX);
		double guideY = Math.min(topY, Math.max(Math.max(start.y, end.y), context.camera().getPos().y - 0.35));
		int color = closing ? FIRST : DRAFT;
		int faint = (color & 0xFFFFFF) | 0xA0000000;
		var mesh = new Mesh(context, AIM_BUFFERS);
		mesh.dashed(start, end, color);
		if (guideY > Math.min(start.y, end.y) + 0.3 && guideY < topY - 0.3) {
			mesh.dashed(atY(start, guideY), atY(end, guideY), color);
		}
		mesh.dashed(atY(start, topY), atY(end, topY), faint);
		mesh.line(end, atY(end, topY), faint, AIM_LINES);
		// Minecraft's ALWAYS_DEPTH_TEST phase leaves the current depth function untouched.
		RenderSystem.depthFunc(GL11.GL_ALWAYS);
		try {
			AIM_BUFFERS.draw();
		} finally {
			RenderSystem.depthFunc(GL11.GL_LEQUAL);
		}
	}

	private static Vec3d base(BlockPos point) {
		return new Vec3d(point.getX(), point.getY() + BASE_OFFSET, point.getZ());
	}

	private static Vec3d atY(Vec3d point, double y) {
		return new Vec3d(point.x, y, point.z);
	}

	private static double outlineTopY(List<BlockPos> points) {
		int highest = points.stream().mapToInt(BlockPos::getY).max().orElse(0);
		return Math.max(MIN_TOP_Y, highest + BASE_OFFSET + ABOVE_VERTEX);
	}

	private abstract static class AimLayer extends RenderLayer {
		private AimLayer() {
			super("tarkovcraft_arena_aim_lines", VertexFormats.LINES, VertexFormat.DrawMode.LINES, 256,
				false, false, () -> { }, () -> { });
		}

		private static RenderLayer create() {
			return of("tarkovcraft_arena_aim_lines", VertexFormats.LINES, VertexFormat.DrawMode.LINES, 256,
				MultiPhaseParameters.builder()
					.program(LINES_PROGRAM)
					.lineWidth(new LineWidth(OptionalDouble.of(3)))
					.transparency(TRANSLUCENT_TRANSPARENCY)
					.depthTest(ALWAYS_DEPTH_TEST)
					.writeMaskState(COLOR_MASK)
					.cull(DISABLE_CULLING)
					.build(false));
		}
	}

	private static final class Mesh {
		private final WorldRenderContext context;
		private final VertexConsumerProvider consumers;
		private final Vec3d camera;

		private Mesh(WorldRenderContext context, VertexConsumerProvider consumers) {
			this.context = context;
			this.consumers = consumers;
			camera = context.camera().getPos();
		}

		private void outline(List<BlockPos> points, boolean closed, int color, boolean walls) {
			double topY = outlineTopY(points);
			for (int i = 0; i < points.size(); i++) {
				Vec3d a = base(points.get(i));
				Vec3d top = atY(a, topY);
				line(a, top, color);
				if (i + 1 == points.size() && !closed) continue;
				Vec3d b = base(points.get((i + 1) % points.size()));
				Vec3d bTop = atY(b, topY);
				line(a, b, color);
				line(top, bTop, color);
				double guideY = camera.y - 0.35;
				if (guideY > Math.max(a.y, b.y) + 0.3 && guideY < topY - 0.3) {
					line(atY(a, guideY), atY(b, guideY), color);
				}
				if (walls) {
					var quads = consumers.getBuffer(RenderLayer.getDebugQuads());
					int translucent = (color & 0xFFFFFF) | 0x19000000;
					vertex(quads, a, translucent).next();
					vertex(quads, b, translucent).next();
					vertex(quads, bTop, translucent).next();
					vertex(quads, top, translucent).next();
					vertex(quads, top, translucent).next();
					vertex(quads, bTop, translucent).next();
					vertex(quads, b, translucent).next();
					vertex(quads, a, translucent).next();
				}
			}
		}

		private void marker(Vec3d base, int color, double radius, double height) {
			Vec3d center = base.add(0, height, 0);
			line(base, center, color);
			for (int i = 0; i < 16; i++) {
				double a = i * Math.PI / 8;
				double b = (i + 1) * Math.PI / 8;
				line(center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius),
					center.add(Math.cos(b) * radius, 0, Math.sin(b) * radius), color);
			}
		}

		private void extract(Exit exit, int color, boolean editor) {
			Vec3d bottom = Vec3d.ofBottomCenter(exit.pos()).add(0, 0.075, 0);
			if (bottom.squaredDistanceTo(camera) > 96 * 96) return;
			var top = bottom.add(0, 1.65, 0);
			int segments = Math.max(40, (int) Math.ceil(exit.radius() * 5));
			for (int i = 0; i < segments; i++) {
				double a = Math.PI * 2 * i / segments;
				double b = Math.PI * 2 * (i + 1) / segments;
				Vec3d offsetA = new Vec3d(Math.cos(a) * exit.radius(), 0, Math.sin(a) * exit.radius());
				Vec3d offsetB = new Vec3d(Math.cos(b) * exit.radius(), 0, Math.sin(b) * exit.radius());
				line(bottom.add(offsetA), bottom.add(offsetB), color);
				if (editor) {
					line(top.add(offsetA), top.add(offsetB), color);
					if (i % (segments / 8) == 0) line(bottom.add(offsetA), top.add(offsetA), color);
				}
			}
			if (editor) {
				String label = Text.translatable("arena.tarkovcraft.extract.label", exit.name(),
					exit.loud() ? "loud" : "quiet").getString();
				label(label, top.add(0, 0.55, 0), color);
			}
		}

		private void label(String text, Vec3d point, int color) {
			DebugRenderer.drawString(context.matrixStack(), consumers, text,
				point.x, point.y, point.z, color, 0.025f, true, 0, true);
		}

		private void line(Vec3d a, Vec3d b, int color) {
			line(a, b, color, RenderLayer.getLines());
		}

		private void dashed(Vec3d a, Vec3d b, int color) {
			int segments = Math.max(1, Math.min(255, (int) Math.ceil(a.distanceTo(b) * 3) | 1));
			for (int i = 0; i < segments; i += 2) {
				line(a.lerp(b, (double) i / segments), a.lerp(b, (i + 1.0) / segments), color, AIM_LINES);
			}
		}

		private void line(Vec3d a, Vec3d b, int color, RenderLayer layer) {
			if (a.squaredDistanceTo(b) < 1.0e-8) return;
			Vec3d direction = b.subtract(a).normalize();
			var lines = consumers.getBuffer(layer);
			var normal = context.matrixStack().peek().getNormalMatrix();
			vertex(lines, a, color).normal(normal, (float) direction.x, (float) direction.y, (float) direction.z).next();
			vertex(lines, b, color).normal(normal, (float) direction.x, (float) direction.y, (float) direction.z).next();
		}

		private VertexConsumer vertex(VertexConsumer consumer, Vec3d point, int color) {
			Vec3d relative = point.subtract(camera);
			return consumer.vertex(context.matrixStack().peek().getPositionMatrix(),
				(float) relative.x, (float) relative.y, (float) relative.z).color(color);
		}
	}
}
