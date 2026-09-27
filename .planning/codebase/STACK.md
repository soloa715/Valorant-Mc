# Tech Stack & Dependencies

## Core Environment & Runtime
- **Java**: Java 21 / Java 25 (Adoptium JDK)
- **Target Server Engine**: Paper MC (1.21.4 / 1.21.11 build 69+)
- **Target Client Mod**: Fabric Loader 0.19.2 (Minecraft 1.21.4)
- **Build System**: Apache Maven (`pom.xml`) + Gradle (`mcreator.gradle` / Fabric build)

## Main Dependencies & APIs
- **Paper API / Bukkit**: `io.papermc.paper:paper-api`
- **Fabric API**: `net.fabricmc:fabric-api`
- **Networking**: Custom payload channels (`valorantmc:ability_cast`, `valorantmc:buy_menu`, `valorantmc:sync_hud`)
- **Asset Tools**: Python 3 utility scripts (`tools/lan_sync_server.py`, `tools/download_assets.py`, `tools/create_project_zip.py`)

## Tooling & Automation
- Batch Scripts: `run-server.bat`, `build-plugin.bat`
- HTTP Asset & Sync Server: Python Flask/HTTP server on port 9090
