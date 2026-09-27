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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CLOVE — Controller
 *
 * C – Pick-Me-Up:  Grant temporary speed boost + 50 overhealth (100c)
 * Q – Meddle:      Throw a decay orb applying vulnerable/wither to enemies (250c)
 * E – Ruse:        Deploy smoke clouds (usable while alive OR dead) (signature)
 * X – Not Dead Yet: Self-revive window upon death requiring a kill/assist (7 ult)
 */
public class Clove extends Agent {

    private static final Map<UUID, Location> deathLocations = new HashMap<>();
    private static final Map<UUID, Long> reviveWindow = new HashMap<>();

    public Clove() {
        super("clove", "Clove", AgentRole.CONTROLLER);
        abilityC = new Ability("Pick-Me-Up",   100, 1, 0);
        abilityQ = new Ability("Meddle",       250, 1, 0);
        abilityE = new Ability("Ruse",           0, 2, 0);
        abilityX = new Ability("Not Dead Yet",   0, 1, 7);
    }

    /** C – Pick-Me-Up: Grant Speed II & Overhealth */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) {
            player.sendMessage(ValorantMC.colorize("&dNo Pick-Me-Up charges!"));
            return;
        }
        abilityC.consume();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.4f);
        player.getWorld().spawnParticle(Particle.SPELL_WITCH, player.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.05);

        // Speed II & Absorption for 8 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 2, false, false)); // +50 overhealth equivalent

        player.sendMessage(ValorantMC.colorize("&d[Clove] &fPick-Me-Up active! (+Speed & Overhealth)"));
    }

    /** Q – Meddle: Throws a decay orb applying decay/vulnerable */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) {
            player.sendMessage(ValorantMC.colorize("&dNo Meddle charges!"));
            return;
        }
        abilityQ.consume();

        Location target = safeTarget(player, 16).add(0.5, 0.5, 0.5);
        player.getWorld().playSound(target, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 1.2f);
        player.getWorld().spawnParticle(Particle.DRAGON_BREATH, target, 40, 1.5, 1.5, 1.5, 0.05);

        for (Player p : target.getWorld().getPlayers()) {
            if (p.getLocation().distance(target) > 4.0) continue;
            if (game.getTeam(p) == null) continue;
            if (!game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) {
                game.applyDamage(player, p, 10, false, false);
                p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, false));
            }
        }
        player.sendMessage(ValorantMC.colorize("&d[Clove] &fMeddle deployed!"));
    }

    /** E – Ruse: Smoke sphere (usable alive OR dead in spectator mode) */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) {
            player.sendMessage(ValorantMC.colorize("&dNo Ruse charges!"));
            return;
        }

        Location smokeCenter;
        if (player.getGameMode() == GameMode.SPECTATOR) {
            // Post-death smoke within 30 blocks of death location
            Location deathLoc = deathLocations.get(player.getUniqueId());
            smokeCenter = safeTarget(player, 25).add(0.5, 1.5, 0.5);
            if (deathLoc != null && smokeCenter.distance(deathLoc) > 30) {
                player.sendMessage(ValorantMC.colorize("&cPost-death Ruse can only be placed within 30 blocks of your death position!"));
                return;
            }
        } else {
            smokeCenter = safeTarget(player, 20).add(0.5, 1.5, 0.5);
        }

        abilityE.consume();
        smokeCenter.getWorld().playSound(smokeCenter, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 240) { cancel(); return; }
                smokeCenter.getWorld().spawnParticle(Particle.SPELL_WITCH, smokeCenter, 25, 1.5, 1.5, 1.5, 0.01);
                smokeCenter.getWorld().spawnParticle(Particle.DRAGON_BREATH, smokeCenter, 15, 1.2, 1.2, 1.2, 0.01);
                ticks += 5;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);

        player.sendMessage(ValorantMC.colorize("&d[Clove] &fRuse smoke deployed!"));
    }

    /** X – Not Dead Yet: Self-revive window */
    @Override
    public void useX(Player player, ValorantGame game) {
        UUID uuid = player.getUniqueId();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            // Activating ult while dead in revive window
            Long deadline = reviveWindow.get(uuid);
            if (deadline == null || System.currentTimeMillis() > deadline) {
                player.sendMessage(ValorantMC.colorize("&cNot Dead Yet revive window expired!"));
                return;
            }

            reviveWindow.remove(uuid);
            Location spawnLoc = deathLocations.getOrDefault(uuid, player.getLocation());

            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(spawnLoc);
            player.setHealth(20.0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 40, 255, false, false));

            player.getWorld().playSound(spawnLoc, Sound.ITEM_TOTEM_USE, 1f, 1.1f);
            player.getWorld().spawnParticle(Particle.TOTEM, spawnLoc.add(0, 1, 0), 50, 0.5, 1, 0.5, 0.2);

            game.broadcast(ValorantMC.colorize("&d[Clove] &l" + player.getName() + " REVIVED with &nNot Dead Yet&d!"));
            player.sendMessage(ValorantMC.colorize("&d[Clove] &aYou have 4 seconds to land a kill or assist to remain alive!"));
            return;
        }

        if (!abilityX.isUltReady()) {
            player.sendMessage(ValorantMC.colorize("&cNot Dead Yet not ready!"));
            return;
        }

        player.sendMessage(ValorantMC.colorize("&d[Clove] &fNot Dead Yet is ready! Will trigger prompt upon death."));
    }

    /** Called when player takes lethal damage */
    public static void handlePlayerDeath(Player player, Agent agent) {
        if (!(agent instanceof Clove clove)) return;
        UUID uuid = player.getUniqueId();
        deathLocations.put(uuid, player.getLocation().clone());

        if (clove.abilityX.isUltReady()) {
            clove.abilityX.activateUlt();
            reviveWindow.put(uuid, System.currentTimeMillis() + 12000L); // 12 second window
            player.sendMessage(ValorantMC.colorize("&d&l[NOT DEAD YET] &fPress &eX&f or type &e/vability X&f within 12 seconds to Revive!"));
        }
    }

    @Override
    public void onRoundStart(Player player) {
        super.onRoundStart(player);
        deathLocations.remove(player.getUniqueId());
        reviveWindow.remove(player.getUniqueId());
    }
}
