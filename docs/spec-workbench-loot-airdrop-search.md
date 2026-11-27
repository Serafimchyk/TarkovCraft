# Спецификация: Верстак v2, Лут-таблицы, Worldgen структур, Аирдропы, Полный §6 (Обыск + Экипировка мобов) и Конфиг (`config/tarkovcraft.json`)

**Статус:** Утверждённая архитектурная спецификация
**Стек:** Minecraft `1.20.1`, Fabric Loader `0.16.10`, Fabric API `0.92.12+1.20.1`, Yarn `1.20.1+build.10`, Java `17`, `environment: "*"` (обязательна совместимость с выделенным сервером).  
**Gradle / `fabric.mod.json` / `tarkovcraft.mixins.json`:** Новые зависимости и новые миксины **не требуются** (используются существующие `LivingEntityDeathMixin` + Fabric API events `ServerEntityEvents.ENTITY_LOAD`, `BiomeModifications.addFeature`, `ServerTickEvents.END_SERVER_TICK`, `CommandRegistrationCallback`). **TactEq не трогать.**

---

## 1. Разделение владения файлами (строго без пересечений)

| Воркер | Права владения файлами |
| :--- | :--- |
| **`assets-catalog`** | ТОЛЬКО `src/main/resources/assets/**` и `src/main/resources/data/**` |
| **`java-catalog`** | ТОЛЬКО `src/main/java/**` и `src/test/java/**` |

> `assets-catalog` и `java-catalog` могут работать **параллельно**, опираясь на зафиксированные ниже ID блоков/фич/сущностей и ключи локализации.

### Полный список файлов

**Владение `assets-catalog`:**
1. **Блок `airdrop_crate`:**
   - `src/main/resources/assets/tarkovcraft/blockstates/airdrop_crate.json` **(новый)**
   - `src/main/resources/assets/tarkovcraft/models/block/airdrop_crate.json` **(новый)**
   - `src/main/resources/assets/tarkovcraft/models/item/airdrop_crate.json` **(новый)**
   - `src/main/resources/assets/tarkovcraft/textures/block/airdrop_crate_top.png` и `airdrop_crate_side.png` **(новые 16x16 RGBA PNG — красный/оранжевый военный контейнер с полосами и жёлтым маркером)**
   - `src/main/resources/data/tarkovcraft/loot_tables/blocks/airdrop_crate.json` **(новый)** — пустой дроп или дроп самого блока (лучше `[]` пустой пул, чтобы нельзя было ломать аирдропы ради бесплатных контейнеров, либо сам ящик — см. §4.1: делаем пустой пул для `airdrop_crate` чтобы в Survival не фармили блоки!).
2. **Worldgen datapack JSON (4 структуры в Overworld):**
   - `src/main/resources/data/tarkovcraft/worldgen/configured_feature/roadblock.json` **(новый)**
   - `src/main/resources/data/tarkovcraft/worldgen/configured_feature/med_tent.json` **(новый)**
   - `src/main/resources/data/tarkovcraft/worldgen/configured_feature/military_camp.json` **(новый)**
   - `src/main/resources/data/tarkovcraft/worldgen/configured_feature/forest_stash.json` **(новый)**
   - `src/main/resources/data/tarkovcraft/worldgen/placed_feature/roadblock.json` **(новый)**
   - `src/main/resources/data/tarkovcraft/worldgen/placed_feature/med_tent.json` **(новый)**
   - `src/main/resources/data/tarkovcraft/worldgen/placed_feature/military_camp.json` **(новый)**
   - `src/main/resources/data/tarkovcraft/worldgen/placed_feature/forest_stash.json` **(новый)**
3. **Локализация:**
   - `src/main/resources/assets/tarkovcraft/lang/en_us.json` и `ru_ru.json` — новые ключи из §8.

