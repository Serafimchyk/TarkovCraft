package com.tarkovcraft.arena;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.lobby.ItemPriceCalculator;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RaidEquipment {
	private RaidEquipment() { }

	static NbtCompound capture(ServerPlayerEntity player) {
		var holder = (PlayerLoadoutHolder) player;
		NbtCompound snapshot = new NbtCompound();
		snapshot.put("Loadout", holder.tarkovcraft$getLoadout().writeNbt());
		snapshot.put("Inventory", player.getInventory().writeNbt(new NbtList()));
		snapshot.putInt("SelectedSlot", player.getInventory().selectedSlot);
		snapshot.put("BodyHealth", holder.tarkovcraft$getBodyHealth().writeNbt());
		snapshot.putFloat("Health", player.getHealth());
		NbtCompound hunger = new NbtCompound();
		player.getHungerManager().writeNbt(hunger);
		snapshot.put("Hunger", hunger);
		snapshot.putInt("Level", player.experienceLevel);
		snapshot.putInt("Experience", player.totalExperience);
		snapshot.putFloat("ExperienceProgress", player.experienceProgress);
		NbtList effects = new NbtList();
		for (StatusEffectInstance effect : player.getStatusEffects()) effects.add(effect.writeNbt(new NbtCompound()));
		snapshot.put("Effects", effects);
		return snapshot;
	}

	static List<ItemStack> rawItems(ServerPlayerEntity player) {
		var inventory = player.getInventory();
		return java.util.stream.Stream.of(inventory.main, inventory.armor, inventory.offHand)
			.flatMap(List::stream).filter(stack -> !stack.isEmpty()).toList();
	}

	record LootKey(Item item, String variant) {
		static LootKey of(ItemStack stack) {
			String variant = "";
			if (AmmoTier.isTaczAmmoStack(stack)) {
				variant = AmmoTier.getAmmoId(stack) + "/" + AmmoTier.getFromAmmoStack(stack).getId();
			} else if (stack.hasNbt() && AmmoTier.isTaczGunStack(stack)) {
				variant = stack.getNbt().getString("GunId");
			} else if (stack.hasNbt()) {
				variant = stack.getNbt().getString("AttachmentId");
			}
			return new LootKey(stack.getItem(), variant);
		}
	}

	static Map<LootKey, Integer> startingItems(ServerPlayerEntity player) {
		Map<LootKey, Integer> counts = new HashMap<>();
		for (ItemStack stack : carriedItems(player)) counts.merge(LootKey.of(stack), stack.getCount(), Integer::sum);
		return counts;
	}

	static long extractedValue(ServerPlayerEntity player, Map<LootKey, Integer> startingItems) {
		long value = 0;
		for (ItemStack stack : carriedItems(player)) {
			LootKey key = LootKey.of(stack);
			int old = Math.min(stack.getCount(), startingItems.getOrDefault(key, 0));
			startingItems.computeIfPresent(key, (ignored, count) -> count - old);
			if (stack.getCount() > old) value += ItemPriceCalculator.sellPrice(stack.copyWithCount(stack.getCount() - old));
		}
		return value;
	}

	private static List<ItemStack> carriedItems(ServerPlayerEntity player) {
		PlayerLoadout loadout = ((PlayerLoadoutHolder) player).tarkovcraft$getLoadout();
		List<ItemStack> items = new ArrayList<>(rawItems(player));
		for (EquipmentSlotType slot : EquipmentSlotType.values()) items.add(loadout.getEquipment(slot));
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) items.add(loadout.getPocket(i));
		loadout.getSecureContainer().getEntries().forEach(entry -> items.add(entry.getStack()));
		items.removeIf(ItemStack::isEmpty);
		for (int i = 0; i < items.size(); i++) {
			// Container readers can initialize NBT. Keep the originals untouched.
			for (var grid : ContainerItemHelper.readAllGrids(items.get(i).copy()))
				grid.getEntries().forEach(entry -> items.add(entry.getStack()));
		}
		return items;
	}

	static void clear(ServerPlayerEntity player, boolean preserveSecure) {
		var holder = (PlayerLoadoutHolder) player;
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		if (preserveSecure) loadout.clearLoot();
		else loadout.clear();
		player.getInventory().clear();
		holder.tarkovcraft$getBodyHealth().resetFull();
		player.setHealth(player.getMaxHealth());
		player.clearStatusEffects();
		player.getHungerManager().setFoodLevel(20);
		player.getHungerManager().setSaturationLevel(5);
	}

	static void restore(ServerPlayerEntity player, NbtCompound snapshot) {
		var holder = (PlayerLoadoutHolder) player;
		holder.tarkovcraft$getLoadout().readNbt(snapshot.getCompound("Loadout"));
		player.getInventory().readNbt(snapshot.getList("Inventory", NbtElement.COMPOUND_TYPE));
		player.getInventory().selectedSlot = snapshot.getInt("SelectedSlot");
		holder.tarkovcraft$getBodyHealth().readNbt(snapshot.getCompound("BodyHealth"));
		player.setHealth(Math.max(1f, snapshot.getFloat("Health")));
		player.getHungerManager().readNbt(snapshot.getCompound("Hunger"));
		player.setExperienceLevel(snapshot.getInt("Level"));
		player.totalExperience = snapshot.getInt("Experience");
		player.experienceProgress = snapshot.getFloat("ExperienceProgress");
		player.clearStatusEffects();
		for (NbtElement tag : snapshot.getList("Effects", NbtElement.COMPOUND_TYPE)) {
			StatusEffectInstance effect = StatusEffectInstance.fromNbt((NbtCompound) tag);
			if (effect != null) player.addStatusEffect(effect);
		}
	}

	static NbtCompound secure(ServerPlayerEntity player) {
		PlayerLoadout secure = new PlayerLoadout();
		secure.copySecureContainerFrom(((PlayerLoadoutHolder) player).tarkovcraft$getLoadout());
		return secure.writeNbt();
	}

	static void sync(ServerPlayerEntity player) {
		var holder = (PlayerLoadoutHolder) player;
		holder.tarkovcraft$syncLoadout();
		holder.tarkovcraft$syncBodyHealth();
		player.playerScreenHandler.syncState();
	}
}
