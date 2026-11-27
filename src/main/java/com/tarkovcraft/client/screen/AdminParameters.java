package com.tarkovcraft.client.screen;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.block.SearchableLootBlock;
import com.tarkovcraft.client.screen.AdminCommands.Action;
import com.tarkovcraft.client.screen.AdminCommands.Parameter;
import com.tarkovcraft.client.screen.TarkovDropdownWidget.Option;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.health.HitZone;
import com.tarkovcraft.health.InjuryType;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.worldgen.TarkovStructureFeature.StructureKind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class AdminParameters {
	private AdminParameters() { }

	record NumberControl(double min, double max, double initial, double step, boolean integer, List<Integer> presets) {
		String value(double number) {
			double bounded = Math.max(min, Math.min(max, number));
			return integer ? Integer.toString((int) bounded) : Float.toString((float) bounded);
		}
	}

	static NumberControl number(Parameter parameter, int y, TarkovLootPool pool) {
		int initial = switch (parameter.name()) {
			case "radius" -> 16;
			case "minutes" -> 15;
			case "damage" -> 25;
			case "penetration" -> 25;
			case "width" -> pool.getDefaultGridWidth();
			case "height" -> pool.getDefaultGridHeight();
			case "y" -> y;
			default -> 1;
		};
		List<Integer> presets = switch (parameter.name()) {
			case "radius" -> List.of(16, 32, 64, 128);
			case "minutes" -> List.of(5, 15, 30, 60);
			case "damage" -> List.of(10, 25, 50, 100);
			case "penetration" -> List.of(0, 25, 50, 100);
			case "width", "height" -> List.of(2, 4, 6, 10);
			case "y" -> List.of(y, 64, 128, 256).stream().distinct().toList();
			default -> List.of(1, 2, 3, 4);
		};
		if (parameter.type() instanceof IntegerArgumentType type) {
			return new NumberControl(type.getMinimum(), type.getMaximum(), initial, 1, true,
				presets.stream().filter(value -> value >= type.getMinimum() && value <= type.getMaximum()).toList());
		}
		if (parameter.type() instanceof FloatArgumentType type) {
			return new NumberControl(type.getMinimum(), type.getMaximum(), initial, 0.5, false,
				presets.stream().filter(value -> value >= type.getMinimum() && value <= type.getMaximum()).toList());
		}
		return null;
	}

	static List<Option> choices(Action action, Parameter parameter, MinecraftClient client, List<Option> arenas, String draftId) {
		BlockPos pos = client.player == null ? BlockPos.ORIGIN : client.player.getBlockPos();
		return switch (parameter.name()) {
			case "id" -> action.key().equals("arena create") ? List.of(new Option(draftId, draftId)) : arenas;
			case "arena" -> arenas;
			case "pool" -> pools(action.key().equals("city scatter"));
			case "block" -> blocks();
			case "part" -> Arrays.stream(BodyPart.values())
				.filter(part -> !action.key().equals("fracture") || BodyPartHealth.isLimb(part))
				.map(part -> localized(part.getId(), part.getTranslationKey())).toList();
			case "zone" -> Arrays.stream(HitZone.values()).map(zone -> localized(zone.getId(), zone.getTranslationKey())).toList();
			case "type" -> action.group() == AdminCommands.Group.ARENA
				? List.of(new Option("quiet", "Тихий"), new Option("loud", "Громкий"))
				: Arrays.stream(InjuryType.values()).filter(type -> type.getId().endsWith("_bleed"))
					.map(type -> localized(type.getId().replace("_bleed", ""), type.getTranslationKey())).toList();
			case "enabled" -> List.of(new Option("on", "Вкл."), new Option("off", "Выкл."));
			case "structure" -> Stream.concat(Stream.of(new Option("any", "Любая")),
				Arrays.stream(StructureKind.values()).map(kind -> kind.name().toLowerCase(Locale.ROOT))
					.map(id -> localized(id, "structure.tarkovcraft." + id))).toList();
			case "players" -> players(client);
			case "name" -> List.of(new Option("extract_" + coordinates(pos), "Выход · " + pos.toShortString()),
				new Option("gate_" + coordinates(pos), "Ворота · " + pos.toShortString()),
				new Option("checkpoint_" + coordinates(pos), "КПП · " + pos.toShortString()));
			default -> List.of();
		};
	}

	private static String coordinates(BlockPos pos) {
		return pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
	}

	private static Option localized(String value, String key) {
		return new Option(value, Text.translatableWithFallback(key, value).getString());
	}

	private static List<Option> pools(boolean auto) {
		Stream<Option> choices = Arrays.stream(TarkovLootPool.values()).map(pool ->
			new Option(pool.getId(), Text.translatableWithFallback(pool.getTranslationKey(), pool.getId()).getString()
				+ " · " + pool.getId() + " · " + pool.getDefaultGridWidth() + "×" + pool.getDefaultGridHeight()));
		return (auto ? Stream.concat(Stream.of(new Option("auto", "Авто по зоне · auto")), choices) : choices).toList();
	}

	private static List<Option> blocks() {
		return Stream.concat(Stream.of(new Option("default", "По таблице лута")), Registries.BLOCK.stream()
			.filter(SearchableLootBlock.class::isInstance).map(block -> {
				var id = Registries.BLOCK.getId(block);
				String value = TarkovCraftMod.MOD_ID.equals(id.getNamespace()) ? id.getPath() : id.toString();
				return new Option(value, block.getName().getString(), new ItemStack(block));
			})).toList();
	}

	private static List<Option> players(MinecraftClient client) {
		var network = client.getNetworkHandler();
		Stream<Option> online = network == null ? Stream.empty() : network.getPlayerList().stream()
			.map(entry -> entry.getProfile().getName()).distinct().sorted().map(name -> new Option(name, name));
		return Stream.concat(Stream.of(new Option("@s", "Я"), new Option("@a", "Все онлайн")), online).toList();
	}
}
