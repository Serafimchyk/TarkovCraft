package com.tarkovcraft.client.renderer;

import com.tarkovcraft.client.model.GearModel;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GearAppearanceTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.createGameVersion();
		Bootstrap.initialize();
		ModItems.init();
	}

	@Test
	void eyeProtectionDoesNotAddAVisorToTheRysHelmet() {
		Item helmet = catalogItem("broneshlem_rys_t_chernyy_5f60c7");
		TarkovArmorItem armor = assertInstanceOf(TarkovArmorItem.class, helmet);
		assertTrue(armor.coversZone(HitZone.HEAD_EYES));
		assertEquals(GearModel.HELMET_LOWCUT, GearAppearance.worn(helmet));
		assertEquals(GearModel.GROUND_HELMET_OPEN, GearAppearance.ground(new ItemStack(helmet)));
	}

	@Test
	void separateVisorUsesItsOwnGeometryInBothRenderers() {
		Item visor = catalogItem("vizor_dlya_shlemov_ops_core_fast_5a16b6");
		assertEquals(GearModel.VISOR, GearAppearance.worn(visor));
		assertEquals(GearModel.VISOR, GearAppearance.ground(new ItemStack(visor)));
	}

	@Test
	void soundMixinsApplyWithVoiceModsAbsent() throws ClassNotFoundException {
		ClassLoader knot = Thread.currentThread().getContextClassLoader();
		Class.forName("net.minecraft.client.sound.SoundSystem", false, knot);
		Class.forName("net.minecraft.server.PlayerManager", false, knot);
	}

	private static Item catalogItem(String path) {
		return Registries.ITEM.get(new Identifier("tarkovcraft", path));
	}
}
