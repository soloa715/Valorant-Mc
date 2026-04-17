package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * VIPER — Controller
 *
 * C – Snakebite:     Throw an acid orb (100c)
 * Q – Poison Cloud:  Throw a reusable smoke canister (200c)
 * E – Toxic Screen:  Deploy a long wall of poison (signature)
 * X – Viper's Pit:   Surround yourself in a massive poison cloud (8 ult)
 */
public class Viper extends Agent {

    private boolean fuelActive = true;

    public Viper() {
        super("viper", "Viper", AgentRole.CONTROLLER);
        abilityC = new Ability("Snakebite",   100, 2, 0);
        abilityQ = new Ability("Poison Cloud", 200, 1, 0);
        abilityE = new Ability("Toxic Screen",   0, 1, 0);
        abilityX = new Ability("Viper's Pit",    0, 1, 8);
    }

    /** C – Snakebite: throw an acid orb that slows and damages */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&aNo Snakebite charges!")); return; }
        abilityC.consume();

        Location target = safeTarget(player, 16).add(0.5, 0.5, 0.5);
        player.getWorld().playSound(target, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.7f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 80) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.DRIPPING_LAVA, target, 10, 1, 0.2, 1, 0.02);
                for (Player p : target.getWorld().getPlayers()) {
                    if (p.getLocation().distance(target) > 2.5) continue;
                    if (game.getTeam(p) == null) continue;
                    if (!game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) {
                        game.applyDamage(player, p, 8, false, false);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false));
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
        player.sendMessage(ValorantMC.colorize("&a[Viper] &fSnakebite!"));
    }

    /** Q – Poison Cloud: deploy a re-usable smoke/damage zone */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&aNo Poison Cloud charges!")); return; }
        abilityQ.consume();

        Location loc = safeTarget(player, 12).add(0.5, 1, 0.5);
        player.getWorld().playSound(loc, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!fuelActive || ticks >= 300) { cancel(); return; }
                loc.getWorld().spawnParticle(Particle.DRIPPING_WATER, loc, 20, 1.5, 1.5, 1.5, 0.01);
                for (Player p : loc.getWorld().getPlayers()) {
                    if (p.getLocation().distance(loc) > 2.5) continue;
                    if (game.getTeam(p) == null) continue;
                    if (!game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) {
                        game.applyDamage(player, p, 5, false, false);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20, 0, false, false));
                    }
                }
                ticks += 3;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 3L);
        player.sendMessage(ValorantMC.colorize("&a[Viper] &fPoison Cloud deployed!"));
    }

    /** E – Toxic Screen: create a long wall of green fire */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&aToxic Screen already deployed!")); return; }
        abilityE.consume();

        Location start = player.getLocation();
        org.bukkit.util.Vector right = player.getLocation().getDirection()
                .rotateAroundY(Math.PI / 2).setY(0).normalize();

        player.sendMessage(ValorantMC.colorize("&a[Viper] &fToxic Screen deployed!"));

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!fuelActive || ticks >= 300) { cancel(); return; }
                for (int i = -6; i <= 6; i++) {
                    for (int y = 0; y < 4; y++) {
                        Location pos = start.clone().add(right.clone().multiply(i)).add(0, y, 0);
                        pos.getWorld().spawnParticle(Particle.DRIPPING_WATER, pos, 2, 0.1, 0, 0.1, 0);
                    }
                }
                // Damage enemies in wall
                for (Player p : start.getWorld().getPlayers()) {
                    if (game.getTeam(p) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    // Check proximity to wall (simplified)
                    for (int i = -6; i <= 6; i++) {
                        Location pos = start.clone().add(right.clone().multiply(i));
                        if (p.getLocation().distance(pos) <= 1) {
                            game.applyDamage(player, p, 5, false, false);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20, 0, false, false));
                        }
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
    }

    /** X – Viper's Pit: large personal domain of poison */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cViper's Pit not ready!")); return; }
        abilityX.activateUlt();

        Location center = player.getLocation();
        game.broadcast(ValorantMC.colorize("&a[Viper] &f" + player.getName() + " activated &lViper's Pit!"));

        // Viper gets enhanced in the pit
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 0, false, false));

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 300) { cancel(); return; }
                // Pit visual
                for (double angle = 0; angle < 2 * Math.PI; angle += 0.3) {
                    Location edge = center.clone().add(Math.cos(angle) * 8, 0, Math.sin(angle) * 8);
                    edge.getWorld().spawnParticle(Particle.DRIPPING_WATER, edge, 2, 0, 1, 0, 0.02);
                }
                // Damage enemies in pit
                for (Player p : center.getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    if (game.getTeam(p) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    if (p.getLocation().distance(center) <= 8) {
                        game.applyDamage(player, p, 8, false, false);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20, 0, false, false));
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
    }
}
