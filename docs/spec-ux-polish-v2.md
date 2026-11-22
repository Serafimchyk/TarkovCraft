# Спецификация: UX & Gameplay Polish v2 (10 улучшений TarkovCraft)

**Статус:** Approved Design Spec (`architect`)  
**Стек:** Minecraft `1.20.1`, Fabric Loader `0.16.10`, Fabric API `0.92.12+1.20.1`, Yarn `1.20.1+build.10`, Java `17`, `environment: "*"`.  
**Gradle / `fabric.mod.json` / `tarkovcraft.mixins.json`:** Изменения **не требуются** (0 новых зависимостей, 0 новых миксинов; zero-hard-dependency на TaCZ сохранён; **TactEq не трогать**).

---

## 1. Разделение владения файлами (строго без пересечений)

| Воркер | Права владения файлами |
| :--- | :--- |
| **`assets-catalog`** | ТОЛЬКО `src/main/resources/assets/tarkovcraft/models/item/*.json` (27 моделей ближнего боя) и `src/main/resources/assets/tarkovcraft/lang/{en_us,ru_ru}.json` |
| **`java-catalog`** | ТОЛЬКО `src/main/java/**` и `src/test/java/**` |

> `assets-catalog` и `java-catalog` работают **параллельно**, опираясь на зафиксированные ниже контракты и ключи локализации.

---

## 2. Детальная спецификация всех 10 пунктов

### Пункт 1 — Airdrop: долгое падение (~85–95с) и спавн между игроками (`AirdropEntity`, `AirdropManager`)

1. **`AirdropEntity.java`:**
   - Изменить константы:
     - `DESCENT_SPEED = -0.065` (было `-0.14`)
     - `MAX_AIR_TICKS = 3600` (было `1200` — защита на 180 сек)
     - `shouldRender(double distance)`: увеличить до `256.0 * 256.0` (чтобы парашют был виден издалека в небе).
2. **`AirdropManager.java`:**
   - В `spawnAirdropAt`:
     - `int spawnY = Math.min(world.getTopY() - 8, Math.max(surfaceY + 120, world.getSeaLevel() + 110));`
     - При высоте `+120` блоков и скорости `-0.065` блоков/тик время спуска составляет `~1846` тиков (**~92 секунды**) — игроки успевают заметить парашют и сбежаться к точке.
   - В `tickServer`:
     - Если `activePlayers.size() >= 2`: вызывать новый метод `spawnAirdropBetweenPlayers(overworld, activePlayers)` вместо выбора одного случайного игрока!
     - **Алгоритм `spawnAirdropBetweenPlayers(ServerWorld world, List<ServerPlayerEntity> players)`:**
       1. Сформировать пары игроков `(a, b)`, расстояние между которыми по XZ `<= 1200` блоков (исключаем пары за 10 000 блоков друг от друга!).
       2. Если есть хотя бы одна такая пара: выбрать случайную (или ближайшую) пару `(a, b)`, вычислить середину `midX = (a.x + b.x) / 2`, `midZ = (a.z + b.z) / 2`.
       3. Проверить точки вокруг `(midX, midZ)` со смещением вдоль отрезка `a <-> b` (`t = 0.50, 0.42, 0.58, 0.34, 0.66, 0.25, 0.75, 0.15, 0.85`), на каждом шаге пробуя до 6 небольших радиальных сдвигов (`12..36` блоков) в поисках **уже прогруженного сухопутного чанка** (`world.isChunkLoaded(cx >> 4, cz >> 4)` + `world.getFluidState(surfacePos).isEmpty()` + `world.getFluidState(surfacePos.up()).isEmpty()`).
       4. Как только найдена сухая прогруженная точка — вызвать `spawnAirdropAt(world, cx, cz, true)`.
       5. Если пары в пределах `1200` блоков нет (игроки слишком далеко друг от друга) или между ними сплошной океан — безопасный фоллбэк в `spawnAirdropNear(players.get(random))`.
     - В `spawnAirdropAt`: в сообщении координат округлять или выводить `(x, z)` и проигрывать звук всем игрокам в радиусе `<= 400` блоков (или всем в Overworld).

---

### Пункт 2 — Фикс просвечивания 3D-модели оружия поверх тултипа/окон (скрин 1)

**Причина:** TaCZ-оружие использует 3D-рендерер (`DynamicItemRenderer`) внутри `DrawContext.drawItem(...)`, который записывает ближние значения в OpenGL depth buffer. Все последующие 2D-слои (тултипы, другие окна, контекстное меню) отсекаются depth-тестом поверх 3D-меша.

**Проверенные сигнатуры (`javap` по MC 1.20.1 / Yarn):**
- `com.mojang.blaze3d.systems.RenderSystem.clear(int mask, boolean getError)`
- `org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT`
- `net.minecraft.client.MinecraftClient.IS_SYSTEM_MAC`
- `net.minecraft.client.gui.DrawContext.draw()` (сбрасывает накопленные 2D-батчи перед очисткой depth buffer!)

**Точный фикс (`TarkovUiTheme.java`, `TarkovInspectWindow.java`, `TarkovInventoryScreen.java` — строго client-only!):**
1. Добавить в `TarkovUiTheme`:
   ```java
   public static void clearDepthBuffer(DrawContext ctx) {
       ctx.draw();
       RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
   }
   ```
