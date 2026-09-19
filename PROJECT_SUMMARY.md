# ValorantMC - Project Architecture & State Summary

## 📌 Architectural Overview
- **Minecraft Version**: 1.21.4
- **Fabric Loader**: 0.19.2
- **Java Version**: 21
- **Architecture Model**: Pure Fabric Dual-Sided Mod (Client + Server).
- **Network Protocol**: Fabric Custom Payload Packets (`valorantmc:ability_cast`, `valorantmc:buy_menu`, `valorantmc:sync_hud`).

---

## 🎮 Agent Abilities & Mechanics
All 17 Agents implemented in `src/main/java/com/valorantmc/agents/impl/`:
1. **Jett**: Tail-Wind (Dash), Cloudburst (Smoke), Updraft (Vertical Leap), Blade Storm (Kunai).
2. **Reyna**: Leer (Nearsight), Devour (Heal), Dismiss (Invulnerability), Empress (Ultimate).
3. **Phoenix**: Hot Hands (Fireball), Blaze (Fire Wall), Curveball (Flashbang), Run It Back (Respawn).
4. **Raze**: Paint Shells (Cluster Grenade), Boom Bot (Targeting Bot), Blast Pack (Satchel), Showstopper (Rocket Launcher).
5. **Sova**: Recon Bolt (Radar Sonar), Shock Bolt (Damage Arrow), Owl Drone, Hunter's Fury (Wall-piercing beam).
6. **Omen**: Dark Cover (Spherical Smoke), Paranoia (Blind Wave), Shrouded Step (Short Teleport), From The Shadows (Global TP).
7. **Brimstone**: Incendiary (Molotov), Sky Smoke (Orbital Smokes), Stim Beacon, Orbital Strike (Beam).
8. **Viper**: Toxic Screen (Poison Wall), Poison Cloud (Emitter), Snake Bite (Acid), Viper's Pit (Gas Zone).
9. **Cypher**: Trapwire (Tether), Cyber Cage (Smoke), Spycam (Remote Camera), Neural Theft.
10. **Killjoy**: Nanoswarm (Grenade), Alarmbot, Turret (Auto-turret), Lockout (Detonation Zone).
11. **Breach**: Aftershock (Wall Blast), Flashpoint (Wall Flash), Fault Line (Stun Zone), Rolling Thunder.
12. **Sage**: Barrier Orb (Ice Wall), Slow Orb, Healing Orb, Resurrection.
13. **Skye**: Guiding Light (Hawk Flash), Trailblazer (Tasmanian Tiger), Regrowth (Heal), Seekers.
14. **KAY/O**: ZERO/point (Suppression Blade), FLASH/drive, FRAG/ment, NULL/cmd.
15. **Yoru**: Fakeout (Decoy), Blindside (Bounce Flash), Gatecrash (Rift Tether), Dimensional Drift.
16. **Astra**: Stars Placement, Gravity Well, Nova Pulse (Stun), Nebula (Smoke), Cosmic Divide (Wall).
17. **Gekko**: Dizzy (Plasma Flash), Wingman (Spike Plant/Defuse), Mosh Pit (Acid), Thrash (Creature Stun).

---

## 📁 Key Directories & Tooling
- `src/main/java/com/valorantmc/`: Server & Mod Core implementation (Agents, Abilities, Maps, Spike, Shop).
- `mod/`: Fabric client mod project.
- `modding/dist/ValorantMC-ResourcePack.zip`: Compiled 3D custom item and block resource pack.
- `modding/dist/ValorantMC-FullProject.zip`: Full zipped project bundle.
- `tools/lan_sync_server.py`: Auto-build host & target deployer HTTP server (Port 9090).
- `tools/create_project_zip.py`: Packages full workspace into ZIP archive.
- `tools/download_assets.py`: Generates custom 3D models and packs.
