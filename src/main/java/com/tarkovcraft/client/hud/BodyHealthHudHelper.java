package com.tarkovcraft.client.hud;

import com.tarkovcraft.client.ClientLoadoutCache;
import com.tarkovcraft.client.screen.TarkovUiTheme;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.DamageReport;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.health.InjuryType;
import com.tarkovcraft.health.MedicalHelper;
import com.tarkovcraft.health.ZonalArmorHelper;
import com.tarkovcraft.item.MedicalType;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.loadout.PlayerLoadout;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import com.tarkovcraft.client.screen.TarkovDeathScreen;
import com.tarkovcraft.client.screen.TarkovInventoryScreen;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders the Tarkov-style body-part health silhouette, injury badges, painkiller timer,
 * effectiveMaxHp surgery limits, armor coverage, and interactive HEALTH tab hitboxes.
 */
public final class BodyHealthHudHelper {
	private static final HitZone[] COVERAGE_ZONES_LEFT = {
		HitZone.HEAD_TOP, HitZone.HEAD_EARS, HitZone.HEAD_EYES, HitZone.HEAD_JAWS
	};
	private static final HitZone[] COVERAGE_ZONES_RIGHT = {
		HitZone.NECK, HitZone.THORAX, HitZone.ARMPIT_LEFT, HitZone.STOMACH
	};

	public record PartHitbox(BodyPart part, int x, int y, int w, int h) {
		public boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	public record QuickHealMed(byte loc, int idx, @Nullable UUID entryId, ItemStack stack) {
	}

	public record QuickHealHitbox(BodyPart part, QuickHealMed med, int x, int y, int w, int h) {
		public boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private BodyHealthHudHelper() {
	}

	public static void drawCompactTopRight(DrawContext context, TextRenderer textRenderer, PlayerEntity player, int screenW) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc != null && (mc.currentScreen instanceof TarkovInventoryScreen || mc.currentScreen instanceof TarkovDeathScreen)) {
			return;
		}
		BodyPartHealth health = ClientLoadoutCache.getBodyHealth();

		int boxW = 48;
		int boxH = 66;
		int x = screenW - boxW - 8;
		int y = 8;

		context.fill(x, y, x + boxW, y + boxH, 0x88101316);
		context.drawBorder(x, y, boxW, boxH, 0x99384048);

		int cx = x + boxW / 2;
		int top = y + 5;

		drawBodyPartRect(context, cx - 4, top, 8, 8, health.getRatio(BodyPart.HEAD));
		drawBodyPartRect(context, cx - 6, top + 10, 12, 11, health.getRatio(BodyPart.THORAX));
		drawBodyPartRect(context, cx - 5, top + 22, 10, 7, health.getRatio(BodyPart.STOMACH));
		drawBodyPartRect(context, cx - 12, top + 10, 5, 17, health.getRatio(BodyPart.LEFT_ARM));
		drawBodyPartRect(context, cx + 7, top + 10, 5, 17, health.getRatio(BodyPart.RIGHT_ARM));
		drawBodyPartRect(context, cx - 6, top + 30, 5, 19, health.getRatio(BodyPart.LEFT_LEG));
		drawBodyPartRect(context, cx + 1, top + 30, 5, 19, health.getRatio(BodyPart.RIGHT_LEG));

		int curTotal = Math.round(health.getTotalHp());
		int effMaxTotal = Math.round(health.getTotalEffectiveMaxHp());
		String label = curTotal + "/" + effMaxTotal;
		context.getMatrices().push();
		context.getMatrices().scale(0.72f, 0.72f, 1.0f);
		int tx = (int) (cx / 0.72f);
		int ty = (int) ((y + boxH - 9) / 0.72f);
		context.drawCenteredTextWithShadow(textRenderer, label, tx, ty, hpToColor(health.getTotalRatio()));
		context.getMatrices().pop();

