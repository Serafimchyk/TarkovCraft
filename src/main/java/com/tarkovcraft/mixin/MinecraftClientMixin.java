package com.tarkovcraft.mixin;

import com.tarkovcraft.client.ClientArenaEditor;
import com.tarkovcraft.client.screen.TarkovDeathScreen;
import com.tarkovcraft.network.TarkovNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1. Redirects Survival InventoryScreen to our server-backed TarkovInventoryScreen.
 * 2. Redirects Survival DeathScreen to our Tarkov KIA Death Screen!
 * 3. Kills vanilla swapHandsKey (offhand swap on F) in Survival/Adventure mode.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Shadow
	@Nullable
	public ClientPlayerEntity player;

	@Shadow
	@Final
	public GameOptions options;

	@Shadow
	public abstract void setScreen(@Nullable Screen screen);

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$redirectScreens(@Nullable Screen screen, CallbackInfo ci) {
		if (player != null && !player.isCreative()) {
			if (screen instanceof InventoryScreen) {
				TarkovNetworking.sendOpenOwnInventory();
				ci.cancel();
			} else if (screen instanceof DeathScreen) {
				ci.cancel();
				this.setScreen(new TarkovDeathScreen());
			}
		}
	}

	@Inject(method = "handleInputEvents", at = @At("HEAD"))
	private void tarkovcraft$consumeSwapHandsKey(CallbackInfo ci) {
		if (player != null && !player.isCreative()) {
			while (options.swapHandsKey.wasPressed()) {
				// Intentionally swallowed — offhand swap is disabled in TarkovCraft
			}
		}
	}

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$undoArenaVertex(CallbackInfoReturnable<Boolean> cir) {
		MinecraftClient client = (MinecraftClient) (Object) this;
		boolean undo = player != null && (player.isSneaking() || client.crosshairTarget == null
			|| client.crosshairTarget.getType() == HitResult.Type.MISS);
		if (ClientArenaEditor.interceptAttack(client, undo)) cir.setReturnValue(false);
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$preventWandMining(boolean breaking, CallbackInfo ci) {
		if (breaking && ClientArenaEditor.interceptAttack((MinecraftClient) (Object) this, false)) ci.cancel();
	}
}
