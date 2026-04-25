package com.valorantmc.listeners;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.ValorantGame;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import com.valorantmc.game.GameState;
import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;

public class GameListener implements Listener {

    private final ValorantMC plugin;

    public GameListener(ValorantMC plugin) {
        this.plugin = plugin;
    }

    // ── Cancel default fall/environment damage for in-game players ────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        ValorantGame game = plugin.getGameManager().getGame(player);
        if (game == null) return;

        // Only cancel environmental damage — player-vs-player is in WeaponListener
        switch (e.getCause()) {
            case FALL, FIRE, FIRE_TICK, LAVA, POISON, WITHER,
                    DROWNING, SUFFOCATION, VOID -> e.setCancelled(true);
            default -> {}
        }
    }

    // ── Prevent hunger during game ─────────────────────────────────────────────

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (plugin.getGameManager().isInGame(p)) e.setCancelled(true);
    }

    // ── Resource pack acceptance ────────────────────────────────────────────────

    @EventHandler
    public void onResourcePack(PlayerResourcePackStatusEvent e) {
        if (!plugin.getConfig().getBoolean("resource-pack.required", false)) return;
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED ||
                e.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            e.getPlayer().kickPlayer(ValorantMC.colorize(
                    "&cYou must accept the resource pack to play ValorantMC!"));
        }
    }

    // ── Player join: resource pack ────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Resource pack
        String url = plugin.getConfig().getString("resource-pack.url", "");
        if (plugin.getConfig().getBoolean("resource-pack.enabled", false) && !url.isEmpty()) {
            String hash = plugin.getConfig().getString("resource-pack.hash", "");
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> p.setResourcePack(url, hash), 40L);
        }
        // Send to lobby with main menu
        plugin.getLobbyManager().enterLobby(p);
    }

    // ── Spike pickup from the ground ──────────────────────────────────────────

    @EventHandler
    public void onSpikePickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        ValorantGame game = plugin.getGameManager().getGame(p);
        if (game == null) return;

        var meta = e.getItem().getItemStack().getItemMeta();
        if (meta == null) return;
        Boolean isSpike = meta.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "spike"), PersistentDataType.BOOLEAN);
        if (!Boolean.TRUE.equals(isSpike)) return;

        // Only attackers can carry the spike
        var team = game.getTeam(p);
        if (team == null || team.getSide() != com.valorantmc.game.ValorantTeam.Side.ATTACKERS) {
            e.setCancelled(true);
            return;
        }
        if (game.getSpike().getCarrierUUID() != null) {
            // Someone already has it
            e.setCancelled(true);
            return;
        }
        game.getSpike().pickupFromGround(p);
        // Let the pickup proceed so the player gets the item in inventory
    }

    // ── Player quit: remove from game ─────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        // Remove from game without triggering lobby re-entry (player is disconnecting)
        if (plugin.getGameManager().isInGame(p)) {
            String gameId = plugin.getGameManager().getGame(p).getId();
            plugin.getGameManager().getGame(p).removePlayer(p);
            // Remove from map directly so leaveGame doesn't try to re-enter lobby
        }
        plugin.getLobbyManager().exitLobby(p);
        plugin.getEconomyManager().savePlayer(p.getUniqueId());
    }

    // ── Block interaction lockdown ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!plugin.getGameManager().isInGame(e.getPlayer())) return;
        if (e.getPlayer().hasPermission("valorantmc.admin")) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!plugin.getGameManager().isInGame(e.getPlayer())) return;
        if (e.getPlayer().hasPermission("valorantmc.admin")) return;
        e.setCancelled(true);
    }

    // ── Item drop lockdown ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent e) {
        if (!plugin.getGameManager().isInGame(e.getPlayer())) return;
        e.setCancelled(true);
    }

    // ── Inventory manipulation lockdown ───────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ValorantGame game = plugin.getGameManager().getGame(p);
        if (game == null) return;
        // Allow GUI interactions (shop, agent select) — only lock player's own inventory
        Inventory inv = e.getClickedInventory();
        if (inv == null) return;
        if (inv.getType() == InventoryType.PLAYER || inv.getType() == InventoryType.CRAFTING) {
            GameState st = game.getState();
            if (st == GameState.ROUND_ACTIVE || st == GameState.BUY_PHASE) {
                e.setCancelled(true);
            }
        }
    }

    // ── Container interaction lockdown ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onContainerOpen(org.bukkit.event.player.PlayerInteractEvent e) {
        if (!plugin.getGameManager().isInGame(e.getPlayer())) return;
        if (e.getClickedBlock() == null) return;
        Material m = e.getClickedBlock().getType();
        // Block opening of storage containers
        if (m == Material.CHEST || m == Material.BARREL || m == Material.HOPPER
                || m == Material.DROPPER || m == Material.DISPENSER
                || m == Material.FURNACE || m == Material.CRAFTING_TABLE
                || m == Material.ANVIL) {
            e.setCancelled(true);
        }
    }

    // ── Swap-hand intercept ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        if (!plugin.getGameManager().isInGame(e.getPlayer())) return;
        // Intercept: trigger reload instead of swap (WeaponListener already handles this)
        e.setCancelled(true);
    }

    // ── Anti-cheat: flight prevention + spawn barrier ────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getGameManager().isInGame(p)) return;

        // Flight prevention
        if (p.isFlying() && p.getGameMode() != GameMode.SPECTATOR) {
            p.setFlying(false);
            p.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§c[Anti-cheat] Flight is disabled in ValorantMC."));
        }

        // Spawn barrier — only active during BUY_PHASE
        ValorantGame game = plugin.getGameManager().getGame(p);
        if (game == null || game.getState() != GameState.BUY_PHASE) return;
        if (e.getTo() == null) return;
        org.bukkit.Location lock = game.getSpawnLock(p);
        if (lock == null) return;

        // Only enforce if the player actually crossed the radius (ignore Y)
        double dx = e.getTo().getX() - lock.getX();
        double dz = e.getTo().getZ() - lock.getZ();
        double distSq = dx * dx + dz * dz;
        double radiusSq = com.valorantmc.game.ValorantGame.BARRIER_RADIUS
                        * com.valorantmc.game.ValorantGame.BARRIER_RADIUS;
        if (distSq > radiusSq) {
            e.setCancelled(true);
            p.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§c§l◼ SPAWN BARRIER  §r§7— Round starts soon!"));
        }
    }
}