		// Active trauma & painkiller badges directly below the silhouette
		int py = y + boxH + 3;
		int heavyCount = health.countInjuries(InjuryType.HEAVY_BLEED);
		if (heavyCount > 0) {
			py = drawHudBadge(context, textRenderer, x + boxW, py, "HB x" + heavyCount, 0xDD7A1414, 0xFFFF4444, 0xFFFFCCCC);
		}
		int lightCount = health.countInjuries(InjuryType.LIGHT_BLEED);
		if (lightCount > 0) {
			py = drawHudBadge(context, textRenderer, x + boxW, py, "LB x" + lightCount, 0xDD6E2A16, 0xFFFF8855, 0xFFFFDDCC);
		}
		int fracCount = health.countInjuries(InjuryType.FRACTURE);
		if (fracCount > 0) {
			py = drawHudBadge(context, textRenderer, x + boxW, py, "FR x" + fracCount, 0xDD4D3C12, 0xFFE0B23B, 0xFFFFEEAA);
		}
		if (health.hasPainkiller()) {
			int secs = (health.getPainkillerTicks() + 19) / 20;
			py = drawHudBadge(context, textRenderer, x + boxW, py, "PK " + secs + "s", 0xDD143B34, 0xFF4FD1C5, 0xFFB2F5EA);
		}
		if (player != null) {
			int energy = Math.max(0, Math.min(100, player.getHungerManager().getFoodLevel() * 5));
			int eBg = energy <= 15 ? 0xDD6A1414 : (energy <= 30 ? 0xDD4D3C12 : 0xDD14222D);
			int eBorder = energy <= 15 ? 0xFFFF4444 : (energy <= 30 ? 0xFFE0B23B : 0xFF3E6888);
			int eText = energy <= 15 ? 0xFFFFCCCC : (energy <= 30 ? 0xFFFFEEAA : 0xFF90CDF4);
			drawHudBadge(context, textRenderer, x + boxW, py, "E:" + energy, eBg, eBorder, eText);
		}
	}

	private static int drawHudBadge(
		DrawContext context, TextRenderer tr, int rightX, int y,
		String text, int bgCol, int borderCol, int textCol
	) {
		int bw = Math.max(30, Math.round(tr.getWidth(text) * 0.72f) + 8);
		int bx = rightX - bw;
		context.fill(bx, y, rightX, y + 10, bgCol);
		context.drawBorder(bx, y, bw, 10, borderCol);
		context.getMatrices().push();
		context.getMatrices().scale(0.72f, 0.72f, 1.0f);
		context.drawCenteredTextWithShadow(tr, text, (int) ((bx + bw / 2.0f) / 0.72f), (int) ((y + 2) / 0.72f), textCol);
		context.getMatrices().pop();
		return y + 12;
	}

	public static void drawDetailedHealthTab(DrawContext context, TextRenderer textRenderer, PlayerEntity player, int x, int y, int w, int h) {
		drawDetailedHealthTab(context, textRenderer, player, x, y, w, h, null, null, -1, -1);
	}

	public static void drawDetailedHealthTab(
		DrawContext context,
		TextRenderer textRenderer,
		PlayerEntity player,
		int x,
		int y,
		int w,
		int h,
		@Nullable ItemStack selectedMedStack,
		@Nullable List<PartHitbox> outHitboxes,
		int mouseX,
		int mouseY
	) {
		drawDetailedHealthTab(context, textRenderer, player, x, y, w, h, selectedMedStack, outHitboxes, null, null, mouseX, mouseY);
	}

