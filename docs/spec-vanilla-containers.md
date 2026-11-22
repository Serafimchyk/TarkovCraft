# Спецификация: Ванильные контейнеры в системе лута и обыска TarkovCraft

**Статус:** Approved Design Spec (`architect`)  
**Стек:** Minecraft `1.20.1`, Fabric Loader `0.16.10`, Fabric API `0.92.12+1.20.1`, Yarn `1.20.1+build.10`, Java `17`, `environment: "*"`.  
**Mixin / Gradle / `fabric.mod.json`:** **Новые Mixin и правки `tarkovcraft.mixins.json` / `build.gradle` НЕ требуются!** Всё реализуется чисто через Fabric API `UseBlockCallback.EVENT`, `PlayerBlockBreakEvents.AFTER`, существующие `TarkovNetworking`, `TarkovScreenHandler`, `TarkovInventoryScreen` и `InGameHudMixin`.

---

## 1. Разделение владения файлами (строго без пересечений)

| Воркер | Права владения файлами |
| :--- | :--- |
| **`assets-catalog`** | ТОЛЬКО `src/main/resources/assets/tarkovcraft/lang/en_us.json` и `ru_ru.json` (2 новых ключа сообщений §7) |
| **`java-catalog`** | ТОЛЬКО `src/main/java/**` и `src/test/java/**` |

### Список файлов (`java-catalog`)
1. `src/main/java/com/tarkovcraft/container/VanillaContainerState.java` **(новый)** — `PersistentState` на `ServerWorld` для хранения `GridContainer` (позиции/повороты) и `ContainerSearchMemory` по каноническому ключу ванильного контейнера.
2. `src/main/java/com/tarkovcraft/container/VanillaContainerBridge.java` **(новый)** — определение поддерживаемых ванильных блоков, проверка блокировки крышки, триггер ванильных лут-таблиц (`checkLootInteraction`), канонические ключи (вкл. двойной сундук и эндер-сундук), математически безубыточная двусторонняя синхронизация `GridContainer <-> Inventory` и дельта-реконсиляция (воронки/внешние правки).
3. `src/main/java/com/tarkovcraft/screen/TarkovScreenHandler.java` — новый режим `MODE_VANILLA_CONTAINER = 4`, открытие `openVanillaContainer(...)`, `onOpen`/`onClose` (анимация и звуки крышки), `canUse`, все операции `LOC_EXTERNAL_GRID`, `LOC_FLOATING_CONTAINER` (открытие обысканных кейсов/рюкзаков/разгрузок из ванильного сундука!), лимит числа стаков `entries.size() < inv.size()` и запрет вложения шалкера в шалкер (`stack.getItem().canBeNested()`).
4. `src/main/java/com/tarkovcraft/network/TarkovNetworking.java` — в `handleInteract` (`[F]` по блоку) вызывать `VanillaContainerBridge.tryOpenForPlayer(player, blockHit.getBlockPos())`.
5. `src/main/java/com/tarkovcraft/TarkovCraftMod.java` — регистрация `UseBlockCallback.EVENT` (перехват ПКМ в Survival/Adventure) и `PlayerBlockBreakEvents.AFTER` (очистка `VanillaContainerState` при сломе блока).
6. `src/main/java/com/tarkovcraft/mixin/InGameHudMixin.java` — подсказка у прицела `[F] Обыскать <Сундук / Бочка / Шалкер / Эндер-сундук>` для `VanillaContainerBridge.isSupportedBlock(...)` (идентично `SearchableLootBlock`).
7. `src/main/java/com/tarkovcraft/client/screen/TarkovInventoryScreen.java` — проверить/адаптировать `extCell` под `9×7` (двойной сундук) в правой колонке, чтобы `9×7` полностью помещался без обрезки по ширине.
8. `src/test/java/com/tarkovcraft/container/VanillaContainerBridgeTest.java` **(новый)** — JUnit 5 тесты на конвертацию, динамический рост высоты, анти-потерю при 27/54 стаках, дельта-реконсиляцию, канонический ключ двойного сундука и NBT round-trip `VanillaContainerState`.

---

## 2. Охватываемые ванильные контейнеры и проверенные Yarn 1.20.1 сигнатуры

