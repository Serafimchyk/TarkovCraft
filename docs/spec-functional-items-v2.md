# Спецификация: Functional Items v2 (TarkovCraft — Minecraft 1.20.1 Fabric)

**Статус:** Approved Design Spec (`architect`)  
**Стек:** Minecraft `1.20.1`, Fabric Loader `0.16.10`, Fabric API `0.92.12+1.20.1`, Yarn `1.20.1+build.10`, Java `17`, `environment: "*"` (обязательна совместимость с выделенным сервером).  
**Mixin / Gradle / `fabric.mod.json`:** Изменения **не требуются** (0 новых миксинов, 0 новых зависимостей).

---

## 1. Разделение владения файлами (строго без пересечений)

| Воркер | Права владения файлами |
| :--- | :--- |
| **`assets-catalog`** | ТОЛЬКО `src/main/resources/data/tarkovcraft/catalog/items_catalog.json`, `src/main/resources/assets/tarkovcraft/lang/en_us.json`, `src/main/resources/assets/tarkovcraft/lang/ru_ru.json` |
| **`java-catalog`** | ТОЛЬКО `src/main/java/**` и `src/test/java/**` |

> **Порядок работы:** `assets-catalog` и `java-catalog` могут работать **параллельно**, так как все JSON-ключи, имена enum-констант и ключи локализации зафиксированы ниже как жёсткий контракт.

### Список изменяемых/создаваемых файлов

**Владение `assets-catalog`:**
1. `src/main/resources/data/tarkovcraft/catalog/items_catalog.json` — обновление размеров `face_mask`, простановка 22 уникальных `stimPreset`, добавление `gridWidth`/`gridHeight` и уточнение размеров для 33 `case`, добавление `grenadeType` для 9 `grenade`.
2. `src/main/resources/assets/tarkovcraft/lang/en_us.json` — 6 ключей вкладок креатива, тултипы стимуляторов, еды, кейсов, гранат, сущность гранаты.
3. `src/main/resources/assets/tarkovcraft/lang/ru_ru.json` — русская локализация всех тех же ключей.

**Владение `java-catalog`:**
1. `src/main/java/com/tarkovcraft/client/screen/TarkovUiTheme.java` — scissor-клиппинг и безопасный масштаб в `drawScaledItem`, category tints для кейсов/гранат/стимуляторов.
2. `src/main/java/com/tarkovcraft/item/TarkovStimulatorItem.java` — 22 уникальных профиля `StimPreset` (с ленивыми `Supplier<StatusEffect>`), применение эффектов и подробный автогенерируемый тултип.
3. `src/main/java/com/tarkovcraft/client/TarkovCraftClient.java` — регистрация `ItemTooltipCallback.EVENT` (голод/насыщение для всей еды) и `EntityRendererRegistry.register(ModEntities.GRENADE, FlyingItemEntityRenderer::new)` (строго client-only).
4. `src/main/java/com/tarkovcraft/item/TarkovCaseItem.java` **(новый)** — открываемый кейс (`TarkovSizedItem`, `maxCount(1)`, ПКМ в руке открывает инвентарь с кейсом в правой панели).
5. `src/main/java/com/tarkovcraft/grid/ContainerItemHelper.java` — поддержка `TarkovCaseItem` в `isContainerItem`, `readCaseGrid`, `writeCaseGrid`, `readAllGrids` (автоматически подключает `containsContainerId`, `getNestingDepth`, расчёт веса в `PlayerLoadout`).
6. `src/main/java/com/tarkovcraft/network/TarkovNetworking.java` — новые C2S-пакеты `C2S_OPEN_CASE` и `C2S_CLOSE_CASE` + серверные ресиверы + поддержка `TarkovCaseItem` на земле в `handleInteract`.
7. `src/main/java/com/tarkovcraft/client/ClientNetworkSender.java` — клиентские хелперы `sendOpenCase(...)` и `sendCloseCase()`.
8. `src/main/java/com/tarkovcraft/screen/TarkovScreenHandler.java` — UUID-привязанный `activeCaseUuid`, `CaseHandle` (поддержка кейса в карманах, слотах оружия, разгрузке, рюкзаке, креативном инвентаре и на земле), `openCaseScreen`, `handleOpenCase`, `handleCloseCase`, все операции `LOC_EXTERNAL_GRID` + анти-дюп/анти-рекурсия.
9. `src/main/java/com/tarkovcraft/client/screen/TarkovInventoryScreen.java` — отображение правой панели при `snap.externalGridView() != null` в `MODE_OWN_INVENTORY`, динамический `extCell` под сетки до `10x10`, кнопка `[X]` закрытия кейса, пункт ПКМ-меню «Открыть» для `TarkovCaseItem`, рекурсивная проверка `ContainerItemHelper.containsContainerId` в `evaluateDrop`.
10. `src/main/java/com/tarkovcraft/client/WorldInteractState.java` — (опционально) включить `TarkovCaseItem` в наземное 2-действенное меню Search/Take.
11. `src/main/java/com/tarkovcraft/item/TarkovGrenadeItem.java` **(новый)** — 7 типов `GrenadeType` для всех 9 гранат, бросок ПКМ, кулдаун, подробный тултип.
12. `src/main/java/com/tarkovcraft/entity/TarkovGrenadeEntity.java` **(новый)** — `ThrownItemEntity` с отскоками, ударным/временным запалом, зональным осколочным уроном + кровотечением без разрушения блоков, флешкой с проверкой линии видимости, 15с дымовой завесой M18 и 12с CS-газом с защитой противогазами/респираторами.
13. `src/main/java/com/tarkovcraft/registry/ModEntities.java` — регистрация `ModEntities.GRENADE`.
14. `src/main/java/com/tarkovcraft/registry/TarkovItemCatalog.java` — создание `TarkovCaseItem` для `"case"`, `TarkovGrenadeItem` для `"grenade"`, 6 отдельных списков для вкладок креатива.
15. `src/main/java/com/tarkovcraft/registry/ModItems.java` — замена старых 4 вкладок на ровно 6 чистых вкладок без дублей.
16. `src/test/java/com/tarkovcraft/registry/TarkovItemCatalogTest.java` (и/или новый `src/test/java/com/tarkovcraft/item/FunctionalItemsV2Test.java`) — JUnit 5 тесты на все 6 фич.

---

## 2. Фича 1 — Фикс размеров балаклав/масок и клиппинг иконок в слотах

### 2.1. Контракт для `assets-catalog` (`items_catalog.json`)

Анализ реальных PNG-текстур показал, что иконки балаклав/обычных масок имеют квадратный/вертикальный bbox (`ratio ~0.7–1.1`), но получили размер `2x1`. Применяем строгое правило для всех **133** записей с `"category": "face_mask"`:

1. **Очки и визоры (`id` содержит `ochki` или `vizor`, 41 шт.):** оставить `width: 2, height: 1` (реальный bbox `~53x18`, ratio ~2.9).
2. **Баллистические/армированные маски (`armorClass >= 2`) и противогазы (`id` начинается с `protivogaz_`) (27 шт.):** установить `width: 2, height: 2`.
   - Особо исправить 4 записи, у которых сейчас `2x1`:
     - `legkaya_armirovannaya_maska_ten_smer_68a9b8` -> `2x2`
     - `legkaya_armirovannaya_maska_ten_s_68a9b8_2` -> `2x2`
     - `maska_death_knight_62963c` -> `2x2`
     - `maska_ten_smerti_seraya_68a9b8` -> `2x2`
3. **Все остальные `face_mask` (балаклавы `balaklava_*`, косметические маски `maska_*`/`zayachya_maska_*`/`lisya_maska_*`/`legkaya_maska_*`/`neoprenovaya_maska_*`, полумаски `polumaska_*`, арафатки, банданы, лыжная шапка, бороды, трубка, респираторы `respirator_*`/`respiratornaya_maska_*` — 65 шт.):** установить `width: 1, height: 1`.

*(Кепки/береты/панамы в категории `helmet` с `2x1` имеют реально вытянутый bbox `55x24`, ratio `2.29` — их можно оставить `2x1`, scissor-клиппинг полностью защитит слот.)*

### 2.2. Контракт для `java-catalog` (`TarkovUiTheme.java`)

