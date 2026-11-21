# Спецификация: Фикс конфликтов биндов (TaCZ / F), 3D OBB-хитбоксы попаданий и отладочный рендер (F3+B / F8)

> **Статус:** Утверждённая архитектурная спецификация (`architect`, `gemini-4-neon-high`)  
> **Стек:** Minecraft `1.20.1`, Fabric Loader `0.16.10`, Fabric API `0.92.12+1.20.1`, Yarn `1.20.1+build.10`, Java `17`  
> **Среда (`fabric.mod.json`):** `environment: "*"` — строгая изоляция клиентских классов (`com.tarkovcraft.client.*` и клиентских миксинов).  
> **Изменения Mixin-конфига:** `"KeyBindingMixin"` уже добавлен `architect` строго в секцию `"client"` файла `src/main/resources/tarkovcraft.mixins.json`. `fabric.mod.json` и `build.gradle` не меняются.

---

## 0. Проверенные Yarn 1.20.1 & Fabric API сигнатуры (`javap`)

Все сигнатуры проверены напрямую по ремапленному JAR Minecraft `1.20.1+build.10` и `fabric-rendering-v1-3.0.10+54550fb677.jar`:

- `net.minecraft.client.option.KeyBinding`:
  - `private static final Map<String, KeyBinding> KEYS_BY_ID;`
  - `private static final Map<InputUtil.Key, KeyBinding> KEY_TO_BINDINGS;`
  - `private InputUtil.Key boundKey;`
  - `private int timesPressed;`
  - `public KeyBinding(String translationKey, InputUtil.Type type, int code, String category)` (3-арг. конструктор делегирует в него)
  - `public static void onKeyPressed(InputUtil.Key key)`
  - `public static void setKeyPressed(InputUtil.Key key, boolean pressed)`
  - `public static void updateKeysByCode()`
  - `public void setPressed(boolean pressed)`
- `net.minecraft.client.util.InputUtil`:
  - `public static final InputUtil.Key UNKNOWN_KEY;`
- `net.minecraft.entity.player.PlayerInventory`:
  - `public ItemStack getStack(int slot)`
  - `public ItemStack removeStack(int slot, int amount)`
  - `public ItemStack removeStack(int slot)` **(1-арг. — ранее не был перехвачен!)**
  - `public void setStack(int slot, ItemStack stack)`
  - `public boolean insertStack(ItemStack stack)`
  - `public boolean insertStack(int slot, ItemStack stack)` **(2-арг. — ранее не был перехвачен; `offer`/`offerOrDrop` вызывают его напрямую!)**
- `net.minecraft.entity.Entity` / `net.minecraft.entity.LivingEntity`:
  - `public double prevX, prevY, prevZ;`
  - `public final Vec3d getLerpedPos(float delta)`
  - `public Vec3d getPos()`, `public Vec3d getVelocity()`, `public final Vec3d getEyePos()`, `public final Vec3d getRotationVec(float tickDelta)`
  - `public float bodyYaw, prevBodyYaw, headYaw, prevHeadYaw;`
  - `public float getBodyYaw()`, `public float getHeadYaw()`
- `net.minecraft.util.math.MathHelper`:
  - `public static float lerpAngleDegrees(float delta, float start, float end)`
- `net.minecraft.client.render.RenderLayer`:
  - `public static RenderLayer getLines()`
- `net.minecraft.client.render.VertexConsumer`:
  - `default VertexConsumer vertex(Matrix4f matrix, float x, float y, float z)`
  - `default VertexConsumer color(float red, float green, float blue, float alpha)`
  - `default VertexConsumer normal(Matrix3f matrix, float x, float y, float z)`
  - `void next()`
- `net.minecraft.client.render.entity.EntityRenderDispatcher`:
  - `public boolean shouldRenderHitboxes()`
- `net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents`:
  - `WorldRenderEvents.AFTER_ENTITIES` (`@FunctionalInterface AfterEntities { void afterEntities(WorldRenderContext context); }`)
- `net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext`:
  - `MatrixStack matrixStack()`, `float tickDelta()`, `Camera camera()`, `ClientWorld world()`, `VertexConsumerProvider consumers()`
- `net.minecraft.client.render.Camera`:
  - `public Vec3d getPos()`

