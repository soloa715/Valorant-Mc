package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * RAZE — Duelist
 *
 * C – Blast Pack:     Sticky bomb that launches Raze (200c)
 * Q – Paint Shells:   Cluster grenade (200c)
 * E – Boom Bot:       Roomba rocket that chases enemies (signature)
 * X – Showstopper:    Rocket launcher (6 ult)
 */
public class Raze extends Agent {

    public Raze() {
        super("raze", "Raze", AgentRole.DUELIST);
        abilityC = new Ability("Blast Pack",   200, 2, 0);
        abilityQ = new Ability("Paint Shells", 200, 2, 0);
        abilityE = new Ability("Boom Bot",       0, 1, 0);
        abilityX = new Ability("Showstopper",    0, 1, 6);
    }

    /** C – Blast Pack: throw a pack; activating again launches Raze */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&6No Blast Pack charges!")); return; }
        abilityC.consume();

        // Propel upward and forward
        org.bukkit.util.Vector v = player.getLocation().getDirection().multiply(1.5).setY(1.2);
        player.setVelocity(v);
        player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, player.getLocation(), 3);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
        player.sendMessage(ValorantMC.colorize("&6[Raze] &fBlast Pack!"));
    }

    /** Q – Paint Shells: throw cluster grenade */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&6No Paint Shells charges!")); return; }
        abilityQ.consume();

        Snowball grenade = player.launchProjectile(Snowball.class);
        grenade.setMetadata("paint_shells",
                new org.bukkit.metadata.FixedMetadataValue(ValorantMC.getInstance(), player.getUniqueId().toString()));
        player.sendMessage(ValorantMC.colorize("&6[Raze] &fPaint Shells thrown!"));
    }

    /** E – Boom Bot: deploy a rolling roomba that chases enemies */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&6Boom Bot already deployed!")); return; }
        abilityE.consume();

        Location spawnLoc = player.getLocation();
        Slime bot = (Slime) player.getWorld().spawnEntity(spawnLoc, EntityType.SLIME);
        bot.setSize(1);
        bot.setCustomName(ValorantMC.colorize("&6[Boom Bot]"));
        bot.setCustomNameVisible(true);
        bot.setMetadata("boom_bot",
                new org.bukkit.metadata.FixedMetadataValue(ValorantMC.getInstance(), player.getUniqueId().toString()));

        player.getWorld().playSound(spawnLoc, Sound.ENTITY_SLIME_JUMP, 1f, 1.5f);
        player.sendMessage(ValorantMC.colorize("&6[Raze] &fBoom Bot deployed!"));

        // Chase nearest enemy
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!bot.isValid() || ticks > 200) { bot.remove(); cancel(); return; }
                Player nearestEnemy = null;
                double nearestDist = 15;
                for (Player p : bot.getWorld().getPlayers()) {
                    if (game.getTeam(p) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    double d = p.getLocation().distance(bot.getLocation());
                    if (d < nearestDist) { nearestDist = d; nearestEnemy = p; }
                }
                if (nearestEnemy != null) {
                    org.bukkit.util.Vector dir = nearestEnemy.getLocation().subtract(bot.getLocation()).toVector().normalize().multiply(0.4);
                    bot.setVelocity(dir);
                    if (nearestDist < 1.5) {
                        // Explode
                        bot.getWorld().createExplosion(bot.getLocation(), 0f);
                        bot.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, bot.getLocation(), 5);
                        game.applyDamage(player, nearestEnemy, 80, false, false);
                        bot.remove();
                        cancel();
                    }
                }
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 2L);
    }

    /** X – Showstopper: fire a massive rocket */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cShowstopper not ready!")); return; }
        abilityX.activateUlt();

        player.sendMessage(ValorantMC.colorize("&6[Raze] &lSHOWSTOPPER! &fRight-click to fire!"));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1.5f);

        player.getPersistentDataContainer().set(
                new NamespacedKey(ValorantMC.getInstance(), "showstopper"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
    }

    /** Fires the showstopper rocket (called from AbilityListener) */
    public void fireShowstopper(Player player, ValorantGame game) {
        player.getPersistentDataContainer().remove(new NamespacedKey(ValorantMC.getInstance(), "showstopper"));

        org.bukkit.util.Vector dir = player.getLocation().getDirection().normalize();
        final Location[] pos = {player.getEyeLocation()};

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks > 40) { cancel(); return; }
                pos[0] = pos[0].add(dir);
                pos[0].getWorld().spawnParticle(Particle.FLAME, pos[0], 8, 0.3, 0.3, 0.3, 0.05);
                pos[0].getWorld().spawnParticle(Particle.SMOKE_LARGE, pos[0], 4, 0.2, 0.2, 0.2, 0.02);

                if (pos[0].getBlock().getType().isSolid()) {
                    // Explode
                    explodeShowstopper(pos[0], player, game);
                    cancel();
                    return;
                }

                for (Player p : pos[0].getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    if (p.getLocation().distance(pos[0]) <= 1) {
                        explodeShowstopper(pos[0], player, game);
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 1L);
    }

    private void explodeShowstopper(Location center, Player shooter, ValorantGame game) {
        center.getWorld().createExplosion(center, 0f, false, false);
        center.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, center, 5);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);

        for (Player p : center.getWorld().getPlayers()) {
            if (p.equals(shooter)) continue;
            double dist = p.getLocation().distance(center);
            if (dist <= 5) {
                int dmg = (int) (200 * (1 - dist / 5.0));
                game.applyDamage(shooter, p, dmg, false, false);
            }
        }
    }
}
