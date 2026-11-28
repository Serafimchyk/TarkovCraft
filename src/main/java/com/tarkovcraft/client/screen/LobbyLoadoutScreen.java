package com.tarkovcraft.client.screen;

import com.tarkovcraft.client.ClientLoadoutCache;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.lobby.LoadoutAssembly.Slot;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.screen.LobbyLoadoutScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import static com.tarkovcraft.client.screen.LobbyTopNavBar.*;
import static com.tarkovcraft.client.screen.TarkovUiTheme.*;
import static com.tarkovcraft.screen.LobbyLoadoutScreenHandler.*;

public final class LobbyLoadoutScreen extends LobbyCommerceScreen<LobbyLoadoutScreenHandler> {
	private static final EquipmentSlotType[] LEFT = {EquipmentSlotType.HELMET, EquipmentSlotType.FACE_MASK,
		EquipmentSlotType.BODY_ARMOR, EquipmentSlotType.RIG, EquipmentSlotType.BACKPACK};
	private static final EquipmentSlotType[] RIGHT = {EquipmentSlotType.HEADSET, EquipmentSlotType.PRIMARY_1,
		EquipmentSlotType.PRIMARY_2, EquipmentSlotType.PISTOL, EquipmentSlotType.MELEE};

	private int listX;
	private int listRight;

