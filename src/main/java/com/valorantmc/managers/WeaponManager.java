package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.CustomGameSettings;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.game.ValorantTeam;
import com.valorantmc.weapons.Weapon;
import com.valorantmc.weapons.WeaponCategory;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Manages runtime weapon instances and per-player state:
 *   – Current weapon held
 *   – Ammo (current + reserve)
 *   – Reload timers
 *   – Shoot cooldowns
 */
public class WeaponManager {

    private final ValorantMC plugin;

    // Per-player state
    private final Map<UUID, Weapon>  heldWeapons    = new HashMap<>();
    private final Map<UUID, Long>    shootCooldown  = new HashMap<>();   // System.currentTimeMillis
    private final Map<UUID, Long>    reloadEnd      = new HashMap<>();   // System.currentTimeMillis
    private final Map<UUID, Integer> shotCount      = new HashMap<>();   // consecutive shots
    private final Map<UUID, Long>    lastMovedTime  = new HashMap<>();   // for movement accuracy

    public WeaponManager(ValorantMC plugin) {
        this.plugin = plugin;
    }

    // ── Weapon retrieval ─────────────────────────────────────────────────────

    /** Create a fresh weapon instance of the given type */
    public Weapon createWeapon(WeaponType type) {
        return new Weapon(type);
    }

    /** Give a weapon item to a player's current hotbar slot */
    public void giveWeapon(Player p, WeaponType type, boolean addToInventory) {
        Weapon weapon = createWeapon(type);
        heldWeapons.put(p.getUniqueId(), weapon);
        if (addToInventory) {
            int slot = p.getInventory().getHeldItemSlot();
            giveTaCZWeapon(p, type, slot);
        }
    }

    public static String getTaCZGunId(WeaponType type) {
        return switch (type) {
            case CLASSIC  -> "valorant:classic";
            case SHORTY   -> "tacz:db_short";
            case FRENZY   -> "tacz:cz75";
            case GHOST    -> "valorant:ghost";
            case SHERIFF  -> "valorant:sheriff";
            case STINGER  -> "tacz:vector45";
            case SPECTRE  -> "tacz:hk_mp5a5";
            case BUCKY    -> "tacz:m870";
            case JUDGE    -> "valorant:judge";
            case BULLDOG  -> "tacz:fn_fal";
            case GUARDIAN -> "tacz:mk14";
            case PHANTOM  -> "valorant:phantom";
            case VANDAL   -> "valorant:vandal";
            case MARSHAL  -> "tacz:m700";
            case OUTLAW   -> "valorant:outlaw";
            case OPERATOR -> "valorant:operator";
            case ARES     -> "tacz:rpk";
            case ODIN     -> "valorant:odin";
            case KNIFE    -> "wtyj:eternal_karambit";
        };
    }

    public static String getTaCZAmmoId(WeaponType type) {
        return switch (type) {
            case CLASSIC, FRENZY, STINGER, SPECTRE -> "tacz:9mm";
            case GHOST                             -> "tacz:45acp";
            case SHERIFF                           -> "tacz:357mag";
            case SHORTY, BUCKY, JUDGE, OUTLAW      -> "tacz:12g";
            case BULLDOG, GUARDIAN, VANDAL         -> "tacz:762x39";
            case PHANTOM                           -> "tacz:556x45";
            case MARSHAL                           -> "tacz:308";
            case OPERATOR                          -> "tacz:338";
            case ARES, ODIN                        -> "tacz:762x54";
            case KNIFE                             -> "tacz:9mm";
        };
    }

