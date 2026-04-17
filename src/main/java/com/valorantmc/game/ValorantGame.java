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
import net.kyori.adventure.title.Title;
import java.time.Duration;
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

    // ── Damage tracking for assists ───────────────────────────────────────────
    private final Map<UUID, Set<UUID>> damageContributors = new HashMap<>(); // victim → set of damagers

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
    private BukkitTask agentSelectTask;
    private BukkitTask buyPhaseTask;

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
        if (bossBar != null) p.hideBossBar(bossBar);
        attackers.removePlayer(p);
        defenders.removePlayer(p);
        playerAgents.remove(p.getUniqueId());
        playerWeapons.remove(p.getUniqueId());
        playerHealth.remove(p.getUniqueId());
        playerShield.remove(p.getUniqueId());
        plugin.getWeaponManager().clearPlayer(p);
        if (Bukkit.getScoreboardManager() != null) {
            p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
        if (p.isOnline()) {
            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
            p.setHealth(20);
        }
        // Cancel any in-progress plant/defuse for this player
        if (plugin.getAbilityListener() != null) plugin.getAbilityListener().cancelFor(p);
        updateScoreboard();
        p.sendMessage(plugin.msg("game.leave"));
    }

    /** Start agent-select phase then begin first round */
    public void start(String mapName) {
        // Cancel any leftover timers from a previous run to prevent double-tick races
        if (timerTask       != null) { timerTask.cancel();       timerTask       = null; }
        if (agentSelectTask != null) { agentSelectTask.cancel(); agentSelectTask = null; }
        if (buyPhaseTask    != null) { buyPhaseTask.cancel();    buyPhaseTask    = null; }

        this.mapName = mapName;
        loadMap(mapName);

        state = GameState.AGENT_SELECT;
        broadcast(plugin.msgRaw("game.game-started"));
        broadcast(ValorantMC.colorize("&eSelect your agent with &b/vagent &7(required to proceed!)"));

        // Open agent-select GUI for everyone & teleport to spawns so they can preview
        teleportToSpawns();
        getAllPlayers().forEach(p -> {
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.openInventory(com.valorantmc.shop.AgentSelectGUI.build(p));
            p.sendMessage(ValorantMC.colorize("&a&lPick an agent to join the round!"));
        });

        // Poll until every player has an agent, or 45s cap
        final int maxWaitTicks = 20 * 45;
        final int agentSelectSeconds = 45;
        final int[] elapsed = {0};
        startBossBar(BossBar.Color.PURPLE, "AGENT SELECT — 0:45", 1f);
        if (agentSelectTask != null) agentSelectTask.cancel();
        agentSelectTask = new BukkitRunnable() {
            @Override public void run() {
                elapsed[0] += 20;
                boolean everyonePicked = getAllPlayers().stream()
                        .allMatch(p -> playerAgents.containsKey(p.getUniqueId()));
                int remaining = Math.max(0, (maxWaitTicks - elapsed[0]) / 20);
                updateBossBar("AGENT SELECT — " + formatTime(remaining),
                        (float) remaining / agentSelectSeconds);
                getAllPlayers().forEach(p -> {
                    if (!playerAgents.containsKey(p.getUniqueId())) {
                        p.sendActionBar(ValorantMC.colorize("&c&lPICK AN AGENT! &7(" + remaining + "s)"));
                    } else {
                        p.sendActionBar(ValorantMC.colorize("&aReady — waiting for others (" + remaining + "s)"));
                    }
                });
                if (everyonePicked || elapsed[0] >= maxWaitTicks) {
                    // Auto-assign a random agent to anyone still missing
                    for (Player p : getAllPlayers()) {
                        if (!playerAgents.containsKey(p.getUniqueId())) {
                            com.valorantmc.agents.Agent random =
                                    plugin.getAgentManager().getRandomAgent();
                            if (random != null) {
                                setAgent(p, random);
                                p.sendMessage(ValorantMC.colorize(
                                        "&eAuto-assigned agent: &b" + random.getDisplayName()));
                            }
                        }
                        p.closeInventory();
                    }
                    startBuyPhase();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startBuyPhase() {
        currentRound++;
        state = GameState.BUY_PHASE;
        if (currentRound == 1) plugin.getStatsManager().startMatch(getAllPlayers());

        // Give each player starting credits / round bonus
        giveRoundStartCredits();

        // Restore all agents / health
        reviveAll();
        spike.reset();
        giveStartingWeapons();
        teleportToSpawns();

        int buyDuration = plugin.getConfig().getInt("game.buy-phase-duration", 30);
        broadcast(ValorantMC.colorize("&e&lROUND " + currentRound + " — Buy Phase! &r&7(" + buyDuration + "s)"));
        // Title announcement
        broadcastTitle(
                Component.text("ROUND " + currentRound).color(NamedTextColor.YELLOW),
                Component.text("BUY PHASE — " + buyDuration + "s").color(NamedTextColor.GRAY));
        broadcast(ValorantMC.colorize("&7Use &b/vshop&7 to buy weapons and abilities."));

        updateScoreboard();
        final String buyLabel = "ROUND " + currentRound + " • BUY PHASE — ";
        startBossBar(BossBar.Color.YELLOW, buyLabel + formatTime(buyDuration), 1f);

        // Buy phase countdown
        if (buyPhaseTask != null) buyPhaseTask.cancel();
        buyPhaseTask = new BukkitRunnable() {
            int remaining = buyDuration;
            @Override public void run() {
                if (state != GameState.BUY_PHASE) { cancel(); return; }
                remaining--;
                updateBossBar(buyLabel + formatTime(remaining),
                        Math.max(0f, (float) remaining / Math.max(1, buyDuration)));
                if (remaining <= 0) { startRound(); cancel(); }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startRound() {
        state = GameState.ROUND_ACTIVE;
        int roundDuration = plugin.getConfig().getInt("game.round-duration", 100);
        broadcast(ValorantMC.colorize("&c&lFIGHT!"));
        broadcastTitle(
                Component.text("FIGHT!").color(NamedTextColor.RED),
                Component.text("Round " + currentRound + " of " + maxRounds).color(NamedTextColor.GRAY));

        // Give spike to random attacker
        List<Player> atks = attackers.getOnlinePlayers();
        if (!atks.isEmpty()) {
            Player spikeCarrier = atks.get(new Random().nextInt(atks.size()));
            spike.pickup(spikeCarrier);
            giveSpikeItem(spikeCarrier);
        }

        updateScoreboard();
        final String roundLabel = "ROUND " + currentRound + " • LIVE — ";
        startBossBar(BossBar.Color.RED, roundLabel + formatTime(roundDuration), 1f);

        timerTask = new BukkitRunnable() {
            int remaining = roundDuration;
            @Override public void run() {
                if (state != GameState.ROUND_ACTIVE) { cancel(); return; }
                remaining--;
                if (spike.isPlanted()) {
                    updateBossBar("ROUND " + currentRound + " • SPIKE PLANTED", 1f);
                } else {
                    updateBossBar(roundLabel + formatTime(remaining),
                            Math.max(0f, (float) remaining / Math.max(1, roundDuration)));
                }

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
        // Round-end title
        boolean atkWon = winningSide == ValorantTeam.Side.ATTACKERS;
        Component roundTitle = atkWon
                ? Component.text("ATTACKERS WIN").color(NamedTextColor.RED)
                : Component.text("DEFENDERS WIN").color(NamedTextColor.AQUA);
        Component roundSub = Component.text(attackers.getRoundWins() + " — " + defenders.getRoundWins())
                .color(NamedTextColor.WHITE);
        broadcastTitle(roundTitle, roundSub);

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
        // VICTORY / DEFEAT title per player
        getAllPlayers().forEach(p -> {
            boolean won = getTeam(p) == winner;
            Component endTitle = won
                    ? Component.text("VICTORY").color(NamedTextColor.GOLD)
                    : Component.text("DEFEAT").color(NamedTextColor.RED);
            Component endSub = Component.text(
                    attackers.getRoundWins() + " — " + defenders.getRoundWins())
                    .color(NamedTextColor.WHITE);
            Title.Times t = Title.Times.times(
                    Duration.ofMillis(500), Duration.ofMillis(4000), Duration.ofMillis(800));
            p.showTitle(Title.title(endTitle, endSub, t));
        });

        // Match summary
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            broadcast(ValorantMC.colorize("&6&l╔═══════ MATCH SUMMARY ═══════╗"));
            broadcast(ValorantMC.colorize(String.format("&6&l%-16s %4s %4s %4s %5s", "PLAYER", "K", "D", "A", "HS%")));
            broadcast(ValorantMC.colorize("&8" + "─".repeat(38)));
            for (Player p : getAllPlayers()) {
                com.valorantmc.managers.StatsManager.PlayerStats ms =
                        plugin.getStatsManager().getMatchStats(p.getUniqueId());
                String hs = String.format("%.0f%%", ms.getHSPct());
                String color = getTeam(p) == winner ? "&a" : "&c";
                broadcast(ValorantMC.colorize(String.format(color + "%-16s &f%4d %4d %4d %5s",
                        p.getName(), ms.kills, ms.deaths, ms.assists, hs)));
            }
            broadcast(ValorantMC.colorize("&6&l╚════════════════════════════╝"));
        }, 40L);

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
        if (agentSelectTask != null) agentSelectTask.cancel();
        if (buyPhaseTask != null) buyPhaseTask.cancel();
        spike.reset();
        if (bossBar != null) {
            getAllPlayers().forEach(p -> p.hideBossBar(bossBar));
        }
        if (Bukkit.getScoreboardManager() != null) {
            getAllPlayers().forEach(p ->
                    p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard()));
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
        // Ensure the target is tracked — if they somehow missed addPlayer(), init them now
        playerHealth.putIfAbsent(target.getUniqueId(), 100);
        playerShield.putIfAbsent(target.getUniqueId(), 0);

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

        // Track damage contributors for assists
        if (source != null && hp > 0) {
            damageContributors.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>())
                    .add(source.getUniqueId());
        }

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
        // Phoenix Run It Back — intercept death and respawn at anchor instead
        NamespacedKey ribKey = new NamespacedKey(plugin, "runitback");
        if (victim.getPersistentDataContainer().has(ribKey, org.bukkit.persistence.PersistentDataType.BOOLEAN)) {
            victim.getPersistentDataContainer().remove(ribKey);
            Agent phoenixAgent = playerAgents.get(victim.getUniqueId());
            if (phoenixAgent instanceof com.valorantmc.agents.impl.Phoenix phoenix
                    && phoenix.getRunItBackAnchor() != null) {
                victim.teleport(phoenix.getRunItBackAnchor());
                phoenix.clearAnchor();
                playerHealth.put(victim.getUniqueId(), 50);
                playerShield.put(victim.getUniqueId(), 0);
                victim.setHealth(10);
                victim.sendMessage(ValorantMC.colorize("&6[Phoenix] &fRun It Back! Respawned at anchor."));
                return; // not a real death — skip everything below
            }
        }

        victim.setHealth(20);
        victim.setGameMode(GameMode.SPECTATOR);
        victim.setSpectatorTarget(null); // ensure starts free-floating, not locked to an enemy
        // If the victim was carrying the spike, drop it where they died so a teammate can grab it.
        boolean droppedSpike = spike.getCarrierUUID() != null
                && spike.getCarrierUUID().equals(victim.getUniqueId());
        if (droppedSpike) {
            spike.drop(victim.getLocation());
            victim.getInventory().remove(Material.RED_DYE);
        }
        // Cancel any plant/defuse the victim was performing
        if (plugin.getAbilityListener() != null) plugin.getAbilityListener().cancelFor(victim);

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
            // Assists — anyone who damaged the victim but didn't get the kill
            Set<UUID> contributors = damageContributors.remove(victim.getUniqueId());
            if (contributors != null) {
                final UUID killerUuid = killer.getUniqueId();
                contributors.stream()
                        .filter(id -> !id.equals(killerUuid))
                        .forEach(id -> plugin.getStatsManager().recordAssist(id));
            }
            // Agent kill callbacks — ult points, Reyna soul orbs, Jett dash recharge, etc.
            Agent killerAgent = playerAgents.get(killer.getUniqueId());
            if (killerAgent != null) killerAgent.onKill(killer, victim, this);
        } else {
            killMsg = ValorantMC.colorize("&8" + victim.getName() + " died.");
        }
        broadcast(killMsg);
        plugin.getStatsManager().recordDeath(victim.getUniqueId());
        // Kill feed — show kill notification as action bar to all players for 3s
        String killerColor = (killer != null && getTeam(killer) != null)
                ? (getTeam(killer).getSide() == ValorantTeam.Side.ATTACKERS ? "§c" : "§b") : "§7";
        String victimColor = (getTeam(victim) != null)
                ? (getTeam(victim).getSide() == ValorantTeam.Side.ATTACKERS ? "§c" : "§b") : "§7";
        String feedMsg = killerColor + (killer != null ? killer.getName() : "")
                + " §f✦ " + victimColor + victim.getName()
                + (headshot ? " §c§l[HEADSHOT]" : "");
        getAllPlayers().forEach(p -> p.sendActionBar(net.kyori.adventure.text.Component.text(feedMsg)));
        // ELIMINATED title for victim
        showTitle(victim,
                Component.text("ELIMINATED").color(NamedTextColor.RED),
                Component.text("Wait for next round").color(NamedTextColor.GRAY));

        // Announce if drops spike
        if (droppedSpike) {
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
            // Slot 0 = primary (empty until bought), Slot 1 = Classic, Slot 2 = Knife,
            // Slot 3 = Spike (only for spike carrier — added later), Slots 4-7 = abilities
            Weapon classic = new Weapon(WeaponType.CLASSIC);
            p.getInventory().setItem(1, classic.toItemStack(p.getUniqueId()));
            Weapon knife = new Weapon(WeaponType.KNIFE);
            p.getInventory().setItem(2, knife.toItemStack(p.getUniqueId()));
            // Re-apply abilities for the player's selected agent (slots 4-7)
            Agent agent = playerAgents.get(p.getUniqueId());
            if (agent != null) {
                agent.onRoundStart(p);
                agent.giveAbilityItems(p);
            }
            // Hold the Classic by default so players can immediately shoot
            p.getInventory().setHeldItemSlot(1);
            // Prime WeaponManager state so first shot works
            plugin.getWeaponManager().setHeldWeapon(p, classic);
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
        p.getInventory().setItem(3, spikeItem);
    }

    private void reviveAll() {
        attackers.reviveAll();
        defenders.reviveAll();
        damageContributors.clear();
        getAllPlayers().forEach(p -> {
            playerHealth.put(p.getUniqueId(), 100);
            playerShield.put(p.getUniqueId(), 0);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.clearActivePotionEffects();
            p.setSpectatorTarget(null);
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
        attackSpawns.clear();
        defendSpawns.clear();
        siteALocations.clear();
        siteBLocations.clear();
        attackSpawns.addAll(map.getAttackSpawns());
        defendSpawns.addAll(map.getDefendSpawns());
        siteALocations.addAll(map.getSiteA());
        siteBLocations.addAll(map.getSiteB());
        broadcast(ValorantMC.colorize("&a&lMap: &f" + map.getDisplayName()));
    }

    /** Broadcast a title + subtitle to all players in the game */
    private void broadcastTitle(Component main, Component sub) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(600));
        Title title = Title.title(main, sub, times);
        getAllPlayers().forEach(p -> p.showTitle(title));
    }

    /** Show a personalised title to one player */
    private void showTitle(Player player, Component main, Component sub) {
        Title.Times times = Title.Times.times(
                Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(600));
        player.showTitle(Title.title(main, sub, times));
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
        setScore(ValorantMC.colorize("&c⚔ Atk: &f" + aAlive + "&8/" + attackers.size()), line--);
        setScore(ValorantMC.colorize("&b🛡 Def: &f" + dAlive + "&8/" + defenders.size()), line--);
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

    /** Called by AbilityListener when the spike finishes planting. */
    public void announceSpikePlanted(Location loc) {
        // Title: defenders see urgent warning, attackers see confirmation
        attackers.getOnlinePlayers().forEach(p -> {
            Title.Times t = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(400));
            p.showTitle(Title.title(
                    Component.text("SPIKE PLANTED").color(NamedTextColor.GREEN),
                    Component.text("Defend until detonation!").color(NamedTextColor.GRAY), t));
        });
        defenders.getOnlinePlayers().forEach(p -> {
            Title.Times t = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(400));
            p.showTitle(Title.title(
                    Component.text("SPIKE PLANTED").color(NamedTextColor.RED),
                    Component.text("DEFUSE IT!").color(NamedTextColor.YELLOW), t));
        });
        broadcast(ValorantMC.colorize("&c&lSPIKE PLANTED &7at " + formatLoc(loc) + "!"));
    }

    /** Called by AbilityListener when the spike is defused. */
    public void announceSpikeDefused() {
        broadcastTitle(
                Component.text("SPIKE DEFUSED").color(NamedTextColor.AQUA),
                Component.text("Defenders win the round!").color(NamedTextColor.GRAY));
        broadcast(ValorantMC.colorize("&b&lSPIKE DEFUSED!"));
    }

    // ── Admin controls ────────────────────────────────────────────────────────

    private boolean paused = false;

    public void pause() {
        if (paused) return;
        paused = true;
        if (timerTask != null) { timerTask.cancel(); timerTask = null; }
        broadcast(ValorantMC.colorize("&e&l[ADMIN] &eGame paused."));
        updateBossBar("PAUSED", bossBar != null ? bossBar.progress() : 1f);
    }

    public void resume() {
        if (!paused) return;
        paused = false;
        broadcast(ValorantMC.colorize("&a&l[ADMIN] &aGame resumed."));
        // Re-enter the current state
        if (state == GameState.ROUND_ACTIVE) startRound();
        else if (state == GameState.BUY_PHASE) startBuyPhase();
    }

    public boolean isPaused() { return paused; }

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

    /**
     * Heal a player directly, bypassing shield absorption.
     * HP is capped at 100. Also updates the Minecraft health bar.
     */
    public void heal(Player p, int amount) {
        if (!playerHealth.containsKey(p.getUniqueId())) return;
        int newHp = Math.min(100, playerHealth.get(p.getUniqueId()) + amount);
        playerHealth.put(p.getUniqueId(), newHp);
        p.setHealth(Math.max(0.5, (newHp / 100.0) * 20.0));
    }

    public void setShield(Player p, int shield) {
        playerShield.put(p.getUniqueId(), Math.min(shield, 50));
    }

    public void setHeavyShield(Player p) {
        playerShield.put(p.getUniqueId(), 50);
    }

    public Agent  getAgent(Player p)  { return playerAgents.get(p.getUniqueId());  }
    public void   setAgent(Player p, Agent a) {
        playerAgents.put(p.getUniqueId(), a);
        if (state == GameState.AGENT_SELECT) {
            broadcast(ValorantMC.colorize(
                    (getTeam(p) != null ? getTeam(p).getChatColor() : "") + p.getName()
                    + " &7selected &b" + a.getDisplayName() + "&7!"));
        }
    }
    public Weapon getWeapon(Player p) { return playerWeapons.get(p.getUniqueId()); }
    public void   setWeapon(Player p, Weapon w) { playerWeapons.put(p.getUniqueId(), w); }

    public List<Location> getSiteALocations() { return siteALocations; }
    public List<Location> getSiteBLocations() { return siteBLocations; }
}
