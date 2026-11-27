package com.tarkovcraft.client.screen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.tarkovcraft.client.screen.AdminCommands.Action;
import com.tarkovcraft.client.screen.AdminCommands.Group;
import com.tarkovcraft.client.screen.AdminGear.Card;
import com.tarkovcraft.client.screen.AdminGear.Category;
import com.tarkovcraft.client.screen.AdminParameters.NumberControl;
import com.tarkovcraft.client.screen.AdminUi.Rect;
import com.tarkovcraft.client.screen.TarkovDropdownWidget.Option;
import com.tarkovcraft.item.AdminItemCatalog;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.network.AdminItemNetworking;
import com.tarkovcraft.screen.AdminMenuScreenHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.tarkovcraft.client.screen.AdminUi.*;

@Environment(EnvType.CLIENT)
public final class AdminMenuScreen extends HandledScreen<AdminMenuScreenHandler> {
	private static final int MARGIN = 12;
	private static final int GAP = 7;
	private static final List<Integer> AMOUNTS = List.of(1, 5, 16, 30);
	private static final int ACTION_HEIGHT = 71;
	private static final int ITEM_HEIGHT = 88;

	private final List<Click> clicks = new ArrayList<>();
	private final Map<AdminItemCatalog.Entry, Integer> amounts = new HashMap<>();
	private final Map<String, String> values = new LinkedHashMap<>();
	private final Map<String, List<Option>> choices = new HashMap<>();
	private List<Action> actions = List.of();
	private List<Action> filteredActions = List.of();
	private List<Card> gear;
	private List<Card> filteredGear = List.of();
	private List<Option> arenas = List.of();
	private CommandDispatcher<CommandSource> dispatcher;
	private Group group = Group.ALL;
	private Category category = Category.ALL;
	private boolean issuing;
	private Action modal;
	private TarkovDropdownWidget dropdown;
	private AdminSearchField search;
	private Rect searchBox;
	private Rect closeBox;
	private Rect grid;
	private Rect modalFields;
	private Rect clickClip;
	private String query = "";
	private String draftArenaId = "";
	private String feedback = "";
	private String hoveredItem = "";
	private int gridScroll;
	private int gridMaxScroll;
	private int modalScroll;
	private int modalMaxScroll;
	private int mouseX;
	private int mouseY;
	private int keyboardFocus = -1;
	private boolean activationHeld;
	private boolean confirmation;

