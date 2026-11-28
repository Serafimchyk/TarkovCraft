package com.tarkovcraft.screen;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.arena.PlayerProfileState;
import com.tarkovcraft.arena.RaidSession;
import com.tarkovcraft.arena.RaidSession.ResultStats;
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

import java.util.List;
import java.util.Locale;

public final class RaidScreenHandler extends ScreenHandler {
	public static final int BACK = 0, CLAIM_ALL = 4, PREVIOUS_PAGE = 5, NEXT_PAGE = 6;
	public static final int TAKE_FIRST = 7, SELECT_FIRST = 11, ROTATE = 15;
	// ButtonClickC2SPacket uses a signed byte. IDs 16–255 encode the 240 stash cells.
	public static final int CELL_FIRST = 16;
	public static final int PAGE_SIZE = 4;

	public enum Mode { SUMMARY, TRANSFER }

	public record StashItem(ItemStack stack, int x, int y, int width, int height, boolean rotated) {
		void write(PacketByteBuf buf) {
			buf.writeItemStack(preview(stack));
			buf.writeByte(x).writeByte(y).writeByte(width).writeByte(height).writeBoolean(rotated);
		}

		static StashItem read(PacketByteBuf buf) {
			return new StashItem(buf.readItemStack(), buf.readUnsignedByte(), buf.readUnsignedByte(),
				buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readBoolean());
		}
	}

	private record View(Mode mode, String result, String arena, @Nullable NbtCompound report, ResultStats stats,
		int page, int totalItems, List<ItemStack> items, List<StashItem> stash) {
		void write(PacketByteBuf buf) {
			buf.writeEnumConstant(mode);
			if (mode == Mode.SUMMARY) {
				buf.writeString(truncate(result, 32), 32);
				buf.writeString(truncate(arena, 128), 128);
				buf.writeNbt(report);
				buf.writeVarInt(stats.seconds()).writeVarInt(stats.kills()).writeVarInt(stats.damage()).writeVarLong(stats.lootValue());
			} else {
				buf.writeVarInt(page).writeVarInt(totalItems);
				buf.writeCollection(items, (packet, stack) -> packet.writeItemStack(preview(stack)));
				buf.writeCollection(stash, (packet, entry) -> entry.write(packet));
			}
		}

		static View read(PacketByteBuf buf) {
			if (buf.readEnumConstant(Mode.class) == Mode.SUMMARY) {
				return new View(Mode.SUMMARY, buf.readString(32), buf.readString(128), buf.readNbt(),
					new ResultStats(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarLong()),
					0, 0, List.of(), List.of());
			}
			return new View(Mode.TRANSFER, "", "", null, null, buf.readVarInt(), buf.readVarInt(),
				buf.readList(PacketByteBuf::readItemStack), buf.readList(StashItem::read));
		}
	}

	@Nullable
	private final ServerPlayerEntity owner;
	private final View view;
	private int selection = -1;
	private boolean rotated;

	private RaidScreenHandler(int syncId, @Nullable ServerPlayerEntity owner, View view) {
		super(ModScreenHandlers.RAID_SCREEN_HANDLER, syncId);
		this.owner = owner;
		this.view = view;
	}

	public static RaidScreenHandler fromClientBuf(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new RaidScreenHandler(syncId, null, View.read(buf));
	}

	public static void openLobby(ServerPlayerEntity player) {
		LobbyPlayScreenHandler.open(player);
	}

	public static void openSummary(ServerPlayerEntity player, String result, String arena,
		@Nullable NbtCompound report, ResultStats stats) {
		open(player, new View(Mode.SUMMARY, result.toUpperCase(Locale.ROOT), arena, report, stats, 0, 0, List.of(), List.of()),
			label("summary", "Raid results"));
	}

	public static void openTransfer(ServerPlayerEntity player) {
		openTransfer(player, 0);
	}

	private static void openTransfer(ServerPlayerEntity player, int requestedPage) {
		if (!LobbyTabNavigation.canUse(player)) return;
		List<ItemStack> loot = RaidSession.pendingLoot(player);
		int page = Math.max(0, Math.min(requestedPage, lastPage(loot.size())));
		int from = page * PAGE_SIZE;
		var stash = PlayerProfileState.get(player.getServer()).stash(player.getUuid());
		List<StashItem> stashItems = stash.getEntries().stream().map(entry -> {
			var size = entry.getOccupiedSize();
			return new StashItem(entry.getStack(), entry.getX(), entry.getY(), size.width(), size.height(), entry.isRotated());
		}).toList();
		open(player, new View(Mode.TRANSFER, "", "", null, null, page, loot.size(),
			loot.subList(from, Math.min(from + PAGE_SIZE, loot.size())), stashItems), label("transfer", "Scav loot"));
	}

