package com.tarkovcraft.loadout;

import com.tarkovcraft.health.BodyPartHealth;

public interface PlayerLoadoutHolder {
	PlayerLoadout tarkovcraft$getLoadout();

	void tarkovcraft$syncLoadout();

	void tarkovcraft$syncLoadoutIfChanged();

	BodyPartHealth tarkovcraft$getBodyHealth();

	void tarkovcraft$syncBodyHealth();
}
