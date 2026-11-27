package com.tarkovcraft.client.screen;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.client.ClientNetworkSender;
import com.tarkovcraft.client.ClientWorkbenchCache;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.health.TaCZBulletHelper;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovArmorRepairItem;
import com.tarkovcraft.screen.TarkovWorkbenchScreenHandler;
import com.tarkovcraft.workbench.TarkovWorkbenchRecipes;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen translucent EFT-style Workbench screen for crafting tiered TaCZ ammo
 * and TarkovCraft armor/rigs/backpacks/plates.
 */
@Environment(EnvType.CLIENT)
public class TarkovWorkbenchScreen extends HandledScreen<TarkovWorkbenchScreenHandler> {
	private static final int TAB_AMMO = 0;
	private static final int TAB_GEAR = 1;
	private static final int ROW_H = 24;

	private record Rect(int x, int y, int w, int h) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private record ClickButton(Rect rect, Runnable action) {
	}

	private enum AmmoSort { CALIBER, GROUP, PEN_ASC, PEN_DESC }

	private enum GearSortMode {
		DEFAULT("ui.tarkovcraft.wb_sort_default"),
		ARMOR_DESC("ui.tarkovcraft.wb_sort_armor_desc"),
		ARMOR_ASC("ui.tarkovcraft.wb_sort_armor_asc"),
		CAPACITY_DESC("ui.tarkovcraft.wb_sort_cap_desc"),
		CAPACITY_ASC("ui.tarkovcraft.wb_sort_cap_asc");

		private final String translationKey;

		GearSortMode(String translationKey) {
			this.translationKey = translationKey;
		}

		public String getTranslationKey() {
			return translationKey;
		}

		public GearSortMode next() {
			GearSortMode[] vals = values();
			return vals[(this.ordinal() + 1) % vals.length];
		}
	}

	private final List<ClickButton> buttons = new ArrayList<>();
	private int activeTab = TAB_AMMO;
	private float contentScale = 1;
	private int contentWidth;
	private int contentHeight;

	// Ammo tab state
	private String selectedCaliber = "tacz:9mm";
	private AmmoSort ammoSort = AmmoSort.CALIBER;
	private TextFieldWidget ammoSearch;
	private AmmoTier selectedTier = AmmoTier.AP;
	private boolean upgradeFromFmj = false;
	private int ammoScrollY = 0;
	private int ammoMaxScroll = 0;
	private Rect ammoListClip;

	// Gear tab state
	private TarkovWorkbenchRecipes.GearCategory gearCategory = TarkovWorkbenchRecipes.GearCategory.HELMET;
	private GearSortMode gearSortMode = GearSortMode.DEFAULT;
	private boolean onlyAvailableGear = false;
	private int selectedGearIndex = 0;
	private int gearScrollY = 0;
	private int gearMaxScroll = 0;
	private Rect gearListClip;
	private TextFieldWidget searchBox;
	private List<TarkovWorkbenchRecipes.GearRecipe> cachedFilteredGear = List.of();
	private TarkovWorkbenchRecipes.GearCategory cachedGearCategory = null;
	private String cachedGearQuery = null;
	private GearSortMode cachedGearSortMode = null;
	private boolean cachedOnlyAvailable = false;
	private boolean cachedCreative = false;
	private int cachedWorkbenchRevision = -1;

	public TarkovWorkbenchScreen(TarkovWorkbenchScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		this.x = 0;
		this.y = 0;
		int availableHeight = Math.max(1, height - topBarOffset());
		contentScale = handler.isLobby() ? Math.min(1, Math.min(width / 440f, availableHeight / 345f)) : 1;
		contentWidth = (int) (width / contentScale);
		contentHeight = (int) (availableHeight / contentScale);
		this.backgroundWidth = contentWidth;
		this.backgroundHeight = contentHeight;

		String prevQuery = searchBox != null ? searchBox.getText() : "";
		String prevAmmoQuery = ammoSearch != null ? ammoSearch.getText() : "";
		int panelW = Math.min(620, contentWidth - 24);
		int panelX = (contentWidth - panelW) / 2;
		int bodyY = TarkovUiTheme.TOP_BAR_H + 8;
		int leftW = Math.max(170, Math.round(panelW * 0.40f));

		searchBox = new TextFieldWidget(
			this.textRenderer,
			panelX + 8,
			bodyY + 24,
			leftW - 16,
			14,
			Text.translatable("ui.tarkovcraft.wb_search")
		);
		searchBox.setMaxLength(48);
		searchBox.setPlaceholder(Text.translatable("ui.tarkovcraft.wb_search"));
		searchBox.setText(prevQuery);
		searchBox.setChangedListener(s -> {
			gearScrollY = 0;
			selectedGearIndex = 0;
		});
		searchBox.setVisible(activeTab == TAB_GEAR);
		addDrawableChild(searchBox);

		ammoSearch = new TextFieldWidget(textRenderer, panelX + 6, bodyY + 6,
			Math.max(170, Math.round(panelW * 0.39f)) - 14, 15, Text.translatable("ui.tarkovcraft.wb_ammo_search"));
		ammoSearch.setMaxLength(48);
		ammoSearch.setPlaceholder(Text.translatable("ui.tarkovcraft.wb_ammo_search"));
		ammoSearch.setText(prevAmmoQuery);
		ammoSearch.setChangedListener(value -> ammoScrollY = 0);
		ammoSearch.setVisible(activeTab == TAB_AMMO);
		addDrawableChild(ammoSearch);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		buttons.clear();
		TarkovUiTheme.drawLobbyBackground(context, width, height);
		TarkovUiTheme.clearDepthBuffer(context);

		if (searchBox != null) {
			searchBox.setVisible(activeTab == TAB_GEAR);
			ammoSearch.setVisible(activeTab == TAB_AMMO);
		}

		context.getMatrices().push();
		context.getMatrices().translate(0, topBarOffset(), 0);
		context.getMatrices().scale(contentScale, contentScale, 1);
		renderWorkbench(context, (int) Math.floor(localX(mouseX)), (int) Math.floor(localY(mouseY)), delta);
		context.getMatrices().pop();
		if (handler.isLobby()) {
			LobbyTopNavBar.render(context, textRenderer, width, mouseX, mouseY, LobbyTopNavBar.Tab.WORKBENCH, lobbyStatus());
		}
	}

