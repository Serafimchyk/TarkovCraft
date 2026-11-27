package com.tarkovcraft.lobby;

import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraderEconomyTest {
	private static final long CYCLE = 45 * 60 * 1000L;

	@BeforeAll
	static void initializeItems() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModBlocks.init();
		ModItems.init();
	}

	@Test
	void demandSurvivesReloadAndSettlesWithinAsymmetricBounds() {
		TraderMarketState market = new TraderMarketState();
		market.refresh(0, List.of("popular", "idle"));
		for (int i = 0; i < 50; i++) market.purchase("popular", 0.03);
		assertEquals(1.80, market.multiplier("popular"), 0.00001);
		market.refresh(CYCLE, List.of("popular", "idle"));
		assertEquals(1.68, market.multiplier("popular"), 0.00001);
		assertEquals(1.00, market.multiplier("idle"), 0.00001);

		TraderMarketState restored = TraderMarketState.read(market.writeNbt(new NbtCompound()));
		restored.refresh(2 * CYCLE, List.of("popular", "idle", "new"));
		assertEquals(1.578, restored.multiplier("popular"), 0.00001);
		assertEquals(0.97, restored.multiplier("idle"), 0.00001);
		assertEquals(1.00, restored.multiplier("new"), 0.00001);
		restored.refresh(200 * CYCLE, List.of("popular", "idle", "new"));
		assertEquals(0.80, restored.multiplier("popular"), 0.00001);
		assertEquals(0.80, restored.multiplier("idle"), 0.00001);
	}

	@Test
	void evenDiscountedGearCostsMoreThanItsResaleAndWearReducesResale() {
		ItemStack fresh = new ItemStack(ModItems.PACA_ARMOR);
		long freshResale = ItemPriceCalculator.sellPrice(fresh);
		assertTrue(ItemPriceCalculator.buyPrice(fresh, 0.80) > freshResale);

		ItemStack worn = fresh.copy();
		worn.setDamage(worn.getMaxDamage() / 2);
		TarkovArmorItem.repair(worn, Integer.MAX_VALUE, 0.08f);
		assertTrue(TarkovArmorItem.getEffectiveMaxDurability(worn) < worn.getMaxDamage());
		assertTrue(ItemPriceCalculator.sellPrice(worn) < freshResale);
		assertTrue(ItemPriceCalculator.buyPrice(worn, 0.80) > ItemPriceCalculator.sellPrice(worn));
	}
}
