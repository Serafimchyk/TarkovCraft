package com.tarkovcraft.mixin;

import com.tarkovcraft.block.SearchableLootBlock;
import com.tarkovcraft.block.TarkovWorkbenchBlock;
import com.tarkovcraft.client.ClientLoadoutCache;
import com.tarkovcraft.client.TarkovCraftClient;
import com.tarkovcraft.client.WorldInteractState;
import com.tarkovcraft.client.hud.BodyHealthHudHelper;
import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.network.TarkovNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.tarkovcraft.client.screen.TarkovDeathScreen;
import com.tarkovcraft.client.screen.TarkovInventoryScreen;

/**
 * Replaces vanilla Survival HUD:
 * - Hides vanilla hearts, hunger, armor, and XP bar.
 * - Draws Tarkov-style quick-use hotbar with dynamic keybind badges.
 * - Draws compact top-right Body-Part Health Silhouette HUD.
 * - Draws EFT-style crosshair [F] interaction prompt — including a scrollable 2-option
 *   menu (Search / Equip) when looking at a Backpack or Tactical Rig lying on the ground!
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$hideVanillaStatusBars(DrawContext context, CallbackInfo ci) {
		if (client.player != null && !client.player.isCreative() && !client.player.isSpectator()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$hideVanillaXpBar(DrawContext context, int x, CallbackInfo ci) {
		if (client.player != null && !client.player.isCreative() && !client.player.isSpectator()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$renderTarkovHud(float tickDelta, DrawContext context, CallbackInfo ci) {
		if (client.player == null || client.player.isCreative() || client.player.isSpectator()) {
			WorldInteractState.updateTarget(null);
			return;
		}
		ci.cancel();

		if (client.currentScreen instanceof TarkovInventoryScreen || client.currentScreen instanceof TarkovDeathScreen) {
			WorldInteractState.updateTarget(null);
			return;
		}

		PlayerLoadout loadout = ClientLoadoutCache.getLoadout();
		int screenW = client.getWindow().getScaledWidth();
		int screenH = client.getWindow().getScaledHeight();

		// 1. Top-right Tarkov Body-Part Silhouette HUD
		BodyHealthHudHelper.drawCompactTopRight(context, client.textRenderer, client.player, screenW);

		// 2. Center crosshair [F] prompt / scrollable action menu
		if (client.currentScreen == null) {
			renderWorldInteractPrompt(context, loadout, screenW, screenH);
		} else {
			WorldInteractState.updateTarget(null);
		}

		// 3. Tarkov Quick-Use Hotbar (dynamic keybind labels)
		int slotSize = 24;
		int gap = 2;
		int groupGap = 8;
		int totalW = slotSize + groupGap + (3 * slotSize + 2 * gap) + groupGap + (6 * slotSize + 5 * gap);
		int startX = (screenW - totalW) / 2;
		int y = screenH - slotSize - 6;

		int selected = client.player.getInventory().selectedSlot;
		boolean meleeActive = loadout.isMeleeSelected();

		int x = startX;
		String meleeKey = TarkovCraftClient.getShortKeyLabel(TarkovCraftClient.toggleMeleeKey);
		int mBg = meleeActive ? 0xDD3D3628 : 0xBB14171B;
		int mBorder = meleeActive ? 0xFFD4B46A : 0xFF424A52;
		drawHotbarBox(context, x, y, slotSize, mBg, mBorder, meleeKey, loadout.getEquipment(EquipmentSlotType.MELEE));
		x += slotSize + groupGap;

		for (int i = 0; i < 3; i++) {
			boolean isSel = !meleeActive && selected == i;
			int bg = isSel ? 0xDD2E3A2B : 0xBB14171B;
			int border = isSel ? 0xFFD4B46A : 0xFF424A52;
			String keyLabel = TarkovCraftClient.getShortKeyLabel(client.options.hotbarKeys[i]);
			drawHotbarBox(context, x, y, slotSize, bg, border, keyLabel, loadout.getHotbarDisplayStack(i));
			x += slotSize + gap;
		}
		x += groupGap - gap;

		for (int i = 3; i < 9; i++) {
			boolean isSel = !meleeActive && selected == i;
			int bg = isSel ? 0xDD2E3A2B : 0xBB181C20;
			int border = isSel ? 0xFFD4B46A : 0xFF3A4148;
			String keyLabel = TarkovCraftClient.getShortKeyLabel(client.options.hotbarKeys[i]);
			drawHotbarBox(context, x, y, slotSize, bg, border, keyLabel, loadout.getHotbarDisplayStack(i));
			x += slotSize + gap;
		}
	}

	@Unique
	private void renderWorldInteractPrompt(DrawContext context, PlayerLoadout loadout, int screenW, int screenH) {
		if (client.player == null || client.world == null) return;
		String key = TarkovCraftClient.getShortKeyLabel(TarkovCraftClient.interactKey);

		double reach = 4.2;
		Vec3d eye = client.player.getCameraPosVec(1.0f);
		Vec3d look = client.player.getRotationVec(1.0f);
		Vec3d end = eye.add(look.multiply(reach));

		double maxDist = reach;
		if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
			maxDist = Math.min(reach, eye.distanceTo(client.crosshairTarget.getPos()));
		}

		Box searchBox = client.player.getBoundingBox().stretch(look.multiply(reach)).expand(1.2);
		EntityHitResult eHit = ProjectileUtil.raycast(
			client.player, eye, end, searchBox,
			e -> (e instanceof CorpseEntity || e instanceof ItemEntity) && !e.isRemoved(),
			maxDist * maxDist
		);
		Entity target = eHit != null ? eHit.getEntity() : TarkovNetworking.findClosestLookedItem(client.player, eye, look, maxDist);

		WorldInteractState.updateTarget(target);

		// EFT Scrollable 2-Action Menu for Backpacks & Rigs on the ground!
		if (WorldInteractState.hasMultiActionMenu() && target instanceof ItemEntity itemEntity) {
			ItemStack st = itemEntity.getStack();
			boolean slotEmpty = (st.getItem() instanceof BackpackItem && loadout.getEquipment(EquipmentSlotType.BACKPACK).isEmpty())
				|| (st.getItem() instanceof TacticalRigItem && loadout.getEquipment(EquipmentSlotType.RIG).isEmpty());

			String itemTitle = st.getName().getString();
			String opt0 = Text.translatable("action.tarkovcraft.search").getString();
			String opt1 = Text.translatable(slotEmpty ? "action.tarkovcraft.equip" : "action.tarkovcraft.take").getString();
			int sel = WorldInteractState.getSelectedAction();

			int boxW = Math.max(120, Math.max(client.textRenderer.getWidth(itemTitle) + 18, 132));
			int bx = screenW / 2 + 12;
			int by = screenH / 2 - 14;

			// Header with item name
			context.fill(bx, by, bx + boxW, by + 13, 0xDD0C0F12);
			context.drawBorder(bx, by, boxW, 43, 0xFF3A424A);
			context.drawTextWithShadow(client.textRenderer, itemTitle, bx + 4, by + 3, 0xFFB8C0C8);

			// Option 0: Search (top, default)
			drawWorldMenuRow(context, bx + 1, by + 13, boxW - 2, 14, key, opt0, sel == WorldInteractState.ACTION_SEARCH, "↑↓");
			// Option 1: Equip / Take (bottom, scroll down)
			drawWorldMenuRow(context, bx + 1, by + 27, boxW - 2, 15, key, opt1, sel == WorldInteractState.ACTION_EQUIP_OR_TAKE, "");
			return;
		}

		// Standard single-line prompt
		Text prompt = null;
		if (target instanceof CorpseEntity corpse) {
			prompt = Text.translatable("prompt.tarkovcraft.search", key, corpse.getOwnerName());
		} else if (target instanceof ItemEntity itemEntity && !itemEntity.getStack().isEmpty()) {
			ItemStack st = itemEntity.getStack();
			boolean willEquip = st.getItem() instanceof TarkovArmorItem a && loadout.getEquipment(a.getTargetSlot()).isEmpty();
			prompt = Text.translatable(willEquip ? "prompt.tarkovcraft.equip" : "prompt.tarkovcraft.take", key, st.getName().getString());
		} else if (client.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
			BlockState state = client.world.getBlockState(bhr.getBlockPos());
			if (state.getBlock() instanceof SearchableLootBlock lootBlock) {
				prompt = Text.translatable("prompt.tarkovcraft.search", key, Text.translatable(lootBlock.getContainerTitleKey()).getString());
			} else if (state.getBlock() instanceof TarkovWorkbenchBlock wb) {
				prompt = Text.translatable("prompt.tarkovcraft.workbench", key, wb.getName().getString());
			}
		}

		if (prompt != null) {
			int pw = client.textRenderer.getWidth(prompt) + 14;
			int px = (screenW - pw) / 2;
			int py = screenH / 2 + 18;
			context.fill(px, py, px + pw, py + 16, 0xCC121619);
			context.drawBorder(px, py, pw, 16, 0xFFD4B46A);
			context.drawCenteredTextWithShadow(client.textRenderer, prompt, screenW / 2, py + 4, 0xFFF2F2F2);
		}
	}

	@Unique
	private void drawWorldMenuRow(DrawContext context, int x, int y, int w, int h, String key, String label, boolean selected, String hint) {
		int bg = selected ? 0xE6343D30 : 0xCC15191D;
		int textCol = selected ? 0xFFF4F4F4 : 0xFF8C96A0;
		context.fill(x, y, x + w, y + h, bg);
		if (selected) {
			context.fill(x, y, x + 2, y + h, 0xFFD4B46A);
			context.drawTextWithShadow(client.textRenderer, "[" + key + "] " + label, x + 5, y + 3, 0xFFD4B46A);
		} else {
			context.drawTextWithShadow(client.textRenderer, "    " + label, x + 5, y + 3, textCol);
		}
		if (!hint.isEmpty()) {
			int hw = client.textRenderer.getWidth(hint);
			context.drawTextWithShadow(client.textRenderer, hint, x + w - hw - 4, y + 3, 0xFF7D8791);
		}
	}

	@Unique
	private void drawHotbarBox(DrawContext context, int x, int y, int size, int bg, int border, String label, ItemStack stack) {
		context.fill(x, y, x + size, y + size, bg);
		context.drawBorder(x, y, size, size, border);
		if (!stack.isEmpty()) {
			context.drawItem(stack, x + 4, y + 4);
			context.drawItemInSlot(client.textRenderer, stack, x + 4, y + 4);
		}
		context.getMatrices().push();
		context.getMatrices().scale(0.65f, 0.65f, 1.0f);
		int bx = (int) ((x + 2) / 0.65f);
		int by = (int) ((y + 2) / 0.65f);
		context.drawTextWithShadow(client.textRenderer, label, bx, by, 0xFFD8DEE4);
		context.getMatrices().pop();
	}
}