**Проверенная сигнатура (`javap -c net.minecraft.client.gui.DrawContext`):**
- `public void enableScissor(int x1, int y1, int x2, int y2)` — кладёт `ScreenRect` в `DrawContext$ScissorStack`, автоматически вычисляя `rect.intersection(parent)` (совместимо с внешним scissor скролл-панелей в `TarkovInventoryScreen`!) и вызывая `drawIfRunning()` (сброс буфера вершин).
- `public void disableScissor()` — вызывает `scissorStack.pop()` и `drawIfRunning()`, восстанавливая родительский scissor.

**Требуемая реализация `TarkovUiTheme.drawScaledItem(DrawContext ctx, ItemStack stack, int x, int y, int w, int h, boolean rotated)`:**
1. В начале добавить guard: `if (stack == null || stack.isEmpty() || w <= 0 || h <= 0) return;`
2. Скорректировать расчёт `targetPx`:
   ```java
   int minDim = Math.min(w, h);
   int maxDim = Math.max(w, h);
   float targetPx;
   if ("tarkovcraft".equals(id.getNamespace())) {
       ItemSize itemSize = ItemSizeRegistry.getSize(stack);
       boolean rectangularItem = itemSize.width() != itemSize.height();
       if (rectangularItem && maxDim > minDim * 1.25f) {
           targetPx = Math.min(maxDim * 0.86f, minDim * 2.0f);
       } else {
           targetPx = Math.min(38.0f, minDim * 0.80f);
       }
   } else {
       targetPx = Math.min(38.0f, minDim * 0.82f);
   }
   ```
3. Обернуть отрисовку предмета в `enableScissor` / `try-finally` / `disableScissor`:
   ```java
   float scale = targetPx / 16.0f;
   ctx.enableScissor(x, y, x + w, y + h);
   try {
       ctx.getMatrices().push();
       ctx.getMatrices().translate(x + w * 0.5f, y + h * 0.5f, 0.0f);
       if (rotated) {
           ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0f));
       }
       ctx.getMatrices().scale(scale, scale, 1.0f);
       ctx.drawItem(stack, -8, -8);
       ctx.getMatrices().pop();
   } finally {
       ctx.disableScissor();
   }
   ```
4. В `TarkovUiTheme.itemCategoryTint(ItemStack)` добавить ветки:
   - `item instanceof TarkovStimulatorItem` -> `0xCC182824` (медицинский тинт)
   - `item instanceof TarkovCaseItem` -> `0xCC1E241C` (контейнерный тинт)
   - `item instanceof TarkovGrenadeItem` -> `0xCC26221A` (боевой тинт)

---

## 3. Фича 2 — 22 уникальных стимулятора и подробные тултипы (`TarkovStimulatorItem`)

### 3.1. Архитектура `StimPreset` (`java-catalog`)

Заменить 10 пресетов на **22 уникальных** `StimPreset`. Чтобы исключить риск `ExceptionInInitializerError` при обращении к `StimPreset.values()` до `Bootstrap.initialize()`, хранить эффекты через `Supplier<StatusEffect>`:

```java
public record EffectSpec(
    java.util.function.Supplier<net.minecraft.entity.effect.StatusEffect> effectSupplier,
    int durationSeconds,
    int amplifier,
    boolean negative
) {
    public net.minecraft.entity.effect.StatusEffect getEffect() {
        return effectSupplier.get();
    }
    public int getDurationTicks() {
        return durationSeconds * 20;
    }
}
```

Каждый `StimPreset` хранит:
- `int painkillerTicks` (20 тиков = 1 сек; в `BodyPartHealth` кап = `6000` тиков = 300 сек)
- `float healAmount` (мгновенное равномерное лечение HP через `health.healEvenly(healAmount)`)
- `boolean stopsAllBleeds` (снимает все `LIGHT_BLEED` и `HEAVY_BLEED`)
- `boolean clearsNegativeEffects` (снимает `POISON`, `WITHER`, `HUNGER`, `NAUSEA`, `WEAKNESS`, `SLOWNESS`, `BLINDNESS`, `DARKNESS`)
- `List<EffectSpec> effects` (применяются циклом в `applyPresetStatusEffects` И напрямую рендерятся в `appendTooltip` — 100% соответствие механики и описания!)

> **Порядок применения в `applyStimulator`:**
> 1. `clearsNegativeEffects` (СНАЧАЛА очистка старых дебаффов, чтобы детокс не снял собственный побочный эффект шприца, если таковой есть, например у Перфоторана!)
> 2. `painkillerTicks`, `stopsAllBleeds`, `healAmount`
> 3. Применение `preset.getEffects()` через `new StatusEffectInstance(spec.getEffect(), spec.getDurationTicks(), spec.amplifier())`

> **Совместимость `StimPreset.fromString(String raw)`:**
> Поиск case-insensitive по `preset.name()`. Для обратной совместимости: если `raw` равен `"COMBAT_STIM"` (или неизвестен), вернуть `MORPHINE` (или `SJ1`).

### 3.2. Таблица всех 22 стимуляторов (общий контракт для `assets-catalog` и `java-catalog`)

| # | Catalog `id` (`items_catalog.json`) | `stimPreset` | Painkiller | Heal HP | Спец. | Положительные эффекты (`negative=false`) | Побочные эффекты (`negative=true`) |
|---|---|---|---|---|---|---|---|
| 1 | `shprits_s_morfiem_544fb3` | `MORPHINE` | 300s (6000t) | 0 | — | `RESISTANCE` I (60s) | `HUNGER` I (30s) |
| 2 | `regenerativnyy_preparat_propital_5c0e53` | `PROPITAL` | 240s (4800t) | 10 | — | `REGENERATION` I (180s) | — |
| 3 | `regenerativnyy_stimulyator_etg_chang_5c0e53` | `ETG_CHANGE` | 60s (1200t) | 40 | — | `REGENERATION` III (45s), `ABSORPTION` I (45s) | `HUNGER` II (45s) |
| 4 | `boevoy_stimulyator_sj1_tglabs_5c0e53` | `SJ1` | 0 | 0 | — | `STRENGTH` I (180s), `SPEED` I (180s), `HASTE` I (180s) | `HUNGER` I (60s) |
| 5 | `boevoy_stimulyator_sj6_tglabs_5c0e53` | `SJ6` | 0 | 0 | — | `SPEED` II (240s), `JUMP_BOOST` I (240s) | — |
| 6 | `boevoy_stimulyator_sj9_tglabs_5fca13` | `SJ9` | 0 | 0 | — | `FIRE_RESISTANCE` I (300s), `NIGHT_VISION` I (180s), `INVISIBILITY` I (45s) | `WEAKNESS` I (60s) |
| 7 | `boevoy_stimulyator_sj12_tglabs_637b61` | `SJ12` | 0 | 15 | — | `SATURATION` I (30s), `NIGHT_VISION` I (240s), `RESISTANCE` I (180s) | `SLOWNESS` I (20s) |
| 8 | `stimulyator_m_u_l_e_5ed516` | `MULE` | 0 | 0 | — | `STRENGTH` II (300s), `HASTE` I (300s) | `HUNGER` I (120s) |
| 9 | `gemostaticheskoe_sredstvo_zagustin_5c0e53` | `ZAGUSTIN` | 0 | 0 | Stops bleeds | `RESISTANCE` I (120s) | `HUNGER` I (40s) |
| 10 | `antidot_xtg_12_5fca13` | `XTG12` | 0 | 10 | Detox | `REGENERATION` I (45s), `RESISTANCE` I (60s) | — |
| 11 | `shprits_s_adrenalinom_5c10c8` | `ADRENALINE` | 65s (1300t) | 12 | — | `SPEED` II (60s), `STRENGTH` I (60s), `REGENERATION` II (15s) | `HUNGER` I (30s) |
| 12 | `shprits_s_l1_noradrenalin_5ed515` | `L1` | 120s (2400t) | 0 | — | `SPEED` I (120s), `STRENGTH` I (120s), `JUMP_BOOST` I (120s) | `HUNGER` I (40s) |
| 13 | `p22_preparat_22_5ed515` | `P22` | 0 | 0 | — | `RESISTANCE` II (60s), `ABSORPTION` II (60s), `REGENERATION` I (45s) | `WEAKNESS` I (30s) |
| 14 | `preparat_3_b_tg_5ed515` | `BTG_3` | 0 | 0 | — | `NIGHT_VISION` I (240s), `STRENGTH` I (240s), `HASTE` II (240s) | `HUNGER` I (60s) |
| 15 | `preparat_2a2_b_tg_66507e` | `BTG_2A2` | 0 | 0 | Detox | `STRENGTH` I (240s), `SPEED` I (240s), `NIGHT_VISION` I (180s) | — |
| 16 | `preparat_ahf1_m_5ed515` | `AHF1_M` | 0 | 20 | Stops bleeds | `ABSORPTION` I (90s), `RESISTANCE` I (60s) | `HUNGER` I (40s) |
| 17 | `preparat_meldonin_5ed516` | `MELDONIN` | 0 | 0 | — | `RESISTANCE` I (300s), `STRENGTH` I (300s), `SPEED` I (300s) | `HUNGER` II (90s) |
| 18 | `preparat_trimadol_637b62` | `TRIMADOL` | 180s (3600t) | 0 | — | `SPEED` II (180s), `HASTE` II (180s), `JUMP_BOOST` I (180s) | `HUNGER` II (60s) |
| 19 | `stimulyator_perfotoran_sinyaya_krov_637b62` | `PERFOTORAN` | 0 | 18 | Stops bleeds + Detox | `REGENERATION` II (60s), `WATER_BREATHING` I (180s) | `WEAKNESS` I (25s) |
| 20 | `pnb_preparat_16_637b61` | `PNB` | 0 | 25 | — | `STRENGTH` II (40s), `REGENERATION` II (40s), `RESISTANCE` II (40s), `SPEED` I (40s) | `HUNGER` II (60s), `NAUSEA` I (12s) |
| 21 | `kokteyl_obdolbos_5ed516` | `OBDOLBOS` | 0 | 0 | — | `SPEED` II (180s), `STRENGTH` II (180s), `JUMP_BOOST` II (180s), `NIGHT_VISION` I (180s) | `NAUSEA` I (20s), `HUNGER` II (90s), `POISON` I (8s) |
| 22 | `kokteyl_obdolbos_2_637b60` | `OBDOLBOS_2` | 0 | 0 | — | `SPEED` II (300s), `STRENGTH` III (300s), `HASTE` II (300s), `ABSORPTION` II (180s) | `POISON` I (15s), `NAUSEA` I (25s), `HUNGER` III (120s) |