---

## 1. Фикс конфликтов биндов (`KeyBindingMixin`) и аудит TaCZ (`PlayerInventoryMixin`)

### 1.1 Клиентский миксин `com.tarkovcraft.mixin.KeyBindingMixin`

**Зарегистрирован в:** `src/main/resources/tarkovcraft.mixins.json` -> `"client"` (`@Environment(EnvType.CLIENT)`).

**Проблема:** ванильный `KeyBinding.KEY_TO_BINDINGS` — `Map<InputUtil.Key, KeyBinding>` (1-к-1). В `updateKeysByCode()` последняя по порядку клавиша затирает предыдущую на той же кнопке (`swapOffhand` на `F` затирает `tarkovcraft.interact`; `tacz.shoot` конфликтует с `attack` на LMB; `tacz.aim` с `use` на RMB и т.д.).

**Контракт реализации (неотменяемый `@At("TAIL")` паттерн — 0 конфликтов с Fabric API и TaCZ):**
- `@Mixin(KeyBinding.class)`
- `@Shadow @Final private static Map<String, KeyBinding> KEYS_BY_ID;`
- `@Shadow @Final private static Map<InputUtil.Key, KeyBinding> KEY_TO_BINDINGS;`
- `@Shadow private InputUtil.Key boundKey;`
- `@Shadow private int timesPressed;`
- `@Unique private static final Map<InputUtil.Key, List<KeyBinding>> TARKOV_KEY_TO_BINDINGS = new HashMap<>();`
- **Хелпер `@Unique private static void tarkovcraft$registerKey(KeyBinding kb)`:**
  - `InputUtil.Key k = ((KeyBindingMixin) (Object) kb).boundKey;`
  - Если `k == null || k.equals(InputUtil.UNKNOWN_KEY)` → `return;`
  - `List<KeyBinding> list = TARKOV_KEY_TO_BINDINGS.computeIfAbsent(k, ignored -> new ArrayList<>(2));`
  - `if (!list.contains(kb)) list.add(kb);`
- **Инжекция 1 — конструктор (чтобы бинды, созданные до/после `updateKeysByCode`, всегда были в мультимапе):**
  - `@Inject(method = "<init>(Ljava/lang/String;Lnet/minecraft/client/util/InputUtil$Type;ILjava/lang/String;)V", at = @At("TAIL"))`
  - Вызвать `tarkovcraft$registerKey((KeyBinding) (Object) this);`.
- **Инжекция 2 — пересборка мультимапы при смене раскладки:**
  - `@Inject(method = "updateKeysByCode", at = @At("TAIL"))`
  - `TARKOV_KEY_TO_BINDINGS.clear();`
  - Для каждого `KeyBinding kb : KEYS_BY_ID.values()` вызвать `tarkovcraft$registerKey(kb);`.
- **Инжекция 3 — рассылка `onKeyPressed` всем остальным биндам на клавише (без двойного инкремента!):**
  - `@Inject(method = "onKeyPressed", at = @At("TAIL"))`
  - Если `key == null || key.equals(InputUtil.UNKNOWN_KEY)` → `return;`
  - `List<KeyBinding> list = TARKOV_KEY_TO_BINDINGS.get(key);`
  - Если `list == null || list.size() <= 1` → `return;`
  - `KeyBinding vanillaHandled = KEY_TO_BINDINGS.get(key);`
  - Для каждого `KeyBinding kb : list`:
    - `if (kb != vanillaHandled) { ((KeyBindingMixin) (Object) kb).timesPressed++; }`
- **Инжекция 4 — рассылка `setKeyPressed` всем остальным биндам на клавише:**
  - `@Inject(method = "setKeyPressed", at = @At("TAIL"))`
  - Если `key == null || key.equals(InputUtil.UNKNOWN_KEY)` → `return;`
  - `List<KeyBinding> list = TARKOV_KEY_TO_BINDINGS.get(key);`
  - Если `list == null || list.size() <= 1` → `return;`
  - `KeyBinding vanillaHandled = KEY_TO_BINDINGS.get(key);`
  - Для каждого `KeyBinding kb : list`:
    - `if (kb != vanillaHandled) { kb.setPressed(pressed); }`

