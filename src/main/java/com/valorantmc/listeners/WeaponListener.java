package com.valorantmc.listeners;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.game.ValorantTeam;
import com.valorantmc.weapons.Weapon;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.Random;

/**
 * Handles all weapon shooting, reloading, melee, and hit registration.
 */
public class WeaponListener implements Listener {

    private final ValorantMC plugin;
    private final Random random = new Random();

    public WeaponListener(ValorantMC plugin) {
        this.plugin = plugin;
    }

    // ── Click to shoot (left or right), sneak+right to reload ────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        Action a = e.getAction();
        boolean isLeft  = a == Action.LEFT_CLICK_AIR  || a == Action.LEFT_CLICK_BLOCK;
        boolean isRight = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (!isLeft && !isRight) return;

        ValorantGame game = plugin.getGameManager().getGame(player);
        if (game == null) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        WeaponType type = Weapon.getWeaponType(held);
        if (type == null) return;

        // Ability items are handled by AbilityListener
        if (plugin.getAbilityManager().isAbilityItem(held)) return;
        // Spike handled by AbilityListener
        if (plugin.getAbilityManager().isSpikeItem(held)) return;

        e.setCancelled(true);

        // Sneak + right-click = reload (legacy shortcut)
        if (isRight && player.isSneaking()) {
            Weapon weapon = plugin.getWeaponManager().getHeldWeapon(player);
            if (weapon == null) {
                weapon = weaponFromItem(held, type);
                plugin.getWeaponManager().setHeldWeapon(player, weapon);
            }
            plugin.getWeaponManager().tryReload(player, weapon);
            return;
        }

        // Either left-click OR right-click = shoot (no ADS in this build)
        Weapon weapon = plugin.getWeaponManager().getHeldWeapon(player);
        if (weapon == null || weapon.getType() != type) {
            weapon = weaponFromItem(held, type);
            plugin.getWeaponManager().setHeldWeapon(player, weapon);
        }

