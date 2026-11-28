package com.tarkovcraft.screen;

import com.tarkovcraft.ammo.AmmoReloadTracker;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.lobby.LoadoutAssembly;
import com.tarkovcraft.lobby.LoadoutAssembly.Quote;
import com.tarkovcraft.lobby.LoadoutAssembly.Slot;
import com.tarkovcraft.lobby.LobbyManager;
import com.tarkovcraft.lobby.TraderCatalogue.Offer;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LobbyLoadoutScreenHandler extends LobbyScreenHandler {
	public static final int ROWS = 5;
	public static final int PREVIOUS = 1, NEXT = 2, SAVE = 3, ASSEMBLE = 4, REPAIR = 5;
	public static final int SLOT_FIRST = 10, PRESET_FIRST = 30, EQUIP_FIRST = 40, AMMO_FIRST = 55;

	public record Row(ItemStack item, boolean market, long price, int adjustment, boolean allowed) { }
	public record Ammo(AmmoTier tier, long price30, long price60) { }

	private record Candidate(Row row, @Nullable UUID entry, @Nullable Offer offer) { }

	private record View(Slot slot, int page, int pages, int preset, boolean saved, Quote quote, long repair,
		List<Row> rows, String caliber, List<Ammo> ammo) {
		void write(PacketByteBuf buf) {
			buf.writeVarInt(slot.index()).writeVarInt(page).writeVarInt(pages).writeVarInt(preset).writeBoolean(saved);
			buf.writeVarInt(quote.missing()).writeVarLong(quote.price()).writeBoolean(quote.blocked());
			buf.writeVarLong(repair);
			buf.writeCollection(rows, (packet, row) -> {
				packet.writeItemStack(row.item());
				packet.writeBoolean(row.market());
				packet.writeVarLong(row.price()).writeByte(row.adjustment());
				packet.writeBoolean(row.allowed());
			});
			buf.writeString(caliber, 128);
			buf.writeCollection(ammo, (packet, row) ->
				packet.writeEnumConstant(row.tier()).writeVarLong(row.price30()).writeVarLong(row.price60()));
		}

		static View read(PacketByteBuf buf) {
			return new View(new Slot(buf.readVarInt()), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readBoolean(), new Quote(buf.readVarInt(), buf.readVarLong(), buf.readBoolean()),
				buf.readVarLong(), buf.readList(p -> new Row(p.readItemStack(), p.readBoolean(), p.readVarLong(),
					p.readByte(), p.readBoolean())), buf.readString(128),
				buf.readList(p -> new Ammo(p.readEnumConstant(AmmoTier.class), p.readVarLong(), p.readVarLong())));
		}
	}

	private final View view;
	private final List<Candidate> candidates;
	private final List<Offer> ammunition;

	private LobbyLoadoutScreenHandler(int syncId, PlayerEntity owner, View view, List<Candidate> candidates,
		List<Offer> ammunition) {
		super(ModScreenHandlers.LOBBY_LOADOUT_SCREEN_HANDLER, syncId, owner);
		this.view = view;
		this.candidates = candidates;
		this.ammunition = ammunition;
	}

	public static LobbyLoadoutScreenHandler fromClientBuf(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new LobbyLoadoutScreenHandler(syncId, inventory.player, View.read(buf), List.of(), List.of());
	}

	public static void open(ServerPlayerEntity player) { open(player, new Slot(EquipmentSlotType.PRIMARY_1.ordinal()), 0, 0); }

	private static void open(ServerPlayerEntity player, Slot slot, int requestedPage, int preset) {
		LoadoutAssembly assembly = new LoadoutAssembly(player);
		List<Candidate> all = new ArrayList<>();
		for (var entry : assembly.stash().getEntries()) {
			ItemStack stack = entry.getStack();
			if (assembly.accepts(slot, stack)) {
				all.add(new Candidate(new Row(stack.copy(), false, 0, 0, assembly.canSwap(slot, stack)), entry.getEntryId(), null));
			}
		}
		for (Offer offer : assembly.offers()) {
			ItemStack stack = offer.stack();
			if (assembly.accepts(slot, stack) && (!isGunSlot(slot.equipment()) || AmmoTier.isTaczGunStack(stack))) {
				int adjustment = 0;
				all.add(new Candidate(new Row(stack, true, assembly.price(offer, 1), adjustment, assembly.canSwap(slot, stack)),
					null, offer));
			}
		}

		int pages = Math.max(1, (all.size() + ROWS - 1) / ROWS);
		int page = Math.max(0, Math.min(pages - 1, requestedPage));
		List<Candidate> candidates = all.subList(page * ROWS, Math.min((page + 1) * ROWS, all.size()));
		String caliber = caliber(assembly, slot.equipment());
		List<Offer> ammo = assembly.offers().stream().filter(offer -> !caliber.isEmpty()
			&& AmmoTier.isTaczAmmoStack(offer.stack()) && AmmoTier.getAmmoId(offer.stack()).equals(caliber)).toList();
		List<Ammo> prices = ammo.stream().map(offer ->
			new Ammo(AmmoTier.getFromAmmoStack(offer.stack()), assembly.price(offer, 1), assembly.price(offer, 2))).toList();
		View view = new View(slot, page, pages, preset, assembly.saved(preset), assembly.quote(preset),
			LobbyServiceScreenHandler.equippedRepairPrice(player), candidates.stream().map(Candidate::row).toList(), caliber, prices);
		open(player, LobbyManager.label("tab.loadout", "Loadout"), view::write,
			syncId -> new LobbyLoadoutScreenHandler(syncId, player, view, candidates, ammo));
	}

	private static String caliber(LoadoutAssembly assembly, EquipmentSlotType slot) {
		String caliber = isGunSlot(slot) ? AmmoReloadTracker.getGunAmmoId(assembly.loadout().getEquipment(slot)) : "";
		return caliber.length() <= 128 ? caliber : "";
	}

	private static boolean isGunSlot(EquipmentSlotType slot) {
		return slot == EquipmentSlotType.PRIMARY_1 || slot == EquipmentSlotType.PRIMARY_2 || slot == EquipmentSlotType.PISTOL;
	}

	@Override
	protected boolean handleButton(ServerPlayerEntity player, int button) {
		int slot = button - SLOT_FIRST;
		int preset = button - PRESET_FIRST;
		if (button == PREVIOUS || button == NEXT) {
			open(player, slot(), page() + (button == NEXT ? 1 : -1), preset());
		} else if (slot >= 0 && slot < Slot.COUNT) {
			open(player, new Slot(slot), 0, preset());
		} else if (preset >= 0 && preset < 3) {
			open(player, slot(), page(), preset);
		} else {
			LoadoutAssembly assembly = new LoadoutAssembly(player);
			boolean success;
			int row = button - EQUIP_FIRST;
			int ammo = button - AMMO_FIRST;
			if (button == SAVE) {
				assembly.save(preset());
				success = true;
			} else if (button == ASSEMBLE) {
				success = assembly.assemble(preset(), quote());
			} else if (button == REPAIR) {
				success = LobbyServiceScreenHandler.repairEquipped(player, repairPrice());
			} else if (row >= 0 && row < candidates.size()) {
				Candidate candidate = candidates.get(row);
				success = assembly.equip(slot(), candidate.entry(), candidate.row().item(), candidate.offer(), candidate.row().price());
			} else if (ammo >= 0 && ammo / 2 < ammunition.size()) {
				Offer offer = ammunition.get(ammo / 2);
				int packs = ammo % 2 + 1;
				Ammo prices = view.ammo().get(ammo / 2);
				success = caliber(assembly, slot().equipment()).equals(view.caliber())
					&& assembly.buyAmmo(offer, packs, packs == 1 ? prices.price30() : prices.price60());
			} else {
				return false;
			}
			if (!success) player.sendMessage(LobbyManager.label("loadout.failed",
				"Could not complete the request. Check space, balance and equipment compatibility."), true);
			open(player, slot(), page(), preset());
		}
		return true;
	}

	public Slot slot() { return view.slot(); }
	public int page() { return view.page(); }
	public int pages() { return view.pages(); }
	public int preset() { return view.preset(); }
	public boolean saved() { return view.saved(); }
	public Quote quote() { return view.quote(); }
	public long repairPrice() { return view.repair(); }
	public List<Row> rows() { return view.rows(); }
	public String caliber() { return view.caliber(); }
	public List<Ammo> ammo() { return view.ammo(); }
}
