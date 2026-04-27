package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.Spike;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * GEKKO — Initiator
 *
 * C – Mosh Pit:  Throw an acid pit (250c, 1 charge)
 * Q – Dizzy:     Send a floating flash creature (free, signature, retrievable)
 * E – Wingman:   Deploy a companion that plants/defuses the spike (150c, 1 charge)
 * X – Thrash:    Launch a creature that freezes an enemy (7 ult)
 */
public class Gekko extends Agent {

    private ArmorStand dizzyEntity = null;
    private ArmorStand thrashEntity = null;

    public Gekko() {
        super("gekko", "Gekko", AgentRole.INITIATOR);
        abilityC = new Ability("Mosh Pit",  250, 1, 0);
        abilityQ = new Ability("Dizzy",       0, 1, 0);
        abilityE = new Ability("Wingman",   150, 1, 0);
        abilityX = new Ability("Thrash",      0, 1, 7);
    }

    /** C – Mosh Pit: throw an acid pit that deals damage and slows enemies */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&a[Gekko] &cNo Mosh Pit charges!")); return; }
        abilityC.consume();

        Location pitLoc = safeTarget(player, 20);
        player.getWorld().playSound(pitLoc, Sound.ENTITY_SLIME_SQUISH, 1f, 0.6f);
        player.sendMessage(ValorantMC.colorize("&a[Gekko] &fMosh Pit!"));

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 120) { cancel(); return; }
                pitLoc.getWorld().spawnParticle(Particle.SLIME, pitLoc.clone().add(0, 0.1, 0), 15, 2, 0.1, 2, 0.02);
                for (Player p : pitLoc.getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    if (p.getLocation().distance(pitLoc) <= 4) {
                        game.applyDamage(player, p, 15, false, false);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 2, false, false));
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
    }

    /**
     * Q – Dizzy: spawn a floating orb that flashes enemies in range every 1.5s.
     * If Dizzy is already alive, this press retrieves it (refunding the charge).
     */
    @Override
    public void useQ(Player player, ValorantGame game) {
        // Retrieve if already active
        if (dizzyEntity != null && !dizzyEntity.isDead()) {
            dizzyEntity.remove();
            dizzyEntity = null;
            abilityQ.resetCharges();
            player.sendMessage(ValorantMC.colorize("&a[Gekko] &fDizzy retrieved!"));
            return;
        }
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&a[Gekko] &cNo Dizzy charges!")); return; }
        abilityQ.consume();

        // Spawn Dizzy above the nearest enemy (or above player as fallback)
        Player target = null;
        double nearest = 20;
        for (Player p : player.getWorld().getPlayers()) {
            if (p.equals(player)) continue;
            if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
            if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
            double d = p.getLocation().distance(player.getLocation());
            if (d < nearest) { nearest = d; target = p; }
        }
        Location spawnLoc = (target != null)
                ? target.getLocation().clone().add(0, 3, 0)
                : safeTarget(player, 15).clone().add(0, 3, 0);

        dizzyEntity = player.getWorld().spawn(spawnLoc, ArmorStand.class);
        dizzyEntity.setVisible(false);
        dizzyEntity.setSmall(true);
        dizzyEntity.setGravity(false);
        dizzyEntity.setMarker(false);
        dizzyEntity.setCustomName(ValorantMC.colorize("&a🌀 Dizzy"));
        dizzyEntity.setCustomNameVisible(true);
        dizzyEntity.setPersistent(false);

        player.getWorld().playSound(spawnLoc, Sound.ENTITY_BAT_TAKEOFF, 1f, 1.3f);
        player.sendMessage(ValorantMC.colorize("&a[Gekko] &fDizzy deployed! Press Q again to retrieve."));

        final ArmorStand dizzyRef = dizzyEntity;
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 100 || dizzyRef.isDead()) {
                    if (!dizzyRef.isDead()) dizzyRef.remove();
                    if (dizzyEntity == dizzyRef) dizzyEntity = null;
                    cancel();
                    return;
                }
                dizzyRef.getWorld().spawnParticle(Particle.DUST, dizzyRef.getLocation(), 5, 0.5, 0.5, 0.5, 0,
                        new Particle.DustOptions(Color.fromRGB(50, 255, 100), 1.2f));

                // Flash enemies in LOS every 30 ticks
                if (ticks % 30 == 0) {
                    for (Player p : dizzyRef.getWorld().getPlayers()) {
                        if (p.equals(player)) continue;
                        if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                        if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                        if (p.getLocation().distance(dizzyRef.getLocation()) <= 8) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false));
                            dizzyRef.getWorld().spawnParticle(Particle.FLASH, dizzyRef.getLocation(), 1);
                            p.sendActionBar(ValorantMC.colorize("&a[Dizzy] &fFlashed!"));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 1L);
    }

    /** E – Wingman: deploy a companion that walks to the spike site and plants/defuses */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&a[Gekko] &cNo Wingman charges!")); return; }
        abilityE.consume();

        ArmorStand wingman = player.getWorld().spawn(player.getLocation().add(player.getLocation().getDirection().multiply(1)), ArmorStand.class);
        wingman.setVisible(false);
        wingman.setSmall(true);
        wingman.setGravity(true);
        wingman.setMarker(false);
        wingman.setCustomName(ValorantMC.colorize("&aWingman"));
        wingman.setCustomNameVisible(true);
        wingman.setPersistent(false);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 1.4f);
        player.sendMessage(ValorantMC.colorize("&a[Gekko] &fWingman deployed!"));

        Spike spike = game.getSpike();

        new BukkitRunnable() {
            int ticks = 0;
            boolean acted = false;
            @Override public void run() {
                if (ticks >= 400 || wingman.isDead() || acted) {
                    wingman.remove();
                    cancel();
                    return;
                }
                wingman.getWorld().spawnParticle(Particle.DUST, wingman.getLocation().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(50, 255, 80), 1f));

                // Determine target — defuse if spike planted, otherwise plant if carrying
                if (spike.isPlanted()) {
                    Location plantedLoc = spike.getPlantLocation();
                    if (plantedLoc != null) {
                        Vector toward = plantedLoc.toVector().subtract(wingman.getLocation().toVector());
                        if (toward.length() > 1) {
                            toward = toward.normalize().multiply(0.3);
                            wingman.setVelocity(toward.setY(0.05));
                        } else {
                            // Start defusing
                            acted = true;
                            wingman.getWorld().playSound(wingman.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.8f);
                            player.sendMessage(ValorantMC.colorize("&a[Wingman] &fDefusing spike!"));
                            ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
                                if (spike.isPlanted()) {
                                    spike.finishDefuse(player);
                                    player.sendMessage(ValorantMC.colorize("&a[Wingman] &fSpike defused!"));
                                }
                            }, 140L); // 7 seconds
                        }
                    }
                } else if (spike.isCarried() && spike.getCarrierUUID() != null && spike.getCarrierUUID().equals(player.getUniqueId())) {
                    // Walk toward nearest bomb site
                    List<Location> sites = game.getSiteALocations();
                    if (!sites.isEmpty()) {
                        Location siteLoc = sites.get(0);
                        Vector toward = siteLoc.toVector().subtract(wingman.getLocation().toVector());
                        if (toward.length() > 1) {
                            toward = toward.normalize().multiply(0.3);
                            wingman.setVelocity(toward.setY(0.05));
                        } else {
                            // Plant spike
                            acted = true;
                            player.sendMessage(ValorantMC.colorize("&a[Wingman] &fPlanting spike!"));
                            spike.startPlant(player);
                            ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
                                if (!spike.isPlanted()) {
                                    spike.finishPlant(player);
                                    player.sendMessage(ValorantMC.colorize("&a[Wingman] &fSpike planted!"));
                                }
                            }, 80L); // 4 seconds
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 1L);
    }

    /** X – Thrash: launch a creature that freezes the first enemy it reaches */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&a[Gekko] &cThrash not ready!")); return; }
        abilityX.activateUlt();

        Location origin = player.getEyeLocation().clone();
        Vector dir = origin.getDirection().normalize().multiply(0.5);
        thrashEntity = player.getWorld().spawn(origin, ArmorStand.class);
        thrashEntity.setVisible(false);
        thrashEntity.setSmall(true);
        thrashEntity.setGravity(false);
        thrashEntity.setMarker(false);
        thrashEntity.setCustomName(ValorantMC.colorize("&c🦎 Thrash"));
        thrashEntity.setCustomNameVisible(true);
        thrashEntity.setVelocity(dir);
        thrashEntity.setPersistent(false);

        player.getWorld().playSound(origin, Sound.ENTITY_PHANTOM_DEATH, 0.8f, 1.5f);
        game.broadcast(ValorantMC.colorize("&a[Gekko] &f" + player.getName() + " launched &lThrash&f!"));

        final ArmorStand thrashRef = thrashEntity;
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 200 || thrashRef.isDead()) { thrashRef.remove(); cancel(); return; }

                thrashRef.getWorld().spawnParticle(Particle.DUST, thrashRef.getLocation(), 3, 0.2, 0.2, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 80, 50), 1.3f));

                for (Player p : thrashRef.getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    if (p.getLocation().distance(thrashRef.getLocation()) <= 1.5) {
                        thrashRef.remove();
                        final Player captured = p;
                        game.broadcast(ValorantMC.colorize("&a[Thrash] &fCaptured &e" + captured.getName() + "&f!"));
                        captured.sendActionBar(ValorantMC.colorize("&c&lCAPTURED by Thrash!"));
                        captured.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 10, false, false));
                        captured.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 10, false, false));
                        captured.getWorld().spawnParticle(Particle.FLASH, captured.getLocation(), 2);
                        // Cancel velocity each tick for 4s
                        new BukkitRunnable() {
                            int t = 0;
                            @Override public void run() {
                                if (t >= 80 || !captured.isOnline()) { cancel(); return; }
                                captured.setVelocity(new Vector(0, -0.08, 0));
                                t++;
                            }
                        }.runTaskTimer(ValorantMC.getInstance(), 0L, 1L);
                        cancel();
                        return;
                    }
                }
                if (thrashRef.getLocation().getBlock().getType().isSolid()) { thrashRef.remove(); cancel(); return; }
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 1L);
    }

    @Override
    public void onRoundStart(Player player) {
        super.onRoundStart(player);
        if (dizzyEntity != null && !dizzyEntity.isDead()) dizzyEntity.remove();
        dizzyEntity = null;
        if (thrashEntity != null && !thrashEntity.isDead()) thrashEntity.remove();
        thrashEntity = null;
    }
}
