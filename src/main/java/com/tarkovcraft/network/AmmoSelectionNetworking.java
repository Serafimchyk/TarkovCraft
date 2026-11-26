package com.tarkovcraft.network;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.ammo.AmmoReloadTracker;
import com.tarkovcraft.ammo.AmmoTier;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class AmmoSelectionNetworking {
	public static final Identifier SELECT = new Identifier(TarkovCraftMod.MOD_ID, "ammo_tier_select");
	public static final int MAX_GUN_ID_LENGTH = 128;

	private AmmoSelectionNetworking() {
	}

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(SELECT, (server, player, handler, buf, sender) -> {
			int ordinal;
			String gunId;
			try {
				ordinal = buf.readUnsignedByte();
				gunId = buf.readString(MAX_GUN_ID_LENGTH);
			} catch (DecoderException | IndexOutOfBoundsException ignored) {
				return;
			}
			AmmoTier[] tiers = AmmoTier.values();
			if (ordinal >= tiers.length || buf.isReadable() || gunId.isEmpty() || Identifier.tryParse(gunId) == null) {
				return;
			}
			server.execute(() -> selectTier(player, gunId, tiers[ordinal]));
		});
	}

	private static void selectTier(ServerPlayerEntity player, String expectedGunId, AmmoTier tier) {
		if (!player.isAlive() || player.isSpectator() || player.currentScreenHandler != player.playerScreenHandler) {
			return;
		}
		ItemStack gun = player.getMainHandStack();
		if (!AmmoTier.isTaczGunStack(gun) || gun.getNbt() == null
				|| !expectedGunId.equals(gun.getNbt().getString("GunId"))) {
			return;
		}
		if (AmmoReloadTracker.prioritizeAmmoTier(player, AmmoReloadTracker.getGunAmmoId(gun), tier)) {
			player.sendMessage(Text.translatable("message.tarkovcraft.ammo_priority", Text.translatable(tier.getTranslationKey())), true);
		}
	}
}
