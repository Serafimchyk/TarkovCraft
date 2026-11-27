# Спецификация: Рефакторинг UI, управления и взаимодействия в стиле Escape from Tarkov

> Платформа: Minecraft `1.20.1` Fabric, Yarn `1.20.1+build.10`, Java `17`, `environment: "*"`.
> Все Yarn-сигнатуры проверены через `javap` по `minecraft-merged-...-1.20.1+build.10-v2.jar`.
> `build.gradle`, `fabric.mod.json` и `tarkovcraft.mixins.json` **не требуют изменений**. TactEq не трогать.

---

## 0. Инварианты (без регрессий)

Обязательно сохранить все существующие механики:
- Неровные многосекционные сетки тактических разгрузок (`TacticalRigItem`), 6 карманов `1x1`, рюкзаки (`BackpackItem`), анти-рекурсия вложения контейнеров.
- Двухэтапный обыск (`SearchSession`: 1 сек раскрытие сетки -> силуэты -> поштучный обыск по редкости; снятый рюкзак обыскивается только при открытом инвентаре).
- Расчёт веса (`PlayerLoadout.calculateWeightKg()` с учётом `TarkovArmorItem.getWeightKg()`).
- Вкладка `ЗДОРОВЬЕ` (`BodyHealthHudHelper.drawDetailedHealthTab` с `ARMOR COVERAGE`, градиентом HP частей тела и `[RICOCHET]`/`[ARMOR]` в логе попаданий).
- Бейджи брони `CL.X`, прочность `cur/max` и красная рамка сломанной брони (`0/max`) в слотах экипировки.
- Полная серверная авторитарность и синхронизация слотов (`S2C_LOADOUT_SYNC`, `S2C_SCREEN_STATE`, `S2C_HEALTH_SYNC`).

---

## 1. Единая тема — `TarkovUiTheme`

**Файл:** `src/main/java/com/tarkovcraft/client/screen/TarkovUiTheme.java` (строго `client`)

### Константы палитры и прозрачностей
- `BG_WORLD_OVERLAY = 0x85000000` (полупрозрачный фон поверх живого мира; **НЕ** вызывать ванильный `this.renderBackground(context)`).
- `BG_TOP_BAR = 0xE00C0D0F`
- `BG_PANEL = 0xC80D0E10` (колонки Снаряжение / Контейнеры / Лут)
- `BG_QUICK_BAR = 0xCC0D0E10`
- `BG_SLOT = 0x99121417` (слоты экипировки поверх 3D-модели)
- `BG_CELL_EMPTY = 0x99101215`
- `BG_CELL_OCCUPIED_DEFAULT = 0xCC1C2026`
- `BG_CELL_SILHOUETTE = 0xDD090B0D`
- `BG_DIM_DRAG_SOURCE = 0x9907080A`
- `BG_TOOLTIP = 0xE60B0C0E`
- `BG_CONTEXT_MENU = 0xF0111316`
- `BG_MODAL = 0xEE0E1013`
- `BG_MODAL_HEADER = 0xF515181C`

### Рамки и акценты
- `BORDER_PANEL = 0xFF2D3035`
- `BORDER_SLOT = 0xFF32363C`
- `BORDER_CELL = 0xFF272B30`
- `BORDER_OCCUPIED = 0xFF3E454E`
- `BORDER_HOVER = 0xFF8C877B` (`1px` чёткая рамка при наведении / выделении)
- `BORDER_TOOLTIP = 0xFF3E4249`
- `BORDER_POPUP = 0xFF42464D`
- `BORDER_BROKEN = 0xFFC93838`
- `ACCENT_SAND = 0xFFD4B46A` (активная вкладка, полоса обыска, хоткей-бейдж)
- `HOVER_MENU_BG = 0xFFDCD6C8` / `HOVER_MENU_TEXT = 0xFF111214` (EFT инверсия строки ПКМ-меню)
- `DROP_VALID_FILL = 0x553B7A3B` / `DROP_VALID_BORDER = 0xFF4CAF50`
- `DROP_INVALID_FILL = 0x558F2B2B` / `DROP_INVALID_BORDER = 0xFFE53935`

### Текст и типографика (без тени: `context.drawText(..., false)`)
- `TEXT_PRIMARY = 0xFFE6E1D6` (кол-во/патроны/прочность/заголовки)
- `TEXT_SECONDARY = 0xB8CFCAC0` (короткие названия предметов в ячейках, подписи слотов)
- `TEXT_MUTED = 0xFF848B94`
- `TEXT_SCALE_SMALL = 0.72f` (в диапазоне `0.7f..0.75f`)
- `TEXT_SCALE_HEADER = 0.85f`

