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
            case "addspawn" -> handleAddSpawn(p, args);
            case "addsite"  -> handleAddSite(p, args);
            case "setworld" -> handleSetWorld(p, args);
            case "save"     -> handleSave(p);
            case "list"     -> handleList(p);
            case "validate" -> handleValidate(p, args);
            case "tp"       -> handleTp(p, args);
            case "wand"     -> giveWand(p);
            default         -> sendHelp(p);
        }
        return true;
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

    private void handleAddSpawn(Player p, String[] args) {
        SetupSession s = getSession(p); if (s == null) return;
        if (args.length < 2) { p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup addspawn <atk|def>")); return; }
        Location loc = p.getLocation();
        String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                + "," + Math.round(loc.getYaw()) + "," + Math.round(loc.getPitch());
        if (args[1].equalsIgnoreCase("atk") || args[1].equalsIgnoreCase("attacker")) {
            s.attackSpawns.add(entry);
            p.sendMessage(color(PREFIX + "&aAdded attacker spawn #" + s.attackSpawns.size() + ": &7" + entry));
        } else if (args[1].equalsIgnoreCase("def") || args[1].equalsIgnoreCase("defender")) {
            s.defendSpawns.add(entry);
            p.sendMessage(color(PREFIX + "&aAdded defender spawn #" + s.defendSpawns.size() + ": &7" + entry));
        } else {
            p.sendMessage(color(PREFIX + "&cUse 'atk' or 'def'."));
        }
    }

    private void handleAddSite(Player p, String[] args) {
        SetupSession s = getSession(p); if (s == null) return;
        if (args.length < 2) { p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup addsite <a|b>")); return; }
        Location loc = p.getLocation();
        String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        if (args[1].equalsIgnoreCase("a")) {
            s.siteA.add(entry);
            p.sendMessage(color(PREFIX + "&aAdded Site A location #" + s.siteA.size() + ": &7" + entry));
        } else if (args[1].equalsIgnoreCase("b")) {
            s.siteB.add(entry);
            p.sendMessage(color(PREFIX + "&aAdded Site B location #" + s.siteB.size() + ": &7" + entry));
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
                s.attackSpawns, s.defendSpawns, s.siteA, s.siteB);
        plugin.getMapManager().reloadMaps();
        sessions.remove(p.getUniqueId());
        p.sendMessage(color(PREFIX + "&a&lMap '" + s.mapName + "' saved and loaded successfully!"));
        p.sendMessage(color(PREFIX + "&7Start a game with: &b/valorant start <id> " + s.mapName));
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
        if (args.length < 4) { p.sendMessage(color(PREFIX + "&cUsage: /vmapsetup tp <map> <atk|def> <index>")); return; }
        MapManager.ValorantMap map = plugin.getMapManager().getMap(args[1].toLowerCase());
        if (map == null) { p.sendMessage(color(PREFIX + "&cMap not found: " + args[1])); return; }
        List<Location> spawns = args[2].equalsIgnoreCase("atk") ? map.getAttackSpawns() : map.getDefendSpawns();
        int idx;
        try { idx = Integer.parseInt(args[3]) - 1; } catch (NumberFormatException e) { idx = 0; }
        if (idx < 0 || idx >= spawns.size()) {
            p.sendMessage(color(PREFIX + "&cIndex out of range (1-" + spawns.size() + ")"));
            return;
        }
        p.teleport(spawns.get(idx));
        p.sendMessage(color(PREFIX + "&aTeleported to spawn #" + (idx + 1) + " of " + args[1]));
    }

    private void giveWand(Player p) {
        ItemStack wand = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta m = wand.getItemMeta();
        if (m == null) { p.sendMessage(ValorantMC.colorize("&cFailed to create map wand.")); return; }
        m.setDisplayName(ValorantMC.colorize("&6Map Wand"));
        m.setLore(List.of(
                ValorantMC.colorize("&7Right-click a block to mark it"),
                ValorantMC.colorize("&7as a bomb-site location for your"),
                ValorantMC.colorize("&7active map setup session."),
                ValorantMC.colorize("&8Use /vmapsetup addsite a/b instead")));
        m.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "map_wand"), PersistentDataType.BOOLEAN, true);
        wand.setItemMeta(m);
        p.getInventory().addItem(wand);
        p.sendMessage(color(PREFIX + "&aMap Wand given! Right-click blocks to mark site locations."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SetupSession getSession(Player p) {
        SetupSession s = sessions.get(p.getUniqueId());
        if (s == null) {
            p.sendMessage(color(PREFIX + "&cNo active session. Run &b/vmapsetup create <name>&c first."));
        }
        return s;
    }

    private static String color(String s) { return ValorantMC.colorize(s); }

    private void sendHelp(Player p) {
        p.sendMessage(color("&b&l=== Map Setup Wizard ==="));
        p.sendMessage(color("&b/vmapsetup create <name>        &7— Start a new map"));
        p.sendMessage(color("&b/vmapsetup addspawn <atk|def>   &7— Record spawn at your position"));
        p.sendMessage(color("&b/vmapsetup addsite <a|b>        &7— Record bomb site at your position"));
        p.sendMessage(color("&b/vmapsetup setworld <world>     &7— Set the world name"));
        p.sendMessage(color("&b/vmapsetup save                 &7— Save and hot-reload"));
        p.sendMessage(color("&b/vmapsetup list                 &7— List all maps"));
        p.sendMessage(color("&b/vmapsetup validate <name>      &7— Check map is playable"));
        p.sendMessage(color("&b/vmapsetup tp <map> <atk|def> <i> &7— Teleport to a spawn"));
        p.sendMessage(color("&b/vmapsetup wand                 &7— Get the Map Wand"));
    }

    // ── Session class ─────────────────────────────────────────────────────────

    public static class SetupSession {
        public final String       mapName;
        public       String       worldName;
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