**Владение `java-catalog`:**
1. `src/main/java/com/tarkovcraft/config/TarkovConfig.java` **(новый)** — Gson-конфиг `config/tarkovcraft.json` с валидацией, автосозданием и hot-reload.
2. `src/main/java/com/tarkovcraft/client/screen/TarkovWorkbenchScreen.java` — превью сетки/карманов для `BackpackItem`/`TacticalRigItem`, сортировка (`DEFAULT`, `ARMOR_DESC/ASC`, `CAPACITY_DESC/ASC`), чекбокс «Доступные».
3. `src/main/java/com/tarkovcraft/loot/TarkovLootPool.java` **(новый)** — enum `MILITARY`, `MEDICAL`, `CIVILIAN`, `AIRDROP`, `SCAV_POCKET`.
4. `src/main/java/com/tarkovcraft/loot/TarkovLootGenerator.java` **(новый)** — богатый генератор лута на основе всех 701 предметов (`ModItems` + `TarkovItemCatalog`) и тировых патронов TaCZ (`TaCZCaliberCatalog` + `AmmoTier`), с учётом `TarkovConfig.lootDensityMultiplier`.
5. `src/main/java/com/tarkovcraft/block/SearchableLootBlock.java` — добавить `TarkovLootPool`, `getTicker` (серверный тик сигнального дыма для `AIRDROP_CRATE`).
6. `src/main/java/com/tarkovcraft/block/SearchableLootBlockEntity.java` — поле `smokeTicksRemaining` (NBT `"SmokeTicks"`), метод `serverTick`, отключение дыма при начале обыска, хелпер `populateFromPool(TarkovLootPool, Random)`.
7. `src/main/java/com/tarkovcraft/registry/ModBlocks.java` — регистрация `ModBlocks.AIRDROP_CRATE` (`6x6`, `TarkovLootPool.AIRDROP`) и добавление в `LOOT_CONTAINER_ENTITY`.
8. `src/main/java/com/tarkovcraft/registry/ModItems.java` — добавить `ModBlocks.AIRDROP_CRATE` в `TARKOV_TACTICAL_GROUP`.
9. `src/main/java/com/tarkovcraft/worldgen/TarkovStructureFeature.java` **(новый)** — `Feature<DefaultFeatureConfig>` с 4 типами поверхностных структур (`ROADBLOCK`, `MED_TENT`, `MILITARY_CAMP`, `FOREST_STASH`), проверкой ровной твёрдой поверхности и наполнением ящиков лутом.
10. `src/main/java/com/tarkovcraft/worldgen/ModWorldGen.java` **(новый)** — регистрация 4 `Feature` в `Registries.FEATURE` + `BiomeModifications.addFeature` для Overworld.
11. `src/main/java/com/tarkovcraft/entity/AirdropEntity.java` **(новый)** — сущность падающего аирдропа с парашютом и красным дымом, превращающаяся при посадке в `ModBlocks.AIRDROP_CRATE` с лутом и сигнальным дымом.
12. `src/main/java/com/tarkovcraft/client/renderer/AirdropEntityRenderer.java` **(новый, `@Environment(EnvType.CLIENT)`)** — рендер ящика `AIRDROP_CRATE` + парашютного купола и строп.
13. `src/main/java/com/tarkovcraft/airdrop/AirdropManager.java` **(новый)** — серверный таймер, выбор игрока/точки, спавн `AirdropEntity`, оповещение игроков + звук, API для `/tarkov airdrop`.
14. `src/main/java/com/tarkovcraft/registry/ModEntities.java` — регистрация `ModEntities.AIRDROP`.
15. `src/main/java/com/tarkovcraft/client/TarkovCraftClient.java` — регистрация `EntityRendererRegistry.register(ModEntities.AIRDROP, AirdropEntityRenderer::new)` (строго client-only!).
16. `src/main/java/com/tarkovcraft/search/ItemRarityRegistry.java` — улучшенная оценка редкости по классу брони/тиру патронов/вместимости кейсов + учёт `TarkovConfig.searchTimeMultiplier`.
17. `src/main/java/com/tarkovcraft/search/SearchSession.java` — чтение `TarkovConfig.get().getContainerRevealTicks()` для Stage 1.
18. `src/main/java/com/tarkovcraft/screen/TarkovScreenHandler.java` — строгий §6: `ownInventoryOpen = (mode == MODE_OWN_INVENTORY)` в `tickServer`, чтобы снятый рюкзак обыскивался **только** когда игрок смотрит в свой собственный инвентарь; отключение сигнального дыма при открытии `AIRDROP_CRATE`.
19. `src/main/java/com/tarkovcraft/client/screen/TarkovInventoryScreen.java` — отрисовка выразительных тёмных **силуэтов/очертаний** (`WxH` фрейм, угловые скобки, штриховка, скан-полоса) для ненайденных предметов (`!entry.discovered()`).
20. `src/main/java/com/tarkovcraft/entity/MobGearSpawner.java` **(новый)** — обработчик `ServerEntityEvents.ENTITY_LOAD`: ~8% (`mobGearSpawnChance`) шанс выдать броню/шлем TarkovCraft + теги рюкзака/разгрузки целевым враждебным мобам (с защитой command-tag `"tarkovcraft:gear_rolled"`).
21. `src/main/java/com/tarkovcraft/entity/CorpseEntity.java` — перенос реальной экипировки с экипированных мобов, генерация лута через `TarkovLootGenerator`, срок жизни из `TarkovConfig.corpseLifetimeSeconds`.
22. `src/main/java/com/tarkovcraft/mixin/LivingEntityDeathMixin.java` — спавн `CorpseEntity` всегда для экипированных мобов (`"tarkovcraft:geared_mob"`) и с шансом `corpseBaseLootChance` (25%) для обычных гуманоидных мобов (вместо 100% халявных трупов на каждом зомби).
23. `src/main/java/com/tarkovcraft/entity/TarkovGrenadeEntity.java` — умножать осколочный урон на `TarkovConfig.get().grenadeDamageMultiplier`.
24. `src/main/java/com/tarkovcraft/command/TarkovCommand.java` — команды `/tarkov reload`, `/tarkov airdrop`, `/tarkov airdrop here`, `/tarkov airdrop interval <min>`, обновить `/tarkov loot` на использование `TarkovLootGenerator`.
25. `src/main/java/com/tarkovcraft/TarkovCraftMod.java` — вызовы `TarkovConfig.loadOrCreate()`, `ModWorldGen.init()`, `MobGearSpawner.register()`, `AirdropManager.tickServer(server)`.
26. `src/test/java/com/tarkovcraft/loot/WorkbenchLootAirdropConfigTest.java` **(новый)** — JUnit 5 тесты на все 6 блоков спецификации.

---

## 2. Проверенные Yarn 1.20.1 / Fabric API 0.92.12 сигнатуры

Все сигнатуры проверены через `javap` по Loom-кэшу проекта:
- `net.minecraft.world.gen.feature.Feature<FC>`:
  - `public Feature(Codec<FC> configCodec)`
  - `public abstract boolean generate(FeatureContext<FC> context)`
  - `FeatureContext`: `getWorld()` (`StructureWorldAccess`), `getRandom()` (`Random`), `getOrigin()` (`BlockPos`), `getConfig()`
  - `DefaultFeatureConfig.CODEC` (`Codec<DefaultFeatureConfig>`)
- `net.fabricmc.fabric.api.biome.v1.BiomeModifications`:
  - `public static void addFeature(Predicate<BiomeSelectionContext> biomeSelector, GenerationStep.Feature step, RegistryKey<PlacedFeature> placedFeatureRegistryKey)`
  - `BiomeSelectors.foundInOverworld()`
  - `GenerationStep.Feature.SURFACE_STRUCTURES`
- `net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents`:
  - `public static final Event<ServerEntityEvents.Load> ENTITY_LOAD` -> `(Entity entity, ServerWorld world)`
- `net.fabricmc.loader.api.FabricLoader`:
  - `FabricLoader.getInstance().getConfigDir()` -> `java.nio.file.Path`
- `net.minecraft.world.World`:
  - `public int getTopY(Heightmap.Type heightmap, int x, int z)`
