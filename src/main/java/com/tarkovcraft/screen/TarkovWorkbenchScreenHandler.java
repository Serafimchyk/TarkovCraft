package com.tarkovcraft.screen;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.arena.PlayerProfileState;
import com.tarkovcraft.block.TarkovWorkbenchBlock;
import com.tarkovcraft.network.TarkovNetworking;
import com.tarkovcraft.registry.ModScreenHandlers;
import com.tarkovcraft.workbench.TarkovWorkbenchRecipes;
import com.tarkovcraft.workbench.WorkbenchInventoryHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative ScreenHandler for the Tarkov Armor & Ammo Workbench.
 */
public class TarkovWorkbenchScreenHandler extends ScreenHandler {
	@Nullable private final BlockPos workbenchPos;
	private final PlayerEntity owner;
	@Nullable private final LobbyScreenStatus lobbyStatus;

	public TarkovWorkbenchScreenHandler(int syncId, PlayerInventory inv, @Nullable BlockPos workbenchPos) {
		super(ModScreenHandlers.TARKOV_WORKBENCH_SCREEN_HANDLER, syncId);
		this.workbenchPos = workbenchPos;
		this.owner = inv.player;
		this.lobbyStatus = isLobby() ? new LobbyScreenStatus(this::addProperty) : null;
		if (lobbyStatus != null && owner instanceof ServerPlayerEntity player) lobbyStatus.refresh(player);
	}

	public static TarkovWorkbenchScreenHandler fromClientBuf(int syncId, PlayerInventory inv, PacketByteBuf buf) {
		BlockPos pos = buf.readBoolean() ? null : buf.readBlockPos();
		return new TarkovWorkbenchScreenHandler(syncId, inv, pos);
	}

	public static void openForPlayer(ServerPlayerEntity player, @Nullable BlockPos pos) {
		if (pos == null && !LobbyTabNavigation.canUse(player)) return;
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
				buf.writeBoolean(pos == null);
				if (pos != null) buf.writeBlockPos(pos);
			}

			@Override
			public Text getDisplayName() {
				return Text.translatable("screen.tarkovcraft.workbench");
			}

			@Nullable
			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return new TarkovWorkbenchScreenHandler(syncId, inv, pos);
			}
		});
		if (player.currentScreenHandler instanceof TarkovWorkbenchScreenHandler wb) {
			wb.syncResources(player);
		}
	}

	@Nullable
	public BlockPos getWorkbenchPos() {
		return workbenchPos;
	}

	public boolean isLobby() { return workbenchPos == null; }

	@Nullable
	public LobbyScreenStatus lobbyStatus() { return lobbyStatus; }

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		return isLobby() && player instanceof ServerPlayerEntity serverPlayer
			&& serverPlayer.currentScreenHandler == this && canUse(player) && LobbyTabNavigation.handle(serverPlayer, id);
	}

	@Override
	public void sendContentUpdates() {
		if (lobbyStatus != null && owner instanceof ServerPlayerEntity player) lobbyStatus.refresh(player);
		super.sendContentUpdates();
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		if (player != owner) return false;
		if (workbenchPos == null) return !(player instanceof ServerPlayerEntity serverPlayer) || LobbyTabNavigation.canUse(serverPlayer);
		return player.getWorld().getBlockState(workbenchPos).getBlock() instanceof TarkovWorkbenchBlock
			&& player.squaredDistanceTo(workbenchPos.toCenterPos()) <= 36.0;
	}

	public void syncResources(ServerPlayerEntity player) {
		WorkbenchInventoryHelper.SyncPayload payload = WorkbenchInventoryHelper.collectSyncPayload(player,
			isLobby() ? PlayerProfileState.get(player.getServer()).stash(player.getUuid()) : null);
		TarkovNetworking.sendWorkbenchSync(player, payload);
	}

	public void handleCraftAmmo(ServerPlayerEntity player, String ammoId, byte tierOrdinal, boolean upgradeFromFmj) {
		if (!canUse(player) || player.isSpectator() || !player.isAlive()) {
			return;
		}
		if (!TaCZCaliberCatalog.isBallisticAmmoId(ammoId)) {
			return;
		}
		AmmoTier[] tiers = AmmoTier.values();
		if (tierOrdinal < 0 || tierOrdinal >= tiers.length) {
			return;
		}
		AmmoTier tier = tiers[tierOrdinal];
		if (!TaCZCaliberCatalog.isTierAllowed(ammoId, tier)) {
			return;
		}
		WorkbenchInventoryHelper.craftAmmo(player, ammoId, tier, upgradeFromFmj, isLobby());
		syncResources(player);
	}

	public void handleCraftGear(ServerPlayerEntity player, Identifier targetItemId) {
		if (!canUse(player) || player.isSpectator() || !player.isAlive() || targetItemId == null) {
			return;
		}
		if (!Registries.ITEM.containsId(targetItemId)) {
			return;
		}
		Item item = Registries.ITEM.get(targetItemId);
		if (!TarkovWorkbenchRecipes.isCraftableGear(item)) {
			return;
		}
		WorkbenchInventoryHelper.craftGear(player, item, isLobby());
		syncResources(player);
	}
}
