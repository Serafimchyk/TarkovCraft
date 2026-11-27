package com.tarkovcraft.arena;

import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

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