- `net.minecraft.client.render.block.BlockRenderManager` **(client-only!)**:
  - `public void renderBlockAsEntity(BlockState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)`

---

## 3. Конфиг-файл (`config/tarkovcraft.json`) + `/tarkov reload`

### 3.1. Класс `com.tarkovcraft.config.TarkovConfig`
- Хранилище: `FabricLoader.getInstance().getConfigDir().resolve("tarkovcraft.json")`.
- Сериализация: встроенный `com.google.gson.Gson` (`new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()`), **без новых зависимостей в `build.gradle`**.
- **Потокобезопасность и JUnit-безопасность:**
  - `private static volatile TarkovConfig instance = new TarkovConfig();`
  - `public static TarkovConfig get()` — всегда возвращает валидный non-null конфиг (даже в JUnit-тестах без запущенного `FabricLoader`!).
  - `public static synchronized TarkovConfig loadOrCreate()` — в `try/catch (Throwable)` резолвит путь; если файла нет — создаёт директорию, записывает дефолтный JSON; если есть — парсит, вызывает `validateAndClamp()`, и перезаписывает файл обратно, чтобы новые поля автоматически дописались в существующий `config/tarkovcraft.json`. При ошибке парсинга логирует `WARN` и сохраняет валидные дефолты.
  - `public static synchronized void save()` — сохраняет текущий `instance` на диск (используется командой `/tarkov airdrop interval <min>`).
  - Также предусмотреть пакетный/тестовый метод `loadFromPath(Path path)` для JUnit-тестирования чтения/записи/клампинга без `FabricLoader`!

### 3.2. Поля `TarkovConfig` (точные JSON-имена, дефолты и границы клампинга)

| Поле JSON | Тип | Дефолт | Допустимый диапазон (`validateAndClamp`) |
|---|---|---|---|
| `airdropEnabled` | `boolean` | `true` | — |
| `airdropIntervalMinutes` | `int` | `15` | `1 .. 1440` |
| `airdropMinRadius` | `int` | `48` | `8 .. 256` |
| `airdropMaxRadius` | `int` | `120` | `airdropMinRadius .. 512` |
| `airdropSmokeSeconds` | `int` | `180` | `0 .. 1800` |
| `airdropAnnounceCoords` | `boolean` | `true` | — |
| `worldgenStructuresEnabled` | `boolean` | `true` | — |
| `lootDensityMultiplier` | `float` | `1.0f` | `0.1f .. 5.0f` |
| `mobGearSpawnChance` | `float` | `0.08f` | `0.0f .. 1.0f` |
| `mobBackpackChance` | `float` | `0.35f` | `0.0f .. 1.0f` |
| `corpseBaseLootChance` | `float` | `0.25f` | `0.0f .. 1.0f` |
| `corpseLifetimeSeconds` | `int` | `600` | `30 .. 7200` |
| `containerRevealSeconds` | `float` | `1.0f` | `0.1f .. 10.0f` |
| `searchTimeMultiplier` | `float` | `1.0f` | `0.1f .. 5.0f` |
| `grenadeDamageMultiplier` | `float` | `1.0f` | `0.1f .. 5.0f` |

Удобные вычисляемые хелперы:
- `getAirdropIntervalTicks()` -> `airdropIntervalMinutes * 60 * 20`
- `getContainerRevealTicks()` -> `Math.max(2, Math.round(containerRevealSeconds * 20.0f))`
- `getCorpseLifetimeTicks()` -> `corpseLifetimeSeconds * 20`

### 3.3. Команда `/tarkov reload`
- В `TarkovCommand` (требует `permission level 2`):
  - `.then(CommandManager.literal("reload").executes(ctx -> reloadConfig(ctx.getSource())))`
  - Вызывает `TarkovConfig.loadOrCreate()`, уведомляет `AirdropManager.onConfigReloaded()`, отвечает в чат сводкой ключевых параметров (аирдроп, мобы, обыск).

---

## 4. Фича 1 — Верстак v2 (`TarkovWorkbenchScreen.java`, client-only)

### 4.1. Сортировка и чекбокс «Доступные» во вкладке `ARMOR & GEAR`
1. Добавить `enum GearSortMode`:
   - `DEFAULT` (`ui.tarkovcraft.wb_sort_default`) — исходный порядок каталога
   - `ARMOR_DESC` (`ui.tarkovcraft.wb_sort_armor_desc`) — класс брони / repairAmount по убыванию
   - `ARMOR_ASC` (`ui.tarkovcraft.wb_sort_armor_asc`) — по возрастанию
   - `CAPACITY_DESC` (`ui.tarkovcraft.wb_sort_cap_desc`) — вместимость (`W*H` рюкзака / сумма секций разгрузки) по убыванию
   - `CAPACITY_ASC` (`ui.tarkovcraft.wb_sort_cap_asc`) — по возрастанию
2. Добавить поля в `TarkovWorkbenchScreen`:
   - `private GearSortMode gearSortMode = GearSortMode.DEFAULT;`
   - `private boolean onlyAvailableGear = false;`
3. **Разметка под полосой категорий (`py + 24` в левой колонке):**
   - `searchBox` на `py + 24`, высота `14`, ширина `leftW - 16`.
   - Новая строка контролов на `py + 40`, высота `14`, ширина `leftW - 16`:
     - Слева (60% ширины): кнопка цикличного переключения сортировки (клик переключает `gearSortMode` по кругу и сбрасывает `gearScrollY = 0`).
     - Справа (38% ширины): чекбокс `[✓] Доступные` (`onlyAvailableGear = !onlyAvailableGear`, сброс `gearScrollY = 0`, подсветка рамки `ACCENT_SAND` когда активен).
   - Сдвинуть `gearListClip` вниз на `py + 58` (высота `ph - 66`) — перекрытий нет!
4. **Фильтрация и сортировка:**
   - После фильтра по `searchBox`: если `onlyAvailableGear && !creative`, оставлять только рецепты, для которых `hasAllMaterials(recipe.materials()) == true` (проверка по `ClientWorkbenchCache.getItemCount(ing.item()) >= ing.count()`).
   - Сортировать отфильтрованный список стабильным компаратором по `gearSortMode` (при равенстве — по имени предмета).

