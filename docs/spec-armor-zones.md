# Спецификация: Пункт 3 ТЗ — «Бронеснаряжение и зоны защиты»

> Соответствует `TZ.md` (разделы 3 и 8). Без выдумывания несогласованных механик (никаких слотов сменных плит, кровотечений, хромоты или TactEq). Все Yarn-сигнатуры проверены через `javap` по `1.20.1+build.10`.

---

## 1. Аудит текущего состояния

### Что уже работает
- 8 предметов брони в `ModItems` с 2D-текстурами, моделями `models/item/*.json` и EN/RU-переводами:
  - Шлемы: `ssh68_helmet` (Cl.3, `HEAD_TOP`), `ratnik_6b47_helmet` (Cl.3, `HEAD_TOP + HEAD_EARS`), `altyn_helmet` (Cl.5, `HEAD_TOP + HEAD_EARS + HEAD_EYES + HEAD_JAWS`).
  - Маски: `balaclava_mask` (Cl.0 косметика), `death_shadow_mask` (Cl.2, `HEAD_EYES + HEAD_JAWS`).
  - Бронежилеты: `paca_armor` (Cl.2, `THORAX + STOMACH`), `press_armor` (Cl.3, `THORAX + STOMACH`), `zabralo_armor` (Cl.6, `THORAX + STOMACH + NECK + ARMPIT_LEFT + ARMPIT_RIGHT`).
- 13 зон попадания (`HitZone`) -> 7 пулов `BodyPart` + ваншот незащищённого `HEAD_EYES`.
- Базовая геометрия `HitZoneResolver` и базовая проверка зон в `ZonalArmorHelper`, базовые тултипы и 6 JUnit-тестов в `BodyPartHealthTest`.

### Выявленные баги, заглушки и пробелы
1. **Ванильная броня не отключена и сломана:**
   - `EquipmentSlotType.HELMET`/`BODY_ARMOR` и `PlayerLoadout.trySmartPickup` принимают ванильный `ArmorItem`, хотя `ZonalArmorHelper` его игнорирует (0 защиты).
   - ПКМ по ванильному `ArmorItem` (`ArmorItem.use -> Equipment.equipAndSwap`) и раздатчики (`ArmorItem.dispenseArmor -> PlayerEntity.canEquip`) всё ещё надевают ванильную броню в скрытые ванильные слоты `inventory.armor` (36–39), где игрок в Survival теряет предмет и получает ванильные атрибуты (например, knockback resistance от незерита).
2. **Критичный баг в `HitZoneResolver.resolveFromDamage` (ближний бой и поворот головы):**
   - В MC 1.20.1 `DamageSource.getPosition()` при `position == null` возвращает `source.getPos()` (ноги атакующего для melee, когда `direct == attacker`). Ветка melee-raycast (`hitPos == null && attacker != null`) — **мёртвый код**, поэтому все удары в ближнем бою на ровной поверхности имеют `relY == 0` и всегда попадают в ноги!
   - Для зон головы используется `victim.getBodyYaw()` вместо `victim.getHeadYaw()`, хотя голова может быть повёрнута на ±50°.
3. **Критичный баг: урон окружения блокируется и ломает броню:**
   - В `PlayerEntityMixin.applyDamage` урон от утопления, голода, огня, лавы, падения, удушья, магии и заморозки проходит через `ZonalArmorHelper` с `pen = 0`. В результате PACA/Zabralo блокирует 88% урона от утопления/голода и теряет прочность.
4. **Неполнота модели брони и баллистики (`PlaceholderArmorItem` / `ZonalArmorHelper`):**
   - Класс всё ещё называется `PlaceholderArmorItem`; косметическая балаклава получает `maxDamage(10)`.
   - `AmmoTier.armorWearMultiplier` — мёртвый код (не передаётся из `TaCZBulletHelper` в `BodyPartHealth`/`ZonalArmorHelper`).
   - Нет материалов брони (разная разрушаемость/запреградный урон), нет веса брони (вес считается по размеру сетки, поэтому Алтын весит 1.4 кг как маска), нет рикошета для шлемов, нет последовательной оценки слоёв (например, `HELMET` -> `FACE_MASK`), сломанная броня (`0/max`) не помечается явно в тултипе и UI.
