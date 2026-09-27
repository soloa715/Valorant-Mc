# Directory Structure & File Map

```
Valorant-MC/
├── .planning/                  # Project planning, roadmap, and codebase maps
├── src/main/java/com/valorantmc/
│   ├── agents/                 # Agent definitions, abilities, roles, and impl/
│   ├── game/                   # Match lifecycle, round timers, teams, Spike engine
│   ├── maps/                   # Map setup wizard, procedural map generator
│   ├── weapons/                # Weapon stats, recoil patterns, spray math
│   ├── economy/                # Shop GUI, VP & credit transactions
│   └── ValorantMC.java         # Main plugin entrypoint
├── mod/                        # Fabric client mod source code
├── modding/                    # Resource pack source, 3D models, textures
│   └── dist/                   # Built ZIP bundles
├── tools/                      # Python deployment & utility scripts
│   ├── lan_sync_server.py
│   ├── create_project_zip.py
│   └── download_assets.py
├── run-server.bat              # Auto-build & Paper server launcher script
├── build-plugin.bat            # Maven plugin build script
├── pom.xml                     # Maven project specification
├── PROJECT_SUMMARY.md          # Architecture overview document
├── README.md                   # Setup guide and command reference
└── eval_report.json            # Quality & feature evaluation report
```
