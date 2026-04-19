package com.valorantmc.gui;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.game.ValorantTeam;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Builds all admin panel GUI screens.
 *
 * Title constants:
 *   TITLE_MAIN, TITLE_PLAYERS_GIVE, TITLE_PLAYERS_TROLL,
 *   TITLE_GIVE, TITLE_TROLL, TITLE_MAP, TITLE_GAME
 */
public class AdminGUI {

    public static final String TITLE_MAIN          = "§4§l⚡ Admin Panel";
    public static final String TITLE_PLAYERS_GIVE  = "§4§l⚡ Give — Select Player";
    public static final String TITLE_PLAYERS_TROLL = "§4§l⚡ Troll — Select Player";
    public static final String TITLE_GIVE          = "§4§l⚡ Give Items";
    public static final String TITLE_TROLL         = "§4§l⚡ Troll Player";
    public static final String TITLE_MAP           = "§4§l⚡ Map Setup";
    public static final String TITLE_GAME          = "§4§l⚡ Game Control";

    // NBT keys stored on buttons so AdminListener knows what to do
    public static final NamespacedKey NSK_ACTION  = new NamespacedKey(ValorantMC.getInstance(), "admin_action");
    public static final NamespacedKey NSK_TARGET  = new NamespacedKey(ValorantMC.getInstance(), "admin_target");

    // ── Main Hub ──────────────────────────────────────────────────────────────

    public static Inventory buildMain(Player admin, ValorantGame game) {
        Inventory inv = Bukkit.createInventory(null, 45, TITLE_MAIN);
        fill(inv, gray());

        // Row 1 — category buttons
        inv.setItem(10, btn(Material.COMPASS,       "§a§lMap Setup",
                "§7Add spawns, bomb sites",
                "§7and edit map config.", "map_setup", null));
        inv.setItem(13, btn(Material.DIAMOND,       "§b§lGive Items",
                "§7Give guns, credits,",
                "§7abilities and ult to a player.", "give_players", null));
        inv.setItem(16, btn(Material.GOLDEN_APPLE,  "§c§lTroll Menu",
                "§7Kill, freeze, blind",
                "§7or harrass players.", "troll_players", null));

        // Row 2 — game control
        inv.setItem(30, btn(Material.COMMAND_BLOCK, "§e§lGame Control",
                "§7End round, skip buy phase,",
                "§7pause or end the game.", "game_control", null));

        // Row 2 — game info
        if (game != null) {
            String mapName = game.getMapName() != null ? game.getMapName() : "none";
            inv.setItem(32, infoItem(Material.MAP, "§f§lCurrent Game",
                    "§7Map: §f" + mapName,
                    "§7State: §f" + game.getState(),
                    "§7Round: §f" + game.getCurrentRound()));
        }

        // Bottom row — close
        inv.setItem(40, btn(Material.BARRIER, "§c§lClose", "§7Close this panel.", "close", null));

        return inv;
    }

    // ── Player Select (shared for Give and Troll) ─────────────────────────────

    public static Inventory buildPlayerSelect(Player admin, ValorantGame game, String context) {
        String title = context.equals("give") ? TITLE_PLAYERS_GIVE : TITLE_PLAYERS_TROLL;
        Inventory inv = Bukkit.createInventory(null, 54, title);
        fill(inv, gray());

        List<Player> players = game != null ? game.getAllPlayers() : new ArrayList<>();
        int slot = 10;
        for (Player target : players) {
            if (slot > 43) break;
            inv.setItem(slot, playerHead(target,
                    "§e" + target.getName(),
                    "§7Click to " + (context.equals("give") ? "give items to" : "troll") + " this player.",
                    "player_select:" + target.getUniqueId(), null));
            slot++;
            if (slot == 17 || slot == 26 || slot == 35) slot += 2; // skip border
        }

        inv.setItem(49, btn(Material.ARROW, "§7Back", "§7Return to admin panel.", "back_main", null));
        return inv;
    }

    // ── Give Items ────────────────────────────────────────────────────────────

