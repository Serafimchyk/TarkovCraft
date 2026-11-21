# Спецификация: Тиры патронов TaCZ и Верстак брони/патронов (`tarkov_workbench`)

**Статус:** Approved Design Spec (`architect`)  
**Стек:** Minecraft `1.20.1`, Fabric Loader `0.16.10`, Fabric API `0.92.12+1.20.1`, Yarn `1.20.1+build.10`, Java `17`, `environment: "*"`.  
**Интеграция с TaCZ Refabricated (`tacz-refabricated-1.0.1-forge1.20.1`):** строго **zero-hard-dependency** (только `Identifier`, NBT, reflection); `build.gradle` и `fabric.mod.json` **не меняются**, `tarkovcraft.mixins.json` **не требует новых миксинов** (дорабатывается существующий `PlayerInventoryMixin.java`), **TactEq не трогать**.

---

## 1. Разделение владения файлами (строго без пересечений)

| Воркер | Права владения файлами |
| :--- | :--- |
| **`assets-catalog`** | `src/main/resources/assets/tarkovcraft/blockstates/tarkov_workbench.json`, `models/block/tarkov_workbench.json`, `models/item/tarkov_workbench.json`, `textures/block/tarkov_workbench_*.png`, `lang/en_us.json`, `lang/ru_ru.json`, `src/main/resources/data/tarkovcraft/recipes/tarkov_workbench.json`, `src/main/resources/data/tarkovcraft/loot_tables/blocks/tarkov_workbench.json` |
| **`java-catalog`** | ТОЛЬКО `src/main/java/**` и `src/test/java/**` |

> `assets-catalog` и `java-catalog` могут работать **параллельно**, опираясь на зафиксированные ниже ID, NBT-ключи, сигнатуры и ключи локализации.

### Список создаваемых/изменяемых файлов

**Владение `assets-catalog`:**
1. `src/main/resources/assets/tarkovcraft/blockstates/tarkov_workbench.json` **(новый)** — `facing=north/east/south/west` повороты (как для `HorizontalFacingBlock`).
2. `src/main/resources/assets/tarkovcraft/models/block/tarkov_workbench.json` **(новый)** — `minecraft:block/cube` с отдельными текстурами `up` (столешница с тисками/патронами), `front` (панель с ящиками), `side`, `down`.
3. `src/main/resources/assets/tarkovcraft/models/item/tarkov_workbench.json` **(новый)** — `{ "parent": "tarkovcraft:block/tarkov_workbench" }`.
4. `src/main/resources/assets/tarkovcraft/textures/block/tarkov_workbench_top.png`, `tarkov_workbench_front.png`, `tarkov_workbench_side.png`, `tarkov_workbench_bottom.png` **(новые 16x16 PNG)** — тёмная военная сталь/дерево в стиле EFT (`weapon_crate` / `technical_crate`).
5. `src/main/resources/data/tarkovcraft/recipes/tarkov_workbench.json` **(новый)** — ванильный shaped-рецепт крафта верстака.
6. `src/main/resources/data/tarkovcraft/loot_tables/blocks/tarkov_workbench.json` **(новый)** — дроп самого блока при сломе.
7. `src/main/resources/assets/tarkovcraft/lang/en_us.json` и `ru_ru.json` — новые ключи локализации (§7).

