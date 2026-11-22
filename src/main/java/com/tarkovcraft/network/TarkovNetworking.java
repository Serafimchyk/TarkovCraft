package com.tarkovcraft.network;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.block.SearchableLootBlockEntity;
import com.tarkovcraft.block.TarkovWorkbenchBlock;
import com.tarkovcraft.client.ClientLoadoutCache;
import com.tarkovcraft.client.WorldInteractState;
import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovCaseItem;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.screen.TarkovScreenHandler;
import com.tarkovcraft.screen.TarkovWorkbenchScreenHandler;
import com.tarkovcraft.search.ContainerSearchMemory;
import com.tarkovcraft.search.SearchSession;
import com.tarkovcraft.workbench.WorkbenchInventoryHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TarkovNetworking {
	// S2C
	public static final Identifier S2C_LOADOUT_SYNC = new Identifier(TarkovCraftMod.MOD_ID, "loadout_sync");
	public static final Identifier S2C_SCREEN_STATE = new Identifier(TarkovCraftMod.MOD_ID, "screen_state");
	public static final Identifier S2C_HEALTH_SYNC = new Identifier(TarkovCraftMod.MOD_ID, "health_sync");
	public static final Identifier S2C_WORKBENCH_SYNC = new Identifier(TarkovCraftMod.MOD_ID, "workbench_sync");

	// C2S
	public static final Identifier C2S_OPEN_OWN_INVENTORY = new Identifier(TarkovCraftMod.MOD_ID, "open_own_inventory");
	public static final Identifier C2S_MOVE_ITEM = new Identifier(TarkovCraftMod.MOD_ID, "move_item");
	public static final Identifier C2S_QUICK_TRANSFER = new Identifier(TarkovCraftMod.MOD_ID, "quick_transfer");
	public static final Identifier C2S_QUICK_EQUIP = new Identifier(TarkovCraftMod.MOD_ID, "quick_equip");
	public static final Identifier C2S_USE_ITEM = new Identifier(TarkovCraftMod.MOD_ID, "use_item");
	public static final Identifier C2S_SPLIT_STACK = new Identifier(TarkovCraftMod.MOD_ID, "split_stack");
	public static final Identifier C2S_REQUEST_SEARCH = new Identifier(TarkovCraftMod.MOD_ID, "request_search");
	public static final Identifier C2S_HEAL_PART = new Identifier(TarkovCraftMod.MOD_ID, "heal_part");
	public static final Identifier C2S_TOGGLE_MELEE = new Identifier(TarkovCraftMod.MOD_ID, "toggle_melee");
	public static final Identifier C2S_INTERACT = new Identifier(TarkovCraftMod.MOD_ID, "interact");
	public static final Identifier C2S_OPEN_CASE = new Identifier(TarkovCraftMod.MOD_ID, "open_case");
	public static final Identifier C2S_CLOSE_CASE = new Identifier(TarkovCraftMod.MOD_ID, "close_case");
	public static final Identifier C2S_WORKBENCH_CRAFT_AMMO = new Identifier(TarkovCraftMod.MOD_ID, "workbench_craft_ammo");
	public static final Identifier C2S_WORKBENCH_CRAFT_GEAR = new Identifier(TarkovCraftMod.MOD_ID, "workbench_craft_gear");

	// Locations
	public static final byte LOC_EQUIPMENT = 0;
	public static final byte LOC_POCKET = 1;
	public static final byte LOC_RIG_GRID = 2;
	public static final byte LOC_BACKPACK_GRID = 3;
	public static final byte LOC_EXTERNAL_GRID = 4;       // Loot block OR corpse pockets OR ground backpack
	public static final byte LOC_CORPSE_EQUIPMENT = 5;
	public static final byte LOC_DROP = 6;
	public static final byte LOC_CORPSE_RIG_GRID = 7;     // Corpse rig OR ground rig compartments
	public static final byte LOC_CORPSE_BACKPACK_GRID = 8;
	public static final byte LOC_FLOATING_CONTAINER = 9;   // Floating container window: idx = (winIdx << 8) | (subGridIdx & 0xFF)

	private TarkovNetworking() {
	}

	public record ViewEntry(
		UUID entryId,
		int x,
		int y,
		int width,
		int height,
		boolean rotated,
		boolean discovered,
		ItemStack stack
	) {
	}

	public record GridView(
		UUID containerId,
		UUID searchTargetId,
		int width,
		int height,
		boolean gridRevealed,
		List<ViewEntry> entries
	) {
		public static GridView fromContainer(
			GridContainer container,
			UUID searchTargetId,
			@Nullable ContainerSearchMemory memory,
			UUID playerId,
			boolean requiresSearch
		) {
			boolean revealed = !requiresSearch || memory == null || memory.isGridRevealed(playerId);
			List<ViewEntry> list = new ArrayList<>();
			if (revealed) {
				for (GridEntry e : container.getEntries()) {
					boolean disc = !requiresSearch || memory == null || memory.isEntryDiscovered(playerId, e.getEntryId());
					ItemSize sz = e.getOccupiedSize();
					list.add(new ViewEntry(
						e.getEntryId(),
						e.getX(),
						e.getY(),
						sz.width(),
						sz.height(),
						e.isRotated(),
						disc,
						disc ? e.getStack().copy() : ItemStack.EMPTY
					));
				}
			}
			return new GridView(container.getContainerId(), searchTargetId, container.getWidth(), container.getHeight(), revealed, list);
		}

		public void write(PacketByteBuf buf) {
			buf.writeUuid(containerId);
			buf.writeUuid(searchTargetId);
			buf.writeVarInt(width);
			buf.writeVarInt(height);
			buf.writeBoolean(gridRevealed);
			buf.writeVarInt(entries.size());
			for (ViewEntry e : entries) {
				buf.writeUuid(e.entryId());
				buf.writeVarInt(e.x());
				buf.writeVarInt(e.y());
				buf.writeVarInt(e.width());
				buf.writeVarInt(e.height());
				buf.writeBoolean(e.rotated());
				buf.writeBoolean(e.discovered());
				if (e.discovered()) {
					buf.writeItemStack(e.stack());
				}
			}
		}

		public static GridView read(PacketByteBuf buf) {
			UUID cid = buf.readUuid();
			UUID stid = buf.readUuid();
			int w = buf.readVarInt();
			int h = buf.readVarInt();
			boolean rev = buf.readBoolean();
			int count = buf.readVarInt();
			List<ViewEntry> list = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				UUID eid = buf.readUuid();
				int x = buf.readVarInt();
				int y = buf.readVarInt();
				int ew = buf.readVarInt();
				int eh = buf.readVarInt();
				boolean rot = buf.readBoolean();
				boolean disc = buf.readBoolean();
				ItemStack st = disc ? buf.readItemStack() : ItemStack.EMPTY;
				list.add(new ViewEntry(eid, x, y, ew, eh, rot, disc, st));
			}
			return new GridView(cid, stid, w, h, rev, list);
		}
	}

	public record OpenContainerView(
		UUID containerUuid,
		String title,
		List<GridView> grids
	) {
		public void write(PacketByteBuf buf) {
			buf.writeUuid(containerUuid);
			buf.writeString(title == null ? "" : title);
			ScreenStateSnapshot.writeGridList(buf, grids);
		}

		public static OpenContainerView read(PacketByteBuf buf) {
			return new OpenContainerView(buf.readUuid(), buf.readString(), ScreenStateSnapshot.readGridList(buf));
		}
	}

	public record ScreenStateSnapshot(
		byte mode,
		String externalTitle,
		List<GridView> ownRigViews,
		@Nullable GridView ownBackpackView,
		@Nullable GridView externalGridView,
		@Nullable PlayerLoadout corpseLoadout,
		List<GridView> corpseRigViews,
		@Nullable GridView corpseBackpackView,
		byte searchStage,
		@Nullable UUID searchTargetId,
		@Nullable UUID searchEntryId,
		float searchProgress,
		List<OpenContainerView> openContainers
	) {
		public ScreenStateSnapshot(
			byte mode,
			String externalTitle,
			List<GridView> ownRigViews,
			@Nullable GridView ownBackpackView,
			@Nullable GridView externalGridView,
			@Nullable PlayerLoadout corpseLoadout,
			List<GridView> corpseRigViews,
			@Nullable GridView corpseBackpackView,
			byte searchStage,
			@Nullable UUID searchTargetId,
			@Nullable UUID searchEntryId,
			float searchProgress
		) {
			this(mode, externalTitle, ownRigViews, ownBackpackView, externalGridView, corpseLoadout,
				corpseRigViews, corpseBackpackView, searchStage, searchTargetId, searchEntryId, searchProgress, List.of());
		}

		public void write(PacketByteBuf buf) {
			buf.writeByte(mode);
			buf.writeString(externalTitle == null ? "" : externalTitle);
			writeGridList(buf, ownRigViews);
			buf.writeBoolean(ownBackpackView != null);
			if (ownBackpackView != null) ownBackpackView.write(buf);
			buf.writeBoolean(externalGridView != null);
			if (externalGridView != null) externalGridView.write(buf);
			buf.writeBoolean(corpseLoadout != null);
			if (corpseLoadout != null) buf.writeNbt(corpseLoadout.writeNbt());
			writeGridList(buf, corpseRigViews);
			buf.writeBoolean(corpseBackpackView != null);
			if (corpseBackpackView != null) corpseBackpackView.write(buf);
			buf.writeByte(searchStage);
			buf.writeBoolean(searchTargetId != null);
			if (searchTargetId != null) buf.writeUuid(searchTargetId);
			buf.writeBoolean(searchEntryId != null);
			if (searchEntryId != null) buf.writeUuid(searchEntryId);
			buf.writeFloat(searchProgress);
			List<OpenContainerView> open = openContainers == null ? List.of() : openContainers;
			buf.writeVarInt(open.size());
			for (OpenContainerView v : open) {
				v.write(buf);
			}
		}

		public static ScreenStateSnapshot read(PacketByteBuf buf) {
			byte mode = buf.readByte();
			String title = buf.readString();
			List<GridView> ownRig = readGridList(buf);
			GridView bpView = buf.readBoolean() ? GridView.read(buf) : null;
			GridView extView = buf.readBoolean() ? GridView.read(buf) : null;
			PlayerLoadout corpse = null;
			if (buf.readBoolean()) {
				corpse = new PlayerLoadout();
				corpse.readNbt(buf.readNbt());
			}
			List<GridView> corpseRig = readGridList(buf);
			GridView corpseBp = buf.readBoolean() ? GridView.read(buf) : null;
			byte stage = buf.readByte();
			UUID targetId = buf.readBoolean() ? buf.readUuid() : null;
			UUID entryId = buf.readBoolean() ? buf.readUuid() : null;
			float progress = buf.readFloat();
			int openCount = buf.readVarInt();
			List<OpenContainerView> open = new ArrayList<>(openCount);
			for (int i = 0; i < openCount; i++) {
				open.add(OpenContainerView.read(buf));
			}
			return new ScreenStateSnapshot(mode, title, ownRig, bpView, extView, corpse, corpseRig, corpseBp, stage, targetId, entryId, progress, open);
		}

		static void writeGridList(PacketByteBuf buf, List<GridView> list) {
			buf.writeVarInt(list.size());
			for (GridView v : list) {
				v.write(buf);
			}
		}

		static List<GridView> readGridList(PacketByteBuf buf) {
			int n = buf.readVarInt();
			List<GridView> out = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				out.add(GridView.read(buf));
			}
			return out;
		}
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(C2S_OPEN_OWN_INVENTORY, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> TarkovScreenHandler.openOwnInventory(player));
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE_MELEE, (server, player, handler, buf, responseSender) -> {
			server.execute(() -> {
				if (player instanceof PlayerLoadoutHolder holder) {
					PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
					loadout.setMeleeSelected(!loadout.isMeleeSelected());
					holder.tarkovcraft$syncLoadout();
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_INTERACT, (server, player, handler, buf, responseSender) -> {
			byte actionIndex = buf.readByte();
			server.execute(() -> handleInteract(player, actionIndex));
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_QUICK_TRANSFER, (server, player, handler, buf, responseSender) -> {
			byte srcLoc = buf.readByte();
			int srcIdx = buf.readVarInt();
			boolean hasEntryId = buf.readBoolean();
			UUID srcEntryId = hasEntryId ? buf.readUuid() : null;
			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.handleQuickTransfer(player, srcLoc, srcIdx, srcEntryId);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_QUICK_EQUIP, (server, player, handler, buf, responseSender) -> {
			byte srcLoc = buf.readByte();
			int srcIdx = buf.readVarInt();
			boolean hasEntryId = buf.readBoolean();
			UUID srcEntryId = hasEntryId ? buf.readUuid() : null;
			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.handleQuickEquip(player, srcLoc, srcIdx, srcEntryId);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_USE_ITEM, (server, player, handler, buf, responseSender) -> {
			byte srcLoc = buf.readByte();
			int srcIdx = buf.readVarInt();
			boolean hasEntryId = buf.readBoolean();
			UUID srcEntryId = hasEntryId ? buf.readUuid() : null;
			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.handleUseItem(player, srcLoc, srcIdx, srcEntryId);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_SPLIT_STACK, (server, player, handler, buf, responseSender) -> {
			byte srcLoc = buf.readByte();
			int srcIdx = buf.readVarInt();
			boolean hasEntryId = buf.readBoolean();
			UUID srcEntryId = hasEntryId ? buf.readUuid() : null;
			int splitAmount = buf.readVarInt();
			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.handleSplitStack(player, srcLoc, srcIdx, srcEntryId, splitAmount);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_SEARCH, (server, player, handler, buf, responseSender) -> {
			UUID targetId = buf.readUuid();
			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.handleRequestSearch(player, targetId);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_HEAL_PART, (server, player, handler, buf, responseSender) -> {
			byte srcLoc = buf.readByte();
			int srcIdx = buf.readVarInt();
			boolean hasEntryId = buf.readBoolean();
			UUID srcEntryId = hasEntryId ? buf.readUuid() : null;
			byte partOrdinal = buf.readByte();
			BodyPart[] parts = BodyPart.values();
			BodyPart targetPart = (partOrdinal >= 0 && partOrdinal < parts.length) ? parts[partOrdinal] : null;
			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.handleHealPart(player, srcLoc, srcIdx, srcEntryId, targetPart);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_OPEN_CASE, (server, player, handler, buf, responseSender) -> {
			byte srcLoc = buf.readByte();
			int srcIdx = buf.readVarInt();
			boolean hasEntryId = buf.readBoolean();
			UUID srcEntryId = hasEntryId ? buf.readUuid() : null;
			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.handleOpenCase(player, srcLoc, srcIdx, srcEntryId);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_CLOSE_CASE, (server, player, handler, buf, responseSender) -> {
			boolean hasUuid = buf.readableBytes() >= 1 && buf.readBoolean();
			UUID containerUuid = hasUuid ? buf.readUuid() : null;
			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.handleCloseCase(player, containerUuid);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_WORKBENCH_CRAFT_AMMO, (server, player, handler, buf, responseSender) -> {
			String ammoId = buf.readString(64);
			byte tierOrdinal = buf.readByte();
			boolean upgradeFromFmj = buf.readBoolean();
			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovWorkbenchScreenHandler wb) {
					wb.handleCraftAmmo(player, ammoId, tierOrdinal, upgradeFromFmj);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_WORKBENCH_CRAFT_GEAR, (server, player, handler, buf, responseSender) -> {
			Identifier targetId = buf.readIdentifier();
			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovWorkbenchScreenHandler wb) {
					wb.handleCraftGear(player, targetId);
				}
			});
		});

		ServerPlayNetworking.registerGlobalReceiver(C2S_MOVE_ITEM, (server, player, handler, buf, responseSender) -> {
			byte srcLoc = buf.readByte();
			int srcIdx = buf.readVarInt();
			boolean hasEntryId = buf.readBoolean();
			UUID srcEntryId = hasEntryId ? buf.readUuid() : null;

			byte dstLoc = buf.readByte();
			int dstIdx = buf.readVarInt();
			int dstX = buf.readVarInt();
			int dstY = buf.readVarInt();
			boolean dstRotated = buf.readBoolean();
			int moveCount = buf.readVarInt(); // <= 0 means full stack

			server.execute(() -> {
				if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
					tsh.handleMoveAction(player, srcLoc, srcIdx, srcEntryId, dstLoc, dstIdx, dstX, dstY, dstRotated, moveCount);
				}
			});
		});
	}

	private static void handleInteract(ServerPlayerEntity player, byte actionIndex) {
		if (player.isSpectator()) return;
		double reach = 4.2;
		Vec3d eye = player.getCameraPosVec(1.0f);
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d end = eye.add(look.multiply(reach));

		BlockHitResult blockHit = player.getWorld().raycast(new RaycastContext(
			eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player
		));
		double maxDistSq = blockHit.getType() == HitResult.Type.BLOCK
			? eye.squaredDistanceTo(blockHit.getPos())
			: reach * reach;

		Box searchBox = player.getBoundingBox().stretch(look.multiply(reach)).expand(1.2);
		EntityHitResult entityHit = ProjectileUtil.raycast(
			player, eye, end, searchBox,
			e -> (e instanceof CorpseEntity || e instanceof ItemEntity) && !e.isRemoved(),
			maxDistSq
		);

		Entity targetEntity = entityHit != null ? entityHit.getEntity() : findClosestLookedItem(player, eye, look, Math.sqrt(maxDistSq));

		if (targetEntity instanceof CorpseEntity corpse) {
			corpse.openForPlayer(player);
			return;
		}

		if (targetEntity instanceof ItemEntity itemEntity && player instanceof PlayerLoadoutHolder holder) {
			ItemStack stack = itemEntity.getStack();
			if (stack.isEmpty()) return;

			// Ground Backpack / Tactical Rig / Case supports scrollable action: 0 = Search on ground, 1 = Equip/Take!
			boolean isGroundContainer = stack.getItem() instanceof BackpackItem
				|| stack.getItem() instanceof TacticalRigItem
				|| stack.getItem() instanceof TarkovCaseItem;
			if (isGroundContainer && actionIndex == WorldInteractState.ACTION_SEARCH) {
				TarkovScreenHandler.openGroundContainer(player, itemEntity);
				return;
			}

			PlayerLoadout loadout = holder.tarkovcraft$getLoadout();
			int beforeCount = stack.getCount();
			boolean fullyPicked = loadout.trySmartPickup(stack, player.getUuid());
			if (fullyPicked || stack.getCount() < beforeCount) {
				if (stack.isEmpty()) {
					itemEntity.discard();
				} else {
					itemEntity.setStack(stack);
				}
				player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.35f, 1.2f);
				holder.tarkovcraft$syncLoadout();
			} else {
				player.sendMessage(Text.translatable("message.tarkovcraft.no_space"), true);
			}
			return;
		}

		if (blockHit.getType() == HitResult.Type.BLOCK) {
			if (player.getWorld().getBlockState(blockHit.getBlockPos()).getBlock() instanceof TarkovWorkbenchBlock) {
				TarkovWorkbenchScreenHandler.openForPlayer(player, blockHit.getBlockPos());
				return;
			}
			BlockEntity be = player.getWorld().getBlockEntity(blockHit.getBlockPos());
			if (be instanceof SearchableLootBlockEntity lootBe) {
				lootBe.openForPlayer(player);
				return;
			}
			com.tarkovcraft.container.VanillaContainerBridge.tryOpenForPlayer(player, blockHit.getBlockPos());
		}
	}

	@Nullable
	public static ItemEntity findClosestLookedItem(Entity viewer, Vec3d eye, Vec3d look, double maxDist) {
		Box box = viewer.getBoundingBox().expand(maxDist);
		List<ItemEntity> items = viewer.getWorld().getEntitiesByClass(ItemEntity.class, box, e -> !e.isRemoved() && !e.getStack().isEmpty());
		ItemEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (ItemEntity item : items) {
			Vec3d center = item.getPos().add(0, 0.18, 0);
			Vec3d toItem = center.subtract(eye);
			double dist = toItem.length();
			if (dist > maxDist || dist < 0.05) continue;
			double dot = toItem.normalize().dotProduct(look);
			if (dot > 0.93) {
				double score = dist * (1.05 - dot);
				if (score < bestScore) {
					bestScore = score;
					best = item;
				}
			}
		}
		return best;
	}

	public static void sendLoadoutSync(ServerPlayerEntity player, PlayerLoadout loadout) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeNbt(loadout.writeNbt());
		ServerPlayNetworking.send(player, S2C_LOADOUT_SYNC, buf);
	}

	public static void sendHealthSync(ServerPlayerEntity player, com.tarkovcraft.health.BodyPartHealth health) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeNbt(health.writeNbt());
		ServerPlayNetworking.send(player, S2C_HEALTH_SYNC, buf);
	}

	public static void sendScreenState(ServerPlayerEntity player, ScreenStateSnapshot snapshot) {
		PacketByteBuf buf = PacketByteBufs.create();
		snapshot.write(buf);
		ServerPlayNetworking.send(player, S2C_SCREEN_STATE, buf);
	}

	public static void sendWorkbenchSync(ServerPlayerEntity player, WorkbenchInventoryHelper.SyncPayload payload) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(payload.itemCounts().size());
		for (var entry : payload.itemCounts().entrySet()) {
			buf.writeIdentifier(Registries.ITEM.getId(entry.getKey()));
			buf.writeVarInt(entry.getValue());
		}
		buf.writeVarInt(payload.fmjAmmoCounts().size());
		for (var entry : payload.fmjAmmoCounts().entrySet()) {
			buf.writeString(entry.getKey(), 64);
			buf.writeVarInt(entry.getValue());
		}
		buf.writeVarInt(payload.extraCalibers().size());
		for (String ammoId : payload.extraCalibers()) {
			buf.writeString(ammoId, 64);
		}
		ServerPlayNetworking.send(player, S2C_WORKBENCH_SYNC, buf);
	}

	@Environment(EnvType.CLIENT)
	public static void registerClientReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(S2C_LOADOUT_SYNC, (client, handler, buf, responseSender) -> {
			NbtCompound nbt = buf.readNbt();
			client.execute(() -> {
				if (client.player instanceof PlayerLoadoutHolder holder) {
					holder.tarkovcraft$getLoadout().readNbt(nbt);
				}
				ClientLoadoutCache.updateLoadoutFromNbt(nbt);
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(S2C_HEALTH_SYNC, (client, handler, buf, responseSender) -> {
			NbtCompound nbt = buf.readNbt();
			client.execute(() -> {
				if (client.player instanceof PlayerLoadoutHolder holder) {
					holder.tarkovcraft$getBodyHealth().readNbt(nbt);
				}
				ClientLoadoutCache.updateBodyHealthFromNbt(nbt);
			});
		});

		ClientPlayNetworking.registerGlobalReceiver(S2C_SCREEN_STATE, (client, handler, buf, responseSender) -> {
			ScreenStateSnapshot snap = ScreenStateSnapshot.read(buf);
			client.execute(() -> ClientLoadoutCache.setScreenState(snap));
		});
	}

	@Environment(EnvType.CLIENT)
	public static void sendOpenOwnInventory() {
		ClientPlayNetworking.send(C2S_OPEN_OWN_INVENTORY, PacketByteBufs.empty());
	}

	@Environment(EnvType.CLIENT)
	public static void sendToggleMelee() {
		ClientPlayNetworking.send(C2S_TOGGLE_MELEE, PacketByteBufs.empty());
	}

	@Environment(EnvType.CLIENT)
	public static void sendInteract(byte actionIndex) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeByte(actionIndex);
		ClientPlayNetworking.send(C2S_INTERACT, buf);
	}

	@Environment(EnvType.CLIENT)
	public static void sendQuickTransfer(byte srcLoc, int srcIdx, @Nullable UUID srcEntryId) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeByte(srcLoc);
		buf.writeVarInt(srcIdx);
		buf.writeBoolean(srcEntryId != null);
		if (srcEntryId != null) {
			buf.writeUuid(srcEntryId);
		}
		ClientPlayNetworking.send(C2S_QUICK_TRANSFER, buf);
	}

	@Environment(EnvType.CLIENT)
	public static void sendMoveItem(
		byte srcLoc, int srcIdx, @Nullable UUID srcEntryId,
		byte dstLoc, int dstIdx, int dstX, int dstY, boolean dstRotated,
		int moveCount
	) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeByte(srcLoc);
		buf.writeVarInt(srcIdx);
		buf.writeBoolean(srcEntryId != null);
		if (srcEntryId != null) {
			buf.writeUuid(srcEntryId);
		}
		buf.writeByte(dstLoc);
		buf.writeVarInt(dstIdx);
		buf.writeVarInt(dstX);
		buf.writeVarInt(dstY);
		buf.writeBoolean(dstRotated);
		buf.writeVarInt(moveCount);
		ClientPlayNetworking.send(C2S_MOVE_ITEM, buf);
	}

	public static byte stageToByte(SearchSession.Stage stage) {
		return switch (stage) {
			case IDLE -> 0;
			case REVEALING_GRID -> 1;
			case SEARCHING_ITEM -> 2;
		};
	}
}
