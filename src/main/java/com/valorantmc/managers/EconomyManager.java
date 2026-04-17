package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {

    private final ValorantMC plugin;
    private final Map<UUID, Integer> credits = new HashMap<>();
    private final Map<UUID, Integer> vpBalance = new HashMap<>();
    private final File               dataFile;

    private static final int MAX_CREDITS = 9000;

    public EconomyManager(ValorantMC plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "economy.yml");
        loadAll();
    }

    public void initPlayer(UUID uuid) {
        credits.putIfAbsent(uuid, plugin.getConfig().getInt("game.starting-credits", 800));
    }

    public int getCredits(UUID uuid) {
        return credits.getOrDefault(uuid, 0);
    }

    public void setCredits(UUID uuid, int amount) {
        credits.put(uuid, Math.min(MAX_CREDITS, Math.max(0, amount)));
    }

    public boolean spend(UUID uuid, int amount) {
        int current = getCredits(uuid);
        if (current < amount) return false;
        credits.put(uuid, current - amount);
        return true;
    }

    public void addCredits(UUID uuid, int amount) {
        int current = getCredits(uuid);
        credits.put(uuid, Math.min(MAX_CREDITS, current + amount));
    }

    public void capCredits(UUID uuid) {
        credits.put(uuid, Math.min(MAX_CREDITS, getCredits(uuid)));
    }

    public boolean canAfford(UUID uuid, int cost) {
        return getCredits(uuid) >= cost;
    }

    public void clearPlayer(UUID uuid) {
        credits.remove(uuid);
    }

    // Convenience for Player
    public int  getCredits(Player p)              { return getCredits(p.getUniqueId()); }
    public boolean spend(Player p, int amount)    { return spend(p.getUniqueId(), amount); }
    public void addCredits(Player p, int amount)  { addCredits(p.getUniqueId(), amount);  }
    public boolean canAfford(Player p, int cost)  { return canAfford(p.getUniqueId(), cost); }

    // ── VP (Valorant Points) ──────────────────────────────────────────────────

    public int  getVP(UUID uuid)             { return vpBalance.getOrDefault(uuid, 0); }
    public int  getVP(Player p)              { return getVP(p.getUniqueId()); }
    public void addVP(UUID uuid, int amount) { vpBalance.merge(uuid, amount, Integer::sum); }
    public void addVP(Player p, int amount)  { addVP(p.getUniqueId(), amount); }

    public boolean canAffordVP(UUID uuid, int cost) { return getVP(uuid) >= cost; }
    public boolean canAffordVP(Player p, int cost)  { return canAffordVP(p.getUniqueId(), cost); }

    public boolean spendVP(UUID uuid, int cost) {
        if (!canAffordVP(uuid, cost)) return false;
        vpBalance.put(uuid, getVP(uuid) - cost);
        return true;
    }
    public boolean spendVP(Player p, int cost) { return spendVP(p.getUniqueId(), cost); }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveAll() {
        org.bukkit.configuration.file.YamlConfiguration cfg =
                new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<UUID, Integer> e : vpBalance.entrySet()) {
            cfg.set("vp." + e.getKey().toString(), e.getValue());
        }
        try { cfg.save(dataFile); }
        catch (java.io.IOException ex) {
            plugin.getLogger().warning("Failed to save economy.yml: " + ex.getMessage());
        }
    }

    public void loadAll() {
        if (!dataFile.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
        org.bukkit.configuration.ConfigurationSection vpSec = cfg.getConfigurationSection("vp");
        if (vpSec != null) {
            for (String key : vpSec.getKeys(false)) {
                try {
                    vpBalance.put(UUID.fromString(key), vpSec.getInt(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void savePlayer(UUID uuid) {
        // Quick single-player save — just re-saves everything (file is small)
        saveAll();
    }
}
