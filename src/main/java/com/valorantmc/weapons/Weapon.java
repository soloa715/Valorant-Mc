package com.valorantmc.weapons;

import com.valorantmc.ValorantMC;
import com.valorantmc.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runtime weapon instance held by a player. Tracks current ammo,
 * reload state, and which skin is applied.
 */
public class Weapon {

    public static final NamespacedKey KEY_WEAPON_TYPE    = new NamespacedKey(ValorantMC.getInstance(), "weapon_type");
    public static final NamespacedKey KEY_WEAPON_AMMO    = new NamespacedKey(ValorantMC.getInstance(), "weapon_ammo");
    public static final NamespacedKey KEY_WEAPON_RESERVE = new NamespacedKey(ValorantMC.getInstance(), "weapon_reserve");
    public static final NamespacedKey KEY_WEAPON_SKIN    = new NamespacedKey(ValorantMC.getInstance(), "weapon_skin");
    public static final NamespacedKey KEY_WEAPON_OWNER   = new NamespacedKey(ValorantMC.getInstance(), "weapon_owner");

    private final WeaponType type;
    private int   currentAmmo;
    private int   reserveAmmo;
    private String appliedSkin;
    private boolean reloading;

    public Weapon(WeaponType type) {
        this.type        = type;
        this.currentAmmo = type.isMelee() ? 0 : type.getMagazineSize();
        this.reserveAmmo = type.isMelee() ? 0 : type.getMagazineSize() * 3;
        this.appliedSkin = "default";
        this.reloading   = false;
    }

    // ── Ammo helpers ─────────────────────────────────────────────────────────

    public boolean canShoot() {
        if (type.isMelee()) return !reloading; // melee never needs ammo
        return !reloading && currentAmmo > 0;
    }

    /** Returns true if a bullet was consumed */
    public boolean consumeBullet() {
        if (currentAmmo <= 0) return false;
        currentAmmo--;
        return true;
    }

    /** Reload; returns false if no reserve ammo */
    public boolean reload() {
        if (reloading) return false;
        if (reserveAmmo <= 0) return false;
        if (currentAmmo == type.getMagazineSize()) return false;
        int needed = type.getMagazineSize() - currentAmmo;
        int refill = Math.min(needed, reserveAmmo);
        currentAmmo += refill;
        reserveAmmo -= refill;
        return true;
    }

    public void refillAmmo() {
        currentAmmo = type.getMagazineSize();
        reserveAmmo = type.getMagazineSize() * 3;
    }

    // ── ItemStack representation ──────────────────────────────────────────────

    public ItemStack toItemStack(UUID ownerUUID) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        // Name & lore
        meta.setDisplayName(ValorantMC.colorize(buildDisplayName()));
        meta.setLore(buildLore());

        // Custom model data for resource-pack textures — use skin ID if a skin is applied
        int modelId = type.getCustomModelId();
        if (!"default".equals(appliedSkin)) {
            com.valorantmc.managers.SkinManager.SkinData skin =
                    ValorantMC.getInstance().getSkinManager().getSkin(appliedSkin);
            if (skin != null) modelId = skin.customModelId();
        }
        meta.setCustomModelData(modelId);

        // Unbreakable cosmetic
        meta.setUnbreakable(true);

        // NBT data
        meta.getPersistentDataContainer().set(KEY_WEAPON_TYPE,    PersistentDataType.STRING,  type.name());
        meta.getPersistentDataContainer().set(KEY_WEAPON_AMMO,    PersistentDataType.INTEGER, currentAmmo);
        meta.getPersistentDataContainer().set(KEY_WEAPON_RESERVE, PersistentDataType.INTEGER, reserveAmmo);
        meta.getPersistentDataContainer().set(KEY_WEAPON_SKIN,    PersistentDataType.STRING,  appliedSkin);
        if (ownerUUID != null)
            meta.getPersistentDataContainer().set(KEY_WEAPON_OWNER, PersistentDataType.STRING, ownerUUID.toString());

        item.setItemMeta(meta);
        return item;
    }

    private String buildDisplayName() {
        String color = switch (type.getCategory()) {
            case SIDEARM  -> "&f";
            case SMG      -> "&a";
            case SHOTGUN  -> "&6";
            case RIFLE    -> "&b";
            case SNIPER   -> "&d";
            case HEAVY    -> "&c";
            case MELEE    -> "&7";
        };
        return color + "&l" + type.getDisplayName();
    }

    private List<String> buildLore() {
        List<String> lore = new ArrayList<>();
        lore.add(ValorantMC.colorize("&8" + type.getCategory().getDisplayName()));
        lore.add("");
        lore.add(ValorantMC.colorize("&7Ammo: &f" + currentAmmo + "/" + type.getMagazineSize()
                + " &8(+" + reserveAmmo + ")"));
        lore.add(ValorantMC.colorize("&7Damage: &f" + type.getDamage()));
        lore.add(ValorantMC.colorize("&7Fire Rate: &f" + type.getFireRate() + " rps"));
        if (type.getPellets() > 1)
            lore.add(ValorantMC.colorize("&7Pellets: &f" + type.getPellets()));
        if (type.getCost() > 0)
            lore.add(ValorantMC.colorize("&7Cost: &6" + type.getCost() + "c"));
        else
            lore.add(ValorantMC.colorize("&7Cost: &aFree"));
        if (!appliedSkin.equals("default"))
            lore.add(ValorantMC.colorize("&7Skin: &e" + appliedSkin));
        lore.add("");
        lore.add(ValorantMC.colorize("&8Right-click to shoot"));
        lore.add(ValorantMC.colorize("&8Sneak+Right-click to reload"));
        return lore;
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    /** Returns null if the item is not a ValorantMC weapon */
    public static WeaponType getWeaponType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String val = item.getItemMeta().getPersistentDataContainer()
                .get(KEY_WEAPON_TYPE, PersistentDataType.STRING);
        if (val == null) return null;
        try { return WeaponType.valueOf(val); } catch (Exception e) { return null; }
    }

    public static int getStoredAmmo(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(KEY_WEAPON_AMMO, PersistentDataType.INTEGER);
        return val == null ? -1 : val;
    }

    public static int getStoredReserve(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        Integer val = item.getItemMeta().getPersistentDataContainer()
                .get(KEY_WEAPON_RESERVE, PersistentDataType.INTEGER);
        return val == null ? -1 : val;
    }

    /** Update ammo displayed in item lore/NBT without recreating the full item */
    public static ItemStack updateAmmoDisplay(ItemStack item, int current, int mag, int reserve) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();

        List<String> lore = meta.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Ammo:")) {
                    lore.set(i, ValorantMC.colorize("&7Ammo: &f" + current + "/" + mag
                            + " &8(+" + reserve + ")"));
                    break;
                }
            }
            meta.setLore(lore);
        }

        meta.getPersistentDataContainer().set(KEY_WEAPON_AMMO, PersistentDataType.INTEGER, current);
        item.setItemMeta(meta);
        return item;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public WeaponType getType()         { return type;        }
    public int        getCurrentAmmo()  { return currentAmmo; }
    public int        getReserveAmmo()  { return reserveAmmo; }
    public String     getAppliedSkin()  { return appliedSkin; }
    public boolean    isReloading()     { return reloading;   }
    public void       setReloading(boolean v)  { reloading   = v; }
    public void       setAppliedSkin(String s) { appliedSkin = s; }
    public void       setCurrentAmmo(int v)    { currentAmmo = v; }
    public void       setReserveAmmo(int v)    { reserveAmmo = v; }
}
