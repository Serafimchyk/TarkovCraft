package com.tarkovcraft.client.screen;

import com.tarkovcraft.client.ClientLoadoutCache;
import com.tarkovcraft.client.hud.BodyHealthHudHelper;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.DamageReport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Escape-from-Tarkov-style KIA Death Screen:
 * Displays killer, weapon/ammo, exact fatal HitZone (e.g. "Голова — глаза (Ваншот!)"),
 * shot distance, body-part silhouette showing final damage state, and recent hit log.
 */
public class TarkovDeathScreen extends Screen {
	private int ticksSinceDeath = 0;
	private ButtonWidget respawnButton;
	private ButtonWidget titleButton;

	public TarkovDeathScreen() {
		super(Text.translatable("screen.tarkovcraft.kia_title"));
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	protected void init() {
		super.init();
		int btnY = Math.min(this.height - 32, (this.height + 210) / 2);
		respawnButton = this.addDrawableChild(ButtonWidget.builder(
			Text.translatable("screen.tarkovcraft.respawn"),
			btn -> {
				if (this.client != null && this.client.player != null) {
					this.client.player.requestRespawn();
					this.client.setScreen(null);
				}
			}
		).dimensions(this.width / 2 - 125, btnY, 120, 20).build());

		titleButton = this.addDrawableChild(ButtonWidget.builder(
			Text.translatable("deathScreen.titleScreen"),
			btn -> {
				if (this.client != null && this.client.world != null) {
					this.client.world.disconnect();
					this.client.disconnect();
					this.client.setScreen(new TitleScreen());
				}
			}
		).dimensions(this.width / 2 + 5, btnY, 120, 20).build());

		respawnButton.active = false;
	}

	@Override
	public void tick() {
		super.tick();
		ticksSinceDeath++;
		if (ticksSinceDeath >= 15 && respawnButton != null) {
			respawnButton.active = true;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// Deep crimson-charcoal Tarkov KIA vignette
		context.fillGradient(0, 0, this.width, this.height, 0xE6140909, 0xF20A0C0E);

		BodyPartHealth health = ClientLoadoutCache.getBodyHealth();
		DamageReport fatal = health.getLastFatalReport();

		int panelW = Math.min(this.width - 24, 430);
		int panelH = 216;
		int px = (this.width - panelW) / 2;
		int py = (this.height - panelH) / 2 - 12;

		context.fill(px, py, px + panelW, py + panelH, 0xE6111417);
		context.drawBorder(px, py, panelW, panelH, 0xFF7A2424);

		// Crimson KIA Header Bar
		context.fill(px + 1, py + 1, px + panelW - 1, py + 24, 0xFF3A1212);
		context.fill(px + 1, py + 24, px + panelW - 1, py + 25, 0xFF9E2B2B);
		context.drawCenteredTextWithShadow(textRenderer,
			Text.translatable("screen.tarkovcraft.kia_header"),
			px + panelW / 2, py + 8, 0xFFFF5E5E);

		// Left Panel: Final Body Silhouette
		int leftW = 148;
		int leftX = px + 8;
		int contentY = py + 32;
		int contentH = panelH - 40;
		context.fill(leftX, contentY, leftX + leftW, contentY + contentH, 0xCC161A1E);
		context.drawBorder(leftX, contentY, leftW, contentH, 0xFF2D333A);

		int silCx = leftX + leftW / 2;
		int silTop = contentY + 12;
		BodyHealthHudHelper.drawBodyPartRect(context, silCx - 10, silTop, 20, 20, health.getRatio(BodyPart.HEAD));
		BodyHealthHudHelper.drawBodyPartRect(context, silCx - 15, silTop + 24, 30, 28, health.getRatio(BodyPart.THORAX));
		BodyHealthHudHelper.drawBodyPartRect(context, silCx - 13, silTop + 54, 26, 18, health.getRatio(BodyPart.STOMACH));
		BodyHealthHudHelper.drawBodyPartRect(context, silCx - 28, silTop + 24, 11, 42, health.getRatio(BodyPart.LEFT_ARM));
		BodyHealthHudHelper.drawBodyPartRect(context, silCx + 17, silTop + 24, 11, 42, health.getRatio(BodyPart.RIGHT_ARM));
		BodyHealthHudHelper.drawBodyPartRect(context, silCx - 14, silTop + 74, 12, 46, health.getRatio(BodyPart.LEFT_LEG));
		BodyHealthHudHelper.drawBodyPartRect(context, silCx + 2, silTop + 74, 12, 46, health.getRatio(BodyPart.RIGHT_LEG));

		String totalStr = Math.round(health.getTotalHp()) + " / " + (int) BodyPart.TOTAL_MAX_HP + " HP";
		context.drawCenteredTextWithShadow(textRenderer, totalStr, silCx, contentY + contentH - 16, 0xFFCF5555);

		// Right Panel: Cause of Death + Hit Log
		int rightX = leftX + leftW + 8;
		int rightW = panelW - leftW - 24;
		context.fill(rightX, contentY, rightX + rightW, contentY + contentH, 0xCC161A1E);
		context.drawBorder(rightX, contentY, rightW, contentH, 0xFF2D333A);

		int ry = contentY + 8;
		if (fatal != null) {
			String zoneName = Text.translatable(fatal.hitZone().getTranslationKey()).getString();
			if (fatal.headEyesOneshot()) {
				zoneName += " (" + Text.translatable("screen.tarkovcraft.oneshot").getString() + ")";
			} else if (fatal.overflowedToThorax()) {
				zoneName += " -> " + Text.translatable(BodyPart.THORAX.getTranslationKey()).getString();
			}

			drawInfoRow(context, rightX + 8, ry, Text.translatable("screen.tarkovcraft.kia_killer").getString(),
				fatal.killerName().isEmpty() ? "Unknown" : fatal.killerName(), 0xFFEFEFEF);
			ry += 18;

			drawInfoRow(context, rightX + 8, ry, Text.translatable("screen.tarkovcraft.kia_zone").getString(),
				zoneName, 0xFFFF5E5E);
			ry += 18;

			String weaponLine = fatal.weaponName().isEmpty() ? "-" : fatal.weaponName();
			if (!fatal.ammoName().isEmpty()) {
				weaponLine += " // " + fatal.ammoName();
			}
			drawInfoRow(context, rightX + 8, ry, Text.translatable("screen.tarkovcraft.kia_weapon").getString(),
				weaponLine, 0xFFD4B46A);
			ry += 18;

			String distLine = fatal.distanceMeters() > 0.1f ? String.format("%.1f m", fatal.distanceMeters()) : "-";
			drawInfoRow(context, rightX + 8, ry, Text.translatable("screen.tarkovcraft.kia_distance").getString(),
				distLine, 0xFFB8C0C8);
			ry += 22;
		} else {
			context.drawTextWithShadow(textRenderer, Text.translatable("screen.tarkovcraft.kia_unknown"), rightX + 8, ry, 0xFFB8C0C8);
			ry += 20;
		}

		// Recent Hits Log
		context.fill(rightX + 6, ry, rightX + rightW - 6, ry + 1, 0xFF2D333A);
		ry += 6;
		drawTiny(context, Text.translatable("health.tarkovcraft.recent_hits").getString(), rightX + 8, ry, 0xFFD4B46A);
		ry += 10;

		List<DamageReport> hits = health.getRecentHits();
		int shown = Math.min(5, hits.size());
		for (int i = 0; i < shown; i++) {
			DamageReport r = hits.get(i);
			String zt = Text.translatable(r.hitZone().getTranslationKey()).getString();
			String armorTag = r.ricocheted() ? " [RICOCHET]" : (r.armorBlocked() ? " [ARMOR]" : "");
			String line = String.format("#%d  %s  -%.0f HP%s%s",
				i + 1, zt, r.finalDamage(),
				armorTag,
				r.overflowedToThorax() ? " -> THORAX" : ""
			);
			drawTiny(context, line, rightX + 8, ry, i == 0 ? 0xFFFF7A7A : 0xFFB8C0C8);
			ry += 10;
		}

		super.render(context, mouseX, mouseY, delta);
	}

	private void drawInfoRow(DrawContext context, int x, int y, String label, String value, int valueColor) {
		drawTiny(context, label, x, y, 0xFF88929C);
		context.drawTextWithShadow(textRenderer, value, x, y + 7, valueColor);
	}

	private void drawTiny(DrawContext context, String text, int x, int y, int col) {
		context.getMatrices().push();
		context.getMatrices().scale(0.72f, 0.72f, 1.0f);
		context.drawTextWithShadow(textRenderer, text, (int) (x / 0.72f), (int) (y / 0.72f), col);
		context.getMatrices().pop();
	}
}
