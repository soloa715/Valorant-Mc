package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * CHAMBER — Sentinel
 *
 * C – Trademark:    Place a proximity trap (200c, 1 charge)
 * Q – Headhunter:   High-damage pistol shot (150c, 4 charges)
 * E – Rendezvous:   Two-part teleport anchor (free, signature)
 * X – Tour de Force: One-shot custom sniper (8 ult)
 */
public class Chamber extends Agent {

    private final List<Location> traps = new ArrayList<>();
    private Location anchor = null;
    private boolean anchorPlaced = false;
    private long lastRendezvousUse = 0L;

    public Chamber() {
        super("chamber", "Chamber", AgentRole.SENTINEL);
        abilityC = new Ability("Trademark",     200, 1, 0);
        abilityQ = new Ability("Headhunter",    150, 4, 0);
        abilityE = new Ability("Rendezvous",      0, 2, 0);
        abilityX = new Ability("Tour de Force",   0, 1, 8);
    }

    /** C – Trademark: place a proximity trap at the target location */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&6[Chamber] &cNo Trademark charges!")); return; }
        abilityC.consume();

        Location trapLoc = safeTarget(player, 14);
        trapLoc.getBlock().getRelative(org.bukkit.block.BlockFace.UP).getLocation().add(0.5, 0.1, 0.5);
        traps.add(trapLoc.clone());

        // Visual marker
        ArmorStand marker = player.getWorld().spawn(trapLoc.clone().add(0.5, 0, 0.5), ArmorStand.class);
        marker.setVisible(false);
        marker.setSmall(true);
        marker.setGravity(false);
        marker.setMarker(true);
        marker.setCustomName(ValorantMC.colorize("&6⚙ Trademark"));
        marker.setCustomNameVisible(true);
        marker.setPersistent(false);

        player.getWorld().playSound(trapLoc, Sound.BLOCK_TRIPWIRE_ATTACH, 1f, 1.2f);
        player.sendMessage(ValorantMC.colorize("&6[Chamber] &fTrademark placed!"));

        // Proximity scan
        new BukkitRunnable() {
            boolean triggered = false;
            @Override public void run() {
                if (triggered || marker.isDead()) { marker.remove(); cancel(); return; }
                marker.getWorld().spawnParticle(Particle.DUST, marker.getLocation().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 165, 0), 1f));

