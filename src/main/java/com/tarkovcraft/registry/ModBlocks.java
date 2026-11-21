package com.tarkovcraft.registry;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.block.SearchableLootBlock;
import com.tarkovcraft.block.SearchableLootBlockEntity;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlocks {
	public static final SearchableLootBlock WEAPON_CRATE = registerBlock("weapon_crate",
		new SearchableLootBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(3.0f).nonOpaque(),
			6, 4, "block.tarkovcraft.weapon_crate"));

	public static final SearchableLootBlock MEDBAG = registerBlock("medbag",
		new SearchableLootBlock(FabricBlockSettings.copyOf(Blocks.RED_WOOL).strength(1.0f).nonOpaque(),
			3, 3, "block.tarkovcraft.medbag"));

	public static final SearchableLootBlock TECHNICAL_CRATE = registerBlock("technical_crate",
		new SearchableLootBlock(FabricBlockSettings.copyOf(Blocks.OAK_PLANKS).strength(2.5f).nonOpaque(),
			4, 4, "block.tarkovcraft.technical_crate"));

	public static final BlockEntityType<SearchableLootBlockEntity> LOOT_CONTAINER_ENTITY = Registry.register(
		Registries.BLOCK_ENTITY_TYPE,
		new Identifier(TarkovCraftMod.MOD_ID, "loot_container"),
		FabricBlockEntityTypeBuilder.create(SearchableLootBlockEntity::new, WEAPON_CRATE, MEDBAG, TECHNICAL_CRATE).build()
	);

	private ModBlocks() {
	}

	private static <T extends Block> T registerBlock(String path, T block) {
		Identifier id = new Identifier(TarkovCraftMod.MOD_ID, path);
		Registry.register(Registries.BLOCK, id, block);
		Registry.register(Registries.ITEM, id, new BlockItem(block, new FabricItemSettings()));
		return block;
	}

	public static void init() {
	}
}