*(Уровни эффектов в таблице указаны как римские I/II/III, что соответствует `amplifier = 0 / 1 / 2` в `StatusEffectInstance`.)*

### 3.3. Формат подробного тултипа (`TarkovStimulatorItem.appendTooltip`)

**Проверенная сигнатура:** `net.minecraft.entity.effect.StatusEffect.getName()` -> `public net.minecraft.text.Text getName()`.

Порядок строк в тултипе:
1. `tooltip.tarkovcraft.stim_use` (`Formatting.AQUA`)
2. Если `painkillerTicks > 0`: `tooltip.tarkovcraft.med_painkiller` (`Formatting.GREEN`, `painkillerTicks / 20`)
3. Если `healAmount > 0`: `tooltip.tarkovcraft.stim_heal` (`Formatting.GREEN`, `Math.round(healAmount)`)
4. Если `stopsAllBleeds`: `tooltip.tarkovcraft.stim_stop_bleeds` (`Formatting.GREEN`)
5. Если `clearsNegativeEffects`: `tooltip.tarkovcraft.stim_detox` (`Formatting.GREEN`)
6. Для каждого `EffectSpec`:
   - Сформировать локализованное имя эффекта + уровень (`amplifier == 0 ? name : name + " " + roman(amplifier + 1)`): `MutableText effText = spec.getEffect().getName().copy(); if (spec.amplifier() > 0) effText.append(" " + toRoman(spec.amplifier() + 1));`
   - Если `!spec.negative()`: `Text.translatable("tooltip.tarkovcraft.stim_effect_pos", effText, spec.durationSeconds()).formatted(Formatting.BLUE)`
   - Если `spec.negative()`: `Text.translatable("tooltip.tarkovcraft.stim_effect_neg", effText, spec.durationSeconds()).formatted(Formatting.RED)`
7. `tooltip.tarkovcraft.item_size` (`Formatting.GRAY`) и `tooltip.tarkovcraft.med_weight` (`Formatting.DARK_GRAY`).

---

## 4. Фича 3 — Подпись голода/насыщения у ВСЕЙ еды (включая ванильную)

**Проверенная сигнатура (`javap` по `fabric-item-api-v1-2.1.29+1802ada577.jar`):**
- Интерфейс: `net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback`
- Событие: `public static final Event<ItemTooltipCallback> EVENT`
- Метод: `void getTooltip(ItemStack stack, TooltipContext context, List<Text> lines)`
- `net.minecraft.item.FoodComponent`: `public int getHunger()`, `public float getSaturationModifier()`

**Граница клиент/сервер:** Регистрировать **СТРОГО** в `com.tarkovcraft.client.TarkovCraftClient.onInitializeClient()` (client-only!).

**Реализация:**
```java
ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
    if (stack == null || stack.isEmpty() || !stack.isFood()) {
        return;
    }
    FoodComponent fc = stack.getItem().getFoodComponent();
    if (fc == null) {
        return;
    }
    int hunger = fc.getHunger();
    float saturation = hunger * fc.getSaturationModifier() * 2.0f;
    String satStr = Math.abs(saturation - Math.round(saturation)) < 0.05f
        ? String.valueOf(Math.round(saturation))
        : String.format(Locale.ROOT, "%.1f", saturation);
    int insertIdx = Math.min(1, lines.size());
    lines.add(insertIdx, Text.translatable("tooltip.tarkovcraft.food_stats", hunger, satStr)
        .formatted(Formatting.GREEN));
});
```
- **Отсутствие дублирования:** `TarkovFoodItem.appendTooltip` выводит только `item_size` и `med_weight` (НЕ выводит голод/насыщение), поэтому `ItemTooltipCallback` добавляет строку ровно 1 раз для ванильной еды, еды TarkovCraft и еды сторонних модов — как в ванильном инвентаре/креативе, так и в `TarkovInventoryScreen` (который вызывает `stack.getTooltip(...)`).

---

## 5. Фича 4 — Открываемые кейсы (`TarkovCaseItem`)

### 5.1. Новый класс `com.tarkovcraft.item.TarkovCaseItem`

- `public class TarkovCaseItem extends Item implements TarkovSizedItem`
- Настройки: `super(settings.maxCount(1))` (строго не стакается!)
- Поля: `gridWidth` (`>= 1`), `gridHeight` (`>= 1`), `storedItemSize`, `rarityTier`, `weightKg`.
- Геттеры: `getGridWidth()`, `getGridHeight()`, `getStoredItemSize()`, `getRarityTier()`, `getWeightKg()`.
- **`use(World world, PlayerEntity user, Hand hand)`:**
  - На клиенте: `return TypedActionResult.pass(stack);`
  - На сервере (`user instanceof ServerPlayerEntity sp`):
    - Вычислить/зафиксировать UUID: `UUID caseUuid = ContainerItemHelper.getOrCreateContainerUuid(stack);`
    - Если `sp instanceof PlayerLoadoutHolder holder && !sp.isCreative()`: `holder.tarkovcraft$syncLoadout();`
    - Вызвать `TarkovScreenHandler.openCaseScreen(sp, caseUuid);`
    - Вернуть `TypedActionResult.success(stack, false);`
- **`appendTooltip`:**
  - `tooltip.tarkovcraft.case_grid` (`Formatting.AQUA`, `gridWidth`, `gridHeight`, `gridWidth * gridHeight`)
  - `tooltip.tarkovcraft.case_open_hint` (`Formatting.YELLOW`)
  - `tooltip.tarkovcraft.item_size` (`Formatting.GRAY`)
  - `tooltip.tarkovcraft.med_weight` (`Formatting.DARK_GRAY`)

### 5.2. Обновление `ContainerItemHelper.java`

1. `isContainerItem(ItemStack stack)`:
   ```java
   return !stack.isEmpty()
       && (stack.getItem() instanceof BackpackItem
           || stack.getItem() instanceof TacticalRigItem
           || stack.getItem() instanceof TarkovCaseItem);
   ```
