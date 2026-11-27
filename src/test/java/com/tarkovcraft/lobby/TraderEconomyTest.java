package com.tarkovcraft.lobby;

import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraderEconomyTest {
	@BeforeAll
	static void initializeItems() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModBlocks.init();
		ModItems.init();
	}

	@Test
	void ragmanStocksAllEquipmentCategoriesAndPraporStocksGrenades() {
		var offers = TraderCatalogue.offers();
		var ragman = offers.stream().filter(offer -> offer.trader() == TraderCatalogue.Trader.RAGMAN)
			.map(TraderCatalogue.Offer::category).collect(Collectors.toSet());
		assertEquals(Set.copyOf(TraderCatalogue.Trader.RAGMAN.categories()), ragman);
		assertTrue(offers.stream().anyMatch(offer -> offer.trader() == TraderCatalogue.Trader.PRAPOR
			&& offer.category() == TraderCatalogue.Category.GRENADES));
	}

	@Test
	void tarkovDevPriceAndWearApplyToTradableArmor() {
		ItemStack fresh = new ItemStack(ModItems.PACA_ARMOR);
		long freshResale = ItemPriceCalculator.sellPrice(fresh);
		var source = TarkovDevPrices.forStack(fresh);
		assertEquals("5648a7494bdc2d9d488b4583", source.eftId());
		assertEquals(TarkovDevPrices.Source.TRADER, source.source());
		assertEquals(source.unitPrice(), ItemPriceCalculator.buyPrice(fresh));
		assertTrue(ItemPriceCalculator.buyPrice(fresh) > freshResale);

		ItemStack worn = fresh.copy();
		worn.setDamage(worn.getMaxDamage() / 2);
		TarkovArmorItem.repair(worn, Integer.MAX_VALUE, 0.08f);
		assertTrue(TarkovArmorItem.getEffectiveMaxDurability(worn) < worn.getMaxDamage());
		assertTrue(ItemPriceCalculator.sellPrice(worn) < freshResale);
		assertTrue(ItemPriceCalculator.buyPrice(worn) > ItemPriceCalculator.sellPrice(worn));
	}
}
