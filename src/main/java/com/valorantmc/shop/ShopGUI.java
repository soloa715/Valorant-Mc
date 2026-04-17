package com.valorantmc.shop;

import com.valorantmc.ValorantMC;
import com.valorantmc.weapons.WeaponCategory;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 6-row inventory GUI for the buy menu.
 *
 * Layout:
 *   Row 0 – Sidearms
 *   Row 1 – SMGs / Shotguns
 *   Row 2 – Rifles
 *   Row 3 – Snipers / Heavy
 *   Row 4 – Armor / Abilities
 *   Row 5 – Info/Back
 */
public class ShopGUI {

    public static final String TITLE = ValorantMC.colorize("&c&lVALORANT &8— &eBuy Menu");

    public static Inventory build(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        int credits = ValorantMC.getInstance().getEconomyManager().getCredits(player);

        // ── Row 0: Sidearms ───────────────────────────────────────────────────
        addWeapon(inv, 0,  WeaponType.CLASSIC,  credits);
        addWeapon(inv, 1,  WeaponType.SHORTY,   credits);
        addWeapon(inv, 2,  WeaponType.FRENZY,   credits);
        addWeapon(inv, 3,  WeaponType.GHOST,    credits);
        addWeapon(inv, 4,  WeaponType.SHERIFF,  credits);
        addDivider(inv, 5, "&7── Sidearms ──");

        // ── Row 1: SMGs / Shotguns ────────────────────────────────────────────
        addWeapon(inv, 9,  WeaponType.STINGER,  credits);
        addWeapon(inv, 10, WeaponType.SPECTRE,  credits);
        addDivider(inv, 11, "&7── SMGs ──");
        addWeapon(inv, 12, WeaponType.BUCKY,    credits);
        addWeapon(inv, 13, WeaponType.JUDGE,    credits);
        addDivider(inv, 14, "&7── Shotguns ──");

        // ── Row 2: Rifles ─────────────────────────────────────────────────────
        addWeapon(inv, 18, WeaponType.BULLDOG,  credits);
        addWeapon(inv, 19, WeaponType.GUARDIAN, credits);
        addWeapon(inv, 20, WeaponType.PHANTOM,  credits);
        addWeapon(inv, 21, WeaponType.VANDAL,   credits);
        addDivider(inv, 22, "&7── Rifles ──");

        // ── Row 3: Snipers / Heavy ────────────────────────────────────────────
        addWeapon(inv, 27, WeaponType.MARSHAL,  credits);
        addWeapon(inv, 28, WeaponType.OUTLAW,   credits);
        addWeapon(inv, 29, WeaponType.OPERATOR, credits);
        addDivider(inv, 30, "&7── Snipers ──");
        addWeapon(inv, 31, WeaponType.ARES,     credits);
        addWeapon(inv, 32, WeaponType.ODIN,     credits);
        addDivider(inv, 33, "&7── Heavy ──");

        // ── Row 4: Armor / Abilities ──────────────────────────────────────────
        addArmor(inv, 36, "Light Shield",  400, Material.LEATHER_CHESTPLATE, credits, "light_shield");
        addArmor(inv, 37, "Heavy Shield", 1000, Material.IRON_CHESTPLATE,    credits, "heavy_shield");
        addDivider(inv, 38, "&7── Armor ──");

        addAbilityBuy(inv, 39, 'C', player);
        addAbilityBuy(inv, 40, 'Q', player);
        addAbilityBuy(inv, 41, 'E', player);
        addDivider(inv, 42, "&7── Abilities ──");

        // ── Row 5: Credits display ────────────────────────────────────────────
        ItemStack creditsDisplay = buildItem(Material.GOLD_INGOT,
                ValorantMC.colorize("&6Credits: &f" + credits),
                List.of(ValorantMC.colorize("&7Your available credits this round.")));
        inv.setItem(49, creditsDisplay);

        // Fill empty slots with glass
        ItemStack filler = buildItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }

