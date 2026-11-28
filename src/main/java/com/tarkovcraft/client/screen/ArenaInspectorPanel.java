package com.tarkovcraft.client.screen;

import com.tarkovcraft.client.ClientArenaEditor;
import com.tarkovcraft.client.ClientArenaEditor.ArenaView;
import com.tarkovcraft.client.screen.AdminUi.Rect;
import com.tarkovcraft.network.ArenaAdminPackets;
import com.tarkovcraft.network.ArenaAdminPackets.Action;
import com.tarkovcraft.network.ArenaAdminPackets.Details;
import com.tarkovcraft.network.ArenaAdminPackets.Key;
import com.tarkovcraft.network.ArenaAdminPackets.Point;
import com.tarkovcraft.network.ArenaAdminPackets.Section;
import com.tarkovcraft.network.ArenaAdminPackets.Setting;
import com.tarkovcraft.network.ArenaAdminPackets.SnapshotPart;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static com.tarkovcraft.client.screen.AdminUi.*;

final class ArenaInspectorPanel {
	private final AdminMenuScreen screen;
	private final List<ArenaView> incoming = new ArrayList<>();
	private List<ArenaView> arenas = List.of();
	private Details incomingDetails;
	private List<Point> incomingPoints = new ArrayList<>();
	private boolean receiving;
	private boolean loaded;
	private Key selection;
	private Key pendingSelection;
	private Section section = Section.BOUNDARY;
	private boolean quickOpen;
	private Rect listClip;
	private Rect detailClip;
	private int listScroll;
	private int detailScroll;
	private int maxListScroll;
	private int maxDetailScroll;
	private AdminSearchField createId;
	private AdminSearchField createName;
	private String tooltipTitle;
	private String tooltipPosition;

	ArenaInspectorPanel(AdminMenuScreen screen) {
		this.screen = screen;
	}

	void refresh() {
		send(Action.REFRESH, null, out -> { });
	}

	void receive(SnapshotPart part, Details details, List<Point> points) {
		if (part == SnapshotPart.START) {
			receiving = true;
			incoming.clear();
			incomingDetails = null;
			incomingPoints = new ArrayList<>();
		}
		if (!receiving) return;
		switch (part) {
			case ARENA -> {
				finishArena();
				incomingDetails = details;
			}
			case POINTS -> incomingPoints.addAll(points);
			case END -> {
				finishArena();
				receiving = false;
				loaded = true;
				arenas = List.copyOf(incoming);
				if (pendingSelection != null) {
					if (arenas.stream().anyMatch(arena -> arena.details().key().equals(pendingSelection))) {
						selection = pendingSelection;
						detailScroll = 0;
					}
					pendingSelection = null;
				}
				arenas.forEach(ClientArenaEditor::updatePreview);
			}
			default -> { }
		}
	}

	private void finishArena() {
		if (incomingDetails == null) return;
		incoming.add(new ArenaView(incomingDetails, List.copyOf(incomingPoints)));
		incomingDetails = null;
		incomingPoints = new ArrayList<>();
	}

	void resetSearchScroll() {
		listScroll = 0;
	}

	void draw(DrawContext ctx, Rect area, String query, int mx, int my) {
		tooltipTitle = null;
		int leftWidth = Math.min(166, Math.max(82, area.w() * 3 / 10));
		Rect left = new Rect(area.x(), area.y(), leftWidth, area.h());
		Rect right = new Rect(left.right() + 7, area.y(), area.w() - leftWidth - 7, area.h());
		card(ctx, left);
		card(ctx, right);
		String term = query.strip().toLowerCase(Locale.ROOT);
		List<ArenaView> visible = arenas.stream()
			.filter(arena -> (name(arena.details()) + " " + arena.details().key()).toLowerCase(Locale.ROOT).contains(term)).toList();
		ArenaView selected = visible.stream().filter(arena -> arena.details().key().equals(selection)).findFirst().orElse(null);
		if (selected == null && !visible.isEmpty()) {
			selected = visible.get(0);
			selection = selected.details().key();
			detailScroll = 0;
		}
		drawList(ctx, left, visible);
		if (selected != null) drawDetails(ctx, right, selected, mx, my);
		else {
			detailClip = null;
			screen.label(ctx, loaded ? "Нет арен для просмотра" : "Загрузка арен…", right.x() + 8, right.y() + 8, right.w() - 16, MUTED);
		}
		if (tooltipTitle != null) drawTooltip(ctx, mx, my);
	}

