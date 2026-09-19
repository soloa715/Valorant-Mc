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

set MC_VERSION=1.21.4
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
REM  STEP 1 — Find Java 25+
REM  Checks: JAVA_HOME env, PATH, common install dirs, tools\jdk25
REM  Downloads Temurin 25 automatically if nothing works
REM ═══════════════════════════════════════════════════════════════
echo [1/4] Locating Java 25...
set JAVA_HOME=

REM --- 1. Use %JAVA_HOME% if already set and points to Java 25 ---
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /C:"version ""25" >nul 2>&1
        if not errorlevel 1 goto :java_found
    )
    set JAVA_HOME=
)

REM --- 2. Use java on PATH if it is version 25 ---
where java >nul 2>&1
if not errorlevel 1 (
    java -version 2>&1 | findstr /C:"version ""25" >nul 2>&1
    if not errorlevel 1 (
        for /f "delims=" %%J in ('where java') do (
            if not defined JAVA_HOME (
                for %%H in ("%%~dpJ..") do set JAVA_HOME=%%~fH
            )
        )
        if defined JAVA_HOME goto :java_found
    )
)

REM --- 3. Scan common install directories ---
for %%B in ("C:\Program Files\Eclipse Adoptium" "C:\Program Files\Java" "C:\Program Files\Microsoft" "C:\Program Files\BellSoft" "C:\Program Files\Zulu") do (
    if not defined JAVA_HOME (
        for /d %%D in (%%~B\jdk-25* %%~B\temurin-25* %%~B\jdk25*) do (
            if not defined JAVA_HOME (
                if exist "%%D\bin\java.exe" set JAVA_HOME=%%D
            )
        )
    )
)
if defined JAVA_HOME goto :java_found

REM --- 4. Check our own tools\jdk25 (previously downloaded) ---
for /d %%D in ("%TOOLS_DIR%\jdk25\jdk-25*" "%TOOLS_DIR%\jdk25\jdk25*") do (
    if not defined JAVA_HOME (
        if exist "%%D\bin\java.exe" set JAVA_HOME=%%D
    )
)
if defined JAVA_HOME goto :java_found

REM --- 5. Download Temurin 25 ---
echo        Java 25 not found. Downloading Temurin 25 LTS...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$a=Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/25/hotspot?os=windows&architecture=x64&image_type=jdk';$u=$a[0].binary.package.link;Write-Host('  -> '+$u);Invoke-WebRequest -Uri $u -OutFile '%TOOLS_DIR%\temurin25.zip';Expand-Archive '%TOOLS_DIR%\temurin25.zip' '%TOOLS_DIR%\jdk25' -Force;Remove-Item '%TOOLS_DIR%\temurin25.zip'"
if errorlevel 1 (
    echo.
    echo  ERROR: Could not download Java 25.
    echo         Install Temurin 25 from https://adoptium.net then re-run.
    pause & exit /b 1
)
for /d %%D in ("%TOOLS_DIR%\jdk25\jdk-25*") do set JAVA_HOME=%%D

:java_found
if not defined JAVA_HOME (
    echo  ERROR: Java 25 not found and download failed. Set JAVA_HOME manually.
    pause & exit /b 1
)

set PATH=%JAVA_HOME%\bin;%PATH%
echo        Found: %JAVA_HOME%
for /f "tokens=*" %%V in ('"%JAVA_HOME%\bin\java" -version 2^>^&1') do (
    echo        %%V & goto :java_done
)
:java_done

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

if not exist "%SERVER_DIR%\paper-1.21.4.jar" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%TOOLS_DIR%\download_paper.ps1"
    if errorlevel 1 (
        echo  ERROR: Paper server download failed.
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
    -jar paper-1.21.4.jar --nogui

endlocal