        return inv;
    }

    private static void addWeapon(Inventory inv, int slot, WeaponType type, int playerCredits) {
        boolean canAfford = playerCredits >= type.getCost();
        List<String> lore = new ArrayList<>();
        lore.add(ValorantMC.colorize("&8" + type.getCategory().getDisplayName()));
        lore.add("");
        lore.add(ValorantMC.colorize("&7Damage:    &f" + type.getDamage()));
        lore.add(ValorantMC.colorize("&7Fire Rate:  &f" + type.getFireRate() + " rps"));
        lore.add(ValorantMC.colorize("&7Magazine:   &f" + type.getMagazineSize()));
        if (type.getPellets() > 1)
            lore.add(ValorantMC.colorize("&7Pellets:    &f" + type.getPellets()));
        lore.add("");
        if (type.isFree()) {
            lore.add(ValorantMC.colorize("&aFree!"));
        } else {
            lore.add(ValorantMC.colorize((canAfford ? "&a" : "&c") + "Cost: &6" + type.getCost() + "c"));
        }
        lore.add("");
        lore.add(ValorantMC.colorize(canAfford ? "&eClick to buy!" : "&cInsufficient credits"));

        String name = (canAfford ? "&f" : "&8") + "&l" + type.getDisplayName();
        ItemStack item = buildItem(type.getMaterial(), ValorantMC.colorize(name), lore);

        // Store weapon id in NBT
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "shop_weapon"),
                org.bukkit.persistence.PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private static void addArmor(Inventory inv, int slot, String name, int cost,
                                  Material mat, int credits, String id) {
        boolean canAfford = credits >= cost;
        List<String> lore = List.of(
                ValorantMC.colorize("&7Provides a shield that absorbs damage."),
                "",
                ValorantMC.colorize((canAfford ? "&a" : "&c") + "Cost: &6" + cost + "c"),
                "",
                ValorantMC.colorize(canAfford ? "&eClick to buy!" : "&cInsufficient credits")
        );
        ItemStack item = buildItem(mat, ValorantMC.colorize("&f&l" + name), new ArrayList<>(lore));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "shop_armor"),
                org.bukkit.persistence.PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private static void addAbilityBuy(Inventory inv, int slot, char key, Player player) {
        com.valorantmc.game.ValorantGame game =
                ValorantMC.getInstance().getGameManager().getGame(player);
        if (game == null) return;
        com.valorantmc.agents.Agent agent = game.getAgent(player);
        if (agent == null) return;

        com.valorantmc.agents.Agent.Ability ability = switch (key) {
            case 'C' -> agent.getAbilityC();
            case 'Q' -> agent.getAbilityQ();
            case 'E' -> agent.getAbilityE();
            default  -> null;
        };
        if (ability == null) return;

        boolean canAfford = ValorantMC.getInstance().getEconomyManager().canAfford(player, ability.cost);
        List<String> lore = new ArrayList<>();
        lore.add(ValorantMC.colorize("&7Ability [" + key + "]: &b" + agent.getDisplayName()));
        lore.add(ValorantMC.colorize("&7Charges: &f" + ability.getCurrentCharges() + "/" + ability.charges));
        lore.add("");
        if (ability.cost == 0) {
            lore.add(ValorantMC.colorize("&aFree signature ability"));
        } else {
            lore.add(ValorantMC.colorize((canAfford ? "&a" : "&c") + "Cost: &6" + ability.cost + "c/charge"));
        }
        lore.add("");
        lore.add(ValorantMC.colorize(canAfford || ability.cost == 0 ? "&eClick to buy!" : "&cInsufficient credits"));

        ItemStack item = buildItem(
                ability.cost == 0 ? Material.LIME_DYE : Material.YELLOW_DYE,
                ValorantMC.colorize("&f&l[" + key + "] " + ability.name),
                lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "shop_ability"),
                org.bukkit.persistence.PersistentDataType.STRING, String.valueOf(key));
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private static void addDivider(Inventory inv, int slot, String label) {
        inv.setItem(slot, buildItem(Material.GRAY_STAINED_GLASS_PANE,
                ValorantMC.colorize(label), List.of()));
    }

    private static ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Get the weapon type associated with a shop item, or null */
    public static WeaponType getWeaponFromShopItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "shop_weapon"),
                        org.bukkit.persistence.PersistentDataType.STRING);
        if (val == null) return null;
        try { return WeaponType.valueOf(val); } catch (Exception e) { return null; }
    }

    /** Get armor ID from shop item */
    public static String getArmorFromShopItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "shop_armor"),
                        org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Get ability key from shop item */
    public static Character getAbilityKeyFromShopItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "shop_ability"),
                        org.bukkit.persistence.PersistentDataType.STRING);
        if (val == null || val.isEmpty()) return null;
        return val.charAt(0);
    }
}
