package com.tarkovcraft.registry;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.entity.TarkovGrenadeEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
	public static final EntityType<CorpseEntity> CORPSE = Registry.register(
		Registries.ENTITY_TYPE,
		new Identifier(TarkovCraftMod.MOD_ID, "corpse"),
		FabricEntityTypeBuilder.create(SpawnGroup.MISC, CorpseEntity::new)
			.dimensions(EntityDimensions.fixed(1.4f, 0.45f))
			.trackRangeBlocks(64)
			.trackedUpdateRate(10)
			.build()
	);

	public static final EntityType<TarkovGrenadeEntity> GRENADE = Registry.register(
		Registries.ENTITY_TYPE,
		new Identifier(TarkovCraftMod.MOD_ID, "grenade"),
		FabricEntityTypeBuilder.<TarkovGrenadeEntity>create(SpawnGroup.MISC, TarkovGrenadeEntity::new)
			.dimensions(EntityDimensions.fixed(0.25f, 0.25f))
			.trackRangeBlocks(64)
			.trackedUpdateRate(4)
			.build()
	);

	private ModEntities() {
	}

	public static void init() {
	}
}
