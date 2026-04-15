package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * SOVA — Initiator
 *
 * C – Shock Bolt:   Bow arrow that AoE shocks on impact (150c)
 * Q – Owl Drone:    Remote drone that scouts enemies (400c)
 * E – Recon Bolt:   Reveal enemies in range (signature)
 * X – Hunter's Fury: 3 wall-piercing energy blasts (8 ult)
 */
public class Sova extends Agent {

    public Sova() {
        super("sova", "Sova", AgentRole.INITIATOR);
        abilityC = new Ability("Shock Bolt",    150, 2, 0);
        abilityQ = new Ability("Owl Drone",     400, 1, 0);
        abilityE = new Ability("Recon Bolt",      0, 1, 0);
        abilityX = new Ability("Hunter's Fury",   0, 1, 8);
    }

    /** C – Shock Bolt: shoot an arrow that explodes on impact */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&bNo Shock Bolt charges!")); return; }
        abilityC.consume();

        Arrow arrow = player.getWorld().spawnArrow(player.getEyeLocation(),
                player.getLocation().getDirection(), 2.0f, 5f);
        arrow.setShooter(player);
        arrow.setMetadata("shock_bolt", new org.bukkit.metadata.FixedMetadataValue(ValorantMC.getInstance(), true));
        arrow.setGlowing(true);
        player.sendMessage(ValorantMC.colorize("&b[Sova] &fShock Bolt fired!"));
    }

    /** Q – Owl Drone: puts the player in a short-duration drone camera */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&bNo Owl Drone charges!")); return; }
        abilityQ.consume();

        player.sendMessage(ValorantMC.colorize("&b[Sova] &fOwl Drone launched! (Spectate mode for 8 seconds)"));
        // Create an armor stand as drone and move camera to it
        ArmorStand drone = (ArmorStand) player.getWorld().spawnEntity(
                player.getEyeLocation().add(player.getLocation().getDirection().multiply(2)),
                EntityType.ARMOR_STAND);
        drone.setVisible(false);
        drone.setGravity(false);
        drone.setMetadata("owl_drone", new org.bukkit.metadata.FixedMetadataValue(ValorantMC.getInstance(), player.getUniqueId().toString()));

        GameMode prevMode = player.getGameMode();
        player.setSpectatorTarget(drone);
        player.setGameMode(GameMode.SPECTATOR);

        // Move drone forward over 8 seconds
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isOnline() || ticks >= 160) {
                    drone.remove();
                    player.setGameMode(prevMode);
                    player.setSpectatorTarget(null);
                    cancel();
                    return;
                }
                Vector dir = player.getLocation().getDirection().setY(0).normalize().multiply(0.2);
                drone.setVelocity(dir);
                drone.getWorld().spawnParticle(Particle.CLOUD, drone.getLocation(), 2, 0.1, 0.1, 0.1, 0);
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 1L);
    }

    /** E – Recon Bolt: fire an arrow that reveals all enemies in large radius */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&bRecon Bolt used! Recharges next round.")); return; }
        abilityE.consume();

        Arrow arrow = player.getWorld().spawnArrow(player.getEyeLocation(),
                player.getLocation().getDirection(), 1.8f, 5f);
        arrow.setShooter(player);
        arrow.setMetadata("recon_bolt", new org.bukkit.metadata.FixedMetadataValue(ValorantMC.getInstance(), true));
        arrow.setGlowing(true);
        player.sendMessage(ValorantMC.colorize("&b[Sova] &fRecon Bolt fired!"));
    }

    /** X – Hunter's Fury: shoot 3 penetrating beams across the map */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cHunter's Fury not ready!")); return; }
        abilityX.activateUlt();

        player.sendMessage(ValorantMC.colorize("&b[Sova] &lHUNTER'S FURY! &fRight-click 3 times to fire!"));
        player.getPersistentDataContainer().set(
                new NamespacedKey(ValorantMC.getInstance(), "hunters_fury"),
                org.bukkit.persistence.PersistentDataType.INTEGER, 3);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1.2f);
    }

    /** Fires one Hunter's Fury beam (called from AbilityListener on right-click) */
    public void fireHuntersFuryBeam(Player player, ValorantGame game) {
        Vector dir = player.getLocation().getDirection().normalize();
        Location pos = player.getEyeLocation();

        player.getWorld().playSound(pos, Sound.ENTITY_ARROW_SHOOT, 2f, 0.5f);

        for (int i = 0; i < 60; i++) {
            pos = pos.add(dir);
            Location finalPos = pos.clone();
            player.getWorld().spawnParticle(Particle.CRIT, finalPos, 3, 0.1, 0.1, 0.1, 0);

            for (Player target : pos.getWorld().getPlayers()) {
                if (target.equals(player)) continue;
                if (target.getLocation().distance(finalPos) <= 0.8) {
                    if (game.getTeam(target) != null && game.getTeam(player) != null &&
                            !game.getTeam(target).getSide().equals(game.getTeam(player).getSide())) {
                        game.applyDamage(player, target, 100, false, false);
                        target.sendActionBar(ValorantMC.colorize("&b[Hunter's Fury] &fYou were hit!"));
                    }
                }
            }
        }
    }
}
