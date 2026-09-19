package com.valorantmc.listeners;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.agents.impl.*;
import com.valorantmc.game.GameState;
import com.valorantmc.game.Spike;
import com.valorantmc.game.ValorantGame;
import org.bukkit.*;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AbilityListener implements Listener {

    private final ValorantMC plugin;

    // Plant/defuse progress tracking
    private final Map<UUID, BukkitRunnable> plantTasks  = new HashMap<>();
    private final Map<UUID, BukkitRunnable> defuseTasks = new HashMap<>();
    private final Map<UUID, Integer>         plantProgress  = new HashMap<>();
    private final Map<UUID, Integer>         defuseProgress = new HashMap<>();

    public AbilityListener(ValorantMC plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        ValorantGame game = plugin.getGameManager().getGame(player);
        if (game == null) return;
        if (game.getState() != GameState.ROUND_ACTIVE && game.getState() != GameState.BUY_PHASE) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null) return;

        // ── Ability items ─────────────────────────────────────────────────────
        char abilityKey = plugin.getAbilityManager().getAbilityKey(held);
        if (abilityKey != '\0') {
            e.setCancelled(true);

            // Check for special multi-use ults
            Agent agent = game.getAgent(player);
            if (agent != null) {
                if (agent instanceof Sova sova) {
                    Integer charges = player.getPersistentDataContainer()
                            .get(new NamespacedKey(plugin, "hunters_fury"), PersistentDataType.INTEGER);
                    if (charges != null && charges > 0 && abilityKey == 'X') {
                        sova.fireHuntersFuryBeam(player, game);
                        player.getPersistentDataContainer().set(
                                new NamespacedKey(plugin, "hunters_fury"),
                                PersistentDataType.INTEGER, charges - 1);
                        return;
                    }
                }
                if (agent instanceof Raze) {
                    Boolean showstopper = player.getPersistentDataContainer()
                            .get(new NamespacedKey(plugin, "showstopper"), PersistentDataType.BOOLEAN);
                    if (Boolean.TRUE.equals(showstopper) && abilityKey == 'X') {
                        ((Raze) agent).fireShowstopper(player, game);
                        return;
                    }
                }
            }

            // Killjoy: if nanoswarm is placed and C pressed again → detonate nearest
            if (agent instanceof Killjoy kj && abilityKey == 'C'
                    && !kj.getNanoswarmLocations().isEmpty()) {
                kj.detonateNearestNanoswarm(player, game);
                return;
            }

            // Jett: Blade Storm active → throw a knife on any ability-slot right-click
            if (agent instanceof Jett) {
                Boolean bladestorm = player.getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, "bladestorm"), PersistentDataType.BOOLEAN);
                if (Boolean.TRUE.equals(bladestorm)) {
                    fireBladeStormKnife(player, game);
                    return;
                }
            }

            // Cypher: pressing Q again while spycam is deployed → view camera
            if (agent instanceof Cypher cypher && abilityKey == 'Q'
                    && cypher.getSpycam() != null && cypher.getSpycam().isValid()) {
                cypher.viewSpycam(player);
                return;
            }

            // Neon: Overdrive active → fire lightning beam on right-click
            if (agent instanceof Neon) {
                Boolean overdrive = player.getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, "overdrive"), PersistentDataType.BOOLEAN);
                if (Boolean.TRUE.equals(overdrive)) {
                    Neon.fireOverdriveBolt(player, game);
                    return;
                }
            }

            // Chamber: Tour de Force active → fire one-shot sniper on right-click
            if (agent instanceof Chamber) {
                Integer tdfShots = player.getPersistentDataContainer()
                        .get(new NamespacedKey(plugin, "tourdeforce"), PersistentDataType.INTEGER);
                if (tdfShots != null && tdfShots > 0) {
                    Chamber.fireTourDeForce(player, game);
                    int remaining = tdfShots - 1;
                    if (remaining <= 0) {
                        player.getPersistentDataContainer().remove(new NamespacedKey(plugin, "tourdeforce"));
                    } else {
                        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "tourdeforce"),
                                PersistentDataType.INTEGER, remaining);
                    }
                    return;
                }
            }

            plugin.getAbilityManager().activateAbility(player, abilityKey, game);
            return;
        }

        // ── Spike: plant on right-click while sneaking ─────────────────────────
        if (plugin.getAbilityManager().isSpikeItem(held)) {
            e.setCancelled(true);
            handleSpikePlant(player, game, e.getAction());
            return;
        }

        // ── Spike defuse: holding USE near planted spike (no item needed) ──────
        handleSpikeDefuse(player, game, e.getAction());
    }

    // ── Plant handling ────────────────────────────────────────────────────────

    private void handleSpikePlant(Player player, ValorantGame game, Action action) {
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!player.isSneaking()) return;

        Spike spike = game.getSpike();
        if (!spike.isCarried() || !player.getUniqueId().equals(spike.getCarrierUUID())) {
            player.sendMessage(ValorantMC.colorize("&cYou don't have the Spike!"));
            return;
        }

        // Check if on a bomb site
        if (!isOnBombSite(player, game)) {
            player.sendMessage(ValorantMC.colorize("&cYou must be on a bomb site to plant!"));
            return;
        }

        if (plantTasks.containsKey(player.getUniqueId())) return; // already planting

        spike.startPlant(player);

        int plantTicks = plugin.getConfig().getInt("game.plant-time", 4) * 20;
        final int[] progress = {0};
        plantProgress.put(player.getUniqueId(), 0);

        BukkitRunnable task = new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline() || !player.isSneaking()) {
                    spike.cancelPlant(player);
                    plantTasks.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                progress[0]++;
                float pct = (float) progress[0] / (plantTicks / 4);  // /4 because we tick every 4 ticks
                player.sendActionBar(ValorantMC.colorize("&cPlanting Spike... &8[" + buildBar(pct) + "&8]"));

                if (progress[0] >= plantTicks / 4) {
                    spike.finishPlant(player);
                    plantTasks.remove(player.getUniqueId());
                    game.announceSpikePlanted(player.getLocation());
                    // Remove only the spike item by NBT tag — don't wipe all items of that material
                    org.bukkit.NamespacedKey spikeKey =
                            new org.bukkit.NamespacedKey(plugin, "spike");
                    for (int _i = 0; _i < player.getInventory().getSize(); _i++) {
                        ItemStack _it = player.getInventory().getItem(_i);
                        if (_it == null || !_it.hasItemMeta()) continue;
                        Boolean _isSpike = _it.getItemMeta().getPersistentDataContainer()
                                .get(spikeKey, org.bukkit.persistence.PersistentDataType.BOOLEAN);
                        if (Boolean.TRUE.equals(_isSpike)) {
                            player.getInventory().setItem(_i, null);
                            break;
                        }
                    }
                    cancel();
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 4L);
        plantTasks.put(player.getUniqueId(), task);
    }

    // ── Defuse handling ───────────────────────────────────────────────────────

    private void handleSpikeDefuse(Player player, ValorantGame game, Action action) {
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!player.isSneaking()) return;

        Spike spike = game.getSpike();
        if (!spike.isPlanted()) return;
        com.valorantmc.game.ValorantTeam team = game.getTeam(player);
        if (team == null || team.getSide() != com.valorantmc.game.ValorantTeam.Side.DEFENDERS) return;

        // Must be near the spike
        final org.bukkit.Location plantLoc = spike.getPlantLocation();
        if (plantLoc == null ||
                player.getLocation().distance(plantLoc) > 3) {
            player.sendActionBar(ValorantMC.colorize("&cGet closer to defuse the Spike!"));
            return;
        }

        if (defuseTasks.containsKey(player.getUniqueId())) return;

        spike.startDefuse(player);
        int defuseTicks = plugin.getConfig().getInt("game.defuse-time", 7) * 20;
        final int[] progress = {0};

        BukkitRunnable task = new BukkitRunnable() {
            @Override public void run() {
                org.bukkit.Location cur = spike.getPlantLocation();
                if (!player.isOnline() || !player.isSneaking() ||
                        cur == null || player.getLocation().distance(cur) > 3) {
                    spike.cancelDefuse(player);
                    defuseTasks.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                progress[0]++;
                float pct = (float) progress[0] / (defuseTicks / 4);
                player.sendActionBar(ValorantMC.colorize("&bDefusing Spike... &8[" + buildBar(pct) + "&8]"));

                if (progress[0] >= defuseTicks / 4) {
                    spike.finishDefuse(player);
                    defuseTasks.remove(player.getUniqueId());
                    game.announceSpikeDefused();
                    cancel();
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 4L);
        defuseTasks.put(player.getUniqueId(), task);
    }

    // ── Movement cancels plant/defuse ─────────────────────────────────────────

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        // Only cancel if they moved horizontally
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        BukkitRunnable pt = plantTasks.remove(p.getUniqueId());
        if (pt != null) {
            pt.cancel();
            ValorantGame game = plugin.getGameManager().getGame(p);
            if (game != null) game.getSpike().cancelPlant(p);
            p.sendActionBar(ValorantMC.colorize("&cPlanting interrupted!"));
        }

        BukkitRunnable dt = defuseTasks.remove(p.getUniqueId());
        if (dt != null) {
            dt.cancel();
            ValorantGame game = plugin.getGameManager().getGame(p);
            if (game != null) game.getSpike().cancelDefuse(p);
            p.sendActionBar(ValorantMC.colorize("&cDefusing interrupted!"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isOnBombSite(Player p, ValorantGame game) {
        // Check against registered bomb-site locations (5-block radius)
        for (org.bukkit.Location site : game.getSiteALocations()) {
            if (p.getLocation().distance(site) <= 5) return true;
        }
        for (org.bukkit.Location site : game.getSiteBLocations()) {
            if (p.getLocation().distance(site) <= 5) return true;
        }
        // If no sites defined, allow anywhere for testing
        return game.getSiteALocations().isEmpty() && game.getSiteBLocations().isEmpty();
    }

    /** Cancel any in-progress plant/defuse for this player (e.g. on quit or death). */
    public void cancelFor(Player p) {
        BukkitRunnable pt = plantTasks.remove(p.getUniqueId());
        if (pt != null) pt.cancel();
        BukkitRunnable dt = defuseTasks.remove(p.getUniqueId());
        if (dt != null) dt.cancel();
        plantProgress.remove(p.getUniqueId());
        defuseProgress.remove(p.getUniqueId());
    }

    // ── Projectile hit: Sova bolts + Raze shells ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent e) {

        // ── Sova Shock Bolt ───────────────────────────────────────────────────
        if (e.getEntity() instanceof Arrow arrow && arrow.hasMetadata("shock_bolt")) {
            e.setCancelled(true);
            arrow.remove();
            if (!(arrow.getShooter() instanceof Player shooter)) return;
            ValorantGame game = plugin.getGameManager().getGame(shooter);
            if (game == null) return;

            Location center = arrow.getLocation();
            center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 4, 0.5, 0.5, 0.5, 0.1);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);

            for (Player p : center.getWorld().getPlayers()) {
                if (p.equals(shooter)) continue;
                if (game.getTeam(p) == null) continue;
                if (game.getTeam(p).getSide().equals(game.getTeam(shooter).getSide())) continue;
                double dist = p.getLocation().distance(center);
                if (dist <= 3.5) {
                    int dmg = (int) (80 * (1 - dist / 3.5));
                    game.applyDamage(shooter, p, Math.max(20, dmg), false, false);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false));
                    p.sendActionBar(ValorantMC.colorize("&b[Shock Bolt] &fYou were hit!"));
                }
            }
            return;
        }

        // ── Sova Recon Bolt ───────────────────────────────────────────────────
        if (e.getEntity() instanceof Arrow arrow && arrow.hasMetadata("recon_bolt")) {
            e.setCancelled(true);
            arrow.remove();
            if (!(arrow.getShooter() instanceof Player shooter)) return;
            ValorantGame game = plugin.getGameManager().getGame(shooter);
            if (game == null) return;

            Location center = arrow.getLocation();
            center.getWorld().spawnParticle(Particle.ENCHANTED_HIT, center, 15, 0.5, 0.5, 0.5, 0.05);
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);

            // Reveal enemies in 20-block radius — repeated 3 times with pulses
            new BukkitRunnable() {
                int pulses = 0;
                @Override public void run() {
                    if (pulses >= 3) { cancel(); return; }
                    center.getWorld().spawnParticle(Particle.END_ROD, center, 20, 3, 3, 3, 0.05);
                    for (Player p : center.getWorld().getPlayers()) {
                        if (game.getTeam(p) == null) continue;
                        if (game.getTeam(p).getSide().equals(game.getTeam(shooter).getSide())) continue;
                        if (p.getLocation().distance(center) <= 20) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                            game.getTeam(shooter).broadcast(ValorantMC.colorize(
                                    "&b[Recon] &f" + p.getName() + " spotted at " + formatLoc(p.getLocation())));
                        }
                    }
                    pulses++;
                }
            }.runTaskTimer(plugin, 0L, 40L);
            return;
        }

        // ── Neon Relay Bolt ───────────────────────────────────────────────────
        if (e.getEntity() instanceof Snowball neonBolt) {
            String shooterUUID = neonBolt.getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, "neon_bolt"), PersistentDataType.STRING);
            if (shooterUUID != null) {
                neonBolt.remove();
                Player shooter = plugin.getServer().getPlayer(java.util.UUID.fromString(shooterUUID));
                if (shooter != null) {
                    ValorantGame boltGame = plugin.getGameManager().getGame(shooter);
                    if (boltGame != null) {
                        Agent boltAgent = boltGame.getAgent(shooter);
                        if (boltAgent instanceof Neon neon) {
                            neon.applyBoltEffect(shooter, boltGame, neonBolt.getLocation());
                        }
                    }
                }
                return;
            }
        }

        // ── Raze Paint Shells ─────────────────────────────────────────────────
        if (e.getEntity() instanceof Snowball shell && shell.hasMetadata("paint_shells")) {
            shell.remove();
            if (!(shell.getShooter() instanceof Player shooter)) return;
            ValorantGame game = plugin.getGameManager().getGame(shooter);
            if (game == null) return;

            Location center = shell.getLocation();
            center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 3);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);

            // Primary explosion
            for (Player p : center.getWorld().getPlayers()) {
                if (p.equals(shooter)) continue;
                if (game.getTeam(p) == null) continue;
                if (game.getTeam(p).getSide().equals(game.getTeam(shooter).getSide())) continue;
                double dist = p.getLocation().distance(center);
                if (dist <= 4) {
                    int dmg = (int) (90 * (1 - dist / 4.0));
                    game.applyDamage(shooter, p, Math.max(15, dmg), false, false);
                }
            }

            // Sub-munitions (3 mini explosions nearby)
            for (int i = 0; i < 3; i++) {
                final Location sub = center.clone().add(
                        (Math.random() - 0.5) * 3, 0, (Math.random() - 0.5) * 3);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    sub.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, sub, 3);
                    sub.getWorld().playSound(sub, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
                    for (Player p : sub.getWorld().getPlayers()) {
                        if (p.equals(shooter)) continue;
                        if (game.getTeam(p) == null) continue;
                        if (game.getTeam(p).getSide().equals(game.getTeam(shooter).getSide())) continue;
                        if (p.getLocation().distance(sub) <= 2.5)
                            game.applyDamage(shooter, p, 30, false, false);
                    }
                }, 5L + i * 5L);
            }
        }
    }

    // ── Jett Blade Storm knife throw ──────────────────────────────────────────

    private void fireBladeStormKnife(Player player, ValorantGame game) {
        org.bukkit.util.Vector dir = player.getLocation().getDirection().normalize();
        org.bukkit.Location start  = player.getEyeLocation();

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.8f);

        // Step-by-step raycast up to 40 blocks
        for (int i = 1; i <= 40; i++) {
            org.bukkit.Location pos = start.clone().add(dir.clone().multiply(i));
            pos.getWorld().spawnParticle(Particle.CRIT, pos, 1, 0, 0, 0, 0);

            if (pos.getBlock().getType().isSolid()) break;

            for (Player target : pos.getWorld().getPlayers()) {
                if (target.equals(player)) continue;
                if (game.getTeam(target) == null) continue;
                if (game.getTeam(target).getSide().equals(game.getTeam(player).getSide())) continue;
                if (target.getLocation().add(0, 1, 0).distance(pos) <= 0.8
                        || target.getEyeLocation().distance(pos) <= 0.8) {
                    // Headshot if near eye level
                    boolean headshot = target.getEyeLocation().distance(pos) <= 0.8;
                    int dmg = headshot ? 150 : 50;
                    game.applyDamage(player, target, dmg, headshot, false);
                    player.getWorld().spawnParticle(Particle.CRIT, pos, 10, 0.2, 0.2, 0.2, 0.1);
                    player.getWorld().playSound(pos, Sound.ENTITY_PLAYER_HURT, 1f, 1.2f);
                    return; // one knife hits one target
                }
            }
        }
    }

    private String formatLoc(org.bukkit.Location l) {
        return "(" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")";
    }

    private String buildBar(float progress) {
        int bars = 20;
        int filled = (int) (progress * bars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            sb.append(i < filled ? "&c|" : "&8|");
        }
        return sb.toString();
    }
}
