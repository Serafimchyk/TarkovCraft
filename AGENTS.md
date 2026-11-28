# TarkovCraft — Правила проекта

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
- **VCS:** Git инициализирован на ветке `main` (`.git` присутствует).

## 2. Рабочие команды Gradle (Windows / PowerShell)

> Перед запуском Gradle проверь, что `JAVA_HOME` указывает на установленный JDK 17. Если терминал был открыт до установки, перечитай значение из системных настроек.

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Быстрая проверка компиляции
.\gradlew.bat compileJava --no-daemon

# Прогон юнит-тестов (JUnit 5 + fabric-loader-junit)
.\gradlew.bat test --no-daemon

# Полная сборка (compileJava + test + remapJar)
.\gradlew.bat build --no-daemon
```

После успешной сборки скопируй итоговый JAR мода `build/libs/tarkovcraft-0.1.0.jar` в папку `C:\Users\seraphimchyk\AppData\Roaming\ModrinthApp\profiles\test\mods`. Замени предыдущий файл с тем же именем.

Порядок запуска в Modrinth и расположение журналов при повторяющихся вылетах описаны в [документации по запуску](docs/launch-and-crash-troubleshooting.md).

Для проверки реальных сигнатур Minecraft/Yarn:
- Использовать `javap` по ремапленному JAR Minecraft из `.gradle/loom-cache/` или сверяться с `build/loom-cache/mixin-map-net.fabricmc.yarn.1_20_1.1.20.1+build.10-v2.main.tiny`.
- При необходимости полных декомпилированных исходников запускать `.\gradlew.bat genSources --no-daemon` (с выставленным JDK 17).

---

## 3. Совместимость и проверка изменений

- Код мода должен работать на выделенном сервере. Размещай клиентский код в `com.tarkovcraft.client.*`, а клиентские миксины регистрируй в секции `client` файла `tarkovcraft.mixins.json`.
- Общий и серверный код должен загружаться без клиентских классов Minecraft и Fabric. В `TarkovNetworking` клиентские обработчики размещай в `com.tarkovcraft.client.*` либо изолируй через `@Environment(EnvType.CLIENT)`.
- Перед изменением сетевого протокола, зависимостей, Mixin или AccessWidener уточни контракт и проверь реальные сигнатуры Minecraft, Yarn, Fabric API и TaCZ по исходникам проекта или Loom-кэшу. Для пакетов проверь валидацию на сервере и выполнение на нужном потоке.
- Для Java-изменений запускай `.\gradlew.bat compileJava test --no-daemon`. Для изменений зависимостей, сети и Mixin запускай полную сборку.
