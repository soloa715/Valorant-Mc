package com.valorantmc.mod.server;

import com.google.gson.*;
import net.minecraft.world.phys.Vec3;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Tracks available Valorant maps (world folders with spawn configs).
 * Maps live in run/server/valorantmc-maps/<MapName>/  with a spawns.json inside.
 * Falls back to ArenaManager if no maps are available.
 */
public class MapManager {

    private static final Path MAPS_DIR = Paths.get("valorantmc-maps");
    private static MapManager INSTANCE;

    // Map name → spawn config (loaded lazily from disk)
    private final Map<String, MapSpawnConfig> configs = new LinkedHashMap<>();
    private String activeMap = "Arena";

    // Vote tracking
    private final Map<UUID, String> votes = new HashMap<>();

    private MapManager() {
        reload();
    }

    public static MapManager getInstance() {
        if (INSTANCE == null) INSTANCE = new MapManager();
        return INSTANCE;
    }

    // ── Reload ────────────────────────────────────────────────────────────────

    public void reload() {
        configs.clear();

        // Always include the built-in fallback arena
        MapSpawnConfig arenaConfig = new MapSpawnConfig("Arena");
        arenaConfig.attackerSpawns.addAll(ArenaManager.getAttackerSpawns());
        arenaConfig.defenderSpawns.addAll(ArenaManager.getDefenderSpawns());
        configs.put("Arena", arenaConfig);

        // Scan for map folders with spawns.json
        if (Files.isDirectory(MAPS_DIR)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(MAPS_DIR)) {
                for (Path mapDir : ds) {
                    if (!Files.isDirectory(mapDir)) continue;
                    Path spawnsFile = mapDir.resolve("spawns.json");
                    if (Files.exists(spawnsFile)) {
                        String name = mapDir.getFileName().toString();
                        MapSpawnConfig cfg = loadSpawns(name, spawnsFile);
                        if (cfg != null) configs.put(name, cfg);
                    }
                }
            } catch (IOException e) {
                System.err.println("[ValorantMC] Failed to scan maps dir: " + e.getMessage());
            }
        }

        // Also load legacy SpawnConfigManager spawns as a named map
        SpawnConfigManager sc = SpawnConfigManager.getInstance();
        if (sc.hasSpawns()) {
            MapSpawnConfig legacyConfig = new MapSpawnConfig("Custom");
            legacyConfig.attackerSpawns.addAll(sc.getAttackerSpawns());
            legacyConfig.defenderSpawns.addAll(sc.getDefenderSpawns());
            configs.put("Custom", legacyConfig);
        }
    }

    // ── Vote system ───────────────────────────────────────────────────────────

    public void vote(UUID player, String mapName) {
        if (configs.containsKey(mapName)) votes.put(player, mapName);
    }

    public String resolveVote() {
        if (votes.isEmpty()) return activeMap;
        Map<String, Long> tally = new HashMap<>();
        votes.values().forEach(m -> tally.merge(m, 1L, Long::sum));
        activeMap = tally.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(activeMap);
        votes.clear();
        return activeMap;
    }

    public void clearVotes() { votes.clear(); }

    // ── Queries ───────────────────────────────────────────────────────────────

    public MapSpawnConfig getActiveConfig() {
        return configs.getOrDefault(activeMap, configs.values().iterator().next());
    }

    public String getActiveMap() { return activeMap; }

    public void setActiveMap(String name) {
        if (configs.containsKey(name)) activeMap = name;
    }

    public List<String> getMapNames() { return new ArrayList<>(configs.keySet()); }

    // ── Spawn saving ──────────────────────────────────────────────────────────

    public void saveSpawnsForMap(String mapName, List<Vec3> attackers, List<Vec3> defenders) {
        Path dir  = MAPS_DIR.resolve(mapName);
        Path file = dir.resolve("spawns.json");
        try {
            Files.createDirectories(dir);
            JsonObject root = new JsonObject();
            root.add("attackerSpawns", toArray(attackers));
            root.add("defenderSpawns", toArray(defenders));
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
            reload();
        } catch (IOException e) {
            System.err.println("[ValorantMC] Failed to save spawns for " + mapName + ": " + e.getMessage());
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private static MapSpawnConfig loadSpawns(String name, Path file) {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            MapSpawnConfig cfg = new MapSpawnConfig(name);
            readList(root, "attackerSpawns", cfg.attackerSpawns);
            readList(root, "defenderSpawns", cfg.defenderSpawns);
            if (cfg.attackerSpawns.isEmpty() || cfg.defenderSpawns.isEmpty()) return null;
            return cfg;
        } catch (Exception e) {
            System.err.println("[ValorantMC] Failed to read spawns for " + name + ": " + e.getMessage());
            return null;
        }
    }

    private static void readList(JsonObject root, String key, List<Vec3> out) {
        if (!root.has(key)) return;
        for (JsonElement el : root.getAsJsonArray(key)) {
            JsonObject o = el.getAsJsonObject();
            out.add(new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble()));
        }
    }

    private static JsonArray toArray(List<Vec3> list) {
        JsonArray arr = new JsonArray();
        for (Vec3 v : list) {
            JsonObject o = new JsonObject();
            o.addProperty("x", Math.round(v.x * 10.0) / 10.0);
            o.addProperty("y", Math.round(v.y * 10.0) / 10.0);
            o.addProperty("z", Math.round(v.z * 10.0) / 10.0);
            arr.add(o);
        }
        return arr;
    }

    // ── Inner record ─────────────────────────────────────────────────────────

    public static class MapSpawnConfig {
        public final String name;
        public final List<Vec3> attackerSpawns = new ArrayList<>();
        public final List<Vec3> defenderSpawns = new ArrayList<>();
        public MapSpawnConfig(String name) { this.name = name; }
    }
}
