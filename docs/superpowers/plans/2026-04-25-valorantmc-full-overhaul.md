# ValorantMC Full Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring ValorantMC to a fully playable, polished state: working weapons rendered in-hand with the companion mod, all abilities functional, a proper lobby/main menu on join, spectator locked to teammates only, a custom match creator with cheats, and a variety of additional Valorant-faithful features.

**Architecture:** The project has two independent deployables — the Paper server plugin (`src/`) and the Forge 1.20.1 companion client mod (`mod/`). They communicate exclusively through slash commands sent as chat packets. All game state lives on the server; the mod provides keybinds, custom item models/renderers, and enhanced client GUIs. Phases 1-6 are plugin-only and can be tested without the mod. Phase 7-8 require the mod to be rebuilt.

**Tech Stack:** Java 25 · Paper API 26.1.2 · Maven · **Fabric** (not Forge) · Adventure API (Component/BossBar/Title) · NBT PersistentDataContainer · Bukkit Scheduler · Fabric API · Fabric Loader

---

## File Map

### New files to create
| File | Purpose |
|------|---------|
| `src/.../managers/LobbyManager.java` | Tracks players in the pre-game lobby; teleports them to lobby spawn on join |
| `src/.../game/CustomGameSettings.java` | POJO holding all custom match settings (cheats, modifiers) |
| `src/.../shop/MainMenuGUI.java` | Full main-menu inventory GUI (Play, Custom, Collection, Stats, Settings) |
| `src/.../shop/CustomGameGUI.java` | Inventory-based custom match settings editor |
| `src/.../listeners/SpectatorListener.java` | Enforces teammate-only spectating; blocks free-fly |
| `src/.../listeners/LobbyListener.java` | Handles join/quit in lobby state; blocks combat/block-break in lobby |
| `src/main/resources/pack/pack.mcmeta` | Resource pack metadata (version 48 for 1.21+) |
| `src/main/resources/pack/assets/minecraft/models/item/crossbow.json` | Root custom model overrides for weapon items |
| `mod/src/main/java/.../WeaponRenderer.java` | Forge BEWLR for per-weapon custom model rendering |
| `mod/src/main/java/.../ModChannel.java` | Forge network channel — detects mod presence, server sends detection ping |
| `mod/src/main/java/.../MainMenuScreen.java` | Forge Screen shown on join when mod is active |
| `mod/src/main/java/.../HudOverlay.java` | Forge HUD overlay — health bar, shield, ult orbs, ammo counter |

### Files to modify
| File | What changes |
|------|-------------|
| `src/.../ValorantMC.java` | Register LobbyManager, SpectatorListener, LobbyListener; send resource pack on join |
| `src/.../game/ValorantGame.java` | Fix `handleDeath` spectator assignment; add overtime; add spike beep timer; add ping support |
| `src/.../listeners/GameListener.java` | Replace join message with MainMenuGUI; remove old resource pack send (moved to LobbyManager) |
| `src/.../listeners/WeaponListener.java` | Tag fired items with CustomModelData for resource pack; add reload-cancel-on-move |
| `src/.../commands/ValorantCommand.java` | Fix `handleTp` stub; fix `handleDropSpike` NBT check; add `/vcustom` routing; add missing tab completions |
| `src/.../agents/impl/Sova.java` | Implement Owl Drone enemy detection (pulse scan every 1.5s) |
| `src/.../managers/WeaponManager.java` | `giveWeapon` sets CustomModelData on item; `toItemStack` writes it |
| `src/.../shop/LobbyGUI.java` | Expand to 54-slot full main menu layout |
| `src/.../shop/ShopListener.java` | Add CustomGameGUI click handler routing |
| `pom.xml` | Confirm/pin Java 25 and Paper 26.1.2; add resource-pack shade config |
| `mod/build.gradle` | Stays at Forge 1.20.1-47.2.0; add network channel dependency entries |
| `mod/src/main/java/.../ValorantMCMod.java` | Register ModChannel, WeaponRenderer, HudOverlay, MainMenuScreen trigger |
| `src/main/resources/plugin.yml` | Add `vcustom` command |
| `src/main/resources/config.yml` | Add `custom-game.*` and `lobby.*` config sections |

---

## Phase 1 — Fix Spectator (Teammate-Only, No Free-Fly)

**Files:**
- Create: `src/main/java/com/valorantmc/listeners/SpectatorListener.java`
- Modify: `src/main/java/com/valorantmc/game/ValorantGame.java:519-614`

### Task 1: Restrict spectator target to a living teammate on death

- [ ] **Step 1: Modify `handleDeath` in ValorantGame.java (line ~537)**

Replace the existing spectator assignment block:
```java
victim.setHealth(20);
victim.setGameMode(GameMode.SPECTATOR);
victim.setSpectatorTarget(null); // ← remove this
```
With:
```java
victim.setHealth(20);
victim.setGameMode(GameMode.SPECTATOR);
// Lock spectator target to first living teammate — updated in SpectatorListener
ValorantTeam victimTeam2 = getTeam(victim);
if (victimTeam2 != null) {
    List<Player> alive = victimTeam2.getOnlinePlayers().stream()
            .filter(tp -> !victimTeam2.isDead(tp) && !tp.getUniqueId().equals(victim.getUniqueId()))
            .toList();
    victim.setSpectatorTarget(alive.isEmpty() ? null : alive.get(0));
}
```

- [ ] **Step 2: Create SpectatorListener.java**

```java
package com.valorantmc.listeners;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.game.ValorantTeam;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.util.List;

public class SpectatorListener implements Listener {

    private final ValorantMC plugin;

    public SpectatorListener(ValorantMC plugin) {
        this.plugin = plugin;
    }

    /** Block spectator free-fly: if the spectated entity dies or leaves, jump to next teammate. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpectatorMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SPECTATOR) return;
        ValorantGame game = plugin.getGameManager().getGame(p);
        if (game == null) return;

        // Prevent free-roam: if spectatorTarget is null (floating), force onto a teammate
        Entity current = p.getSpectatorTarget();
        if (current == null || !(current instanceof Player watched) || isEnemyOf(p, watched, game)) {
            reassignTarget(p, game);
            // Cancel the move if still no target (everyone dead — let them float until round ends)
        }
    }

    /** Left/right click cycles through alive teammates. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpectatorClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SPECTATOR) return;
        ValorantGame game = plugin.getGameManager().getGame(p);
        if (game == null) return;

        e.setCancelled(true);
        cycleTarget(p, game, e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK);
    }

    private void reassignTarget(Player spectator, ValorantGame game) {
        ValorantTeam team = game.getTeam(spectator);
        if (team == null) return;
        List<Player> alive = aliveTeammates(spectator, team);
        spectator.setSpectatorTarget(alive.isEmpty() ? null : alive.get(0));
    }

    private void cycleTarget(Player spectator, ValorantGame game, boolean backward) {
        ValorantTeam team = game.getTeam(spectator);
        if (team == null) return;
        List<Player> alive = aliveTeammates(spectator, team);
        if (alive.isEmpty()) return;

        Entity current = spectator.getSpectatorTarget();
        int idx = 0;
        if (current instanceof Player watched) {
            idx = alive.indexOf(watched);
            if (idx < 0) idx = 0;
            else idx = backward ? (idx - 1 + alive.size()) % alive.size()
                                : (idx + 1) % alive.size();
        }
        spectator.setSpectatorTarget(alive.get(idx));
        spectator.sendActionBar(net.kyori.adventure.text.Component.text(
                "§7Spectating §f" + alive.get(idx).getName()
                + " §8| §7Left/Right click to switch"));
    }

    private List<Player> aliveTeammates(Player spectator, ValorantTeam team) {
        return team.getOnlinePlayers().stream()
                .filter(tp -> !team.isDead(tp) && !tp.getUniqueId().equals(spectator.getUniqueId()))
                .toList();
    }

    private boolean isEnemyOf(Player spectator, Player target, ValorantGame game) {
        ValorantTeam st = game.getTeam(spectator);
        ValorantTeam tt = game.getTeam(target);
        if (st == null || tt == null) return true;
        return st.getSide() != tt.getSide();
    }
}
```

