package com.tarkovcraft.registry;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.item.ArmorMaterialType;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.MedicalType;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.item.TarkovMedicalItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.EnumSet;
import java.util.List;

public final class ModItems {
	// Backpacks
	public static final BackpackItem SLING_BACKPACK = register("sling_backpack",
		new BackpackItem(new FabricItemSettings(), 2, 3, new ItemSize(2, 3)));
	public static final BackpackItem BERKUT_BACKPACK = register("berkut_backpack",
		new BackpackItem(new FabricItemSettings(), 4, 5, new ItemSize(4, 5)));
	public static final BackpackItem TRIZIP_BACKPACK = register("trizip_backpack",
		new BackpackItem(new FabricItemSettings(), 5, 6, new ItemSize(5, 6)));

	// Tactical Rigs
	public static final TacticalRigItem SCAV_VEST = register("scav_vest",
		new TacticalRigItem(new FabricItemSettings(),
			List.of(new ItemSize(1, 2), new ItemSize(1, 2), new ItemSize(1, 1), new ItemSize(1, 1)),
			new ItemSize(2, 2)));
	public static final TacticalRigItem BANK_ROBBER_RIG = register("bank_robber_rig",
		new TacticalRigItem(new FabricItemSettings(),
			List.of(new ItemSize(1, 2), new ItemSize(1, 2), new ItemSize(1, 2), new ItemSize(1, 2)),
			new ItemSize(3, 2)));
	public static final TacticalRigItem TRITON_RIG = register("triton_rig",
		new TacticalRigItem(new FabricItemSettings(),
			List.of(
				new ItemSize(1, 2), new ItemSize(1, 3), new ItemSize(1, 3), new ItemSize(1, 2),
				new ItemSize(1, 1), new ItemSize(1, 1), new ItemSize(1, 1), new ItemSize(1, 1)
			),
			new ItemSize(3, 3)));

	// Zonal Helmets
	// SSh-68: Class 3, covers ONLY HEAD_TOP (ears, eyes, jaws remain EXPOSED!)
	public static final TarkovArmorItem SSH68_HELMET = register("ssh68_helmet",
		new TarkovArmorItem(new FabricItemSettings(), EquipmentSlotType.HELMET, new ItemSize(2, 2), 3, 30,
			ArmorMaterialType.STEEL, 0.45f, 1.3f, EnumSet.of(HitZone.HEAD_TOP)));
	// 6B47 Ratnik: Class 3, covers HEAD_TOP + HEAD_EARS (eyes & jaws remain EXPOSED!)
	public static final TarkovArmorItem RATNIK_6B47_HELMET = register("ratnik_6b47_helmet",
		new TarkovArmorItem(new FabricItemSettings(), EquipmentSlotType.HELMET, new ItemSize(2, 2), 3, 35,
			ArmorMaterialType.ARAMID, 0.30f, 1.25f, EnumSet.of(HitZone.HEAD_TOP, HitZone.HEAD_EARS)));
	// Altyn Visor Helmet: Class 5, covers HEAD_TOP + HEAD_EARS + HEAD_EYES + HEAD_JAWS (stops head-eyes oneshot!)
	public static final TarkovArmorItem ALTYN_HELMET = register("altyn_helmet",
		new TarkovArmorItem(new FabricItemSettings(), EquipmentSlotType.HELMET, new ItemSize(2, 2), 5, 55,
			ArmorMaterialType.TITANIUM, 0.40f, 4.1f,
			EnumSet.of(HitZone.HEAD_TOP, HitZone.HEAD_EARS, HitZone.HEAD_EYES, HitZone.HEAD_JAWS)));

	// Face Masks
	// Balaclava: cosmetic (Class 0, zero ballistic protection)
	public static final TarkovArmorItem BALACLAVA_MASK = register("balaclava_mask",
		new TarkovArmorItem(new FabricItemSettings(), EquipmentSlotType.FACE_MASK, new ItemSize(1, 1), 0, 0,
			ArmorMaterialType.COSMETIC, 0.0f, 0.1f, EnumSet.noneOf(HitZone.class)));
	// Death Shadow Lightweight Mask: Class 2, covers HEAD_EYES + HEAD_JAWS
	public static final TarkovArmorItem DEATH_SHADOW_MASK = register("death_shadow_mask",
		new TarkovArmorItem(new FabricItemSettings(), EquipmentSlotType.FACE_MASK, new ItemSize(2, 2), 2, 25,
			ArmorMaterialType.ARAMID, 0.10f, 0.4f, EnumSet.of(HitZone.HEAD_EYES, HitZone.HEAD_JAWS)));

