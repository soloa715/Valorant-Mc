package com.valorantmc.game;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.managers.EconomyManager;
import com.valorantmc.managers.MapManager;
import com.valorantmc.weapons.Weapon;
import com.valorantmc.weapons.WeaponType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

/**
 * A single match of ValorantMC.  Tracks teams, rounds, economy,
 * the spike, and the overall game state machine.
 */
public class ValorantGame {

    private final ValorantMC plugin;
    private final String id;

    // ── Teams ─────────────────────────────────────────────────────────────────
    private final ValorantTeam attackers;
    private final ValorantTeam defenders;

    // ── State ─────────────────────────────────────────────────────────────────
    private GameState state = GameState.WAITING;
    private int currentRound  = 0;
    private int roundTimer    = 0;
    private final int maxRounds;

    // ── Players ───────────────────────────────────────────────────────────────
    private final Map<UUID, Agent>  playerAgents  = new HashMap<>();
    private final Map<UUID, Weapon> playerWeapons = new HashMap<>();
    private final Map<UUID, Integer> playerHealth = new HashMap<>();  // 0-150 (base 100 + shield)
    private final Map<UUID, Integer> playerShield = new HashMap<>();  // 0-50 (light) / 0-100 (heavy)

    // ── Map ───────────────────────────────────────────────────────────────────
    private String mapName;
    private final List<Location> attackSpawns = new ArrayList<>();
    private final List<Location> defendSpawns = new ArrayList<>();
    private final List<Location> siteALocations = new ArrayList<>();
    private final List<Location> siteBLocations = new ArrayList<>();

    // ── Spike ─────────────────────────────────────────────────────────────────
    private final Spike spike;

    // ── Scoreboard ────────────────────────────────────────────────────────────
    private Scoreboard scoreboard;
    private Objective  objective;

    // ── Bossbar ───────────────────────────────────────────────────────────────
    private BossBar bossBar;

    // ── Tasks ─────────────────────────────────────────────────────────────────
    private BukkitTask timerTask;

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int ROUNDS_TO_WIN = 13;
    private static final int HALF_TIME_ROUND = 12;

