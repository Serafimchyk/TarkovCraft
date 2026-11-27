package com.tarkovcraft.screen;

import com.tarkovcraft.arena.PlayerProfileState;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.lobby.ItemPriceCalculator;
import com.tarkovcraft.lobby.LobbyManager;
import com.tarkovcraft.lobby.TraderCatalogue;
import com.tarkovcraft.lobby.TraderCatalogue.Offer;
import com.tarkovcraft.lobby.TraderCatalogue.Trader;
import com.tarkovcraft.lobby.TarkovDevPrices.Source;
import com.tarkovcraft.lobby.TraderCatalogue.Category;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LobbyTraderScreenHandler extends LobbyScreenHandler {
	public static final int ROWS = 8;
	public static final int PREVIOUS = 4;
	public static final int NEXT = 5;
	public static final int STASH = 6;
	public static final int CARRIED = 7;
	public static final int SELL = 8;
	public static final int CONFIRM = 9;
	public static final int CANCEL_PREVIEW = 56;
	public static final int CATEGORY_FIRST = 60;
	public static final int ROW_FIRST = 10;
	public static final int BASKET_FIRST = 20;

	public record Row(ItemStack stack, long price, Source priceSource) { }
	public record BasketCell(ItemStack stack, int x, int y, int width, int height, long price) { }
	private enum SourceLocation { STASH, EQUIPMENT, POCKET, STORAGE }
	private record Ref(SourceLocation source, int index, UUID stashId) { }
	private record Selection(Ref ref, ItemStack expected) { }

	private record View(Trader trader, Category category, boolean carried, int page, int pages, List<Row> rows,
			List<BasketCell> basket, int preview, Text notice) {
		void write(PacketByteBuf buf) {
			buf.writeEnumConstant(trader);
			buf.writeEnumConstant(category);
			buf.writeBoolean(carried);
			buf.writeVarInt(page);
			buf.writeVarInt(pages);
			buf.writeVarInt(preview);
			buf.writeText(notice);
			buf.writeCollection(rows, (packet, row) -> {
				packet.writeItemStack(row.stack());
				packet.writeVarLong(row.price());
				packet.writeEnumConstant(row.priceSource());
			});
			buf.writeCollection(basket, (packet, cell) -> {
				packet.writeItemStack(cell.stack());
				packet.writeByte(cell.x());
				packet.writeByte(cell.y());
				packet.writeByte(cell.width());
				packet.writeByte(cell.height());
				packet.writeVarLong(cell.price());
			});
		}

		static View read(PacketByteBuf buf) {
			var trader = buf.readEnumConstant(Trader.class);
			var category = buf.readEnumConstant(Category.class);
			boolean carried = buf.readBoolean();
			int page = buf.readVarInt();
			int pages = buf.readVarInt();
			int preview = buf.readVarInt();
			Text notice = buf.readText();
			return new View(trader, category, carried, page, pages,
				buf.readList(p -> new Row(p.readItemStack(), p.readVarLong(), p.readEnumConstant(Source.class))),
				buf.readList(p -> new BasketCell(p.readItemStack(), p.readUnsignedByte(), p.readUnsignedByte(),
					p.readUnsignedByte(), p.readUnsignedByte(), p.readVarLong())), preview, notice);
		}
	}

	private final View view;
	private final List<Offer> offers;
	private final List<Selection> sourceRows;
	private final Map<UUID, Selection> selected;

	private LobbyTraderScreenHandler(int syncId, PlayerEntity owner, View view, List<Offer> offers,
			List<Selection> sourceRows, Map<UUID, Selection> selected) {
		super(ModScreenHandlers.LOBBY_TRADER_SCREEN_HANDLER, syncId, owner);
		this.view = view;
		this.offers = offers;
		this.sourceRows = sourceRows;
		this.selected = selected;
	}

	public Trader trader() { return view.trader(); }
	public Category category() { return view.category(); }
	public int preview() { return view.preview(); }
	public Text notice() { return view.notice(); }
	public boolean carried() { return view.carried(); }
	public int page() { return view.page(); }
	public int pages() { return view.pages(); }
	public List<Row> rows() { return view.rows(); }
	public List<BasketCell> basket() { return view.basket(); }

	public static LobbyTraderScreenHandler fromClientBuf(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new LobbyTraderScreenHandler(syncId, inventory.player, View.read(buf), List.of(), List.of(), Map.of());
	}

	public static void open(ServerPlayerEntity player) {
		open(player, Trader.PRAPOR, Category.ALL, false, 0, Map.of(), -1, Text.empty());
	}

	public static void openForStashSale(ServerPlayerEntity player, UUID entryId) {
		if (!LobbyTabNavigation.canUse(player)) return;
		GridContainer stash = PlayerProfileState.get(player.getServer()).stash(player.getUuid());
		GridEntry entry = stash.getEntry(entryId);
		if (entry == null || !ItemPriceCalculator.canSell(entry.getStack())) return;
		Selection selection = new Selection(new Ref(SourceLocation.STASH, 0, entryId), entry.getStack().copy());
		player.onHandledScreenClosed();
		open(player, Trader.FENCE, Category.ALL, false, 0, Map.of(entryId, selection), -1, Text.empty());
	}

	private static void open(ServerPlayerEntity player, Trader trader, Category category, boolean carried, int requestedPage,
			Map<UUID, Selection> selected, int preview, Text notice) {
		PlayerProfileState profile = PlayerProfileState.get(player.getServer());
		GridContainer stash = profile.stash(player.getUuid());
		PlayerLoadout loadout = ((PlayerLoadoutHolder) player).tarkovcraft$getLoadout();
		List<Offer> stock = trader == Trader.FENCE ? List.of() : TraderCatalogue.offers().stream()
			.filter(offer -> offer.trader() == trader && (category == Category.ALL || offer.category() == category)).toList();
		List<Selection> sources = trader == Trader.FENCE ? sources(player, stash, loadout, carried) : List.of();
		Map<UUID, Selection> basketRefs = new LinkedHashMap<>();
		GridContainer basket = new GridContainer(6, 6);
		for (Selection selection : selected.values()) {
			ItemStack stack = resolve(player, stash, loadout, selection.ref());
			if (!ItemStack.areEqual(stack, selection.expected())) continue;
			GridEntry entry = basket.tryPlaceInFreeCell(stack);
			if (entry != null) basketRefs.put(entry.getEntryId(), selection);
			else notice = LobbyManager.label("trade.basket_full", "The item does not fit in the sale grid");
		}
		sources = sources.stream().filter(source -> basketRefs.values().stream()
			.noneMatch(selection -> selection.ref().equals(source.ref()))).toList();
		int size = trader == Trader.FENCE ? sources.size() : stock.size();
		int pages = Math.max(1, (size + ROWS - 1) / ROWS);
		int page = Math.max(0, Math.min(pages - 1, requestedPage));
		int from = page * ROWS;
		int to = Math.min(size, from + ROWS);
		List<Offer> offers = trader == Trader.FENCE ? List.of() : stock.subList(from, to);
		List<Selection> sourceRows = trader == Trader.FENCE ? sources.subList(from, to) : List.of();
		List<Row> rows = new ArrayList<>();
		for (Offer offer : offers) {
			rows.add(new Row(offer.stack(), ItemPriceCalculator.buyPrice(offer.stack()), ItemPriceCalculator.priceSource(offer.stack())));
		}
		for (Selection source : sourceRows)
			rows.add(new Row(source.expected(), ItemPriceCalculator.sellPrice(source.expected()), Source.LOCAL));
		List<BasketCell> cells = basket.getEntries().stream().map(entry -> new BasketCell(entry.getStack(),
			entry.getX(), entry.getY(), entry.getOccupiedSize().width(), entry.getOccupiedSize().height(),
			ItemPriceCalculator.sellPrice(entry.getStack()))).toList();
		View view = new View(trader, category, carried, page, pages, rows, cells,
			preview >= 0 && preview < offers.size() ? preview : -1, notice);
		open(player, LobbyManager.label("tab.traders", "Traders"), view::write,
			syncId -> new LobbyTraderScreenHandler(syncId, player, view, offers, sourceRows, basketRefs));
	}

	private static List<Selection> sources(ServerPlayerEntity player, GridContainer stash, PlayerLoadout loadout,
			boolean carried) {
		List<Selection> sources = new ArrayList<>();
		if (!carried) {
			for (GridEntry entry : stash.getEntries())
				addSource(sources, new Ref(SourceLocation.STASH, 0, entry.getEntryId()), entry.getStack());
			return sources;
		}
		for (EquipmentSlotType slot : EquipmentSlotType.values())
			addSource(sources, new Ref(SourceLocation.EQUIPMENT, slot.ordinal(), null), loadout.getEquipment(slot));
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++)
			addSource(sources, new Ref(SourceLocation.POCKET, i, null), loadout.getPocket(i));
		for (int i = 0; ; i++) {
			ItemStack stack = loadout.getExposedStorageStack(i, player.getUuid(), true);
			if (stack.isEmpty()) break;
			addSource(sources, new Ref(SourceLocation.STORAGE, i, null), stack);
		}
		return sources;
	}

	private static void addSource(List<Selection> sources, Ref ref, ItemStack stack) {
		if (ItemPriceCalculator.canSell(stack)) {
			sources.add(new Selection(ref, stack.copy()));
		}
	}

	private static ItemStack resolve(ServerPlayerEntity player, GridContainer stash, PlayerLoadout loadout, Ref ref) {
		return switch (ref.source()) {
			case STASH -> {
				GridEntry entry = stash.getEntry(ref.stashId());
				yield entry == null ? ItemStack.EMPTY : entry.getStack();
			}
			case EQUIPMENT -> loadout.getEquipment(EquipmentSlotType.values()[ref.index()]);
			case POCKET -> loadout.getPocket(ref.index());
			case STORAGE -> loadout.getExposedStorageStack(ref.index(), player.getUuid(), true);
		};
	}

	@Override
	protected boolean handleButton(ServerPlayerEntity player, int button) {
		if (button >= 0 && button < Trader.values().length) {
			open(player, Trader.values()[button], Category.ALL, false, 0, selected, -1, Text.empty());
		} else if (button >= CATEGORY_FIRST && button < CATEGORY_FIRST + Category.values().length) {
			Category category = Category.values()[button - CATEGORY_FIRST];
			if (trader() == Trader.FENCE || category != Category.ALL && !trader().categories().contains(category)) return false;
			open(player, trader(), category, false, 0, selected, -1, Text.empty());
		} else if (button == PREVIOUS || button == NEXT) {
			reopen(player, carried(), page() + (button == NEXT ? 1 : -1), selected, -1, Text.empty());
		} else if (trader() == Trader.FENCE && (button == STASH || button == CARRIED)) {
			reopen(player, button == CARRIED, 0, selected, -1, Text.empty());
		} else if (trader() == Trader.FENCE && button == SELL) {
			boolean sold = sell(player);
			reopen(player, carried(), page(), sold ? Map.of() : selected, -1, sold
				? LobbyManager.label("trade.sold", "Sale completed")
				: LobbyManager.label("trade.sale_changed", "Review the items in the sale grid"));
		} else if (button >= ROW_FIRST && button < ROW_FIRST + rows().size()) {
			int index = button - ROW_FIRST;
			if (trader() == Trader.FENCE) {
				Map<UUID, Selection> next = new LinkedHashMap<>(selected);
				next.put(UUID.randomUUID(), sourceRows.get(index));
				reopen(player, carried(), page(), next, -1, Text.empty());
			} else {
				reopen(player, false, page(), selected, index, Text.empty());
			}
		} else if (button == CONFIRM && preview() >= 0 && preview() < offers.size()) {
			Text notice = buy(player, offers.get(preview()), rows().get(preview()).price());
			reopen(player, false, page(), selected, -1, notice);
		} else if (button == CANCEL_PREVIEW && preview() >= 0) {
			reopen(player, false, page(), selected, -1, Text.empty());
		} else if (trader() == Trader.FENCE && button >= BASKET_FIRST && button < BASKET_FIRST + selected.size()) {
			Map<UUID, Selection> next = new LinkedHashMap<>(selected);
			next.remove(new ArrayList<>(selected.keySet()).get(button - BASKET_FIRST));
			reopen(player, carried(), page(), next, -1, Text.empty());
		} else {
			return false;
		}
		return true;
	}

	private void reopen(ServerPlayerEntity player, boolean carried, int page, Map<UUID, Selection> selection, int preview, Text notice) {
		open(player, trader(), category(), carried, page, selection, preview, notice);
	}

	private static Text buy(ServerPlayerEntity player, Offer offer, long quotedPrice) {
		long price = ItemPriceCalculator.buyPrice(offer.stack());
		if (price != quotedPrice) return LobbyManager.label("trade.price_changed", "The price changed. Review the new price.");
		PlayerProfileState profile = PlayerProfileState.get(player.getServer());
		UUID id = player.getUuid();
		long balance = profile.rubles(id);
		if (balance < price) return LobbyManager.label("insufficient_rubles", "Insufficient rubles");
		GridContainer stash = profile.stash(id).copy();
		if (!stash.tryAutoPlace(offer.stack().copy())) return LobbyManager.label("inventory_full", "No room for the item");
		profile.replaceStash(id, stash);
		profile.setRubles(id, balance - price);
		return LobbyManager.label("trade.purchased", "Purchase delivered to the stash");
	}

		private boolean sell(ServerPlayerEntity player) {
		PlayerProfileState profile = PlayerProfileState.get(player.getServer());
		UUID id = player.getUuid();
		GridContainer stash = profile.stash(id);
		PlayerLoadoutHolder holder = (PlayerLoadoutHolder) player;
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		long total = 0;
		for (Selection selection : selected.values()) {
			ItemStack actual = resolve(player, stash, loadout, selection.ref());
			if (!ItemStack.areEqual(actual, selection.expected())) return false;
			total += ItemPriceCalculator.sellPrice(actual);
		}
		long balance = profile.rubles(id);
		if (total <= 0 || balance > Long.MAX_VALUE - total) return false;
		// Exposed storage indices shift on removal, so sell those stacks from the end first.
		List<Ref> refs = selected.values().stream().map(Selection::ref)
			.sorted(Comparator.comparing(Ref::source).thenComparingInt(Ref::index).reversed()).toList();
		for (Ref ref : refs) {
			switch (ref.source()) {
				case STASH -> stash.remove(ref.stashId());
				case EQUIPMENT -> loadout.setEquipment(EquipmentSlotType.values()[ref.index()], ItemStack.EMPTY);
				case POCKET -> loadout.setPocket(ref.index(), ItemStack.EMPTY);
				case STORAGE -> loadout.removeExposedStorageStack(ref.index(), Integer.MAX_VALUE, id, true);
			}
		}
		profile.setRubles(id, balance + total);
		holder.tarkovcraft$syncLoadout();
		return true;
	}
}
