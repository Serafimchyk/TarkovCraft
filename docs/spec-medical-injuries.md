# Спецификация: Пункт 4 ТЗ — Медицина, травмы, штрафы конечностей, жёсткий урон от падения и фикс Z-слоя драга

> **Статус:** Утверждённая архитектурная спецификация (`architect`, `gemini-4-neon-high`)  
> **Стек:** Minecraft `1.20.1`, Fabric Loader `0.16.10`, Fabric API `0.92.12+1.20.1`, Yarn `1.20.1+build.10`, Java `17`  
> **Среда (`fabric.mod.json`):** `environment: "*"` — строгая изоляция клиентских классов (`com.tarkovcraft.client.*`).  
> **Новые Mixin'ы:** **НЕ требуются.** `src/main/resources/tarkovcraft.mixins.json`, `fabric.mod.json` и `build.gradle` остаются без изменений.

---

## 0. Проверенные Yarn 1.20.1 сигнатуры (`javap` по Loom-кэшу)

Все приведённые ниже сигнатуры верифицированы напрямую по `minecraft-merged-...-1.20.1+build.10-v2.jar`:

- `net.minecraft.server.world.ServerWorld`:
  - `public <T extends ParticleEffect> int spawnParticles(T particle, double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed)`
- `net.minecraft.particle.DustParticleEffect`:
  - `public DustParticleEffect(org.joml.Vector3f color, float scale)`
- `net.minecraft.entity.effect.StatusEffects`:
  - `SLOWNESS`, `MINING_FATIGUE`, `WEAKNESS`, `NAUSEA`, `INSTANT_HEALTH`
- `net.minecraft.entity.effect.StatusEffectInstance`:
  - `public StatusEffectInstance(StatusEffect type, int duration, int amplifier, boolean ambient, boolean showParticles, boolean showIcon)`
- `net.minecraft.item.Item`:
  - `public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand)`
  - `public boolean isItemBarVisible(ItemStack stack)`
  - `public int getItemBarStep(ItemStack stack)` (возвращает `0..13`)
  - `public int getItemBarColor(ItemStack stack)` (RGB `0xRRGGBB`)
  - `public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context)`
- `net.minecraft.util.TypedActionResult`:
  - `success(T)`, `success(T, boolean)`, `consume(T)`, `pass(T)`, `fail(T)`
- `net.minecraft.entity.player.PlayerEntity` / `net.minecraft.entity.Entity` / `net.minecraft.entity.LivingEntity`:
  - `public void addExhaustion(float exhaustion)`
  - `public ItemCooldownManager getItemCooldownManager()` (`isCoolingDown(Item)`, `set(Item, int)`)
  - `public boolean isSprinting()` / `public void setSprinting(boolean sprinting)`
  - `public final boolean addStatusEffect(StatusEffectInstance effect)`
  - `public void setHealth(float health)` / `public void onDeath(DamageSource source)`
  - `public DamageSources getDamageSources()` (`generic()`, `fall()`)
- `net.minecraft.registry.tag.DamageTypeTags`:
  - `DamageTypeTags.IS_FALL`
- `net.minecraft.sound.SoundEvents` (проверенные `SoundEvent` поля):
  - `BLOCK_BONE_BLOCK_BREAK`, `ENTITY_PLAYER_HURT`, `BLOCK_WOOL_PLACE`, `ENTITY_SHEEP_SHEAR`, `ENTITY_GENERIC_EAT`, `ENTITY_GENERIC_DRINK`, `ITEM_ARMOR_EQUIP_LEATHER`, `ITEM_HONEYCOMB_WAX_ON`, `BLOCK_HONEY_BLOCK_SLIDE`

---

## 1. Травмы, `effectiveMaxHp` и обезбол в `BodyPartHealth`

### 1.1 Новый enum `com.tarkovcraft.health.InjuryType`

```java
package com.tarkovcraft.health;

public enum InjuryType {
	LIGHT_BLEED("light_bleed", 1 << 0),
	HEAVY_BLEED("heavy_bleed", 1 << 1),
	FRACTURE("fracture", 1 << 2);

	private final String id;
	private final int bitMask;
	// getters + getTranslationKey() -> "injury.tarkovcraft." + id
}
```

### 1.2 Расширение `com.tarkovcraft.health.BodyPartHealth`

**Новые поля:**
- `private final Map<BodyPart, Float> effectiveMaxHpMap = new EnumMap<>(BodyPart.class);`
- `private final Map<BodyPart, EnumSet<InjuryType>> injuries = new EnumMap<>(BodyPart.class);`
- `private int painkillerTicks = 0;` (максимум `6000` тиков = 5 мин)
- `private int bleedTickCounter = 0;`
- `private int sprintPainTickCounter = 0;`
- `private int stomachDrainTickCounter = 0;`

**Правила и методы:**
- `resetFull()`:
  - Сбрасывает `effectiveMaxHpMap.put(part, part.getMaxHp())` для всех 7 частей.
  - Сбрасывает `hpMap.put(part, part.getMaxHp())`.
  - Очищает `injuries.put(part, EnumSet.noneOf(InjuryType.class))`.
  - Обнуляет `painkillerTicks`, `bleedTickCounter`, `sprintPainTickCounter`, `stomachDrainTickCounter`, `recentHits`, `lastFatalReport`.
- `getEffectiveMaxHp(BodyPart part)`: возвращает `effectiveMaxHpMap.getOrDefault(part, part.getMaxHp())`.
- `setEffectiveMaxHp(BodyPart part, float value)`: зажимает в `[1.0f, part.getMaxHp()]`, и если `getHp(part) > clamped`, снижает `hpMap` до `clamped`.
- `getTotalEffectiveMaxHp()`: сумма `getEffectiveMaxHp(part)` по всем 7 частям.
- `setHp(BodyPart part, float value)`: зажимает в `[0.0f, getEffectiveMaxHp(part)]` (**не** `part.getMaxHp()`!).
- `getRatio(BodyPart part)`: возвращает `clamp(getHp(part) / Math.max(1.0f, getEffectiveMaxHp(part)), 0.0f, 1.0f)` — чтобы вылеченная после хирургии конечность отображалась зелёной пропорционально своему эффективному максимуму (а потерянный сегмент рисуется серым в `drawZoneBar`).
- `getBaseRatio(BodyPart part)`: возвращает `clamp(getHp(part) / part.getMaxHp(), 0.0f, 1.0f)` и `getEffectiveMaxRatio(BodyPart part)` = `clamp(getEffectiveMaxHp(part) / part.getMaxHp(), 0.0f, 1.0f)` (для полосы в UI).
- `getTotalRatio()`: остаётся `clamp(getTotalHp() / BodyPart.TOTAL_MAX_HP, 0.0f, 1.0f)`.
- `isBlacked(BodyPart part)`: `getHp(part) <= 0.001f`.
- `isLimb(BodyPart part)`: `part == LEFT_ARM || part == RIGHT_ARM || part == LEFT_LEG || part == RIGHT_LEG`.
- `healEvenly(float amount)`: лечит только невыбитые части (`cur > 0.001f && cur < getEffectiveMaxHp(part)`), не поднимая выше `effectiveMaxHp`.
- **Управление травмами:**
  - `hasInjury(BodyPart part, InjuryType type)`
  - `addInjury(BodyPart part, InjuryType type)`:
    - Если `type == FRACTURE && !isLimb(part)` → игнорировать (переломы только на руках и ногах).
    - Если `type == HEAVY_BLEED` → удалить `LIGHT_BLEED` с той же части (тяжёлое поглощает лёгкое на одной части, чтобы не дублировать стаки на одной зоне).
    - Если `type == LIGHT_BLEED && hasInjury(part, HEAVY_BLEED)` → игнорировать.
  - `removeInjury(BodyPart part, InjuryType type)` -> `boolean` (было ли снято).
  - `getInjuries(BodyPart part)` -> копия `EnumSet<InjuryType>`.
  - `hasLightBleed(BodyPart)`, `hasHeavyBleed(BodyPart)`, `hasFracture(BodyPart)`.
  - `countInjuries(InjuryType type)`, `hasAnyBleed()`.
  - `getImpairedLegCount()`: количество ног (`LEFT_LEG`, `RIGHT_LEG`), где `isBlacked(leg) || hasFracture(leg)` (`0..2`).
  - `getImpairedArmCount()`: количество рук (`LEFT_ARM`, `RIGHT_ARM`), где `isBlacked(arm) || hasFracture(arm)` (`0..2`).
  - `isStomachBlacked()`: `isBlacked(BodyPart.STOMACH)`.