    public ValorantGame(ValorantMC plugin, String id) {
        this.plugin    = plugin;
        this.id        = id;
        this.maxRounds = plugin.getConfig().getInt("game.max-rounds", 25);
        this.attackers = new ValorantTeam(ValorantTeam.Side.ATTACKERS);
        this.defenders = new ValorantTeam(ValorantTeam.Side.DEFENDERS);
        this.spike     = new Spike(this);
        setupScoreboard();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void addPlayer(Player p) {
        // Balance teams
        if (attackers.size() <= defenders.size()) {
            attackers.addPlayer(p);
        } else {
            defenders.addPlayer(p);
        }
        playerHealth.put(p.getUniqueId(), 100);
        playerShield.put(p.getUniqueId(), 0);
        plugin.getEconomyManager().initPlayer(p.getUniqueId());
        updateScoreboard();
        p.sendMessage(plugin.msg("game.join"));
    }

    public void removePlayer(Player p) {
        attackers.removePlayer(p);
        defenders.removePlayer(p);
        playerAgents.remove(p.getUniqueId());
        playerWeapons.remove(p.getUniqueId());
        playerHealth.remove(p.getUniqueId());
        playerShield.remove(p.getUniqueId());
        updateScoreboard();
        p.sendMessage(plugin.msg("game.leave"));
    }

    /** Start agent-select phase then begin first round */
    public void start(String mapName) {
        this.mapName = mapName;
        loadMap(mapName);

        state = GameState.AGENT_SELECT;
        broadcast(plugin.msgRaw("game.game-started"));
        broadcast(ValorantMC.colorize("&eSelect your agent with &b/vagent"));

        // Agent select lasts 20 seconds then auto-starts
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            startBuyPhase();
        }, 20 * 20L);
    }

    private void startBuyPhase() {
        currentRound++;
        state = GameState.BUY_PHASE;

        // Give each player starting credits / round bonus
        giveRoundStartCredits();

        // Restore all agents / health
        reviveAll();
        spike.reset();
        giveStartingWeapons();
        teleportToSpawns();

        int buyDuration = plugin.getConfig().getInt("game.buy-phase-duration", 30);
        broadcast(ValorantMC.colorize("&e&lROUND " + currentRound + " — Buy Phase! &r&7(" + buyDuration + "s)"));
        broadcast(ValorantMC.colorize("&7Use &b/vshop&7 to buy weapons and abilities."));

        updateScoreboard();
        startBossBar(BossBar.Color.YELLOW, "Buy Phase - " + buyDuration + "s", 1f);

        // Buy phase countdown
        new BukkitRunnable() {
            int remaining = buyDuration;
            @Override public void run() {
                if (state != GameState.BUY_PHASE) { cancel(); return; }
                remaining--;
                updateBossBar(remaining + "s", (float) remaining / buyDuration);
                if (remaining <= 0) { startRound(); cancel(); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startRound() {
        state = GameState.ROUND_ACTIVE;
        int roundDuration = plugin.getConfig().getInt("game.round-duration", 100);
        broadcast(ValorantMC.colorize("&c&lFIGHT!"));

        // Give spike to random attacker
        List<Player> atks = attackers.getOnlinePlayers();
        if (!atks.isEmpty()) {
            Player spikeCarrier = atks.get(new Random().nextInt(atks.size()));
            spike.pickup(spikeCarrier);
            giveSpikeItem(spikeCarrier);
        }

        updateScoreboard();
        startBossBar(BossBar.Color.RED, "Round " + currentRound + " - " + roundDuration + "s", 1f);

        timerTask = new BukkitRunnable() {
            int remaining = roundDuration;
            @Override public void run() {
                if (state != GameState.ROUND_ACTIVE) { cancel(); return; }
                remaining--;
                updateBossBar(formatTime(remaining), (float) remaining / roundDuration);

                if (remaining <= 0) {
                    // Time up — defenders win (spike not planted)
                    if (!spike.isPlanted()) {
                        endRound(ValorantTeam.Side.DEFENDERS, "Time ran out");
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /** Called when a round finishes for any reason. */
    public void endRound(ValorantTeam.Side winningSide, String reason) {
        if (state == GameState.ROUND_END || state == GameState.GAME_OVER) return;
        state = GameState.ROUND_END;
        if (timerTask != null) timerTask.cancel();

        ValorantTeam winner = (winningSide == ValorantTeam.Side.ATTACKERS) ? attackers : defenders;
        ValorantTeam loser  = (winningSide == ValorantTeam.Side.ATTACKERS) ? defenders : attackers;
        winner.addRoundWin();

        broadcast(ValorantMC.colorize(winner.getChatColor() + "&l" + winner.getDisplayName()
                + " &r&6win the round! &7(" + reason + ")"));

        // Economy awards
        int winBonus  = plugin.getConfig().getInt("game.round-win-bonus",  3000);
        int lossBonus = plugin.getConfig().getInt("game.round-loss-bonus", 1900);
        winner.getOnlinePlayers().forEach(p ->
                plugin.getEconomyManager().addCredits(p.getUniqueId(), winBonus));
        loser.getOnlinePlayers().forEach(p ->
                plugin.getEconomyManager().addCredits(p.getUniqueId(), lossBonus));

        updateScoreboard();

        // Check if game is over
        if (winner.getRoundWins() >= ROUNDS_TO_WIN) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> endGame(winner), 80L);
            return;
        }

        // Halftime swap
        if (currentRound == HALF_TIME_ROUND) {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::doHalfTime, 80L);
            return;
        }

        // Next round after 4 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, this::startBuyPhase, 80L);
    }

    private void doHalfTime() {
        attackers.swapSide();
        defenders.swapSide();
        broadcast(ValorantMC.colorize("&6&lHALF TIME! Teams have swapped sides."));
        plugin.getServer().getScheduler().runTaskLater(plugin, this::startBuyPhase, 80L);
    }

    private void endGame(ValorantTeam winner) {
        state = GameState.GAME_OVER;
        if (bossBar != null) bossBar.name(Component.text("Game Over!"));

        broadcast(ValorantMC.colorize("&6&l========= GAME OVER ========="));
        broadcast(ValorantMC.colorize(winner.getChatColor() + "&l" + winner.getDisplayName() + " WIN!"));
        broadcast(ValorantMC.colorize("&7Score: &c" + attackers.getRoundWins() + " &7- &b" + defenders.getRoundWins()));
        broadcast(ValorantMC.colorize("&6&l============================="));

        // Disconnect players after 10 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            getAllPlayers().forEach(p -> {
                removePlayer(p);
                p.sendMessage(plugin.msg("game.game-over"));
            });
            plugin.getGameManager().removeGame(id);
        }, 200L);
    }

    public void shutdown() {
        if (timerTask != null) timerTask.cancel();
        spike.reset();
        if (bossBar != null) {
            getAllPlayers().forEach(p ->
                    p.hideBossBar(bossBar));
        }
    }

    // ── Combat ────────────────────────────────────────────────────────────────

    /**
     * Apply damage to a player.
     * @param source  attacker (may be null for spike damage)
     * @param target  victim
     * @param rawDamage  pre-multiplier body-shot damage
     * @param isHeadshot true if hit was a headshot
     * @param isLegshot  true if hit was a leg shot
     */
    public void applyDamage(Player source, Player target, int rawDamage,
                             boolean isHeadshot, boolean isLegshot) {
        if (!playerHealth.containsKey(target.getUniqueId())) return;

        double multiplier = isHeadshot ? 2.5 : isLegshot ? 0.85 : 1.0;
        int finalDamage = (int) Math.ceil(rawDamage * multiplier);

        // Shield absorbs first
        int shield = playerShield.getOrDefault(target.getUniqueId(), 0);
        if (shield > 0) {
            int absorbed = Math.min(shield, finalDamage);
            shield -= absorbed;
            finalDamage -= absorbed;
            playerShield.put(target.getUniqueId(), shield);
        }

        int hp = playerHealth.getOrDefault(target.getUniqueId(), 100) - finalDamage;

        if (hp <= 0) {
            playerHealth.put(target.getUniqueId(), 0);
            handleDeath(source, target, isHeadshot);
        } else {
            playerHealth.put(target.getUniqueId(), hp);
            // Update Minecraft health bar proportionally
            target.setHealth(Math.max(0.5, (hp / 100.0) * 20.0));
        }

        // Send hit confirmation to shooter
        if (source != null) {
            String location = isHeadshot ? "&c[HEADSHOT]" : isLegshot ? "&7[LEG]" : "&f[BODY]";
            source.sendActionBar(ValorantMC.colorize(location + " &f" + finalDamage
                    + " &8→ " + target.getName() + " &8(" + Math.max(0, hp) + "hp)"));
        }

        // Hit sound / particles
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        if (isHeadshot) {
            target.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 10, 0.2, 0.2, 0.2, 0.1);
        }
    }

    private void handleDeath(Player killer, Player victim, boolean headshot) {
        victim.setHealth(20);
        victim.setGameMode(GameMode.SPECTATOR);

        ValorantTeam victimTeam = getTeam(victim);
        if (victimTeam != null) victimTeam.markDead(victim);

        // Kill message
        String killMsg;
        if (killer != null) {
            killMsg = ValorantMC.colorize(getTeam(killer).getChatColor() + killer.getName()
                    + " &7eliminated "
                    + (victimTeam != null ? victimTeam.getChatColor() : "") + victim.getName()
                    + (headshot ? " &c&l[HEADSHOT]" : ""));
            // Kill bonus
            plugin.getEconomyManager().addCredits(killer.getUniqueId(),
                    plugin.getConfig().getInt("game.kill-bonus", 200));
            // Stats
            plugin.getStatsManager().recordKill(killer.getUniqueId(), headshot);
        } else {
            killMsg = ValorantMC.colorize("&8" + victim.getName() + " died.");
        }
        broadcast(killMsg);
        plugin.getStatsManager().recordDeath(victim.getUniqueId());

        // Announce if drops spike
        if (spike.getCarrierUUID() != null && spike.getCarrierUUID().equals(victim.getUniqueId())) {
            broadcast(ValorantMC.colorize("&cThe Spike has been dropped at "
                    + formatLoc(victim.getLocation()) + "!"));
        }

        // Check if a team is eliminated
        checkElimination();
        updateScoreboard();
    }

    private void checkElimination() {
        if (state != GameState.ROUND_ACTIVE) return;
        if (attackers.isEliminated() && !spike.isPlanted()) {
            endRound(ValorantTeam.Side.DEFENDERS, "All attackers eliminated");
        } else if (defenders.isEliminated() && !spike.isPlanted()) {
            endRound(ValorantTeam.Side.ATTACKERS, "All defenders eliminated");
        }
    }

    // ── Helper methods ─────────────────────────────────────────────────────────

    private void giveRoundStartCredits() {
        int startingCredits = plugin.getConfig().getInt("game.starting-credits", 800);
        if (currentRound == 1) {
            getAllPlayers().forEach(p ->
                    plugin.getEconomyManager().setCredits(p.getUniqueId(), startingCredits));
        }
        // Existing credits carry over — just cap them
        getAllPlayers().forEach(p ->
                plugin.getEconomyManager().capCredits(p.getUniqueId()));
    }

    private void giveStartingWeapons() {
        getAllPlayers().forEach(p -> {
            p.getInventory().clear();
            // Give free Classic pistol
            Weapon classic = new Weapon(WeaponType.CLASSIC);
            p.getInventory().setItem(8, classic.toItemStack(p.getUniqueId()));
            // Give knife
            Weapon knife = new Weapon(WeaponType.KNIFE);
            p.getInventory().setItem(7, knife.toItemStack(p.getUniqueId()));
            p.getInventory().setHeldItemSlot(0);
        });
    }

    private void giveSpikeItem(Player p) {
        // Spike is represented as a red dye named "Spike"
        org.bukkit.inventory.ItemStack spikeItem = new org.bukkit.inventory.ItemStack(Material.RED_DYE);
        org.bukkit.inventory.meta.ItemMeta m = spikeItem.getItemMeta();
        m.setDisplayName(ValorantMC.colorize("&c&lSpike"));
        m.setLore(List.of(
                ValorantMC.colorize("&7Plant at a bomb site!"),
                ValorantMC.colorize("&8Sneak + Right-click to plant")));
        m.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "spike"),
                org.bukkit.persistence.PersistentDataType.BOOLEAN, true);
        spikeItem.setItemMeta(m);
        p.getInventory().setItem(4, spikeItem);
    }

    private void reviveAll() {
        attackers.reviveAll();
        defenders.reviveAll();
        getAllPlayers().forEach(p -> {
            playerHealth.put(p.getUniqueId(), 100);
            playerShield.put(p.getUniqueId(), 0);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.setGameMode(GameMode.ADVENTURE);
        });
    }

    private void teleportToSpawns() {
        List<Player> atks = attackers.getOnlinePlayers();
        List<Player> defs = defenders.getOnlinePlayers();
        for (int i = 0; i < atks.size(); i++) {
            Location loc = i < attackSpawns.size() ? attackSpawns.get(i) :
                    atks.get(i).getWorld().getSpawnLocation();
            atks.get(i).teleport(loc);
        }
        for (int i = 0; i < defs.size(); i++) {
            Location loc = i < defendSpawns.size() ? defendSpawns.get(i) :
                    defs.get(i).getWorld().getSpawnLocation();
            defs.get(i).teleport(loc);
        }
    }

    private void loadMap(String name) {
        MapManager.ValorantMap map = plugin.getMapManager().getMap(name);
        if (map == null) {
            plugin.getLogger().warning("Map not found: " + name);
            return;
        }
        plugin.getMapManager().ensureBuilt(map);
        attackSpawns.addAll(map.getAttackSpawns());
        defendSpawns.addAll(map.getDefendSpawns());
        siteALocations.addAll(map.getSiteA());
        siteBLocations.addAll(map.getSiteB());
        broadcast(ValorantMC.colorize("&a&lMap: &f" + map.getDisplayName()));
    }

    // ── Scoreboard ────────────────────────────────────────────────────────────

    private void setupScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        objective  = scoreboard.registerNewObjective("valorantmc", Criteria.DUMMY,
                ValorantMC.colorize("&c&lVALORANT&r &b" +
                        (attackers.getRoundWins()) + " - " + (defenders.getRoundWins())));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public void updateScoreboard() {
        // Clear and rebuild
        scoreboard.getEntries().forEach(scoreboard::resetScores);

        int line = 15;
        setScore(ValorantMC.colorize("&r"), line--);
        setScore(ValorantMC.colorize("&cAttackers: &f" + attackers.getRoundWins()), line--);
        setScore(ValorantMC.colorize("&bDefenders: &f" + defenders.getRoundWins()), line--);
        setScore(ValorantMC.colorize("&r "), line--);
        setScore(ValorantMC.colorize("&eRound: &f" + currentRound + "/" + maxRounds), line--);
        setScore(ValorantMC.colorize("&r  "), line--);

        // Team alive
        long aAlive = attackers.getOnlinePlayers().stream()
                .filter(p -> !attackers.isDead(p)).count();
        long dAlive = defenders.getOnlinePlayers().stream()
                .filter(p -> !defenders.isDead(p)).count();
        setScore(ValorantMC.colorize("&cAtk alive: &f" + aAlive + "/" + attackers.size()), line--);
        setScore(ValorantMC.colorize("&bDef alive: &f" + dAlive + "/" + defenders.size()), line--);
        setScore(ValorantMC.colorize("&r   "), line--);
        setScore(ValorantMC.colorize("&8valorantmc"), line);

        // Update title
        objective.setDisplayName(ValorantMC.colorize("&c" + attackers.getRoundWins()
                + " &7| &bVALORANT&7 | &c" + defenders.getRoundWins()));

        // Push to all players
        getAllPlayers().forEach(p -> p.setScoreboard(scoreboard));
    }

    private void setScore(String entry, int score) {
        objective.getScore(entry).setScore(score);
    }

    // ── BossBar ───────────────────────────────────────────────────────────────

    private void startBossBar(BossBar.Color color, String text, float progress) {
        if (bossBar != null) getAllPlayers().forEach(p -> p.hideBossBar(bossBar));
        bossBar = BossBar.bossBar(Component.text(text), progress, color, BossBar.Overlay.PROGRESS);
        getAllPlayers().forEach(p -> p.showBossBar(bossBar));
    }

    private void updateBossBar(String text, float progress) {
        if (bossBar == null) return;
        bossBar.name(Component.text(text));
        bossBar.progress(Math.max(0, Math.min(1, progress)));
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    public void broadcast(String msg) {
        getAllPlayers().forEach(p -> p.sendMessage(msg));
    }

    public void broadcastExcept(Player exclude, String msg) {
        getAllPlayers().stream()
                .filter(p -> !p.getUniqueId().equals(exclude.getUniqueId()))
                .forEach(p -> p.sendMessage(msg));
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private String formatLoc(Location loc) {
        return "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String         getId()           { return id;          }
    public GameState      getState()        { return state;       }
    public ValorantTeam   getAttackers()    { return attackers;   }
    public ValorantTeam   getDefenders()    { return defenders;   }
    public Spike          getSpike()        { return spike;       }
    public int            getCurrentRound() { return currentRound;}
    public String         getMapName()      { return mapName;     }

    public ValorantTeam getTeam(Player p) {
        if (attackers.contains(p)) return attackers;
        if (defenders.contains(p)) return defenders;
        return null;
    }

    public ValorantTeam getEnemyTeam(Player p) {
        if (attackers.contains(p)) return defenders;
        if (defenders.contains(p)) return attackers;
        return null;
    }

    public boolean isInGame(Player p) { return getTeam(p) != null; }

    public List<Player> getAllPlayers() {
        List<Player> all = new ArrayList<>();
        all.addAll(attackers.getOnlinePlayers());
        all.addAll(defenders.getOnlinePlayers());
        return all;
    }

    public int getHealth(Player p)  { return playerHealth.getOrDefault(p.getUniqueId(), 100); }
    public int getShield(Player p)  { return playerShield.getOrDefault(p.getUniqueId(), 0);   }

    public void setShield(Player p, int shield) {
        playerShield.put(p.getUniqueId(), Math.min(shield, 50));
    }

    public void setHeavyShield(Player p) {
        playerShield.put(p.getUniqueId(), 50);
    }

    public Agent  getAgent(Player p)  { return playerAgents.get(p.getUniqueId());  }
    public void   setAgent(Player p, Agent a) { playerAgents.put(p.getUniqueId(), a); }
    public Weapon getWeapon(Player p) { return playerWeapons.get(p.getUniqueId()); }
    public void   setWeapon(Player p, Weapon w) { playerWeapons.put(p.getUniqueId(), w); }

    public List<Location> getSiteALocations() { return siteALocations; }
    public List<Location> getSiteBLocations() { return siteBLocations; }
}