	public static void drawDetailedHealthTab(
		DrawContext context,
		TextRenderer textRenderer,
		PlayerEntity player,
		int x,
		int y,
		int w,
		int h,
		@Nullable ItemStack selectedMedStack,
		@Nullable List<PartHitbox> outHitboxes,
		@Nullable List<QuickHealMed> availableMeds,
		@Nullable List<QuickHealHitbox> outQuickHeals,
		int mouseX,
		int mouseY
	) {
		BodyPartHealth health = ClientLoadoutCache.getBodyHealth();
		PlayerLoadout loadout = ClientLoadoutCache.getLoadout();

		int silTop = y + (h >= 128 ? 8 : 2);
		drawSilhouetteWithZoneBars(context, textRenderer, health, x + w / 2, silTop,
			selectedMedStack, outHitboxes, availableMeds, outQuickHeals, mouseX, mouseY);

		List<DamageReport> hits = health.getRecentHits();
		int panelW = Math.min(w - 16, 420);
		int panelX = x + (w - panelW) / 2;

		// 1. Penalties & Painkiller status strip under silhouette
		int statusY = silTop + 123;
		if (statusY + 12 > y + h) return;
		drawStatusPenaltiesBar(context, textRenderer, health, panelX, statusY, panelW);

		// 2. Armor Coverage panel
		int covY = statusY + 15;
		int covH = 50;
		if (covY + covH > y + h) return;
		context.fill(panelX, covY, panelX + panelW, covY + covH, 0xCC0E1114);
		context.drawBorder(panelX, covY, panelW, covH, 0xFF2B3238);
		drawTiny(context, textRenderer, Text.translatable("armor.tarkovcraft.coverage_header").getString(),
			panelX + 5, covY + 3, 0xFFD4B46A);

		int col0X = panelX + 6;
		int col1X = panelX + panelW / 2 + 4;
		int colW = panelW / 2 - 12;
		drawCoverageColumn(context, textRenderer, loadout, COVERAGE_ZONES_LEFT, col0X, covY + 13, colW);
		drawCoverageColumn(context, textRenderer, loadout, COVERAGE_ZONES_RIGHT, col1X, covY + 13, colW);

		// 3. Recent Hit Log
		if (!hits.isEmpty()) {
			int logY = covY + covH + 3;
			int logH = Math.min(42, y + h - logY - 2);
			if (logH >= 24) {
				context.fill(panelX, logY, panelX + panelW, logY + logH, 0xCC0E1114);
				context.drawBorder(panelX, logY, panelW, logH, 0xFF2B3238);
				drawTiny(context, textRenderer, Text.translatable("health.tarkovcraft.recent_hits").getString(),
					panelX + 5, logY + 3, 0xFFD4B46A);
				int rowY = logY + 12;
				int maxRows = Math.min(3, Math.max(1, (logH - 12) / 10));
				int shown = Math.min(maxRows, hits.size());
				for (int i = 0; i < shown; i++) {
					DamageReport r = hits.get(i);
					String zoneTxt = Text.translatable(r.hitZone().getTranslationKey()).getString();
					String armorTag = r.ricocheted() ? " [RICOCHET]" : (r.armorBlocked() ? " [ARMOR]" : "");
					String line = String.format("%s  -%.0f HP%s%s",
						zoneTxt,
						r.finalDamage(),
						armorTag,
						r.overflowedToThorax() ? " -> THORAX" : ""
					);
					drawTiny(context, textRenderer, line, panelX + 5, rowY, r.headEyesOneshot() ? 0xFFFF5555 : 0xFFC8D0D8);
					rowY += 10;
				}
			}
		}
	}

	private static void drawStatusPenaltiesBar(DrawContext context, TextRenderer tr, BodyPartHealth health, int x, int y, int w) {
		context.fill(x, y, x + w, y + 12, 0xCC0E1114);
		context.drawBorder(x, y, w, 12, 0xFF2B3238);

		List<String> tags = new ArrayList<>();
		List<Integer> cols = new ArrayList<>();

		if (health.hasPainkiller()) {
			int secs = (health.getPainkillerTicks() + 19) / 20;
			tags.add(Text.translatable("health.tarkovcraft.status_painkiller", secs).getString());
			cols.add(0xFF4FD1C5);
		}
		if (health.getImpairedLegCount() > 0) {
			tags.add(Text.translatable("health.tarkovcraft.penalty_limp").getString());
			cols.add(0xFFE0B23B);
		}
		if (health.getImpairedArmCount() > 0) {
			tags.add(Text.translatable("health.tarkovcraft.penalty_arms").getString());
			cols.add(0xFFE08A3B);
		}
		if (health.isStomachBlacked()) {
			tags.add(Text.translatable("health.tarkovcraft.penalty_stomach").getString());
			cols.add(0xFFFF6B6B);
		}

		if (tags.isEmpty() && !health.hasAnyBleed()) {
			drawTiny(context, tr, Text.translatable("health.tarkovcraft.status_healthy").getString(), x + 5, y + 3, 0xFF77B85A);
			return;
		}
		if (tags.isEmpty()) {
			tags.add(Text.translatable("health.tarkovcraft.tag_light_bleed").getString());
			cols.add(0xFFFF8855);
		}

		int cx = x + 5;
		for (int i = 0; i < tags.size(); i++) {
			String txt = (i > 0 ? " | " : "") + tags.get(i);
			drawTiny(context, tr, txt, cx, y + 3, cols.get(i));
			cx += Math.round(tr.getWidth(txt) * 0.72f) + 2;
			if (cx > x + w - 20) break;
		}
	}

