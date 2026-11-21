# TarkovCraft — Правила оркестратора и команды «Золотой баланс»

Этот файл — главный источник всегда активных правил проекта для Antigravity.

## 1. Подтверждённый стек проекта

- **Мод:** `TarkovCraft` (`id: tarkovcraft`, `version: 0.1.0`, `group: com.tarkovcraft`)
- **Minecraft:** `1.20.1` (`~1.20.1`)
- **Загрузчик:** Fabric Loader `0.16.10` (`>=0.15.0`), Fabric Loom `1.8-SNAPSHOT` (фактически `1.8.13`)
- **Маппинги:** Yarn `1.20.1+build.10` (`net.fabricmc:yarn:1.20.1+build.10:v2`)
- **Java:** `17` (`options.release = 17`, `sourceCompatibility = 17`, `targetCompatibility = 17`)
- **Gradle Wrapper:** `8.10.2` (`.\gradlew.bat`, `-Xmx2G`, `org.gradle.parallel=true`)
- **Ключевые зависимости (`build.gradle`, `gradle.properties`):**
  - `net.fabricmc:fabric-loader:0.16.10`
  - `net.fabricmc.fabric-api:fabric-api:0.92.12+1.20.1`
  - Тесты: `net.fabricmc:fabric-loader-junit:0.16.10`, `org.junit.jupiter:junit-jupiter:5.11.0`, `org.junit.platform:junit-platform-launcher` (JUnit 5)
- **Интеграции и ограничения (`TZ.md`):**
  - Целевая интеграция — **TaCZ Refabricated** (на текущем этапе через zero-hard-dependency reflection-мост `com.tarkovcraft.health.TaCZBulletHelper`).
  - **TactEq не трогать** — это отдельный сторонний профиль, не целевой Fabric-профиль.
- **Точки входа (`src/main/resources/fabric.mod.json`):**
  - `environment`: `"*"` (обязательна совместимость с выделенным сервером!)
  - `main`: `com.tarkovcraft.TarkovCraftMod`
  - `client`: `com.tarkovcraft.client.TarkovCraftClient`
- **Mixin (`src/main/resources/tarkovcraft.mixins.json`, `package: com.tarkovcraft.mixin`, `JAVA_17`, `defaultRequire: 1`):**
  - Общие (`mixins`): `PlayerEntityMixin`, `PlayerInventoryMixin`, `LivingEntityDeathMixin`, `ItemEntityPickupMixin`, `ServerPlayNetworkHandlerMixin`
  - Клиентские (`client`): `MinecraftClientMixin`, `InGameHudMixin`, `ItemEntityRendererMixin`
- **AccessWidener / AccessTransformer:** не настроен (`.accesswidener` в проекте отсутствует).
- **Датаген:** не настроен в `build.gradle` / `fabric.mod.json`.
- **Ресурсы и данные:**
  - Ассеты: `src/main/resources/assets/tarkovcraft/` (`blockstates`, `lang/en_us.json`, `lang/ru_ru.json`, `models/block`, `models/item`, `textures/block`, `textures/item`)
  - Датапак-данные: `src/main/resources/data/tarkovcraft/item_sizes/default_sizes.json`
- **VCS:** Git инициализирован на ветке `main` (`.git` присутствует). Для Swarm-воркеров по-прежнему использовать `workspace:"inherit"` и строго соблюдать непересекающееся владение файлами.

## 2. Рабочие команды Gradle (Windows / PowerShell)

> **ВАЖНО:** в системном `PATH` по умолчанию стоит JDK `1.8.0_504` (`JAVA_HOME` не задан). Перед любой командой Gradle **обязательно** выставлять JDK 17:

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.9.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Быстрая проверка компиляции
.\gradlew.bat compileJava --no-daemon

# Прогон юнит-тестов (JUnit 5 + fabric-loader-junit)
.\gradlew.bat test --no-daemon

