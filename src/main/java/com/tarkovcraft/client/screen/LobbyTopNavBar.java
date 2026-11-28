package com.tarkovcraft.client.screen;

import com.tarkovcraft.client.ClientLoadoutCache;
import com.tarkovcraft.screen.LobbyScreenStatus;
import com.tarkovcraft.screen.LobbyTabNavigation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.function.Consumer;

import static com.tarkovcraft.client.screen.TarkovUiTheme.*;

public final class LobbyTopNavBar {
	public static final int HEIGHT = 47;
	private static final int TAB_TOP = 23;
	private static final int INSET = 8;
	private static final int ICON_WIDTH = 24;
	static final int GREEN = 0xFF9EBD82;
	static final int ALERT = 0xFFE48770;

	public enum Tab {
		PLAY("play", "Play", Action.PLAY, 0),
		CHARACTER("character", "Character", Action.CHARACTER, 0),
		LOADOUT("loadout", "Loadout", Action.LOADOUT, 0),
		TRADERS("traders", "Traders", Action.TRADERS, 0),
		WORKBENCH("workbench", "Workbench", Action.WORKBENCH, 0),
		SERVICE("service", "Service", Action.SERVICE, 0);

		private final Text title;
		private final Action action;
		private final int releaseSession;

		Tab(String key, String fallback, Action action, int releaseSession) {
			this.title = label("tab." + key, fallback);
			this.action = action;
			this.releaseSession = releaseSession;
		}
	}

	public enum Action {
		PLAY(LobbyTabNavigation.PLAY), CHARACTER(LobbyTabNavigation.CHARACTER),
		LOADOUT(LobbyTabNavigation.LOADOUT), TRADERS(LobbyTabNavigation.TRADERS), WORKBENCH(LobbyTabNavigation.WORKBENCH),
		SERVICE(LobbyTabNavigation.SERVICE), EDITOR(LobbyTabNavigation.EDITOR), SETTINGS(-1), DISCONNECT(-1);

		private final int button;
		Action(int button) { this.button = button; }
	}

	public record Status(String name, long rubles, int health, int maxHealth, boolean wounded, boolean needsService,
		int raidSeconds, int scavSeconds, boolean ready, boolean editorAllowed) { }

	private LobbyTopNavBar() { }

	static Status status(MinecraftClient client, LobbyScreenStatus status) {
		var health = ClientLoadoutCache.getBodyHealth();
		return new Status(client.player.getName().getString(), status.rubles(), Math.round(health.getTotalHp()),
			Math.round(health.getTotalEffectiveMaxHp()), health.needsTreatment(), status.needsService(),
			status.raidSeconds(), status.scavSeconds(), status.ready(), status.editorAllowed());
	}

	static void activate(MinecraftClient client, Screen screen, int syncId, Action action) {
		switch (action) {
			case SETTINGS -> client.setScreen(new LobbyPauseScreen(screen));
			case DISCONNECT -> LobbyPauseScreen.confirmDisconnect(screen);
			default -> {
				if (client.interactionManager != null) client.interactionManager.clickButton(syncId, action.button);
			}
		}
	}