	private void renderWorkbench(DrawContext context, int mouseX, int mouseY, float delta) {
		renderTopBar(context, mouseX, mouseY);
		int panelW = Math.min(620, contentWidth - 24);
		int panelH = Math.max(180, contentHeight - TarkovUiTheme.TOP_BAR_H - 18);
		int panelX = (contentWidth - panelW) / 2;
		int panelY = TarkovUiTheme.TOP_BAR_H + 8;

		context.fill(panelX, panelY, panelX + panelW, panelY + panelH, TarkovUiTheme.opaque(TarkovUiTheme.BG_PANEL));
		context.drawBorder(panelX, panelY, panelW, panelH, TarkovUiTheme.BORDER_PANEL);

		if (activeTab == TAB_AMMO) {
			renderAmmoTab(context, panelX, panelY, panelW, panelH, mouseX, mouseY);
		} else {
			renderGearTab(context, panelX, panelY, panelW, panelH, mouseX, mouseY);
		}

		super.render(context, mouseX, mouseY, delta);
	}

	private int topBarOffset() { return handler.isLobby() ? LobbyTopNavBar.HEIGHT : 0; }

	private LobbyTopNavBar.Status lobbyStatus() {
		return LobbyTopNavBar.status(client, handler.lobbyStatus());
	}

	@Override
	public void close() {
		if (handler.isLobby()) LobbyTopNavBar.activate(client, this, handler.syncId, LobbyTopNavBar.Action.PLAY);
		else super.close();
	}