# Полная сборка (compileJava + test + remapJar)
.\gradlew.bat build --no-daemon
```

Для проверки реальных сигнатур Minecraft/Yarn (задача `architect`):
- Использовать `javap` по ремапленному JAR Minecraft из `.gradle/loom-cache/` или сверяться с `build/loom-cache/mixin-map-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.main.tiny`.
- При необходимости полных декомпилированных исходников запускать `.\gradlew.bat genSources --no-daemon` (с выставленным JDK 17).

---

## 3. Команда агентов «Золотой баланс»

Описания ролей хранятся в `.agents/agents/*.md`, пошаговый плейбук запуска — в `.agents/skills/golden-balance/SKILL.md`.

| Роль | Файл контракта | Целевая модель и мышление | Swarm `model` ID | Права и границы файлов |
| :--- | :--- | :--- | :--- | :--- |
| **Оркестратор** (текущая сессия) | `AGENTS.md` | `Gemini 4 Neon` — `medium` | `gemini-4-neon-medium` | Маршрутизация, приёмка, постановка задач (сам код не пишет при делегировании). |
| **`architect`** | `.agents/agents/architect.md` | `Gemini 4 Neon` — `high` | `gemini-4-neon-high` | Преимущественно read-only/планирование; прямая запись **только** в спецификации, `build.gradle`, `gradle.properties`, `settings.gradle`, `src/main/resources/fabric.mod.json`, `src/main/resources/tarkovcraft.mixins.json`, `*.accesswidener`. |
| **`reviewer`** | `.agents/agents/reviewer.md` | `Gemini 4 Neon` — `medium` | `gemini-4-neon-medium` | Строго read-only (`edit_file`/`write_file` отключены) + запуск проверочных команд (`gradlew`, статические проверки, осмотр диффов). |
| **`java-coder`** | `.agents/agents/java-coder.md` | `Gemini 4 Hydrogen` — `high` | `gemini-4-hydrogen-high` | Чтение/запись **только** в `src/main/java/**` и `src/test/java/**` + запуск Gradle (`compileJava`, `test`, `build`). |
| **`assets-worker`** | `.agents/agents/assets-worker.md` | `Gemini 4 Hydrogen` — `medium` | `gemini-4-hydrogen-medium` | Чтение/запись **только** в `src/main/resources/assets/**` и `src/main/resources/data/**` (JSON моделей, blockstates, рецепты, теги, loot tables, lang, `item_sizes`). **Запрещено** трогать `fabric.mod.json`, `tarkovcraft.mixins.json`, Java и Gradle. |

### Ограничение схемы Antigravity и правильный способ запуска

В текущем окружении Antigravity:
1. YAML-frontmatter файлов `.agents/agents/*.md` **не поддерживает** поля точной модели (`gemini-4-neon-high`, `gemini-4-hydrogen-high`) и раздельные флаги `thinking`/`effort` — поэтому эти поля намеренно **не добавлены** в YAML.
2. Инструмент `invoke_subagent` принимает только грубые алиасы `Model: "inherit" | "flash_lite" | "flash" | "pro"`, что не позволяет жёстко разделить `Neon high` vs `Neon medium` и `Hydrogen high` vs `Hydrogen medium`.
3. **Основной (точный) способ запуска — через `swarm`:** инструмент `swarm{action:"add", name:"<role>", model:"<exact-id>", workspace:"inherit", brief:"..."}` нативно поддерживает точные ID моделей с уровнем мышления (`gemini-4-neon-high`, `gemini-4-neon-medium`, `gemini-4-hydrogen-high`, `gemini-4-hydrogen-medium`). В `brief` каждого воркера обязательно указывать: *«Сначала прочитай `.agents/agents/<role>.md` и строго соблюдай свой контракт»*.
4. **Фоллбэк (если Swarm не используется):** вызывать `.agents/agents/<role>.md` через `invoke_subagent` (`Model: "pro"` для `architect`/`reviewer`, `Model: "flash"` или `"inherit"` для `java-coder`/`assets-worker`) и явно предупредить пользователя, что точный уровень `high`/`medium` в режиме `invoke_subagent` не гарантируется схемой.

---

## 4. Жёсткие правила оркестратора (обязательны всегда)

1. **Запрет на `Hydrogen low`:**
   - **Никогда** не назначать `gemini-4-hydrogen-low` (`Gemini 4 Hydrogen (Low)`) для Java-кода, Mixin, тестов и Gradle.
2. **Экономия квоты Pro (`Neon xhigh`):**
   - **Никогда** не использовать `gemini-4-neon-xhigh` (`Gemini 4 Neon (XHigh)` / Extra) автоматически.
   - Переход на `Neon xhigh` допустим **только** по прямому указанию пользователя после того, как `architect` на `gemini-4-neon-high` не справился.
3. **Протокол зависимостей, сети и Mixin:**
   - Перед **любым** изменением `build.gradle`, `gradle.properties`, версий зависимостей/маппингов, сетевых пакетов (`com.tarkovcraft.network.*`), `fabric.mod.json`, `tarkovcraft.mixins.json`, `*.accesswidener` или аннотаций `@Inject`/`@Redirect`/`@Overwrite`/`@Modify*` обязателен этап **`architect (gemini-4-neon-high)`**.
   - **Никаких угадываний** имён классов, методов, полей и дескрипторов Minecraft/Yarn/Fabric API/TaCZ — `architect` обязан проверять реальные сигнатуры по исходникам проекта, Loom-кэшу (`javap` / `.tiny`) или `genSources`.
4. **Граница клиент / сервер (`environment: "*"`):**
   - Клиентские классы (`net.minecraft.client.*`, `ClientPlayNetworking`, экраны, рендер, HUD, частицы, кейбинды `KeyBinding` / `GLFW`) **никогда** не должны импортироваться или вызываться на выделенном сервере из common/server-кода.
   - Клиентский код размещать в `com.tarkovcraft.client.*`; клиентские миксины регистрировать **только** в секции `"client"` файла `src/main/resources/tarkovcraft.mixins.json`.
   - Особое внимание к `com.tarkovcraft.network.TarkovNetworking`: не добавлять новые безусловные клиентские импорты в общие классы; клиентские обработчики пакетов выносить в `com.tarkovcraft.client.*` либо строго изолировать за `@Environment(EnvType.CLIENT)` без обращений из серверных веток.
5. **Правило эскалации кодера:**
   - `java-coder` не имеет права менять `build.gradle`, `gradle.properties`, `settings.gradle`, `fabric.mod.json`, `tarkovcraft.mixins.json` без явной спеки/одобрения `architect`.
   - Если `java-coder` упёрся в конфликт зависимостей, ошибку Mixin-ремаппинга или непонятный краш после **2 попыток** — немедленная эскалация в `architect`, без костылей, отключённых тестов и заглушек.

---

## 5. Цикл выполнения задач

### А. Простая фича (без смены архитектуры, зависимостей, сети и Mixin)
1. **Оркестратор (`gemini-4-neon-medium`):** уточняет границы задачи и файлы владения.
2. **Реализация:**
   - `java-coder` (`gemini-4-hydrogen-high`) пишет/правит Java и JUnit-тесты в `src/main/java` и `src/test/java`.
   - Параллельно (если нужны JSON-ассеты/переводы/data) `assets-worker` (`gemini-4-hydrogen-medium`) обновляет `src/main/resources/assets/**` и `src/main/resources/data/**`.
3. **Сборка и тесты:** `java-coder` прогоняет `.\gradlew.bat compileJava test --no-daemon` (или `build`) с JDK 17.
4. **Ревью:** `reviewer` (`gemini-4-neon-medium`) проверяет все изменённые файлы, границу клиент/сервер, отсутствие регрессий, заглушек и прогоняет верификацию.

### Б. Сложная фича / зависимости / Mixin / сетевой протокол / AccessWidener
1. **Проектирование:** `architect` (`gemini-4-neon-high`) изучает реальные сигнатуры/зависимости и выдаёт точную спецификацию: список файлов, точные Yarn-сигнатуры и Mixin-таргеты, контракт пакетов, краевые случаи, критерии приёмки. При необходимости `architect` сам вносит правки в `build.gradle` / `fabric.mod.json` / `tarkovcraft.mixins.json` / `*.accesswidener`.
2. **Реализация:** `java-coder` (`gemini-4-hydrogen-high`) реализует код **строго по спеке** (+ `assets-worker` на `gemini-4-hydrogen-medium` для ресурсов, если требуется).
3. **Сборка и тесты:** `java-coder` прогоняет `.\gradlew.bat build --no-daemon` с JDK 17.
4. **Ревью:** `reviewer` (`gemini-4-neon-medium`) делает полный разбор диффа; для критичных правок Mixin/сети/зависимостей финальный аудит проводит `architect` (`gemini-4-neon-high`).
