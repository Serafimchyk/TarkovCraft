package com.tarkovcraft.block;

import com.tarkovcraft.config.TarkovConfig;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.loot.TarkovLootGenerator;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.screen.TarkovScreenHandler;
import com.tarkovcraft.search.ContainerSearchMemory;
import com.tarkovcraft.search.SearchTarget;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

public class SearchableLootBlockEntity extends BlockEntity {
	private static final DustParticleEffect RED_FLARE_DUST =
		new DustParticleEffect(new Vector3f(0.92f, 0.14f, 0.14f), 2.5f);

	private int defaultWidth;
	private int defaultHeight;
	private GridContainer grid;
	private ContainerSearchMemory searchMemory = new ContainerSearchMemory();
	private int smokeTicksRemaining = 0;
	@Nullable
	private TarkovLootPool lootPool = null;
	private boolean pendingLootGeneration = false;
	private long lastLootEpochMs = 0L;
	private boolean autoRespawn = true;

	public SearchableLootBlockEntity(BlockPos pos, BlockState state) {
		this(ModBlocks.LOOT_CONTAINER_ENTITY, pos, state,
			state.getBlock() instanceof SearchableLootBlock b ? b.getGridWidth() : 4,
			state.getBlock() instanceof SearchableLootBlock b ? b.getGridHeight() : 4);
	}