	// Body Armor Vests
	// PACA: Class 2, Thorax + Stomach (Neck & Armpits EXPOSED!)
	public static final TarkovArmorItem PACA_ARMOR = register("paca_armor",
		new TarkovArmorItem(new FabricItemSettings(), EquipmentSlotType.BODY_ARMOR, new ItemSize(3, 3), 2, 50,
			ArmorMaterialType.ARAMID, 0.0f, 2.4f, EnumSet.of(HitZone.THORAX, HitZone.STOMACH)));
	// Zhuk-3 Press: Class 3, Thorax + Stomach (Neck & Armpits EXPOSED!)
	public static final TarkovArmorItem PRESS_ARMOR = register("press_armor",
		new TarkovArmorItem(new FabricItemSettings(), EquipmentSlotType.BODY_ARMOR, new ItemSize(3, 3), 3, 60,
			ArmorMaterialType.UHMWPE, 0.0f, 5.0f, EnumSet.of(HitZone.THORAX, HitZone.STOMACH)));
	// 6B43 Zabralo: Class 6 Heavy Armor, covers Thorax + Stomach + Neck + Left/Right Armpits!
	public static final TarkovArmorItem ZABRALO_ARMOR = register("zabralo_armor",
		new TarkovArmorItem(new FabricItemSettings(), EquipmentSlotType.BODY_ARMOR, new ItemSize(3, 4), 6, 95,
			ArmorMaterialType.COMBINED, 0.0f, 14.5f,
			EnumSet.of(HitZone.THORAX, HitZone.STOMACH, HitZone.NECK, HitZone.ARMPIT_LEFT, HitZone.ARMPIT_RIGHT)));

	// EFT Medical Items
	public static final TarkovMedicalItem BANDAGE = register("bandage",
		new TarkovMedicalItem(new FabricItemSettings(), MedicalType.BANDAGE));
	public static final TarkovMedicalItem CAT_TOURNIQUET = register("cat_tourniquet",
		new TarkovMedicalItem(new FabricItemSettings(), MedicalType.CAT_TOURNIQUET));
	public static final TarkovMedicalItem SPLINT = register("splint",
		new TarkovMedicalItem(new FabricItemSettings(), MedicalType.SPLINT));
	public static final TarkovMedicalItem ALU_SPLINT = register("alu_splint",
		new TarkovMedicalItem(new FabricItemSettings(), MedicalType.ALU_SPLINT));
	public static final TarkovMedicalItem ANALGIN = register("analgin",
		new TarkovMedicalItem(new FabricItemSettings(), MedicalType.ANALGIN));
	public static final TarkovMedicalItem AI2_MEDKIT = register("ai2_medkit",
		new TarkovMedicalItem(new FabricItemSettings(), MedicalType.AI2_MEDKIT));
	public static final TarkovMedicalItem SALEWA_MEDKIT = register("salewa_medkit",
		new TarkovMedicalItem(new FabricItemSettings(), MedicalType.SALEWA_MEDKIT));
	public static final TarkovMedicalItem CMS_KIT = register("cms_kit",
		new TarkovMedicalItem(new FabricItemSettings(), MedicalType.CMS_KIT));
	public static final TarkovMedicalItem SURV12_KIT = register("surv12_kit",
		new TarkovMedicalItem(new FabricItemSettings(), MedicalType.SURV12_KIT));

	public static final ItemGroup TARKOV_ARMOR_GROUP = Registry.register(
		Registries.ITEM_GROUP,
		new Identifier(TarkovCraftMod.MOD_ID, "tarkov_armor_tab"),
		FabricItemGroup.builder()
			.icon(() -> new ItemStack(ALTYN_HELMET))
			.displayName(Text.translatable("itemGroup.tarkovcraft.tarkov_armor_tab"))
			.entries((displayContext, entries) -> {
				entries.add(SSH68_HELMET);
				entries.add(RATNIK_6B47_HELMET);
				entries.add(ALTYN_HELMET);
				entries.add(BALACLAVA_MASK);
				entries.add(DEATH_SHADOW_MASK);
				entries.add(PACA_ARMOR);
				entries.add(PRESS_ARMOR);
				entries.add(ZABRALO_ARMOR);
				TarkovItemCatalog.getArmorItems().forEach(entries::add);
			})
			.build()
	);

	public static final ItemGroup TARKOV_RIGS_GROUP = Registry.register(
		Registries.ITEM_GROUP,
		new Identifier(TarkovCraftMod.MOD_ID, "tarkov_rigs_tab"),
		FabricItemGroup.builder()
			.icon(() -> new ItemStack(TRITON_RIG))
			.displayName(Text.translatable("itemGroup.tarkovcraft.tarkov_rigs_tab"))
			.entries((displayContext, entries) -> {
				entries.add(SCAV_VEST);
				entries.add(BANK_ROBBER_RIG);
				entries.add(TRITON_RIG);
				TarkovItemCatalog.getRigItems().forEach(entries::add);
			})
			.build()
	);

