@echo off
REM ============================================================
REM  ValorantMC — one-click local dev launcher (Windows)
REM ============================================================
REM
REM  HOW IT WORKS:
REM    Server : Paper 1.21.4  — runs the ValorantMC plugin
REM             (all game logic: agents, weapons, economy, rounds)
REM    Client : Fabric 1.21.4 — install the mod in .minecraft\mods
REM             (HUD, crosshair, buy screen, minimap, keybinds)
REM    Bridge : Plugin messaging channels (valorantmc:hud etc.)
REM             The Fabric client mod connects to Paper exactly
REM             like any other Minecraft client — no Fabric server
REM             needed. The mod works with any server type.
REM
REM  STEPS:
REM    1. Find Java 21  (auto-detects; downloads Temurin 21 if missing)
REM    2. Build plugin  (Maven)
REM    3. Build mod     (Gradle — downloads Gradle 8.12 once to tools\)
REM    4. Download Paper 1.21.4 if missing
REM    5. Deploy plugin → run\plugins\
REM       Deploy mod    → run\client-mods\  (copy to .minecraft\mods)
REM    6. Launch Paper server
REM ============================================================

setlocal enabledelayedexpansion

set TOOLS_DIR=%~dp0tools
set RUN_DIR=%~dp0run
set PLUGINS_DIR=%RUN_DIR%\plugins
set CLIENT_MODS_DIR=%RUN_DIR%\client-mods

set PAPER_VERSION=1.21.4
set PAPER_BUILD=198
set PAPER_JAR=paper-%PAPER_VERSION%-%PAPER_BUILD%.jar
set GRADLE_VERSION=8.12
set GRADLE_BAT=%TOOLS_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat

if not exist "%TOOLS_DIR%"       mkdir "%TOOLS_DIR%"
if not exist "%RUN_DIR%"         mkdir "%RUN_DIR%"
if not exist "%PLUGINS_DIR%"     mkdir "%PLUGINS_DIR%"
if not exist "%CLIENT_MODS_DIR%" mkdir "%CLIENT_MODS_DIR%"

echo.
echo ============================================================
echo  ValorantMC Dev Launcher  ^|  Paper 1.21.4 / Fabric 1.21.4
echo ============================================================
echo.

REM ═══════════════════════════════════════════════════════════════
REM  STEP 1 — Find Java 21+
REM  Checks: PATH, common install dirs, then downloads Temurin 21
REM ═══════════════════════════════════════════════════════════════
echo [1/5] Locating Java 21...

