package com.tarkovcraft.client.renderer;

import com.tarkovcraft.client.model.GearModel;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.item.ModelledItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.network.EquipmentAppearance;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
	void registeredEquipmentHasBakedTexturesForItsDeclaredSilhouettes() throws IOException {
		for (Item item : Registries.ITEM) {
			if (!(item instanceof ModelledItem gear)) continue;
			var appearance = gear.getModelAppearance();
			if (appearance.modelSilhouette() != null) {
				assertNotNull(GearAppearance.worn(item), appearance.modelSilhouette());
				assertTexture(appearance.modelTexture(), "armor");
			}
			if (appearance.groundSilhouette() != null) {
				assertNotNull(GearAppearance.ground(new ItemStack(item)), appearance.groundSilhouette());
				assertTexture(appearance.modelTexture(), "ground");
			}
		}
		assertEquals(GearModel.BACKPACK_SMALL, GearAppearance.worn(ModItems.BERKUT_BACKPACK));
		assertEquals(GearModel.HELMET_VISOR, GearAppearance.worn(ModItems.ALTYN_HELMET));
	}

	@Test
	void trackedAppearanceClearsLootedEquipment() {
		PlayerLoadout loadout = new PlayerLoadout();
		loadout.setEquipment(EquipmentSlotType.BACKPACK, new ItemStack(ModItems.BERKUT_BACKPACK));
		loadout.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.ALTYN_HELMET));
		EquipmentAppearance before = EquipmentAppearance.capture(loadout);
		assertEquals(before, EquipmentAppearance.fromTrackedNbt(before.toTrackedNbt()));

		loadout.setEquipment(EquipmentSlotType.BACKPACK, ItemStack.EMPTY);
		EquipmentAppearance after = EquipmentAppearance.fromTrackedNbt(EquipmentAppearance.capture(loadout).toTrackedNbt());
		assertEquals(Items.AIR, after.backpack());
		assertEquals(ModItems.ALTYN_HELMET, after.helmet());
	}

	private static void assertTexture(Identifier texture, String folder) throws IOException {
		assertNotNull(texture);
		String path = "/assets/" + texture.getNamespace() + "/textures/models/" + folder + "/" + texture.getPath() + ".png";
		try (var input = GearAppearanceTest.class.getResourceAsStream(path)) {
			assertNotNull(input, path);
			var image = ImageIO.read(input);
			assertEquals(64, image.getWidth(), path);
			assertEquals(64, image.getHeight(), path);
		}
	}

	@Test
	void soundMixinsApplyWithVoiceModsAbsent() throws ClassNotFoundException {
		ClassLoader knot = Thread.currentThread().getContextClassLoader();
		Class.forName("net.minecraft.client.sound.SoundSystem", false, knot);
		Class.forName("net.minecraft.server.PlayerManager", false, knot);
		Class.forName("net.minecraft.server.world.ServerWorld", false, knot);
	}

	private static Item catalogItem(String path) {
		return Registries.ITEM.get(new Identifier("tarkovcraft", path));
	}
}