- **Обезбол:**
  - `getPainkillerTicks()`, `hasPainkiller()` (`painkillerTicks > 0`), `setPainkillerTicks(int ticks)` (зажимать `[0, 6000]`), `addPainkillerTicks(int delta)` (зажимать `[0, 6000]`).

### 1.3 Обратно-совместимая схема NBT (`writeNbt` / `readNbt`)

- Существующие ключи **не менять**: `<part.getId()>` (`float`), `"RecentHits"` (`NbtList`), `"Fatal"` (`NbtCompound`).
- Новые опциональные ключи:
  - `"EffectiveMaxHp"` (`NbtCompound`): для каждой части `part.getId() -> float`. При отсутствии в старом NBT — инициализировать `part.getMaxHp()`.
  - `"Injuries"` (`NbtCompound`): для каждой части с травмами `part.getId() -> int` (битовая маска `InjuryType.getBitMask()`). При отсутствии — пустой набор.
  - `"PainkillerTicks"` (`int`, по умолчанию `0`).
  - `"BleedTick"` (`int`, по умолчанию `0`), `"SprintPainTick"` (`int`, по умолчанию `0`), `"StomachDrainTick"` (`int`, по умолчанию `0`).
- **Порядок чтения в `readNbt`:** сначала прочитать `"EffectiveMaxHp"`, затем HP частей (чтобы `setHp` корректно зажимал по `effectiveMaxHp`), затем `"Injuries"`, таймеры, `"RecentHits"`, `"Fatal"`.

### 1.4 Шансы травм при боевых попаданиях (`applyHit`)

- Сохранить существующие перегрузки `applyHit` (8 и 12 аргументов) для полной обратной совместимости с существующими тестами `BodyPartHealthTest` — они делегируют в новую 13-аргументную перегрузку с `injuryRoll = 1.0f` (гарантированно без случайных травм в старых тестах).
- Новая перегрузка:
  ```java
  public boolean applyHit(
      PlayerLoadout loadout, HitZone zone, float rawDamage, int penetrationPower,
      float armorWearMultiplier, boolean bypassesArmor, boolean canRicochet,
      float ricochetRoll, float injuryRoll,
      String killerName, String weaponName, String ammoName, float distanceMeters
  )
  ```
- `PlayerEntityMixin.applyDamage` и `TarkovCommand.simulateHit` передают `self.getRandom().nextFloat()` в `injuryRoll`.
- **Логика наложения травм** (после расчёта HP, только если `!killed && !bypassesArmor && !armorRes.ricocheted() && !armorRes.stoppedByArmor() && dmg > 0.0f`):
  - **Тяжёлое кровотечение (`HEAVY_BLEED`):** если `dmg >= 25.0f`:
    - `heavyChance = Math.min(0.45f, 0.12f + (dmg - 25.0f) * 0.008f)`
    - Если `injuryRoll < heavyChance` → `addInjury(targetPart, HEAVY_BLEED)`.
  - **Лёгкое кровотечение (`LIGHT_BLEED`):** если `dmg >= 10.0f` и не прокнуло `HEAVY_BLEED` на этом попадании:
    - `lightChance = Math.min(0.50f, 0.18f + (dmg - 10.0f) * 0.010f)`
    - Порог: `threshold = (dmg >= 25.0f ? heavyChance : 0.0f) + lightChance`.
    - Если `injuryRoll < threshold` → `addInjury(targetPart, LIGHT_BLEED)`.
  - **Перелом (`FRACTURE`, только если `isLimb(targetPart)`):**
    - Если конечность только что выбита в 0 (`isBlacked(targetPart)` и `dmg >= 20.0f`): `fractureChance = 0.70f`.
    - Иначе если `dmg >= 18.0f`: `fractureChance = Math.min(0.45f, 0.15f + (dmg - 18.0f) * 0.008f)`.
    - Если `injuryRoll < fractureChance` → `addInjury(targetPart, FRACTURE)`.

---

## 2. Жёсткий урон от падения (`applyFallDamage`)

### 2.1 Перехват в `PlayerEntityMixin.applyDamage`

- В `PlayerEntityMixin.tarkovcraft$applyZonalBodyDamage`, **сразу после** проверки `outOfWorld`/`genericKill` и `ci.cancel()`, **ДО** вызова `HitZoneResolver.resolveFromDamage(...)`:
  ```java
  if (source.isIn(DamageTypeTags.IS_FALL)) {
      float weightKg = tarkovcraft$loadout.calculateWeightKg();
      boolean killed = tarkovcraft$bodyHealth.applyFallDamage(amount, weightKg, self.getRandom().nextFloat());
      // Звуковой отклик перелома/удара
      if (tarkovcraft$bodyHealth.hasFracture(BodyPart.LEFT_LEG) || tarkovcraft$bodyHealth.hasFracture(BodyPart.RIGHT_LEG)) {
          self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
              SoundEvents.BLOCK_BONE_BLOCK_BREAK, SoundCategory.PLAYERS, 0.9f, 0.85f);
      }
      self.getWorld().playSound(null, self.getX(), self.getY(), self.getZ(),
          SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.85f, 0.95f);
      tarkovcraft$syncBodyHealth();
      if (killed) {
          self.setHealth(0.0f);
          self.onDeath(source);
      } else {
          self.setHealth(Math.max(1.0f, tarkovcraft$bodyHealth.getTotalRatio() * self.getMaxHealth()));
      }
      return;
  }
  ```

### 2.2 Точная формула в `BodyPartHealth.applyFallDamage(float vanillaAmount, float weightKg, float randomRoll)`

- Если `vanillaAmount <= 0.0f` → вернуть `isDead()`.
- **Множитель перегруза:**
  - `float excessKg = Math.max(0.0f, weightKg - 20.0f);`
  - `float weightMult = 1.0f + Math.min(0.80f, excessKg * 0.02f);` (до 20 кг = `1.0x`, 40 кг = `1.4x`, 60+ кг = `1.8x`).
- **Эффективная сила падения и урон на КАЖДУЮ ногу:**
  - `float effectiveFall = vanillaAmount * weightMult;`
  - `float perLegDamage = effectiveFall * 11.0f;`
- **Распределение урона строго по ОБЕИМ ногам + перелив в `THORAX`:**
  - `float leftBefore = getHp(BodyPart.LEFT_LEG);`
  - `float rightBefore = getHp(BodyPart.RIGHT_LEG);`
  - `float leftExcess = Math.max(0.0f, perLegDamage - leftBefore);`
  - `float rightExcess = Math.max(0.0f, perLegDamage - rightBefore);`
  - `setHp(BodyPart.LEFT_LEG, leftBefore - perLegDamage);`
  - `setHp(BodyPart.RIGHT_LEG, rightBefore - perLegDamage);`
  - `float thoraxOverflow = leftExcess + rightExcess;`
  - `if (thoraxOverflow > 0.0f) setHp(BodyPart.THORAX, getHp(BodyPart.THORAX) - thoraxOverflow);`
- **Пороги травм от падения:**
  - **Среднее падение (`effectiveFall >= 3.0f`, ~6+ блоков налегке):**
    - `addInjury(BodyPart.LEFT_LEG, InjuryType.FRACTURE);`
    - Если `effectiveFall >= 4.0f || randomRoll < 0.50f`: `addInjury(BodyPart.RIGHT_LEG, InjuryType.FRACTURE);`
  - **Сильное/высокое падение (`effectiveFall >= 6.0f`, ~9+ блоков налегке или ~7+ с грузом — гарантированно `perLegDamage >= 66 >= 65`, выбивает ОБЕ ноги в `0 HP`!):**
    - `addInjury(BodyPart.LEFT_LEG, InjuryType.FRACTURE);`
    - `addInjury(BodyPart.RIGHT_LEG, InjuryType.FRACTURE);`
    - Если `effectiveFall >= 8.0f`:
      - `addInjury(BodyPart.LEFT_LEG, InjuryType.HEAVY_BLEED);`
      - `addInjury(BodyPart.RIGHT_LEG, InjuryType.LIGHT_BLEED);`
    - Иначе (`6.0f <= effectiveFall < 8.0f`):
      - `addInjury(BodyPart.LEFT_LEG, InjuryType.LIGHT_BLEED);`
      - Если `randomRoll < 0.50f`: `addInjury(BodyPart.RIGHT_LEG, InjuryType.LIGHT_BLEED);`
  - **Экстремальное падение (`effectiveFall >= 10.0f`, ~13+ блоков налегке):**
    - `perLegDamage >= 110`, избыток `(110 - 65) * 2 = 90 HP > 85 HP` груди → `THORAX = 0` → **KIA**!
