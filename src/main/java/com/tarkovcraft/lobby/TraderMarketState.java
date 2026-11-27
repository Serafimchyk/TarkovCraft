package com.tarkovcraft.lobby;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

public final class TraderMarketState extends PersistentState {
	private static final long CYCLE_MS = 45 * 60 * 1000L;
	private final Map<String, Demand> goods = new HashMap<>();
	private long cycle = -1;

	private static final class Demand {
		double multiplier = 1;
		int idle;
		boolean purchased;
	}

	public static TraderMarketState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(
			TraderMarketState::read, TraderMarketState::new, "tarkovcraft_trader_market");
	}

	public void refresh(long now, Iterable<String> catalogue) {
		advance(now / CYCLE_MS);
		for (String key : catalogue) {
			if (!goods.containsKey(key)) {
				goods.put(key, new Demand());
				markDirty();
			}
		}
	}

	private void advance(long current) {
		if (cycle < 0) {
			cycle = current;
			markDirty();
		}
		if (current <= cycle) return;
		// Demand reaches the same displayed price after 128 cycles, even from the maximum markup.
		long elapsed = Math.min(128, current - cycle);
		for (Demand demand : goods.values()) {
			for (int i = 0; i < elapsed; i++) {
				demand.idle = demand.purchased ? 0 : Math.min(2, demand.idle + 1);
				demand.purchased = false;
				if (demand.multiplier > 1) {
					demand.multiplier = 1 + (demand.multiplier - 1) * 0.85;
					if (demand.multiplier < 1.000001) demand.multiplier = 1;
				} else if (demand.idle >= 2) {
					demand.multiplier = Math.max(0.80, demand.multiplier - 0.03);
				}
			}
		}
		cycle = current;
		markDirty();
	}

	public double multiplier(String key) {
		Demand demand = goods.get(key);
		return demand == null ? 1 : demand.multiplier;
	}

	public void purchase(String key, double increase) {
		Demand demand = goods.computeIfAbsent(key, ignored -> new Demand());
		demand.multiplier = Math.min(1.80, demand.multiplier + increase);
		demand.purchased = true;
		markDirty();
	}

	static TraderMarketState read(NbtCompound nbt) {
		TraderMarketState state = new TraderMarketState();
		if (nbt.contains("Cycle", NbtElement.NUMBER_TYPE)) state.cycle = nbt.getLong("Cycle");
		NbtCompound items = nbt.getCompound("Goods");
		for (String key : items.getKeys()) {
			NbtCompound item = items.getCompound(key);
			Demand demand = new Demand();
			demand.multiplier = Math.max(0.80, Math.min(1.80, item.getDouble("Multiplier")));
			demand.idle = item.getInt("Idle");
			demand.purchased = item.getBoolean("Purchased");
			state.goods.put(key, demand);
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putLong("Cycle", cycle);
		NbtCompound items = new NbtCompound();
		goods.forEach((key, demand) -> {
			NbtCompound item = new NbtCompound();
			item.putDouble("Multiplier", demand.multiplier);
			item.putInt("Idle", demand.idle);
			item.putBoolean("Purchased", demand.purchased);
			items.put(key, item);
		});
		nbt.put("Goods", items);
		return nbt;
	}
}