Поддерживаются ровно **5 типов ванильных контейнеров хранения** (раздатчики, выбрасыватели, воронки, печи, варочные стойки **не трогаем**, чтобы не ломать редстоун-схемы и их специализированные GUI):
1. `ChestBlock` — **одиночный** (27 слотов) и **двойной сундук** (54 слота)
2. `TrappedChestBlock` (наследник `ChestBlock`, 27 / 54 слота; сохраняет редстоун-сигнал через `onOpen`/`onClose`!)
3. `BarrelBlock` (27 слотов; сохраняет смену текстуры `OPEN` и звуки через `onOpen`/`onClose`!)
4. `ShulkerBoxBlock` (все 17 цветов, 27 слотов; сохраняет анимацию, звуки и запрет шалкера-в-шалкере!)
5. `EnderChestBlock` (27 слотов, личный `player.getEnderChestInventory()`)

**Проверенные через `javap` сигнатуры (Yarn `1.20.1+build.10` + Fabric API `0.92.12+1.20.1`):**
- `net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT`:
  `ActionResult interact(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult)`
- `net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER`:
  `void afterBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity)`
- `net.minecraft.block.ChestBlock`:
  - `public static final EnumProperty<ChestType> CHEST_TYPE`
  - `public static Direction getFacing(BlockState state)` *(возвращает направление к соседней половинке двойного сундука)*
  - `public static boolean isChestBlocked(WorldAccess world, BlockPos pos)`
  - `public static Inventory getInventory(ChestBlock block, BlockState state, World world, BlockPos pos, boolean ignoreBlocked)`
- `net.minecraft.block.ShulkerBoxBlock` *(внимание: `ShulkerBoxBlock.canOpen` — `private`, поэтому проверяем напрямую!)*:
  - `public static final DirectionProperty FACING`
  - `ShulkerBoxBlockEntity.getAnimationStage()` -> `ShulkerBoxBlockEntity.AnimationStage.CLOSED`
  - `ShulkerEntity.calculateBoundingBox(Direction facing, float extraLength, float prevExtraLength)` -> `Box`
- `net.minecraft.block.entity.LootableContainerBlockEntity`:
  - `public void checkLootInteraction(@Nullable PlayerEntity player)`
- `net.minecraft.inventory.EnderChestInventory`:
  - `public void setActiveBlockEntity(EnderChestBlockEntity blockEntity)`
  - `public void onOpen(PlayerEntity player)` / `public void onClose(PlayerEntity player)`
- `net.minecraft.item.Item.canBeNested()` -> `boolean` *(возвращает `false` для шалкеров)*
- `net.minecraft.world.PersistentState` + `ServerWorld.getPersistentStateManager().getOrCreate(Function<NbtCompound, T>, Supplier<T>, String)`

---

## 3. Механизм открытия (100% идентично модовым `SearchableLootBlock`)

1. **Клавиша `[F]` (основной способ — Survival и Creative):**
   - В `InGameHudMixin.renderWorldInteractPrompt`: когда прицел на блоке `VanillaContainerBridge.isSupportedBlock(state.getBlock())`, выводить точно такой же промпт, как для `SearchableLootBlock`: `prompt.tarkovcraft.search` (`[F] Обыскать Сундук` / `Бочка` / `Шалкер` / `Эндер-сундук`).
   - В `TarkovNetworking.handleInteract`: если блок — поддерживаемый ванильный контейнер -> вызвать `VanillaContainerBridge.tryOpenForPlayer(player, blockHit.getBlockPos())`.
2. **ПКМ (`UseBlockCallback.EVENT` в `TarkovCraftMod.onInitialize()`):**
   - Если `player.isSpectator()` -> `ActionResult.PASS`.
   - Если `player.shouldCancelInteraction()` (`Shift + ПКМ` с предметом в руке — установка блока/воронки/факела на сундук) -> `ActionResult.PASS`.
   - Если `player.isCreative()` -> `ActionResult.PASS` (в Creative доступен ванильный просмотр по ПКМ, а по `[F]` — EFT-интерфейс, согласованно с `InGameHudMixin`).
   - В **Survival / Adventure**:
     - Для поддерживаемых ванильных контейнеров **НЕ давать** открыться ванильному GUI (иначе обходится §6 обыск и тетрис-сетка!).
     - Если в руке **нет** TaCZ-оружия (`!AmmoTier.isTaczGunStack(player.getStackInHand(hand))`):
       - Для максимального удобства: либо на сервере открыть `VanillaContainerBridge.tryOpenForPlayer(sp, pos)` и вернуть `ActionResult.SUCCESS`, ЛИБО строго как `SearchableLootBlock` (только `[F]`).
       - **Выбранное решение (лучшее из обоих миров без конфликта с TaCZ ADS!):**
         - Если в руке TaCZ-оружие -> вернуть `ActionResult.FAIL` (отменяет ванильный сундук, позволяя TaCZ ADS работать свободно, и на сервере не спамит экран во время перестрелки).
         - Если в руке НЕ TaCZ-оружие (пустая рука / любой предмет при `!shouldCancelInteraction()`) -> на сервере вызвать `VanillaContainerBridge.tryOpenForPlayer(sp, pos)` (при `hand == Hand.MAIN_HAND`) и вернуть `ActionResult.SUCCESS`! Таким образом и `[F]`, и обычный ПКМ открывают наш EFT-интерфейс (с §6 обыском), а прицеливание из оружия TaCZ и `Shift+ПКМ` для постановки блоков не ломаются!
         - *(Дополнительно: проверь, стоит ли разрешить и для `SearchableLootBlock` такой же чистый `[F]` — `SearchableLootBlock` можно не трогать или оставить как есть, т.к. задача просит «чтобы ванильные контейнеры лутались и открывались также как модовые», т.е. обязательны `[F]` + подсказка у прицела + отсутствие ванильного GUI! Если игрок жмёт ПКМ с оружием — только подсказка `[F]`, если без оружия — удобно открывается EFT-экран.)*
