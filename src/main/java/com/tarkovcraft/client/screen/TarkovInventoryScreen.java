package com.tarkovcraft.client.screen;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.client.ClientLoadoutCache;
import com.tarkovcraft.client.ClientNetworkSender;
import com.tarkovcraft.client.TarkovCraftClient;
import com.tarkovcraft.client.hud.BodyHealthHudHelper;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.grid.ItemSizeRegistry;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.MedicalHelper;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.MedicalType;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.network.TarkovNetworking;
import com.tarkovcraft.screen.TarkovScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Full-screen translucent Escape-from-Tarkov-style Inventory & Looting screen:
 * - 3-column layout (Left: 3D Player + Equipment, Center: Rig/Pockets/Backpack, Right: External Loot when open)
 * - Bottom Quick-Use bar (Melee, 1..3 weapons, 4..9 pockets)
 * - Hold-and-Drag (>4px threshold), click-to-select, R rotate, F transfer, Ctrl+F equip/unequip, Del discard
 * - Dynamic EFT RMB Context Menu, Draggable Inspect Window, and Split Stack Modal
 */
@Environment(EnvType.CLIENT)
public class TarkovInventoryScreen extends HandledScreen<TarkovScreenHandler> {
	private static final int TAB_GEAR = 0;
	private static final int TAB_HEALTH = 1;
	private static final int MAX_INSPECT_WINDOWS = 6;

	private int activeTab = TAB_GEAR;
	private StashFilter stashFilter = StashFilter.ALL;
	private Text stashFilterTooltip;
	private Rect healthMedStrip;
	private int healthMedOffset;
	private int healthMedMaxOffset;
	private List<HealthMedSlot> availableHealthMeds = List.of();

	private enum StashFilter {
		ALL("*", "All"),
		WEAPONS("W", "Weapons"),
		AMMO("A", "Ammunition"),
		ARMOR("V", "Armor"),
		MEDICINE("+", "Medicine"),
		CONTAINERS("#", "Containers"),
		LOOT("$", "Loot and barter");

		final String symbol;
		final String fallback;

		StashFilter(String symbol, String fallback) {
			this.symbol = symbol;
			this.fallback = fallback;
		}

		Text label() {
			return lobbyLabel("filter_" + name().toLowerCase(Locale.ROOT), fallback);
		}

		boolean matches(ItemStack stack) {
			var item = stack.getItem();
			StashFilter actual;
			if (AmmoTier.isTaczGunStack(stack) || item instanceof SwordItem || item instanceof BowItem
				|| item instanceof CrossbowItem || item instanceof TridentItem || item instanceof com.tarkovcraft.item.TarkovMeleeItem) {
				actual = WEAPONS;
			} else if (AmmoTier.isTaczAmmoStack(stack) || item instanceof ArrowItem) {
				actual = AMMO;
			} else if (item instanceof TarkovArmorItem || item instanceof ArmorItem) {
				actual = ARMOR;
			} else if (item instanceof TarkovMedicalItem || item instanceof com.tarkovcraft.item.TarkovStimulatorItem) {
				actual = MEDICINE;
			} else if (ContainerItemHelper.isContainerItem(stack)) {
				actual = CONTAINERS;
			} else {
				actual = LOOT;
			}
			return this == ALL || this == actual;
		}
	}
	private final List<InteractiveRegion> regions = new ArrayList<>();
	private final List<ActionButton> actionButtons = new ArrayList<>();
	private final List<BodyHealthHudHelper.PartHitbox> healthPartHitboxes = new ArrayList<>();
	private final List<BodyHealthHudHelper.QuickHealHitbox> quickHealHitboxes = new ArrayList<>();
	private final List<HealthMedSlot> healthMedSlots = new ArrayList<>();

	@Nullable
	private SelectedMedRef selectedHealthMed;

	@Nullable
	private DragState drag;
	@Nullable
	private ContextMenu contextMenu;
	private final List<TarkovInspectWindow> inspectWindows = new ArrayList<>();
	private final Map<UUID, int[]> floatingWinPos = new HashMap<>();
	private final List<FloatingWindowLayout> floatingLayouts = new ArrayList<>();
	@Nullable
	private UUID draggingFloatingUuid = null;
	@Nullable
	private TarkovSplitModal splitModal;
	@Nullable
	private SelectedTarget selectedTarget;

	private record FloatingWindowLayout(
		UUID uuid,
		int windowIndex,
		Rect bounds,
		Rect headerRect,
		Rect closeRect,
		@Nullable Rect searchRect
	) {
	}

	private Rect tabGearBtn;
	private Rect tabHealthBtn;
	private Rect closeBtn;
	private Rect leftColumnArea;
	@Nullable
	private Rect centerScrollArea;
	@Nullable
	private Rect rightScrollArea;

	private int cellSize = 22;
	private int centerScrollY = 0;
	private int centerMaxScroll = 0;
	private int rightScrollY = 0;
	private int rightMaxScroll = 0;
	private int rigSectionOffsetY = 0;
	private int backpackSectionOffsetY = 0;

	private boolean rigCollapsed = false;
	private boolean backpackCollapsed = false;

	private float playerPreviewYaw = 18.0f;
	private boolean rotatingPlayerModel = false;
	private double lastMouseX, lastMouseY;

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record ActionButton(Rect rect, @Nullable Rect clip, Runnable action) {
		boolean hits(double mx, double my) {
			return rect.contains(mx, my) && (clip == null || clip.contains(mx, my));
		}
	}

	private record InteractiveRegion(
		Rect rect,
		@Nullable Rect clipBounds,
		byte locType,
		int containerIndex,
		int gridWidth,
		int gridHeight,
		int regionCellSize,
		@Nullable TarkovNetworking.GridView view
	) {
		boolean hits(double mx, double my) {
			if (!rect.contains(mx, my)) return false;
			return clipBounds == null || clipBounds.contains(mx, my);
		}
	}

	private record HoveredTarget(
		byte loc,
		int idx,
		@Nullable UUID entryId,
		ItemStack stack,
		boolean rotated,
		@Nullable TarkovNetworking.GridView parentView
	) {
	}

	private record SelectedTarget(byte loc, int idx, @Nullable UUID entryId) {
		boolean matches(byte l, int i, @Nullable UUID e) {
			return loc == l && idx == i && Objects.equals(entryId, e);
		}
	}

	private record HealthMedSlot(byte loc, int idx, @Nullable UUID entryId, ItemStack stack, Rect rect) {
	}

	private record SelectedMedRef(byte loc, int idx, @Nullable UUID entryId) {
		boolean matches(HealthMedSlot slot) {
			return loc == slot.loc && idx == slot.idx && Objects.equals(entryId, slot.entryId);
		}
	}

	private static final class DragState {
		final byte srcLoc;
		final int srcIdx;
		@Nullable
		final UUID srcEntryId;
		final ItemStack stack;
		final int moveCount;
		final double startX;
		final double startY;
		boolean rotated;
		boolean active;

		DragState(
			byte srcLoc, int srcIdx, @Nullable UUID srcEntryId,
			ItemStack stack, boolean rotated, int moveCount,
			double startX, double startY
		) {
			this.srcLoc = srcLoc;
			this.srcIdx = srcIdx;
			this.srcEntryId = srcEntryId;
			this.stack = stack;
			this.rotated = rotated;
			this.moveCount = moveCount;
			this.startX = startX;
			this.startY = startY;
			this.active = false;
		}

		boolean isSource(byte loc, int idx, @Nullable UUID entryId) {
			return active && srcLoc == loc && srcIdx == idx && Objects.equals(srcEntryId, entryId);
		}
	}

	private record MenuOption(String label, Runnable action) {
	}

	private static final class ContextMenu {
		final int x;
		final int y;
		final int width;
		final int rowH = 17;
		final List<MenuOption> options;

		ContextMenu(int x, int y, int width, List<MenuOption> options) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.options = options;
		}

		int height() {
			return options.size() * rowH + 2;
		}