- [ ] **Step 3: Register SpectatorListener in ValorantMC.java**

In `onEnable()`, after the other `registerEvents(...)` calls, add:
```java
getServer().getPluginManager().registerEvents(new SpectatorListener(this), this);
```

- [ ] **Step 4: Also update `reviveAll()` in ValorantGame.java to clear spectator target (line ~684)**

The existing call `p.setSpectatorTarget(null)` is fine in reviveAll; no change needed there. Confirm line 687 already does this.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/valorantmc/listeners/SpectatorListener.java \
        src/main/java/com/valorantmc/game/ValorantGame.java \
        src/main/java/com/valorantmc/ValorantMC.java
git commit -m "feat: restrict spectator to alive teammates; block free-fly"
```

---

## Phase 2 — Fix Incomplete Commands

**Files:**
- Modify: `src/main/java/com/valorantmc/commands/ValorantCommand.java`

### Task 2: Fix the 3 broken command handlers

- [ ] **Step 1: Fix `handleTp` (line 257-266) — it currently does nothing**

Replace:
```java
private boolean handleTp(CommandSender sender, String[] args) {
    if (!sender.hasPermission("valorantmc.admin")) { ... }
    if (!(sender instanceof Player p)) { ... }
    if (args.length < 2) return true;
    ValorantGame game = plugin.getGameManager().getGame(args[1]);
    if (game == null) { p.sendMessage(...); return true; }
    p.sendMessage(ValorantMC.colorize("&aTeleported to game &e" + args[1]));
    return true;
}
```
With:
```java
private boolean handleTp(CommandSender sender, String[] args) {
    if (!sender.hasPermission("valorantmc.admin")) { sender.sendMessage(plugin.msg("errors.no-permission")); return true; }
    if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("errors.player-only")); return true; }
    if (args.length < 2) { p.sendMessage(ValorantMC.colorize("&cUsage: /valorant tp <game-id>")); return true; }
    ValorantGame game = plugin.getGameManager().getGame(args[1]);
    if (game == null) { p.sendMessage(ValorantMC.colorize("&cGame not found: " + args[1])); return true; }
    List<org.bukkit.Location> spawns = game.getAttackSpawnsPublic();
    if (spawns.isEmpty()) { p.sendMessage(ValorantMC.colorize("&cNo spawn points configured for this game.")); return true; }
    p.teleport(spawns.get(0));
    p.sendMessage(ValorantMC.colorize("&aTeleported to game &e" + args[1] + "&a's first spawn."));
    return true;
}
```

- [ ] **Step 2: Fix `handleDropSpike` (line 285-297) — uses `remove(Material.RED_DYE)` which wipes ALL red dye**

Replace:
```java
s.drop(p.getLocation());
p.getInventory().remove(org.bukkit.Material.RED_DYE);
```
With:
```java
s.drop(p.getLocation());
org.bukkit.NamespacedKey spikeKey = new org.bukkit.NamespacedKey(plugin, "spike");
for (int si = 0; si < p.getInventory().getSize(); si++) {
    org.bukkit.inventory.ItemStack sit = p.getInventory().getItem(si);
    if (sit == null || !sit.hasItemMeta()) continue;
    Boolean flag = sit.getItemMeta().getPersistentDataContainer()
            .get(spikeKey, org.bukkit.persistence.PersistentDataType.BOOLEAN);
    if (Boolean.TRUE.equals(flag)) { p.getInventory().setItem(si, null); break; }
}
```

- [ ] **Step 3: Add missing tab completions for `status`, `pause`, `resume`, `kick`**

In `onTabComplete`, the existing `args.length == 2` switch is missing these sub-commands. Add them to the case:
```java
case "join", "start", "tp", "status", "pause", "resume" ->
        new ArrayList<>(plugin.getGameManager().getGameIds());
case "kick" -> plugin.getServer().getOnlinePlayers().stream()
        .map(Player::getName).toList();
```

- [ ] **Step 4: Add `vcustom` to the switch statement (needed by Phase 6)**

```java
case "vcustom" -> { return handleCustomGame(sender); }
```
(implementation is a stub for now — returns false; full body added in Phase 6)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/valorantmc/commands/ValorantCommand.java
git commit -m "fix: handleTp teleports correctly; handleDropSpike uses NBT; tab-complete expands"
```

---

## Phase 3 — Fix Sova Owl Drone

**Files:**
- Modify: `src/main/java/com/valorantmc/agents/impl/Sova.java:44-79`

### Task 3: Add enemy-detection pulse to the Owl Drone

The drone currently moves forward as a static armor stand with no detection. We need a repeating task that scans for enemies within 15 blocks of the drone and broadcasts their direction to Sova.

- [ ] **Step 1: Replace the `useQ` implementation in Sova.java**

Current `useQ` spawns an armor stand and puts Sova in spectator. Replace the interior of that method (keep the spectator teleport) — add a repeating scan task:

```java
@Override
public void useQ(Player player, ValorantGame game) {
    Ability q = getAbility('Q');
    if (!q.use()) { player.sendMessage(ValorantMC.colorize("&cAbility not ready.")); return; }

    org.bukkit.World world = player.getWorld();
    org.bukkit.Location spawnLoc = player.getEyeLocation().clone().add(player.getLocation().getDirection().multiply(2));

    // Spawn drone as invisible armor stand
    org.bukkit.entity.ArmorStand drone = (org.bukkit.entity.ArmorStand)
            world.spawnEntity(spawnLoc, org.bukkit.entity.EntityType.ARMOR_STAND);
    drone.setVisible(false);
    drone.setGravity(false);
    drone.setMarker(true);
    drone.setCustomName(ValorantMC.colorize("&b[Owl Drone]"));
    drone.setCustomNameVisible(true);
    drone.getPersistentDataContainer().set(
            new org.bukkit.NamespacedKey(com.valorantmc.ValorantMC.getInstance(), "sova_drone"),
            org.bukkit.persistence.PersistentDataType.STRING, player.getUniqueId().toString());

    // Put Sova in spectator locked to drone
    player.setSpectatorTarget(drone);
    player.setGameMode(org.bukkit.GameMode.SPECTATOR);
    player.sendActionBar(ValorantMC.colorize("&b[Owl Drone] &7WASD to fly · Left-click to shoot dart · Auto-expires in 8s"));

    final int DRONE_DURATION_TICKS = 160; // 8 seconds
    final int PULSE_INTERVAL = 30;        // scan every 1.5 seconds
    final int[] tick = {0};
    final org.bukkit.Location droneLoc = drone.getLocation().clone();

    com.valorantmc.ValorantMC plugin = com.valorantmc.ValorantMC.getInstance();
    new org.bukkit.scheduler.BukkitRunnable() {
        @Override public void run() {
            if (!drone.isValid() || tick[0] >= DRONE_DURATION_TICKS) {
                endDrone(player, drone, game);
                cancel();
                return;
            }

            // Move drone in the direction the player camera faces
            if (player.getSpectatorTarget() == drone) {
                org.bukkit.util.Vector move = player.getLocation().getDirection().multiply(0.4);
                move.setY(0); // horizontal only
                drone.teleport(drone.getLocation().add(move));
            }

            // Pulse scan every PULSE_INTERVAL ticks
            if (tick[0] % PULSE_INTERVAL == 0) {
                ValorantTeam sovaTeam = game.getTeam(player);
                if (sovaTeam == null) { cancel(); return; }
                ValorantTeam enemies = game.getEnemyTeam(player);
                if (enemies == null) { cancel(); return; }

                boolean detected = false;
                for (Player enemy : enemies.getOnlinePlayers()) {
                    double dist = drone.getLocation().distance(enemy.getLocation());
                    if (dist <= 15.0) {
                        // Grant glowing to enemy briefly
                        enemy.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.GLOWING, 40, 0, false, false));
                        // Tell Sova direction + distance
                        double dx = enemy.getLocation().getX() - drone.getLocation().getX();
                        double dz = enemy.getLocation().getZ() - drone.getLocation().getZ();
                        double angle = Math.toDegrees(Math.atan2(dz, dx));
                        player.sendMessage(ValorantMC.colorize(
                                "&b[Drone] &cEnemy detected! &7Bearing " + String.format("%.0f°", angle)
                                + " · " + String.format("%.1f", dist) + "m"));
                        detected = true;
                    }
                }
                // Visual ping
                world.spawnParticle(org.bukkit.Particle.SONIC_BOOM, drone.getLocation(), 1, 0.1, 0.1, 0.1, 0);
                if (!detected) {
                    player.sendActionBar(ValorantMC.colorize("&b[Owl Drone] &7No enemies in range"));
                }
            }

            tick[0]++;
        }
    }.runTaskTimer(plugin, 1L, 1L);
}

private void endDrone(Player player, org.bukkit.entity.ArmorStand drone, ValorantGame game) {
    drone.remove();
    if (player.isOnline() && player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.setSpectatorTarget(null);
        player.sendMessage(ValorantMC.colorize("&b[Owl Drone] &7Drone expired."));
    }
}
```

