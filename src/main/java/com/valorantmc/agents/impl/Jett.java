package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * JETT — Duelist
 *
 * C – Cloudburst:  Throw a smoke cloud forward (200c)
 * Q – Updraft:     Launch upward (free, 2 charges)
 * E – Tailwind:    Dash forward (signature, 1 charge – restores on 2 kills)
 * X – Blade Storm: Throw deadly knives (7 ult points)
 */
public class Jett extends Agent {

    private int dashKills = 0;

    public Jett() {
        super("jett", "Jett", AgentRole.DUELIST);
        abilityC = new Ability("Cloudburst",  200, 3, 0);
        abilityQ = new Ability("Updraft",       0, 2, 0);
        abilityE = new Ability("Tailwind",      0, 1, 0);
        abilityX = new Ability("Blade Storm",   0, 1, 7);
    }

    /** C – Cloudburst: place a smoke sphere where you're looking */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&cCloudburst has no charges!")); return; }
        abilityC.consume();

        Location target = player.getLocation().add(player.getLocation().getDirection().multiply(8));

        player.getWorld().spawnParticle(Particle.CLOUD, target, 150, 1.5, 1.5, 1.5, 0.02);
        player.getWorld().playSound(target, Sound.ITEM_FIRECHARGE_USE, 1f, 1.2f);
        player.sendMessage(ValorantMC.colorize("&b[Jett] &fCloudburst deployed!"));

        // Linger for 4 seconds with repeated particles
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 80) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.CLOUD, target, 15, 1.5, 1.5, 1.5, 0.01);
                ticks += 4;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 4L);
    }

    /** Q – Updraft: launch into the air */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&cUpdraft has no charges!")); return; }
        abilityQ.consume();

        player.setVelocity(new Vector(0, 1.4, 0));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, 0.5, 0, 0.5, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.5f);
        player.sendMessage(ValorantMC.colorize("&b[Jett] &fUpdraft!"));
    }

    /** E – Tailwind: dash in look direction */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&cTailwind has no charges! Get 2 kills to recharge.")); return; }
        abilityE.consume();
        dashKills = 0;

        Vector dir = player.getLocation().getDirection().normalize().multiply(2.0).setY(0.3);
        player.setVelocity(dir);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 40, 0.2, 0.2, 0.2, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.5f);
        player.sendMessage(ValorantMC.colorize("&b[Jett] &fTailwind!"));
    }

    /** X – Blade Storm: rapid-fire knives (each deals 50 bodyshot damage, 150 headshot) */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cBlade Storm not ready!")); return; }
        abilityX.activateUlt();

        player.sendMessage(ValorantMC.colorize("&6&lBLADE STORM ACTIVATED! Right-click to throw blades!"));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1.3f);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.SPEED, 200, 1, false, false));

        // Mark player as in blade-storm mode (handled in AbilityListener)
        player.getPersistentDataContainer().set(
                new NamespacedKey(ValorantMC.getInstance(), "bladestorm"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);

        // Duration 5 seconds
        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            player.getPersistentDataContainer().remove(
                    new NamespacedKey(ValorantMC.getInstance(), "bladestorm"));
            player.sendMessage(ValorantMC.colorize("&6Blade Storm ended."));
        }, 100L);
    }

    @Override
    public void onKill(Player player, Player victim, ValorantGame game) {
        super.onKill(player, victim, game);
        dashKills++;
        if (dashKills >= 2 && !abilityE.canUse()) {
            abilityE.resetCharges();
            player.sendMessage(ValorantMC.colorize("&b[Jett] &fTailwind recharged!"));
        }
    }
}
