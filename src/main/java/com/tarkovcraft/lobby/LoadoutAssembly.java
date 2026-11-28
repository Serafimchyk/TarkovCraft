package com.tarkovcraft.lobby;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.arena.PlayerProfileState;
import com.tarkovcraft.arena.PlayerProfileState.KitPreset;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.tarkovcraft.lobby.TraderCatalogue.Offer;

public final class LoadoutAssembly {
	public record Slot(int index) {
		public static final int EQUIPPED = EquipmentSlotType.values().length;
		public static final int SECURE_FIRST = EQUIPPED + PlayerLoadout.POCKET_COUNT;
		public static final int COUNT = SECURE_FIRST + 4;

		public EquipmentSlotType equipment() {
			return index < EQUIPPED ? EquipmentSlotType.values()[index] : null;
		}

		public int pocket() { return index - EQUIPPED; }
		public boolean secure() { return index >= SECURE_FIRST; }
		public int x() { return (index - SECURE_FIRST) % 2; }
		public int y() { return (index - SECURE_FIRST) / 2; }
	}

	public record Quote(int missing, long price, boolean blocked) { }


	private record Plan(GridContainer stash, Map<EquipmentSlotType, ItemStack> equipment, Quote quote) { }

	private final ServerPlayerEntity player;
	private final UUID id;
	private final PlayerProfileState profiles;
	private final PlayerLoadout loadout;
	private final List<Offer> offers;

	public LoadoutAssembly(ServerPlayerEntity player) {
		this.player = player;
		id = player.getUuid();
		profiles = PlayerProfileState.get(player.getServer());
		loadout = ((PlayerLoadoutHolder) player).tarkovcraft$getLoadout();
		offers = TraderCatalogue.offers();
	}

	public GridContainer stash() { return profiles.stash(id); }
	public PlayerLoadout loadout() { return loadout; }
	public List<Offer> offers() { return offers; }

	public long price(Offer offer, int packs) {
		return Math.multiplyExact(ItemPriceCalculator.buyPrice(offer.stack()), packs);
	}

	public boolean accepts(Slot slot, ItemStack stack) {
		if (slot.equipment() != null) return slot.equipment().accepts(stack);
		if (!slot.secure()) return loadout.canPlaceInPocket(stack);
		GridContainer secure = loadout.getSecureContainer();
		GridEntry old = secure.getAt(slot.x(), slot.y());
		int x = old == null ? slot.x() : old.getX();
		int y = old == null ? slot.y() : old.getY();
		return PlayerLoadout.canPlaceInSecureContainer(stack)
			&& secure.canPlace(stack, x, y, false, old == null ? null : old.getEntryId());
	}

	public boolean canSwap(Slot slot, ItemStack stack) {
		return slot.equipment() != null ? loadout.canEquip(slot.equipment(), stack) : accepts(slot, stack);
	}

	public boolean equip(Slot slot, UUID entryId, ItemStack expected, Offer offer, long quotedPrice) {
		GridContainer next = stash().copy();
		GridEntry entry = entryId == null ? null : next.remove(entryId);
		ItemStack replacement = offer == null ? entry == null ? ItemStack.EMPTY : entry.getStack() : offer.stack();
		long price = offer == null ? 0 : price(offer, 1);
		if (replacement.isEmpty() || !ItemStack.areEqual(replacement, expected) || !canSwap(slot, replacement)
			|| price != quotedPrice || price > profiles.rubles(id)) return false;

		EquipmentSlotType equipped = slot.equipment();
		GridEntry inSecure = slot.secure() ? loadout.getSecureContainer().getAt(slot.x(), slot.y()) : null;
		ItemStack old = equipped != null ? loadout.getEquipment(equipped)
			: slot.secure() ? inSecure == null ? ItemStack.EMPTY : inSecure.getStack() : loadout.getPocket(slot.pocket());
		if (!old.isEmpty() && !next.tryAutoPlace(old.copy())) return false;

		if (equipped != null) {
			loadout.setEquipment(equipped, replacement.copy());
		} else if (!slot.secure()) {
			if (!loadout.setPocket(slot.pocket(), replacement.copy())) return false;
		} else {
			PlayerLoadout preview = new PlayerLoadout();
			preview.copySecureContainerFrom(loadout);
			GridContainer secure = preview.getSecureContainer();
			int x = slot.x(), y = slot.y();
			if (inSecure != null) {
				secure.remove(inSecure.getEntryId());
				x = inSecure.getX();
				y = inSecure.getY();
			}
			if (secure.place(replacement, x, y, false) == null) return false;
			loadout.copySecureContainerFrom(preview);
		}
		profiles.replaceStash(id, next);
		if (offer != null) pay(price);
		sync();
		return true;
	}

