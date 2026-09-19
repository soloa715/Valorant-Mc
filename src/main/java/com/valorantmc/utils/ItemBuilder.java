package com.valorantmc.utils;

import com.valorantmc.ValorantMC;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fluent builder for ItemStacks. */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta  meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        meta.setDisplayName(ValorantMC.colorize(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) lore.add(ValorantMC.colorize(line));
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) lore.add(ValorantMC.colorize(line));
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder customModel(int id) {
        meta.setCustomModelData(id);
        return this;
    }

    public ItemBuilder unbreakable() {
        meta.setUnbreakable(true);
        return this;
    }

    public ItemBuilder hideFlags() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder nbt(String key, String value) {
        meta.getPersistentDataContainer()
                .set(new NamespacedKey(ValorantMC.getInstance(), key),
                        PersistentDataType.STRING, value);
        return this;
    }

    public ItemBuilder nbt(String key, int value) {
        meta.getPersistentDataContainer()
                .set(new NamespacedKey(ValorantMC.getInstance(), key),
                        PersistentDataType.INTEGER, value);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