5. **UI, тултипы и фидбек:**
   - В `TarkovInventoryScreen` слоты брони не показывают класс (`CL.3`), прочность (`30/30` или `0/30` красным) и нет сводки активной защиты зон (какие зоны головы/торса закрыты, а какие открыты — например `EARS`, `EYES`, `NECK`).
   - В `DamageReport`, `BodyHealthHudHelper` и `TarkovDeathScreen` нет отображения рикошета (`[RICOCHET]`) и серверного звука блока/рикошета; `/tarkov hit` жёстко зашивает `penetration = 25`.

---

## 2. Спецификация для `java-coder` (`src/main/java/**`, `src/test/java/**`)

> `tarkovcraft.mixins.json` уже обновлён архитектором: `"ArmorItemMixin"` добавлен в секцию `"mixins"`. `build.gradle` и `fabric.mod.json` не меняются.

### 2.1. Новые классы
1. **`src/main/java/com/tarkovcraft/item/ArmorMaterialType.java`** (common, без клиентских импортов)
   - Enum материалов брони (простые, прозрачные параметры):
     - `COSMETIC("cosmetic", 1.0f, 1.0f)`
     - `ARAMID("aramid", 0.55f, 0.12f)` — мягкая ткань/арамид (PACA, 6B47, Death Shadow)
     - `UHMWPE("uhmwpe", 0.45f, 0.11f)` — СВМПЭ (Zhuk-3 Press)
     - `COMBINED("combined", 0.65f, 0.13f)` — комбинированная тяжёлая (6B43 Zabralo)
     - `TITANIUM("titanium", 0.70f, 0.14f)` — титан (Altyn)
     - `STEEL("steel", 0.85f, 0.15f)` — бронесталь (SSh-68)
   - Геттеры: `getId()`, `getDestructibility()` (множитель износа прочности), `getBluntThroughput()` (доля запреградного урона при остановке пули), `getTranslationKey()` (`"armormaterial.tarkovcraft." + id`).

2. **`src/main/java/com/tarkovcraft/item/TarkovArmorItem.java`** (замена `PlaceholderArmorItem.java`; старый `PlaceholderArmorItem.java` удалить после обновления всех ссылок)
   - Поля: `EquipmentSlotType targetSlot`, `ItemSize storedItemSize`, `int armorClass`, `ArmorMaterialType material`, `float ricochetChance` (`0.0f..0.65f`), `float weightKg`, `Set<HitZone> coveredZones`.
   - Конструктор:
     - Если `armorClass > 0`: `super(settings.maxCount(1).maxDamage(Math.max(1, maxDurability)))`.
     - Если `armorClass <= 0` (косметика, например `BALACLAVA_MASK`): `super(settings.maxCount(1))` — без полоски прочности.
   - Методы: `getTargetSlot()`, `getStoredItemSize()`, `getArmorClass()`, `getMaterial()`, `getRicochetChance()`, `getWeightKg()`, `getCoveredZones()`, `coversZone(HitZone zone)`, `static int getRemainingDurability(ItemStack stack)`, `static boolean isBroken(ItemStack stack)` (`armorClass > 0 && getRemainingDurability(stack) <= 0`).
   - `appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context)`:
     - Если `armorClass > 0`:
       - Класс брони (`tooltip.tarkovcraft.armor_class`, `AQUA`) + материал (`tooltip.tarkovcraft.armor_material`, `GRAY`, через `Text.translatable(material.getTranslationKey())`).
       - Прочность: если `curDur <= 0` -> `tooltip.tarkovcraft.armor_broken` (`RED`), иначе `tooltip.tarkovcraft.armor_durability` (`GRAY` / `YELLOW` < 50% / `RED` < 25%).
       - Шанс рикошета (если `ricochetChance > 0`): `tooltip.tarkovcraft.armor_ricochet` (`DARK_AQUA`, с процентом `Math.round(ricochetChance * 100)`).
       - Закрытые зоны (`tooltip.tarkovcraft.armor_zones`, `DARK_GREEN`).
       - Открытые уязвимые зоны слота (для `HELMET`: незакрытые из `{HEAD_TOP, HEAD_EARS, HEAD_EYES, HEAD_JAWS}`; для `BODY_ARMOR`: незакрытые из `{THORAX, STOMACH, NECK, ARMPIT_LEFT, ARMPIT_RIGHT}`) -> `tooltip.tarkovcraft.armor_exposed` (`RED`), если список не пуст.
     - Иначе: `tooltip.tarkovcraft.armor_cosmetic` (`DARK_GRAY`).
     - Вес: `tooltip.tarkovcraft.armor_weight` (`DARK_GRAY`, `String.format(Locale.ROOT, "%.1f", weightKg)`).
   - **Граница клиент/сервер:** `Item.appendTooltip` — штатный метод `net.minecraft.item.Item` (проверено: `net.minecraft.client.item.TooltipContext` присутствует на обоих окружениях в Yarn 1.20.1). Не вызывать `MinecraftClient` или другие клиентские классы.