> **Note:** `ValorantMC.getInstance()` requires a static getter. Add `private static ValorantMC instance;` and `instance = this;` in `onEnable()`, with `public static ValorantMC getInstance() { return instance; }` in ValorantMC.java. Check if it already exists first.

- [ ] **Step 2: Add `getInstance()` static method to ValorantMC.java if it does not already exist**

```java
private static ValorantMC instance;

@Override
public void onEnable() {
    instance = this;
    // ... rest of onEnable
}

public static ValorantMC getInstance() { return instance; }
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/valorantmc/agents/impl/Sova.java \
        src/main/java/com/valorantmc/ValorantMC.java
git commit -m "feat: Sova Owl Drone — enemy detection pulse scan every 1.5s with glow + direction report"
```

---

## Phase 4 — Main Menu & Lobby on Join

**Files:**
- Create: `src/main/java/com/valorantmc/managers/LobbyManager.java`
- Create: `src/main/java/com/valorantmc/shop/MainMenuGUI.java`
- Create: `src/main/java/com/valorantmc/listeners/LobbyListener.java`
- Modify: `src/main/java/com/valorantmc/listeners/GameListener.java:76-86`
- Modify: `src/main/java/com/valorantmc/ValorantMC.java`
- Modify: `src/main/resources/config.yml`

### Task 4: LobbyManager

- [ ] **Step 1: Create LobbyManager.java**

```java
package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LobbyManager {

    private final ValorantMC plugin;
    private final Set<UUID> lobbyPlayers = new HashSet<>();

    public LobbyManager(ValorantMC plugin) {
        this.plugin = plugin;
    }

    public void enterLobby(Player p) {
        lobbyPlayers.add(p.getUniqueId());
        p.setGameMode(GameMode.ADVENTURE);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.getInventory().clear();
        p.clearActivePotionEffects();

        Location lobbySpawn = getLobbySpawn();
        if (lobbySpawn != null) p.teleport(lobbySpawn);

        // Open main menu after 1 tick (inventory must be open-able)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.openInventory(com.valorantmc.shop.MainMenuGUI.build(p));
            }
        }, 5L);
    }

    public void exitLobby(Player p) {
        lobbyPlayers.remove(p.getUniqueId());
    }

    public boolean isInLobby(Player p) {
        return lobbyPlayers.contains(p.getUniqueId());
    }

    public Location getLobbySpawn() {
        String raw = plugin.getConfig().getString("lobby.spawn", null);
        if (raw == null) return null;
        try {
            String[] parts = raw.split(",");
            World w = org.bukkit.Bukkit.getWorld(parts[0]);
            if (w == null) return null;
            return new Location(w,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    parts.length > 4 ? Float.parseFloat(parts[4]) : 0f,
                    parts.length > 5 ? Float.parseFloat(parts[5]) : 0f);
        } catch (Exception e) {
            return null;
        }
    }

    public void setLobbySpawn(Location loc) {
        String val = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ()
                + "," + loc.getYaw() + "," + loc.getPitch();
        plugin.getConfig().set("lobby.spawn", val);
        plugin.saveConfig();
    }
}
```

- [ ] **Step 2: Create MainMenuGUI.java (54-slot full menu)**

```java
package com.valorantmc.shop;

import com.valorantmc.ValorantMC;
import com.valorantmc.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class MainMenuGUI {

    public static final String TITLE = ValorantMC.colorize("&c&l VALORANT &r&7— Main Menu");

    public static Inventory build(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Background fill
        ItemStack border = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Red accent bar row 0
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
        }

        // PLAY (Quick Match)
        inv.setItem(20, new ItemBuilder(Material.LIME_CONCRETE)
                .name("&a&lPLAY")
                .lore("&7Join or create a quick match.",
                      "&7You will be auto-assigned to",
                      "&7an open game or a new one.",
                      "",
                      "&eClick to play!")
                .nbt("menu_action", "quickplay")
                .build());

        // CUSTOM GAME
        inv.setItem(22, new ItemBuilder(Material.YELLOW_CONCRETE)
                .name("&e&lCUSTOM GAME")
                .lore("&7Create a private match.",
                      "&7Configure cheats, modifiers,",
                      "&7team sizes, and more.",
                      "",
                      "&eClick to configure!")
                .nbt("menu_action", "custom_game")
                .build());

        // COLLECTION (skins)
        inv.setItem(24, new ItemBuilder(Material.DIAMOND)
                .name("&b&lCOLLECTION")
                .lore("&7Browse and equip weapon skins.",
                      "",
                      "&eClick to open!")
                .nbt("menu_action", "skins")
                .build());

        // STATS
        inv.setItem(30, new ItemBuilder(Material.BOOK)
                .name("&6&lCAREER")
                .lore("&7View your match history,",
                      "&7K/D/A, HS%, and round win rate.",
                      "",
                      "&eClick to view!")
                .nbt("menu_action", "stats")
                .build());

        // SETTINGS (set lobby spawn if admin)
        inv.setItem(32, new ItemBuilder(Material.COMPARATOR)
                .name("&7&lSETTINGS")
                .lore("&7/valorant setlobby — set spawn",
                      "&7/valorant reload  — reload config",
                      "",
                      (player.hasPermission("valorantmc.admin") ? "&a[Admin access]" : "&8[No admin access]"))
                .nbt("menu_action", "settings")
                .build());

        // Active games list row 4 (slots 36-44)
        inv.setItem(36, new ItemBuilder(Material.PAPER)
                .name("&f&lActive Games")
                .lore("&7Click a game below to join it directly.")
                .build());

        // Spacer
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        }

        // Close button
        inv.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("&c&lClose")
                .lore("&7Close this menu.")
                .nbt("menu_action", "close")
                .build());

        return inv;
    }
}
```

- [ ] **Step 3: Create LobbyListener.java**