	public void save(int preset) {
		NbtCompound equipment = new NbtCompound();
		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			ItemStack stack = loadout.getEquipment(slot);
			if (stack.isEmpty()) continue;
			ItemStack identity = new ItemStack(stack.getItem());
			if (AmmoTier.isTaczGunStack(stack)) {
				identity.getOrCreateNbt().putString("GunId", variant(stack, "GunId"));
			} else if (AmmoTier.isTaczAmmoStack(stack)) {
				identity.getOrCreateNbt().putString("AmmoId", AmmoTier.getAmmoId(stack));
				AmmoTier.setOnAmmoStack(identity, AmmoTier.getFromAmmoStack(stack));
			}
			equipment.put(slot.getId(), identity.writeNbt(new NbtCompound()));
		}
		equipment.putBoolean("Saved", true);
		profiles.setPreset(id, preset, new KitPreset(equipment));
	}

	public boolean saved(int preset) {
		return profiles.preset(id, preset).equipment().getBoolean("Saved");
	}

	public Quote quote(int preset) { return plan(preset).quote(); }

	public boolean assemble(int preset, Quote expected) {
		Plan plan = plan(preset);
		Quote quote = plan.quote();
		if (quote.blocked() || !quote.equals(expected) || quote.price() > profiles.rubles(id)) return false;
		profiles.replaceStash(id, plan.stash());
		plan.equipment().forEach(loadout::setEquipment);
		profiles.setRubles(id, profiles.rubles(id) - quote.price());
		sync();
		return true;
	}

	private Plan plan(int preset) {
		NbtCompound wanted = profiles.preset(id, preset).equipment();
		GridContainer next = stash().copy();
		Map<EquipmentSlotType, ItemStack> equipment = new EnumMap<>(EquipmentSlotType.class);
		List<ItemStack> displaced = new ArrayList<>();
		boolean blocked = !wanted.getBoolean("Saved");
		long price = 0;
		int missing = 0;

		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			ItemStack target = ItemStack.fromNbt(wanted.getCompound(slot.getId()));
			ItemStack current = loadout.getEquipment(slot);
			if (matches(current, target)) {
				equipment.put(slot, current);
			} else {
				equipment.put(slot, ItemStack.EMPTY);
				if (!current.isEmpty()) displaced.add(current.copy());
			}
		}
		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			if (!equipment.get(slot).isEmpty()) continue;
			ItemStack target = ItemStack.fromNbt(wanted.getCompound(slot.getId()));
			if (target.isEmpty()) continue;
			GridEntry entry = next.getEntries().stream().filter(candidate -> matches(candidate.getStack(), target))
				.findFirst().orElse(null);
			if (entry != null) {
				equipment.put(slot, next.remove(entry.getEntryId()).getStack());
				continue;
			}
			ItemStack reuse = displaced.stream().filter(stack -> matches(stack, target)).findFirst().orElse(null);
			if (reuse != null) {
				displaced.remove(reuse);
				equipment.put(slot, reuse);
				continue;
			}
			missing++;
			Offer offer = offers.stream().filter(candidate -> matches(candidate.stack(), target)).findFirst().orElse(null);
			if (offer == null) {
				blocked = true;
			} else {
				equipment.put(slot, offer.stack().copy());
				price += price(offer, 1);
			}
		}
		PlayerLoadout assembled = new PlayerLoadout();
		equipment.forEach(assembled::setEquipment);
		for (var entry : equipment.entrySet()) blocked |= !assembled.canEquip(entry.getKey(), entry.getValue());
		for (ItemStack stack : displaced) blocked |= !next.tryAutoPlace(stack);
		return new Plan(next, equipment, new Quote(missing, price, blocked));
	}

	private static boolean matches(ItemStack candidate, ItemStack target) {
		if (candidate.isEmpty() || target.isEmpty()) return candidate.isEmpty() && target.isEmpty();
		if (candidate.getItem() != target.getItem()) return false;
		if (AmmoTier.isTaczGunStack(candidate)) return variant(candidate, "GunId").equals(variant(target, "GunId"));
		return !AmmoTier.isTaczAmmoStack(candidate) || AmmoTier.getAmmoId(candidate).equals(AmmoTier.getAmmoId(target))
			&& AmmoTier.getFromAmmoStack(candidate) == AmmoTier.getFromAmmoStack(target);
	}

	private static String variant(ItemStack stack, String key) {
		return stack.hasNbt() ? stack.getNbt().getString(key) : "";
	}

	public boolean buyAmmo(Offer offer, int packs, long quotedPrice) {
		long price = price(offer, packs);
		if (price != quotedPrice || price > profiles.rubles(id)) return false;
		PlayerLoadout next = loadout.copy();
		ItemStack rig = next.getEquipment(EquipmentSlotType.RIG);
		boolean rigAccessible = !ContainerItemHelper.requiresSearch(rig)
			|| ContainerItemHelper.getSearchMemory(rig).isGridRevealed(id);
		List<GridContainer> grids = rigAccessible ? ContainerItemHelper.readRigGrids(rig) : List.of();
		for (int i = 0; i < packs; i++) {
			ItemStack ammo = offer.stack().copy();
			for (GridContainer grid : grids) {
				if (grid.tryAutoPlace(ammo)) break;
			}
			if (!ammo.isEmpty() && (!PlayerLoadout.canPlaceInSecureContainer(ammo)
				|| !next.getSecureContainer().tryAutoPlace(ammo))) return false;
		}
		if (!grids.isEmpty()) {
			ContainerItemHelper.writeRigGrids(rig, grids);
			loadout.setEquipment(EquipmentSlotType.RIG, rig);
		}
		loadout.copySecureContainerFrom(next);
		pay(price);
		sync();
		return true;
	}

	private void pay(long price) {
		profiles.setRubles(id, profiles.rubles(id) - price);
	}

	private void sync() {
		((PlayerLoadoutHolder) player).tarkovcraft$syncLoadout();
		player.playerScreenHandler.syncState();
	}
}