### 4.2. Предпросмотр расположения слотов рюкзака и разгрузки (правая панель)
Когда в `ARMOR & GEAR` выбран `BackpackItem` или `TacticalRigItem`:
- Между блоком заголовка/ТТХ (`ry`) и списком материалов (`REQUIRED MATERIALS`) отрисовать компактную панель **«СХЕМА СЛОТОВ»** (`ui.tarkovcraft.wb_slot_preview`, высота ~`58..76px`, с `enableScissor` для безопасности):
  - **Для `BackpackItem bp` (`gridW x gridH`):**
    - Вычислить `int c = Math.max(5, Math.min(11, Math.min((rw - 16) / Math.max(1, bp.getGridWidth()), 52 / Math.max(1, bp.getGridHeight()))));`
    - Нарисовать сетку `bp.getGridWidth() x bp.getGridHeight()` (заливка `TarkovUiTheme.BG_CELL_EMPTY`, рамки `TarkovUiTheme.BORDER_CELL`, внешняя рамка `TarkovUiTheme.ACCENT_SAND` с подписью `WxH (N)`).
  - **Для `TacticalRigItem rig` (`List<ItemSize> layouts = rig.getCompartmentLayouts()`):**
    - Вычислить `int c = Math.max(5, Math.min(10, (rw - 24) / 10));` (если секций много — автоматически уменьшать до `5..7px`, чтобы все секции поместились в 1–2 ряда).
    - Разложить каждую секцию `ItemSize sz` слева направо с переносом строки при выходе за `rw - 12` (gap `4px`).
    - Для каждой секции: нарисовать её внутренние клетки `sz.width() x sz.height()` (`BG_CELL_EMPTY` + `BORDER_CELL`), обвести секцию рамкой `BORDER_OCCUPIED`, а внизу/внутри (если `w >= 12`) мелкой подписью вывести `sz.width() + "x" + sz.height()`.
- Список ингредиентов (`renderIngredientsList`) размещать компактно ниже превью (уменьшив `rowH` ингредиента с `16` до `13` при наличии превью, чтобы кнопка `CRAFT` внизу никогда не перекрывалась!).

---

## 5. Фича 2 — Лут-таблицы (`TarkovLootPool` + `TarkovLootGenerator`)

### 5.1. Enum `com.tarkovcraft.loot.TarkovLootPool`
- `MILITARY` — военные ящики (`WEAPON_CRATE`, блокпосты, лагеря)
- `MEDICAL` — медицинские сумки (`MEDBAG`, медпалатки)
- `CIVILIAN` — технические/гражданские ящики (`TECHNICAL_CRATE`, тайники)
- `AIRDROP` — контейнеры аирдропа (`AIRDROP_CRATE`)
- `SCAV_POCKET` — базовый мелкий лут в карманах обычных/экипированных мобов

### 5.2. Генератор `com.tarkovcraft.loot.TarkovLootGenerator`
Использует **весь каталог из 701 предмета** (`ModItems` + `TarkovItemCatalog`), лениво кешируя отфильтрованные списки по категориям/классам брони (`armorClass 0..6`, `helmets`, `masks`, `plates/repair`, `rigs`, `backpacks`, `meds`, `stimulators`, `food`, `grenades`, `cases`, `melee`, `headsets`) + тировые патроны из `TaCZCaliberCatalog` + `AmmoTier`!

**Общий алгоритм `populateLootGrid(GridContainer grid, TarkovLootPool pool, Random random)`:**
- Базовое количество роллов определяется пулом и умножается на `TarkovConfig.get().lootDensityMultiplier` (минимум 1 предмет, максимум пока есть место в сетке).
- Для каждого ролла взвешенно выбирается категория и случайный предмет нужного тира -> `grid.tryAutoPlace(stack)`.

**Состав пулов:**
1. **`MILITARY` (4–7 роллов):**
   - Тировые патроны TaCZ (30%): `FMJ` (45%), `HP` (20%), `AP` (30%), `IGN` (5%), количество `12..30`
   - Броня 2–5 кл. и шлемы 2–5 кл. (20%)
   - Бронеплиты и ремнаборы (12%)
   - Разгрузки и средние рюкзаки (14%)
   - Гранаты (`TarkovGrenadeItem`, 1–2 шт.) (12%)
   - Наушники / тактические маски / холодное оружие (12%)
2. **`MEDICAL` (4–7 роллов):**
   - Медицина (`ModItems` базовые + `TarkovItemCatalog` `TarkovMedicalItem`) (48%)
   - Стимуляторы (`TarkovStimulatorItem` — все 22 шприца!) (34%)
   - Еда/вода (14%)
   - Медкейс / инъектор-кейс (`TarkovCaseItem` с `med`/`inektor` в ID) (4% редкий джекпот!)
3. **`CIVILIAN` (3–6 роллов):**
   - Еда и напитки (`TarkovFoodItem`, 1–3 шт.) (35%)
   - Балаклавы, очки, косметические маски/шапки (`armorClass <= 1`) (22%)
   - Лёгкие рюкзаки/разгрузки (`cells <= 20`) (12%)
   - Базовая медицина (бинты, анальгин, АИ-2, жгуты) (15%)
   - Ванильные ресурсы для верстака (железо, медь, кожа, порох, золото — 2..6 шт.) (12%)
   - Кошельки/ключницы/мелкие кейсы (4%)
4. **`AIRDROP` (8–12 роллов в сетку `6x6`):**
   - Броня и шлемы **4–6 класса** (24%)
   - Патроны TaCZ **`AP` (55%) / `IGN` (45%)**, количество `20..40` (22%)
   - Топовые стимуляторы (Propital, eTG-change, SJ6, MULE, Obdolbos 2, PNB, Trimadol и др.) (14%)
   - Крупные рюкзаки (`cells >= 24`) и разгрузки (`cells >= 16`) (14%)
   - Кейсы (`TarkovCaseItem` — SICC, Docs, Meds/Mag/Grenade/Weapon/Item/THICC) (10%)
   - Гранаты (1–3 шт.) + хирургички/Grizzly + бронеплиты/ремнаборы (16%)
