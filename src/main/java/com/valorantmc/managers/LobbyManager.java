package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import org.bukkit.Bukkit;
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

    /** Puts the player in lobby state: adventure mode, clear inventory, teleport, open menu. */
    public void enterLobby(Player p) {
        lobbyPlayers.add(p.getUniqueId());
        p.setGameMode(GameMode.ADVENTURE);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.getInventory().clear();
        p.clearActivePotionEffects();
        p.setFlying(false);
        p.setAllowFlight(false);

        Location spawn = getLobbySpawn();
        if (spawn != null) p.teleport(spawn);

        // Open main menu after a short delay so the inventory state is clean
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.openInventory(com.valorantmc.shop.MainMenuGUI.build(p, plugin));
            }
        }, 5L);
    }

    /** Removes player from lobby tracking (call when they join a game). */
    public void exitLobby(Player p) {
        lobbyPlayers.remove(p.getUniqueId());
    }

    public boolean isInLobby(Player p) {
        return lobbyPlayers.contains(p.getUniqueId());
    }

    public Location getLobbySpawn() {
        String raw = plugin.getConfig().getString("lobby.spawn", null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            String[] parts = raw.split(",");
            World w = Bukkit.getWorld(parts[0].trim());
            if (w == null) return null;
            float yaw   = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0f;
            return new Location(w,
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()),
                    yaw, pitch);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid lobby.spawn in config.yml: " + raw);
            return null;
        }
    }

    public void setLobbySpawn(Location loc) {
        String val = String.join(",",
                loc.getWorld().getName(),
                String.valueOf(loc.getX()),
                String.valueOf(loc.getY()),
                String.valueOf(loc.getZ()),
                String.valueOf(loc.getYaw()),
                String.valueOf(loc.getPitch()));
        plugin.getConfig().set("lobby.spawn", val);
        plugin.saveConfig();
    }
}