2. Вызывать `TarkovUiTheme.clearDepthBuffer(context)`:
   - В `TarkovInspectWindow.render`: сразу ПОСЛЕ `ctx.drawItem(stack, -8, -8)` и `ctx.getMatrices().pop()` (чтобы 3D-модель в превью окна не портила depth-буфер ни для своего текста, ни для следующих окон/тултипов!).
   - В `TarkovInventoryScreen.render`:
     - перед рендером каждого плавающего `TarkovContainerWindow` и каждого `TarkovInspectWindow`,
     - перед `renderDragPreview`,
     - перед `splitModal.render`,
     - перед `renderContextMenu`,
     - перед `renderHoverTooltip`.
   - В начале `TarkovUiTheme.drawFlatTooltip(...)` — гарантирует, что ни один тултип во всём моде никогда не перекроется 3D-предметом.

---

### Пункт 3 — Логическое разделение допустимых тиров патронов (`TaCZCaliberCatalog`, `AmmoTier`, сквозная интеграция)

1. **`TaCZCaliberCatalog.CaliberSpec`:**
   - Добавить поле `EnumSet<AmmoTier> allowedTiers` (хранить через `Set<AmmoTier>` / `EnumSet.copyOf(...)`, сохраняя порядок `HP, FMJ, AP, IGN`):
     - **`SHOTGUN`** (`tacz:12g`, и динамические с `12g`/`20g`/`410`/`gauge`/`shot`): `EnumSet.of(HP, FMJ, AP)` (**без `IGN`** — картечь / стандартная пуля / бронебойная пуля AP-20).
     - **`HEAVY_SNIPER`** (`tacz:338`, `tacz:50bmg`, и динамические с `50bmg`/`338`/`127`/`12_7`/`145`): `EnumSet.of(FMJ, AP, IGN)` (**без `HP`** — антиматериальные калибры без экспансивки).
     - **Обычные пистолетные / ПП и магнумы** (`tacz:9mm`, `tacz:45acp`, `tacz:762x25`, `tacz:22wmr`, `tacz:357mag`, `tacz:50ae`, `tacz:500mag`, и динамические `9x18`/`9x19`/`380`/`45acp`): `EnumSet.of(HP, FMJ, AP)` (**без `IGN`**).
     - **Бронебойные PDW** (`tacz:57x28`, `tacz:46x30`), **промежуточные (`ASSAULT_RIFLE`)** (`545x39`, `556x45`, `58x42`, `300blk`, `762x39`) и **винтовочные (`BATTLE_SNIPER`)** (`68x51fury`, `308`, `762x54`, `30_06`, `45_70`, `792x57`): **все 4 тира** `EnumSet.allOf(AmmoTier.class)`.
   - Методы-хелперы на `CaliberSpec` и `TaCZCaliberCatalog`:
     - `spec.isTierAllowed(AmmoTier tier)`
     - `spec.getAllowedTiersList()` (упорядоченный `List<AmmoTier>`)
     - `spec.clampTier(AmmoTier requested)` — если разрешён -> вернуть его; если запросили `IGN` у дроби/пистолета -> `AP`; если запросили `HP` у `.50 BMG`/`.338` -> `FMJ`.
     - `TaCZCaliberCatalog.isTierAllowed(String ammoId, AmmoTier tier)`
2. **Сквозное применение:**
   - `ModItems.TARKOV_AMMO_GROUP` (`tarkov_ammo_tab`): добавлять только `for (AmmoTier tier : spec.getAllowedTiersList())`.
   - `TarkovWorkbenchRecipes.getAmmoRecipe` и `TarkovWorkbenchScreenHandler.handleCraftAmmo`: если `!spec.isTierAllowed(tier)` -> отклонять крафт на сервере (`return;`)!
   - `TarkovWorkbenchScreen` (вкладка AMMO):
     - При выборе нового калибра автоматически корректировать `selectedTier = spec.clampTier(selectedTier)`.
     - Недопустимые для выбранного калибра кнопки тиров рисовать затемнёнными (`0x66101215`, текст `0xFF4A4E55`) и некликабельными (с подписью N/A или зачёркиванием).
   - `TarkovLootGenerator.rollAmmo`: прогонять выпавший тир через `spec.clampTier(rolledTier)`.
   - Тултипы патронов и оружия (§2.9): для дробовиков в описании тира можно уточнять пояснение (`HP` = Картечь/Экспансивная, `FMJ` = Стандартная пуля, `AP` = Подкалиберная бронебойная AP-20), а в инспекте оружия выводить список допустимых тиров калибра.

---

### Пункт 4 — Единая шкала голода / энергии `0..100` (скрин 3)

В инвентаре (`TarkovInventoryScreen.drawBottomStatsStrip`) энергия уже считается как `foodLevel * 5` (`0..100`), а насыщение как `saturationLevel * 5` (`0..100`). Приводим **все** места к этой же единой шкале `0..100`:
- Формула для любого `FoodComponent fc`:
  - `int energy = fc.getHunger() * 5;` (например, ванильные `+7` -> **`+35`** из 100!)
  - `int sat = Math.round(fc.getHunger() * fc.getSaturationModifier() * 10.0f);` (т.к. ванильное насыщение = `hunger * satMod * 2.0f`, умноженное на `5` = `* 10.0f`; например, `10.5 * 5 = +53` из 100!)