    public static Inventory buildGive(Player admin, Player target) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_GIVE);
        fill(inv, gray());

        String tid = target.getUniqueId().toString();

        // Row 1 — Credits
        int[] creditAmounts = {100, 500, 1000, 3000, 9000};
        int[] creditSlots   = {1, 2, 3, 4, 5};
        for (int i = 0; i < creditAmounts.length; i++) {
            inv.setItem(creditSlots[i], btn(Material.GOLD_NUGGET,
                    "§6+" + creditAmounts[i] + " Credits",
                    "§7Give §6" + creditAmounts[i] + " credits",
                    "§7to §e" + target.getName() + "§7.",
                    "give_credits:" + creditAmounts[i], tid));
        }
        inv.setItem(7, btn(Material.GOLD_INGOT, "§6Max Credits §8(9000)",
                "§7Set §e" + target.getName() + "§7's credits",
                "§7to the maximum (9000).",
                "give_credits_max", tid));

        // Target player head at top-right
        inv.setItem(8, playerHead(target, "§eTarget: §f" + target.getName(),
                "§7All actions below apply to this player.", null, null));

        // Row 2 — Sidearms
        int slot = 9;
        for (WeaponType wt : WeaponType.values()) {
            if (wt.getCategory() != com.valorantmc.weapons.WeaponCategory.SIDEARM) continue;
            inv.setItem(slot++, weaponBtn(wt, tid));
        }

        // Row 3 — SMGs + Shotguns
        slot = 18;
        for (WeaponType wt : WeaponType.values()) {
            if (wt.getCategory() != com.valorantmc.weapons.WeaponCategory.SMG
                    && wt.getCategory() != com.valorantmc.weapons.WeaponCategory.SHOTGUN) continue;
            inv.setItem(slot++, weaponBtn(wt, tid));
        }

        // Row 4 — Rifles
        slot = 27;
        for (WeaponType wt : WeaponType.values()) {
            if (wt.getCategory() != com.valorantmc.weapons.WeaponCategory.RIFLE) continue;
            inv.setItem(slot++, weaponBtn(wt, tid));
        }

        // Row 5 — Snipers + Heavy + Melee
        slot = 36;
        for (WeaponType wt : WeaponType.values()) {
            if (wt.getCategory() != com.valorantmc.weapons.WeaponCategory.SNIPER
                    && wt.getCategory() != com.valorantmc.weapons.WeaponCategory.HEAVY
                    && wt.getCategory() != com.valorantmc.weapons.WeaponCategory.MELEE) continue;
            inv.setItem(slot++, weaponBtn(wt, tid));
        }

        // Bottom row — Ult, Abilities, Armor, Shields, Refill, Back
        inv.setItem(45, btn(Material.NETHER_STAR, "§d+1 Ult Point",
                "§7Give 1 ultimate point to §e" + target.getName() + "§7.", "give_ult_1", tid));
        inv.setItem(46, btn(Material.BEACON,      "§dFull Ult",
                "§7Fill ultimate charge for §e" + target.getName() + "§7.", "give_ult_full", tid));
        inv.setItem(47, btn(Material.LIME_DYE,    "§a+1 Ability C",
                "§7Refill 1 charge of ability C.", "give_ability_C", tid));
        inv.setItem(48, btn(Material.CYAN_DYE,    "§a+1 Ability Q",
                "§7Refill 1 charge of ability Q.", "give_ability_Q", tid));
        inv.setItem(49, btn(Material.LIGHT_BLUE_DYE, "§a+1 Ability E",
                "§7Refill 1 charge of ability E.", "give_ability_E", tid));
        inv.setItem(50, btn(Material.IRON_CHESTPLATE, "§7Light Shield §8(25 HP)",
                "§7Give light armor to §e" + target.getName() + "§7.", "give_light_shield", tid));
        inv.setItem(51, btn(Material.DIAMOND_CHESTPLATE, "§bHeavy Shield §8(50 HP)",
                "§7Give heavy armor to §e" + target.getName() + "§7.", "give_heavy_shield", tid));
        inv.setItem(52, btn(Material.POTION,      "§aFull Refill Ammo",
                "§7Refill all ammo for §e" + target.getName() + "§7.", "give_refill_ammo", tid));
        inv.setItem(53, btn(Material.ARROW,       "§7Back", "§7Return to player list.", "back_give_players", tid));

        return inv;
    }

    // ── Troll Menu ────────────────────────────────────────────────────────────

    public static Inventory buildTroll(Player admin, Player target) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_TROLL);
        fill(inv, gray());

        String tid = target.getUniqueId().toString();

        // Header info
        inv.setItem(4, playerHead(target, "§cTrolling: §f" + target.getName(),
                "§7HP: §f" + (int)(target.getHealth() / 20.0 * 100) + "§7/100",
                "§7World: §f" + target.getWorld().getName(),
                null, null));

        // Combat actions
        inv.setItem(10, btn(Material.BONE,             "§c☠ Kill",
                "§7Instantly kill §c" + target.getName() + "§7.", "troll_kill", tid));
        inv.setItem(11, btn(Material.ICE,              "§b❄ Freeze",
                "§7Apply Slowness 255 + Jump Boost -5",
                "§7to §b" + target.getName() + "§7 for 10s.", "troll_freeze", tid));
        inv.setItem(12, btn(Material.MAGMA_CREAM,      "§a▶ Unfreeze",
                "§7Remove all debuffs from §e" + target.getName() + "§7.", "troll_unfreeze", tid));
        inv.setItem(13, btn(Material.INK_SAC,          "§8◉ Blindness",
                "§7Apply Blindness for 8s to §e" + target.getName() + "§7.", "troll_blind", tid));
        inv.setItem(14, btn(Material.SPIDER_EYE,       "§5~ Nausea",
                "§7Apply Nausea for 8s to §e" + target.getName() + "§7.", "troll_nausea", tid));
        inv.setItem(15, btn(Material.FEATHER,          "§eNo Clip",
                "§7Toggle flight for §e" + target.getName() + "§7.", "troll_noclip", tid));
        inv.setItem(16, btn(Material.TNT,              "§c✦ Launch",
                "§7Launch §c" + target.getName() + "§7 into the air.", "troll_launch", tid));

        // Item actions
        inv.setItem(19, btn(Material.CHEST,            "§6Strip Weapons",
                "§7Remove all weapons from §e" + target.getName() + "§7's inventory.", "troll_strip", tid));
        inv.setItem(20, btn(Material.EMERALD,          "§a Max Credits",
                "§7Set §e" + target.getName() + "§7's credits to max (9000).", "troll_maxcredits", tid));
        inv.setItem(21, btn(Material.GLASS,            "§7 Zero Credits",
                "§7Set §e" + target.getName() + "§7's credits to 0.", "troll_zerocredits", tid));
        inv.setItem(22, btn(Material.FIRE_CHARGE,      "§c☄ Ignite",
                "§7Set §c" + target.getName() + "§7 on fire for 5s.", "troll_ignite", tid));
        inv.setItem(23, btn(Material.ENDER_PEARL,      "§5⟳ Random Teleport",
                "§7Teleport §5" + target.getName() + "§7 to a random",
                "§7location on the map.", "troll_randtp", tid));
        inv.setItem(24, btn(Material.GOLDEN_SWORD,     "§6⟳ Teleport to Me",
                "§7Teleport §e" + target.getName() + "§7 to your location.", "troll_tptome", tid));
        inv.setItem(25, btn(Material.COMPASS,          "§b⟳ TP Me to Them",
                "§7Teleport yourself to §e" + target.getName() + "§7.", "troll_tototarget", tid));

        // Misc
        inv.setItem(28, btn(Material.DIRT,             "§8Revive",
                "§7Revive §e" + target.getName() + "§7 if they're dead.", "troll_revive", tid));
        inv.setItem(29, btn(Material.MILK_BUCKET,      "§fClear Effects",
                "§7Remove all potion effects.", "troll_cleareffects", tid));
        inv.setItem(30, btn(Material.SUGAR,            "§eSpeed Boost",
                "§7Give Speed II for 10s.", "troll_speed", tid));
        inv.setItem(31, btn(Material.REDSTONE,         "§cSlow Walk",
                "§7Give Slowness III for 10s.", "troll_slow", tid));

        // Back
        inv.setItem(49, btn(Material.ARROW, "§7Back", "§7Return to player list.", "back_troll_players", tid));

        return inv;
    }

    // ── Map Setup ─────────────────────────────────────────────────────────────

    public static Inventory buildMapSetup(Player admin, ValorantGame game) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MAP);
        fill(inv, gray());

        // Header: map name
        String mapName = game != null && game.getMapName() != null ? game.getMapName() : "none";
        inv.setItem(4, infoItem(Material.MAP, "§e§lMap: §f" + mapName,
                "§7Edit spawn points and",
                "§7bomb site locations below."));

        // Attacker spawns (row 2, left half)
        inv.setItem(0, infoItem(Material.RED_STAINED_GLASS_PANE, "§c§lAttacker Spawns", ""));
        List<Location> atkSpawns = game != null ? game.getAttackSpawnsPublic() : new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (i < atkSpawns.size()) {
                Location loc = atkSpawns.get(i);
                inv.setItem(9 + i, btn(Material.RED_CONCRETE, "§cATK Spawn #" + (i+1),
                        "§7X: §f" + loc.getBlockX(),
                        "§7Y: §f" + loc.getBlockY() + " Z: §f" + loc.getBlockZ(),
                        "§eClick §7to teleport there.",
                        "§cShift-click §7to remove.",
                        "map_tp_atk:" + i, null));
            } else {
                inv.setItem(9 + i, btn(Material.RED_STAINED_GLASS_PANE, "§8Empty ATK Slot #" + (i+1),
                        "§7No spawn set.", "map_empty", null));
            }
        }

        // Defender spawns (row 2, right half)
        inv.setItem(8, infoItem(Material.BLUE_STAINED_GLASS_PANE, "§b§lDefender Spawns", ""));
        List<Location> defSpawns = game != null ? game.getDefendSpawnsPublic() : new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (i < defSpawns.size()) {
                Location loc = defSpawns.get(i);
                inv.setItem(14 + i, btn(Material.BLUE_CONCRETE, "§bDEF Spawn #" + (i+1),
                        "§7X: §f" + loc.getBlockX(),
                        "§7Y: §f" + loc.getBlockY() + " Z: §f" + loc.getBlockZ(),
                        "§eClick §7to teleport there.",
                        "§cShift-click §7to remove.",
                        "map_tp_def:" + i, null));
            } else {
                inv.setItem(14 + i, btn(Material.BLUE_STAINED_GLASS_PANE, "§8Empty DEF Slot #" + (i+1),
                        "§7No spawn set.", "map_empty", null));
            }
        }

        // Bomb site A (row 3)
        inv.setItem(18, infoItem(Material.ORANGE_STAINED_GLASS_PANE, "§6§lSite A Locations", ""));
        List<Location> siteA = game != null ? game.getSiteALocations() : new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (i < siteA.size()) {
                Location loc = siteA.get(i);
                inv.setItem(19 + i, btn(Material.ORANGE_CONCRETE, "§6Site A #" + (i+1),
                        "§7X: §f" + loc.getBlockX() + " Y: §f" + loc.getBlockY() + " Z: §f" + loc.getBlockZ(),
                        "§eClick §7to teleport.",
                        "map_tp_siteA:" + i, null));
            } else {
                inv.setItem(19 + i, btn(Material.ORANGE_STAINED_GLASS_PANE, "§8Empty Site A Slot",
                        "§7Not set.", "map_empty", null));
            }
        }

        // Bomb site B (row 4)
        inv.setItem(27, infoItem(Material.GREEN_STAINED_GLASS_PANE, "§a§lSite B Locations", ""));
        List<Location> siteB = game != null ? game.getSiteBLocations() : new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (i < siteB.size()) {
                Location loc = siteB.get(i);
                inv.setItem(28 + i, btn(Material.GREEN_CONCRETE, "§aSite B #" + (i+1),
                        "§7X: §f" + loc.getBlockX() + " Y: §f" + loc.getBlockY() + " Z: §f" + loc.getBlockZ(),
                        "§eClick §7to teleport.",
                        "map_tp_siteB:" + i, null));
            } else {
                inv.setItem(28 + i, btn(Material.GREEN_STAINED_GLASS_PANE, "§8Empty Site B Slot",
                        "§7Not set.", "map_empty", null));
            }
        }

        // Add buttons (row 5)
        inv.setItem(36, btn(Material.RED_DYE,    "§c+ Add ATK Spawn",
                "§7Records your current location",
                "§7as an attacker spawn point.", "map_add_atk", null));
        inv.setItem(37, btn(Material.BLUE_DYE,   "§b+ Add DEF Spawn",
                "§7Records your current location",
                "§7as a defender spawn point.", "map_add_def", null));
        inv.setItem(38, btn(Material.ORANGE_DYE, "§6+ Add Site A",
                "§7Records your current location",
                "§7as a bomb-site A point.", "map_add_siteA", null));
        inv.setItem(39, btn(Material.GREEN_DYE,  "§a+ Add Site B",
                "§7Records your current location",
                "§7as a bomb-site B point.", "map_add_siteB", null));
        inv.setItem(41, btn(Material.WRITABLE_BOOK, "§eSave Map",
                "§7Saves changes to the map YAML",
                "§7and hot-reloads it.", "map_save", null));
        inv.setItem(42, btn(Material.TNT,        "§cClear All Spawns",
                "§7Removes ALL spawn and site",
                "§7data for this map. §c§lCannot undo!", "map_clear", null));
        inv.setItem(49, btn(Material.ARROW,      "§7Back", "§7Return to admin panel.", "back_main", null));

        return inv;
    }

    // ── Game Control ──────────────────────────────────────────────────────────

    public static Inventory buildGameControl(Player admin, ValorantGame game) {
        Inventory inv = Bukkit.createInventory(null, 45, TITLE_GAME);
        fill(inv, gray());

        if (game == null) {
            inv.setItem(22, infoItem(Material.BARRIER, "§cNo active game",
                    "§7You are not in an active game."));
            inv.setItem(40, btn(Material.ARROW, "§7Back", "", "back_main", null));
            return inv;
        }

        inv.setItem(10, btn(Material.RED_CONCRETE,   "§c✦ End Round (ATK Win)",
                "§7Immediately end the round,",
                "§7attackers win.", "game_end_round_atk", null));
        inv.setItem(12, btn(Material.BLUE_CONCRETE,  "§b✦ End Round (DEF Win)",
                "§7Immediately end the round,",
                "§7defenders win.", "game_end_round_def", null));
        inv.setItem(14, btn(Material.YELLOW_CONCRETE,"§e⏩ Skip Buy Phase",
                "§7Instantly end buy phase and",
                "§7start the combat round.", "game_skip_buy", null));
        inv.setItem(16, btn(game.isPaused() ? Material.GREEN_CONCRETE : Material.ORANGE_CONCRETE,
                game.isPaused() ? "§a▶ Resume Game" : "§e⏸ Pause Game",
                "§7Pause or resume the round timer.", "game_toggle_pause", null));

        inv.setItem(28, btn(Material.BARRIER, "§4✕ End Game",
                "§7Force end the entire match.",
                "§cThis cannot be undone!", "game_end", null));
        inv.setItem(30, btn(Material.BOOK,    "§f Status",
                "§7Map: §f"  + (game.getMapName() != null ? game.getMapName() : "none"),
                "§7State: §f" + game.getState(),
                "§7Round: §f" + game.getCurrentRound(),
                "§7Players: §f" + game.getAllPlayers().size(), "game_status", null));
        inv.setItem(32, btn(Material.RESPAWN_ANCHOR, "§6Refill Everyone's Ammo",
                "§7Refill ammo for all players.", "game_refill_all", null));
        inv.setItem(34, btn(Material.TOTEM_OF_UNDYING, "§aRevive All Dead",
                "§7Revive all dead players", "§7to spectator target.", "game_revive_all", null));

        inv.setItem(40, btn(Material.ARROW, "§7Back", "§7Return to admin panel.", "back_main", null));
        return inv;
    }

    // ── Item builder helpers ──────────────────────────────────────────────────

    /** Build an action button with 1-2 lore lines */
    public static ItemStack btn(Material mat, String name, String lore1, String action, String target) {
        return btn(mat, name, lore1, null, null, null, action, target);
    }

    public static ItemStack btn(Material mat, String name, String lore1, String lore2,
                                String action, String target) {
        return btn(mat, name, lore1, lore2, null, null, action, target);
    }

    public static ItemStack btn(Material mat, String name, String lore1, String lore2,
                                String lore3, String lore4, String action, String target) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        if (lore1 != null) lore.add(lore1);
        if (lore2 != null) lore.add(lore2);
        if (lore3 != null) lore.add(lore3);
        if (lore4 != null) lore.add(lore4);
        meta.setLore(lore);
        if (action != null)
            meta.getPersistentDataContainer().set(NSK_ACTION, PersistentDataType.STRING, action);
        if (target != null)
            meta.getPersistentDataContainer().set(NSK_TARGET, PersistentDataType.STRING, target);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack weaponBtn(WeaponType wt, String targetUUID) {
        return btn(wt.getMaterial(),
                "§f§l" + wt.getDisplayName(),
                "§7Category: §f" + wt.getCategory().getDisplayName(),
                "§7Damage: §f" + wt.getDamage() + "  Cost: §6" + wt.getCost() + "c",
                "give_weapon:" + wt.name(), targetUUID);
    }

    private static ItemStack infoItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        List<String> l = new ArrayList<>(Arrays.asList(lore));
        meta.setLore(l);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack playerHead(Player p, String name, String lore1, String action, String target) {
        return playerHead(p, name, lore1, null, action, target);
    }

    static ItemStack playerHead(Player p, String name, String lore1, String lore2, String action, String target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(p.getUniqueId()));
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        if (lore1 != null) lore.add(lore1);
        if (lore2 != null) lore.add(lore2);
        meta.setLore(lore);
        if (action != null)
            meta.getPersistentDataContainer().set(NSK_ACTION, PersistentDataType.STRING, action);
        if (target != null)
            meta.getPersistentDataContainer().set(NSK_TARGET, PersistentDataType.STRING, target);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack gray() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        if (m == null) return pane;
        m.setDisplayName(" ");
        pane.setItemMeta(m);
        return pane;
    }

    private static void fill(Inventory inv, ItemStack filler) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }
}