	public static void render(DrawContext context, TextRenderer font, int width, int mouseX, int mouseY,
			Tab active, Status status) {
		context.fill(0, 0, width, HEIGHT, opaque(BG_TOP_BAR));
		context.fill(0, HEIGHT - 1, width, HEIGHT, BORDER_PANEL);
		context.fill(INSET, TAB_TOP - 1, width - INSET, TAB_TOP, BORDER_PANEL);

		Text playerStatus = status.raidSeconds() >= 0
			? label("nav.raid_status", "Raid %s", time(status.raidSeconds()))
			: status.ready() ? label("nav.player_ready", "Ready") : label("nav.in_lobby", "In lobby");
		int actionsLeft = width - INSET - ICON_WIDTH * (status.editorAllowed() ? 3 : 2);
		int statusWidth = Math.max(1, actionsLeft - INSET);
		int fundsX = INSET + statusWidth * 44 / 100;
		int hpX = INSET + statusWidth * 62 / 100;
		int scavX = INSET + statusWidth * 76 / 100;
		line(context, font, status.name() + "  /  " + playerStatus.getString(), INSET, 8, fundsX - INSET - 3,
			TEXT_PRIMARY, 0.85f);
		line(context, font, rubles(status.rubles()), fundsX, 8, hpX - fundsX - 3, ACCENT_SAND, 0.85f);
		line(context, font, status.health() + "/" + status.maxHealth() + " HP", hpX, 8, scavX - hpX - 3,
			status.wounded() ? ALERT : TEXT_PRIMARY, 0.85f);
		Text scav = switch (status.scavSeconds()) {
			case -1 -> label("nav.scav_closed", "Scav entry closed");
			case 0 -> label("nav.scav_ready", "Scav ready");
			default -> label("nav.scav_cooldown", "Scav %s", time(status.scavSeconds()));
		};
		line(context, font, scav.getString(), scavX, 8, actionsLeft - scavX - 3,
			status.scavSeconds() == 0 ? GREEN : TEXT_MUTED, 0.85f);

		if (status.editorAllowed()) {
			icon(context, font, actionsLeft, mouseX, mouseY, "✎", label("nav.editor", "Map editor"));
		}
		icon(context, font, width - INSET - ICON_WIDTH * 2, mouseX, mouseY, "⚙", Text.translatable("menu.options"));
		icon(context, font, width - INSET - ICON_WIDTH, mouseX, mouseY, "⏻", Text.translatable("menu.disconnect"));

		Tab[] tabs = Tab.values();
		for (int index = 0; index < tabs.length; index++) {
			Tab tab = tabs[index];
			int left = tabBoundary(width, index);
			int right = tabBoundary(width, index + 1);
			boolean hover = mouseX >= left && mouseX < right && mouseY >= TAB_TOP && mouseY < HEIGHT;
			if (active == tab || hover && tab.action != null) {
				context.fill(left, TAB_TOP, right, HEIGHT - 1, active == tab ? 0xFF3F392D : opaque(BG_SLOT));
			}
			if (active == tab) {
				context.fill(left + 4, HEIGHT - 2, right - 4, HEIGHT, ACCENT_SAND);
			}
			String title = trimWithEllipsis(font, tab.title.getString(), right - left - 12, 0.86f);
			int center = (left + right) / 2;
			drawCenteredFlatText(context, font, title, center, TAB_TOP + (tab.action == null ? 4 : 8),
				active == tab ? ACCENT_SAND : tab.action == null ? TEXT_MUTED : TEXT_PRIMARY, 0.86f);
			if (tab.action == null) {
				String unavailable = label("tab.unavailable", "Unavailable").getString();
				drawCenteredFlatText(context, font, unavailable, center, TAB_TOP + 15, TEXT_MUTED, 0.65f);
				if (hover) context.drawTooltip(font, label("tab.release_session", "Planned for session %s", tab.releaseSession), mouseX, mouseY);
			}
			if (tab == Tab.SERVICE && status.needsService()) {
				context.fill(right - 8, TAB_TOP + 7, right - 5, TAB_TOP + 10, ALERT);
			}
		}
	}

	public static boolean mouseClicked(int width, double x, double y, int button, Tab active,
			Status status, Consumer<Action> onAction) {
		if (button != 0 || y < 0 || y >= HEIGHT) return false;
		if (y < TAB_TOP) {
			int fromRight = width - INSET - 1 - (int) x;
			if (fromRight >= 0 && fromRight < ICON_WIDTH) {
				onAction.accept(Action.DISCONNECT);
			} else if (fromRight >= ICON_WIDTH && fromRight < ICON_WIDTH * 2) {
				onAction.accept(Action.SETTINGS);
			} else if (status.editorAllowed() && fromRight >= ICON_WIDTH * 2 && fromRight < ICON_WIDTH * 3) {
				onAction.accept(Action.EDITOR);
			}
			return true;
		}
		Tab[] tabs = Tab.values();
		for (int index = 0; index < tabs.length; index++) {
			Tab tab = tabs[index];
			if (x >= tabBoundary(width, index) && x < tabBoundary(width, index + 1)
					&& tab != active && tab.action != null) {
				onAction.accept(tab.action);
				break;
			}
		}
		return true;
	}

	private static int tabBoundary(int width, int index) {
		return INSET + index * (width - INSET * 2) / Tab.values().length;
	}

	private static void icon(DrawContext context, TextRenderer font, int x, int mouseX, int mouseY,
			String icon, Text tooltip) {
		boolean hover = mouseX >= x && mouseX < x + ICON_WIDTH && mouseY >= 0 && mouseY < TAB_TOP;
		if (hover) context.fill(x + 1, 2, x + ICON_WIDTH - 1, TAB_TOP - 1, opaque(BG_SLOT));
		drawCenteredFlatText(context, font, icon, x + ICON_WIDTH / 2, 7, hover ? ACCENT_SAND : TEXT_PRIMARY, 1.0f);
		if (hover) context.drawTooltip(font, tooltip, mouseX, mouseY);
	}

	static void line(DrawContext context, TextRenderer font, String text, int x, int y, int maxWidth, int color, float scale) {
		drawFlatText(context, font, trimWithEllipsis(font, text, maxWidth, scale), x, y, color, scale);
	}

	static String time(int seconds) {
		seconds = Math.max(0, seconds);
		return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
	}

	static String rubles(long value) {
		return String.format(Locale.ROOT, "%,d", value).replace(',', ' ') + " ₽";
	}

	static Text label(String key, String fallback, Object... args) {
		return Text.translatableWithFallback("screen.tarkovcraft.lobby." + key, fallback, args);
	}
}