### Размеры и пропорции
- `TOP_BAR_H = 26`
- `QUICK_BAR_H = 30`
- `STATS_BAR_H = 18`
- `OUTER_PAD = 6`, `COL_GAP = 6`
- Доли колонок от `availW = width - OUTER_PAD * 2 - COL_GAP * 2`:
  - Левая: `31%` (`min 142px`)
  - Центральная: `36%` (`min 150px`)
  - Правая: остаток `~33%` (`min 120px`)
- Адаптивный размер ячейки сетки: `cellSize = clamp((centerW - 22) / 6, 18, 28)`.
- `DRAG_THRESHOLD_PX = 4.0`
- Слои Z (гарантия отсутствия z-fighting с 3D-игроком):
  - 3D-игрок: `z = 0` (`InventoryScreen.drawEntity` сам сдвигает на `+50`)
  - Слоты левой колонки: `z = 130`
  - Drag ghost: `z = 320`
  - Окно осмотра / сплита: `z = 420`
  - Контекстное меню ПКМ и плоский EFT-тултип: `z = 500`

### Хелперы рендера в `TarkovUiTheme`
- `drawFlatText(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color, float scale)` — через `matrices.push() -> scale(scale, scale, 1f) -> ctx.drawText(tr, text, Math.round(x / scale), Math.round(y / scale), color, false) -> matrices.pop()`.
- `drawCenteredFlatText(...)` — аналогично с выравниванием по центру.
- `drawRightAlignedFlatText(...)` — выравнивание по правому краю (для кол-ва в нижнем правом углу и короткого имени в верхнем правом).
- `drawScaledItem(DrawContext ctx, ItemStack stack, int x, int y, int w, int h)` — масштабирует `16x16` иконку до `82%` (в диапазоне `78–85%`) от `Math.min(w, h)` (с сохранением пропорций и разумным потолком `maxVisual = 34f` чтобы пиксель-арт не мылился), центрирует в `(x + w/2, y + h/2)`:
  ```java
  float targetPx = Math.min(34.0f, Math.min(w, h) * 0.82f);
  float scale = targetPx / 16.0f;
  ctx.getMatrices().push();
  ctx.getMatrices().translate(x + w * 0.5f, y + h * 0.5f, 0.0f);
  ctx.getMatrices().scale(scale, scale, 1.0f);
  ctx.drawItem(stack, -8, -8);
  ctx.getMatrices().pop();
  ```
- `itemCategoryTint(ItemStack stack)` — мягкий полупрозрачный тинт занятой ячейки:
  - `TarkovArmorItem` -> `0xCC18242C` (холодный стальной)
  - `BackpackItem` / `TacticalRigItem` -> `0xCC1E241C` (тактический хаки)
  - `AmmoTier.isTaczAmmoStack(stack)` / оружие (`tacz:*gun*`, `SwordItem`, `AxeItem`) -> `0xCC26221A` (оружейный оливково-песочный)
  - `TarkovScreenHandler.isConsumableItem(stack)` -> `0xCC182620` (медицина/провизия)
  - Прочее -> `BG_CELL_OCCUPIED_DEFAULT` (`0xCC1C2026`).
- `drawFlatTooltip(DrawContext ctx, TextRenderer tr, List<Text> lines, int mouseX, int mouseY, int screenW, int screenH)` — плоский EFT-тултип `0xE60B0C0E` с рамкой `0xFF3E4249`, `padding = 5`, привязка к границам экрана, без ванильной фиолетовой рамки, рендер на `z = 500`.

---

## 2. Полноэкранный 3-колоночный Layout (`TarkovInventoryScreen`)

**Файл:** `src/main/java/com/tarkovcraft/client/screen/TarkovInventoryScreen.java` (строго `client`)

### Геометрия экрана (`init()` / пересчёт каждый кадр из `this.width`, `this.height`)
- Полный экран: `this.x = 0; this.y = 0; this.backgroundWidth = this.width; this.backgroundHeight = this.height;`
- **Фон:** `context.fill(0, 0, this.width, this.height, TarkovUiTheme.BG_WORLD_OVERLAY)`.
- **Top Bar (`0..width`, `0..26`):**
  - Заливка `BG_TOP_BAR`, нижняя линия `1px` `BORDER_PANEL`.
  - Вкладки слева: `СНАРЯЖЕНИЕ` (`tab.tarkovcraft.gear`), `ЗДОРОВЬЕ` (`tab.tarkovcraft.health`) с полоской `2px` `ACCENT_SAND` под активной.
  - Справа: кнопка `ESC / НАЗАД` (`ui.tarkovcraft.back_esc`) -> `this.close()`.
  - Если `width >= 520`: ненавязчивая строка подсказок по центру.