2. Добавить `readCaseGrid(ItemStack stack)` и `writeCaseGrid(ItemStack stack, GridContainer grid)`:
   - Хранение под теми же NBT-ключами `TarkovContainer` -> `ContainerUuid` + `Grid`, что и у рюкзаков (`readBackpackGrid`/`writeBackpackGrid`), используя `caseItem.getGridWidth()` / `caseItem.getGridHeight()`.
3. В `readAllGrids(ItemStack stack)`:
   - Добавить ветку `if (stack.getItem() instanceof TarkovCaseItem) return List.of(readCaseGrid(stack));`
   - Это автоматически включает кейсы в:
     - `ContainerItemHelper.containsContainerId(stack, targetId)` (рекурсивная защита от вложения кейса/рюкзака внутрь самого себя!),
     - `ContainerItemHelper.getNestingDepth(stack)` (ограничение глубины вложенности `MAX_NESTING_DEPTH = 2`),
     - `PlayerLoadout.estimateItemWeight(stack)` (вес содержимого кейса учитывается в общем весе игрока!).

### 5.3. Сетевой протокол (`TarkovNetworking.java` + `ClientNetworkSender.java`)

Два новых C2S-пакета (чище, чем перегрузка `C2S_USE_ITEM`, т.к. не смешивает кейсы с `isConsumableItem`):

| Пакет `Identifier` | Направление | Схема `PacketByteBuf` | Серверная валидация |
| :--- | :--- | :--- | :--- |
| `tarkovcraft:open_case` (`C2S_OPEN_CASE`) | C2S | `byte srcLoc`, `varInt srcIdx`, `boolean hasEntryId`, `[UUID srcEntryId]` | `player.currentScreenHandler instanceof TarkovScreenHandler tsh`, `server.execute(...)`, `tsh.handleOpenCase(player, srcLoc, srcIdx, srcEntryId)` |
| `tarkovcraft:close_case` (`C2S_CLOSE_CASE`) | C2S | *(пустой буфер)* | `player.currentScreenHandler instanceof TarkovScreenHandler tsh`, `server.execute(...)`, `tsh.handleCloseCase(player)` |

- Константы `C2S_OPEN_CASE`, `C2S_CLOSE_CASE` и `ServerPlayNetworking.registerGlobalReceiver(...)` — в `TarkovNetworking.java`.
- Клиентские методы `ClientNetworkSender.sendOpenCase(byte srcLoc, int srcIdx, @Nullable UUID srcEntryId)` и `ClientNetworkSender.sendCloseCase()` — строго в `com.tarkovcraft.client.ClientNetworkSender` (`@Environment(EnvType.CLIENT)`).
- Бинарная схема `S2C_SCREEN_STATE` (`ScreenStateSnapshot`) **не меняется**: открытый кейс передаётся через существующие `externalTitle` и `externalGridView`, а его слоты адресуются существующим `LOC_EXTERNAL_GRID`!

### 5.4. Механизм открытия и защиты от дюпа/потери (`TarkovScreenHandler.java`)

1. Добавить поле `@Nullable private UUID activeCaseUuid = null;` (используется при `mode == MODE_OWN_INVENTORY`).
2. Новый статический метод `openCaseScreen(ServerPlayerEntity player, UUID caseUuid)`:
   - Открывает `ExtendedScreenHandlerFactory` с `MODE_OWN_INVENTORY` (клиенту не нужен отдельный режим — `ScreenStateSnapshot.externalGridView()` сам покажет правую панель!).
   - После `openHandledScreen` на созданном `TarkovScreenHandler tsh` выставляет `tsh.activeCaseUuid = caseUuid;` и вызывает `tsh.syncToClient(player);`.
3. **Серверный `CaseHandle` (поиск кейса по `activeCaseUuid` каждый раз свежим чтением — ZERO stale references!):**
   ```java
   private interface CaseHandle {
       ItemStack getStack();
       boolean isNestedInsideContainer(); // true если кейс лежит в Rig или Backpack
       GridContainer readGrid();
       void saveGrid(GridContainer grid);
   }
   ```
   Порядок поиска в `resolveActiveCaseHandle(ServerPlayerEntity player)` по `activeCaseUuid`:
   1. `PlayerLoadout` слоты `EquipmentSlotType` (`PRIMARY_1`, `PRIMARY_2`, `PISTOL`, и др.) -> `isNested = false`, `saveGrid` пишет напрямую в `eqStack`.
   2. `PlayerLoadout` карманы `0..5` -> `isNested = false`, `saveGrid` пишет напрямую в `pocketStack`.
   3. Экипированный `RIG` (если `!requiresSearch` или `isGridRevealed`) -> сканирует все sub-grids на `TarkovCaseItem` с `activeCaseUuid` -> `isNested = true`, `saveGrid` обновляет `entry.getStack()` через `writeCaseGrid` И вызывает `ContainerItemHelper.writeRigGrids(rig, grids)`!
   4. Экипированный `BACKPACK` (если `!requiresSearch` или `isGridRevealed`) -> сканирует grid на `TarkovCaseItem` с `activeCaseUuid` -> `isNested = true`, `saveGrid` обновляет `entry.getStack()` через `writeCaseGrid` И вызывает `ContainerItemHelper.writeBackpackGrid(bp, bpGrid)`!
   5. Если `player.isCreative()` (кейс в ванильном хотбаре/руке креатива): сканирует `player.getInventory().main` (0..35) и `player.getInventory().offHand` (0) -> `isNested = false`, `saveGrid` пишет через `writeCaseGrid` + `player.getInventory().markDirty()`.
   - Если нигде не найден (кейс выбросили, переместили в ящик или уничтожили) -> сбросить `this.activeCaseUuid = null` и вернуть `null`!

4. **`handleOpenCase(ServerPlayerEntity serverPlayer, byte srcLoc, int srcIdx, @Nullable UUID srcEntryId)`:**
   - Работает только при `mode == MODE_OWN_INVENTORY` (не перетирает открытый лут-ящик/труп) и только для собственных источников (`LOC_POCKET`, `LOC_EQUIPMENT`, `LOC_RIG_GRID`, `LOC_BACKPACK_GRID`).
   - **Критически важно:** если у кейса ещё не было `ContainerUuid` и он лежит в `LOC_RIG_GRID` или `LOC_BACKPACK_GRID`, нужно создать UUID на `entry.getStack()` И **записать обратно** `writeRigGrids` / `writeBackpackGrid`, иначе UUID потеряется при следующем чтении!
   - Установить `this.activeCaseUuid = caseUuid;` и вызвать `syncToClient(serverPlayer)`.
5. **`handleCloseCase(ServerPlayerEntity serverPlayer)`:**
   - Установить `this.activeCaseUuid = null;` и вызвать `syncToClient(serverPlayer)`.