5. **`SCAV_POCKET` (1–3 ролла в `6x1` карманы):**
   - 1x1 предметы: еда (1–2 шт.), бинты/анальгин/жгут, `FMJ`/`HP` патроны (8–16 шт.), 1x1 стимулятор (редко 10%), 1x1 граната (редко 8%), ванильные железо/медь/порох.

> Важно: `TarkovLootGenerator` гарантирует, что созданные ящики имеют пустую `ContainerSearchMemory` (`searched = false` для всех игроков!).

---

## 6. Фича 3 — Worldgen 4 поверхностных структур в Overworld

### 6.1. `com.tarkovcraft.worldgen.TarkovStructureFeature extends Feature<DefaultFeatureConfig>`
- 4 инстанса, параметризованные `enum StructureKind`:
  1. `ROADBLOCK` (`tarkovcraft:roadblock`, `5x5`): мешки/стены из `COBBLESTONE_WALL` / `MOSSY_COBBLESTONE_WALL` и `SANDSTONE` / `HAY_BLOCK`, забор из `IRON_BARS`, фонарь, **1–2 ящика `WEAPON_CRATE`** (`MILITARY`).
  2. `MED_TENT` (`tarkovcraft:med_tent`, `5x5`): каркас из `OAK_FENCE` (высота 3), крыша из `WHITE_WOOL` + красный крест `RED_WOOL` в центре, `LANTERN`, **1–2 сумки `MEDBAG`** (`MEDICAL`).
  3. `MILITARY_CAMP` (`tarkovcraft:military_camp`, `7x5`): костёр `CAMPFIRE` в центре, палатка из `GREEN_WOOL` / `SPRUCE_FENCE`, пара `BARREL`, **1 `WEAPON_CRATE` (`MILITARY`) + 1 `TECHNICAL_CRATE` (`CIVILIAN`)**.
  4. `FOREST_STASH` (`tarkovcraft:forest_stash`, `3x3`): замаскированный тайник у пенька/камней (`MOSSY_COBBLESTONE`, `OAK_LOG`, `FERN`/трава), **1 `TECHNICAL_CRATE` (`CIVILIAN`)** (30% шанс `WEAPON_CRATE` `MILITARY`).

### 6.2. Проверки безопасности генерации (никаких структур в воде/на деревьях/в воздухе)
В `generate(FeatureContext<DefaultFeatureConfig> ctx)`:
1. `if (!TarkovConfig.get().worldgenStructuresEnabled) return false;`
2. Взять `BlockPos origin = ctx.getOrigin();` (уже на `MOTION_BLOCKING_NO_LEAVES`).
3. Проверить по всему footprint (`[-halfW..+halfW] x [-halfD..+halfD]`):
   - Высота поверхности `topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z)` отличается от `origin.getY()` не более чем на `2` блока (ровная площадка!).
   - Блок под ногами (`topY - 1`): `isOpaqueFullCube` AND **НЕ** жидкость (`getFluidState().isEmpty()`), **НЕ** `BlockTags.LOGS`, **НЕ** `BlockTags.LEAVES`, **НЕ** `Blocks.ICE`/`PACKED_ICE`.
   - Над площадкой (`topY`..`topY + 3`): воздух/трава (`isReplaceable()`).
4. При установке ящика:
   - `world.setBlockState(cratePos, crateBlock.getDefaultState(), Block.NOTIFY_LISTENERS);`
   - `if (world.getBlockEntity(cratePos) instanceof SearchableLootBlockEntity be) { be.populateFromPool(pool, ctx.getRandom()); }`

### 6.3. Регистрация (`ModWorldGen.java` + JSON `assets-catalog`)
- В `ModWorldGen.init()` (вызывается из `TarkovCraftMod.onInitialize()`):
  - Зарегистрировать 4 `Feature` в `Registries.FEATURE`: `roadblock`, `med_tent`, `military_camp`, `forest_stash`.
  - Для каждого вызвать:
    ```java
    BiomeModifications.addFeature(
        BiomeSelectors.foundInOverworld(),
        GenerationStep.Feature.SURFACE_STRUCTURES,
        RegistryKey.of(RegistryKeys.PLACED_FEATURE, new Identifier(TarkovCraftMod.MOD_ID, name))
    );
    ```
- В `data/tarkovcraft/worldgen/configured_feature/<name>.json`:
  ```json
  { "type": "tarkovcraft:<name>", "config": {} }
  ```
- В `data/tarkovcraft/worldgen/placed_feature/<name>.json` (редкость без спама чанков):
  - `roadblock`: `rarity_filter` `chance: 140`
  - `med_tent`: `rarity_filter` `chance: 160`
  - `military_camp`: `rarity_filter` `chance: 180`
  - `forest_stash`: `rarity_filter` `chance: 100`
  - Общий шаблон:
    ```json
    {
      "feature": "tarkovcraft:<name>",
      "placement": [
        { "type": "minecraft:rarity_filter", "chance": 140 },
        { "type": "minecraft:in_square" },
        { "type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES" },
        { "type": "minecraft:biome" }
      ]
    }
    ```

---

## 7. Фича 4 — Аирдропы (`AirdropEntity` + `AirdropManager` + `ModBlocks.AIRDROP_CRATE`)