**Почему `@At("TAIL")` лучше `@At("HEAD") + ci.cancel()`:**
1. Ванильная запись `KEY_TO_BINDINGS` сохраняет стандартное поведение, любые сторонние `@Inject`/`@Redirect` (TaCZ / Fabric) не блокируются.
2. Каждый бинд на клавише срабатывает **ровно 1 раз** (один — ванильно, остальные `N-1` — через наш цикл).
3. `MinecraftClientMixin.tarkovcraft$consumeSwapHandsKey` по-прежнему гасит `options.swapHandsKey.wasPressed()` в Survival/Adventure, а `TarkovCraftClient` получает `interactKey.wasPressed()` на `F` даже если ванильный `swapOffhand` остался на `F`.

### 1.2 Аудит `PlayerInventoryMixin` для перезарядки TaCZ

В `com.tarkovcraft.mixin.PlayerInventoryMixin` обнаружены 2 незакрытых метода `PlayerInventory`, которые используют TaCZ Refabricated / Fabric Transfer API / `offerOrDrop`:
1. **`removeStack(int slot)` (1-арг.):**
   - Добавить `@Inject(method = "removeStack(I)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)`:
   - В `!player.isCreative() && player instanceof PlayerLoadoutHolder holder && slot >= 0 && slot < 36`:
     - Извлечь полный стек из соответствующего слота (через `getStack(slot)` / `removeStack(slot, Integer.MAX_VALUE)`), поставить `AmmoTier` при извлечении патронов TaCZ, синхронизировать loadout на сервере, вернуть извлечённый стек через `cir.setReturnValue(...)`.
2. **`insertStack(int slot, ItemStack stack)` (2-арг.):**
   - Добавить `@Inject(method = "insertStack(ILnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)`:
   - В `!player.isCreative() && player instanceof PlayerLoadoutHolder holder`:
     - Если `stack.isEmpty()` → `cir.setReturnValue(false); return;`.
     - Если `slot >= 0 && slot < 36`: попробовать положить в конкретный слот если он пуст/совместим (или сразу через `loadout.trySmartPickup(stack, player.getUuid())` при `slot == -1` или если конкретный слот занят/неприменим).
     - Если вставлено → `holder.tarkovcraft$syncLoadout()`, `cir.setReturnValue(true)`.
   - Это закрывает потерю возвращаемых патронов/магазинов TaCZ при разрядке или `player.getInventory().offerOrDrop(...)` в скрытый ванильный массив `main`!

---

## 2. Единый источник истины 3D OBB (`HitZoneBoxes`), рейкаст попаданий и пробой брони TaCZ

### 2.1 Новый класс `com.tarkovcraft.health.HitZoneBoxes` (pure common — БЕЗ клиентских импортов!)

Хранит геометрию всех 13 зон `HitZone` как повернутые 3D-параллелепипеды (OBB) в локальной системе координат жертвы:
- **Оси Minecraft Yaw (`yawDeg`):**
  - `yawRad = Math.toRadians(yawDeg)`
  - `forward = (-sin(yawRad), 0, cos(yawRad))` (`yaw=0` -> South `+Z`)
  - `right = (-fwdZ, 0, fwdX)` (`yaw=0` -> West `-X`, т.е. правая сторона игрока `> 0`, левая `< 0`)
  - `up = (0, 1, 0)`

**Публичные структуры:**
```java
public record ZoneBox(
	HitZone zone,
	Vec3d origin,
	float yawDeg,
	double minRight, double maxRight,
	double minY, double maxY,
	double minFwd, double maxFwd
) {
	/** Kay-Kajiya 3-axis OBB slab intersection. Returns t >= 0 (entry distance), or Double.NaN on miss. */
	public double intersectRay(Vec3d rayOrigin, Vec3d rayDir, double maxDist);

	/** Returns the 8 world-space vertices of this OBB in standard box order (0..3 bottom, 4..7 top). */
	public Vec3d[] getWorldCorners();
}

public record RayHit(HitZone zone, double distance, Vec3d hitPos) {}
```

- Константа рёбер:
  ```java
  public static final int[][] EDGE_INDICES = {
      {0, 1}, {1, 2}, {2, 3}, {3, 0}, // bottom face
      {4, 5}, {5, 6}, {6, 7}, {7, 4}, // top face
      {0, 4}, {1, 5}, {2, 6}, {3, 7}  // vertical pillars
  };
  ```