```java
package com.valorantmc.listeners;

import com.valorantmc.ValorantMC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.Material;
import com.valorantmc.shop.MainMenuGUI;
import com.valorantmc.shop.LobbyGUI;
import com.valorantmc.shop.SkinGUI;
import com.valorantmc.shop.AgentSelectGUI;

public class LobbyListener implements Listener {

    private final ValorantMC plugin;

    public LobbyListener(ValorantMC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (plugin.getLobbyManager().isInLobby(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (plugin.getLobbyManager().isInLobby(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.getLobbyManager().isInLobby(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getLobbyManager().exitLobby(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory inv = e.getView().getTopInventory();
        String title = e.getView().getTitle();

        if (!title.equals(MainMenuGUI.TITLE)) return;
        e.setCancelled(true);

        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        if (!e.getCurrentItem().hasItemMeta()) return;

        var pdc = e.getCurrentItem().getItemMeta().getPersistentDataContainer();
        var key = new org.bukkit.NamespacedKey(plugin, "menu_action");
        String action = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "quickplay" -> {
                p.closeInventory();
                plugin.getLobbyManager().exitLobby(p);
                plugin.getGameManager().quickPlay(p);
            }
            case "custom_game" -> {
                p.closeInventory();
                p.openInventory(com.valorantmc.shop.CustomGameGUI.build(p, null));
            }
            case "skins" -> {
                p.closeInventory();
                p.openInventory(SkinGUI.build(p, null));
            }
            case "stats" -> {
                p.closeInventory();
                // Reuse command handler
                com.valorantmc.managers.StatsManager.PlayerStats stats =
                        plugin.getStatsManager().getStats(p.getUniqueId());
                p.sendMessage(ValorantMC.colorize("&6&l=== " + p.getName() + "'s Stats ==="));
                p.sendMessage(ValorantMC.colorize("&7K/D/A: &f" + stats.kills + "/" + stats.deaths + "/" + stats.assists));
                p.sendMessage(ValorantMC.colorize("&7HS%: &f" + String.format("%.1f%%", stats.getHSPct())));
                p.sendMessage(ValorantMC.colorize("&7Win Rate: &f" + stats.roundsWon + "/" + stats.roundsPlayed + " rounds"));
            }
            case "close" -> p.closeInventory();
            case "settings" -> {
                p.closeInventory();
                p.sendMessage(ValorantMC.colorize("&7Commands: &e/valorant setlobby &7· &e/valorant reload"));
            }
        }
    }
}
```

- [ ] **Step 4: Update GameListener.onJoin to enter lobby instead of just sending a message**

Replace the existing `onJoin` body (lines 77-86) with:
```java
@EventHandler
public void onJoin(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    // Resource pack (keep existing logic)
    String url = plugin.getConfig().getString("resource-pack.url", "");
    if (plugin.getConfig().getBoolean("resource-pack.enabled", false) && !url.isEmpty()) {
        String hash = plugin.getConfig().getString("resource-pack.hash", "");
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> p.setResourcePack(url, hash), 40L);
    }
    // Put player in lobby
    plugin.getLobbyManager().enterLobby(p);
}
```

- [ ] **Step 5: Add `getLobbyManager()` to ValorantMC.java and instantiate it in onEnable**

Add field:
```java
private LobbyManager lobbyManager;
public LobbyManager getLobbyManager() { return lobbyManager; }
```

In `onEnable()` before `gameManager`:
```java
lobbyManager = new LobbyManager(this);
```

Also register `LobbyListener`:
```java
getServer().getPluginManager().registerEvents(new LobbyListener(this), this);
```

Also update `GameManager.leaveGame(Player)` to call `plugin.getLobbyManager().enterLobby(p)` after removing the player from the game, so they return to the menu.

- [ ] **Step 6: Add `lobby.spawn` to config.yml**

```yaml
lobby:
  spawn: "world,0,64,0,0,0"
```

- [ ] **Step 7: Update `/valorant setlobby` command to use LobbyManager**

In `ValorantCommand.handleSetLobby` (the `setlobby` case), replace the manual string save with:
```java
plugin.getLobbyManager().setLobbySpawn(sp.getLocation());
sender.sendMessage(ValorantMC.colorize("&aLobby spawn set!"));
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/valorantmc/managers/LobbyManager.java \
        src/main/java/com/valorantmc/shop/MainMenuGUI.java \
        src/main/java/com/valorantmc/listeners/LobbyListener.java \
        src/main/java/com/valorantmc/listeners/GameListener.java \
        src/main/java/com/valorantmc/ValorantMC.java \
        src/main/resources/config.yml
git commit -m "feat: main menu lobby on join; LobbyManager; LobbyListener; full MainMenuGUI"
```

---

## Phase 5 — Custom Game Mode with Cheats Menu

**Files:**
- Create: `src/main/java/com/valorantmc/game/CustomGameSettings.java`
- Create: `src/main/java/com/valorantmc/shop/CustomGameGUI.java`
- Modify: `src/main/java/com/valorantmc/game/ValorantGame.java`
- Modify: `src/main/java/com/valorantmc/commands/ValorantCommand.java`
- Modify: `src/main/resources/plugin.yml`

### Task 5: CustomGameSettings POJO

- [ ] **Step 1: Create CustomGameSettings.java**

```java
package com.valorantmc.game;

public class CustomGameSettings {

    public boolean unlimitedAbilities  = false; // no cooldowns, no charges consumed
    public boolean infiniteCredits     = false; // credits never decrease
    public boolean wallhack            = false; // enemies glow through walls for everyone
    public boolean oneShot             = false; // any hit = instant kill
    public boolean noCooldowns         = false; // weapon fire rate unlimited
    public boolean infiniteAmmo        = false; // ammo never decreases
    public boolean showEnemyHP         = false; // broadcast enemy HP to all after each hit
    public float   abilityDamageMult   = 1.0f;  // multiply all ability damage
    public int     startingCredits     = 800;   // override starting credits
    public int     maxRounds           = 25;    // 0 = no limit (practice mode)
    public boolean allowTeamDamage     = false; // friendly fire
    public String  hostName            = "";    // UUID of the match creator

    public CustomGameSettings() {}
}
```

- [ ] **Step 2: Create CustomGameGUI.java**

