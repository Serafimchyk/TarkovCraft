package com.tarkovcraft.client.screen;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.network.AmmoSelectionNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

/** Selects which available ammo tier TaCZ should draw from first on the next reload. */
public final class AmmoRadialScreen extends Screen {
	private static final int CARD_WIDTH = 60;
	private static final int CARD_HEIGHT = 28;
	private static final int RADIUS = 70;
	private static final AmmoTier[] TIERS = AmmoTier.values();

	private final String gunId;
	private final TaCZCaliberCatalog.CaliberSpec caliber;
	private final Map<AmmoTier, Integer> ammoCounts;

	public AmmoRadialScreen(String gunId, String ammoId, Map<AmmoTier, Integer> ammoCounts) {
		super(Text.translatable("screen.tarkovcraft.ammo_radial"));
		this.gunId = gunId;
		this.caliber = TaCZCaliberCatalog.getSpecOrDynamic(ammoId);
		this.ammoCounts = Map.copyOf(ammoCounts);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int centerX = width / 2;
		int centerY = height / 2;
		context.fillGradient(0, 0, width, height, 0x79131517, 0xB1121315);
		context.drawCenteredTextWithShadow(textRenderer, title, centerX, centerY - 12, 0xE5CE9F);
		context.drawCenteredTextWithShadow(textRenderer, caliber.displayName(), centerX, centerY + 2, 0xB7B4AD);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.tarkovcraft.ammo_radial_hint"),
			centerX, centerY + radiusY() + 18, 0xB7B4AD);

		for (AmmoTier tier : TIERS) {
			int x = cardX(tier);
			int y = cardY(tier);
			boolean selectable = isSelectable(tier);
			boolean hovered = selectable && isOverCard(tier, mouseX, mouseY);
			context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, hovered ? 0xE7393630 : 0xE7222427);
			context.drawBorder(x, y, CARD_WIDTH, CARD_HEIGHT, hovered ? tier.getArgbColor() : 0xFF585750);
			context.drawCenteredTextWithShadow(textRenderer, tier.getBadge(), x + CARD_WIDTH / 2, y + 4,
				selectable ? tier.getArgbColor() : 0x808080);
			context.drawCenteredTextWithShadow(textRenderer, Integer.toString(ammoCounts.getOrDefault(tier, 0)),
				x + CARD_WIDTH / 2, y + 16, selectable ? 0xE7E3DB : 0x808080);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (AmmoTier tier : TIERS) {
				if (isSelectable(tier) && isOverCard(tier, mouseX, mouseY)) {
					if (ClientPlayNetworking.canSend(AmmoSelectionNetworking.SELECT)) {
						PacketByteBuf buf = PacketByteBufs.create();
						buf.writeByte(tier.ordinal());
						buf.writeString(gunId, AmmoSelectionNetworking.MAX_GUN_ID_LENGTH);
						ClientPlayNetworking.send(AmmoSelectionNetworking.SELECT, buf);
					}
					close();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private boolean isSelectable(AmmoTier tier) {
		return caliber.isTierAllowed(tier) && ammoCounts.getOrDefault(tier, 0) > 0;
	}

	private boolean isOverCard(AmmoTier tier, double mouseX, double mouseY) {
		int x = cardX(tier);
		int y = cardY(tier);
		return mouseX >= x && mouseX < x + CARD_WIDTH && mouseY >= y && mouseY < y + CARD_HEIGHT;
	}

	private int cardX(AmmoTier tier) {
		int radius = Math.min(RADIUS, width / 2 - CARD_WIDTH / 2 - 4);
		int offset = switch (tier) {
			case FMJ -> radius;
			case IGN -> -radius;
			default -> 0;
		};
		return width / 2 + offset - CARD_WIDTH / 2;
	}

	private int cardY(AmmoTier tier) {
		int offset = switch (tier) {
			case HP -> -radiusY();
			case AP -> radiusY();
			default -> 0;
		};
		return height / 2 + offset - CARD_HEIGHT / 2;
	}

	private int radiusY() {
		return Math.min(RADIUS, height / 2 - 30);
	}
}