	private void drawList(DrawContext ctx, Rect panel, List<ArenaView> visible) {
		screen.label(ctx, "Арены", panel.x() + 6, panel.y() + 8, panel.w() - 35, INK);
		screen.control(ctx, new Rect(panel.right() - 24, panel.y() + 4, 18, 16), "↻", false, false, this::refresh);
		screen.control(ctx, new Rect(panel.x() + 6, panel.y() + 23, panel.w() - 12, 18), "+ Создать арену",
			false, true, this::openCreate);
		listClip = new Rect(panel.x() + 5, panel.y() + 46, panel.w() - 10, Math.max(1, panel.h() - 51));
		maxListScroll = Math.max(0, visible.size() * 41 - listClip.h());
		listScroll = MathHelper.clamp(listScroll, 0, maxListScroll);
		clip(ctx, listClip);
		for (int i = listScroll / 41; i < visible.size(); i++) {
			ArenaView arena = visible.get(i);
			Details details = arena.details();
			int x = listClip.x();
			int y = listClip.y() + i * 41 - listScroll;
			if (y >= listClip.bottom()) break;
			int vertices = (int) arena.points().stream().filter(point -> point.section() == Section.BOUNDARY).count();
			boolean active = details.key().equals(selection);
			Rect row = new Rect(x, y, listClip.w() - 4, 38);
			screen.control(ctx, row, "", active, false, () -> {
				selection = details.key();
				detailScroll = 0;
			});
			screen.label(ctx, name(details), x + 5, y + 4, row.w() - 10, INK);
			screen.label(ctx, vertices + " верш. · " + details.key().dimension().getPath(), x + 5, y + 15, row.w() - 10, MUTED);
			screen.label(ctx, details.ready() ? "Готова" : "Не настроена", x + 5, y + 26, row.w() - 10, details.ready() ? TEAL : AMBER);
		}
		unclip(ctx);
		screen.scrollbar(ctx, listClip, listScroll, maxListScroll);
	}