	public AdminMenuScreen(AdminMenuScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@Override
	protected void init() {
		super.init();
		int searchWidth = Math.min(216, Math.max(80, width / 3));
		closeBox = new Rect(width - MARGIN - 51, 13, 48, 20);
		searchBox = new Rect(closeBox.x() - 5 - searchWidth, closeBox.y(), searchWidth, closeBox.h());
		search = addSelectableChild(new AdminSearchField(textRenderer,
			new Rect(searchBox.x() + 8, searchBox.y() + (searchBox.h() - 8) / 2, searchBox.w() - 16, 8),
			"Поиск", "Поиск…"));
		search.setMaxLength(120);
		search.setText(query);
		search.setChangedListener(value -> {
			query = value;
			filter();
		});
		dropdown = null;
		loadCommands();
	}

	private ClientPlayNetworkHandler connection() {
		return client == null ? null : client.getNetworkHandler();
	}

	private void loadCommands() {
		var network = connection();
		dispatcher = network == null ? null : network.getCommandDispatcher();
		actions = AdminCommands.available(dispatcher);
		if (modal != null) {
			String key = modal.key();
			modal = actions.stream().filter(action -> action.key().equals(key)).findFirst().orElse(null);
		}
		filter();
	}

	@Override
	protected void handledScreenTick() {
		var network = connection();
		if (network != null && network.getCommandDispatcher() != dispatcher) loadCommands();
		search.tick();
		if (dropdown != null) dropdown.tick();
	}

	private boolean itemMode() {
		return group == Group.GEAR && issuing;
	}

	private void filter() {
		gridScroll = 0;
		keyboardFocus = -1;
		if (itemMode()) {
			if (gear == null) gear = AdminGear.cards();
			String[] terms = AdminCommands.searchable(query).split("\\s+");
			filteredGear = gear.stream().filter(card -> card.matches(category, terms)).toList();
		} else {
			filteredActions = actions.stream().filter(action -> (group == Group.ALL || action.group() == group)
				&& action.matches(query)).toList();
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		clicks.clear();
		hoveredItem = "";
		ctx.fill(0, 0, width, height, BACKGROUND);
		drawHeader(ctx, delta);
		int bottom = tabs(ctx, List.of(Group.values()), group, value -> value.title, value -> {
			group = value;
			filter();
		}, 56);
		if (group == Group.GEAR) {
			bottom = tabs(ctx, List.of(false, true), issuing, value -> value ? "Выдать предметы" : "Наборы и киты", value -> {
				issuing = value;
				if (issuing) gear = null;
				filter();
			}, bottom + 3);
			if (issuing) bottom = tabs(ctx, List.of(Category.values()), category, value -> value.title, value -> {
				category = value;
				filter();
			}, bottom + 3);
		}
		drawGrid(ctx, bottom + 7);
		String footer = hoveredItem.isBlank() ? feedback : hoveredItem;
		label(ctx, footer, MARGIN, height - 13, width - MARGIN * 2 - 55, MUTED);
		String total = Integer.toString(itemMode() ? filteredGear.size() : filteredActions.size());
		label(ctx, total, width - MARGIN - textRenderer.getWidth(total), height - 13, 50, MUTED);
		if (modal != null) {
			clicks.clear();
			drawModal(ctx);
		}
		if (dropdown != null) dropdown.render(ctx, mouseX, mouseY, delta);
	}

	private void drawHeader(DrawContext ctx, float delta) {
		Rect header = new Rect(MARGIN - 3, 7, width - 2 * (MARGIN - 3), 43);
		fillRoundedRect(ctx, header, 6, PANEL);
		drawRoundedBorder(ctx, header, 6, BORDER);
		label(ctx, "TARKOVCRAFT // ADMIN", MARGIN + 6, 17, searchBox.x() - MARGIN - 12, INK);
		String location = location();
		Rect badge = new Rect(MARGIN + 5, 31, Math.min(width - MARGIN * 2 - 12, textRenderer.getWidth(location) + 12), 15);
		fillRoundedRect(ctx, badge, 4, CONTROL);
		drawRoundedBorder(ctx, badge, 4, BORDER);
		center(ctx, textRenderer, location, badge, MUTED);
		fillRoundedRect(ctx, searchBox, 4, CONTROL);
		drawRoundedBorder(ctx, searchBox, 4, search.isFocused() || searchBox.contains(mouseX, mouseY) ? AMBER : BORDER);
		search.render(ctx, mouseX, mouseY, delta);
		control(ctx, closeBox, "✕ [ESC]", false, false, this::close);
	}

	private String location() {
		if (client.player == null) return "";
		var pos = client.player.getBlockPos();
		var dimension = client.player.getWorld().getRegistryKey().getValue();
		String name = dimension.getNamespace().equals("minecraft") ? dimension.getPath() : dimension.toString();
		return name + " · " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private <T> int tabs(DrawContext ctx, List<T> tabs, T selected, java.util.function.Function<T, String> names,
		Consumer<T> onSelect, int y) {
		int x = MARGIN;
		for (T tab : tabs) {
			String title = names.apply(tab);
			int w = textRenderer.getWidth(title) + 12;
			if (x + w > width - MARGIN && x != MARGIN) {
				x = MARGIN;
				y += 22;
			}
			control(ctx, new Rect(x, y, w, 18), title, tab.equals(selected), false, () -> onSelect.accept(tab));
			x += w + 4;
		}
		return y + 18;
	}

	private void drawGrid(DrawContext ctx, int top) {
		grid = new Rect(MARGIN, top, width - MARGIN * 2, Math.max(1, height - 20 - top));
		int total = itemMode() ? filteredGear.size() : filteredActions.size();
		if (total == 0) {
			gridMaxScroll = 0;
			center(ctx, textRenderer, actions.isEmpty() ? "Сервер не предоставил команды TarkovCraft." : "Ничего не найдено",
				new Rect(grid.x(), grid.y(), grid.w(), Math.min(30, grid.h())), MUTED);
			return;
		}
		int columns = itemMode() ? MathHelper.clamp((grid.w() + GAP) / 89, 1, 5) : grid.w() < 520 ? 2 : 3;
		int rowHeight = (itemMode() ? ITEM_HEIGHT : ACTION_HEIGHT) + GAP;
		int cardWidth = (grid.w() - 6 - GAP * (columns - 1)) / columns;
		int rows = (total + columns - 1) / columns;
		gridMaxScroll = Math.max(0, rows * rowHeight - GAP - grid.h());
		gridScroll = MathHelper.clamp(gridScroll, 0, gridMaxScroll);
		ctx.enableScissor(grid.x(), grid.y(), grid.right(), grid.bottom());
		clickClip = grid;
		int first = gridScroll / rowHeight;
		int last = Math.min(rows, (gridScroll + grid.h() + rowHeight - 1) / rowHeight);
		for (int row = first; row < last; row++) {
			for (int column = 0; column < columns; column++) {
				int index = row * columns + column;
				if (index >= total) break;
				Rect card = new Rect(grid.x() + column * (cardWidth + GAP), grid.y() + row * rowHeight - gridScroll,
					cardWidth, rowHeight - GAP);
				boolean hover = grid.contains(mouseX, mouseY) && card.contains(mouseX, mouseY);
				fillRoundedRect(ctx, card, 6, hover ? HOVER : PANEL);
				drawRoundedBorder(ctx, card, 6, BORDER);
				if (itemMode()) drawItemCard(ctx, card, filteredGear.get(index), hover);
				else drawActionCard(ctx, card, filteredActions.get(index));
			}
		}
		clickClip = null;
		ctx.disableScissor();
		scrollbar(ctx, grid, gridScroll, gridMaxScroll);
	}

	private void drawActionCard(DrawContext ctx, Rect card, Action action) {
		label(ctx, action.title(), card.x() + 9, card.y() + 9, card.w() - 18, INK);
		var lines = textRenderer.getTextHandler().wrapLines(action.description(), card.w() - 18, Style.EMPTY);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			String line = lines.get(i).getString();
			if (i == 1 && lines.size() > 2) line = textRenderer.trimToWidth(line, card.w() - 18 - textRenderer.getWidth("…")) + "…";
			label(ctx, line, card.x() + 9, card.y() + 24 + 10 * i, card.w() - 18, MUTED);
		}
		boolean configure = !action.parameters().isEmpty() || action.confirm();
		control(ctx, new Rect(card.x() + 8, card.bottom() - 23, Math.min(card.w() - 16, 91), 16),
			configure ? "Настроить" : "Выполнить", false, !configure,
			configure ? () -> openAction(action) : () -> sendCommand(action, List.of()));
	}

	private void drawItemCard(DrawContext ctx, Rect rect, Card card, boolean hovered) {
		ctx.drawItem(card.stack(), rect.x() + 6, rect.y() + 7);
		int badgeWidth = Math.min(rect.w() - 30, textRenderer.getWidth(card.badge()) + 10);
		Rect badge = new Rect(rect.right() - badgeWidth - 5, rect.y() + 8, badgeWidth, 14);
		fillRoundedRect(ctx, badge, 4, ACTIVE);
		center(ctx, textRenderer, card.badge(), badge, TEAL);
		label(ctx, card.title(), rect.x() + 6, rect.y() + 30, rect.w() - 12, INK);
		if (hovered) hoveredItem = card.title();
		int w = (rect.w() - 13) / AMOUNTS.size();
		int count = amounts.getOrDefault(card.entry(), 1);
		for (int i = 0; i < AMOUNTS.size(); i++) {
			int amount = AMOUNTS.get(i);
			control(ctx, new Rect(rect.x() + 5 + i * w, rect.y() + 45, w - 1, 15), "x" + amount, amount == count,
				false, () -> amounts.put(card.entry(), amount));
		}
		int full = rect.w() - 10;
		int giveWidth = card.equip() ? (full - 3) / 2 : full;
		control(ctx, new Rect(rect.x() + 5, rect.bottom() - 22, giveWidth, 16), "Выдать", false, true,
			() -> give(card, count, false));
		if (card.equip()) {
			control(ctx, new Rect(rect.x() + 8 + giveWidth, rect.bottom() - 22, full - giveWidth - 3, 16),
				"Надеть", false, false, () -> give(card, 1, true));
		}
	}

	private void give(Card card, int count, boolean equip) {
		if (!ClientPlayNetworking.canSend(AdminItemNetworking.C2S_ADMIN_GIVE_ITEM)) {
			feedback = "Сервер не поддерживает выдачу из панели.";
			return;
		}
		var buf = PacketByteBufs.create();
		buf.writeVarInt(handler.syncId);
		buf.writeString(card.entry().itemId().toString());
		buf.writeString(card.entry().variant() == null ? "" : card.entry().variant().toString());
		buf.writeByte(count);
		buf.writeBoolean(equip);
		ClientPlayNetworking.send(AdminItemNetworking.C2S_ADMIN_GIVE_ITEM, buf);
		feedback = "Запрос отправлен · " + card.title() + " ×" + count;
	}

	private void openAction(Action action) {
		modal = action;
		values.clear();
		choices.clear();
		confirmation = false;
		modalScroll = 0;
		keyboardFocus = -1;
		feedback = "";
		draftArenaId = newArenaId();
		for (var parameter : action.parameters()) {
			String name = parameter.name();
			NumberControl number = number(parameter);
			if (number != null) {
				values.put(name, number.value(number.initial()));
			} else {
				var options = AdminParameters.choices(action, parameter, client, arenas, draftArenaId);
				choices.put(name, options);
				values.put(name, options.isEmpty() ? "" : options.get(0).value());
			}
		}
		if (needsArena(action)) refreshArenas("");
	}

	private static boolean needsArena(Action action) {
		return !action.key().equals("arena create") && action.parameters().stream()
			.anyMatch(parameter -> parameter.name().equals("id") || parameter.name().equals("arena"));
	}

	private String newArenaId() {
		return "arena_" + Long.toUnsignedString(System.currentTimeMillis(), 36);
	}

	private void refreshArenas(String preferred) {
		var network = connection();
		if (network == null || dispatcher == null) return;
		var parsed = dispatcher.parse("tarkov raid start ", network.getCommandSource());
		dispatcher.getCompletionSuggestions(parsed).thenAccept(suggestions -> client.execute(() -> {
			if (connection() != network) return;
			arenas = suggestions.getList().stream().map(Suggestion::getText).distinct().sorted()
				.map(id -> new Option(id, id)).toList();
			if (modal == null || !needsArena(modal)) return;
			String name = modal.group() == Group.RAID ? "arena" : "id";
			choices.put(name, arenas);
			if (arenas.stream().anyMatch(option -> option.value().equals(preferred))) {
				setValue(name, preferred);
			} else if (arenas.stream().noneMatch(option -> option.value().equals(values.get(name)))) {
				setValue(name, arenas.isEmpty() ? "" : arenas.get(0).value());
			}
		}));
	}

	private void quickCreate(Action create) {
		String id = newArenaId();
		var args = List.of(id);
		String command = create.command(args);
		if (!valid(create, args, command)) return;
		connection().sendChatCommand(command);
		refreshArenas(id);
	}

	private NumberControl number(AdminCommands.Parameter parameter) {
		TarkovLootPool pool = TarkovLootPool.byId(values.get("pool"));
		BlockPos pos = client.player == null ? BlockPos.ORIGIN : client.player.getBlockPos();
		return AdminParameters.number(parameter, pos.getY(), pool == null ? TarkovLootPool.MILITARY : pool);
	}

	private void drawModal(DrawContext ctx) {
		ctx.fill(0, 0, width, height, 0xB0090C10);
		int w = Math.min(370, width - MARGIN * 2);
		int h = Math.min(height - MARGIN * 2, 104 + modal.parameters().size() * 45);
		Rect box = new Rect((width - w) / 2, (height - h) / 2, w, h);
		fillRoundedRect(ctx, box, 6, PANEL);
		drawRoundedBorder(ctx, box, 6, BORDER);
		label(ctx, modal.title(), box.x() + 12, box.y() + 12, box.w() - 50, INK);
		label(ctx, modal.description(), box.x() + 12, box.y() + 27, box.w() - 24, MUTED);
		control(ctx, new Rect(box.right() - 32, box.y() + 7, 21, 19), "✕", false, false, this::dismiss);
		modalFields = new Rect(box.x() + 12, box.y() + 41, box.w() - 24, box.h() - 92);
		modalMaxScroll = Math.max(0, modal.parameters().size() * 45 - modalFields.h());
		modalScroll = MathHelper.clamp(modalScroll, 0, modalMaxScroll);
		ctx.enableScissor(modalFields.x(), modalFields.y(), modalFields.right(), modalFields.bottom());
		clickClip = modalFields;
		for (int i = 0; i < modal.parameters().size(); i++) {
			int y = modalFields.y() + i * 45 - modalScroll;
			if (y + 41 <= modalFields.y() || y >= modalFields.bottom()) continue;
			drawParameter(ctx, modal.parameters().get(i), new Rect(modalFields.x(), y, modalFields.w() - 5, 41));
		}
		clickClip = null;
		ctx.disableScissor();
		scrollbar(ctx, modalFields, modalScroll, modalMaxScroll);

		var args = arguments(modal);
		String command = modal.command(args);
		boolean enabled = valid(modal, args, command);
		String preview = confirmation ? "Повторно нажмите для подтверждения"
			: enabled ? "/" + command : "Выберите доступные параметры";
		label(ctx, preview, box.x() + 12, box.bottom() - 41, box.w() - 24, MUTED);
		control(ctx, new Rect(box.right() - 111, box.bottom() - 26, 99, 18),
			confirmation ? "Подтвердить" : "Выполнить", false, true, enabled ? this::runModal : null);
		if (needsArena(modal)) {
			Action create = actions.stream().filter(action -> action.key().equals("arena create")).findFirst().orElse(null);
			if (create != null) control(ctx, new Rect(box.x() + 12, box.bottom() - 26, 92, 18), "+ Новая арена",
				false, false, () -> quickCreate(create));
		}
	}

	private void drawParameter(DrawContext ctx, AdminCommands.Parameter parameter, Rect row) {
		String name = parameter.name();
		String value = values.getOrDefault(name, "");
		int y = row.y() + 13;
		label(ctx, parameter.title(), row.x(), row.y() + 1, row.w(), MUTED);
		NumberControl number = number(parameter);
		if (number != null) {
			double current = Double.parseDouble(value);
			int valueWidth = 35;
			control(ctx, new Rect(row.x(), y, 17, 19), "−", false, false,
				() -> setValue(name, number.value(current - number.step())));
			center(ctx, textRenderer, value, new Rect(row.x() + 17, y, valueWidth, 19), INK);
			control(ctx, new Rect(row.x() + 17 + valueWidth, y, 17, 19), "+", false, false,
				() -> setValue(name, number.value(current + number.step())));
			int x = row.x() + 74;
			int presetWidth = Math.min(36, Math.max(17, (row.right() - x) / 4 - 3));
			for (int preset : number.presets()) {
				if (x + presetWidth > row.right()) break;
				control(ctx, new Rect(x, y, presetWidth, 19), Integer.toString(preset), preset == current,
					false, () -> setValue(name, number.value(preset)));
				x += presetWidth + 3;
			}
			return;
		}
		List<Option> options = choices.getOrDefault(name, List.of());
		if (name.equals("enabled") || name.equals("type")) {
			int w = (row.w() - 4) / Math.max(1, options.size());
			for (int i = 0; i < options.size(); i++) {
				Option option = options.get(i);
				control(ctx, new Rect(row.x() + i * w, y, w - 4, 19), option.label(), option.value().equals(value),
					false, () -> setValue(name, option.value()));
			}
			return;
		}
		Option selected = options.stream().filter(option -> option.value().equals(value)).findFirst().orElse(null);
		Rect anchor = new Rect(row.x(), y, row.w(), 20);
		String optionLabel = selected == null ? "Нет вариантов" : selected.label();
		if (options.isEmpty() || modal.key().equals("arena create")) {
			fillRoundedRect(ctx, anchor, 4, CONTROL);
			drawRoundedBorder(ctx, anchor, 4, BORDER);
			center(ctx, textRenderer, optionLabel, anchor, selected == null ? MUTED : INK);
		} else {
			control(ctx, anchor, optionLabel + "  ▾", false, false, () -> {
				dropdown = new TarkovDropdownWidget(textRenderer, anchor, height, options, value, chosen -> {
					setValue(name, chosen);
					dropdown = null;
				});
			});
		}
	}

	private void setValue(String name, String value) {
		values.put(name, value);
		confirmation = false;
		if (name.equals("pool")) {
			for (var parameter : modal.parameters()) {
				if (parameter.name().equals("width") || parameter.name().equals("height")) {
					var number = number(parameter);
					values.put(parameter.name(), number.value(number.initial()));
				}
			}
		}
	}

	private List<String> arguments(Action action) {
		return action.parameters().stream().map(parameter -> values.getOrDefault(parameter.name(), "")).toList();
	}

	private void runModal() {
		if (modal.confirm() && !confirmation) {
			confirmation = true;
			return;
		}
		sendCommand(modal, arguments(modal));
	}

	private boolean valid(Action action, List<String> args, String command) {
		var network = connection();
		return network != null && dispatcher == network.getCommandDispatcher()
			&& action.valid(dispatcher, network.getCommandSource(), args, command);
	}

	private void sendCommand(Action action, List<String> args) {
		String command = action.command(args);
		if (!valid(action, args, command)) return;
		var network = connection();
		if (action.key().equals("locate") || action.key().equals("crate list")) close();
		network.sendChatCommand(command);
		dismiss();
		feedback = "Команда отправлена · " + action.title();
	}

	private void dismiss() {
		modal = null;
		dropdown = null;
		keyboardFocus = -1;
	}

	private void scrollbar(DrawContext ctx, Rect area, int scroll, int max) {
		if (max <= 0) return;
		fillRoundedRect(ctx, new Rect(area.right() - 3, area.y(), 3, area.h()), 2, BORDER);
		int size = Math.min(area.h(), Math.max(8, area.h() * area.h() / (area.h() + max)));
		int y = area.y() + (area.h() - size) * scroll / max;
		fillRoundedRect(ctx, new Rect(area.right() - 3, y, 3, size), 2, AMBER);
	}

	private void label(DrawContext ctx, String text, int x, int y, int max, int color) {
		ctx.drawText(textRenderer, trim(textRenderer, text, max), x, y, color, false);
	}

	private void control(DrawContext ctx, Rect rect, String label, boolean selected, boolean accent, Runnable action) {
		boolean visible = clickClip == null || rect.bottom() > clickClip.y() && rect.y() < clickClip.bottom();
		boolean focused = action != null && visible && clicks.size() == keyboardFocus;
		boolean hover = focused || rect.contains(mouseX, mouseY) && (clickClip == null || clickClip.contains(mouseX, mouseY));
		boolean enabled = action != null;
		int fill = enabled && accent ? (hover ? TEAL : AMBER) : selected || (enabled && hover) ? ACTIVE : CONTROL;
		fillRoundedRect(ctx, rect, 4, fill);
		drawRoundedBorder(ctx, rect, 4, selected || enabled && (hover || accent) ? AMBER : BORDER);
		center(ctx, textRenderer, label, rect, enabled ? (accent ? DARK : INK) : MUTED);
		if (enabled && visible) clicks.add(new Click(rect, clickClip, action));
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		if (dropdown != null) {
			if (!dropdown.mouseClicked(x, y, button)) dropdown = null;
			return true;
		}
		if (button != 0) return false;
		keyboardFocus = -1;
		if (modal == null && searchBox.contains(x, y)) {
			setFocused(search);
			search.onClick(x, y);
			return true;
		}
		setFocused(null);
		for (int i = clicks.size() - 1; i >= 0; i--) {
			Click click = clicks.get(i);
			if (click.rect().contains(x, y) && (click.clip() == null || click.clip().contains(x, y))) {
				click.action().run();
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double amount) {
		if (amount == 0) return false;
		keyboardFocus = -1;
		int change = amount > 0 ? -29 : 29;
		if (dropdown != null) {
			dropdown.scroll(amount);
		} else if (modal != null && modalFields != null && modalFields.contains(x, y)) {
			modalScroll = MathHelper.clamp(modalScroll + change, 0, modalMaxScroll);
		} else if (modal == null && grid != null && grid.contains(x, y)) {
			gridScroll = MathHelper.clamp(gridScroll + change, 0, gridMaxScroll);
		}
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (dropdown != null) dropdown = null;
			else if (modal != null) dismiss();
			else close();
			return true;
		}
		if (dropdown != null) {
			if (isActivation(key)) {
				if (!activationHeld) dropdown.keyPressed(key, scanCode, modifiers);
				activationHeld = true;
				return true;
			}
			dropdown.keyPressed(key, scanCode, modifiers);
			return true;
		}
		if (modal == null && hasControlDown() && key == GLFW.GLFW_KEY_F) {
			setFocused(search);
			search.setCursorToEnd();
			search.setSelectionStart(0);
			keyboardFocus = -1;
			return true;
		}
		if (key == GLFW.GLFW_KEY_TAB && !clicks.isEmpty()) {
			setFocused(null);
			keyboardFocus = keyboardFocus < 0 ? (hasShiftDown() ? clicks.size() - 1 : 0)
				: Math.floorMod(keyboardFocus + (hasShiftDown() ? -1 : 1), clicks.size());
			return true;
		}
		if (modal == null && getFocused() == search) return search.keyPressed(key, scanCode, modifiers);
		if ((isActivation(key) || key == GLFW.GLFW_KEY_SPACE) && keyboardFocus >= 0 && keyboardFocus < clicks.size()) {
			if (!activationHeld) clicks.get(keyboardFocus).action().run();
			activationHeld = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (dropdown != null) return dropdown.charTyped(chr, modifiers);
		return modal == null && getFocused() == search && search.charTyped(chr, modifiers);
	}

	@Override
	public boolean keyReleased(int key, int scancode, int modifiers) {
		if (isActivation(key) || key == GLFW.GLFW_KEY_SPACE) activationHeld = false;
		return super.keyReleased(key, scancode, modifiers);
	}

	private static boolean isActivation(int key) {
		return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) { }

	private record Click(Rect rect, Rect clip, Runnable action) { }
}