6. **Интеграция `LOC_EXTERNAL_GRID` при `mode == MODE_OWN_INVENTORY && activeCaseUuid != null` во ВСЕ операции `TarkovScreenHandler`:**
   - `syncToClient`: если `mode == MODE_OWN_INVENTORY && activeCaseUuid != null`, разрешить `CaseHandle h = resolveActiveCaseHandle(serverPlayer)`: если `h != null`, заполнить `extTitle = h.getStack().getName().getString()` и `extView = TarkovNetworking.GridView.fromContainer(h.readGrid(), activeCaseUuid, null, playerId, false)` (свой кейс всегда сразу открыт без поиска).
   - `peekSource` (`LOC_EXTERNAL_GRID`): читать `h.readGrid().getEntry(entryId)`.
   - `extractSource` (`LOC_EXTERNAL_GRID`): `readGrid()`, `split(count)` / `remove(entryId)`, `h.saveGrid(grid)`.
   - `repositionInSameGrid` (`LOC_EXTERNAL_GRID`): `readGrid()`, `canPlace(..., entryId)`, `remove` + `placeWithId`, `h.saveGrid(grid)`.
   - `canInsertIntoDestination` (`LOC_EXTERNAL_GRID`):
     - **Анти-рекурсия и лимит глубины:**
       - Отклонить, если `ContainerItemHelper.containsContainerId(stack, activeCaseUuid)` (запрещает класть кейс в самого себя или рюкзак, содержащий этот кейс, внутрь кейса!).
       - Отклонить, если `h.isNestedInsideContainer() && ContainerItemHelper.isContainerItem(stack)` (гарантирует, что кейс внутри рюкзака/разгрузки не может принять внутрь себя другой контейнер — глубина строго `<= MAX_NESTING_DEPTH = 2`!).
     - Проверить `(at != null && canStackInto(at.getStack(), stack)) || g.canPlace(stack, dstX, dstY, dstRotated)`.
   - `insertIntoDestination` (`LOC_EXTERNAL_GRID`): `readGrid()`, `place(...)`, `h.saveGrid(grid)`.
   - `handleQuickTransfer`:
     - Из `LOC_EXTERNAL_GRID` -> уже переносит в own storage (`Pockets -> Rig -> Backpack`) и вызывает `extractSource`!
     - Из own storage (`LOC_POCKET`, `LOC_RIG_GRID`, `LOC_BACKPACK_GRID`): если `mode == MODE_OWN_INVENTORY && activeCaseUuid != null`, разрешить `h`: если предмет НЕ нарушает анти-рекурсию/глубину (`!ContainerItemHelper.containsContainerId(peek, activeCaseUuid)` и `!(h.isNestedInsideContainer() && ContainerItemHelper.isContainerItem(peek))`), попытаться `g.tryAutoPlace(copy)` -> `h.saveGrid(g)` -> `extractSource(...)` -> `syncToClient`!
   - `placeSplitIntoSameOrOwnFreeCell` (`LOC_EXTERNAL_GRID`) и `mutateMedicalInContainer` (`LOC_EXTERNAL_GRID`): также поддержать `CaseHandle` (читать/сохранять через `h.saveGrid(grid)`).
   - `MODE_GROUND_ITEM`: поддержать `groundItem.getStack().getItem() instanceof TarkovCaseItem` по аналогии с `BackpackItem` (чтение/запись `readCaseGrid`/`writeCaseGrid`), а также в `TarkovNetworking.handleInteract` и `WorldInteractState.updateTarget` добавить `|| stack.getItem() instanceof TarkovCaseItem`.

### 5.5. Изменения в `TarkovInventoryScreen.java`

1. Строка 243:
   ```java
   boolean hasExternal = handler.getMode() != TarkovScreenHandler.MODE_OWN_INVENTORY
       || (snap != null && snap.externalGridView() != null);
   ```
2. В `renderExternalColumn`:
   - Динамический размер ячейки для больших кейсов (до `10x10`):
     ```java
     int maxCols = snap.externalGridView() != null ? Math.max(6, snap.externalGridView().width()) : 6;
     int extCell = Math.max(12, Math.min(cellSize, (area.w - pad * 2 - 8) / maxCols));
     ```
   - Если `handler.getMode() == TarkovScreenHandler.MODE_OWN_INVENTORY && snap.externalGridView() != null`: в правом углу заголовка правой панели отрисовать кнопку `[X]` (`13x11`), по клику вызывающую `ClientNetworkSender.sendCloseCase()` (сдвинув `btnW` при обрезке `trimmedHeader`).
3. В `openContextMenu`:
   - Если `stack.getItem() instanceof TarkovCaseItem` и `handler.getMode() == TarkovScreenHandler.MODE_OWN_INVENTORY` и источник — собственный инвентарь (`LOC_POCKET`, `LOC_EQUIPMENT`, `LOC_RIG_GRID`, `LOC_BACKPACK_GRID`):
     - Добавить пункт `Text.translatable("ctx.tarkovcraft.open").getString()` -> `ClientNetworkSender.sendOpenCase(target.loc(), target.idx(), target.entryId())`.
4. В `evaluateDrop` (строка 1488):
   - Заменить `UUID draggedContainerId = ContainerItemHelper.getContainerUuid(moving); if (draggedContainerId != null && draggedContainerId.equals(region.view.containerId()))` на:
     ```java
     if (ContainerItemHelper.containsContainerId(moving, region.view.containerId())) {
         return new DropEvaluation(false, 0, 0, 1, 1);
     }
     ```

### 5.6. Таблица всех 33 кейсов для `assets-catalog` (`items_catalog.json`)

Для всех 33 записей с `"category": "case"` добавить поля `"gridWidth"` и `"gridHeight"`, а также уточнить внешний размер `"width"`/`"height"` (с учётом реальных пропорций иконок и Таркова):

| # | Catalog `id` | `width` x `height` | `gridWidth` x `gridHeight` |
|---|---|---|---|
| 1 | `bumazhnik_wz_60b0f6` | `1x1` | `2x2` |
| 2 | `neprostoy_bumazhnik_loui_peeton_6937ec` | `1x1` | `2x2` |
| 3 | `klyuchnitsa_59fafd` | `1x1` | `4x4` |
| 4 | `klyuchnitsa_gingy_62a09d` | `1x1` | `2x2` |
| 5 | `keys_dlya_klyuchey_67d3ed` | `1x1` | `4x4` |
| 6 | `keys_derzhatel_dlya_klyuch_kart_619cbf` | `1x1` | `3x3` |
| 7 | `keys_dlya_zhetonov_5c093e` | `1x1` | `6x6` |
| 8 | `keys_dlya_redkikh_zhetonov_twitch_6937ec` | `1x1` | `6x6` |
| 9 | `planshet_dlya_dokumentov_590c60` | `1x2` | `4x4` |
| 10 | `planshet_dlya_dokumentov_6a9978` | `1x2` | `4x4` |
| 11 | `keys_s_i_c_c_5d235b` | `2x1` | `5x5` |
| 12 | `keys_dlya_inektorov_619cbf` | `1x1` | `4x4` |
| 13 | `keys_dlya_deneg_59fb01` | `2x2` | `7x7` |
| 14 | `pistoletnyy_keys_567143` | `2x2` | `4x3` |
| 15 | `banka_pod_bolty_693bfb` | `1x2` | `4x4` |
| 16 | `kopilka_sheefgg_69f9d3` | `1x2` | `4x4` |
| 17 | `tubus_dlya_plakatov_697883` | `1x2` | `3x6` |
| 18 | `termosumka_dlya_napitkov_hot_rod_6937ec` | `2x2` | `5x5` |
| 19 | `yashchik_s_instrumentami_discord_ver_688b2d` | `2x2` | `6x6` |
| 20 | `keys_dlya_strimerskikh_predmetov_66bc98` | `2x2` | `6x6` |
| 21 | `keys_dlya_medikamentov_5aafbc` | `3x3` | `7x7` |
| 22 | `keys_dlya_medikamentov_6a997c` | `3x3` | `7x7` |
| 23 | `keys_dlya_magazinov_5c127c` | `3x2` | `7x7` |
| 24 | `keys_dlya_granat_5e2af5` | `3x3` | `7x7` |
| 25 | `keys_dlya_broneplit_676009` | `3x3` | `7x7` |
| 26 | `termosumka_mr_holodilnick_5c093d` | `3x3` | `8x8` |
| 27 | `keys_dlya_khlama_5b7c71` | `4x4` | `10x10` |
| 28 | `oruzheynyy_keys_59fb02` | `4x2` | `8x5` |
| 29 | `oruzheynyy_keys_6a997c` | `4x2` | `8x5` |
| 30 | `keys_dlya_veshchey_59fb04` | `3x3` | `8x8` |
| 31 | `keys_dlya_veshchey_6a997c` | `3x3` | `8x8` |
| 32 | `oruzheynyy_keys_t_h_i_c_c_5b6d9c` | `4x2` | `10x8` |
| 33 | `keys_dlya_veshchey_t_h_i_c_c_5c0a84` | `4x3` | `10x10` |

---

## 6. Фича 5 — Функциональные бросаемые гранаты (`TarkovGrenadeItem` + `TarkovGrenadeEntity`)

### 6.1. Проверенные Yarn 1.20.1 / Fabric API сигнатуры