3. **`src/main/java/com/tarkovcraft/mixin/ArmorItemMixin.java`** (common, уже зарегистрирован в `tarkovcraft.mixins.json` -> `"mixins"`)
   - Целевой класс: `@Mixin(ArmorItem.class)`
   - Проверенная Yarn-сигнатура и дескриптор:
     - `@Inject(method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/TypedActionResult;", at = @At("HEAD"), cancellable = true)`
   - Логика:
     - Если `user != null && !user.isCreative() && !user.isSpectator()`:
       - Если `!world.isClient`: `user.sendMessage(Text.translatable("message.tarkovcraft.vanilla_armor_disabled"), true);`
       - `cir.setReturnValue(TypedActionResult.fail(user.getStackInHand(hand)));`
   - Причина: предотвращает срабатывание `Equipment.equipAndSwap` (который иначе удаляет предмет из руки через `copyAndEmpty()`).

### 2.2. Изменения в существующих файлах

4. **`src/main/java/com/tarkovcraft/registry/ModItems.java`**
   - Перевести все 8 предметов на `TarkovArmorItem` с точными параметрами (ID предметов НЕ менять!):
     - `SSH68_HELMET`: `HELMET`, `2x2`, Cl.3, dur 30, `STEEL`, ricochet `0.45f`, weight `1.3f`, `{HEAD_TOP}`
     - `RATNIK_6B47_HELMET`: `HELMET`, `2x2`, Cl.3, dur 35, `ARAMID`, ricochet `0.30f`, weight `1.25f`, `{HEAD_TOP, HEAD_EARS}`
     - `ALTYN_HELMET`: `HELMET`, `2x2`, Cl.5, dur 55, `TITANIUM`, ricochet `0.40f`, weight `4.1f`, `{HEAD_TOP, HEAD_EARS, HEAD_EYES, HEAD_JAWS}`
     - `BALACLAVA_MASK`: `FACE_MASK`, `1x1`, Cl.0, dur 0, `COSMETIC`, ricochet `0.0f`, weight `0.1f`, `{}`
     - `DEATH_SHADOW_MASK`: `FACE_MASK`, `2x2`, Cl.2, dur 25, `ARAMID`, ricochet `0.10f`, weight `0.4f`, `{HEAD_EYES, HEAD_JAWS}`
     - `PACA_ARMOR`: `BODY_ARMOR`, `3x3`, Cl.2, dur 50, `ARAMID`, ricochet `0.0f`, weight `2.4f`, `{THORAX, STOMACH}`
     - `PRESS_ARMOR`: `BODY_ARMOR`, `3x3`, Cl.3, dur 60, `UHMWPE`, ricochet `0.0f`, weight `5.0f`, `{THORAX, STOMACH}`
     - `ZABRALO_ARMOR`: `BODY_ARMOR`, `3x4`, Cl.6, dur 95, `COMBINED`, ricochet `0.0f`, weight `14.5f`, `{THORAX, STOMACH, NECK, ARMPIT_LEFT, ARMPIT_RIGHT}`

