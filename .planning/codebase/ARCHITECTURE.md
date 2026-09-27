# System Architecture & Component Design

## Core Architecture
`ValorantMC` is structured as a dual-sided Minecraft mod/plugin ecosystem combining a Paper Server plugin and a Fabric client companion mod.

```
[Fabric Client / Resource Pack]  <--- Custom Payload Packets --->  [Paper Server Plugin Core]
- Custom 3D Item Models                                          - Game State & Round Manager
- HUD Overlays & Ability FX                                     - Agent & Ability System
- Sound & Visual FX                                              - Map & Spawning Manager
                                                                 - Weapon & Spray Mechanics
                                                                 - Economy & Shop System
```

## Primary Subsystems
1. **Agent & Ability System** (`src/main/java/com/valorantmc/agents/`):
   - Modular Agent registry supporting 17 agents across Duelist, Controller, Sentinel, and Initiator roles.
   - Dynamic cooldowns, ultimate point trackers, and active particle/projectile raycasting.

2. **Match & Round Lifecycle Engine**:
   - Lobby management (`/valorant create`, `join`, `start`, `forcestart`).
   - Round flow states: Buy Phase (30s) -> Active Round (100s) -> Spike Detonation/Defuse (45s) -> Post-Round.

3. **Economy & Shop Subsystem**:
   - Round credits (800 starting, kill/plant/win bonuses).
   - VP (Valorant Points) persistent currency for weapon cosmetics and skins.

4. **Map Generator & Setup Wizard**:
   - Built-in procedural maps (`ascent`, `split`, `bind`).
   - Custom map wizard (`/vmapsetup`) supporting dynamic attacker/defender spawns, site bounds, and hot-reloading.