	private static void drawCoverageColumn(
		DrawContext context, TextRenderer textRenderer,
		PlayerLoadout loadout, HitZone[] zones, int x, int startY, int colW
	) {
		String openTxt = Text.translatable("armor.tarkovcraft.zone_open").getString();
		int rowY = startY;
		for (HitZone zone : zones) {
			int armorClass = ZonalArmorHelper.getHighestProtectingClass(loadout, zone);
			String zoneTxt = Text.translatable(zone.getTranslationKey()).getString();
			String statusTxt = armorClass > 0 ? ("CL." + armorClass) : openTxt;
			int statusCol = armorClass > 0 ? 0xFF63D2FF : 0xFFFF5555;

			drawTiny(context, textRenderer, zoneTxt, x, rowY, 0xFFC8D0D8);
			int sw = Math.round(textRenderer.getWidth(statusTxt) * 0.72f);
			drawTiny(context, textRenderer, statusTxt, x + colW - sw, rowY, statusCol);
			rowY += 9;
		}
	}

	public static void drawSilhouetteWithZoneBars(DrawContext context, TextRenderer textRenderer, BodyPartHealth health, int cx, int top) {
		drawSilhouetteWithZoneBars(context, textRenderer, health, cx, top, null, null, -1, -1);
	}

	public static void drawSilhouetteWithZoneBars(
		DrawContext context,
		TextRenderer textRenderer,
		BodyPartHealth health,
		int cx,
		int top,
		@Nullable ItemStack selectedMedStack,
		@Nullable List<PartHitbox> outHitboxes,
		int mouseX,
		int mouseY
	) {
		drawSilhouetteWithZoneBars(context, textRenderer, health, cx, top, selectedMedStack, outHitboxes, null, null, mouseX, mouseY);
	}