- **Запись `DamageReport`:**
  - `DamageReport report = new DamageReport(HitZone.LEFT_LEG, BodyPart.LEFT_LEG, perLegDamage * 2.0f, perLegDamage * 2.0f, thoraxOverflow > 0.0f, false, false, false, "Fall Impact", "Gravity", String.format(Locale.ROOT, "%.1f kg", weightKg), vanillaAmount + 3.0f);`
  - Добавить в `recentHits` (лимит `MAX_HIT_LOG = 6`); если `isDead()` → `lastFatalReport = report`.
  - Вернуть `isDead()`.

---

## 3. Серверный тик штрафов и кровотечений

### 3.1 Чистая логика тика `BodyPartHealth.tickEffects(boolean isSprinting)`

Создать record внутри `BodyPartHealth` (или в `com.tarkovcraft.health`):
```java
public record TickResult(
	boolean stateChanged,
	boolean killedByBloodLoss,
	boolean bleedDamaged,
	boolean heavyBleedActive,
	boolean sprintPainTriggered,
	int impairedLegs,
	int impairedArms,
	boolean stomachBlacked,
	boolean painkillerActive,
	float exhaustionToAdd
) {}
```

**Правила одного серверного тика (20 тиков = 1 секунда):**
1. **Обезбол:** если `painkillerTicks > 0`:
   - `painkillerTicks--`;
   - `stateChanged = (painkillerTicks == 0 || painkillerTicks % 20 == 0)`.
2. **Кровотечения:**
   - Подсчитать части с `LIGHT_BLEED` и `HEAVY_BLEED`.
   - Если кровотечений нет → `bleedTickCounter = 0`.
   - Иначе `bleedTickCounter++`; при `bleedTickCounter >= 40` (каждые 2.0 сек):
     - `bleedTickCounter = 0`, `bleedDamaged = true`, `stateChanged = true`.
     - По каждой части тела:
       - `HEAVY_BLEED`: снять **`4.0f` HP** (2.0 HP/сек на рану).
       - `LIGHT_BLEED`: снять **`1.5f` HP** (0.75 HP/сек на рану).
       - Если часть нежизненная (`!part.isVital()`) и её HP меньше списываемого (или уже 0) → избыток переливается в `THORAX`.
       - Если часть жизненная (`HEAD`/`THORAX`) → списывается напрямую.
     - Если после списания `isDead()`:
       - Записать `DamageReport(HitZone.THORAX, BodyPart.THORAX, totalBleedDmg, totalBleedDmg, overflowed, false, false, false, "Blood Loss", anyHeavy ? "Heavy Bleeding" : "Light Bleeding", "", 0.0f)` в `recentHits` и `lastFatalReport`.
       - `killedByBloodLoss = true`.
3. **Спринт на выбитых/сломанных ногах с обезболом:**
   - `impairedLegs = getImpairedLegCount()`.
   - Если `impairedLegs > 0 && hasPainkiller() && isSprinting && !isDead()`:
     - `sprintPainTickCounter++`; при `sprintPainTickCounter >= 20` (каждую 1.0 сек):
       - `sprintPainTickCounter = 0`, `sprintPainTriggered = true`, `stateChanged = true`.
       - Нанести **`1.5f` HP** боли: разделить поровну между повреждёнными ногами (если нога выбита в 0 — избыток переливается в `THORAX`; при 2 выбитых ногах `1.5f` идёт в `THORAX`).
       - Если `isDead()` — записать `DamageReport(..., "Pain Shock", "Broken Legs Sprint", "", 0.0f)` в `lastFatalReport`.
   - Иначе `sprintPainTickCounter = 0`.
4. **Выбитый живот (`STOMACH`):**
   - Если `isStomachBlacked() && !isDead()`:
     - `stomachDrainTickCounter++`; при `stomachDrainTickCounter >= 40` (каждые 2.0 сек):
       - `stomachDrainTickCounter = 0`, `exhaustionToAdd = 0.45f`.
   - Иначе `stomachDrainTickCounter = 0`.

### 3.2 Серверный оркестратор `com.tarkovcraft.health.BodyHealthTicker`

Вызывается из `TarkovCraftMod.onInitialize()` внутри существующего `ServerTickEvents.END_SERVER_TICK` для каждого `ServerPlayerEntity player`:
1. **Строгая проверка режима:** `if (player.isCreative() || player.isSpectator() || !player.isAlive()) return;`
2. Вызвать `TickResult res = health.tickEffects(player.isSprinting());`.
3. **Частицы и звук крови (`res.bleedDamaged()`):**
   - `ServerWorld sw = (ServerWorld) player.getWorld();`
   - `DustParticleEffect blood = new DustParticleEffect(new Vector3f(0.78f, 0.05f, 0.05f), res.heavyBleedActive() ? 1.15f : 0.75f);`
   - `sw.spawnParticles(blood, player.getX(), player.getY() + 0.9, player.getZ(), res.heavyBleedActive() ? 8 : 3, 0.22, 0.35, 0.22, 0.01);`
   - Если `res.heavyBleedActive()`: `sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.35f, 0.75f);`
4. **Звук боли при спринте под обезболом (`res.sprintPainTriggered()`):**
   - `player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.55f, 0.95f);`
5. **Смерть от кровопотери / болевого шока:**
   - Если `res.killedByBloodLoss() || health.isDead()`:
     - `holder.tarkovcraft$syncBodyHealth();`
     - `player.setHealth(0.0f);`
     - `player.onDeath(player.getDamageSources().generic());`
     - `return;`
6. **Штрафы конечностей и живота:**
   - **Ноги без обезбола (`res.impairedLegs() > 0 && !res.painkillerActive()`):**
     - На каждом тике: `if (player.isSprinting()) player.setSprinting(false);`
     - Раз в 10 тиков (`player.age % 10 == 0`): `player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, res.impairedLegs() >= 2 ? 2 : 1, true, false, true));`
   - **Руки без обезбола (`res.impairedArms() > 0 && !res.painkillerActive()`):**
     - Раз в 10 тиков (`player.age % 10 == 0`):
       - `player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 25, res.impairedArms() >= 2 ? 1 : 0, true, false, true));`
       - `player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 25, res.impairedArms() >= 2 ? 1 : 0, true, false, true));`
   - **Живот (`res.exhaustionToAdd() > 0.0f`):**
     - `player.addExhaustion(res.exhaustionToAdd());`
7. **Синхронизация при `res.stateChanged()`:**
   - `player.setHealth(Math.max(1.0f, health.getTotalRatio() * player.getMaxHealth()));`
   - `holder.tarkovcraft$syncBodyHealth();`

---

## 4. 9 медицинских предметов EFT

### 4.1 Enum `com.tarkovcraft.item.MedicalType`

Все 9 предметов имеют `maxCount(1)` (не стакаются, чтобы пул/заряды в NBT никогда не конфликтовали):

