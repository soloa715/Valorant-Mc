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
 * PHOENIX — Duelist
 *
 * C – Blaze:     Create a fire wall (200c)
 * Q – Curveball: Throw a blinding flash (150c)
 * E – Hot Hands: Throw a fireball that heals Phoenix (signature)
 * X – Run It Back: Respawn at a marked spot if killed during ult (8 ult)
 */
public class Phoenix extends Agent {

    private Location runItBackAnchor;

    public Phoenix() {
        super("phoenix", "Phoenix", AgentRole.DUELIST);
        abilityC = new Ability("Blaze",        200, 1, 0);
        abilityQ = new Ability("Curveball",    150, 2, 0);
        abilityE = new Ability("Hot Hands",      0, 1, 0);
        abilityX = new Ability("Run It Back",    0, 1, 8);
    }

    /** C – Blaze: create a moving wall of fire */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&cBlaze used!")); return; }
        abilityC.consume();

        Location start = player.getLocation();
        player.getWorld().playSound(start, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 80) { cancel(); return; }
                Location wallLoc = start.clone().add(player.getLocation().getDirection().setY(0).normalize().multiply(ticks / 10.0 + 1));
                for (int y = 0; y < 3; y++) {
                    player.getWorld().spawnParticle(Particle.FLAME, wallLoc.clone().add(0, y, 0), 8, 0.3, 0, 0.3, 0.02);
                }
                // Damage enemies, heal Phoenix
                for (Player p : wallLoc.getWorld().getPlayers()) {
                    if (p.getLocation().distance(wallLoc) > 1.2) continue;
                    if (game.getTeam(p) == null) continue;
                    if (p.equals(player)) {
                        game.heal(p, 2);
                    } else if (!game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) {
                        game.applyDamage(player, p, 15, false, false);
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
        player.sendMessage(ValorantMC.colorize("&6[Phoenix] &fBlaze!"));
    }

    /** Q – Curveball: flash everyone nearby */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&cCurveball has no charges!")); return; }
        abilityQ.consume();

        Location loc = player.getLocation().add(player.getLocation().getDirection().multiply(3));
        player.getWorld().spawnParticle(Particle.FLASH, loc, 5);
        player.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 2f);

        for (Player nearby : player.getWorld().getPlayers()) {
            if (nearby.equals(player)) continue;
            if (nearby.getLocation().distance(loc) <= 10) {
                nearby.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                nearby.sendActionBar(ValorantMC.colorize("&6[Flash] &fPhoenix's Curveball!"));
            }
        }
        player.sendMessage(ValorantMC.colorize("&6[Phoenix] &fCurveball!"));
    }

    /** E – Hot Hands: throw a fire zone that heals Phoenix */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&cHot Hands used! Recharges next round.")); return; }
        abilityE.consume();

        Location target = safeTarget(player, 12).add(0.5, 1, 0.5);
        player.getWorld().playSound(target, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 60) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.FLAME, target, 15, 1.5, 0.3, 1.5, 0.02);
                for (Player p : target.getWorld().getPlayers()) {
                    if (p.getLocation().distance(target) > 2) continue;
                    if (p.equals(player)) {
                        game.heal(p, 2);
                    } else if (game.getTeam(p) != null &&
                            !game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) {
                        game.applyDamage(player, p, 8, false, false);
                    }
                }
                ticks += 4;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 4L);
        player.sendMessage(ValorantMC.colorize("&6[Phoenix] &fHot Hands!"));
    }

    /** X – Run It Back: mark location; if killed in 10s, respawn at mark */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cRun It Back not ready!")); return; }
        abilityX.activateUlt();

        runItBackAnchor = player.getLocation().clone();
        player.sendMessage(ValorantMC.colorize("&6[Phoenix] &lRUN IT BACK! &fYou'll respawn here if killed."));
        player.getWorld().spawnParticle(Particle.FLAME, runItBackAnchor, 40, 0.5, 0, 0.5, 0.1);
        player.getWorld().playSound(runItBackAnchor, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.5f);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, false, false));

        // Mark ult active in PDC
        player.getPersistentDataContainer().set(
                new NamespacedKey(ValorantMC.getInstance(), "runitback"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);

        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            if (player.isOnline()) {
                player.getPersistentDataContainer().remove(new NamespacedKey(ValorantMC.getInstance(), "runitback"));
                player.sendMessage(ValorantMC.colorize("&6Run It Back ended."));
                runItBackAnchor = null;
            }
        }, 200L);
    }

    public Location getRunItBackAnchor() { return runItBackAnchor; }
    public void clearAnchor() { runItBackAnchor = null; }
}