5. **`src/main/java/com/tarkovcraft/loadout/EquipmentSlotType.java`** и **`PlayerLoadout.java`**
   - Заменить `PlaceholderArmorItem` -> `TarkovArmorItem`.
   - **Убрать принятие ванильного `ArmorItem`** из `EquipmentSlotType.HELMET` / `BODY_ARMOR` и из `PlayerLoadout.trySmartPickup`. (Ванильная броня больше не надевается в слоты TarkovCraft.)
   - В `PlayerLoadout.estimateItemWeight(ItemStack)`: для `TarkovArmorItem armor` возвращать `armor.getWeightKg()`.
   - Обновить ссылки `PlaceholderArmorItem` -> `TarkovArmorItem` также в:
     - `ItemSizeRegistry.java`
     - `ItemRarityRegistry.java`
     - `InGameHudMixin.java` (клиентский)
     - `ItemEntityRendererMixin.java` (клиентский)

6. **`src/main/java/com/tarkovcraft/health/HitZone.java`**
   - Добавить метод `public boolean isHelmetRicochetZone()`: `return this == HEAD_TOP || this == HEAD_EARS;`.

7. **`src/main/java/com/tarkovcraft/health/HitZoneResolver.java`**
   - В `resolveFromDamage(LivingEntity victim, DamageSource source)`:
     - Экологические источники (`source.isIn(DamageTypeTags.IS_FALL)` / `"fall"` / `"stalagmite"` -> ноги; `IS_DROWNING` / `IS_FIRE` / `IS_FREEZING` / `"inWall"` / `"starve"` / `"magic"` / `"wither"` -> `THORAX`).
     - **Фикс melee:** если `direct != null && direct == attacker`, НЕ использовать `source.getPosition()` (ноги атакующего)! Строить луч от `attacker.getEyePos()` вдоль `attacker.getRotationVec(1.0f)` и искать пересечение через `victim.getBoundingBox().expand(0.15).raycast(aEye, aEye.add(aLook.multiply(6.0)))` (проверено `javap`: `Box.raycast(Vec3d, Vec3d) -> Optional<Vec3d>`), с фоллбэком на `aEye.add(aLook.multiply(Math.max(0.5, dist)))`.
     - Передавать раздельно `victim.getBodyYaw()` и `victim.getHeadYaw()`.
   - Добавить перегрузку `resolveGeometric(Vec3d victimFeetPos, float victimHeight, float bodyYawDeg, float headYawDeg, Vec3d hitPos, @Nullable Vec3d incomingDir)`:
     - Для `relY >= 0.78` (голова) использовать `headYawDeg`, для остального тела — `bodyYawDeg`.
     - Старую 5-аргументную `resolveGeometric(...)` оставить делегирующей `(bodyYawDeg, bodyYawDeg)`.

