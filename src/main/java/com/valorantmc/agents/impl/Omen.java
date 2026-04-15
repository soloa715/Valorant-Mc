package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * OMEN — Controller
 *
 * C – Shrouded Step: Short-range teleport (100c)
 * Q – Paranoia:      Blinding shadow projectile (300c)
 * E – Dark Cover:    Deploy a smoke orb (signature, 2 charges)
 * X – From the Shadows: Teleport anywhere on the map (7 ult)
 */
public class Omen extends Agent {

    public Omen() {
        super("omen", "Omen", AgentRole.CONTROLLER);
        abilityC = new Ability("Shrouded Step",    100, 2, 0);
        abilityQ = new Ability("Paranoia",         300, 2, 0);
        abilityE = new Ability("Dark Cover",         0, 2, 0);
        abilityX = new Ability("From the Shadows",   0, 1, 7);
    }

    /** C – Shrouded Step: teleport a short distance */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&5No Shrouded Step charges!")); return; }
        abilityC.consume();

        Location dest = player.getTargetBlock(null, 8).getLocation().add(0.5, 1, 0.5);
        if (dest.getBlock().getType() != Material.AIR) { player.sendMessage(ValorantMC.colorize("&cBlocked!")); return; }

        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 30, 0.3, 1, 0.3, 0.2);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.7f);
        player.teleport(dest);
        player.getWorld().spawnParticle(Particle.PORTAL, dest, 30, 0.3, 1, 0.3, 0.2);
        player.sendMessage(ValorantMC.colorize("&5[Omen] &fShrouded Step!"));
    }

    /** Q – Paranoia: send a wave that blinds all it passes through */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&5No Paranoia charges!")); return; }
        abilityQ.consume();

        org.bukkit.util.Vector dir = player.getLocation().getDirection().normalize();
        Location pos = player.getEyeLocation();

        player.getWorld().playSound(pos, Sound.ENTITY_PHANTOM_BITE, 1f, 0.7f);

        final Location[] current = {pos.clone()};
        final org.bukkit.util.Vector[] direction = {dir.clone()};

        ValorantMC.getInstance().getServer().getScheduler().runTaskTimer(ValorantMC.getInstance(),
                task -> {
                    current[0] = current[0].add(direction[0].multiply(1));
                    direction[0] = dir; // re-set to avoid multiplication build-up
                    current[0].getWorld().spawnParticle(Particle.PORTAL, current[0], 10, 0.3, 0.3, 0.3, 0.1);

                    for (Player p : current[0].getWorld().getPlayers()) {
                        if (p.equals(player)) continue;
                        if (p.getLocation().distance(current[0]) <= 2.5) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
                            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, false));
                        }
                    }

                    if (current[0].distance(pos) > 30 || current[0].getBlock().getType().isSolid()) {
                        task.cancel();
                    }
                }, 0L, 2L);
        player.sendMessage(ValorantMC.colorize("&5[Omen] &fParanoia sent!"));
    }

    /** E – Dark Cover: deploy a smoke sphere at cursor */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&5No Dark Cover charges!")); return; }
        abilityE.consume();

        Location smokeCenter = player.getTargetBlock(null, 20).getLocation().add(0.5, 1.5, 0.5);
        player.getWorld().playSound(smokeCenter, Sound.ITEM_FIRECHARGE_USE, 1f, 0.5f);

        // Smoke lasts 6 seconds
        ValorantMC.getInstance().getServer().getScheduler().runTaskTimer(ValorantMC.getInstance(), task -> {
            smokeCenter.getWorld().spawnParticle(Particle.SMOKE_LARGE, smokeCenter, 20, 1.5, 1.5, 1.5, 0.01);
        }, 0L, 5L);

        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            // Smoke dissipates (no action needed, just stop spawning particles)
        }, 120L);
        player.sendMessage(ValorantMC.colorize("&5[Omen] &fDark Cover placed!"));
    }

    /** X – From the Shadows: teleport anywhere, enemies get a warning */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cFrom the Shadows not ready!")); return; }
        abilityX.activateUlt();

        // Teleport to where they're looking (long range)
        Location dest = player.getTargetBlock(null, 64).getLocation().add(0.5, 1, 0.5);
        game.broadcast(ValorantMC.colorize("&5An Omen is teleporting somewhere!"));

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 255, false, false));

        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            if (player.isOnline()) {
                player.getWorld().spawnParticle(Particle.PORTAL, dest, 50, 0.5, 1, 0.5, 0.2);
                player.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                player.teleport(dest);
                player.sendMessage(ValorantMC.colorize("&5[Omen] &fFrom the Shadows!"));
            }
        }, 60L);
    }
}