### 7.1. Блок `ModBlocks.AIRDROP_CRATE` и сигнальный дым
- `ModBlocks.AIRDROP_CRATE = registerBlock("airdrop_crate", new SearchableLootBlock(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(4.0f).nonOpaque(), 6, 6, "block.tarkovcraft.airdrop_crate", TarkovLootPool.AIRDROP))`
- Добавить `AIRDROP_CRATE` в `FabricBlockEntityTypeBuilder.create(SearchableLootBlockEntity::new, WEAPON_CRATE, MEDBAG, TECHNICAL_CRATE, AIRDROP_CRATE)`!
- В `SearchableLootBlockEntity`:
  - Поле `private int smokeTicksRemaining = 0;` (NBT ключ `"SmokeTicks"`).
  - Статический тикер `SearchableLootBlockEntity.serverTick(World world, BlockPos pos, BlockState state, SearchableLootBlockEntity be)`:
    - Если `world instanceof ServerWorld sw && be.smokeTicksRemaining > 0`:
      - Уменьшать `smokeTicksRemaining--`.
      - Каждые 3 тика спавнить столб красного сигнального дыма (от `pos.getY() + 1.05` вверх):
        - `sw.spawnParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 1.05, z, 2, 0.15, 0.2, 0.15, 0.06)`
        - `sw.spawnParticles(RED_FLARE_DUST, x, y + 1.2 + (age % 12) * 1.1, z, 4, 0.25, 0.35, 0.25, 0.01)` (где `RED_FLARE_DUST = new DustParticleEffect(new Vector3f(0.92f, 0.14f, 0.14f), 2.5f)`).
      - Если `be.smokeTicksRemaining == 0` -> `be.markDirty()`.
  - В `SearchableLootBlock.getTicker(...)`: возвращать `world.isClient ? null : checkType(type, ModBlocks.LOOT_CONTAINER_ENTITY, SearchableLootBlockEntity::serverTick)`.
  - При первом открытии (`openForPlayer`): опционально оставлять дым гореть или гасить при раскрытии сетки (`if (be.getSmokeTicksRemaining() > 0 && be.getSearchMemory().isGridRevealed(playerId)) be.setSmokeTicksRemaining(0);`).

### 7.2. Сущность `AirdropEntity` + Клиентский рендерер `AirdropEntityRenderer`
- **Common (`com.tarkovcraft.entity.AirdropEntity extends Entity`):**
  - Регистрация в `ModEntities.AIRDROP`:
    ```java
    public static final EntityType<AirdropEntity> AIRDROP = Registry.register(
        Registries.ENTITY_TYPE,
        new Identifier(TarkovCraftMod.MOD_ID, "airdrop"),
        FabricEntityTypeBuilder.<AirdropEntity>create(SpawnGroup.MISC, AirdropEntity::new)
            .dimensions(EntityDimensions.fixed(1.1f, 1.1f))
            .trackRangeBlocks(160)
            .trackedUpdateRate(2)
            .build()
    );
    ```
  - Физика в `tick()`:
    - Управляемый спуск на парашюте: `setVelocity(0.0, -0.14, 0.0); move(MovementType.SELF, getVelocity());`
    - На сервере каждые 3 тика: красные частицы сигнального дыма (`RED_FLARE_DUST` + `CAMPFIRE_COSY_SMOKE`).
    - Условие посадки (сервер): `isOnGround() || !world.getBlockState(getBlockPos().down()).isReplaceable() || getY() <= world.getBottomY() + 2 || age > 1200`:
      - Найти ближайшую свободную клетку `landingPos` (если текущая занята — `landingPos.up()`), поставить `ModBlocks.AIRDROP_CRATE.getDefaultState()`.
      - Заполнить `SearchableLootBlockEntity` пулом `TarkovLootPool.AIRDROP` и выставить `setSmokeTicksRemaining(TarkovConfig.get().airdropSmokeSeconds * 20)`.
      - Проиграть звук посадки (`SoundEvents.BLOCK_ANVIL_LAND`, vol `2.0f`, pitch `0.7f`).
      - `discard()`.
- **Client-only (`com.tarkovcraft.client.renderer.AirdropEntityRenderer extends EntityRenderer<AirdropEntity>`):**
  - `@Environment(EnvType.CLIENT)`, регистрируется в `TarkovCraftClient`: `EntityRendererRegistry.register(ModEntities.AIRDROP, AirdropEntityRenderer::new)`.
  - Отрисовывает:
    1. Ящик `ModBlocks.AIRDROP_CRATE.getDefaultState()` через `MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(...)` (центрирован на `-0.5, 0, -0.5`).
    2. Купол парашюта на высоте `+2.4..+3.1` над ящиком: 3 слегка наклонённые панели из `Blocks.WHITE_WOOL` / `Blocks.RED_WOOL` (через `renderBlockAsEntity` с масштабом `scale(2.2f, 0.18f, 2.2f)`) + 4 стропы из `Blocks.IRON_BARS` или `RenderLayer.getLines()` от углов ящика к углам купола!
  - `getTexture(AirdropEntity)` возвращает `SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE`.

### 7.3. Серверный `com.tarkovcraft.airdrop.AirdropManager`
- Хранит `private static int ticksRemaining = -1;`
- `tickServer(MinecraftServer server)`:
  - Читает `TarkovConfig cfg = TarkovConfig.get();`
  - Если `!cfg.airdropEnabled` -> return.
  - Если `ticksRemaining < 0` -> `ticksRemaining = cfg.getAirdropIntervalTicks();`
  - Найти живых non-spectator игроков в `ServerWorld overworld = server.getOverworld()`. Если список пуст -> не тикать (пауза на пустом сервере).
  - `ticksRemaining--`; при `<= 0`:
    - Сбросить `ticksRemaining = cfg.getAirdropIntervalTicks();`
    - Выбрать случайного игрока из списка и вызвать `spawnAirdropNear(overworld, targetPlayer, cfg.airdropMinRadius, cfg.airdropMaxRadius)`.
- `spawnAirdropNear(ServerWorld world, ServerPlayerEntity anchor, int minR, int maxR)`:
  - До 16 попыток выбрать точку `(bx, bz)` на дистанции `[minR, maxR]`, проверяя `world.isChunkLoaded(bx >> 4, bz >> 4)` и что поверхность (`getTopY(MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1`) НЕ вода/лава.
  - Спавнит `AirdropEntity` на `(bx + 0.5, Math.min(world.getTopY() - 2, topY + 60), bz + 0.5)`.
  - Оповещает игроков в радиусе `300` блоков (или всех в Overworld):
    - Если `cfg.airdropAnnounceCoords`: `Text.translatable("message.tarkovcraft.airdrop_incoming_coords", round10(bx), round10(bz))` (примерные координаты с шагом 10!)
    - Иначе: `Text.translatable("message.tarkovcraft.airdrop_incoming")`
    - Звук `SoundEvents.BLOCK_BEACON_ACTIVATE` (vol `1.2f`, pitch `0.85f`).