	public SearchableLootBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int width, int height) {
		super(type, pos, state);
		this.defaultWidth = Math.max(1, width);
		this.defaultHeight = Math.max(1, height);
		this.grid = new GridContainer(this.defaultWidth, this.defaultHeight);
	}

	public GridContainer getGrid() {
		return grid;
	}

	public ContainerSearchMemory getSearchMemory() {
		return searchMemory;
	}

	public SearchTarget asSearchTarget() {
		return new SearchTarget(grid.getContainerId(), List.of(grid), searchMemory, this::markDirty);
	}

	public int getSmokeTicksRemaining() {
		return smokeTicksRemaining;
	}

	public void setSmokeTicksRemaining(int smokeTicksRemaining) {
		this.smokeTicksRemaining = Math.max(0, smokeTicksRemaining);
		markDirty();
	}

	@Nullable
	public TarkovLootPool getLootPool() {
		return lootPool;
	}

	public TarkovLootPool getEffectiveLootPool() {
		if (lootPool != null) {
			return lootPool;
		}
		if (getCachedState().getBlock() instanceof SearchableLootBlock lootBlock) {
			return lootBlock.getDefaultLootPool();
		}
		return TarkovLootPool.MILITARY;
	}

	public void setLootPool(@Nullable TarkovLootPool lootPool) {
		this.lootPool = lootPool;
		markDirty();
	}

	public boolean isPendingLootGeneration() {
		return pendingLootGeneration;
	}

	public void setPendingLootGeneration(boolean pendingLootGeneration) {
		this.pendingLootGeneration = pendingLootGeneration;
		markDirty();
	}

	public void resizeGrid(int width, int height) {
		int clampedW = Math.max(1, Math.min(10, width));
		int clampedH = Math.max(1, Math.min(10, height));
		this.defaultWidth = clampedW;
		this.defaultHeight = clampedH;
		this.grid = new GridContainer(clampedW, clampedH);
		this.searchMemory = new ContainerSearchMemory();
		markDirty();
	}

	public void populateFromPool(TarkovLootPool pool, Random random) {
		TarkovLootPool effective = pool != null ? pool : getEffectiveLootPool();
		this.lootPool = effective;
		this.pendingLootGeneration = false;
		this.lastLootEpochMs = System.currentTimeMillis();
		this.grid.clear();
		this.searchMemory = new ContainerSearchMemory();
		TarkovLootGenerator.populateLootGrid(this.grid, effective, random);
		markDirty();
	}

	public long getLastLootEpochMs() {
		return lastLootEpochMs;
	}

	public void setLastLootEpochMs(long lastLootEpochMs) {
		this.lastLootEpochMs = Math.max(0L, lastLootEpochMs);
		markDirty();
	}

	public boolean isAutoRespawn() {
		return autoRespawn;
	}

	public void setAutoRespawn(boolean autoRespawn) {
		this.autoRespawn = autoRespawn;
		markDirty();
	}

	public static boolean isRespawnDue(long lastLootEpochMs) {
		long intervalMs = TarkovConfig.get().getLootRespawnMillis();
		if (intervalMs <= 0L || lastLootEpochMs <= 0L) {
			return false;
		}
		return (System.currentTimeMillis() - lastLootEpochMs) >= intervalMs;
	}

	public void refillLoot(Random random) {
		populateFromPool(getEffectiveLootPool(), random);
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, SearchableLootBlockEntity be) {
		if (!(world instanceof ServerWorld sw) || be.smokeTicksRemaining <= 0) {
			return;
		}
		be.smokeTicksRemaining--;
		if (be.smokeTicksRemaining % 3 == 0) {
			double cx = pos.getX() + 0.5;
			double cy = pos.getY() + 1.05;
			double cz = pos.getZ() + 0.5;
			double pillarHeight = (be.smokeTicksRemaining % 12) * 0.9;
			sw.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, cx, cy, cz, 2, 0.15, 0.2, 0.15, 0.06);
			sw.spawnParticles(RED_FLARE_DUST, cx, cy + 0.15 + pillarHeight, cz, 4, 0.25, 0.35, 0.25, 0.01);
		}
		if (be.smokeTicksRemaining == 0) {
			be.markDirty();
		}
	}

	public Text getDisplayTitle() {
		if (lootPool != null) {
			return Text.translatable("container.tarkovcraft.loot_crate_typed", Text.translatable(lootPool.getTranslationKey()));
		}
		if (getCachedState().getBlock() instanceof SearchableLootBlock lootBlock) {
			return Text.translatable(lootBlock.getContainerTitleKey());
		}
		return Text.translatable("container.tarkovcraft.loot_crate");
	}

	public void openForPlayer(ServerPlayerEntity player) {
		if (world != null) {
			if (pendingLootGeneration) {
				populateFromPool(getEffectiveLootPool(), world.getRandom());
			} else if (autoRespawn && isRespawnDue(lastLootEpochMs)) {
				populateFromPool(getEffectiveLootPool(), world.getRandom());
			}
		}
		if (smokeTicksRemaining > 0) {
			smokeTicksRemaining = 0;
			markDirty();
		}
		player.openHandledScreen(new ExtendedScreenHandlerFactory() {
			@Override
			public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
				buf.writeByte(TarkovScreenHandler.MODE_LOOT_BLOCK);
				buf.writeBlockPos(pos);
			}

			@Override
			public Text getDisplayName() {
				return getDisplayTitle();
			}

			@Nullable
			@Override
			public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
				return TarkovScreenHandler.forLootBlock(syncId, inv, SearchableLootBlockEntity.this);
			}
		});
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		nbt.putInt("DefaultW", defaultWidth);
		nbt.putInt("DefaultH", defaultHeight);
		nbt.put("Grid", grid.toNbt());
		nbt.put("SearchMemory", searchMemory.toNbt());
		nbt.putInt("SmokeTicks", smokeTicksRemaining);
		if (lootPool != null) {
			nbt.putString("LootPool", lootPool.getId());
		}
		if (pendingLootGeneration) {
			nbt.putBoolean("PendingLoot", true);
		}
		if (lastLootEpochMs > 0L) {
			nbt.putLong("LastLootEpochMs", lastLootEpochMs);
		}
		nbt.putBoolean("AutoRespawn", autoRespawn);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		if (nbt.contains("DefaultW")) {
			this.defaultWidth = Math.max(1, Math.min(10, nbt.getInt("DefaultW")));
		}
		if (nbt.contains("DefaultH")) {
			this.defaultHeight = Math.max(1, Math.min(10, nbt.getInt("DefaultH")));
		}
		if (nbt.contains("Grid", NbtElement.COMPOUND_TYPE)) {
			this.grid = GridContainer.fromNbt(nbt.getCompound("Grid"), defaultWidth, defaultHeight);
		} else {
			this.grid = new GridContainer(defaultWidth, defaultHeight);
		}
		if (nbt.contains("SearchMemory", NbtElement.COMPOUND_TYPE)) {
			this.searchMemory = ContainerSearchMemory.fromNbt(nbt.getCompound("SearchMemory"));
		} else {
			this.searchMemory = new ContainerSearchMemory();
		}
		this.smokeTicksRemaining = Math.max(0, nbt.getInt("SmokeTicks"));
		if (nbt.contains("LootPool", NbtElement.STRING_TYPE)) {
			this.lootPool = TarkovLootPool.byId(nbt.getString("LootPool"));
		} else {
			this.lootPool = null;
		}
		this.pendingLootGeneration = nbt.getBoolean("PendingLoot");
		this.lastLootEpochMs = Math.max(0L, nbt.getLong("LastLootEpochMs"));
		this.autoRespawn = !nbt.contains("AutoRespawn") || nbt.getBoolean("AutoRespawn");
	}
}