- `net.minecraft.entity.projectile.thrown.ThrownItemEntity extends ThrownEntity implements FlyingItemEntity`:
  - `public ThrownItemEntity(EntityType<? extends ThrownItemEntity> type, World world)`
  - `public ThrownItemEntity(EntityType<? extends ThrownItemEntity> type, LivingEntity owner, World world)`
  - `public void setItem(ItemStack item)`
  - `protected abstract Item getDefaultItem()`
  - `public ItemStack getStack()`
  - `protected float getGravity()` (унаследовано от `ThrownEntity`)
  - `public void tick()`
  - `protected void onBlockHit(BlockHitResult blockHitResult)` (унаследовано от `ProjectileEntity`)
  - `protected void onEntityHit(EntityHitResult entityHitResult)` (унаследовано от `ProjectileEntity`)
  - `public void setVelocity(Entity shooter, float pitch, float yaw, float roll, float speed, float divergence)`
- `net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder`:
  - `FabricEntityTypeBuilder.<TarkovGrenadeEntity>create(SpawnGroup.MISC, TarkovGrenadeEntity::new).dimensions(EntityDimensions.fixed(0.25f, 0.25f)).trackRangeBlocks(64).trackedUpdateRate(4).build()`
- `net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry` **(client-only!)**:
  - `EntityRendererRegistry.register(ModEntities.GRENADE, FlyingItemEntityRenderer::new)`
- `net.minecraft.client.render.entity.FlyingItemEntityRenderer<T extends Entity & FlyingItemEntity>`:
  - `public FlyingItemEntityRenderer(EntityRendererFactory.Context ctx)`
- Частицы и урон:
  - `ServerWorld.spawnParticles(ParticleEffect, double x, double y, double z, int count, double dx, double dy, double dz, double speed)`
  - `new DustParticleEffect(new org.joml.Vector3f(r, g, b), float scale)`
  - `ParticleTypes.EXPLOSION_EMITTER`, `ParticleTypes.EXPLOSION`, `ParticleTypes.FLASH`, `ParticleTypes.CAMPFIRE_SIGNAL_SMOKE`, `ParticleTypes.CAMPFIRE_COSY_SMOKE`, `ParticleTypes.SMOKE`, `ParticleTypes.SNEEZE`, `ParticleTypes.LAVA`
  - `World.ExplosionSourceType.NONE` (блоки НЕ разрушаются!)
  - `getDamageSources().explosion(this, getOwner())` и `getDamageSources().thrown(this, getOwner())`

### 6.2. Enum `TarkovGrenadeItem.GrenadeType` и таблица 9 гранат (`assets-catalog` + `java-catalog`)

| `grenadeType` | Catalog `id` в `items_catalog.json` | Запал (тики / сек) | Ударная? (`armingTicks`) | Радиус | Макс. урон (Tarkov HP) | Пробитие | Шанс кровотечения | Облако (тики / сек) |
|---|---|---|---|---|---|---|---|---|
| `FRAG_F1` | `ruchnaya_granata_f_1_s_sokrashchenny_67b49e` | `40` (2.0s) | нет | `7.5f` | `135.0f` | `25` | `0.65f` | `0` |
| `FRAG_V40` | `ruchnaya_granata_v40_mini_grenade_66dae7` | `56` (2.8s) | нет | `4.5f` | `75.0f` | `15` | `0.40f` | `0` |
| `FRAG_RGN` | `ruchnaya_granata_rgn_617fd9` | `70` (3.5s) | **да** (`6` = 0.3s) | `5.5f` | `95.0f` | `20` | `0.50f` | `0` |
| `FRAG_RGO` | `ruchnaya_granata_rgo_618a43` | `70` (3.5s) | **да** (`6` = 0.3s) | `7.0f` | `125.0f` | `28` | `0.65f` | `0` |
| `FLASH_M7290` | `svetozvukovaya_granata_model_7290_619256` | `40` (2.0s) | нет | `11.0f` | `0.0f` | `0` | `0.0f` | `0` |
| `SMOKE_M18` | `dymovaya_granata_m18_zelenyy_617aa4` | `30` (1.5s) | нет | `5.5f` | `0.0f` | `0` | `0.0f` | `300` (15.0s) |
| `GAS_CS` | `gazovaya_granata_cs_model_8230_69f078`, `gazovaya_granata_cs_model_8230_rde_6_6a9854`, `gazovaya_granata_cs_model_8230_rde_7_6a9854` | `35` (1.75s) | нет | `5.5f` | `0.0f` | `0` | `0.0f` | `240` (12.0s) |

### 6.3. Логика `TarkovGrenadeItem` и `TarkovGrenadeEntity`

- **`TarkovGrenadeItem extends Item implements TarkovSizedItem`:**
  - `maxCount(4)`, хранит `GrenadeType`, `ItemSize`, `ItemRarityTier`, `weightKg`.
  - `use(World world, PlayerEntity user, Hand hand)`:
    - Кулдаун `20` тиков (`user.getItemCooldownManager().set(this, 20)`).
    - Звук броска: `SoundEvents.ENTITY_SNOWBALL_THROW` (vol `0.6f`, pitch `0.75f`).
    - На сервере: создаёт `TarkovGrenadeEntity(world, user, stack, grenadeType)`, задаёт `setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, 1.35f, 1.0f)`, спавнит `world.spawnEntity(grenade)`.
    - Если `!user.isCreative()`: `stack.decrement(1)`; если `user instanceof PlayerLoadoutHolder holder`, вызвать `holder.tarkovcraft$syncLoadout()`.
    - Возвращает `TypedActionResult.success(stack, world.isClient())`.
  - `appendTooltip`: тип гранаты, запал (`tooltip.tarkovcraft.grenade_fuse` / `tooltip.tarkovcraft.grenade_impact`), радиус (`tooltip.tarkovcraft.grenade_radius`), длительность облака (если `cloudTicks > 0`), размер, вес.

