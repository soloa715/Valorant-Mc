package com.valorantmc.commands;

import com.valorantmc.ValorantMC;
import com.valorantmc.managers.MapManager;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * /vmapsetup — In-game map configuration wizard for server hosters.
 *
 * Subcommands:
 *   create <name>            — Start a new map setup session
 *   addspawn <atk|def>       — Record current position as a spawn
 *   addsite <a|b>            — Record current block as a bomb-site location
 *   setworld <worldName>     — Set the world for this map
 *   save                     — Write map to disk and hot-reload
 *   list                     — List all configured maps
 *   validate <name>          — Check if a map has enough spawns/sites
 *   tp <name> <atk|def> <i> — Teleport to a spawn for visual verification
 *   wand                     — Get the Map Wand (right-click to mark sites)
 */
public class MapSetupCommand implements CommandExecutor {

    private final ValorantMC plugin;

    // Active setup sessions: player UUID → session
    private final Map<UUID, SetupSession> sessions = new HashMap<>();

    private static final String PREFIX = "&b[MapSetup] &f";

    public MapSetupCommand(ValorantMC plugin) {
        this.plugin = plugin;
        startParticleTask();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!p.hasPermission("valorantmc.admin")) {
            p.sendMessage(ValorantMC.colorize("&cYou need the &evalorantmc.admin&c permission."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create"   -> handleCreate(p, args);
            case "edit", "load" -> handleEdit(p, args);
            case "radius"   -> handleRadius(p, args);
            case "pos1"     -> handlePos1(p);
            case "pos2"     -> handlePos2(p);
            case "setsite", "setregion", "region" -> handleSetSite(p, args);
            case "addspawn" -> handleAddSpawn(p, args);
            case "addsite"  -> handleAddSite(p, args);
            case "setworld" -> handleSetWorld(p, args);
            case "save"     -> handleSave(p);
            case "cancel"   -> handleCancel(p);
            case "reset"    -> handleReset(p, args);
            case "list"     -> handleList(p);
            case "validate" -> handleValidate(p, args);
            case "tp"       -> handleTp(p, args);
            case "wand"     -> giveWand(p);
            case "gui", "panel" -> handleGui(p);
            default         -> sendHelp(p);
        }
        return true;
    }

    private void handleCancel(Player p) {
        if (sessions.remove(p.getUniqueId()) != null) {
            p.sendMessage(color(PREFIX + "&eCanceled current map setup session."));
        } else {
            p.sendMessage(color(PREFIX + "&7No active map session to cancel."));
        }
    }

    private void handleReset(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup reset <mapName>"));
            return;
        }
        String mapName = args[1].toLowerCase();
        boolean ok = plugin.getMapManager().resetMap(mapName);
        if (ok) {
            p.sendMessage(color(PREFIX + "&aReset map '&e" + mapName + "&a' back to default configurations!"));
        } else {
            p.sendMessage(color(PREFIX + "&cMap not found: " + mapName));
        }
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    private void handleCreate(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup create <name>")); return; }
        String name = args[1].toLowerCase();
        SetupSession session = new SetupSession(name, p.getWorld().getName());
        sessions.put(p.getUniqueId(), session);
        p.sendMessage(color(PREFIX + "&aCreated map session: &e" + name));
        p.sendMessage(color(PREFIX + "&7Use &b/vmapsetup addspawn atk/def &7to record spawns."));
        p.sendMessage(color(PREFIX + "&7Use &b/vmapsetup addsite a/b &7to mark bomb sites."));
        giveWand(p);
    }

    private void handleEdit(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup edit <mapName>"));
            return;
        }
        String mapName = args[1].toLowerCase();
        MapManager.ValorantMap map = plugin.getMapManager().getMap(mapName);
        if (map == null) {
            p.sendMessage(color(PREFIX + "&cMap not found: " + mapName));
            return;
        }
        World mapWorld = plugin.getServer().getWorld("valmap_" + mapName);
        if (mapWorld == null && map.getAttackSpawns() != null && !map.getAttackSpawns().isEmpty()) {
            mapWorld = map.getAttackSpawns().get(0).getWorld();
        }
        if (mapWorld == null) {
            mapWorld = p.getWorld();
        }

        String wName = mapWorld.getName();
        SetupSession s = new SetupSession(map.getName(), wName);
        s.siteRadius = map.getSiteRadius();

        for (Location loc : map.getAttackSpawns()) {
            s.attackSpawns.add(loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "," + Math.round(loc.getYaw()) + "," + Math.round(loc.getPitch()));
        }
        for (Location loc : map.getDefendSpawns()) {
            s.defendSpawns.add(loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "," + Math.round(loc.getYaw()) + "," + Math.round(loc.getPitch()));
        }
        for (Location loc : map.getSiteA()) {
            s.siteA.add(loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
        }
        for (Location loc : map.getSiteB()) {
            s.siteB.add(loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
        }

        sessions.put(p.getUniqueId(), s);
        giveWand(p);

        // Auto teleport player directly into the map world
        if (map.getAttackSpawns() != null && !map.getAttackSpawns().isEmpty()) {
            Location tpLoc = map.getAttackSpawns().get(0).clone();
            tpLoc.setWorld(mapWorld);
            p.teleport(tpLoc);
        } else {
            p.teleport(mapWorld.getSpawnLocation());
        }

        p.sendMessage(color(PREFIX + "&aLoaded map '&e" + map.getDisplayName() + "&a' into setup mode!"));
        p.sendMessage(color(PREFIX + "&7ATK: &f" + s.attackSpawns.size() + " &8| &7DEF: &f" + s.defendSpawns.size() + " &8| &7Site A: &f" + s.siteA.size() + " &8| &7Site B: &f" + s.siteB.size() + " &8| &7Radius: &b" + s.siteRadius + "m"));
        p.sendMessage(color(PREFIX + "&7Visual 3D site zones &aACTIVATED&7! Use &b/vmapsetup gui&7 or hold Map Wand."));
    }

    private void handleRadius(Player p, String[] args) {
        SetupSession s = getSession(p); if (s == null) return;
        if (args.length < 2) {
            p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup radius <1-30> (current: " + s.siteRadius + "m)"));
            return;
        }
        try {
            double r = Double.parseDouble(args[1]);
            s.siteRadius = Math.max(1.0, Math.min(30.0, r));
            p.sendMessage(color(PREFIX + "&aSite zone radius updated to: &b" + s.siteRadius + " meters&a!"));
        } catch (NumberFormatException e) {
            p.sendMessage(color(PREFIX + "&cInvalid radius number."));
        }
    }

    private void handleGui(Player p) {
        com.valorantmc.game.ValorantGame game = plugin.getGameManager().getGame(p);
        p.openInventory(com.valorantmc.gui.AdminGUI.buildMapSetup(p, game));
    }

    private void handleSave(Player p) {
        SetupSession s = getSession(p); if (s == null) return;
        // Validation
        List<String> issues = new ArrayList<>();
        if (s.attackSpawns.size() < 2) issues.add("Need at least 2 attacker spawns (have " + s.attackSpawns.size() + ")");
        if (s.defendSpawns.size() < 2) issues.add("Need at least 2 defender spawns (have " + s.defendSpawns.size() + ")");
        if (s.siteA.isEmpty()) issues.add("Need at least 1 Site A location");
        if (s.siteB.isEmpty()) issues.add("Need at least 1 Site B location");
        if (!issues.isEmpty()) {
            p.sendMessage(color(PREFIX + "&cCannot save — fix these issues first:"));
            issues.forEach(i -> p.sendMessage(color("  &c• " + i)));
            return;
        }
        plugin.getMapManager().saveSessionToFile(s.mapName, s.worldName,
                s.attackSpawns, s.defendSpawns, s.siteA, s.siteB, s.siteRadius);
        plugin.getMapManager().reloadMaps();
        sessions.remove(p.getUniqueId());
        p.sendMessage(color(PREFIX + "&a&lMap '" + s.mapName + "' saved and loaded successfully!"));
        p.sendMessage(color(PREFIX + "&7Start a game with: &b/valorant start <id> " + s.mapName));
    }

    private void handleAddSpawn(Player p, String[] args) {
        SetupSession s = getSession(p); if (s == null) return;
        if (args.length < 2) { p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup addspawn <atk|def>")); return; }
        org.bukkit.block.Block target = p.getTargetBlockExact(50);
        boolean raycast = (target != null);
        Location loc = raycast ? target.getLocation().add(0.5, 1.0, 0.5) : p.getLocation();
        loc.setYaw(p.getLocation().getYaw());
        loc.setPitch(p.getLocation().getPitch());
        String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                + "," + Math.round(loc.getYaw()) + "," + Math.round(loc.getPitch());
        if (args[1].equalsIgnoreCase("atk") || args[1].equalsIgnoreCase("attacker")) {
            s.attackSpawns.add(entry);
            p.sendMessage(color(PREFIX + "&aAdded attacker spawn #" + s.attackSpawns.size() + ": &7" + entry
                    + (raycast ? " &8[targeted block]" : " &8[player position]")));
        } else if (args[1].equalsIgnoreCase("def") || args[1].equalsIgnoreCase("defender")) {
            s.defendSpawns.add(entry);
            p.sendMessage(color(PREFIX + "&aAdded defender spawn #" + s.defendSpawns.size() + ": &7" + entry
                    + (raycast ? " &8[targeted block]" : " &8[player position]")));
        } else {
            p.sendMessage(color(PREFIX + "&cUse 'atk' or 'def'."));
        }
    }

    private void handleAddSite(Player p, String[] args) {
        SetupSession s = getSession(p); if (s == null) return;
        if (args.length < 2) { p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup addsite <a|b>")); return; }
        org.bukkit.block.Block target = p.getTargetBlockExact(50);
        boolean raycast = (target != null);
        Location loc = raycast ? target.getLocation().add(0.5, 1.0, 0.5) : p.getLocation();
        String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        if (args[1].equalsIgnoreCase("a")) {
            s.siteA.add(entry);
            p.sendMessage(color(PREFIX + "&aAdded Site A location #" + s.siteA.size() + ": &7" + entry
                    + (raycast ? " &8[targeted block]" : " &8[player position]")));
        } else if (args[1].equalsIgnoreCase("b")) {
            s.siteB.add(entry);
            p.sendMessage(color(PREFIX + "&aAdded Site B location #" + s.siteB.size() + ": &7" + entry
                    + (raycast ? " &8[targeted block]" : " &8[player position]")));
        } else {
            p.sendMessage(color(PREFIX + "&cUse 'a' or 'b'."));
        }
    }

    private void handleSetWorld(Player p, String[] args) {
        SetupSession s = getSession(p); if (s == null) return;
        if (args.length < 2) { p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup setworld <worldName>")); return; }
        s.worldName = args[1];
        p.sendMessage(color(PREFIX + "&aWorld set to: &e" + args[1]));
    }

    private void handleList(Player p) {
        Set<String> names = plugin.getMapManager().getMapNames();
        if (names.isEmpty()) {
            p.sendMessage(color(PREFIX + "&7No maps configured yet. Use &b/vmapsetup create <name>&7."));
            return;
        }
        p.sendMessage(color(PREFIX + "&eConfigured maps (" + names.size() + "):"));
        for (String name : names) {
            MapManager.ValorantMap map = plugin.getMapManager().getMap(name);
            if (map == null) continue;
            int atk = map.getAttackSpawns().size();
            int def = map.getDefendSpawns().size();
            int a   = map.getSiteA().size();
            int b   = map.getSiteB().size();
            String status = (atk >= 2 && def >= 2 && a > 0 && b > 0) ? "&a\u2714" : "&c\u2718";
            p.sendMessage(color("  " + status + " &e" + name + " &8(" + atk + " atk, " + def + " def, A:" + a + ", B:" + b + ")"));
        }
    }

    private void handleValidate(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup validate <name>")); return; }
        String name = args[1].toLowerCase();
        MapManager.ValorantMap map = plugin.getMapManager().getMap(name);
        if (map == null) { p.sendMessage(color(PREFIX + "&cMap not found: " + name)); return; }
        boolean ok = true;
        if (map.getAttackSpawns().size() < 2) { p.sendMessage(color("  &c\u2718 &7Attacker spawns: " + map.getAttackSpawns().size() + " (need >=2)")); ok = false; }
        else p.sendMessage(color("  &a\u2714 &7Attacker spawns: " + map.getAttackSpawns().size()));
        if (map.getDefendSpawns().size() < 2) { p.sendMessage(color("  &c\u2718 &7Defender spawns: " + map.getDefendSpawns().size() + " (need >=2)")); ok = false; }
        else p.sendMessage(color("  &a\u2714 &7Defender spawns: " + map.getDefendSpawns().size()));
        if (map.getSiteA().isEmpty()) { p.sendMessage(color("  &c\u2718 &7Site A: no locations")); ok = false; }
        else p.sendMessage(color("  &a\u2714 &7Site A: " + map.getSiteA().size() + " locations"));
        if (map.getSiteB().isEmpty()) { p.sendMessage(color("  &c\u2718 &7Site B: no locations")); ok = false; }
        else p.sendMessage(color("  &a\u2714 &7Site B: " + map.getSiteB().size() + " locations"));
        p.sendMessage(color(ok ? PREFIX + "&a&lMap '" + name + "' is ready to play!" : PREFIX + "&c&lMap '" + name + "' has issues — fix them first."));
    }

    private void handleTp(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup tp <map> [atk|def] [index]"));
            return;
        }
        String mapName = args[1].toLowerCase();
        MapManager.ValorantMap map = plugin.getMapManager().getMap(mapName);

        if (args.length < 3) {
            // Teleport to world spawn or first spawn point
            World w = plugin.getServer().getWorld("valmap_" + mapName);
            if (w == null && map != null && map.getAttackSpawns() != null && !map.getAttackSpawns().isEmpty()) {
                w = map.getAttackSpawns().get(0).getWorld();
            }
            if (w != null) {
                if (map != null && map.getAttackSpawns() != null && !map.getAttackSpawns().isEmpty()) {
                    p.teleport(map.getAttackSpawns().get(0));
                    p.sendMessage(color(PREFIX + "&aTeleported to &e" + map.getName() + " &a(Spawn 1)"));
                } else {
                    p.teleport(w.getSpawnLocation());
                    p.sendMessage(color(PREFIX + "&aTeleported to world &e" + w.getName()));
                }
                return;
            }
            p.sendMessage(color(PREFIX + "&cMap world not loaded: valmap_" + mapName));
            return;
        }

        if (args.length < 4) {
            p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup tp <map> <atk|def> <index>"));
            return;
        }

        if (map == null) { p.sendMessage(color(PREFIX + "&cMap not found: " + args[1])); return; }
        List<Location> spawns = args[2].equalsIgnoreCase("atk") ? map.getAttackSpawns() : map.getDefendSpawns();
        if (spawns.isEmpty()) {
            p.sendMessage(color(PREFIX + "&cNo spawns defined for map '" + args[1] + "' (" + args[2] + "). Use &b/vmapsetup create " + args[1] + "&c and &b/vmapsetup addspawn " + args[2] + "&c first!"));
            return;
        }
        int input;
        try { input = Integer.parseInt(args[3]); } catch (NumberFormatException e) { input = 1; }
        int idx = (input <= 0) ? 0 : Math.min(input - 1, spawns.size() - 1);
        p.teleport(spawns.get(idx));
        p.sendMessage(color(PREFIX + "&aTeleported to spawn #" + (idx + 1) + " of " + map.getName() + " (" + args[2].toUpperCase() + ")"));
    }

    private void handlePos1(Player p) {
        SetupSession s = getSession(p); if (s == null) return;
        org.bukkit.block.Block target = p.getTargetBlockExact(50);
        Location loc = target != null ? target.getLocation() : p.getLocation();
        s.pos1Str = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        p.sendMessage(color(PREFIX + "&aPos1 set to: &e" + s.pos1Str));
        p.sendMessage(color(PREFIX + "&7Right-click a block for Pos2, then run &b/vmapsetup setsite <a|b>&7."));
    }

    private void handlePos2(Player p) {
        SetupSession s = getSession(p); if (s == null) return;
        org.bukkit.block.Block target = p.getTargetBlockExact(50);
        Location loc = target != null ? target.getLocation() : p.getLocation();
        s.pos2Str = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        p.sendMessage(color(PREFIX + "&bPos2 set to: &e" + s.pos2Str));
        p.sendMessage(color(PREFIX + "&7Run &b/vmapsetup setsite a &7or &b/vmapsetup setsite b &7to save 3D region!"));
    }

    private void handleSetSite(Player p, String[] args) {
        SetupSession s = getSession(p); if (s == null) return;
        if (args.length < 2) {
            p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup setsite <a|b>"));
            return;
        }
        if (s.pos1Str == null || s.pos2Str == null) {
            p.sendMessage(color(PREFIX + "&cSet both Pos1 and Pos2 first! (Left & Right click blocks with Map Wand)"));
            return;
        }
        String site = args[1].toLowerCase();
        if (site.equals("a")) {
            s.siteA.clear();
            s.siteA.add(s.pos1Str);
            s.siteA.add(s.pos2Str);
            p.sendMessage(color(PREFIX + "&a&lSet Site A region from &e" + s.pos1Str + " &ato &e" + s.pos2Str + "&a!"));
        } else if (site.equals("b")) {
            s.siteB.clear();
            s.siteB.add(s.pos1Str);
            s.siteB.add(s.pos2Str);
            p.sendMessage(color(PREFIX + "&a&lSet Site B region from &e" + s.pos1Str + " &ato &e" + s.pos2Str + "&a!"));
        } else {
            p.sendMessage(color(PREFIX + "&cUse 'a' or 'b'."));
        }
    }

    private void giveWand(Player p) {
        ItemStack wand = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta m = wand.getItemMeta();
        if (m == null) { p.sendMessage(ValorantMC.colorize("&cFailed to create map wand.")); return; }
        m.setDisplayName(ValorantMC.colorize("&6&lMap Wand"));
        m.setLore(List.of(
                ValorantMC.colorize("&aLeft-click block &7— Set Pos1 (Corner 1)"),
                ValorantMC.colorize("&bRight-click block &7— Set Pos2 (Corner 2)"),
                ValorantMC.colorize("&eUse /vmapsetup setsite <a|b> &7to save region")));
        m.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "map_wand"), PersistentDataType.BOOLEAN, true);
        wand.setItemMeta(m);
        p.getInventory().addItem(wand);
        p.sendMessage(color(PREFIX + "&a&lMap Wand given!"));
        p.sendMessage(color(PREFIX + "&7• &aLeft-click block &7to set Pos1 (Corner 1)"));
        p.sendMessage(color(PREFIX + "&7• &bRight-click block &7to set Pos2 (Corner 2)"));
        p.sendMessage(color(PREFIX + "&7• &eUse &b/vmapsetup setsite <a|b> &7or GUI to assign region to a site!"));
    }

    // ── Particle Renderer ─────────────────────────────────────────────────────

    private void startParticleTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, SetupSession> entry : sessions.entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null || !p.isOnline()) continue;
                SetupSession s = entry.getValue();

                // Render Site A (Green/Cyan dust box)
                renderSiteParticleBoxes(p, s.siteA, s.siteRadius, Color.fromRGB(0, 255, 170));

                // Render Site B (Red/Orange dust box)
                renderSiteParticleBoxes(p, s.siteB, s.siteRadius, Color.fromRGB(255, 60, 0));

                // Render Pos1 -> Pos2 selection preview (White dust box) if set
                if (s.pos1Str != null && s.pos2Str != null) {
                    renderSiteParticleBoxes(p, List.of(s.pos1Str, s.pos2Str), 5.0, Color.fromRGB(255, 255, 255));
                }

                // Render ATK spawns (Red pillars)
                renderSpawnPillars(p, s.attackSpawns, Color.fromRGB(255, 30, 30));

                // Render DEF spawns (Blue pillars)
                renderSpawnPillars(p, s.defendSpawns, Color.fromRGB(30, 140, 255));
            }
        }, 10L, 8L);
    }

    private void renderSiteParticleBoxes(Player p, List<String> coords, double radius, Color color) {
        if (coords.isEmpty()) return;
        World world = p.getWorld();
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2f);

        // If 2 or more coordinates are present (Pos1 & Pos2), render ONE big 3D cuboid region box
        if (coords.size() >= 2) {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

            for (String entry : coords) {
                String[] parts = entry.split(",");
                if (parts.length < 3) continue;
                try {
                    double x = Double.parseDouble(parts[0].trim());
                    double y = Double.parseDouble(parts[1].trim());
                    double z = Double.parseDouble(parts[2].trim());
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x + 1.0);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y + 1.0);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z + 1.0);
                } catch (Exception ignored) {}
            }

            if (minX > maxX) return;

            // 4 Vertical corner pillars
            for (double y = minY; y <= maxY; y += 0.4) {
                p.spawnParticle(Particle.REDSTONE, minX, y, minZ, 1, 0, 0, 0, 0, dust);
                p.spawnParticle(Particle.REDSTONE, maxX, y, minZ, 1, 0, 0, 0, 0, dust);
                p.spawnParticle(Particle.REDSTONE, maxX, y, maxZ, 1, 0, 0, 0, 0, dust);
                p.spawnParticle(Particle.REDSTONE, minX, y, maxZ, 1, 0, 0, 0, 0, dust);
            }

            // Draw 3 perimeter horizontal rectangles (floor, mid, ceiling)
            double[] heights = {minY + 0.1, (minY + maxY) / 2.0, maxY};
            for (double h : heights) {
                drawParticleLine(p, minX, h, minZ, maxX, h, minZ, dust);
                drawParticleLine(p, maxX, h, minZ, maxX, h, maxZ, dust);
                drawParticleLine(p, maxX, h, maxZ, minX, h, maxZ, dust);
                drawParticleLine(p, minX, h, maxZ, minX, h, minZ, dust);
            }
            return;
        }

        // Single coordinate fallback (square box centered at point with radius)
        for (String entry : coords) {
            String[] parts = entry.split(",");
            if (parts.length < 3) continue;
            try {
                double cx = Double.parseDouble(parts[0].trim()) + 0.5;
                double cy = Double.parseDouble(parts[1].trim());
                double cz = Double.parseDouble(parts[2].trim()) + 0.5;

                // Center vertical beam
                for (double y = cy; y <= cy + 4.0; y += 0.5) {
                    p.spawnParticle(Particle.REDSTONE, cx, y, cz, 1, 0, 0, 0, 0, dust);
                }

                double minX = cx - radius;
                double maxX = cx + radius;
                double minZ = cz - radius;
                double maxZ = cz + radius;

                for (double y = cy; y <= cy + 2.5; y += 0.4) {
                    p.spawnParticle(Particle.REDSTONE, minX, y, minZ, 1, 0, 0, 0, 0, dust);
                    p.spawnParticle(Particle.REDSTONE, maxX, y, minZ, 1, 0, 0, 0, 0, dust);
                    p.spawnParticle(Particle.REDSTONE, maxX, y, maxZ, 1, 0, 0, 0, 0, dust);
                    p.spawnParticle(Particle.REDSTONE, minX, y, maxZ, 1, 0, 0, 0, 0, dust);
                }

                double[] heights = {cy + 0.1, cy + 1.0};
                for (double h : heights) {
                    drawParticleLine(p, minX, h, minZ, maxX, h, minZ, dust);
                    drawParticleLine(p, maxX, h, minZ, maxX, h, maxZ, dust);
                    drawParticleLine(p, maxX, h, maxZ, minX, h, maxZ, dust);
                    drawParticleLine(p, minX, h, maxZ, minX, h, minZ, dust);
                }
            } catch (Exception ignored) {}
        }
    }

    private void drawParticleLine(Player p, double x1, double y1, double z1, double x2, double y2, double z2, Particle.DustOptions dust) {
        double dist = Math.hypot(x2 - x1, z2 - z1);
        int steps = (int) Math.max(4, dist * 3);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double px = x1 + (x2 - x1) * t;
            double pz = z1 + (z2 - z1) * t;
            p.spawnParticle(Particle.REDSTONE, px, y1, pz, 1, 0, 0, 0, 0, dust);
        }
    }

    private void renderSpawnPillars(Player p, List<String> coords, Color color) {
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);
        for (String entry : coords) {
            String[] parts = entry.split(",");
            if (parts.length < 3) continue;
            try {
                double sx = Double.parseDouble(parts[0].trim()) + 0.5;
                double sy = Double.parseDouble(parts[1].trim());
                double sz = Double.parseDouble(parts[2].trim()) + 0.5;

                for (double y = sy; y <= sy + 2.5; y += 0.4) {
                    p.spawnParticle(Particle.REDSTONE, sx, y, sz, 1, 0, 0, 0, 0, dust);
                }
            } catch (Exception ignored) {}
        }
    }

    public SetupSession getActiveSession(Player p) {
        return sessions.get(p.getUniqueId());
    }

    public SetupSession getOrCreateSession(Player p, String mapName) {
        return sessions.computeIfAbsent(p.getUniqueId(), k -> new SetupSession(mapName, p.getWorld().getName()));
    }

    public void removeSession(Player p) {
        sessions.remove(p.getUniqueId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SetupSession getSession(Player p) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            p.sendMessage(color(PREFIX + "&cNo active session. Run &b/vmapsetup create <name>&c or &b/vmapsetup edit <name>&c first."));
        }
        return s;
    }

    private static String color(String s) { return ValorantMC.colorize(s); }

    private void sendHelp(Player p) {
        p.sendMessage(color("&b&l=== Map Setup Wizard ==="));
        p.sendMessage(color("&b/vmapsetup create <name>        &7— Start a new map"));
        p.sendMessage(color("&b/vmapsetup edit <name>          &7— Edit existing map"));
        p.sendMessage(color("&b/vmapsetup pos1 / pos2          &7— Set region corner 1 or 2"));
        p.sendMessage(color("&b/vmapsetup setsite <a|b>        &7— Assign pos1..pos2 to Site A/B"));
        p.sendMessage(color("&b/vmapsetup radius <1-30>        &7— Set site zone radius (meters)"));
        p.sendMessage(color("&b/vmapsetup addspawn <atk|def>   &7— Record spawn at your position"));
        p.sendMessage(color("&b/vmapsetup addsite <a|b>        &7— Record bomb site at your position"));
        p.sendMessage(color("&b/vmapsetup save                 &7— Save and hot-reload"));
        p.sendMessage(color("&b/vmapsetup reset <name>         &7— Reset map back to defaults"));
        p.sendMessage(color("&b/vmapsetup cancel               &7— Cancel setup session"));
        p.sendMessage(color("&b/vmapsetup list                 &7— List all maps"));
        p.sendMessage(color("&b/vmapsetup validate <name>      &7— Check map is playable"));
        p.sendMessage(color("&b/vmapsetup tp <map> <atk|def> <i> &7— Teleport to a spawn"));
        p.sendMessage(color("&b/vmapsetup wand                 &7— Get the Map Wand"));
        p.sendMessage(color("&b/vmapsetup gui                  &7— Open Admin Map Setup Panel"));
    }

    // ── Session class ─────────────────────────────────────────────────────────

    public static class SetupSession {
        public final String       mapName;
        public       String       worldName;
        public       double       siteRadius  = 5.0;
        public       String       pos1Str     = null;
        public       String       pos2Str     = null;
        public final List<String> attackSpawns = new ArrayList<>();
        public final List<String> defendSpawns = new ArrayList<>();
        public final List<String> siteA        = new ArrayList<>();
        public final List<String> siteB        = new ArrayList<>();

        public SetupSession(String mapName, String worldName) {
            this.mapName   = mapName;
            this.worldName = worldName;
        }
    }
}
