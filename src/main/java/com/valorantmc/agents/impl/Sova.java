package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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

    /** Q – Owl Drone: remote scouting drone with enemy detection pulse */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&bNo Owl Drone charges!")); return; }
        abilityQ.consume();

        World world = player.getWorld();
        ArmorStand drone = (ArmorStand) world.spawnEntity(
                player.getEyeLocation().add(player.getLocation().getDirection().multiply(2)),
                EntityType.ARMOR_STAND);
        drone.setVisible(false);
        drone.setGravity(false);
        drone.setMarker(true);
        drone.setCustomName(ValorantMC.colorize("&b◆ Owl Drone"));
        drone.setCustomNameVisible(true);
        drone.setMetadata("owl_drone",
                new org.bukkit.metadata.FixedMetadataValue(ValorantMC.getInstance(), player.getUniqueId().toString()));

        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(drone);
        player.sendActionBar(Component.text(
                "§b[Owl Drone] §7WASD to fly · Drone scans enemies every 1.5s · 8s duration"));

        final int DURATION_TICKS  = 160; // 8 seconds
        final int SCAN_INTERVAL   = 30;  // every 1.5 seconds
        final int SCAN_RADIUS     = 15;  // blocks

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!player.isOnline() || tick >= DURATION_TICKS || !drone.isValid()) {
                    endDrone(player, drone);
                    cancel();
                    return;
                }

                // Fly: nudge the drone in the direction Sova's camera faces
                if (player.getSpectatorTarget() == drone) {
                    Vector move = player.getLocation().getDirection();
                    move.setY(Math.max(-0.3, Math.min(0.3, move.getY()))); // limit vertical speed
                    move.multiply(0.35);
                    drone.teleport(drone.getLocation().add(move));
                }

                // Visual trail
                world.spawnParticle(Particle.CLOUD, drone.getLocation(), 2, 0.05, 0.05, 0.05, 0);

                // Enemy detection pulse every SCAN_INTERVAL ticks
                if (tick % SCAN_INTERVAL == 0) {
                    runScan(player, drone, game, SCAN_RADIUS);
                }

                // Action bar countdown
                int secondsLeft = (DURATION_TICKS - tick) / 20;
                player.sendActionBar(Component.text(
                        "§b[Owl Drone] §7Scanning… §f" + secondsLeft + "s §8| §7Click to recall early"));

                tick++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 1L, 1L);
    }

    private void runScan(Player sova, ArmorStand drone, ValorantGame game, int radius) {
        com.valorantmc.game.ValorantTeam enemies = game.getEnemyTeam(sova);
        if (enemies == null) return;

        // Sonar ring particle so the drone looks like it's pinging
        World world = drone.getWorld();
        for (int deg = 0; deg < 360; deg += 20) {
            double rad = Math.toRadians(deg);
            double x = drone.getLocation().getX() + 2 * Math.cos(rad);
            double z = drone.getLocation().getZ() + 2 * Math.sin(rad);
            world.spawnParticle(Particle.SONIC_BOOM,
                    new Location(world, x, drone.getLocation().getY(), z), 1, 0, 0, 0, 0);
        }

        boolean detected = false;
        for (Player enemy : enemies.getOnlinePlayers()) {
            double dist = drone.getLocation().distance(enemy.getLocation());
            if (dist > radius) continue;

            // Apply glowing for 2 seconds so Sova can see the outline through the drone camera
            enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));

            // Report bearing and distance to Sova
            double dx = enemy.getLocation().getX() - drone.getLocation().getX();
            double dz = enemy.getLocation().getZ() - drone.getLocation().getZ();
            double bearing = (Math.toDegrees(Math.atan2(dz, dx)) + 360) % 360;
            sova.sendMessage(ValorantMC.colorize(
                    "&b[Owl Drone] &cEnemy spotted: &f" + enemy.getName()
                    + " &7— " + String.format("%.1f", dist) + "m · "
                    + String.format("%.0f°", bearing)));
            detected = true;
        }

        if (!detected) {
            sova.sendMessage(ValorantMC.colorize("&b[Owl Drone] &7No enemies within " + radius + "m."));
        }
    }

    private void endDrone(Player player, ArmorStand drone) {
        drone.remove();
        if (!player.isOnline()) return;
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.ADVENTURE);
            player.setSpectatorTarget(null);
        }
        player.sendMessage(ValorantMC.colorize("&b[Owl Drone] &7Drone recalled."));
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

        for (int i = 0; i < 150; i++) {
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
