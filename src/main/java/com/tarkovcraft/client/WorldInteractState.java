package com.tarkovcraft.client;

import com.tarkovcraft.item.BackpackItem;
import com.tarkovcraft.item.TacticalRigItem;
import com.tarkovcraft.item.TarkovCaseItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks the currently targeted world interactable and the scroll-wheel selected action
 * for multi-action items on the ground (Backpacks & Tactical Rigs):
 *   0 = Search (default, top)
 *   1 = Equip / Take (scroll down, bottom)
 */
public final class WorldInteractState {
	public static final int ACTION_SEARCH = 0;
	public static final int ACTION_EQUIP_OR_TAKE = 1;

	private static int lastTargetEntityId = -1;
	private static boolean multiActionActive = false;
	private static int selectedAction = ACTION_SEARCH;

	private WorldInteractState() {
	}

	public static void updateTarget(@Nullable Entity entity) {
		if (entity instanceof ItemEntity ie
			&& !ie.getStack().isEmpty()
			&& (ie.getStack().getItem() instanceof BackpackItem
			|| ie.getStack().getItem() instanceof TacticalRigItem
			|| ie.getStack().getItem() instanceof TarkovCaseItem)) {
			if (ie.getId() != lastTargetEntityId) {
				lastTargetEntityId = ie.getId();
				selectedAction = ACTION_SEARCH; // Default top option: Search
			}
			multiActionActive = true;
		} else {
			lastTargetEntityId = -1;
			multiActionActive = false;
			selectedAction = ACTION_SEARCH;
		}
	}

	public static boolean hasMultiActionMenu() {
		return multiActionActive;
	}

	public static int getSelectedAction() {
		return multiActionActive ? selectedAction : ACTION_SEARCH;
	}

	public static void scrollAction(int delta) {
		if (!multiActionActive) return;
		selectedAction = Math.max(ACTION_SEARCH, Math.min(ACTION_EQUIP_OR_TAKE, selectedAction + delta));
	}
}