| Константа | ID (`ModItems`) | Размер | Режим (`CapacityMode`) | Запас (`maxCapacity`) | Макс. HP/раз | Лёгк. кровь | Тяж. кровь | Перелом | Хирургия (`retention`) | Обезбол (тики) | Exhaustion | Вес (кг) | Редкость (`ItemRarityTier`) | КД (тики) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `BANDAGE` | `bandage` | `1x1` | `USES` | `2` | `0` | Да (1) | Нет | Нет | Нет (`0`) | `0` | `0.0` | `0.05` | `COMMON` | `15` |
| `CAT_TOURNIQUET` | `cat_tourniquet` | `1x1` | `USES` | `1` | `0` | Нет | Да (1) | Нет | Нет (`0`) | `0` | `0.0` | `0.12` | `COMMON` | `20` |
| `SPLINT` | `splint` | `1x1` | `USES` | `1` | `0` | Нет | Нет | Да (1) | Нет (`0`) | `0` | `0.0` | `0.20` | `COMMON` | `20` |
| `ALU_SPLINT` | `alu_splint` | `1x1` | `USES` | `5` | `0` | Нет | Нет | Да (1) | Нет (`0`) | `0` | `0.0` | `0.35` | `UNCOMMON` | `20` |
| `ANALGIN` | `analgin` | `1x1` | `USES` | `4` | `0` | Нет | Нет | Нет | Нет (`0`) | `1800` (90с) | `1.5` | `0.06` | `COMMON` | `15` |
| `AI2_MEDKIT` | `ai2_medkit` | `1x1` | `HP_POOL` | `100` | `50` | Нет | Нет | Нет | Нет (`0`) | `0` | `0.0` | `0.15` | `COMMON` | `20` |
| `SALEWA_MEDKIT` | `salewa_medkit` | `1x2` | `HP_POOL` | `400` | `85` | Да (`45` пула) | Да (`135` пула) | Нет | Нет (`0`) | `0` | `0.0` | `0.60` | `UNCOMMON` | `25` |
| `CMS_KIT` | `cms_kit` | `2x1` | `USES` | `5` | `0` | Нет | Нет | Нет | Да (`0.55f`) | `0` | `0.8` | `0.65` | `RARE` | `40` |
| `SURV12_KIT` | `surv12_kit` | `3x1` | `USES` | `15` | `0` | Нет | Нет | Да (на оперируемой / слом. части) | Да (`0.80f`) | `0` | `1.0` | `1.00` | `EPIC` | `45` |

### 4.2 Класс `com.tarkovcraft.item.TarkovMedicalItem extends Item`

- **NBT-ключ остатка:** `public static final String NBT_REMAINING = "TarkovMedRemaining";`
- `getRemaining(ItemStack stack)`: если `stack` не имеет `NBT_REMAINING` → возвращает `type.getMaxCapacity()`; иначе зажимает `[0, type.getMaxCapacity()]`.
- `setRemaining(ItemStack stack, int value)`:
  - Если `value <= 0` → `stack.setCount(0);` (чистое удаление без пустых остатков).
  - Иначе → `stack.getOrCreateNbt().putInt(NBT_REMAINING, Math.min(type.getMaxCapacity(), value));`.
- **Полоса ресурса (`Item` overrides):**
  - `isItemBarVisible(ItemStack stack)`: `type.getMaxCapacity() > 1 && getRemaining(stack) < type.getMaxCapacity()`.
  - `getItemBarStep(ItemStack stack)`: `Math.round(13.0f * getRemaining(stack) / (float) Math.max(1, type.getMaxCapacity()))` (зажать `0..13`).
  - `getItemBarColor(ItemStack stack)`: `ratio >= 0.50f ? 0x55CC55 : (ratio >= 0.25f ? 0xE0B23B : 0xE53935)`.
- **EFT-тултип (`appendTooltip`):**
  - Ресурс: для `HP_POOL` → `tooltip.tarkovcraft.med_pool` (`HP Pool: %s/%s` / `Запас ОЗ: %s/%s`) + `tooltip.tarkovcraft.med_max_heal` (`Max per use: %s HP`); для `USES` → `tooltip.tarkovcraft.med_uses` (`Uses: %s/%s` / `Заряды: %s/%s`).
  - Список эффектов (по свойствам `MedicalType`):
    - `tooltip.tarkovcraft.med_cures_light_bleed` (для Salewa — с указанием стоимости `45` через `tooltip.tarkovcraft.med_cures_light_bleed_cost`)
    - `tooltip.tarkovcraft.med_cures_heavy_bleed` (для Salewa — `tooltip.tarkovcraft.med_cures_heavy_bleed_cost`, `135`)
    - `tooltip.tarkovcraft.med_cures_fracture`
    - `tooltip.tarkovcraft.med_painkiller` (`Painkiller: %ss`)
    - `tooltip.tarkovcraft.med_surgery` (`Restores blacked limb (max HP: %s%%)`)
    - `tooltip.tarkovcraft.med_weight` (`Weight: %s kg`, или переиспользовать `tooltip.tarkovcraft.armor_weight` — лучше собственный `tooltip.tarkovcraft.med_weight` не дублировать, а использовать существующий `tooltip.tarkovcraft.armor_weight`? Нет, лучше добавить нейтральный `tooltip.tarkovcraft.med_weight` или `tooltip.tarkovcraft.armor_weight` — используем `tooltip.tarkovcraft.armor_weight` не стоит по смыслу, добавим `tooltip.tarkovcraft.med_weight`).
- **Интеграция в существующие реестры:**
  - `ModItems`: зарегистрировать все 9 констант (`BANDAGE`, `CAT_TOURNIQUET`, `SPLINT`, `ALU_SPLINT`, `ANALGIN`, `AI2_MEDKIT`, `SALEWA_MEDKIT`, `CMS_KIT`, `SURV12_KIT`) и добавить их в `TARKOV_GROUP`.
  - `ItemSizeRegistry.getSize(ItemStack)`: `if (item instanceof TarkovMedicalItem med) return med.getStoredItemSize();`
  - `ItemRarityRegistry.getTier(ItemStack)`: `if (item instanceof TarkovMedicalItem med) return med.getRarityTier();`
  - `PlayerLoadout.estimateItemWeight(ItemStack)`: `if (stack.getItem() instanceof TarkovMedicalItem med) return med.getWeightKg();`
  - `TarkovScreenHandler.isConsumableItem(ItemStack)`: добавить `|| stack.getItem() instanceof TarkovMedicalItem`.

### 4.3 Логика применения `com.tarkovcraft.health.MedicalHelper`

Вынесена в чистый common-класс (без клиентских импортов), полностью покрываемый JUnit 5:

```java
public record ApplyResult(
	boolean applied,
	@Nullable BodyPart targetPart,
	float exhaustionToAdd,
	boolean depleted
) {}
```

**Методы:**
1. `public static boolean canApplyToPart(BodyPartHealth health, ItemStack stack, BodyPart part)`:
   - Проверяет, имеет ли смысл применять `stack` именно на `part` (используется и на сервере для валидации, и на клиенте во вкладке `ЗДОРОВЬЕ` для зелёной подсветки доступных частей тела!).
   - Правила:
     - `remaining <= 0` → `false`.
     - `ANALGIN`: `health.getPainkillerTicks() < type.getPainkillerTicks() - 20` (допустим клик по любой части).
     - `BANDAGE`: `health.hasLightBleed(part)`.
     - `CAT_TOURNIQUET`: `health.hasHeavyBleed(part)`.
     - `SPLINT` / `ALU_SPLINT`: `health.hasFracture(part)`.
     - `CMS_KIT`: `!part.isVital() && health.isBlacked(part)`.
     - `SURV12_KIT`: `(!part.isVital() && health.isBlacked(part)) || health.hasFracture(part)`.
     - `AI2_MEDKIT`: `!health.isBlacked(part) && health.getHp(part) + 0.01f < health.getEffectiveMaxHp(part)`.
     - `SALEWA_MEDKIT`: `(health.hasHeavyBleed(part) && remaining >= 135) || (health.hasLightBleed(part) && remaining >= 45) || (!health.isBlacked(part) && health.getHp(part) + 0.01f < health.getEffectiveMaxHp(part))`.
2. `public static @Nullable BodyPart selectAutoTarget(BodyPartHealth health, ItemStack stack)`:
   - Приоритет выбора цели (если ничего не подходит → `null`):
     1. `ANALGIN`: если `health.getPainkillerTicks() < type.getPainkillerTicks() - 20` → `BodyPart.HEAD`.
     2. `CAT_TOURNIQUET` или (`SALEWA_MEDKIT` при `remaining >= 135`): первая часть с `HEAVY_BLEED` в порядке `THORAX, HEAD, STOMACH, LEFT_LEG, RIGHT_LEG, LEFT_ARM, RIGHT_ARM`.
     3. `BANDAGE` или (`SALEWA_MEDKIT` при `remaining >= 45`): первая часть с `LIGHT_BLEED` в том же порядке.
     4. `SPLINT` / `ALU_SPLINT`: первая конечность с `FRACTURE` в порядке `LEFT_LEG, RIGHT_LEG, LEFT_ARM, RIGHT_ARM`.
     5. `CMS_KIT` / `SURV12_KIT`: первая выбитая нежизненная часть в порядке `STOMACH, LEFT_LEG, RIGHT_LEG, LEFT_ARM, RIGHT_ARM`; для `SURV12_KIT` запасной вариант — первая конечность с `FRACTURE`.
     6. `AI2_MEDKIT` / `SALEWA_MEDKIT`: среди невыбитых повреждённых частей (`hp > 0.001f && hp + 0.01f < effMax`) — сначала жизненно важные (`HEAD`, `THORAX` с минимальным `getRatio`), затем остальные с минимальным `getRatio`.
