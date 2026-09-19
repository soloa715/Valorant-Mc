package com.valorantmc.agents.impl;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.AgentRole;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * CYPHER — Sentinel
 *
 * C – Cyber Cage: Drop a stasis cage that stops bullets (100c)
 * Q – Spycam:     Place a remote camera (100c)
 * E – Trapwire:   Place an invisible tripwire (200c / signature in ranked)
 * X – Neural Theft: Reveal all enemies from a corpse (7 ult)
 */
public class Cypher extends Agent {

    private final List<Location> trapwires  = new ArrayList<>();
    private ArmorStand           spycamEntity;

    public Cypher() {
        super("cypher", "Cypher", AgentRole.SENTINEL);
        abilityC = new Ability("Cyber Cage",   100, 2, 0);
        abilityQ = new Ability("Spycam",       100, 1, 0);
        abilityE = new Ability("Trapwire",     200, 2, 0);
        abilityX = new Ability("Neural Theft",   0, 1, 7);
    }

    /** C – Cyber Cage: create a small cage of particles */
    @Override
    public void useC(Player player, ValorantGame game) {
        if (!abilityC.canUse()) { player.sendMessage(ValorantMC.colorize("&7No Cyber Cage charges!")); return; }
        abilityC.consume();

        Location loc = safeTarget(player, 8).add(0.5, 0.5, 0.5);
        player.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 120) { cancel(); return; }
                for (double angle = 0; angle < 2 * Math.PI; angle += 0.5) {
                    Location ring = loc.clone().add(Math.cos(angle) * 2, 0, Math.sin(angle) * 2);
                    ring.getWorld().spawnParticle(Particle.PORTAL, ring, 1, 0, 0.5, 0, 0.01);
                }
                // Blind & slow enemies inside the cage radius
                for (Player p : loc.getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    if (game.getTeam(p) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    if (p.getLocation().distance(loc) <= 2.2) {
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.BLINDNESS, 30, 0, false, false));
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SLOWNESS, 20, 1, false, false));
                    }
                }
                ticks += 4;
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 4L);
        player.sendMessage(ValorantMC.colorize("&7[Cypher] &fCyber Cage placed!"));
    }

    /** Q – Spycam: place a camera, right-click Spycam item to view it */
    @Override
    public void useQ(Player player, ValorantGame game) {
        if (!abilityQ.canUse()) { player.sendMessage(ValorantMC.colorize("&7Spycam already placed!")); return; }
        abilityQ.consume();

        if (spycamEntity != null && spycamEntity.isValid()) spycamEntity.remove();
        Location loc = safeTarget(player, 6).add(0.5, 1.5, 0.5);
        spycamEntity = (ArmorStand) player.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        spycamEntity.setCustomName(ValorantMC.colorize("&7[Spycam]"));
        spycamEntity.setCustomNameVisible(true);
        spycamEntity.setGravity(false);
        spycamEntity.setInvulnerable(true);
        spycamEntity.setVisible(false);
        spycamEntity.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.OBSERVER));
        spycamEntity.setMetadata("spycam",
                new org.bukkit.metadata.FixedMetadataValue(ValorantMC.getInstance(), player.getUniqueId().toString()));

        player.getWorld().playSound(loc, Sound.BLOCK_DISPENSER_DISPENSE, 1f, 1.5f);
        player.sendMessage(ValorantMC.colorize("&7[Cypher] &fSpycam placed! Right-click again to view."));
    }

    /** E – Trapwire: set an invisible tripwire */
    @Override
    public void useE(Player player, ValorantGame game) {
        if (!abilityE.canUse()) { player.sendMessage(ValorantMC.colorize("&7No Trapwire charges!")); return; }
        abilityE.consume();

        Location loc = safeTarget(player, 5).add(0.5, 0.5, 0.5);
        trapwires.add(loc);
        player.getWorld().playSound(loc, Sound.ENTITY_SPIDER_STEP, 0.5f, 1.5f);
        player.sendMessage(ValorantMC.colorize("&7[Cypher] &fTrapwire set!"));

        // Scan for enemies every tick
        new BukkitRunnable() {
            @Override public void run() {
                if (!trapwires.contains(loc)) { cancel(); return; }
                for (Player p : loc.getWorld().getPlayers()) {
                    if (game.getTeam(p) == null) continue;
                    if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                    if (p.getLocation().distance(loc) <= 0.8) {
                        trapwires.remove(loc);
                        // Tether: slow + reveal
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.SLOWNESS, 100, 4, false, false));
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.GLOWING, 100, 0, false, false));
                        p.sendMessage(ValorantMC.colorize("&c[Trapwire] &fYou triggered Cypher's trapwire!"));
                        game.getTeam(player).broadcast(ValorantMC.colorize("&7[Cypher] &fTrapwire triggered at " + p.getName() + "!"));
                        loc.getWorld().playSound(loc, Sound.ENTITY_CREEPER_HURT, 1f, 1.5f);
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 2L);
    }

    /** X – Neural Theft: reveal all enemies from nearest corpse */
    @Override
    public void useX(Player player, ValorantGame game) {
        if (!abilityX.isUltReady()) { player.sendMessage(ValorantMC.colorize("&cNeural Theft not ready!")); return; }
        abilityX.activateUlt();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1f, 0.8f);
        player.sendMessage(ValorantMC.colorize("&7[Cypher] &fNeural Theft! Revealing all enemies..."));

        // 3-second cast then reveal
        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            for (Player p : player.getWorld().getPlayers()) {
                if (game.getTeam(p) == null) continue;
                if (game.getTeam(p).getSide().equals(game.getTeam(player).getSide())) continue;
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.GLOWING, 100, 0, false, false));
                game.getTeam(player).broadcast(ValorantMC.colorize("&7[Neural Theft] &f" +
                        p.getName() + " is at " + formatLoc(p.getLocation())));
            }
        }, 60L);
    }

    public void viewSpycam(Player player) {
        if (spycamEntity == null || !spycamEntity.isValid()) {
            player.sendMessage(ValorantMC.colorize("&cNo Spycam deployed!"));
            return;
        }
        GameMode prev = player.getGameMode();
        player.setSpectatorTarget(spycamEntity);
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(ValorantMC.colorize("&7Viewing Spycam — right-click to exit"));

        // Exit after 15s
        ValorantMC.getInstance().getServer().getScheduler().runTaskLater(ValorantMC.getInstance(), () -> {
            if (player.isOnline()) {
                player.setGameMode(prev);
                player.setSpectatorTarget(null);
            }
        }, 300L);
    }

    private String formatLoc(Location l) {
        return "(" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")";
    }

    public List<Location> getTrapwires()  { return trapwires;    }
    public ArmorStand     getSpycam()     { return spycamEntity; }
}