- **Точная таблица OBB (`compute(Vec3d feetPos, float height, float width, float bodyYawDeg, float headYawDeg)`):**
  Пусть `h = Math.max(0.4, height)`, `ws = Math.max(0.5, width / 0.60)`:

  | # | `HitZone` | Yaw | `minR .. maxR` (`* ws`) | `minY .. maxY` (`* h`) | `minF .. maxF` (`* ws`) | Назначение |
  | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
  | 1 | `HEAD_TOP` | `headYaw` | `-0.23 .. +0.23` | `0.92 .. 1.02` | `-0.23 .. +0.23` | Темя / макушка |
  | 2 | `HEAD_TOP` | `headYaw` | `-0.22 .. +0.22` | `0.78 .. 0.92` | `-0.23 .. +0.02` | Затылок |
  | 3 | `HEAD_EYES` | `headYaw` | `-0.19 .. +0.19` | `0.85 .. 0.92` | `+0.02 .. +0.24` | Лицо — глаза (выступает спереди) |
  | 4 | `HEAD_JAWS` | `headYaw` | `-0.19 .. +0.19` | `0.78 .. 0.85` | `+0.02 .. +0.24` | Лицо — челюсть |
  | 5 | `HEAD_EARS` | `headYaw` | `-0.25 .. -0.19` | `0.81 .. 0.92` | `-0.12 .. +0.12` | Левое ухо (выступает сбоку) |
  | 6 | `HEAD_EARS` | `headYaw` | `+0.19 .. +0.25` | `0.81 .. 0.92` | `-0.12 .. +0.12` | Правое ухо |
  | 7 | `NECK` | `bodyYaw` | `-0.13 .. +0.13` | `0.74 .. 0.78` | `-0.13 .. +0.13` | Шея / горло |
  | 8 | `THORAX` | `bodyYaw` | `-0.20 .. +0.20` | `0.54 .. 0.74` | `-0.15 .. +0.15` | Грудь / спина |
  | 9 | `STOMACH` | `bodyYaw` | `-0.20 .. +0.20` | `0.40 .. 0.54` | `-0.15 .. +0.15` | Живот |
  | 10 | `ARMPIT_LEFT` | `bodyYaw` | `-0.23 .. -0.17` | `0.60 .. 0.72` | `-0.14 .. +0.14` | Левая подмышка |
  | 11 | `ARMPIT_RIGHT` | `bodyYaw` | `+0.17 .. +0.23` | `0.60 .. 0.72` | `-0.14 .. +0.14` | Правая подмышка |
  | 12 | `LEFT_ARM` | `bodyYaw` | `-0.42 .. -0.23` | `0.40 .. 0.74` | `-0.11 .. +0.11` | Левая рука |
  | 13 | `RIGHT_ARM` | `bodyYaw` | `+0.23 .. +0.42` | `0.40 .. 0.74` | `-0.11 .. +0.11` | Правая рука |
  | 14 | `LEFT_LEG` | `bodyYaw` | `-0.20 .. -0.005` | `0.00 .. 0.40` | `-0.14 .. +0.14` | Левая нога |
  | 15 | `RIGHT_LEG` | `bodyYaw` | `+0.005 .. +0.20` | `0.00 .. 0.40` | `-0.14 .. +0.14` | Правая нога |

- Удобные обёртки:
  - `public static List<ZoneBox> computeForEntity(LivingEntity entity)` -> `compute(entity.getPos(), entity.getHeight(), entity.getWidth(), entity.getBodyYaw(), entity.getHeadYaw())`.
  - `public static @Nullable RayHit raycastZones(List<ZoneBox> boxes, Vec3d rayOrigin, Vec3d rayDir, double maxDist)`:
    - Нормализует `rayDir` (если `lengthSquared() < 1e-9` → `null`).
    - Ищет минимальное валидное `t >= 0` среди всех `ZoneBox` и возвращает `new RayHit(bestZone, bestT, rayOrigin.add(normDir.multiply(bestT)))`.

### 2.2 Обновление `com.tarkovcraft.health.HitZoneResolver`