- **Вкладка `ЗДОРОВЬЕ` (`activeTab == TAB_HEALTH`):**
  - Панель `BG_PANEL` на область `bodyY .. bodyY + bodyH` (с небольшими полями `OUTER_PAD`), внутри `BodyHealthHudHelper.drawDetailedHealthTab(...)` + нижняя полоса статов (`weight`, `HP`, `energy`).
- **Левая колонка — Снаряжение (`leftX, bodyY, leftW, bodyH`):**
  - Фон `BG_PANEL`, рамка `BORDER_PANEL`.
  - **Живая 3D-модель игрока:**
    - Проверенная Yarn 1.20.1 сигнатура (`javap`):
      `InventoryScreen.drawEntity(DrawContext context, int x, int y, int size, Quaternionf quaternionf, @Nullable Quaternionf quaternionf2, LivingEntity entity)`
      Дескриптор: `(Lnet/minecraft/client/gui/DrawContext;IIILorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/entity/LivingEntity;)V`.
    - Перед вызовом сохранить `bodyYaw`, `getYaw()`, `getPitch()`, `prevHeadYaw`, `headYaw` игрока `client.player`; выставить `180.0f + playerPreviewYaw` (`playerPreviewYaw` по умолчанию `18.0f`), `pitch = 0.0f`; вызвать внутри `enableScissor(leftX + 1, bodyY + 1, leftX + leftW - 1, bodyY + bodyH - STATS_BAR_H - 2)` с `new Quaternionf().rotateZ((float) Math.PI)` и `null`; в `finally` восстановить все 5 полей!
    - При зажатии ЛКМ в пустой области левой колонки (не по слоту и не по попапу) выставлять `rotatingPlayerModel = true`; в `mouseDragged` менять `playerPreviewYaw += (float) (deltaX * 1.4)`; в `mouseReleased` сбрасывать.
  - **Слоты экипировки (рендерятся поверх 3D-модели на `matrices.translate(0, 0, 130)`):**
    - Верхняя зона вокруг 3D-модели (`sideW = Math.max(42, Math.min(54, (leftW - 16) / 3))`, `sqH = Math.max(32, Math.min(40, (upperH - 12) / 2))`):
      - Слева: `HEADWEAR` (`HELMET`), ниже `BODY ARMOR` (`BODY_ARMOR`)
      - Справа: `FACE COVER` (`FACE_MASK`), ниже `TACTICAL RIG` (`RIG`)
    - Нижняя оружейная зона (под ногами модели, во всю ширину колонки):
      - `ON SLING` (`PRIMARY_1`, badge `1`)
      - `ON BACK` (`PRIMARY_2`, badge `2`)
      - Двухколоночный ряд: `HOLSTER` (`PISTOL`, badge `3`) | `SCABBARD` (`MELEE`, badge `~`)
    - Все слоты используют `BG_SLOT` (`0x99121417`), рамку `BORDER_SLOT` (`0xFF32363C`, при hover/select `BORDER_HOVER`, при сломанной броне `BORDER_BROKEN`), сохраняют бейдж `CL.X` и прочность `cur/max`.
  - **Нижняя полоса статов (`height = 18px`):** вес `%.1f kg`, здоровье `+ 435` (цвет по `hpToColor`), энергия/еда `E:%d` (`foodLevel * 5`) и гидрация/насыщение `S:%d`.
- **Центральная колонка — Контейнеры (`centerX, bodyY, centerW, bodyH`):**
  - Фон `BG_PANEL`, рамка `BORDER_PANEL`, `enableScissor` по внутренней области, скролл колёсиком (`centerScrollY`), тонкий `3px` скроллбар.
  - Секции:
    1. `TACTICAL RIG` (если надета): заголовок + кнопки `[-]/[+]` (сворачивание) и `[X]` (`СНЯТЬ` -> `ClientNetworkSender.sendQuickEquip(LOC_EQUIPMENT, RIG.ordinal(), null)`) + единая полоса Stage 1 (если не обыскана) + неровная сетка карманов разгрузки.
    2. `POCKETS`: 6 ячеек `1x1` (`cellSize x cellSize`) с метками клавиш `4..9`.
    3. `BACKPACK`: заголовок + кнопки `[-]/[+]` (сворачивание) и `[X]` (`СНЯТЬ` -> `ClientNetworkSender.sendQuickEquip(LOC_EQUIPMENT, BACKPACK.ordinal(), null)`) + слот самого рюкзака + сетка рюкзака (`GridView`, с Stage 1 / Stage 2).
