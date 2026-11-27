package com.tarkovcraft.client.screen;

import com.tarkovcraft.client.ClientLoadoutCache;
import com.tarkovcraft.client.hud.BodyHealthHudHelper;
import com.tarkovcraft.health.InjuryType;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.screen.LobbyServiceScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static com.tarkovcraft.client.screen.LobbyTopNavBar.*;
import static com.tarkovcraft.client.screen.TarkovUiTheme.*;
import static com.tarkovcraft.screen.LobbyServiceScreenHandler.*;

public final class LobbyServiceScreen extends LobbyCommerceScreen<LobbyServiceScreenHandler> {
	public LobbyServiceScreen(LobbyServiceScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title, Tab.SERVICE);
	}

	@Override
	protected void drawContent(DrawContext context) {
		int w = Math.min(760, width - 16);
		int x = (width - w) / 2;
		int y = HEIGHT + 8;
		int h = height - y - 8;
		int healthW = (w - 6) * 43 / 100;
		drawHealth(context, x, y, healthW, h);
		drawRepairs(context, x + healthW + 6, y, w - healthW - 6, h);
	}

	private void drawHealth(DrawContext context, int x, int y, int w, int h) {
		panel(context, x, y, w, h);
		line(context, textRenderer, label("service.treatment", "Therapist · Treatment").getString(),
			x + 7, y + 8, w - 14, ACCENT_SAND, 0.95f);

		var health = ClientLoadoutCache.getBodyHealth();
		float scale = Math.max(0.35f, Math.min(1, Math.min((w - 12) / 196f, (h - 78) / 120f)));
		context.getMatrices().push();
		context.getMatrices().translate(x + w / 2f, y + 23, 0);
		context.getMatrices().scale(scale, scale, 1);
		BodyHealthHudHelper.drawSilhouetteWithZoneBars(context, textRenderer, health, 0, 0);
		context.getMatrices().pop();

		List<String> injuries = new ArrayList<>();
		for (InjuryType injury : InjuryType.values()) {
			int count = health.countInjuries(injury);
			if (count > 0) injuries.add(Text.translatable(injury.getTranslationKey()).getString() + " ×" + count);
		}
		String injuryText = injuries.isEmpty() ? label("service.no_injuries", "No active injuries").getString()
			: String.join("  ·  ", injuries);
		line(context, textRenderer, injuryText, x + 7, y + h - 51, w - 14, injuries.isEmpty() ? GREEN : ALERT, 0.8f);
		long price = handler.healPrice();
		button(context, label("service.heal", "Heal all · %s", rubles(price)), x + 6, y + h - 39, w - 12, 15, HEAL,
			price > 0 && handler.status().rubles() >= price, false);
		button(context, label("service.stash_meds", "Use stash meds"), x + 6, y + h - 21, w - 12, 15, USE_STASH_MEDS,
			handler.hasMeds(), false);
	}

	private void drawRepairs(DrawContext context, int x, int y, int w, int h) {
		panel(context, x, y, w, h);
		line(context, textRenderer, label("service.repair", "Mechanic · Armor repair").getString(),
			x + 7, y + 8, w - 14, ACCENT_SAND, 0.95f);
		long equipped = handler.equippedPrice();
		button(context, label("service.repair_equipped", "Repair equipped · %s", rubles(equipped)),
			x + 6, y + 22, w - 12, 16, REPAIR_EQUIPPED, equipped > 0 && handler.status().rubles() >= equipped, false);
		int rowHeight = Math.max(16, Math.min(28, (h - 66) / 6));
		for (int i = 0; i < handler.rows().size(); i++)
			drawRepair(context, handler.rows().get(i), x + 6, y + 42 + i * rowHeight, w - 12, rowHeight, i);
		if (handler.rows().isEmpty())
			line(context, textRenderer, label("service.no_repairs", "No damaged armor").getString(),
				x + 7, y + 46, w - 14, TEXT_MUTED, 0.85f);
		pagination(context, x + w - 6, y + h - 21, handler.page(), handler.pages(), PREVIOUS, NEXT);
	}

	private void drawRepair(DrawContext context, RepairRow row, int x, int y, int w, int h, int index) {
		context.fill(x, y, x + w, y + h - 1, BG_SLOT);
		drawScaledItem(context, row.item(), x + 2, y + 2, 15, 15);
		int buyW = Math.min(86, w * 43 / 100);
		String location = row.equipped() ? label("service.equipped", "Equipped").getString() : label("trade.stash", "Stash").getString();
		line(context, textRenderer, row.item().getName().getString() + " · " + location, x + 21, y + 2,
			w - 26 - buyW, TEXT_PRIMARY, 0.76f);
		String durability = TarkovArmorItem.getRemainingDurability(row.item()) + "/" + TarkovArmorItem.getEffectiveMaxDurability(row.item())
			+ " → " + row.currentAfter() + "/" + row.maxAfter();
		line(context, textRenderer, durability, x + 21, y + h - 10, w - 26 - buyW, TEXT_SECONDARY, 0.78f);
		button(context, label("service.repair_item", "Repair · %s", rubles(row.price())),
			x + w - buyW, y + 1, buyW, h - 3, REPAIR_FIRST + index, handler.status().rubles() >= row.price(), false);
		if (hit(x, y, w - buyW, h)) hover(row.item());
	}
}
