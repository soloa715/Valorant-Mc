package com.valorantmc.shop;

import com.valorantmc.ValorantMC;
import com.valorantmc.managers.SkinManager;
import com.valorantmc.utils.ItemBuilder;
import com.valorantmc.weapons.Weapon;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 54-slot GUI that lets a player change the skin of any weapon they own.
 *
 * Layout:
 *   Row 0 (slots 0-8):  weapon selector (click a weapon icon to filter)
 *   Rows 1-4:           skins for the currently-selected weapon, or all if none selected
 *   Slot 49:            "Reset to default"
 */
public class SkinGUI {

    public static final String TITLE = ValorantMC.colorize("&8Skin Collection");

    public static Inventory build(Player p, WeaponType filter) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Top row: one icon per weapon type
        int col = 0;
        for (WeaponType t : WeaponType.values()) {
            if (t == WeaponType.KNIFE) continue;
            ItemStack icon = new ItemBuilder(t.getMaterial())
                    .name("&f" + t.getDisplayName())
                    .lore("&7Click to view skins")
                    .customModel(t.getCustomModelId())
                    .build();
            inv.setItem(col, icon);
            col++;
            if (col >= 9) break;
        }

        // Skin grid
        SkinManager skinManager = ValorantMC.getInstance().getSkinManager();
        List<SkinManager.SkinData> skins = filter != null
                ? skinManager.getSkinsForWeapon(filter)
                : skinManager.getAllSkins().stream().toList();

        int slot = 9;
        for (SkinManager.SkinData skin : skins) {
            if (slot >= 45) break;
            boolean owned = skinManager.hasSkin(p.getUniqueId(), skin.id());
            ItemStack item = new ItemBuilder(skin.weaponType().getMaterial())
                    .name((owned ? "&a" : "&8") + skin.displayName())
                    .lore(
                            "&7Collection: &b" + skin.collection(),
                            "&7Tier: &e" + skin.tier().displayName,
                            "&7Weapon: &f" + skin.weaponType().getDisplayName(),
                            "",
                            owned ? "&aClick to equip" : "&cLocked — buy in shop"
                    )
                    .customModel(skin.customModelId())
                    .build();
            inv.setItem(slot++, item);
        }

        // Reset button
        ItemStack reset = new ItemBuilder(Material.BARRIER)
                .name("&cReset to Default Skins")
                .lore("&7Removes all applied skins.")
                .build();
        inv.setItem(49, reset);

        return inv;
    }
}
