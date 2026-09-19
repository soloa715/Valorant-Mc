package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages Valorant map configurations.
 *
 * Each map is stored as a YAML file in plugins/ValorantMC/maps/<name>.yml
 *
 * Example map YAML:
 *   world: world
 *   attack_spawns:
 *     - 100,64,200
 *     - 102,64,200
 *   defend_spawns:
 *     - 200,64,300
 *   site_a:
 *     - 150,64,250
 *   site_b:
 *     - 180,64,260
 */
public class MapManager {

    public static class ValorantMap {
        private final String name;
        private final String displayName;
        private final List<Location> attackSpawns = new ArrayList<>();
        private final List<Location> defendSpawns = new ArrayList<>();
        private final List<Location> siteA        = new ArrayList<>();
        private final List<Location> siteB        = new ArrayList<>();
        private boolean built = false;

        // Origin in the world where the arena should be generated (for built-in maps)
        private String originWorld;
        private int originX, originY, originZ;
        private boolean autoGenerate = false;

        private double siteRadius = 5.0;

        public ValorantMap(String name, String displayName) {
            this.name        = name;
            this.displayName = displayName;
        }

        public String         getName()         { return name;        }
        public String         getDisplayName()  { return displayName; }
        public List<Location> getAttackSpawns() { return attackSpawns;}
        public List<Location> getDefendSpawns() { return defendSpawns;}
        public List<Location> getSiteA()        { return siteA;       }
        public List<Location> getSiteB()        { return siteB;       }
        public double         getSiteRadius()   { return siteRadius;  }
        public void setSiteRadius(double r)     { this.siteRadius = Math.max(1.0, Math.min(30.0, r)); }
        public boolean        isBuilt()         { return built;       }
        public boolean        isAutoGenerate()  { return autoGenerate;}
        public String         getOriginWorld()  { return originWorld; }
        public int getOriginX() { return originX; }
        public int getOriginY() { return originY; }
        public int getOriginZ() { return originZ; }

        public void setBuilt(boolean b) { this.built = b; }
        public void setOrigin(String world, int x, int y, int z) {
            this.originWorld = world; this.originX = x; this.originY = y; this.originZ = z;
            this.autoGenerate = true;
        }
    }

    private final ValorantMC plugin;
    private final Map<String, ValorantMap> maps = new LinkedHashMap<>();
    private final File mapsFolder;

    public MapManager(ValorantMC plugin) {
        this.plugin     = plugin;
        this.mapsFolder = new File(plugin.getDataFolder(), "maps");
        if (!mapsFolder.exists()) mapsFolder.mkdirs();
        if (plugin.getConfig().getBoolean("maps.auto-load", true)) {
            autoCopyFromModding();
            loadMapsFromDisk();
            unpackDefaultMaps();
            if (maps.isEmpty()) {
                plugin.getLogger().info("No real maps found — falling back to procedural arenas.");
                registerBuiltinMaps();
            }
        }
    }