3. **Проверка блокировки крышки (перед открытием):**
   - `ChestBlock`: проверить `ChestBlock.isChestBlocked(world, pos)`; если двойной сундук (`CHEST_TYPE != SINGLE`), проверить и соседнюю половинку `otherPos = pos.offset(ChestBlock.getFacing(state))`!
   - `EnderChestBlock`: проверить `world.getBlockState(pos.up()).isSolidBlock(world, pos.up())` (как в ванильном `EnderChestBlock.onUse`).
   - `ShulkerBoxBlock`: проверить `canOpenShulker(state, world, pos, shulkerBe)`:
     ```java
     if (be.getAnimationStage() != ShulkerBoxBlockEntity.AnimationStage.CLOSED) return true;
     Box box = ShulkerEntity.calculateBoundingBox(state.get(ShulkerBoxBlock.FACING), 0.0f, 0.5f)
         .offset(pos).contract(1.0E-6);
     return world.isSpaceEmpty(box);
     ```
   - `BarrelBlock`: открывается всегда (как в ванилле).
   - Если заблокирован -> отправить action-bar `message.tarkovcraft.container_blocked` и не открывать.
4. **Анимация и звуки крышки (`onOpen` / `onClose`):**
   - При создании `TarkovScreenHandler` в `MODE_VANILLA_CONTAINER`:
     - Для `EnderChestBlockEntity ecBe`: `player.getEnderChestInventory().setActiveBlockEntity(ecBe);`
     - Вызвать `vanillaInventory.onOpen(player);` (открывает крышку сундука/шалкера/эндер-сундука, меняет состояние `BarrelBlock.OPEN`, подаёт редстоун на `TrappedChestBlock`, играет звук открытия!).
   - В `TarkovScreenHandler.onClosed(PlayerEntity player)`:
     - Если `mode == MODE_VANILLA_CONTAINER && vanillaInventory != null`: вызвать `vanillaInventory.onClose(player);` (закрывает крышку и играет звук закрытия!).

---

## 4. Ванильные лут-таблицы и математически безубыточная синхронизация `GridContainer <-> Inventory`

### 4.1. Триггер ванильной генерации лута (данжи, деревни, крепости, корабли)
Перед построением/реконсиляцией сетки:
- Если `be instanceof LootableContainerBlockEntity lootBe`: вызвать `lootBe.checkLootInteraction(player)`.
- Для двойного сундука (`ChestBlock`, `CHEST_TYPE != SINGLE`): вызвать `checkLootInteraction(player)` на **обеих** половинках (`be1` и `be2` по `pos.offset(ChestBlock.getFacing(state))`), затем получить `Inventory inv = ChestBlock.getInventory(chestBlock, state, world, pos, true)`.

### 4.2. Канонические ключи в `VanillaContainerState` (`PersistentState`)
- Одиночный сундук / бочка / шалкер: `"block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ()`
- Двойной сундук: сравнить `pos` и `otherPos` по `(x, y, z)`, взять минимальный `minPos` -> `"block:" + minPos.getX() + "," + minPos.getY() + "," + minPos.getZ()` (обе половинки ВСЕГДА обращаются к одной записи!).
- Эндер-сундук: `"ender:" + player.getUuid()` (личный инвентарь игрока, не привязан к `BlockPos`!).

