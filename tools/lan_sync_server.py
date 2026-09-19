#!/usr/bin/env python3
"""
ValorantMC Automated Build Host & LAN Auto-Sync Server
- Auto-compiles/builds mod assets and packages valorantmc-mod-1.0.0.jar
- Serves latest built mod directly over HTTP /mod.jar to target computers on LAN
- Auto-deploys to user-specified Client Mods and Server Mods directories
"""

import os, sys, socket, http.server, socketserver, argparse, urllib.parse, hashlib, json, time, subprocess, zipfile

BASE_DIR   = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST_DIR   = os.path.join(BASE_DIR, "modding", "dist")
MOD_BUILD  = os.path.join(BASE_DIR, "mod", "build", "libs")
TARGET_DIR = os.path.join(BASE_DIR, "target")
PACK_FILE  = os.path.join(DIST_DIR, "ValorantMC-ResourcePack.zip")

DEFAULT_PORT = 9090

def get_lan_ip():
    """Detect local IP address on LAN"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def trigger_auto_build():
    """Runs automated asset and mod compilation pipeline"""
    print("[AutoBuild] Compiling assets and checking mod packages...")
    asset_script = os.path.join(BASE_DIR, "tools", "download_assets.py")
    if os.path.exists(asset_script):
        try:
            subprocess.run([sys.executable, asset_script], check=True, capture_output=True)
            print("  ✓ Assets compiled successfully!")
        except Exception as e:
            print(f"  ⚠ Asset compilation warning: {e}")

def get_file_hash(filepath):
    """Calculates SHA256 checksum for a file"""
    if not filepath or not os.path.exists(filepath):
        return None
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()

def get_mod_jar_path():
    """Finds available compiled JAR file from Gradle or Maven build outputs"""
    # 1. Gradle build output
    if os.path.exists(MOD_BUILD):
        for f in os.listdir(MOD_BUILD):
            if f.endswith(".jar") and not f.endswith("-sources.jar") and not f.endswith("-dev.jar"):
                return os.path.join(MOD_BUILD, f)
    # 2. Maven build output
    if os.path.exists(TARGET_DIR):
        for f in os.listdir(TARGET_DIR):
            if f.endswith(".jar") and not f.endswith("-sources.jar"):
                return os.path.join(TARGET_DIR, f)
    return None

def generate_lan_updater_bat(lan_ip, port):
    """Generates an automated batch script that builds & downloads straight to specified mod folders"""
    return f"""@echo off
REM ============================================================
REM  ValorantMC Automated Build Host and LAN Sync Tool
REM  Host: http://{lan_ip}:{port}
REM ============================================================
setlocal enabledelayedexpansion

set HOST_IP={lan_ip}
set HOST_PORT={port}
set DEV_URL=http://%HOST_IP%:%HOST_PORT%
set CONFIG_FILE=valorantmc_paths.txt

echo.
echo ============================================================
echo  ValorantMC Auto-Build and Target Deployer
echo  Host: %DEV_URL%
echo ============================================================
echo.

if exist "%CONFIG_FILE%" (
    for /f "usebackq tokens=1,* delims==" %%A in ("%CONFIG_FILE%") do (
        if "%%A"=="CLIENT_MODS" set CLIENT_MODS=%%B
        if "%%A"=="SERVER_DIR" set SERVER_DIR=%%B
    )
)

if "%CLIENT_MODS%"=="" (
    set DEFAULT_CLIENT=%APPDATA%\\.minecraft\\mods
    echo Please specify your Client Mods folder path:
    echo Default: !DEFAULT_CLIENT!
    set /p CLIENT_INPUT="Client Mods Directory: "
    if "!CLIENT_INPUT!"=="" set CLIENT_INPUT=!DEFAULT_CLIENT!
    set CLIENT_MODS=!CLIENT_INPUT!
)