3. `public static ApplyResult applyMedical(BodyPartHealth health, ItemStack stack, @Nullable BodyPart requestedPart, boolean infiniteCreative)`:
   - Если `requestedPart == null`, выбрать `target = selectAutoTarget(health, stack)`.
   - Иначе проверить `canApplyToPart(health, stack, requestedPart)`.
   - Если цели нет / недопустимо → вернуть `ApplyResult(false, null, 0f, false)` (предмет **НЕ** расходуется!).
   - Выполнить действие:
     - `ANALGIN`: `health.setPainkillerTicks(Math.max(health.getPainkillerTicks(), type.getPainkillerTicks()))`, списать 1 заряд, `exhaustion = 1.5f`.
     - `BANDAGE`: `health.removeInjury(target, LIGHT_BLEED)`, списать 1 заряд.
     - `CAT_TOURNIQUET`: `health.removeInjury(target, HEAVY_BLEED)`, списать 1 заряд.
     - `SPLINT` / `ALU_SPLINT`: `health.removeInjury(target, FRACTURE)`, списать 1 заряд.
     - `CMS_KIT`: `float newEff = Math.min(health.getEffectiveMaxHp(target), Math.max(1.0f, Math.round(target.getMaxHp() * 0.55f)));` `health.setEffectiveMaxHp(target, newEff);` `health.setHp(target, 1.0f);` списать 1 заряд, `exhaustion = 0.8f`.
     - `SURV12_KIT`: если `health.isBlacked(target)` → `float newEff = Math.min(health.getEffectiveMaxHp(target), Math.max(1.0f, Math.round(target.getMaxHp() * 0.80f)));` `health.setEffectiveMaxHp(target, newEff);` `health.setHp(target, 1.0f);` `health.removeInjury(target, FRACTURE);`; иначе `health.removeInjury(target, FRACTURE);`; списать 1 заряд, `exhaustion = 1.0f`.
     - `AI2_MEDKIT`: `float missing = health.getEffectiveMaxHp(target) - health.getHp(target);` `float heal = Math.min(missing, Math.min(50.0f, remaining));` `health.setHp(target, health.getHp(target) + heal);` списать `Math.min(remaining, Math.max(1, Math.round(heal)))`.
     - `SALEWA_MEDKIT`:
       - Если `health.hasHeavyBleed(target) && remaining >= 135`: снять `HEAVY_BLEED`, списать `135`.
       - Иначе если `health.hasLightBleed(target) && remaining >= 45`: снять `LIGHT_BLEED`, списать `45`.
       - Иначе: `float missing = health.getEffectiveMaxHp(target) - health.getHp(target);` `float heal = Math.min(missing, Math.min(85.0f, remaining));` `health.setHp(target, health.getHp(target) + heal);` списать `Math.min(remaining, Math.max(1, Math.round(heal)))`.
   - Если `!infiniteCreative`: вызвать `TarkovMedicalItem.setRemaining(stack, remaining - cost)`.
   - Вернуть `ApplyResult(true, target, type.getExhaustionCost(), stack.isEmpty())`.
4. `public static void playMedicalSound(ServerPlayerEntity player, MedicalType type)`:
   - `BANDAGE` / `CAT_TOURNIQUET` → `SoundEvents.BLOCK_WOOL_PLACE` (vol `0.8f`, pitch `1.1f`)
   - `SPLINT` / `ALU_SPLINT` → `SoundEvents.ITEM_ARMOR_EQUIP_LEATHER` (vol `0.8f`, pitch `1.2f`)
   - `ANALGIN` → `SoundEvents.ENTITY_GENERIC_DRINK` (vol `0.7f`, pitch `1.3f`)
   - `AI2_MEDKIT` / `SALEWA_MEDKIT` → `SoundEvents.ITEM_HONEYCOMB_WAX_ON` (vol `0.75f`, pitch `1.15f`)
   - `CMS_KIT` / `SURV12_KIT` → `SoundEvents.ENTITY_SHEEP_SHEAR` (vol `0.7f`, pitch `0.95f`)

---

## 5. Три способа лечения и новый пакет `C2S_HEAL_PART`

### 5.1 ПКМ в руке (`TarkovMedicalItem.use(World, PlayerEntity, Hand)`)

- На клиенте (`world.isClient`): вернуть `TypedActionResult.pass(stack)` (без клиентских импортов — клиент отправит пакет использования на сервер).
- На сервере (`!world.isClient`):
  - Проверить `user instanceof ServerPlayerEntity sp && sp instanceof PlayerLoadoutHolder holder && !sp.isSpectator() && sp.isAlive()`.
  - Проверить `!sp.getItemCooldownManager().isCoolingDown(this)`.
  - Вызвать `MedicalHelper.applyMedical(health, stack, null, sp.isCreative())`.
  - Если `!res.applied()`: отправить `sp.sendMessage(Text.translatable("message.tarkovcraft.med_not_needed"), true);` и вернуть `TypedActionResult.fail(stack)`.
  - Если `res.applied()`:
    - Применить `sp.addExhaustion(res.exhaustionToAdd())` при `> 0`.
    - `sp.getItemCooldownManager().set(this, type.getCooldownTicks());`
    - Если `stack.isEmpty() && !sp.isCreative()` → `sp.setStackInHand(hand, ItemStack.EMPTY);`
    - Обновить ванильное HP `sp.setHealth(Math.max(1.0f, health.getTotalRatio() * sp.getMaxHealth()))`.
    - Вызвать `holder.tarkovcraft$syncBodyHealth();` и `holder.tarkovcraft$syncLoadout();` (**обязательно**, чтобы NBT остатка/пула в кармане синхронизировался на клиент!).
    - `MedicalHelper.playMedicalSound(sp, type);`
    - Вернуть `TypedActionResult.success(sp.getStackInHand(hand), false)`.

### 5.2 ПКМ-меню `ИСПОЛЬЗОВАТЬ` (`TarkovScreenHandler.handleUseItem`)

- В начале `handleUseItem(...)` после `peekSource(...)`:
  - Если `peek.getItem() instanceof TarkovMedicalItem`: делегировать в `applyMedicalFromSource(serverPlayer, srcLoc, srcIdx, srcEntryId, null, true)` и `return;`.
- Существующая логика для еды/зелий остаётся неизменной.

### 5.3 Адресное лечение во вкладке `ЗДОРОВЬЕ`: `TarkovNetworking.C2S_HEAL_PART`

- **Идентификатор (`TarkovNetworking`):**
  ```java
  public static final Identifier C2S_HEAL_PART = new Identifier(TarkovCraftMod.MOD_ID, "heal_part");
  ```
- **Бинарный формат пакета:**
  1. `byte srcLoc`
  2. `VarInt srcIdx`
  3. `boolean hasEntryId` + (`UUID srcEntryId` если `true`)
  4. `byte partOrdinal` (`-1` = auto-target; `0..6` = `BodyPart.values()[partOrdinal]`)
- **Отправитель строго в `com.tarkovcraft.client.ClientNetworkSender`** (не добавлять клиентских импортов в `TarkovNetworking`!):
  ```java
  public static void sendHealPart(byte srcLoc, int srcIdx, @Nullable UUID srcEntryId, @Nullable BodyPart part)
  ```
- **Серверный приёмник (`TarkovNetworking.registerServerReceivers()`):**
  - Декодирует буфер в потоке Netty, проверяет `partOrdinal`: если `>= 0 && < BodyPart.values().length` → `BodyPart.values()[partOrdinal]`, иначе `null`.
  - В `server.execute(...)`: если `player.currentScreenHandler instanceof TarkovScreenHandler tsh` → `tsh.handleHealPart(player, srcLoc, srcIdx, srcEntryId, targetPart)`.
