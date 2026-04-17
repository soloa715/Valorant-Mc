@echo off
REM ============================================================
REM  ValorantMC — one-click local dev server launcher (Windows)
REM ============================================================
REM  1. Sets JAVA_HOME to Java 25 (Adoptium)
REM  2. Builds the plugin with Maven (Java 25)
REM  3. Downloads Paper 1.21.11 (MC 26.1.2) if not present
REM  4. Copies the built JAR into run/plugins/
REM  5. Launches the server
REM ============================================================

setlocal enabledelayedexpansion

REM ── Java 25 (Adoptium) ──────────────────────────────────────
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

REM ── Server config ───────────────────────────────────────────
set PAPER_VERSION=1.21.11
set PAPER_BUILD=69
set PAPER_JAR=paper-%PAPER_VERSION%-%PAPER_BUILD%.jar
set RUN_DIR=%~dp0run
set PLUGINS_DIR=%RUN_DIR%\plugins

echo.
echo ============================================================
echo  ValorantMC Dev Server Launcher  ^|  MC 26.1.2 / Java 25
echo ============================================================
echo.

REM ── Step 1: Build plugin ────────────────────────────────────
echo [1/4] Building plugin with Maven (Java 25)...
call mvn clean package -q
if errorlevel 1 (
    echo.
    echo ERROR: Maven build failed. Fix errors above and retry.
    pause
    exit /b 1
)
echo       Build OK.

REM ── Step 2: Prepare run directory ───────────────────────────
if not exist "%RUN_DIR%"     mkdir "%RUN_DIR%"
if not exist "%PLUGINS_DIR%" mkdir "%PLUGINS_DIR%"

REM Accept EULA automatically (dev environment)
>"%RUN_DIR%\eula.txt" echo eula=true

REM Minimal server.properties for local dev
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

REM ── Step 3: Download Paper if missing ───────────────────────
if not exist "%RUN_DIR%\%PAPER_JAR%" (
    echo [2/4] Downloading Paper %PAPER_VERSION% build %PAPER_BUILD% (MC 26.1.2^)...
    powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://api.papermc.io/v2/projects/paper/versions/%PAPER_VERSION%/builds/%PAPER_BUILD%/downloads/%PAPER_JAR%' -OutFile '%RUN_DIR%\%PAPER_JAR%'"
    if errorlevel 1 (
        echo ERROR: Failed to download Paper. Check your internet connection.
        pause
        exit /b 1
    )
    echo       Paper downloaded.
) else (
    echo [2/4] Paper %PAPER_VERSION% build %PAPER_BUILD% already present. Skipping download.
)

REM ── Step 4: Copy plugin JAR ─────────────────────────────────
echo [3/4] Copying plugin to run/plugins/...
del /q "%PLUGINS_DIR%\ValorantMC-*.jar" >nul 2>&1
copy /y "%~dp0target\ValorantMC-1.0.0.jar" "%PLUGINS_DIR%\" >nul
if errorlevel 1 (
    echo ERROR: Could not copy plugin JAR. Did Maven actually produce it?
    pause
    exit /b 1
)
echo       Plugin installed.

REM ── Step 4b: Deploy TaCZ gunpack as datapack ────────────────
if exist "%~dp0modding\tacz-extract\data" (
    set DATAPACK_DIR=%RUN_DIR%\world\datapacks\valorant-gunpack
    if not exist "!DATAPACK_DIR!" (
        echo [3b/4] Installing TaCZ gunpack datapack...
        mkdir "!DATAPACK_DIR!" >nul 2>&1
        mkdir "!DATAPACK_DIR!\data" >nul 2>&1
        xcopy /s /y /q "%~dp0modding\tacz-extract\data" "!DATAPACK_DIR!\data" >nul
        >"!DATAPACK_DIR!\pack.mcmeta" echo {"pack":{"pack_format":57,"description":"Valorant Gunpack"}}
    )
)

REM ── Step 5: Launch server ───────────────────────────────────
echo [4/4] Starting Paper server on port 25565...
echo.
echo ============================================================
echo  Server starting. Ctrl-C to stop.
echo  Connect from Minecraft 26.1.2 to:  localhost
echo ============================================================
echo.

cd /d "%RUN_DIR%"
"%JAVA_HOME%\bin\java" -Xms2G -Xmx6G ^
     -XX:+UseG1GC -XX:+ParallelRefProcEnabled ^
     -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions ^
     -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ^
     -jar "%PAPER_JAR%" --nogui

endlocal