- **`TarkovGrenadeEntity extends ThrownItemEntity`:**
  - `getDefaultItem()` -> `Items.SNOWBALL` (безопасный ненулевой фоллбэк; реальная иконка всегда задаётся через `setItem(stack.copyWithCount(1))`).
  - `getGravity()` -> `0.045f`.
  - **Отскоки и остановка:**
    - В `onBlockHit(BlockHitResult hit)`:
      - Если `emitting` -> `setVelocity(Vec3d.ZERO)`, return.
      - Если `!world.isClient && grenadeType.isImpactFuse() && age >= grenadeType.getArmingTicks()` -> `detonate()`, return.
      - Иначе отразить компоненту скорости по `hit.getSide().getAxis()` (`-0.42` по Y, `-0.55` по X/Z) с трением `0.72` по касательным осям. Если `hit.getSide() == Direction.UP && vel.lengthSquared() < 0.015`: установить `resting = true; setNoGravity(true); setVelocity(Vec3d.ZERO);`.
      - Проиграть `SoundEvents.BLOCK_METAL_HIT` (vol `0.45f`, pitch `1.25f`).
    - В `onEntityHit(EntityHitResult hit)`:
      - Если `emitting` -> return.
      - На сервере: если `grenadeType.isImpactFuse() && age >= grenadeType.getArmingTicks()` -> `detonate()`, return.
      - Иначе нанести `1.0f` урона (`thrown`), отскочить назад (`vel.multiply(-0.35, 0.35, -0.35)`), звук `BLOCK_METAL_HIT`.
    - В `tick()`:
      - Если `resting && getWorld().getBlockState(getBlockPos().down()).isAir()` -> снять `resting = false; setNoGravity(false);`.
      - Если `resting || emitting` -> `setVelocity(Vec3d.ZERO);`.
      - Вызвать `super.tick();`.
      - На клиенте: если `!detonated && age % 2 == 0` -> спавн `ParticleTypes.SMOKE` над гранатой.
      - На сервере:
        - Если `!detonated`: `fuseRemaining--`; если `fuseRemaining <= 0` -> `detonate()`.
        - Если `emitting`: `cloudRemaining--`; вызов `tickCloud((ServerWorld) world)`; если `cloudRemaining <= 0` -> `discard()`.
  - **Детонация (`detonate()`):**
    - `detonated = true;`
    - **Осколочные (`FRAG_F1`, `FRAG_V40`, `FRAG_RGN`, `FRAG_RGO`):**
      - **Блоки не ломаем:** звук `ENTITY_GENERIC_EXPLOSION` (vol `3.0f`, pitch `0.9f`) + `ServerWorld.spawnParticles` (`EXPLOSION_EMITTER`, `EXPLOSION`, `CAMPFIRE_COSY_SMOKE`, `LAVA`). (Можно дополнительно `world.createExplosion(this, getX(), getY(), getZ(), 0.0f, World.ExplosionSourceType.NONE)` только ради ванильной взрывной волны или обойтись прямыми звуком+частицами — прямые звук+частицы надёжнее и не создают двойного звука!)
      - Всем `LivingEntity` в радиусе `blastRadius`:
        - Проверка укрытия (line-of-sight `RaycastContext.ShapeType.COLLIDER` от центра взрыва к ногам+0.5 и к глазам цели): обе точки закрыты -> `exposure = 0.0f` (укрытие спасает!), одна -> `0.5f`, обе открыты -> `1.0f`.
        - `falloff = Math.max(0.0f, 1.0f - (float)(dist / radius))`, `effectiveDmg = maxDamage * (0.25f + 0.75f * falloff * falloff) * exposure`.
        - Если `target instanceof ServerPlayerEntity sp && !sp.isCreative() && !sp.isSpectator() && sp instanceof PlayerLoadoutHolder holder`:
          - Распределить `effectiveDmg` на 3 случайных зоны из `{THORAX, STOMACH, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, HEAD_JAWS}` через `BodyPartHealth.applyHit(...)` (учитывает зональную броню TarkovCraft!).
          - При `random.nextFloat() < bleedChance * falloff * exposure`: наложить `HEAVY_BLEED` (при `falloff > 0.6f`) или `LIGHT_BLEED`.
          - Синхронизировать `holder.tarkovcraft$syncBodyHealth()`, `holder.tarkovcraft$syncLoadout()`, обновить vanilla HP или вызвать `sp.setHealth(0.0f); sp.onDeath(getDamageSources().explosion(this, getOwner()));`.
        - Если моб (`!(target instanceof PlayerEntity)`): нанести `target.damage(getDamageSources().explosion(this, getOwner()), effectiveDmg / 4.5f)`.
        - Добавить отбрасывание от эпицентра.
      - `discard()`.
    - **Светошумовая (`FLASH_M7290`):**
      - Звук `ENTITY_FIREWORK_ROCKET_LARGE_BLAST` (vol `3.0f`, pitch `1.4f`) + `ENTITY_GENERIC_EXPLOSION` (vol `1.2f`, pitch `1.8f`).
      - Частицы `ParticleTypes.FLASH` (4 шт.) + `ParticleTypes.EXPLOSION` (3 шт.).
      - Всем `LivingEntity` в радиусе `11.0f` с прямой видимостью (`exposure > 0`):
        - Проверить угол взгляда игрока к вспышке (`dot = lookVec.dotProduct(toFlash.normalize())`): смотрит на вспышку (`dot > 0.25`) -> `1.0x`, отвернулся -> `0.5x`.
        - Наложить `BLINDNESS` (`Math.max(40, (int)(220 * falloff * facingMult))` тиков), `DARKNESS` (80 тиков), `NAUSEA` (160 тиков), `SLOWNESS` II (100 тиков).
      - `discard()`.
    - **Дымовая (`SMOKE_M18`):**
      - `emitting = true; resting = true; setNoGravity(true); setVelocity(Vec3d.ZERO); cloudRemaining = grenadeType.getCloudTicks();`
      - Звук `BLOCK_FIRE_EXTINGUISH` (vol `1.2f`, pitch `0.8f`).
      - В `tickCloud`: каждые 2 тика спавнить плотное облако (`CAMPFIRE_SIGNAL_SMOKE` 8 шт. + зелёный `DustParticleEffect(new Vector3f(0.22f, 0.72f, 0.28f), 2.8f)` 14 шт. в радиусе `blastRadius * 0.65`), каждые 30 тиков — тихое шипение `BLOCK_FIRE_EXTINGUISH`.
    - **Газовая (`GAS_CS`):**
      - `emitting = true; resting = true; setNoGravity(true); setVelocity(Vec3d.ZERO); cloudRemaining = grenadeType.getCloudTicks();`
      - Звук `BLOCK_FIRE_EXTINGUISH` (vol `1.2f`, pitch `1.0f`).
      - В `tickCloud`: каждые 2 тика спавнить жёлто-зелёный газ (`DustParticleEffect(new Vector3f(0.68f, 0.78f, 0.22f), 2.4f)` 12 шт. + `CAMPFIRE_COSY_SMOKE` 5 шт. + `SNEEZE` 4 шт.).
      - Каждые 10 тиков всем `LivingEntity` в радиусе `blastRadius`:
        - Если `!hasGasMaskProtection(entity)`: наложить `NAUSEA` (100t, amp 0), `BLINDNESS` (60t, amp 0), `POISON` (60t, amp 0), `SLOWNESS` (60t, amp 0).
  - **Публичный статический хелпер `hasGasMaskProtection(LivingEntity entity)` (удобно покрывать JUnit-тестами!):**
    - Возвращает `true`, если `entity instanceof PlayerLoadoutHolder holder` и в `EquipmentSlotType.FACE_MASK` надета маска, чей `Registries.ITEM.getId(item).getPath()` содержит `"protivogaz"` или `"respirator"` или `"gasmask"` (покрывает все 6 противогазов и респираторов в каталоге: `protivogaz_avon_m53a1_689b88`, `protivogaz_msa_millenium_riot_contro_6a8316`, `protivogaz_gp_5_5b432c`, `protivogaz_gp_7_60363c`, `respirator_gentex_ops_core_sotr_689b40`, `respiratornaya_maska_59e771`).
  - **NBT:** сохранять/читать `GrenadeType`, `FuseRemaining`, `CloudRemaining`, `Detonated`, `Emitting`, `Resting`.

---

## 7. Фича 6 — 6 раздельных вкладок креатива без дублей (`ModItems` + `TarkovItemCatalog` + `lang`)

Удалить старые `TARKOV_GROUP` (`tarkov_tab`), `TARKOV_GEAR_GROUP` (`tarkov_gear_tab`), `TARKOV_MEDS_FOOD_GROUP` (`tarkov_meds_food_tab`). Зарегистрировать **ровно 6** вкладок, каждая базовая вещь/блок и каждая категория каталога попадает ровно в **одну** вкладку:

| # | `Identifier` | Константа `ModItems` | Иконка | Базовые предметы (`ModItems`/`ModBlocks`) | Категории `TarkovItemCatalog` (геттер) | EN / RU название |
|---|---|---|---|---|---|---|
| 1 | `tarkovcraft:tarkov_armor_tab` | `TARKOV_ARMOR_GROUP` | `ALTYN_HELMET` | `SSH68_HELMET`, `RATNIK_6B47_HELMET`, `ALTYN_HELMET`, `BALACLAVA_MASK`, `DEATH_SHADOW_MASK`, `PACA_ARMOR`, `PRESS_ARMOR`, `ZABRALO_ARMOR` | `helmet`, `body_armor`, `face_mask`, `armor_plate`, `repair_kit` (`getArmorItems()`) | `TarkovCraft: Armor & Helmets` / `TarkovCraft: Броня и Шлемы` |
| 2 | `tarkovcraft:tarkov_rigs_tab` | `TARKOV_RIGS_GROUP` | `TRITON_RIG` | `SCAV_VEST`, `BANK_ROBBER_RIG`, `TRITON_RIG` | `rig` (`getRigItems()`) | `TarkovCraft: Tactical Rigs` / `TarkovCraft: Разгрузки` |
| 3 | `tarkovcraft:tarkov_backpacks_tab` | `TARKOV_BACKPACKS_GROUP` | `BERKUT_BACKPACK` | `SLING_BACKPACK`, `BERKUT_BACKPACK`, `TRIZIP_BACKPACK` | `backpack` (`getBackpackItems()`) | `TarkovCraft: Backpacks` / `TarkovCraft: Рюкзаки` |
| 4 | `tarkovcraft:tarkov_meds_tab` | `TARKOV_MEDS_GROUP` | `SALEWA_MEDKIT` | `BANDAGE`, `CAT_TOURNIQUET`, `SPLINT`, `ALU_SPLINT`, `ANALGIN`, `AI2_MEDKIT`, `SALEWA_MEDKIT`, `CMS_KIT`, `SURV12_KIT` | `medical`, `stimulator` (`getMedicalItems()`) | `TarkovCraft: Meds & Stimulators` / `TarkovCraft: Медицина и Стимуляторы` |
| 5 | `tarkovcraft:tarkov_food_tab` | `TARKOV_FOOD_GROUP` | `getFoodItems().get(0)` (fallback `Items.APPLE`) | — | `food` (`getFoodItems()`) | `TarkovCraft: Food & Drinks` / `TarkovCraft: Еда и Вода` |
| 6 | `tarkovcraft:tarkov_tactical_tab` | `TARKOV_TACTICAL_GROUP` | `ModBlocks.WEAPON_CRATE` | `ModBlocks.WEAPON_CRATE`, `ModBlocks.MEDBAG`, `ModBlocks.TECHNICAL_CRATE` | `case`, `grenade`, `melee`, `headset` (`getTacticalItems()`) | `TarkovCraft: Cases, Grenades & Gear` / `TarkovCraft: Кейсы, Гранаты и Тактика` |