- **Обработчик `TarkovScreenHandler.handleHealPart(...)` и критичная персистентность NBT:**
  - `handleHealPart` разрешает источники **только** из собственного инвентаря игрока (`LOC_POCKET`, `LOC_RIG_GRID`, `LOC_BACKPACK_GRID`).
  - `handleUseItem` для медицины разрешает собственный инвентарь + обысканные внешние гриды (`allowExternal = true`).
  - **ВАЖНО (защита от потери NBT и дюпов):** `peekSource` возвращает копию грида из `ContainerItemHelper.readRigGrids`/`readBackpackGrid`. Поэтому `applyMedicalFromSource` обязан модифицировать `ItemStack` внутри реального контейнера и **записывать грид обратно**:
    - `LOC_POCKET`: модифицировать `loadout.getPocket(srcIdx)` на месте; если после `applyMedical` стек пуст → `loadout.setPocket(srcIdx, ItemStack.EMPTY)`.
    - `LOC_RIG_GRID`: проверить `requiresSearch`/`isEntryDiscovered`, прочитать `readRigGrids(rig)`, найти `GridEntry`, применить `MedicalHelper.applyMedical(...)` к `entry.getStack()`, если `entry.getStack().isEmpty()` → `grid.remove(srcEntryId)`, и при `res.applied()` записать `ContainerItemHelper.writeRigGrids(rig, grids)`.
    - `LOC_BACKPACK_GRID`: аналогично через `readBackpackGrid(bp)` / `writeBackpackGrid(bp, grid)`.
    - Внешние гриды (только при `allowExternal == true`): аналогично с проверкой `isGridRevealed && isEntryDiscovered` + `markExternalDirty()`.
  - Проверить `!serverPlayer.getItemCooldownManager().isCoolingDown(medItem)`.
  - При `res.applied()`: применить `exhaustion`, выставить cooldown, обновить `serverPlayer.setHealth(...)`, сыграть `MedicalHelper.playMedicalSound(...)`, вызвать `holder.tarkovcraft$syncBodyHealth()`, `markExternalDirty()`, `syncToClient(serverPlayer)`.
  - При `!res.applied()`: отправить actionbar `message.tarkovcraft.med_invalid_target` (если задана часть) или `message.tarkovcraft.med_not_needed`, ничего не мутируя.

---

## 6. UI, HUD и фикс Z-слоя подсветки драга

### 6.1 Фикс неблокера `#22` (`TarkovInventoryScreen.renderDragPreview`)

- В `TarkovUiTheme`: добавить константу:
  ```java
  public static final int Z_DRAG_HIGHLIGHT = 260;
  ```
  (строго выше `Z_SLOTS = 130` и строго ниже `Z_DRAG_GHOST = 320`).
- В `TarkovInventoryScreen.renderDragPreview(DrawContext context, int mouseX, int mouseY)`:
  - Обернуть отрисовку зелёной/красной подсветки (`if (hovered != null) { ... }`) в:
    ```java
    context.getMatrices().push();
    context.getMatrices().translate(0.0f, 0.0f, TarkovUiTheme.Z_DRAG_HIGHLIGHT);
    // context.fill(...) + context.drawBorder(...)
    context.getMatrices().pop();
    ```

### 6.2 Отображение ресурса медицины в сетке (`TarkovInventoryScreen.drawCellCornerTags` + `TarkovUiTheme.itemCategoryTint`)

- В `TarkovUiTheme.itemCategoryTint(ItemStack)`: для `TarkovMedicalItem` возвращать `0xCC182824` (чистый медицинский тёмно-бирюзовый оттенок).
- В `TarkovInventoryScreen.drawCellCornerTags(...)`:
  - Если `stack.getItem() instanceof TarkovMedicalItem med`: вывести в правом нижнем углу `cur + "/" + max` (цвет зелёный/жёлтый/красный по доле остатка), аналогично прочности `TarkovArmorItem`.

### 6.3 Компактный HUD (`BodyHealthHudHelper.drawCompactTopRight`)

- Силуэт в правом верхнем углу (`boxW = 48, boxH = 66`) сохраняется.
- Суммарное HP снизу силуэта показывает `curTotal + "/" + Math.round(health.getTotalEffectiveMaxHp())`.
- Сразу **под** рамкой силуэта (`py = y + boxH + 3`, выравнивание по правому краю `x + boxW`) рисовать компактные бейджи (высота `10px`, шаг `12px`) **только** для активных статусов:
  - Тяжёлое кровотечение (`count > 0`): фон `0xDD7A1414`, рамка `0xFFFF4444`, текст `"HB x" + count` (`0xFFFFCCCC`).
  - Лёгкое кровотечение (`count > 0`): фон `0xDD6E2A16`, рамка `0xFFFF8855`, текст `"LB x" + count` (`0xFFFFDDCC`).
  - Переломы (`count > 0`): фон `0xDD4D3C12`, рамка `0xFFE0B23B`, текст `"FR x" + count` (`0xFFFFEEAA`).
  - Активный обезбол (`painkillerTicks > 0`): фон `0xDD143B34`, рамка `0xFF4FD1C5`, текст `"PK " + ((painkillerTicks + 19) / 20) + "s"` (`0xFFB2F5EA`).

### 6.4 Интерактивная вкладка `ЗДОРОВЬЕ` (`BodyHealthHudHelper` + `TarkovInventoryScreen`)

- Сохранить существующий 7-аргументный `BodyHealthHudHelper.drawDetailedHealthTab(...)` (делегирует в расширенную перегрузку) — **не ломать** вкладку `СНАРЯЖЕНИЕ`, блок `ARMOR COVERAGE` и теги `[RICOCHET]` / `[ARMOR]` в `RECENT HITS`.
- Добавить public record `BodyHealthHudHelper.PartHitbox(BodyPart part, int x, int y, int w, int h)` с методом `contains(double mx, double my)`.
- Расширенная перегрузка:
  ```java
  public static void drawDetailedHealthTab(
      DrawContext context, TextRenderer textRenderer, PlayerEntity player,
      int x, int y, int w, int h,
      @Nullable ItemStack selectedMedStack,
      @Nullable List<PartHitbox> outHitboxes,
      int mouseX, int mouseY
  )
  ```
- **Изменения в `drawSilhouetteWithZoneBars` / `drawZoneBar`:**
  - Если `outHitboxes != null`, записывать прямоугольник каждого элемента силуэта И каждой полосы `drawZoneBar` (все 7 частей кликабельны как по силуэту, так и по карточке части тела!).
  - В `drawZoneBar`:
    - Справа выводить `cur + "/" + effMax` (если `effMax < baseMax`, цифру `effMax` подсветить приглушённым `0xFF9AA0A8`).
    - Полоса прогресса:
      - Фон `0xFF0E1114` на всю ширину `barW`.
      - Если `effMax < baseMax`: на правом участке `[effW .. barW]` рисовать тёмно-серую невосстановимую зону (`0xFF34383E`).
      - Живое HP заполняется от `0` до `Math.round(barW * health.getBaseRatio(part))` цветом `hpToColor(health.getRatio(part))`.
    - **Теги травм** прямо внутри/под карточкой части (увеличить высоту карточки `bh = 22` и шаг между карточками сделать компактным `24px`, либо рисовать цветные микро-теги в нижней строке карточки `y + 15`):
      - `HEAVY` (`0xFFFF4444`), `BLEED` (`0xFFFF8855`), `FRAC` (`0xFFE0B23B`), `BLACKED` (`0xFFE53935`).
    - **Подсветка при выбранной медицине (`selectedMedStack != null`):**
      - Если `MedicalHelper.canApplyToPart(health, selectedMedStack, part)` == `true`: рамка карточки `0xFF4CAF50` (при наведении мыши — `0xFFD4B46A` + лёгкая заливка `0x334CAF50`).
- **Сводка штрафов и обезбола** (1 строка под силуэтом, над `ARMOR COVERAGE`):
  - Показывает активные эффекты: `LIMP` / `NO SPRINT`, `ARM TREMOR`, `STOMACH DRAIN`, `PAINKILLER: Xs`, либо `HEALTHY` (зелёный).
