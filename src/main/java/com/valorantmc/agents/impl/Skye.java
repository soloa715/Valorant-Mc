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

import java.util.ArrayList;
import java.util.List;

/**
 * SKYE — Initiator
 *
 * C – Regrowth:      Channel-heal nearby allies (free, signature)
 * Q – Trailblazer:   Send a wolf-like tracking creature (200c, 1 charge)
 * E – Guiding Light: Throw a hawk that flashes enemies (250c, 2 charges)
 * X – Seekers:       Send 3 tracking orbs that blind on reach (7 ult)
 */
public class Skye extends Agent {

    private final List<Entity> seekerOrbs = new ArrayList<>();

    public Skye() {
        super("skye", "Skye", AgentRole.INITIATOR);
        abilityC = new Ability("Regrowth",      0, 1, 0);
        abilityQ = new Ability("Trailblazer", 200, 1, 0);
        abilityE = new Ability("Guiding Light", 250, 2, 0);
        abilityX = new Ability("Seekers",       0, 1, 7);
    }

    /** C – Regrowth: channel-heal all nearby allies for 3 seconds */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&2[Skye] &cRegrowth already used! Recharges next round.")); return; }
        abilityC.consume();

        player.sendMessage(ValorantMC.colorize("&2[Skye] &fRegrowth — channelling heal!"));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 60) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 5, 1, 0.5, 1, 0.1);
                for (Player ally : player.getWorld().getPlayers()) {
                    if (ally.equals(player)) continue;
                    if (game.getTeam(ally) == null || game.getTeam(player) == null) continue;
                    if (!game.getTeam(ally).getSide().equals(game.getTeam(player).getSide())) continue;
                    if (ally.getLocation().distance(player.getLocation()) > 8) continue;
                    int hp = game.getHealth(ally);
                    if (hp < 100) {
                        game.heal(ally, 5);
                        ally.getWorld().spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 1, 0.3, 0.3, 0.3, 0);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
    }

    /** Q – Trailblazer: spawn a wolf creature that charges toward the nearest enemy */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&2[Skye] &cNo Trailblazer charges!")); return; }
        abilityQ.consume();

        ArmorStand wolf = player.getWorld().spawn(player.getLocation().add(player.getLocation().getDirection().multiply(1)), ArmorStand.class);
        wolf.setVisible(false);
        wolf.setSmall(true);
        wolf.setCustomName(ValorantMC.colorize("&2Trailblazer"));
        wolf.setCustomNameVisible(true);
        wolf.setGravity(true);
        wolf.setMarker(false);
        wolf.setPersistent(false);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 1.4f);
        player.sendMessage(ValorantMC.colorize("&2[Skye] &fTrailblazer deployed!"));

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 200 || wolf.isDead()) { wolf.remove(); cancel(); return; }

                // Move toward nearest enemy
                Player target = null;
                double nearest = 20;
                for (Player p : wolf.getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    double d = p.getLocation().distance(wolf.getLocation());
                    if (d < nearest) { nearest = d; target = p; }
                }

                if (target != null) {
                    Vector toward = target.getLocation().toVector().subtract(wolf.getLocation().toVector()).normalize().multiply(0.5);
                    wolf.setVelocity(toward.setY(0.1));
                    wolf.getWorld().spawnParticle(Particle.DUST, wolf.getLocation(), 3, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(Color.fromRGB(50, 200, 80), 1.2f));

                    // Explode on reach
                    if (nearest < 1.5) {
                        wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
                        wolf.getWorld().spawnParticle(Particle.FLASH, wolf.getLocation(), 2);
                        final Player finalTarget = target;
                        for (Player p : wolf.getWorld().getPlayers()) {
                            if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                            if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                            if (p.getLocation().distance(wolf.getLocation()) <= 3) {
                                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false));
                                game.applyDamage(player, p, 30, false, false);
                                p.sendActionBar(ValorantMC.colorize("&2[Trailblazer] &fHit!"));
                            }
                        }
                        wolf.remove();
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 2L);
    }

    /** E – Guiding Light: throw a flash hawk; enemies facing it are blinded */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&2[Skye] &cNo Guiding Light charges!")); return; }
        abilityE.consume();

        Location flashLoc = safeTarget(player, 18);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PARROT_FLY, 1f, 1.4f);
        player.getWorld().spawnParticle(Particle.DUST, flashLoc, 20, 1, 1, 1, 0,
                new Particle.DustOptions(Color.fromRGB(255, 220, 80), 1.5f));
        player.getWorld().playSound(flashLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 2.0f);
        player.sendMessage(ValorantMC.colorize("&2[Skye] &fGuiding Light!"));

        for (Player p : player.getWorld().getPlayers()) {
            if (p.equals(player)) continue;
            if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
            if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
            if (p.getLocation().distance(flashLoc) > 8) continue;
            // Blind enemies who are "looking toward" the flash (dot product check)
            Vector toFlash = flashLoc.toVector().subtract(p.getEyeLocation().toVector()).normalize();
            Vector pLook = p.getLocation().getDirection().normalize();
            double dot = pLook.dot(toFlash);
            if (dot > 0.0) { // facing toward flash
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false));
                p.sendActionBar(ValorantMC.colorize("&2[Guiding Light] &fFlashed!"));
            }
        }
    }

    /** X – Seekers: send 3 homing orbs toward nearest enemies */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&2[Skye] &cSeekers not ready!")); return; }
        abilityX.activateUlt();

        game.broadcast(ValorantMC.colorize("&2[Skye] &f" + player.getName() + " released the &lSeekers&f!"));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.5f);
        seekerOrbs.clear();

        for (int i = 0; i < 3; i++) {
            double angle = (i / 3.0) * 2 * Math.PI;
            Location spawnLoc = player.getLocation().add(Math.cos(angle), 1, Math.sin(angle));
            ArmorStand orb = player.getWorld().spawn(spawnLoc, ArmorStand.class);
            orb.setVisible(false);
            orb.setSmall(true);
            orb.setGravity(false);
            orb.setMarker(false);
            orb.setCustomName(ValorantMC.colorize("&2●"));
            orb.setCustomNameVisible(true);
            orb.setPersistent(false);
            seekerOrbs.add(orb);

            final int orbIndex = i;
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (ticks >= 200 || orb.isDead()) { orb.remove(); cancel(); return; }

                    Player target = null;
                    double nearest = 30;
                    for (Player p : orb.getWorld().getPlayers()) {
                        if (p.equals(player)) continue;
                        if (game.getTeam(p) == null || game.getTeam(player) == null) continue;
                        if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                        double d = p.getLocation().distance(orb.getLocation());
                        if (d < nearest) { nearest = d; target = p; }
                    }
                    if (target != null) {
                        Vector toward = target.getLocation().toVector().subtract(orb.getLocation().toVector()).normalize().multiply(0.4);
                        orb.setVelocity(toward);
                        orb.getWorld().spawnParticle(Particle.DUST, orb.getLocation(), 2, 0.1, 0.1, 0.1, 0,
                                new Particle.DustOptions(Color.fromRGB(50, 255, 100), 1f));
                        if (nearest < 1.5) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false));
                            target.sendActionBar(ValorantMC.colorize("&2[Seeker] &fFound!"));
                            orb.getWorld().spawnParticle(Particle.FLASH, orb.getLocation(), 1);
                            orb.remove();
                            cancel();
                            return;
                        }
                    }
                    ticks += 2;
                }
            }.runTaskTimer(ValorantMC.getInstance(), (long) (i * 4), 2L);
        }
    }

    @Override
    public void onRoundStart(Player player) {
        super.onRoundStart(player);
        seekerOrbs.forEach(e -> { if (!e.isDead()) e.remove(); });
        seekerOrbs.clear();
    }
}
