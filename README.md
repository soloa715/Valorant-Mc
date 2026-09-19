# ValorantMC

Full Valorant experience inside Minecraft — 12 agents, 19 weapons, spike plant/defuse, round economy, skins, and custom maps.

**Server:** Paper 1.21.11 (Minecraft 26.1.2) · **Java:** 25

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Java | 25 (Adoptium recommended) |
| Paper | 1.21.11 build 69+ |
| RAM | 2 GB minimum, 4–6 GB recommended |

---

## Quick Start (Windows)

1. Install [Java 25 Adoptium](https://adoptium.net/) to the default path
2. Double-click **`run-server.bat`**

The script auto-builds with Maven, downloads Paper 1.21.11 if missing, accepts the EULA, copies the JAR, and starts the server on port 25565.

---

## Starting a Game

```
/valorant create myGame          # Create lobby
/valorant join myGame            # Players join (auto-team balanced)
/valorant start myGame ascent    # Start on map 'ascent'
```

During agent select (45 s) use `/vagent`. During buy phase (30 s) use `/vshop`.

**Admin shortcuts:**
```
/valorant forcestart myGame ascent   # Start with any player count
/valorant status myGame              # Live status: round, score, spike, alive counts
/valorant pause myGame / resume      # Freeze/unfreeze round timer
/valorant kick <player>              # Remove a player
/valorant list                       # All active games
/valorant setlobby                   # Set lobby spawn to your position
```

---

## Map Setup

**Built-in maps** (zero config needed): `ascent`, `split`, `bind` — procedural arenas with cover. Start a game and they build automatically.

**Custom map wizard:**
```
/vmapsetup create mymap
/vmapsetup addspawn atk      # Stand at each attacker spawn, repeat ≥2×
/vmapsetup addspawn def      # Stand at each defender spawn, repeat ≥2×
/vmapsetup addsite a         # Stand at bomb site A locations
/vmapsetup addsite b         # Stand at bomb site B locations
/vmapsetup setworld <world>  # If using a custom Minecraft world
/vmapsetup validate mymap    # Check map is ready
/vmapsetup save              # Write to disk + hot-reload
/valorant start <id> mymap   # Play it
```

Verify spawns visually: `/vmapsetup tp mymap atk 1`

---

## Resource Pack (Custom Weapon Models + Skins)

Skins use `CustomModelData` — a resource pack is required for visual changes.

1. Build pack from `modding/resource-pack/` (see README inside)
2. Zip → host online → get SHA-1 hash
3. Edit `plugins/ValorantMC/config.yml`:
   ```yaml
   resource-pack:
     enabled: true
     required: true
     url: "https://your-host.com/ValorantMC-pack.zip"
     hash: "your-sha1-here"
   ```

Without a pack, weapons function normally but look like vanilla items.

---

## Economy

- **Credits** — reset to 800 each round, earned through kills/plants/wins, spent in `/vshop`
- **VP (Valorant Points)** — persist across sessions, spent in `/vskin`
- Set `free-skins: true` in config to grant all skins without VP cost (default: `true`)

---

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `valorantmc.admin` | op | All admin commands |
| `valorantmc.play` | true | Join and play |
| `valorantmc.shop` | true | Use buy menu |

---

## Config Keys

| Key | Default | Description |
|-----|---------|-------------|
| `game.buy-phase-duration` | 30 | Buy phase seconds |
| `game.round-duration` | 100 | Round time limit |
| `game.spike-timer` | 45 | Spike detonation countdown |
| `game.starting-credits` | 800 | Credits at round start |
| `game.kill-bonus` | 200 | Credits per kill |
| `game.round-win-bonus` | 3000 | Credits for round win |
| `weapons.enable-spray-patterns` | true | Bullet spread increases with shots |
| `weapons.enable-recoil` | true | Camera kick on fire |
| `free-skins` | true | Grant skins for free |
| `resource-pack.enabled` | false | Send pack on join |
| `resource-pack.required` | false | Kick if pack declined |

---

## Agents

| Agent | Role | Ult Points |
|-------|------|------------|
| Jett | Duelist | 7 — Blade Storm |
| Phoenix | Duelist | 8 — Run It Back |
| Reyna | Duelist | 6 — Empress |
| Raze | Duelist | 6 — Showstopper |
| Brimstone | Controller | 7 — Orbital Strike |
| Omen | Controller | 7 — From the Shadows |
| Viper | Controller | 8 — Viper's Pit |
| Sage | Sentinel | 8 — Resurrection |
| Cypher | Sentinel | 7 — Neural Theft |
| Killjoy | Sentinel | 8 — Lockdown |
| Sova | Initiator | 8 — Hunter's Fury |
| Breach | Initiator | 7 — Rolling Thunder |

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Unsupported API version` | Keep `api-version: 1.21` in plugin.yml |
| Map not found | Check exact name with `/valorant maps` |
| Abilities do nothing | Must be ROUND_ACTIVE or BUY_PHASE state |
| Skins look identical | Install resource pack (see above) |
| Build fails | Ensure JAVA_HOME points to JDK 25 |
| Players can fly | Anti-cheat auto-disables flight in-game |