    private void unpackDefaultMaps() {
        String[] mapNames = {"ascent", "abyss"};
        for (String m : mapNames) {
            File target = new File(mapsFolder, m + ".yml");
            if (!target.exists()) {
                try {
                    plugin.saveResource("maps/" + m + ".yml", false);
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * On first startup, copy any Valorant map worlds from the `modding/maps/` folder
     * into the server's worlds directory under a ValorantMC-prefixed name, and load them.
     * The originals remain untouched as control copies.
     */
    private void autoCopyFromModding() {
        File serverRoot = plugin.getServer().getWorldContainer();
        File moddingDir = new File(serverRoot, "../../modding/maps");
        if (!moddingDir.exists()) {
            moddingDir = new File(plugin.getDataFolder().getParentFile().getParentFile().getParentFile(), "modding/maps");
        }
        if (!moddingDir.exists()) {
            moddingDir = new File(serverRoot, "../modding/maps");
        }
        if (!moddingDir.exists()) {
            moddingDir = new File("modding/maps");
        }
        if (!moddingDir.exists()) return;
        plugin.getLogger().info("[MapManager] Loading community maps from: " + moddingDir.getAbsolutePath());

        File serverDir = plugin.getServer().getWorldContainer();
        File[] packs = moddingDir.listFiles(File::isDirectory);
        if (packs == null) return;

        for (File pack : packs) {
            // Each pack contains a single inner world folder with level.dat
            File innerWorld = findLevelDatFolder(pack);
            if (innerWorld == null) continue;

            String rawName = pack.getName().toLowerCase()
                    .replace("mc x val -", "")
                    .replace("mc x val:", "")
                    .replaceAll("v\\d+(\\.\\d+)?", "")
                    .replaceAll("[^a-z]+", "")
                    .trim();
            if (rawName.isEmpty()) continue;

            String worldName = "valmap_" + rawName;
            File targetDir = new File(serverDir, worldName);
            if (!targetDir.exists()) {
                plugin.getLogger().info("Copying map world " + pack.getName() + " → " + worldName);
                try {
                    copyDir(innerWorld.toPath(), targetDir.toPath());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to copy " + pack.getName() + ": " + e.getMessage());
                    continue;
                }
            }
            // Load the world
            if (plugin.getServer().getWorld(worldName) == null) {
                WorldCreator wc = new WorldCreator(worldName);
                World w = plugin.getServer().createWorld(wc);
                if (w != null) {
                    w.setAutoSave(false);
                    w.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
                    w.setTime(6000);
                    w.setStorm(false);
                    w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                    w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                    w.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
                    w.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
                    plugin.getLogger().info("Loaded world: " + worldName);
                }
            }

            // Register as a map using the world's saved spawn as the anchor point.
            // The map creator set the world spawn at a sensible floor-level location,
            // so we trust its Y and scan downward for solid ground at each offset.
            if (!maps.containsKey(rawName)) {
                ValorantMap m = new ValorantMap(rawName, capitalize(rawName));
                World w = plugin.getServer().getWorld(worldName);
                if (w != null) {
                    Location spawn = w.getSpawnLocation();
                    int sx = spawn.getBlockX();
                    int sy = spawn.getBlockY();
                    int sz = spawn.getBlockZ();
                    // 5 attacker spawns spread along X, slightly north (−Z)
                    for (int i = -4; i <= 4; i += 2) {
                        m.getAttackSpawns().add(floorAt(w, sx + i, sz - 5, sy));
                    }
                    // 5 defender spawns spread along X, slightly south (+Z)
                    for (int i = -4; i <= 4; i += 2) {
                        m.getDefendSpawns().add(floorAt(w, sx + i, sz + 5, sy));
                    }
                    m.getSiteA().add(floorAt(w, sx - 15, sz, sy));
                    m.getSiteB().add(floorAt(w, sx + 15, sz, sy));
                    plugin.getLogger().info("[MapManager] Auto-spawns anchored at "
                            + sx + "," + sy + "," + sz + " in " + worldName);
                }
                maps.put(rawName, m);
            }
        }
    }

    private File findLevelDatFolder(File start) {
        if (new File(start, "level.dat").exists()) return start;
        File[] subs = start.listFiles(File::isDirectory);
        if (subs == null) return null;
        for (File s : subs) {
            File r = findLevelDatFolder(s);
            if (r != null) return r;
        }
        return null;
    }

    private void copyDir(Path src, Path dst) throws java.io.IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(p -> {
                try {
                    Path rel = src.relativize(p);
                    Path target = dst.resolve(rel.toString());
                    if (Files.isDirectory(p)) Files.createDirectories(target);
                    else {
                        Files.createDirectories(target.getParent());
                        // Skip lock files
                        String fn = p.getFileName().toString();
                        if (fn.equals("session.lock") || fn.equals("uid.dat")) return;
                        Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) { /* ignore individual failures */ }
            });
        }
    }

    /**
     * Find a floor-level location at (x, z) by scanning downward from startY.
     * Looks for: solid block at Y, air at Y+1, air at Y+2 (room to stand).
     * Falls back to startY if nothing solid is found.
     */
    private Location floorAt(World w, int x, int z, int startY) {
        int minY = w.getMinHeight() + 1;
        int maxY = Math.min(startY + 10, w.getMaxHeight() - 2);
        // Scan a window around startY: first downward, then slightly above
        for (int y = maxY; y >= minY; y--) {
            org.bukkit.block.Block floor = w.getBlockAt(x, y, z);
            org.bukkit.block.Block head1 = w.getBlockAt(x, y + 1, z);
            org.bukkit.block.Block head2 = w.getBlockAt(x, y + 2, z);
            if (!floor.getType().isAir()
                    && !floor.getType().toString().contains("LEAVES")
                    && head1.getType().isAir()
                    && head2.getType().isAir()) {
                return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
        }
        // Nothing found — return spawn Y directly (better than rooftop)
        return new Location(w, x + 0.5, startY, z + 0.5);
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void loadMapsFromDisk() {
        File[] files = mapsFolder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            try {
                loadMapFile(f);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load map " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    private void loadMapFile(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String name        = cfg.getString("name", file.getName().replace(".yml", ""));
        String displayName = cfg.getString("display-name", name);
        
        // Always prefer dedicated valmap_<name> world if loaded on server
        String targetWorldName = "valmap_" + name.toLowerCase();
        World world = plugin.getServer().getWorld(targetWorldName);
        if (world == null) {
            String cfgWorld = cfg.getString("world", "world");
            world = plugin.getServer().getWorld(cfgWorld);
        }
        if (world == null) {
            world = plugin.getServer().getWorld("world");
        }

        if (world == null) {
            plugin.getLogger().warning("Map " + name + " references unknown world. Skipping.");
            return;
        }

        ValorantMap map = new ValorantMap(name, displayName);
        map.setSiteRadius(cfg.getDouble("site-radius", cfg.getDouble("site_radius", 5.0)));

        // Support both underscore keys (canonical) and hyphen keys (legacy/user YAMLs)
        loadLocations(cfg, "attack_spawns", world, map.getAttackSpawns());
        if (map.getAttackSpawns().isEmpty()) loadLocations(cfg, "attack-spawns", world, map.getAttackSpawns());
        loadLocations(cfg, "defend_spawns", world, map.getDefendSpawns());
        if (map.getDefendSpawns().isEmpty()) loadLocations(cfg, "defend-spawns", world, map.getDefendSpawns());
        loadLocations(cfg, "site_a", world, map.getSiteA());
        if (map.getSiteA().isEmpty()) loadLocations(cfg, "site-a", world, map.getSiteA());
        loadLocations(cfg, "site_b", world, map.getSiteB());
        if (map.getSiteB().isEmpty()) loadLocations(cfg, "site-b", world, map.getSiteB());

        maps.put(name.toLowerCase(), map);
        plugin.getLogger().info("Loaded map: " + displayName + " (site-radius: " + map.getSiteRadius() + ")");
    }

    private void loadLocations(YamlConfiguration cfg, String key, World world, List<Location> list) {
        List<String> raw = cfg.getStringList(key);
        for (String entry : raw) {
            String[] parts = entry.split(",");
            if (parts.length < 3) continue;
            try {
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());
                float  yaw   = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 0;
                float  pitch = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0;
                list.add(new Location(world, x, y, z, yaw, pitch));
            } catch (NumberFormatException ignored) {}
        }
    }

    /** Register built-in auto-generated maps. (Disabled so only hand-configured YAML maps are registered) */
    private void registerBuiltinMaps() {
        // Disabled mock generation
    }

    /**
     * Build the arena for the given map (if it's an auto-generated map and not yet built),
     * then populate its spawn + site locations.
     */
    public void ensureBuilt(ValorantMap map) {
        if (map.isBuilt() || !map.isAutoGenerate()) return;
        World world = plugin.getServer().getWorld(map.getOriginWorld());
        if (world == null) {
            plugin.getLogger().warning("Cannot build arena: world '" + map.getOriginWorld() + "' not loaded.");
            return;
        }
        plugin.getLogger().info("Generating arena for map " + map.getDisplayName()
                + " at " + map.getOriginX() + "," + map.getOriginY() + "," + map.getOriginZ());
        ArenaBuilder.Built b = ArenaBuilder.build(world, map.getOriginX(), map.getOriginY(), map.getOriginZ());
        map.getAttackSpawns().clear();
        map.getDefendSpawns().clear();
        map.getSiteA().clear();
        map.getSiteB().clear();
        map.getAttackSpawns().addAll(b.attackSpawns);
        map.getDefendSpawns().addAll(b.defendSpawns);
        map.getSiteA().addAll(b.siteA);
        map.getSiteB().addAll(b.siteB);
        map.setBuilt(true);
    }

    /** Save a YAML template for a new map */
    public void saveMapTemplate(String name, String worldName) {
        File f = new File(mapsFolder, name + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("name", name);
        cfg.set("display-name", name);
        cfg.set("world", worldName);
        cfg.set("attack_spawns", List.of("0,64,0,0,0"));
        cfg.set("defend_spawns", List.of("10,64,0,180,0"));
        cfg.set("site_a",        List.of("5,64,5"));
        cfg.set("site_b",        List.of("-5,64,-5"));
        try {
            cfg.save(f);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save map template: " + e.getMessage());
        }
    }

    /**
     * Called from MapSetupCommand to persist a freshly configured map.
     * Writes a YAML file to the maps directory and marks it ready.
     */
    public void saveSessionToFile(String name, String worldName,
                                  List<String> attackSpawns, List<String> defendSpawns,
                                  List<String> siteA, List<String> siteB) {
        saveSessionToFile(name, worldName, attackSpawns, defendSpawns, siteA, siteB, 5.0);
    }

    public void saveSessionToFile(String name, String worldName,
                                  List<String> attackSpawns, List<String> defendSpawns,
                                  List<String> siteA, List<String> siteB, double siteRadius) {
        String valWorld = "valmap_" + name.toLowerCase();
        if (plugin.getServer().getWorld(valWorld) != null) {
            worldName = valWorld;
        }

        org.bukkit.configuration.file.YamlConfiguration cfg =
                new org.bukkit.configuration.file.YamlConfiguration();
        cfg.set("name", name);
        cfg.set("display-name", capitalise(name));
        cfg.set("world", worldName);
        cfg.set("auto-generate", false);
        cfg.set("site-radius", siteRadius);
        cfg.set("attack_spawns", attackSpawns);
        cfg.set("defend_spawns", defendSpawns);
        cfg.set("site_a", siteA);
        cfg.set("site_b", siteB);
        java.io.File out = new java.io.File(mapsFolder, name + ".yml");
        try {
            cfg.save(out);
            plugin.getLogger().info("[MapManager] Saved map: " + name);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("[MapManager] Failed to save map " + name + ": " + e.getMessage());
        }
    }

    /** Public entry point so MapSetupCommand can hot-reload maps after saving. */
    public void reloadMaps() {
        // Remove only maps that were loaded from YAML disk files (non-auto-generated).
        // Auto-generated / procedural maps stay in memory.
        maps.entrySet().removeIf(e -> !e.getValue().isAutoGenerate());
        loadMapsFromDisk();
    }

    public boolean resetMap(String mapName) {
        String key = mapName.toLowerCase();
        File ymlFile = new File(mapsFolder, key + ".yml");
        boolean deleted = false;
        if (ymlFile.exists()) {
            deleted = ymlFile.delete();
        }
        maps.remove(key);
        autoCopyFromModding();
        loadMapsFromDisk();
        unpackDefaultMaps();
        return deleted || maps.containsKey(key);
    }

    private String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public ValorantMap getMap(String name)      { return maps.get(name.toLowerCase()); }
    public Collection<ValorantMap> getAllMaps()  { return maps.values();               }
    public Set<String> getMapNames()            { return maps.keySet();               }
    public int getMapCount()                    { return maps.size();                 }
    public boolean hasMap(String name)          { return maps.containsKey(name.toLowerCase()); }
}
