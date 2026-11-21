package com.tarkovcraft.client;

import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.network.TarkovNetworking;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

public final class ClientLoadoutCache {
	private static final PlayerLoadout LOADOUT = new PlayerLoadout();
	private static final BodyPartHealth BODY_HEALTH = new BodyPartHealth();
	@Nullable
	private static TarkovNetworking.ScreenStateSnapshot screenState;

	private ClientLoadoutCache() {
	}

	public static PlayerLoadout getLoadout() {
		return LOADOUT;
	}

	public static void updateLoadoutFromNbt(NbtCompound nbt) {
		LOADOUT.readNbt(nbt);
	}

	public static BodyPartHealth getBodyHealth() {
		return BODY_HEALTH;
	}

	public static void updateBodyHealthFromNbt(NbtCompound nbt) {
		BODY_HEALTH.readNbt(nbt);
	}

	@Nullable
	public static TarkovNetworking.ScreenStateSnapshot getScreenState() {
		return screenState;
	}

	public static void setScreenState(@Nullable TarkovNetworking.ScreenStateSnapshot snapshot) {
		screenState = snapshot;
	}
}
