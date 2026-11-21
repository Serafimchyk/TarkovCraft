package com.tarkovcraft.loadout;

import com.tarkovcraft.health.BodyPartHealth;

public interface PlayerLoadoutHolder {
	PlayerLoadout tarkovcraft$getLoadout();

	void tarkovcraft$syncLoadout();

	BodyPartHealth tarkovcraft$getBodyHealth();

	void tarkovcraft$syncBodyHealth();
}