	private static void open(ServerPlayerEntity player, View view, Text title) {
		if (!LobbyTabNavigation.canUse(player)) return;
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity viewer, PacketByteBuf buf) { view.write(buf); }

			@Override
			public Text getDisplayName() { return title; }

			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity viewer) {
				return new RaidScreenHandler(syncId, player, view);
			}
		});
	}

	private static String truncate(String text, int limit) {
		return text.length() <= limit ? text : text.substring(0, limit);
	}

	private static ItemStack preview(ItemStack stack) {
		ItemStack display = new ItemStack(stack.getItem(), stack.getCount());
		if (stack.hasCustomName()) display.setCustomName(Text.literal(truncate(stack.getName().getString(), 100)));
		if (AmmoTier.isTaczGunStack(stack) && stack.hasNbt())
			display.getOrCreateNbt().putString("GunId", truncate(stack.getNbt().getString("GunId"), 128));
		if (AmmoTier.isTaczAmmoStack(stack)) {
			display.getOrCreateNbt().putString("AmmoId", truncate(AmmoTier.getAmmoId(stack), 128));
			AmmoTier.setOnAmmoStack(display, AmmoTier.getFromAmmoStack(stack));
		}
		return display;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return owner == null || player == owner && LobbyTabNavigation.canUse(owner);
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !canUse(player) || serverPlayer.currentScreenHandler != this)
			return false;
		if (id == BACK) {
			if (mode() == Mode.SUMMARY) RaidSession.acknowledgeResult(serverPlayer);
			if (mode() == Mode.SUMMARY && result().equals("SCAV_EXTRACTED") && !RaidSession.pendingLoot(serverPlayer).isEmpty()) {
				openTransfer(serverPlayer);
			} else {
				openLobby(serverPlayer);
			}
			return true;
		}
		return mode() == Mode.TRANSFER && lootButton(serverPlayer, id & 0xff);
	}

	private boolean lootButton(ServerPlayerEntity player, int id) {
		if (id == PREVIOUS_PAGE || id == NEXT_PAGE) {
			int requestedPage = page() + (id == NEXT_PAGE ? 1 : -1);
			if (requestedPage < 0 || requestedPage >= pageCount()) return false;
			openTransfer(player, requestedPage);
			return true;
		}
		int row = id - SELECT_FIRST;
		if (row >= 0 && row < items().size()) {
			selection = selection == row ? -1 : row;
			rotated = false;
			return true;
		}
		if (id == ROTATE && selection >= 0) {
			rotated = !rotated;
			return true;
		}
		if (id >= CELL_FIRST && selection >= 0) {
			int cell = id - CELL_FIRST;
			int width = PlayerProfileState.STASH_WIDTH;
			boolean placed = RaidSession.placeLoot(player, page() * PAGE_SIZE + selection, items().get(selection),
				cell % width, cell / width, rotated);
			if (placed) openTransfer(player, page());
			else player.sendMessage(label("no_space", "No room for this loot."), true);
			return true;
		}
		if (id == CLAIM_ALL) {
			claim(player, -1);
			return true;
		}
		row = id - TAKE_FIRST;
		if (row < 0 || row >= items().size()) return false;
		int index = page() * PAGE_SIZE + row;
		List<ItemStack> live = RaidSession.pendingLoot(player);
		if (index >= live.size() || !ItemStack.areEqual(live.get(index), items().get(row))) {
			openTransfer(player, page());
			return false;
		}
		claim(player, index);
		return true;
	}

	private void claim(ServerPlayerEntity player, int index) {
		boolean changed = RaidSession.claimLoot(player, index);
		if (!changed || index == -1 && !RaidSession.pendingLoot(player).isEmpty())
			player.sendMessage(label("no_space", "No room for this loot."), true);
		openTransfer(player, page());
	}

	private static int lastPage(int count) { return count == 0 ? 0 : (count - 1) / PAGE_SIZE; }

	public Mode mode() { return view.mode(); }
	public String result() { return view.result(); }
	public String arena() { return view.arena(); }
	@Nullable public DamageReport report() { return view.report() == null ? null : DamageReport.fromNbt(view.report()); }
	public ResultStats stats() { return view.stats(); }
	public int page() { return view.page(); }
	public int pageCount() { return lastPage(view.totalItems()) + 1; }
	public int totalItems() { return view.totalItems(); }
	public List<ItemStack> items() { return view.items(); }
	public List<StashItem> stash() { return view.stash(); }

	public static Text label(String key, String fallback, Object... args) {
		return Text.translatableWithFallback("screen.tarkovcraft.raid." + key, fallback, args);
	}
}
