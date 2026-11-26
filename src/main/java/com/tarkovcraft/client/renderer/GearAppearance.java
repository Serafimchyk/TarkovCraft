package com.tarkovcraft.client.renderer;

import com.tarkovcraft.client.model.GearModel;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.MedicalType;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.item.TarkovFunctionalItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.Nullable;

/** Selects a model from the pack for each supported equipment family. */
public final class GearAppearance {
	private GearAppearance() {
	}

	@Nullable
	public static GearModel worn(Item item) {
		if (item instanceof BackpackItem backpack) {
			return backpack.getGridWidth() * backpack.getGridHeight() >= 24
				? GearModel.BACKPACK_LARGE : GearModel.BACKPACK_SMALL;
		}

		if (item instanceof TacticalRigItem) {
			String path = Registries.ITEM.getId(item).getPath();
			if (path.startsWith("remenno_plechevaya_") || path.equals("bank_robber_rig")) {
				return GearModel.RIG_BELT_RPS;
			}
			if (path.startsWith("chekhol_dlya_broneplit_")) {
				return GearModel.RIG_ARMORED;
			}
			return path.equals("scav_vest") || path.startsWith("zhilet_okhrannika_") || path.startsWith("okhotnichiy_zhilet_")
				? GearModel.RIG_SCAV_VEST : GearModel.RIG_CHEST_HEAVY;
		}
		if (item instanceof TarkovArmorItem armor) {
			String path = Registries.ITEM.getId(item).getPath();
			return switch (armor.getTargetSlot()) {
				case HELMET -> helmet(path, armor);
				case FACE_MASK -> faceMask(path, armor);
				case BODY_ARMOR -> bodyArmor(path, armor);
				default -> null;
			};
		}
		if (item instanceof TarkovFunctionalItem functional && functional.isHeadset()) {
			return GearModel.HEADSET_ACTIVE;
		}
		return null;
	}

	private static GearModel helmet(String path, TarkovArmorItem armor) {
		if (path.contains("tagilly")) return GearModel.MASK_TAGILLA;
		if (path.contains("ronin")) return GearModel.HELMET_RONIN;
		if (path.contains("ushanka") || path.contains("shapka")) return GearModel.USHANKA;
		if (path.contains("kepka") || path.startsWith("panama_") || path.startsWith("beret_")) {
			return GearModel.TACTICAL_CAP;
		}
		// Hit zones describe protection. They do not equip a visor on an open shell.
		return armor.coversZone(HitZone.HEAD_EARS) || path.equals("ssh68_helmet") || path.startsWith("broneshlem_altyn_")
			? GearModel.HELMET_LOWCUT : GearModel.HELMET_HIGHCUT;
	}

	private static GearModel faceMask(String path, TarkovArmorItem armor) {
		if (path.startsWith("vizor_")) return GearModel.VISOR;
		if (path.contains("ochki")) return GearModel.TACTICAL_GOGGLES;
		if (armor.isGasProtection() || path.startsWith("respirator")) return GearModel.GAS_MASK;
		if (path.contains("tagilly") || path.contains("ten_smer") || path.equals("death_shadow_mask")) {
			return GearModel.MASK_TAGILLA;
		}
		return GearModel.BALACLAVA;
	}

	private static GearModel bodyArmor(String path, TarkovArmorItem armor) {
		if (path.contains("slick") || path.contains("paca") || path.contains("hexgrid")) {
			return GearModel.ARMOR_SLICK;
		}
		return armor.getCoveredZones().contains(HitZone.NECK) || path.equals("zabralo_armor")
			? GearModel.ARMOR_HEAVY : GearModel.ARMOR_MEDIUM;
	}

	@Nullable
	public static GearModel ground(ItemStack stack) {
		if (stack.isEmpty()) return null;
		Item item = stack.getItem();
		if ((item instanceof TarkovMedicalItem medical && medical.getMedicalType().getCapacityMode() == MedicalType.CapacityMode.HP_POOL)
			|| (item instanceof TarkovCaseItem storage && storage.getCaseRole() == TarkovCaseItem.CaseRole.MEDICAL)) {
			return GearModel.GROUND_CASE_MEDKIT;
		}
		GearModel worn = worn(item);
		if (worn == null) return null;
		return switch (worn) {
			case BACKPACK_SMALL -> GearModel.GROUND_BACKPACK_SMALL;
			case BACKPACK_LARGE -> GearModel.GROUND_BACKPACK_LARGE;
			case ARMOR_HEAVY, ARMOR_MEDIUM, ARMOR_SLICK -> GearModel.GROUND_ARMOR;
			case RIG_ARMORED, RIG_BELT_RPS, RIG_CHEST_HEAVY, RIG_SCAV_VEST -> GearModel.GROUND_RIG;
			case HELMET_HIGHCUT, HELMET_LOWCUT -> GearModel.GROUND_HELMET_OPEN;
			case HELMET_RONIN, HELMET_VISOR -> GearModel.GROUND_HELMET_VISOR;
			case MASK_TAGILLA -> GearModel.GROUND_MASK_TAGILLA;
			default -> worn;
		};
	}
}