**Изменения в `TarkovItemCatalog.createItemForCategory`:**
- `case "case"` -> читать `gridWidth` (default `4`) и `gridHeight` (default `4`), возвращать `new TarkovCaseItem(settings, gridW, gridH, size, rarity, weight)`.
- `case "grenade"` -> читать `grenadeType` (default `"FRAG_F1"`), парсить `TarkovGrenadeItem.GrenadeType.fromString(...)`, возвращать `new TarkovGrenadeItem(settings, gType, size, rarity, weight)`.
- `case "headset"` -> остаётся `new TarkovFunctionalItem(settings, category, size, rarity, weight)`.

---

## 8. Ключи локализации (`assets-catalog` — `en_us.json` и `ru_ru.json`)

| Ключ | `en_us.json` | `ru_ru.json` |
|---|---|---|
| `itemGroup.tarkovcraft.tarkov_armor_tab` | `TarkovCraft: Armor & Helmets` | `TarkovCraft: Броня и Шлемы` |
| `itemGroup.tarkovcraft.tarkov_rigs_tab` | `TarkovCraft: Tactical Rigs` | `TarkovCraft: Разгрузки` |
| `itemGroup.tarkovcraft.tarkov_backpacks_tab` | `TarkovCraft: Backpacks` | `TarkovCraft: Рюкзаки` |
| `itemGroup.tarkovcraft.tarkov_meds_tab` | `TarkovCraft: Meds & Stimulators` | `TarkovCraft: Медицина и Стимуляторы` |
| `itemGroup.tarkovcraft.tarkov_food_tab` | `TarkovCraft: Food & Drinks` | `TarkovCraft: Еда и Вода` |
| `itemGroup.tarkovcraft.tarkov_tactical_tab` | `TarkovCraft: Cases, Grenades & Gear` | `TarkovCraft: Кейсы, Гранаты и Тактика` |
| `tooltip.tarkovcraft.food_stats` | `Hunger: +%s  \|  Saturation: +%s` | `Голод: +%s  \|  Насыщение: +%s` |
| `tooltip.tarkovcraft.stim_heal` | `+%s HP (all limbs)` | `+%s ОЗ (все части тела)` |
| `tooltip.tarkovcraft.stim_stop_bleeds` | `Stops all bleeds` | `Останавливает все кровотечения` |
| `tooltip.tarkovcraft.stim_detox` | `Clears poison & debuffs` | `Снимает отравление и дебаффы` |
| `tooltip.tarkovcraft.stim_effect_pos` | `+ %s (%ss)` | `+ %s (%s сек)` |
| `tooltip.tarkovcraft.stim_effect_neg` | `- %s (%ss)` | `- %s (%s сек)` |
| `tooltip.tarkovcraft.case_grid` | `Storage: %sx%s (%s cells)` | `Вместимость: %sx%s (%s ячеек)` |
| `tooltip.tarkovcraft.case_open_hint` | `Right-click or RMB -> Open` | `ПКМ в руке или ПКМ -> Открыть` |
| `tooltip.tarkovcraft.grenade_type_frag` | `Fragmentation Grenade` | `Осколочная граната` |
| `tooltip.tarkovcraft.grenade_type_impact` | `Impact Fragmentation Grenade` | `Ударная осколочная граната` |
| `tooltip.tarkovcraft.grenade_type_flash` | `Stun / Flashbang Grenade` | `Светозвуковая граната` |
| `tooltip.tarkovcraft.grenade_type_smoke` | `Smoke Grenade` | `Дымовая граната` |
| `tooltip.tarkovcraft.grenade_type_gas` | `CS Tear Gas Grenade` | `Газовая граната (CS)` |
| `tooltip.tarkovcraft.grenade_fuse` | `Fuse: %ss` | `Запал: %s сек` |
| `tooltip.tarkovcraft.grenade_impact_fuse` | `Impact (0.3s arm) / %ss max` | `Ударная (взвод 0.3с) / макс. %s сек` |
| `tooltip.tarkovcraft.grenade_radius` | `Radius: %sm` | `Радиус: %s м` |
| `tooltip.tarkovcraft.grenade_cloud` | `Cloud duration: %ss` | `Время действия: %s сек` |
| `entity.tarkovcraft.grenade` | `Grenade` | `Граната` |

*(Старые ключи `tarkov_tab`, `tarkov_gear_tab`, `tarkov_meds_food_tab` можно удалить или оставить неиспользуемыми — лучше удалить для чистоты.)*

---

## 9. Тест-кейсы (JUnit 5) и критерии приёмки

Обновить `TarkovItemCatalogTest.java` и/или добавить `FunctionalItemsV2Test.java`:
1. **Каталог и 6 вкладок:**
   - Все 6 бакетов `TarkovItemCatalog` (`getArmorItems`, `getRigItems`, `getBackpackItems`, `getMedicalItems`, `getFoodItems`, `getTacticalItems`) непустые, не содержат пересечений (дублей) и в сумме покрывают все `678` динамических предметов.
   - Ни одна балаклава (`balaklava_*`) не имеет размер `2x1` (все `1x1`); баллистические маски `armorClass >= 2` имеют `2x2`; очки `ochki` имеют `2x1`.
2. **22 уникальных стимулятора:**
   - Все 22 `stimulator` из `items_catalog.json` имеют **22 различных** `StimPreset` (множество использованных пресетов размером `22`).
   - У каждого `StimPreset` заполнены эффекты и/или спецдействия, `appendTooltip` не кидает исключений и выдаёт `>= 4` строки.
3. **33 открываемых кейса (`TarkovCaseItem`):**
   - Все 33 `case` из каталога зарегистрированы как `TarkovCaseItem` с `gridWidth >= 2`, `gridHeight >= 2`.
   - `ContainerItemHelper.readCaseGrid` / `writeCaseGrid` сохраняют предметы в NBT round-trip.
   - Анти-рекурсия: `caseGrid.canPlace(caseStack, 0, 0, false)` возвращает `false`; `ContainerItemHelper.containsContainerId(backpackContainingCase, caseUuid)` возвращает `true`.
   - Расчёт веса `PlayerLoadout.estimateItemWeight(caseStack)` включает вес предметов внутри кейса.
4. **9 гранат (`TarkovGrenadeItem` + `TarkovGrenadeEntity`):**
   - Все 9 `grenade` из каталога зарегистрированы как `TarkovGrenadeItem` с валидным `GrenadeType` (включая все 7 типов: `FRAG_F1`, `FRAG_V40`, `FRAG_RGN`, `FRAG_RGO`, `FLASH_M7290`, `SMOKE_M18`, `GAS_CS`).
   - `ModEntities.GRENADE` зарегистрирован в `Registries.ENTITY_TYPE`.
   - Проверка логики защиты от CS-газа (`protivogaz_*` и `respirator_*` дают защиту, обычная `balaklava_*` и пустой слот — нет).

### Команда верификации (JDK 17 обязателен!)

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.9.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build --no-daemon
```

**Критерии приёмки:**
- `compileJava`, `test` и `remapJar` (`build`) проходят с кодом `0` без предупреждений о missing resources/broken enums.
- Строгая граница клиент/сервер (`ItemTooltipCallback` и `FlyingItemEntityRenderer` только в `com.tarkovcraft.client.*`, `ClientNetworkSender` только в `client`).
- Ровно 6 вкладок креатива без дубликатов.