8. **`src/main/java/com/tarkovcraft/health/ZonalArmorHelper.java`**
   - Расширить `ArmorResult`:
     ```java
     public record ArmorResult(
         float remainingDamage,
         boolean protectedZone,
         boolean stoppedByArmor,
         boolean ricocheted,
         int absorbedArmorClass,
         int durabilityDamageApplied
     ) {}
     ```
   - Добавить полную детерминированную перегрузку:
     ```java
     public static ArmorResult evaluateHit(
         PlayerLoadout loadout,
         HitZone zone,
         float rawDamage,
         int penetrationPower,
         float armorWearMultiplier,
         boolean bypassesArmor,
         boolean canRicochet,
         float ricochetRoll
     )
     ```
     - Старую 4-аргументную `evaluateHit(loadout, zone, rawDamage, penetrationPower)` сохранить как делегат `(..., 1.0f, false, false, 1.0f)`.
   - Алгоритм:
     1. Если `bypassesArmor` -> `new ArmorResult(rawDamage, false, false, false, 0, 0)` (броня не снижает урон и не изнашивается).
     2. Собрать покрывающие слои с `remainingDur > 0 && armor.coversZone(zone)` в порядке **снаружи внутрь**: `HELMET`, `FACE_MASK`, `BODY_ARMOR`.
     3. Если слоёв нет -> `new ArmorResult(rawDamage, false, false, false, 0, 0)`.
     4. Для каждого слоя:
        - `durRatio = remainingDur / (float) maxDur`
        - **Рикошет:** если `canRicochet && zone.isHelmetRicochetZone() && armor.getRicochetChance() > 0f`:
          - `effRicochet = Math.min(0.65f, armor.getRicochetChance() * (0.5f + 0.5f * durRatio))`
          - Если `ricochetRoll < effRicochet`:
            - `wear = Math.max(1, Math.round(currentDmg * 0.08f * armor.getMaterial().getDestructibility() * safeWearMult))`
            - Нанести `wear` прочности; вернуть `new ArmorResult(Math.max(1.0f, currentDmg * 0.05f), true, true, true, armor.getArmorClass(), totalWear)`.
        - **Пробитие / остановка:**
          - `effectiveThreshold = armor.getArmorClass() * 10.0f * (0.45f + 0.55f * durRatio)`
          - `baseWear = currentDmg * 0.25f * armor.getMaterial().getDestructibility() * safeWearMult`
          - Если `currentPen < effectiveThreshold` (остановлено):
            - `wear = Math.max(1, Math.round(baseWear))`
            - Нанести `wear`; `blunt = Math.max(1.0f, currentDmg * armor.getMaterial().getBluntThroughput())`
            - Вернуть `new ArmorResult(blunt, true, true, false, armor.getArmorClass(), totalWear)`.
          - Иначе (пробитие слоя):
            - `wear = Math.max(1, Math.round(baseWear * 1.20f))`
            - Нанести `wear`
            - `overPen = currentPen - effectiveThreshold`
            - `reduction = Math.max(0.10f, 0.30f - overPen * 0.01f)`
            - `currentDmg = Math.max(1.0f, currentDmg * (1.0f - reduction))`
            - `currentPen = Math.max(0, Math.round(currentPen - effectiveThreshold * 0.35f))`
            - Перейти к следующему внутреннему слою (если есть).
     5. Если все слои пробиты -> вернуть `new ArmorResult(currentDmg, true, false, false, highestClass, totalWear)`.
   - Добавить чистый helper для UI/тестов:
     - `public static int getHighestProtectingClass(PlayerLoadout loadout, HitZone zone)` — возвращает максимальный `armorClass` несломанной брони, закрывающей `zone` (или `0`, если зона открыта / броня сломана).

9. **`src/main/java/com/tarkovcraft/health/TaCZBulletHelper.java`**
   - Добавить `float armorWearMultiplier` в `BulletInfo` (`loadedTier.getArmorWearMultiplier()` для TaCZ, `1.0f` для ближнего боя/стрел, `0.0f` для без-атакующего окружения).

10. **`src/main/java/com/tarkovcraft/health/DamageReport.java` и `BodyPartHealth.java`**
    - В `DamageReport` добавить `boolean ricocheted` (NBT-ключ `"Ricochet"`, дефолт `false`).
    - В `BodyPartHealth`:
      - Добавить перегрузку `applyHit(loadout, zone, rawDamage, penetrationPower, armorWearMultiplier, bypassesArmor, canRicochet, ricochetRoll, killerName, weaponName, ammoName, distanceMeters)`.
      - Сохранить прежнюю 8-аргументную `applyHit(...)` как делегат `(1.0f, false, false, 1.0f)`.
      - Условие ваншота `HEAD_EYES`: `zone.isUnprotectedOneshot() && !bypassesArmor && !armorRes.protectedZone()`.
      - Записывать `armorRes.ricocheted()` в `DamageReport`.

