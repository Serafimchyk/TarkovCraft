package com.tarkovcraft.client.screen;

import com.tarkovcraft.lobby.LobbyManager;
import com.tarkovcraft.screen.LobbyPlayScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.tarkovcraft.client.screen.LobbyLayoutMetrics.ROW_HEIGHT;
import static com.tarkovcraft.client.screen.LobbyTopNavBar.*;
import static com.tarkovcraft.client.screen.TarkovUiTheme.*;
import static com.tarkovcraft.screen.LobbyPlayScreenHandler.*;

public final class LobbyPlayScreen extends HandledScreen<LobbyPlayScreenHandler> {
	private static final int INSET = 8;
	private static final int OPEN_INVITES = -1;
	private static final int CLOSE_INVITES = -2;
	private LobbyLayoutMetrics layout;
	private static java.lang.ref.WeakReference<ClientPlayerEntity> previousPlayer = new java.lang.ref.WeakReference<>(null);
	private static boolean inviting;
	private float previewYaw;
	private boolean rotating;

	public LobbyPlayScreen(LobbyPlayScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		var client = net.minecraft.client.MinecraftClient.getInstance();
		if (previousPlayer.get() != client.player) {
			previousPlayer = new java.lang.ref.WeakReference<>(client.player);
			inviting = false;
		}
	}

	@Override
	protected void init() {
		super.init();
		layout = new LobbyLayoutMetrics(width, height);
	}