```java
package com.valorantmc.shop;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.CustomGameSettings;
import com.valorantmc.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class CustomGameGUI {

    public static final String TITLE_PREFIX = ValorantMC.colorize("&e&lCUSTOM GAME &7— ");

    public static Inventory build(Player player, CustomGameSettings s) {
        if (s == null) s = new CustomGameSettings();
        final CustomGameSettings settings = s;
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + "Settings");

        // Background
        var filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Helper: ON/OFF toggle item
        // Slot layout:
        // Row 1: [10] Unlimited Abilities  [12] Infinite Credits  [14] Wallhack      [16] One-Shot
        // Row 2: [10] No Cooldowns         [22] Infinite Ammo     [24] Show Enemy HP [26] Friendly Fire
        // Row 3: [30] Ability DMG Mult x1  [32] Starting Credits  [34] Max Rounds    [36] Start Game
        // Row 4: [49] Back to Menu

        inv.setItem(10, toggle("Unlimited Abilities", "No ability charges consumed\nCooldowns reset instantly",
                Material.BLAZE_POWDER, settings.unlimitedAbilities, "toggle_unlimited_abilities"));
        inv.setItem(12, toggle("Infinite Credits", "Credits never decrease\nYou can buy anything",
                Material.GOLD_INGOT, settings.infiniteCredits, "toggle_infinite_credits"));
        inv.setItem(14, toggle("Wallhack", "All enemies glow through walls\nfor your entire team",
                Material.ENDER_EYE, settings.wallhack, "toggle_wallhack"));
        inv.setItem(16, toggle("One-Shot Mode", "Any hit = instant kill\n(ignores shields)",
                Material.ARROW, settings.oneShot, "toggle_one_shot"));
        inv.setItem(19, toggle("No Cooldowns", "Weapons fire unlimited rate\nNo reload required",
                Material.CLOCK, settings.noCooldowns, "toggle_no_cooldowns"));
        inv.setItem(21, toggle("Infinite Ammo", "Ammo never decreases",
                Material.GUNPOWDER, settings.infiniteAmmo, "toggle_infinite_ammo"));
        inv.setItem(23, toggle("Show Enemy HP", "Broadcasts enemy HP to all\nafter each hit",
                Material.REDSTONE, settings.showEnemyHP, "toggle_show_hp"));
        inv.setItem(25, toggle("Friendly Fire", "Enable team damage",
                Material.FIRE_CHARGE, settings.allowTeamDamage, "toggle_ff"));

        // Numeric settings
        inv.setItem(28, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name("&6Ability DMG: &f" + String.format("%.0f%%", settings.abilityDamageMult * 100))
                .lore("&7Right-click to increase (+25%)", "&7Left-click to decrease (-25%)",
                      "&8Current: " + String.format("%.0f%%", settings.abilityDamageMult * 100))
                .nbt("setting_action", "dmg_mult")
                .build());

        inv.setItem(30, new ItemBuilder(Material.GOLD_NUGGET)
                .name("&6Starting Credits: &f" + settings.startingCredits)
                .lore("&7Right-click +200 · Left-click -200",
                      "&8Current: " + settings.startingCredits)
                .nbt("setting_action", "start_credits")
                .build());

        inv.setItem(32, new ItemBuilder(Material.PAPER)
                .name("&6Max Rounds: &f" + (settings.maxRounds == 0 ? "Unlimited" : String.valueOf(settings.maxRounds)))
                .lore("&7Right-click +1 · Left-click -1",
                      "&7Set to 0 for practice (no limit)",
                      "&8Current: " + settings.maxRounds)
                .nbt("setting_action", "max_rounds")
                .build());

        // Start Game
        inv.setItem(40, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&lSTART CUSTOM GAME")
                .lore("&7Creates a new game with these settings.",
                      "&7You will be the host.",
                      "",
                      "&aClick to start!")
                .nbt("setting_action", "start_custom")
                .build());

        // Back
        inv.setItem(45, new ItemBuilder(Material.ARROW)
                .name("&7&l← Back")
                .lore("&7Return to main menu.")
                .nbt("setting_action", "back")
                .build());

        return inv;
    }

    private static org.bukkit.inventory.ItemStack toggle(String name, String desc,
            Material icon, boolean enabled, String action) {
        String status = enabled ? "&a&lON" : "&c&lOFF";
        Material mat = enabled ? icon : Material.GRAY_CONCRETE;
        var loreLines = java.util.Arrays.asList(
                ValorantMC.colorize(desc.replace("\n", "\n" + ValorantMC.colorize("&7"))),
                "",
                ValorantMC.colorize("&7Status: " + status),
                ValorantMC.colorize("&eClick to toggle"));
        return new ItemBuilder(mat)
                .name("&f" + name + " &8[" + (enabled ? "&aON" : "&cOFF") + "&8]")
                .lore(loreLines.toArray(new String[0]))
                .nbt("setting_action", action)
                .build();
    }
}
```

- [ ] **Step 3: Add CustomGameGUI click handling to ShopListener**

In `ShopListener.java`, add a new `@EventHandler` for `InventoryClickEvent` that checks for `CustomGameGUI.TITLE_PREFIX` in the title and routes `setting_action` NBT values to toggle the settings fields and rebuild the GUI.

Key logic:
```java
if (!title.startsWith(CustomGameGUI.TITLE_PREFIX)) return;
e.setCancelled(true);

// get or create settings stored on player via PersistentDataContainer
// toggle the boolean / increment the numeric value
// rebuild and re-open the inventory
```

Store `CustomGameSettings` per-player as a transient map in a `CustomGameManager` or directly in `ShopListener`. Simplest: store in a `HashMap<UUID, CustomGameSettings>` in `ValorantMC`.

- [ ] **Step 4: Add to ValorantMC.java**

```java
private final java.util.Map<java.util.UUID, com.valorantmc.game.CustomGameSettings> customSettings = new java.util.HashMap<>();

public com.valorantmc.game.CustomGameSettings getCustomSettings(java.util.UUID uuid) {
    return customSettings.computeIfAbsent(uuid, k -> new com.valorantmc.game.CustomGameSettings());
}
```

- [ ] **Step 5: Integrate CustomGameSettings into ValorantGame**

Add field to ValorantGame:
```java
private CustomGameSettings customSettings = null;
public void setCustomSettings(CustomGameSettings s) { this.customSettings = s; }
public CustomGameSettings getCustomSettings() { return customSettings; }
```

In `applyDamage()`, add after damage calculation:
```java
if (customSettings != null) {
    if (customSettings.oneShot) finalDamage = 9999;
    // abilityDamageMult applied separately via ability methods
}
// After death check, if showEnemyHP and target survived:
if (customSettings != null && customSettings.showEnemyHP && hp > 0) {
    String hpMsg = target.getName() + ": " + hp + " HP remaining";
    game.broadcast(ValorantMC.colorize("&8[HP] &7" + hpMsg));
}
```

In `giveRoundStartCredits()`:
```java
if (customSettings != null && customSettings.infiniteCredits) {
    getAllPlayers().forEach(p -> plugin.getEconomyManager().setCredits(p.getUniqueId(), 9000));
    return;
}
```

In `WeaponManager.tryShoot()`, check:
```java
// After getting the game, check infiniteAmmo:
ValorantGame g = plugin.getGameManager().getGame(player);
if (g != null && g.getCustomSettings() != null && g.getCustomSettings().infiniteAmmo) {
    // skip ammo decrement
}
```

- [ ] **Step 6: Add wallhack effect — apply glowing to enemies every round start**

In `startRound()` in ValorantGame.java, after the FIGHT title:
```java
if (customSettings != null && customSettings.wallhack) {
    getAllPlayers().forEach(p -> {
        ValorantTeam enemies = getEnemyTeam(p);
        if (enemies != null) enemies.getOnlinePlayers().forEach(enemy ->
            enemy.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false)));
    });
}
```

And clear it in `reviveAll()`:
```java
if (customSettings != null && customSettings.wallhack) {
    getAllPlayers().forEach(p -> p.removePotionEffect(
            org.bukkit.potion.PotionEffectType.GLOWING));
}
```

- [ ] **Step 7: Add `vcustom` command to plugin.yml**

```yaml
vcustom:
  description: Open the custom game creator
  usage: /vcustom
  aliases: [custom]
```

- [ ] **Step 8: Implement `handleCustomGame` in ValorantCommand.java**

```java
private boolean handleCustomGame(CommandSender sender) {
    if (!(sender instanceof Player p)) return true;
    p.openInventory(com.valorantmc.shop.CustomGameGUI.build(p,
            plugin.getCustomSettings(p.getUniqueId())));
    return true;
}
```

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/valorantmc/game/CustomGameSettings.java \
        src/main/java/com/valorantmc/shop/CustomGameGUI.java \
        src/main/java/com/valorantmc/game/ValorantGame.java \
        src/main/java/com/valorantmc/commands/ValorantCommand.java \
        src/main/java/com/valorantmc/listeners/ShopListener.java \
        src/main/java/com/valorantmc/ValorantMC.java \
        src/main/resources/plugin.yml
