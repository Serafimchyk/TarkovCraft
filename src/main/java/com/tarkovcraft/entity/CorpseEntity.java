package com.tarkovcraft.entity;

import com.tarkovcraft.arena.RaidSession;
import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovArmorItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.loot.TarkovLootGenerator;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.registry.ModEntities;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.screen.TarkovScreenHandler;
import com.tarkovcraft.search.ContainerSearchMemory;
import com.tarkovcraft.search.SearchTarget;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lootable corpse spawned on death of a player or humanoid mob.
 * NO fake generic container: holds the deceased's REAL equipment, REAL 6x(1x1) pockets,
 * REAL Tactical Rig (with its sub-grids), and REAL Backpack (with its grid).
 * Everything on the corpse searches automatically in order: Pockets -> Rig -> Backpack.
 * Both the Rig and Backpack can be stripped whole at any time (even unsearched).
 */
public class CorpseEntity extends Entity {
	public enum Kind {
		PLAYER, SCAV, BOSS;

		private static Kind fromName(String name, Kind fallback) {
			for (Kind kind : values()) {
				if (kind.name().equals(name)) return kind;
			}
			return fallback;
		}
	}

	public static final List<EquipmentSlotType> VISIBLE_SLOTS = List.of(
		EquipmentSlotType.BODY_ARMOR, EquipmentSlotType.RIG, EquipmentSlotType.BACKPACK,
		EquipmentSlotType.FACE_MASK, EquipmentSlotType.HELMET, EquipmentSlotType.HEADSET);

