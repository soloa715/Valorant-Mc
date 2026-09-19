#!/usr/bin/env python3
import os, sys, zipfile

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT_ZIP = os.path.join(BASE_DIR, "modding", "dist", "ValorantMC-FullProject.zip")

EXCLUDE_DIRS = {".git", ".gradle", "build", "classes", "generated"}
EXCLUDE_FILES = {"ValorantMC-FullProject.zip"}

print(f"Creating project bundle: {OUTPUT_ZIP}...")

os.makedirs(os.path.dirname(OUTPUT_ZIP), exist_ok=True)

count = 0
with zipfile.ZipFile(OUTPUT_ZIP, "w", zipfile.ZIP_DEFLATED) as zipf:
    for root, dirs, files in os.walk(BASE_DIR):
        # Exclude directories
        dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
        
        for file in files:
            if file in EXCLUDE_FILES or file.endswith(".tmp"):
                continue
            
            abs_path = os.path.join(root, file)
            rel_path = os.path.relpath(abs_path, BASE_DIR)
            zipf.write(abs_path, os.path.join("Valorant-MC", rel_path))
            count += 1

size_mb = os.path.getsize(OUTPUT_ZIP) / (1024 * 1024)
print(f"✓ Project bundle created successfully! Packaged {count} files ({size_mb:.2f} MB)")
print(f"Location: {OUTPUT_ZIP}")
