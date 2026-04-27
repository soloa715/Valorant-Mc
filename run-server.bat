@echo off
REM ============================================================
REM  ValorantMC — one-click local dev launcher (Windows)
REM ============================================================
REM
REM  ARCHITECTURE (read before changing):
REM  ---------------------------------------------------------------
REM  SERVER  : Paper (game logic — ValorantGame, agents, weapons,
REM            economy, spike — all use Bukkit/Paper API and cannot
REM            run on a plain Fabric server without a full rewrite)
REM  CLIENT  : Fabric 1.21.4 mod (HUD overlay, crosshair, buy screen,
REM            minimap, keybindings, custom weapon textures)
REM
REM  The Fabric mod connects to the Paper server via Minecraft's
REM  plugin-message channel (valorantmc:hud, valorantmc:buymenu,
REM  valorantmc:radar, valorantmc:hello, valorantmc:buyaction).
REM  Both ends register these payload types — the mod via
REM  ValorantMCMod.onInitialize(), the server via FabricChannelListener.
REM
REM  STEPS THIS SCRIPT PERFORMS:
REM  ---------------------------------------------------------------
REM  1. Build Paper plugin     (Maven, Java 25)
REM  2. Build Fabric client mod (Gradle 8, Java 21 via foojay)
REM  3. Download Paper server   if not present
REM  4. Install plugin JAR     → run\plugins\
REM  5. Copy mod JAR           → run\client-mods\
REM     └─ Install this in %APPDATA%\.minecraft\mods on each client
REM        running a Fabric 1.21.4 loader.
REM  6. Launch Paper server
REM ============================================================

setlocal enabledelayedexpansion

REM ── Java 25 — used for Maven (Paper plugin) and running the server ──
set JAVA25_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot

REM ── Java 21 — Gradle uses this to compile the Fabric mod ──────────
REM   Leave JAVA21_HOME unset to let Gradle auto-download JDK 21 via
REM   the foojay resolver declared in mod/settings.gradle.
REM   Set it if you already have JDK 21 installed, e.g.:
REM     set JAVA21_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot
if not defined JAVA21_HOME (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set JAVA21_HOME=%%D
)
if not defined JAVA21_HOME (
    for /d %%D in ("C:\Program Files\Java\jdk-21*") do set JAVA21_HOME=%%D
)
REM If still not found, Gradle toolchain resolver will auto-provision JDK 21.

REM ── Paper server config ───────────────────────────────────────────
set PAPER_VERSION=1.21.11
set PAPER_BUILD=69
set PAPER_JAR=paper-%PAPER_VERSION%-%PAPER_BUILD%.jar
set RUN_DIR=%~dp0run
set PLUGINS_DIR=%RUN_DIR%\plugins
set CLIENT_MODS_DIR=%RUN_DIR%\client-mods

REM ── Gradle binary (downloaded on first run) ───────────────────────
set GRADLE_VERSION=8.12
set GRADLE_HOME=%~dp0tools\gradle-%GRADLE_VERSION%
set GRADLE_BAT=%GRADLE_HOME%\bin\gradle.bat

echo.
echo ============================================================
echo  ValorantMC Dev Launcher
echo  Paper server %PAPER_VERSION% ^| Fabric mod MC 1.21.4 ^| Java 25+21
echo ============================================================
echo.

REM ── Step 1: Build Paper plugin (Maven, Java 25) ───────────────────
echo [1/5] Building Paper plugin (Maven + Java 25^)...
set JAVA_HOME=%JAVA25_HOME%
set PATH=%JAVA_HOME%\bin;%PATH%

call mvn clean package -q
if errorlevel 1 (
    echo.
    echo  ERROR: Maven build failed. Fix errors above and retry.
    pause
    exit /b 1
)
echo        Plugin built OK.

REM ── Step 2: Ensure Gradle is available ───────────────────────────
if not exist "%GRADLE_BAT%" (
    echo [2/5] Downloading Gradle %GRADLE_VERSION%...
    if not exist "%~dp0tools" mkdir "%~dp0tools"
    powershell -NoProfile -Command ^
        "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%~dp0tools\gradle.zip'"
    if errorlevel 1 (
        echo  ERROR: Failed to download Gradle. Check your internet connection.
        pause
        exit /b 1
    )
    powershell -NoProfile -Command ^
        "Expand-Archive -Path '%~dp0tools\gradle.zip' -DestinationPath '%~dp0tools' -Force"
    del /q "%~dp0tools\gradle.zip"
    echo        Gradle %GRADLE_VERSION% downloaded.
) else (
    echo [2/5] Gradle %GRADLE_VERSION% already present.
)

REM ── Step 3: Build Fabric client mod (Gradle + Java 21) ───────────
echo [3/5] Building Fabric client mod (Gradle %GRADLE_VERSION% + Java 21^)...
echo        (First run downloads MC 1.21.4 + Fabric API — takes a few minutes^)

REM  If Java 21 was found above, tell Gradle where it is.
REM  Otherwise leave JAVA_HOME as Java 25 and let foojay auto-provision Java 21.
if defined JAVA21_HOME (
    echo        Using Java 21 at: %JAVA21_HOME%
    set JAVA_HOME=%JAVA21_HOME%
    set PATH=%JAVA21_HOME%\bin;%PATH%
)

