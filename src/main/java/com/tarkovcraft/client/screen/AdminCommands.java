package com.tarkovcraft.client.screen;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.command.CommandSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
final class AdminCommands {
	enum Group {
		ALL("Все"), ARENA("Арены"), RAID("Рейды"), CRATE("Лут"), CITY("Город"),
		AIRDROP("Аирдропы"), HEALTH("Здоровье"), GEAR("Снаряжение"), WORLD("Мир"), SYSTEM("Система");

		final String title;

		Group(String title) {
			this.title = title;
		}
	}

	private static final Map<String, String> COMMAND_NAMES = Map.ofEntries(
		Map.entry("arena create", "Создать арену"),
		Map.entry("arena set_corner", "Отметить вершину арены"),
		Map.entry("arena wand", "Выдать маркер границ"),
		Map.entry("arena set_duration", "Задать длительность рейда"),
		Map.entry("arena add_spawn", "Добавить спавн ЧВК"),
		Map.entry("arena add_scav_spawn", "Добавить спавн Диких"),
		Map.entry("arena add_loot_point", "Добавить точку лута"),
		Map.entry("arena add_extract", "Добавить экстракт"),
		Map.entry("arena spawn_scav", "Заспавнить бота Дикого"),
		Map.entry("arena set_lobby_anchor", "Установить точку лобби"),
		Map.entry("arena add_airdrop_point", "Добавить точку аирдропа"),
		Map.entry("arena add_poi", "Добавить точку интереса"),
		Map.entry("arena set_max_ground_y", "Ограничить высоту аирдропов"),
		Map.entry("arena set_airdrops", "Настроить аирдропы арены"),
		Map.entry("arena set_boss", "Настроить босса арены"),
		Map.entry("arena reset_boss", "Сбросить появление босса"),
		Map.entry("raid start", "Запустить рейд"),
		Map.entry("raid stop", "Завершить рейд"),
		Map.entry("raid", "Открыть лобби или статус рейда"),
		Map.entry("raid scav", "Зайти за Дикого"),
		Map.entry("crate list", "Показать таблицы лута"),
		Map.entry("crate give", "Выдать ящик лута"),
		Map.entry("crate spawn", "Поставить ящик лута"),
		Map.entry("crate set", "Изменить таблицу лута ящика"),
		Map.entry("crate refill", "Пополнить ящики в радиусе"),
		Map.entry("loot", "Поставить ящик лута"),
		Map.entry("city respawn", "Задать интервал обновления лута"),
		Map.entry("city autoloot on", "Включить городской лут"),
		Map.entry("city autoloot off", "Выключить городской лут"),
		Map.entry("city zone", "Назначить таблицу лута зоне"),
		Map.entry("city scatter", "Расставить ящики в городе"),
		Map.entry("airdrop", "Вызвать аирдроп поблизости"),
		Map.entry("airdrop here", "Вызвать аирдроп в этой точке"),
		Map.entry("airdrop interval", "Изменить интервал аирдропов"),
		Map.entry("corpse", "Создать тестовый труп"),
		Map.entry("clear_corpses", "Удалить трупы"),
		Map.entry("locate", "Найти структуру"),
		Map.entry("kit", "Надеть стартовое снаряжение"),
		Map.entry("meds", "Выдать набор медикаментов"),
		Map.entry("clear_loadout", "Очистить инвентарь"),
		Map.entry("heal", "Восстановить здоровье"),
		Map.entry("bleed", "Наложить кровотечение"),
		Map.entry("fracture", "Нанести перелом"),
		Map.entry("hit", "Симулировать попадание"),
		Map.entry("reload", "Перезагрузить конфигурацию"),
		Map.entry("admin", "Открыть панель администратора")
	);