	public LobbyLoadoutScreen(LobbyLoadoutScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title, Tab.LOADOUT);
	}

	@Override
	protected void drawContent(DrawContext context) {
		int w = Math.min(760, width - 16);
		int x = (width - w) / 2;
		int y = HEIGHT + 7;
		int h = height - y - 7;
		int leftW = (w - 6) * 48 / 100;
		listX = x + leftW + 6;
		listRight = x + w;
		drawEquipment(context, x, y, leftW, h);
		drawCandidates(context, listX, y, listRight - listX, h);
	}

	private void drawEquipment(DrawContext context, int x, int y, int w, int h) {
		panel(context, x, y, w, h);
		PlayerLoadout loadout = ClientLoadoutCache.getLoadout();
		int slotW = (w - 64) / 2;
		int slotH = Math.max(15, Math.min(29, (h - 120) / 5));
		int modelX = x + w / 2;
		int modelY = y + 22 + slotH * 5;
		var player = client.player;
		boolean invisible = player.isInvisible();
		context.enableScissor(x + slotW + 7, y + 20, x + w - slotW - 7, modelY + 1);
		try {
			player.setInvisible(false);
			InventoryScreen.drawEntity(context, modelX, modelY, Math.max(16, slotH * 2),
				modelX - pointerX, y + 40 - pointerY, player);
		} finally {
			player.setInvisible(invisible);
			context.disableScissor();
		}
		line(context, textRenderer, title.getString(), x + 7, y + 7, w - 14, ACCENT_SAND, 0.95f);

		for (int i = 0; i < LEFT.length; i++) {
			int rowY = y + 22 + i * slotH;
			drawSlot(context, loadout, LEFT[i], x + 6, rowY, slotW, slotH);
			drawSlot(context, loadout, RIGHT[i], x + w - slotW - 6, rowY, slotW, slotH);
		}
		drawStorage(context, loadout, x + 6, modelY + 2, w - 12);

		int bottom = y + h - 6;
		long balance = handler.status().rubles();
		long repair = handler.repairPrice();
		button(context, label("service.repair_equipped", "Repair equipped · %s", rubles(repair)),
			x + 6, bottom - 58, w - 12, 15, REPAIR, repair > 0 && balance >= repair, false);
		int presetW = (w - 14) / 3;
		for (int i = 0; i < 3; i++) {
			button(context, label("loadout.preset", "Preset %s", i + 1), x + 6 + i * presetW, bottom - 39,
				presetW - 2, 15, PRESET_FIRST + i, true, handler.preset() == i);
		}
		int saveW = (w - 12) / 3;
		button(context, label("loadout.save", "Save kit"), x + 6, bottom - 18, saveW - 2, 18, SAVE, true, false);
		var quote = handler.quote();
		Text action = !handler.saved() ? label("loadout.empty_preset", "Empty preset")
			: quote.blocked() ? label("loadout.blocked", "Kit unavailable")
			: quote.missing() == 0 ? label("loadout.assemble", "Assemble preset")
			: label("loadout.assemble_buy", "Assemble · %s", rubles(quote.price()));
		button(context, action, x + 6 + saveW, bottom - 18, w - 12 - saveW, 18, ASSEMBLE,
			handler.saved() && !quote.blocked() && balance >= quote.price(), false);
	}

	private void drawSlot(DrawContext context, PlayerLoadout loadout, EquipmentSlotType slot,
		int x, int y, int w, int h) {
		ItemStack stack = loadout.getEquipment(slot);
		context.fill(x, y, x + w, y + h - 1, hit(x, y, w, h) ? HOVER_MENU_BG : BG_SLOT);
		context.drawBorder(x, y, w, h - 1, handler.slot().equipment() == slot ? ACCENT_SAND : BORDER_CELL);
		drawScaledItem(context, stack, x + 2, y + (h - 14) / 2, 14, 14);
		line(context, textRenderer, slotName(slot).getString(), x + 18, y + (h - 8) / 2, w - 21,
			TEXT_PRIMARY, 0.74f);
		clickArea(x, y, w, h - 1, SLOT_FIRST + slot.ordinal());
		if (hit(x, y, w, h - 1)) hover(stack);
	}

	private void drawStorage(DrawContext context, PlayerLoadout loadout, int x, int y, int w) {
		int cell = 12;
		int secureX = x + w - 2 * cell;
		line(context, textRenderer, label("loadout.pockets", "Pockets").getString(), x, y + 1, w / 2,
			TEXT_SECONDARY, 0.78f);
		line(context, textRenderer, label("loadout.secure", "Secure 2×2").getString(), x + w / 2, y + 13,
			w / 2 - 2 * cell - 3, TEXT_SECONDARY, 0.75f);
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			int cx = x + i * cell;
			drawCell(context, loadout.getPocket(i), cx, y + 12, cell, 1, 1, false);
			storageAction(context, cx, y + 12, cell, Slot.EQUIPPED + i);
		}
		var secure = loadout.getSecureContainer();
		for (int dy = 0; dy < 2; dy++)
			for (int dx = 0; dx < 2; dx++)
				drawCell(context, ItemStack.EMPTY, secureX + dx * cell, y + dy * cell, cell, 1, 1, false);
		for (var entry : secure.getEntries()) {
			var size = entry.getOccupiedSize();
			drawCell(context, entry.getStack(), secureX + entry.getX() * cell, y + entry.getY() * cell,
				cell, size.width(), size.height(), entry.isRotated());
		}
		for (int dy = 0; dy < 2; dy++)
			for (int dx = 0; dx < 2; dx++)
				storageAction(context, secureX + dx * cell, y + dy * cell, cell, Slot.SECURE_FIRST + dx + dy * 2);
	}

	private void storageAction(DrawContext context, int x, int y, int cell, int slot) {
		if (handler.slot().index() == slot) context.drawBorder(x, y, cell, cell, ACCENT_SAND);
		clickArea(x, y, cell, cell, SLOT_FIRST + slot);
	}

	private void drawCell(DrawContext context, ItemStack item, int x, int y, int cell, int columns, int rows, boolean rotated) {
		int w = cell * columns;
		int h = cell * rows;
		context.fill(x, y, x + w, y + h, BG_SLOT);
		context.drawBorder(x, y, w, h, BORDER_CELL);
		drawScaledItem(context, item, x + 1, y + 1, w - 2, h - 2, rotated);
		if (hit(x, y, w, h)) hover(item);
	}

	private void drawCandidates(DrawContext context, int x, int y, int w, int h) {
		panel(context, x, y, w, h);
		line(context, textRenderer, selectedName().getString(), x + 7, y + 7, w - 14, ACCENT_SAND, 0.95f);
		boolean hasAmmo = !handler.ammo().isEmpty();
		int rowH = Math.min(37, Math.max(15, (h - (hasAmmo ? 119 : 73)) / ROWS));
		int rowY = y + 23;
		Boolean market = null;
		for (int i = 0; i < handler.rows().size(); i++) {
			Row row = handler.rows().get(i);
			if (market == null || market != row.market()) {
				market = row.market();
				Text section = market ? label("loadout.market", "Buy and equip") : label("loadout.stash", "In stash");
				line(context, textRenderer, section.getString(), x + 7, rowY, w - 14, ACCENT_SAND, 0.83f);
				rowY += 12;
			}
			drawCandidate(context, row, x + 6, rowY, w - 12, rowH, i);
			rowY += rowH;
		}
		if (handler.rows().isEmpty())
			line(context, textRenderer, label("loadout.no_items", "No compatible items").getString(),
				x + 7, rowY, w - 14, TEXT_MUTED, 0.85f);

		int footerY = y + h - 22;
		pagination(context, x + w - 6, footerY, handler.page(), handler.pages(), PREVIOUS, NEXT);
		if (hasAmmo) drawAmmo(context, x + 6, footerY - 47, w - 12);
	}

	private void drawCandidate(DrawContext context, Row row, int x, int y, int w, int h, int index) {
		context.fill(x, y, x + w, y + h - 1, BG_SLOT);
		drawScaledItem(context, row.item(), x + 2, y + 1, 16, h - 3);
		int actionW = Math.min(98, w / 3);
		line(context, textRenderer, row.item().getName().getString(), x + 21, y + 2, w - 25 - actionW, TEXT_PRIMARY, 0.78f);
		if (row.item().getItem() instanceof TarkovArmorItem armor) {
			Text detail = label("loadout.armor", "Class %s · %s/%s", armor.getArmorClass(),
				TarkovArmorItem.getRemainingDurability(row.item()), TarkovArmorItem.getEffectiveMaxDurability(row.item()));
			line(context, textRenderer, detail.getString(), x + 21, y + h - 8, w - 27 - actionW, TEXT_SECONDARY, 0.72f);
		}
		if (row.market() && row.adjustment() != 0) {
			String adjustment = (row.adjustment() > 0 ? "+" : "") + row.adjustment() + "%";
			line(context, textRenderer, adjustment, x + w - actionW - 29, y + h - 8, 27,
				row.adjustment() < 0 ? GREEN : ALERT, 0.72f);
		}
		button(context, row.market() ? Text.literal(rubles(row.price())) : label("loadout.equip", "Equip"),
			x + w - actionW, y + 1, actionW, h - 3, EQUIP_FIRST + index,
			row.allowed() && handler.status().rubles() >= row.price(), false);
		if (hit(x, y, w - actionW, h)) hover(row.item());
	}

	private void drawAmmo(DrawContext context, int x, int y, int w) {
		line(context, textRenderer, label("loadout.ammo", "Ammo · %s", handler.caliber()).getString(),
			x, y, w, ACCENT_SAND, 0.82f);
		int column = w / handler.ammo().size();
		for (int i = 0; i < handler.ammo().size(); i++) {
			Ammo ammo = handler.ammo().get(i);
			int bx = x + i * column;
			Text tier = Text.literal(ammo.tier().name());
			drawAmmoButton(context, bx, y + 10, column - 2, tier, 30, ammo.price30(), AMMO_FIRST + i * 2);
			drawAmmoButton(context, bx, y + 26, column - 2, tier, 60, ammo.price60(), AMMO_FIRST + i * 2 + 1);
		}
	}

	private void drawAmmoButton(DrawContext context, int x, int y, int w, Text tier, int count, long price, int id) {
		button(context, Text.literal(tier.getString() + " +" + count + " · " + rubles(price)),
			x, y, w, 15, id, handler.status().rubles() >= price, false);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		if (x < listX || x >= listRight || y < HEIGHT || amount == 0) return false;
		if (amount > 0 && handler.page() > 0) send(PREVIOUS);
		if (amount < 0 && handler.page() + 1 < handler.pages()) send(NEXT);
		return true;
	}

	private Text selectedName() {
		Slot slot = handler.slot();
		if (slot.equipment() != null) return slotName(slot.equipment());
		return slot.secure() ? label("loadout.secure", "Secure 2×2") : label("loadout.pockets", "Pockets");
	}

	private static Text slotName(EquipmentSlotType slot) {
		return Text.translatableWithFallback("screen.tarkovcraft.slot." + slot.getId(), slot.getDisplayName());
	}
}