**Владение `java-catalog`:**
1. `src/main/java/com/tarkovcraft/ammo/AmmoTier.java` — расширение: `isTaczGunStack`, алиас чтения `tarkovcraft_ammo_tier`, `getAmmoId`, `getArmorClassEstimate`, цвет RGB для UI-бейджа.
2. `src/main/java/com/tarkovcraft/ammo/TaCZCaliberCatalog.java` **(новый)** — каталог всех 23 баллистических калибров дефолтного пака TaCZ + soft-reflection догрузка кастомных калибров через `TimelessAPI.getAllCommonAmmoIndex()`.
3. `src/main/java/com/tarkovcraft/ammo/AmmoReloadTracker.java` **(новый)** — серверный трекер перезарядки (дополняет `PlayerInventoryMixin`, гарантируя проставление `TarkovLoadedTier` в том числе в Creative и при любых способах пополнения `GunCurrentAmmoCount`).
4. `src/main/java/com/tarkovcraft/mixin/PlayerInventoryMixin.java` — усилить штамповку `TarkovLoadedTier`: проверять `AmmoTier.isTaczGunStack(activeGun)` и поддерживать синхронизацию при извлечении патронов.
5. `src/main/java/com/tarkovcraft/health/TaCZBulletHelper.java` — добавить точные базовые значения пробития для всех 23 калибров (включая `792x57`, `22wmr`).
6. `src/main/java/com/tarkovcraft/workbench/TarkovWorkbenchRecipes.java` **(новый)** — детерминированный движок рецептов для вкладки **AMMO** (крафт с нуля + переснаряжение `FMJ -> HP/AP/IGN`) и вкладки **ARMOR & GEAR** (автогенерация рецептов по классу брони `0..6` / `repairAmount` / вместимости разгрузки и рюкзака для всего каталога).
7. `src/main/java/com/tarkovcraft/workbench/WorkbenchInventoryHelper.java` **(новый)** — серверный подсчёт, атомарное списание (Pockets -> Rig -> Backpack в Survival; бесплатно в Creative) и выдача результата + сбор `WorkbenchSyncData`.
8. `src/main/java/com/tarkovcraft/block/TarkovWorkbenchBlock.java` **(новый)** — `HorizontalFacingBlock` (stateless, без BlockEntity), открывает верстак по ПКМ (`onUse`) и по `[F]`.
9. `src/main/java/com/tarkovcraft/screen/TarkovWorkbenchScreenHandler.java` **(новый)** — `ScreenHandler` верстака (валидация дистанции `<= 6.0` блоков, `openForPlayer`, `handleCraftAmmo`, `handleCraftGear`, `syncResources`).
10. `src/main/java/com/tarkovcraft/client/screen/TarkovWorkbenchScreen.java` **(новый, `@Environment(EnvType.CLIENT)`)** — EFT-интерфейс в стиле `TarkovUiTheme` с 2 вкладками (AMMO / ARMOR & GEAR), фильтрами, строкой поиска `TextFieldWidget`, превью и чек-листом ресурсов.
11. `src/main/java/com/tarkovcraft/client/ClientWorkbenchCache.java` **(новый, `@Environment(EnvType.CLIENT)`)** — клиентский кэш остатков ресурсов и доп. калибров, обновляемый по `S2C_WORKBENCH_SYNC`.
12. `src/main/java/com/tarkovcraft/registry/ModBlocks.java` — регистрация `TARKOV_WORKBENCH`.
13. `src/main/java/com/tarkovcraft/registry/ModItems.java` — добавление `ModBlocks.TARKOV_WORKBENCH` в `TARKOV_TACTICAL_GROUP`.
14. `src/main/java/com/tarkovcraft/registry/ModScreenHandlers.java` — регистрация `TARKOV_WORKBENCH_SCREEN_HANDLER`.
15. `src/main/java/com/tarkovcraft/network/TarkovNetworking.java` — 3 пакета (`S2C_WORKBENCH_SYNC`, `C2S_WORKBENCH_CRAFT_AMMO`, `C2S_WORKBENCH_CRAFT_GEAR`) + открытие `TarkovWorkbenchBlock` по `[F]` в `handleInteract`.
16. `src/main/java/com/tarkovcraft/client/ClientNetworkSender.java` — клиентские методы `sendWorkbenchCraftAmmo(...)`, `sendWorkbenchCraftGear(...)`, `registerWorkbenchClientReceiver()`.
17. `src/main/java/com/tarkovcraft/TarkovCraftMod.java` — тик `AmmoReloadTracker.tickPlayer(player)` в `END_SERVER_TICK`.
18. `src/main/java/com/tarkovcraft/client/TarkovCraftClient.java` — регистрация `HandledScreens.register(ModScreenHandlers.TARKOV_WORKBENCH_SCREEN_HANDLER, TarkovWorkbenchScreen::new)`, `ClientNetworkSender.registerWorkbenchClientReceiver()`, а также расширение `ItemTooltipCallback.EVENT` для тултипов всех `tacz:ammo` и заряженных стволов `tacz:modern_kinetic_gun`.
19. `src/main/java/com/tarkovcraft/client/screen/TarkovInventoryScreen.java` — окрашивать угловой бейдж патронов (`drawCellCornerTags`) в цвет тира (`HP` зелёный, `FMJ` серый, `AP` золотой, `IGN` красный).
20. `src/main/java/com/tarkovcraft/mixin/InGameHudMixin.java` — подсказка `[F] Open Workbench` при наведении на `TarkovWorkbenchBlock`.
21. `src/test/java/com/tarkovcraft/workbench/AmmoAndWorkbenchTest.java` **(новый)** — JUnit 5 тесты.

---

## 2. Проверенные сигнатуры TaCZ Refabricated и Yarn 1.20.1