- Сохранить все существующие перегрузки `resolveGeometric(...)` без поломки `BodyPartHealthTest`.
- Обновить `resolveFromDamage(LivingEntity victim, DamageSource source)`:
  1. Сохранить ранние выходы для `IS_FALL`, `drown`, `fire`, `freeze`, `inWall`, `starve`, `magic`, `wither`.
  2. Вычислить `List<ZoneBox> boxes = HitZoneBoxes.computeForEntity(victim);`.
  3. **Снаряд (`direct != null && direct != attacker`):**
     - Получить `Vec3d vel = direct.getVelocity();`.
     - Если `vel.lengthSquared() > 1e-6`:
       - `Vec3d dir = vel.normalize();`
       - **Первичный луч:** `Vec3d origin = direct.getPos().subtract(dir.multiply(0.5));` (откат на 0.5м назад по вектору скорости, чтобы поймать входную грань даже если пуля уже внутри тела), `maxDist = Math.max(16.0, vel.length() * 2.0 + 6.0)`.
       - `RayHit hit = HitZoneBoxes.raycastZones(boxes, origin, dir, maxDist);`
       - Если `hit != null` → вернуть `hit.zone()`!
     - **Вторичный луч (если скорость 0 или промах из-за расширения хитбокса пули):**
       - Если `direct` двигался (`prevPos = new Vec3d(direct.prevX, direct.prevY, direct.prevZ)` отличается от `direct.getPos()` на `> 1e-4`): рейкаст из `prevPos` по `(direct.getPos() - prevPos).normalize()`.
       - Иначе/если всё ещё `null` и `attacker != null`: рейкаст от `attacker.getEyePos()` по `attacker.getRotationVec(1.0f)` (`maxDist = attacker.distanceTo(victim) + 6.0`). Если попал → вернуть `hit.zone()`!
     - **Геометрический fallback (НЕ сырой старый `source.getPosition()`!):**
       - Если `vel.lengthSquared() > 1e-6`: вычислить ближайшую точку на прямой `(direct.getPos(), dir)` к центру жертвы `victim.getPos().add(0, victim.getHeight() * 0.5, 0)` и передать её в `resolveGeometric(...)`!
       - Иначе использовать `source.getPosition()` / `direct.getPos()` + `resolveGeometric(...)`.
  4. **Ближний бой (`direct == attacker` или `direct == null && attacker != null`):**
     - Рейкаст от `attacker.getEyePos()` по `attacker.getRotationVec(1.0f)` (`maxDist = 8.0`) по `boxes`.
     - Если попал → вернуть `hit.zone()`.
     - Иначе fallback на `raycastMeleeHitPos` + `resolveGeometric(...)`.

### 2.3 Фикс пробоя брони TaCZ в `PlayerEntityMixin` и `TaCZBulletHelper`

- **`TaCZBulletHelper.inspectDamageSource(DamageSource source)`:**
  - Считать пулей TaCZ, если `(direct != null && direct.getClass().getName().contains("EntityKineticBullet"))` **ИЛИ** `(source.getName() != null && source.getName().toLowerCase().contains("tacz.bullet"))`.
- **`PlayerEntityMixin.tarkovcraft$applyZonalBodyDamage`:**
  - Заменить расчёт `bypassesArmor`:
    ```java
    boolean envBypass = source.isIn(DamageTypeTags.IS_FALL)
        || source.isIn(DamageTypeTags.IS_DROWNING)
        || source.isIn(DamageTypeTags.IS_FIRE)
        || source.isIn(DamageTypeTags.IS_FREEZING);
    // TaCZ BULLET_IGNORE_ARMOR sets BYPASSES_ARMOR; force ZonalArmorHelper to evaluate ALL TaCZ bullets!
    boolean bypassesArmor = envBypass || (!bulletInfo.isTaczBullet() && source.isIn(DamageTypeTags.BYPASSES_ARMOR));
    ```

---

## 3. Отдельные визуально видимые 3D-хитбоксы (`HitZoneDebugRenderer`)

### 3.1 Клиентский рендерер `com.tarkovcraft.client.renderer.HitZoneDebugRenderer`