- **Правая колонка — Лут (`rightX, bodyY, rightW, bodyH`):**
  - Если `handler.getMode() == MODE_OWN_INVENTORY`: **НЕ рендерить** (справа виден мир!).
  - Если открыт ящик / труп / контейнер на земле: панель `BG_PANEL`, рамка `BORDER_PANEL`, `enableScissor`, скролл (`rightScrollY`) + скроллбар.
  - В шапке: заголовок цели + кнопка `ОБЫСКАТЬ` (`ui.tarkovcraft.search_btn`), если есть хотя бы один необысканный контейнер (клик отправляет `ClientNetworkSender.sendRequestSearch(firstUnsearchedTargetId)`), а также индикатор прогресса текущего обыска.
  - Сохранить весь функционал трупа (слоты экипировки трупа + карманы + разгрузка + рюкзак) и контейнеров на земле.
- **Нижняя панель `QUICK USE`:**
  - По центру снизу (`y = height - QUICK_BAR_H - 4`), слоты `MELEE`, `1..3` (оружие), `4..9` (карманы) — кликабельны и поддерживают перетаскивание как `LOC_EQUIPMENT` / `LOC_POCKET` (адаптивный размер `qSlot = clamp((width - 60) / 11, 18, 24)`).

---

## 3. Визуальная полировка ячеек и предметов

- В занятых ячейках сетки:
  - Заливка `TarkovUiTheme.itemCategoryTint(stack)`, рамка `BORDER_OCCUPIED` (при наведении или выделении `selectedTarget` -> `1px` `BORDER_HOVER`).
  - Иконка через `TarkovUiTheme.drawScaledItem(...)` (82% от меньшей стороны, по центру).
  - Текст **без тени** (`scale = 0.72f`):
    - Нижний правый угол (`TEXT_PRIMARY = 0xFFE6E1D6`): количество (`count > 1`), либо прочность брони `cur/max` (цвет по проценту), либо бейдж тира патронов (`HP`/`AP`/`BS`).
    - Верхний правый угол (`TEXT_SECONDARY = 0xB8CFCAC0`, если `ew >= 34`): короткое имя предмета с обрезкой `textRenderer.trimToWidth(...)`.
- Тултип:
  - Плоский EFT-тултип (`TarkovUiTheme.drawFlatTooltip`), строится из `stack.getTooltip(client.player, client.options.advancedItemTooltips ? TooltipContext.ADVANCED : TooltipContext.BASIC)` (проверено `javap`: статические поля `TooltipContext.BASIC` / `TooltipContext.ADVANCED`).
  - **Отключается**, когда `drag != null && drag.active`, открыто `contextMenu`, или курсор над `inspectWindow` / `splitModal`.

---

## 4. Настоящий EFT Hold-and-Drag и хоткеи

### Hold-and-Drag (`DragState` + `mouseClicked` / `mouseDragged` / `mouseReleased`)
- `DragState`: `srcLoc, srcIdx, srcEntryId, stack, moveCount, rotated, double startX, double startY, boolean active`.
- **`mouseClicked(mx, my, button)`:**
  - Порядок перехвата: `splitModal` -> `contextMenu` -> `inspectWindow` -> Top Bar -> кнопки сворачивания/снятия/обыска -> предметы -> пустая область левой колонки (начать вращение 3D-модели).
  - По ЛКМ на предмете:
    - `Shift + LMB` -> `TarkovNetworking.sendQuickTransfer(...)` (быстрый перенос, совместимо с текущей привычкой).
    - `Ctrl + LMB` (если `count > 1`) -> старт `DragState` с `moveCount = Math.max(1, count / 2)` (перетаскивание половины).
    - `Alt + LMB` (если `count > 1`) -> старт `DragState` с `moveCount = 1` (перетаскивание 1 шт.).
    - Обычный ЛКМ -> старт `DragState` с `moveCount = 0`, `active = false`.
- **`mouseDragged(mx, my, button, dx, dy)`:**
  - Если `rotatingPlayerModel` -> `playerPreviewYaw += (float)(dx * 1.4)`.
  - Если `inspectWindow` тащится за заголовок -> обновить позицию окна.
  - Если `splitModal` тащит слайдер -> обновить значение сплита.
  - Если `drag != null && !drag.active` и `Math.hypot(mx - drag.startX, my - drag.startY) > 4.0` -> `drag.active = true`!
- **Во время активного перетаскивания (`drag != null && drag.active`):**
  - Исходная ячейка/слот затемняется оверлеем `BG_DIM_DRAG_SOURCE`.
  - Под курсором на `z = 320` рисуется ghost-превью (`ItemSize` с учётом `drag.rotated`).
  - Подсветка цели под курсором: клиентская проверка валидности (учитывает границы сетки, перекрытие с другими `ViewEntry` кроме самого `srcEntryId`, `gridRevealed`, `EquipmentSlotType.accepts`, `canPlaceInPocket`, возможность стака):
    - Валидно -> `DROP_VALID_FILL` (`0x553B7A3B`) + `DROP_VALID_BORDER`
    - Конфликт -> `DROP_INVALID_FILL` (`0x558F2B2B`) + `DROP_INVALID_BORDER`
  - Нажатие `R` (`GLFW.GLFW_KEY_R`) поворачивает `drag.rotated = !drag.rotated`.