### 2.1. Реальный TaCZ (`tacz-refabricated-1.0.1-forge1.20.1`) — проверено через `javap`
- Предмет патронов: `tacz:ammo` (`com.tacz.guns.item.AmmoItem`), калибр хранится в NBT `AmmoId` (например, `"tacz:545x39"`).
- Перезарядка: `AbstractGunItem.findAndExtractInventoryAmmo(IItemHandler, ItemStack, int)` вызывает `cn.sh1rocu.tacz.util.itemhandler.InvWrapper.extractItem(slot, amount, simulate)`, который при `simulate == false` вызывает `PlayerInventory.removeStack(int slot, int amount)` (`method_5434`). Это **уже перехватывается** в `PlayerInventoryMixin`!
- Пуля: `com.tacz.guns.entity.EntityKineticBullet` имеет публичные `getAmmoId()` и `getGunId()` (возвращают `Identifier`) — уже используются через reflection в `TaCZBulletHelper`.
- Реестр всех установленных калибров (включая сторонние gunpack'и):
  - `com.tacz.guns.api.TimelessAPI.getAllCommonAmmoIndex()` -> `Set<Map.Entry<Identifier, CommonAmmoIndex>>`.
  - Можно безопасно вызывать через `Class.forName("com.tacz.guns.api.TimelessAPI")` в `try/catch (Throwable)` (zero-hard-dependency!).
- Дефолтный пак TaCZ (`tacz_default_gun`) содержит **25** ID патронов: 23 баллистических (`9mm`, `45acp`, `762x25`, `57x28`, `46x30`, `357mag`, `50ae`, `500mag`, `12g`, `22wmr`, `545x39`, `556x45`, `58x42`, `300blk`, `762x39`, `68x51fury`, `308`, `762x54`, `30_06`, `45_70`, `792x57`, `338`, `50bmg`) + 2 взрывных (`40mm`, `rpg_rocket` — не тируются в баллистические HP/AP/IGN).

### 2.2. Проверенные Yarn 1.20.1 сигнатуры
- `net.minecraft.block.HorizontalFacingBlock`: `public static final DirectionProperty FACING;`
- `AbstractBlock.onUse(BlockState, World, BlockPos, PlayerEntity, Hand, BlockHitResult)` -> `ActionResult`
- `net.minecraft.client.gui.widget.TextFieldWidget(TextRenderer, int x, int y, int width, int height, Text message)`: `getText()`, `setText(String)`, `setChangedListener(Consumer<String>)`, `setMaxLength(int)`, `setDrawsBackground(boolean)`, `setPlaceholder(Text)`, `keyPressed(int, int, int)`, `charTyped(char, int)`.

---

## 3. Система тиров патронов вместо ванильных TaCZ

### 3.1. NBT-контракт и обратная совместимость (`AmmoTier.java`)
- **Ключи NBT:**
  - На `tacz:ammo`: `NBT_AMMO_TIER = "TarkovAmmoTier"` (значения `"hp"`, `"ap"`, `"ign"`; у `"fmj"` ключ отсутствует, чтобы штатные/лутовые патроны TaCZ бесшовно стакались со скрафченными `FMJ`!). При чтении `getFromAmmoStack` также принимать алиас `"tarkovcraft_ammo_tier"`.
  - На стволе (`tacz:modern_kinetic_gun`): `NBT_LOADED_TIER = "TarkovLoadedTier"` (`"hp"`, `"fmj"`, `"ap"`, `"ign"`).
- **Новые хелперы в `AmmoTier.java`:**
  - `public static boolean isTaczGunStack(ItemStack stack)` — `tacz:modern_kinetic_gun` или `nbt.contains("GunId")`.
  - `public static String getAmmoId(ItemStack stack)` — возвращает `nbt.getString("AmmoId")` или `""`.
  - `public int getArgbColor()` — `HP`: `0xFF55FF55`, `FMJ`: `0xFFB0B8C0`, `AP`: `0xFFFFAA00`, `IGN`: `0xFFFF5555`.
  - `public static String formatArmorClassTarget(int pen)` — `pen >= 55 -> "Class 6"`, `>= 45 -> "Class 5"`, `>= 35 -> "Class 4"`, `>= 25 -> "Class 3"`, `>= 15 -> "Class 2"`, иначе `"Class 1 / Flesh"`.

### 3.2. «Замена ванильных патронов TaCZ» и отображение тира (`TarkovCraftClient.java` — client-only!)
Без хрупких миксинов в TaCZ:
1. Любой немаркированный `tacz:ammo` (с верстака TaCZ, из креатива или лута) автоматически является **`FMJ` (`PS/FMJ`)** и получает полный баллистический тултип.
2. В `TarkovCraftClient.onInitializeClient()` внутри `ItemTooltipCallback.EVENT`:
   - **Если `AmmoTier.isTaczAmmoStack(stack)`:**
     - К заголовку `lines.get(0)` дописать цветной бейдж тира: `lines.set(0, lines.get(0).copy().append(" ").append(Text.literal("[" + tier.getBadge() + "]").formatted(tier.getColor())))`.
     - Вставить с индекса `1`:
       - `tooltip.tarkovcraft.ammo_tier` (цвет тира): тир (`HP` / `PS/FMJ` / `BP/AP` / `BS/M995`)
       - `tooltip.tarkovcraft.ammo_pen` (`GRAY`/`YELLOW`): пробитие для данного `AmmoId` (`TaCZBulletHelper.computePenetration(ammoId, tier)`) + ориентир по классу брони
       - `tooltip.tarkovcraft.ammo_dmg_wear` (`GRAY`): `Damage x1.30 | Armor Wear x0.70`
       - Если `tier == FMJ`: подсказка `tooltip.tarkovcraft.ammo_upgrade_hint` (`DARK_GRAY`): можно переснарядить в `HP / AP / IGN` на Верстаке TarkovCraft.
   - **Если `AmmoTier.isTaczGunStack(stack)`:**
     - Вставить с индекса `1` строку `tooltip.tarkovcraft.gun_loaded_tier`: заряженный тир из `AmmoTier.getLoadedTierFromGun(stack)` в цвете тира!
3. В `TarkovInventoryScreen.drawCellCornerTags`: передавать `tier.getArgbColor()` вместо `TarkovUiTheme.TEXT_PRIMARY`, чтобы в сетке тир считывался мгновенно.

### 3.3. Надёжная передача тира при перезарядке (`PlayerInventoryMixin` + `AmmoReloadTracker`)
1. **Survival (`PlayerInventoryMixin`):** уже перехватывает `removeStack(slot, amount)` и `removeStack(slot)` при перезарядке TaCZ. Уточнить условие:
   - `if (!activeGun.isEmpty() && AmmoTier.isTaczGunStack(activeGun))` -> `AmmoTier.stampLoadedTierOnGun(activeGun, AmmoTier.getFromAmmoStack(extracted));`
2. **Серверный фоллбэк (`AmmoReloadTracker.tickPlayer(ServerPlayerEntity player)`):**
   - Хранит `WeakHashMap<UUID, Integer>` (или `HashMap<UUID, Integer>` с очисткой при disconnect) с последним `GunCurrentAmmoCount` для активного ствола в руке (`player.getMainHandStack()`).
   - Если `AmmoTier.isTaczGunStack(held)` и в NBT `GunCurrentAmmoCount > prevCount`:
     - В **Creative** (где TaCZ не вызывает `removeStack`): найти в инвентаре/карманах/разгрузке игрока первый `tacz:ammo` с не-FMJ тиром (или matching caliber) и проставить `AmmoTier.stampLoadedTierOnGun(held, foundTier)`.
     - В **Survival**: если `TarkovLoadedTier` ещё отсутствует, выставить `FMJ` по умолчанию.
3. При выстреле `TaCZBulletHelper.inspectDamageSource` читает `AmmoTier.getLoadedTierFromGun(held)` -> применяет `computePenetration`, `damageMultiplier`, `armorWearMultiplier` в `BodyPartHealth.applyHit`!

---

## 4. Каталог калибров и движок рецептов (`TaCZCaliberCatalog` + `TarkovWorkbenchRecipes`)

### 4.1. Все 23 баллистических калибра TaCZ (`TaCZCaliberCatalog.java`)

`CaliberGroup` определяет размер крафтовой пачки и стоимость:
- `PISTOL_SMG` (`batch = 30`): `tacz:9mm` (18), `tacz:45acp` (18), `tacz:762x25` (19), `tacz:57x28` (26), `tacz:46x30` (26), `tacz:22wmr` (14)
- `MAGNUM_PISTOL` (`batch = 20`): `tacz:357mag` (25), `tacz:50ae` (26), `tacz:500mag` (28)
- `SHOTGUN` (`batch = 16`): `tacz:12g` (15)
- `ASSAULT_RIFLE` (`batch = 24`): `tacz:545x39` (28), `tacz:556x45` (28), `tacz:58x42` (30), `tacz:300blk` (30), `tacz:762x39` (31)
- `BATTLE_SNIPER` (`batch = 16`): `tacz:68x51fury` (38), `tacz:308` (38), `tacz:762x54` (39), `tacz:30_06` (38), `tacz:45_70` (34), `tacz:792x57` (38)
- `HEAVY_SNIPER` (`batch = 10`): `tacz:338` (48), `tacz:50bmg` (54)

Плюс мягкий runtime-поиск дополнительных калибров:
- `TaCZCaliberCatalog.discoverRuntimeCalibers()` через reflection вызывает `TimelessAPI.getAllCommonAmmoIndex()`, добавляя любые неизвестные `ammoId` (кроме `40mm`/`rpg_rocket`) с `CaliberGroup.ASSAULT_RIFLE` и базовым пробитием `TaCZBulletHelper.estimateBaseCaliberPenetration(ammoId)`.
- Также при открытии верстака сервер сканирует `tacz:ammo` в инвентаре игрока и добавляет любые нестандартные `AmmoId` в `S2C_WORKBENCH_SYNC`, чтобы игрок мог переснарядить патроны **любого** стороннего gunpack'а!

### 4.2. Рецепты патронов (Крафт с нуля И Переснаряжение `FMJ -> Tier`)

Базовый множитель группы `m`: `PISTOL_SMG = 1`, `SHOTGUN = 1`, `MAGNUM_PISTOL = 1`, `ASSAULT_RIFLE = 2`, `BATTLE_SNIPER = 2`, `HEAVY_SNIPER = 3`.

**Режим 1 — Крафт с нуля (выход: `batch` патронов):**
- `FMJ`: `2*m` Iron Ingot + `2*m` Copper Ingot + `2*m` Gunpowder
- `HP`: `1*m` Iron Ingot + `3*m` Copper Ingot + `2*m` Gunpowder + `2*m` Redstone
- `AP`: `2*m` Iron Ingot + `2*m` Gold Ingot + `3*m` Gunpowder + `2*m` Redstone
- `IGN`: `3*m` Iron Ingot + `1*m` Diamond + `2*m` Blaze Powder + `3*m` Gunpowder

**Режим 2 — Переснаряжение / Апгрейд (требует `batch` обычных `FMJ` этого калибра + реагенты, выход: `batch` тировых патронов):**
- `FMJ -> HP`: `batch` FMJ + `1*m` Copper Ingot + `2*m` Redstone
- `FMJ -> AP`: `batch` FMJ + `2*m` Gold Ingot + `1*m` Gunpowder + `1*m` Redstone
- `FMJ -> IGN`: `batch` FMJ + `1*m` Diamond + `2*m` Blaze Powder + `1*m` Gunpowder
*(Если у игрока `FMJ` лежит из TaCZ Gunsmith Table или из лута, он может быстро переделать его в `HP`/`AP`/`IGN` с экономией железа и пороха!)*

### 4.3. Автогенерация рецептов для ВСЕЙ брони и снаряжения (`TarkovWorkbenchRecipes`)

Фильтры вкладки **ARMOR & GEAR** (собирают базовые `ModItems` + все динамические предметы `TarkovItemCatalog` без ручных JSON):
1. `HELMET` — все `TarkovArmorItem` со слотом `HELMET`
2. `BODY_ARMOR` — все `TarkovArmorItem` со слотом `BODY_ARMOR`
3. `FACE_MASK` — все `TarkovArmorItem` со слотом `FACE_MASK`
4. `PLATES_REPAIR` — все `TarkovArmorRepairItem` (бронеплиты и ремнаборы)
5. `RIG` — все `TacticalRigItem`
6. `BACKPACK` — все `BackpackItem`

**Детерминированный расчёт ингредиентов (`computeGearIngredients(Item item)`):**
- **`TarkovArmorItem` (по `armorClass` 0..6, слот-множитель: `BODY_ARMOR = 1.5`, `HELMET = 1.0`, `FACE_MASK = 0.75`):**
  - `Class 0`: `ceil(2*s)` Leather + `ceil(2*s)` String (для масок с `protivogaz` в ID: + `2` Glass + `2` Charcoal/Coal)
  - `Class 1`: `ceil(3*s)` Leather + `ceil(2*s)` Copper Ingot + `ceil(2*s)` Iron Ingot
  - `Class 2`: `ceil(4*s)` Leather + `ceil(4*s)` Iron Ingot + `ceil(2*s)` String
  - `Class 3`: `ceil(4*s)` Leather + `ceil(6*s)` Iron Ingot + `ceil(2*s)` Gold Ingot
  - `Class 4`: `ceil(4*s)` Leather + `ceil(8*s)` Iron Ingot + `ceil(2*s)` Diamond + `ceil(2*s)` Gold Ingot
  - `Class 5`: `ceil(4*s)` Leather + `ceil(10*s)` Iron Ingot + `ceil(4*s)` Diamond + `ceil(3*s)` Obsidian
  - `Class 6`: `ceil(4*s)` Leather + `ceil(2*s)` Iron Block + `ceil(6*s)` Diamond + `ceil(4*s)` Obsidian + `Math.max(1, Math.round(s))` Netherite Scrap
- **`TarkovArmorRepairItem` (по `repairAmount`):**
  - `Iron Ingot`: `Math.max(2, repairAmount / 10)`
  - `Copper Ingot`: `Math.max(2, repairAmount / 15)`
  - При `repairAmount >= 40`: `+ 1` Diamond (или Gold Ingot при `>= 25`)
- **`TacticalRigItem` (по `totalCells = sum(w*h)`):**
  - `Leather`: `Math.max(3, totalCells / 3)`
  - `String`: `Math.max(4, totalCells / 2)`
  - `Iron Ingot`: `Math.max(1, totalCells / 8)`
  - При `totalCells >= 18`: `+ 2` Gold Ingot
- **`BackpackItem` (по `cells = gridWidth * gridHeight`):**
  - `Leather`: `Math.max(4, cells / 4)`
  - `String`: `Math.max(4, cells / 3)`
  - `Iron Ingot`: `Math.max(1, cells / 12)`
  - При `cells >= 30`: `+ 2` Diamond

---

## 5. Блок, ScreenHandler, Сеть и UI Верстака

### 5.1. Блок `TarkovWorkbenchBlock` и регистрация
- `TarkovWorkbenchBlock extends HorizontalFacingBlock`:
  - `FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).strength(3.0f).requiresTool()`
  - `appendProperties(StateManager.Builder)` добавляет `FACING`; `getPlacementState(ItemPlacementContext)` ставит `ctx.getHorizontalPlayerFacing().getOpposite()`.
  - `onUse`: на сервере (`player instanceof ServerPlayerEntity sp`) открывает `TarkovWorkbenchScreenHandler.openForPlayer(sp, pos)`, возвращает `ActionResult.CONSUME`; на клиенте `ActionResult.SUCCESS`.
- `ModBlocks.TARKOV_WORKBENCH = registerBlock("tarkov_workbench", new TarkovWorkbenchBlock(...))` — автоматически регистрирует `BlockItem` под ID `tarkovcraft:tarkov_workbench`.
- Добавить `ModBlocks.TARKOV_WORKBENCH` в `ModItems.TARKOV_TACTICAL_GROUP`.
- В `TarkovNetworking.handleInteract` (при нажатии `[F]` на блок): если `state.getBlock() instanceof TarkovWorkbenchBlock`, вызвать `TarkovWorkbenchScreenHandler.openForPlayer(player, blockHit.getBlockPos())`.
- В `InGameHudMixin`: показывать подсказку `[F] Use Tarkov Workbench` при наведении на `TarkovWorkbenchBlock`.
- Ванильный рецепт `data/tarkovcraft/recipes/tarkov_workbench.json`:
  - Верхний ряд: `III` (`minecraft:iron_ingot`)
  - Средний ряд: `ICI` (`minecraft:iron_ingot`, `minecraft:crafting_table`, `minecraft:iron_ingot`)
  - Нижний ряд: `PCP` (`#minecraft:planks`, `minecraft:anvil` или `minecraft:iron_block` — лучше `minecraft:iron_ingot` + `#minecraft:planks` + `minecraft:smithing_table` или `minecraft:iron_block`, чтобы было доступно в ранней/средней игре: `III / PCP / P P` с `C = crafting_table`, `P = #minecraft:planks`, `B = minecraft:iron_block` внизу по центру -> 3 Iron Ingot + 1 Crafting Table + 1 Iron Block + 4 Planks!).

### 5.2. Сетевой протокол (`TarkovNetworking` + `ClientNetworkSender`)

| Пакет `Identifier` | Направление | Схема `PacketByteBuf` | Валидация и поток |
|---|---|---|---|
| `tarkovcraft:workbench_sync` (`S2C_WORKBENCH_SYNC`) | S2C | `varInt itemCount`, `[Identifier itemId, varInt count]`, `varInt fmjAmmoCount`, `[String ammoId, varInt count]`, `varInt extraCaliberCount`, `[String ammoId]` | `client.execute(() -> ClientWorkbenchCache.update(...))` |
| `tarkovcraft:workbench_craft_ammo` (`C2S_WORKBENCH_CRAFT_AMMO`) | C2S | `String ammoId` (max 64), `byte tierOrdinal`, `boolean upgradeFromFmj` | `server.execute`: проверить `currentScreenHandler instanceof TarkovWorkbenchScreenHandler wb && wb.canUse(player)`, валидировать `ammoId` (`^[a-z0-9_]+:[a-z0-9_]+$`) и `tier`, проверить и атомарно списать ресурсы через `WorkbenchInventoryHelper`, выдать патроны, звук `BLOCK_SMITHING_TABLE_USE`, переслать `S2C_WORKBENCH_SYNC`. |
| `tarkovcraft:workbench_craft_gear` (`C2S_WORKBENCH_CRAFT_GEAR`) | C2S | `Identifier targetItemId` | `server.execute`: проверить `wb.canUse(player)` и `TarkovWorkbenchRecipes.isCraftableGear(item)` (строгий whitelist!), атомарно списать ресурсы, выдать `new ItemStack(item)`, звук `BLOCK_ANVIL_USE`, переслать `S2C_WORKBENCH_SYNC`. |

> **Строгая граница клиент/сервер:** Регистрация ресивера `S2C_WORKBENCH_SYNC` и отправка `C2S_WORKBENCH_*` находятся **строго** в `com.tarkovcraft.client.ClientNetworkSender` (`@Environment(EnvType.CLIENT)`), а `TarkovNetworking` содержит только ID-константы, `sendWorkbenchSync(...)` и серверные ресиверы!

### 5.3. Инвентарь ресурсов (`WorkbenchInventoryHelper.java`)
- **Survival/Adventure:**
  - Читает и списывает ресурсы из `PlayerLoadout`:
    1. `pockets[0..5]`
    2. Все отсеки экипированной `RIG` (если `!requiresSearch || isGridRevealed`)
    3. Сетка экипированного `BACKPACK` (если `!requiresSearch || isGridRevealed`)
  - После списания вызывает `ContainerItemHelper.writeRigGrids` / `writeBackpackGrid` и `holder.tarkovcraft$syncLoadout()`.
  - Выдача результата: `loadout.tryInsertItem(result, player.getUuid())`; если не поместилось — `player.dropItem(result, false)` + сообщение `message.tarkovcraft.workbench_dropped`.
- **Creative:**
  - `hasAllIngredients` всегда `true`, `consumeIngredients` ничего не списывает, выдача через `player.getInventory().offerOrDrop(result)`.

### 5.4. Клиентский UI (`TarkovWorkbenchScreen.java`, `@Environment(EnvType.CLIENT)`)
- Наследуется от `HandledScreen<TarkovWorkbenchScreenHandler>`, полноэкранная полупрозрачная EFT-панель в палитре `TarkovUiTheme` (`BG_WORLD_OVERLAY`, `BG_PANEL`, `ACCENT_SAND`, `BORDER_PANEL`).
- **Верхняя панель:** 2 вкладки — **`AMMO`** (`tab.tarkovcraft.wb_ammo`) и **`ARMOR & GEAR`** (`tab.tarkovcraft.wb_gear`) + кнопка **`BACK [ESC]`**.
- **Вкладка `AMMO`:**
  - Левая колонка (скроллируемый список): все доступные калибры (`TaCZCaliberCatalog` + доп. калибры из `ClientWorkbenchCache`), с отображением иконки `tacz:ammo` этого калибра и базового пробития.
  - Правая колонка:
    - 4 кнопки выбора тира (`HP`, `FMJ`, `AP`, `IGN`) с цветовой подсветкой тира.
    - Переключатель режима: **`CRAFT FROM SCRATCH`** vs **`UPGRADE FMJ`** (для `HP`/`AP`/`IGN`, показывает сколько `FMJ` этого калибра есть у игрока).
    - Блок характеристик выбранного патрона: итоговое пробитие (`Pen: 44 (Class 4-5)`), урон (`x0.92`), износ брони (`x1.25`), размер пачки (`x24`).
    - Список требуемых ресурсов с иконками и счётчиками (`owned / required`, зелёный если хватает, красный если нет).
    - Кнопка **`CRAFT x24`** (активна при наличии всех ресурсов или в Creative) -> `ClientNetworkSender.sendWorkbenchCraftAmmo(...)`.
- **Вкладка `ARMOR & GEAR`:**
  - Подвкладки-фильтры: `HELMETS`, `ARMOR`, `MASKS`, `PLATES`, `RIGS`, `BACKPACKS` + строка поиска `TextFieldWidget` (фильтрация по локализованному названию).
  - Левая колонка: скроллируемый список предметов с иконкой (`TarkovUiTheme.drawScaledItem`), названием и коротким тегом (`CL.4` / `20 cells` / `5x6`).
  - Правая колонка: крупное превью предмета + ключевые ТТХ (класс/прочность/материал/зоны или сетка) + список ингредиентов (`owned / required`) + кнопка **`CRAFT`** -> `ClientNetworkSender.sendWorkbenchCraftGear(...)`.
- Клавиши: `ESC` закрывает экран; пока `searchBox.isFocused()`, нажатие `E` (inventoryKey) печатает букву в поиск, а не закрывает экран!

---

## 6. Ключи локализации (`assets-catalog` — `en_us.json` и `ru_ru.json`)

| Ключ | `en_us.json` | `ru_ru.json` |
|---|---|---|
| `block.tarkovcraft.tarkov_workbench` | `Tarkov Armor & Ammo Workbench` | `Верстак брони и патронов` |
| `screen.tarkovcraft.workbench` | `Tarkov Workbench` | `Верстак TarkovCraft` |
| `prompt.tarkovcraft.workbench` | `[%s] Open %s` | `[%s] Открыть %s` |
| `tab.tarkovcraft.wb_ammo` | `AMMO` | `ПАТРОНЫ` |
| `tab.tarkovcraft.wb_gear` | `ARMOR & GEAR` | `БРОНЯ И СНАРЯЖЕНИЕ` |
| `ui.tarkovcraft.wb_craft` | `CRAFT` | `СКРАФТИТЬ` |
| `ui.tarkovcraft.wb_craft_batch` | `CRAFT x%s` | `СКРАФТИТЬ x%s` |
| `ui.tarkovcraft.wb_mode_scratch` | `FROM SCRATCH` | `С НУЛЯ` |
| `ui.tarkovcraft.wb_mode_upgrade` | `UPGRADE FMJ` | `АПГРЕЙД FMJ` |
| `ui.tarkovcraft.wb_search` | `Search...` | `Поиск...` |
| `ui.tarkovcraft.wb_ingredients` | `REQUIRED MATERIALS` | `НЕОБХОДИМЫЕ РЕСУРСЫ` |
| `ui.tarkovcraft.wb_cat_helmets` | `HELMETS` | `ШЛЕМЫ` |
| `ui.tarkovcraft.wb_cat_armor` | `ARMOR` | `БРОНЯ` |
| `ui.tarkovcraft.wb_cat_masks` | `MASKS` | `МАСКИ` |
| `ui.tarkovcraft.wb_cat_plates` | `PLATES` | `ПЛИТЫ` |
| `ui.tarkovcraft.wb_cat_rigs` | `RIGS` | `РАЗГРУЗКИ` |
| `ui.tarkovcraft.wb_cat_backpacks` | `BACKPACKS` | `РЮКЗАКИ` |
| `ammotier.tarkovcraft.hp` | `Hollow Point (HP)` | `Экспансивные (HP)` |
| `ammotier.tarkovcraft.fmj` | `Standard (PS/FMJ)` | `Стандартные (PS/FMJ)` |
| `ammotier.tarkovcraft.ap` | `Armor-Piercing (BP/AP)` | `Бронебойные (BP/AP)` |
| `ammotier.tarkovcraft.ign` | `Ultra-AP (BS/M995)` | `Сверхбронебойные (BS/M995)` |
| `tooltip.tarkovcraft.ammo_tier` | `Ammo Tier: %s` | `Тир патрона: %s` |
| `tooltip.tarkovcraft.ammo_pen` | `Penetration: %s (%s)` | `Пробитие: %s (%s)` |
| `tooltip.tarkovcraft.ammo_dmg_wear` | `Flesh Dmg: x%s | Armor Wear: x%s` | `Урон: x%s | Износ брони: x%s` |
| `tooltip.tarkovcraft.ammo_upgrade_hint` | `Upgrade to HP/AP/IGN at Tarkov Workbench` | `Можно улучшить до HP/AP/IGN на Верстаке` |
| `tooltip.tarkovcraft.gun_loaded_tier` | `Loaded Ammo: [%s]` | `Заряжено: [%s]` |
| `message.tarkovcraft.workbench_crafted` | `Crafted: %s` | `Создано: %s` |
| `message.tarkovcraft.workbench_no_materials` | `Not enough materials` | `Недостаточно материалов` |
| `message.tarkovcraft.workbench_dropped` | `Inventory full — crafted item dropped at your feet` | `Инвентарь полон — предмет выпал под ноги` |

---

## 7. Тест-кейсы (JUnit 5) и критерии приёмки

В `src/test/java/com/tarkovcraft/workbench/AmmoAndWorkbenchTest.java`:
1. **`AmmoTier` & `TaCZCaliberCatalog`:**
   - Все 23 дефолтных калибра TaCZ присутствуют, имеют валидный формат `tacz:<id>` и `batch >= 10`.
   - `AmmoTier.createTaczAmmoStack("tacz:545x39", AmmoTier.IGN, 24)` корректно выставляет `AmmoId`, `TarkovAmmoTier = "ign"`, и `getFromAmmoStack` возвращает `IGN`; для `FMJ` `TarkovAmmoTier` не выставлен (совместимость со штатными патронами TaCZ).
   - `TaCZBulletHelper.computePenetration("tacz:545x39", tier)` строго монотонно растёт: `HP < FMJ < AP < IGN`.
2. **`TarkovWorkbenchRecipes`:**
   - Для всех 23 калибров и всех 4 тиров (`HP`/`FMJ`/`AP`/`IGN`) генерируются непустые рецепты с нуля; для `HP`/`AP`/`IGN` — корректные рецепты апгрейда из `FMJ` с меньшим числом металла/пороха.
   - Для всех 6 категорий брони/снаряжения рецепты непустые, все предметы из `ModItems` и `TarkovItemCatalog` (шлемы, жилеты, маски, плиты, разгрузки, рюкзаки) валидируются через `isCraftableGear(item) == true`, а произвольный ванильный предмет (например, `Items.DIAMOND_BLOCK`) отклоняется (`false` — защита от эксплойта!).
   - Стоимость брони растёт от Class 0 к Class 6 (Class 6 требует `Netherite Scrap`).
3. **`WorkbenchInventoryHelper` (pure `PlayerLoadout` logic):**
   - Подсчёт и атомарное списание ресурсов через карманы + разгрузку + рюкзак (включая частичное списание across pockets/rig/backpack и откат/отказ при нехватке!).
4. **Регистрация блока и ассетов:**
   - `ModBlocks.TARKOV_WORKBENCH` зарегистрирован в `Registries.BLOCK` и `Registries.ITEM`, блокстейт/модель/рецепт/лут-таблица присутствуют на classpath.

### Команда верификации (JDK 17 обязателен!)
```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.9.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build --no-daemon
```
**Критерии приёмки:**
- `compileJava`, `test` и `remapJar` проходят с кодом `0`.
- Zero-hard-dependency на TaCZ (`build.gradle` без изменений, мод собирается и работает как с TaCZ, так и без него).
- Строгая граница клиент/сервер (`ItemTooltipCallback`, `TarkovWorkbenchScreen`, `ClientWorkbenchCache`, `ClientNetworkSender` строго в `com.tarkovcraft.client.*`).