	public static void drawSilhouetteWithZoneBars(
		DrawContext context,
		TextRenderer textRenderer,
		BodyPartHealth health,
		int cx,
		int top,
		@Nullable ItemStack selectedMedStack,
		@Nullable List<PartHitbox> outHitboxes,
		@Nullable List<QuickHealMed> availableMeds,
		@Nullable List<QuickHealHitbox> outQuickHeals,
		int mouseX,
		int mouseY
	) {
		drawInteractivePartRect(context, BodyPart.HEAD, cx - 10, top, 20, 20, health, selectedMedStack, outHitboxes, mouseX, mouseY);
		drawInteractivePartRect(context, BodyPart.THORAX, cx - 15, top + 24, 30, 28, health, selectedMedStack, outHitboxes, mouseX, mouseY);
		drawInteractivePartRect(context, BodyPart.STOMACH, cx - 13, top + 54, 26, 18, health, selectedMedStack, outHitboxes, mouseX, mouseY);
		drawInteractivePartRect(context, BodyPart.LEFT_ARM, cx - 28, top + 24, 11, 42, health, selectedMedStack, outHitboxes, mouseX, mouseY);
		drawInteractivePartRect(context, BodyPart.RIGHT_ARM, cx + 17, top + 24, 11, 42, health, selectedMedStack, outHitboxes, mouseX, mouseY);
		drawInteractivePartRect(context, BodyPart.LEFT_LEG, cx - 14, top + 74, 12, 46, health, selectedMedStack, outHitboxes, mouseX, mouseY);
		drawInteractivePartRect(context, BodyPart.RIGHT_LEG, cx + 2, top + 74, 12, 46, health, selectedMedStack, outHitboxes, mouseX, mouseY);

		drawZoneBar(context, textRenderer, cx + 38, top, BodyPart.HEAD, health, selectedMedStack, outHitboxes, availableMeds, outQuickHeals, true, mouseX, mouseY);
		drawZoneBar(context, textRenderer, cx + 38, top + 24, BodyPart.THORAX, health, selectedMedStack, outHitboxes, availableMeds, outQuickHeals, true, mouseX, mouseY);
		drawZoneBar(context, textRenderer, cx + 38, top + 48, BodyPart.STOMACH, health, selectedMedStack, outHitboxes, availableMeds, outQuickHeals, true, mouseX, mouseY);
		drawZoneBar(context, textRenderer, cx + 38, top + 72, BodyPart.RIGHT_ARM, health, selectedMedStack, outHitboxes, availableMeds, outQuickHeals, true, mouseX, mouseY);
		drawZoneBar(context, textRenderer, cx + 38, top + 96, BodyPart.RIGHT_LEG, health, selectedMedStack, outHitboxes, availableMeds, outQuickHeals, true, mouseX, mouseY);

		drawZoneBar(context, textRenderer, cx - 130, top + 34, BodyPart.LEFT_ARM, health, selectedMedStack, outHitboxes, availableMeds, outQuickHeals, false, mouseX, mouseY);
		drawZoneBar(context, textRenderer, cx - 130, top + 76, BodyPart.LEFT_LEG, health, selectedMedStack, outHitboxes, availableMeds, outQuickHeals, false, mouseX, mouseY);
	}

	private static void drawInteractivePartRect(
		DrawContext context, BodyPart part, int x, int y, int w, int h,
		BodyPartHealth health, @Nullable ItemStack selectedMedStack,
		@Nullable List<PartHitbox> outHitboxes, int mouseX, int mouseY
	) {
		drawBodyPartRect(context, x, y, w, h, health.getRatio(part));
		if (selectedMedStack != null && MedicalHelper.canApplyToPart(health, selectedMedStack, part)) {
			boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
			context.drawBorder(x, y, w, h, hov ? 0xFFD4B46A : 0xFF4CAF50);
		}
		if (outHitboxes != null) {
			outHitboxes.add(new PartHitbox(part, x, y, w, h));
		}
	}