- **`mouseReleased(mx, my, button)`:**
  - Сброс `rotatingPlayerModel`, перетаскивания `inspectWindow` и слайдера `splitModal`.
  - Если `drag != null`:
    - Если `!drag.active` (быстрый клик `<= 4px`): запомнить `selectedTarget` (визуальное выделение рамкой `BORDER_HOVER`), предмет не перемещается и не выкидывается.
    - Если `drag.active`:
      - Если под курсором валидная целевая область -> отправить `TarkovNetworking.sendMoveItem(...)`.
      - Если цель невалидна или курсор вне панелей -> **НЕ выкидывать предмет**, просто отменить drag (возврат в исходный слот).
    - `drag = null`.

### Хоткеи (`keyPressed`)
1. `ESC` (`GLFW_KEY_ESCAPE`): закрывает по приоритету `splitModal` -> `contextMenu` -> `inspectWindow` -> экран.
2. `R` (`GLFW_KEY_R`) при `drag != null && drag.active`: поворот предмета.
3. При наведении на предмет (без активного drag и без открытого `splitModal`):
   - **`Ctrl + F`** (`Screen.hasControlDown() && (keyCode == GLFW.GLFW_KEY_F || TarkovCraftClient.interactKey.matchesKey(keyCode, scanCode))`): быстрая экипировка/снятие -> `ClientNetworkSender.sendQuickEquip(...)`. *(Проверять ДО обычного `F`!)*
   - **`F`** (без `Ctrl`, `keyCode == GLFW.GLFW_KEY_F || TarkovCraftClient.interactKey.matchesKey(keyCode, scanCode)`): быстрый перенос инвентарь <-> лут -> `TarkovNetworking.sendQuickTransfer(...)`.
   - **`Delete`** (`GLFW.GLFW_KEY_DELETE`): выбросить весь стек на землю -> `TarkovNetworking.sendMoveItem(..., LOC_DROP, 0, 0, 0, false, 0)`.
   - **`Q`** (`client.options.dropKey`): выбросить 1 шт. (`Ctrl+Q` весь стек), без конфликтов.
4. Клавиша инвентаря (`client.options.inventoryKey`): закрывает экран (если не перехвачено).

---

## 5. Контекстное меню ПКМ, окно осмотра, сплит-модал и сетевые пакеты

### 5.1. Контекстное меню ПКМ
- Внешний вид: фон `BG_CONTEXT_MENU` (`0xF0111316`), рамка `BORDER_POPUP` (`0xFF42464D`), строки высотой `17px`, ширина по содержимому (`min 124px`), привязка к границам экрана.
- Hover-инверсия: фон строки `HOVER_MENU_BG` (`0xFFDCD6C8`), текст `HOVER_MENU_TEXT` (`0xFF111214`), без тени.
- Динамический состав пунктов (только применимые к предмету):
  1. `ИСПОЛЬЗОВАТЬ` (`ctx.tarkovcraft.use`) — только если `TarkovScreenHandler.isConsumableItem(stack)` -> `ClientNetworkSender.sendUseItem(...)`.
  2. `ЭКИПИРОВАТЬ` (`ctx.tarkovcraft.equip`) / `СНЯТЬ` (`ctx.tarkovcraft.unequip`) — для `LOC_EQUIPMENT` показывает `СНЯТЬ`; для остальных показывает `ЭКИПИРОВАТЬ`, если предмет подходит хотя бы к одному слоту экипировки (броня/шлем/маска/разгрузка/рюкзак/оружие/ближний бой) -> `ClientNetworkSender.sendQuickEquip(...)`.
  3. `ОБЫСКАТЬ` (`ctx.tarkovcraft.search`) / `ОТКРЫТЬ` (`ctx.tarkovcraft.open`) — для `BackpackItem`/`TacticalRigItem`: если не обыскан -> `ОБЫСКАТЬ` (`ClientNetworkSender.sendRequestSearch(...)`); если надет и обыскан -> `ОТКРЫТЬ` (разворачивает секцию и скроллит к ней).
  4. `РАЗДЕЛИТЬ` (`ctx.tarkovcraft.split`) — только если `stack.getCount() > 1` -> открывает `TarkovSplitModal` (слайдер `1 .. count-1`, быстрая кнопка `50%`, `OK` / `Отмена`) -> `ClientNetworkSender.sendSplitStack(...)`.
  5. `ОСМОТРЕТЬ` (`ctx.tarkovcraft.inspect`) — всегда -> открывает `TarkovInspectWindow`.
  6. `ВЫБРОСИТЬ` (`ctx.tarkovcraft.discard`) — всегда -> `TarkovNetworking.sendMoveItem(..., LOC_DROP, 0, 0, 0, false, 0)`.