    public static ItemStack createTaCZItem(String gunId, String skinId, String displayName, List<String> lore) {
        Material mat = Material.matchMaterial("tacz:modern_kinetic_gun");
        if (mat == null) {
            mat = Material.IRON_SWORD;
        }
        ItemStack item = new ItemStack(mat);
        StringBuilder nbt = new StringBuilder("{GunId:\"").append(gunId).append("\"");
        if (skinId != null && !skinId.isEmpty()) {
            nbt.append(",Skin:\"").append(skinId).append("\"");
        }
        nbt.append(",HasBulletInBarrel:1b,GunCurrentAmmoCount:1,DummyAmmo:0}");
        try {
            item = org.bukkit.Bukkit.getUnsafe().modifyItemStack(item, nbt.toString());
        } catch (Throwable ignored) {}

        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null && !displayName.isEmpty()) {
                meta.setDisplayName(ValorantMC.colorize(displayName));
            }
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String l : lore) colored.add(ValorantMC.colorize(l));
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void giveTaCZWeapon(Player p, WeaponType type, int slot) {
        if (type.isMelee()) {
            String knifeGunId = "wtyj:eternal_karambit";
            int customModelData = type.getCustomModelId();
            if (plugin.getSkinManager() != null) {
                String equipped = plugin.getSkinManager().getEquippedSkin(p.getUniqueId(), WeaponType.KNIFE);
                if (equipped != null) {
                    SkinManager.SkinData sd = plugin.getSkinManager().getSkin(equipped);
                    if (sd != null) {
                        if (sd.taczGunId() != null) knifeGunId = sd.taczGunId();
                        if (sd.customModelId() > 0) customModelData = sd.customModelId();
                    }
                }
            }

            String nbt = "{MeleeWeaponId:\"" + knifeGunId + "\",CustomModelData:" + customModelData + ",PublicBukkitValues:{\"valorantmc:weapon_type\":\"KNIFE\"}}";
            String cmd = "item replace entity " + p.getName() + " hotbar." + slot + " with lrtactical:melee" + nbt + " 1";
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
            return;
        }

        String gunId = getTaCZGunId(type);
        String skinTag = "";
        int customModelData = type.getCustomModelId();
        String skinDisplayName = type.getDisplayName();
        if (plugin.getSkinManager() != null) {
            String equipped = plugin.getSkinManager().getEquippedSkin(p.getUniqueId(), type);
            if (equipped != null) {
                SkinManager.SkinData sd = plugin.getSkinManager().getSkin(equipped);
                if (sd != null) {
                    if (sd.displayName() != null) {
                        skinDisplayName = sd.displayName();
                    }
                    if (sd.customModelId() > 0) {
                        customModelData = sd.customModelId();
                    }
                    if (sd.taczSkinId() != null && !sd.taczSkinId().isEmpty() && !sd.taczSkinId().startsWith("valorant:")) {
                        skinTag = ",Skin:\"" + sd.taczSkinId() + "\"";
                    }
                }
            }
        }

        int mag = Math.max(1, type.getMagazineSize());
        int reserve = mag * 4;

        if (customModelData != type.getCustomModelId()) {
            // Player equipped a custom skin! Give item with CustomModelData for authentic skin texture
            String matName = type.getMaterial().name().toLowerCase();
            String nbt = "{CustomModelData:" + customModelData
                    + ",GunId:\"" + gunId + "\""
                    + ",HasBulletInBarrel:1b,GunCurrentAmmoCount:" + mag
                    + ",DummyAmmo:" + reserve + ",MaxDummyAmmo:" + (mag * 10)
                    + ",display:{Name:'{\"text\":\"" + skinDisplayName + "\",\"color\":\"yellow\",\"italic\":false}'}"
                    + ",PublicBukkitValues:{\"valorantmc:weapon_type\":\"" + type.name() + "\",\"valorantmc:weapon_ammo\":" + mag + ",\"valorantmc:weapon_reserve\":" + reserve + "}}";
            String cmd = "item replace entity " + p.getName() + " hotbar." + slot + " with minecraft:" + matName + nbt + " 1";
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
        } else {
            // Default skin -> give TACZ 3D gun
            String nbt = "{GunId:\"" + gunId + "\"" + skinTag
                    + ",HasBulletInBarrel:1b,GunCurrentAmmoCount:" + mag
                    + ",DummyAmmo:" + reserve + ",MaxDummyAmmo:" + (mag * 10)
                    + ",PublicBukkitValues:{\"valorantmc:weapon_type\":\"" + type.name() + "\"}}";
            String cmd = "item replace entity " + p.getName() + " hotbar." + slot + " with tacz:modern_kinetic_gun" + nbt + " 1";
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
        }

        // Put matching ammo in upper inventory (container.0 or container.1), never in hotbar!
        String ammoId = getTaCZAmmoId(type);
        int containerSlot = (type.getCategory() == com.valorantmc.weapons.WeaponCategory.SIDEARM) ? 1 : 0;
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                "item replace entity " + p.getName() + " container." + containerSlot + " with tacz:ammo{AmmoId:\"" + ammoId + "\"} 64");
    }

    // ── Shooting ─────────────────────────────────────────────────────────────

    /**
     * Attempt to shoot.
     * @return true if shot was fired, false if blocked (cooldown, no ammo, reloading)
     */
    public boolean tryShoot(Player p, Weapon weapon) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        // Custom game overrides
        com.valorantmc.game.CustomGameSettings cs = getCustomSettings(p);
        boolean infAmmo      = cs != null && cs.infiniteAmmo;
        boolean noCooldowns  = cs != null && cs.noCooldowns;

        // Reload check (skip if noCooldowns)
        if (!noCooldowns && reloadEnd.containsKey(uuid) && now < reloadEnd.get(uuid)) {
            return false;
        }

        // Cooldown check (skip if noCooldowns)
        if (!noCooldowns) {
            long cooldownMs = (long) (1000.0 / weapon.getType().getFireRate());
            if (shootCooldown.containsKey(uuid) && now - shootCooldown.get(uuid) < cooldownMs) {
                return false;
            }
        }

        // Ammo check (skip if infiniteAmmo)
        if (!infAmmo && !weapon.canShoot()) {
            if (weapon.getCurrentAmmo() == 0) {
                p.sendMessage(plugin.msg("weapons.no-ammo"));
                tryReload(p, weapon);
            }
            return false;
        }

        if (!infAmmo) weapon.consumeBullet();
        shootCooldown.put(uuid, now);
        updateHeldItem(p, weapon);
        return true;
    }

    /** Returns the CustomGameSettings for the game the player is in, or null. */
    private com.valorantmc.game.CustomGameSettings getCustomSettings(Player p) {
        com.valorantmc.game.ValorantGame g = plugin.getGameManager().getGame(p);
        return g == null ? null : g.getCustomSettings();
    }

    // ── Reloading ─────────────────────────────────────────────────────────────

    public boolean tryReload(Player p, Weapon weapon) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (weapon.getType().isMelee()) return false; // melee can't reload
        if (reloadEnd.containsKey(uuid) && now < reloadEnd.get(uuid)) return false;
        if (weapon.isReloading()) return false;
        if (weapon.getCurrentAmmo() >= weapon.getType().getMagazineSize()) {
            ValorantMC.sendActionBar(p, "&eAmmo is full!");
            return false;
        }
        if (weapon.getReserveAmmo() <= 0) {
            ValorantMC.sendActionBar(p, "&cNo reserve ammo!");
            return false;
        }

        long reloadMs   = (long) (weapon.getType().getReloadTime() * 1000);
        long reloadTicks = weapon.getType().getReloadTicks();
        reloadEnd.put(uuid, now + reloadMs);
        weapon.setReloading(true);

        // Reload sound
        p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 0.8f, 1.5f);
        ValorantMC.sendActionBar(p, "&6Reloading &f" + weapon.getType().getDisplayName() + "&8...");

        // Action bar countdown while reloading
        final long endTime = now + reloadMs;
        final int totalTicks = (int) reloadTicks;
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline() || !weapon.isReloading()) { task.cancel(); return; }
            long remaining = endTime - System.currentTimeMillis();
            if (remaining <= 0) { task.cancel(); return; }
            float pct = (float) remaining / reloadMs;
            int filled = (int)((1f - pct) * 10);
            String bar = "&a" + "█".repeat(filled) + "&8" + "█".repeat(10 - filled);
            ValorantMC.sendActionBar(p, "&6Reloading &f" + weapon.getType().getDisplayName() + " " + bar);
        }, 4L, 4L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            // Validate this weapon is still the one being reloaded (player didn't switch)
            Weapon current = heldWeapons.get(p.getUniqueId());
            if (current != weapon) {
                weapon.setReloading(false);
                return;
            }
            if (weapon.isReloading()) {
                weapon.reload();
                weapon.setReloading(false);
                reloadEnd.remove(uuid);
                updateHeldItem(p, weapon);
                // Reload-complete sound + action bar
                p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_CROSSBOW_LOADING_END, 0.9f, 1.6f);
                ValorantMC.sendActionBar(p, "&aReloaded! &f" + weapon.getCurrentAmmo() + "/" + weapon.getType().getMagazineSize());
            }
        }, reloadTicks);

        return true;
    }

    public void cancelReload(Player p, Weapon weapon) {
        weapon.setReloading(false);
        reloadEnd.remove(p.getUniqueId());
    }

    // ── Skin application ─────────────────────────────────────────────────────

    public void applySkin(Player p, Weapon weapon, String skinName) {
        weapon.setAppliedSkin(skinName);
        updateHeldItem(p, weapon);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Re-write the held item so ammo display stays current */
    public void updateHeldItem(Player p, Weapon weapon) {
        // Disabled since TaCZ handles item state natively
    }

    /** Refill all ammo for a player (called on round start) */
    public void refillAmmo(Player p) {
        Weapon w = heldWeapons.get(p.getUniqueId());
        if (w != null) {
            w.refillAmmo();
            updateHeldItem(p, w);
        }
    }

    /** Clear all state for a player (on leave / game end) */
    public void clearPlayer(Player p) {
        UUID uuid = p.getUniqueId();
        heldWeapons.remove(uuid);
        shootCooldown.remove(uuid);
        reloadEnd.remove(uuid);
        shotCount.remove(uuid);
        lastMovedTime.remove(uuid);
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public Weapon getHeldWeapon(Player p) { return heldWeapons.get(p.getUniqueId()); }
    public void   setHeldWeapon(Player p, Weapon w) {
        if (w == null) heldWeapons.remove(p.getUniqueId());
        else           heldWeapons.put(p.getUniqueId(), w);
    }
    public int    getWeaponCount() { return WeaponType.values().length; }

    public boolean isReloading(Player p) {
        long now = System.currentTimeMillis();
        return reloadEnd.containsKey(p.getUniqueId()) && now < reloadEnd.get(p.getUniqueId());
    }

    // ── Spray / recoil tracking ───────────────────────────────────────────────

    /** Record a shot — increments shot counter, resets after 600ms gap */
    public void recordShot(Player p) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastShot = shootCooldown.get(uuid);
        // Reset counter if more than 600ms since last shot (burst break)
        if (lastShot == null || now - lastShot > 600) {
            shotCount.put(uuid, 1);
        } else {
            shotCount.merge(uuid, 1, Integer::sum);
        }
    }

    public int getShotCount(Player p) {
        return shotCount.getOrDefault(p.getUniqueId(), 0);
    }

    /** Record player movement for accuracy penalty */
    public void recordMove(Player p) {
        lastMovedTime.put(p.getUniqueId(), System.currentTimeMillis());
    }

    /** Returns true if the player moved within the given threshold (ms) */
    public boolean isMoving(Player p, long thresholdMs) {
        Long last = lastMovedTime.get(p.getUniqueId());
        if (last == null) return false;
        return System.currentTimeMillis() - last < thresholdMs;
    }

    /** Compute the effective spread for the current shot */
    public float getEffectiveSpread(Player p, Weapon weapon, boolean enableRecoil) {
        float spread = weapon.getType().getBaseSpread();
        if (enableRecoil) {
            spread += weapon.getType().getRecoilPerShot() * Math.min(getShotCount(p), 10);
        }
        // Movement penalty — 2.5x spread if moved in last 800ms
        if (isMoving(p, 800)) {
            spread *= 2.5f;
        }
        // Crouch bonus — 0.6x if sneaking
        if (p.isSneaking()) {
            spread *= 0.6f;
        }
        return spread;
    }
}
