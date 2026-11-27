package com.tarkovcraft.client.screen;

import com.tarkovcraft.client.compat.TarkovModMenuIntegration;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import static com.tarkovcraft.client.screen.LobbyTopNavBar.label;

public final class LobbyPauseScreen extends Screen {
	private final Screen parent;

	public LobbyPauseScreen(Screen parent) {
		super(Text.translatable("menu.game"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int buttonWidth = Math.min(200, width - 36);
		int x = (width - buttonWidth) / 2;
		int y = height / 2 - 46;
		button(Text.translatable("menu.returnToGame"), x, y, buttonWidth, this::close);
		button(Text.translatable("menu.options"), x, y + 24, buttonWidth,
			() -> client.setScreen(new OptionsScreen(this, client.options)));
		button(Text.translatable("controls.title"), x, y + 48, buttonWidth,
			() -> client.setScreen(new ControlsOptionsScreen(this, client.options)));
		ButtonWidget mod = button(label("pause.mod_settings", "Mod settings"), x, y + 72, buttonWidth, () -> {
			Screen screen = ModConfig.create(this);
			if (screen != null) client.setScreen(screen);
		});
		mod.active = hasModSettings();
		button(Text.translatable("menu.disconnect"), x, y + 96, buttonWidth, () -> confirmDisconnect(this));
	}

	private ButtonWidget button(Text text, int x, int y, int width, Runnable action) {
		return addDrawableChild(ButtonWidget.builder(text, button -> action.run()).dimensions(x, y, width, 20).build());
	}

	private static boolean hasModSettings() {
		FabricLoader loader = FabricLoader.getInstance();
		return loader.isModLoaded("modmenu")
			&& (loader.isModLoaded("cloth-config") || loader.isModLoaded("cloth-config2"));
	}

	private static final class ModConfig {
		static Screen create(Screen parent) {
			return new TarkovModMenuIntegration().getModConfigScreenFactory().create(parent);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		TarkovUiTheme.drawLobbyBackground(context, width, height);
		int panelWidth = Math.min(224, width - 12);
		int x = (width - panelWidth) / 2;
		int y = height / 2 - 72;
		context.fill(x, y, x + panelWidth, y + 154, TarkovUiTheme.opaque(TarkovUiTheme.BG_PANEL));
		context.drawBorder(x, y, panelWidth, 154, TarkovUiTheme.BORDER_PANEL);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, y + 10, TarkovUiTheme.ACCENT_SAND);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	public static void confirmDisconnect(Screen parent) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.setScreen(new ConfirmScreen(confirmed -> {
			if (!confirmed || client.world == null) {
				client.setScreen(parent);
				return;
			}
			boolean local = client.isInSingleplayer();
			client.world.disconnect();
			client.disconnect(local ? new TitleScreen() : new MultiplayerScreen(new TitleScreen()));
		}, Text.translatable("menu.disconnect"), label("pause.disconnect_confirm", "Leave this server?")));
	}
}