- **Интерактивная полоса `МЕДИЦИНА` во вкладке `ЗДОРОВЬЕ` (`TarkovInventoryScreen`):**
  - Выделить нижнюю/правую полосу (например, справа от центральной зоны шириной ~`124px` на широких экранах или полосой шириной `panelW` высотой `34px` прямо над нижней строкой `STATS_BAR_H` — горизонтальная полоса снизу гарантированно не ломает раскладку на любом разрешении, включая `854x480`!).
  - Заголовок полосы: `ui.tarkovcraft.medical_strip` (`MEDICAL` / `МЕДИЦИНА`) + краткая подсказка `ui.tarkovcraft.medical_hint` (`Click med -> Click body part | Shift+Click = Auto`).
  - Собирать все `TarkovMedicalItem` из собственных `POCKETS`, `RIG` (если `gridRevealed`) и `BACKPACK` (если `gridRevealed`) в `List<HealthMedSlot>` (хранит `loc, idx, entryId, stack, Rect`).
  - При отрисовке выбранный `selectedHealthMed` выделяется рамкой `TarkovUiTheme.ACCENT_SAND`.
  - **Обработка кликов в `TarkovInventoryScreen.mouseClicked` при `activeTab == TAB_HEALTH`:**
    - ПКМ: снимает `selectedHealthMed`.
    - ЛКМ по слоту в полосе `МЕДИЦИНА`:
      - Если зажат `Shift` ИЛИ предмет — `ANALGIN` → сразу отправить `ClientNetworkSender.sendHealPart(slot.loc, slot.idx, slot.entryId, null)`.
      - Иначе: выбрать/переключить `selectedHealthMed = slot`.
    - ЛКМ по `PartHitbox` из `healthPartHitboxes`, когда `selectedHealthMed != null`:
      - Если `MedicalHelper.canApplyToPart(ClientLoadoutCache.getBodyHealth(), selStack, hit.part())`: отправить `ClientNetworkSender.sendHealPart(sel.loc, sel.idx, sel.entryId, hit.part())`.
  - При наведении мыши на предмет в полосе `МЕДИЦИНА` показывать стандартный плоский EFT-тултип.
  - Если после обновления инвентаря выбранный `selectedHealthMed` исчез (израсходован) — автоматически сбрасывать `selectedHealthMed = null`.

---

## 7. Команды, ресурсы (`assets-worker`) и план тестов

### 7.1 Команды в `com.tarkovcraft.command.TarkovCommand`

Добавить подветки в `/tarkov` (permission level 2):
- `/tarkov meds`: выдаёт игроку по 1 шт. всех 9 медицинских предметов (`BANDAGE`, `CAT_TOURNIQUET`, `SPLINT`, `ALU_SPLINT`, `ANALGIN`, `AI2_MEDKIT`, `SALEWA_MEDKIT`, `CMS_KIT`, `SURV12_KIT`) через `loadout.tryInsertItem(stack, player.getUuid())` (если нет места — `player.dropItem(stack, false)`), затем `holder.tarkovcraft$syncLoadout()`.
- `/tarkov bleed <part> <light|heavy>`:
  - Подсказки для `<part>`: все `BodyPart::getId`.
  - Подсказки для `<type>`: `List.of("light", "heavy")`.
  - Накладывает `LIGHT_BLEED` или `HEAVY_BLEED`, синхронизирует `tarkovcraft$syncBodyHealth()`.
- `/tarkov fracture <part>`:
  - Подсказки: `List.of("left_arm", "right_arm", "left_leg", "right_leg")`.
  - Если часть — не конечность, вернуть ошибку обратной связью; иначе наложить `FRACTURE` и синхронизировать `tarkovcraft$syncBodyHealth()`.

### 7.2 Задачи для `assets-worker` (`src/main/resources/assets/tarkovcraft/**`)

**1. 9 моделей предметов** (`src/main/resources/assets/tarkovcraft/models/item/<id>.json`), формат:
```json
{ "parent": "minecraft:item/generated", "textures": { "layer0": "tarkovcraft:item/<id>" } }
```

**2. 9 текстур 16×16 RGBA PNG** (`src/main/resources/assets/tarkovcraft/textures/item/<id>.png`):

| ID | Модель | Текстура | Визуальный стиль (16×16) |
| :--- | :--- | :--- | :--- |
| `bandage` | `models/item/bandage.json` | `textures/item/bandage.png` | Белый/кремовый рулон стерильного бинта с тёмной обводкой |
| `cat_tourniquet` | `models/item/cat_tourniquet.json` | `textures/item/cat_tourniquet.png` | Чёрный тактический жгут CAT с красным фиксатором и серым воротком |
| `splint` | `models/item/splint.json` | `textures/item/splint.png` | Бежево-деревянная медицинская шина с белыми ремешками |
| `alu_splint` | `models/item/alu_splint.json` | `textures/item/alu_splint.png` | Сине-серебристая складная алюминиевая шина (SAM/Alu) |
| `analgin` | `models/item/analgin.json` | `textures/item/analgin.png` | Серебристый блистер с 4 белыми таблетками и зелёной полоской |
| `ai2_medkit` | `models/item/ai2_medkit.json` | `textures/item/ai2_medkit.png` | Классическая оранжевая квадратная аптечка АИ-2 с белым крестом |
| `salewa_medkit` | `models/item/salewa_medkit.json` | `textures/item/salewa_medkit.png` | Красная вертикальная аптечка Salewa с чёрной молнией и белым крестом |
| `cms_kit` | `models/item/cms_kit.json` | `textures/item/cms_kit.png` | Тёмно-оливковый компактный хирургический набор с медицинским крестом |
| `surv12_kit` | `models/item/surv12_kit.json` | `textures/item/surv12_kit.png` | Крупный тактический кейс Surv12 (песочно-чёрный) с красным крестом |

**3. Новые ключи локализации** (добавить в `en_us.json` и `ru_ru.json`, сохранив все 114 существующих ключей и синхронность наборов ключей):

| Ключ | EN (`en_us.json`) | RU (`ru_ru.json`) |
| :--- | :--- | :--- |
| `item.tarkovcraft.bandage` | `Aseptic Bandage` | `Асептический бинт` |
| `item.tarkovcraft.cat_tourniquet` | `CAT Hemostatic Tourniquet` | `Жгут кровоостанавливающий CAT` |
| `item.tarkovcraft.splint` | `Immobilizing Splint` | `Иммобилизующая шина` |
| `item.tarkovcraft.alu_splint` | `Aluminum Splint` | `Алюминиевая шина` |
| `item.tarkovcraft.analgin` | `Analgin Painkillers` | `Обезболивающее «Анальгин»` |
| `item.tarkovcraft.ai2_medkit` | `AI-2 Medkit` | `Аптечка АИ-2` |
| `item.tarkovcraft.salewa_medkit` | `Salewa First Aid Kit` | `Аптечка Salewa` |
| `item.tarkovcraft.cms_kit` | `CMS Surgical Kit` | `Хирургический набор CMS` |
| `item.tarkovcraft.surv12_kit` | `Surv12 Field Surgical Kit` | `Полевой хирургический набор Surv12` |
| `injury.tarkovcraft.light_bleed` | `Light Bleeding` | `Лёгкое кровотечение` |
| `injury.tarkovcraft.heavy_bleed` | `Heavy Bleeding` | `Тяжёлое кровотечение` |
| `injury.tarkovcraft.fracture` | `Fracture` | `Перелом` |
| `tooltip.tarkovcraft.med_pool` | `HP Resource: %s/%s` | `Ресурс ОЗ: %s/%s` |
| `tooltip.tarkovcraft.med_uses` | `Uses: %s/%s` | `Заряды: %s/%s` |
| `tooltip.tarkovcraft.med_max_heal` | `Max HP per use: %s` | `Макс. лечение за раз: %s ОЗ` |
| `tooltip.tarkovcraft.med_cures_light_bleed` | `Stops Light Bleeding` | `Останавливает лёгкое кровотечение` |
| `tooltip.tarkovcraft.med_cures_light_bleed_cost` | `Stops Light Bleeding (-%s HP pool)` | `Останавливает лёгкое кровотечение (-%s ресурса)` |
| `tooltip.tarkovcraft.med_cures_heavy_bleed` | `Stops Heavy Bleeding` | `Останавливает тяжёлое кровотечение` |
| `tooltip.tarkovcraft.med_cures_heavy_bleed_cost` | `Stops Heavy Bleeding (-%s HP pool)` | `Останавливает тяжёлое кровотечение (-%s ресурса)` |
| `tooltip.tarkovcraft.med_cures_fracture` | `Treats Fracture` | `Лечит перелом` |
| `tooltip.tarkovcraft.med_painkiller` | `Painkiller effect: %ss` | `Обезболивание: %s сек` |
| `tooltip.tarkovcraft.med_surgery` | `Restores blacked limb (max HP: %s%%)` | `Восстанавливает выбитую часть (макс. ОЗ: %s%%)` |
| `tooltip.tarkovcraft.med_weight` | `Weight: %s kg` | `Вес: %s кг` |
| `message.tarkovcraft.med_not_needed` | `No treatable injuries or damage` | `Нет подходящих травм или повреждений` |
| `message.tarkovcraft.med_invalid_target` | `Cannot apply this medical item to that body part` | `Нельзя применить эту медицину к данной части тела` |
| `health.tarkovcraft.tag_heavy_bleed` | `HEAVY` | `ТЯЖ.КР` |
| `health.tarkovcraft.tag_light_bleed` | `BLEED` | `КРОВЬ` |
| `health.tarkovcraft.tag_fracture` | `FRAC` | `ПЕРЕЛ` |
| `health.tarkovcraft.tag_blacked` | `BLACKED` | `ВЫБИТО` |
| `health.tarkovcraft.status_healthy` | `STATUS: HEALTHY` | `СТАТУС: ЗДОРОВ` |
| `health.tarkovcraft.status_painkiller` | `PAINKILLER %ss` | `ОБЕЗБОЛ %sс` |
| `health.tarkovcraft.penalty_limp` | `LIMP / NO SPRINT` | `ХРОМОТА / БЕЗ БЕГА` |
| `health.tarkovcraft.penalty_arms` | `ARM INJURY` | `ТРАВМА РУК` |
| `health.tarkovcraft.penalty_stomach` | `STOMACH DRAIN` | `ИСТОЩЕНИЕ (ЖИВОТ)` |
| `ui.tarkovcraft.medical_strip` | `MEDICAL` | `МЕДИЦИНА` |
| `ui.tarkovcraft.medical_hint` | `Select med -> Click body part (Shift+Click: Auto)` | `Выбери медицину -> Клик по части тела (Shift+Клик: Авто)` |
| `ui.tarkovcraft.no_meds` | `No medical items in pockets, rig, or backpack` | `Нет медицины в карманах, разгрузке и рюкзаке` |