	private void drawDetails(DrawContext ctx, Rect panel, ArenaView selected, int mx, int my) {
		Details details = selected.details();
		Key key = details.key();
		int x = panel.x() + 7;
		int w = panel.w() - 15;
		detailClip = new Rect(x, panel.y() + 5, w + 4, Math.max(1, panel.h() - 10));
		List<Point> points = selected.points().stream().filter(point -> point.section() == section).toList();
		int mapHeight = MathHelper.clamp(detailClip.h() / 2, 90, 175);
		int controlColumns = w >= 300 ? 3 : 2;
		int quickRows = quickOpen ? (7 + controlColumns - 1) / controlColumns : 0;
		int settingsRows = (6 + controlColumns - 1) / controlColumns;
		int sectionRows = (Section.values().length + controlColumns - 1) / controlColumns;
		maxDetailScroll = Math.max(0, 117 + mapHeight + (quickRows + settingsRows + sectionRows) * 22
			+ Math.max(1, points.size()) * 22 - detailClip.h());
		detailScroll = MathHelper.clamp(detailScroll, 0, maxDetailScroll);
		clip(ctx, detailClip);
		int y = detailClip.y() - detailScroll;
		screen.label(ctx, name(details), x + 2, y + 2, w - 4, INK);
		screen.label(ctx, key.id() + " · " + key.dimension() + (details.active() ? " · Стартовал рейд" : ""),
			x + 2, y + 14, w - 4, MUTED);
		boolean previewing = ClientArenaEditor.isPreviewing(key);
		int buttonWidth = (w - 4) / 2;
		screen.control(ctx, new Rect(x, y + 28, buttonWidth, 18), "Выдать маркер границ", false, false,
			() -> send(Action.GIVE_WAND, key, out -> { }));
		screen.control(ctx, new Rect(x + buttonWidth + 4, y + 28, w - buttonWidth - 4, 18),
			"3D-предпросмотр " + (previewing ? "ВКЛ" : "ВЫКЛ"), previewing, false,
			() -> ClientArenaEditor.togglePreview(selected));
		screen.control(ctx, new Rect(x, y + 49, w, 18), "Добавить точку здесь " + (quickOpen ? "▴" : "▾"),
			quickOpen, false, () -> quickOpen = !quickOpen);
		y += 71;
		if (quickOpen) {
			boolean canAdd = !details.active() && details.boundaryReady() && key.dimension().equals(currentDimension());
			add(ctx, cell(x, y, w, controlColumns, 0), key, Section.PMC, "+ ЧВК", false, canAdd);
			add(ctx, cell(x, y, w, controlColumns, 1), key, Section.SCAV, "+ Дикий", false, canAdd);
			add(ctx, cell(x, y, w, controlColumns, 2), key, Section.EXTRACT, "+ quiet", false, canAdd);
			add(ctx, cell(x, y, w, controlColumns, 3), key, Section.EXTRACT, "+ loud", true, canAdd);
			add(ctx, cell(x, y, w, controlColumns, 4), key, Section.AIRDROP, "+ Аирдроп", false, canAdd);
			add(ctx, cell(x, y, w, controlColumns, 5), key, Section.LOOT, "+ Лут", false, canAdd);
			add(ctx, cell(x, y, w, controlColumns, 6), key, Section.POI, "+ POI", false, canAdd);
			y += quickRows * 22 + 2;
		}
		Rect map = new Rect(x, y, w, mapHeight);
		if (map.bottom() > detailClip.y() && map.y() < detailClip.bottom()) drawMap(ctx, map, selected, mx, my);
		y += mapHeight + 8;
		screen.label(ctx, "Параметры арены", x + 2, y, w, INK);
		y += 13;
		int k = 0;
		number(ctx, cell(x, y, w, controlColumns, k++), details, Setting.DURATION, "Рейд, мин.", details.duration(), 1, 60, 5);
		toggle(ctx, cell(x, y, w, controlColumns, k++), details, Setting.AIRDROPS, "Аирдропы", details.airdrops());
		number(ctx, cell(x, y, w, controlColumns, k++), details, Setting.MAX_Y, "Макс. Y", details.maxY(), details.bottomY(), details.topY() - 1, 8);
		toggle(ctx, cell(x, y, w, controlColumns, k++), details, Setting.BOSS, "Босс", details.boss());
		toggle(ctx, cell(x, y, w, controlColumns, k++), details, Setting.SCAVS, "Боты Дикие", details.scavs());
		number(ctx, cell(x, y, w, controlColumns, k), details, Setting.SCAV_COUNT, "Кол-во ботов", details.scavCount(), 0, 32, 4);
		y += settingsRows * 22 + 5;
		screen.label(ctx, "Точки арены", x + 2, y, w, INK);
		y += 13;
		int[] counts = new int[Section.values().length];
		for (Point point : selected.points()) counts[point.section().ordinal()]++;
		for (Section tab : Section.values()) {
			screen.control(ctx, cell(x, y, w, controlColumns, tab.ordinal()),
				ClientArenaEditor.label(tab) + " · " + counts[tab.ordinal()], section == tab, false, () -> section = tab);
		}
		y += sectionRows * 22;
		if (points.isEmpty()) screen.label(ctx, "В этом списке пока нет точек", x + 2, y + 6, w, MUTED);
		for (int i = Math.max(0, (detailClip.y() - y) / 22); i < points.size(); i++) {
			int rowY = y + i * 22;
			if (rowY >= detailClip.bottom()) break;
			drawPoint(ctx, new Rect(x, rowY, w, 19), details, points.get(i), i);
		}
		unclip(ctx);
		screen.scrollbar(ctx, detailClip, detailScroll, maxDetailScroll);
	}

	private void drawPoint(DrawContext ctx, Rect row, Details details, Point point, int index) {
		fillRoundedRect(ctx, row, 4, CONTROL);
		String title = point.section() == Section.BOUNDARY ? "#" + (index + 1) : ClientArenaEditor.pointLabel(point);
		screen.label(ctx, title + " · " + point.pos().toShortString(), row.x() + 5, row.y() + 6, row.w() - 57,
			ClientArenaEditor.color(point.section()));
		screen.control(ctx, new Rect(row.right() - 46, row.y() + 1, 23, 17), "ТП", false, false,
			() -> pointAction(Action.TELEPORT, details.key(), point, index));
		screen.control(ctx, new Rect(row.right() - 21, row.y() + 1, 19, 17), "✕", false, false,
			details.active() ? null : () -> pointAction(Action.REMOVE, details.key(), point, index));
	}

