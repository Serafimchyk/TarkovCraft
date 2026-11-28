package com.tarkovcraft.screen;

import com.tarkovcraft.arena.PlayerProfileState;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.InjuryType;
import com.tarkovcraft.health.MedicalHelper;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.lobby.LobbyManager;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
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

public final class LobbyServiceScreenHandler extends LobbyScreenHandler {
	public static final int HEAL = 0;
	public static final int USE_STASH_MEDS = 1;
	public static final int REPAIR_EQUIPPED = 2;
	public static final int PREVIOUS = 3;
	public static final int NEXT = 4;
	public static final int REPAIR_FIRST = 10;
	private static final int ROWS = 6;
	private static final float REPAIR_LOSS = 0.08f;

	public record RepairRow(ItemStack item, boolean equipped, int currentAfter, int maxAfter, long price) { }
	private record Repair(@Nullable EquipmentSlotType slot, @Nullable UUID entryId, ItemStack original,
			ItemStack result, long price) {
		RepairRow view() {
			return new RepairRow(original, slot != null, TarkovArmorItem.getRemainingDurability(result),
				TarkovArmorItem.getEffectiveMaxDurability(result), price);
		}
	}
	private record View(int page, int pages, long healPrice, long equippedPrice, boolean hasMeds, List<RepairRow> rows) {
		void write(PacketByteBuf buf) {
			buf.writeVarInt(page);
			buf.writeVarInt(pages);
			buf.writeVarLong(healPrice);
			buf.writeVarLong(equippedPrice);
			buf.writeBoolean(hasMeds);
			buf.writeCollection(rows, (packet, row) -> {
				packet.writeItemStack(row.item());
				packet.writeBoolean(row.equipped());
				packet.writeVarInt(row.currentAfter());
				packet.writeVarInt(row.maxAfter());
				packet.writeVarLong(row.price());
			});
		}

		static View read(PacketByteBuf buf) {
			return new View(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readBoolean(),
				buf.readList(p -> new RepairRow(p.readItemStack(), p.readBoolean(), p.readVarInt(), p.readVarInt(), p.readVarLong())));
		}
	}

	private final View view;
	private final List<Repair> repairs;
	private final List<Repair> equipped;

	private LobbyServiceScreenHandler(int syncId, PlayerEntity owner, View view, List<Repair> repairs, List<Repair> equipped) {
		super(ModScreenHandlers.LOBBY_SERVICE_SCREEN_HANDLER, syncId, owner);
		this.view = view;
		this.repairs = repairs;
		this.equipped = equipped;
	}

	public int page() { return view.page(); }
	public int pages() { return view.pages(); }
	public long healPrice() { return view.healPrice(); }
	public long equippedPrice() { return view.equippedPrice(); }
	public boolean hasMeds() { return view.hasMeds(); }
	public List<RepairRow> rows() { return view.rows(); }

	public static LobbyServiceScreenHandler fromClientBuf(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
		return new LobbyServiceScreenHandler(syncId, inventory.player, View.read(buf), List.of(), List.of());
	}

	public static void open(ServerPlayerEntity player) { open(player, 0); }

	private static void open(ServerPlayerEntity player, int requestedPage) {
		PlayerLoadoutHolder holder = (PlayerLoadoutHolder) player;
		BodyPartHealth health = holder.tarkovcraft$getBodyHealth();
		GridContainer stash = PlayerProfileState.get(player.getServer()).stash(player.getUuid());
		List<Repair> equipped = equippedRepairs(player);
		List<Repair> all = new ArrayList<>(equipped);
		for (GridEntry entry : stash.getEntries()) addRepair(all, null, entry.getEntryId(), entry.getStack());

		int pages = Math.max(1, (all.size() + ROWS - 1) / ROWS);
		int page = Math.max(0, Math.min(pages - 1, requestedPage));
		List<Repair> repairs = all.subList(page * ROWS, Math.min((page + 1) * ROWS, all.size()));
		boolean hasMeds = stash.getEntries().stream().anyMatch(entry -> availableMed(health, entry.getStack()));
		View view = new View(page, pages, healingPrice(health), equipped.stream().mapToLong(Repair::price).sum(),
			hasMeds, repairs.stream().map(Repair::view).toList());
		open(player, LobbyManager.label("tab.service", "Service"), view::write,
			syncId -> new LobbyServiceScreenHandler(syncId, player, view, repairs, equipped));
	}

	static long equippedRepairPrice(ServerPlayerEntity player) {
		return equippedRepairs(player).stream().mapToLong(Repair::price).sum();
	}

	static boolean repairEquipped(ServerPlayerEntity player, long expectedPrice) {
		List<Repair> repairs = equippedRepairs(player);
		return expectedPrice > 0 && repairs.stream().mapToLong(Repair::price).sum() == expectedPrice
			&& repair(player, repairs);
	}