                for (Player p : marker.getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    if (p.getLocation().distance(marker.getLocation()) <= 2.5) {
                        triggered = true;
                        marker.getWorld().playSound(marker.getLocation(), Sound.BLOCK_TRIPWIRE_CLICK_ON, 1f, 1.5f);
                        marker.getWorld().spawnParticle(Particle.FLASH, marker.getLocation(), 1);
                        marker.remove();
                        applyTrapEffect(player, game, marker.getLocation());
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
    }

    private void applyTrapEffect(Player source, ValorantGame game, Location loc) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 60) { cancel(); return; }
                for (Player p : loc.getWorld().getPlayers()) {
                    if (p.equals(source)) continue;
                    if (game.getTeam(p) == null || game.getTeam(source) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(source).getSide())) continue;
                    if (p.getLocation().distance(loc) <= 2.5) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3, false, false));
                        game.applyDamage(source, p, 10, false, false);
                        p.sendActionBar(ValorantMC.colorize("&6[Trademark] &fTethered!"));
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
    }

    /** Q – Headhunter: fire a single high-damage shot */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&6[Chamber] &cNo Headhunter charges!")); return; }
        abilityQ.consume();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
        player.sendMessage(ValorantMC.colorize("&6[Chamber] &fHeadhunter fired! (" + abilityQ.getCurrentCharges() + " left)"));

        // Raycast shot — checks for first enemy in line of sight (up to 64 blocks)
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (int i = 0; i <= 64; i++) {
            Location check = eye.clone().add(dir.clone().multiply(i));
            if (check.getBlock().getType().isSolid()) break;
            check.getWorld().spawnParticle(Particle.CRIT, check, 1, 0, 0, 0, 0);
            for (Player p : check.getWorld().getPlayers()) {
                if (p.equals(player)) continue;
                if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                double hy = p.getEyeLocation().getY();
                boolean isHead = check.getY() >= hy - 0.2 && check.getY() <= hy + 0.3;
                if (p.getLocation().add(0, 1, 0).distance(check) <= 0.9) {
                    game.applyDamage(player, p, isHead ? 160 : 55, isHead, false);
                    return;
                }
            }
        }
    }

    /** E – Rendezvous: first use = place anchor; second use = teleport to anchor */
    @Override
    public void useE(Player player, ValorantGame game) {
        long now = System.currentTimeMillis();
        if (now - lastRendezvousUse < 3000) {
            player.sendMessage(ValorantMC.colorize("&6[Chamber] &cRendezvous on cooldown!"));
            return;
        }
        if (!anchorPlaced) {
            if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&6[Chamber] &cNo Rendezvous charges!")); return; }
            abilityE.consume();
            anchor = player.getLocation().clone();
            anchorPlaced = true;
            lastRendezvousUse = now;
            player.getWorld().playSound(anchor, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.5f);
            player.getWorld().spawnParticle(Particle.PORTAL, anchor, 20, 0.5, 1, 0.5, 0.1);
            player.sendMessage(ValorantMC.colorize("&6[Chamber] &fRendezvous anchor placed! Press E again to recall."));
        } else {
            // Teleport back to anchor
            player.teleport(anchor);
            anchorPlaced = false;
            lastRendezvousUse = now;
            anchor.getWorld().spawnParticle(Particle.PORTAL, anchor, 20, 0.5, 1, 0.5, 0.1);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
            player.sendMessage(ValorantMC.colorize("&6[Chamber] &fReturned to anchor!"));
        }
    }

    /** X – Tour de Force: arm a one-shot custom sniper (7 uses) */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&6[Chamber] &cTour de Force not ready!")); return; }
        abilityX.activateUlt();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
        game.broadcast(ValorantMC.colorize("&6[Chamber] &f" + player.getName() + " deployed &lTour de Force&f!"));
        player.sendMessage(ValorantMC.colorize("&6&lTOUR DE FORCE! &fOne-shot active — right-click to fire!"));

        // PDC flag — WeaponListener / AbilityListener handles on-hit via tourdeforce tag
        player.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "tourdeforce"),
                org.bukkit.persistence.PersistentDataType.INTEGER, 5); // 5 shots

        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            player.getPersistentDataContainer().remove(new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "tourdeforce"));
            player.sendMessage(ValorantMC.colorize("&6Tour de Force expired."));
        }, 400L);
    }

    /** Fire a Tour de Force shot — called from AbilityListener on right-click */
    public static void fireTourDeForce(Player player, ValorantGame game) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        player.getWorld().playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
        player.getWorld().spawnParticle(Particle.CLOUD, eye, 5, 0.1, 0.1, 0.1, 0.2);

        for (int i = 0; i <= 80; i++) {
            Location check = eye.clone().add(dir.clone().multiply(i));
            if (check.getBlock().getType().isSolid()) break;
            if (i % 4 == 0) check.getWorld().spawnParticle(Particle.CRIT, check, 1, 0, 0, 0, 0);

            for (Player p : check.getWorld().getPlayers()) {
                if (p.equals(player)) continue;
                if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                if (p.getLocation().add(0, 1, 0).distance(check) <= 1.0) {
                    game.applyDamage(player, p, 160, true, false); // always lethal
                    // Slow zone at kill location
                    Location killLoc = p.getLocation().clone();
                    new BukkitRunnable() {
                        int t = 0;
                        @Override public void run() {
                            if (t >= 120) { cancel(); return; }
                            killLoc.getWorld().spawnParticle(Particle.DUST, killLoc.clone().add(0, 0.5, 0), 8, 2, 0.5, 2, 0,
                                    new Particle.DustOptions(Color.fromRGB(255, 165, 0), 1.2f));
                            for (Player nearby : killLoc.getWorld().getPlayers()) {
                                if (game.getTeam(nearby) == null || game.getTeam(player) == null) continue;
                                if (game.getTeam(nearby).getSide().equals(game.getTeam(player).getSide())) continue;
                                if (nearby.getLocation().distance(killLoc) <= 4) {
                                    nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3, false, false));
                                }
                            }
                            t += 5;
                        }
                    }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
                    return;
                }
            }
        }
    }

    @Override
    public void onRoundStart(Player player) {
        super.onRoundStart(player);
        anchorPlaced = false;
        anchor = null;
        traps.clear();
    }
}
