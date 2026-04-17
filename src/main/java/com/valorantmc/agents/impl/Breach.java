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
 * BREACH — Initiator
 *
 * C – Aftershock:     Charge then send a concussive blast through walls (100c)
 * Q – Flashpoint:     Send a blinding burst through walls (250c)
 * E – Fault Line:     Send a seismic wave that concusses enemies (signature)
 * X – Rolling Thunder: Large earthquake that launches enemies (7 ult)
 */
public class Breach extends Agent {

    public Breach() {
        super("breach", "Breach", AgentRole.INITIATOR);
        abilityC = new Ability("Aftershock",      100, 2, 0);
        abilityQ = new Ability("Flashpoint",      250, 2, 0);
        abilityE = new Ability("Fault Line",        0, 1, 0);
        abilityX = new Ability("Rolling Thunder",   0, 1, 7);
    }

    /** C – Aftershock: blast that damages through walls */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) {
            player.sendMessage(ValorantMC.colorize(abilityC.isOnCooldown()
                    ? "&6Aftershock cooling down: &f" + String.format("%.1f", abilityC.getCooldownSeconds()) + "s"
                    : "&6No Aftershock charges!"));
            return;
        }
        abilityC.consume(); abilityC.setCooldown(4000);

        Location target = safeTarget(player, 10);
        player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, target.clone().add(0.5,0.5,0.5), 5, 1, 1, 1, 0.1);
        player.getWorld().playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);

        for (Player p : target.getWorld().getPlayers()) {
            if (p.equals(player)) continue;
            if (game.getTeam(p) == null) continue;
            if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue; // skip teammates
            if (p.getLocation().distance(target) <= 4) {
                game.applyDamage(player, p, 60, false, false);
                p.sendActionBar(ValorantMC.colorize("&6[Aftershock] &fConcussed!"));
            }
        }
        player.sendMessage(ValorantMC.colorize("&6[Breach] &fAftershock!"));
    }

    /** Q – Flashpoint: blind through walls */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&6No Flashpoint charges!")); return; }
        abilityQ.consume();

        Location target = safeTarget(player, 10);
        player.getWorld().spawnParticle(Particle.FLASH, target.clone().add(0.5, 0.5, 0.5), 5);

        for (Player p : target.getWorld().getPlayers()) {
            if (p.equals(player)) continue;
            if (p.getLocation().distance(target) <= 7) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30, 0, false, false));
                p.sendActionBar(ValorantMC.colorize("&6[Flashpoint] &fFlashed!"));
            }
        }
        player.sendMessage(ValorantMC.colorize("&6[Breach] &fFlashpoint!"));
    }

    /** E – Fault Line: seismic wave that concusses enemies in its path */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&6Fault Line used!")); return; }
        abilityE.consume();

        org.bukkit.util.Vector dir = player.getLocation().getDirection().setY(0).normalize();
        final Location[] pos = {player.getLocation().clone()};

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRAVEL_HIT, 1f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks > 30) { cancel(); return; }
                pos[0] = pos[0].clone().add(dir);
                pos[0].getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, pos[0], 20, 1, 0.1, 1, 0.1,
                        Material.DIRT.createBlockData());
                pos[0].getWorld().playSound(pos[0], Sound.BLOCK_GRAVEL_STEP, 0.5f, 0.8f);

                for (Player p : pos[0].getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    if (p.getLocation().distance(pos[0]) <= 2.5) {
                        if (game.getTeam(p) != null && !game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false));
                            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, false));
                            p.sendActionBar(ValorantMC.colorize("&6[Fault Line] &fConcussed!"));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 2L);
        player.sendMessage(ValorantMC.colorize("&6[Breach] &fFault Line!"));
    }

    /** X – Rolling Thunder: massive earthquake, knock up enemies */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cRolling Thunder not ready!")); return; }
        abilityX.activateUlt();

        org.bukkit.util.Vector dir = player.getLocation().getDirection().setY(0).normalize();
        final Location[] pos = {player.getLocation().clone()};

        game.broadcast(ValorantMC.colorize("&6[Breach] &lROLLING THUNDER!"));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks > 50) { cancel(); return; }
                pos[0] = pos[0].clone().add(dir.clone().multiply(1.5));

                // Expanding shockwave
                for (double angle = 0; angle < 2 * Math.PI; angle += 0.3) {
                    Location edge = pos[0].clone().add(Math.cos(angle) * (ticks * 0.1), 0, Math.sin(angle) * (ticks * 0.1));
                    edge.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, edge, 5, 0.2, 0.2, 0.2, 0.05,
                            Material.STONE.createBlockData());
                }

                for (Player p : pos[0].getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    if (p.getLocation().distance(pos[0]) <= 3) {
                        if (game.getTeam(p) != null && !game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) {
                            p.setVelocity(new org.bukkit.util.Vector(0, 1.5, 0));
                            game.applyDamage(player, p, 80, false, false);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3, false, false));
                            p.sendMessage(ValorantMC.colorize("&c[Rolling Thunder] &fKnocked up!"));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 2L);
    }
}