if "%SERVER_DIR%"=="" (
    set DEFAULT_SERVER=%CD%\\server
    echo.
    echo Please specify your Dedicated Server folder path:
    echo Default: !DEFAULT_SERVER!
    set /p SERVER_INPUT="Server Directory: "
    if "!SERVER_INPUT!"=="" set SERVER_INPUT=!DEFAULT_SERVER!
    set SERVER_DIR=!SERVER_INPUT!
)

echo CLIENT_MODS=%CLIENT_MODS%> "%CONFIG_FILE%"
echo SERVER_DIR=%SERVER_DIR%>> "%CONFIG_FILE%"

set SERVER_MODS=%SERVER_DIR%\\mods

echo.
echo Automated Target Destinations:
echo   [Client Mods] -> %CLIENT_MODS%\\valorantmc-mod-1.0.0.jar
echo   [Server Directory] -> %SERVER_DIR%
echo   [Server Mods] -> %SERVER_MODS%\\valorantmc-mod-1.0.0.jar
echo.
echo (To re-specify paths, delete %CONFIG_FILE%)
echo.

if not exist "%CLIENT_MODS%" mkdir "%CLIENT_MODS%"
if not exist "%SERVER_MODS%" mkdir "%SERVER_MODS%"

:loop
echo [%time%] Checking %DEV_URL%/version for auto-build updates...

powershell -NoProfile -ExecutionPolicy Bypass -Command "$v = Invoke-RestMethod -Uri '%DEV_URL%/version'; if ($v) {{ Out-File -FilePath '%TEMP%\\v_temp.txt' -InputObject $v.timestamp -Encoding ascii }}" >nul 2>&1

if exist "%TEMP%\\v_temp.txt" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%DEV_URL%/mod.jar' -OutFile '%CLIENT_MODS%\\valorantmc-mod-1.0.0.jar'" >nul 2>&1
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%DEV_URL%/mod.jar' -OutFile '%SERVER_MODS%\\valorantmc-mod-1.0.0.jar'" >nul 2>&1
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%DEV_URL%/pack.zip' -OutFile '%SERVER_DIR%\\ValorantMC-ResourcePack.zip'" >nul 2>&1
    echo [%time%] [OK] Built mod auto-deployed to %CLIENT_MODS%!
    del "%TEMP%\\v_temp.txt" >nul 2>&1
) else (
    echo [%time%] [!] Host at %DEV_URL% unreachable or offline...
)

