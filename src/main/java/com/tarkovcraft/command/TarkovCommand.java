package com.tarkovcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tarkovcraft.airdrop.AirdropManager;
import com.tarkovcraft.block.SearchableLootBlock;
import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.container.VanillaContainerBridge;
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
import com.tarkovcraft.loot.CityZoneClassifier;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModEntities;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.worldgen.TarkovStructureFeature;
import com.tarkovcraft.worldgen.TarkovStructureRegistry;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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
		List<String> structureNames = List.of("any", "roadblock", "med_tent", "military_camp", "forest_stash");
		List<String> poolNames = TarkovLootPool.allIds();
		List<String> crateBlockNames = List.of("default", "weapon_crate", "medbag", "technical_crate", "airdrop_crate");

		dispatcher.register(CommandManager.literal("tarkov")
			.requires(source -> source.hasPermissionLevel(2))
			.then(CommandManager.literal("kit")
				.executes(ctx -> giveStarterKit(ctx.getSource())))
			.then(CommandManager.literal("meds")
				.executes(ctx -> giveMedicalKit(ctx.getSource())))
			.then(CommandManager.literal("loot")
				.executes(ctx -> spawnLootCrate(ctx.getSource(), "military", "default"))
				.then(CommandManager.argument("pool", StringArgumentType.word())
					.suggests((ctx, builder) -> CommandSource.suggestMatching(poolNames, builder))
					.executes(ctx -> spawnLootCrate(
						ctx.getSource(),
						StringArgumentType.getString(ctx, "pool"),
						"default"
					))
				)
			)
			.then(CommandManager.literal("crate")
				.then(CommandManager.literal("list")
					.executes(ctx -> listLootPools(ctx.getSource())))
				.then(CommandManager.literal("give")
					.then(CommandManager.argument("pool", StringArgumentType.word())
						.suggests((ctx, builder) -> CommandSource.suggestMatching(poolNames, builder))
						.executes(ctx -> giveLootCrate(
							ctx.getSource(),
							StringArgumentType.getString(ctx, "pool"),
							"default", -1, -1
						))
						.then(CommandManager.argument("block", StringArgumentType.word())
							.suggests((ctx, builder) -> CommandSource.suggestMatching(crateBlockNames, builder))
							.executes(ctx -> giveLootCrate(
								ctx.getSource(),
								StringArgumentType.getString(ctx, "pool"),
								StringArgumentType.getString(ctx, "block"), -1, -1
							))
							.then(CommandManager.argument("width", IntegerArgumentType.integer(1, 10))
								.then(CommandManager.argument("height", IntegerArgumentType.integer(1, 10))
									.executes(ctx -> giveLootCrate(
										ctx.getSource(),
										StringArgumentType.getString(ctx, "pool"),
										StringArgumentType.getString(ctx, "block"),
										IntegerArgumentType.getInteger(ctx, "width"),
										IntegerArgumentType.getInteger(ctx, "height")
									))
								)
							)
						)
					)
				)
				.then(CommandManager.literal("spawn")
					.then(CommandManager.argument("pool", StringArgumentType.word())
						.suggests((ctx, builder) -> CommandSource.suggestMatching(poolNames, builder))
						.executes(ctx -> spawnLootCrate(
							ctx.getSource(),
							StringArgumentType.getString(ctx, "pool"),
							"default"
						))
						.then(CommandManager.argument("block", StringArgumentType.word())
							.suggests((ctx, builder) -> CommandSource.suggestMatching(crateBlockNames, builder))
							.executes(ctx -> spawnLootCrate(
								ctx.getSource(),
								StringArgumentType.getString(ctx, "pool"),
								StringArgumentType.getString(ctx, "block")
							))
						)
					)
				)
				.then(CommandManager.literal("set")
					.then(CommandManager.argument("pool", StringArgumentType.word())
						.suggests((ctx, builder) -> CommandSource.suggestMatching(poolNames, builder))
						.executes(ctx -> setLookedCratePool(
							ctx.getSource(),
							StringArgumentType.getString(ctx, "pool")
						))
					)
				)
				.then(CommandManager.literal("refill")
					.executes(ctx -> refillCratesInRadius(ctx.getSource(), 16))
					.then(CommandManager.argument("radius", IntegerArgumentType.integer(1, 128))
						.executes(ctx -> refillCratesInRadius(
							ctx.getSource(),
							IntegerArgumentType.getInteger(ctx, "radius")
						))
					)
				)
			)
			.then(CommandManager.literal("city")
				.then(CommandManager.literal("respawn")
					.then(CommandManager.argument("minutes", IntegerArgumentType.integer(0, 10080))
						.executes(ctx -> setLootRespawnMinutes(
							ctx.getSource(),
							IntegerArgumentType.getInteger(ctx, "minutes")
						))
					)
				)
				.then(CommandManager.literal("autoloot")
					.then(CommandManager.literal("on")
						.executes(ctx -> setCityAutoLoot(ctx.getSource(), true)))
					.then(CommandManager.literal("off")
						.executes(ctx -> setCityAutoLoot(ctx.getSource(), false)))
				)
				.then(CommandManager.literal("zone")
					.then(CommandManager.argument("pool", StringArgumentType.word())
						.suggests((ctx, builder) -> CommandSource.suggestMatching(poolNames, builder))
						.then(CommandManager.argument("radius", IntegerArgumentType.integer(2, 128))
							.executes(ctx -> assignCityZone(
								ctx.getSource(),
								StringArgumentType.getString(ctx, "pool"),
								IntegerArgumentType.getInteger(ctx, "radius")
							))
						)
					)
				)
				.then(CommandManager.literal("scatter")
					.then(CommandManager.argument("radius", IntegerArgumentType.integer(8, 128))
						.executes(ctx -> scatterCityCrates(
							ctx.getSource(),
							IntegerArgumentType.getInteger(ctx, "radius"),
							"auto"
						))
						.then(CommandManager.argument("pool", StringArgumentType.word())
							.suggests((ctx, builder) -> {
								java.util.List<String> opts = new java.util.ArrayList<>();
								opts.add("auto");
								opts.addAll(poolNames);
								return CommandSource.suggestMatching(opts, builder);
							})
							.executes(ctx -> scatterCityCrates(
								ctx.getSource(),
								IntegerArgumentType.getInteger(ctx, "radius"),
								StringArgumentType.getString(ctx, "pool")
							))
						)
					)
				)
			)
			.then(CommandManager.literal("corpse")
				.executes(ctx -> spawnTestCorpse(ctx.getSource())))
			.then(CommandManager.literal("clear_corpses")
				.executes(ctx -> clearCorpses(ctx.getSource())))
			.then(CommandManager.literal("reload")
				.executes(ctx -> reloadConfig(ctx.getSource())))
			.then(CommandManager.literal("locate")
				.executes(ctx -> locateStructure(ctx.getSource(), "any"))
				.then(CommandManager.argument("structure", StringArgumentType.word())
					.suggests((ctx, builder) -> CommandSource.suggestMatching(structureNames, builder))
					.executes(ctx -> locateStructure(
						ctx.getSource(),
						StringArgumentType.getString(ctx, "structure")
					))
				)
			)
			.then(CommandManager.literal("airdrop")
				.executes(ctx -> spawnAirdropNear(ctx.getSource()))
				.then(CommandManager.literal("here")
					.executes(ctx -> spawnAirdropHere(ctx.getSource())))
				.then(CommandManager.literal("interval")
					.then(CommandManager.argument("minutes", IntegerArgumentType.integer(1, 1440))
						.executes(ctx -> setAirdropInterval(
							ctx.getSource(),
							IntegerArgumentType.getInteger(ctx, "minutes")
						))
					)
				)
			)
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
			player.getRandom().nextFloat(), player.getRandom().nextFloat(), player.getRandom().nextFloat(),
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

	private static SearchableLootBlock resolveCrateBlock(String blockName, TarkovLootPool pool) {
		if (blockName == null || blockName.isBlank() || "default".equalsIgnoreCase(blockName)) {
			return SearchableLootBlock.defaultBlockForPool(pool);
		}
		return switch (blockName.toLowerCase(java.util.Locale.ROOT)) {
			case "weapon_crate" -> ModBlocks.WEAPON_CRATE;
			case "medbag" -> ModBlocks.MEDBAG;
			case "airdrop_crate" -> ModBlocks.AIRDROP_CRATE;
			default -> ModBlocks.TECHNICAL_CRATE;
		};
	}

	private static int listLootPools(ServerCommandSource source) {
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Available loot pools (" + TarkovLootPool.values().length + "):").formatted(Formatting.GOLD), false);
		for (TarkovLootPool pool : TarkovLootPool.values()) {
			String giveCmd = "/tarkov crate give " + pool.getId();
			Text btn = Text.literal("[" + pool.getId() + "]")
				.styled(s -> s
					.withColor(pool.isCityZone() ? Formatting.AQUA : Formatting.GREEN)
					.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, giveCmd))
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to run: " + giveCmd)))
				);
			source.sendFeedback(() -> Text.literal(" - ")
				.append(btn)
				.append(Text.literal(" ").append(Text.translatable(pool.getTranslationKey())).formatted(Formatting.GRAY))
				.append(Text.literal(" (" + pool.getDefaultGridWidth() + "x" + pool.getDefaultGridHeight() + ")").formatted(Formatting.DARK_GRAY)),
				false);
		}
		return 1;
	}

	private static int giveLootCrate(ServerCommandSource source, String poolRaw, String blockName, int width, int height) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		TarkovLootPool pool = TarkovLootPool.byId(poolRaw);
		if (pool == null) {
			source.sendError(Text.literal("[TarkovCraft] Unknown loot pool: " + poolRaw + ". Use /tarkov crate list"));
			return 0;
		}
		SearchableLootBlock block = resolveCrateBlock(blockName, pool);
		int w = width > 0 ? width : pool.getDefaultGridWidth();
		int h = height > 0 ? height : pool.getDefaultGridHeight();
		ItemStack crateStack = SearchableLootBlock.createLootCrateStack(block, pool, w, h);

		boolean inserted = player.getInventory().insertStack(crateStack);
		if (!inserted && !crateStack.isEmpty()) {
			if (player instanceof PlayerLoadoutHolder holder) {
				holder.tarkovcraft$getLoadout().tryInsertItem(crateStack, player.getUuid());
				holder.tarkovcraft$syncLoadout();
			}
			if (!crateStack.isEmpty()) {
				player.dropItem(crateStack, false);
			}
		}
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Given loot crate: ")
			.append(Text.translatable(pool.getTranslationKey()).formatted(Formatting.GOLD))
			.append(Text.literal(" (" + w + "x" + h + ")").formatted(Formatting.GRAY)), false);
		return 1;
	}

	private static int spawnLootCrate(ServerCommandSource source, String poolRaw, String blockName) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		TarkovLootPool pool = TarkovLootPool.byId(poolRaw);
		if (pool == null) {
			source.sendError(Text.literal("[TarkovCraft] Unknown loot pool: " + poolRaw + ". Use /tarkov crate list"));
			return 0;
		}
		SearchableLootBlock block = resolveCrateBlock(blockName, pool);
		BlockPos pos = player.getBlockPos().offset(player.getHorizontalFacing(), 2);
		player.getWorld().setBlockState(pos, block.getDefaultState());
		BlockEntity be = player.getWorld().getBlockEntity(pos);
		if (be instanceof SearchableLootBlockEntity lootBe) {
			lootBe.resizeGrid(pool.getDefaultGridWidth(), pool.getDefaultGridHeight());
			lootBe.populateFromPool(pool, player.getWorld().getRandom());
		}
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Spawned ")
			.append(Text.translatable(pool.getTranslationKey()).formatted(Formatting.GOLD))
			.append(Text.literal(" crate at " + pos.toShortString())), false);
		return 1;
	}

	private static int setLookedCratePool(ServerCommandSource source, String poolRaw) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		TarkovLootPool pool = TarkovLootPool.byId(poolRaw);
		if (pool == null) {
			source.sendError(Text.literal("[TarkovCraft] Unknown loot pool: " + poolRaw + ". Use /tarkov crate list"));
			return 0;
		}
		HitResult hit = player.raycast(8.0, 0.0f, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			source.sendError(Text.literal("[TarkovCraft] Look directly at a TarkovCraft loot crate (within 8 blocks)."));
			return 0;
		}
		BlockPos pos = ((BlockHitResult) hit).getBlockPos();
		BlockEntity be = player.getWorld().getBlockEntity(pos);
		if (!(be instanceof SearchableLootBlockEntity lootBe)) {
			source.sendError(Text.literal("[TarkovCraft] Target block at " + pos.toShortString() + " is not a TarkovCraft loot crate."));
			return 0;
		}
		lootBe.populateFromPool(pool, player.getWorld().getRandom());
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Crate at " + pos.toShortString() + " set to ")
			.append(Text.translatable(pool.getTranslationKey()).formatted(Formatting.GOLD))
			.append(Text.literal(" and refilled.")), false);
		return 1;
	}

	private static int refillCratesInRadius(ServerCommandSource source, int radius) {
		BlockPos center = BlockPos.ofFloored(source.getPosition());
		int count = 0;
		int minChunkX = (center.getX() - radius) >> 4;
		int maxChunkX = (center.getX() + radius) >> 4;
		int minChunkZ = (center.getZ() - radius) >> 4;
		int maxChunkZ = (center.getZ() + radius) >> 4;
		int rSq = radius * radius;

		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				if (!source.getWorld().isChunkLoaded(cx, cz)) {
					continue;
				}
				var chunk = source.getWorld().getChunk(cx, cz);
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof SearchableLootBlockEntity lootBe
						&& be.getPos().getSquaredDistance(center) <= rSq) {
						lootBe.refillLoot(source.getWorld().getRandom());
						count++;
					}
				}
			}
		}
		int finalCount = count;
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Refilled " + finalCount + " loot crate(s) within " + radius + " blocks."), true);
		return finalCount;
	}

	private static int setLootRespawnMinutes(ServerCommandSource source, int minutes) {
		TarkovConfig cfg = TarkovConfig.get();
		cfg.lootRespawnMinutes = minutes;
		cfg.validateAndClamp();
		TarkovConfig.save();
		if (cfg.lootRespawnMinutes == 0) {
			source.sendFeedback(() -> Text.literal("[TarkovCraft] Hourly loot auto-respawn DISABLED."), true);
		} else {
			source.sendFeedback(() -> Text.literal("[TarkovCraft] Loot auto-respawn set to " + cfg.lootRespawnMinutes + " min (real time)."), true);
		}
		return 1;
	}

	private static int setCityAutoLoot(ServerCommandSource source, boolean enabled) {
		TarkovConfig cfg = TarkovConfig.get();
		cfg.cityAutoLootVanillaContainers = enabled;
		TarkovConfig.save();
		source.sendFeedback(() -> Text.literal("[TarkovCraft] City vanilla container auto-loot: " + (enabled ? "ON" : "OFF")), true);
		return 1;
	}

	private static int assignCityZone(ServerCommandSource source, String poolRaw, int radius) {
		TarkovLootPool pool = TarkovLootPool.byId(poolRaw);
		if (pool == null) {
			source.sendError(Text.literal("[TarkovCraft] Unknown loot pool: " + poolRaw + ". Use /tarkov crate list"));
			return 0;
		}
		var world = source.getWorld();
		BlockPos center = BlockPos.ofFloored(source.getPosition());
		int rSq = radius * radius;
		int minChunkX = (center.getX() - radius) >> 4;
		int maxChunkX = (center.getX() + radius) >> 4;
		int minChunkZ = (center.getZ() - radius) >> 4;
		int maxChunkZ = (center.getZ() + radius) >> 4;

		int modCount = 0;
		int vanillaCount = 0;
		java.util.Set<String> handledVanilla = new java.util.HashSet<>();

		for (int cx = minChunkX; cx <= maxChunkX; cx++) {
			for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				if (!world.isChunkLoaded(cx, cz)) {
					continue;
				}
				var chunk = world.getChunk(cx, cz);
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockPos p = be.getPos();
					if (p.getSquaredDistance(center) > rSq) {
						continue;
					}
					if (be instanceof SearchableLootBlockEntity lootBe) {
						lootBe.populateFromPool(pool, world.getRandom());
						modCount++;
					} else {
						var state = world.getBlockState(p);
						if (VanillaContainerBridge.isSupportedBlock(state.getBlock())
							&& !(state.getBlock() instanceof net.minecraft.block.EnderChestBlock)) {
							String key = VanillaContainerBridge.resolveCanonicalKey(p, state, java.util.UUID.randomUUID());
							if (handledVanilla.add(key) && VanillaContainerBridge.assignZoneAndRefill(world, p, pool)) {
								vanillaCount++;
							}
						}
					}
				}
			}
		}

		int fMod = modCount;
		int fVan = vanillaCount;
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Zone ")
			.append(Text.translatable(pool.getTranslationKey()).formatted(Formatting.GOLD))
			.append(Text.literal(" applied within " + radius + "m: " + fVan + " vanilla containers + " + fMod + " Tarkov crates.")), true);
		return fMod + fVan;
	}

	private static int scatterCityCrates(ServerCommandSource source, int radius, String poolRaw) {
		TarkovLootPool fixedPool = "auto".equalsIgnoreCase(poolRaw) ? null : TarkovLootPool.byId(poolRaw);
		if (!"auto".equalsIgnoreCase(poolRaw) && fixedPool == null) {
			source.sendError(Text.literal("[TarkovCraft] Unknown loot pool: " + poolRaw + " (use 'auto' or /tarkov crate list)"));
			return 0;
		}
		var world = source.getWorld();
		var rng = world.getRandom();
		BlockPos center = BlockPos.ofFloored(source.getPosition());
		int minY = Math.max(world.getBottomY() + 2, center.getY() - 24);
		int maxY = Math.min(world.getTopY() - 4, center.getY() + 24);

		java.util.List<BlockPos> placed = new java.util.ArrayList<>();
		int maxCrates = Math.min(80, Math.max(8, (radius * radius) / 220));
		int attempts = maxCrates * 35;

		for (int a = 0; a < attempts && placed.size() < maxCrates; a++) {
			int dx = rng.nextInt(radius * 2 + 1) - radius;
			int dz = rng.nextInt(radius * 2 + 1) - radius;
			if (dx * dx + dz * dz > radius * radius) {
				continue;
			}
			int x = center.getX() + dx;
			int z = center.getZ() + dz;
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			int y = minY + rng.nextInt(Math.max(1, maxY - minY + 1));
			BlockPos pos = new BlockPos(x, y, z);

			// Must be empty space on a solid floor, inside a building (roof overhead, not open sky), away from other crates
			if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
				continue;
			}
			BlockPos floorPos = pos.down();
			var floorState = world.getBlockState(floorPos);
			if (!floorState.isSolidBlock(world, floorPos) || floorState.getBlock() instanceof SearchableLootBlock) {
				continue;
			}
			if (world.isSkyVisible(pos)) {
				continue;
			}
			// Require at least 1 nearby wall within 2 blocks so crates sit naturally near walls/corners
			boolean nearWall = false;
			for (net.minecraft.util.math.Direction dir : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
				if (world.getBlockState(pos.offset(dir)).isSolidBlock(world, pos.offset(dir))) {
					nearWall = true;
					break;
				}
			}
			if (!nearWall) {
				continue;
			}
			boolean tooClose = false;
			for (BlockPos prev : placed) {
				if (prev.getSquaredDistance(pos) < 12 * 12) {
					tooClose = true;
					break;
				}
			}
			if (tooClose) {
				continue;
			}

			TarkovLootPool pool = fixedPool != null ? fixedPool : CityZoneClassifier.classify(world, pos, ModBlocks.TECHNICAL_CRATE);
			SearchableLootBlock block = SearchableLootBlock.defaultBlockForPool(pool);
			world.setBlockState(pos, block.getDefaultState());
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof SearchableLootBlockEntity lootBe) {
				lootBe.resizeGrid(pool.getDefaultGridWidth(), pool.getDefaultGridHeight());
				lootBe.setLootPool(pool);
				lootBe.setPendingLootGeneration(true);
				lootBe.setAutoRespawn(true);
				placed.add(pos);
			}
		}

		int finalPlaced = placed.size();
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Scattered " + finalPlaced
			+ " indoor loot crate(s) within " + radius + "m (auto-respawn: " + TarkovConfig.get().lootRespawnMinutes + "m)."), true);
		return finalPlaced;
	}

	private static int reloadConfig(ServerCommandSource source) {
		TarkovConfig.loadOrCreate();
		AirdropManager.onConfigReloaded();
		source.sendFeedback(() -> Text.translatable("message.tarkovcraft.config_reloaded"), true);
		return 1;
	}

	private static int locateStructure(ServerCommandSource source, String typeRaw) {
		TarkovStructureFeature.StructureKind filter = null;
		if (typeRaw != null && !"any".equalsIgnoreCase(typeRaw)) {
			try {
				filter = TarkovStructureFeature.StructureKind.valueOf(typeRaw.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException e) {
				source.sendError(Text.literal("[TarkovCraft] Unknown structure: " + typeRaw
					+ " (use: any, roadblock, med_tent, military_camp, forest_stash)"));
				return 0;
			}
		}

		BlockPos origin = BlockPos.ofFloored(source.getPosition());
		TarkovStructureRegistry registry = TarkovStructureRegistry.get(source.getWorld());
		TarkovStructureRegistry.FoundStructure found = registry.findNearest(filter, origin);
		if (found == null) {
			source.sendError(Text.literal("[TarkovCraft] No generated structures recorded yet for '" + typeRaw
				+ "'. Explore new chunks in Overworld to generate them."));
			return 0;
		}

		BlockPos pos = found.pos();
		int dist = (int) Math.round(Math.sqrt(pos.getSquaredDistance(origin)));
		String tpCmd = "/tp @s " + pos.getX() + " " + (pos.getY() + 1) + " " + pos.getZ();
		Text coords = Text.literal("[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
			.styled(style -> style
				.withColor(Formatting.GREEN)
				.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCmd))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to teleport: " + tpCmd)))
			);
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Nearest "
			+ found.kind().name().toLowerCase(java.util.Locale.ROOT) + " is at ")
			.append(coords)
			.append(Text.literal(" (" + dist + " blocks away)")), false);
		return 1;
	}

	private static int spawnAirdropNear(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		AirdropManager.spawnAirdropNear(player);
		return 1;
	}

	private static int spawnAirdropHere(ServerCommandSource source) {
		ServerPlayerEntity player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		AirdropManager.spawnAirdropAt(player.getServerWorld(), player.getBlockX(), player.getBlockZ(), true);
		return 1;
	}

	private static int setAirdropInterval(ServerCommandSource source, int minutes) {
		TarkovConfig cfg = TarkovConfig.get();
		cfg.airdropIntervalMinutes = minutes;
		cfg.validateAndClamp();
		TarkovConfig.save();
		AirdropManager.onConfigReloaded();
		source.sendFeedback(() -> Text.literal("[TarkovCraft] Airdrop interval set to " + cfg.airdropIntervalMinutes + " min."), true);
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