- Строго `@Environment(EnvType.CLIENT)`, без новых рендер-миксинов.
- Регистрация в `TarkovCraftClient.onInitializeClient()`:
  - `WorldRenderEvents.AFTER_ENTITIES.register(HitZoneDebugRenderer::render);`
  - Новая клавиша `public static KeyBinding toggleHitboxesKey;` (`"key.tarkovcraft.toggle_hitboxes"`, `InputUtil.Type.KEYSYM`, `GLFW.GLFW_KEY_F8`, `"category.tarkovcraft"`).
  - В `END_CLIENT_TICK`: при `toggleHitboxesKey.wasPressed()` переключить `HitZoneDebugRenderer.toggle()` и вывести actionbar-сообщение `message.tarkovcraft.hitboxes_on` / `message.tarkovcraft.hitboxes_off`.
- **Условие отображения:**
  - `enabled = userToggled || client.getEntityRenderDispatcher().shouldRenderHitboxes()` (работает и по `F3 + B`, и по `F8`).
- **Целевые сущности:**
  - Игроки (`PlayerEntity`, кроме самого локального игрока в режиме от 1-го лица `client.options.getPerspective().isFirstPerson()`, чтобы в `F5` игрок видел свои хитбоксы) и гуманоидные `LivingEntity` (зомби, скелеты, пиглины, илладжеры, жители, ведьмы, стойки для брони, а также любые `MobEntity` с `height >= 1.35f && width <= 0.85f`), живые, в радиусе `64` блоков от камеры.
- **Отрисовка:**
  - Интерполяция позиции `entity.getLerpedPos(tickDelta)` и углов `MathHelper.lerpAngleDegrees(tickDelta, prevBodyYaw, bodyYaw)` / `MathHelper.lerpAngleDegrees(tickDelta, prevHeadYaw, headYaw)`.
  - Берёт коробки напрямую из `HitZoneBoxes.compute(lerpedPos, entity.getHeight(), entity.getWidth(), bodyYaw, headYaw)` — **100% совпадение с хитбоксами урона**.
  - Рисует 12 рёбер каждой коробки через `ctx.consumers().getBuffer(RenderLayer.getLines())` с вычислением единичной нормали ребра `(nx, ny, nz)`.
- **Цветовая палитра зон (RGBA):**
  - `HEAD_EYES`: `(1.00f, 0.12f, 0.12f, 1.0f)` — ярко-красный
  - `HEAD_EARS`: `(0.95f, 0.15f, 0.90f, 1.0f)` — пурпурный
  - `HEAD_JAWS`: `(1.00f, 0.55f, 0.10f, 1.0f)` — оранжевый
  - `HEAD_TOP`: `(0.25f, 0.78f, 1.00f, 1.0f)` — голубой
  - `NECK`: `(1.00f, 0.92f, 0.15f, 1.0f)` — жёлтый
  - `THORAX`: `(0.15f, 0.90f, 0.30f, 1.0f)` — зелёный
  - `STOMACH`: `(0.62f, 0.96f, 0.22f, 1.0f)` — салатовый
  - `ARMPIT_LEFT` / `ARMPIT_RIGHT`: `(0.65f, 0.25f, 1.00f, 1.0f)` — фиолетовый
  - `LEFT_ARM` / `RIGHT_ARM`: `(0.20f, 0.48f, 1.00f, 1.0f)` — синий
  - `LEFT_LEG` / `RIGHT_LEG`: `(0.12f, 0.88f, 0.82f, 1.0f)` — бирюзовый

---

## 4. Локализация (`assets-worker`) и план тестов (`java-coder`)

### 4.1 Новые ключи локализации для `assets-worker` (`en_us.json` и `ru_ru.json`)

| Ключ | EN (`en_us.json`) | RU (`ru_ru.json`) |
| :--- | :--- | :--- |
| `key.tarkovcraft.toggle_hitboxes` | `Toggle 3D Hitboxes` | `Показать 3D-хитбоксы` |
| `message.tarkovcraft.hitboxes_on` | `TarkovCraft 3D Hitboxes: ON` | `3D-хитбоксы TarkovCraft: ВКЛ` |
| `message.tarkovcraft.hitboxes_off` | `TarkovCraft 3D Hitboxes: OFF` | `3D-хитбоксы TarkovCraft: ВЫКЛ` |

