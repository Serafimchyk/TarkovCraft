# Спецификация: Критичный аудит (P0/P1 фиксы) + Вкладка патронов в креативе (`tarkov_ammo_tab`)

**Статус:** Утверждённая архитектурная спецификация
**Стек:** Minecraft `1.20.1`, Fabric Loader `0.16.10`, Fabric API `0.92.12+1.20.1`, Yarn `1.20.1+build.10`, Java `17`, `environment: "*"`.  
**Gradle / `fabric.mod.json` / `tarkovcraft.mixins.json`:** Изменения **не требуются** (0 новых зависимостей, 0 новых миксинов; правятся только существующие `PlayerEntityMixin` и `PlayerInventoryMixin`). **TactEq не трогать.**

---

## 1. Разделение владения файлами (строго без пересечений)

| Воркер | Права владения файлами |
| :--- | :--- |
| **`assets-catalog`** | ТОЛЬКО `src/main/resources/data/tarkovcraft/catalog/items_catalog.json`, `src/main/resources/assets/tarkovcraft/lang/en_us.json`, `src/main/resources/assets/tarkovcraft/lang/ru_ru.json` |
| **`java-catalog`** | ТОЛЬКО `src/main/java/**` и `src/test/java/**` |

### Список файлов

**Владение `assets-catalog`:**
1. `src/main/resources/data/tarkovcraft/catalog/items_catalog.json` — добавить семантические метаданные (убирают транслит из Java-логики):
   - `"gasProtection": true` для 6 противогазов/респираторов (`protivogaz_avon_m53a1_689b88`, `protivogaz_msa_millenium_riot_contro_6a8316`, `protivogaz_gp_5_5b432c`, `protivogaz_gp_7_60363c`, `respirator_gentex_ops_core_sotr_689b40`, `respiratornaya_maska_59e771`).
   - `"caseRole": "MEDICAL"` для 3 медкейсов/инъектор-кейсов (`keys_dlya_medikamentov_5aafbc`, `keys_dlya_medikamentov_6a997c`, `keys_dlya_inektorov_619cbf`).
2. `src/main/resources/assets/tarkovcraft/lang/en_us.json` и `ru_ru.json` — добавить ключ `itemGroup.tarkovcraft.tarkov_ammo_tab`.

