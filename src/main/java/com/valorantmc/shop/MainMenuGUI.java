package com.valorantmc.shop;

import com.valorantmc.ValorantMC;
import com.valorantmc.managers.StatsManager;
import com.valorantmc.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class MainMenuGUI {

    public static final String TITLE = ValorantMC.colorize("&c&lVALORANT &r&8— Main Menu");

    public static Inventory build(Player player, ValorantMC plugin) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Background
        ItemStack dark = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inv.setItem(i, dark);

        // Red accent bar across the top
        ItemStack red = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) inv.setItem(i, red);

        // ── Main actions (row 2-3) ────────────────────────────────────────────

        // PLAY — quick match
        inv.setItem(20, new ItemBuilder(Material.LIME_CONCRETE)
                .name("&a&lPLAY")
                .lore("&7Jump straight into a match.",
                      "&7Auto-joins an open game or",
                      "&7creates a new one for you.",
                      "",
                      "&eLeft-click to play!")
                .nbt("menu_action", "quickplay")
                .build());

        // CUSTOM GAME
        inv.setItem(22, new ItemBuilder(Material.YELLOW_CONCRETE)
                .name("&e&lCUSTOM GAME")
                .lore("&7Create a private match with",
                      "&7custom settings and cheats.",
                      "",
                      "&eLeft-click to configure!")
                .nbt("menu_action", "custom_game")
                .build());

        // COLLECTION
        inv.setItem(24, new ItemBuilder(Material.DIAMOND)
                .name("&b&lCOLLECTION")
                .lore("&7Browse and equip weapon skins.",
                      "",
                      "&eLeft-click to open!")
                .nbt("menu_action", "skins")
                .build());

        // ── Secondary actions (row 4) ─────────────────────────────────────────

        // CAREER / STATS
        StatsManager.PlayerStats stats = plugin.getStatsManager().getStats(player.getUniqueId());
        double kdr = stats.getKDR();
        double hsPct = stats.getHSPct();
        inv.setItem(29, new ItemBuilder(Material.BOOK)
                .name("&6&lCAREER")
                .lore("&7K/D/A: &f" + stats.kills + "/" + stats.deaths + "/" + stats.assists,
                      "&7KDR:  &f" + String.format("%.2f", kdr),
                      "&7HS%%:  &f" + String.format("%.1f%%", hsPct),
                      "&7Rounds: &f" + stats.roundsWon + "W / " + stats.roundsPlayed + " played",
                      "",
                      "&eLeft-click to view!")
                .nbt("menu_action", "stats")
                .build());

        // AGENT SELECT
        inv.setItem(31, new ItemBuilder(Material.LIGHT_BLUE_CONCRETE)
                .name("&b&lAGENT SELECT")
                .lore("&7Pre-pick your agent.",
                      "&7You can change this inside a match.",
                      "",
                      "&eLeft-click to open!")
                .nbt("menu_action", "agent")
                .build());

        // SETTINGS / ADMIN
        List<String> settingsLore = new ArrayList<>();
        settingsLore.add("&7Server commands:");
        settingsLore.add("&8/valorant setlobby &7— set lobby spawn");
        settingsLore.add("&8/valorant reload &7— reload config");
        if (player.hasPermission("valorantmc.admin")) {
            settingsLore.add("");
            settingsLore.add("&a[Admin access enabled]");
        }
        inv.setItem(33, new ItemBuilder(Material.COMPARATOR)
                .name("&7&lSETTINGS")
                .lore(settingsLore.toArray(new String[0]))
                .nbt("menu_action", "settings")
                .build());

        // ── Active games list (row 5, slots 36-44) ────────────────────────────
        List<com.valorantmc.game.ValorantGame> active = new ArrayList<>(plugin.getGameManager().getAllGames());
        if (active.isEmpty()) {
            inv.setItem(40, new ItemBuilder(Material.GRAY_CONCRETE)
                    .name("&7No active games")
                    .lore("&7Use &eQuick Play &7to start one!")
                    .build());
        } else {
            int slot = 37;
            for (com.valorantmc.game.ValorantGame g : active) {
                if (slot > 43) break;
                int players = g.getAllPlayers().size();
                inv.setItem(slot, new ItemBuilder(Material.GREEN_CONCRETE)
                        .name("&a&lGame: &f" + g.getId())
                        .lore("&7Map: &f" + (g.getMapName() != null ? g.getMapName() : "—"),
                              "&7State: &f" + g.getState(),
                              "&7Players: &f" + players,
                              "",
                              "&eLeft-click to join!")
                        .nbt("menu_action", "join:" + g.getId())
                        .build());
                slot++;
            }
        }

        // ── Bottom bar ────────────────────────────────────────────────────────
        ItemStack gray = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inv.setItem(i, gray);

        // Close
        inv.setItem(49, new ItemBuilder(Material.BARRIER)
                .name("&c&lClose")
                .lore("&7Close this menu.")
                .nbt("menu_action", "close")
                .build());

        return inv;
    }
}