	private static void drawZoneBar(
		DrawContext context,
		TextRenderer textRenderer,
		int x,
		int y,
		BodyPart part,
		BodyPartHealth health,
		@Nullable ItemStack selectedMedStack,
		@Nullable List<PartHitbox> outHitboxes,
		@Nullable List<QuickHealMed> availableMeds,
		@Nullable List<QuickHealHitbox> outQuickHeals,
		boolean iconsOnRight,
		int mouseX,
		int mouseY
	) {
		int bw = 92;
		int bh = 22;
		float ratio = health.getRatio(part);
		int cur = Math.round(health.getHp(part));
		int effMax = Math.round(health.getEffectiveMaxHp(part));
		int baseMax = (int) part.getMaxHp();
		int barColor = hpToColor(ratio);

		boolean canHeal = selectedMedStack != null && MedicalHelper.canApplyToPart(health, selectedMedStack, part);
		boolean hovered = mouseX >= x && mouseX < x + bw && mouseY >= y && mouseY < y + bh;
		int border = canHeal
			? (hovered ? 0xFFD4B46A : 0xFF4CAF50)
			: (ratio <= 0.001f ? 0xFFC93838 : 0xFF3E464E);

		context.fill(x, y, x + bw, y + bh, (canHeal && hovered) ? 0x334CAF50 : 0xCC161A1E);
		context.drawBorder(x, y, bw, bh, border);

		context.getMatrices().push();
		context.getMatrices().scale(0.72f, 0.72f, 1.0f);
		String name = Text.translatable(part.getTranslationKey()).getString();
		context.drawTextWithShadow(textRenderer, name, (int) ((x + 3) / 0.72f), (int) ((y + 2) / 0.72f), 0xFFD4D8DC);

		String curStr = String.valueOf(cur);
		String maxStr = "/" + effMax;
		int curW = textRenderer.getWidth(curStr);
		int maxW = textRenderer.getWidth(maxStr);
		int rightScaledX = (int) ((x + bw - 3) / 0.72f);
		int textScaledY = (int) ((y + 2) / 0.72f);
		context.drawTextWithShadow(textRenderer, curStr, rightScaledX - maxW - curW, textScaledY,
			ratio <= 0.001f ? 0xFFFF5555 : barColor);
		context.drawTextWithShadow(textRenderer, maxStr, rightScaledX - maxW, textScaledY,
			effMax < baseMax ? 0xFF9AA0A8 : 0xFFB8C0C8);
		context.getMatrices().pop();

		int innerW = bw - 6;
		int barY1 = y + 10;
		int barY2 = y + 14;
		context.fill(x + 3, barY1, x + 3 + innerW, barY2, 0xFF0E1114);
		if (effMax < baseMax) {
			int effW = Math.round(innerW * health.getEffectiveMaxRatio(part));
			if (effW < innerW) {
				context.fill(x + 3 + effW, barY1, x + 3 + innerW, barY2, 0xFF34383E);
			}
		}
		int fillW = Math.round(innerW * health.getBaseRatio(part));
		if (fillW > 0) {
			context.fill(x + 3, barY1, x + 3 + fillW, barY2, barColor);
		}

		// Injury & blacked-out tags on bottom line (y + 15)
		int tagX = x + 3;
		if (health.isBlacked(part)) {
			tagX = drawMicroTag(context, textRenderer, tagX, y + 15,
				Text.translatable("health.tarkovcraft.tag_blacked").getString(), 0xFFE53935);
		}
		if (health.hasHeavyBleed(part)) {
			tagX = drawMicroTag(context, textRenderer, tagX, y + 15,
				Text.translatable("health.tarkovcraft.tag_heavy_bleed").getString(), 0xFFFF4444);
		} else if (health.hasLightBleed(part)) {
			tagX = drawMicroTag(context, textRenderer, tagX, y + 15,
				Text.translatable("health.tarkovcraft.tag_light_bleed").getString(), 0xFFFF8855);
		}
		if (health.hasFracture(part)) {
			drawMicroTag(context, textRenderer, tagX, y + 15,
				Text.translatable("health.tarkovcraft.tag_fracture").getString(), 0xFFE0B23B);
		}

		if (outHitboxes != null) {
			outHitboxes.add(new PartHitbox(part, x, y, bw, bh));
		}

		// Quick-Heal 14x14 mini-icons next to injured parts (no durability text)
		if (availableMeds != null && outQuickHeals != null) {
			boolean needsHelp = health.getHp(part) < health.getEffectiveMaxHp(part) - 0.5f
				|| health.hasLightBleed(part)
				|| health.hasHeavyBleed(part)
				|| health.hasFracture(part)
				|| health.isBlacked(part);
			if (needsHelp) {
				List<QuickHealMed> suitable = collectQuickHealsForPart(health, part, availableMeds);
				int btnSize = 14;
				int btnY = y + (bh - btnSize) / 2;
				for (int i = 0; i < suitable.size(); i++) {
					QuickHealMed qm = suitable.get(i);
					int btnX = iconsOnRight
						? (x + bw + 4 + i * (btnSize + 2))
						: (x - 4 - btnSize - i * (btnSize + 2));
					boolean qHov = mouseX >= btnX && mouseX < btnX + btnSize && mouseY >= btnY && mouseY < btnY + btnSize;
					context.fill(btnX, btnY, btnX + btnSize, btnY + btnSize, qHov ? 0xDD272B30 : 0xCC121619);
					context.drawBorder(btnX, btnY, btnSize, btnSize, qHov ? TarkovUiTheme.ACCENT_SAND : 0xFF3A424A);
					TarkovUiTheme.drawScaledItem(context, qm.stack(), btnX + 1, btnY + 1, 12, 12);
					outQuickHeals.add(new QuickHealHitbox(part, qm, btnX, btnY, btnSize, btnSize));
				}
			}
		}
	}