**Владение `java-catalog`:**
1. `src/main/java/com/tarkovcraft/mixin/PlayerEntityMixin.java` — (P0 #1) безопасный перенос ВСЕГО инвентаря (loadout + offHand + vanilla main) в `CorpseEntity` с дропом излишков + поддержка `GameRules.KEEP_INVENTORY` + синхронизация loadout; (P0 #5) учёт `StatusEffects.RESISTANCE` и `self.getAbsorptionAmount()` в `applyDamage`.
2. `src/main/java/com/tarkovcraft/entity/CorpseEntity.java` — (P0 #1) `fromPlayer` возвращает список невлезших предметов (или перекладывает их в свободные слоты/рюкзак трупа/дроп) — никогда не удаляет предмет при неудачном `pocketsGrid.place(...)`.
3. `src/main/java/com/tarkovcraft/client/screen/TarkovInventoryScreen.java` — (P0 #1) отрисовка **всех 9 слотов** экипировки на трупе (`HELMET`, `FACE_MASK`, `BODY_ARMOR`, `RIG`, `BACKPACK`, `PRIMARY_1`, `PRIMARY_2`, `PISTOL`, `MELEE`); (P1 #7) использование кэшированного веса `ClientLoadoutCache.getCachedWeightKg()`.
4. `src/main/java/com/tarkovcraft/TarkovCraftMod.java` — (P0 #1) сохранять `PlayerLoadout` в `ServerPlayerEvents.COPY_FROM` при `GameRules.KEEP_INVENTORY`; (P1 #8) сброс `AirdropManager.resetTimer()` на `ServerLifecycleEvents.SERVER_STARTED` / `SERVER_STOPPED`; обновить стартовый лог.
5. `src/main/java/com/tarkovcraft/registry/ModItems.java` — (Фича #2) новая вкладка креатива `TARKOV_AMMO_GROUP` (`tarkov_ammo_tab`) со всеми калибрами `TaCZCaliberCatalog` × 4 тира.
6. `src/main/java/com/tarkovcraft/loadout/PlayerLoadout.java` — (P0 #3) фикс потери стака при частичном `tryAutoPlace` в `tryInsertItem`; (P0 #6) симметричный метод `setExposedStorageStack(..., boolean includeBackpack)`; (P1 #7) безопасный tick-scoped + mutation-invalidated кэш `getExposedStorageStackCached(...)`.
7. `src/main/java/com/tarkovcraft/mixin/PlayerInventoryMixin.java` — (P0 #6) вызывать `loadout.setExposedStorageStack(slot - 9, safe, player.getUuid(), isExternalWorkbenchOpen())` в `setStack`; (P1 #7) использовать `getExposedStorageStackCached(...)` в `getStack`.
8. `src/main/java/com/tarkovcraft/grid/GridContainer.java` — (P0 #4) перегрузка `tryAutoPlace(ItemStack stack, @Nullable Predicate<UUID> canMergeIntoEntry)` (не даёт мержить предмет в ещё не найденный стак в ящике).
9. `src/main/java/com/tarkovcraft/screen/TarkovScreenHandler.java` — (P0 #4) в `handleQuickTransfer` помечать discovered ТОЛЬКО вновь созданные `GridEntry`, а не весь ящик; (P1 #8) в `handleUseItem` списывать стимулятор/ремнабор ТОЛЬКО после `applyStimulator`/`applyRepair == true`.
10. `src/main/java/com/tarkovcraft/client/ClientLoadoutCache.java` — (P1 #7) кэш `cachedWeightKg`, пересчитываемый только при обновлении loadout.
11. `src/main/java/com/tarkovcraft/workbench/TarkovWorkbenchRecipes.java` — (P1 #7) кэш списков `GearRecipe` по `GearCategory`; (P2 #8) проверка `armor.isGasProtection()` вместо транслита `"protivogaz"`.
12. `src/main/java/com/tarkovcraft/client/ClientWorkbenchCache.java` — (P1 #7) счётчик `revision` (инкрементируется в `update(...)`).
13. `src/main/java/com/tarkovcraft/client/screen/TarkovWorkbenchScreen.java` — (P1 #7) кэширование отфильтрованного и отсортированного списка `GearRecipe` с инвалидацией по `(category, query, sortMode, onlyAvailable, revision)`.
14. `src/main/java/com/tarkovcraft/health/BodyPartHealth.java` — (P1 #8) развести `bleedRoll` и `fractureRoll` (добавить 14-аргументную перегрузку `applyHit`, сохранив 13-аргументную для совместимости тестов).
15. `src/main/java/com/tarkovcraft/health/TaCZBulletHelper.java` — (P1 #8) кэшировать reflection `Method` (`getGunId`, `getAmmoId`) в `ConcurrentHashMap<Class<?>, BulletGetters>`.
16. `src/main/java/com/tarkovcraft/item/TarkovArmorItem.java`, `TarkovCaseItem.java`, `TarkovItemCatalog.java`, `TarkovGrenadeEntity.java`, `TarkovLootGenerator.java` — (P2 #8) убрать транслит `"protivogaz"`/`"inektor"` из бизнес-логики, перейдя на `armor.isGasProtection()` и `caseItem.isMedicalCase()` (загружаемые из `items_catalog.json`).
17. `src/main/java/com/tarkovcraft/airdrop/AirdropManager.java` — (P1 #8) проверка `world.isChunkLoaded(x >> 4, z >> 4)` и выбор твёрдой неводной поверхности (до 16 попыток) перед спавном аирдропа.
18. `src/main/java/com/tarkovcraft/worldgen/ModWorldGen.java` — (P2 #8) `FOREST_STASH` только в `BiomeTags.IS_FOREST` / `IS_TAIGA`; остальные 3 структуры — Overworld без океанов/рек/пляжей.
19. `src/test/java/com/tarkovcraft/audit/AuditFixesV1Test.java` **(новый)** — JUnit 5 регрессионные тесты на все P0/P1 фиксы и вкладку патронов.

---

## 2. Детальный разбор и точные фиксы каждого пункта

### Пункт 1 [P0] — Исчезновение оружия и предметов при смерти игрока

**Подтверждённые по реальному коду 3 причины:**
1. **`TarkovInventoryScreen.renderExternalColumn` (`MODE_CORPSE`, строки 624–636):**
   - Рисуются только 6 из 9 слотов трупа: `HELMET`, `BODY_ARMOR`, `PRIMARY_1`, `RIG`, `BACKPACK`, `FACE_MASK`.
   - Слоты **`PRIMARY_2` (ON BACK), `PISTOL` (HOLSTER) и `MELEE` (SCABBARD) вообще не отрисовываются!** Оружие из этих слотов остаётся запертым внутри `CorpseEntity` и пропадает вместе с трупом.
   - **Фикс UI:** в `renderExternalColumn` для `MODE_CORPSE` отрисовать **все 9 слотов** в 5 компактных рядах (`h = 24..26`, `halfW`):
     - Ряд 1: `HELMET` ("HELM") | `FACE_MASK` ("MASK")
     - Ряд 2: `BODY_ARMOR` ("ARMOR") | `RIG` ("RIG")
     - Ряд 3: `BACKPACK` ("BACKPACK") | `MELEE` ("MELEE")
     - Ряд 4: `PRIMARY_1` ("PRIMARY 1") | `PRIMARY_2` ("PRIMARY 2")
     - Ряд 5: `PISTOL` ("HOLSTER", ширина `fullW` или `halfW`)
2. **`PlayerEntityMixin.tarkovcraft$spawnCorpseInsteadOfDropping` (строки 216–226):**
   - Игнорирует `GameRules.KEEP_INVENTORY` (всегда стирает `loadout` и спавнит труп).
   - Вызывает `self.getInventory().clear()`, **удаляя в пустоту** `self.getInventory().offHand` (слот 40) и любые предметы в `self.getInventory().main` (например, взятые в Creative до переключения в Survival)!
   - Не вызывает `tarkovcraft$syncLoadout()` после очистки.
   - **Фикс (`PlayerEntityMixin` + `CorpseEntity.fromPlayer` + `TarkovCraftMod`):**
     - Если `self.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)` -> `ci.cancel(); return;` (ничего не чистим и труп не спавним!). В `TarkovCraftMod` в `ServerPlayerEvents.COPY_FROM`: копировать `loadout` если `alive || oldPlayer.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)`.
     - Если `keepInventory == false`:
       - Собрать список доп. предметов `List<ItemStack> extras`:
         - из `self.getInventory().offHand` (все непустые стеки -> `.copy()`),
         - из `self.getInventory().main` (все непустые стеки — читать напрямую поле `DefaultedList<ItemStack> main`, минуя `PlayerInventoryMixin.getStack`!).
       - Если `!tarkovcraft$loadout.isEmpty() || !extras.isEmpty()`:
         - Вызвать `List<ItemStack> overflow = new ArrayList<>();`
         - `CorpseEntity corpse = CorpseEntity.fromPlayer(self.getWorld(), self, tarkovcraft$loadout, extras, overflow);`
         - В `CorpseEntity.fromPlayer`:
           1. Все 9 слотов `EquipmentSlotType` сохраняются в `corpse.loadout`.
           2. Все 6 карманов: `if (corpse.pocketsGrid.place(p.copy(), i, 0, false) == null) overflow.add(p.copy());` (НИКОГДА не терять!).
           3. Для каждого `extra` из `extras`: сначала `corpse.pocketsGrid.tryAutoPlace(extra)`, если не пусто -> попробовать свободный слот оружия/брони (`corpse.loadout.trySmartPickup`), если не пусто -> положить в `corpse`'s backpack/rig если есть, и если `!extra.isEmpty()` -> добавить в `overflow`!
         - Заспавнить `corpse` в мире.
         - Для каждого стека из `overflow`: безопасно дропнуть в мир (`self.dropItem(st, true, false)`).
         - Очистить `tarkovcraft$loadout.clear()`, `self.getInventory().clear()`, вызвать `tarkovcraft$syncLoadout()`, `ci.cancel()`.
   - **Инвариант:** Ни один предмет из `loadout`, `offHand` или `inventory.main` физически не может исчезнуть при смерти!

---

### Пункт 2 [Фича] — Вкладка креатива с тирами патронов (`tarkov_ammo_tab`)

- В `ModItems.java` добавить 7-ю вкладку `TARKOV_AMMO_GROUP`:
  ```java
  public static final ItemGroup TARKOV_AMMO_GROUP = Registry.register(
      Registries.ITEM_GROUP,
      new Identifier(TarkovCraftMod.MOD_ID, "tarkov_ammo_tab"),
      FabricItemGroup.builder()
          .icon(() -> Registries.ITEM.containsId(new Identifier("tacz", "ammo"))
              ? AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.AP, 1)
              : new ItemStack(ModBlocks.TARKOV_WORKBENCH))
          .displayName(Text.translatable("itemGroup.tarkovcraft.tarkov_ammo_tab"))
          .entries((displayContext, entries) -> {
              if (!Registries.ITEM.containsId(new Identifier("tacz", "ammo"))) {
                  return;
              }
              for (TaCZCaliberCatalog.CaliberSpec spec : TaCZCaliberCatalog.getAllCalibers()) {
                  for (AmmoTier tier : AmmoTier.values()) {
                      entries.add(AmmoTier.createTaczAmmoStack(spec.ammoId(), tier, 1));
                  }
              }
          })
          .build()
  );
  ```
- **Локализация (`assets-catalog`):**
  - `en_us.json`: `"itemGroup.tarkovcraft.tarkov_ammo_tab": "TarkovCraft: Ammo Tiers"`
  - `ru_ru.json`: `"itemGroup.tarkovcraft.tarkov_ammo_tab": "TarkovCraft: Патроны (Тиры)"`
- **Безопасность без TaCZ:** при отсутствии `tacz:ammo` иконка берётся с `ModBlocks.TARKOV_WORKBENCH`, список записей просто пуст — 0 крашей и 0 мусорных `Iron Nugget`.

---

### Пункт 3 [P0] — Потеря лута при частичном стакинге в `PlayerLoadout.tryInsertItem`

**Подтверждено в `PlayerLoadout.java` (строки 218–246):**
- При частичном слиянии стака `grid.tryAutoPlace(incoming)` уменьшает `incoming.count`, но возвращает `false`, из-за чего `writeRigGrids` / `writeBackpackGrid` **не вызывается**, а вызывающий код списывает `before - incoming.getCount()` -> часть стака исчезает!
- **Точный фикс:**
  - В блоке Rig:
    ```java
    boolean changed = false;
    for (GridContainer grid : rigGrids) {
        int beforeGrid = incoming.getCount();
        boolean full = grid.tryAutoPlace(incoming);
        if (full || incoming.getCount() < beforeGrid) {
            changed = true;
            if (incoming.isEmpty()) {
                break;
            }
        }
    }
    if (changed) {
        ContainerItemHelper.writeRigGrids(rigStack, rigGrids);
        invalidateExposedCache();
        if (incoming.isEmpty()) {
            return true;
        }
    }
    ```
  - В блоке Backpack:
    ```java
    GridContainer bpGrid = ContainerItemHelper.readBackpackGrid(bpStack);
    int beforeBp = incoming.getCount();
    boolean full = bpGrid.tryAutoPlace(incoming);
    if (full || incoming.getCount() < beforeBp) {
        ContainerItemHelper.writeBackpackGrid(bpStack, bpGrid);
        invalidateExposedCache();
        if (incoming.isEmpty()) {
            return true;
        }
    }
    ```

---

### Пункт 4 [P0] — Эксплойт мгновенного обыска в `TarkovScreenHandler.handleQuickTransfer`

**Подтверждено в `TarkovScreenHandler.java` (строки 419–431):**
- Цикл `for (GridEntry e : lootBlock.getGrid().getEntries()) lootBlock.getSearchMemory().markEntryDiscovered(playerId, e.getEntryId());` раскрывал ВСЕ ненайденные предметы в ящике!
- **Точный фикс:**
  1. В `GridContainer.java` добавить перегрузку:
     ```java
     public boolean tryAutoPlace(ItemStack stack, @Nullable java.util.function.Predicate<UUID> canMergeIntoEntry)
     ```
     (существующий `tryAutoPlace(ItemStack)` делегирует в `tryAutoPlace(stack, null)`). При слиянии в существующий `entry` проверять `canMergeIntoEntry == null || canMergeIntoEntry.test(entry.getEntryId())` — нельзя мержить свой предмет в ненайденный стак!
  2. В `TarkovScreenHandler.handleQuickTransfer` (блок `MODE_LOOT_BLOCK`):
     ```java
     Set<UUID> existingIds = new HashSet<>();
     for (GridEntry e : lootBlock.getGrid().getEntries()) {
         existingIds.add(e.getEntryId());
     }
     ContainerSearchMemory mem = lootBlock.getSearchMemory();
     if (lootBlock.getGrid().tryAutoPlace(copy, id -> mem.isEntryDiscovered(playerId, id)) || copy.getCount() < before) {
         int moved = before - copy.getCount();
         extractSource(loadout, srcLoc, srcIdx, srcEntryId, moved);
         for (GridEntry e : lootBlock.getGrid().getEntries()) {
             if (!existingIds.contains(e.getEntryId())) {
                 mem.markEntryDiscovered(playerId, e.getEntryId());
             }
         }
         lootBlock.markDirty();
         syncToClient(serverPlayer);
     }
     ```

---

### Пункт 5 [P0] — `Resistance` и `Absorption` не работают в `PlayerEntityMixin.applyDamage`

**Подтверждено (`javap`: `DamageTypeTags.BYPASSES_RESISTANCE`, `LivingEntity.getAbsorptionAmount()`, `setAbsorptionAmount(float)`):**
- В `PlayerEntityMixin.tarkovcraft$applyZonalBodyDamage`:
  1. **Учёт `StatusEffects.RESISTANCE`** (до расчёта урона — и для падения, и для боевого урона, если `!source.isIn(DamageTypeTags.BYPASSES_RESISTANCE)`):
     ```java
     StatusEffectInstance res = self.getStatusEffect(StatusEffects.RESISTANCE);
     if (res != null && !source.isIn(DamageTypeTags.BYPASSES_RESISTANCE)) {
         int reduction = Math.min(25, (res.getAmplifier() + 1) * 5);
         float mult = Math.max(0.0f, (25 - reduction) / 25.0f);
         amount *= mult; // (или tarkovDamage *= mult)
     }
     if (amount <= 0.001f) {
         return;
     }
     ```
  2. **Учёт `Absorption` (золотые сердца)** перед `tarkovcraft$bodyHealth.applyHit(...)`:
     - Масштаб: `1.0` vanilla HP = `TARKOV_DAMAGE_SCALE` (`4.5f`) Tarkov HP.
     ```java
     float absorptionVanilla = self.getAbsorptionAmount();
     if (absorptionVanilla > 0.0f && tarkovDamage > 0.0f && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         float absorptionTarkov = absorptionVanilla * TARKOV_DAMAGE_SCALE;
         float absorbedTarkov = Math.min(tarkovDamage, absorptionTarkov);
         tarkovDamage -= absorbedTarkov;
         self.setAbsorptionAmount(Math.max(0.0f, absorptionVanilla - absorbedTarkov / TARKOV_DAMAGE_SCALE));
         if (tarkovDamage <= 0.001f) {
             self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
                 SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.5f, 1.35f);
             return;
         }
     }
     ```

---

### Пункт 6 [P0] — Симметричность `PlayerInventoryMixin.setStack` в сторонних верстаках

**Подтверждено в `PlayerInventoryMixin.java` (строки 62, 85, 121, 152):**
- `getStack` и `removeStack` для слотов `9..35` работают по `rig + (includeBackpack ? backpack : none)`, а `setStack` вызывал `loadout.setRigExposedStack(slot - 9, safe)` (только Rig!).
- **Точный фикс:**
  - В `PlayerLoadout.java` добавить `public boolean setExposedStorageStack(int slotIndex, ItemStack incoming, @Nullable UUID playerId, boolean includeBackpack)`:
    - Повторяет 1-в-1 обход `getExposedStorageStack` (с теми же проверками `!requiresSearch || isGridRevealed` на Rig и Backpack).
    - Если `cursor == slotIndex` попадает в Rig — обновляет/удаляет entry и пишет `writeRigGrids`; если в Backpack (`includeBackpack == true`) — обновляет/удаляет entry и пишет `writeBackpackGrid`.
    - Если `cursor` прошёл все существующие записи и `incoming` не пуст — пытается положить в Rig, а при `includeBackpack == true` и в Backpack.
    - Вызывает `invalidateExposedCache()`.
  - В `PlayerInventoryMixin.tarkovcraft$setStack` заменить строку 152 на:
    `loadout.setExposedStorageStack(slot - 9, safe, player.getUuid(), isExternalWorkbenchOpen());`

---

### Пункт 7 [P1 Перформанс] — Убрать лишний парсинг NBT и аллокации на кадр/тик

1. **`ClientLoadoutCache` + `TarkovInventoryScreen.drawBottomStatsStrip`:**
   - Добавить `private static float cachedWeightKg = 0.0f;` в `ClientLoadoutCache`.
   - Пересчитывать `cachedWeightKg = LOADOUT.calculateWeightKg();` строго внутри `updateLoadoutFromNbt(NbtCompound)` (и геттер `getCachedWeightKg()`).
   - В `TarkovInventoryScreen.drawBottomStatsStrip()` заменить `loadout.calculateWeightKg()` на `ClientLoadoutCache.getCachedWeightKg()`.
2. **`TarkovWorkbenchRecipes` + `ClientWorkbenchCache` + `TarkovWorkbenchScreen`:**
   - В `TarkovWorkbenchRecipes`: кэшировать `EnumMap<GearCategory, List<GearRecipe>>` (`computeIfAbsent`, не пересоздавать сотни объектов на каждом кадре!).
   - В `ClientWorkbenchCache`: добавить `private static int revision = 0;` (увеличивать в `update(...)`, геттер `getRevision()`).
   - В `TarkovWorkbenchScreen`: кэшировать `List<GearRecipe> cachedFilteredGear` и перестраивать его только если изменилось любое из: `(gearCategory, query, gearSortMode, onlyAvailableGear, creative, ClientWorkbenchCache.getRevision())`.
3. **`PlayerInventoryMixin.getStack` (слоты `9..35`):**
   - В `PlayerLoadout` добавить transient tick-scoped + mutation-invalidated кэш:
     - `private transient long exposedCacheTick = Long.MIN_VALUE;`
     - `private transient boolean exposedCacheIncludeBp = false;`
     - `private transient List<ItemStack> exposedCache = null;`
     - `public void invalidateExposedCache() { exposedCache = null; exposedCacheTick = Long.MIN_VALUE; }`
     - `public ItemStack getExposedStorageStackCached(int slotIndex, @Nullable UUID playerId, boolean includeBackpack, long worldTick)`:
       - Если `exposedCache == null || exposedCacheTick != worldTick || exposedCacheIncludeBp != includeBackpack`, один раз собрать плоский `List<ItemStack>` из Rig (+ Backpack если `includeBackpack`) и запомнить `(worldTick, includeBackpack)`.
       - Вернуть `slotIndex >= 0 && slotIndex < exposedCache.size() ? exposedCache.get(slotIndex) : ItemStack.EMPTY`.
     - Вызывать `invalidateExposedCache()` во всех мутирующих методах `PlayerLoadout` (`setEquipment`, `tryInsertItem`, `removeExposedStorageStack`, `setExposedStorageStack`, `clear`, `readNbt`).
     - В `PlayerInventoryMixin.tarkovcraft$getStack`: передавать `player.getWorld() != null ? player.getWorld().getTime() : 0L`. (27 вызовов за цикл превращаются в 1 парсинг NBT, с нулевым риском stale data!)

---

### Пункт 8 [P1–P2] — Точечные исправления качества

1. **`BodyPartHealth.applyHit` — два независимых броска (`bleedRoll`, `fractureRoll`):**
   - Добавить 14-аргументную перегрузку `applyHit(..., float ricochetRoll, float bleedRoll, float fractureRoll, ...)`.
   - Существующая 13-аргументная перегрузка делегирует в 14-аргументную (передавая `injuryRoll` как `bleedRoll` и `(injuryRoll * 0.6180339f + 0.37f) % 1.0f` как `fractureRoll` — сохраняет 100% совместимость с существующими тестами!).
   - В `PlayerEntityMixin`, `TarkovGrenadeEntity`, `TarkovCommand` передавать два раздельных `random.nextFloat()`.
2. **`TaCZBulletHelper.inspectDamageSource` — кэш reflection `Method`:**
   - `private static final ConcurrentHashMap<Class<?>, BulletGetters> GETTER_CACHE = new ConcurrentHashMap<>();`
   - `private record BulletGetters(@Nullable Method getGunId, @Nullable Method getAmmoId) {}`
   - Однократный `computeIfAbsent` по классу пули.
3. **Убрать транслит `"protivogaz"` / `"respirator"` / `"inektor"` из Java-логики:**
   - `TarkovArmorItem`: добавить `private final boolean gasProtection;` (перегруженный конструктор + геттер `isGasProtection()`, по умолчанию `false`).
   - `TarkovCaseItem`: добавить `public enum CaseRole { GENERAL, MEDICAL }` + поле `caseRole` + геттер `isMedicalCase()`.
   - `TarkovItemCatalog`: читать `"gasProtection"` (boolean) для `face_mask` и `"caseRole"` (String) для `case` из `items_catalog.json`.
   - `assets-catalog`: проставить `"gasProtection": true` на 6 противогазов/респираторов и `"caseRole": "MEDICAL"` на 3 медкейса/инъектор-кейса в `items_catalog.json`.
   - Заменить проверки в:
     - `TarkovGrenadeEntity.hasGasMaskProtection`: проверять `mask.getItem() instanceof TarkovArmorItem armor && armor.isGasProtection()`!
     - `TarkovWorkbenchRecipes.computeGearIngredients`: проверять `armor.isGasProtection()` вместо `path.contains("protivogaz")`!
     - `TarkovLootGenerator`: проверять `caze.isMedicalCase()` вместо `path.contains("med") || path.contains("inektor")`!
4. **`AirdropManager`:**
   - В `TarkovCraftMod.onInitialize()`:
     - `ServerLifecycleEvents.SERVER_STARTED.register(server -> AirdropManager.resetTimer());`
     - `ServerLifecycleEvents.SERVER_STOPPED.register(server -> AirdropManager.resetTimer());`
   - В `AirdropManager.spawnAirdropNear`: до 16 попыток найти точку `(x, z)` с `world.isChunkLoaded(x >> 4, z >> 4)` и твёрдой неводной поверхностью (`world.getFluidState(surfacePos).isEmpty()`). В `spawnAirdropAt`: защита `if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;`.
5. **`ModWorldGen` (проверено по `BiomeTags` через `javap`):**
   - `FOREST_STASH`: `BiomeSelectors.tag(BiomeTags.IS_FOREST).or(BiomeSelectors.tag(BiomeTags.IS_TAIGA))`.
   - `ROADBLOCK`, `MED_TENT`, `MILITARY_CAMP`: `BiomeSelectors.foundInOverworld().and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate()).and(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN).negate()).and(BiomeSelectors.tag(BiomeTags.IS_RIVER).negate()).and(BiomeSelectors.tag(BiomeTags.IS_BEACH).negate())`.
6. **`TarkovScreenHandler.handleUseItem` (без потери предмета при неудачном применении):**
   - Для `TarkovStimulatorItem` и `TarkovArmorRepairItem`: НЕ вызывать `extractSource` заранее! Передать `ItemStack singleCopy = peek.copyWithCount(1);` в `applyStimulator`/`applyRepair`, и ТОЛЬКО если вернулось `true` (и `!serverPlayer.isCreative()`), вызвать `extractSource(loadout, srcLoc, srcIdx, srcEntryId, 1)`.
7. **`TarkovCraftMod` стартовый лог:**
   - Заменить устаревшую строку на: `LOGGER.info("TarkovCraft initialized (Grid Inventory, Zonal Armor, Ballistic Ammo, Workbench, Worldgen & Airdrops).");`

---

## 3. Тест-кейсы (JUnit 5) и верификация

В `src/test/java/com/tarkovcraft/audit/AuditFixesV1Test.java`:
1. **`CorpseEntity.fromPlayer`:** сохраняет ВСЕ 9 слотов оружия/брони (`PRIMARY_1`, `PRIMARY_2`, `PISTOL`, `MELEE`, шлем, маска, броня, разгрузка, рюкзак) + переносит `extras` (offHand / vanilla main) в карманы или возвращает в `overflow` — суммарное число предметов до и после смерти **строго равно** (0 потерь!).
2. **`ModItems.TARKOV_AMMO_GROUP`:** вкладка зарегистрирована в `Registries.ITEM_GROUP` под `tarkovcraft:tarkov_ammo_tab` без крашей.
3. **`PlayerLoadout.tryInsertItem` (частичное слияние):** при частичном добавлении стака в почти полную разгрузку и рюкзак NBT **сохраняется** и сумма (внутри контейнера + остаток `incoming`) строго равна исходному количеству.
4. **Анти-эксплойт обыска (`GridContainer.tryAutoPlace` с предикатом):** добавление предмета в ящик не мержится в ненайденный стак и не раскрывает существующие ненайденные `GridEntry`.
5. **Симметричность `PlayerLoadout.setExposedStorageStack`:** изменение и удаление слота из Backpack при `includeBackpack = true` корректно сохраняется в NBT рюкзака; `getExposedStorageStackCached` отражает изменения сразу после мутации.
6. **`BodyPartHealth.applyHit` с раздельными `bleedRoll` / `fractureRoll`:** при `bleedRoll = 0.01f, fractureRoll = 0.99f` накладывается кровотечение БЕЗ перелома, и наоборот.
7. **Отсутствие транслита:** `TarkovArmorItem.isGasProtection()` возвращает `true` ровно для 6 противогазов/респираторов и `false` для балаклав; `TarkovCaseItem.isMedicalCase()` возвращает `true` ровно для 3 мед/инъектор-кейсов.

### Команда верификации (JDK 17 обязателен!)
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.9.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build --no-daemon
```
