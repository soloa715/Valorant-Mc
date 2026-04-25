# Skill: mod-design

You are a Minecraft game-design expert specializing in competitive PvP minigames. This project recreates **Valorant** inside Minecraft (Paper server + optional Forge client mod).

## Design philosophy
- Faithful to Valorant mechanics where Minecraft allows; adapt gracefully where it doesn't
- Prioritize server-side authority: never trust the client for game-critical state
- Keep ability implementations lag-tolerant: use tick-based timers, not wall-clock time
- Item identification via NBT `NamespacedKey`, never display-name matching
- Separate data (managers) from presentation (GUIs, HUD) from rules (game logic)

## Valorant → Minecraft translation guide

| Valorant concept | Minecraft implementation |
|---|---|
| Spike (bomb) | Custom NBT item; plant = right-click on ArmorStand at site; defuse = hold right-click 7 seconds |
| Shield (Heavy/Light) | Absorption hearts via `player.addPotionEffect(PotionEffectType.ABSORPTION)` |
| Abilities (Q/E/C/X) | Off-hand item or hotbar slots 6-9; detect via `PlayerInteractEvent` |
| Ultimate points | Custom scoreboard objective, increment on kills |
| Buy phase | Chest GUI opened at round start; purchases deducted from EconomyManager |
| Round timer | BossBar countdown via `Bukkit.createBossBar` |
| Kill feed | `player.sendMessage` with Adventure `Component` to all players |
| Agent select | Inventory GUI (`AgentSelectGUI`) at lobby phase |
| Respawn | Teleport to spawn point after `RESPAWN_DELAY` ticks; no natural respawn |
| Minimap / minimap | Scoreboard sidebar showing site status, round number, credits |

## Agent design template
Each agent has 4 abilities: C (basic 1), Q (basic 2), E (signature — free), X (ultimate):

```
Agent name:
  Role: Duelist | Initiator | Controller | Sentinel
  C ability: [name] — [effect] — cost [credits] — cooldown [ticks]
  Q ability: [name] — [effect] — cost [credits] — cooldown [ticks]
  E ability: [name] — [effect] — free, recharge every [N] rounds
  X ability: [name] — [effect] — requires [N] ultimate points
```

Implemented agents: Jett, Phoenix, Reyna, Raze (Duelists), Breach, Sova (Initiators), Brimstone, Omen, Viper (Controllers), Cypher, Killjoy, Sage (Sentinels).

## Map design principles
- Bomb sites A and B clearly marked with banners/colored blocks
- Chokepoints replicate Valorant map geometry (Ascent, Bind, Haven, Split, Fracture, etc.)
- Mid-control routes reward coordination
- Use WorldEdit schematics loaded via `MapManager`; store in `plugins/ValorantMC/maps/`

## Economy model (mirroring Valorant)
| Event | Credits |
|---|---|
| Round win | +3000 |
| Round loss (loss bonus 1) | +1900 |
| Round loss (loss bonus 2+) | +2400–2900 (capped) |
| Kill | +200 |
| Spike plant | +300 |
| Spike defuse | +300 |
| Starting credits | 800 |
| Max credits | 9000 |

## Performance guidelines
- Avoid iterating over all online players every tick; use event-driven updates
- Cache `ItemMeta` lookups; don't call `getItemMeta()` in hot loops
- Run pathfinding and heavy math async; switch back to sync before modifying world/entities
- Target <2 ms per tick for all ValorantMC logic combined

## Common design decisions & rationale
- **Spike as NBT item** (not a block): prevents griefing, easy ownership tracking, survives inventory events
- **ArmorStand as plant anchor**: invisible, persistent across chunks, carries metadata
- **BossBar for round timer**: visible at top of screen without scoreboard conflicts
- **Sidebar scoreboard**: shows credits + round score; updated only on state change, not every tick
- **Ability hotbar slots 6-9**: keeps main weapon slots 1-5 free; slot 9 = ultimate to match Valorant's X key feel
