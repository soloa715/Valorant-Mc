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
 * SAGE — Sentinel
 *
 * C – Barrier Orb:  Place a tall ice wall (400c)
 * Q – Slow Orb:     Throw a slow field (100c)
 * E – Healing Orb:  Heal yourself or an ally (signature)
 * X – Resurrection: Revive a dead ally (8 ult points)
 */
public class Sage extends Agent {

    public Sage() {
        super("sage", "Sage", AgentRole.SENTINEL);
        abilityC = new Ability("Barrier Orb",  400, 1, 0);
        abilityQ = new Ability("Slow Orb",      100, 2, 0);
        abilityE = new Ability("Healing Orb",     0, 1, 0);
        abilityX = new Ability("Resurrection",    0, 1, 8);
    }

    /** C – Barrier Orb: build a temporary ice wall in front of Sage */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&cBarrier Orb used!")); return; }
        abilityC.consume();

        Location base = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
        base.setY(base.getBlockY());

        // Build a 1-wide 4-tall ice wall
        for (int y = 0; y < 4; y++) {
            Location block = base.clone().add(0, y, 0);
            block.getBlock().setType(Material.PACKED_ICE);
        }

        player.getWorld().playSound(base, Sound.BLOCK_ICE_PLACE, 1f, 0.8f);
        player.sendMessage(ValorantMC.colorize("&b[Sage] &fBarrier Orb placed!"));

        // Remove wall after 10 seconds
        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            for (int y = 0; y < 4; y++) {
                Location block = base.clone().add(0, y, 0);
                if (block.getBlock().getType() == Material.PACKED_ICE)
                    block.getBlock().setType(Material.AIR);
            }
        }, 200L);
    }

    /** Q – Slow Orb: throw a slow zone (Slows players in area) */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&cSlow Orb has no charges!")); return; }
        abilityQ.consume();

        Location target = player.getTargetBlock(null, 20).getLocation();
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, target, 100, 2, 0.5, 2, 0.05);
        player.getWorld().playSound(target, Sound.BLOCK_POWDER_SNOW_FALL, 1f, 0.8f);
        player.sendMessage(ValorantMC.colorize("&b[Sage] &fSlow Orb thrown!"));

        // Apply slowness to all players in radius
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 100) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, target.clone().add(0, 0.5, 0), 10, 2, 0.3, 2, 0);
                for (Player nearby : target.getWorld().getPlayers()) {
                    if (nearby.getLocation().distance(target) <= 3.5) {
                        nearby.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, false, false));
                        if (game.getTeam(nearby) != null && game.getTeam(player) != null &&
                                !game.getTeam(nearby).getSide().equals(game.getTeam(player).getSide())) {
                            nearby.sendActionBar(ValorantMC.colorize("&b[Slow] &fSage's Slow Orb!"));
                        }
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
    }

    /** E – Healing Orb: heals the player you're looking at (or yourself) */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&cHealing Orb used! Recharges next round.")); return; }

        // Find nearest teammate in sight
        Player target = getNearestTeammate(player, game, 6);
        if (target == null) target = player;

        final Player healTarget = target;
        abilityE.consume();

        final int[] tickCount = {0};
        final int[] healedAmount = {0};
        Player finalTarget = target;
        new BukkitRunnable() {
            @Override public void run() {
                if (tickCount[0] >= 60 || healedAmount[0] >= 100) { cancel(); return; }
                int currentHp = game.getHealth(healTarget);
                if (currentHp >= 100) { cancel(); return; }
                game.applyDamage(null, healTarget, -5, false, false); // negative damage = heal
                healTarget.getWorld().spawnParticle(Particle.HEART, healTarget.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
                healedAmount[0] += 5;
                tickCount[0]++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);

        player.sendMessage(ValorantMC.colorize("&b[Sage] &fHealing Orb → " + target.getName()));
        target.sendMessage(ValorantMC.colorize("&b[Sage] &fYou are being healed!"));
    }

    /** X – Resurrection: revive the nearest dead ally */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cResurrection not ready!")); return; }

        // Find nearest dead teammate within 5 blocks
        Player deadAlly = findNearestDeadAlly(player, game, 5);
        if (deadAlly == null) {
            player.sendMessage(ValorantMC.colorize("&cNo dead ally nearby to resurrect!"));
            return;
        }
        abilityX.activateUlt();

        deadAlly.setGameMode(GameMode.ADVENTURE);
        deadAlly.setHealth(20);
        game.getTeam(deadAlly).getDeadMembers().remove(deadAlly.getUniqueId());

        player.getWorld().spawnParticle(Particle.HEART, deadAlly.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
        player.getWorld().playSound(deadAlly.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);

        game.broadcast(ValorantMC.colorize("&b[Sage] &f" + player.getName() + " resurrected " + deadAlly.getName() + "!"));
    }

    private Player getNearestTeammate(Player player, ValorantGame game, double radius) {
        Player nearest = null;
        double nearestDist = radius;
        for (Player p : player.getWorld().getPlayers()) {
            if (p.equals(player)) continue;
            if (game.getTeam(p) == null) continue;
            if (!game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
            double dist = p.getLocation().distance(player.getLocation());
            if (dist < nearestDist) { nearestDist = dist; nearest = p; }
        }
        return nearest;
    }

    private Player findNearestDeadAlly(Player player, ValorantGame game, double radius) {
        for (Player p : player.getWorld().getPlayers()) {
            if (p.equals(player)) continue;
            if (game.getTeam(p) == null) continue;
            if (!game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
            if (!game.getTeam(p).isDead(p)) continue;
            if (p.getLocation().distance(player.getLocation()) <= radius) return p;
        }
        return null;
    }
}