	private void pointAction(Action action, Key key, Point point, int index) {
		send(action, key, out -> {
			out.writeVarInt(index);
			point.write(out);
		});
		if (action == Action.TELEPORT) screen.close();
	}

	private void number(DrawContext ctx, Rect cell, Details details, Setting setting, String label, int value, int min, int max, int step) {
		int stepSize = AdminMenuScreen.hasShiftDown() ? step : 1;
		Consumer<Integer> change = amount -> setting(details, setting, MathHelper.clamp(value + amount, min, max));
		screen.control(ctx, new Rect(cell.x(), cell.y(), 15, cell.h()), "−", false, false,
			details.active() || value <= min ? null : () -> change.accept(-stepSize));
		screen.control(ctx, new Rect(cell.x() + 17, cell.y(), cell.w() - 34, cell.h()), label + " " + value, false, false, null);
		screen.control(ctx, new Rect(cell.right() - 15, cell.y(), 15, cell.h()), "+", false, false,
			details.active() || value >= max ? null : () -> change.accept(stepSize));
	}

	private void toggle(DrawContext ctx, Rect cell, Details details, Setting setting, String label, boolean on) {
		screen.control(ctx, cell, label + (on ? " ВКЛ" : " ВЫКЛ"), on, false,
			details.active() ? null : () -> setting(details, setting, on ? 0 : 1));
	}

	private void setting(Details details, Setting setting, int value) {
		send(Action.SET, details.key(), out -> out.writeEnumConstant(setting).writeInt(value));
	}

	private void add(DrawContext ctx, Rect cell, Key key, Section section, String label, boolean loud, boolean enabled) {
		screen.control(ctx, cell, label, false, false,
			enabled ? () -> send(Action.ADD, key, out -> out.writeEnumConstant(section).writeBoolean(loud)) : null);
	}

	private static Rect cell(int x, int y, int width, int columns, int index) {
		int column = index % columns;
		return new Rect(x + width * column / columns, y + (index / columns) * 22,
			width * (column + 1) / columns - width * column / columns - 3, 19);
	}

	private void drawMap(DrawContext ctx, Rect map, ArenaView arena, int mx, int my) {
		fillRoundedRect(ctx, map, 5, CONTROL);
		drawRoundedBorder(ctx, map, 5, BORDER);
		List<Point> points = arena.points();
		if (points.isEmpty()) {
			center(ctx, font(), "Выдайте маркер, чтобы разметить границы", map, MUTED);
			return;
		}
		Projection projection = new Projection(map, points);
		List<MapPoint> border = points.stream().filter(point -> point.section() == Section.BOUNDARY).map(projection::project).toList();
		if (arena.details().boundaryReady()) fillPolygon(ctx, map, border);
		for (int i = 1; i < border.size(); i++) line(ctx, border.get(i - 1), border.get(i), AMBER);
		if (arena.details().boundaryReady()) line(ctx, border.get(border.size() - 1), border.get(0), AMBER);
		if (border.size() == 1) dot(ctx, border.get(0), AMBER, 2);
		double closest = 49;
		for (Point point : points) {
			MapPoint projected = projection.project(point);
			if (point.section() != Section.BOUNDARY) dot(ctx, projected, ClientArenaEditor.color(point.section()), 2);
			double distance = Math.pow(mx - projected.x(), 2) + Math.pow(my - projected.y(), 2);
			if (distance <= closest && map.contains(mx, my) && detailClip.contains(mx, my)) {
				closest = distance;
				tooltipTitle = ClientArenaEditor.pointLabel(point);
				tooltipPosition = point.pos().toShortString();
			}
		}
	}

	private record MapPoint(float x, float y) { }

	private record Projection(float x, float y, double centerX, double centerZ, double scale) {
		Projection(Rect map, List<Point> points) {
			this(map, points.stream().mapToInt(point -> point.pos().getX()).min().orElseThrow(),
				points.stream().mapToInt(point -> point.pos().getX()).max().orElseThrow(),
				points.stream().mapToInt(point -> point.pos().getZ()).min().orElseThrow(),
				points.stream().mapToInt(point -> point.pos().getZ()).max().orElseThrow());
		}