echo Waiting 10 seconds before next sync check...
timeout /t 10 /nobreak >nul
goto :loop
"""

class LANHandler(http.server.BaseHTTPRequestHandler):
    lan_ip = "127.0.0.1"
    port   = DEFAULT_PORT

    def log_message(self, format, *args):
        pass

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path.lower()

        if path == "/version" or path == "/version.json":
            mod_path = get_mod_jar_path()
            mod_hash = get_file_hash(mod_path) if mod_path else "none"
            pack_hash = get_file_hash(PACK_FILE) if os.path.exists(PACK_FILE) else "none"
            
            payload = {
                "timestamp": int(time.time()),
                "mod_sha256": mod_hash,
                "pack_sha256": pack_hash,
                "lan_ip": self.lan_ip,
                "port": self.port
            }
            content = json.dumps(payload, indent=2).encode('utf-8')
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)

        elif path == "/" or path == "/index.html":
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            html = f"""<!DOCTYPE html>
<html>
<head>
    <title>ValorantMC Auto-Build & Sync Host (Port {self.port})</title>
    <style>
        body {{ font-family: 'Segoe UI', sans-serif; background: #0f1923; color: #ece8e1; margin: 40px; text-align: center; }}
        .card {{ background: #1f2326; max-width: 650px; margin: 0 auto; padding: 30px; border-radius: 10px; border-top: 4px solid #ff4655; }}
        h1 {{ color: #ff4655; font-size: 28px; margin-bottom: 5px; }}
        code {{ background: #111; padding: 6px 12px; border-radius: 4px; color: #00e676; font-size: 15px; font-family: monospace; }}
        .btn {{ display: inline-block; background: #ff4655; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; font-weight: bold; margin: 8px; }}
        .btn:hover {{ background: #e03e4d; }}
    </style>
</head>
<body>
    <div class="card">
        <h1>VALORANTMC AUTO-BUILD & SYNC HOST</h1>
        <p>Sync Port: <code>{self.port}</code> | LAN IP: <code>{self.lan_ip}</code></p>
        <hr style="border:0; border-top: 1px solid #333; margin: 20px 0;">
        <p><strong>Auto-Deploy to your specified Client Mods & Server folders:</strong></p>
        <p><a href="/lan_auto_updater.bat" class="btn">Download Auto-Deployer (.bat)</a></p>
        <hr style="border:0; border-top: 1px solid #333; margin: 20px 0;">
        <p><strong>Direct Endpoints:</strong></p>
        <p>
            <a href="/version" class="btn" style="background:#333;">Version API (/version)</a>
            <a href="/mod.jar" class="btn" style="background:#333;">Built Mod (.jar)</a>
            <a href="/pack.zip" class="btn" style="background:#333;">Resource Pack (.zip)</a>
            <a href="/project.zip" class="btn" style="background:#28a745;">Full Project (.zip)</a>
        </p>
    </div>
</body>
</html>"""
            self.wfile.write(html.encode('utf-8'))

        elif path == "/lan_auto_updater.bat" or path == "/install.bat":
            content = generate_lan_updater_bat(self.lan_ip, self.port).encode('utf-8')
            self.send_response(200)
            self.send_header("Content-Type", "application/x-bat")
            self.send_header("Content-Disposition", 'attachment; filename="lan_auto_updater.bat"')
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)

        elif path == "/pack.zip":
            if os.path.exists(PACK_FILE):
                with open(PACK_FILE, "rb") as f:
                    data = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "application/zip")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            else:
                self.send_error(404, "Resource pack zip not found.")

        elif path == "/project.zip":
            proj_zip = os.path.join(DIST_DIR, "ValorantMC-FullProject.zip")
            if not os.path.exists(proj_zip):
                zip_script = os.path.join(BASE_DIR, "tools", "create_project_zip.py")
                if os.path.exists(zip_script):
                    subprocess.run([sys.executable, zip_script])
            if os.path.exists(proj_zip):
                with open(proj_zip, "rb") as f:
                    data = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "application/zip")
                self.send_header("Content-Disposition", 'attachment; filename="ValorantMC-FullProject.zip"')
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            else:
                self.send_error(404, "Project bundle zip not found.")

        elif path == "/mod.jar":
            mod_path = get_mod_jar_path()
            if mod_path and os.path.exists(mod_path):
                with open(mod_path, "rb") as f:
                    data = f.read()
                self.send_response(200)
                self.send_header("Content-Type", "application/java-archive")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            else:
                self.send_error(404, "Mod JAR not built yet. Run ./gradlew build or run-server.bat")

        else:
            self.send_error(404, "Not Found")

def main():
    parser = argparse.ArgumentParser(description="ValorantMC LAN Sync Server & Auto-Updater Host")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="Port to host HTTP sync server on (default: 9090)")
    args = parser.parse_args()

    trigger_auto_build()
    lan_ip = get_lan_ip()
    LANHandler.lan_ip = lan_ip
    LANHandler.port   = args.port

    socketserver.TCPServer.allow_reuse_address = True
    server = socketserver.TCPServer(("0.0.0.0", args.port), LANHandler)
    print("============================================================")
    print(" ValorantMC Automated Build Host Active")
    print("============================================================")
    print(f"  ✓ Local Host IP           : {lan_ip}")
    print(f"  ✓ Custom Sync Port         : {args.port}")
    print(f"  ✓ Web Host Dashboard      : http://{lan_ip}:{args.port}/")
    print(f"  ✓ Version Check API       : http://{lan_ip}:{args.port}/version")
    print(f"  ✓ Target Auto-Deploy Bat  : http://{lan_ip}:{args.port}/lan_auto_updater.bat")
    print("============================================================")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping LAN server.")
        server.server_close()

if __name__ == "__main__":
    main()