	private static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
		Map.entry("arena create", "Создать арену с опорной точкой на вашей позиции."),
		Map.entry("arena set_corner", "Записать вашу позицию как вершину полигона по её порядковому номеру."),
		Map.entry("arena wand", "Выдать маркер, привязанный к выбранной арене."),
		Map.entry("arena set_duration", "Установить лимит времени для рейда на арене."),
		Map.entry("arena add_spawn", "Записать вашу позицию как точку появления ЧВК."),
		Map.entry("arena add_scav_spawn", "Записать вашу позицию как точку появления Диких."),
		Map.entry("arena add_loot_point", "Добавить точку для генерации лута внутри арены."),
		Map.entry("arena add_extract", "Создать точку выхода на вашей позиции."),
		Map.entry("arena spawn_scav", "Создать вооружённого Дикого на указанных координатах."),
		Map.entry("arena set_lobby_anchor", "Назначить вашу позицию точкой лобби."),
		Map.entry("arena add_airdrop_point", "Отметить ровную площадку под открытым небом."),
		Map.entry("arena add_poi", "Отметить позицию ящика внутри арены."),
		Map.entry("arena set_max_ground_y", "Ограничить высоту площадок для аирдропов."),
		Map.entry("arena set_airdrops", "Включить или отключить аирдропы на выбранной арене."),
		Map.entry("arena set_boss", "Настроить появление босса на выбранной арене."),
		Map.entry("arena reset_boss", "Разрешить повторное появление босса на арене."),
		Map.entry("raid start", "Запустить рейд на выбранной арене для участников."),
		Map.entry("raid stop", "Досрочно завершить рейд. Оставшиеся участники получат итог «Пропал без вести»."),
		Map.entry("raid", "Открыть лобби или посмотреть статус текущего рейда."),
		Map.entry("raid scav", "Присоединиться к рейду в роли Дикого."),
		Map.entry("crate list", "Показать в чате таблицы лута и размеры сеток."),
		Map.entry("crate give", "Получить ящик с выбранной таблицей, видом и сеткой."),
		Map.entry("crate spawn", "Поставить ящик на вашей позиции."),
		Map.entry("crate set", "Назначить таблицу ящику, на который вы смотрите."),
		Map.entry("crate refill", "Обновить содержимое ящиков вокруг вас."),
		Map.entry("city respawn", "Установить интервал обновления городских ящиков."),
		Map.entry("city autoloot on", "Включить автоматический лут в городских контейнерах."),
		Map.entry("city autoloot off", "Отключить автоматический лут в городских контейнерах."),
		Map.entry("city zone", "Назначить таблицу существующим контейнерам вокруг вас."),
		Map.entry("city scatter", "Расставить ящики в зданиях вокруг вашей позиции."),
		Map.entry("airdrop", "Найти площадку и запустить доставку поблизости."),
		Map.entry("airdrop here", "Запустить доставку на вашей позиции."),
		Map.entry("airdrop interval", "Установить период автоматической доставки."),
		Map.entry("corpse", "Создать тестовый труп рядом с вами."),
		Map.entry("clear_corpses", "Удалить трупы в текущем измерении."),
		Map.entry("locate", "Показать координаты ближайшей сгенерированной структуры."),
		Map.entry("kit", "Экипировать стартовую броню, разгрузку и рюкзак."),
		Map.entry("meds", "Получить набор для лечения и первой помощи."),
		Map.entry("clear_loadout", "Удалить предметы из инвентаря, снаряжения и подсумка."),
		Map.entry("heal", "Восстановить здоровье всех частей тела."),
		Map.entry("bleed", "Наложить выбранное кровотечение на часть тела."),
		Map.entry("fracture", "Наложить перелом на выбранную конечность."),
		Map.entry("hit", "Проверить попадание с выбранным уроном и пробитием."),
		Map.entry("reload", "Перечитать конфигурацию мода с диска.")
	);

	private static final Map<String, String> PARAMETER_NAMES = Map.ofEntries(
		Map.entry("id", "ID арены"), Map.entry("arena", "ID арены"), Map.entry("displayName", "Название арены"),
		Map.entry("corner", "Номер угла"), Map.entry("name", "Название экстракта"), Map.entry("type", "Тип"),
		Map.entry("minutes", "Минуты"), Map.entry("enabled", "Режим"), Map.entry("y", "Высота Y"),
		Map.entry("players", "Участники"), Map.entry("pool", "Таблица лута"), Map.entry("block", "Вид ящика"),
		Map.entry("width", "Ширина ящика"), Map.entry("height", "Высота ящика"), Map.entry("radius", "Радиус в блоках"),
		Map.entry("structure", "Тип структуры"), Map.entry("zone", "Зона попадания"), Map.entry("part", "Часть тела"),
		Map.entry("damage", "Урон"), Map.entry("penetration", "Пробитие")
	);

	private static final Set<String> CONFIRM = Set.of(
		"raid start", "arena set_lobby_anchor", "arena set_corner", "city zone", "city scatter", "city autoloot on",
		"city autoloot off", "crate refill", "hit", "kit", "clear_loadout"
	);

	private AdminCommands() { }

	static List<Action> available(CommandDispatcher<CommandSource> dispatcher) {
		if (dispatcher == null) return List.of();
		CommandNode<CommandSource> tarkov = dispatcher.getRoot().getChild("tarkov");
		if (tarkov == null) return List.of();
		List<Action> result = new ArrayList<>();
		visit(tarkov, new ArrayList<>(), result);
		CommandNode<CommandSource> raid = dispatcher.getRoot().getChild("raid");
		if (raid != null) visit(raid, new ArrayList<>(), result);
		Map<String, Action> unique = new LinkedHashMap<>();
		for (Action action : result) {
			if (Set.of("", "admin", "loot").contains(action.key())) continue;
			if (action.key().equals("arena create") && action.parameters().size() > 1) continue;
			unique.merge(action.key(), action, (left, right) ->
				left.parameters().size() >= right.parameters().size() ? left : right);
		}
		return unique.values().stream().sorted(Comparator.comparing(Action::group).thenComparing(Action::key)).toList();
	}

	private static void visit(CommandNode<CommandSource> node, List<CommandNode<CommandSource>> path, List<Action> actions) {
		path.add(node);
		if (node.getCommand() != null) actions.add(Action.of(path));
		for (CommandNode<CommandSource> child : node.getChildren()) visit(child, path, actions);
		path.remove(path.size() - 1);
	}

	static String searchable(String text) {
		return text.toLowerCase(Locale.ROOT).replace('ё', 'е').strip();
	}

	record Action(Group group, String key, String title, String usage, List<CommandNode<CommandSource>> nodes,
		List<Parameter> parameters, boolean confirm) {
		static Action of(List<CommandNode<CommandSource>> path) {
			List<Parameter> parameters = new ArrayList<>();
			List<String> literals = new ArrayList<>();
			boolean tarkov = path.get(0).getName().equals("tarkov");
			for (CommandNode<CommandSource> node : path) {
				if (node instanceof ArgumentCommandNode<CommandSource, ?> argument) {
					parameters.add(new Parameter(argument.getName(), argument.getType()));
				} else if (node != path.get(0) || !tarkov) {
					literals.add(node.getName());
				}
			}
			String key = String.join(" ", literals);
			String usage = path.stream().map(CommandNode::getUsageText).collect(Collectors.joining(" ", "/", ""));
			String fallback = literals.isEmpty() ? path.get(0).getName() : key.replace('_', ' ');
			return new Action(groupFor(literals), key, COMMAND_NAMES.getOrDefault(key, fallback), usage,
				List.copyOf(path), List.copyOf(parameters), CONFIRM.contains(key));
		}

		String description() {
			return DESCRIPTIONS.getOrDefault(key, group.title);
		}

		private static Group groupFor(List<String> literals) {
			String root = literals.isEmpty() ? "tarkov" : literals.get(0);
			return switch (root) {
				case "arena" -> Group.ARENA;
				case "raid" -> Group.RAID;
				case "crate", "loot" -> Group.CRATE;
				case "city" -> Group.CITY;
				case "airdrop" -> Group.AIRDROP;
				case "heal", "bleed", "fracture", "hit" -> Group.HEALTH;
				case "kit", "meds", "clear_loadout" -> Group.GEAR;
				case "corpse", "clear_corpses", "locate" -> Group.WORLD;
				default -> Group.SYSTEM;
			};
		}

		String command(List<String> values) {
			List<String> words = new ArrayList<>(nodes.size());
			int index = 0;
			for (CommandNode<CommandSource> node : nodes) {
				if (node instanceof ArgumentCommandNode<?, ?>) {
					String value = values.get(index).strip();
					words.add(value.isEmpty() ? node.getUsageText() : parameters.get(index).encode(value));
					index++;
				} else {
					words.add(node.getName());
				}
			}
			return String.join(" ", words);
		}

		boolean matches(String query) {
			String index = searchable(group.title + " " + title + " " + description() + " " + usage);
			for (String word : searchable(query).split("\\s+")) {
				if (!index.contains(word)) return false;
			}
			return true;
		}

		boolean valid(CommandDispatcher<CommandSource> dispatcher, CommandSource source, List<String> values, String command) {
			if (command.length() > 256 || values.stream().anyMatch(String::isBlank)) return false;
			var parse = dispatcher.parse(command, source);
			var context = parse.getContext();
			if (parse.getReader().canRead() || context.getCommand() == null) return false;
			var parsed = context.getNodes();
			if (parsed.size() != nodes.size()) return false;
			for (int i = 0; i < nodes.size(); i++) {
				if (nodes.get(i) != parsed.get(i).getNode()) return false;
			}
			return true;
		}
	}

	record Parameter(String name, ArgumentType<?> type) {
		String title() {
			return PARAMETER_NAMES.getOrDefault(name, name.replace('_', ' '));
		}

		String encode(String value) {
			if (type instanceof StringArgumentType string && string.getType() == StringArgumentType.StringType.QUOTABLE_PHRASE) {
				return StringArgumentType.escapeIfRequired(value);
			}
			return value;
		}
	}
}
