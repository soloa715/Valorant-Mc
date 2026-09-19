package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * FADE — Initiator
 *
 * C – Seize:    Throw an orb that roots a nearby enemy (200c, 2 charges)
 * Q – Haunt:    Deploy a haunt eye that reveals enemies for 8s (free, signature)
 * E – Prowler:  Launch tracking prowler orbs that blind on reach (200c, 2 charges)
 * X – Nightfall: Expanding terror wave that decays enemies hit (8 ult)
 */
public class Fade extends Agent {

    public Fade() {
        super("fade", "Fade", AgentRole.INITIATOR);
        abilityC = new Ability("Seize",     200, 2, 0);
        abilityQ = new Ability("Haunt",       0, 1, 0);
        abilityE = new Ability("Prowler",   200, 2, 0);
        abilityX = new Ability("Nightfall",   0, 1, 8);
    }

    /** C – Seize: throw an orb that roots and decays the nearest enemy on landing */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&5[Fade] &cNo Seize charges!")); return; }
        abilityC.consume();

        Location target = safeTarget(player, 18);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.7f, 1.5f);
        player.getWorld().spawnParticle(Particle.DUST, target, 15, 0.5, 0.5, 0.5, 0,
                new Particle.DustOptions(Color.fromRGB(100, 0, 150), 1.5f));
        player.sendMessage(ValorantMC.colorize("&5[Fade] &fSeize!"));

        // Find nearest enemy to landing point
        Player victim = null;
        double nearest = 5;
        for (Player p : target.getWorld().getPlayers()) {
            if (p.equals(player)) continue;
            if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
            if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
            double d = p.getLocation().distance(target);
            if (d < nearest) { nearest = d; victim = p; }
        }
        if (victim == null) return;

        final Player finalVictim = victim;
        finalVictim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 4, false, false));
        finalVictim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,   60, 1, false, false));
        finalVictim.sendActionBar(ValorantMC.colorize("&5[Seize] &fCaught!"));

        // Cancel velocity each tick for the root duration
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 60 || finalVictim.isDead() || !finalVictim.isOnline()) { cancel(); return; }
                finalVictim.setVelocity(new Vector(0, -0.1, 0));
                finalVictim.getWorld().spawnParticle(Particle.DUST, finalVictim.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 0, 180), 1f));
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 1L);
    }

    /** Q – Haunt: place a reveal eye that reports enemy positions to allies for 8s */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&5[Fade] &cHaunt already deployed! Recharges next round.")); return; }
        abilityQ.consume();

        Location eyeLoc = safeTarget(player, 20).clone();
        ArmorStand eye = player.getWorld().spawn(eyeLoc, ArmorStand.class);
        eye.setVisible(false);
        eye.setSmall(true);
        eye.setGravity(false);
        eye.setMarker(true);
        eye.setCustomName(ValorantMC.colorize("&5👁 Haunt"));
        eye.setCustomNameVisible(true);
        eye.setPersistent(false);

        player.getWorld().playSound(eyeLoc, Sound.ENTITY_ENDERMAN_AMBIENT, 0.8f, 0.6f);
        player.sendMessage(ValorantMC.colorize("&5[Fade] &fHaunt deployed!"));

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 160 || eye.isDead()) { eye.remove(); cancel(); return; }
                eye.getWorld().spawnParticle(Particle.DUST, eye.getLocation().add(0, 0.5, 0), 3, 0.3, 0.3, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(150, 0, 200), 1f));

                if (ticks % 10 == 0) {
                    // Reveal enemies within 15 blocks to all allies
                    for (Player ally : eye.getWorld().getPlayers()) {
                        if (game.getTeam(ally) == null || game.getTeam(player) == null) continue;
                        if (!game.getTeam(ally).getSide().equals(game.getTeam(player).getSide())) continue;
                        for (Player enemy : eye.getWorld().getPlayers()) {
                            if (game.getTeam(enemy) == null) continue;
                            if (game.getTeam(enemy).getSide().equals(game.getTeam(player).getSide())) continue;
                            double dist = enemy.getLocation().distance(eye.getLocation());
                            if (dist <= 15) {
                                double dx = enemy.getLocation().getX() - eye.getLocation().getX();
                                double dz = enemy.getLocation().getZ() - eye.getLocation().getZ();
                                double bearing = Math.toDegrees(Math.atan2(dz, dx));
                                ally.sendMessage(ValorantMC.colorize(
                                    "&5[Haunt] &fEnemy &e" + enemy.getName()
                                    + " &7@ &f" + String.format("%.0f", dist) + "m &7bearing &f" + String.format("%.0f°", bearing)));
                                enemy.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 25, 0, false, false));
                            }
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 1L);
    }

    /** E – Prowler: launch 2 tracking orbs that blind the first enemy they reach */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&5[Fade] &cNo Prowler charges!")); return; }
        abilityE.consume();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_SHOOT, 0.5f, 1.8f);
        player.sendMessage(ValorantMC.colorize("&5[Fade] &fProwlers released!"));

        for (int i = 0; i < 2; i++) {
            double spread = (i == 0) ? 0.1 : -0.1;
            Vector launchDir = player.getLocation().getDirection().clone().add(new Vector(spread, 0, 0)).normalize().multiply(0.35);
            ArmorStand prowler = player.getWorld().spawn(player.getEyeLocation(), ArmorStand.class);
            prowler.setVisible(false);
            prowler.setSmall(true);
            prowler.setGravity(false);
            prowler.setMarker(false);
            prowler.setPersistent(false);
            prowler.setVelocity(launchDir);

            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= 200 || prowler.isDead()) { prowler.remove(); cancel(); return; }

                    prowler.getWorld().spawnParticle(Particle.DUST, prowler.getLocation(), 2, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(160, 50, 220), 1f));

                    // Home toward nearest enemy every 5 ticks
                    if (ticks % 5 == 0) {
                        Player target = null;
                        double nearest = 20;
                        for (Player p : prowler.getWorld().getPlayers()) {
                            if (p.equals(player)) continue;
                            if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                            if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                            double d = p.getLocation().distance(prowler.getLocation());
                            if (d < nearest) { nearest = d; target = p; }
                        }
                        if (target != null) {
                            Vector toward = target.getLocation().toVector().subtract(prowler.getLocation().toVector()).normalize().multiply(0.35);
                            prowler.setVelocity(toward);

                            if (nearest < 1.5) {
                                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
                                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1, false, false));
                                target.sendActionBar(ValorantMC.colorize("&5[Prowler] &fCaught!"));
                                prowler.getWorld().spawnParticle(Particle.FLASH, prowler.getLocation(), 1);
                                prowler.remove();
                                cancel();
                                return;
                            }
                        }
                    }
                    if (prowler.getLocation().getBlock().getType().isSolid()) {
                        prowler.remove();
                        cancel();
                        return;
                    }
                    ticks++;
                }
            }.runTaskTimer(ValorantMC.getInstance(), (long)(i * 2), 1L);
        }
    }

    /** X – Nightfall: expanding terror wave that weakens and withers enemies */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&5[Fade] &cNightfall not ready!")); return; }
        abilityX.activateUlt();

        Location origin = player.getLocation().clone();
        game.broadcast(ValorantMC.colorize("&5[Fade] &f" + player.getName() + " unleashed &lNightfall&f!"));
        player.getWorld().playSound(origin, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.7f);

        new BukkitRunnable() {
            int wave = 0;
            @Override public void run() {
                if (wave >= 30) { cancel(); return; }
                double radius = wave;
                // Ring of particles
                for (int deg = 0; deg < 360; deg += 10) {
                    double rad = Math.toRadians(deg);
                    Location pos = origin.clone().add(radius * Math.cos(rad), 0, radius * Math.sin(rad));
                    for (int h = 0; h <= 2; h++) {
                        pos.clone().add(0, h, 0).getWorld().spawnParticle(Particle.DUST,
                                pos.clone().add(0, h, 0), 2, 0.2, 0, 0.2, 0,
                                new Particle.DustOptions(Color.fromRGB(80, 0, 120), 1.5f));
                    }
                }
                // Affect enemies the wave just reached
                for (Player p : origin.getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    double dist = p.getLocation().distance(origin);
                    if (Math.abs(dist - radius) <= 1.5) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 3, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,   120, 1, false, false));
                        p.sendActionBar(ValorantMC.colorize("&5[Nightfall] &fDecaying!"));
                    }
                }
                wave++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 2L);
    }
}
