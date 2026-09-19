# ValorantMC Complete Project & Context Transfer Guide

This document provides step-by-step instructions to transfer the complete **ValorantMC** codebase, compiled binaries, resource packs, configuration, and AI assistant context to your other computer.

---

## 🚀 Step 1: Download or Transfer the Project Folder

You have two easy options to get all current project files onto your other computer:

### Option A: Direct LAN Download (Recommended)
1. Make sure `lan_sync_server.py` is running on the host IP (e.g. `http://192.168.1.39:9090`).
2. Open a web browser on your target computer and navigate to:
   **`http://192.168.1.39:9090/project.zip`**
3. Extract `ValorantMC-FullProject.zip` into your desired folder (e.g., `C:\Users\YourName\Valorant-MC`).

### Option B: Push & Clone via Git
1. If you prefer using Git:
   ```bash
   git clone https://github.com/soloa715/Valorant-MC.git
   ```
2. Pull the latest committed changes.

---

## 🛠️ Step 2: Computer Environment Setup

### Required Prerequisites:
1. **Java JDK 21**: Make sure OpenJDK 21 or Eclipse Temurin 21 is installed.
   - Verify with: `java -version`
2. **Minecraft 1.21.4 with Fabric Loader 0.19.2** (e.g., on PrismLauncher or Official Minecraft Launcher).
3. **Fabric API**: Download `fabric-api-0.119.4+1.21.4.jar` into your Minecraft client `mods/` directory.

---

## 📦 Step 3: Deploying Built Artifacts to Client & Server

Inside the extracted folder, all assets and compiled binaries are ready in `modding/dist/` and `target/`:

1. **Client & Server Mod JAR**:
   - `target/ValorantMC-1.0.0.jar` -> Copy to your client `mods/` folder and server `mods/` folder.
2. **3D Weapon & Block Resource Pack**:
   - `modding/dist/ValorantMC-ResourcePack.zip` -> Copy to server root directory (or `.minecraft/resourcepacks/`).
3. **Automated Sync**:
   - Run `lan_auto_updater.bat` on Windows. It will prompt for your PrismLauncher/Client `mods/` folder and Server folder, then automatically keep both synced with latest builds!

---

## 🤖 Step 4: Transferring AI Context & Continuing with Antigravity

If you are using **Google Antigravity** (`agy`) on your other computer:

1. **Point Antigravity to the project root**:
   Open terminal or your IDE on the new computer in the `Valorant-MC` folder:
   ```bash
   agy
   ```
2. **Resume Context**:
   Tell Antigravity:
   > *"Read `PROJECT_SUMMARY.md` and `TRANSFER_GUIDE.md` to get up to speed on the current state of ValorantMC."*
3. **Transfer Raw Logs (Optional)**:
   If you want the exact raw CLI conversation logs transferred:
   - Copy `~/.gemini/antigravity-cli/brain/9e235a30-a87c-432c-9bf7-9f8d0b7b8fd0` to `~/.gemini/antigravity-cli/brain/` on your new computer.

---

## 📊 Summary of What's Included in This Build
- **17 Valorant Agents & 68 Unique Abilities** implemented.
- **Pure Fabric 1.21.4 Dual-Sided Mod Architecture** with 0ms server latency overhead.
- **Custom 3D Item Models & Skins** generated in `modding/dist/ValorantMC-ResourcePack.zip`.
- **Ascent, Bind, and Split Map Configs** in `modding/maps/`.
- **LAN Auto-Sync Host Server** (`tools/lan_sync_server.py` on port `9090`).
