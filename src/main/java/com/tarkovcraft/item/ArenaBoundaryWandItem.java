package com.tarkovcraft.item;

import com.tarkovcraft.arena.ArenaBoundaryEditing;
import com.tarkovcraft.network.ArenaAdminPackets.Key;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public final class ArenaBoundaryWandItem extends Item {
	public ArenaBoundaryWandItem() {
		super(new Settings().maxCount(1));
	}

	public static void bind(ItemStack stack, Key key) {
		var tag = stack.getOrCreateNbt();
		tag.putString("arenaId", key.id());
		tag.putString("arenaDimension", key.dimension().toString());
	}

	public static Key binding(ItemStack stack) {
		var tag = stack.getNbt();
		if (!stack.isOf(ModItems.ARENA_BOUNDARY_WAND) || tag == null) return null;
		Identifier dimension = Identifier.tryParse(tag.getString("arenaDimension"));
		String id = tag.getString("arenaId");
		return dimension == null || id.isEmpty() ? null : new Key(dimension, id);
	}

	public static ItemStack held(PlayerEntity player) {
		ItemStack main = player.getMainHandStack();
		return main.isOf(ModItems.ARENA_BOUNDARY_WAND) ? main : player.getOffHandStack().isOf(ModItems.ARENA_BOUNDARY_WAND)
			? player.getOffHandStack() : ItemStack.EMPTY;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (!player.hasPermissionLevel(2)) return TypedActionResult.fail(stack);
		if (player instanceof ServerPlayerEntity admin) {
			if (player.isSneaking()) ArenaBoundaryEditing.clear(admin, stack);
			else ArenaBoundaryEditing.cycle(admin, stack, 1);
		}
		return TypedActionResult.success(stack, world.isClient);
	}
}
