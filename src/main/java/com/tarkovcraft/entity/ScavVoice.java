package com.tarkovcraft.entity;

import com.tarkovcraft.TarkovCraftMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.Locale;

enum ScavVoice {
	DETECTED(4), WOUNDED(3), NOISE(2), RELOAD(2);

	private final SoundEvent[] lines;

	ScavVoice(int count) {
		lines = new SoundEvent[count];
		for (int i = 0; i < count; i++) {
			Identifier id = new Identifier(TarkovCraftMod.MOD_ID, "scav." + name().toLowerCase(Locale.ROOT) + "." + (i + 1));
			lines[i] = Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id, 28.0f));
		}
	}

	static void initialize() {
	}

	void play(TarkovScavEntity bot) {
		bot.getWorld().playSound(null, bot.getX(), bot.getEyeY(), bot.getZ(),
			lines[bot.getRandom().nextInt(lines.length)], SoundCategory.VOICE, 1.0f, bot.isBoss() ? 0.84f : 1.0f);
	}
}