- **Команды в `TarkovCommand`:**
  - `/tarkov airdrop` -> `AirdropManager.spawnAirdropNear(world, player, cfg.airdropMinRadius, cfg.airdropMaxRadius)`
  - `/tarkov airdrop here` -> `AirdropManager.spawnAirdropAt(world, player.getBlockPos(), 45)`
  - `/tarkov airdrop interval <minutes>` -> обновляет `TarkovConfig.get().airdropIntervalMinutes`, сохраняет `TarkovConfig.save()`, вызывает `AirdropManager.onConfigReloaded()`.

---

## 8. Фича 5 — Полный §6 ТЗ (Обыск с силуэтами + Редкая экипировка на мобах)

### 8.1. Закрытие всех пробелов §6 (`TZ.md`)
1. **Фикс 1 сек раскрытия слотов** (`SearchSession`): `totalTicks = TarkovConfig.get().getContainerRevealTicks();` (по умолчанию `20` тиков = `1.0s`).
2. **Очертания / силуэты ненайденных предметов (`TarkovInventoryScreen.drawGridView`):**
   - Сохраняя строгий серверный анти-чит (`ViewEntry.stack == ItemStack.EMPTY` до открытия!), заменить плоский чёрный прямоугольник на настоящий **визуальный силуэт предмета размером `ew x eh`**:
     - Тёмная основа `0xEE06080B` + внутренняя утопленная форма силуэта (`ex + 3, ey + 3, ew - 6, eh - 6`, цвет `0xFF151920`) с тонкой внутренней рамкой `0xFF2A313C`.
     - 4 тактических угловых уголка (L-скобки длиной `4px`) — цвет `ACCENT_SAND` во время активного скана, `0xFF454E5A` в ожидании.
     - Во время активного поиска (`isSearchingItem == true`): вертикальная/горизонтальная бегущая скан-полоса (по `snap.searchProgress()`) + прогресс-бар внизу силуэта + центрированная иконка `"?"` / размер `entry.width() + "x" + entry.height()`.
3. **Время поиска по редкости всех 701 предмета (`ItemRarityRegistry`):**
   - В `ItemRarityRegistry.getTier(stack)`:
     - Если `item instanceof TarkovArmorItem a`: `ac >= 5 -> EPIC`, `ac == 4 -> RARE`, `ac == 3 -> UNCOMMON` (брать `max(storedRarity, armorClassTier)`).
     - Если `AmmoTier.isTaczAmmoStack(stack)`: `IGN -> EPIC`, `AP -> RARE`, `HP -> UNCOMMON`, `FMJ -> COMMON`.
     - Если `item instanceof TarkovCaseItem c`: `w*h >= 49 -> EPIC`, `>= 16 -> RARE`.
   - В `getSearchTicks(stack)`: `Math.max(4, Math.round(getTier(stack).getSearchTicks() * TarkovConfig.get().searchTimeMultiplier))`.
4. **Ненайденный предмет нельзя забрать:** подтверждено (все операции `TarkovScreenHandler` блокируют `!isEntryDiscovered`).
5. **Снятый рюкзак обыскивается ТОЛЬКО когда игрок смотрит в свой собственный инвентарь:**
   - В `TarkovScreenHandler.tickServer`:
     ```java
     boolean ownInventoryOpen = (mode == MODE_OWN_INVENTORY);
     boolean stateChanged = searchSession.tickQueue(ownInventoryOpen, externalTargets, ownTargets);
     ```
   - Когда инвентарь закрыт — `tickServer` вообще не вызывается. Когда открыт чужой труп/ящик (`MODE_CORPSE`/`MODE_LOOT_BLOCK`) — обыскивается только внешний контейнер; как только игрок снял рюкзак и открыл **свой** инвентарь (`MODE_OWN_INVENTORY`) — начинается обыск снятого рюкзака!

### 8.2. Редкий спавн экипировки на враждебных мобах (`MobGearSpawner` + `LivingEntityDeathMixin` + `CorpseEntity`)
- **Класс `com.tarkovcraft.entity.MobGearSpawner`:**
  - Регистрируется в `TarkovCraftMod.onInitialize()`: `ServerEntityEvents.ENTITY_LOAD.register(MobGearSpawner::onEntityLoad);`
  - Целевые мобы: `ZombieEntity`, `SkeletonEntity`, `PillagerEntity`, `VindicatorEntity`, `HuskEntity`, `StrayEntity` (проверять `!mob.isBaby()`!).
  - **Защита от повторного ролла при перезагрузке чанка:**
    - Использовать встроенные персистентные command-tags сущности (`Set<String> tags = mob.getCommandTags()` — сохраняются ванильным NBT `Tags`, 0 новых миксинов!):
    - Если `tags.contains("tarkovcraft:gear_rolled")` -> return немедленно!
    - Иначе `mob.addCommandTag("tarkovcraft:gear_rolled");`
  - **Ролл (~8%, `TarkovConfig.get().mobGearSpawnChance`):**
    - Если `world.getRandom().nextFloat() < cfg.mobGearSpawnChance`:
      - `mob.addCommandTag("tarkovcraft:geared_mob");`
      - Выдать лёгкую/среднюю броню TarkovCraft (класс 1–3) из каталога:
        - Шлем (70%): `mob.equipStack(EquipmentSlot.HEAD, helmetStack); mob.setEquipmentDropChance(EquipmentSlot.HEAD, 0.0f);`
        - Бронежилет (70%): `mob.equipStack(EquipmentSlot.CHEST, armorStack); mob.setEquipmentDropChance(EquipmentSlot.CHEST, 0.0f);`
        - Если ни шлем, ни жилет не выпали — гарантированно выдать хотя бы шлем.
      - С шансом `cfg.mobBackpackChance` (35%): `mob.addCommandTag("tarkovcraft:geared_backpack");`
      - С шансом `0.45f`: `mob.addCommandTag("tarkovcraft:geared_rig");`
