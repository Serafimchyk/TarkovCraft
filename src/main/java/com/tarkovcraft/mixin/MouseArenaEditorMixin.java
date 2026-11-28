package com.tarkovcraft.mixin;

import com.tarkovcraft.client.ClientArenaEditor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
abstract class MouseArenaEditorMixin {
	@Shadow @Final private MinecraftClient client;

	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void tarkovcraft$cycleArena(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (window == client.getWindow().getHandle() && ClientArenaEditor.scroll(client, vertical)) ci.cancel();
	}
}
