package com.tarkovcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.health.InjuryType;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModEntities;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;

/**
 * Operator-only (/tarkov, permission level 2) testing utilities.
 */
public final class TarkovCommand {
	private TarkovCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		List<String> zoneNames = Arrays.stream(HitZone.values()).map(HitZone::getId).toList();
		List<String> partNames = Arrays.stream(BodyPart.values()).map(BodyPart::getId).toList();
		List<String> limbNames = List.of("left_arm", "right_arm", "left_leg", "right_leg");
		List<String> bleedTypes = List.of("light", "heavy");

		dispatcher.register(CommandManager.literal("tarkov")
			.requires(source -> source.hasPermissionLevel(2))
			.then(CommandManager.literal("kit")
				.executes(ctx -> giveStarterKit(ctx.getSource())))
			.then(CommandManager.literal("meds")
				.executes(ctx -> giveMedicalKit(ctx.getSource())))
			.then(CommandManager.literal("loot")
				.executes(ctx -> spawnTestLootCrate(ctx.getSource())))
			.then(CommandManager.literal("corpse")
				.executes(ctx -> spawnTestCorpse(ctx.getSource())))
			.then(CommandManager.literal("clear_corpses")
				.executes(ctx -> clearCorpses(ctx.getSource())))
			.then(CommandManager.literal("heal")
				.executes(ctx -> healFull(ctx.getSource())))
			.then(CommandManager.literal("bleed")
				.then(CommandManager.argument("part", StringArgumentType.word())
					.suggests((ctx, builder) -> CommandSource.suggestMatching(partNames, builder))
					.then(CommandManager.argument("type", StringArgumentType.word())
						.suggests((ctx, builder) -> CommandSource.suggestMatching(bleedTypes, builder))
						.executes(ctx -> applyBleed(
							ctx.getSource(),
							StringArgumentType.getString(ctx, "part"),
							StringArgumentType.getString(ctx, "type")
						))
					)
				)
			)
			.then(CommandManager.literal("fracture")
				.then(CommandManager.argument("part", StringArgumentType.word())
					.suggests((ctx, builder) -> CommandSource.suggestMatching(limbNames, builder))
					.executes(ctx -> applyFracture(
						ctx.getSource(),
						StringArgumentType.getString(ctx, "part")
					))
				)
			)
			.then(CommandManager.literal("hit")
				.then(CommandManager.argument("zone", StringArgumentType.word())
					.suggests((ctx, builder) -> CommandSource.suggestMatching(zoneNames, builder))
					.then(CommandManager.argument("damage", FloatArgumentType.floatArg(1.0f, 500.0f))
						.executes(ctx -> simulateHit(
							ctx.getSource(),
							StringArgumentType.getString(ctx, "zone"),
							FloatArgumentType.getFloat(ctx, "damage"),
							25
						))
						.then(CommandManager.argument("penetration", IntegerArgumentType.integer(0, 100))
							.executes(ctx -> simulateHit(
								ctx.getSource(),
								StringArgumentType.getString(ctx, "zone"),
								FloatArgumentType.getFloat(ctx, "damage"),
								IntegerArgumentType.getInteger(ctx, "penetration")
							))
						)
					)
				)
			)
		);
	}

	private static int healFull(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null || !(player instanceof PlayerLoadoutHolder holder)) return 0;
		holder.tarkovcraft$getBodyHealth().resetFull();
		player.setHealth(player.getMaxHealth());
		holder.tarkovcraft$syncBodyHealth();
		source.sendFeedback(() -> Text.literal("[TarkovCraft] All body parts restored to 435/435 HP."), false);
		return 1;
	}

	private static int simulateHit(ServerCommandSource source, String zoneStr, float damage, int penetration) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null || !(player instanceof PlayerLoadoutHolder holder)) return 0;

		HitZone zone = HitZone.byId(zoneStr);
		BodyPartHealth health = holder.tarkovcraft$getBodyHealth();
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();

		boolean killed = health.applyHit(
			loadout, zone, damage, penetration, 1.0f, false, true,
			player.getRandom().nextFloat(), player.getRandom().nextFloat(),
			"Scav Sniper", "AK-74M", "5.45x39 PS", 32.5f
		);
		holder.tarkovcraft$syncBodyHealth();
		holder.tarkovcraft$syncLoadout();

		if (killed) {
			player.setHealth(0.0f);
			player.onDeath(player.getDamageSources().generic());
		} else {
			player.setHealth(Math.max(1.0f, health.getTotalRatio() * player.getMaxHealth()));
			source.sendFeedback(() -> Text.literal(
				"[TarkovCraft] Hit " + zone.getId() + " (" + damage + " raw, pen=" + penetration + ") -> "
					+ zone.getBodyPart().getId() + "=" + Math.round(health.getHp(zone.getBodyPart()))
					+ ", thorax=" + Math.round(health.getHp(com.tarkovcraft.health.BodyPart.THORAX))
			), false);
		}
		return 1;
	}

	private static int giveStarterKit(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null || !(player instanceof PlayerLoadoutHolder holder)) {
			return 0;
		}
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		loadout.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.SSH68_HELMET));
		loadout.setEquipment(EquipmentSlotType.FACE_MASK, new ItemStack(ModItems.BALACLAVA_MASK));
		loadout.setEquipment(EquipmentSlotType.BODY_ARMOR, new ItemStack(ModItems.PACA_ARMOR));
		loadout.setEquipment(EquipmentSlotType.MELEE, new ItemStack(Items.IRON_SWORD));

		ItemStack rig = new ItemStack(ModItems.TRITON_RIG);
		loadout.setEquipment(EquipmentSlotType.RIG, rig);

		ItemStack bp = new ItemStack(ModItems.BERKUT_BACKPACK);
		GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
		bpGrid.tryAutoPlace(new ItemStack(Items.COOKED_BEEF, 4));
		bpGrid.tryAutoPlace(new ItemStack(Items.GOLDEN_APPLE, 1));
		ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
		loadout.setEquipment(EquipmentSlotType.BACKPACK, bp);

		loadout.setPocket(0, new ItemStack(Items.APPLE, 2));
		loadout.setPocket(1, new ItemStack(Items.TORCH, 8));

		holder.tarkovcraft$syncLoadout();
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Starter kit equipped."), false);
		return 1;
	}

	private static int giveMedicalKit(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null || !(player instanceof PlayerLoadoutHolder holder)) {
			return 0;
		}
		PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
		List<ItemStack> meds = List.of(
			new ItemStack(ModItems.BANDAGE),
			new ItemStack(ModItems.CAT_TOURNIQUET),
			new ItemStack(ModItems.SPLINT),
			new ItemStack(ModItems.ALU_SPLINT),
			new ItemStack(ModItems.ANALGIN),
			new ItemStack(ModItems.AI2_MEDKIT),
			new ItemStack(ModItems.SALEWA_MEDKIT),
			new ItemStack(ModItems.CMS_KIT),
			new ItemStack(ModItems.SURV12_KIT)
		);
		for (ItemStack med : meds) {
			ItemStack copy = med.copy();
			if (!loadout.tryInsertItem(copy, player.getUuid()) || !copy.isEmpty()) {
				if (!copy.isEmpty()) {
					player.dropItem(copy, false);
				}
			}
		}
		holder.tarkovcraft$syncLoadout();
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Issued all 9 medical items."), false);
		return 1;
	}

	private static int applyBleed(ServerCommandSource source, String partStr, String typeStr) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null || !(player instanceof PlayerLoadoutHolder holder)) {
			return 0;
		}
		BodyPart part = BodyPart.byId(partStr);
		InjuryType injury = "heavy".equalsIgnoreCase(typeStr) ? InjuryType.HEAVY_BLEED : InjuryType.LIGHT_BLEED;
		holder.tarkovcraft$getBodyHealth().addInjury(part, injury);
		holder.tarkovcraft$syncBodyHealth();
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Applied " + injury.getId() + " to " + part.getId() + "."), false);
		return 1;
	}

	private static int applyFracture(ServerCommandSource source, String partStr) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null || !(player instanceof PlayerLoadoutHolder holder)) {
			return 0;
		}
		BodyPart part = BodyPart.byId(partStr);
		if (!BodyPartHealth.isLimb(part)) {
			source.sendError(Text.literal("[TarkovCraft] Only limbs (left_arm, right_arm, left_leg, right_leg) can fracture."));
			return 0;
		}
		holder.tarkovcraft$getBodyHealth().addInjury(part, InjuryType.FRACTURE);
		holder.tarkovcraft$syncBodyHealth();
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Applied fracture to " + part.getId() + "."), false);
		return 1;
	}

	private static int spawnTestLootCrate(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		BlockPos pos = player.getBlockPos().offset(player.getHorizontalFacing(), 2);
		player.getWorld().setBlockState(pos, ModBlocks.WEAPON_CRATE.getDefaultState());
		BlockEntity be = player.getWorld().getBlockEntity(pos);
		if (be instanceof SearchableLootBlockEntity lootBe) {
			GridContainer grid = lootBe.getGrid();
			grid.tryAutoPlace(new ItemStack(Items.IRON_INGOT, 5));
			grid.tryAutoPlace(new ItemStack(ModItems.SCAV_VEST));
			grid.tryAutoPlace(new ItemStack(Items.GOLDEN_APPLE, 1));
			grid.tryAutoPlace(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
			grid.tryAutoPlace(new ItemStack(ModItems.SSH68_HELMET));
			lootBe.markDirty();
		}
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Spawned Weapon Crate at " + pos.toShortString()), false);
		return 1;
	}

	private static int spawnTestCorpse(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		CorpseEntity corpse = new CorpseEntity(ModEntities.CORPSE, player.getWorld());
		BlockPos pos = player.getBlockPos().offset(player.getHorizontalFacing(), 2);
		corpse.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		corpse.setYaw(player.getYaw());
		corpse.setOwnerName("Scav Raider");
		corpse.populateScavLoot(player.getWorld().getRandom());
		player.getWorld().spawnEntity(corpse);
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Spawned Scav Raider corpse (pockets + rig + backpack)."), false);
		return 1;
	}

	private static int clearCorpses(ServerCommandSource source) {
		List<? extends CorpseEntity> corpses = source.getWorld().getEntitiesByType(ModEntities.CORPSE, e -> true);
		int count = corpses.size();
		for (CorpseEntity c : corpses) {
			c.discard();
		}
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Removed " + count + " corpse(s)."), false);
		return count;
	}
}
