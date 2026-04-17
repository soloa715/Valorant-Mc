# ValorantMC — Setup Guide

## Requirements
- **Java 17+**
- **Paper 1.20.4** (recommended) — download from papermc.io
- **Maven** — to build the plugin

## Building

```bash
cd ValorantMC
mvn clean package
```

The compiled JAR will be at `target/ValorantMC-1.0.0.jar`.
Copy it to your Paper server's `plugins/` folder.

---

## In-Game Setup

### 1. Create a game
```
/valorant create mygame
```

### 2. Add players
Players run:
```
/valorant join mygame
```

### 3. Select agents
Each player opens the agent selector:
```
/vagent
```
or click an agent in the GUI.

### 4. Start on a map
```
/valorant start mygame ascent
```

### 5. During Buy Phase
Players use:
```
/vshop
```
to open the buy menu and purchase weapons, armor, and abilities.

---

## Maps

Maps are stored as YAML files in `plugins/ValorantMC/maps/`.

To use the real Valorant maps in Minecraft:
1. Download maps from **https://ommo.me/valorant-x-minecraft**
2. Import the world/schematic into your server
3. Edit `plugins/ValorantMC/maps/ascent.yml` (or copy it for each map)
4. Set the correct coordinates for spawn points and bomb sites
5. Reload with `/valorant reload`

### Finding coordinates
Stand at the spawn point, run `/valorant tp <game> <map>` or use F3 in Minecraft to find XYZ.

---

## Resource Pack (Optional but Recommended)

For custom gun textures and sounds:

1. Create a resource pack that overrides item models using `CustomModelData`
2. Each weapon has a `customModelId` in `WeaponType.java`
3. Host the pack on a CDN (e.g. MCPacks, GitHub releases, your own server)
4. Set in `config.yml`:
   ```yaml
   resource-pack:
     enabled: true
     url: "https://yourserver.com/valorantmc-pack.zip"
     hash: "sha1-hash-of-pack"
     required: false
   ```

### Skin textures from NameMC
The URL you provided (namemc.com/minecraft-skins/tag/valorant) has player skins.
These apply to **player models** — to use them:
- Give players custom skin-changing permissions (requires a skin plugin like SkinsRestorer)
- Or distribute as texture pack overlays

---

## Weapons Reference

| Name     | Category | Cost  | Damage | Fire Rate |
|----------|----------|-------|--------|-----------|
| Classic  | Sidearm  | Free  | 78     | 6.75      |
| Shorty   | Sidearm  | 150   | 24×2p  | 3.33      |
| Frenzy   | Sidearm  | 450   | 78     | 10        |
| Ghost    | Sidearm  | 500   | 105    | 6.75      |
| Sheriff  | Sidearm  | 800   | 160    | 4.0       |
| Stinger  | SMG      | 1100  | 67     | 18        |
| Spectre  | SMG      | 1600  | 78     | 13.33     |
| Bucky    | Shotgun  | 900   | 34×5p  | 1.1       |
| Judge    | Shotgun  | 1850  | 34×7p  | 3.5       |
| Bulldog  | Rifle    | 2050  | 116    | 9.15      |
| Guardian | Rifle    | 2250  | 195    | 5.25      |
| Phantom  | Rifle    | 2900  | 156    | 11        |
| Vandal   | Rifle    | 2900  | 156    | 9.75      |
| Marshal  | Sniper   | 950   | 202    | 1.5       |
| Outlaw   | Sniper   | 2400  | 238    | 2.5       |
| Operator | Sniper   | 4700  | 255    | 0.75      |
| Ares     | Heavy    | 1600  | 72     | 10        |
| Odin     | Heavy    | 3200  | 62     | 12        |
| Knife    | Melee    | Free  | 50     | —         |

---

## Agents Reference

| Agent     | Role       | Q            | E            | C           | X                 | Ult Pts |
|-----------|------------|--------------|--------------|-------------|-------------------|---------|
| Jett      | Duelist    | Updraft      | Tailwind     | Cloudburst  | Blade Storm       | 7       |
| Reyna     | Duelist    | Devour       | Dismiss      | Leer        | Empress           | 6       |
| Phoenix   | Duelist    | Curveball    | Hot Hands    | Blaze       | Run It Back       | 8       |
| Raze      | Duelist    | Paint Shells | Boom Bot     | Blast Pack  | Showstopper       | 6       |
| Sova      | Initiator  | Shock Bolt   | Recon Bolt   | Owl Drone   | Hunter's Fury     | 8       |
| Breach    | Initiator  | Flashpoint   | Fault Line   | Aftershock  | Rolling Thunder   | 7       |
| Brimstone | Controller | Stim Beacon  | Sky Smoke    | Incendiary  | Orbital Strike    | 7       |
| Omen      | Controller | Paranoia     | Dark Cover   | Shrouded Step| From the Shadows | 7       |
| Viper     | Controller | Poison Cloud | Toxic Screen | Snakebite   | Viper's Pit       | 8       |
| Sage      | Sentinel   | Slow Orb     | Healing Orb  | Barrier Orb | Resurrection      | 8       |
| Killjoy   | Sentinel   | Alarmbot     | Turret       | Nanoswarm   | Lockdown          | 8       |
| Cypher    | Sentinel   | Spycam       | Trapwire     | Cyber Cage  | Neural Theft      | 7       |

---

## Skins System

Skins apply custom 3D models via `CustomModelData`.
Without a resource pack, weapons still function — they just use vanilla item appearances.

To equip a skin, use the skin menu (coming soon via `/valorant skins`) or config.

---

## Mod vs Plugin — Should you make a mod?

**Short answer: Add a Fabric mod later for visuals.**

The plugin already handles all gameplay logic. A Fabric client mod would add:
- True 3D gun models with animations
- Custom crosshair / scopes
- Agent ability HUD
- Kill feed overlay
- Minimap

This is optional — the plugin works standalone. Adding a mod is a good next step
once the gameplay is solid and you want to push visuals further.

---

## Admin Commands

| Command | Description |
|---------|-------------|
| `/valorant create <id>` | Create a new game |
| `/valorant start <id> <map>` | Start game on a map |
| `/valorant reload` | Reload config |
| `/valorant maps` | List available maps |
