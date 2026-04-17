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
            // Only fall back to procedurally-generated arenas if the user has no real maps.
            // (Procedural arenas appear as "a square in the sky" — not what we want.)
            if (maps.isEmpty()) {
                plugin.getLogger().info("No real maps found — falling back to procedural arenas.");
                registerBuiltinMaps();
            }
        }
    }

    /**
     * On first startup, copy any Valorant map worlds from the `modding/maps/` folder
     * into the server's worlds directory under a ValorantMC-prefixed name, and load them.
     * The originals remain untouched as control copies.
     */
    private void autoCopyFromModding() {
        File moddingDir = new File(plugin.getDataFolder().getParentFile().getParentFile(), "modding/maps");
        if (!moddingDir.exists()) return;

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

            // Register as a map, pointing at a safe ground-level location near world spawn
            if (!maps.containsKey(rawName)) {
                ValorantMap m = new ValorantMap(rawName, capitalize(rawName));
                World w = plugin.getServer().getWorld(worldName);
                if (w != null) {
                    Location spawn = findSafeSpawn(w);
                    // Default spawn points: 5 attackers north, 5 defenders south of spawn
                    for (int i = -4; i <= 4; i += 2) {
                        m.getAttackSpawns().add(safeAt(w, spawn.getBlockX() + i, spawn.getBlockZ() - 10));
                        m.getDefendSpawns().add(safeAt(w, spawn.getBlockX() + i, spawn.getBlockZ() + 10));
                    }
                    m.getSiteA().add(safeAt(w, spawn.getBlockX() - 20, spawn.getBlockZ()));
                    m.getSiteB().add(safeAt(w, spawn.getBlockX() + 20, spawn.getBlockZ()));
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

    /** Find a sensible standing location near the world's saved spawn. */
    private Location findSafeSpawn(World w) {
        Location s = w.getSpawnLocation();
        return safeAt(w, s.getBlockX(), s.getBlockZ());
    }

    /** Top non-air block at (x,z) +1, clamped to world height range. */
    private Location safeAt(World w, int x, int z) {
        int top = w.getHighestBlockYAt(x, z);
        if (top < w.getMinHeight() + 1) top = 64;
        return new Location(w, x + 0.5, top + 1, z + 0.5);
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
        String worldName   = cfg.getString("world", "world");
        World  world       = plugin.getServer().getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("Map " + name + " references unknown world '" + worldName + "'. Skipping.");
            return;
        }

        ValorantMap map = new ValorantMap(name, displayName);

        loadLocations(cfg, "attack_spawns", world, map.getAttackSpawns());
        loadLocations(cfg, "defend_spawns", world, map.getDefendSpawns());
        loadLocations(cfg, "site_a",        world, map.getSiteA());
        loadLocations(cfg, "site_b",        world, map.getSiteB());

        maps.put(name.toLowerCase(), map);
        plugin.getLogger().info("Loaded map: " + displayName);
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

    /** Register built-in auto-generated maps. Each map gets a unique world origin. */
    private void registerBuiltinMaps() {
        String worldName = plugin.getConfig().getString("maps.default-world", "world");
        int y = plugin.getConfig().getInt("maps.arena-y", 100);
        int spacing = 500;
        String[] names = {
                "ascent", "bind", "haven", "split", "pearl",
                "fracture", "breeze", "lotus", "sunset", "abyss"
        };
        String[] displays = {
                "Ascent", "Bind", "Haven", "Split", "Pearl",
                "Fracture", "Breeze", "Lotus", "Sunset", "Abyss"
        };
        for (int i = 0; i < names.length; i++) {
            if (maps.containsKey(names[i])) continue;
            ValorantMap m = new ValorantMap(names[i], displays[i]);
            m.setOrigin(worldName, i * spacing, y, 0);
            maps.put(names[i], m);
        }
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
        org.bukkit.configuration.file.YamlConfiguration cfg =
                new org.bukkit.configuration.file.YamlConfiguration();
        cfg.set("name", name);
        cfg.set("display-name", capitalise(name));
        cfg.set("world", worldName);
        cfg.set("auto-generate", false);
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
        loadMapsFromDisk();
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
