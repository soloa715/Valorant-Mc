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
        "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /C:"25" >nul 2>&1
        if not errorlevel 1 goto :java_found
    )
    set JAVA_HOME=
)

REM --- 2. Use java on PATH if it is version 25 ---
where java >nul 2>&1
if not errorlevel 1 (
    java -version 2>&1 | findstr /C:"25" >nul 2>&1
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
REM  STEP 2 — Build Fabric mod (Gradle)
REM ═══════════════════════════════════════════════════════════════
echo [2/4] Building Fabric mod...

if not exist "%GRADLE_BAT%" (
    echo        Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%TOOLS_DIR%\gradle.zip'; Expand-Archive '%TOOLS_DIR%\gradle.zip' '%TOOLS_DIR%' -Force; Remove-Item '%TOOLS_DIR%\gradle.zip'"
    if errorlevel 1 (
        echo  ERROR: Failed to download Gradle. Check internet connection.
        pause & exit /b 1
    )
)

echo        (First run downloads ~150 MB of MC %MC_VERSION% + Fabric API)
pushd "%~dp0mod"
call "%GRADLE_BAT%" build -q --no-daemon
if errorlevel 1 (
    popd
    echo.
    echo  ERROR: Fabric mod build failed. See output above.
    pause & exit /b 1
)
popd
echo        OK: mod\build\libs\valorantmc-mod-1.0.0.jar

REM ═══════════════════════════════════════════════════════════════
REM  STEP 3 — Install Fabric server (once)
REM ═══════════════════════════════════════════════════════════════
echo [3/4] Checking Fabric server...

if not exist "%SERVER_DIR%\fabric-server-launch.jar" (
    echo        Installing Fabric %MC_VERSION% server loader %LOADER_VERSION%...

    REM Download fabric-installer if needed
    if not exist "%TOOLS_DIR%\%FABRIC_INSTALLER_JAR%" (
        echo        Downloading Fabric installer %FABRIC_INSTALLER_VERSION%...
        powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest 'https://maven.fabricmc.net/net/fabricmc/fabric-installer/%FABRIC_INSTALLER_VERSION%/fabric-installer-%FABRIC_INSTALLER_VERSION%.jar' -OutFile '%TOOLS_DIR%\%FABRIC_INSTALLER_JAR%'"
        if errorlevel 1 (
            echo  ERROR: Failed to download Fabric installer. Check internet.
            pause & exit /b 1
        )
    )

    REM Run installer — installs into SERVER_DIR
    "%JAVA_HOME%\bin\java" -jar "%TOOLS_DIR%\%FABRIC_INSTALLER_JAR%" server -mcversion %MC_VERSION% -loader %LOADER_VERSION% -downloadMinecraft -dir "%SERVER_DIR%"
    if errorlevel 1 (
        echo.
        echo  ERROR: Fabric server installation failed.
        pause & exit /b 1
    )
    echo        Fabric server installed.

    REM Accept EULA
    >"%SERVER_DIR%\eula.txt" echo eula=true
) else (
    echo        Already installed.
)

REM Server properties (create once)
if not exist "%SERVER_DIR%\server.properties" (
    (
        echo online-mode=false
        echo motd=ValorantMC Dev Server
        echo server-port=25565
        echo gamemode=adventure
        echo difficulty=normal
        echo spawn-protection=0
        echo view-distance=10
        echo max-players=20
        echo enable-command-block=true
    ) > "%SERVER_DIR%\server.properties"
)

if not exist "%SERVER_DIR%\mods" mkdir "%SERVER_DIR%\mods"

REM ═══════════════════════════════════════════════════════════════
REM  STEP 4 — Deploy mod to server and client-mods folder
REM ═══════════════════════════════════════════════════════════════
echo [4/4] Deploying mod...

REM Download Fabric API if not already present
set FABRIC_API_JAR=%SERVER_DIR%\mods\fabric-api-0.110.5+1.21.4.jar
if not exist "%FABRIC_API_JAR%" (
    echo        Downloading Fabric API 0.110.5+1.21.4...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.110.5+1.21.4/fabric-api-0.110.5+1.21.4.jar' -OutFile '%FABRIC_API_JAR%'"
    if errorlevel 1 (echo  WARNING: Fabric API download failed) else (echo        Fabric API downloaded.)
)

del /q "%SERVER_DIR%\mods\valorantmc-mod-*.jar"    >nul 2>&1
copy /y "%~dp0mod\build\libs\valorantmc-mod-1.0.0.jar" "%SERVER_DIR%\mods\" >nul
if errorlevel 1 (echo  WARNING: server mod copy failed) else (
    echo        Server  -^> run\server\mods\valorantmc-mod-1.0.0.jar
)

del /q "%CLIENT_MODS_DIR%\valorantmc-mod-*.jar"    >nul 2>&1
copy /y "%~dp0mod\build\libs\valorantmc-mod-1.0.0.jar" "%CLIENT_MODS_DIR%\" >nul
if errorlevel 1 (echo  WARNING: client-mods copy failed) else (
    echo        Client  -^> run\client-mods\valorantmc-mod-1.0.0.jar
)

REM ═══════════════════════════════════════════════════════════════
REM  LAUNCH
REM ═══════════════════════════════════════════════════════════════
echo.
echo ============================================================
echo  Starting Fabric MC %MC_VERSION% on port 25565
echo.
echo  To connect: use Fabric loader %LOADER_VERSION% for MC %MC_VERSION%
echo  Install mod: copy run\client-mods\valorantmc-mod-1.0.0.jar
echo               to %%APPDATA%%\.minecraft\mods\
echo.
echo  In-game commands:
echo    /vjoin         — join default game
echo    /vagent <name> — pick your agent
echo    /vstart        — admin: start the match
echo    /vshop         — open buy menu (or press B)
echo    /vquick        — quick play
echo  Ctrl-C to stop.
echo ============================================================
echo.

cd /d "%SERVER_DIR%"
"%JAVA_HOME%\bin\java" -Xms2G -Xmx4G ^
    -XX:+UseG1GC -XX:+ParallelRefProcEnabled ^
    -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions ^
    -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ^
    -jar fabric-server-launch.jar --nogui

endlocal