pushd "%~dp0mod"
call "%GRADLE_BAT%" build -q --no-daemon 2>&1
if errorlevel 1 (
    popd
    echo.
    echo  ERROR: Fabric mod build failed. Fix errors above and retry.
    pause
    exit /b 1
)
popd

REM Restore Java 25 for the server
set JAVA_HOME=%JAVA25_HOME%
set PATH=%JAVA_HOME%\bin;%PATH%
echo        Fabric mod built OK.

REM ── Step 4: Prepare run directory ────────────────────────────────
if not exist "%RUN_DIR%"         mkdir "%RUN_DIR%"
if not exist "%PLUGINS_DIR%"     mkdir "%PLUGINS_DIR%"
if not exist "%CLIENT_MODS_DIR%" mkdir "%CLIENT_MODS_DIR%"

REM Accept EULA automatically (dev environment)
>"%RUN_DIR%\eula.txt" echo eula=true

REM Minimal server.properties
if not exist "%RUN_DIR%\server.properties" (
    (
        echo online-mode=false
        echo motd=ValorantMC Dev Server
        echo server-port=25565
        echo gamemode=adventure
        echo difficulty=normal
        echo spawn-protection=0
        echo view-distance=10
        echo max-players=20
    ) > "%RUN_DIR%\server.properties"
)

REM ── Step 5a: Download Paper if missing ───────────────────────────
if not exist "%RUN_DIR%\%PAPER_JAR%" (
    echo [4/5] Downloading Paper %PAPER_VERSION% build %PAPER_BUILD%...
    powershell -NoProfile -Command ^
        "Invoke-WebRequest -Uri 'https://api.papermc.io/v2/projects/paper/versions/%PAPER_VERSION%/builds/%PAPER_BUILD%/downloads/%PAPER_JAR%' -OutFile '%RUN_DIR%\%PAPER_JAR%'"
    if errorlevel 1 (
        echo  ERROR: Failed to download Paper. Check your internet connection.
        pause
        exit /b 1
    )
    echo        Paper downloaded.
) else (
    echo [4/5] Paper %PAPER_VERSION% build %PAPER_BUILD% already present.
)

REM ── Step 5b: Deploy plugin JAR ────────────────────────────────────
del /q "%PLUGINS_DIR%\ValorantMC-*.jar" >nul 2>&1
copy /y "%~dp0target\ValorantMC-1.0.0.jar" "%PLUGINS_DIR%\" >nul
if errorlevel 1 (
    echo  ERROR: Could not copy plugin JAR. Did Maven actually produce target\ValorantMC-1.0.0.jar?
    pause
    exit /b 1
)
echo        Plugin installed  → run\plugins\ValorantMC-1.0.0.jar

REM ── Step 5c: Deploy Fabric mod JAR ───────────────────────────────
del /q "%CLIENT_MODS_DIR%\valorantmc-mod-*.jar" >nul 2>&1
copy /y "%~dp0mod\build\libs\valorantmc-mod-1.0.0.jar" "%CLIENT_MODS_DIR%\" >nul
if errorlevel 1 (
    echo  WARNING: Could not copy Fabric mod JAR.
    echo           Expected: mod\build\libs\valorantmc-mod-1.0.0.jar
) else (
    echo        Mod JAR staged → run\client-mods\valorantmc-mod-1.0.0.jar
    echo        Install this in: %%APPDATA%%\.minecraft\mods
    echo        (Requires Fabric loader 0.16.9+ for Minecraft 1.21.4^)
)

REM ── Step 5d: Deploy TaCZ gunpack as datapack ─────────────────────
if exist "%~dp0modding\tacz-extract\data" (
    set DATAPACK_DIR=%RUN_DIR%\world\datapacks\valorant-gunpack
    if not exist "!DATAPACK_DIR!" (
        echo        Installing TaCZ gunpack datapack...
        mkdir "!DATAPACK_DIR!" >nul 2>&1
        mkdir "!DATAPACK_DIR!\data" >nul 2>&1
        xcopy /s /y /q "%~dp0modding\tacz-extract\data" "!DATAPACK_DIR!\data" >nul
        >"!DATAPACK_DIR!\pack.mcmeta" echo {"pack":{"pack_format":57,"description":"Valorant Gunpack"}}
    )
)

REM ── Step 6: Launch Paper server ───────────────────────────────────
echo.
echo [5/5] Starting Paper server on port 25565 (Java 25^)...
echo.
echo ============================================================
echo  Server:  localhost:25565  (Paper %PAPER_VERSION%^)
echo  Mod JAR: run\client-mods\valorantmc-mod-1.0.0.jar
echo           → copy to %%APPDATA%%\.minecraft\mods on each client
echo  Ctrl-C to stop.
echo ============================================================
echo.

cd /d "%RUN_DIR%"
"%JAVA25_HOME%\bin\java" -Xms2G -Xmx6G ^
    -XX:+UseG1GC -XX:+ParallelRefProcEnabled ^
    -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions ^
    -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ^
    -jar "%PAPER_JAR%" --nogui

endlocal
