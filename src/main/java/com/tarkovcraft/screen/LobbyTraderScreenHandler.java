package com.tarkovcraft.screen;

import com.tarkovcraft.arena.PlayerProfileState;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.lobby.ItemPriceCalculator;
import com.tarkovcraft.lobby.LobbyManager;
import com.tarkovcraft.lobby.TraderCatalogue;
import com.tarkovcraft.lobby.TraderCatalogue.Offer;
import com.tarkovcraft.lobby.TraderCatalogue.Trader;
import com.tarkovcraft.lobby.TraderMarketState;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

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
	public static final int ROW_FIRST = 10;
	public static final int BASKET_FIRST = 20;

	public record Row(ItemStack stack, long price, int percent) { }
	public record BasketCell(ItemStack stack, int x, int y, int width, int height, long price) { }
	private enum Source { STASH, EQUIPMENT, POCKET, STORAGE }
	private record Ref(Source source, int index, UUID stashId) { }
	private record Selection(Ref ref, ItemStack expected) { }

	private record View(Trader trader, boolean carried, int page, int pages, List<Row> rows, List<BasketCell> basket) {
		void write(PacketByteBuf buf) {
			buf.writeEnumConstant(trader);
			buf.writeBoolean(carried);
			buf.writeVarInt(page);
			buf.writeVarInt(pages);
			buf.writeCollection(rows, (packet, row) -> {
				packet.writeItemStack(row.stack());
				packet.writeVarLong(row.price());
				packet.writeVarInt(row.percent());
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
			return new View(buf.readEnumConstant(Trader.class), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
				buf.readList(p -> new Row(p.readItemStack(), p.readVarLong(), p.readVarInt())),
				buf.readList(p -> new BasketCell(p.readItemStack(), p.readUnsignedByte(), p.readUnsignedByte(),
					p.readUnsignedByte(), p.readUnsignedByte(), p.readVarLong())));
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
	public boolean carried() { return view.carried(); }
	public int page() { return view.page(); }
	public int pages() { return view.pages(); }
	public List<Row> rows() { return view.rows(); }
	public List<BasketCell> basket() { return view.basket(); }

	public static LobbyTraderScreenHandler fromClientBuf(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new LobbyTraderScreenHandler(syncId, inventory.player, View.read(buf), List.of(), List.of(), Map.of());
	}

	public static void open(ServerPlayerEntity player) {
		open(player, Trader.PRAPOR, false, 0, Map.of());
	}

	private static void open(ServerPlayerEntity player, Trader trader, boolean carried, int requestedPage,
			Map<UUID, Selection> selected) {
		PlayerProfileState profile = PlayerProfileState.get(player.getServer());
		GridContainer stash = profile.stash(player.getUuid());
		PlayerLoadout loadout = ((PlayerLoadoutHolder) player).tarkovcraft$getLoadout();
		TraderMarketState market = TraderMarketState.get(player.getServer());
		List<Offer> allOffers = TraderCatalogue.offers();
		market.refresh(System.currentTimeMillis(), allOffers.stream().map(Offer::key).toList());
		List<Offer> stock = allOffers.stream().filter(offer -> offer.trader() == trader).toList();
		List<Selection> sources = trader == Trader.FENCE ? sources(player, stash, loadout, carried) : List.of();
		Map<UUID, Selection> basketRefs = new LinkedHashMap<>();
		GridContainer basket = new GridContainer(6, 6);
		for (Selection selection : selected.values()) {
			ItemStack stack = resolve(player, stash, loadout, selection.ref());
			if (!ItemStack.areEqual(stack, selection.expected())) continue;
			GridEntry entry = basket.tryPlaceInFreeCell(stack);
			if (entry != null) basketRefs.put(entry.getEntryId(), selection);
			else player.sendMessage(LobbyManager.label("trade.basket_full", "The item does not fit in the sale grid"), true);
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
			double multiplier = market.multiplier(offer.key());
			rows.add(new Row(offer.stack(), ItemPriceCalculator.buyPrice(offer.stack(), multiplier),
				(int) Math.round((multiplier - 1) * 100)));
		}
		for (Selection source : sourceRows) rows.add(new Row(source.expected(), ItemPriceCalculator.sellPrice(source.expected()), 0));
		List<BasketCell> cells = basket.getEntries().stream().map(entry -> new BasketCell(entry.getStack(),
			entry.getX(), entry.getY(), entry.getOccupiedSize().width(), entry.getOccupiedSize().height(),
			ItemPriceCalculator.sellPrice(entry.getStack()))).toList();
		View view = new View(trader, carried, page, pages, rows, cells);
		open(player, LobbyManager.label("tab.traders", "Traders"), view::write,
			syncId -> new LobbyTraderScreenHandler(syncId, player, view, offers, sourceRows, basketRefs));
	}

	private static List<Selection> sources(ServerPlayerEntity player, GridContainer stash, PlayerLoadout loadout,
			boolean carried) {
		List<Selection> sources = new ArrayList<>();
		if (!carried) {
			for (GridEntry entry : stash.getEntries())
				addSource(sources, new Ref(Source.STASH, 0, entry.getEntryId()), entry.getStack());
			return sources;
		}
		for (EquipmentSlotType slot : EquipmentSlotType.values())
			addSource(sources, new Ref(Source.EQUIPMENT, slot.ordinal(), null), loadout.getEquipment(slot));
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++)
			addSource(sources, new Ref(Source.POCKET, i, null), loadout.getPocket(i));
		for (int i = 0; ; i++) {
			ItemStack stack = loadout.getExposedStorageStack(i, player.getUuid(), true);
			if (stack.isEmpty()) break;
			addSource(sources, new Ref(Source.STORAGE, i, null), stack);
		}
		return sources;
	}

	private static void addSource(List<Selection> sources, Ref ref, ItemStack stack) {
		if (ItemPriceCalculator.sellPrice(stack) > 0
				&& ContainerItemHelper.readAllGrids(stack).stream().allMatch(GridContainer::isEmpty)) {
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
			open(player, Trader.values()[button], false, 0, selected);
		} else if (button == PREVIOUS || button == NEXT) {
			open(player, trader(), carried(), page() + (button == NEXT ? 1 : -1), selected);
		} else if (trader() == Trader.FENCE && (button == STASH || button == CARRIED)) {
			open(player, trader(), button == CARRIED, 0, selected);
		} else if (trader() == Trader.FENCE && button == SELL) {
			boolean sold = sell(player);
			open(player, trader(), carried(), page(), sold ? Map.of() : selected);
		} else if (button >= ROW_FIRST && button < ROW_FIRST + rows().size()) {
			int index = button - ROW_FIRST;
			if (trader() == Trader.FENCE) {
				Map<UUID, Selection> next = new LinkedHashMap<>(selected);
				next.put(UUID.randomUUID(), sourceRows.get(index));
				open(player, trader(), carried(), page(), next);
			} else {
				buy(player, offers.get(index), rows().get(index).price());
				open(player, trader(), carried(), page(), selected);
			}
		} else if (trader() == Trader.FENCE && button >= BASKET_FIRST && button < BASKET_FIRST + selected.size()) {
			Map<UUID, Selection> next = new LinkedHashMap<>(selected);
			next.remove(new ArrayList<>(selected.keySet()).get(button - BASKET_FIRST));
			open(player, trader(), carried(), page(), next);
		} else {
			return false;
		}
		return true;
	}

	private void buy(ServerPlayerEntity player, Offer offer, long quotedPrice) {
		TraderMarketState market = TraderMarketState.get(player.getServer());
		market.refresh(System.currentTimeMillis(), List.of());
		double multiplier = market.multiplier(offer.key());
		long price = ItemPriceCalculator.buyPrice(offer.stack(), multiplier);
		if (price != quotedPrice) {
			player.sendMessage(LobbyManager.label("trade.price_changed", "The price changed. Review the new price."), true);
			return;
		}
		PlayerProfileState profile = PlayerProfileState.get(player.getServer());
		UUID id = player.getUuid();
		long balance = profile.rubles(id);
		if (balance < price) {
			player.sendMessage(LobbyManager.label("insufficient_rubles", "Insufficient rubles"), true);
			return;
		}
		GridContainer stash = profile.stash(id).copy();
		if (!stash.tryAutoPlace(offer.stack().copy())) {
			player.sendMessage(LobbyManager.label("inventory_full", "No room for the item"), true);
			return;
		}
		profile.replaceStash(id, stash);
		profile.setRubles(id, balance - price);
		market.purchase(offer.key(), offer.demandIncrease());
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