*(Примечание: `data/tarkovcraft/item_sizes/default_sizes.json` менять не нужно — размеры `TarkovMedicalItem` резолвятся напрямую в `ItemSizeRegistry.getSize` как у `TarkovArmorItem`.)*

### 7.3 План JUnit 5 тестов для `java-coder`

Создать `src/test/java/com/tarkovcraft/health/MedicalAndInjuryTest.java` (с `@BeforeAll` bootstrap как в `BodyPartHealthTest`):
1. `fallDamageScalesWithHeightAndWeightAndBlacksBothLegs`:
   - Лёгкое падение (`vanilla=1.5`, `weight=10`) → лёгкий урон по обеим ногам, нет переломов/выбивания.
   - Среднее падение (`vanilla=4.0`, `weight=10`) → значимый урон обеим ногам + `FRACTURE` на обеих ногах.
   - Высокое падение (`vanilla=6.5`, `weight=10`) → ОБЕ ноги выбиты (`0 HP`), обе с `FRACTURE`, есть `LIGHT_BLEED`.
   - Перегруз (`vanilla=5.0`, `weight=50`) → усиливает урон и переливает избыток в `THORAX`.
   - Экстремальное падение (`vanilla=11.0`, `weight=10`) → перелив в `THORAX` до `0 HP` → `killed == true`, `lastFatalReport` записан.
2. `bleedingDrainsOverTimeOverflowsAndCanKill`:
   - `LIGHT_BLEED` за 40 тиков снимает `1.5 HP`; `HEAVY_BLEED` за 40 тиков снимает `4.0 HP`.
   - Кровотечение на выбитой ноге (`0 HP`) переливает урон в `THORAX`.
   - Длительное тяжёлое кровотечение доводит `THORAX` до `0`, `killedByBloodLoss == true`, `lastFatalReport.killerName().equals("Blood Loss")`.
3. `limbPenaltiesAndPainkillerSprintDamage`:
   - 1 сломанная/выбитая нога → `impairedLegs == 1`, 2 ноги → `impairedLegs == 2`.
   - С активным обезболом (`Analgin` = `1800` тиков): `painkillerActive == true`, спринт на выбитых ногах каждые 20 тиков триггерит `sprintPainTriggered == true` и снимает `1.5 HP` (перелив в `THORAX`).
   - Сломанные/выбитые руки → `impairedArms == 1 / 2`.
   - Выбитый `STOMACH` каждые 40 тиков возвращает `exhaustionToAdd > 0`.
4. `surgeryCmsAndSurv12Rules`:
   - `CMS_KIT` восстанавливает выбитую `LEFT_LEG` (`0 -> 1 HP`), режет `effectiveMaxHp` до `Math.round(65 * 0.55f) = 36`, тратит 1 заряд (`5 -> 4`), последующее лечение не поднимает HP выше `36`.
   - `CMS_KIT` отклоняет `HEAD`, `THORAX` и невыбитую ногу (заряды не тратятся).
   - `SURV12_KIT` восстанавливает выбитую сломанную `RIGHT_LEG` (`0 -> 1 HP`), режет `effectiveMaxHp` до `Math.round(65 * 0.80f) = 52`, И одновременно снимает `FRACTURE` за 1 заряд (`15 -> 14`).
5. `medkitPoolsAi2AndSalewaAndAutoTargetPriority`:
   - `AI2_MEDKIT` (пул `100`): лечит не более `50 HP` за раз, не снимает кровотечения, не лечит выбитые части, в auto-target ставит повреждённые `HEAD`/`THORAX` выше конечностей.
   - `SALEWA_MEDKIT` (пул `400`): в auto-target сначала снимает `HEAVY_BLEED` (`-135` -> `265`), затем `LIGHT_BLEED` (`-45` -> `220`), затем лечит HP до `85` за раз; при обнулении пула стек становится пустым (`stack.isEmpty()`).
6. `bandagesTourniquetSplintsAndAnalginUses`:
   - `BANDAGE` (2 заряда -> только `LIGHT_BLEED`), `CAT_TOURNIQUET` (1 заряд -> `HEAVY_BLEED`, исчезает), `SPLINT` (1 заряд) и `ALU_SPLINT` (5 зарядов -> `FRACTURE`), `ANALGIN` (4 заряда -> `1800` тиков + `exhaustionToAdd == 1.5f`).
   - Применение любого медпредмета на здорового игрока возвращает `applied == false` и не тратит ресурс.
7. `bodyPartHealthNbtRoundTripAndLegacyCompatibility`:
   - Сохранение/загрузка `effectiveMaxHp`, всех `InjuryType`, `painkillerTicks`.
   - Загрузка старого NBT (без ключей `"EffectiveMaxHp"`, `"Injuries"`, `"PainkillerTicks"`) не падает и выставляет полные базовые максимумы без травм.

---

## 8. Границы файлов и критерии приёмки

- **`java-coder` (`src/main/java/**`, `src/test/java/**`):**
  - Новые файлы: `InjuryType.java`, `MedicalHelper.java`, `BodyHealthTicker.java`, `MedicalType.java`, `TarkovMedicalItem.java`, `MedicalAndInjuryTest.java`.
  - Изменяемые файлы: `BodyPartHealth.java`, `PlayerEntityMixin.java`, `TarkovCraftMod.java`, `ModItems.java`, `ItemSizeRegistry.java`, `ItemRarityRegistry.java`, `PlayerLoadout.java`, `TarkovNetworking.java`, `ClientNetworkSender.java`, `TarkovScreenHandler.java`, `TarkovCommand.java`, `TarkovUiTheme.java`, `BodyHealthHudHelper.java`, `TarkovInventoryScreen.java`.
- **`assets-worker` (`src/main/resources/assets/tarkovcraft/**`):**
  - 9 моделей `models/item/<id>.json`, 9 текстур 16×16 `textures/item/<id>.png`, синхронизированные `lang/en_us.json` и `lang/ru_ru.json`.
- **Запрещено трогать:** `TactEq`, `tarkovcraft.mixins.json`, `fabric.mod.json`, `build.gradle`.
- **Верификация:** `.\gradlew.bat build --no-daemon` (JDK 17) — компиляция, все старые + новые JUnit 5 тесты зелёные, `remapJar` успешен, клиентские импорты не просачиваются в common-классы.
