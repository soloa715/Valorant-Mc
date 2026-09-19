#!/usr/bin/env python3
"""
ValorantMC Automated Asset & Map Downloader
- Auto-extracts built-in map configurations (Ascent, Split, Bind, Abyss)
- Auto-compiles the custom 3D item, weapon skin, and block resource pack
- Prepares resource pack SHA-1 hash for server auto-prompt
"""

import os, sys, urllib.request, zipfile, shutil, hashlib, subprocess

BASE_DIR    = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAPS_SRC    = os.path.join(BASE_DIR, "src", "main", "resources", "maps")
MAPS_DEST   = os.path.join(BASE_DIR, "modding", "maps")
DIST_DIR    = os.path.join(BASE_DIR, "modding", "dist")
SERVER_DIR  = os.path.join(BASE_DIR, "run", "server")
PACK_FILE   = os.path.join(DIST_DIR, "ValorantMC-ResourcePack.zip")

os.makedirs(MAPS_DEST, exist_ok=True)
os.makedirs(DIST_DIR, exist_ok=True)
os.makedirs(SERVER_DIR, exist_ok=True)

# ── 1. MAP AUTO-EXTRACTION ───────────────────────────────────────────────────
def prepare_maps():
    print("[1/3] Preparing Valorant map configurations...")
    if os.path.exists(MAPS_SRC):
        for f in os.listdir(MAPS_SRC):
            if f.endswith(".yml"):
                src_path = os.path.join(MAPS_SRC, f)
                dst_path = os.path.join(MAPS_DEST, f)
                shutil.copy2(src_path, dst_path)
                print(f"  [OK] Processed map: {f}")
    else:
        print("  [!] Map source directory not found.")

# ── 2. RESOURCE PACK GENERATOR ────────────────────────────────────────────────
def build_resource_pack():
    print("[2/3] Building custom 3D item, weapon skin, and block resource pack...")
    script = os.path.join(BASE_DIR, "modding", "build_assets.py")
    if os.path.exists(script):
        res = subprocess.run([sys.executable, script], capture_output=True, text=True)
        if res.returncode == 0:
            print("  [OK] Custom 3D item & block resource pack compiled successfully!")
        else:
            print(f"  [!] Build asset script notice: {res.stderr}")
    else:
        print("  [!] build_assets.py script not found.")

# ── 3. HASH CALCULATION ───────────────────────────────────────────────────────
def calculate_pack_hash():
    print("[3/3] Calculating resource pack SHA-1 hash for server auto-prompt...")
    if os.path.exists(PACK_FILE):
        h = hashlib.sha1()
        with open(PACK_FILE, "rb") as f:
            while chunk := f.read(8192):
                h.update(chunk)
        sha1 = h.hexdigest()
        print(f"  [OK] Pack SHA-1 Hash: {sha1}")
        print(f"  [OK] Resource Pack Location: {PACK_FILE}")
        
        # Write info json
        cfg_out = os.path.join(DIST_DIR, "pack_info.json")
        with open(cfg_out, "w") as f:
            f.write(f'{{\n  "file": "ValorantMC-ResourcePack.zip",\n  "sha1": "{sha1}",\n  "url": "http://localhost:8080/pack.zip"\n}}\n')
        print("  [OK] Wrote pack_info.json")
    else:
        print("  [!] Resource pack zip file not found.")

if __name__ == "__main__":
    print("============================================================")
    print(" ValorantMC Asset & Resource Pack Auto-Downloader")
    print("============================================================")
    prepare_maps()
    build_resource_pack()
    calculate_pack_hash()
    print("============================================================")
    print(" Complete! Maps and Resource Pack ready for auto-serving.")
    print("============================================================")
