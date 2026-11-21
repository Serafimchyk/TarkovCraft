package com.tarkovcraft.entity;

import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.loadout.EquipmentSlotType;
import com.tarkovcraft.loadout.PlayerLoadout;
import com.tarkovcraft.registry.ModEntities;
import com.tarkovcraft.registry.ModItems;
import com.tarkovcraft.screen.TarkovScreenHandler;
import com.tarkovcraft.search.ContainerSearchMemory;
import com.tarkovcraft.search.SearchTarget;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lootable corpse spawned on death of a player or humanoid mob.
 * NO fake generic container: holds the deceased's REAL equipment, REAL 6x(1x1) pockets,
 * REAL Tactical Rig (with its sub-grids), and REAL Backpack (with its grid).
 * Everything on the corpse searches automatically in order: Pockets -> Rig -> Backpack.
 * Both the Rig and Backpack can be stripped whole at any time (even unsearched).
 */
public class CorpseEntity extends Entity {
	private static final int MAX_LIFETIME_TICKS = 20 * 60 * 15; // 15 minutes
	private static final int EMPTY_DESPAWN_TICKS = 20 * 10;     // 10 seconds once completely looted
	private static final TrackedData<String> OWNER_NAME = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.STRING);
	private static final TrackedData<Boolean> HAS_BACKPACK = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> HAS_HELMET = DataTracker.registerData(CorpseEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

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
		CorpseEntity corpse = new CorpseEntity(ModEntities.CORPSE, world);
		corpse.setPosition(player.getX(), player.getY(), player.getZ());
		corpse.setYaw(player.getYaw());
		corpse.setOwnerName(player.getName().getString());

		PlayerLoadout copy = sourceLoadout.copy();
		for (int i = 0; i < PlayerLoadout.POCKET_COUNT; i++) {
			ItemStack p = copy.getPocket(i);
			if (!p.isEmpty()) {
				corpse.pocketsGrid.place(p.copy(), i, 0, false);
				copy.setPocket(i, ItemStack.EMPTY);
			}
		}
		markContainerUnsearched(copy.getEquipment(EquipmentSlotType.BACKPACK));
		markContainerUnsearched(copy.getEquipment(EquipmentSlotType.RIG));
		corpse.loadout = copy;
		corpse.syncVisualFlags();
		return corpse;
	}

	public static CorpseEntity fromMob(World world, LivingEntity mob) {
		CorpseEntity corpse = new CorpseEntity(ModEntities.CORPSE, world);
		corpse.setPosition(mob.getX(), mob.getY(), mob.getZ());
		corpse.setYaw(mob.getYaw());
		corpse.setOwnerName(mob.getDisplayName().getString());
		corpse.populateScavLoot(world.getRandom());
		return corpse;
	}

	public void populateScavLoot(Random random) {
		// Helmet & Armor occasionally
		if (random.nextFloat() < 0.6f) {
			loadout.setEquipment(EquipmentSlotType.HELMET, new ItemStack(ModItems.SSH68_HELMET));
		}
		if (random.nextFloat() < 0.5f) {
			loadout.setEquipment(EquipmentSlotType.BODY_ARMOR, new ItemStack(ModItems.PACA_ARMOR));
		}

		// Equip an unsearched Tactical Rig with REAL loot inside its compartments
		ItemStack rig = new ItemStack(random.nextBoolean() ? ModItems.SCAV_VEST : ModItems.BANK_ROBBER_RIG);
		List<GridContainer> rigGrids = ContainerItemHelper.readRigGrids(rig);
		if (!rigGrids.isEmpty()) {
			rigGrids.get(0).tryAutoPlace(new ItemStack(Items.ARROW, 8 + random.nextInt(8)));
		}
		if (rigGrids.size() > 1) {
			rigGrids.get(1).tryAutoPlace(new ItemStack(Items.BREAD, 1 + random.nextInt(2)));
		}
		if (rigGrids.size() > 2 && random.nextFloat() < 0.5f) {
			rigGrids.get(2).tryAutoPlace(new ItemStack(Items.GOLD_NUGGET, 3 + random.nextInt(5)));
		}
		ContainerItemHelper.writeRigGrids(rig, rigGrids);
		ContainerItemHelper.setRequiresSearch(rig, true);
		ContainerItemHelper.saveSearchMemory(rig, new ContainerSearchMemory());
		loadout.setEquipment(EquipmentSlotType.RIG, rig);

		// Equip an unsearched Backpack with REAL loot inside
		ItemStack bp = new ItemStack(random.nextBoolean() ? ModItems.BERKUT_BACKPACK : ModItems.SLING_BACKPACK);
		GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bp);
		bpGrid.tryAutoPlace(new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(4)));
		bpGrid.tryAutoPlace(new ItemStack(Items.COOKED_BEEF, 1 + random.nextInt(3)));
		if (random.nextFloat() < 0.5f) {
			bpGrid.tryAutoPlace(new ItemStack(Items.DIAMOND, 1));
		}
		if (random.nextFloat() < 0.35f) {
			bpGrid.tryAutoPlace(new ItemStack(Items.GOLDEN_APPLE, 1));
		}
		ContainerItemHelper.writeBackpackGrid(bp, bpGrid);
		ContainerItemHelper.setRequiresSearch(bp, true);
		ContainerItemHelper.saveSearchMemory(bp, new ContainerSearchMemory());
		loadout.setEquipment(EquipmentSlotType.BACKPACK, bp);

		// Real pockets (6x 1x1)
		pocketsGrid.place(new ItemStack(Items.APPLE, 1 + random.nextInt(2)), 0, 0, false);
		pocketsGrid.place(new ItemStack(Items.EMERALD, 1), 1, 0, false);
		syncVisualFlags();
	}

	private static void markContainerUnsearched(ItemStack stack) {
		if (!stack.isEmpty() && ContainerItemHelper.isContainerItem(stack)) {
			ContainerItemHelper.setRequiresSearch(stack, true);
			ContainerItemHelper.saveSearchMemory(stack, new ContainerSearchMemory());
		}
	}

	public void syncVisualFlags() {
		this.dataTracker.set(HAS_BACKPACK, !loadout.getEquipment(EquipmentSlotType.BACKPACK).isEmpty());
		this.dataTracker.set(HAS_HELMET, !loadout.getEquipment(EquipmentSlotType.HELMET).isEmpty());
	}

	@Override
	protected void initDataTracker() {
		this.dataTracker.startTracking(OWNER_NAME, "Scav");
		this.dataTracker.startTracking(HAS_BACKPACK, false);
		this.dataTracker.startTracking(HAS_HELMET, false);
	}

	public String getOwnerName() {
		return this.dataTracker.get(OWNER_NAME);
	}

	public void setOwnerName(String name) {
		this.dataTracker.set(OWNER_NAME, name == null ? "Scav" : name);
	}

	public boolean hasBackpackVisual() {
		return this.dataTracker.get(HAS_BACKPACK);
	}

	public boolean hasHelmetVisual() {
		return this.dataTracker.get(HAS_HELMET);
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
			if (ageTicks >= MAX_LIFETIME_TICKS) {
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
		syncVisualFlags();
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putString("OwnerName", getOwnerName());
		nbt.put("Loadout", loadout.writeNbt());
		nbt.put("PocketsGrid", pocketsGrid.toNbt());
		nbt.put("PocketsSearchMemory", pocketsSearchMemory.toNbt());
		nbt.putInt("AgeTicks", ageTicks);
	}
}
