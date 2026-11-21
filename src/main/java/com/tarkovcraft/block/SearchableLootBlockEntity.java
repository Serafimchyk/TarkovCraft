package com.tarkovcraft.block;

import com.tarkovcraft.grid.GridContainer;
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
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SearchableLootBlockEntity extends BlockEntity {
	private int defaultWidth;
	private int defaultHeight;
	private GridContainer grid;
	private ContainerSearchMemory searchMemory = new ContainerSearchMemory();

	public SearchableLootBlockEntity(BlockPos pos, BlockState state) {
		this(ModBlocks.LOOT_CONTAINER_ENTITY, pos, state,
			state.getBlock() instanceof SearchableLootBlock b ? b.getGridWidth() : 4,
			state.getBlock() instanceof SearchableLootBlock b ? b.getGridHeight() : 4);
	}

	public SearchableLootBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int width, int height) {
		super(type, pos, state);
		this.defaultWidth = width;
		this.defaultHeight = height;
		this.grid = new GridContainer(width, height);
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

	public Text getDisplayTitle() {
		if (getCachedState().getBlock() instanceof SearchableLootBlock lootBlock) {
			return Text.translatable(lootBlock.getContainerTitleKey());
		}
		return Text.translatable("container.tarkovcraft.loot_crate");
	}

	public void openForPlayer(ServerPlayerEntity player) {
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
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		if (nbt.contains("DefaultW")) {
			this.defaultWidth = Math.max(1, nbt.getInt("DefaultW"));
		}
		if (nbt.contains("DefaultH")) {
			this.defaultHeight = Math.max(1, nbt.getInt("DefaultH"));
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
	}
}