	public static final ItemGroup TARKOV_BACKPACKS_GROUP = Registry.register(
		Registries.ITEM_GROUP,
		new Identifier(TarkovCraftMod.MOD_ID, "tarkov_backpacks_tab"),
		FabricItemGroup.builder()
			.icon(() -> new ItemStack(BERKUT_BACKPACK))
			.displayName(Text.translatable("itemGroup.tarkovcraft.tarkov_backpacks_tab"))
			.entries((displayContext, entries) -> {
				entries.add(SLING_BACKPACK);
				entries.add(BERKUT_BACKPACK);
				entries.add(TRIZIP_BACKPACK);
				TarkovItemCatalog.getBackpackItems().forEach(entries::add);
			})
			.build()
	);

	public static final ItemGroup TARKOV_MEDS_GROUP = Registry.register(
		Registries.ITEM_GROUP,
		new Identifier(TarkovCraftMod.MOD_ID, "tarkov_meds_tab"),
		FabricItemGroup.builder()
			.icon(() -> new ItemStack(SALEWA_MEDKIT))
			.displayName(Text.translatable("itemGroup.tarkovcraft.tarkov_meds_tab"))
			.entries((displayContext, entries) -> {
				entries.add(BANDAGE);
				entries.add(CAT_TOURNIQUET);
				entries.add(SPLINT);
				entries.add(ALU_SPLINT);
				entries.add(ANALGIN);
				entries.add(AI2_MEDKIT);
				entries.add(SALEWA_MEDKIT);
				entries.add(CMS_KIT);
				entries.add(SURV12_KIT);
				TarkovItemCatalog.getMedicalItems().forEach(entries::add);
			})
			.build()
	);

	public static final ItemGroup TARKOV_FOOD_GROUP = Registry.register(
		Registries.ITEM_GROUP,
		new Identifier(TarkovCraftMod.MOD_ID, "tarkov_food_tab"),
		FabricItemGroup.builder()
			.icon(() -> TarkovItemCatalog.getFoodItems().isEmpty()
				? new ItemStack(Items.APPLE)
				: new ItemStack(TarkovItemCatalog.getFoodItems().get(0)))
			.displayName(Text.translatable("itemGroup.tarkovcraft.tarkov_food_tab"))
			.entries((displayContext, entries) -> TarkovItemCatalog.getFoodItems().forEach(entries::add))
			.build()
	);

	public static final ItemGroup TARKOV_TACTICAL_GROUP = Registry.register(
		Registries.ITEM_GROUP,
		new Identifier(TarkovCraftMod.MOD_ID, "tarkov_tactical_tab"),
		FabricItemGroup.builder()
			.icon(() -> new ItemStack(ModBlocks.WEAPON_CRATE))
			.displayName(Text.translatable("itemGroup.tarkovcraft.tarkov_tactical_tab"))
			.entries((displayContext, entries) -> {
				entries.add(ModBlocks.WEAPON_CRATE);
				entries.add(ModBlocks.MEDBAG);
				entries.add(ModBlocks.TECHNICAL_CRATE);
				entries.add(ModBlocks.AIRDROP_CRATE);
				entries.add(ModBlocks.TARKOV_WORKBENCH);
				TarkovItemCatalog.getTacticalItems().forEach(entries::add);
			})
			.build()
	);

	public static final ItemGroup TARKOV_AMMO_GROUP = Registry.register(
		Registries.ITEM_GROUP,
		new Identifier(TarkovCraftMod.MOD_ID, "tarkov_ammo_tab"),
		FabricItemGroup.builder()
			.icon(() -> Registries.ITEM.containsId(new Identifier("tacz", "ammo"))
				? AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.AP, 1)
				: new ItemStack(ModBlocks.TARKOV_WORKBENCH))
			.displayName(Text.translatable("itemGroup.tarkovcraft.tarkov_ammo_tab"))
			.entries((displayContext, entries) -> {
				if (!Registries.ITEM.containsId(new Identifier("tacz", "ammo"))) {
					return;
				}
				for (TaCZCaliberCatalog.CaliberSpec spec : TaCZCaliberCatalog.getAllKnownCalibers()) {
					for (AmmoTier tier : AmmoTier.values()) {
						entries.add(AmmoTier.createTaczAmmoStack(spec.ammoId(), tier, 1));
					}
				}
			})
			.build()
	);

	private ModItems() {
	}

	private static <T extends Item> T register(String path, T item) {
		return Registry.register(Registries.ITEM, new Identifier(TarkovCraftMod.MOD_ID, path), item);
	}

	public static void init() {
		TarkovItemCatalog.registerAll();
	}
}
