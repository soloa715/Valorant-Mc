package com.valorantmc.game;

import com.valorantmc.ValorantMC;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Represents the Valorant Spike (bomb).
 *
 * State machine:
 *   IDLE -> CARRIED (picked up by attacker)
 *   CARRIED -> PLANTING (attacker holds USE on a bomb site)
 *   PLANTING -> PLANTED (plant completes) or CARRIED (interrupted)
 *   PLANTED -> DETONATED or DEFUSING
 *   DEFUSING -> DEFUSED or PLANTED (interrupted)
 */
public class Spike {

    public enum SpikeState { IDLE, CARRIED, PLANTING, PLANTED, DEFUSING, DETONATED, DEFUSED }

    private final ValorantGame game;
    private SpikeState state = SpikeState.IDLE;
    private UUID carrierUUID;
    private Location plantLocation;

    // Timers
    private BukkitTask beepTask;
    private BukkitTask detonationTask;
    private int detonationCountdown;

    // Visible plant marker
    private ArmorStand plantVisual;
    private Location plantBlockLoc;
    private Material prevBlockType;

    public Spike(ValorantGame game) {
        this.game = game;
        this.detonationCountdown = ValorantMC.getInstance().getConfig().getInt("game.spike-timer", 45);
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public void pickup(Player attacker) {
        if (state != SpikeState.IDLE) return;
        carrierUUID = attacker.getUniqueId();
        state = SpikeState.CARRIED;

        attacker.sendMessage(ValorantMC.getInstance().msg("spike.picked-up"));
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        game.broadcastExcept(attacker,
                ValorantMC.colorize("&c" + attacker.getName() + " picked up the Spike!"));
    }

    public void startPlant(Player attacker) {
        if (state != SpikeState.CARRIED) return;
        if (!attacker.getUniqueId().equals(carrierUUID)) return;
        state = SpikeState.PLANTING;
        // The actual plant timer is managed by AbilityListener
        attacker.sendMessage(ValorantMC.colorize("&ePlanting Spike... don't move!"));
    }

    public void finishPlant(Player attacker) {
        if (state != SpikeState.PLANTING) return;
        state = SpikeState.PLANTED;
        plantLocation = attacker.getLocation();
        spawnPlantVisual();

        game.broadcast(ValorantMC.colorize("&c&lSPIKE PLANTED! Defenders: defuse it or everyone dies!"));
        game.broadcast(ValorantMC.colorize("&c" + attacker.getName() + " planted the Spike!"));

        // Reward
        ValorantMC.getInstance().getEconomyManager()
                .addCredits(attacker.getUniqueId(), ValorantMC.getInstance().getConfig().getInt("game.spike-plant-bonus", 300));

        // Beep
        startBeepTask();
        // Detonation countdown
        startDetonationTask();
    }

    public void cancelPlant(Player attacker) {
        if (state == SpikeState.PLANTING && attacker.getUniqueId().equals(carrierUUID)) {
            state = SpikeState.CARRIED;
        }
    }

    public void startDefuse(Player defender) {
        if (state != SpikeState.PLANTED) return;
        state = SpikeState.DEFUSING;
        defender.sendMessage(ValorantMC.colorize("&bDefusing Spike... don't move!"));
        game.broadcastExcept(defender, ValorantMC.colorize("&b" + defender.getName() + " is defusing the Spike!"));
    }

    public void finishDefuse(Player defender) {
        if (state != SpikeState.DEFUSING) return;
        state = SpikeState.DEFUSED;
        cancelTasks();
        removePlantVisual();

        game.broadcast(ValorantMC.colorize("&b&lSPIKE DEFUSED! Defenders win!"));
        ValorantMC.getInstance().getEconomyManager()
                .addCredits(defender.getUniqueId(), ValorantMC.getInstance().getConfig().getInt("game.spike-defuse-bonus", 300));
        game.endRound(ValorantTeam.Side.DEFENDERS, "Spike defused");
    }

    public void cancelDefuse(Player defender) {
        if (state == SpikeState.DEFUSING) {
            state = SpikeState.PLANTED;
            defender.sendMessage(ValorantMC.colorize("&cDefuse interrupted!"));
        }
    }

    public void reset() {
        cancelTasks();
        removePlantVisual();
        state       = SpikeState.IDLE;
        carrierUUID = null;
        plantLocation = null;
    }

    // ── Private tasks ─────────────────────────────────────────────────────────

    private void startBeepTask() {
        // Beep interval decreases as detonation nears:
        //   countdown > 25 → every 3 s    (60 ticks)
        //   countdown 15–25 → every 1.5 s  (30 ticks)
        //   countdown 8–15  → every 0.75 s (15 ticks)
        //   countdown 0–8   → every 0.25 s (5 ticks)
        // We poll every 5 ticks and track elapsed ticks since last beep.
        beepTask = new BukkitRunnable() {
            int ticksSinceBeep = 0;
            @Override
            public void run() {
                if (state != SpikeState.PLANTED && state != SpikeState.DEFUSING) {
                    cancel();
                    return;
                }
                ticksSinceBeep += 5;
                int cd = detonationCountdown;  // read current value written by detonationTask
                int intervalTicks = cd > 25 ? 60 : cd > 15 ? 30 : cd > 8 ? 15 : 5;
                if (ticksSinceBeep >= intervalTicks) {
                    ticksSinceBeep = 0;
                    if (plantLocation != null) {
                        float pitch = cd > 15 ? 1.0f : cd > 8 ? 1.4f : 1.9f;
                        plantLocation.getWorld().playSound(
                                plantLocation, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, pitch);
                    }
                }
            }
        }.runTaskTimer(ValorantMC.getInstance(), 0L, 5L);
    }

    private void startDetonationTask() {
        detonationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state == SpikeState.DEFUSED || state == SpikeState.DETONATED) {
                    cancel();
                    return;
                }
                if (detonationCountdown <= 10 && detonationCountdown > 0) {
                    game.broadcast(ValorantMC.colorize(
                            "&c&lSpike detonates in &e" + detonationCountdown + "&c&l seconds!"));
                }
                if (detonationCountdown <= 0) {
                    detonate();
                    cancel();
                } else {
                    detonationCountdown--;
                }
            }
        }.runTaskTimer(ValorantMC.getInstance(), 20L, 20L);
    }

    private void detonate() {
        state = SpikeState.DETONATED;
        cancelTasks();
        removePlantVisual();

        if (plantLocation != null) {
            plantLocation.getWorld().createExplosion(plantLocation, 0f, false, false);
            plantLocation.getWorld().playSound(plantLocation, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
            // Visual: spawn lots of fireworks/particles
            plantLocation.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, plantLocation, 20);
            plantLocation.getWorld().spawnParticle(Particle.FLAME, plantLocation, 80, 3, 3, 3, 0.2);
        }

        game.broadcast(ValorantMC.colorize("&c&lSPIKE DETONATED! Attackers win!"));
        game.endRound(ValorantTeam.Side.ATTACKERS, "Spike detonated");
    }

    private void spawnPlantVisual() {
        if (plantLocation == null) return;
        // Place a redstone block at foot level so everyone can see & walk up to it
        Block b = plantLocation.getBlock();
        prevBlockType = b.getType();
        plantBlockLoc = b.getLocation();
        b.setType(Material.REDSTONE_BLOCK);

        // Floating glowing armor stand as an unambiguous marker (visible through walls)
        Location standLoc = plantLocation.clone().add(0.5, 0.3, 0.5);
        plantVisual = (ArmorStand) plantLocation.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);
        plantVisual.setInvisible(true);
        plantVisual.setGravity(false);
        plantVisual.setMarker(false);
        plantVisual.setInvulnerable(true);
        plantVisual.setCustomName(ValorantMC.colorize("&c&lSPIKE"));
        plantVisual.setCustomNameVisible(true);
        plantVisual.setGlowing(true);
        if (plantVisual.getEquipment() != null) {
            plantVisual.getEquipment().setHelmet(makeSpikeDropItem());
        }
    }

    private void removePlantVisual() {
        if (plantVisual != null && !plantVisual.isDead()) plantVisual.remove();
        plantVisual = null;
        if (plantBlockLoc != null && prevBlockType != null) {
            plantBlockLoc.getBlock().setType(prevBlockType);
        }
        plantBlockLoc = null;
        prevBlockType = null;
    }

    private void cancelTasks() {
        if (beepTask      != null) { beepTask.cancel();      beepTask      = null; }
        if (detonationTask!= null) { detonationTask.cancel();detonationTask = null; }
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public SpikeState getState()         { return state;         }
    public UUID       getCarrierUUID()   { return carrierUUID;   }
    public Location   getPlantLocation() { return plantLocation; }
    public boolean    isPlanted()        { return state == SpikeState.PLANTED || state == SpikeState.DEFUSING; }
    public boolean    isCarried()        { return state == SpikeState.CARRIED || state == SpikeState.PLANTING; }

    /** Drop the spike (carrier killed or used /vdropspike). */
    public void drop(Location where) {
        if (state != SpikeState.CARRIED && state != SpikeState.PLANTING) return;
        state = SpikeState.IDLE;
        carrierUUID = null;
        removePlantVisual();
        if (where != null) {
            where.getWorld().dropItem(where, makeSpikeDropItem());
            where.getWorld().playSound(where, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 0.6f);
        }
    }

    /** Pick up a previously-dropped spike from the ground (attacker only). */
    public void pickupFromGround(Player attacker) {
        if (state != SpikeState.IDLE) return;
        carrierUUID = attacker.getUniqueId();
        state = SpikeState.CARRIED;
        attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        attacker.sendMessage(ValorantMC.getInstance().msg("spike.picked-up"));
    }

    private org.bukkit.inventory.ItemStack makeSpikeDropItem() {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(Material.RED_DYE);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ValorantMC.colorize("&c&lSpike"));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(ValorantMC.getInstance(), "spike"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }
}
