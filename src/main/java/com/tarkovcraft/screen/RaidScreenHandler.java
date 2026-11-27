package com.tarkovcraft.screen;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.arena.RaidSession;
import com.tarkovcraft.health.DamageReport;
import com.tarkovcraft.registry.ModScreenHandlers;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RaidScreenHandler extends ScreenHandler {
	public static final int BACK = 0;
	public static final int CLAIM_ALL = 4;
	public static final int PREVIOUS_PAGE = 5;
	public static final int NEXT_PAGE = 6;
	public static final int TAKE_FIRST = 7;
	public static final int PAGE_SIZE = 4;

	public enum Mode {
		SUMMARY("summary", "Raid results"),
		TRANSFER("transfer", "Scav loot");

		private final String key;
		private final String fallback;

		Mode(String key, String fallback) {
			this.key = key;
			this.fallback = fallback;
		}

		public Text title() {
			return label(key, fallback);
		}
	}

	private final Mode mode;
	private final String result;
	private final String arena;
	@Nullable
	private final DamageReport report;
	private final int page;
	private final int totalItems;
	private final List<ItemStack> items;
	@Nullable
	private final ServerPlayerEntity owner;

	private RaidScreenHandler(int syncId, @Nullable ServerPlayerEntity owner, Mode mode, String result, String arena,
		@Nullable NbtCompound report, int page, int totalItems, List<ItemStack> items) {
		super(ModScreenHandlers.RAID_SCREEN_HANDLER, syncId);
		this.owner = owner;
		this.mode = mode;
		this.result = result;
		this.arena = arena;
		this.report = report == null ? null : DamageReport.fromNbt(report);
		this.page = page;
		this.totalItems = totalItems;
		this.items = items;
	}

	public static RaidScreenHandler fromClientBuf(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		Mode mode = buf.readEnumConstant(Mode.class);
		String result = buf.readString(32);
		String arena = buf.readString(128);
		NbtCompound report = buf.readNbt();
		int page = buf.readVarInt();
		int totalItems = buf.readVarInt();
		List<ItemStack> items = buf.readList(PacketByteBuf::readItemStack);
		return new RaidScreenHandler(syncId, null, mode, result, arena, report, page, totalItems, items);
	}

	public static void openLobby(ServerPlayerEntity player) {
		LobbyPlayScreenHandler.open(player);
	}

	public static void openSummary(ServerPlayerEntity player, String result, String arena, @Nullable NbtCompound report) {
		open(player, Mode.SUMMARY, result.toUpperCase(Locale.ROOT), arena, report, 0);
	}

	public static void openTransfer(ServerPlayerEntity player) {
		open(player, Mode.TRANSFER, "", "", null, 0);
	}

	public static boolean isOpen(ServerPlayerEntity player) {
		return player.currentScreenHandler instanceof RaidScreenHandler
			|| player.currentScreenHandler instanceof LobbyPlayScreenHandler
			|| player.currentScreenHandler instanceof TarkovScreenHandler inventory && inventory.getMode() == TarkovScreenHandler.MODE_STASH;
	}

	private static void open(ServerPlayerEntity player, Mode mode, String result, String arena,
		@Nullable NbtCompound report, int requestedPage) {
		if (!player.isAlive() || !RaidSession.isLobby(player)) return;
		List<ItemStack> loot = mode == Mode.TRANSFER ? RaidSession.pendingLoot(player) : List.of();
		int page = Math.min(Math.max(requestedPage, 0), lastPage(loot.size()));
		int from = page * PAGE_SIZE;
		int to = Math.min(from + PAGE_SIZE, loot.size());
		List<ItemStack> items = new ArrayList<>(to - from);
		if (mode == Mode.TRANSFER) {
			for (int i = from; i < to; i++) items.add(loot.get(i).copy());
		}
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity viewer, PacketByteBuf buf) {
				buf.writeEnumConstant(mode);
				buf.writeString(truncate(result, 32), 32);
				buf.writeString(truncate(arena, 128), 128);
				buf.writeNbt(report);
				buf.writeVarInt(page);
				buf.writeVarInt(loot.size());
				buf.writeCollection(items, (packet, stack) -> packet.writeItemStack(preview(stack)));
			}

			@Override
			public Text getDisplayName() {
				return mode.title();
			}

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity viewer) {
				return new RaidScreenHandler(syncId, player, mode, result, arena, report, page, loot.size(), items);
			}
		});
	}

	private static String truncate(String text, int limit) {
		return text.length() <= limit ? text : text.substring(0, limit);
	}

	private static ItemStack preview(ItemStack stack) {
		ItemStack display = new ItemStack(stack.getItem(), stack.getCount());
		if (stack.hasCustomName()) display.setCustomName(Text.literal(truncate(stack.getName().getString(), 100)));
		if (AmmoTier.isTaczGunStack(stack) && stack.hasNbt()) {
			display.getOrCreateNbt().putString("GunId", truncate(stack.getNbt().getString("GunId"), 128));
		}
		if (AmmoTier.isTaczAmmoStack(stack)) {
			display.getOrCreateNbt().putString("AmmoId", truncate(AmmoTier.getAmmoId(stack), 128));
		}
		return display;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return owner == null || player == owner && owner.isAlive() && RaidSession.isLobby(owner);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !canUse(player) || serverPlayer.currentScreenHandler != this) {
			return false;
		}
		if (id == BACK) {
			if (mode == Mode.SUMMARY && result.equals("SCAV_EXTRACTED") && !RaidSession.pendingLoot(serverPlayer).isEmpty()) {
				openTransfer(serverPlayer);
			} else {
				openLobby(serverPlayer);
			}
			return true;
		}
		return mode == Mode.TRANSFER && lootButton(serverPlayer, id);
	}

	private boolean lootButton(ServerPlayerEntity player, int id) {
		if (id == PREVIOUS_PAGE || id == NEXT_PAGE) {
			int requestedPage = page + (id == NEXT_PAGE ? 1 : -1);
			if (requestedPage < 0 || requestedPage > lastPage(totalItems)) return false;
			open(player, mode, "", "", null, requestedPage);
			return true;
		}
		if (id == CLAIM_ALL) {
			claim(player, -1);
			return true;
		}
		int row = id - TAKE_FIRST;
		if (row < 0 || row >= items.size()) return false;
		int index = page * PAGE_SIZE + row;
		List<ItemStack> live = RaidSession.pendingLoot(player);
		if (index >= live.size() || !ItemStack.areEqual(live.get(index), items.get(row))) {
			open(player, mode, "", "", null, page);
			return false;
		}
		claim(player, index);
		return true;
	}

	private void claim(ServerPlayerEntity player, int index) {
		boolean changed = RaidSession.claimLoot(player, index);
		if (!changed) player.sendMessage(label("no_space", "No room for this loot."), true);
		open(player, mode, "", "", null, page);
	}

	private static int lastPage(int count) {
		return count == 0 ? 0 : (count - 1) / PAGE_SIZE;
	}

	public Mode mode() { return mode; }
	public String result() { return result; }
	public String arena() { return arena; }
	@Nullable
	public DamageReport report() { return report; }
	public int page() { return page; }
	public int pageCount() { return lastPage(totalItems) + 1; }
	public int totalItems() { return totalItems; }
	public List<ItemStack> items() { return items; }

	public static Text label(String key, String fallback, Object... args) {
		return Text.translatableWithFallback("screen.tarkovcraft.raid." + key, fallback, args);
	}
}
