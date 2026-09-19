package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * BRIMSTONE — Controller
 *
 * C – Incendiary:   Throw a molotov (250c)
 * Q – Stim Beacon:  Drop a beacon giving rapid-fire to allies (100c)
 * E – Sky Smoke:    Drop up to 3 smokes from the sky (signature)
 * X – Orbital Strike: Call a devastating laser strike (7 ult)
 */
public class Brimstone extends Agent {

    public Brimstone() {
        super("brimstone", "Brimstone", AgentRole.CONTROLLER);
        abilityC = new Ability("Incendiary",     250, 2, 0);
        abilityQ = new Ability("Stim Beacon",    100, 2, 0);
        abilityE = new Ability("Sky Smoke",        0, 3, 0);
        abilityX = new Ability("Orbital Strike",   0, 1, 7);
    }

    /** C – Incendiary: throw a molotov */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&cNo Incendiary charges!")); return; }
        abilityC.consume();

        Location target = safeTarget(player, 20).add(0.5, 0.5, 0.5);
        player.getWorld().playSound(target, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 100) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.FLAME, target, 20, 1.5, 0.1, 1.5, 0.02);
                player.getWorld().spawnParticle(Particle.LAVA, target, 3, 1.5, 0.1, 1.5, 0);
                for (Player p : target.getWorld().getPlayers()) {
                    if (p.getLocation().distance(target) <= 2.5) {
                        if (game.getTeam(p) != null && !game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) {
                            game.applyDamage(player, p, 10, false, false);
                        }
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
        player.sendMessage(ValorantMC.colorize("&c[Brimstone] &fIncendiary!"));
    }

    /** Q – Stim Beacon: drop a beacon that gives nearby allies haste */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&cNo Stim Beacon charges!")); return; }
        abilityQ.consume();

        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        player.sendMessage(ValorantMC.colorize("&c[Brimstone] &fStim Beacon placed!"));

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 200) { cancel(); return; }
                loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0,1,0), 3, 0.3, 0.5, 0.3, 0.02);
                for (Player p : loc.getWorld().getPlayers()) {
                    if (game.getTeam(p) == null) continue;
                    if (!game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    if (p.getLocation().distance(loc) <= 5) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.HASTE, 40, 1, false, false));
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SPEED, 40, 0, false, false));
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
    }

    /** E – Sky Smoke: deploy smoke at targeted location */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&cNo Sky Smoke charges!")); return; }
        abilityE.consume();

        Location target = safeTarget(player, 30).add(0.5, 1, 0.5);
        player.getWorld().playSound(target, Sound.ITEM_FIRECHARGE_USE, 0.5f, 0.5f);

        // Drop from sky
        final Location[] current = {target.clone().add(0, 15, 0)};
        new BukkitRunnable() {
            @Override public void run() {
                current[0] = current[0].clone().add(0, -1, 0);
                current[0].getWorld().spawnParticle(Particle.LARGE_SMOKE, current[0], 5, 0.2, 0.2, 0.2, 0.01);
                if (current[0].getBlockY() <= target.getBlockY()) {
                    // Start smoke cloud
                    startSmokeCloud(target, player, game);
                    cancel();
                }
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 2L);
        player.sendMessage(ValorantMC.colorize("&c[Brimstone] &fSky Smoke deployed!"));
    }

    private void startSmokeCloud(Location center, Player player, ValorantGame game) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 240) { cancel(); return; }
                center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 15, 1.5, 1.5, 1.5, 0.01);
                ticks += 3;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 3L);
    }

    /** X – Orbital Strike: devastating laser from the sky */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cOrbital Strike not ready!")); return; }
        abilityX.activateUlt();

        Location target = safeTarget(player, 50).add(0.5, 0, 0.5);
        game.broadcast(ValorantMC.colorize("&c[Brimstone] &lORBITAL STRIKE INCOMING!"));
        player.getWorld().playSound(target, Sound.ENTITY_WITHER_DEATH, 1f, 0.5f);

        // 2-second delay then laser
        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= 60) { cancel(); return; }
                    // Beam from sky
                    for (int y = 0; y < 30; y++) {
                        Location beam = target.clone().add(0, y, 0);
                        beam.getWorld().spawnParticle(Particle.FLAME, beam, 5, 0.5, 0, 0.5, 0.05);
                        beam.getWorld().spawnParticle(Particle.CRIT, beam, 3, 0.5, 0, 0.5, 0.1);
                    }
                    // Damage enemies in zone
                    for (Player p : target.getWorld().getPlayers()) {
                        if (p.equals(player)) continue;
                        if (p.getLocation().distance(target) <= 3) {
                            if (game.getTeam(p) != null && !game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) {
                                game.applyDamage(player, p, 20, false, false);
                            }
                        }
                    }
                    ticks += 5;
                }
            }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
        }, 40L);
    }
}
