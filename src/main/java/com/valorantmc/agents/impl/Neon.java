package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * NEON — Duelist
 *
 * C – Relay Bolt:  Throw a stun bolt (200c, 2 charges)
 * Q – Fast Lane:   Sprint forward leaving an energy wall (free, 2 charges)
 * E – High Gear:   Toggle sprint speed — signature, 1 charge (kill recharges)
 * X – Overdrive:   Sustained lightning beam (7 ult points)
 */
public class Neon extends Agent {

    private boolean speedActive = false;

    public Neon() {
        super("neon", "Neon", AgentRole.DUELIST);
        abilityC = new Ability("Relay Bolt",  200, 2, 0);
        abilityQ = new Ability("Fast Lane",     0, 2, 0);
        abilityE = new Ability("High Gear",     0, 1, 0);
        abilityX = new Ability("Overdrive",     0, 1, 7);
    }

    /** C – Relay Bolt: throw a stun bolt that blinds + slows on impact */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&e[Neon] &cNo Relay Bolt charges!")); return; }
        abilityC.consume();

        Snowball bolt = player.launchProjectile(Snowball.class);
        bolt.setVelocity(player.getLocation().getDirection().multiply(1.6));

        // Impact handler — checked via ProjectileHitEvent already wired in AbilityListener;
        // We store a tag so AbilityListener can find the landing location.
        bolt.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "neon_bolt"),
                org.bukkit.persistence.PersistentDataType.STRING, player.getUniqueId().toString());

        player.sendMessage(ValorantMC.colorize("&e[Neon] &fRelay Bolt fired!"));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 1.4f);

        // Also trigger effect at impact by polling landing — handled in AbilityListener's
        // ProjectileHitEvent for "neon_bolt" tag. As a self-contained fallback, schedule
        // at the target point after 1s if not already detonated.
        Location predictedLand = safeTarget(player, 20);
        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            if (!bolt.isDead()) {
                bolt.remove();
                applyBoltEffect(player, game, predictedLand);
            }
        }, 20L);
    }

    public void applyBoltEffect(Player source, ValorantGame game, Location loc) {
        loc.getWorld().spawnParticle(Particle.FLASH, loc, 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
        for (Player p : loc.getWorld().getPlayers()) {
            if (game.getTeam(p) == null) continue;
            if (game.getTeam(p).getSide().equals(game.getTeam(source).getSide())) continue;
            if (p.getLocation().distance(loc) <= 4) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  40, 3, false, false));
                p.sendActionBar(ValorantMC.colorize("&e[Relay Bolt] &fStunned!"));
            }
        }
    }

    /** Q – Fast Lane: dash forward + leave a particle wall for 6 seconds */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&e[Neon] &cNo Fast Lane charges!")); return; }
        abilityQ.consume();

        Vector dir = player.getLocation().getDirection().setY(0).normalize().multiply(1.8);
        player.setVelocity(dir.setY(0.3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2, false, false));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.6f);
        player.sendMessage(ValorantMC.colorize("&e[Neon] &fFast Lane!"));

        // Spawn energy wall in Neon's path for 6 seconds
        Location wallBase = player.getLocation().clone();
        Vector right = dir.clone().rotateAroundY(Math.PI / 2).normalize();
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 120) { cancel(); return; }
                for (int side = -4; side <= 4; side++) {
                    for (int h = 0; h <= 3; h++) {
                        Location pos = wallBase.clone().add(right.clone().multiply(side)).add(0, h, 0);
                        pos.getWorld().spawnParticle(Particle.DUST, pos, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(0, 180, 255), 1.2f));
                    }
                }
                ticks += 4;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 4L);
    }

    /** E – High Gear: toggle sprint mode; kill recharges */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!speedActive) {
            if (!abilityE.canUse()) {
                player.sendMessage(ValorantMC.colorize("&e[Neon] &cHigh Gear on cooldown — get a kill to recharge!"));
                return;
            }
            abilityE.consume();
            speedActive = true;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, false));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.8f);
            player.sendMessage(ValorantMC.colorize("&e[Neon] &fHigh Gear — ACTIVE! Press E again to slide."));
        } else {
            // Second press = slide: short forward dash + brief resistance
            speedActive = false;
            player.removePotionEffect(PotionEffectType.SPEED);
            Vector slideDir = player.getLocation().getDirection().setY(0).normalize().multiply(2.2);
            player.setVelocity(slideDir.setY(0.15));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 15, 1, false, false));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1f, 1.5f);
            player.sendMessage(ValorantMC.colorize("&e[Neon] &fSlide!"));
        }
    }

    /** X – Overdrive: sustained lightning beam, right-click to fire (handled in AbilityListener) */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&e[Neon] &cOverdrive not ready!")); return; }
        abilityX.activateUlt();

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 3, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 300, 2, false, false));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.6f);
        game.broadcast(ValorantMC.colorize("&e[Neon] &f" + player.getName() + " activated &lOverdrive&f!"));
        player.sendMessage(ValorantMC.colorize("&e&lOVERDRIVE ACTIVE! &fRight-click to fire the lightning beam!"));

        // PDC flag — AbilityListener fires lightning beam on right-click
        player.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "overdrive"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);

        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            player.getPersistentDataContainer().remove(new org.bukkit.NamespacedKey(ValorantMC.getInstance(), "overdrive"));
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.HASTE);
            player.sendMessage(ValorantMC.colorize("&eOverdrive faded."));
        }, 300L);
    }

    /** Fire a single Overdrive lightning beam tick (called from AbilityListener on right-click) */
    public static void fireOverdriveBolt(Player player, ValorantGame game) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        player.getWorld().playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.9f);
        for (int i = 0; i < 14; i++) {
            Location pos = eye.clone().add(dir.clone().multiply(i));
            pos.getWorld().spawnParticle(Particle.DUST, pos, 3, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(Color.fromRGB(100, 200, 255), 1.5f));
            // Check entity hit at this block
            for (Player p : pos.getWorld().getPlayers()) {
                if (p.equals(player)) continue;
                if (game.getTeam(p) == null) continue;
                if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                if (p.getLocation().add(0, 1, 0).distance(pos) <= 1.2) {
                    game.applyDamage(player, p, 65, false, false);
                    return; // beam hits first target, stops
                }
            }
            if (pos.getBlock().getType().isSolid()) break;
        }
    }

    @Override
    public void onKill(Player player, Player victim, ValorantGame game) {
        super.onKill(player, victim, game);
        // Kill recharges High Gear
        if (!abilityE.canUse()) {
            abilityE.resetCharges();
            speedActive = false;
            player.sendMessage(ValorantMC.colorize("&e[Neon] &fHigh Gear recharged!"));
        }
    }

    @Override
    public void onRoundStart(Player player) {
        super.onRoundStart(player);
        speedActive = false;
        player.removePotionEffect(PotionEffectType.SPEED);
    }
}