### 5.2. Окно осмотра — `TarkovInspectWindow.java` (client)
- Перетаскиваемое окно поверх инвентаря (`z = 420`), `232 x 192` (clamped to screen), заголовок `20px` с названием предмета и кнопкой `[X]`.
- Внутри:
  - Крупный рендер предмета (`70x70` рамка, `scale = 3.0f` -> `48x48px`) + габарит `WxH`.
  - Справа: вес (`kg`), количество, габариты (`ItemSize`), редкость (`ItemRarityRegistry.getTier`).
  - Внизу: подробные статы (для брони — класс, материал, прочность, рикошет, закрытые и открытые зоны; для контейнеров — сетка/карманы; для еды — голод/насыщение; плюс остальные строки тултипа).

### 5.3. Сплит-модал — `TarkovSplitModal.java` (client)
- Компактное окно (`164 x 84`) поверх инвентаря (`z = 420`), центрируется около курсора с привязкой к экрану.
- Показывает название предмета, интерактивный слайдер `1 .. max-1`, текущее значение (`split / remain`), кнопки `50%`, `OK` (`ui.tarkovcraft.confirm`) и `Отмена` (`ui.tarkovcraft.cancel`).

### 5.4. Сетевой протокол (строгая граница клиент/сервер)

**Новые `Identifier` и серверные обработчики в `src/main/java/com/tarkovcraft/network/TarkovNetworking.java`:**
*(Новых клиентских импортов в `TarkovNetworking` НЕ добавлять! Все клиентские отправки вынести в `src/main/java/com/tarkovcraft/client/ClientNetworkSender.java`.)*

| Пакет | Направление | Схема `PacketByteBuf` | Серверная валидация и действие (`server.execute`) |
| :--- | :--- | :--- | :--- |
| `tarkovcraft:quick_equip` (`C2S_QUICK_EQUIP`) | C2S | `byte srcLoc`, `VarInt srcIdx`, `boolean hasId`, `[UUID srcEntryId]` | `player.currentScreenHandler instanceof TarkovScreenHandler tsh` -> `tsh.handleQuickEquip(player, ...)` |
| `tarkovcraft:use_item` (`C2S_USE_ITEM`) | C2S | `byte srcLoc`, `VarInt srcIdx`, `boolean hasId`, `[UUID srcEntryId]` | `tsh.handleUseItem(player, ...)` |
| `tarkovcraft:split_stack` (`C2S_SPLIT_STACK`) | C2S | `byte srcLoc`, `VarInt srcIdx`, `boolean hasId`, `[UUID srcEntryId]`, `VarInt splitAmount` | `tsh.handleSplitStack(player, ..., splitAmount)` |
| `tarkovcraft:request_search` (`C2S_REQUEST_SEARCH`) | C2S | `UUID targetId` | `tsh.handleRequestSearch(player, targetId)` |

#### Безопасный серверный контракт `handleUseItem` (без дюпов и крашей)
1. Проверить `canUse(serverPlayer)`, `!serverPlayer.isSpectator()`, `serverPlayer.isAlive()`.
2. `ItemStack peek = peekSource(loadout, playerId, srcLoc, srcIdx, srcEntryId)` (автоматически блокирует необысканные предметы!).
3. Проверить `isConsumableItem(peek)`:
   - `stack.isFood() || stack.getUseAction() == UseAction.DRINK || stack.getUseAction() == UseAction.EAT`.
4. Для еды проверить: `FoodComponent fc = peek.getItem().getFoodComponent();` — разрешить если `fc == null || fc.isAlwaysEdible() || serverPlayer.canConsume(false) || health.getTotalHp() < BodyPart.TOTAL_MAX_HP`.
5. **Атомарно извлечь ровно 1 шт. ДО `finishUsing`:**
   - `ItemStack single = extractSource(loadout, srcLoc, srcIdx, srcEntryId, 1);`
   - Если `single.isEmpty()` -> `return`.
   - Запомнить `Item originalItem = single.getItem(); UseAction useAction = single.getUseAction();`.
6. Вызвать `ItemStack remainder = single.finishUsing(serverPlayer.getWorld(), serverPlayer);` (проверено `javap`: `ItemStack.finishUsing(World, LivingEntity) -> ItemStack`).
7. Анти-дюп остатка: если `!remainder.isEmpty() && !serverPlayer.isCreative() && remainder.getItem() != originalItem`:
   - Положить `remainder` (например `GLASS_BOTTLE`, `BOWL`, `BUCKET`) через `loadout.tryInsertItem(remainder, playerId)`; если нет места -> `serverPlayer.dropItem(remainder, false)`.