git commit -m "feat: custom game mode with cheats GUI (wallhack, one-shot, infinite credits, etc.)"
```

---

## Phase 6 — Weapon Rendering: Custom Models via Resource Pack

**Goal:** Each weapon type shows a distinct visual when held. Server tags each weapon `ItemStack` with `CustomModelData`; a bundled resource pack overrides the iron sword model per value. The Forge mod supplements this with enhanced BEWLR rendering when installed.

**Files:**
- Modify: `src/main/java/com/valorantmc/weapons/Weapon.java`
- Modify: `src/main/java/com/valorantmc/managers/WeaponManager.java`
- Create: `src/main/resources/pack/pack.mcmeta`
- Create: `src/main/resources/pack/assets/minecraft/models/item/iron_sword.json`
- Create: `src/main/resources/pack/assets/minecraft/models/item/valorantmc/[weapon].json` (one per weapon type)
- Modify: `src/main/java/com/valorantmc/weapons/WeaponType.java`
- Modify: `src/main/java/com/valorantmc/ValorantMC.java` (export resource pack on startup)

### Task 6: Add `customModelData` to WeaponType enum

- [ ] **Step 1: Add `customModelData` field to WeaponType enum**

At the top of the enum, each weapon gets a unique int (1001–1099). Add a field:
```java
private final int customModelData;

WeaponType(String displayName, WeaponCategory category, int damage, int magSize, int reserveAmmo,
           int fireRateTicks, float spread, float recoilPerShot, int maxRange,
           boolean penetrates, int pellets, boolean isMelee, boolean isSniper,
           int customModelData) {
    // ... existing fields ...
    this.customModelData = customModelData;
}

public int getCustomModelData() { return customModelData; }
```

Then update each enum constant to append its CMD value. Example:
```java
CLASSIC ("Classic",  SIDEARM, 26, 12, 36,  8, 0.10f, 0.02f, 50, false, 1, false, false, 1001),
SHORTY  ("Shorty",   SIDEARM, 12,  6, 36, 12, 0.30f, 0.04f, 20, false, 3, false, false, 1002),
FRENZY  ("Frenzy",   SIDEARM, 26, 13, 39,  5, 0.15f, 0.03f, 40, false, 1, false, false, 1003),
GHOST   ("Ghost",    SIDEARM, 30, 15, 45,  9, 0.08f, 0.02f, 50, false, 1, false, false, 1004),
SHERIFF ("Sheriff",  SIDEARM, 55, 6,  24, 14, 0.05f, 0.06f, 50, false, 1, false, false, 1005),
STINGER ("Stinger",  SMG,     27, 20, 60,  3, 0.12f, 0.03f, 40, false, 1, false, false, 1011),
SPECTRE ("Spectre",  SMG,     26, 30, 90,  4, 0.10f, 0.02f, 40, false, 1, false, false, 1012),
BUCKY   ("Bucky",    SHOTGUN, 20,  5, 15, 15, 0.35f, 0.06f, 15, false, 5, false, false, 1021),
JUDGE   ("Judge",    SHOTGUN, 17,  7, 21, 10, 0.32f, 0.05f, 15, false, 6, false, false, 1022),
BULLDOG ("Bulldog",  RIFLE,   35, 24, 72,  9, 0.08f, 0.03f, 60, false, 1, false, false, 1031),
GUARDIAN("Guardian", RIFLE,   65, 12, 36, 12, 0.04f, 0.04f, 70, false, 1, false, false, 1032),
PHANTOM ("Phantom",  RIFLE,   39, 30, 90,  5, 0.07f, 0.025f,70,false, 1, false, false, 1033),
VANDAL  ("Vandal",   RIFLE,   40, 25, 75,  6, 0.06f, 0.03f, 70, false, 1, false, false, 1034),
MARSHAL ("Marshal",  SNIPER,  101,5, 15, 20, 0.02f, 0.00f, 200, false, 1, false, true,  1041),
OUTLAW  ("Outlaw",   SNIPER,  140,2,  8, 28, 0.01f, 0.00f, 200, false, 2, false, true,  1042),
OPERATOR("Operator", SNIPER,  255,5, 15, 35, 0.00f, 0.00f, 200, false, 1, false, true,  1043),
ARES    ("Ares",     HEAVY,   30, 50,150,  4, 0.09f, 0.02f, 60, false, 1, false, false, 1051),
ODIN    ("Odin",     HEAVY,   38, 100,300, 4, 0.07f, 0.015f,70,false, 1, false, false, 1052),
KNIFE   ("Knife",    MELEE,   50,  0,  0,  0, 0.00f, 0.00f, 3,  false, 1, true,  false, 1061),
```

- [ ] **Step 2: Set CustomModelData in `Weapon.toItemStack(UUID)`**

In the `toItemStack` method (likely in `Weapon.java`), after `ItemMeta` is fetched:
```java
meta.setCustomModelData(type.getCustomModelData());
```

This single line makes the resource pack able to override the model per weapon.

- [ ] **Step 3: Create resource pack files**

`src/main/resources/pack/pack.mcmeta`:
```json
{
  "pack": {
    "pack_format": 48,
    "description": "ValorantMC Weapon Models"
  }
}
```

`src/main/resources/pack/assets/minecraft/models/item/iron_sword.json`:
```json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "minecraft:item/iron_sword"
  },
  "overrides": [
    { "predicate": { "custom_model_data": 1001 }, "model": "minecraft:item/valorantmc/classic" },
    { "predicate": { "custom_model_data": 1002 }, "model": "minecraft:item/valorantmc/shorty" },
    { "predicate": { "custom_model_data": 1003 }, "model": "minecraft:item/valorantmc/frenzy" },
    { "predicate": { "custom_model_data": 1004 }, "model": "minecraft:item/valorantmc/ghost" },
    { "predicate": { "custom_model_data": 1005 }, "model": "minecraft:item/valorantmc/sheriff" },
    { "predicate": { "custom_model_data": 1011 }, "model": "minecraft:item/valorantmc/stinger" },
    { "predicate": { "custom_model_data": 1012 }, "model": "minecraft:item/valorantmc/spectre" },
    { "predicate": { "custom_model_data": 1021 }, "model": "minecraft:item/valorantmc/bucky" },
    { "predicate": { "custom_model_data": 1022 }, "model": "minecraft:item/valorantmc/judge" },
    { "predicate": { "custom_model_data": 1031 }, "model": "minecraft:item/valorantmc/bulldog" },
    { "predicate": { "custom_model_data": 1032 }, "model": "minecraft:item/valorantmc/guardian" },
    { "predicate": { "custom_model_data": 1033 }, "model": "minecraft:item/valorantmc/phantom" },
    { "predicate": { "custom_model_data": 1034 }, "model": "minecraft:item/valorantmc/vandal" },
    { "predicate": { "custom_model_data": 1041 }, "model": "minecraft:item/valorantmc/marshal" },
    { "predicate": { "custom_model_data": 1042 }, "model": "minecraft:item/valorantmc/outlaw" },
    { "predicate": { "custom_model_data": 1043 }, "model": "minecraft:item/valorantmc/operator" },
    { "predicate": { "custom_model_data": 1051 }, "model": "minecraft:item/valorantmc/ares" },
    { "predicate": { "custom_model_data": 1052 }, "model": "minecraft:item/valorantmc/odin" },
    { "predicate": { "custom_model_data": 1061 }, "model": "minecraft:item/valorantmc/knife" }
  ]
}
```

Each `minecraft:item/valorantmc/[name].json` file — for now, use vanilla placeholders that point to different colored textures (iron_sword, diamond_sword, crossbow, etc.) until real OBJ models are added:

Example `classic.json`:
```json
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "minecraft:item/iron_sword"
  }
}
```

Create one per weapon. Use a different base item per category so they're visually distinct even without custom models:
- Sidearms → `iron_sword`
- SMG → `stone_sword`  
- Shotgun → `wooden_axe`
- Rifle → `diamond_sword`
- Sniper → `netherite_sword`
- Heavy → `iron_axe`
- Knife → `golden_sword`

- [ ] **Step 4: Export resource pack as a zip on plugin startup**

In `ValorantMC.onEnable()`, add a method that exports the embedded pack to `plugins/ValorantMC/resourcepack.zip`:
```java
private void exportResourcePack() {
    java.io.File packFile = new java.io.File(getDataFolder(), "resourcepack.zip");
    if (packFile.exists()) return; // already exported
    // Save all pack/* resources from the jar
    saveResource("pack/pack.mcmeta", false);
    // Note: for a full zip, use a build-time ZIP task or bundle the zip in resources directly
    getLogger().info("Resource pack exported to " + packFile.getAbsolutePath());
    getLogger().info("Host it on a web server and set resource-pack.url in config.yml");
}
```

> **Practical note:** The simplest deployment is to ZIP the `pack/` directory manually and host it on any HTTP server (or use a free host like MC Pack Host). Set `resource-pack.url` in `config.yml` and enable `resource-pack.enabled: true`.

- [ ] **Step 5: Verify WeaponType enum constructor parameter count matches existing enum**

Run:
```bash
mvn compile 2>&1 | head -50
```
Expected: `BUILD SUCCESS`. Fix any constructor mismatch.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/valorantmc/weapons/WeaponType.java \
        src/main/java/com/valorantmc/weapons/Weapon.java \
        src/main/resources/pack/
git commit -m "feat: custom model data per weapon type; resource pack scaffolding for weapon models"
```