		private Projection(Rect map, int minX, int maxX, int minZ, int maxZ) {
			this(map.x() + map.w() / 2f, map.y() + map.h() / 2f, (minX + (double) maxX) / 2, (minZ + (double) maxZ) / 2,
				Math.min((map.w() - 22) / Math.max(1, (double) maxX - minX), (map.h() - 22) / Math.max(1, (double) maxZ - minZ)));
		}

		MapPoint project(Point point) {
			return new MapPoint(x + (float) ((point.pos().getX() - centerX) * scale), y + (float) ((point.pos().getZ() - centerZ) * scale));
		}
	}

	private static void fillPolygon(DrawContext ctx, Rect map, List<MapPoint> border) {
		List<Float> crossings = new ArrayList<>();
		for (int y = map.y(); y < map.bottom(); y++) {
			float center = y + 0.5f;
			crossings.clear();
			for (int i = 0; i < border.size(); i++) {
				MapPoint a = border.get(i);
				MapPoint b = border.get((i + 1) % border.size());
				if ((a.y() > center) != (b.y() > center)) crossings.add(a.x() + (center - a.y()) * (b.x() - a.x()) / (b.y() - a.y()));
			}
			crossings.sort(Float::compare);
			for (int i = 1; i < crossings.size(); i += 2) {
				ctx.fill(Math.round(crossings.get(i - 1)), y, Math.round(crossings.get(i)), y + 1, 0x594A4535);
			}
		}
	}

	private static void line(DrawContext ctx, MapPoint a, MapPoint b, int color) {
		float dx = b.x() - a.x();
		float dy = b.y() - a.y();
		var matrices = ctx.getMatrices();
		matrices.push();
		matrices.translate(a.x(), a.y(), 0);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
		ctx.fill(0, 0, (int) Math.ceil(Math.hypot(dx, dy)), 1, color);
		matrices.pop();
	}