11. **`src/main/java/com/tarkovcraft/mixin/PlayerEntityMixin.java`** (common)
    - **Отключение ванильных слотов брони в Survival/Adventure** (все 3 сигнатуры проверены `javap`):
      - `@Inject(method = "canEquip(Lnet/minecraft/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)`:
        если `!self.isCreative() && !self.isSpectator() && MobEntity.getPreferredEquipmentSlot(stack).getType() == EquipmentSlot.Type.ARMOR` -> `cir.setReturnValue(false);`
      - `@Inject(method = "equipStack(Lnet/minecraft/entity/EquipmentSlot;Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)`:
        если `!self.isCreative() && !self.isSpectator() && slot.getType() == EquipmentSlot.Type.ARMOR && !stack.isEmpty()` -> `ci.cancel();`
      - `@Inject(method = "getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)`:
        если `!self.isCreative() && !self.isSpectator() && slot.getType() == EquipmentSlot.Type.ARMOR` -> `cir.setReturnValue(ItemStack.EMPTY);`
    - В `tarkovcraft$applyZonalBodyDamage`:
      - Вычислить `boolean bypassesArmor = source.isIn(DamageTypeTags.BYPASSES_ARMOR) || source.isIn(DamageTypeTags.IS_FALL) || source.isIn(DamageTypeTags.IS_DROWNING) || source.isIn(DamageTypeTags.IS_FIRE) || source.isIn(DamageTypeTags.IS_FREEZING);`
      - Вычислить `boolean canRicochet = !bypassesArmor && (bulletInfo.isTaczBullet() || source.isIn(DamageTypeTags.IS_PROJECTILE));`
      - Передать `bulletInfo.armorWearMultiplier()`, `bypassesArmor`, `canRicochet`, `self.getRandom().nextFloat()` в `tarkovcraft$bodyHealth.applyHit(...)`.
      - Звуковой фидбек на сервере (по последнему `DamageReport` в `tarkovcraft$bodyHealth.getRecentHits()`):
        - Если `report.ricocheted()` -> `self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.35f, 1.9f);`
        - Иначе если `report.armorBlocked()` -> `self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.65f, 1.15f);`

12. **`src/main/java/com/tarkovcraft/command/TarkovCommand.java`**
    - Добавить опциональный аргумент `[penetration]` (`IntegerArgumentType.integer(0, 100)`, по умолчанию `25`) к `/tarkov hit <zone> <damage> [penetration]`, чтобы тестировать пробитие/блокировку всех классов брони вживую.

13. **Клиентский UI (`com.tarkovcraft.client.*` — строго client-side):**
    - **`TarkovInventoryScreen.java`**:
      - В `drawEquipSlot(...)` для `TarkovArmorItem` (при `armorClass > 0`):
        - Рисовать компактный бейдж класса в правом верхнем углу (`"CL." + armorClass`, `0xFF63D2FF` или красный при `0` прочности).
        - Внизу слота рисовать текущую прочность `cur/max` (зелёный/жёлтый/красный; если `0` — красная рамка слота `0xFFC93838`).
    - **`BodyHealthHudHelper.java`**:
      - В `drawDetailedHealthTab(...)`: справа/слева внизу или над логом попаданий добавить компактную панель **«ЗАЩИТА ЗОН»** (`armor.tarkovcraft.coverage_header`), использующую `ZonalArmorHelper.getHighestProtectingClass(ClientLoadoutCache.getLoadout(), zone)` для ключевых зон (`HEAD_TOP`, `HEAD_EARS`, `HEAD_EYES`, `HEAD_JAWS`, `NECK`, `THORAX`, `ARMPIT_LEFT`, `STOMACH`): показывать `CL.X` зелёным/голубым или `OPEN` красным.
      - В строке `Recent Hits` (`BodyHealthHudHelper` и `TarkovDeathScreen`): если `r.ricocheted()`, показывать `[RICOCHET]` вместо `[ARMOR]`.

### 2.3. Юнит-тесты (`src/test/java/com/tarkovcraft/health/BodyPartHealthTest.java` и/или `ZonalArmorHelperTest.java`)
Обязательные тест-кейсы:
1. Все 6 существующих тестов `BodyPartHealthTest` проходят без регрессий.
2. **Износ и сломанная броня:** при падении прочности до `0` броня перестаёт защищать; `HEAD_EYES` под сломанным Алтыном / Death Shadow снова становится ваншотом.
3. **Зависимость износа от `AmmoTier.armorWearMultiplier` и материала:** `IGN (1.5x)` изнашивает броню быстрее, чем `HP (0.7x)`; `UHMWPE (Press)` теряет меньше прочности при равном уроне, чем `STEEL (SSh-68)`.
4. **Рикошет шлема:** `ricochetRoll` ниже порога для `SSH68_HELMET` на `HEAD_TOP` -> `ricocheted == true`, минимальный урон и минимальный износ; на незакрытых `HEAD_EARS` рикошет не срабатывает; на `BODY_ARMOR` (`PACA`) рикошет не срабатывает.
5. **Слоёная защита (`HELMET` + `FACE_MASK`):** `ALTYN` + `DEATH_SHADOW_MASK` на `HEAD_EYES` — при остановке внешним слоем маска не теряет прочность; при пробитии внешнего слоя внутренний слой дополнительно ослабляет/останавливает и изнашивается.
6. **Урон, обходящий броню (`bypassesArmor = true`):** утопление/огонь/голод на `THORAX` в `ZABRALO_ARMOR` наносит 100% урона и наносит **0** износа броне; `bypassesArmor = true` на `HEAD_EYES` не вызывает ваншот.
7. **`HitZoneResolver` с раздельным `headYaw` vs `bodyYaw`:** голова повёрнута на 90° относительно тела — выстрел спереди тела попадает в `HEAD_EARS`, а выстрел со стороны взгляда головы — в `HEAD_EYES`.
8. **Отключение ванильной брони в слотах:** `EquipmentSlotType.HELMET.accepts(new ItemStack(Items.DIAMOND_HELMET))` == `false`, `EquipmentSlotType.BODY_ARMOR.accepts(new ItemStack(Items.NETHERITE_CHESTPLATE))` == `false`, `PlayerLoadout.trySmartPickup` не надевает ванильную броню в слоты экипировки.