- **Места обновления (`java-catalog` + `assets-catalog`):**
  1. `TarkovCraftClient.java` (`ItemTooltipCallback` для `stack.isFood()`): выводить `energy` и `sat` через `tooltip.tarkovcraft.food_stats` (`"Energy: +%s  |  Saturation: +%s"` / `"Энергия: +%s  |  Насыщение: +%s"`).
  2. `TarkovInspectWindow.java` (строки 107–113): выводить те же `energy` и `sat` через `ui.tarkovcraft.inspect_food`.
  3. `BodyHealthHudHelper.drawCompactTopRight`: добавить под/рядом с силуэтом компактную строку `E:XX` (`foodLevel * 5`, жёлтый при `<= 30`, красный при `<= 15`), чтобы игрок видел свою `0..100` энергию прямо на HUD.
  4. `TarkovInventoryScreen.drawBottomStatsStrip`: уже выводит `E:` (`foodLevel * 5`) и `S:` (`Math.round(saturationLevel * 5.0f)`) — проверить, что подписи идентичны.

---

### Пункт 5 — Визуальное отображение 27 моделей ближнего боя в руках (`assets-catalog`)

**Диагностика реальных моделей и PNG-текстур:**
- Сейчас ВСЕ 27 моделей в `src/main/resources/assets/tarkovcraft/models/item/*.json` имеют `"parent": "minecraft:item/generated"` БЕЗ блока `"display"`, поэтому предмет держится посередине и в мелком масштабе (лопата выглядит как ложка, а нож «Касатка» держится за лезвие).
- Измерение фактической ориентации текстур (64×64 PNG) выявило 3 геометрических группы:
  1. **Вертикальные (`VERT`: лопата МПЛ-50, топоры `crash_axe`, `antikvarnyy_topor`, `pokhodnyy_topor`, `kiba_arms_tactical_tomahawk`, `takticheskiy_tomagavk_sog_voodoo_haw`, ледоруб `ledorub_red_rebel`, лом `lom_montirovka_freeman`, дубинка `politseyskaya_dubinka_pr_taran`, мачете `machete_*`, мечи `miller_bros_*`, `apok_tactical_wasteland_gladius`):** рукоять строго внизу (`y ≈ 44..62`), клинок/лезвие вверху (`y ≈ 2..24`).
     - Оставить `"parent": "minecraft:item/generated"` и добавить `"display"`:
       ```json
       "display": {
         "thirdperson_righthand": { "rotation": [0, -90, 0], "translation": [0, 5.5, 1.0], "scale": [1.15, 1.15, 0.9] },
         "thirdperson_lefthand":  { "rotation": [0, 90, 0],  "translation": [0, 5.5, 1.0], "scale": [1.15, 1.15, 0.9] },
         "firstperson_righthand": { "rotation": [0, -90, 12], "translation": [1.13, 4.2, 1.13], "scale": [0.95, 0.95, 0.8] },
         "firstperson_lefthand":  { "rotation": [0, 90, -12], "translation": [1.13, 4.2, 1.13], "scale": [0.95, 0.95, 0.8] }
       }
       ```
     - *(Для `malaya_pekhotnaya_lopata_mpl_50_5bead2`, `crash_axe_5bffe7`, `antikvarnyy_topor_5bc9c1`, `labris_tsepen_679ba9`, `miller_bros_*`, `apok_*` можно масштаб `thirdperson` `1.25` / `firstperson` `1.05`, чтобы лопата и топоры выглядели солидно!)*
  2. **Горизонтальные ножи (`HORIZ`: `nozh_ka_bar_*`, `nozh_finka_nkvd_*`, `shtyk_nozh_*`, `nozh_bars_*`, `nozh_sektanta_*`, `nozh_microtech_*`):**
     - `assets-catalog` обязан скриптом/осмотром PNG определить, с какой стороны (`LEFT` или `RIGHT`) находится **рукоять** у каждого горизонтального ножа:
       - Если рукоять СЛЕВА, а остриё СПРАВА -> поворот в `righthand`: `rotation: [0, -90, 90]` (переводит остриё вверх-вперёд, рукоять в кисть!), `translation: [0, 3.8, 1.0]`, `scale: [0.85, 0.85, 0.8]`; `lefthand`: `rotation: [0, 90, -90]`.
       - Если рукоять СПРАВА, а остриё СЛЕВА -> поворот в `righthand`: `rotation: [0, -90, -90]`, `lefthand`: `rotation: [0, 90, 90]`.
  3. **Диагональные ножи и кукри (`TR-BL /` или `TL-BR \`: `nozh_kasatka_6aa948`, `nozh_akula_674d90`, `takticheskoe_kukri_united_cutlery_m4_65ca45`, `labris_tsepen_679ba9`):**
     - **Особо проверить `nozh_kasatka_6aa948` и `nozh_akula_674d90`!** Определить по PNG, в каком углу рукоять (`BL`, `TR`, `TL` или `BR`), и выставить такой Z-угол в `display`, чтобы **рукоять оказывалась внизу в ладони**, а остриё смотрело **вверх и вперёд** (для `TR-BL` с рукоятью в `BL` и лезвием в `TR` подходит стандартный `minecraft:item/handheld` или `rotation: [0, -90, 45]`; если у Касатки рукоять в `TR`, а лезвие в `BL`, нужен поворот `rotation: [0, -90, -135]` + `translation: [0, 4.0, 1.0]`!).
     - `assets-catalog` проверяет ВСЕ 27 PNG программно/визуально (где толщина/цвет рукояти vs клинка) и проставляет точные `display`-блоки для каждого из 27 файлов `models/item/<melee_id>.json`!

---

### Пункт 6 — Серверная система времени применения медикаментов и стимуляторов (без мгновенного хила!)

1. **Длительность применения (`MedicalType.java` + `TarkovStimulatorItem.java`):**
   - Добавить `int useDurationTicks` в `MedicalType`:
     - Бинты / жгуты (`BANDAGE`, `ARMY_BANDAGE`, `CAT_TOURNIQUET`, `ESMARCH`, `CALOK_B`): `40` тиков (`2.0с`)
     - Таблетки / мази обезбол (`ANALGIN`, `IBUPROFEN`, `AUGMENTIN`, `VASELINE`, `GOLDEN_STAR`): `40` тиков (`2.0с`)
     - Шины (`SPLINT`, `ALU_SPLINT`, `TIGZRESQ_SPLINT`): `60` тиков (`3.0с`)
     - Аптечки: `AI2_MEDKIT` = `45`т (`2.25с`), `CAR_MEDKIT`/`IFAK`/`AFAK` = `60`т (`3.0с`), `SALEWA_MEDKIT`/`GRIZZLY` = `70`т (`3.5с`)
     - Хирургия: `CMS_KIT` = `100`т (`5.0с`), `SURV12_KIT` = `110`т (`5.5с`)
   - В `TarkovStimulatorItem`: `public static final int USE_DURATION_TICKS = 35;` (`1.75с`).
2. **Синхронизация прогресса через `BodyPartHealth` (0 новых S2C-пакетов!):**
   - Добавить в `BodyPartHealth` 4 transient/synced поля (сериализуются в `writeNbt()`/`readNbt()` для `S2C_HEALTH_SYNC`, но **сбрасываются** в `resetFull()` и при загрузке игрока `PlayerEntityMixin.readCustomDataFromNbt`):
     - `String activeMedItemId` (пустая строка = нет активного применения)
     - `int activeMedElapsedTicks`
     - `int activeMedTotalTicks`
     - `@Nullable BodyPart activeMedTargetPart`
   - Геттеры: `isMedUseActive()`, `getActiveMedProgress()` (`0..1`), `getActiveMedRemainingSeconds()`, `getActiveMedItemId()`, `getActiveMedTargetPart()`.
3. **Серверный оркестратор `com.tarkovcraft.health.MedicalUseManager`:**
   - `private static final Map<UUID, ActiveMedUse> ACTIVE = new ConcurrentHashMap<>();`
   - `ActiveMedUse` хранит: `boolean fromHand`, `@Nullable Hand hand`, `byte srcLoc`, `int srcIdx`, `@Nullable UUID srcEntryId`, `Item expectedItem`, `@Nullable BodyPart targetPart`, `boolean isStimulator`, `int elapsedTicks`, `int totalTicks`.
   - **Точки старта (все 4 пути ведут сюда — мгновенный хил полностью убран!):**
     1. `TarkovMedicalItem.use(world, user, hand)` -> `MedicalUseManager.startFromHand(sp, hand, stack)`
     2. `TarkovStimulatorItem.use(world, user, hand)` -> `MedicalUseManager.startFromHand(sp, hand, stack)`
     3. `TarkovScreenHandler.handleUseItem` (для `TarkovMedicalItem` и `TarkovStimulatorItem`) -> `MedicalUseManager.startFromInventory(sp, srcLoc, srcIdx, srcEntryId, null)`
     4. `TarkovScreenHandler.handleHealPart` (вкладка HEALTH + новые иконки из п.7!) -> `MedicalUseManager.startFromInventory(sp, srcLoc, srcIdx, srcEntryId, targetPart)`
   - **Проверки при старте:**
     - Нельзя запустить второй медикамент, пока `ACTIVE.containsKey(playerId)` -> `message.tarkovcraft.med_busy`.
     - Проверка кулдауна (`player.getItemCooldownManager().isCoolingDown(item)`).
     - Для `TarkovMedicalItem`: заранее проверить, есть ли цель (`targetPart != null ? MedicalHelper.canApplyToPart(health, stack, targetPart) : MedicalHelper.findAutoTarget(health, stack) != null || type.isPainkillerOnly()`). Если лечить нечего -> `message.tarkovcraft.med_not_needed` и НЕ запускать таймер!
     - Предмет **НЕ списывается** при старте; проигрывается тихий звук начала (`SoundEvents.ITEM_ARMOR_EQUIP_LEATHER`, vol `0.45f`, pitch `1.2f`).
   - **Тик (`MedicalUseManager.tickPlayer(ServerPlayerEntity player)` — вызывать из `TarkovCraftMod` в `END_SERVER_TICK` для живых игроков, плюс `removePlayer` на `DISCONNECT`/`onDeath`):**
     - Проверить, что исходный предмет всё ещё существует в указанном слоте (`hand` или `srcLoc/srcIdx/srcEntryId`) и `stack.getItem() == active.expectedItem`. Если исчез/переложен/выкинут -> отмена (`message.tarkovcraft.med_cancelled`), очистка полей в `BodyPartHealth`, `syncBodyHealth()`.
     - `elapsedTicks++`; обновлять `BodyPartHealth` поля и каждые `2` тика вызывать `holder.tarkovcraft$syncBodyHealth()`.
     - По достижении `elapsedTicks >= totalTicks`:
       - Применить эффект (`MedicalHelper.applyMedical(...)` или `TarkovStimulatorItem.applyStimulator(...)`), **только теперь** списать заряд/предмет в исходном контейнере, выставить КД (`getCooldownTicks()` / `COOLDOWN_TICKS`), проиграть звук завершения, очистить `ACTIVE` и `BodyPartHealth` поля, синхронизировать `syncBodyHealth()`, `syncLoadout()`, и `tsh.syncToClient(player)` (если открыт `TarkovScreenHandler`)!
4. **Клиентский прогресс-бар лечения:**
   - В `InGameHudMixin` (по центру над хотбаром, `y = screenH - 46`) И в `TarkovInventoryScreen` (компактная полоска внизу верхней панели/вкладки ЗДОРОВЬЕ): при `health.isMedUseActive()` рисовать тёмную плашку с иконкой предмета, заливкой `ACCENT_SAND` / зелёным (`0xFF4CAF50`) и текстом `"<Название> — 2.3s"`.

---

### Пункт 7 — Мини-иконки подходящих медикаментов рядом с каждой раненой конечностью (`TarkovInventoryScreen` + `BodyHealthHudHelper`)

- Во вкладке **`TAB_HEALTH`** (`TarkovInventoryScreen`):
  - Для каждой из 7 частей тела (`BodyPart`), у которой есть проблема (`health.getHp(part) < health.getEffectiveMaxHp(part) - 0.5f || health.hasAnyInjury(part) || health.isBlacked(part)`):
    - Отобрать из `collectedMeds` (карманы + разгрузка + рюкзак + хотбар, уже собираемых в `collectOwnMedicalItems`) до **4 уникальных по `Item`** подходящих медикаментов, для которых `MedicalHelper.canApplyToPart(health, slot.stack(), part) == true`.
    - Отсортировать по приоритету: 1) лечение тяжёлого кровотека (если есть), 2) хирургия (если выбита), 3) шина (если перелом), 4) лёгкий кровотек, 5) аптечка HP.
    - Отрисовать компактный ряд кнопок `14×14` (с иконкой `12×12` через `TarkovUiTheme.drawScaledItem`, **БЕЗ цифр прочности**) непосредственно справа от правых zone-баров (`HEAD`, `THORAX`, `STOMACH`, `RIGHT_ARM`, `RIGHT_LEG`) и слева от левых (`LEFT_ARM`, `LEFT_LEG`) — места в полноэкранной вкладке HEALTH с запасом!
    - Хранить кликабельные хитбоксы `QuickHealButton(Rect rect, HealthMedSlot med, BodyPart targetPart)`:
      - При наведении: золотая рамка `ACCENT_SAND` + тултип с названием медикамента и целевой частью тела.
      - При ЛКМ: сразу вызывает `ClientNetworkSender.sendHealPart(med.loc(), med.idx(), med.entryId(), targetPart)` -> запускает серверный таймер из п.6 именно на эту конечность!

---

### Пункт 8 — Несколько окон инспекта + плавающие окна контейнеров даже при открытом трупе/ящике

#### 8.1. Несколько окон инспекта (`TarkovInventoryScreen` + `TarkovInspectWindow`)
- Заменить `@Nullable private TarkovInspectWindow inspectWindow;` на `private final List<TarkovInspectWindow> inspectWindows = new ArrayList<>();` (максимум `6` одновременно открытых окон).
- При открытии нового окна (`ctx.tarkovcraft.inspect`): смещать стартовую позицию на `(count * 16, count * 16)` (каскадом, с `clampToScreen`).
- Рендер: цикл `0 .. inspectWindows.size() - 1` с вызовом `TarkovUiTheme.clearDepthBuffer(context)` перед каждым окном!
- Клики (`mouseClicked`): обходить с конца (`size - 1` вниз до `0` — сверху вниз по Z-порядку):
  - Попадание в окно -> переместить его в конец списка (поднять наверх!).
  - Клик по `[X]` (или ПКМ по окну) -> удалить из списка.
- `ESC`: закрывает самое верхнее окно (`inspectWindows.remove(inspectWindows.size() - 1)`).

#### 8.2. Плавающие окна контейнеров (`TarkovContainerWindow` + серверная поддержка в `TarkovScreenHandler`)
- **Цель:** позволить открывать кейсы (`TarkovCaseItem`), рюкзаки (`BackpackItem`) и разгрузки (`TacticalRigItem`) как **перетаскиваемые плавающие окна** поверх ЛЮБОГО режима (`MODE_OWN_INVENTORY`, `MODE_CORPSE`, `MODE_LOOT_BLOCK`), включая уже обысканные контейнеры внутри трупа или лут-ящика!
- **Сетевой протокол (`TarkovNetworking.java` + `ClientNetworkSender.java`):**
  - Добавить локацию `public static final byte LOC_FLOATING_CONTAINER = 9;`
    - Кодировка `containerIndex` для `LOC_FLOATING_CONTAINER`: `(windowIndex << 8) | (subGridIndex & 0xFF)` (до 4 окон, до 256 отсеков в разгрузке — полностью укладывается в `varInt` без изменения схемы `C2S_MOVE_ITEM` / `C2S_QUICK_TRANSFER` / `C2S_SPLIT_STACK` / `C2S_USE_ITEM` / `C2S_HEAL_PART`!).
  - Расширить `ScreenStateSnapshot` (`S2C_SCREEN_STATE`) полем `List<OpenContainerView> openContainers`:
    ```java
    public record OpenContainerView(UUID containerUuid, String title, List<GridView> grids) { ... }
    ```
  - В `C2S_CLOSE_CASE`: добавить `boolean hasUuid` + `[UUID containerUuid]` (если `hasUuid == false` — закрыть все/последнее; если `true` — закрыть конкретное окно по UUID!).
  - `C2S_OPEN_CASE` оставить с той же бинарной схемой (`srcLoc, srcIdx, hasEntryId, [srcEntryId]`) — расширить серверную логику `handleOpenCase`!
- **Сервер (`TarkovScreenHandler.java`):**
  - Заменить `@Nullable private UUID activeCaseUuid;` на `private final List<UUID> openContainerUuids = new ArrayList<>();` (лимит `MAX_OPEN_WINDOWS = 4`).
  - В `handleOpenCase`:
    - Разрешить открытие **в любом режиме** (`MODE_OWN_INVENTORY`, `MODE_CORPSE`, `MODE_LOOT_BLOCK`, `MODE_GROUND_ITEM`)!
    - Принимать `TarkovCaseItem`, `BackpackItem` или `TacticalRigItem` (из своего инвентаря, слотов трупа, сеток трупа, ящика `LOC_EXTERNAL_GRID`, или даже из другого уже открытого плавающего окна `LOC_FLOATING_CONTAINER`!).
    - **Анти-чит по §6:** если источник внешний (труп / лут-ящик), проверить что сам контейнер `isEntryDiscovered(playerId, entryId)` (нельзя открыть ещё не найденный предмет!). Если у контейнера `requiresSearch(stack) == true`, его `GridView` передаётся с `requiresSearch = true` и его `ContainerSearchMemory` — скрытые предметы внутри останутся силуэтами до обыска!
    - Проставить `ContainerUuid` (записав обратно в родителя: `writeRigGrids` / `writeBackpackGrid` / `writeCaseGrid` / `markExternalDirty()`), добавить UUID в `openContainerUuids` (если уже открыт — поднять в конец), вызвать `syncToClient`.
  - Обобщённый `ContainerHandle` (`resolveContainerHandle(UUID targetUuid)`):
    - Ищет контейнер по UUID в: 1) `PlayerLoadout` (equipment, pockets, rig grids, backpack grid, и вложенные кейсы 1 уровня), 2) Creative `player.getInventory()`, 3) открытый `lootBlock` (если `MODE_LOOT_BLOCK`), 4) открытый `corpse` (equipment + pockets + rig + backpack, если `MODE_CORPSE`), 5) `groundItem` (если `MODE_GROUND_ITEM`).
    - Умеет читать и сохранять `List<GridContainer>` как для `TarkovCaseItem` (1 сетка), так и для `BackpackItem` (1 сетка) и `TacticalRigItem` (N сеток), записывая изменения обратно в родителя!
    - Если контейнер исчез (выкинули/закрыли труп/ушли далеко) -> автоматически удаляет его UUID из `openContainerUuids`.
  - Поддержать `LOC_FLOATING_CONTAINER` в `peekSource`, `extractSource`, `repositionInSameGrid`, `canInsertIntoDestination` (с полной защитой `containsContainerId` и `MAX_NESTING_DEPTH = 2`!), `insertIntoDestination`, `handleQuickTransfer`, `handleSplitStack`, `mutateMedicalInContainer`.
  - При `MODE_OWN_INVENTORY` + ПКМ в руке на кейсе (`TarkovCaseItem.use`): открывает `TarkovScreenHandler` с этим кейсом уже в `openContainerUuids` (показывается как плавающее окно или в правой панели — единое поведение!).
- **Клиент (`TarkovInventoryScreen.java`):**
  - В `openContextMenu`: показывать пункт `ctx.tarkovcraft.open` для ЛЮБОГО `TarkovCaseItem`, `BackpackItem` (не в слоте своего надетого рюкзака, т.к. тот уже развёрнут по центру), `TacticalRigItem` (не в слоте своей разгрузки) во ВСЕХ режимах (`OWN`, `CORPSE`, `LOOT_BLOCK`) -> `ClientNetworkSender.sendOpenCase(...)`!
  - Класс `TarkovContainerWindow` (или внутренний класс в `TarkovInventoryScreen`): хранит позицию `(x, y)` по `UUID`, перетаскивается за заголовок, имеет кнопку `[X]` (`ClientNetworkSender.sendCloseCase(uuid)`), регистрирует свои `InteractiveRegion` с `LOC_FLOATING_CONTAINER` и корректным `(winIdx << 8) | subGridIdx`!
  - Рендерится с `TarkovUiTheme.clearDepthBuffer(context)` перед каждым окном, поддерживает drag-and-drop в/из окна, `F` quick-transfer, ПКМ контекстное меню и `R` поворот!

---

### Пункт 9 — Подробные описания предметов и калибров TaCZ-оружия (скрин 2)

1. **Новый клиентский хелпер `com.tarkovcraft.client.TaCZGunInfoHelper` (`@Environment(EnvType.CLIENT)`, zero-hard-dependency):**
   - Кэш `ConcurrentHashMap<String, GunMeta>` (ключ = `GunId`), где `record GunMeta(String ammoId, int baseMagCapacity, int rpm)`.
   - По `GunId` через reflection вызывает `TimelessAPI.getCommonGunIndex(new Identifier(gunId))` -> `getGunData()` -> `getAmmoId()`, `getAmmoAmount()`, `getRoundsPerMinute(fireMode)` (с безопасным `try/catch (Throwable)`).
   - Читает из NBT ствола: `GunId`, `GunCurrentAmmoCount`, `HasBulletInBarrel`, `GunFireMode` (`AUTO`/`SEMI`/`BURST`), `TarkovLoadedTier`.
   - Возвращает структурированный `GunSummary(String caliberName, String ammoId, String fireMode, int currentAmmo, int maxMag, boolean bulletInBarrel, AmmoTier loadedTier, int penetration, List<AmmoTier> allowedTiers)`.
2. **Обновление `TarkovCraftClient` (`ItemTooltipCallback` для `AmmoTier.isTaczGunStack(stack)`):**
   - Вставлять с индекса `1`:
     - `tooltip.tarkovcraft.gun_caliber`: `Калибр: 5.56x45mm NATO` (золотой/голубой)
     - `tooltip.tarkovcraft.gun_ammo_count`: `Патроны: 30/30 (+1)` (серый/белый)
     - `tooltip.tarkovcraft.gun_fire_mode`: `Режим огня: AUTO` (если есть `GunFireMode`)
     - `tooltip.tarkovcraft.gun_loaded_tier_full`: `Заряжено: [BP/AP] (Проб. 44 | Урон x0.92)` (в цвете тира!)
     - `tooltip.tarkovcraft.gun_allowed_tiers`: `Тиры: HP, PS/FMJ, BP/AP, BS/M995` (тёмно-серый)
3. **Улучшение `TarkovInspectWindow.java`:**
   - Увеличить размер окна до `WIN_W = 256, WIN_H = 224` с поддержкой колёсика мыши (`scrollY`) для длинных описаний!
   - В `collectDetailLines()`:
     - Если `!mc.options.advancedItemTooltips`: **фильтровать** сырые технические строки (`tacz:modern_kinetic_gun`, строки вида `NBT: ...`, `GunId:...`, сырые `namespace:path`), чтобы окно инспекта выглядело как чистая карточка EFT (а при F3+H / advanced tooltips показывать всё!).
     - Сохранять цвета строк (`List<Text>` вместо `List<String>`!) при отрисовке в нижней секции инспекта — тултип становится читаемым и цветным, как в EFT!
     - Для брони, шлемов, масок, плит, медицины, стимуляторов, еды, гранат, кейсов, рюкзаков и разгрузок — проверить, что все ключевые характеристики (класс, материал, прочность, зоны защиты/открытые зоны, время применения, вместимость, энергия/насыщение `0..100`) присутствуют в цветных строках инспекта.

---

### Пункт 10 — Пофиксить отображение названия заряженных патронов справа снизу (`InGameHudMixin.java`)

**Анализ реального кода (`InGameHudMixin.java` + TaCZ `GunHudOverlay`):**
- В `InGameHudMixin.java` (строки 70–246) вообще отсутствует чистый HUD-индикатор заряженного тира патронов, а стандартный `GunHudOverlay` TaCZ занимает правый нижний угол (`x = screenW - 110 .. screenW - 8`, `y = screenH - 46 .. screenH - 6`), из-за чего любая невыровненная подпись либо отсутствует, либо накладывается на счётчик TaCZ / обрезается у правого края!
- **Точный фикс в `InGameHudMixin.java`:**
  - Ввести чистый метод `renderLoadedAmmoHud(DrawContext context, int screenW, int screenH)` (вызывать из `renderHotbar` **ДО** проверки `isCreative()`? Нет — если игрок в Creative, `renderHotbar` возвращается рано; лучше вызывать `renderLoadedAmmoHud` в `renderHotbar` для Survival И через лёгкий `@Inject(method = "render", at = @At("TAIL"))` — либо вызвать `renderLoadedAmmoHud` ДО `if (isCreative)` в `renderHotbar`! Да, вызвать **до** `if (client.player.isCreative()) return;` в `tarkovcraft$renderTarkovHud`, если `client.currentScreen == null && !client.player.isSpectator()`, чтобы работало и в Survival, и в Creative!).
  - Когда в основной руке TaCZ-оружие (`AmmoTier.isTaczGunStack(held)`):
    - Получить `TaCZGunInfoHelper.GunSummary info = TaCZGunInfoHelper.inspectGun(held);`
    - Сформировать короткую чистую строку: `String text = "[" + info.loadedTier().getBadge() + "] " + info.caliberName();` (с `trimWithEllipsis` по макс. ширине `130px`, чтобы никогда не обрезалось и не вылезало за экран!).
    - Разместить плашку **ровно справа снизу НАД HUD TaCZ**: `int boxH = 14; int boxW = Math.min(144, textW + 10); int bx = screenW - boxW - 8; int by = screenH - 64;` (TaCZ `GunHudOverlay` рисуется в `screenH - 46 .. screenH - 6`, зазор `18px` гарантирует отсутствие наложения!).
    - Стилистика: тёмная полупрозрачная плашка `0xCC0D0E10`, левая акцентная полоска `2px` цвета тира (`info.loadedTier().getArgbColor()`), тонкая рамка `0xFF2D3035`, цветной бейдж тира + светлое название калибра!

---

## 3. Ключи локализации (`assets-catalog` — `en_us.json` и `ru_ru.json`)

| Ключ | `en_us.json` | `ru_ru.json` |
|---|---|---|
| `tooltip.tarkovcraft.food_stats` *(обновить!)* | `Energy: +%s  \|  Saturation: +%s` | `Энергия: +%s  \|  Насыщение: +%s` |
| `ui.tarkovcraft.inspect_food` *(обновить!)* | `Energy: +%s  Sat: +%s` | `Энергия: +%s  Насыщ.: +%s` |
| `tooltip.tarkovcraft.gun_caliber` | `Caliber: %s` | `Калибр: %s` |
| `tooltip.tarkovcraft.gun_ammo_count` | `Ammo: %s/%s` | `Патроны: %s/%s` |
| `tooltip.tarkovcraft.gun_ammo_count_chamber` | `Ammo: %s/%s (+1)` | `Патроны: %s/%s (+1)` |
| `tooltip.tarkovcraft.gun_fire_mode` | `Fire Mode: %s` | `Режим огня: %s` |
| `tooltip.tarkovcraft.gun_loaded_tier_full` | `Loaded: [%s] (Pen %s \| Dmg x%s)` | `Заряжено: [%s] (Проб. %s \| Урон x%s)` |
| `tooltip.tarkovcraft.gun_allowed_tiers` | `Allowed Tiers: %s` | `Доступные тиры: %s` |
| `tooltip.tarkovcraft.ammo_shotgun_hp` | `Buckshot — high flesh damage, low armor pen` | `Картечь — высокий урон по телу, слабое пробитие` |
| `tooltip.tarkovcraft.ammo_shotgun_fmj` | `Lead Slug — balanced mid-range slug` | `Свинцовая пуля — сбалансированный заряд` |
| `tooltip.tarkovcraft.ammo_shotgun_ap` | `AP-20 Sabot Slug — penetrates Class 3-4 armor` | `Подкалиберная AP-20 — пробивает 3–4 класс брони` |
| `tooltip.tarkovcraft.med_use_time` | `Use time: %ss` | `Время применения: %s сек` |
| `message.tarkovcraft.med_busy` | `Already applying a medical item` | `Вы уже применяете медикамент` |
| `message.tarkovcraft.med_cancelled` | `Medical application cancelled` | `Применение медикамента прервано` |
| `ui.tarkovcraft.med_using` | `Using %s... %ss` | `Применение: %s... %sс` |
| `tooltip.tarkovcraft.quick_heal` | `Quick use: %s -> %s` | `Применить: %s -> %s` |

---

## 4. Тест-кейсы (JUnit 5) и верификация

В `src/test/java/com/tarkovcraft/ux/UxPolishV2Test.java`:
1. **`TaCZCaliberCatalog` ограничения тиров:**
   - `tacz:12g` разрешает `[HP, FMJ, AP]` и **запрещает `IGN`** (`clampTier(IGN) == AP`).
   - `tacz:50bmg` и `tacz:338` разрешают `[FMJ, AP, IGN]` и **запрещают `HP`** (`clampTier(HP) == FMJ`).
   - `tacz:9mm` и `tacz:45acp` запрещают `IGN`, а `tacz:57x28`, `tacz:46x30`, `tacz:545x39`, `tacz:556x45`, `tacz:308` разрешают все 4 тира.
2. **`MedicalUseState` / `MedicalType.getUseDurationTicks()`:**
   - Все `MedicalType` имеют `useDurationTicks >= 40` (хирургия `>= 100`, аптечки `>= 45`), `TarkovStimulatorItem.USE_DURATION_TICKS >= 30`.
   - `BodyPartHealth` сохраняет и сбрасывает `activeMed*` поля в `writeNbt`/`readNbt` и `resetFull()`.
3. **`AirdropEntity` и `AirdropManager`:**
   - `AirdropEntity` имеет медленный спуск (`|DESCENT_SPEED| <= 0.08`) и `MAX_AIR_TICKS >= 3000`.
4. **Единая шкала голода:**
   - Формула пересчёта `hunger * 5` и `Math.round(hunger * satMod * 10.0f)` даёт `35` энергии и `53` насыщения для `hunger=7, satMod=0.75`.
5. **Все 27 melee-моделей (`models/item/*.json`):**
   - Проверить в тесте (как в `TarkovItemCatalogTest`), что КАЖДАЯ из 27 моделей ближнего боя имеет непустой объект `"display"` с ключами `firstperson_righthand`, `thirdperson_righthand`, `firstperson_lefthand`, `thirdperson_lefthand`!

### Команда верификации (JDK 17 обязателен!)
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.9.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build --no-daemon
```