	private void renderTopBar(DrawContext context, int mouseX, int mouseY) {
		int h = TarkovUiTheme.TOP_BAR_H;
		context.fill(0, 0, contentWidth, h, TarkovUiTheme.opaque(TarkovUiTheme.BG_TOP_BAR));
		context.fill(0, h, contentWidth, h + 1, TarkovUiTheme.BORDER_PANEL);

		String title = Text.translatable("screen.tarkovcraft.workbench").getString().toUpperCase(Locale.ROOT);
		TarkovUiTheme.drawFlatText(context, textRenderer, title, 10, 9,
			TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_HEADER);

		String ammoLabel = Text.translatable("tab.tarkovcraft.wb_ammo").getString();
		String gearLabel = Text.translatable("tab.tarkovcraft.wb_gear").getString();
		int ammoW = Math.max(66, Math.round(textRenderer.getWidth(ammoLabel) * TarkovUiTheme.TEXT_SCALE_HEADER) + 18);
		int gearW = Math.max(96, Math.round(textRenderer.getWidth(gearLabel) * TarkovUiTheme.TEXT_SCALE_HEADER) + 18);
		int totalTabsW = ammoW + 6 + gearW;
		int tabX = Math.max(120, (contentWidth - totalTabsW) / 2);

		drawTopTab(context, new Rect(tabX, 4, ammoW, 18), ammoLabel, activeTab == TAB_AMMO, mouseX, mouseY, () -> {
			activeTab = TAB_AMMO;
			if (searchBox != null) searchBox.setFocused(false);
			setFocused(null);
		});
		drawTopTab(context, new Rect(tabX + ammoW + 6, 4, gearW, 18), gearLabel, activeTab == TAB_GEAR, mouseX, mouseY, () -> {
			activeTab = TAB_GEAR;
			if (ammoSearch != null) ammoSearch.setFocused(false);
			setFocused(null);
		});

		String backTxt = Text.translatable("ui.tarkovcraft.back_esc").getString();
		int bw = Math.max(66, Math.round(textRenderer.getWidth(backTxt) * TarkovUiTheme.TEXT_SCALE_SMALL) + 14);
		Rect backRect = new Rect(contentWidth - bw - 8, 5, bw, 16);
		boolean bHov = backRect.contains(mouseX, mouseY);
		context.fill(backRect.x, backRect.y, backRect.x + backRect.w, backRect.y + backRect.h,
			bHov ? TarkovUiTheme.HOVER_MENU_BG : 0xFF1A1D22);
		context.drawBorder(backRect.x, backRect.y, backRect.w, backRect.h, TarkovUiTheme.BORDER_SLOT);
		TarkovUiTheme.drawCenteredFlatText(context, textRenderer, backTxt, backRect.x + backRect.w / 2, backRect.y + 5,
			bHov ? TarkovUiTheme.HOVER_MENU_TEXT : TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		buttons.add(new ClickButton(backRect, this::close));
	}

	private void drawTopTab(DrawContext context, Rect r, String label, boolean selected, int mouseX, int mouseY, Runnable action) {
		boolean hov = r.contains(mouseX, mouseY);
		int bg = selected ? 0xFF28241C : (hov ? 0xFF1E2228 : 0xFF14161A);
		int border = selected ? TarkovUiTheme.ACCENT_SAND : (hov ? TarkovUiTheme.BORDER_HOVER : TarkovUiTheme.BORDER_SLOT);
		context.fill(r.x, r.y, r.x + r.w, r.y + r.h, bg);
		context.drawBorder(r.x, r.y, r.w, r.h, border);
		TarkovUiTheme.drawCenteredFlatText(context, textRenderer, label, r.x + r.w / 2, r.y + 6,
			selected ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		buttons.add(new ClickButton(r, action));
	}

	private void renderAmmoTab(DrawContext context, int px, int py, int pw, int ph, int mouseX, int mouseY) {
		int leftW = Math.max(170, Math.round(pw * 0.39f));
		int rightX = px + leftW + 1;
		int rightW = pw - leftW - 1;
		context.fill(px + leftW, py + 1, px + leftW + 1, py + ph - 1, TarkovUiTheme.BORDER_PANEL);

		drawModeButton(context, new Rect(px + 6, py + 24, leftW - 14, 16),
			Text.translatable("ui.tarkovcraft.wb_ammo_sort." + ammoSort.name().toLowerCase(Locale.ROOT)).getString(),
			false, true, mouseX, mouseY, () -> {
				AmmoSort[] modes = AmmoSort.values();
				ammoSort = modes[(ammoSort.ordinal() + 1) % modes.length];
				ammoScrollY = 0;
			});
		List<TaCZCaliberCatalog.CaliberSpec> calibers = sortedCalibers();
		ammoListClip = new Rect(px + 6, py + 44, leftW - 12, ph - 50);
		if (calibers.isEmpty()) {
			ammoMaxScroll = 0;
			TarkovUiTheme.drawFlatText(context, textRenderer, Text.translatable("ui.tarkovcraft.wb_ammo_empty").getString(),
				px + 8, py + 49, TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);
			return;
		}
		var selected = calibers.stream().filter(spec -> spec.ammoId().equals(selectedCaliber)).findFirst().orElse(calibers.get(0));
		TarkovUiTheme.scissorInGui(context, ammoListClip.x, ammoListClip.y, ammoListClip.x + ammoListClip.w, ammoListClip.y + ammoListClip.h);
		int cy = ammoListClip.y - ammoScrollY;
		for (int i = 0; i < calibers.size(); i++) {
			TaCZCaliberCatalog.CaliberSpec spec = calibers.get(i);
			Rect row = new Rect(ammoListClip.x, cy, ammoListClip.w - 6, ROW_H - 2);
			boolean sel = spec.ammoId().equals(selected.ammoId());
			boolean hov = row.contains(mouseX, mouseY) && ammoListClip.contains(mouseX, mouseY);
			context.fill(row.x, row.y, row.x + row.w, row.y + row.h,
				sel ? 0xFF28241C : (hov ? 0xFF1C2026 : TarkovUiTheme.opaque(TarkovUiTheme.BG_SLOT)));
			context.drawBorder(row.x, row.y, row.w, row.h,
				sel ? TarkovUiTheme.ACCENT_SAND : (hov ? TarkovUiTheme.BORDER_HOVER : TarkovUiTheme.BORDER_CELL));

			ItemStack icon = AmmoTier.createTaczAmmoStack(spec.ammoId(), selectedTier, 1);
			TarkovUiTheme.drawScaledItem(context, icon, row.x + 2, row.y + 2, 18, 18);

			String name = TarkovUiTheme.trimWithEllipsis(textRenderer, spec.displayName(), row.w - 62, TarkovUiTheme.TEXT_SCALE_SMALL);
			TarkovUiTheme.drawFlatText(context, textRenderer, name, row.x + 24, row.y + 7,
				sel ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
			TarkovUiTheme.drawRightAlignedFlatText(context, textRenderer, Text.translatable("ui.tarkovcraft.wb_ammo_pen_short", penetration(spec)).getString(),
				row.x + row.w - 5, row.y + 7, TarkovUiTheme.TEXT_MUTED, TarkovUiTheme.TEXT_SCALE_SMALL);

			if (ammoListClip.contains(mouseX, mouseY)) {
				buttons.add(new ClickButton(row, () -> {
					selectedCaliber = spec.ammoId();
					selectedTier = spec.clampTier(selectedTier);
					if (selectedTier == AmmoTier.FMJ) {
						upgradeFromFmj = false;
					}
				}));
			}
			cy += ROW_H;
		}
		context.disableScissor();

		ammoMaxScroll = Math.max(0, calibers.size() * ROW_H - ammoListClip.h);
		ammoScrollY = Math.max(0, Math.min(ammoScrollY, ammoMaxScroll));
		drawScrollbar(context, ammoListClip, ammoScrollY, ammoMaxScroll);

		// Right details column
		TaCZCaliberCatalog.CaliberSpec spec = selected;
		selectedTier = spec.clampTier(selectedTier);
		int rx = rightX + 12;
		int rw = rightW - 24;
		int ry = py + 12;

		ItemStack preview = AmmoTier.createTaczAmmoStack(spec.ammoId(), selectedTier, spec.getBatchSize());
		context.fill(rx, ry, rx + 36, ry + 36, TarkovUiTheme.itemCategoryTint(preview));
		context.drawBorder(rx, ry, 36, 36, TarkovUiTheme.BORDER_OCCUPIED);
		TarkovUiTheme.drawScaledItem(context, preview, rx + 2, ry + 2, 32, 32);

		TarkovUiTheme.drawFlatText(context, textRenderer, spec.displayName(), rx + 44, ry + 5,
			TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_HEADER);
		TarkovUiTheme.drawFlatText(context, textRenderer,
			Text.translatable(selectedTier.getTranslationKey()).getString(),
			rx + 44, ry + 19, selectedTier.getArgbColor(), TarkovUiTheme.TEXT_SCALE_SMALL);
		ry += 44;

		// 4 Tier buttons
		AmmoTier[] tiers = AmmoTier.values();
		int gap = 5;
		int tierW = (rw - gap * (tiers.length - 1)) / tiers.length;
		for (int i = 0; i < tiers.length; i++) {
			AmmoTier t = tiers[i];
			Rect tr = new Rect(rx + i * (tierW + gap), ry, tierW, 18);
			boolean allowed = spec.isTierAllowed(t);
			boolean sel = allowed && t == selectedTier;
			boolean hov = allowed && tr.contains(mouseX, mouseY);
			int bgCol = !allowed ? 0xFF101215 : (sel ? 0xFF23272D : (hov ? 0xFF1A1E24 : TarkovUiTheme.opaque(TarkovUiTheme.BG_SLOT)));
			int bdCol = !allowed ? 0xFF1D2024 : (sel ? t.getArgbColor() : (hov ? TarkovUiTheme.BORDER_HOVER : TarkovUiTheme.BORDER_CELL));
			int txtCol = !allowed ? 0xFF4A4E55 : t.getArgbColor();
			String label = allowed ? t.getBadge() : (t.getBadge() + " N/A");
			context.fill(tr.x, tr.y, tr.x + tr.w, tr.y + tr.h, bgCol);
			context.drawBorder(tr.x, tr.y, tr.w, tr.h, bdCol);
			TarkovUiTheme.drawCenteredFlatText(context, textRenderer, label, tr.x + tr.w / 2, tr.y + 6,
				txtCol, TarkovUiTheme.TEXT_SCALE_SMALL);
			if (allowed) {
				buttons.add(new ClickButton(tr, () -> {
					selectedTier = t;
					if (selectedTier == AmmoTier.FMJ) {
						upgradeFromFmj = false;
					}
				}));
			}
		}
		ry += 24;

		// Mode toggle (From Scratch vs Upgrade FMJ)
		int modeW = (rw - 6) / 2;
		int ownedFmj = ClientWorkbenchCache.getFmjCount(spec.ammoId());
		Rect scratchRect = new Rect(rx, ry, modeW, 16);
		boolean sSel = !upgradeFromFmj || selectedTier == AmmoTier.FMJ;
		drawModeButton(context, scratchRect, Text.translatable("ui.tarkovcraft.wb_mode_scratch").getString(),
			sSel, true, mouseX, mouseY, () -> upgradeFromFmj = false);

		Rect upgradeRect = new Rect(rx + modeW + 6, ry, modeW, 16);
		boolean canUpgradeMode = selectedTier != AmmoTier.FMJ;
		String upgText = Text.translatable("ui.tarkovcraft.wb_mode_upgrade").getString() + " (" + ownedFmj + ")";
		drawModeButton(context, upgradeRect, upgText, upgradeFromFmj && canUpgradeMode, canUpgradeMode, mouseX, mouseY, () -> {
			if (selectedTier != AmmoTier.FMJ) upgradeFromFmj = true;
		});
		ry += 22;

		// Ballistic stats box
		int finalPen = TaCZBulletHelper.computePenetration(spec.ammoId(), selectedTier);
		context.fill(rx, ry, rx + rw, ry + 34, TarkovUiTheme.opaque(TarkovUiTheme.BG_SLOT));
		context.drawBorder(rx, ry, rw, 34, TarkovUiTheme.BORDER_CELL);
		TarkovUiTheme.drawFlatText(context, textRenderer,
			Text.translatable("tooltip.tarkovcraft.ammo_pen", finalPen, AmmoTier.formatArmorClassTarget(finalPen)).getString(),
			rx + 6, ry + 6, selectedTier.getArgbColor(), TarkovUiTheme.TEXT_SCALE_SMALL);
		TarkovUiTheme.drawFlatText(context, textRenderer,
			Text.translatable("tooltip.tarkovcraft.ammo_dmg_wear",
				String.format(Locale.ROOT, "%.2f", selectedTier.getDamageMultiplier()),
				String.format(Locale.ROOT, "%.2f", selectedTier.getArmorWearMultiplier())).getString(),
			rx + 6, ry + 19, TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		ry += 42;

		// Required materials
		TarkovWorkbenchRecipes.AmmoRecipe recipe = TarkovWorkbenchRecipes.getAmmoRecipe(
			spec.ammoId(), selectedTier, upgradeFromFmj && selectedTier != AmmoTier.FMJ
		);
		boolean creative = !handler.isLobby() && client != null && client.player != null && client.player.isCreative();
		boolean allEnough = renderIngredientsList(
			context, rx, ry, rw,
			recipe.materials(),
			recipe.upgradeFromFmj() ? AmmoTier.createTaczAmmoStack(spec.ammoId(), AmmoTier.FMJ, 1) : ItemStack.EMPTY,
			ownedFmj,
			recipe.requiredFmjCount(),
			creative,
			19
		);

		String craftLabel = Text.translatable("ui.tarkovcraft.wb_craft_batch", recipe.batchSize()).getString();
		Rect craftBtn = new Rect(rx, py + ph - 28, rw, 20);
		drawCraftButton(context, craftBtn, craftLabel, creative || allEnough, mouseX, mouseY, () ->
			ClientNetworkSender.sendWorkbenchCraftAmmo(spec.ammoId(), selectedTier, recipe.upgradeFromFmj())
		);
	}

	private List<TaCZCaliberCatalog.CaliberSpec> sortedCalibers() {
		String query = ammoSearch.getText().trim().toLowerCase(Locale.ROOT);
		Comparator<TaCZCaliberCatalog.CaliberSpec> name = Comparator.comparing(
			TaCZCaliberCatalog.CaliberSpec::displayName, String.CASE_INSENSITIVE_ORDER);
		Comparator<TaCZCaliberCatalog.CaliberSpec> order = switch (ammoSort) {
			case CALIBER -> name;
			case GROUP -> Comparator.comparing(TaCZCaliberCatalog.CaliberSpec::group).thenComparing(name);
			case PEN_ASC -> Comparator.comparingInt(this::penetration).thenComparing(name);
			case PEN_DESC -> Comparator.comparingInt(this::penetration).reversed().thenComparing(name);
		};
		return TaCZCaliberCatalog.getAllCalibersWithExtras(ClientWorkbenchCache.getExtraCalibers()).stream()
			.filter(spec -> spec.displayName().toLowerCase(Locale.ROOT).contains(query) || spec.ammoId().contains(query))
			.sorted(order).toList();
	}

	private int penetration(TaCZCaliberCatalog.CaliberSpec spec) {
		return TaCZBulletHelper.computePenetration(spec.ammoId(), spec.clampTier(selectedTier));
	}

	private void renderGearTab(DrawContext context, int px, int py, int pw, int ph, int mouseX, int mouseY) {
		int leftW = Math.max(185, Math.round(pw * 0.42f));
		int rightX = px + leftW + 1;
		int rightW = pw - leftW - 1;
		context.fill(px + leftW, py + 1, px + leftW + 1, py + ph - 1, TarkovUiTheme.BORDER_PANEL);

		// Category filter strip across top of panel
		TarkovWorkbenchRecipes.GearCategory[] cats = TarkovWorkbenchRecipes.GearCategory.values();
		int catGap = 3;
		int catW = (pw - 16 - catGap * (cats.length - 1)) / cats.length;
		for (int i = 0; i < cats.length; i++) {
			TarkovWorkbenchRecipes.GearCategory cat = cats[i];
			Rect cr = new Rect(px + 8 + i * (catW + catGap), py + 6, catW, 14);
			boolean sel = cat == gearCategory;
			boolean hov = cr.contains(mouseX, mouseY);
			context.fill(cr.x, cr.y, cr.x + cr.w, cr.y + cr.h, sel ? 0xFF28241C : (hov ? 0xFF1C2026 : TarkovUiTheme.opaque(TarkovUiTheme.BG_SLOT)));
			context.drawBorder(cr.x, cr.y, cr.w, cr.h, sel ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.BORDER_CELL);
			String label = TarkovUiTheme.trimWithEllipsis(textRenderer,
				Text.translatable(cat.getTranslationKey()).getString(), cr.w - 4, TarkovUiTheme.TEXT_SCALE_SMALL);
			TarkovUiTheme.drawCenteredFlatText(context, textRenderer, label, cr.x + cr.w / 2, cr.y + 4,
				sel ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
			buttons.add(new ClickButton(cr, () -> {
				gearCategory = cat;
				gearScrollY = 0;
				selectedGearIndex = 0;
			}));
		}

		// Sort & Available controls row below searchBox (py + 40, height 14)
		int ctrlW = leftW - 16;
		int sortW = Math.round(ctrlW * 0.60f);
		int availX = px + 8 + sortW + 4;
		int availW = ctrlW - sortW - 4;
		Rect sortRect = new Rect(px + 8, py + 40, sortW, 14);
		boolean sortHov = sortRect.contains(mouseX, mouseY);
		context.fill(sortRect.x, sortRect.y, sortRect.x + sortRect.w, sortRect.y + sortRect.h,
			sortHov ? 0xFF1C2026 : TarkovUiTheme.opaque(TarkovUiTheme.BG_SLOT));
		context.drawBorder(sortRect.x, sortRect.y, sortRect.w, sortRect.h,
			gearSortMode != GearSortMode.DEFAULT ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.BORDER_CELL);
		String sortLabel = TarkovUiTheme.trimWithEllipsis(textRenderer,
			Text.translatable(gearSortMode.getTranslationKey()).getString(), sortRect.w - 6, TarkovUiTheme.TEXT_SCALE_SMALL);
		TarkovUiTheme.drawCenteredFlatText(context, textRenderer, sortLabel, sortRect.x + sortRect.w / 2, sortRect.y + 4,
			gearSortMode != GearSortMode.DEFAULT ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		buttons.add(new ClickButton(sortRect, () -> {
			gearSortMode = gearSortMode.next();
			gearScrollY = 0;
			selectedGearIndex = 0;
		}));

		Rect availRect = new Rect(availX, py + 40, availW, 14);
		boolean availHov = availRect.contains(mouseX, mouseY);
		context.fill(availRect.x, availRect.y, availRect.x + availRect.w, availRect.y + availRect.h,
			onlyAvailableGear ? 0xFF232C20 : (availHov ? 0xFF1C2026 : TarkovUiTheme.opaque(TarkovUiTheme.BG_SLOT)));
		context.drawBorder(availRect.x, availRect.y, availRect.w, availRect.h,
			onlyAvailableGear ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.BORDER_CELL);
		String availPrefix = onlyAvailableGear ? "[x] " : "[ ] ";
		String availLabel = TarkovUiTheme.trimWithEllipsis(textRenderer,
			availPrefix + Text.translatable("ui.tarkovcraft.wb_only_available").getString(),
			availRect.w - 6, TarkovUiTheme.TEXT_SCALE_SMALL);
		TarkovUiTheme.drawCenteredFlatText(context, textRenderer, availLabel, availRect.x + availRect.w / 2, availRect.y + 4,
			onlyAvailableGear ? 0xFF8FD46E : TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		buttons.add(new ClickButton(availRect, () -> {
			onlyAvailableGear = !onlyAvailableGear;
			gearScrollY = 0;
			selectedGearIndex = 0;
		}));

		boolean creative = !handler.isLobby() && client != null && client.player != null && client.player.isCreative();

		// Filter & sort recipes (cached until category, query, sortMode, onlyAvailable, creative, or sync revision changes)
		String query = searchBox != null ? searchBox.getText().trim().toLowerCase(Locale.ROOT) : "";
		int rev = ClientWorkbenchCache.getRevision();
		if (cachedGearCategory != gearCategory
			|| !query.equals(cachedGearQuery)
			|| cachedGearSortMode != gearSortMode
			|| cachedOnlyAvailable != onlyAvailableGear
			|| cachedCreative != creative
			|| cachedWorkbenchRevision != rev) {
			List<TarkovWorkbenchRecipes.GearRecipe> all = TarkovWorkbenchRecipes.getGearRecipes(gearCategory);
			List<TarkovWorkbenchRecipes.GearRecipe> rebuilt = new ArrayList<>();
			for (TarkovWorkbenchRecipes.GearRecipe r : all) {
				if (!query.isEmpty() && !r.resultItem().getName().getString().toLowerCase(Locale.ROOT).contains(query)) {
					continue;
				}
				if (onlyAvailableGear && !creative && !hasAllMaterials(r.materials())) {
					continue;
				}
				rebuilt.add(r);
			}
			if (gearSortMode != GearSortMode.DEFAULT) {
				Comparator<TarkovWorkbenchRecipes.GearRecipe> cmp = switch (gearSortMode) {
					case ARMOR_DESC -> Comparator.comparingInt((TarkovWorkbenchRecipes.GearRecipe r) -> getArmorMetric(r.resultItem())).reversed();
					case ARMOR_ASC -> Comparator.comparingInt(r -> getArmorMetric(r.resultItem()));
					case CAPACITY_DESC -> Comparator.comparingInt((TarkovWorkbenchRecipes.GearRecipe r) -> getCapacityMetric(r.resultItem())).reversed();
					case CAPACITY_ASC -> Comparator.comparingInt(r -> getCapacityMetric(r.resultItem()));
					default -> Comparator.comparing(r -> r.resultItem().getName().getString());
				};
				rebuilt.sort(cmp.thenComparing(r -> r.resultItem().getName().getString(), String.CASE_INSENSITIVE_ORDER));
			}
			cachedFilteredGear = List.copyOf(rebuilt);
			cachedGearCategory = gearCategory;
			cachedGearQuery = query;
			cachedGearSortMode = gearSortMode;
			cachedOnlyAvailable = onlyAvailableGear;
			cachedCreative = creative;
			cachedWorkbenchRevision = rev;
		}
		List<TarkovWorkbenchRecipes.GearRecipe> filtered = cachedFilteredGear;

		gearListClip = new Rect(px + 8, py + 58, leftW - 16, ph - 66);
		TarkovUiTheme.scissorInGui(context, gearListClip.x, gearListClip.y, gearListClip.x + gearListClip.w, gearListClip.y + gearListClip.h);
		int cy = gearListClip.y - gearScrollY;
		if (!filtered.isEmpty()) {
			selectedGearIndex = Math.max(0, Math.min(selectedGearIndex, filtered.size() - 1));
			for (int i = 0; i < filtered.size(); i++) {
				TarkovWorkbenchRecipes.GearRecipe recipe = filtered.get(i);
				Rect row = new Rect(gearListClip.x, cy, gearListClip.w - 6, ROW_H - 2);
				boolean sel = i == selectedGearIndex;
				boolean hov = row.contains(mouseX, mouseY) && gearListClip.contains(mouseX, mouseY);
				context.fill(row.x, row.y, row.x + row.w, row.y + row.h,
					sel ? 0xFF28241C : (hov ? 0xFF1C2026 : TarkovUiTheme.opaque(TarkovUiTheme.BG_SLOT)));
				context.drawBorder(row.x, row.y, row.w, row.h,
					sel ? TarkovUiTheme.ACCENT_SAND : (hov ? TarkovUiTheme.BORDER_HOVER : TarkovUiTheme.BORDER_CELL));

				ItemStack icon = new ItemStack(recipe.resultItem());
				TarkovUiTheme.drawScaledItem(context, icon, row.x + 2, row.y + 2, 18, 18);

				String tag = shortGearTag(recipe.resultItem());
				String name = TarkovUiTheme.trimWithEllipsis(textRenderer,
					icon.getName().getString(), row.w - 58, TarkovUiTheme.TEXT_SCALE_SMALL);
				TarkovUiTheme.drawFlatText(context, textRenderer, name, row.x + 24, row.y + 7,
					sel ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);
				TarkovUiTheme.drawRightAlignedFlatText(context, textRenderer, tag, row.x + row.w - 5, row.y + 7,
					TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);

				final int idx = i;
				if (gearListClip.contains(mouseX, mouseY)) {
					buttons.add(new ClickButton(row, () -> selectedGearIndex = idx));
				}
				cy += ROW_H;
			}
		}
		context.disableScissor();

		gearMaxScroll = Math.max(0, filtered.size() * ROW_H - gearListClip.h);
		gearScrollY = Math.max(0, Math.min(gearScrollY, gearMaxScroll));
		drawScrollbar(context, gearListClip, gearScrollY, gearMaxScroll);

		if (filtered.isEmpty()) {
			return;
		}

		TarkovWorkbenchRecipes.GearRecipe selRecipe = filtered.get(selectedGearIndex);
		Item resultItem = selRecipe.resultItem();
		ItemStack preview = new ItemStack(resultItem);

		int rx = rightX + 12;
		int rw = rightW - 24;
		int ry = py + 24;

		context.fill(rx, ry, rx + 48, ry + 48, TarkovUiTheme.itemCategoryTint(preview));
		context.drawBorder(rx, ry, 48, 48, TarkovUiTheme.BORDER_OCCUPIED);
		TarkovUiTheme.drawScaledItem(context, preview, rx + 2, ry + 2, 44, 44);

		String itemTitle = TarkovUiTheme.trimWithEllipsis(textRenderer, preview.getName().getString(), rw - 58, TarkovUiTheme.TEXT_SCALE_HEADER);
		TarkovUiTheme.drawFlatText(context, textRenderer, itemTitle, rx + 56, ry + 4,
			TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_HEADER);
		renderGearMetaLines(context, resultItem, rx + 56, ry + 18);
		ry += 52;

		boolean hasSlotPreview = resultItem instanceof BackpackItem || resultItem instanceof TacticalRigItem;
		if (hasSlotPreview) {
			ry = renderContainerSlotPreview(context, resultItem, rx, ry, rw);
		}

		int rowH = hasSlotPreview ? 15 : 19;
		boolean allEnough = renderIngredientsList(
			context, rx, ry, rw, selRecipe.materials(), ItemStack.EMPTY, 0, 0, creative, rowH
		);

		String craftLabel = Text.translatable("ui.tarkovcraft.wb_craft").getString();
		Rect craftBtn = new Rect(rx, py + ph - 28, rw, 20);
		drawCraftButton(context, craftBtn, craftLabel, creative || allEnough, mouseX, mouseY, () ->
			ClientNetworkSender.sendWorkbenchCraftGear(Registries.ITEM.getId(resultItem))
		);
	}

	private int renderContainerSlotPreview(DrawContext context, Item item, int x, int y, int w) {
		TarkovUiTheme.drawFlatText(context, textRenderer,
			Text.translatable("ui.tarkovcraft.wb_slot_preview").getString(),
			x, y, TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		y += 10;

		int boxH = 66;
		context.fill(x, y, x + w, y + boxH, 0xFF0A0C0E);
		context.drawBorder(x, y, w, boxH, TarkovUiTheme.BORDER_CELL);

		TarkovUiTheme.scissorInGui(context, x + 1, y + 1, x + w - 1, y + boxH - 1);
		try {
			if (item instanceof BackpackItem bp) {
				int gw = Math.max(1, bp.getGridWidth());
				int gh = Math.max(1, bp.getGridHeight());
				int c = Math.max(5, Math.min(11, Math.min((w - 16) / gw, 52 / gh)));
				int gridW = gw * c;
				int gridH = gh * c;
				int gx = x + 8;
				int gy = y + Math.max(4, (boxH - gridH) / 2);

				for (int cx = 0; cx < gw; cx++) {
					for (int cy = 0; cy < gh; cy++) {
						int rx = gx + cx * c;
						int ry = gy + cy * c;
						context.fill(rx, ry, rx + c, ry + c, TarkovUiTheme.BG_CELL_EMPTY);
						context.drawBorder(rx, ry, c, c, TarkovUiTheme.BORDER_CELL);
					}
				}
				context.drawBorder(gx - 1, gy - 1, gridW + 2, gridH + 2, TarkovUiTheme.ACCENT_SAND);
				String capText = gw + "x" + gh + " (" + (gw * gh) + ")";
				TarkovUiTheme.drawFlatText(context, textRenderer, capText, gx + gridW + 8, gy + 2,
					TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
			} else if (item instanceof TacticalRigItem rig) {
				List<ItemSize> layouts = rig.getCompartmentLayouts();
				int maxRowCells = 10;
				int c = Math.max(5, Math.min(10, (w - 24) / maxRowCells));
				if (layouts.size() > 6) {
					c = Math.min(c, 7);
				}
				int gap = 4;
				int curX = x + 6;
				int curY = y + 5;
				int rowMaxH = 0;

				for (ItemSize sz : layouts) {
					int pw = sz.width() * c;
					int ph = sz.height() * c;
					if (curX + pw > x + w - 6 && curX > x + 6) {
						curX = x + 6;
						curY += rowMaxH + gap;
						rowMaxH = 0;
					}
					for (int sx = 0; sx < sz.width(); sx++) {
						for (int sy = 0; sy < sz.height(); sy++) {
							int rx = curX + sx * c;
							int ry = curY + sy * c;
							context.fill(rx, ry, rx + c, ry + c, TarkovUiTheme.BG_CELL_EMPTY);
							context.drawBorder(rx, ry, c, c, TarkovUiTheme.BORDER_CELL);
						}
					}
					context.drawBorder(curX - 1, curY - 1, pw + 2, ph + 2, TarkovUiTheme.BORDER_OCCUPIED);
					if (pw >= 12 && ph >= 8) {
						TarkovUiTheme.drawCenteredFlatText(context, textRenderer, sz.width() + "x" + sz.height(),
							curX + pw / 2, curY + Math.max(1, ph / 2 - 2), 0xFFCFD6DF, 0.55f);
					}
					curX += pw + gap;
					rowMaxH = Math.max(rowMaxH, ph);
				}
			}
		} finally {
			context.disableScissor();
		}
		return y + boxH + 6;
	}

	private static boolean hasAllMaterials(List<TarkovWorkbenchRecipes.Ingredient> mats) {
		for (TarkovWorkbenchRecipes.Ingredient ing : mats) {
			if (ClientWorkbenchCache.getItemCount(ing.item()) < ing.count()) {
				return false;
			}
		}
		return true;
	}

	private static int getArmorMetric(Item item) {
		if (item instanceof TarkovArmorItem armor) {
			return armor.getArmorClass();
		}
		if (item instanceof TarkovArmorRepairItem repair) {
			return repair.getRepairAmount();
		}
		return 0;
	}

	private static int getCapacityMetric(Item item) {
		if (item instanceof BackpackItem bp) {
			return bp.getGridWidth() * bp.getGridHeight();
		}
		if (item instanceof TacticalRigItem rig) {
			return rig.getCompartmentLayouts().stream().mapToInt(s -> s.width() * s.height()).sum();
		}
		return 0;
	}

	private void renderGearMetaLines(DrawContext context, Item item, int x, int y) {
		if (item instanceof TarkovArmorItem armor) {
			String line1 = armor.getArmorClass() > 0
				? Text.translatable("tooltip.tarkovcraft.armor_class", armor.getArmorClass()).getString()
					+ "  |  " + Text.translatable(armor.getMaterial().getTranslationKey()).getString()
				: Text.translatable("tooltip.tarkovcraft.armor_cosmetic").getString();
			TarkovUiTheme.drawFlatText(context, textRenderer, line1, x, y,
				TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
			String line2 = (armor.getArmorClass() > 0
				? Text.translatable("tooltip.tarkovcraft.armor_durability",
					TarkovArmorItem.formatDurability(new ItemStack(item))).getString() + "  |  "
				: "")
				+ Text.translatable("tooltip.tarkovcraft.armor_weight",
					String.format(Locale.ROOT, "%.1f", armor.getWeightKg())).getString();
			TarkovUiTheme.drawFlatText(context, textRenderer, line2, x, y + 12,
				TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		} else if (item instanceof TarkovArmorRepairItem repair) {
			TarkovUiTheme.drawFlatText(context, textRenderer,
				Text.translatable("tooltip.tarkovcraft.repair_use", repair.getRepairAmount()).getString(),
				x, y, TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
			TarkovUiTheme.drawFlatText(context, textRenderer,
				Text.translatable("tooltip.tarkovcraft.med_weight",
					String.format(Locale.ROOT, "%.2f", repair.getWeightKg())).getString(),
				x, y + 12, TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		} else if (item instanceof TacticalRigItem rig) {
			int total = rig.getCompartmentLayouts().stream().mapToInt(s -> s.width() * s.height()).sum();
			TarkovUiTheme.drawFlatText(context, textRenderer,
				Text.translatable("tooltip.tarkovcraft.rig_capacity", total, rig.getCompartmentLayouts().size()).getString(),
				x, y, TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
		} else if (item instanceof BackpackItem bp) {
			TarkovUiTheme.drawFlatText(context, textRenderer,
				Text.translatable("tooltip.tarkovcraft.backpack_grid", bp.getGridWidth(), bp.getGridHeight()).getString(),
				x, y, TarkovUiTheme.ACCENT_SAND, TarkovUiTheme.TEXT_SCALE_SMALL);
		}
	}

	private boolean renderIngredientsList(
		DrawContext context,
		int x,
		int y,
		int w,
		List<TarkovWorkbenchRecipes.Ingredient> mats,
		ItemStack fmjStack,
		int ownedFmj,
		int requiredFmj,
		boolean creative,
		int rowStep
	) {
		TarkovUiTheme.drawFlatText(context, textRenderer,
			Text.translatable("ui.tarkovcraft.wb_ingredients").getString(),
			x, y, TarkovUiTheme.TEXT_SECONDARY, TarkovUiTheme.TEXT_SCALE_SMALL);
		y += 11;

		int rowH = Math.max(13, rowStep - 2);
		boolean allEnough = true;
		if (requiredFmj > 0 && !fmjStack.isEmpty()) {
			boolean ok = creative || ownedFmj >= requiredFmj;
			if (!ok) allEnough = false;
			drawIngredientRow(context, x, y, w, rowH, fmjStack, fmjStack.getName().getString() + " [PS/FMJ]", ownedFmj, requiredFmj, ok, creative);
			y += rowStep;
		}

		for (TarkovWorkbenchRecipes.Ingredient ing : mats) {
			int owned = ClientWorkbenchCache.getItemCount(ing.item());
			boolean ok = creative || owned >= ing.count();
			if (!ok) allEnough = false;
			ItemStack st = new ItemStack(ing.item());
			drawIngredientRow(context, x, y, w, rowH, st, st.getName().getString(), owned, ing.count(), ok, creative);
			y += rowStep;
		}
		return allEnough;
	}

	private void drawIngredientRow(
		DrawContext context,
		int x,
		int y,
		int w,
		int h,
		ItemStack icon,
		String label,
		int owned,
		int required,
		boolean ok,
		boolean creative
	) {
		context.fill(x, y, x + w, y + h, TarkovUiTheme.opaque(TarkovUiTheme.BG_SLOT));
		context.drawBorder(x, y, w, h, TarkovUiTheme.BORDER_CELL);
		int iconSz = Math.max(11, h - 2);
		TarkovUiTheme.drawScaledItem(context, icon, x + 2, y + 1, iconSz, iconSz);
		String trimmed = TarkovUiTheme.trimWithEllipsis(textRenderer, label, w - 70, TarkovUiTheme.TEXT_SCALE_SMALL);
		int textY = y + Math.max(2, (h - 6) / 2);
		TarkovUiTheme.drawFlatText(context, textRenderer, trimmed, x + iconSz + 6, textY,
			TarkovUiTheme.TEXT_PRIMARY, TarkovUiTheme.TEXT_SCALE_SMALL);

		String countStr = creative ? ("INF / " + required) : (owned + " / " + required);
		int countCol = ok ? 0xFF77B85A : 0xFFE55353;
		TarkovUiTheme.drawRightAlignedFlatText(context, textRenderer, countStr, x + w - 6, textY,
			countCol, TarkovUiTheme.TEXT_SCALE_SMALL);
	}

	private void drawModeButton(
		DrawContext context,
		Rect r,
		String label,
		boolean selected,
		boolean enabled,
		int mouseX,
		int mouseY,
		Runnable action
	) {
		boolean hov = enabled && r.contains(mouseX, mouseY);
		int bg = !enabled ? 0xFF111316 : (selected ? 0xFF28241C : (hov ? 0xFF1C2026 : TarkovUiTheme.opaque(TarkovUiTheme.BG_SLOT)));
		int border = !enabled ? 0xFF22252A : (selected ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.BORDER_CELL);
		int textCol = !enabled ? TarkovUiTheme.TEXT_MUTED : (selected ? TarkovUiTheme.ACCENT_SAND : TarkovUiTheme.TEXT_PRIMARY);
		context.fill(r.x, r.y, r.x + r.w, r.y + r.h, bg);
		context.drawBorder(r.x, r.y, r.w, r.h, border);
		String trimmed = TarkovUiTheme.trimWithEllipsis(textRenderer, label, r.w - 8, TarkovUiTheme.TEXT_SCALE_SMALL);
		TarkovUiTheme.drawCenteredFlatText(context, textRenderer, trimmed, r.x + r.w / 2, r.y + 5,
			textCol, TarkovUiTheme.TEXT_SCALE_SMALL);
		if (enabled) {
			buttons.add(new ClickButton(r, action));
		}
	}

	private void drawCraftButton(
		DrawContext context,
		Rect r,
		String label,
		boolean enabled,
		int mouseX,
		int mouseY,
		Runnable action
	) {
		boolean hov = enabled && r.contains(mouseX, mouseY);
		int bg = !enabled ? 0xFF17191D : (hov ? 0xFF3A4933 : 0xFF283525);
		int border = !enabled ? 0xFF2C3036 : (hov ? 0xFF8FD46E : TarkovUiTheme.ACCENT_SAND);
		int textCol = !enabled ? TarkovUiTheme.TEXT_MUTED : (hov ? 0xFFFFFFFF : TarkovUiTheme.ACCENT_SAND);
		context.fill(r.x, r.y, r.x + r.w, r.y + r.h, bg);
		context.drawBorder(r.x, r.y, r.w, r.h, border);
		TarkovUiTheme.drawCenteredFlatText(context, textRenderer, label, r.x + r.w / 2, r.y + 7,
			textCol, TarkovUiTheme.TEXT_SCALE_SMALL);
		if (enabled) {
			buttons.add(new ClickButton(r, action));
		}
	}

	private static String shortGearTag(Item item) {
		if (item instanceof TarkovArmorItem armor) {
			return armor.getArmorClass() > 0 ? "CL." + armor.getArmorClass() : "COSM";
		}
		if (item instanceof TarkovArmorRepairItem repair) {
			return "+" + repair.getRepairAmount();
		}
		if (item instanceof TacticalRigItem rig) {
			int total = rig.getCompartmentLayouts().stream().mapToInt(s -> s.width() * s.height()).sum();
			return total + "c";
		}
		if (item instanceof BackpackItem bp) {
			return bp.getGridWidth() + "x" + bp.getGridHeight();
		}
		return "";
	}

	private double localX(double x) { return x / contentScale; }

	private double localY(double y) { return (y - topBarOffset()) / contentScale; }


	private void drawScrollbar(DrawContext context, Rect area, int scrollY, int maxScroll) {
		if (maxScroll <= 0) return;
		int barX = area.x + area.w - 4;
		int trackH = area.h - 2;
		int thumbH = Math.max(18, Math.round(trackH * (area.h / (float) (area.h + maxScroll))));
		int thumbY = area.y + 1 + Math.round((trackH - thumbH) * (scrollY / (float) maxScroll));
		context.fill(barX, area.y + 1, barX + 3, area.y + 1 + trackH, 0xFF101215);
		context.fill(barX, thumbY, barX + 3, thumbY + thumbH, TarkovUiTheme.BORDER_HOVER);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (handler.isLobby() && LobbyTopNavBar.mouseClicked(width, mouseX, mouseY, button,
				LobbyTopNavBar.Tab.WORKBENCH, lobbyStatus(),
				action -> LobbyTopNavBar.activate(client, this, handler.syncId, action))) return true;
		mouseX = localX(mouseX);
		mouseY = localY(mouseY);
		if (button == 0) {
			for (ClickButton cb : buttons) {
				if (cb.rect().contains(mouseX, mouseY)) {
					cb.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return super.mouseReleased(localX(mouseX), localY(mouseY), button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		return super.mouseDragged(localX(mouseX), localY(mouseY), button, localX(deltaX), deltaY / contentScale);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		mouseX = localX(mouseX);
		mouseY = localY(mouseY);
		int step = (int) Math.round(-amount * 20.0);
		if (activeTab == TAB_AMMO && ammoListClip != null && ammoListClip.contains(mouseX, mouseY)) {
			ammoScrollY = Math.max(0, Math.min(ammoMaxScroll, ammoScrollY + step));
			return true;
		}
		if (activeTab == TAB_GEAR && gearListClip != null && gearListClip.contains(mouseX, mouseY)) {
			gearScrollY = Math.max(0, Math.min(gearMaxScroll, gearScrollY + step));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		TextFieldWidget field = activeTab == TAB_GEAR ? searchBox : ammoSearch;
		if (field != null && field.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) field.setFocused(false);
			else field.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			this.close();
			return true;
		}
		if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
			this.close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		TextFieldWidget field = activeTab == TAB_GEAR ? searchBox : ammoSearch;
		if (field != null && field.isFocused()) return field.charTyped(chr, modifiers);
		return super.charTyped(chr, modifiers);
	}
}