        if (!plugin.getWeaponManager().tryShoot(player, weapon)) {
            if (weapon.getCurrentAmmo() == 0 && !weapon.getType().isMelee()) {
                player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 2.0f);
            }
            return;
        }

        // ── Fire! ─────────────────────────────────────────────────────────────
        fireWeapon(player, weapon, game);
    }

    // ── F-key (swap hands) = reload, without requiring the companion mod ─────
    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Player player = e.getPlayer();
        if (plugin.getGameManager().getGame(player) == null) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        WeaponType type = Weapon.getWeaponType(held);
        if (type == null || type.isMelee()) return;

        e.setCancelled(true);
        Weapon weapon = plugin.getWeaponManager().getHeldWeapon(player);
        if (weapon == null || weapon.getType() != type) {
            weapon = weaponFromItem(held, type);
            plugin.getWeaponManager().setHeldWeapon(player, weapon);
        }
        plugin.getWeaponManager().tryReload(player, weapon);
    }

    /** Build a Weapon and seed its ammo/reserve from the item's NBT (if any). */
    private Weapon weaponFromItem(ItemStack item, WeaponType type) {
        Weapon w = new Weapon(type);
        int storedAmmo    = Weapon.getStoredAmmo(item);
        int storedReserve = Weapon.getStoredReserve(item);
        if (storedAmmo    >= 0) w.setCurrentAmmo(storedAmmo);
        if (storedReserve >= 0) w.setReserveAmmo(storedReserve);
        return w;
    }

    private void fireWeapon(Player player, Weapon weapon, ValorantGame game) {
        WeaponType type = weapon.getType();

        if (type.isMelee()) return; // melee handled in EntityDamageByEntity

        if (type.isSniper()) {
            // Use arrow projectile for snipers
            Arrow arrow = player.launchProjectile(Arrow.class);
            arrow.setDamage(type.getDamage() / 5.0);  // MC damage normalised
            arrow.setMetadata("valorant_weapon",
                    new org.bukkit.metadata.FixedMetadataValue(plugin, type.name()));
        } else {
            // Raycast for instant-hit weapons
            for (int pellet = 0; pellet < type.getPellets(); pellet++) {
                doRaycastShot(player, weapon, game);
            }
        }

        // Muzzle flash particles
        Location muzzle = player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.2));
        player.getWorld().spawnParticle(Particle.CRIT, muzzle, 3, 0.05, 0.05, 0.05, 0.3);

        // Gunshot sound
        Sound sound = getGunSound(type);
        player.getWorld().playSound(player.getLocation(), sound, 1f, getPitch(type));

        // Shell casing particles
        player.getWorld().spawnParticle(Particle.ITEM,
                player.getEyeLocation().add(player.getLocation().getDirection().rotateAroundY(1.5)),
                1, 0, 0, 0, 0.1, new ItemStack(Material.GOLD_NUGGET));
    }

    private void doRaycastShot(Player player, Weapon weapon, ValorantGame game) {
        WeaponType type = weapon.getType();

        // Apply spray spread
        boolean enableRecoil = plugin.getConfig().getBoolean("weapons.enable-recoil", true);
        boolean enableSpray  = plugin.getConfig().getBoolean("weapons.enable-spray-patterns", true);
        org.bukkit.util.Vector dir = player.getLocation().getDirection();
        if (enableSpray || enableRecoil) {
            plugin.getWeaponManager().recordShot(player);
            float spread = plugin.getWeaponManager().getEffectiveSpread(player, weapon, enableRecoil);
            if (spread > 0) {
                dir.add(new org.bukkit.util.Vector(
                        (random.nextDouble() - 0.5) * 2 * spread,
                        (random.nextDouble() - 0.5) * 2 * spread,
                        (random.nextDouble() - 0.5) * 2 * spread)).normalize();
            }
            // Camera kick (recoil) — rotate pitch upward without teleporting
            if (enableRecoil && weapon.getType().getRecoilPerShot() > 0) {
                float kick = weapon.getType().getRecoilPerShot() * 7f;
                float newPitch = Math.max(-89f, player.getLocation().getPitch() - kick);
                player.setRotation(player.getLocation().getYaw(), newPitch);
            }
        }

        double range = type.getMaxRange();
        RayTraceResult result = player.getWorld().rayTrace(
                player.getEyeLocation(), dir, range,
                FluidCollisionMode.NEVER, true, 0.2,
                e -> e instanceof Player && !e.equals(player));

        if (result == null) return;

        if (result.getHitEntity() instanceof Player target) {
            // Team check
            ValorantTeam shooterTeam = game.getTeam(player);
            ValorantTeam targetTeam  = game.getTeam(target);
            if (shooterTeam == null || targetTeam == null) return;
            if (shooterTeam.getSide() == targetTeam.getSide()) return; // friendly fire off

            boolean isHeadshot = isHeadshotHit(player, target, result);
            boolean isLegshot  = isLegshotHit(player, target, result);

            game.applyDamage(player, target, type.getDamage(), isHeadshot, isLegshot);

        } else if (result.getHitBlock() != null && type.penetrates()) {
            // Wall penetration: continue ray slightly past the block
            org.bukkit.util.Vector penetrateDir = dir.normalize().multiply(1.5);
            RayTraceResult result2 = player.getWorld().rayTrace(
                    result.getHitPosition().toLocation(player.getWorld()).add(penetrateDir),
                    dir, 30, FluidCollisionMode.NEVER, true, 0.2,
                    e -> e instanceof Player && !e.equals(player));
            if (result2 != null && result2.getHitEntity() instanceof Player target) {
                ValorantTeam t1 = game.getTeam(player);
                ValorantTeam t2 = game.getTeam(target);
                if (t1 != null && t2 != null && t1.getSide() != t2.getSide()) {
                    int reducedDamage = (int) (type.getDamage() * 0.7); // wall penalty
                    game.applyDamage(player, target, reducedDamage, false, false);
                }
            }
        }

        // Impact particles on block
        if (result.getHitBlock() != null) {
            Location impact = result.getHitPosition().toLocation(player.getWorld());
            player.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, impact, 6, 0, 0, 0, 0.1,
                    result.getHitBlock().getBlockData());
        }
    }

    // ── Arrow hit (snipers) ──────────────────────────────────────────────────

    @EventHandler
    public void onArrowHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        if (!arrow.hasMetadata("valorant_weapon")) return;

        e.setCancelled(true); // prevent default MC damage
        arrow.remove();

        if (!(e.getHitEntity() instanceof Player target)) return;

        String typeName = arrow.getMetadata("valorant_weapon").get(0).asString();
        WeaponType type;
        try { type = WeaponType.valueOf(typeName); } catch (Exception ex) { return; }

        ValorantGame game = plugin.getGameManager().getGame(shooter);
        if (game == null) return;

        ValorantTeam st = game.getTeam(shooter);
        ValorantTeam tt = game.getTeam(target);
        if (st == null || tt == null || st.getSide() == tt.getSide()) return;

        // Sniper headshots: check Y-distance to eye
        double targetEyeY = target.getEyeLocation().getY();
        double arrowY     = arrow.getLocation().getY();
        boolean isHead    = Math.abs(arrowY - targetEyeY) < 0.3;
        boolean isLeg     = arrowY < target.getLocation().getY() + 0.5;

        game.applyDamage(shooter, target, type.getDamage(), isHead, isLeg);
    }

    // ── Cancel default MC melee damage ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player player)) return;
        if (!(e.getEntity() instanceof Player target)) return;

        ValorantGame game = plugin.getGameManager().getGame(player);
        if (game == null) return;

        e.setCancelled(true); // we handle all damage ourselves

        WeaponType heldType = Weapon.getWeaponType(player.getInventory().getItemInMainHand());
        if (heldType == null) return;

        ValorantTeam st = game.getTeam(player);
        ValorantTeam tt = game.getTeam(target);
        if (st == null || tt == null || st.getSide() == tt.getSide()) return;

        if (heldType.isMelee()) {
            game.applyDamage(player, target, heldType.getDamage(), false, false);
            return;
        }

        // Left-click-on-entity counts as a gun shot (PlayerInteractEvent doesn't fire on entities)
        ItemStack held = player.getInventory().getItemInMainHand();
        Weapon weapon = plugin.getWeaponManager().getHeldWeapon(player);
        if (weapon == null || weapon.getType() != heldType) {
            weapon = weaponFromItem(held, heldType);
            plugin.getWeaponManager().setHeldWeapon(player, weapon);
        }
        if (!plugin.getWeaponManager().tryShoot(player, weapon)) return;
        fireWeapon(player, weapon, game);
    }

    // ── Hotbar slot change: update held weapon tracking ─────────────────────

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent e) {
        Player player = e.getPlayer();
        ValorantGame game = plugin.getGameManager().getGame(player);
        if (game == null) return;

        // Persist current weapon state to its hotbar item before swapping
        Weapon prev = plugin.getWeaponManager().getHeldWeapon(player);
        ItemStack prevItem = player.getInventory().getItem(e.getPreviousSlot());
        if (prev != null && Weapon.getWeaponType(prevItem) == prev.getType()) {
            player.getInventory().setItem(e.getPreviousSlot(),
                    prev.toItemStack(player.getUniqueId()));
        }

        ItemStack newItem = player.getInventory().getItem(e.getNewSlot());
        WeaponType type = Weapon.getWeaponType(newItem);
        if (type != null) {
            Weapon w = new Weapon(type);
            int storedAmmo    = Weapon.getStoredAmmo(newItem);
            int storedReserve = Weapon.getStoredReserve(newItem);
            if (storedAmmo    >= 0) w.setCurrentAmmo(storedAmmo);
            if (storedReserve >= 0) w.setReserveAmmo(storedReserve);
            plugin.getWeaponManager().setHeldWeapon(player, w);
        } else {
            // Switched to a non-weapon slot (ability/spike) — clear held weapon so
            // shoot/reload events on the wrong slot don't act on a stale gun.
            plugin.getWeaponManager().setHeldWeapon(player, null);
        }
    }

    // ── Drop prevention ───────────────────────────────────────────────────────

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        Player player = e.getPlayer();
        if (!plugin.getGameManager().isInGame(player)) return;
        WeaponType type = Weapon.getWeaponType(e.getItemDrop().getItemStack());
        if (type != null) e.setCancelled(true);
    }

    // ── Movement tracking for accuracy penalty ────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getGameManager().isInGame(p)) return;
        // Only count actual positional movement (not just head rotation)
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        plugin.getWeaponManager().recordMove(p);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean isHeadshotHit(Player shooter, Player target, RayTraceResult result) {
        // Head hitbox: from eye level - 0.1 up to eye level + 0.25
        double eyeY = target.getEyeLocation().getY();
        double hitY = result.getHitPosition().getY();
        return hitY >= eyeY - 0.1 && hitY <= eyeY + 0.25;
    }

    private boolean isLegshotHit(Player shooter, Player target, RayTraceResult result) {
        double feet = target.getLocation().getY();
        double hitY = result.getHitPosition().getY();
        return hitY <= feet + 0.6;
    }

    private Sound getGunSound(WeaponType type) {
        return switch (type.getCategory()) {
            case SIDEARM  -> Sound.ENTITY_ARROW_HIT_PLAYER;
            case SMG      -> Sound.ENTITY_ARROW_SHOOT;
            case SHOTGUN  -> Sound.ENTITY_BLAZE_SHOOT;
            case RIFLE    -> Sound.ENTITY_ARROW_SHOOT;
            case SNIPER   -> Sound.ENTITY_WITHER_SHOOT;
            case HEAVY    -> Sound.ENTITY_BLAZE_SHOOT;
            case MELEE    -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
        };
    }

    private float getPitch(WeaponType type) {
        return switch (type) {
            case OPERATOR -> 0.5f;
            case MARSHAL, OUTLAW -> 0.6f;
            case ODIN    -> 0.8f;
            case SHORTY  -> 1.4f;
            default      -> 1.0f + (float) ((random.nextDouble() - 0.5) * 0.1);
        };
    }
}