	@Override
	public void close() {
		if (inviting) {
			inviting = false;
		} else if (client != null) {
			client.setScreen(new LobbyPauseScreen(this));
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		LobbyManager.Snapshot snapshot = handler.snapshot();
		TarkovUiTheme.drawLobbyBackground(context, width, height);
		panel(context, layout.squad, label("squad.title", "Squad"));
		panel(context, layout.raid, label("deployment.title", "Deployment"));
		drawPreview(context, snapshot);
		drawSquad(context, snapshot);
		drawDeployment(context, snapshot);
		for (Target target : targets(snapshot)) {
			drawTarget(context, target, mouseX, mouseY);
		}
		if (!snapshot.inviter().isEmpty()) {
			drawInviteToast(context, snapshot, mouseX, mouseY);
		}
		LobbyTopNavBar.render(context, textRenderer, width, mouseX, mouseY, Tab.PLAY, status(snapshot));
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) { }

	private Status status(LobbyManager.Snapshot snapshot) {
		String name = client != null && client.player != null ? client.player.getGameProfile().getName() : "";
		return new Status(name, snapshot.rubles(), snapshot.health(), snapshot.maxHealth(),
			(snapshot.warningFlags() & LobbyManager.WOUNDED) != 0, snapshot.service(),
			snapshot.raidSeconds(), snapshot.scavStatusSeconds(), snapshot.ready(), snapshot.editor());
	}

	private void panel(DrawContext context, LobbyLayoutMetrics.Rect rect, Text title) {
		context.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), BG_PANEL);
		context.drawBorder(rect.x(), rect.y(), rect.width(), rect.height(), BORDER_PANEL);
		context.fill(rect.x() + 1, rect.y() + 1, rect.right() - 1, rect.y() + 20, BG_MODAL_HEADER);
		line(context, textRenderer, title.getString(), rect.x() + INSET, rect.y() + 7,
			rect.width() - INSET * 2, ACCENT_SAND, 0.9f);
	}

	private void drawSquad(DrawContext context, LobbyManager.Snapshot snapshot) {
		var box = layout.squad;
		if (inviting) {
			Text online = label("squad.online", "Players in lobby");
			boxLine(context, box, online, 49, TEXT_PRIMARY);
			if (snapshot.online().isEmpty()) {
				boxLine(context, box, label("squad.no_players", "No players available"), 68, TEXT_MUTED);
			}
			return;
		}

		for (int index = 0; index < 4; index++) {
			int rowY = box.y() + 49 + index * 23;
			int x = box.x() + INSET;
			context.fill(x, rowY, box.right() - INSET, rowY + 20, BG_SLOT);
			if (index >= snapshot.party().size()) {
				context.drawHorizontalLine(x + 6, x + 14, rowY + 10, BORDER_SLOT);
				continue;
			}
			var player = snapshot.party().get(index);
			int available = box.width() - INSET * 2 - (showMemberActions(snapshot, player) ? 43 : 12);
			context.fill(x, rowY, x + 2, rowY + 20, player.ready() ? GREEN : BORDER_SLOT);
			String name = (player.leader() ? "★ " : "") + player.name();
			line(context, textRenderer, name, x + 6, rowY + 3, available, TEXT_PRIMARY, 0.82f);
			Text ready = player.ready() ? label("nav.player_ready", "Ready") : label("squad.preparing", "Preparing");
			line(context, textRenderer, ready.getString(), x + 6, rowY + 12, available,
				player.ready() ? GREEN : TEXT_MUTED, 0.68f);
		}
	}

	private boolean showMemberActions(LobbyManager.Snapshot snapshot, LobbyManager.PlayerView player) {
		return snapshot.leader() && !player.leader();
	}

	private void drawDeployment(DrawContext context, LobbyManager.Snapshot snapshot) {
		var box = layout.raid;
		var arena = snapshot.arena();
		if (snapshot.raidSeconds() >= 0) {
			boxLine(context, box, label("deployment.in_progress", "Raid in progress"), 30, ALERT);
			boxLine(context, box, Text.literal(time(snapshot.raidSeconds())), 45, TEXT_PRIMARY);
			if (snapshot.scavSeconds() > 0) {
				boxLine(context, box, label("deployment.scav_delay", "Scav available in %s", time(snapshot.scavSeconds())),
					65, TEXT_MUTED);
			} else if (snapshot.scavSeconds() < 0) {
				boxLine(context, box, label("deployment.scav_unavailable", "Scav entry closed"), 65, TEXT_MUTED);
			}
			return;
		}

		if (arena.valid()) {
			boxLine(context, box, label("deployment.arena_duration", "%s min  /  %s extracts", arena.minutes(), arena.exits()),
				56, TEXT_SECONDARY);
			int picked = snapshot.arenaIndex();
			if (picked >= 0 && snapshot.arenas().size() > 1) {
				boxLine(context, box, Text.literal((picked + 1) + " / " + snapshot.arenas().size()), 68, TEXT_MUTED);
			}
		}

		if (!arena.valid() && !arena.name().isEmpty()) {
			boxLine(context, box, label("deployment.arena_incomplete", "Arena setup incomplete"), 56, ALERT);
		}
		Text ready = snapshot.countdownSeconds() >= 0
			? label("deployment.starts_in", "Deploying in %s", time(snapshot.countdownSeconds()))
			: label("deployment.ready_count", "Ready %s / %s", snapshot.readyCount(), snapshot.queuedCount());
		boxLine(context, box, ready, box.height() - 48,
			snapshot.countdownSeconds() >= 0 ? ACCENT_SAND : TEXT_PRIMARY);
	}

	private void drawPreview(DrawContext context, LobbyManager.Snapshot snapshot) {
		var box = layout.preview;
		int center = box.x() + box.width() / 2;
		int footer = box.bottom() - 8;
		int modelFloor = footer - (snapshot.warned() ? 47 : 24);
		int reservedTop = snapshot.pendingLoot() > 0 ? 27 : 12;
		int modelScale = Math.max(16, Math.min(box.width() / 3, (modelFloor - box.y() - reservedTop) / 2));
		if (client != null && client.player != null) {
			drawCharacter(context, client.player, box, center, modelFloor, modelScale);
		}
		String weight = label("kit.weight", "%s kg", String.format(Locale.ROOT, "%.1f", snapshot.kitWeightKg())).getString();
		String knownValue = snapshot.kitRubles() < 0 ? weight : weight + "  /  ≈" + rubles(snapshot.kitRubles());
		String stats = trimWithEllipsis(textRenderer, knownValue, box.width() - INSET * 2, 0.88f);
		drawCenteredFlatText(context, textRenderer, stats, center, modelFloor + 8, TEXT_PRIMARY, 0.88f);
		int warningY = modelFloor + 24;
		int flags = snapshot.warningFlags();
		if ((flags & LobbyManager.WOUNDED) != 0) {
			drawWarning(context, box, label("kit.wounded", "Your PMC is wounded"), warningY);
			warningY += 10;
		}
		if ((flags & LobbyManager.NO_WEAPON) != 0) {
			drawWarning(context, box, label("kit.no_weapon", "No weapon equipped"), warningY);
		} else if ((flags & LobbyManager.NO_AMMO) != 0) {
			drawWarning(context, box, label("kit.no_ammo", "No reserve ammunition"), warningY);
		}
	}

	private void drawCharacter(DrawContext context, ClientPlayerEntity player, LobbyLayoutMetrics.Rect box,
			int x, int y, int size) {
		float oldBodyYaw = player.bodyYaw;
		float oldYaw = player.getYaw();
		float oldPitch = player.getPitch();
		float oldHeadYaw = player.headYaw;
		float oldPreviousHeadYaw = player.prevHeadYaw;
		boolean invisible = player.isInvisible();
		context.enableScissor(box.x(), box.y(), box.right(), y + 2);
		try {
			float yaw = 180.0f + previewYaw;
			player.bodyYaw = yaw;
			player.setYaw(yaw);
			player.setPitch(0);
			player.headYaw = yaw;
			player.prevHeadYaw = yaw;
			player.setInvisible(false);
			InventoryScreen.drawEntity(context, x, y, size, new Quaternionf().rotateZ((float) Math.PI), null, player);
		} finally {
			player.bodyYaw = oldBodyYaw;
			player.setYaw(oldYaw);
			player.setPitch(oldPitch);
			player.headYaw = oldHeadYaw;
			player.prevHeadYaw = oldPreviousHeadYaw;
			player.setInvisible(invisible);
			context.disableScissor();
		}
	}

	private void drawWarning(DrawContext context, LobbyLayoutMetrics.Rect box, Text text, int y) {
		String warning = trimWithEllipsis(textRenderer, text.getString(), box.width() - INSET * 2, 0.82f);
		drawCenteredFlatText(context, textRenderer, warning, box.x() + box.width() / 2, y, ALERT, 0.82f);
	}

	private void boxLine(DrawContext context, LobbyLayoutMetrics.Rect box, Text text, int y, int color) {
		line(context, textRenderer, text.getString(), box.x() + INSET, box.y() + y, box.width() - INSET * 2,
			color, 0.84f);
	}

	private List<Target> targets(LobbyManager.Snapshot snapshot) {
		List<Target> targets = new ArrayList<>();
		squadTargets(snapshot, targets);
		deploymentTargets(snapshot, targets);
		return targets;
	}

	private void squadTargets(LobbyManager.Snapshot snapshot, List<Target> targets) {
		var box = layout.squad;
		Text mode = snapshot.squads() ? label("squad.squads", "Squads") : label("squad.ffa", "Free-for-all");
		targets.add(full(box, 26, mode, TOGGLE_SQUADS, snapshot.canConfigure() && snapshot.raidSeconds() < 0, false));
		if (inviting) {
			for (int index = 0; index < snapshot.online().size(); index++) {
				var player = snapshot.online().get(index);
				targets.add(full(box, 62 + index * 19, Text.literal("+ " + player.name()), INVITE_FIRST + index,
					snapshot.leader() && snapshot.party().size() < 4, false));
			}
			int offset = box.height() - 48;
			targets.add(side(box, offset, false, Text.literal("‹"), PREVIOUS_PLAYERS, snapshot.canPreviousPlayers()));
			targets.add(side(box, offset, true, Text.literal("›"), NEXT_PLAYERS, snapshot.canNextPlayers()));
			targets.add(full(box, box.height() - 26, Text.translatable("gui.back"), CLOSE_INVITES, true, false));
			return;
		}
		for (int index = 0; index < snapshot.party().size(); index++) {
			var player = snapshot.party().get(index);
			if (!showMemberActions(snapshot, player)) continue;
			int y = box.y() + 51 + index * 23;
			targets.add(target(box.right() - INSET - 35, y, 17, 16, Text.literal("★"),
				TRANSFER_LEADER_FIRST + index, true, false, label("squad.transfer_leader", "Transfer leadership")));
			targets.add(target(box.right() - INSET - 17, y, 17, 16, Text.literal("×"),
				KICK_FIRST + index, true, false, label("squad.kick", "Remove from squad")));
		}
		boolean canInvite = snapshot.leader() && snapshot.party().size() < 4;
		targets.add(full(box, box.height() - 48, label("squad.invite", "+ Invite a player"), OPEN_INVITES, canInvite, false));
		if (snapshot.party().size() > 1) {
			targets.add(full(box, box.height() - 26, label("squad.leave", "Leave squad"), LEAVE, true, false));
		}
	}

	private void deploymentTargets(LobbyManager.Snapshot snapshot, List<Target> targets) {
		var box = layout.raid;
		if (snapshot.raidSeconds() >= 0) {
			targets.add(full(box, box.height() - 48, label("deployment.spectate", "Spectate"), SPECTATE, true, false));
			targets.add(full(box, box.height() - 26, label("deployment.join_scav", "Play as Scav"),
				JOIN_SCAV, snapshot.scavSeconds() == 0, true));
		} else {
			var arena = snapshot.arena();
			int left = box.x() + INSET;
			int right = box.right() - INSET;
			boolean canCycle = snapshot.canConfigure() && snapshot.arenas().size() > 1;
			targets.add(target(left, box.y() + 30, 18, ROW_HEIGHT, Text.literal("‹"), PREVIOUS_ARENA, canCycle, false,
				label("deployment.previous_arena", "Previous arena")));
			targets.add(target(right - 18, box.y() + 30, 18, ROW_HEIGHT, Text.literal("›"), NEXT_ARENA, canCycle, false,
				label("deployment.next_arena", "Next arena")));
			String name = !arena.name().isEmpty() ? arena.name() : label("deployment.no_arena", "No arena configured").getString();
			targets.add(target(left + 19, box.y() + 30, right - left - 38, ROW_HEIGHT, Text.literal(name),
				-3, false, false, Text.literal(name)));
			if (arena.valid()) {
				boolean canChange = snapshot.canConfigure();
				targets.add(full(box, 82, label("deployment.boss", "Boss %s", onOff(arena.boss())),
					TOGGLE_BOSS, canChange, false));
				targets.add(full(box, 103, label("deployment.airdrops", "Airdrops %s", onOff(arena.airdrops())),
					TOGGLE_AIRDROPS, canChange, false));
				Text timeOfDay = switch (arena.timeOfDay()) {
					case 1 -> label("deployment.overcast", "Overcast");
					case 2 -> label("deployment.night", "Night");
					default -> label("deployment.day", "Day");
				};
				targets.add(full(box, 124, label("deployment.time", "Time %s", timeOfDay), NEXT_TIME, canChange, false));
			}
			Text ready = snapshot.ready() ? label("deployment.cancel", "Cancel")
				: snapshot.readyCount() == 0 ? label("deployment.deploy", "Deploy") : label("deployment.ready", "Ready");
			targets.add(full(box, box.height() - 26, ready, TOGGLE_READY, arena.valid() || snapshot.ready(), true));
		}

		if (snapshot.pendingLoot() > 0) {
			targets.add(full(layout.preview, 8, label("deployment.scav_loot", "Scav loot (%s)", snapshot.pendingLoot()),
				TRANSFER, true, false));
		}
	}

	private Text onOff(boolean enabled) {
		return enabled ? label("deployment.on", "On") : label("deployment.off", "Off");
	}

	private void drawInviteToast(DrawContext context, LobbyManager.Snapshot snapshot, int mouseX, int mouseY) {
		var toast = inviteToast();
		context.fill(toast.x(), toast.y(), toast.right(), toast.bottom(), BG_MODAL);
		context.drawBorder(toast.x(), toast.y(), toast.width(), toast.height(), ACCENT_SAND);
		boxLine(context, toast, label("squad.invited", "Invitation from %s", snapshot.inviter()), 7, TEXT_PRIMARY);
		for (Target target : inviteActions(toast)) {
			drawTarget(context, target, mouseX, mouseY);
		}
	}

	private LobbyLayoutMetrics.Rect inviteToast() {
		return new LobbyLayoutMetrics.Rect(layout.preview.x() + 4, layout.preview.y() + 4, layout.preview.width() - 8, 50);
	}

	private List<Target> inviteActions(LobbyLayoutMetrics.Rect toast) {
		return List.of(side(toast, 24, false, label("squad.accept", "Accept"), ACCEPT, true),
			side(toast, 24, true, label("squad.decline", "Decline"), DECLINE, true));
	}

	private Target full(LobbyLayoutMetrics.Rect rect, int y, Text text, int action, boolean enabled, boolean primary) {
		return target(rect.x() + INSET, rect.y() + y, rect.width() - INSET * 2, ROW_HEIGHT, text, action, enabled, primary, null);
	}

	private Target side(LobbyLayoutMetrics.Rect rect, int y, boolean right, Text text, int action, boolean enabled) {
		int w = (rect.width() - INSET * 2 - 4) / 2;
		int x = right ? rect.right() - INSET - w : rect.x() + INSET;
		return target(x, rect.y() + y, w, ROW_HEIGHT, text, action, enabled, false, null);
	}

	private Target target(int x, int y, int w, int h, Text text, int action, boolean enabled, boolean primary, Text hint) {
		return new Target(new LobbyLayoutMetrics.Rect(x, y, w, h), text, action, enabled, primary, hint);
	}

	private void drawTarget(DrawContext context, Target target, int mouseX, int mouseY) {
		var box = target.bounds();
		boolean hover = box.contains(mouseX, mouseY);
		int fill = target.primary() && target.enabled() ? hover ? 0xFFDCC28A : ACCENT_SAND
			: target.enabled() && hover ? 0xFF332F27 : BG_SLOT;
		int color = target.primary() && target.enabled() ? HOVER_MENU_TEXT : target.enabled() ? TEXT_PRIMARY : TEXT_MUTED;
		context.fill(box.x(), box.y(), box.right(), box.bottom(), fill);
		context.drawBorder(box.x(), box.y(), box.width(), box.height(),
			target.enabled() && hover ? BORDER_HOVER : BORDER_SLOT);
		String label = trimWithEllipsis(textRenderer, target.text().getString(), box.width() - 6, 0.82f);
		drawCenteredFlatText(context, textRenderer, label, box.x() + box.width() / 2, box.y() + (box.height() - 7) / 2,
			color, 0.82f);
		if (hover && target.hint() != null) context.drawTooltip(textRenderer, target.hint(), mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		LobbyManager.Snapshot snapshot = handler.snapshot();
		if (LobbyTopNavBar.mouseClicked(width, mouseX, mouseY, button, Tab.PLAY, status(snapshot), this::navAction)) return true;
		if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
		if (!snapshot.inviter().isEmpty() && inviteToast().contains(mouseX, mouseY)) {
			clickTarget(inviteActions(inviteToast()), mouseX, mouseY);
			return true;
		}
		if (clickTarget(targets(snapshot), mouseX, mouseY)) return true;
		if (layout.preview.contains(mouseX, mouseY)) {
			rotating = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean clickTarget(List<Target> targets, double x, double y) {
		for (Target target : targets) {
			if (!target.bounds().contains(x, y)) continue;
			if (target.enabled()) {
				switch (target.action()) {
					case OPEN_INVITES -> inviting = true;
					case CLOSE_INVITES -> inviting = false;
					default -> send(target.action());
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (button == 0 && rotating) {
			previewYaw = (previewYaw + (float) deltaX * 2.3f) % 360.0f;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (button == 0 && rotating) {
			rotating = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void navAction(Action action) {
		switch (action) {
			case CHARACTER -> send(CHARACTER);
			case EDITOR -> send(EDITOR);
			case SETTINGS -> client.setScreen(new LobbyPauseScreen(this));
			case DISCONNECT -> LobbyPauseScreen.confirmDisconnect(this);
			case PLAY -> { }
		}
	}

	private void send(int action) {
		if (client != null && client.interactionManager != null) {
			client.interactionManager.clickButton(handler.syncId, action);
		}
	}

	private record Target(LobbyLayoutMetrics.Rect bounds, Text text, int action, boolean enabled, boolean primary, Text hint) { }
}