	private static List<Repair> equippedRepairs(ServerPlayerEntity player) {
		var loadout = ((PlayerLoadoutHolder) player).tarkovcraft$getLoadout();
		List<Repair> repairs = new ArrayList<>();
		for (EquipmentSlotType slot : EquipmentSlotType.values())
			addRepair(repairs, slot, null, loadout.getEquipment(slot));
		return repairs;
	}

	private static long healingPrice(BodyPartHealth health) {
		int injuryCount = 0;
		for (InjuryType injury : InjuryType.values()) injuryCount += health.countInjuries(injury);
		return Math.min(5000, Math.max(0, Math.round((BodyPart.TOTAL_MAX_HP - health.getTotalHp()) * 10) + 300 * injuryCount));
	}

	private static boolean availableMed(BodyPartHealth health, ItemStack stack) {
		return stack.getItem() instanceof TarkovMedicalItem med && !med.getMedicalType().isPainkillerOnly()
			&& MedicalHelper.selectAutoTarget(health, stack) != null;
	}

	private static void addRepair(List<Repair> repairs, @Nullable EquipmentSlotType slot, @Nullable UUID id, ItemStack stack) {
		if (!(stack.getItem() instanceof TarkovArmorItem armor) || armor.getArmorClass() <= 0 || !stack.isDamageable()) return;
		ItemStack result = stack.copy();
		int restored = TarkovArmorItem.repair(result, Integer.MAX_VALUE, REPAIR_LOSS);
		if (restored > 0) repairs.add(new Repair(slot, id, stack.copy(), result, (60L + 35L * armor.getArmorClass()) * restored));
	}

	@Override
	protected boolean handleButton(ServerPlayerEntity player, int button) {
		switch (button) {
			case PREVIOUS, NEXT -> {
				open(player, page() + (button == NEXT ? 1 : -1));
				return true;
			}
			case HEAL -> heal(player, false);
			case USE_STASH_MEDS -> heal(player, true);
			case REPAIR_EQUIPPED -> repair(player, equipped);
			default -> {
				int row = button - REPAIR_FIRST;
				if (row < 0 || row >= repairs.size()) return false;
				repair(player, List.of(repairs.get(row)));
			}
		}
		open(player, page());
		return true;
	}

	private void heal(ServerPlayerEntity player, boolean useMeds) {
		PlayerLoadoutHolder holder = (PlayerLoadoutHolder) player;
		BodyPartHealth health = holder.tarkovcraft$getBodyHealth();
		PlayerProfileState profile = PlayerProfileState.get(player.getServer());
		UUID id = player.getUuid();
		if (useMeds) {
			GridContainer stash = profile.stash(id);
			boolean used;
			boolean changed = false;
			do {
				used = false;
				for (GridEntry entry : stash.getEntries()) {
					ItemStack stack = entry.getStack();
					if (!availableMed(health, stack) || !MedicalHelper.applyMedical(health, stack, null, false).applied()) continue;
					used = true;
					if (stack.isEmpty()) stash.remove(entry.getEntryId());
				}
				changed |= used;
			} while (used && health.needsTreatment());
			if (!changed) return;
			profile.markDirty();
		} else {
			long price = healingPrice(health);
			if (price == 0 || price != healPrice() || !pay(player, profile, price)) return;
			for (BodyPart part : BodyPart.values()) {
				health.setEffectiveMaxHp(part, part.getMaxHp());
				health.setHp(part, part.getMaxHp());
				for (InjuryType injury : InjuryType.values()) health.removeInjury(part, injury);
			}
		}
		player.setHealth(Math.max(1, health.getTotalRatio() * player.getMaxHealth()));
		holder.tarkovcraft$syncBodyHealth();
	}

	private static boolean repair(ServerPlayerEntity player, List<Repair> repairs) {
		if (repairs.isEmpty()) return false;
		PlayerLoadoutHolder holder = (PlayerLoadoutHolder) player;
		var loadout = holder.tarkovcraft$getLoadout();
		PlayerProfileState profile = PlayerProfileState.get(player.getServer());
		GridContainer stash = profile.stash(player.getUuid());
		List<ItemStack> targets = new ArrayList<>();
		long price = 0;
		for (Repair repair : repairs) {
			GridEntry entry = repair.entryId() == null ? null : stash.getEntry(repair.entryId());
			ItemStack target = repair.slot() != null ? loadout.getEquipment(repair.slot())
				: entry == null ? ItemStack.EMPTY : entry.getStack();
			if (!ItemStack.areEqual(target, repair.original())) return false;
			targets.add(target);
			price += repair.price();
		}
		if (!pay(player, profile, price)) return false;
		for (int i = 0; i < targets.size(); i++)
			targets.get(i).setNbt(repairs.get(i).result().getNbt().copy());
		holder.tarkovcraft$syncLoadout();
		return true;
	}

	private static boolean pay(ServerPlayerEntity player, PlayerProfileState profile, long price) {
		UUID id = player.getUuid();
		long balance = profile.rubles(id);
		if (price > balance) {
			player.sendMessage(LobbyManager.label("insufficient_rubles", "Insufficient rubles"), true);
			return false;
		}
		profile.setRubles(id, balance - price);
		return true;
	}
}
