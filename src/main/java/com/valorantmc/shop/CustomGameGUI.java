package com.valorantmc.shop;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.CustomGameSettings;
import com.valorantmc.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class CustomGameGUI {

    public static final String TITLE = ValorantMC.colorize("&e&lCUSTOM GAME &8— Settings");

    /**
     * Builds the 54-slot custom game settings inventory.
     * Each toggle stores its action key in NBT "cg_action".
     */
    public static Inventory build(Player player, CustomGameSettings s) {
        if (s == null) s = new CustomGameSettings();
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Background
        var bg = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inv.setItem(i, bg);

        // Yellow header bar
        var hdr = new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) inv.setItem(i, hdr);

        // ── Row 2 — Cheat toggles (slots 10-16) ──────────────────────────────
        inv.setItem(10, toggle("Unlimited Abilities",
                "Charges are never consumed.\nCooldowns reset instantly.",
                Material.BLAZE_POWDER, s.unlimitedAbilities, "toggle_unlimited_abilities"));

        inv.setItem(11, toggle("Infinite Credits",
                "Credits never decrease.\nYou can always buy anything.",
                Material.GOLD_INGOT, s.infiniteCredits, "toggle_infinite_credits"));

        inv.setItem(12, toggle("Wallhack",
                "All enemies glow permanently\nthrough walls for your team.",
                Material.ENDER_EYE, s.wallhack, "toggle_wallhack"));

        inv.setItem(13, toggle("One-Shot Mode",
                "Any hit instantly kills.\nIgnores HP and shields.",
                Material.ARROW, s.oneShot, "toggle_one_shot"));

        inv.setItem(14, toggle("Infinite Ammo",
                "Ammo never decreases.\nYou still reload normally.",
                Material.GUNPOWDER, s.infiniteAmmo, "toggle_infinite_ammo"));

        inv.setItem(15, toggle("No Cooldowns",
                "Fire rate is unlimited.\nReload time is skipped.",
                Material.CLOCK, s.noCooldowns, "toggle_no_cooldowns"));

        // ── Row 3 — Match toggles (slots 19-25) ───────────────────────────────
        inv.setItem(19, toggle("Show Enemy HP",
                "After each hit, your team sees\nthe target's remaining HP.",
                Material.REDSTONE, s.showEnemyHP, "toggle_show_hp"));

        inv.setItem(20, toggle("Friendly Fire",
                "Team damage is enabled.\nBe careful!",
                Material.FIRE_CHARGE, s.allowTeamDamage, "toggle_ff"));

        // Ability damage multiplier — numeric
        String dmgPct = String.format("%.0f%%", s.abilityDmgMult * 100);
        inv.setItem(22, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name("&6Ability Damage: &f" + dmgPct)
                .lore("&7Right-click &8+25%  &7Left-click &8-25%",
                      "&8Current: &f" + dmgPct,
                      "&8Range: 25% – 300%")
                .nbt("cg_action", "dmg_mult")
                .build());

        // Starting credits — numeric
        inv.setItem(24, new ItemBuilder(Material.GOLD_NUGGET)
                .name("&6Starting Credits: &f" + s.startingCredits)
                .lore("&7Right-click &8+200  &7Left-click &8-200",
                      "&8Current: &f" + s.startingCredits,
                      "&8Range: 0 – 9000")
                .nbt("cg_action", "start_credits")
                .build());

        // Max rounds — numeric
        String roundsLabel = s.maxRounds == 0 ? "Unlimited" : String.valueOf(s.maxRounds);
        inv.setItem(25, new ItemBuilder(Material.PAPER)
                .name("&6Max Rounds: &f" + roundsLabel)
                .lore("&7Right-click &8+1  &7Left-click &8-1",
                      "&7Set to &80 &7for unlimited (practice)",
                      "&8Current: &f" + roundsLabel)
                .nbt("cg_action", "max_rounds")
                .build());

        // ── Row 5 — Action buttons ────────────────────────────────────────────
        inv.setItem(40, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&lSTART CUSTOM GAME")
                .lore("&7Creates a private game with",
                      "&7these settings applied.",
                      "&7You will be auto-joined as host.",
                      "",
                      "&aLeft-click to start!")
                .nbt("cg_action", "start_custom")
                .build());

        inv.setItem(36, new ItemBuilder(Material.ARROW)
                .name("&7&l← Back to Menu")
                .lore("&7Return to the main menu.")
                .nbt("cg_action", "back")
                .build());

        // Bottom bar
        var gray = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inv.setItem(i, gray);

        return inv;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static org.bukkit.inventory.ItemStack toggle(
            String name, String description, Material iconOn, boolean enabled, String action) {
        Material mat  = enabled ? iconOn : Material.GRAY_CONCRETE;
        String status = enabled ? "&a&lON" : "&c&lOFF";
        String[] descLines = description.split("\n");
        String[] lore = new String[descLines.length + 2];
        for (int i = 0; i < descLines.length; i++) lore[i] = "&7" + descLines[i];
        lore[descLines.length]     = "";
        lore[descLines.length + 1] = "&7Status: " + status + " &8— Click to toggle";
        return new ItemBuilder(mat)
                .name("&f" + name + " &8[" + (enabled ? "&aON" : "&cOFF") + "&8]")
                .lore(lore)
                .nbt("cg_action", action)
                .build();
    }
}