REM Use PowerShell to find Java 21+ from PATH or common directories
for /f "delims=" %%J in ('powershell -NoProfile -Command " ^
  $javaHome = $null; ^
  $javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source; ^
  if ($javaExe) { ^
    $ver = ((& $javaExe -version 2^>^&1)[0] -replace '.*version \x22(\d+).*','$1'); ^
    if ([int]$ver -ge 21) { $javaHome = Split-Path (Split-Path $javaExe) } ^
  } ^
  if (-not $javaHome) { ^
    $bases = @('C:\Program Files\Eclipse Adoptium','C:\Program Files\Java', ^
               'C:\Program Files\Microsoft','C:\Program Files\BellSoft', ^
               'C:\Program Files\Zulu','C:\Program Files\Amazon Corretto', ^
               ($env:LOCALAPPDATA+'\Programs\Eclipse Adoptium'), ^
               ($env:USERPROFILE+'\.jdks'),($env:USERPROFILE+'\scoop\apps'), ^
               'C:\scoop\apps','C:\tools'); ^
    foreach ($b in $bases) { ^
      if (Test-Path $b) { ^
        $dirs = Get-ChildItem $b -Directory -ErrorAction SilentlyContinue ^| ^
                Where-Object {$_.Name -match 'jdk.*(21)'} ^| ^
                Sort-Object Name -Descending; ^
        foreach ($d in $dirs) { ^
          $exe = Join-Path $d.FullName 'bin\java.exe'; ^
          if (-not (Test-Path $exe)) {$exe = Join-Path $d.FullName 'current\bin\java.exe'} ^
          if (Test-Path $exe) {$javaHome = $d.FullName; break} ^
        } ^
      } ^
      if ($javaHome) {break} ^
    } ^
  } ^
  if ($javaHome) {Write-Output $javaHome} else {Write-Output 'NOTFOUND'} ^
"') do set JAVA_HOME=%%J

if /i "%JAVA_HOME%"=="NOTFOUND" (
    echo        Java 21 not found. Downloading Temurin 21 LTS...
    powershell -NoProfile -Command " ^
      $api = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?os=windows&architecture=x64&image_type=jdk'; ^
      $url = $api[0].binary.package.link; ^
      $zip = '%TOOLS_DIR%\temurin21.zip'; ^
      Write-Host ('  -> ' + $url); ^
      Invoke-WebRequest -Uri $url -OutFile $zip; ^
      Expand-Archive -Path $zip -DestinationPath '%TOOLS_DIR%\jdk21' -Force; ^
      Remove-Item $zip ^
    "
    if errorlevel 1 (
        echo.
        echo  ERROR: Could not download Java 21.
        echo         Install Temurin 21 from https://adoptium.net then re-run.
        pause & exit /b 1
    )
    for /d %%D in ("%TOOLS_DIR%\jdk21\jdk-21*") do set JAVA_HOME=%%D
)

if not defined JAVA_HOME (
    echo  ERROR: Java 21 not found and download failed.
    echo         Set JAVA_HOME manually and re-run.
    pause & exit /b 1
)

set PATH=%JAVA_HOME%\bin;%PATH%
echo        Found: %JAVA_HOME%
for /f "tokens=*" %%V in ('"%JAVA_HOME%\bin\java" -version 2^>^&1') do (
    echo        %%V & goto :java_done
)
:java_done

REM ═══════════════════════════════════════════════════════════════
REM  STEP 2 — Build Paper plugin (Maven)
REM ═══════════════════════════════════════════════════════════════
echo [2/5] Building Paper plugin...
call mvn clean package -q
if errorlevel 1 (
    echo.
    echo  ERROR: Maven build failed. See output above.
    pause & exit /b 1
)
echo        OK: target\ValorantMC-1.0.0.jar

REM ═══════════════════════════════════════════════════════════════
REM  STEP 3 — Build Fabric client mod (Gradle)
REM ═══════════════════════════════════════════════════════════════
echo [3/5] Building Fabric client mod...

if not exist "%GRADLE_BAT%" (
    echo        Downloading Gradle %GRADLE_VERSION%...
    powershell -NoProfile -Command " ^
      Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' ^
                        -OutFile '%TOOLS_DIR%\gradle.zip'; ^
      Expand-Archive '%TOOLS_DIR%\gradle.zip' '%TOOLS_DIR%' -Force; ^
      Remove-Item '%TOOLS_DIR%\gradle.zip' ^
    "
    if errorlevel 1 (
        echo  ERROR: Failed to download Gradle. Check internet connection.
        pause & exit /b 1
    )
)

echo        (First run downloads ~150 MB of MC 1.21.4 + Fabric API)
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
REM  STEP 4 — Download Paper 1.21.4 if missing
REM ═══════════════════════════════════════════════════════════════
echo [4/5] Checking Paper server...

if not exist "%RUN_DIR%\%PAPER_JAR%" (
    echo        Downloading Paper %PAPER_VERSION% build %PAPER_BUILD%...
    powershell -NoProfile -Command " ^
      Invoke-WebRequest ^
        'https://api.papermc.io/v2/projects/paper/versions/%PAPER_VERSION%/builds/%PAPER_BUILD%/downloads/%PAPER_JAR%' ^
        -OutFile '%RUN_DIR%\%PAPER_JAR%' ^
    "
    if errorlevel 1 (
        echo  ERROR: Failed to download Paper. Check internet connection.
        pause & exit /b 1
    )
    echo        Downloaded.
) else (
    echo        Already present.
)

REM EULA + server.properties
>"%RUN_DIR%\eula.txt" echo eula=true
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

REM ═══════════════════════════════════════════════════════════════
REM  STEP 5 — Deploy JARs
REM ═══════════════════════════════════════════════════════════════
echo [5/5] Deploying JARs...

del /q "%PLUGINS_DIR%\ValorantMC-*.jar"   >nul 2>&1
copy /y "%~dp0target\ValorantMC-1.0.0.jar" "%PLUGINS_DIR%\" >nul
if errorlevel 1 (echo  WARNING: plugin copy failed) else (
    echo        Plugin  → run\plugins\ValorantMC-1.0.0.jar
)

del /q "%CLIENT_MODS_DIR%\valorantmc-mod-*.jar" >nul 2>&1
copy /y "%~dp0mod\build\libs\valorantmc-mod-1.0.0.jar" "%CLIENT_MODS_DIR%\" >nul
if errorlevel 1 (echo  WARNING: mod copy failed) else (
    echo        Mod     → run\client-mods\valorantmc-mod-1.0.0.jar
)

REM ═══════════════════════════════════════════════════════════════
REM  LAUNCH
REM ═══════════════════════════════════════════════════════════════
echo.
echo ============================================================
echo  Starting Paper 1.21.4 on port 25565
echo.
echo  Connect with: Fabric loader 0.16.9 for MC 1.21.4
echo  Install mod:  copy run\client-mods\valorantmc-mod-1.0.0.jar
echo                  to %%APPDATA%%\.minecraft\mods\
echo  Ctrl-C to stop.
echo ============================================================
echo.

cd /d "%RUN_DIR%"
"%JAVA_HOME%\bin\java" -Xms2G -Xmx4G ^
    -XX:+UseG1GC -XX:+ParallelRefProcEnabled ^
    -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions ^
    -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ^
    -jar "%PAPER_JAR%" --nogui

endlocal
