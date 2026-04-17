package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsManager {

    public static class PlayerStats {
        public int kills;
        public int deaths;
        public int headshots;
        public int assists;
        public int roundsPlayed;
        public int roundsWon;

        public double getKDR()  { return deaths == 0 ? kills : (double) kills / deaths; }
        public double getHSPct() { return kills == 0 ? 0 : (double) headshots / kills * 100; }
    }

    private final ValorantMC plugin;
    private final Map<UUID, PlayerStats> statsMap = new HashMap<>();
    private final File statsFile;

    // Per-match stats (cleared at match start, summarised at match end)
    private final Map<UUID, PlayerStats> matchStats = new HashMap<>();

    public StatsManager(ValorantMC plugin) {
        this.plugin    = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        loadAll();
    }

    public PlayerStats getStats(UUID uuid) {
        return statsMap.computeIfAbsent(uuid, k -> new PlayerStats());
    }

    public void recordKill(UUID uuid, boolean headshot) {
        PlayerStats s = getStats(uuid);
        s.kills++;
        if (headshot) s.headshots++;
    }

    public void recordDeath(UUID uuid) {
        getStats(uuid).deaths++;
    }

    public void recordAssist(UUID uuid) {
        getStats(uuid).assists++;
    }

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerStats> entry : statsMap.entrySet()) {
            String path = entry.getKey().toString();
            PlayerStats s = entry.getValue();
            cfg.set(path + ".kills",         s.kills);
            cfg.set(path + ".deaths",        s.deaths);
            cfg.set(path + ".headshots",     s.headshots);
            cfg.set(path + ".assists",       s.assists);
            cfg.set(path + ".roundsPlayed",  s.roundsPlayed);
            cfg.set(path + ".roundsWon",     s.roundsWon);
        }
        try { cfg.save(statsFile); }
        catch (Exception e) { plugin.getLogger().warning("Failed to save stats: " + e.getMessage()); }
    }

    // ── Match stats ───────────────────────────────────────────────────────────

    public void startMatch(Collection<org.bukkit.entity.Player> players) {
        matchStats.clear();
        for (org.bukkit.entity.Player p : players) {
            matchStats.put(p.getUniqueId(), new PlayerStats());
        }
    }

    public void recordMatchKill(UUID uuid, boolean headshot) {
        PlayerStats ms = matchStats.computeIfAbsent(uuid, k -> new PlayerStats());
        ms.kills++;
        if (headshot) ms.headshots++;
        // Lifetime
        recordKill(uuid, headshot);
    }

    public void recordMatchDeath(UUID uuid) {
        matchStats.computeIfAbsent(uuid, k -> new PlayerStats()).deaths++;
        recordDeath(uuid);
    }

    public void recordMatchAssist(UUID uuid) {
        matchStats.computeIfAbsent(uuid, k -> new PlayerStats()).assists++;
        recordAssist(uuid);
    }

    public PlayerStats getMatchStats(UUID uuid) {
        return matchStats.getOrDefault(uuid, new PlayerStats());
    }

    public Map<UUID, PlayerStats> getAllMatchStats() { return matchStats; }

    private void loadAll() {
        if (!statsFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(statsFile);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                PlayerStats s = new PlayerStats();
                s.kills        = cfg.getInt(key + ".kills");
                s.deaths       = cfg.getInt(key + ".deaths");
                s.headshots    = cfg.getInt(key + ".headshots");
                s.assists      = cfg.getInt(key + ".assists");
                s.roundsPlayed = cfg.getInt(key + ".roundsPlayed");
                s.roundsWon    = cfg.getInt(key + ".roundsWon");
                statsMap.put(uuid, s);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