8. Лечение `BodyPartHealth` для лечебных предметов:
   - `Items.ENCHANTED_GOLDEN_APPLE` -> `health.healEvenly(80.0f)`
   - `Items.GOLDEN_APPLE` -> `health.healEvenly(25.0f)`
   - Зелье с `StatusEffects.INSTANT_HEALTH` (`PotionUtil.getPotionEffects`) -> `health.healEvenly(35.0f * (amplifier + 1))`
   - Прочая еда -> если `fc != null` и `health.getTotalHp() < BodyPart.TOTAL_MAX_HP`, лёгкое восстановление `health.healEvenly(Math.max(2.0f, fc.getHunger() * 1.5f))`.
   - Обновить ванильное HP пропорционально `Math.max(1.0f, health.getTotalRatio() * serverPlayer.getMaxHealth())` и вызвать `holder.tarkovcraft$syncBodyHealth()`.
9. Звук: `SoundEvents.ENTITY_GENERIC_DRINK` для `DRINK`, иначе `SoundEvents.ENTITY_PLAYER_BURP`. Вызвать `markExternalDirty()` и `syncToClient(serverPlayer)`.

#### Безопасный серверный контракт `handleSplitStack` (без схлопывания обратно в себя)
1. В `GridContainer.java` добавить чистый метод `public @Nullable GridEntry tryPlaceInFreeCell(ItemStack stack)`:
   - В отличие от `tryAutoPlace`, **НЕ** пытается мёржить в существующие стеки — ищет только свободную ячейку (сначала без поворота, затем с поворотом, проверяя `canPlace`), вставляет новый `GridEntry` и возвращает его (или `null`, если места нет).
2. В `TarkovScreenHandler.handleSplitStack(serverPlayer, srcLoc, srcIdx, srcEntryId, splitAmount)`:
   - Проверить `canUse(serverPlayer)`, получить `peek = peekSource(...)`.
   - Проверить `peek.getCount() > 1 && splitAmount >= 1 && splitAmount < peek.getCount()`.
   - Проверить наличие свободного места (в той же сетке через `tryPlaceInFreeCell`, иначе в свободный карман `Pockets`, иначе в `Rig` через `tryPlaceInFreeCell`, иначе в `Backpack` через `tryPlaceInFreeCell`) **ДО** финальной потери предмета — если нигде нет свободной ячейки, не извлекать (отправить `message.tarkovcraft.no_space`).
   - При успехе пометить новый `GridEntry` как `discovered` в `ContainerSearchMemory` (если контейнер требует поиска), вызвать `markExternalDirty()` и `syncToClient(serverPlayer)`.

#### Приоритизация обыска (`SearchSession.prioritizeTarget`)
- В `SearchSession.java` добавить поле `@Nullable private UUID prioritizedTargetId;` и метод `public void prioritizeTarget(@Nullable UUID targetId)`.
- В `selectActiveTarget(...)`: если `prioritizedTargetId != null`, сначала искать совпадение среди `externalTargets` и (если `ownInventoryOpen`) `ownTargets` с `!t.isFullySearched(playerId)`; если найден — вернуть его, иначе сбросить `prioritizedTargetId = null` и выполнить стандартный обход очереди.

#### Разделение `handleQuickTransfer` (`F`) vs `handleQuickEquip` (`Ctrl+F`)
- `handleQuickTransfer` (`F` / `Shift+LMB`):
  - Из внешнего источника (`LOC_EXTERNAL_GRID`, `LOC_CORPSE_*`) -> перенос в своё хранилище (`tryInsertItem`), а если не поместилось или это рюкзак/разгрузка при пустом слоте -> `trySmartPickup`.
  - Из своего хранилища (`POCKET` / `RIG` / `BACKPACK`) при открытом внешнем контейнере (`MODE_LOOT_BLOCK` / `MODE_GROUND_ITEM`) -> перенос во внешний контейнер; если открыт только свой инвентарь -> перенос между рюкзаком <-> разгрузкой/карманами (или экипировка если пустой слот снаряжения).
  - Из своей экипировки (`LOC_EQUIPMENT`) -> снятие в хранилище (`Pockets -> Rig -> Backpack`).
- `handleQuickEquip` (`Ctrl+F` / ПКМ `ЭКИПИРОВАТЬ`/`СНЯТЬ`):
  - Если `srcLoc == LOC_EQUIPMENT` -> снять в `Pockets -> Rig -> Backpack` (временно очистив слот, чтобы рюкзак не попал сам в себя).
  - Иначе -> найти подходящий пустой слот экипировки (`findEmptyEquipSlotFor`) и надеть напрямую (вкл. снятие рюкзака/разгрузки с трупа прямо на себя).

---

## 6. Ключи локализации (`en_us.json` и `ru_ru.json`)