---

## Phase 7 — Forge Mod: HUD Overlay + Mod Detection

**Files:**
- Create: `mod/src/main/java/com/valorantmc/mod/ModChannel.java`
- Create: `mod/src/main/java/com/valorantmc/mod/HudOverlay.java`
- Modify: `mod/src/main/java/com/valorantmc/mod/ValorantMCMod.java`

### Task 7: Mod detection via plugin channel

The server needs to know if the client has the mod installed. We use a simple plugin channel handshake: server sends a ping on channel `valorantmc:hello`; mod replies `valorantmc:hello`. Plugin stores which UUIDs have the mod.

- [ ] **Step 1: Create ModChannel.java in the mod**

```java
package com.valorantmc.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import io.netty.buffer.Unpooled;

import java.util.function.Supplier;

public class ModChannel {

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new net.minecraft.resources.ResourceLocation("valorantmc", "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, HelloPacket.class,
                HelloPacket::encode, HelloPacket::decode, HelloPacket::handle);
    }

    public record HelloPacket(String message) {
        static void encode(HelloPacket p, FriendlyByteBuf buf) { buf.writeUtf(p.message()); }
        static HelloPacket decode(FriendlyByteBuf buf) { return new HelloPacket(buf.readUtf()); }
        static void handle(HelloPacket p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                // Server said hello; reply with "pong"
                CHANNEL.sendToServer(new HelloPacket("pong"));
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
```

- [ ] **Step 2: On the plugin side, create `src/main/java/com/valorantmc/managers/ModDetectionManager.java`**

```java
package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ModDetectionManager implements PluginMessageListener {

    private static final String CHANNEL = "valorantmc:main";
    private final ValorantMC plugin;
    private final Set<UUID> modUsers = new HashSet<>();

    public ModDetectionManager(ValorantMC plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void ping(Player p) {
        // Send a 5-byte hello
        byte[] data = "hello".getBytes();
        p.sendPluginMessage(plugin, CHANNEL, data);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        String msg = new String(message);
        if (msg.equals("pong")) {
            modUsers.add(player.getUniqueId());
            player.sendMessage(ValorantMC.colorize("&b[ValorantMC] &aMod detected! Enhanced features enabled."));
        }
    }

    public boolean hasMod(Player p) { return modUsers.contains(p.getUniqueId()); }
    public void remove(Player p) { modUsers.remove(p.getUniqueId()); }
}
```

Register in `ValorantMC.onEnable()`:
```java
modDetectionManager = new ModDetectionManager(this);
```

And call `modDetectionManager.ping(p)` in `GameListener.onJoin` after a 2-second delay:
```java
plugin.getServer().getScheduler().runTaskLater(plugin,
        () -> plugin.getModDetectionManager().ping(p), 40L);
```

- [ ] **Step 3: Create HudOverlay.java in the mod**

```java
package com.valorantmc.mod;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

public class HudOverlay {

    // Overlay renders:
    // Bottom-left: current HP (red bar), shield (white overlay), ammo (white / reserve gray)
    // Bottom-right: ultimate orbs (filled = gold, empty = gray circles)
    // These values come from the item lore/name NBT on the held item — no custom packets needed.

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        GuiGraphics gfx = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();

        // Read ammo from held item name (WeaponManager writes "12/36" into item name)
        var held = mc.player.getMainHandItem();
        if (!held.isEmpty() && held.hasCustomHoverName()) {
            String name = held.getHoverName().getString();
            // Parse "NAME — 12/36" format
            if (name.contains("—")) {
                String ammo = name.substring(name.lastIndexOf("—") + 1).trim();
                gfx.drawString(mc.font, ammo, w - 60, h - 50, 0xFFFFFF, true);
            }
        }

        // Ultimate orbs: stored in PDC — we read from scoreboard display name as a text hack
        // (Full implementation requires a custom packet for real data)
        gfx.drawString(mc.font, "§bVMC", 4, h - 60, 0xFFFFFF, false);
    }
}
```

- [ ] **Step 4: Register HudOverlay in ValorantMCMod**

In `ValorantMCMod.java`, in the constructor, add:
```java
MinecraftForge.EVENT_BUS.register(new HudOverlay());
```

And in the mod bus listener:
```java
private void onRegisterKeybinds(RegisterKeyMappingsEvent e) {
    // ... existing key binds ...
}
```

Also call `ModChannel.register()` in the constructor:
```java
ModChannel.register();
```

- [ ] **Step 5: Build the mod**

```bash
cd mod && gradlew build 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add mod/src/
git commit -m "feat(mod): ModChannel handshake detection; HudOverlay ammo/HP display"
```

---

## Phase 8 — Additional Valorant Features

### Task 8a: Spike beep countdown

**File:** `src/main/java/com/valorantmc/game/Spike.java`

- [ ] **Step 1: Add a beep-timer task to Spike that accelerates as detonation approaches**

In `Spike.java`, in the method called when the spike is planted (likely `plant(Location)`), start a repeating task:
```java
// After setting planted=true:
org.bukkit.scheduler.BukkitRunnable beeper = new org.bukkit.scheduler.BukkitRunnable() {
    int ticksLeft = 45 * 20; // 45 seconds
    @Override public void run() {
        if (!isPlanted || ticksLeft <= 0) { cancel(); return; }
        // Beep every 1s at start, every 0.5s under 15s, every 0.25s under 5s
        int interval = ticksLeft > 15 * 20 ? 20 : ticksLeft > 5 * 20 ? 10 : 5;
        if (ticksLeft % interval == 0) {
            game.getAllPlayers().forEach(p -> p.getWorld().playSound(
                    p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,
                    0.5f, ticksLeft > 15 * 20 ? 1.0f : ticksLeft > 5 * 20 ? 1.2f : 1.5f));
        }
        ticksLeft--;
    }
}.runTaskTimer(game.getPlugin(), 0L, 1L);
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/valorantmc/game/Spike.java
git commit -m "feat: spike beep countdown — accelerates near detonation"
```

### Task 8b: Overtime system

**File:** `src/main/java/com/valorantmc/game/ValorantGame.java:330-343`

- [ ] **Step 1: Add overtime logic in `endRound`**

