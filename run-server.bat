@echo off
REM ============================================================
REM  ValorantMC — one-click Fabric dev launcher (Windows)
REM ============================================================
REM
REM  HOW IT WORKS:
REM    Server : Fabric MC 1.21.4  — runs the ValorantMC mod
REM             (all game logic: agents, weapons, economy, rounds)
REM    Client : Same Fabric mod installed in .minecraft\mods
REM             (HUD, crosshair, buy screen, minimap, keybinds)
REM
REM  STEPS:
REM    1. Find Java 21  (auto-detects; downloads Temurin 21 if missing)
REM    2. Build mod     (Gradle — downloads Gradle 8.12 once to tools\)
REM    3. Install Fabric server 1.21.4 in run\server\ (once)
REM    4. Deploy mod → run\server\mods\ and run\client-mods\
REM    5. Launch Fabric server
REM ============================================================

setlocal enabledelayedexpansion

set TOOLS_DIR=%~dp0tools
set RUN_DIR=%~dp0run
set SERVER_DIR=%RUN_DIR%\server
set CLIENT_MODS_DIR=%RUN_DIR%\client-mods

set MC_VERSION=1.20.1
set LOADER_VERSION=0.19.2
set FABRIC_INSTALLER_VERSION=1.0.1
set FABRIC_INSTALLER_JAR=fabric-installer-%FABRIC_INSTALLER_VERSION%.jar
set GRADLE_VERSION=9.4.1
set GRADLE_BAT=%TOOLS_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat

if not exist "%TOOLS_DIR%"       mkdir "%TOOLS_DIR%"
if not exist "%RUN_DIR%"         mkdir "%RUN_DIR%"
if not exist "%SERVER_DIR%"      mkdir "%SERVER_DIR%"
if not exist "%CLIENT_MODS_DIR%" mkdir "%CLIENT_MODS_DIR%"

echo.
echo ============================================================
echo  ValorantMC Dev Launcher  ^|  Fabric MC %MC_VERSION%
echo ============================================================
echo.

REM ═══════════════════════════════════════════════════════════════
REM  STEP 1 — Find Java 17
REM ═══════════════════════════════════════════════════════════════
echo [1/4] Locating Java 17...

if not exist "%TOOLS_DIR%\jdk17\jdk-17.0.12+7\bin\java.exe" (
    echo        Downloading OpenJDK 17...
    mkdir "%TOOLS_DIR%\jdk17" 2>nul
    powershell -Command "Invoke-WebRequest -Uri 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.12_7.zip' -OutFile '%TOOLS_DIR%\jdk17\jdk.zip'"
    echo        Extracting...
    powershell -Command "Expand-Archive -Path '%TOOLS_DIR%\jdk17\jdk.zip' -DestinationPath '%TOOLS_DIR%\jdk17' -Force"
    del "%TOOLS_DIR%\jdk17\jdk.zip"
)
set JAVA_HOME=%TOOLS_DIR%\jdk17\jdk-17.0.12+7
set PATH=%JAVA_HOME%\bin;%PATH%
echo        Found: %JAVA_HOME%

REM ═══════════════════════════════════════════════════════════════
REM  STEP 2 — Build Paper Server Plugin (Maven)
REM ═══════════════════════════════════════════════════════════════
echo [2/4] Building ValorantMC Paper plugin...

call "%~dp0build-plugin.bat"
if errorlevel 1 (
    echo.
    echo  ERROR: Plugin build failed. See output above.
    pause & exit /b 1
)
echo        OK: target\ValorantMC-1.0.0.jar

REM ═══════════════════════════════════════════════════════════════
REM  STEP 3 — Check Paper 1.21.4 server (once)
REM ═══════════════════════════════════════════════════════════════
echo [3/4] Checking Paper 1.21.4 server...

if not exist "%SERVER_DIR%\mohist-1.20.1.jar" (
    powershell -Command "Invoke-WebRequest -Uri 'https://mohistmc.com/api/v2/projects/mohist/1.20.1/builds/latest/download' -OutFile '%SERVER_DIR%\mohist-1.20.1.jar'"
    if errorlevel 1 (
        echo  ERROR: Mohist server download failed.
        pause & exit /b 1
    )
)
>"%SERVER_DIR%\eula.txt" echo eula=true

REM Server properties (create once)
if not exist "%SERVER_DIR%\server.properties" (
    (
        echo allow-flight=true
        echo motd=ValorantMC Paper Server
        echo server-port=25565
        echo gamemode=adventure
        echo difficulty=normal
        echo spawn-protection=0
        echo view-distance=10
        echo max-players=20
        echo enable-command-block=true
    ) > "%SERVER_DIR%\server.properties"
)

if not exist "%SERVER_DIR%\plugins" mkdir "%SERVER_DIR%\plugins"

REM ═══════════════════════════════════════════════════════════════
REM  STEP 4 — Deploy plugin to server plugins folder
REM ═══════════════════════════════════════════════════════════════
echo [4/4] Deploying ValorantMC plugin...

copy /y "%~dp0target\ValorantMC-1.0.0.jar" "%SERVER_DIR%\plugins\" >nul
if errorlevel 1 (echo  WARNING: plugin copy failed) else (
    echo        Plugin  -^> run\server\plugins\ValorantMC-1.0.0.jar
)

REM ═══════════════════════════════════════════════════════════════
REM  LAUNCH
REM ═══════════════════════════════════════════════════════════════
echo.
echo ============================================================
echo  Starting Paper MC %MC_VERSION% on port 25565
echo.
echo  Vanilla players can connect directly!
echo  (Custom weapon models served automatically via resource pack)
echo.
echo  In-game commands:
echo    /valorant join — join match
echo    /vagent        — pick agent
echo    /vshop         — open buy menu
echo    /vmapsetup     — admin map wizard
echo  Ctrl-C to stop.
echo ============================================================
echo.

cd /d "%SERVER_DIR%"
"%JAVA_HOME%\bin\java" -Xms2G -Xmx4G ^
    -XX:+UseG1GC -XX:+ParallelRefProcEnabled ^
    -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions ^
    -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ^
    -jar mohist-1.20.1.jar --nogui

endlocal