---

## 3. Спецификация для `assets-worker` (`src/main/resources/assets/**`, `data/**`)

Новые модели/текстуры `.png` не требуются (все 8 ID предметов брони сохраняются и уже имеют модели и текстуры).
Нужно синхронно обновить **оба** файла локализации (`en_us.json` и `ru_ru.json`, UTF-8 без BOM):

| Ключ | `en_us.json` | `ru_ru.json` |
| :--- | :--- | :--- |
| `tooltip.tarkovcraft.armor_material` | `Material: %s` | `Материал: %s` |
| `tooltip.tarkovcraft.armor_ricochet` | `Ricochet Chance: %s%%` | `Шанс рикошета: %s%%` |
| `tooltip.tarkovcraft.armor_exposed` | `Exposed: %s` | `Открыто: %s` |
| `tooltip.tarkovcraft.armor_broken` | `BROKEN (0/%s — No protection!)` | `СЛОМАНА (0/%s — Нет защиты!)` |
| `tooltip.tarkovcraft.armor_weight` | `Weight: %s kg` | `Вес: %s кг` |
| `armormaterial.tarkovcraft.cosmetic` | `Fabric` | `Ткань` |
| `armormaterial.tarkovcraft.aramid` | `Aramid` | `Арамид` |
| `armormaterial.tarkovcraft.uhmwpe` | `UHMWPE` | `СВМПЭ` |
| `armormaterial.tarkovcraft.combined` | `Combined` | `Комбинированная` |
| `armormaterial.tarkovcraft.titanium` | `Titanium` | `Титан` |
| `armormaterial.tarkovcraft.steel` | `Armor Steel` | `Бронесталь` |
| `message.tarkovcraft.vanilla_armor_disabled` | `Vanilla armor is disabled — use TarkovCraft vests, helmets, and masks` | `Ванильная броня отключена — используйте бронежилеты, шлемы и маски TarkovCraft` |
| `armor.tarkovcraft.coverage_header` | `ARMOR COVERAGE` | `ЗАЩИТА ЗОН` |
| `armor.tarkovcraft.zone_open` | `OPEN` | `ОТКРЫТО` |

Также проверить `src/main/resources/data/tarkovcraft/item_sizes/default_sizes.json` (изменения не требуются, размеры брони читаются из `TarkovArmorItem.getStoredItemSize()`) и провалидировать все JSON через PowerShell `ConvertFrom-Json`.

---

## 4. Команда проверки и критерии приёмки

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.9.8-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat build --no-daemon
```

Критерии приёмки:
- `.\gradlew.bat build --no-daemon` зелёный (компиляция + все JUnit 5 тесты + `remapJar`).
- В `src/main/java` нет упоминаний `PlaceholderArmorItem` и нет клиентских импортов в common/server-классах.
- Ванильный `ArmorItem` не надевается ни через слоты `TarkovInventoryScreen`, ни через ПКМ, ни через smart-pickup, ни через раздатчик в Survival/Adventure (при этом Creative не сломан).
- Все JSON валидны, `en_us.json` и `ru_ru.json` синхронны.