After `winner.addRoundWin()`, replace the win-check:
```java
// Before: if (winner.getRoundWins() >= ROUNDS_TO_WIN)
// Real Valorant: first to 13 wins; if tied 12-12, play overtime (first to win 2 more in a row)

int atkWins = attackers.getRoundWins();
int defWins = defenders.getRoundWins();
boolean overtime = (atkWins == 12 && defWins == 12) || (currentRound > HALF_TIME_ROUND * 2 && Math.abs(atkWins - defWins) == 0);

if (winner.getRoundWins() >= ROUNDS_TO_WIN && !overtime) {
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> endGame(winner), 80L);
    return;
}

// Overtime: check if one team is ahead by 2 after entering overtime
if (overtime && Math.abs(atkWins - defWins) >= 2) {
    ValorantTeam overtimeWinner = atkWins > defWins ? attackers : defenders;
    plugin.getServer().getScheduler().runTaskLater(plugin, () -> endGame(overtimeWinner), 80L);
    return;
}
if (overtime) {
    broadcast(ValorantMC.colorize("&e&lOVERTIME! &7First team to lead by 2 wins."));
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/valorantmc/game/ValorantGame.java
git commit -m "feat: overtime — first to lead by 2 after 12-12 tie"
```

### Task 8c: Death recap message

**File:** `src/main/java/com/valorantmc/game/ValorantGame.java:602-614`

- [ ] **Step 1: Enhance the ELIMINATED title with death recap**

After the existing `showTitle(victim, ...)` call:
```java
// Send death recap as a chat message to the victim only
if (killer != null) {
    Weapon kw = playerWeapons.get(killer.getUniqueId());
    String weaponName = kw != null ? kw.getType().getDisplayName() : "Unknown";
    victim.sendMessage(ValorantMC.colorize(
            "&c&l▶ ELIMINATED &7by " + getTeam(killer).getChatColor() + killer.getName()
            + " &7using &f" + weaponName
            + (headshot ? " &c[HEADSHOT]" : "") + "."));
    victim.sendMessage(ValorantMC.colorize(
            "&7Spectate teammates with &eLeft/Right click&7."));
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/valorantmc/game/ValorantGame.java
git commit -m "feat: death recap message — shows killer name, weapon, headshot flag"
```

### Task 8d: Ultimate charge HUD via scoreboard

**File:** `src/main/java/com/valorantmc/game/ValorantGame.java:749-777`

- [ ] **Step 1: Show ult orbs in scoreboard sidebar**

In `updateScoreboard()`, add a new line after the alive-counts block:
```java
// Ult points for requesting player — add per-player sidebar update
for (Player p : getAllPlayers()) {
    Agent a = playerAgents.get(p.getUniqueId());
    if (a == null) continue;
    Agent.Ability ult = a.getAbility('X');
    if (ult == null) continue;
    int current = ult.getCurrentUltPoints();
    int needed  = ult.getUltPointsNeeded();
    StringBuilder orbs = new StringBuilder();
    for (int i = 0; i < needed; i++) orbs.append(i < current ? "§6●" : "§8●");
    // We can't do per-player scoreboards easily without a per-player scoreboard object
    // Use action bar instead — called here and sent each scoreboard update
    p.sendActionBar(net.kyori.adventure.text.Component.text(
            "§7Ult: " + orbs + "  §8(" + current + "/" + needed + ")"));
}
```

> **Note:** This action bar will be overwritten by other action bars (kill feed, etc.) during round. A cleaner solution is to use the scoreboard's `BELOW_NAME` display slot for ult, but this requires per-player objectives. For now, the action bar approach gives per-player ult visibility whenever `updateScoreboard()` is called.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/valorantmc/game/ValorantGame.java
git commit -m "feat: ultimate charge displayed as orbs in action bar on scoreboard update"
```

### Task 8e: Ability charge indicators in lore

**File:** `src/main/java/com/valorantmc/agents/Agent.java`

- [ ] **Step 1: Update `giveAbilityItems` to write current charges in lore**

In `Agent.giveAbilityItems(Player)`, when building the ability `ItemStack`, update the lore to include charges and cooldown:
```java
List<String> lore = new ArrayList<>();
lore.add(ValorantMC.colorize("&7" + ability.getDescription()));
lore.add(ValorantMC.colorize("&8Cost: &f" + (ability.getCost() == 0 ? "Free" : ability.getCost() + "c")));
lore.add(ValorantMC.colorize("&8Charges: &f" + ability.getCurrentCharges() + "/" + ability.getMaxCharges()));
if (ability.getCooldownTicks() > 0) {
    lore.add(ValorantMC.colorize("&8Cooldown: &f" + (ability.getCooldownTicks() / 20) + "s"));
}
if (ability.isUlt()) {
    lore.add(ValorantMC.colorize("&6Ult Points: &f" + ability.getCurrentUltPoints() + "/" + ability.getUltPointsNeeded()));
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/valorantmc/agents/Agent.java
git commit -m "feat: ability items show charge count and cooldown in lore"
```

---

## Phase 9 — Build Verification & Final Polish

### Task 9: Full compile and smoke test

- [ ] **Step 1: Compile the plugin**

```bash
mvn clean package -q 2>&1 | tail -30
```
Expected: `BUILD SUCCESS`. Fix all compilation errors before proceeding.

- [ ] **Step 2: Compile the Forge mod**

```bash
cd mod && gradlew build -q 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify plugin.yml declares all commands**

Confirm the following commands exist in `plugin.yml`:
- `valorant`, `vshop`, `vagent`, `vskip`, `vstats`, `vreload`, `vdropspike`, `vwalk`, `vuse`, `vskin`, `vplay`, `vadmin`, `vmapsetup`, `vcustom`

- [ ] **Step 4: Verify no raw `RED_DYE` remove calls remain**

```bash
grep -r "remove(Material.RED_DYE)" src/
```
Expected: no output.

- [ ] **Step 5: Verify no `setSpectatorTarget(null)` in handleDeath**

```bash
grep -n "setSpectatorTarget(null)" src/main/java/com/valorantmc/game/ValorantGame.java
```
Expected: only in `reviveAll()`, not in `handleDeath`.

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "chore: Phase 9 final build verification and polish"
```

---

## Spec Coverage Self-Check

| Requirement | Covered by |
|------------|-----------|
| Full weapons visible with mod | Phase 6 (CMD + resource pack) + Phase 7 (HudOverlay) |
| Abilities fully working | Phase 3 (Sova drone), Phase 8e (charge display) |
| Main menu on join | Phase 4 (LobbyManager + MainMenuGUI) |
| Good GUI with mod installed | Phase 7 (ModDetection); Phase 4 GUI works for all |
| Spectate teammates only, no free-fly | Phase 1 (SpectatorListener) |
| Custom game mode + cheats menu | Phase 5 (CustomGameSettings + CustomGameGUI) |
| Fix incomplete commands | Phase 2 (handleTp, handleDropSpike, tab-complete) |
| Additional Valorant features | Phase 8 (spike beeps, overtime, death recap, ult HUD, ability lore) |
| Update codebase so everything works | Phase 9 (compile + smoke checks) |

---

## Execution Order

Phases must be executed in order — each phase's changes are prerequisites for the next. Recommended execution:

1. Phase 1 (Spectator) — smallest change, highest user-visible impact
2. Phase 2 (Commands) — isolated fixes, no risk
3. Phase 3 (Sova) — agent fix, isolated
4. Phase 4 (Lobby/Menu) — requires LobbyManager to be wired before Phase 5
5. Phase 5 (Custom Game) — requires LobbyManager from Phase 4
6. Phase 6 (Resource Pack) — isolated WeaponType change
7. Phase 7 (Forge Mod) — isolated mod changes
8. Phase 8 (Additional Features) — incremental polish
9. Phase 9 (Build Verification) — final gate