- **Смерть моба (`LivingEntityDeathMixin` + `CorpseEntity.fromMob`):**
  - В `LivingEntityDeathMixin`:
    - Если `self.getCommandTags().contains("tarkovcraft:geared_mob")` -> **всегда** спавнить `CorpseEntity.fromGearedMob(world, self)`.
    - Иначе (обычный гуманоидный моб) -> спавнить `CorpseEntity.fromRegularMob(world, self)` **только** с вероятностью `TarkovConfig.get().corpseBaseLootChance` (по умолчанию 25%)!
  - В `CorpseEntity`:
    - `fromGearedMob`: переносит реальный `HEAD` (`TarkovArmorItem`) и `CHEST` (`TarkovArmorItem`) с моба в `loadout`, при `"tarkovcraft:geared_rig"` добавляет необысканную разгрузку с лутом (`MILITARY`/`CIVILIAN`), при `"tarkovcraft:geared_backpack"` — необысканный рюкзак с лутом (`MILITARY`/`CIVILIAN`/`MEDICAL`), и 1–3 предмета в карманах (`SCAV_POCKET`).
    - `fromRegularMob`: **без** халявной брони/рюкзака — только 1–3 мелких предмета в карманах (`SCAV_POCKET`, необысканные).
    - `tick()`: использовать `TarkovConfig.get().getCorpseLifetimeTicks()` вместо захардкоженного `MAX_LIFETIME_TICKS`.

---

## 9. Ключи локализации (`assets-catalog` — `en_us.json` и `ru_ru.json`)

| Ключ | `en_us.json` | `ru_ru.json` |
|---|---|---|
| `block.tarkovcraft.airdrop_crate` | `Supply Airdrop Crate` | `Контейнер с аирдропом` |
| `entity.tarkovcraft.airdrop` | `Supply Airdrop` | `Аирдроп` |
| `ui.tarkovcraft.wb_slot_preview` | `SLOT LAYOUT` | `СХЕМА СЛОТОВ` |
| `ui.tarkovcraft.wb_only_available` | `Available` | `Доступные` |
| `ui.tarkovcraft.wb_sort_default` | `Sort: Default` | `Сорт.: По умолч.` |
| `ui.tarkovcraft.wb_sort_armor_desc` | `Sort: Class ↓` | `Сорт.: Класс ↓` |
| `ui.tarkovcraft.wb_sort_armor_asc` | `Sort: Class ↑` | `Сорт.: Класс ↑` |
| `ui.tarkovcraft.wb_sort_cap_desc` | `Sort: Slots ↓` | `Сорт.: Слоты ↓` |
| `ui.tarkovcraft.wb_sort_cap_asc` | `Sort: Slots ↑` | `Сорт.: Слоты ↑` |
| `message.tarkovcraft.airdrop_incoming_coords` | `[AIRDROP] Supply drop inbound near X: %s, Z: %s!` | `[АИРДРОП] Сброшен груз снабжения в районе X: %s, Z: %s!` |
| `message.tarkovcraft.airdrop_incoming` | `[AIRDROP] Supply drop inbound nearby — look for red smoke!` | `[АИРДРОП] Поблизости сброшен груз — ищите красный дым!` |
| `message.tarkovcraft.config_reloaded` | `[TarkovCraft] Config reloaded from config/tarkovcraft.json` | `[TarkovCraft] Конфиг перезагружен из config/tarkovcraft.json` |

---

## 10. Тест-кейсы (JUnit 5) и критерии приёмки

В `src/test/java/com/tarkovcraft/loot/WorkbenchLootAirdropConfigTest.java`:
1. **`TarkovConfig`:**
   - Дефолтные значения соответствуют §3.2; `loadFromPath(tempFile)` создаёт JSON, корректно читает изменённые значения и клампит невалидные (`airdropIntervalMinutes = -5 -> 1`, `mobGearSpawnChance = 5.0f -> 1.0f`, `airdropMaxRadius < airdropMinRadius -> clamped`).
2. **`TarkovLootGenerator`:**
   - Все 5 пулов (`MILITARY`, `MEDICAL`, `CIVILIAN`, `AIRDROP`, `SCAV_POCKET`) генерируют непустые валидные предметы без исключений; в `AIRDROP` попадает высокоуровневая броня/патроны `AP`/`IGN`/кейсы; `lootDensityMultiplier` масштабирует количество лута.
3. **§6 Обыск (`SearchSession` + `ItemRarityRegistry`):**
   - При `ownInventoryOpen = false` снятый рюкзак НЕ обыскивается; при `ownInventoryOpen = true` — обыскивается сначала Stage 1 (`containerRevealSeconds`), затем Stage 2 по редкости.
   - `ItemRarityRegistry` корректно повышает тир для брони 5–6 кл. и патронов `AP`/`IGN`.
4. **Регистрация мира, блоков, сущностей и ассетов:**
   - `ModBlocks.AIRDROP_CRATE`, `ModEntities.AIRDROP`, 4 `Feature` зарегистрированы; все 8 JSON worldgen-файлов (`configured_feature` + `placed_feature`) и модель/блокстейт/лут-таблица `airdrop_crate` присутствуют на classpath и валидны.

### Команда верификации (JDK 17 обязателен!)
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.9.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build --no-daemon
```
**Критерии приёмки:**
- `compileJava`, `test` и `remapJar` проходят с кодом `0`.
- Строгая граница клиент/сервер (`AirdropEntityRenderer`, `TarkovWorkbenchScreen`, `TarkovInventoryScreen` строго в `com.tarkovcraft.client.*`).
- Нет новых зависимостей в `build.gradle` и нет хрупких миксинов в чужие моды.
