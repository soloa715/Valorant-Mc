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

        // Pre-configure all official Valorant maps
        registerDefaultMap("Ascent",
            Arrays.asList(new Vec3(-10, 100, -20), new Vec3(-12, 100, -22), new Vec3(-8, 100, -20)),
            Arrays.asList(new Vec3(10, 100, 20), new Vec3(12, 100, 22), new Vec3(8, 100, 20)));
        registerDefaultMap("Bind",
            Arrays.asList(new Vec3(-20, 100, -10), new Vec3(-22, 100, -12), new Vec3(-20, 100, -8)),
            Arrays.asList(new Vec3(20, 100, 10), new Vec3(22, 100, 12), new Vec3(20, 100, 8)));
        registerDefaultMap("Haven",
            Arrays.asList(new Vec3(0, 100, -30), new Vec3(-2, 100, -32), new Vec3(2, 100, -30)),
            Arrays.asList(new Vec3(0, 100, 30), new Vec3(2, 100, 32), new Vec3(-2, 100, 30)));
        registerDefaultMap("Split",
            Arrays.asList(new Vec3(-15, 100, -15), new Vec3(-17, 100, -15), new Vec3(-15, 100, -17)),
            Arrays.asList(new Vec3(15, 100, 15), new Vec3(17, 100, 15), new Vec3(15, 100, 17)));
        registerDefaultMap("Icebox",
            Arrays.asList(new Vec3(-25, 100, 0), new Vec3(-25, 100, -2), new Vec3(-27, 100, 0)),
            Arrays.asList(new Vec3(25, 100, 0), new Vec3(25, 100, 2), new Vec3(27, 100, 0)));
        registerDefaultMap("Breeze",
            Arrays.asList(new Vec3(-30, 100, -30), new Vec3(-32, 100, -30), new Vec3(-30, 100, -32)),
            Arrays.asList(new Vec3(30, 100, 30), new Vec3(32, 100, 30), new Vec3(30, 100, 32)));
        registerDefaultMap("Fracture",
            Arrays.asList(new Vec3(-18, 100, -18), new Vec3(-20, 100, -18), new Vec3(-18, 100, -20)),
            Arrays.asList(new Vec3(18, 100, 18), new Vec3(20, 100, 18), new Vec3(18, 100, 20)));
        registerDefaultMap("Lotus",
            Arrays.asList(new Vec3(-22, 100, -5), new Vec3(-24, 100, -5), new Vec3(-22, 100, -7)),
            Arrays.asList(new Vec3(22, 100, 5), new Vec3(24, 100, 5), new Vec3(22, 100, 7)));
        registerDefaultMap("Pearl",
            Arrays.asList(new Vec3(-14, 100, -25), new Vec3(-16, 100, -25), new Vec3(-14, 100, -27)),
            Arrays.asList(new Vec3(14, 100, 25), new Vec3(16, 100, 25), new Vec3(14, 100, 27)));
        registerDefaultMap("Sunset",
            Arrays.asList(new Vec3(-12, 100, -18), new Vec3(-14, 100, -18), new Vec3(-12, 100, -20)),
            Arrays.asList(new Vec3(12, 100, 18), new Vec3(14, 100, 18), new Vec3(12, 100, 20)));
        registerDefaultMap("Abyss",
            Arrays.asList(new Vec3(-28, 100, -14), new Vec3(-30, 100, -14), new Vec3(-28, 100, -16)),
            Arrays.asList(new Vec3(28, 100, 14), new Vec3(30, 100, 14), new Vec3(28, 100, 16)));

        // Scan for custom map folders with spawns.json
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
            JsonObject obj = new JsonObject();
            obj.addProperty("x", v.x);
            obj.addProperty("y", v.y);
            obj.addProperty("z", v.z);
            arr.add(obj);
        }
        return arr;
    }

    private void registerDefaultMap(String name, List<Vec3> atk, List<Vec3> def) {
        MapSpawnConfig cfg = new MapSpawnConfig(name);
        cfg.attackerSpawns.addAll(atk);
        cfg.defenderSpawns.addAll(def);
        configs.put(name, cfg);
    }

    // ── Inner record ─────────────────────────────────────────────────────────

    public static class MapSpawnConfig {
        public final String name;
        public final List<Vec3> attackerSpawns = new ArrayList<>();
        public final List<Vec3> defenderSpawns = new ArrayList<>();
        public MapSpawnConfig(String name) { this.name = name; }
    }
}
