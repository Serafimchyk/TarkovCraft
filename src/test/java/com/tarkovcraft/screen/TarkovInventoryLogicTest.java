package com.tarkovcraft.screen;

import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TarkovInventoryLogicTest {

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
	}

	@Test
	void isConsumableItemIdentifiesFoodAndDrinksOnly() {
		assertTrue(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.BREAD)));
		assertTrue(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.GOLDEN_APPLE)));
		assertTrue(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.POTION)));
		assertTrue(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.MILK_BUCKET)));
		assertTrue(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.HONEY_BOTTLE)));

		assertFalse(TarkovScreenHandler.isConsumableItem(new ItemStack(ModItems.PACA_ARMOR)));
		assertFalse(TarkovScreenHandler.isConsumableItem(new ItemStack(ModItems.BERKUT_BACKPACK)));
		assertFalse(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.IRON_SWORD)));
		assertFalse(TarkovScreenHandler.isConsumableItem(new ItemStack(Items.COBBLESTONE)));
		assertFalse(TarkovScreenHandler.isConsumableItem(ItemStack.EMPTY));
	}

	@Test
	void canEquipItemMatchesExpectedSlots() {
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(ModItems.PACA_ARMOR)));
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(ModItems.SSH68_HELMET)));
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(ModItems.BALACLAVA_MASK)));
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(ModItems.BERKUT_BACKPACK)));
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(ModItems.SCAV_VEST)));
		assertTrue(TarkovScreenHandler.canEquipItem(new ItemStack(Items.IRON_SWORD)));

		// Vanilla armor remains rejected
		assertFalse(TarkovScreenHandler.canEquipItem(new ItemStack(Items.DIAMOND_HELMET)));
		assertFalse(TarkovScreenHandler.canEquipItem(new ItemStack(Items.NETHERITE_CHESTPLATE)));
		assertFalse(TarkovScreenHandler.canEquipItem(new ItemStack(Items.BREAD)));
		assertFalse(TarkovScreenHandler.canEquipItem(ItemStack.EMPTY));
	}
}