	private static List<QuickHealMed> collectQuickHealsForPart(
		BodyPartHealth health, BodyPart part, List<QuickHealMed> allMeds
	) {
		Map<Item, QuickHealMed> unique = new LinkedHashMap<>();
		for (QuickHealMed m : allMeds) {
			if (!unique.containsKey(m.stack().getItem()) && MedicalHelper.canApplyToPart(health, m.stack(), part)) {
				unique.put(m.stack().getItem(), m);
			}
		}
		List<QuickHealMed> sorted = new ArrayList<>(unique.values());
		sorted.sort(Comparator.comparingInt(m -> quickHealPriority(health, part, m.stack())));
		return sorted.size() > 4 ? sorted.subList(0, 4) : sorted;
	}

	private static int quickHealPriority(BodyPartHealth health, BodyPart part, ItemStack stack) {
		if (!(stack.getItem() instanceof TarkovMedicalItem med)) return 99;
		MedicalType t = med.getMedicalType();
		if (health.hasHeavyBleed(part) && t.isCuresHeavyBleed()) return 0;
		if (health.isBlacked(part) && t.isSurgicalKit()) return 1;
		if (health.hasFracture(part) && t.isCuresFracture()) return 2;
		if (health.hasLightBleed(part) && t.isCuresLightBleed()) return 3;
		if (t.getMaxHealPerUse() > 0) return 4;
		return 5;
	}

	private static int drawMicroTag(DrawContext context, TextRenderer tr, int x, int y, String text, int color) {
		context.getMatrices().push();
		context.getMatrices().scale(0.62f, 0.62f, 1.0f);
		context.drawText(tr, text, Math.round(x / 0.62f), Math.round(y / 0.62f), color, false);
		context.getMatrices().pop();
		return x + Math.round(tr.getWidth(text) * 0.62f) + 4;
	}

	public static void drawBodyPartRect(DrawContext context, int x, int y, int w, int h, float ratio) {
		int fill = hpToColor(ratio);
		int outline;
		if (ratio <= 0.001f) {
			outline = 0xFFE53935;
		} else if (ratio < 0.25f) {
			outline = 0xFF8E2A22;
		} else {
			outline = 0xCC0D1013;
		}
		context.fill(x, y, x + w, y + h, fill);
		context.drawBorder(x, y, w, h, outline);
	}

	private static void drawTiny(DrawContext context, TextRenderer tr, String text, int x, int y, int col) {
		context.getMatrices().push();
		context.getMatrices().scale(0.72f, 0.72f, 1.0f);
		context.drawTextWithShadow(tr, text, (int) (x / 0.72f), (int) (y / 0.72f), col);
		context.getMatrices().pop();
	}

	private static final float[] GRADIENT_STOPS = {0.0f, 0.25f, 0.50f, 0.75f, 1.0f};
	private static final int[] GRADIENT_COLORS = {
		0xC62828,
		0xE0662F,
		0xE0B23B,
		0xA9BE4A,
		0x77B85A
	};

	public static int hpToColor(float ratio) {
		if (ratio <= 0.001f) return 0xFF141414;
		float r = Math.max(0.0f, Math.min(1.0f, ratio));
		for (int i = 0; i < GRADIENT_STOPS.length - 1; i++) {
			float a = GRADIENT_STOPS[i];
			float b = GRADIENT_STOPS[i + 1];
			if (r <= b) {
				float t = (r - a) / (b - a);
				return 0xFF000000 | lerpRgb(GRADIENT_COLORS[i], GRADIENT_COLORS[i + 1], t);
			}
		}
		return 0xFF000000 | GRADIENT_COLORS[GRADIENT_COLORS.length - 1];
	}

	private static int lerpRgb(int c1, int c2, float t) {
		int r = Math.round(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
		int g = Math.round(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
		int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
		return (r << 16) | (g << 8) | b;
	}
}
