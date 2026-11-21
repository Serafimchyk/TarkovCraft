@echo off
cd /d "%~dp0"
title TarkovCraft Build & Deploy

color 06
cls
echo ======================================================================
echo   [TARKOVCRAFT]  BUILD ^& DEPLOY  //  MODRINTH [test]
echo ======================================================================
echo.

call gradlew.bat build
if errorlevel 1 goto :BUILD_FAILED

echo.
echo [INFO] Copying jar to Modrinth profile...
copy /Y "%~dp0build\libs\tarkovcraft-0.1.0.jar" "C:\Users\serap\AppData\Roaming\ModrinthApp\profiles\test\mods\tarkovcraft-0.1.0.jar"
if errorlevel 1 goto :COPY_FAILED

color 0A
echo.
echo ======================================================================
echo   [+] BUILD SUCCESSFUL // DEPLOYED TO MODRINTH [test]
echo ======================================================================

if exist "%~dp0celebrate.ps1" (
    powershell -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0celebrate.ps1" "%~dp0."
)

pause
exit /b 0

:BUILD_FAILED
color 0C
echo.
echo ======================================================================
echo   [!] BUILD FAILED — check Gradle error above
echo ======================================================================
powershell -NoProfile -Command "[console]::Beep(220, 400)"
pause
exit /b 1

:COPY_FAILED
color 0C
echo.
echo ======================================================================
echo   [!] COPY FAILED — close Minecraft or check Modrinth path
echo ======================================================================
powershell -NoProfile -Command "[console]::Beep(220, 400)"
pause
exit /b 1