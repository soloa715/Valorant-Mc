package com.valorantmc.shop;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.GameState;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Pre-game lobby GUI. Players open this via /valorant play — shows the list
 * of live games, lets them join or (if admin) create + start a new one.
 */
public class LobbyGUI {

    public static final String TITLE = ValorantMC.colorize("&c&lVALORANT &7Lobby");

    public static final String NBT_KEY = "lobby_action";

    public static Inventory build(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Filler
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // "Quick Play" button
        inv.setItem(11, new ItemBuilder(Material.LIME_CONCRETE)
                .name("&a&lQUICK PLAY")
                .lore(
                        "&7Join or start a new match instantly.",
                        "&7The next available game opens for you.",
                        "",
                        "&eClick to play!"
                )
                .nbt("lobby_action", "quickplay")
                .build());

        // Agent select shortcut
        inv.setItem(13, new ItemBuilder(Material.LIGHT_BLUE_CONCRETE)
                .name("&b&lPick Agent")
                .lore("&7Choose your agent before the round.")
                .nbt("lobby_action", "agent")
                .build());

        // Skins
        inv.setItem(15, new ItemBuilder(Material.DIAMOND)
                .name("&d&lCollection")
                .lore("&7Browse and equip weapon skins.")
                .nbt("lobby_action", "skins")
                .build());

        // Stats
        inv.setItem(22, new ItemBuilder(Material.BOOK)
                .name("&e&lYour Stats")
                .lore("&7Kills, deaths, headshots, win-rate.")
                .nbt("lobby_action", "stats")
                .build());

        return inv;
    }
}