*(Всего +3 синхронизированных ключа в оба файла: итого 154 ключа, UTF-8 без BOM.)*

### 4.2 План JUnit 5 тестов `src/test/java/com/tarkovcraft/health/HitZoneBoxesTest.java`

1. `frontalCenterMassShotsHitThoraxAndStomachNotLegs`:
   - Выстрел спереди на высоте груди (`y = 1.15`, `origin = (0, 1.15, 8)`, `dir = (0, 0, -1)`) попадает строго в `HitZone.THORAX` (даже если луч стартует в 8 блоках позади, как у пули TaCZ!).
   - Выстрел спереди на высоте живота (`y = 0.85`) попадает строго в `HitZone.STOMACH`.
2. `headSubzonesDistinguishEyesJawsEarsAndTop`:
   - Фронтальный луч на `y = 1.60` → `HEAD_EYES`.
   - Фронтальный луч на `y = 1.46` → `HEAD_JAWS`.
   - Боковой луч на `y = 1.56` сбоку → `HEAD_EARS`.
   - Выстрел в затылок (`z = -5 -> +Z`, `y = 1.56`) и сверху в темя (`y = 3 -> -Y`) → `HEAD_TOP`.
3. `independentHeadYawRotatesHeadZonesWithoutRotatingThorax`:
   - `bodyYaw = 0°` (грудь на юг `+Z`), `headYaw = 90°` (голова смотрит на запад `-X`):
     - Выстрел с запада (`x = -5 -> +X`, `y = 1.60`) попадает в `HEAD_EYES`.
     - Выстрел с юга (`z = +5 -> -Z`, `y = 1.56`) попадает в `HEAD_EARS`.
     - Выстрел с юга (`z = +5 -> -Z`, `y = 1.15`) по-прежнему попадает в `THORAX`.
4. `neckArmsArmpitsAndLegsAreDistinct`:
   - Луч на `y = 1.37` центр → `NECK`.
   - Лучи в левую/правую руку (`x = ±0.32, y = 1.10`) → `LEFT_ARM` / `RIGHT_ARM`.
   - Косой луч во фланг под руку (`y = 1.18`, `localRight ≈ ±0.20`, `localFwd ≈ +0.13`) → `ARMPIT_LEFT` / `ARMPIT_RIGHT`.
   - Лучи в ноги (`x = ±0.10, y = 0.40`) → `LEFT_LEG` / `RIGHT_LEG`.
5. `allBoxesProduceValidEightWorldCorners`:
   - `compute(...)` возвращает все 13 значений `HitZone` (15 коробок с 2 ушами и 2 частями `HEAD_TOP`), у каждой `getWorldCorners().length == 8`, все координаты конечны (`Double.isFinite`).

---

## 5. Границы файлов и критерии приёмки

- **`architect` (уже выполнено):** `src/main/resources/tarkovcraft.mixins.json` (`"KeyBindingMixin"` в `"client"`), `docs/spec-binds-and-hitboxes.md`.
- **`java-coder` (`src/main/java/**`, `src/test/java/**`):**
  - Новые: `src/main/java/com/tarkovcraft/mixin/KeyBindingMixin.java`, `src/main/java/com/tarkovcraft/health/HitZoneBoxes.java`, `src/main/java/com/tarkovcraft/client/renderer/HitZoneDebugRenderer.java`, `src/test/java/com/tarkovcraft/health/HitZoneBoxesTest.java`.
  - Изменяемые: `PlayerInventoryMixin.java` (`removeStack(int)` + `insertStack(int, ItemStack)`), `HitZoneResolver.java`, `TaCZBulletHelper.java`, `PlayerEntityMixin.java`, `TarkovCraftClient.java`.
- **`assets-worker` (`src/main/resources/assets/tarkovcraft/lang/*.json`):**
  - +3 синхронизированных ключа (`key.tarkovcraft.toggle_hitboxes`, `message.tarkovcraft.hitboxes_on`, `message.tarkovcraft.hitboxes_off`).
- **Верификация:** `.\gradlew.bat build --no-daemon` (JDK 17) — компиляция, все 36 существующих + новые `HitZoneBoxesTest` зелёные, `remapJar` успешен, `HitZoneBoxes` без клиентских импортов, `KeyBindingMixin` строго в `"client"`.
