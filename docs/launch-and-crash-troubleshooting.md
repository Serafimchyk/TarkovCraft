# Запуск TarkovCraft и диагностика вылетов

## Сборка и запуск в Modrinth

1. Закрой Minecraft. Открой PowerShell в корне проекта и проверь, что используется JDK 17.

   ```powershell
   $env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
   & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version
   .\gradlew.bat compileJava test build --no-daemon
   ```

   Первая команда проверки должна показать `17.x`. После `BUILD SUCCESSFUL` готовый мод находится в `build\libs\tarkovcraft-0.1.0.jar`.

2. В Modrinth используй профиль `test` с Minecraft **1.20.1**, загрузчиком **Fabric**, Java **17** и Fabric API **0.92.12+1.20.1**. Проект собирается с Fabric Loader **0.16.10**, а установленный профиль проверен при запуске с **0.20.1**. Для проверки оружейных звуков используй **TaCZ Refabricated** для Fabric 1.20.1. В профиле `test` установлена версия `1.0.1-forge1.2.0-hotfix+mc1.20.1`.

3. Замени JAR в папке модов этого профиля. Проверь, что в `mods` осталась одна активная копия TarkovCraft. Готовый JAR называется `tarkovcraft-0.1.0.jar`. Файл с суффиксом `-sources.jar` содержит исходники.

   ```powershell
   $mods = Join-Path $env:APPDATA 'ModrinthApp\profiles\test\mods'
   Copy-Item -LiteralPath '.\build\libs\tarkovcraft-0.1.0.jar' -Destination $mods -Force -ErrorAction Stop
   Get-ChildItem -LiteralPath $mods -Filter '*tarkovcraft*.jar'
   ```

4. Запусти `test` из Modrinth. Сначала дождись главного меню, затем проверь новый одиночный мир. Для проверки дальних выстрелов и голосовой связи на выделенном сервере обнови TarkovCraft и на клиенте, и на сервере. Сервер тоже запускается на Java 17 и Fabric 1.20.1.

## Если игра снова вылетает

Запомни время вылета и последнее действие. Для диагностики важно, на каком этапе он произошёл: до меню, при загрузке мира, при подключении к серверу или во время игры. Сохрани лог **сразу после падения**. Следующий запуск перезаписывает `latest.log`.

| Где произошёл вылет | Что открыть |
| --- | --- |
| Minecraft закрылся до меню или в игре | `%APPDATA%\ModrinthApp\profiles\test\logs\latest.log` и самый свежий файл в `crash-reports` того же профиля |
| Modrinth показал код выхода, а время `latest.log` не обновилось | Журнал запуска в Modrinth или свежий файл в `%APPDATA%\ModrinthApp\launcher_logs` |
| При подключении закрывается или перезапускается выделенный сервер | Серверные `logs/latest.log` и `crash-reports` за то же время |

Если отчёт есть, сохрани исключение со всеми строками `Caused by`. Если отчёт не появился, сохрани последние 120 строк свежего лога. Код выхода сам по себе редко показывает причину.

```powershell
$profileDir = Join-Path $env:APPDATA 'ModrinthApp\profiles\test'
$latest = Join-Path $profileDir 'logs\latest.log'
Get-Item -LiteralPath $latest | Select-Object LastWriteTime, Length
Get-Content -LiteralPath $latest -Tail 120
Get-ChildItem -LiteralPath (Join-Path $profileDir 'crash-reports') -Filter '*.txt' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 FullName, LastWriteTime
```

Изолированный запуск с JAR из профиля `test`, TaCZ Refabricated, Simple Voice Chat и Voice Physics дошёл до сообщения `Sound engine started` в OpenAL. Эта проверка охватывает запуск клиента. Для падения при входе в мир или на сервер нужен лог именно с этого этапа.
