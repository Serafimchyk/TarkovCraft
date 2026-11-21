---
name: golden-balance
description: >-
  Запускает разработку задач по моду TarkovCraft (Minecraft 1.20.1 Fabric) через
  команду «Золотой баланс»: Neon (high/medium) для архитектуры и ревью + Hydrogen
  (high/medium) для Java-кода и JSON-ресурсов. Использовать, когда пользователь
  просит выполнить фичу, рефакторинг, фикс или добавить контент командой агентов
  или по схеме «Золотой баланс».
---

# Плейбук оркестратора: «Золотой баланс» (TarkovCraft)

Используй этот навык, когда пользователь просит разработать фичу, исправить баг, добавить контент или провести рефакторинг с помощью команды агентов проекта.

## Шаг 0. Подготовка оркестратора и выбор режима

1. Убедись, что прочитаны `AGENTS.md` и (при необходимости) `TZ.md`.
2. **Целевая модель оркестратора:** `Gemini 4 Neon (Medium)` (`gemini-4-neon-medium`).
   - Если текущая сессия в Swarm-режиме запущена на более дорогой модели и задача типовая, при желании можно переключить себя на `gemini-4-neon-medium` только по согласованию с правилами (или оставить текущую, если пользователь сам выбрал `Neon High`).
   - **Запрещено:** автоматически включать `gemini-4-neon-xhigh` или назначать `gemini-4-hydrogen-low` на Java/Mixin/Gradle.
3. **Предпочтительный движок запуска:** **`swarm`**, потому что только `swarm{action:"add", model:"..."}` гарантирует точное закрепление моделей и уровней мышления (`gemini-4-neon-high`, `gemini-4-neon-medium`, `gemini-4-hydrogen-high`, `gemini-4-hydrogen-medium`).
   - Поскольку репозиторий сейчас без `.git`, для всех воркеров передавай `workspace:"inherit"` и **никогда** не назначай двум воркерам пересекающиеся файлы.

## Шаг 1. Классификация задачи

Выбери один из двух маршрутов:

- **Маршрут А — Простая фича** (не меняет `build.gradle`/зависимости, маппинги, сетевой протокол, `fabric.mod.json`, `tarkovcraft.mixins.json`, `@Inject`/`@Redirect`/`@Overwrite` и архитектуру):
  1. `java-coder` (`gemini-4-hydrogen-high`) — реализация в `src/main/java` и `src/test/java` + прогон `compileJava test` с JDK 17.
  2. Параллельно (если нужны JSON-ассеты/переводы/data): `assets-worker` (`gemini-4-hydrogen-medium`) — правки строго в `src/main/resources/assets/**` и `src/main/resources/data/**`.
  3. `reviewer` (`gemini-4-neon-medium`) — read-only аудит диффа, границы клиент/сервер и прогон проверочных команд.

- **Маршрут Б — Сложная фича / зависимости / Mixin / сеть / AccessWidener:**
  1. `architect` (`gemini-4-neon-high`) — проверяет реальные Yarn-сигнатуры (по коду, `.gradle/loom-cache/`, `build/loom-cache/*.tiny`, `javap` или `genSources`), при необходимости правит `build.gradle` / `fabric.mod.json` / `tarkovcraft.mixins.json`, и публикует точную спеку.
  2. `java-coder` (`gemini-4-hydrogen-high`) — реализует строго по спеке `architect` (+ `assets-worker` на `gemini-4-hydrogen-medium`, если нужны ресурсы).
  3. `java-coder` прогоняет `.\gradlew.bat build --no-daemon` с JDK 17.
  4. `reviewer` (`gemini-4-neon-medium`) — полный read-only ревью; для критичных изменений Mixin/сети/зависимостей финальную проверку выполняет `architect` (`gemini-4-neon-high`).

## Шаг 2. Шаблоны спавна воркеров через `swarm`

Добавляй **только те роли**, которые реально нужны текущей задаче (не держи лишних пустых воркеров). В каждом `brief` обязательно ссылайся на файл контракта `.agents/agents/<role>.md`:

- **Для `architect`:**
  ```text
  swarm{
    action: "add",
    name: "architect",
    model: "gemini-4-neon-high",
    workspace: "inherit",
    brief: "Сначала прочитай AGENTS.md и .agents/agents/architect.md и строго соблюдай свой контракт. Задача: <описание>. Проверь реальные Yarn-сигнатуры (без угадываний) и опубликуй [done] с точной спекой для java-coder (и assets-worker при необходимости)."
  }
  ```

- **Для `java-coder`:**
  ```text
  swarm{
    action: "add",
    name: "java-coder",
    model: "gemini-4-hydrogen-high",
    workspace: "inherit",
    brief: "Сначала прочитай AGENTS.md и .agents/agents/java-coder.md и строго соблюдай свой контракт. Реализуй строго в src/main/java и src/test/java: <задача или ссылка на пост #N от architect>. Прогони Gradle с JAVA_HOME='C:\\Program Files\\Microsoft\\jdk-17.0.9.8-hotspot' (compileJava, test / build) и передай @reviewer [done] со списком файлов и выводом сборки."
  }
  ```

- **Для `assets-worker`:**
  ```text
  swarm{
    action: "add",
    name: "assets-worker",
    model: "gemini-4-hydrogen-medium",
    workspace: "inherit",
    brief: "Сначала прочитай AGENTS.md и .agents/agents/assets-worker.md и строго соблюдай свой контракт. Подготовь JSON-ресурсы строго в src/main/resources/assets/tarkovcraft и src/main/resources/data/tarkovcraft: <список>. Провалидируй все JSON и передай @reviewer [done]."
  }
  ```

- **Для `reviewer`:**
  ```text
  swarm{
    action: "add",
    name: "reviewer",
    model: "gemini-4-neon-medium",
    workspace: "inherit",
    brief: "Сначала прочитай AGENTS.md и .agents/agents/reviewer.md и строго соблюдай свой read-only контракт. Проверь работу из #<seq>, границу клиент/сервер, отсутствие заглушек/костылей и прогони проверку Gradle с JDK 17. Если нужны правки — отправь замечания @java-coder / @assets-worker; если всё чисто — напиши @coordinator [done] APPROVE."
  }
  ```

## Шаг 3. Фоллбэк без Swarm (`invoke_subagent`)

Если Swarm недоступен или пользователь попросил не открывать Swarm:
1. Вызывай те же роли по имени через `invoke_subagent` (`TypeName: "architect" | "reviewer" | "java-coder" | "assets-worker"`).
2. Маппинг `Model` (единственно поддерживаемый схемой `invoke_subagent`):
   - `architect`, `reviewer` → `Model: "pro"`
   - `java-coder`, `assets-worker` → `Model: "flash"` (или `"inherit"`, если родительская сессия уже на нужной модели)
3. Явно сообщи пользователю, что в режиме `invoke_subagent` Antigravity не позволяет жёстко закрепить `Neon high/medium` и `Hydrogen high/medium` по отдельности (для точного «Золотого баланса» нужен режим Swarm).

## Шаг 4. Приёмка и завершение

- Не принимай задачу, пока:
  1. Не прошёл зелёный Gradle-прогон с JDK 17 (`compileJava` + `test`, а для сложных фич — `build`).
  2. `reviewer` не выдал `APPROVE` (или `architect` для критичных Mixin/сетевых правок).
  3. Не проверена граница клиент/сервер.
- При завершении Swarm-задачи закрой борд строкой `[done] <краткое резюме>` и отчитайся пользователю.