### 4.3. Размеры сетки и защита от потери предметов
- **Базовые размеры:**
  - 27-слотовый контейнер (сундук, сундук-ловушка, бочка, шалкер, эндер-сундук): **`6 × 5`** (30 клеток >= 27 слотов; ширина `6` вмещает винтовки `5×2` и броню `3×4`).
  - 54-слотовый контейнер (двойной сундук): **`9 × 7`** (63 клетки >= 54 слота).
- **Динамический рост высоты при импорте из ванильного `Inventory` (0% потерь!):**
  - Если внешний мод или воронка положили в сундук 27 крупных EFT-предметов (например `3×3`), которым не хватает `6×5` клеток по площади, `placeAllWithAutoGrow(baseW, baseH, stacks)` автоматически увеличивает `height += 3` (до макс. `24`) и раскладывает заново, пока **каждый** непустой стек из `Inventory` не будет размещён в `GridContainer`!
- **Инвариант при записи обратно `GridContainer -> Inventory`:**
  - В `TarkovScreenHandler` (`canInsertIntoDestination`, `handleQuickTransfer`, `placeSplitIntoSameOrOwnFreeCell`) для `MODE_VANILLA_CONTAINER`:
    - Если операция добавляет **новый** `GridEntry` (а не объединяет стак в существующий), проверять `grid.getEntries().size() < vanillaInventory.size()` (не более 27 стаков для обычного и 54 для двойного сундука!).
    - Для `ShulkerBoxBlockEntity`: дополнительно проверять `stack.getItem().canBeNested()` (запрет шалкера внутри шалкера, как в ванилле!).
  - При **любом** изменении сетки (перемещение, взятие, вложение, сплит, медицина, изменение вложенного кейса/рюкзака через `LOC_FLOATING_CONTAINER`!) немедленно вызывать `VanillaContainerBridge.syncGridToInventory(grid, vanillaInventory)` + `state.markDirty()`:
    ```java
    int slot = 0;
    for (GridEntry entry : grid.getEntries()) {
        if (slot >= inv.size()) break; // инвариант гарантирует entries.size() <= inv.size()
        inv.setStack(slot++, entry.getStack().copy());
    }
    while (slot < inv.size()) {
        inv.setStack(slot++, ItemStack.EMPTY);
    }
    inv.markDirty();
    ```
  - Благодаря мгновенному write-through: при сломе сундука/шалкера весь лут выпадает/сохраняется в предмете шалкера, компараторы выдают точный сигнал, воронки работают!

### 4.4. Дельта-реконсиляция (сохранение тетрис-раскладки и памяти обыска при воронках)
При каждом открытии контейнера `reconcile(StoredVanillaContainer stored, Inventory liveInv, int baseW, int baseH)`:
1. Собрать список непустых `ItemStack` из `liveInv`.
2. Проверить мультимножественное совпадение с `stored.grid.getEntries()` (по `ItemStack.canCombine(a, b) && a.getCount() == b.getCount()`):
   - **Если 100% совпадает (fast-path):** вернуть `stored.grid` и `stored.searchMemory` без изменений — все позиции, повороты и `discovered`-статусы сохраняются!
   - **Если воронка/внешний код изменили `liveInv` (delta-path):**
     - Оставить в `grid` все совпавшие `GridEntry` (с их прежними `entryId`, координатами и `discovered`-статусами!).
     - Удалить из `grid` те `GridEntry`, которых больше нет в `liveInv`.
     - Все новые `ItemStack` из `liveInv` вставить через `tryPlaceInFreeCell` (при нехватке места — пересобрать с увеличенной высотой, сохраняя прежние `entryId` у оставшихся записей!). Новые записи получают новые `entryId` и требуют обыска!
     - `state.markDirty()`.

---

## 5. Механика обыска (§6) и интеграция с `TarkovScreenHandler` / `TarkovInventoryScreen`

1. **Обыск (§6) на ванильных контейнерах:**
   - Работает через существующий `SearchTarget(grid.getContainerId(), List.of(grid), searchMemory, state::markDirty)` в `TarkovScreenHandler.getExternalSearchTargets()` — 100% идентично `SearchableLootBlockEntity`!
   - 1с раскрытие сетки (`containerRevealSeconds`) -> тёмные силуэты -> поочерёдный обыск по редкости (`ItemRarityRegistry`) -> ненайденные предметы нельзя забрать.
   - **Пустой контейнер:** при открытии если `grid.isEmpty()` -> сразу `searchMemory.markGridRevealed(playerId)` (не заставляет игрока ждать 1с на пустом сундуке!).
   - **Положенный игроком предмет:** помечается discovered ТОЛЬКО для свежедобавленного `GridEntry` (используя уже исправленный анти-эксплойт с `Set<UUID> existingIds` + `tryAutoPlace(copy, id -> mem.isEntryDiscovered(playerId, id))` — чужие неосмотренные предметы НЕ раскрываются!).
