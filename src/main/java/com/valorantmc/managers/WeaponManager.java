package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import com.valorantmc.weapons.Weapon;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.entity.Player;

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
            p.getInventory().setItem(slot, weapon.toItemStack(p.getUniqueId()));
        }
    }

    // ── Shooting ─────────────────────────────────────────────────────────────

    /**
     * Attempt to shoot.
     * @return true if shot was fired, false if blocked (cooldown, no ammo, reloading)
     */
    public boolean tryShoot(Player p, Weapon weapon) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        // Reload check
        if (reloadEnd.containsKey(uuid) && now < reloadEnd.get(uuid)) {
            return false;
        }

        // Cooldown check
        long cooldownMs = (long) (1000.0 / weapon.getType().getFireRate());
        if (shootCooldown.containsKey(uuid) && now - shootCooldown.get(uuid) < cooldownMs) {
            return false;
        }

        // Ammo check
        if (!weapon.canShoot()) {
            if (weapon.getCurrentAmmo() == 0) {
                p.sendMessage(plugin.msg("weapons.no-ammo"));
                tryReload(p, weapon);
            }
            return false;
        }

        weapon.consumeBullet();
        shootCooldown.put(uuid, now);
        updateHeldItem(p, weapon);
        return true;
    }

    // ── Reloading ─────────────────────────────────────────────────────────────

    public boolean tryReload(Player p, Weapon weapon) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (weapon.getType().isMelee()) return false; // melee can't reload
        if (reloadEnd.containsKey(uuid) && now < reloadEnd.get(uuid)) return false;
        if (weapon.isReloading()) return false;
        if (weapon.getCurrentAmmo() >= weapon.getType().getMagazineSize()) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("§eAmmo is full!"));
            return false;
        }
        if (weapon.getReserveAmmo() <= 0) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("§cNo reserve ammo!"));
            return false;
        }

        long reloadMs   = (long) (weapon.getType().getReloadTime() * 1000);
        long reloadTicks = weapon.getType().getReloadTicks();
        reloadEnd.put(uuid, now + reloadMs);
        weapon.setReloading(true);

        // Reload sound
        p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 0.8f, 1.5f);
        p.sendActionBar(net.kyori.adventure.text.Component.text(
                "§6Reloading §f" + weapon.getType().getDisplayName() + "§8..."));

        // Action bar countdown while reloading
        final long endTime = now + reloadMs;
        final int totalTicks = (int) reloadTicks;
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline() || !weapon.isReloading()) { task.cancel(); return; }
            long remaining = endTime - System.currentTimeMillis();
            if (remaining <= 0) { task.cancel(); return; }
            float pct = (float) remaining / reloadMs;
            int filled = (int)((1f - pct) * 10);
            String bar = "§a" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled);
            p.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§6Reloading §f" + weapon.getType().getDisplayName() + " " + bar));
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
                p.sendActionBar(net.kyori.adventure.text.Component.text(
                        "§aReloaded! §f" + weapon.getCurrentAmmo() + "/"
                        + weapon.getType().getMagazineSize()));
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
        p.getInventory().setItem(p.getInventory().getHeldItemSlot(),
                weapon.toItemStack(p.getUniqueId()));
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