		boolean contains(double mx, double my) {
			return mx >= x && mx < x + width && my >= y && my < y + height();
		}
	}

	public TarkovInventoryScreen(TarkovScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		this.x = 0;
		this.y = 0;
		this.backgroundWidth = this.width;
		this.backgroundHeight = this.height;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.lastMouseX = mouseX;
		this.lastMouseY = mouseY;
		regions.clear();
		actionButtons.clear();
		stashFilterTooltip = null;
		healthMedStrip = null;

		boolean stash = isStash();
		if (stash) TarkovUiTheme.drawLobbyBackground(context, width, height);
		else context.fill(0, 0, width, height, TarkovUiTheme.BG_WORLD_OVERLAY);

		PlayerLoadout loadout = ClientLoadoutCache.getLoadout();
		TarkovNetworking.ScreenStateSnapshot snap = ClientLoadoutCache.getScreenState();
		if (stash && snap != null && snap.mode() != TarkovScreenHandler.MODE_STASH) snap = null;
		boolean hasExternal = handler.getMode() != TarkovScreenHandler.MODE_OWN_INVENTORY;

		renderTopBar(context, mouseX, mouseY);

		int bodyY = TarkovUiTheme.TOP_BAR_H + TarkovUiTheme.OUTER_PAD + (stash ? LobbyTopNavBar.HEIGHT : 0);
		int bottom = stash ? TarkovUiTheme.OUTER_PAD : TarkovUiTheme.QUICK_BAR_H + TarkovUiTheme.OUTER_PAD + 6;
		int bodyH = Math.max(stash ? 0 : 140, this.height - bodyY - bottom);

		floatingLayouts.clear();
		if (activeTab == TAB_HEALTH && client != null && client.player != null) {
			healthPartHitboxes.clear();
			quickHealHitboxes.clear();
			healthMedSlots.clear();
			int hx = TarkovUiTheme.OUTER_PAD;
			int hw = this.width - TarkovUiTheme.OUTER_PAD * 2;
			context.fill(hx, bodyY, hx + hw, bodyY + bodyH, inventoryColor(TarkovUiTheme.BG_PANEL));
			context.drawBorder(hx, bodyY, hw, bodyH, TarkovUiTheme.BORDER_PANEL);

			int medStripH = 38;
			int medStripY = bodyY + bodyH - TarkovUiTheme.STATS_BAR_H - medStripH - 4;
			availableHealthMeds = collectOwnMedicalItems(loadout, snap);
			List<HealthMedSlot> collectedMeds = availableHealthMeds;
			ItemStack selectedMedStack = resolveSelectedMedStack(collectedMeds);
			List<BodyHealthHudHelper.QuickHealMed> qMeds = new ArrayList<>(collectedMeds.size());
			for (HealthMedSlot ms : collectedMeds) {
				qMeds.add(new BodyHealthHudHelper.QuickHealMed(ms.loc(), ms.idx(), ms.entryId(), ms.stack()));
			}

			BodyHealthHudHelper.drawDetailedHealthTab(
				context, textRenderer, client.player,
				hx, bodyY, hw, medStripY - bodyY - 2,
				selectedMedStack, healthPartHitboxes, qMeds, quickHealHitboxes, mouseX, mouseY
			);
			drawMedUseStripIfActive(context, hx + 8, bodyY + 6);
			drawHealthMedicalStrip(context, hx + 6, medStripY, hw - 12, medStripH, collectedMeds, mouseX, mouseY);
			drawBottomStatsStrip(context, loadout, hx + 6, bodyY + bodyH - TarkovUiTheme.STATS_BAR_H - 2, hw - 12);
			leftColumnArea = null;
			centerScrollArea = null;
			rightScrollArea = null;
		} else {
			int availW = this.width - TarkovUiTheme.OUTER_PAD * 2 - TarkovUiTheme.COL_GAP * 2;
			int leftW = Math.max(142, Math.round(availW * 0.31f));
			int centerW = Math.max(150, Math.round(availW * 0.36f));
			int rightW = Math.max(120, availW - leftW - centerW);
			if (leftW + centerW + rightW > availW) {
				centerW = Math.max(140, availW - leftW - rightW);
			}
			if (stash) {
				var columns = LobbyLayoutMetrics.characterColumns(availW);
				rightW = columns.stash();
				leftW = columns.equipment();
				centerW = columns.storage();
			}
			cellSize = Math.max(stash ? 10 : 18, Math.min(28, (centerW - 22) / 6));

			int leftX = TarkovUiTheme.OUTER_PAD;
			int centerX = leftX + leftW + TarkovUiTheme.COL_GAP;
			int rightX = centerX + centerW + TarkovUiTheme.COL_GAP;

			leftColumnArea = new Rect(leftX, bodyY, leftW, bodyH);
			renderEquipmentColumn(context, leftColumnArea, loadout, mouseX, mouseY);

			centerScrollArea = new Rect(centerX, bodyY, centerW, bodyH);
			renderStorageColumn(context, centerScrollArea, loadout, snap, mouseX, mouseY);

			if (hasExternal && snap != null) {
				rightScrollArea = new Rect(rightX, bodyY, rightW, bodyH);
				renderExternalColumn(context, rightScrollArea, snap, mouseX, mouseY);
			} else {
				rightScrollArea = null;
			}
			drawMedUseStripIfActive(context, centerX + 6, bodyY + 3);
		}

		if (!stash) renderQuickUseBar(context, loadout, mouseX, mouseY);

		if (activeTab == TAB_GEAR && snap != null && !snap.openContainers().isEmpty()) {
			renderFloatingContainerWindows(context, snap, mouseX, mouseY);
		}

		// Floating layers (drag ghost z=320, modals z=420, context menu & tooltip z=500)
		if (drag != null && drag.active) {
			TarkovUiTheme.clearDepthBuffer(context);
			renderDragPreview(context, mouseX, mouseY);
		}
		for (TarkovInspectWindow win : inspectWindows) {
			win.render(context, textRenderer, mouseX, mouseY, this.width, this.height);
		}
		if (splitModal != null) {
			TarkovUiTheme.clearDepthBuffer(context);
			splitModal.render(context, textRenderer, mouseX, mouseY);
		}
		if (contextMenu != null) {
			TarkovUiTheme.clearDepthBuffer(context);
			renderContextMenu(context, mouseX, mouseY);
		} else if ((drag == null || !drag.active)
			&& !isOverAnyInspectWindow(mouseX, mouseY)
			&& (splitModal == null || !splitModal.contains(mouseX, mouseY))) {
			if (stashFilterTooltip != null) context.drawTooltip(textRenderer, stashFilterTooltip, mouseX, mouseY);
			else renderHoverTooltip(context, mouseX, mouseY);
		}
	}

	private boolean isStash() {
		return handler.getMode() == TarkovScreenHandler.MODE_STASH;
	}

	private static Text lobbyLabel(String key, String fallback) {
		return Text.translatableWithFallback("screen.tarkovcraft.lobby." + key, fallback);
	}

	private LobbyTopNavBar.Status lobbyStatus() {
		int flags = handler.stashFlags();
		BodyPartHealth health = ClientLoadoutCache.getBodyHealth();
		return new LobbyTopNavBar.Status(client.player.getName().getString(), handler.stashRubles(),
			Math.round(health.getTotalHp()), Math.round(health.getTotalEffectiveMaxHp()), health.needsTreatment(),
			(flags & TarkovScreenHandler.STASH_SERVICE) != 0, handler.stashRaidSeconds(), handler.stashScavSeconds(),
			(flags & TarkovScreenHandler.STASH_READY) != 0, (flags & TarkovScreenHandler.STASH_CAN_EDIT) != 0);
	}

	private void sendStashButton(int action) {
		if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, action);
	}

	@Override
	public void close() {
		if (isStash()) sendStashButton(TarkovScreenHandler.STASH_PLAY);
		else super.close();
	}

	private void lobbyNavAction(LobbyTopNavBar.Action action) {
		LobbyTopNavBar.activate(client, this, handler.syncId, action);
	}

	private void renderTopBar(DrawContext context, int mouseX, int mouseY) {
		if (isStash() && client != null && client.player != null) {
			LobbyTopNavBar.render(context, textRenderer, width, mouseX, mouseY, LobbyTopNavBar.Tab.CHARACTER, lobbyStatus());
			int y = LobbyTopNavBar.HEIGHT;
			context.fill(0, y, width, y + TarkovUiTheme.TOP_BAR_H, inventoryColor(TarkovUiTheme.BG_TOP_BAR));
			tabGearBtn = new Rect(8, y + 3, 86, TarkovUiTheme.TOP_BAR_H - 5);
			tabHealthBtn = new Rect(98, y + 3, 86, TarkovUiTheme.TOP_BAR_H - 5);
			closeBtn = null;
			drawTopTab(context, tabGearBtn, Text.translatable("tab.tarkovcraft.gear").getString(), activeTab == TAB_GEAR, mouseX, mouseY);
			drawTopTab(context, tabHealthBtn, Text.translatable("tab.tarkovcraft.health").getString(), activeTab == TAB_HEALTH, mouseX, mouseY);
			return;
		}
		context.fill(0, 0, this.width, TarkovUiTheme.TOP_BAR_H, TarkovUiTheme.BG_TOP_BAR);
		context.fill(0, TarkovUiTheme.TOP_BAR_H, this.width, TarkovUiTheme.TOP_BAR_H + 1, TarkovUiTheme.BORDER_PANEL);

		tabGearBtn = new Rect(8, 3, 86, TarkovUiTheme.TOP_BAR_H - 5);
		tabHealthBtn = new Rect(98, 3, 86, TarkovUiTheme.TOP_BAR_H - 5);
		String backTxt = Text.translatable("ui.tarkovcraft.back_esc").getString();
		int backW = Math.max(66, Math.round(textRenderer.getWidth(backTxt) * TarkovUiTheme.TEXT_SCALE_HEADER) + 14);
		closeBtn = new Rect(this.width - backW - 8, 3, backW, TarkovUiTheme.TOP_BAR_H - 5);

		drawTopTab(context, tabGearBtn, Text.translatable("tab.tarkovcraft.gear").getString(), activeTab == TAB_GEAR, mouseX, mouseY);
		drawTopTab(context, tabHealthBtn, Text.translatable("tab.tarkovcraft.health").getString(), activeTab == TAB_HEALTH, mouseX, mouseY);
		drawTopTab(context, closeBtn, backTxt, false, mouseX, mouseY);

		if (this.width >= 520) {
			String interactLbl = TarkovCraftClient.getShortKeyLabel(TarkovCraftClient.interactKey);
			String hint = (drag != null && drag.active)
				? "[R] Rotate  |  Drop on valid cell"
				: "[" + interactLbl + "] Quick  |  Ctrl+" + interactLbl + " Equip  |  RMB Menu  |  Del Drop";
			TarkovUiTheme.drawCenteredFlatText(context, textRenderer, hint, this.width / 2, 9,
				(drag != null && drag.active) ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.TEXT_MUTED,
				TarkovUiTheme.TEXT_SCALE_SMALL);
		}
	}

	private void drawTopTab(DrawContext context, Rect r, String label, boolean active, int mouseX, int mouseY) {
		boolean hov = r.contains(mouseX, mouseY);
		int bg = active ? 0xDD22262C : (hov ? 0xCC1B1F24 : 0x00000000);
		int textCol = active ? TarkovUiTheme.ACCENT_SAND : (hov ? TarkovUiTheme.TEXT_PRIMARY : TarkovUiTheme.TEXT_SECONDARY);
		if (bg != 0) {
			context.fill(r.x, r.y, r.x + r.w, r.y + r.h, bg);
		}
		if (active) {
			context.fill(r.x, r.y + r.h - 2, r.x + r.w, r.y + r.h, TarkovUiTheme.ACCENT_SAND);
		}
		TarkovUiTheme.drawCenteredFlatText(context, textRenderer, label, r.x + r.w / 2, r.y + 6, textCol, TarkovUiTheme.TEXT_SCALE_HEADER);
	}

	private void renderEquipmentColumn(DrawContext context, Rect area, PlayerLoadout loadout, int mouseX, int mouseY) {
		context.fill(area.x, area.y, area.x + area.w, area.y + area.h, inventoryColor(TarkovUiTheme.BG_PANEL));
		context.drawBorder(area.x, area.y, area.w, area.h, TarkovUiTheme.BORDER_PANEL);

		int pad = 6;
		int weaponH = isStash() ? Math.max(16, Math.min(28, (area.h - 105) / 3)) : 28;
		int weaponStep = weaponH + 3;
		int weaponZoneH = weaponStep * 3 + 5;
		int upperH = Math.max(74, area.h - TarkovUiTheme.STATS_BAR_H - weaponZoneH - pad * 2);

		// Live 3D Player Model behind equipment slots
		if (client != null && client.player != null) {
			int modelSize = Math.max(28, Math.min(64, Math.round(upperH * 0.40f)));
			int modelX = area.x + area.w / 2;
			int modelY = area.y + Math.max(76, Math.round(upperH * 0.92f));

			boolean invisible = client.player.isInvisible();
			float savedBodyYaw = client.player.bodyYaw;
			float savedYaw = client.player.getYaw();
			float savedPitch = client.player.getPitch();
			float savedPrevHeadYaw = client.player.prevHeadYaw;
			float savedHeadYaw = client.player.headYaw;

			context.enableScissor(area.x + 1, area.y + 1, area.x + area.w - 1, area.y + area.h - TarkovUiTheme.STATS_BAR_H - 2);
			try {
				if (isStash()) client.player.setInvisible(false);
				float previewYaw = 180.0f + playerPreviewYaw;
				client.player.bodyYaw = previewYaw;
				client.player.setYaw(previewYaw);
				client.player.setPitch(0.0f);
				client.player.prevHeadYaw = previewYaw;
				client.player.headYaw = previewYaw;
				InventoryScreen.drawEntity(
					context, modelX, modelY, modelSize,
					new Quaternionf().rotateZ((float) Math.PI), null, client.player
				);
			} finally {
				if (isStash()) client.player.setInvisible(invisible);
				client.player.bodyYaw = savedBodyYaw;
				client.player.setYaw(savedYaw);
				client.player.setPitch(savedPitch);
				client.player.prevHeadYaw = savedPrevHeadYaw;
				client.player.headYaw = savedHeadYaw;
				context.disableScissor();
			}
		}

		// Render all equipment slots cleanly above the 3D player model
		context.getMatrices().push();
		context.getMatrices().translate(0.0f, 0.0f, TarkovUiTheme.Z_SLOTS);

		int sideW = Math.max(isStash() ? 20 : 48, Math.min(68, (area.w - 18) / 3));
		int sqH = Math.max(32, Math.min(40, (upperH - 12) / 2));
		int topY = area.y + pad;
		int row2Y = topY + sqH + 4;
		int rightColX = area.x + area.w - pad - sideW;

		drawEquipSlot(context, area.x + pad, topY, sideW, sqH, EquipmentSlotType.HELMET, "HEADWEAR", null, loadout, false, null, mouseX, mouseY);
		drawEquipSlot(context, rightColX, topY, sideW, sqH, EquipmentSlotType.FACE_MASK, "FACE COVER", null, loadout, false, null, mouseX, mouseY);
		drawEquipSlot(context, area.x + pad, row2Y, sideW, sqH, EquipmentSlotType.BODY_ARMOR, "BODY ARMOR", null, loadout, false, null, mouseX, mouseY);
		drawEquipSlot(context, rightColX, row2Y, sideW, sqH, EquipmentSlotType.RIG, "TACTICAL RIG", null, loadout, false, null, mouseX, mouseY);

		int wY = area.y + pad + upperH + 4;
		int headsetX = area.x + pad + sideW + 3;
		int headsetWidth = rightColX - headsetX - 3;
		drawEquipSlot(context, headsetX, topY, headsetWidth, sqH, EquipmentSlotType.HEADSET,
			Text.translatable("ui.tarkovcraft.headset").getString(), null, loadout, false, null, mouseX, mouseY);
		if (isStash()) {
			drawEquipSlot(context, headsetX, row2Y, headsetWidth, sqH, EquipmentSlotType.BACKPACK,
				Text.translatable("ui.tarkovcraft.backpack").getString(), null, loadout, false, null, mouseX, mouseY);
		}
		int fullW = area.w - pad * 2;
		int halfW = (fullW - 4) / 2;
		String k1 = client != null ? TarkovCraftClient.getShortKeyLabel(client.options.hotbarKeys[0]) : "1";
		String k2 = client != null ? TarkovCraftClient.getShortKeyLabel(client.options.hotbarKeys[1]) : "2";
		String k3 = client != null ? TarkovCraftClient.getShortKeyLabel(client.options.hotbarKeys[2]) : "3";
		String kM = TarkovCraftClient.getShortKeyLabel(TarkovCraftClient.toggleMeleeKey);

		drawEquipSlot(context, area.x + pad, wY, fullW, weaponH, EquipmentSlotType.PRIMARY_1, "ON SLING", k1, loadout, false, null, mouseX, mouseY);
		drawEquipSlot(context, area.x + pad, wY + weaponStep, fullW, weaponH, EquipmentSlotType.PRIMARY_2, "ON BACK", k2, loadout, false, null, mouseX, mouseY);
		drawEquipSlot(context, area.x + pad, wY + weaponStep * 2, halfW, weaponH, EquipmentSlotType.PISTOL, "HOLSTER", k3, loadout, false, null, mouseX, mouseY);
		drawEquipSlot(context, area.x + pad + halfW + 4, wY + weaponStep * 2, fullW - halfW - 4, weaponH, EquipmentSlotType.MELEE, "SCABBARD", kM, loadout, false, null, mouseX, mouseY);

		drawBottomStatsStrip(context, loadout, area.x + 5, area.y + area.h - TarkovUiTheme.STATS_BAR_H - 3, area.w - 10);

		context.getMatrices().pop();
	}

	private void drawBottomStatsStrip(DrawContext context, PlayerLoadout loadout, int x, int y, int w) {
		context.fill(x, y, x + w, y + TarkovUiTheme.STATS_BAR_H, 0xCC0A0C0E);
		context.drawBorder(x, y, w, TarkovUiTheme.STATS_BAR_H, TarkovUiTheme.BORDER_PANEL);
		if (client == null || client.player == null) return;

		var bodyHealth = ClientLoadoutCache.getBodyHealth();
		int hpVal = Math.round(bodyHealth.getTotalHp());
		int food = client.player.getHungerManager().getFoodLevel() * 5;
		int sat = Math.round(client.player.getHungerManager().getSaturationLevel() * 5.0f);
		String weight = String.format(Locale.ROOT, "%.1fkg", ClientLoadoutCache.getCachedWeightKg());

		int ty = y + 6;
		TarkovUiTheme.drawFlatText(context, textRenderer, weight, x + 4, ty, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		TarkovUiTheme.drawFlatText(context, textRenderer, "+" + hpVal, x + Math.max(38, w / 4), ty,
			BodyHealthHudHelper.hpToColor(bodyHealth.getTotalRatio()), TarkovUiTheme.TEXT_SCALE_SMALL);
		TarkovUiTheme.drawFlatText(context, textRenderer, "E:" + food, x + Math.max(74, w / 2 + 4), ty,
			0xFF63B3ED, TarkovUiTheme.TEXT_SCALE_SMALL);
		if (w >= 132) {
			TarkovUiTheme.drawRightAlignedFlatText(context, textRenderer, "S:" + sat, x + w - 4, ty,
				0xFF9AE6B4, TarkovUiTheme.TEXT_SCALE_SMALL);
		}
	}

	private void renderStorageColumn(
		DrawContext context, Rect area, PlayerLoadout loadout,
		@Nullable TarkovNetworking.ScreenStateSnapshot snap, int mouseX, int mouseY
	) {
		context.fill(area.x, area.y, area.x + area.w, area.y + area.h, inventoryColor(TarkovUiTheme.BG_PANEL));
		context.drawBorder(area.x, area.y, area.w, area.h, TarkovUiTheme.BORDER_PANEL);

		context.enableScissor(area.x + 1, area.y + 1, area.x + area.w - 1, area.y + area.h - 1);

		int pad = 6;
		int curY = area.y + pad - centerScrollY;
		int startY = curY;

		// 1. TACTICAL RIG SECTION
		ItemStack rigStack = loadout.getEquipment(EquipmentSlotType.RIG);
		if (!rigStack.isEmpty() && snap != null && !snap.ownRigViews().isEmpty()) {
			rigSectionOffsetY = curY - startY;
			String rigTitle = Text.translatable("ui.tarkovcraft.rig").getString() + " // " + rigStack.getName().getString().toUpperCase();
			curY = drawCollapsibleSectionHeader(
				context, area, pad, curY, rigTitle, rigCollapsed,
				() -> rigCollapsed = !rigCollapsed,
				() -> ClientNetworkSender.sendQuickEquip(TarkovNetworking.LOC_EQUIPMENT, EquipmentSlotType.RIG.ordinal(), null),
				mouseX, mouseY
			);
			if (!rigCollapsed) {
				curY = drawRigViewsBody(context, area, pad, curY, TarkovNetworking.LOC_RIG_GRID, snap.ownRigViews(), snap, mouseX, mouseY);
			} else {
				curY += 4;
			}
		}

		// 2. POCKETS SECTION
		TarkovUiTheme.drawFlatText(context, textRenderer, Text.translatable("ui.tarkovcraft.pockets").getString(),
			area.x + pad, curY + 1, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		curY += 11;
		int pCell = Math.min(cellSize, Math.max(18, (area.w - pad * 2 - 18) / PlayerLoadout.POCKET_COUNT));
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			int px = area.x + pad + i * (pCell + 3);
			Rect r = new Rect(px, curY, pCell, pCell);
			regions.add(new InteractiveRegion(r, area, TarkovNetworking.LOC_POCKET, i, 1, 1, pCell, null));
			String keyBadge = client != null ? TarkovCraftClient.getShortKeyLabel(client.options.hotbarKeys[i + 3]) : String.valueOf(i + 4);
			drawSingleCellSlot(context, r, TarkovNetworking.LOC_POCKET, i, loadout.getPocket(i), keyBadge, mouseX, mouseY);
		}
		curY += pCell + 10;

		var secure = loadout.getSecureContainer();
		var secureView = TarkovNetworking.GridView.fromContainer(secure, secure.getContainerId(), null, null, false);
		int secureX = area.x + pad;
		TarkovUiTheme.drawFlatText(context, textRenderer,
			Text.translatable("ui.tarkovcraft.secure_container", secure.getWidth(), secure.getHeight()).getString(),
			secureX, curY + 1, TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
		curY += 11;
		drawGridView(context, secureX, curY, area, TarkovNetworking.LOC_SECURE_CONTAINER, 0,
			pCell, secureView, snap, true, mouseX, mouseY);
		context.drawBorder(secureX - 1, curY - 1, secure.getWidth() * pCell + 2, secure.getHeight() * pCell + 2,
			TarkovUiTheme.ACCENT_SAND);
		curY += secure.getHeight() * pCell + 10;

		// 3. BACKPACK SECTION
		ItemStack bpStack = loadout.getEquipment(EquipmentSlotType.BACKPACK);
		backpackSectionOffsetY = curY - startY;
		if (!bpStack.isEmpty()) {
			String bpTitle = Text.translatable("ui.tarkovcraft.backpack").getString() + " // " + bpStack.getName().getString().toUpperCase();
			curY = drawCollapsibleSectionHeader(
				context, area, pad, curY, bpTitle, backpackCollapsed,
				() -> backpackCollapsed = !backpackCollapsed,
				() -> ClientNetworkSender.sendQuickEquip(TarkovNetworking.LOC_EQUIPMENT, EquipmentSlotType.BACKPACK.ordinal(), null),
				mouseX, mouseY
			);
		} else {
			TarkovUiTheme.drawFlatText(context, textRenderer, Text.translatable("ui.tarkovcraft.backpack").getString(),
				area.x + pad, curY + 1, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
			curY += 11;
		}

		if (bpStack.isEmpty() || !backpackCollapsed) {
			int bpSlotSize = Math.max(32, Math.min(40, cellSize + 12));
			drawEquipSlot(context, area.x + pad, curY, bpSlotSize, bpSlotSize, EquipmentSlotType.BACKPACK, "", null, loadout, false, area, mouseX, mouseY);

			if (!bpStack.isEmpty() && snap != null && snap.ownBackpackView() != null) {
				TarkovNetworking.GridView bpView = snap.ownBackpackView();
				int gridX = area.x + pad + bpSlotSize + 6;
				if (gridX + bpView.width() * cellSize > area.x + area.w - pad - 4) {
					curY += bpSlotSize + 5;
					drawGridView(context, area.x + pad, curY, area, TarkovNetworking.LOC_BACKPACK_GRID, 0, cellSize, bpView, snap, false, mouseX, mouseY);
					curY += bpView.height() * cellSize + 10;
				} else {
					drawGridView(context, gridX, curY, area, TarkovNetworking.LOC_BACKPACK_GRID, 0, cellSize, bpView, snap, false, mouseX, mouseY);
					curY += Math.max(bpSlotSize, bpView.height() * cellSize) + 10;
				}
			} else {
				curY += bpSlotSize + 10;
			}
		} else {
			curY += 4;
		}

		context.disableScissor();

		int contentH = curY - startY;
		centerMaxScroll = Math.max(0, contentH - area.h + pad);
		centerScrollY = Math.max(0, Math.min(centerScrollY, centerMaxScroll));
		drawScrollbar(context, area, centerScrollY, centerMaxScroll);
	}

	private int drawCollapsibleSectionHeader(
		DrawContext context, Rect area, int pad, int curY,
		String title, boolean collapsed, Runnable onToggle, Runnable onUnequip,
		int mouseX, int mouseY
	) {
		int headerW = area.w - pad * 2 - 4;
		context.fill(area.x + pad, curY, area.x + pad + headerW, curY + 13, 0xCC14171B);
		context.drawBorder(area.x + pad, curY, headerW, 13, TarkovUiTheme.BORDER_PANEL);

		String trimmed = textRenderer.trimToWidth(title, Math.round((headerW - 36) / TarkovUiTheme.TEXT_SCALE_SMALL));
		TarkovUiTheme.drawFlatText(context, textRenderer, trimmed, area.x + pad + 4, curY + 3,
			TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);

		// [-] / [+] toggle button
		Rect toggleRect = new Rect(area.x + pad + headerW - 28, curY + 1, 13, 11);
		boolean tHov = toggleRect.contains(mouseX, mouseY) && area.contains(mouseX, mouseY);
		context.fill(toggleRect.x, toggleRect.y, toggleRect.x + toggleRect.w, toggleRect.y + toggleRect.h,
			tHov ? TarkovUiTheme.HOVER_MENU_BG : 0xFF1B1F24);
		TarkovUiTheme.drawCenteredFlatText(context, textRenderer, collapsed ? "+" : "-", toggleRect.x + 6, toggleRect.y + 2,
			tHov ? TarkovUiTheme.HOVER_MENU_TEXT : TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		actionButtons.add(new ActionButton(toggleRect, area, onToggle));

		// [X] unequip button
		Rect unequipRect = new Rect(area.x + pad + headerW - 14, curY + 1, 13, 11);
		boolean uHov = unequipRect.contains(mouseX, mouseY) && area.contains(mouseX, mouseY);
		context.fill(unequipRect.x, unequipRect.y, unequipRect.x + unequipRect.w, unequipRect.y + unequipRect.h,
			uHov ? TarkovUiTheme.HOVER_MENU_BG : 0xFF281A1A);
		TarkovUiTheme.drawCenteredFlatText(context, textRenderer, "X", unequipRect.x + 6, unequipRect.y + 2,
			uHov ? TarkovUiTheme.HOVER_MENU_TEXT : 0xFFE57373, TarkovUiTheme.TEXT_SCALE_SMALL);
		actionButtons.add(new ActionButton(unequipRect, area, onUnequip));

		return curY + 15;
	}

	private void renderExternalColumn(
		DrawContext context, Rect area, TarkovNetworking.ScreenStateSnapshot snap, int mouseX, int mouseY
	) {
		context.fill(area.x, area.y, area.x + area.w, area.y + area.h, inventoryColor(TarkovUiTheme.BG_PANEL));
		context.drawBorder(area.x, area.y, area.w, area.h, TarkovUiTheme.BORDER_PANEL);

		boolean stash = isStash();
		int pad = 6;
		int headerH = stash ? 36 : 18;
		context.fill(area.x + 1, area.y + 1, area.x + area.w - 1, area.y + headerH, 0xDD13161A);
		context.fill(area.x + 1, area.y + headerH, area.x + area.w - 1, area.y + headerH + 1, TarkovUiTheme.BORDER_PANEL);

		String header = stash ? lobbyLabel("stash", "Stash").getString()
			: snap.externalTitle().isEmpty() ? "LOOT" : snap.externalTitle().toUpperCase(Locale.ROOT);
		UUID firstUnsearched = stash ? null : findFirstUnsearchedTarget(snap);
		if (stash) drawStashControls(context, area, mouseX, mouseY);
		int btnW = stash ? area.w / 3 : firstUnsearched != null ? 54 : 0;
		String trimmedHeader = textRenderer.trimToWidth(header, Math.round((area.w - pad * 2 - btnW - 8) / TarkovUiTheme.TEXT_SCALE_SMALL));
		TarkovUiTheme.drawFlatText(context, textRenderer, trimmedHeader, area.x + pad, area.y + 6,
			TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);

		if (firstUnsearched != null) {
			String btnTxt = Text.translatable("ui.tarkovcraft.search_btn").getString();
			int bw = Math.max(48, Math.round(textRenderer.getWidth(btnTxt) * TarkovUiTheme.TEXT_SCALE_SMALL) + 8);
			Rect searchBtn = new Rect(area.x + area.w - bw - 5, area.y + 3, bw, 12);
			boolean sHov = searchBtn.contains(mouseX, mouseY);
			context.fill(searchBtn.x, searchBtn.y, searchBtn.x + searchBtn.w, searchBtn.y + searchBtn.h,
				sHov ? TarkovUiTheme.HOVER_MENU_BG : 0xFF283226);
			context.drawBorder(searchBtn.x, searchBtn.y, searchBtn.w, searchBtn.h,
				sHov ? TarkovUiTheme.BORDER_HOVER : TarkovUiTheme.ACCENT_SAND);
			TarkovUiTheme.drawCenteredFlatText(context, textRenderer, btnTxt, searchBtn.x + searchBtn.w / 2, searchBtn.y + 3,
				sHov ? TarkovUiTheme.HOVER_MENU_TEXT : TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
			actionButtons.add(new ActionButton(searchBtn, area, () -> ClientNetworkSender.sendRequestSearch(firstUnsearched)));
		}

		Rect scrollClip = new Rect(area.x, area.y + headerH + 1, area.w, area.h - headerH - 1);
		context.enableScissor(scrollClip.x + 1, scrollClip.y, scrollClip.x + scrollClip.w - 1, scrollClip.y + scrollClip.h - 1);

		int curY = scrollClip.y + pad - rightScrollY;
		int startY = curY;

		int maxCols = snap.externalGridView() != null ? Math.max(6, snap.externalGridView().width()) : 6;
		int minCell = maxCols > 6 ? 10 : 12;
		int extCell = stash ? LobbyLayoutMetrics.stashCell(area.w, maxCols)
			: Math.max(minCell, Math.min(cellSize, (area.w - pad * 2 - 8) / maxCols));

		if (handler.getMode() == TarkovScreenHandler.MODE_CORPSE && snap.corpseLoadout() != null) {
			PlayerLoadout corpseLo = snap.corpseLoadout();
			int halfW = (area.w - pad * 2 - 8) / 2;

			drawEquipSlot(context, area.x + pad, curY, halfW, 26, EquipmentSlotType.HELMET, "HELM", null, corpseLo, true, scrollClip, mouseX, mouseY);
			drawEquipSlot(context, area.x + pad + halfW + 4, curY, halfW, 26, EquipmentSlotType.FACE_MASK, "FACE COVER", null, corpseLo, true, scrollClip, mouseX, mouseY);
			curY += 29;
			drawEquipSlot(context, area.x + pad, curY, halfW, 26, EquipmentSlotType.BODY_ARMOR, "ARMOR", null, corpseLo, true, scrollClip, mouseX, mouseY);
			drawEquipSlot(context, area.x + pad + halfW + 4, curY, halfW, 26, EquipmentSlotType.RIG, "RIG", null, corpseLo, true, scrollClip, mouseX, mouseY);
			curY += 29;
			drawEquipSlot(context, area.x + pad, curY, halfW, 26, EquipmentSlotType.BACKPACK, "BACKPACK", null, corpseLo, true, scrollClip, mouseX, mouseY);
			drawEquipSlot(context, area.x + pad + halfW + 4, curY, halfW, 26, EquipmentSlotType.MELEE, "MELEE", null, corpseLo, true, scrollClip, mouseX, mouseY);
			curY += 29;
			drawEquipSlot(context, area.x + pad, curY, halfW, 26, EquipmentSlotType.PRIMARY_1, "PRIMARY 1", null, corpseLo, true, scrollClip, mouseX, mouseY);
			drawEquipSlot(context, area.x + pad + halfW + 4, curY, halfW, 26, EquipmentSlotType.PRIMARY_2, "PRIMARY 2", null, corpseLo, true, scrollClip, mouseX, mouseY);
			curY += 29;
			drawEquipSlot(context, area.x + pad, curY, halfW, 26, EquipmentSlotType.PISTOL, "HOLSTER", null, corpseLo, true, scrollClip, mouseX, mouseY);
			drawEquipSlot(context, area.x + pad + halfW + 4, curY, halfW, 26, EquipmentSlotType.HEADSET,
				Text.translatable("ui.tarkovcraft.headset").getString(), null, corpseLo, true, scrollClip, mouseX, mouseY);
			curY += 32;

			if (snap.externalGridView() != null) {
				TarkovUiTheme.drawFlatText(context, textRenderer, Text.translatable("ui.tarkovcraft.pockets").getString(),
					area.x + pad, curY, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
				curY += 10;
				drawGridView(context, area.x + pad, curY, scrollClip, TarkovNetworking.LOC_EXTERNAL_GRID, 0, extCell, snap.externalGridView(), snap, false, mouseX, mouseY);
				curY += snap.externalGridView().height() * extCell + 8;
			}

			if (!snap.corpseRigViews().isEmpty()) {
				ItemStack cRig = corpseLo.getEquipment(EquipmentSlotType.RIG);
				TarkovUiTheme.drawFlatText(context, textRenderer,
					Text.translatable("ui.tarkovcraft.rig").getString() + " // " + cRig.getName().getString().toUpperCase(),
					area.x + pad, curY, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
				curY += 10;
				curY = drawRigViewsBody(context, scrollClip, pad, curY, TarkovNetworking.LOC_CORPSE_RIG_GRID, snap.corpseRigViews(), snap, mouseX, mouseY);
			}

			if (snap.corpseBackpackView() != null) {
				ItemStack cBp = corpseLo.getEquipment(EquipmentSlotType.BACKPACK);
				TarkovUiTheme.drawFlatText(context, textRenderer,
					Text.translatable("ui.tarkovcraft.backpack").getString() + " // " + cBp.getName().getString().toUpperCase(),
					area.x + pad, curY, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
				curY += 10;
				drawGridView(context, area.x + pad, curY, scrollClip, TarkovNetworking.LOC_CORPSE_BACKPACK_GRID, 0, extCell, snap.corpseBackpackView(), snap, false, mouseX, mouseY);
				curY += snap.corpseBackpackView().height() * extCell + 10;
			}
		} else if (handler.getMode() == TarkovScreenHandler.MODE_GROUND_ITEM) {
			if (snap.externalGridView() != null) {
				drawGridView(context, area.x + pad, curY, scrollClip, TarkovNetworking.LOC_EXTERNAL_GRID, 0, extCell, snap.externalGridView(), snap, false, mouseX, mouseY);
				curY += snap.externalGridView().height() * extCell + 10;
			} else if (!snap.corpseRigViews().isEmpty()) {
				curY = drawRigViewsBody(context, scrollClip, pad, curY, TarkovNetworking.LOC_CORPSE_RIG_GRID, snap.corpseRigViews(), snap, mouseX, mouseY);
			}
		} else if (snap.externalGridView() != null) {
			drawGridView(context, area.x + pad, curY, scrollClip, TarkovNetworking.LOC_EXTERNAL_GRID, 0, extCell, snap.externalGridView(), snap, false, mouseX, mouseY);
			curY += snap.externalGridView().height() * extCell + 10;
		}

		context.disableScissor();

		int contentH = curY - startY;
		rightMaxScroll = Math.max(0, contentH - scrollClip.h + pad);
		rightScrollY = Math.max(0, Math.min(rightScrollY, rightMaxScroll));
		drawScrollbar(context, scrollClip, rightScrollY, rightMaxScroll);
	}

	private void drawStashControls(DrawContext context, Rect area, int mouseX, int mouseY) {
		int actionWidth = (area.w - 16) / 3;
		drawStashButton(context, new Rect(area.x + area.w - actionWidth * 2 - 8, area.y + 3, actionWidth, 13),
			lobbyLabel("stash_store_all", "Store all"), false,
			() -> sendStashButton(TarkovScreenHandler.STASH_STORE_ALL), mouseX, mouseY);
		drawStashButton(context, new Rect(area.x + area.w - actionWidth - 5, area.y + 3, actionWidth, 13),
			lobbyLabel("stash_sort", "Sort"), false,
			() -> sendStashButton(TarkovScreenHandler.STASH_SORT), mouseX, mouseY);
		int step = (area.w - 10) / StashFilter.values().length;
		for (StashFilter filter : StashFilter.values()) {
			Rect rect = new Rect(area.x + 5 + step * filter.ordinal(), area.y + 20, step - 2, 13);
			drawStashButton(context, rect, Text.literal(filter.symbol), stashFilter == filter,
				() -> stashFilter = filter, mouseX, mouseY);
			if (rect.contains(mouseX, mouseY)) stashFilterTooltip = filter.label();
		}
	}

	private void drawStashButton(DrawContext context, Rect rect, Text text, boolean selected,
			Runnable action, int mouseX, int mouseY) {
		boolean hover = rect.contains(mouseX, mouseY);
		context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h,
			hover ? TarkovUiTheme.HOVER_MENU_BG : TarkovUiTheme.BG_SLOT);
		context.drawBorder(rect.x, rect.y, rect.w, rect.h,
			selected ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.BORDER_PANEL);
		float scale = TarkovUiTheme.TEXT_SCALE_SMALL;
		String label = TarkovUiTheme.trimWithEllipsis(textRenderer, text.getString(), rect.w - 4, scale);
		TarkovUiTheme.drawCenteredFlatText(context, textRenderer, label, rect.x + rect.w / 2, rect.y + 3,
			selected ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.TEXT_PRIMARY, scale);
		actionButtons.add(new ActionButton(rect, null, action));
	}

	private int drawRigViewsBody(
		DrawContext context, Rect clip, int pad, int curY,
		byte locType, List<TarkovNetworking.GridView> rigViews,
		TarkovNetworking.ScreenStateSnapshot snap, int mouseX, int mouseY
	) {
		TarkovNetworking.GridView first = rigViews.get(0);
		if (!first.gridRevealed()) {
			boolean scanningRig = snap.searchStage() == 1 && first.searchTargetId().equals(snap.searchTargetId());
			int barW = clip.w - pad * 2 - 6;
			context.fill(clip.x + pad, curY, clip.x + pad + barW, curY + 11, 0xFF111418);
			context.drawBorder(clip.x + pad, curY, barW, 11, scanningRig ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.BORDER_SLOT);
			if (scanningRig) {
				context.fill(clip.x + pad + 1, curY + 1, clip.x + pad + 1 + Math.round((barW - 2) * snap.searchProgress()), curY + 10, 0xAA8A733E);
				TarkovUiTheme.drawCenteredFlatText(context, textRenderer, Text.translatable("ui.tarkovcraft.searching").getString(),
					clip.x + pad + barW / 2, curY + 3, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
			} else {
				TarkovUiTheme.drawCenteredFlatText(context, textRenderer, Text.translatable("ui.tarkovcraft.unsearched").getString(),
					clip.x + pad + barW / 2, curY + 3, TarkovUiTheme.TEXT_MUTED, TarkovUiTheme.TEXT_SCALE_SMALL);
			}
			curY += 14;
		}

		int rCell = Math.max(18, Math.min(cellSize, (clip.w - pad * 2 - 10) / 5));
		int rx = clip.x + pad;
		int rowMaxH = 0;
		for (int i = 0; i < rigViews.size(); i++) {
			TarkovNetworking.GridView gv = rigViews.get(i);
			int gw = gv.width() * rCell;
			int gh = gv.height() * rCell;
			if (rx + gw > clip.x + clip.w - pad - 4 && rx > clip.x + pad) {
				rx = clip.x + pad;
				curY += rowMaxH + 4;
				rowMaxH = 0;
			}
			drawGridView(context, rx, curY, clip, locType, i, rCell, gv, snap, true, mouseX, mouseY);
			rx += gw + 4;
			rowMaxH = Math.max(rowMaxH, gh);
		}
		return curY + rowMaxH + 8;
	}

	private void renderQuickUseBar(DrawContext context, PlayerLoadout loadout, int mouseX, int mouseY) {
		int qSlot = Math.max(18, Math.min(24, (this.width - 60) / 11));
		int gap = 2;
		int groupGap = 8;
		int slotsW = qSlot + groupGap + (3 * qSlot + 2 * gap) + groupGap + (6 * qSlot + 5 * gap);
		int labelW = this.width >= 480 ? 64 : 0;
		int barW = slotsW + labelW + 14;
		int barH = TarkovUiTheme.QUICK_BAR_H;
		int barX = (this.width - barW) / 2;
		int barY = this.height - barH - 4;

		context.fill(barX, barY, barX + barW, barY + barH, TarkovUiTheme.BG_QUICK_BAR);
		context.drawBorder(barX, barY, barW, barH, TarkovUiTheme.BORDER_PANEL);

		int sx = barX + 6;
		if (labelW > 0) {
			TarkovUiTheme.drawFlatText(context, textRenderer, Text.translatable("ui.tarkovcraft.quick_use").getString(),
				sx, barY + 11, TarkovUiTheme.TEXT_MUTED, TarkovUiTheme.TEXT_SCALE_SMALL);
			sx += labelW;
		}
		int sy = barY + (barH - qSlot) / 2;

		String meleeKey = TarkovCraftClient.getShortKeyLabel(TarkovCraftClient.toggleMeleeKey);
		Rect meleeRect = new Rect(sx, sy, qSlot, qSlot);
		regions.add(new InteractiveRegion(meleeRect, null, TarkovNetworking.LOC_EQUIPMENT, EquipmentSlotType.MELEE.ordinal(), 1, 1, qSlot, null));
		drawSingleCellSlot(context, meleeRect, TarkovNetworking.LOC_EQUIPMENT, EquipmentSlotType.MELEE.ordinal(),
			loadout.getEquipment(EquipmentSlotType.MELEE), meleeKey, mouseX, mouseY);
		sx += qSlot + groupGap;

		EquipmentSlotType[] weaponSlots = {EquipmentSlotType.PRIMARY_1, EquipmentSlotType.PRIMARY_2, EquipmentSlotType.PISTOL};
		for (int i = 0; i < 3; i++) {
			Rect wr = new Rect(sx, sy, qSlot, qSlot);
			int slotOrd = weaponSlots[i].ordinal();
			regions.add(new InteractiveRegion(wr, null, TarkovNetworking.LOC_EQUIPMENT, slotOrd, 1, 1, qSlot, null));
			String keyLbl = client != null ? TarkovCraftClient.getShortKeyLabel(client.options.hotbarKeys[i]) : String.valueOf(i + 1);
			drawSingleCellSlot(context, wr, TarkovNetworking.LOC_EQUIPMENT, slotOrd,
				loadout.getEquipment(weaponSlots[i]), keyLbl, mouseX, mouseY);
			sx += qSlot + gap;
		}
		sx += groupGap - gap;

		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			Rect pr = new Rect(sx, sy, qSlot, qSlot);
			regions.add(new InteractiveRegion(pr, null, TarkovNetworking.LOC_POCKET, i, 1, 1, qSlot, null));
			String keyLbl = client != null ? TarkovCraftClient.getShortKeyLabel(client.options.hotbarKeys[i + 3]) : String.valueOf(i + 4);
			drawSingleCellSlot(context, pr, TarkovNetworking.LOC_POCKET, i, loadout.getPocket(i), keyLbl, mouseX, mouseY);
			sx += qSlot + gap;
		}
	}

	private void drawSingleCellSlot(
		DrawContext context, Rect r, byte locType, int idx, ItemStack stack,
		@Nullable String keyBadge, int mouseX, int mouseY
	) {
		boolean hovered = r.contains(mouseX, mouseY) && (drag == null || !drag.active);
		boolean selected = selectedTarget != null && selectedTarget.matches(locType, idx, null);
		boolean isDragSrc = drag != null && drag.isSource(locType, idx, null);

		int bg = stack.isEmpty() ? TarkovUiTheme.BG_CELL_EMPTY : TarkovUiTheme.itemCategoryTint(stack);
		int border = (hovered || selected) ? TarkovUiTheme.BORDER_HOVER
			: (stack.isEmpty() ? TarkovUiTheme.BORDER_CELL : TarkovUiTheme.BORDER_OCCUPIED);

		context.fill(r.x, r.y, r.x + r.w, r.y + r.h, bg);
		context.drawBorder(r.x, r.y, r.w, r.h, border);

		if (!stack.isEmpty()) {
			TarkovUiTheme.drawScaledItem(context, stack, r.x + 1, r.y + 1, r.w - 2, r.h - 2);
			drawCellCornerTags(context, r.x, r.y, r.w, r.h, stack);
		}
		if (keyBadge != null && !keyBadge.isEmpty()) {
			TarkovUiTheme.drawFlatText(context, textRenderer, keyBadge, r.x + 2, r.y + 2,
				TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
		}
		if (isDragSrc) {
			context.fill(r.x + 1, r.y + 1, r.x + r.w - 1, r.y + r.h - 1, TarkovUiTheme.BG_DIM_DRAG_SOURCE);
		}
	}

	private void drawEquipSlot(
		DrawContext context, int x, int y, int w, int h,
		EquipmentSlotType slot, String label, @Nullable String keyBadge,
		PlayerLoadout loadout, boolean isCorpse, @Nullable Rect clip, int mouseX, int mouseY
	) {
		Rect r = new Rect(x, y, w, h);
		byte loc = isCorpse ? TarkovNetworking.LOC_CORPSE_EQUIPMENT : TarkovNetworking.LOC_EQUIPMENT;
		regions.add(new InteractiveRegion(r, clip, loc, slot.ordinal(), 1, 1, Math.min(w, h), null));

		ItemStack stack = loadout.getEquipment(slot);
		TarkovArmorItem armor = (!stack.isEmpty() && stack.getItem() instanceof TarkovArmorItem a && a.getArmorClass() > 0) ? a : null;
		boolean brokenArmor = armor != null && TarkovArmorItem.isBroken(stack);
		boolean hovered = r.contains(mouseX, mouseY) && (clip == null || clip.contains(mouseX, mouseY)) && (drag == null || !drag.active);
		boolean selected = selectedTarget != null && selectedTarget.matches(loc, slot.ordinal(), null);
		boolean isDragSrc = drag != null && drag.isSource(loc, slot.ordinal(), null);

		int borderCol = brokenArmor ? TarkovUiTheme.BORDER_BROKEN
			: ((hovered || selected) ? TarkovUiTheme.BORDER_HOVER : TarkovUiTheme.BORDER_SLOT);

		context.fill(x, y, x + w, y + h, inventoryColor(TarkovUiTheme.BG_SLOT));
		context.drawBorder(x, y, w, h, borderCol);

		if (label != null && !label.isEmpty()) {
			int rightReserve = 0;
			if (keyBadge != null && !keyBadge.isEmpty()) {
				rightReserve = Math.max(11, Math.round(textRenderer.getWidth(keyBadge) * TarkovUiTheme.TEXT_SCALE_SMALL) + 4) + 3;
			} else if (armor != null) {
				rightReserve = Math.max(18, Math.round(textRenderer.getWidth("CL." + armor.getArmorClass()) * TarkovUiTheme.TEXT_SCALE_SMALL) + 4) + 3;
			}
			int availW = Math.max(12, w - 6 - rightReserve);
			float lblScale = Math.min(0.68f, availW / (float) Math.max(1, textRenderer.getWidth(label)));
			lblScale = Math.max(0.48f, lblScale);
			String trimmedLbl = TarkovUiTheme.trimWithEllipsis(textRenderer, label, availW, lblScale);
			TarkovUiTheme.drawFlatText(context, textRenderer, trimmedLbl, x + 3, y + 2,
				TarkovUiTheme.TEXT_SECONDARY, lblScale);
		}
		if (keyBadge != null && !keyBadge.isEmpty()) {
			int kw = Math.max(11, Math.round(textRenderer.getWidth(keyBadge) * TarkovUiTheme.TEXT_SCALE_SMALL) + 4);
			context.fill(x + w - kw - 1, y + 1, x + w - 1, y + 10, 0xCC0A0C0E);
			TarkovUiTheme.drawCenteredFlatText(context, textRenderer, keyBadge, x + w - kw / 2 - 1, y + 2,
				TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
		} else if (armor != null) {
			String clTxt = "CL." + armor.getArmorClass();
			int cw = Math.max(18, Math.round(textRenderer.getWidth(clTxt) * TarkovUiTheme.TEXT_SCALE_SMALL) + 4);
			context.fill(x + w - cw - 1, y + 1, x + w - 1, y + 10, 0xCC0A0C0E);
			TarkovUiTheme.drawCenteredFlatText(context, textRenderer, clTxt, x + w - cw / 2 - 1, y + 2,
				brokenArmor ? 0xFFFF5555 : 0xFF63D2FF, TarkovUiTheme.TEXT_SCALE_SMALL);
		}

		if (!stack.isEmpty()) {
			int iconPadTop = (label != null && !label.isEmpty()) ? 8 : 2;
			TarkovUiTheme.drawScaledItem(context, stack, x + 2, y + iconPadTop, w - 4, h - iconPadTop - 2);
		}

		if (armor != null) {
			int maxDur = Math.max(1, TarkovArmorItem.getEffectiveMaxDurability(stack));
			int curDur = TarkovArmorItem.getRemainingDurability(stack);
			float ratio = curDur / (float) maxDur;
			int durCol = curDur <= 0 ? 0xFFFF5555
				: (ratio < 0.25f ? 0xFFE53935 : (ratio < 0.50f ? 0xFFE0B23B : 0xFF77B85A));
			TarkovUiTheme.drawFlatText(context, textRenderer, TarkovArmorItem.formatDurability(stack), x + 3, y + h - 8,
				durCol, TarkovUiTheme.TEXT_SCALE_SMALL);
		} else if (!stack.isEmpty() && stack.getCount() > 1) {
			TarkovUiTheme.drawRightAlignedFlatText(context, textRenderer, String.valueOf(stack.getCount()),
				x + w - 3, y + h - 8, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		}

		if (isDragSrc) {
			context.fill(x + 1, y + 1, x + w - 1, y + h - 1, TarkovUiTheme.BG_DIM_DRAG_SOURCE);
		}
	}

	private void drawGridView(
		DrawContext context, int x, int y, Rect clip,
		byte locType, int containerIndex, int gCell,
		TarkovNetworking.GridView view,
		@Nullable TarkovNetworking.ScreenStateSnapshot snap,
		boolean suppressStage1Bar,
		int mouseX, int mouseY
	) {
		int gw = view.width() * gCell;
		int gh = view.height() * gCell;
		Rect r = new Rect(x, y, gw, gh);
		regions.add(new InteractiveRegion(r, clip, locType, containerIndex, view.width(), view.height(), gCell, view));

		for (int gx = 0; gx < view.width(); gx++) {
			for (int gy = 0; gy < view.height(); gy++) {
				int cx = x + gx * gCell;
				int cy = y + gy * gCell;
				context.fill(cx, cy, cx + gCell, cy + gCell, TarkovUiTheme.BG_CELL_EMPTY);
				context.drawBorder(cx, cy, gCell, gCell, TarkovUiTheme.BORDER_CELL);
			}
		}

		if (!view.gridRevealed()) {
			context.fill(x, y, x + gw, y + gh, 0xDD090B0E);
			context.drawBorder(x, y, gw, gh, suppressStage1Bar ? TarkovUiTheme.BORDER_SLOT : TarkovUiTheme.ACCENT_SAND);
			if (!suppressStage1Bar) {
				boolean isScanningThis = snap != null
					&& snap.searchStage() == 1
					&& view.searchTargetId().equals(snap.searchTargetId());
				String msg = Text.translatable(isScanningThis ? "ui.tarkovcraft.searching" : "ui.tarkovcraft.unsearched").getString();
				if (gw >= 36) {
					TarkovUiTheme.drawCenteredFlatText(context, textRenderer, msg, x + gw / 2, y + gh / 2 - 5,
						isScanningThis ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.TEXT_MUTED, TarkovUiTheme.TEXT_SCALE_SMALL);
				}
				if (isScanningThis) {
					int barW = Math.max(14, gw - 8);
					int bx = x + (gw - barW) / 2;
					int by = y + gh / 2 + 3;
					context.fill(bx, by, bx + barW, by + 4, 0xFF20252A);
					context.fill(bx, by, bx + Math.round(barW * snap.searchProgress()), by + 4, TarkovUiTheme.ACCENT_SAND);
				}
			}
			return;
		}

		for (TarkovNetworking.ViewEntry entry : view.entries()) {
			int ex = x + entry.x() * gCell;
			int ey = y + entry.y() * gCell;
			int ew = entry.width() * gCell;
			int eh = entry.height() * gCell;

			if (isStash() && locType == TarkovNetworking.LOC_EXTERNAL_GRID && !stashFilter.matches(entry.stack())) {
				context.fill(ex + 1, ey + 1, ex + ew - 1, ey + eh - 1, TarkovUiTheme.BG_CELL_SILHOUETTE);
				context.drawBorder(ex + 1, ey + 1, ew - 2, eh - 2, TarkovUiTheme.BORDER_SLOT);
				continue;
			}

			if (!entry.discovered()) {
				context.fill(ex + 1, ey + 1, ex + ew - 1, ey + eh - 1, TarkovUiTheme.BG_CELL_SILHOUETTE);
				// Dark tactical item silhouette block (Tarkov-style unrevealed item shape)
				int padX = Math.max(3, ew / 6);
				int padY = Math.max(3, eh / 6);
				if (ew - padX * 2 >= 4 && eh - padY * 2 >= 4) {
					context.fill(ex + padX, ey + padY, ex + ew - padX, ey + eh - padY, 0xFF171C22);
					context.fill(ex + padX + 1, ey + padY + 1, ex + ew - padX - 1, Math.min(ey + eh - padY - 1, ey + padY + Math.max(2, eh / 4)), 0xFF202730);
				}
				context.drawBorder(ex + 1, ey + 1, ew - 2, eh - 2, TarkovUiTheme.BORDER_SLOT);

				boolean isSearchingItem = snap != null
					&& snap.searchStage() == 2
					&& entry.entryId().equals(snap.searchEntryId());
				if (isSearchingItem) {
					context.drawBorder(ex + 1, ey + 1, ew - 2, eh - 2, TarkovUiTheme.ACCENT_SAND);
					int sweepH = Math.max(2, Math.round((eh - 4) * snap.searchProgress()));
					context.fill(ex + 2, ey + 2, ex + ew - 2, ey + 2 + sweepH, 0x33D4AF6A);
					int bw = Math.max(8, ew - 6);
					int bx = ex + 3;
					int by = ey + eh - 5;
					context.fill(bx, by, bx + bw, by + 3, 0xFF20252A);
					context.fill(bx, by, bx + Math.round(bw * snap.searchProgress()), by + 3, TarkovUiTheme.ACCENT_SAND);
					TarkovUiTheme.drawCenteredFlatText(context, textRenderer, "?", ex + ew / 2, ey + (eh - 6) / 2,
						TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
				} else {
					TarkovUiTheme.drawCenteredFlatText(context, textRenderer, "?", ex + ew / 2, ey + (eh - 6) / 2,
						TarkovUiTheme.TEXT_MUTED, TarkovUiTheme.TEXT_SCALE_SMALL);
				}
			} else {
				boolean hovered = mouseX >= ex && mouseX < ex + ew && mouseY >= ey && mouseY < ey + eh
					&& clip.contains(mouseX, mouseY) && (drag == null || !drag.active);
				boolean selected = selectedTarget != null && selectedTarget.matches(locType, containerIndex, entry.entryId());
				boolean isDragSrc = drag != null && drag.isSource(locType, containerIndex, entry.entryId());

				int fillCol = inventoryColor(TarkovUiTheme.itemCategoryTint(entry.stack()));
				int borderCol = (hovered || selected) ? TarkovUiTheme.BORDER_HOVER : TarkovUiTheme.BORDER_OCCUPIED;

				context.fill(ex + 1, ey + 1, ex + ew - 1, ey + eh - 1, fillCol);
				context.drawBorder(ex + 1, ey + 1, ew - 2, eh - 2, borderCol);

				TarkovUiTheme.drawScaledItem(context, entry.stack(), ex + 2, ey + 2, ew - 4, eh - 4, entry.rotated());

				if (ew >= 34) {
					float nameScale = 0.60f;
					String shortName = TarkovUiTheme.trimWithEllipsis(textRenderer, entry.stack().getName().getString(), ew - 6, nameScale);
					TarkovUiTheme.drawRightAlignedFlatText(context, textRenderer, shortName, ex + ew - 3, ey + 2,
						TarkovUiTheme.TEXT_SECONDARY, nameScale);
				}
				drawCellCornerTags(context, ex, ey, ew, eh, entry.stack());

				if (isDragSrc) {
					context.fill(ex + 1, ey + 1, ex + ew - 1, ey + eh - 1, TarkovUiTheme.BG_DIM_DRAG_SOURCE);
				}
			}
		}
	}

	private void drawCellCornerTags(DrawContext context, int x, int y, int w, int h, ItemStack stack) {
		if (stack.getItem() instanceof TarkovArmorItem armor && armor.getArmorClass() > 0) {
			int maxDur = Math.max(1, TarkovArmorItem.getEffectiveMaxDurability(stack));
			int curDur = TarkovArmorItem.getRemainingDurability(stack);
			float ratio = curDur / (float) maxDur;
			int durCol = curDur <= 0 ? 0xFFFF5555
				: (ratio < 0.25f ? 0xFFE53935 : (ratio < 0.50f ? 0xFFE0B23B : 0xFF77B85A));
			TarkovUiTheme.drawCellCornerBadge(context, textRenderer, TarkovArmorItem.formatDurability(stack), x, y, w, h, durCol);
		} else if (stack.getItem() instanceof TarkovMedicalItem med) {
			int max = med.getMedicalType().getMaxCapacity();
			int cur = TarkovMedicalItem.getRemaining(stack);
			float ratio = cur / (float) Math.max(1, max);
			int medCol = ratio >= 0.50f ? 0xFF77B85A : (ratio >= 0.25f ? 0xFFE0B23B : 0xFFE53935);
			TarkovUiTheme.drawCellCornerBadge(context, textRenderer, cur + "/" + max, x, y, w, h, medCol);
		} else if (AmmoTier.isTaczAmmoStack(stack)) {
			AmmoTier tier = AmmoTier.getFromAmmoStack(stack);
			String badge = stack.getCount() > 1 ? (tier.getBadge() + " " + stack.getCount()) : tier.getBadge();
			TarkovUiTheme.drawCellCornerBadge(context, textRenderer, badge,
				x, y, w, h, tier.getArgbColor());
		} else if (stack.getCount() > 1) {
			TarkovUiTheme.drawCellCornerBadge(context, textRenderer, String.valueOf(stack.getCount()),
				x, y, w, h, TarkovUiTheme.TEXT_PRIMARY);
		}
	}

	private void drawScrollbar(DrawContext context, Rect area, int scrollY, int maxScroll) {
		if (maxScroll <= 0) return;
		int barX = area.x + area.w - 4;
		int trackH = area.h - 6;
		int thumbH = Math.max(18, Math.round(trackH * (area.h / (float) (area.h + maxScroll))));
		int thumbY = area.y + 3 + Math.round((trackH - thumbH) * (scrollY / (float) maxScroll));
		context.fill(barX, area.y + 3, barX + 3, area.y + area.h - 3, 0x33FFFFFF);
		context.fill(barX, thumbY, barX + 3, thumbY + thumbH, TarkovUiTheme.ACCENT_SAND);
	}

	private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
		if (contextMenu == null) return;
		int mx = contextMenu.x;
		int my = contextMenu.y;
		int mw = contextMenu.width;
		int mh = contextMenu.height();

		context.getMatrices().push();
		context.getMatrices().translate(0.0f, 0.0f, TarkovUiTheme.Z_POPUP);
		context.fill(mx, my, mx + mw, my + mh, inventoryColor(TarkovUiTheme.BG_CONTEXT_MENU));
		context.drawBorder(mx, my, mw, mh, TarkovUiTheme.BORDER_POPUP);

		for (int i = 0; i < contextMenu.options.size(); i++) {
			int ry = my + 1 + i * contextMenu.rowH;
			boolean hov = mouseX >= mx + 1 && mouseX < mx + mw - 1 && mouseY >= ry && mouseY < ry + contextMenu.rowH;
			if (hov) {
				context.fill(mx + 1, ry, mx + mw - 1, ry + contextMenu.rowH, TarkovUiTheme.HOVER_MENU_BG);
			}
			int textCol = hov ? TarkovUiTheme.HOVER_MENU_TEXT : TarkovUiTheme.TEXT_PRIMARY;
			TarkovUiTheme.drawFlatText(context, textRenderer, contextMenu.options.get(i).label(),
				mx + 6, ry + 5, textCol, TarkovUiTheme.TEXT_SCALE_SMALL);
		}
		context.getMatrices().pop();
	}

	private void renderDragPreview(DrawContext context, int mouseX, int mouseY) {
		if (drag == null || !drag.active) return;

		InteractiveRegion hovered = findRegionAt(mouseX, mouseY);
		int previewCell = hovered != null ? hovered.regionCellSize : cellSize;
		ItemSize sz = ItemSizeRegistry.getSize(drag.stack).oriented(drag.rotated);
		int pw = sz.width() * previewCell;
		int ph = sz.height() * previewCell;

		if (hovered != null) {
			DropEvaluation eval = evaluateDrop(hovered, mouseX, mouseY);
			int fillCol = eval.valid ? TarkovUiTheme.DROP_VALID_FILL : TarkovUiTheme.DROP_INVALID_FILL;
			int borderCol = eval.valid ? TarkovUiTheme.DROP_VALID_BORDER : TarkovUiTheme.DROP_INVALID_BORDER;
			context.getMatrices().push();
			context.getMatrices().translate(0.0f, 0.0f, TarkovUiTheme.Z_DRAG_HIGHLIGHT);
			if (hovered.view != null) {
				int hx = hovered.rect.x + eval.dstX * previewCell;
				int hy = hovered.rect.y + eval.dstY * previewCell;
				int hw = eval.highlightW * previewCell;
				int hh = eval.highlightH * previewCell;
				context.fill(hx, hy, hx + hw, hy + hh, fillCol);
				context.drawBorder(hx, hy, hw, hh, borderCol);
			} else {
				context.fill(hovered.rect.x + 1, hovered.rect.y + 1,
					hovered.rect.x + hovered.rect.w - 1, hovered.rect.y + hovered.rect.h - 1, fillCol);
				context.drawBorder(hovered.rect.x, hovered.rect.y, hovered.rect.w, hovered.rect.h, borderCol);
			}
			context.getMatrices().pop();
		}

		int fx = mouseX - pw / 2;
		int fy = mouseY - ph / 2;
		context.getMatrices().push();
		context.getMatrices().translate(0.0f, 0.0f, TarkovUiTheme.Z_DRAG_GHOST);
		context.fill(fx, fy, fx + pw, fy + ph, 0xCC1B2127);
		context.drawBorder(fx, fy, pw, ph, TarkovUiTheme.ACCENT_SAND);

		ItemStack previewStack = drag.stack.copy();
		if (drag.moveCount > 0) {
			previewStack.setCount(drag.moveCount);
		}
		TarkovUiTheme.drawScaledItem(context, previewStack, fx + 2, fy + 2, pw - 4, ph - 4, drag.rotated);
		if (previewStack.getCount() > 1) {
			TarkovUiTheme.drawRightAlignedFlatText(context, textRenderer, String.valueOf(previewStack.getCount()),
				fx + pw - 3, fy + ph - 8, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		}
		context.getMatrices().pop();
	}

	private void renderHoverTooltip(DrawContext context, int mouseX, int mouseY) {
		if (activeTab == TAB_HEALTH) {
			for (BodyHealthHudHelper.QuickHealHitbox qh : quickHealHitboxes) {
				if (qh.contains(mouseX, mouseY)) {
					Text tip = Text.translatable("tooltip.tarkovcraft.quick_heal",
						qh.med().stack().getName(),
						Text.translatable(qh.part().getTranslationKey()));
					TarkovUiTheme.drawFlatTooltip(context, textRenderer, List.of(tip), mouseX, mouseY, this.width, this.height);
					return;
				}
			}
			for (HealthMedSlot slot : healthMedSlots) {
				if (slot.rect().contains(mouseX, mouseY)) {
					TooltipContext ctx = (client != null && client.options.advancedItemTooltips)
						? TooltipContext.ADVANCED
						: TooltipContext.BASIC;
					List<Text> lines = slot.stack().getTooltip(client != null ? client.player : null, ctx);
					TarkovUiTheme.drawFlatTooltip(context, textRenderer, lines, mouseX, mouseY, this.width, this.height);
					return;
				}
			}
		}
		InteractiveRegion hovered = findRegionAt(mouseX, mouseY);
		if (hovered == null) return;

		if (hovered.view != null && hovered.view.gridRevealed()) {
			int gx = (mouseX - hovered.rect.x) / hovered.regionCellSize;
			int gy = (mouseY - hovered.rect.y) / hovered.regionCellSize;
			TarkovNetworking.ViewEntry entry = findEntryAt(hovered.view, gx, gy);
			if (entry != null && !entry.discovered()) {
				TarkovUiTheme.drawFlatTooltip(context, textRenderer,
					List.of(Text.translatable("tooltip.tarkovcraft.unsearched_item")),
					mouseX, mouseY, this.width, this.height);
				return;
			}
		}

		HoveredTarget target = resolveHoveredTarget(mouseX, mouseY);
		if (target != null && !target.stack().isEmpty()) {
			TooltipContext ctx = (client != null && client.options.advancedItemTooltips)
				? TooltipContext.ADVANCED
				: TooltipContext.BASIC;
			List<Text> lines = target.stack().getTooltip(client != null ? client.player : null, ctx);
			TarkovUiTheme.drawFlatTooltip(context, textRenderer, lines, mouseX, mouseY, this.width, this.height);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (healthMedStrip != null && healthMedStrip.contains(mouseX, mouseY) && healthMedMaxOffset > 0) {
			healthMedOffset = Math.max(0, Math.min(healthMedMaxOffset, healthMedOffset - (int) Math.signum(amount) * 3));
			return true;
		}
		if (contextMenu != null) {
			contextMenu = null;
			return true;
		}
		for (int i = inspectWindows.size() - 1; i >= 0; i--) {
			TarkovInspectWindow win = inspectWindows.get(i);
			if (win.contains(mouseX, mouseY)) {
				win.handleMouseScrolled(mouseX, mouseY, amount);
				return true;
			}
		}
		int step = (int) (-amount * 18);
		if (centerScrollArea != null && centerScrollArea.contains(mouseX, mouseY)) {
			centerScrollY = Math.max(0, Math.min(centerMaxScroll, centerScrollY + step));
			return true;
		}
		if (rightScrollArea != null && rightScrollArea.contains(mouseX, mouseY)) {
			rightScrollY = Math.max(0, Math.min(rightMaxScroll, rightScrollY + step));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// 1. Split Modal intercepts first
		if (splitModal != null) {
			boolean close = splitModal.handleMouseClicked(mouseX, mouseY, button);
			if (close || button == 1) {
				splitModal = null;
			}
			return true;
		}

		// 2. RMB Context Menu
		if (contextMenu != null) {
			if (button == 0 && contextMenu.contains(mouseX, mouseY)) {
				int idx = (int) ((mouseY - contextMenu.y - 1) / contextMenu.rowH);
				if (idx >= 0 && idx < contextMenu.options.size()) {
					MenuOption opt = contextMenu.options.get(idx);
					contextMenu = null;
					opt.action().run();
					return true;
				}
			}
			contextMenu = null;
			return true;
		}

		if (isStash() && client != null && client.player != null
			&& LobbyTopNavBar.mouseClicked(width, mouseX, mouseY, button,
				LobbyTopNavBar.Tab.CHARACTER, lobbyStatus(), this::lobbyNavAction)) return true;

		// 3. Inspect Windows (topmost first, brings clicked window to front)
		for (int i = inspectWindows.size() - 1; i >= 0; i--) {
			TarkovInspectWindow win = inspectWindows.get(i);
			if (win.contains(mouseX, mouseY)) {
				if (button == 1 || win.handleMouseClicked(mouseX, mouseY, button)) {
					inspectWindows.remove(i);
				} else if (i != inspectWindows.size() - 1) {
					inspectWindows.remove(i);
					inspectWindows.add(win);
				}
				return true;
			}
		}

		FloatingWindowLayout floatingWindow = findFloatingWindowAt(mouseX, mouseY);
		if (floatingWindow != null && button != 1) {
			if (button == 0) {
				if (floatingWindow.closeRect().contains(mouseX, mouseY)) {
					ClientNetworkSender.sendCloseCase(floatingWindow.uuid());
				} else if (floatingWindow.searchRect() != null && floatingWindow.searchRect().contains(mouseX, mouseY)) {
					ClientNetworkSender.sendRequestSearch(floatingWindow.uuid());
				} else if (floatingWindow.headerRect().contains(mouseX, mouseY)) {
					draggingFloatingUuid = floatingWindow.uuid();
				} else {
					clickGearItem(mouseX, mouseY);
				}
			}
			return true;
		}

		// 4. Right-click: cancel drag OR clear selected med OR open dynamic EFT Context Menu
		if (button == 1) {
			if (drag != null) {
				drag = null;
				return true;
			}
			if (activeTab == TAB_HEALTH) {
				selectedHealthMed = null;
				return true;
			}
			if (activeTab == TAB_GEAR) {
				HoveredTarget target = resolveHoveredTarget(mouseX, mouseY);
				if (target != null && !target.stack().isEmpty()) {
					selectedTarget = new SelectedTarget(target.loc(), target.idx(), target.entryId());
					openContextMenu((int) mouseX, (int) mouseY, target);
					return true;
				}
			}
			return true;
		}

		if (button != 0) {
			return super.mouseClicked(mouseX, mouseY, button);
		}

		// 5. Top bar tabs & Back button
		if (tabGearBtn != null && tabGearBtn.contains(mouseX, mouseY)) {
			activeTab = TAB_GEAR;
			return true;
		}
		if (tabHealthBtn != null && tabHealthBtn.contains(mouseX, mouseY)) {
			activeTab = TAB_HEALTH;
			drag = null;
			return true;
		}
		if (closeBtn != null && closeBtn.contains(mouseX, mouseY)) {
			this.close();
			return true;
		}
		if (activeTab == TAB_HEALTH) {
			return handleHealthTabClick(mouseX, mouseY);
		}
		if (activeTab != TAB_GEAR) {
			return true;
		}

		// 6. Section action buttons ([-]/[+], [X], SEARCH)
		for (int i = actionButtons.size() - 1; i >= 0; i--) {
			ActionButton btn = actionButtons.get(i);
			if (btn.hits(mouseX, mouseY)) {
				btn.action().run();
				return true;
			}
		}

		if (clickGearItem(mouseX, mouseY)) {
			return true;
		}

		// Empty space in the left column starts 3D player rotation.
		if (leftColumnArea != null && leftColumnArea.contains(mouseX, mouseY) && findRegionAt(mouseX, mouseY) == null) {
			rotatingPlayerModel = true;
		}
		selectedTarget = null;
		return true;
	}

	private boolean clickGearItem(double mouseX, double mouseY) {
		HoveredTarget target = resolveHoveredTarget(mouseX, mouseY);
		if (target == null || target.stack().isEmpty()) {
			return false;
		}
		if (Screen.hasShiftDown()) {
			TarkovNetworking.sendQuickTransfer(target.loc(), target.idx(), target.entryId());
			return true;
		}
		int moveCount = 0;
		if (target.stack().getCount() > 1) {
			if (Screen.hasControlDown()) {
				moveCount = Math.max(1, target.stack().getCount() / 2);
			} else if (Screen.hasAltDown()) {
				moveCount = 1;
			}
		}
		drag = new DragState(target.loc(), target.idx(), target.entryId(),
			target.stack().copy(), target.rotated(), moveCount, mouseX, mouseY);
		return true;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0) {
			if (splitModal != null && splitModal.handleMouseDragged(mouseX)) {
				return true;
			}
			for (int i = inspectWindows.size() - 1; i >= 0; i--) {
				if (inspectWindows.get(i).handleMouseDragged(deltaX, deltaY, this.width, this.height)) {
					return true;
				}
			}
			if (draggingFloatingUuid != null) {
				int[] pos = floatingWinPos.get(draggingFloatingUuid);
				if (pos != null) {
					pos[0] = Math.max(4, Math.min(this.width - 60, pos[0] + (int) Math.round(deltaX)));
					pos[1] = Math.max(TarkovUiTheme.TOP_BAR_H + 2, Math.min(this.height - 40, pos[1] + (int) Math.round(deltaY)));
				}
				return true;
			}
			if (rotatingPlayerModel) {
				playerPreviewYaw += (float) (deltaX * 1.4);
				return true;
			}
			if (drag != null && !drag.active) {
				if (Math.hypot(mouseX - drag.startX, mouseY - drag.startY) > TarkovUiTheme.DRAG_THRESHOLD_PX) {
					drag.active = true;
				}
				return true;
			}
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0) {
			rotatingPlayerModel = false;
			draggingFloatingUuid = null;
			for (TarkovInspectWindow win : inspectWindows) {
				win.handleMouseReleased();
			}
			if (splitModal != null) splitModal.handleMouseReleased();

			if (drag != null) {
				if (!drag.active) {
					// Short click (<= 4px): select item cleanly without moving or dropping
					selectedTarget = new SelectedTarget(drag.srcLoc, drag.srcIdx, drag.srcEntryId);
				} else {
					InteractiveRegion region = findRegionAt(mouseX, mouseY);
					if (region != null) {
						DropEvaluation eval = evaluateDrop(region, mouseX, mouseY);
						if (region.locType == TarkovNetworking.LOC_EQUIPMENT
							&& region.containerIndex == EquipmentSlotType.HEADSET.ordinal()
							&& EquipmentSlotType.HEADSET.accepts(drag.stack)
							&& ClientLoadoutCache.getLoadout().blocksHeadset() && client != null && client.player != null) {
							client.player.sendMessage(Text.translatable("message.tarkovcraft.headset_blocked"), true);
						}
						if (eval.valid) {
							TarkovNetworking.sendMoveItem(
								drag.srcLoc, drag.srcIdx, drag.srcEntryId,
								region.locType, region.containerIndex,
								eval.dstX, eval.dstY, drag.rotated, drag.moveCount
							);
						}
					}
					// Invalid or outside panels: silently cancel drag without dropping!
				}
				drag = null;
				return true;
			}
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (splitModal != null) {
				splitModal = null;
				return true;
			}
			if (contextMenu != null) {
				contextMenu = null;
				return true;
			}
			if (!inspectWindows.isEmpty()) {
				inspectWindows.remove(inspectWindows.size() - 1);
				return true;
			}
			TarkovNetworking.ScreenStateSnapshot snap = ClientLoadoutCache.getScreenState();
			if (snap != null && !snap.openContainers().isEmpty()) {
				UUID lastId = snap.openContainers().get(snap.openContainers().size() - 1).containerUuid();
				ClientNetworkSender.sendCloseCase(lastId);
				return true;
			}
			this.close();
			return true;
		}

		if (splitModal != null) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				splitModal.confirm();
				splitModal = null;
				return true;
			}
			return true;
		}

		if (keyCode == GLFW.GLFW_KEY_R && drag != null && drag.active) {
			drag.rotated = !drag.rotated;
			return true;
		}

		if (activeTab == TAB_GEAR && (drag == null || !drag.active)) {
			HoveredTarget target = resolveHoveredTarget(lastMouseX, lastMouseY);
			if (target != null && !target.stack().isEmpty()) {
				boolean isInteractKey = keyCode == GLFW.GLFW_KEY_F || TarkovCraftClient.interactKey.matchesKey(keyCode, scanCode);
				// Ctrl+F -> Quick Equip / Unequip (checked BEFORE plain F!)
				if (Screen.hasControlDown() && isInteractKey) {
					contextMenu = null;
					ClientNetworkSender.sendQuickEquip(target.loc(), target.idx(), target.entryId());
					return true;
				}
				// F -> Quick Transfer
				if (!Screen.hasControlDown() && isInteractKey) {
					contextMenu = null;
					TarkovNetworking.sendQuickTransfer(target.loc(), target.idx(), target.entryId());
					return true;
				}
				// Delete -> Discard full stack
				if (keyCode == GLFW.GLFW_KEY_DELETE) {
					contextMenu = null;
					TarkovNetworking.sendMoveItem(target.loc(), target.idx(), target.entryId(),
						TarkovNetworking.LOC_DROP, 0, 0, 0, false, 0);
					return true;
				}
				// Q -> Drop 1 (or Ctrl+Q -> Drop all)
				if (client != null && client.options.dropKey.matchesKey(keyCode, scanCode)) {
					contextMenu = null;
					int count = Screen.hasControlDown() ? 0 : 1;
					TarkovNetworking.sendMoveItem(target.loc(), target.idx(), target.entryId(),
						TarkovNetworking.LOC_DROP, 0, 0, 0, false, count);
					return true;
				}
			}
		}

		if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			this.close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private int inventoryColor(int argb) { return isStash() ? TarkovUiTheme.opaque(argb) : argb; }

	private void openContextMenu(int mx, int my, HoveredTarget target) {
		List<MenuOption> opts = new ArrayList<>();
		ItemStack stack = target.stack();
		if (isStash() && target.loc() == TarkovNetworking.LOC_EXTERNAL_GRID && target.entryId() != null
				&& com.tarkovcraft.lobby.ItemPriceCalculator.canSell(stack)) {
			long price = com.tarkovcraft.lobby.ItemPriceCalculator.sellPrice(stack);
			opts.add(new MenuOption(Text.translatable("ctx.tarkovcraft.sell_fence", LobbyTopNavBar.rubles(price)).getString(),
				() -> ClientNetworkSender.sendStashSale(handler.syncId, target.entryId())));
		}

		if (TarkovScreenHandler.isConsumableItem(stack)) {
			opts.add(new MenuOption(Text.translatable("ctx.tarkovcraft.use").getString(), () ->
				ClientNetworkSender.sendUseItem(target.loc(), target.idx(), target.entryId())
			));
		}

		if (target.loc() == TarkovNetworking.LOC_EQUIPMENT) {
			opts.add(new MenuOption(Text.translatable("ctx.tarkovcraft.unequip").getString(), () ->
				ClientNetworkSender.sendQuickEquip(target.loc(), target.idx(), target.entryId())
			));
		} else if (TarkovScreenHandler.canEquipItem(stack)) {
			opts.add(new MenuOption(Text.translatable("ctx.tarkovcraft.equip").getString(), () ->
				ClientNetworkSender.sendQuickEquip(target.loc(), target.idx(), target.entryId())
			));
		}

		boolean isOwnWornContainer = target.loc() == TarkovNetworking.LOC_EQUIPMENT
			&& (stack.getItem() instanceof BackpackItem || stack.getItem() instanceof TacticalRigItem);
		if (stack.getItem() instanceof TarkovCaseItem
			|| ((stack.getItem() instanceof BackpackItem || stack.getItem() instanceof TacticalRigItem) && !isOwnWornContainer)) {
			opts.add(new MenuOption(Text.translatable("ctx.tarkovcraft.open").getString(), () ->
				ClientNetworkSender.sendOpenCase(target.loc(), target.idx(), target.entryId())
			));
		}

		if (stack.getItem() instanceof BackpackItem || stack.getItem() instanceof TacticalRigItem) {
			if (ContainerItemHelper.requiresSearch(stack)) {
				UUID containerId = ContainerItemHelper.getContainerUuid(stack);
				if (containerId != null) {
					opts.add(new MenuOption(Text.translatable("ctx.tarkovcraft.search").getString(), () ->
						ClientNetworkSender.sendRequestSearch(containerId)
					));
				}
			} else if (target.loc() == TarkovNetworking.LOC_EQUIPMENT) {
				opts.add(new MenuOption(Text.translatable("ctx.tarkovcraft.open").getString(), () -> {
					if (stack.getItem() instanceof TacticalRigItem) {
						rigCollapsed = false;
						centerScrollY = Math.max(0, Math.min(centerMaxScroll, rigSectionOffsetY));
					} else {
						backpackCollapsed = false;
						centerScrollY = Math.max(0, Math.min(centerMaxScroll, backpackSectionOffsetY));
					}
				}));
			}
		}

		if (stack.getCount() > 1) {
			opts.add(new MenuOption(Text.translatable("ctx.tarkovcraft.split").getString(), () ->
				splitModal = new TarkovSplitModal(stack, mx, my, this.width, this.height, amount ->
					ClientNetworkSender.sendSplitStack(target.loc(), target.idx(), target.entryId(), amount)
				)
			));
		}

		opts.add(new MenuOption(Text.translatable("ctx.tarkovcraft.inspect").getString(), () -> {
			if (inspectWindows.size() >= MAX_INSPECT_WINDOWS) {
				inspectWindows.remove(0);
			}
			inspectWindows.add(new TarkovInspectWindow(stack, this.width, this.height, inspectWindows.size()));
		}));

		opts.add(new MenuOption(Text.translatable("ctx.tarkovcraft.discard").getString(), () ->
			TarkovNetworking.sendMoveItem(target.loc(), target.idx(), target.entryId(),
				TarkovNetworking.LOC_DROP, 0, 0, 0, false, 0)
		));

		int menuW = 124;
		for (MenuOption opt : opts) {
			menuW = Math.max(menuW, Math.round(textRenderer.getWidth(opt.label()) * TarkovUiTheme.TEXT_SCALE_SMALL) + 18);
		}
		int mh = opts.size() * 17 + 2;
		int clampedX = Math.max(4, Math.min(mx, this.width - menuW - 4));
		int clampedY = Math.max(4, Math.min(my, this.height - mh - 4));
		contextMenu = new ContextMenu(clampedX, clampedY, menuW, opts);
	}

	private record DropEvaluation(boolean valid, int dstX, int dstY, int highlightW, int highlightH) {
	}

	private DropEvaluation evaluateDrop(InteractiveRegion region, double mouseX, double mouseY) {
		if (drag == null) {
			return new DropEvaluation(false, 0, 0, 1, 1);
		}
		ItemStack moving = drag.stack;
		int movingCount = drag.moveCount > 0 ? drag.moveCount : moving.getCount();
		PlayerLoadout loadout = ClientLoadoutCache.getLoadout();

		if (region.locType == TarkovNetworking.LOC_EQUIPMENT) {
			EquipmentSlotType slot = EquipmentSlotType.values()[region.containerIndex];
			boolean sameEquipSlot = drag.srcLoc == TarkovNetworking.LOC_EQUIPMENT && drag.srcIdx == region.containerIndex;
			boolean ok = !sameEquipSlot && drag.moveCount <= 0 && loadout.canEquip(slot, moving);
			return new DropEvaluation(ok, 0, 0, 1, 1);
		}

		if (region.locType == TarkovNetworking.LOC_POCKET) {
			boolean is1x1 = loadout.canPlaceInPocket(moving);
			ItemStack pocket = loadout.getPocket(region.containerIndex);
			boolean ok = is1x1 && (pocket.isEmpty() || canClientStackInto(pocket, moving, movingCount));
			return new DropEvaluation(ok, 0, 0, 1, 1);
		}

		if (region.view != null) {
			if (!region.view.gridRevealed() || (region.locType == TarkovNetworking.LOC_SECURE_CONTAINER
				&& !PlayerLoadout.canPlaceInSecureContainer(moving))) {
				return new DropEvaluation(false, 0, 0, 1, 1);
			}
			// Prevent container recursion on client preview
			if (ContainerItemHelper.containsContainerId(moving, region.view.containerId())) {
				return new DropEvaluation(false, 0, 0, 1, 1);
			}

			int gx = Math.max(0, (int) ((mouseX - region.rect.x) / region.regionCellSize));
			int gy = Math.max(0, (int) ((mouseY - region.rect.y) / region.regionCellSize));

			// Check stack-merge onto an existing discovered entry under cursor
			TarkovNetworking.ViewEntry targetEntry = findEntryAt(region.view, gx, gy);
			if (targetEntry != null && !Objects.equals(targetEntry.entryId(), drag.srcEntryId)) {
				boolean canStack = targetEntry.discovered() && canClientStackInto(targetEntry.stack(), moving, movingCount);
				return new DropEvaluation(canStack, targetEntry.x(), targetEntry.y(), targetEntry.width(), targetEntry.height());
			}

			ItemSize sz = ItemSizeRegistry.getSize(moving).oriented(drag.rotated);
			if (gx + sz.width() > region.gridWidth || gy + sz.height() > region.gridHeight) {
				return new DropEvaluation(false, gx, gy, sz.width(), sz.height());
			}

			boolean sameGrid = region.locType == drag.srcLoc && region.containerIndex == drag.srcIdx;
			boolean isPartial = drag.moveCount > 0 && drag.moveCount < moving.getCount();
			for (TarkovNetworking.ViewEntry e : region.view.entries()) {
				if (sameGrid && !isPartial && Objects.equals(e.entryId(), drag.srcEntryId)) {
					continue;
				}
				boolean overlapX = gx < e.x() + e.width() && gx + sz.width() > e.x();
				boolean overlapY = gy < e.y() + e.height() && gy + sz.height() > e.y();
				if (overlapX && overlapY) {
					return new DropEvaluation(false, gx, gy, sz.width(), sz.height());
				}
			}

			// Ground rig/backpack or external crate/own grid validation
			boolean supportedLoc = region.locType == TarkovNetworking.LOC_RIG_GRID
				|| region.locType == TarkovNetworking.LOC_BACKPACK_GRID
				|| region.locType == TarkovNetworking.LOC_EXTERNAL_GRID
				|| region.locType == TarkovNetworking.LOC_FLOATING_CONTAINER
				|| region.locType == TarkovNetworking.LOC_SECURE_CONTAINER
				|| (region.locType == TarkovNetworking.LOC_CORPSE_RIG_GRID
				&& handler.getMode() == TarkovScreenHandler.MODE_GROUND_ITEM);
			return new DropEvaluation(supportedLoc, gx, gy, sz.width(), sz.height());
		}

		return new DropEvaluation(false, 0, 0, 1, 1);
	}

	private static boolean canClientStackInto(ItemStack target, ItemStack incoming, int incomingCount) {
		return target.isStackable()
			&& ItemStack.canCombine(target, incoming)
			&& target.getCount() + incomingCount <= target.getMaxCount();
	}

	@Nullable
	private static UUID findFirstUnsearchedTarget(TarkovNetworking.ScreenStateSnapshot snap) {
		if (snap.externalGridView() != null && !isGridFullySearched(snap.externalGridView())) {
			return snap.externalGridView().searchTargetId();
		}
		for (TarkovNetworking.GridView v : snap.corpseRigViews()) {
			if (!isGridFullySearched(v)) {
				return v.searchTargetId();
			}
		}
		if (snap.corpseBackpackView() != null && !isGridFullySearched(snap.corpseBackpackView())) {
			return snap.corpseBackpackView().searchTargetId();
		}
		return null;
	}

	private static boolean isGridFullySearched(TarkovNetworking.GridView view) {
		if (!view.gridRevealed()) return false;
		for (TarkovNetworking.ViewEntry e : view.entries()) {
			if (!e.discovered()) return false;
		}
		return true;
	}

	@Nullable
	private HoveredTarget resolveHoveredTarget(double mx, double my) {
		InteractiveRegion region = findRegionAt(mx, my);
		if (region == null) return null;

		if (region.locType == TarkovNetworking.LOC_EQUIPMENT) {
			ItemStack st = ClientLoadoutCache.getLoadout().getEquipment(EquipmentSlotType.values()[region.containerIndex]);
			return st.isEmpty() ? null : new HoveredTarget(region.locType, region.containerIndex, null, st, false, null);
		}
		if (region.locType == TarkovNetworking.LOC_CORPSE_EQUIPMENT) {
			TarkovNetworking.ScreenStateSnapshot snap = ClientLoadoutCache.getScreenState();
			if (snap != null && snap.corpseLoadout() != null) {
				ItemStack st = snap.corpseLoadout().getEquipment(EquipmentSlotType.values()[region.containerIndex]);
				return st.isEmpty() ? null : new HoveredTarget(region.locType, region.containerIndex, null, st, false, null);
			}
			return null;
		}
		if (region.locType == TarkovNetworking.LOC_POCKET) {
			ItemStack st = ClientLoadoutCache.getLoadout().getPocket(region.containerIndex);
			return st.isEmpty() ? null : new HoveredTarget(region.locType, region.containerIndex, null, st, false, null);
		}
		if (region.view != null && region.view.gridRevealed()) {
			int gx = (int) ((mx - region.rect.x) / region.regionCellSize);
			int gy = (int) ((my - region.rect.y) / region.regionCellSize);
			TarkovNetworking.ViewEntry entry = findEntryAt(region.view, gx, gy);
			if (entry != null && entry.discovered() && !entry.stack().isEmpty()
				&& (!isStash() || region.locType != TarkovNetworking.LOC_EXTERNAL_GRID || stashFilter.matches(entry.stack()))) {
				return new HoveredTarget(region.locType, region.containerIndex, entry.entryId(), entry.stack(), entry.rotated(), region.view);
			}
		}
		return null;
	}

	private List<HealthMedSlot> collectOwnMedicalItems(
		PlayerLoadout loadout,
		@Nullable TarkovNetworking.ScreenStateSnapshot snap
	) {
		List<HealthMedSlot> out = new ArrayList<>();
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			ItemStack st = loadout.getPocket(i);
			if (!st.isEmpty() && st.getItem() instanceof TarkovMedicalItem) {
				out.add(new HealthMedSlot(TarkovNetworking.LOC_POCKET, i, null, st, new Rect(0, 0, 0, 0)));
			}
		}
		for (var entry : loadout.getSecureContainer().getEntries()) {
			ItemStack stack = entry.getStack();
			if (stack.getItem() instanceof TarkovMedicalItem) {
				out.add(new HealthMedSlot(TarkovNetworking.LOC_SECURE_CONTAINER, 0, entry.getEntryId(), stack, new Rect(0, 0, 0, 0)));
			}
		}
		if (snap != null) {
			for (int ri = 0; ri < snap.ownRigViews().size(); ri++) {
				TarkovNetworking.GridView gv = snap.ownRigViews().get(ri);
				if (!gv.gridRevealed()) continue;
				for (TarkovNetworking.ViewEntry e : gv.entries()) {
					if (e.discovered() && !e.stack().isEmpty() && e.stack().getItem() instanceof TarkovMedicalItem) {
						out.add(new HealthMedSlot(TarkovNetworking.LOC_RIG_GRID, ri, e.entryId(), e.stack(), new Rect(0, 0, 0, 0)));
					}
				}
			}
			TarkovNetworking.GridView bpView = snap.ownBackpackView();
			if (bpView != null && bpView.gridRevealed()) {
				for (TarkovNetworking.ViewEntry e : bpView.entries()) {
					if (e.discovered() && !e.stack().isEmpty() && e.stack().getItem() instanceof TarkovMedicalItem) {
						out.add(new HealthMedSlot(TarkovNetworking.LOC_BACKPACK_GRID, 0, e.entryId(), e.stack(), new Rect(0, 0, 0, 0)));
					}
				}
			}
		}
		if (isStash() && snap != null && snap.externalGridView() != null) {
			for (TarkovNetworking.ViewEntry entry : snap.externalGridView().entries()) {
				if (entry.discovered() && entry.stack().getItem() instanceof TarkovMedicalItem) {
					out.add(new HealthMedSlot(TarkovNetworking.LOC_EXTERNAL_GRID, 0, entry.entryId(), entry.stack(), new Rect(0, 0, 0, 0)));
				}
			}
		}
		return out;
	}

	@Nullable
	private ItemStack resolveSelectedMedStack(List<HealthMedSlot> meds) {
		if (selectedHealthMed == null) {
			return null;
		}
		for (HealthMedSlot s : meds) {
			if (selectedHealthMed.matches(s)) {
				return s.stack();
			}
		}
		selectedHealthMed = null;
		return null;
	}

	private void drawHealthMedicalStrip(
		DrawContext context, int x, int y, int w, int h,
		List<HealthMedSlot> collectedMeds, int mouseX, int mouseY
	) {
		context.fill(x, y, x + w, y + h, 0xCC0C0E11);
		context.drawBorder(x, y, w, h, TarkovUiTheme.BORDER_PANEL);

		String hdr = Text.translatable("ui.tarkovcraft.medical_strip").getString();
		String hint = Text.translatable("ui.tarkovcraft.medical_hint").getString();
		TarkovUiTheme.drawFlatText(context, textRenderer, hdr, x + 5, y + 3,
			TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
		TarkovUiTheme.drawRightAlignedFlatText(context, textRenderer, hint, x + w - 5, y + 3,
			TarkovUiTheme.TEXT_MUTED, TarkovUiTheme.TEXT_SCALE_SMALL);

		if (collectedMeds.isEmpty()) {
			TarkovUiTheme.drawCenteredFlatText(context, textRenderer, Text.translatable("ui.tarkovcraft.no_meds").getString(),
				x + w / 2, y + 19, TarkovUiTheme.TEXT_MUTED, TarkovUiTheme.TEXT_SCALE_SMALL);
			return;
		}

		int slotSize = 22;
		int gap = 4;
		int sx = x + 6;
		int sy = y + 13;
		int lastOffset = Math.max(0, collectedMeds.size() - (w - 6) / (slotSize + gap));
		healthMedStrip = new Rect(x, y, w, h);
		healthMedMaxOffset = isStash() ? lastOffset : 0;
		healthMedOffset = Math.min(healthMedOffset, healthMedMaxOffset);
		for (HealthMedSlot raw : collectedMeds.subList(healthMedOffset, collectedMeds.size())) {
			if (sx + slotSize > x + w - 4) break;
			Rect r = new Rect(sx, sy, slotSize, slotSize);
			HealthMedSlot slot = new HealthMedSlot(raw.loc(), raw.idx(), raw.entryId(), raw.stack(), r);
			healthMedSlots.add(slot);

			boolean isSel = selectedHealthMed != null && selectedHealthMed.matches(slot);
			boolean hov = r.contains(mouseX, mouseY);
			context.fill(r.x, r.y, r.x + r.w, r.y + r.h, TarkovUiTheme.itemCategoryTint(slot.stack()));
			context.drawBorder(r.x, r.y, r.w, r.h,
				isSel ? TarkovUiTheme.ACCENT_SAND : (hov ? TarkovUiTheme.BORDER_HOVER : TarkovUiTheme.BORDER_OCCUPIED));

			TarkovUiTheme.drawScaledItem(context, slot.stack(), r.x + 1, r.y + 1, r.w - 2, r.h - 2);
			drawCellCornerTags(context, r.x, r.y, r.w, r.h, slot.stack());
			sx += slotSize + gap;
		}
	}

	private boolean handleHealthTabClick(double mouseX, double mouseY) {
		for (BodyHealthHudHelper.QuickHealHitbox qh : quickHealHitboxes) {
			if (qh.contains(mouseX, mouseY)) {
				ClientNetworkSender.sendHealPart(qh.med().loc(), qh.med().idx(), qh.med().entryId(), qh.part());
				return true;
			}
		}
		for (HealthMedSlot slot : healthMedSlots) {
			if (slot.rect().contains(mouseX, mouseY)) {
				boolean isPainkillerOnly = slot.stack().getItem() instanceof TarkovMedicalItem med
					&& med.getMedicalType().isPainkillerOnly();
				if (Screen.hasShiftDown() || isPainkillerOnly) {
					ClientNetworkSender.sendHealPart(slot.loc(), slot.idx(), slot.entryId(), null);
					selectedHealthMed = null;
				} else if (selectedHealthMed != null && selectedHealthMed.matches(slot)) {
					selectedHealthMed = null;
				} else {
					selectedHealthMed = new SelectedMedRef(slot.loc(), slot.idx(), slot.entryId());
				}
				return true;
			}
		}

		if (selectedHealthMed != null) {
			ItemStack selStack = resolveSelectedMedStack(availableHealthMeds);
			if (selStack != null) {
				for (BodyHealthHudHelper.PartHitbox hit : healthPartHitboxes) {
					if (hit.contains(mouseX, mouseY)) {
						if (MedicalHelper.canApplyToPart(ClientLoadoutCache.getBodyHealth(), selStack, hit.part())) {
							ClientNetworkSender.sendHealPart(
								selectedHealthMed.loc(), selectedHealthMed.idx(), selectedHealthMed.entryId(), hit.part()
							);
						}
						return true;
					}
				}
			}
		}
		return true;
	}

	@Nullable
	private InteractiveRegion findRegionAt(double mx, double my) {
		FloatingWindowLayout window = findFloatingWindowAt(mx, my);
		for (int i = regions.size() - 1; i >= 0; i--) {
			InteractiveRegion region = regions.get(i);
			if (window != null && (region.locType != TarkovNetworking.LOC_FLOATING_CONTAINER
				|| (region.containerIndex >> 8) != window.windowIndex())) {
				continue;
			}
			if (region.hits(mx, my)) {
				return region;
			}
		}
		return null;
	}

	@Nullable
	private FloatingWindowLayout findFloatingWindowAt(double mouseX, double mouseY) {
		if (activeTab == TAB_GEAR) {
			for (int index = floatingLayouts.size() - 1; index >= 0; index--) {
				FloatingWindowLayout window = floatingLayouts.get(index);
				if (window.bounds().contains(mouseX, mouseY)) return window;
			}
		}
		return null;
	}

	private boolean isOverAnyInspectWindow(double mx, double my) {
		for (TarkovInspectWindow win : inspectWindows) {
			if (win.contains(mx, my)) return true;
		}
		return false;
	}

	private void drawMedUseStripIfActive(DrawContext context, int x, int y) {
		BodyPartHealth health = ClientLoadoutCache.getBodyHealth();
		if (!health.isMedUseActive()) return;
		net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(health.getActiveMedItemId());
		ItemStack icon = (id != null && net.minecraft.registry.Registries.ITEM.containsId(id))
			? new ItemStack(net.minecraft.registry.Registries.ITEM.get(id))
			: ItemStack.EMPTY;
		String name = icon.isEmpty() ? "Med" : icon.getName().getString();
		String secStr = String.format(Locale.ROOT, "%.1f", health.getActiveMedRemainingSeconds());
		String label = Text.translatable("ui.tarkovcraft.med_using", name, secStr).getString();

		int w = 136;
		int h = 15;
		context.fill(x, y, x + w, y + h, 0xE00C0F13);
		context.drawBorder(x, y, w, h, 0xFF4CAF50);
		context.fill(x + 2, y + h - 4, x + 2 + Math.round((w - 4) * health.getActiveMedProgress()), y + h - 2, 0xFF4CAF50);
		TarkovUiTheme.drawFlatText(context, textRenderer,
			TarkovUiTheme.trimWithEllipsis(textRenderer, label, w - 8, TarkovUiTheme.TEXT_SCALE_SMALL),
			x + 4, y + 3, TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
	}

	private void renderFloatingContainerWindows(
		DrawContext context, TarkovNetworking.ScreenStateSnapshot snap, int mouseX, int mouseY
	) {
		List<TarkovNetworking.OpenContainerView> open = snap.openContainers();
		int fCell = Math.max(16, Math.min(22, cellSize));
		int pad = 6;
		int headerH = 18;

		for (int winIdx = 0; winIdx < open.size(); winIdx++) {
			TarkovNetworking.OpenContainerView view = open.get(winIdx);
			List<TarkovNetworking.GridView> grids = view.grids();
			if (grids.isEmpty()) continue;

			// Compute layout dimensions (single grid or horizontal/wrapped multi-compartment rig)
			int bodyW = 0;
			int bodyH = 0;
			int maxW = Math.min(this.width - 32, 320);
			int rowX = 0;
			int rowH = 0;
			for (int gi = 0; gi < grids.size(); gi++) {
				TarkovNetworking.GridView gv = grids.get(gi);
				int gw = gv.width() * fCell;
				int gh = gv.height() * fCell;
				if (gi > 0 && rowX + gw + 4 > maxW - pad * 2) {
					bodyH += rowH + 4;
					rowX = 0;
					rowH = 0;
				}
				rowX += gw + (gi > 0 && rowX > 0 ? 4 : 0);
				bodyW = Math.max(bodyW, rowX);
				rowH = Math.max(rowH, gh);
			}
			bodyH += rowH;
			int winW = Math.max(118, bodyW + pad * 2);
			int winH = headerH + pad + bodyH + pad;

			int[] pos = floatingWinPos.computeIfAbsent(view.containerUuid(), k -> new int[]{
				Math.max(12, (this.width - winW) / 2 + floatingWinPos.size() * 18),
				Math.max(TarkovUiTheme.TOP_BAR_H + 10, (this.height - winH) / 2 + floatingWinPos.size() * 18)
			});
			pos[0] = Math.max(4, Math.min(this.width - winW - 4, pos[0]));
			pos[1] = Math.max(TarkovUiTheme.TOP_BAR_H + 2, Math.min(this.height - winH - 4, pos[1]));
			int wx = pos[0];
			int wy = pos[1];

			TarkovUiTheme.clearDepthBuffer(context);
			context.getMatrices().push();
			context.getMatrices().translate(0.0f, 0.0f, 220.0f + winIdx * 10.0f);

			context.fill(wx, wy, wx + winW, wy + winH, TarkovUiTheme.BG_MODAL);
			context.drawBorder(wx, wy, winW, winH, TarkovUiTheme.BORDER_POPUP);
			context.fill(wx + 1, wy + 1, wx + winW - 1, wy + headerH, TarkovUiTheme.BG_MODAL_HEADER);
			context.fill(wx + 1, wy + headerH, wx + winW - 1, wy + headerH + 1, TarkovUiTheme.BORDER_PANEL);

			boolean unsearched = false;
			for (TarkovNetworking.GridView gv : grids) {
				if (!isGridFullySearched(gv)) {
					unsearched = true;
					break;
				}
			}

			Rect closeRect = new Rect(wx + winW - 16, wy + 3, 13, 12);
			boolean cHov = closeRect.contains(mouseX, mouseY);
			context.fill(closeRect.x, closeRect.y, closeRect.x + closeRect.w, closeRect.y + closeRect.h,
				cHov ? TarkovUiTheme.HOVER_MENU_BG : 0xFF281A1A);
			TarkovUiTheme.drawCenteredFlatText(context, textRenderer, "X", closeRect.x + 6, closeRect.y + 3,
				cHov ? TarkovUiTheme.HOVER_MENU_TEXT : 0xFFE57373, TarkovUiTheme.TEXT_SCALE_SMALL);

			Rect searchRect = null;
			int titleReserve = 22;
			if (unsearched) {
				String sTxt = Text.translatable("ui.tarkovcraft.search_btn").getString();
				int sw = Math.max(42, Math.round(textRenderer.getWidth(sTxt) * TarkovUiTheme.TEXT_SCALE_SMALL) + 8);
				searchRect = new Rect(closeRect.x - sw - 4, wy + 3, sw, 12);
				boolean sHov = searchRect.contains(mouseX, mouseY);
				context.fill(searchRect.x, searchRect.y, searchRect.x + searchRect.w, searchRect.y + searchRect.h,
					sHov ? TarkovUiTheme.HOVER_MENU_BG : 0xFF283226);
				context.drawBorder(searchRect.x, searchRect.y, searchRect.w, searchRect.h,
					sHov ? TarkovUiTheme.BORDER_HOVER : TarkovUiTheme.ACCENT_SAND);
				TarkovUiTheme.drawCenteredFlatText(context, textRenderer, sTxt,
					searchRect.x + searchRect.w / 2, searchRect.y + 3,
					sHov ? TarkovUiTheme.HOVER_MENU_TEXT : TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
				titleReserve += sw + 6;
			}

			String title = TarkovUiTheme.trimWithEllipsis(textRenderer,
				view.title().isEmpty() ? "CONTAINER" : view.title().toUpperCase(Locale.ROOT),
				winW - pad - titleReserve, TarkovUiTheme.TEXT_SCALE_SMALL);
			TarkovUiTheme.drawFlatText(context, textRenderer, title, wx + pad, wy + 6,
				TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);

			Rect bounds = new Rect(wx, wy, winW, winH);
			Rect headerRect = new Rect(wx, wy, winW, headerH);
			floatingLayouts.add(new FloatingWindowLayout(view.containerUuid(), winIdx, bounds, headerRect, closeRect, searchRect));

			int gx = wx + pad;
			int gy = wy + headerH + pad;
			int curRowH = 0;
			for (int gi = 0; gi < grids.size(); gi++) {
				TarkovNetworking.GridView gv = grids.get(gi);
				int gw = gv.width() * fCell;
				int gh = gv.height() * fCell;
				if (gi > 0 && gx + gw > wx + winW - pad + 1) {
					gx = wx + pad;
					gy += curRowH + 4;
					curRowH = 0;
				}
				int encodedIdx = (winIdx << 8) | (gi & 0xFF);
				drawGridView(context, gx, gy, bounds, TarkovNetworking.LOC_FLOATING_CONTAINER, encodedIdx, fCell, gv, snap, false, mouseX, mouseY);
				gx += gw + 4;
				curRowH = Math.max(curRowH, gh);
			}

			context.getMatrices().pop();
		}
	}

	@Nullable
	private TarkovNetworking.ViewEntry findEntryAt(TarkovNetworking.GridView view, int gx, int gy) {
		for (TarkovNetworking.ViewEntry e : view.entries()) {
			if (gx >= e.x() && gx < e.x() + e.width() && gy >= e.y() && gy < e.y() + e.height()) {
				return e;
			}
		}
		return null;
	}
}