2. **Полная поддержка вложенных контейнеров (`LOC_FLOATING_CONTAINER`):**
   - В `TarkovScreenHandler.resolveContainerHandle` и `handleOpenCase` добавить ветку для `mode == MODE_VANILLA_CONTAINER && vanillaGrid != null`:
     - Можно открыть обысканный кейс / рюкзак / разгрузку прямо внутри ванильного сундука в плавающем окне!
     - При изменении содержимого этого кейса/рюкзака вызывать `markExternalDirty()` -> `VanillaContainerBridge.syncGridToInventory(...)` + `state.markDirty()`, чтобы обновлённый NBT кейса немедленно сохранился в ванильный `Inventory`!
3. **Адаптация ширины правой колонки (`TarkovInventoryScreen.java`):**
   - В `renderExternalColumn` уже есть:
     `int maxCols = snap.externalGridView() != null ? Math.max(6, snap.externalGridView().width()) : 6;`
     `int extCell = Math.max(12, Math.min(cellSize, (area.w - pad * 2 - 8) / maxCols));`
   - Это **уже** автоматически масштабирует `9×7` двойного сундука в правую колонку! Проверить, что `MODE_VANILLA_CONTAINER` попадает в ветку `else if (snap.externalGridView() != null)` (попадает автоматически, т.к. `mode != MODE_CORPSE && mode != MODE_GROUND_ITEM`!).
4. **Очистка `VanillaContainerState` при сломе блока:**
   - В `PlayerBlockBreakEvents.AFTER`: если `world instanceof ServerWorld sw` и `VanillaContainerBridge.isSupportedBlock(state.getBlock())`, удалить ключ `pos` (и канонический ключ двойного сундука, если это была половинка) из `VanillaContainerState.get(sw)` (для `EnderChestBlock` НЕ удалять, т.к. эндер-инвентарь привязан к игроку!).

---

## 6. Ключи локализации (`assets-catalog` — `en_us.json` и `ru_ru.json`)

| Ключ | `en_us.json` | `ru_ru.json` |
|---|---|---|
| `message.tarkovcraft.container_blocked` | `Container is blocked` | `Контейнер заблокирован` |
| `message.tarkovcraft.container_slot_limit` | `Container stack limit reached` | `Достигнут лимит слотов контейнера` |

---

## 7. Тест-кейсы (JUnit 5) и критерии приёмки

В `src/test/java/com/tarkovcraft/container/VanillaContainerBridgeTest.java`:
1. **Конвертация и сохранность (0% потерь):**
   - 27 `1×1` стаков в `SimpleInventory(27)` -> `reconcile` в `6×5` сохраняет все 27 стаков и `syncGridToInventory` возвращает точно 27 стаков.
   - 54 `1×1` стака в `SimpleInventory(54)` -> `reconcile` в `9×7` сохраняет все 54 стака.
   - 15 крупных `3×3` бронежилетов в `SimpleInventory(27)` -> динамически расширяет высоту сетки и сохраняет все 15 бронежилетов (0 потерь!).
2. **Дельта-реконсиляция и память обыска:**
   - При повторном открытии неизменённого `SimpleInventory` сохраняются точные `(x, y, rotated, entryId)` и `discovered`-статус.
   - При добавлении нового стака «воронкой» старый предмет остаётся `discovered`, а новый появляется как `undiscovered`!
3. **Запрет шалкера-в-шалкере и лимит стаков:**
   - `Items.SHULKER_BOX.canBeNested()` == `false` (блокируется при вставке в `ShulkerBoxBlockEntity`).
   - Попытка вставить 28-й отдельный стек в 27-слотовый ванильный контейнер отклоняется.
4. **Канонический ключ двойного сундука и `VanillaContainerState` NBT round-trip:**
   - `canonicalDoubleKey(posA, posB).equals(canonicalDoubleKey(posB, posA))` всегда `true`.
   - `VanillaContainerState.writeNbt` -> `VanillaContainerState.fromNbt` полностью сохраняет сетку и `ContainerSearchMemory`.

### Команда верификации (JDK 17 обязателен!)
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.9.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build --no-daemon
```