	private static final int EMPTY_DESPAWN_TICKS = 20 * 10;     // 10 seconds once completely looted
	private static final TrackedData<String> OWNER_NAME = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<Optional<UUID>> OWNER_UUID =
		DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
	private static final TrackedData<String> CORPSE_KIND =
		DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<NbtCompound> OWNER_PROFILE =
		DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);
	private static final Map<EquipmentSlotType, TrackedData<ItemStack>> GEAR = new EnumMap<>(EquipmentSlotType.class);

	static {
		for (EquipmentSlotType slot : VISIBLE_SLOTS) {
			GEAR.put(slot, DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.ITEM_STACK));
		}
	}

	private NbtCompound cachedProfileTag;
	@Nullable
	private GameProfile cachedProfile;

	private PlayerLoadout loadout = new PlayerLoadout();
	// Corpse pockets represented as a 6x1 searchable grid (6x 1x1 pockets, real Tarkov pockets!)
	private GridContainer pocketsGrid = new GridContainer(6, 1);
	private ContainerSearchMemory pocketsSearchMemory = new ContainerSearchMemory();
	private int ageTicks = 0;
	private int emptyTicks = 0;

	public CorpseEntity(EntityType<?> type, World world) {
		super(type, world);
		this.intersectionChecked = true;
	}

	public static CorpseEntity fromPlayer(World world, PlayerEntity player, PlayerLoadout sourceLoadout) {
		return fromPlayer(world, player, sourceLoadout, List.of(), new ArrayList<>());
	}

	public static CorpseEntity fromPlayer(
		World world,
		PlayerEntity player,
		PlayerLoadout sourceLoadout,
		List<ItemStack> extras,
		List<ItemStack> overflow
	) {
		return fromSnapshot(world, player.getPos(), player.getYaw(), player.getGameProfile(),
			player.getName().getString(), sourceLoadout, extras, overflow);
	}

	public static CorpseEntity fromSnapshot(
		World world,
		Vec3d position,
		float yaw,
		GameProfile ownerProfile,
		String ownerName,
		PlayerLoadout sourceLoadout,
		List<ItemStack> extras,
		List<ItemStack> overflow
	) {
		CorpseEntity corpse = new CorpseEntity(ModEntities.CORPSE, world);
		corpse.setPosition(position.x, position.y, position.z);
		corpse.setYaw(yaw);
		corpse.dataTracker.set(OWNER_UUID, Optional.of(ownerProfile.getId()));
		corpse.dataTracker.set(CORPSE_KIND, Kind.PLAYER.name());
		GameProfile skinProfile = new GameProfile(ownerProfile.getId(), ownerProfile.getName());
		skinProfile.getProperties().putAll("textures", ownerProfile.getProperties().get("textures"));
		corpse.dataTracker.set(OWNER_PROFILE, NbtHelper.writeGameProfile(new NbtCompound(), skinProfile));
		corpse.setOwnerName(ownerName);

		corpse.loadout = transferPlayerInventoryToCorpse(sourceLoadout, corpse.pocketsGrid, extras, overflow);
		corpse.syncVisualEquipment();
		return corpse;
	}

	public static PlayerLoadout transferPlayerInventoryToCorpse(
		PlayerLoadout sourceLoadout,
		GridContainer targetPocketsGrid,
		List<ItemStack> extras,
		List<ItemStack> overflow
	) {
		PlayerLoadout copy = sourceLoadout.copyLoot();
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			ItemStack p = copy.getPocket(i);
			if (!p.isEmpty()) {
				if (targetPocketsGrid.place(p.copy(), i, 0, false) == null) {
					overflow.add(p.copy());
				}
				copy.setPocket(i, ItemStack.EMPTY);
			}
		}

		// Store any offHand or vanilla main-inventory extras into corpse pockets -> empty equipment -> rig -> backpack -> overflow
		if (extras != null) {
			for (ItemStack extra : extras) {
				if (extra == null || extra.isEmpty()) {
					continue;
				}
				ItemStack rem = extra.copy();
				if (!targetPocketsGrid.tryAutoPlace(rem) && !rem.isEmpty()) {
					tryPlaceExtraIntoCorpseLoadout(copy, rem);
				}
				if (!rem.isEmpty()) {
					overflow.add(rem);
				}
			}
		}

		markContainerUnsearched(copy.getEquipment(EquipmentSlotType.BACKPACK));
		markContainerUnsearched(copy.getEquipment(EquipmentSlotType.RIG));
		return copy;
	}

	private static void tryPlaceExtraIntoCorpseLoadout(PlayerLoadout corpseLoadout, ItemStack rem) {
		if (rem.isEmpty()) {
			return;
		}
		// 1. Empty matching equipment slot (armor, backpack, rig, melee, weapons)
		for (EquipmentSlotType slot : EquipmentSlotType.values()) {
			if (corpseLoadout.getEquipment(slot).isEmpty() && slot.accepts(rem)) {
				corpseLoadout.setEquipment(slot, rem.copy());
				rem.setCount(0);
				return;
			}
		}
		// 2. Equipped Rig compartments
		ItemStack rig = corpseLoadout.getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem) {
			List<GridContainer> grids = ContainerItemHelper.readRigGrids(rig);
			boolean changed = false;
			for (GridContainer g : grids) {
				int before = rem.getCount();
				boolean full = g.tryAutoPlace(rem);
				if (full || rem.getCount() < before) {
					changed = true;
					if (rem.isEmpty()) break;
				}
			}
			if (changed) {
				ContainerItemHelper.writeRigGrids(rig, grids);
				if (rem.isEmpty()) return;
			}
		}
		// 3. Equipped Backpack grid
		ItemStack bp = corpseLoadout.getEquipment(EquipmentSlotType.BACKPACK);
		if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem) {
			GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
			int before = rem.getCount();
			boolean full = bpGrid.tryAutoPlace(rem);
			if (full || rem.getCount() < before) {
				ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
			}
		}
	}

	public static CorpseEntity fromMob(World world, LivingEntity mob) {
		return fromGearedMob(world, mob);
	}

	public static CorpseEntity fromGearedMob(World world, LivingEntity mob) {
		CorpseEntity corpse = new CorpseEntity(ModEntities.CORPSE, world);
		corpse.setPosition(mob.getX(), mob.getY(), mob.getZ());
		corpse.setYaw(mob.getYaw());
		corpse.setOwnerName(mob.getDisplayName().getString());
		Random random = world.getRandom();

		ItemStack headStack = mob.getEquippedStack(EquipmentSlot.HEAD);
		if (!headStack.isEmpty() && headStack.getItem() instanceof TarkovArmorItem armor
			&& armor.getTargetSlot() == EquipmentSlotType.HELMET) {
			corpse.loadout.setEquipment(EquipmentSlotType.HELMET, headStack.copy());
		} else if (random.nextFloat() < 0.45f) {
			ItemStack rolledHelmet = TarkovLootGenerator.pickRandomLightHelmet(random);
			if (!rolledHelmet.isEmpty()) {
				corpse.loadout.setEquipment(EquipmentSlotType.HELMET, rolledHelmet);
			}
		}

		ItemStack chestStack = mob.getEquippedStack(EquipmentSlot.CHEST);
		if (!chestStack.isEmpty() && chestStack.getItem() instanceof TarkovArmorItem armor
			&& armor.getTargetSlot() == EquipmentSlotType.BODY_ARMOR) {
			corpse.loadout.setEquipment(EquipmentSlotType.BODY_ARMOR, chestStack.copy());
		} else if (random.nextFloat() < 0.5f) {
			ItemStack rolledArmor = TarkovLootGenerator.pickRandomLightArmor(random);
			if (!rolledArmor.isEmpty()) {
				corpse.loadout.setEquipment(EquipmentSlotType.BODY_ARMOR, rolledArmor);
			}
		}

		if (mob.getCommandTags().contains(MobGearSpawner.TAG_GEARED_RIG) || random.nextFloat() < 0.65f) {
			ItemStack rig = TarkovLootGenerator.pickRandomScavRig(random);
			if (!rig.isEmpty()) {
				List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rig);
				for (GridContainer g : rigGrids) {
					TarkovLootGenerator.populateLootGrid(g, TarkovLootPool.SCAV_POCKET, random);
				}
				ContainerItemHelper.writeRigGrids(rig, rigGrids);
				markContainerUnsearched(rig);
				corpse.loadout.setEquipment(EquipmentSlotType.RIG, rig);
			}
		}

		if (mob.getCommandTags().contains(MobGearSpawner.TAG_GEARED_BACKPACK)) {
			ItemStack bp = TarkovLootGenerator.pickRandomScavBackpack(random);
			if (!bp.isEmpty()) {
				GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
				TarkovLootGenerator.populateLootGrid(bpGrid, TarkovLootPool.CIVILIAN, random);
				ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
				markContainerUnsearched(bp);
				corpse.loadout.setEquipment(EquipmentSlotType.BACKPACK, bp);
			}
		}

		TarkovLootGenerator.populateLootGrid(corpse.pocketsGrid, TarkovLootPool.SCAV_POCKET, random);
		corpse.syncVisualEquipment();
		return corpse;
	}

	public static CorpseEntity fromRegularMob(World world, LivingEntity mob) {
		CorpseEntity corpse = new CorpseEntity(ModEntities.CORPSE, world);
		corpse.setPosition(mob.getX(), mob.getY(), mob.getZ());
		corpse.setYaw(mob.getYaw());
		corpse.setOwnerName(mob.getDisplayName().getString());
		TarkovLootGenerator.populateLootGrid(corpse.pocketsGrid, TarkovLootPool.SCAV_POCKET, world.getRandom());
		corpse.syncVisualEquipment();
		return corpse;
	}

	public static CorpseEntity fromBot(World world, TarkovScavEntity bot, PlayerLoadout loadout, List<ItemStack> overflow) {
		CorpseEntity corpse = new CorpseEntity(ModEntities.CORPSE, world);
		corpse.setPosition(bot.getPos());
		corpse.setYaw(bot.getYaw());
		corpse.setOwnerName(bot.getDisplayName().getString());
		corpse.dataTracker.set(CORPSE_KIND, (bot.isBoss() ? Kind.BOSS : Kind.SCAV).name());
		corpse.loadout = transferPlayerInventoryToCorpse(loadout, corpse.pocketsGrid, List.of(), overflow);
		corpse.syncVisualEquipment();
		return corpse;
	}

	public void populateScavLoot(Random random) {
		ItemStack helmet = TarkovLootGenerator.pickRandomLightHelmet(random);
		if (!helmet.isEmpty() && random.nextFloat() < 0.65f) {
			loadout.setEquipment(EquipmentSlotType.HELMET, helmet);
		}
		ItemStack armor = TarkovLootGenerator.pickRandomLightArmor(random);
		if (!armor.isEmpty() && random.nextFloat() < 0.6f) {
			loadout.setEquipment(EquipmentSlotType.BODY_ARMOR, armor);
		}

		ItemStack rig = TarkovLootGenerator.pickRandomScavRig(random);
		if (!rig.isEmpty()) {
			List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rig);
			for (GridContainer g : rigGrids) {
				TarkovLootGenerator.populateLootGrid(g, TarkovLootPool.SCAV_POCKET, random);
			}
			ContainerItemHelper.writeRigGrids(rig, rigGrids);
			markContainerUnsearched(rig);
			loadout.setEquipment(EquipmentSlotType.RIG, rig);
		}

		ItemStack bp = TarkovLootGenerator.pickRandomScavBackpack(random);
		if (!bp.isEmpty()) {
			GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
			TarkovLootGenerator.populateLootGrid(bpGrid, TarkovLootPool.CIVILIAN, random);
			ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
			markContainerUnsearched(bp);
			loadout.setEquipment(EquipmentSlotType.BACKPACK, bp);
		}

		TarkovLootGenerator.populateLootGrid(pocketsGrid, TarkovLootPool.SCAV_POCKET, random);
		syncVisualEquipment();
	}

	private static void markContainerUnsearched(ItemStack stack) {
		if (!stack.isEmpty() && ContainerItemHelper.isContainerItem(stack)) {
			ContainerItemHelper.setRequiresSearch(stack, true);
			ContainerItemHelper.saveSearchMemory(stack, new ContainerSearchMemory());
		}
	}

	public void syncVisualEquipment() {
		if (getWorld().isClient) return;
		for (EquipmentSlotType slot : VISIBLE_SLOTS) {
			TrackedData<ItemStack> tracked = GEAR.get(slot);
			ItemStack equipped = loadout.getEquipment(slot);
			ItemStack visible = dataTracker.get(tracked);
			if (!ItemStack.areItemsEqual(equipped, visible)) {
				// Worn models use the item ID. Container contents and search state stay in the loot UI.
				dataTracker.set(tracked, equipped.isEmpty() ? ItemStack.EMPTY : new ItemStack(equipped.getItem()));
			}
		}
	}

	@Override
	protected void initDataTracker() {
		dataTracker.startTracking(OWNER_NAME, "Scav");
		dataTracker.startTracking(OWNER_UUID, Optional.empty());
		dataTracker.startTracking(CORPSE_KIND, Kind.SCAV.name());
		dataTracker.startTracking(OWNER_PROFILE, new NbtCompound());
		for (EquipmentSlotType slot : VISIBLE_SLOTS) {
			dataTracker.startTracking(GEAR.get(slot), ItemStack.EMPTY);
		}
	}

	public String getOwnerName() {
		return this.dataTracker.get(OWNER_NAME);
	}

	@Nullable
	public UUID getOwnerUuid() {
		return dataTracker.get(OWNER_UUID).orElse(null);
	}

	public Kind getKind() {
		return Kind.fromName(dataTracker.get(CORPSE_KIND), Kind.SCAV);
	}

	@Nullable
	public GameProfile getOwnerProfile() {
		UUID ownerUuid = getOwnerUuid();
		if (ownerUuid == null) return null;
		NbtCompound profileTag = dataTracker.get(OWNER_PROFILE);
		if (cachedProfile == null || profileTag != cachedProfileTag || !ownerUuid.equals(cachedProfile.getId())) {
			GameProfile stored = NbtHelper.toGameProfile(profileTag);
			cachedProfile = stored != null && ownerUuid.equals(stored.getId())
				? stored : new GameProfile(ownerUuid, getOwnerName());
			cachedProfileTag = profileTag;
		}
		return cachedProfile;
	}

	public void setOwnerName(String name) {
		this.dataTracker.set(OWNER_NAME, name == null ? "Scav" : name);
	}

	public ItemStack getVisibleEquipment(EquipmentSlotType slot) {
		TrackedData<ItemStack> tracked = GEAR.get(slot);
		return tracked == null ? ItemStack.EMPTY : dataTracker.get(tracked);
	}

	public PlayerLoadout getLoadout() {
		return loadout;
	}

	public GridContainer getPocketsGrid() {
		return pocketsGrid;
	}

	public ContainerSearchMemory getPocketsSearchMemory() {
		return pocketsSearchMemory;
	}

	/**
	 * Ordered list of all searchable containers currently on this corpse:
	 * 1. Pockets (6x1)
	 * 2. Tactical Rig (all compartments, searched as one unit)
	 * 3. Backpack (its full WxH grid)
	 */
	public List<SearchTarget> buildCorpseSearchTargets() {
		List<SearchTarget> targets = new ArrayList<>();
		targets.add(new SearchTarget(pocketsGrid.getContainerId(), List.of(pocketsGrid), pocketsSearchMemory, () -> {}));

		ItemStack rig = loadout.getEquipment(EquipmentSlotType.RIG);
		if (!rig.isEmpty() && rig.getItem() instanceof TacticalRigItem && ContainerItemHelper.requiresSearch(rig)) {
			UUID rigId = ContainerItemHelper.getOrCreateContainerUuid(rig);
			List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rig);
			ContainerSearchMemory rigMem = ContainerItemHelper.getSearchMemory(rig);
			targets.add(new SearchTarget(rigId, rigGrids, rigMem, () -> {
				ContainerItemHelper.saveSearchMemory(rig, rigMem);
			}));
		}

		ItemStack bp = loadout.getEquipment(EquipmentSlotType.BACKPACK);
		if (!bp.isEmpty() && bp.getItem() instanceof BackpackItem && ContainerItemHelper.requiresSearch(bp)) {
			UUID bpId = ContainerItemHelper.getOrCreateContainerUuid(bp);
			GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
			ContainerSearchMemory bpMem = ContainerItemHelper.getSearchMemory(bp);
			targets.add(new SearchTarget(bpId, List.of(bpGrid), bpMem, () -> {
				ContainerItemHelper.saveSearchMemory(bp, bpMem);
			}));
		}

		return targets;
	}

	public boolean isCompletelyEmpty() {
		return loadout.isEmpty() && pocketsGrid.isEmpty();
	}

	@Override
	public boolean canHit() {
		return !this.isRemoved();
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		// Allow Creative-mode players to immediately punch/remove corpses!
		if (!this.getWorld().isClient && source.getAttacker() instanceof PlayerEntity p && p.isCreative()) {
			this.discard();
			return true;
		}
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.getWorld().isClient) {
			syncVisualEquipment();
			ageTicks++;
			if (isCompletelyEmpty()) {
				emptyTicks++;
				if (emptyTicks >= EMPTY_DESPAWN_TICKS) {
					this.discard();
					return;
				}
			} else {
				emptyTicks = 0;
			}
			if (ageTicks >= TarkovConfig.get().getCorpseLifetimeTicks()) {
				this.discard();
			}
		}
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		// Looting corpses is strictly via [F] interact, NOT right-click
		return ActionResult.PASS;
	}

	public void openForPlayer(ServerPlayerEntity player) {
		if (player.getUuid().equals(getOwnerUuid()) && RaidSession.isScav(player)) {
			player.sendMessage(Text.translatable("message.tarkovcraft.raid.own_corpse"), true);
			return;
		}
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
				buf.writeByte(TarkovScreenHandler.MODE_CORPSE);
				buf.writeInt(CorpseEntity.this.getId());
			}

			@Override
			public Text getDisplayName() {
				return Text.translatable("entity.tarkovcraft.corpse_title", getOwnerName());
			}

			@Nullable
			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return TarkovScreenHandler.forCorpse(syncId, inv, CorpseEntity.this);
			}
		});
		if (player.currentScreenHandler instanceof TarkovScreenHandler tsh) {
			tsh.syncToClient(player);
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("OwnerName", NbtElement.STRING_TYPE)) {
			setOwnerName(nbt.getString("OwnerName"));
		}
		Optional<UUID> ownerUuid = nbt.containsUuid("OwnerUuid") ? Optional.of(nbt.getUuid("OwnerUuid")) : Optional.empty();
		dataTracker.set(OWNER_UUID, ownerUuid);
		Kind defaultKind = ownerUuid.isPresent() ? Kind.PLAYER : Kind.SCAV;
		dataTracker.set(CORPSE_KIND, Kind.fromName(nbt.getString("CorpseKind"), defaultKind).name());
		dataTracker.set(OWNER_PROFILE, nbt.getCompound("OwnerProfile"));
		if (nbt.contains("Loadout", NbtElement.COMPOUND_TYPE)) {
			loadout.readNbt(nbt.getCompound("Loadout"));
		}
		if (nbt.contains("PocketsGrid", NbtElement.COMPOUND_TYPE)) {
			pocketsGrid = GridContainer.fromNbt(nbt.getCompound("PocketsGrid"), 6, 1);
		}
		if (nbt.contains("PocketsSearchMemory", NbtElement.COMPOUND_TYPE)) {
			pocketsSearchMemory = ContainerSearchMemory.fromNbt(nbt.getCompound("PocketsSearchMemory"));
		}
		ageTicks = nbt.getInt("AgeTicks");
		syncVisualEquipment();
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putString("OwnerName", getOwnerName());
		dataTracker.get(OWNER_UUID).ifPresent(uuid -> nbt.putUuid("OwnerUuid", uuid));
		nbt.putString("CorpseKind", getKind().name());
		if (!dataTracker.get(OWNER_PROFILE).isEmpty()) nbt.put("OwnerProfile", dataTracker.get(OWNER_PROFILE));
		nbt.put("Loadout", loadout.writeLootNbt());
		nbt.put("PocketsGrid", pocketsGrid.toNbt());
		nbt.put("PocketsSearchMemory", pocketsSearchMemory.toNbt());
		nbt.putInt("AgeTicks", ageTicks);
	}
}