	private static void dot(DrawContext ctx, MapPoint point, int color, int radius) {
		int x = Math.round(point.x());
		int y = Math.round(point.y());
		ctx.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, DARK);
		ctx.fill(x - radius + 1, y - radius + 1, x + radius, y + radius, color);
	}

	private void drawTooltip(DrawContext ctx, int mx, int my) {
		int w = Math.min(screen.width - 8, Math.max(font().getWidth(tooltipTitle), font().getWidth(tooltipPosition)) + 14);
		Rect box = new Rect(Math.max(4, Math.min(mx + 8, screen.width - w - 4)),
			Math.max(4, Math.min(my + 9, screen.height - 36)), w, 31);
		fillRoundedRect(ctx, box, 4, CONTROL);
		drawRoundedBorder(ctx, box, 4, BORDER);
		screen.label(ctx, tooltipTitle, box.x() + 7, box.y() + 5, w - 14, INK);
		screen.label(ctx, tooltipPosition, box.x() + 7, box.y() + 17, w - 14, MUTED);
	}

	private void openCreate() {
		Rect box = createBox();
		createId = new AdminSearchField(font(), new Rect(box.x() + 17, box.y() + 39, box.w() - 34, 14), "ID", "ID арены");
		createId.setMaxLength(48);
		createId.setText("arena_" + Long.toUnsignedString(System.currentTimeMillis(), 36));
		createName = new AdminSearchField(font(), new Rect(box.x() + 17, box.y() + 74, box.w() - 34, 14), "Название", "Название арены");
		createName.setMaxLength(80);
		focus(createName);
	}

	private Rect createBox() {
		int w = Math.min(288, screen.width - 24);
		return new Rect((screen.width - w) / 2, (screen.height - 129) / 2, w, 129);
	}

	boolean creating() {
		return createId != null;
	}

	void drawCreate(DrawContext ctx, int mx, int my, float delta) {
		ctx.fill(0, 0, screen.width, screen.height, 0xB0090C10);
		Rect box = createBox();
		card(ctx, box);
		screen.label(ctx, "Создать арену в текущем измерении", box.x() + 12, box.y() + 11, box.w() - 24, INK);
		drawField(ctx, createId, box, "ID · a–z, 0–9, _ и -", mx, my, delta);
		drawField(ctx, createName, box, "Название", mx, my, delta);
		screen.control(ctx, new Rect(box.x() + 12, box.bottom() - 27, 70, 18), "Отмена", false, false, this::closeCreate);
		screen.control(ctx, new Rect(box.right() - 91, box.bottom() - 27, 79, 18), "Создать", false, true,
			validCreate() ? this::create : null);
	}

	private void drawField(DrawContext ctx, AdminSearchField field, Rect box, String title, int mx, int my, float delta) {
		Rect control = fieldRect(field, box);
		screen.label(ctx, title, control.x() + 2, control.y() - 10, control.w() - 4, MUTED);
		fillRoundedRect(ctx, control, 4, CONTROL);
		drawRoundedBorder(ctx, control, 4, field.isFocused() ? AMBER : BORDER);
		field.render(ctx, mx, my, delta);
	}

	private static Rect fieldRect(AdminSearchField field, Rect box) {
		return new Rect(box.x() + 12, field.getY() - 3, box.w() - 24, 20);
	}

	boolean clickCreate(double x, double y) {
		if (!creating()) return false;
		Rect box = createBox();
		for (AdminSearchField field : List.of(createId, createName)) {
			if (!fieldRect(field, box).contains(x, y)) continue;
			focus(field);
			field.onClick(x, y);
			return true;
		}
		return false;
	}

	boolean keyCreate(int key, int scancode, int modifiers) {
		if (key == GLFW.GLFW_KEY_ESCAPE) closeCreate();
		else if (key == GLFW.GLFW_KEY_TAB) focus(createId.isFocused() ? createName : createId);
		else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			if (validCreate()) create();
		} else focusedField().keyPressed(key, scancode, modifiers);
		return true;
	}

	boolean typeCreate(char chr, int modifiers) {
		return focusedField().charTyped(chr, modifiers);
	}

	private AdminSearchField focusedField() {
		return createId.isFocused() ? createId : createName;
	}

	private void focus(AdminSearchField field) {
		createId.setFocused(field == createId);
		createName.setFocused(field == createName);
	}

	void closeCreate() {
		createId = null;
		createName = null;
	}

	private boolean validCreate() {
		return createId.getText().matches("[a-z0-9_-]{1,48}") && !createName.getText().isBlank();
	}

	private void create() {
		String id = createId.getText();
		String name = createName.getText().strip();
		Identifier dimension = currentDimension();
		if (dimension != null) pendingSelection = new Key(dimension, id);
		send(Action.CREATE, null, out -> out.writeString(id).writeString(name));
		closeCreate();
		detailScroll = 0;
	}

	boolean scroll(double x, double y, int amount) {
		if (creating()) return true;
		if (listClip != null && listClip.contains(x, y)) listScroll = MathHelper.clamp(listScroll + amount, 0, maxListScroll);
		else if (detailClip != null && detailClip.contains(x, y)) detailScroll = MathHelper.clamp(detailScroll + amount, 0, maxDetailScroll);
		return true;
	}

	private void send(Action action, Key key, Consumer<PacketByteBuf> write) {
		if (!ClientPlayNetworking.canSend(ArenaAdminPackets.ACTION)) return;
		var out = PacketByteBufs.create().writeVarInt(screen.getScreenHandler().syncId).writeEnumConstant(action);
		if (key != null) key.write(out);
		write.accept(out);
		ClientPlayNetworking.send(ArenaAdminPackets.ACTION, out);
	}

	private static String name(Details details) {
		return details.name().isBlank() ? details.key().id() : details.name();
	}

	private static Identifier currentDimension() {
		var world = MinecraftClient.getInstance().world;
		return world == null ? null : world.getRegistryKey().getValue();
	}

	private static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	private static void card(DrawContext ctx, Rect panel) {
		fillRoundedRect(ctx, panel, 6, PANEL);
		drawRoundedBorder(ctx, panel, 6, BORDER);
	}

	private void clip(DrawContext ctx, Rect area) {
		ctx.enableScissor(area.x(), area.y(), area.right(), area.bottom());
		screen.clickClip = area;
	}

	private void unclip(DrawContext ctx) {
		screen.clickClip = null;
		ctx.disableScissor();
	}
}