Синхронно добавить в оба файла (UTF-8 без BOM, без дубликатов, существующие 91 ключ не ломать):

| Ключ | `en_us.json` | `ru_ru.json` |
| :--- | :--- | :--- |
| `ui.tarkovcraft.back_esc` | `BACK [ESC]` | `НАЗАД [ESC]` |
| `ui.tarkovcraft.search_btn` | `SEARCH` | `ОБЫСКАТЬ` |
| `ui.tarkovcraft.searching` | `SEARCHING...` | `ОБЫСК...` |
| `ui.tarkovcraft.unsearched` | `UNSEARCHED` | `НЕ ОБЫСКАНО` |
| `ui.tarkovcraft.quick_use` | `QUICK USE` | `БЫСТРЫЙ ДОСТУП` |
| `ui.tarkovcraft.pockets` | `POCKETS` | `КАРМАНЫ` |
| `ui.tarkovcraft.backpack` | `BACKPACK` | `РЮКЗАК` |
| `ui.tarkovcraft.rig` | `TACTICAL RIG` | `РАЗГРУЗКА` |
| `ui.tarkovcraft.confirm` | `OK` | `ОК` |
| `ui.tarkovcraft.cancel` | `Cancel` | `Отмена` |
| `ui.tarkovcraft.split_title` | `SPLIT STACK` | `РАЗДЕЛИТЬ` |
| `ui.tarkovcraft.inspect_size` | `Size: %sx%s` | `Размер: %sx%s` |
| `ui.tarkovcraft.inspect_weight` | `Weight: %s kg` | `Вес: %s кг` |
| `ui.tarkovcraft.inspect_rarity` | `Rarity: %s` | `Редкость: %s` |
| `ui.tarkovcraft.inspect_count` | `Quantity: %s` | `Количество: %s` |
| `ui.tarkovcraft.inspect_food` | `Energy: +%s  Sat: +%s` | `Энергия: +%s  Насыщ.: +%s` |
| `ctx.tarkovcraft.use` | `Use` | `Использовать` |
| `ctx.tarkovcraft.equip` | `Equip` | `Экипировать` |
| `ctx.tarkovcraft.open` | `Open` | `Открыть` |
| `ctx.tarkovcraft.search` | `Search` | `Обыскать` |
| `ctx.tarkovcraft.split` | `Split...` | `Разделить...` |
| `ctx.tarkovcraft.inspect` | `Inspect` | `Осмотреть` |
| `ctx.tarkovcraft.discard` | `Discard` | `Выбросить` |

---

## 7. План JUnit 5 тестов (`src/test/java/**`)

1. **`GridContainerTest.java`**:
   - `tryPlaceInFreeCellDoesNotMergeIntoExistingStack`: разместить стек 16 яблок -> вызвать `tryPlaceInFreeCell` для 8 яблок -> в контейнере **2** отдельных `GridEntry` (16 и 8), а не один стек 24.
   - `tryPlaceInFreeCellRotatesAndFailsWhenFull`: предмет `2x1` встаёт повёрнутым в свободный столбец `1x2`; при заполненной сетке возвращает `null`.
2. **`SearchSessionTest.java`**:
   - `prioritizeTargetSwitchesToRequestedContainerAndResumesQueue`: два необысканных контейнера (`pockets` и `backpack`) -> вызов `prioritizeTarget(backpackId)` заставляет `SearchSession` сначала обыскать `backpack`, а после завершения автоматически вернуться к `pockets`.
3. **`src/test/java/com/tarkovcraft/screen/TarkovInventoryLogicTest.java`** (новый файл):
   - `isConsumableItemIdentifiesFoodAndDrinksOnly`: `Items.BREAD`, `Items.GOLDEN_APPLE`, `Items.POTION`, `Items.MILK_BUCKET`, `Items.HONEY_BOTTLE` -> `true`; `ModItems.PACA_ARMOR`, `ModItems.BERKUT_BACKPACK`, `Items.IRON_SWORD`, `Items.COBBLESTONE`, `ItemStack.EMPTY` -> `false`.
   - `canEquipItemMatchesExpectedSlots`: проверка соответствия брони/рюкзака/разгрузки/меча слотам экипировки без регрессии отказа ванильной брони.

---

## 8. Команда проверки и критерии приёмки

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.9.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build --no-daemon
```

Критерии приёмки:
- `.\gradlew.bat build --no-daemon` зелёный (все старые 24 + новые JUnit-тесты пройдены).
- Ноль клиентских импортов в common/server-классах (`TarkovNetworking`, `TarkovScreenHandler`, `GridContainer`, `SearchSession`).
- Все 5 разделов рефакторинга реализованы полностью, без регрессий по броне, здоровью, обыску и неровным сеткам.
- `en_us.json` и `ru_ru.json` синхронны и валидны.
