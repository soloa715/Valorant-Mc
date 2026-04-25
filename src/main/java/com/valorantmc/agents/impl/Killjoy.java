package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * KILLJOY — Sentinel
 *
 * C – Nanoswarm:  Deploy a grenade that activates into a hidden swarm (200c)
 * Q – Alarmbot:   Deploy a bot that reveals and damages enemies (200c)
 * E – Turret:     Deploy a turret that shoots enemies (signature)
 * X – Lockdown:   Detain enemies in a large radius (8 ult)
 */
public class Killjoy extends Agent {

    private final List<ArmorStand> deployedTurrets = new ArrayList<>();
    private final List<Location>   nanoswarmLocations = new ArrayList<>();

    public Killjoy() {
        super("killjoy", "Killjoy", AgentRole.SENTINEL);
        abilityC = new Ability("Nanoswarm",  200, 2, 0);
        abilityQ = new Ability("Alarmbot",   200, 2, 0);
        abilityE = new Ability("Turret",       0, 1, 0);
        abilityX = new Ability("Lockdown",     0, 1, 8);
    }

    /** C – Nanoswarm: place a hidden grenade that can be activated */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&eNo Nanoswarm charges!")); return; }
        abilityC.consume();

        Location loc = safeTarget(player, 6).add(0.5, 1, 0.5);
        nanoswarmLocations.add(loc);

        player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 5, 0.1, 0.1, 0.1, 0);
        player.sendMessage(ValorantMC.colorize("&e[Killjoy] &fNanoswarm placed! Right-click the item again to detonate."));

        // Auto-detonate after 20s if not triggered
        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () ->
                nanoswarmLocations.remove(loc), 400L);
    }

    /** Q – Alarmbot: place a proximity bot that alerts on nearby enemies */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&eNo Alarmbot charges!")); return; }
        abilityQ.consume();

        Location loc = safeTarget(player, 5).add(0.5, 1, 0.5);
        player.getWorld().playSound(loc, Sound.BLOCK_DISPENSER_DISPENSE, 1f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks > 1200 || game.getState() != com.valorantmc.game.GameState.ROUND_ACTIVE) { cancel(); return; }
                for (Player p : loc.getWorld().getPlayers()) {
                    if (game.getTeam(p) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    if (p.getLocation().distance(loc) <= 5.0) {
                        game.applyDamage(player, p, 5, false, false);
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.GLOWING, 60, 0, false, false));
                        // Ping team
                        game.getTeam(player).broadcast(
                                ValorantMC.colorize("&e[Alarmbot] &fEnemy at " + formatLoc(p.getLocation()) + "!"));
                        ticks += 200; // reduce scan after trigger
                    }
                }
                loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 1, 0.1, 0.1, 0.1, 0);
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
        player.sendMessage(ValorantMC.colorize("&e[Killjoy] &fAlarmbot deployed!"));
    }

    /** E – Turret: place a turret that auto-shoots enemies in 40-degree cone */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&eTurret already deployed!")); return; }
        abilityE.consume();

        Location loc = safeTarget(player, 5).add(0.5, 1, 0.5);
        // Capture the direction the turret faces when placed (forward 180° arc)
        org.bukkit.util.Vector turretFacing = player.getLocation().getDirection().setY(0).normalize();

        ArmorStand turret = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        turret.setCustomName(ValorantMC.colorize("&e[KJ Turret]"));
        turret.setCustomNameVisible(true);
        turret.setGravity(false);
        turret.setInvulnerable(true);
        turret.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.DISPENSER));
        deployedTurrets.add(turret);

        player.getWorld().playSound(loc, Sound.BLOCK_ANVIL_PLACE, 1f, 1.5f);

        // Turret scanning task — 180° forward cone only
        new BukkitRunnable() {
            @Override public void run() {
                if (!turret.isValid() || game.getState() != com.valorantmc.game.GameState.ROUND_ACTIVE) {
                    turret.remove();
                    cancel();
                    return;
                }
                for (Player p : loc.getWorld().getPlayers()) {
                    if (game.getTeam(p) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    double dist = p.getLocation().distance(loc);
                    if (dist > 10) continue;
                    // 180° FOV check: dot product of turretFacing · direction-to-target must be > 0
                    org.bukkit.util.Vector toTarget = p.getLocation().toVector()
                            .subtract(loc.toVector()).setY(0);
                    if (toTarget.lengthSquared() < 0.001) continue; // target is at turret position
                    if (turretFacing.dot(toTarget.normalize()) <= 0) continue; // behind the turret
                    // Fire a bullet
                    loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 3, 0, 0, 0, 0.5);
                    game.applyDamage(player, p, 18, false, false);
                    p.sendActionBar(ValorantMC.colorize("&e[Turret] &fKilljoy's turret is shooting you!"));
                    return; // only shoot one target per scan
                }
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 15L); // shoots every 0.75s
        player.sendMessage(ValorantMC.colorize("&e[Killjoy] &fTurret deployed!"));
    }

    /** X – Lockdown: detain all enemies in radius */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cLockdown not ready!")); return; }
        abilityX.activateUlt();

        Location center = player.getLocation();
        game.broadcast(ValorantMC.colorize("&e[Killjoy] &fLockdown deployed! Get out of range!"));
        player.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.7f);

        // 7.5 second windup then detain
        new BukkitRunnable() {
            int countdown = 8;
            @Override public void run() {
                if (countdown <= 0) {
                    // Detain
                    for (Player p : center.getWorld().getPlayers()) {
                        if (game.getTeam(p) == null) continue;
                        if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                        if (p.getLocation().distance(center) <= 13) {
                            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                    org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 255, false, false));
                            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                    org.bukkit.potion.PotionEffectType.WEAKNESS, 100, 255, false, false));
                            p.sendMessage(ValorantMC.colorize("&e[Lockdown] &cYou are detained!"));
                        }
                    }
                    center.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
                    cancel();
                } else {
                    center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 30, 6, 0.5, 6, 0.1);
                    game.broadcast(ValorantMC.colorize("&eLockdown detaining in &f" + countdown + "&e seconds!"));
                    countdown--;
                }
            }
        }.runTaskTimer(ValorantMC.getInstance(), 20L, 20L);
    }

    private String formatLoc(Location loc) {
        return "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    public List<Location> getNanoswarmLocations() { return nanoswarmLocations; }

    /** Trigger the nearest nanoswarm to the player */
    public void detonateNearestNanoswarm(Player player, ValorantGame game) {
        Location nearest = null;
        double nearestDist = 10;
        for (Location l : nanoswarmLocations) {
            double d = l.distance(player.getLocation());
            if (d < nearestDist) { nearestDist = d; nearest = l; }
        }
        if (nearest == null) return;
        nanoswarmLocations.remove(nearest);
        final Location finalLoc = nearest;

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 60) { cancel(); return; }
                finalLoc.getWorld().spawnParticle(Particle.CRIT, finalLoc, 15, 1, 0.5, 1, 0.1);
                for (Player p : finalLoc.getWorld().getPlayers()) {
                    if (game.getTeam(p) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    if (p.getLocation().distance(finalLoc) <= 2.5) {
                        game.applyDamage(player, p, 12, false, false);
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
    }
}
