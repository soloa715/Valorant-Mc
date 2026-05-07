package com.valorantmc.mod.server;

import com.valorantmc.mod.BuyMenuPayload;
import com.valorantmc.mod.HudPayload;
import com.valorantmc.mod.RadarPayload;
import com.valorantmc.mod.ScoreboardPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class ValorantGame {

    // ── Phase durations (ticks at 20/s) ───────────────────────────────────────
    private static final int AGENT_SELECT_TICKS = 20 * 20;
    private static final int BUY_PHASE_TICKS    = 20 * 15;
    private static final int ROUND_TICKS        = 20 * 100;
    private static final int ROUND_END_TICKS    = 20 * 5;
    private static final int SPIKE_TICKS        = 20 * 45;
    private static final int ROUNDS_TO_WIN      = 13;
    private static final int HALF_TIME_ROUND    = 12;

    private final String id;
    private GameState state = GameState.WAITING;
    private int currentRound = 0;
    private int phaseTicks   = 0;
    private boolean isOvertime = false;
    private final int maxRounds = 25;

    // ── Teams ─────────────────────────────────────────────────────────────────
    private final ValorantTeam attackers;
    private final ValorantTeam defenders;
    private final Set<UUID> lastRoundWinners = new HashSet<>();

    // ── Per-player state ──────────────────────────────────────────────────────
    private final Map<UUID, Integer> playerHealth  = new HashMap<>();
    private final Map<UUID, Integer> playerShield  = new HashMap<>();
    private final Map<UUID, String>  playerAgents  = new HashMap<>();
    // per player: category → weapon (primary/sidearm/melee each tracked separately)
    private final Map<UUID, Map<WeaponCategory, Weapon>> playerWeapons = new HashMap<>();
    private final Map<UUID, Integer> shotCooldown  = new HashMap<>();
    private final Map<UUID, Integer> reloadTimer   = new HashMap<>();

    // ── Economy ───────────────────────────────────────────────────────────────
    private final EconomyManager economy = new EconomyManager();

    // ── Per-player match stats ────────────────────────────────────────────────
    private final Map<UUID, Integer> statKills   = new HashMap<>();
    private final Map<UUID, Integer> statDeaths  = new HashMap<>();
    private final Map<UUID, Integer> statAssists = new HashMap<>();

    // ── Spike state ───────────────────────────────────────────────────────────
    private UUID    spikeCarrier    = null;
    private boolean spikePlanted    = false;
    private int     spikeDetonTicks = 0;
    private UUID    spikeDefuser    = null;
    private int     defuseTicks     = 0;
    private Vec3    spikePlantPos   = null;

    // ── Dropped weapons (item entity UUID → weapon type + level) ─────────────
    private final Map<UUID, net.minecraft.world.entity.item.ItemEntity> droppedWeapons = new LinkedHashMap<>();

    // ── Kill feed ─────────────────────────────────────────────────────────────
    private final Map<UUID, String> pendingKillFeed = new HashMap<>();

    // ── Boss bar ──────────────────────────────────────────────────────────────
    private ServerBossEvent bossBar;

    // ── HUD tick counter ──────────────────────────────────────────────────────
    private int hudTick    = 0;
    private int radarTick  = 0;

    // ── Spawns ────────────────────────────────────────────────────────────────
    private final List<Vec3> attackerSpawns = new ArrayList<>();
    private final List<Vec3> defenderSpawns = new ArrayList<>();

    public ValorantGame(String id) {
        this.id        = id;
        this.attackers = new ValorantTeam(ValorantTeam.Side.ATTACKERS);
        this.defenders = new ValorantTeam(ValorantTeam.Side.DEFENDERS);
    }

    // ── Player join / leave ───────────────────────────────────────────────────

    public void addPlayer(ServerPlayer p) {
        if (attackers.size() <= defenders.size()) attackers.addPlayer(p);
        else defenders.addPlayer(p);

        playerHealth.put(p.getUUID(), 100);
        playerShield.put(p.getUUID(), 0);
        economy.initPlayer(p.getUUID());
        p.setGameMode(GameType.ADVENTURE);
        p.sendSystemMessage(Component.literal("§aJoined game §e" + id + " §7as §f"
                + getTeam(p).getDisplayName()));
    }

    public void removePlayer(ServerPlayer p) {
        attackers.removePlayer(p);
        defenders.removePlayer(p);
        AgentAbilities.cleanup(p.getUUID());
        playerAgents.remove(p.getUUID());
        playerWeapons.remove(p.getUUID());
        shotCooldown.remove(p.getUUID());
        reloadTimer.remove(p.getUUID());
        playerHealth.remove(p.getUUID());
        playerShield.remove(p.getUUID());
        if (bossBar != null) bossBar.removePlayer(p);
        p.setGameMode(GameType.SURVIVAL);
        p.setHealth(p.getMaxHealth());
        p.getInventory().clearContent();
    }

    // ── Game start ────────────────────────────────────────────────────────────

    public void start(MinecraftServer server) {
        if (state != GameState.WAITING) return;

        // Load map spawn points from MapManager (resolves votes + saved configs)
        if (attackerSpawns.isEmpty()) {
            MapManager mm = MapManager.getInstance();
            String chosenMap = mm.resolveVote();
            MapManager.MapSpawnConfig cfg = mm.getActiveConfig();
            attackerSpawns.addAll(cfg.attackerSpawns);
            defenderSpawns.addAll(cfg.defenderSpawns);
            broadcast(server, "§7Map: §e" + chosenMap + " §8(" + attackerSpawns.size() + " atk / " + defenderSpawns.size() + " def spawns)");
            if (attackerSpawns.stream().anyMatch(v -> v.y < 100)) {
                // Spawns look like arena coords — build the fallback platform
                ArenaManager.buildArena(server);
            }
        }

        state      = GameState.AGENT_SELECT;
        phaseTicks = AGENT_SELECT_TICKS;

        List<String> agentList = java.util.Arrays.asList(
                "Jett","Reyna","Raze","Phoenix","Sova","Sage",
                "Killjoy","Cypher","Omen","Viper","Brimstone","Breach",
                "Neon","Skye","Chamber","Fade","Gekko"
        );

        broadcast(server, "§e§lVALORANTMC §r§7— Agent select! Press §bN §7or type §b/vagent <name>§7.");
        sendTitle(server, "§6AGENT SELECT", "§7Choose your agent — " + (AGENT_SELECT_TICKS / 20) + "s", 10, 80, 20);
        startBossBar(server, BossEvent.BossBarColor.PURPLE, "AGENT SELECT — " + formatTime(AGENT_SELECT_TICKS / 20), 1f);

        getAllPlayers(server).forEach(p -> {
            p.setGameMode(GameType.ADVENTURE);
            p.getInventory().clearContent();
            String myAgent = playerAgents.getOrDefault(p.getUUID(), "");
            ServerPlayNetworking.send(p, new com.valorantmc.mod.AgentSelectPayload(agentList, myAgent));
        });
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        if (state == GameState.WAITING || state == GameState.GAME_OVER) return;

        shotCooldown.replaceAll((k, v) -> Math.max(0, v - 1));

        List<UUID> doneReloading = new ArrayList<>();
        reloadTimer.forEach((uuid, ticks) -> {
            if (ticks <= 1) doneReloading.add(uuid);
            else reloadTimer.put(uuid, ticks - 1);
        });
        doneReloading.forEach(uuid -> {
            reloadTimer.remove(uuid);
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                Weapon w = getHeldWeapon(p);
                if (w != null && !w.getType().isMelee()) {
                    w.setReloading(false);
                    w.reload();
                    p.displayClientMessage(Component.literal("§aReload complete!"), true);
                }
            }
        });

        phaseTicks--;

        switch (state) {
            case AGENT_SELECT -> tickAgentSelect(server);
            case BUY_PHASE    -> tickBuyPhase(server);
            case ROUND_ACTIVE -> tickRoundActive(server);
            case ROUND_END    -> tickRoundEnd(server);
            default           -> {}
        }

        if (++hudTick >= 4) {
            hudTick = 0;
            sendHudToAll(server);
        }
        if (++radarTick >= 40) {
            radarTick = 0;
            sendRadarToAll(server);
        }
        if (++pickupTick >= 5) {
            pickupTick = 0;
            checkWeaponPickups(server);
        }
    }

    // ── Phase ticks ───────────────────────────────────────────────────────────

    private void tickAgentSelect(MinecraftServer server) {
        int sec = phaseTicks / 20;
        if (phaseTicks % 20 == 0) {
            updateBossBar("AGENT SELECT — " + formatTime(sec), (float) phaseTicks / AGENT_SELECT_TICKS);
            getAllPlayers(server).forEach(p -> {
                boolean picked = playerAgents.containsKey(p.getUUID());
                p.displayClientMessage(Component.literal(picked
                        ? "§aReady — waiting for others (" + sec + "s)"
                        : "§c§lPICK AN AGENT §7— /vagent <name> (" + sec + "s)"), true);
            });
        }
        boolean everyonePicked = getAllUuids().stream().allMatch(playerAgents::containsKey);
        if (everyonePicked || phaseTicks <= 0) {
            String[] defaultAgents = {"Jett","Reyna","Raze","Phoenix","Sova","Sage","Killjoy","Cypher","Omen","Viper"};
            Random rng = new Random();
            getAllPlayers(server).forEach(p -> {
                if (!playerAgents.containsKey(p.getUUID())) {
                    String agent = defaultAgents[rng.nextInt(defaultAgents.length)];
                    playerAgents.put(p.getUUID(), agent);
                    p.sendSystemMessage(Component.literal("§eAuto-assigned agent: §b" + agent));
                }
            });
            enterBuyPhase(server);
        }
    }

    private void tickBuyPhase(MinecraftServer server) {
        int sec = phaseTicks / 20;
        if (phaseTicks % 20 == 0) {
            updateBossBar("ROUND " + currentRound + " • BUY PHASE — " + formatTime(sec),
                    (float) phaseTicks / BUY_PHASE_TICKS);
            if (sec > 0) {
                getAllPlayers(server).forEach(p ->
                        p.displayClientMessage(Component.literal("§e◼ BUY PHASE §8| §7" + sec + "s remaining"), true));
            }
        }
        if (phaseTicks <= 0) enterRoundActive(server);
    }

    private void tickRoundActive(MinecraftServer server) {
        if (spikePlanted && spikeDetonTicks > 0) {
            spikeDetonTicks--;
            if (spikeDetonTicks <= 0) {
                endRound(server, attackers.getSide(), "§c§lSPIKE DETONATED §r§7— Attackers win!");
                return;
            }
        }

        if (spikeDefuser != null) {
            defuseTicks--;
            if (defuseTicks <= 0) {
                spikePlanted   = false;
                UUID defuserUuid = spikeDefuser;
                spikeDefuser   = null;
                if (defuserUuid != null) {
                    String defuserAgent = playerAgents.get(defuserUuid);
                    if (defuserAgent != null) AgentAbilities.addUltPoint(defuserUuid, defuserAgent);
                }
                endRound(server, defenders.getSide(), "§b§lSPIKE DEFUSED §r§7— Defenders win!");
                return;
            }
        }

        int sec = phaseTicks / 20;
        if (phaseTicks % 20 == 0) {
            String label = spikePlanted
                    ? "§cSPIKE PLANTED — " + formatTime(spikeDetonTicks / 20)
                    : "ROUND " + currentRound + " — " + formatTime(sec);
            updateBossBar(label, spikePlanted
                    ? (float) spikeDetonTicks / SPIKE_TICKS
                    : (float) phaseTicks / ROUND_TICKS);
        }

        if (attackers.isEliminated()) {
            endRound(server, defenders.getSide(), "§b§lDEFENDERS WIN §r§7— All attackers eliminated!");
            return;
        }
        if (defenders.isEliminated()) {
            endRound(server, attackers.getSide(), "§c§lATTACKERS WIN §r§7— All defenders eliminated!");
            return;
        }

        if (phaseTicks <= 0 && !spikePlanted) {
            endRound(server, defenders.getSide(), "§b§lTIME UP §r§7— Defenders win!");
        }
    }

    private void tickRoundEnd(MinecraftServer server) {
        if (phaseTicks <= 0) {
            if (attackers.getRoundWins() >= ROUNDS_TO_WIN || defenders.getRoundWins() >= ROUNDS_TO_WIN
                    || currentRound >= maxRounds) {
                endGame(server);
            } else {
                if (currentRound == HALF_TIME_ROUND) {
                    attackers.swapSide();
                    defenders.swapSide();
                    broadcast(server, "§e§lHALF TIME — Teams have swapped sides!");
                }
                enterBuyPhase(server);
            }
        }
    }

    // ── Phase transitions ─────────────────────────────────────────────────────

    private void enterBuyPhase(MinecraftServer server) {
        currentRound++;
        state      = GameState.BUY_PHASE;
        phaseTicks = BUY_PHASE_TICKS;

        attackers.reviveAll();
        defenders.reviveAll();
        spikePlanted = false;
        spikeCarrier = null;
        spikeDefuser = null;
        spikePlantPos = null;

        for (UUID uuid : getAllUuids()) {
            boolean won = lastRoundWinners.contains(uuid);
            economy.giveRoundStartCredits(uuid, won, currentRound);
            playerHealth.put(uuid, 100);
            playerShield.put(uuid, 0);
            String agent = playerAgents.get(uuid);
            if (agent != null) AgentAbilities.initRound(uuid, agent);
        }

        walkMode.clear();
        AgentAbilities.cleanupRound(server);
        droppedWeapons.values().forEach(e -> { if (e.isAlive()) e.discard(); });
        droppedWeapons.clear();
        giveStartingWeapons(server);
        teleportToSpawns(server);

        broadcast(server, "§e§lROUND " + currentRound + " §r§7— Buy Phase! (" + BUY_PHASE_TICKS / 20 + "s)");
        sendTitle(server, "§eROUND " + currentRound, "§7BUY PHASE — " + BUY_PHASE_TICKS / 20 + "s", 10, 40, 20);
        startBossBar(server, BossEvent.BossBarColor.YELLOW,
                "ROUND " + currentRound + " • BUY PHASE — " + formatTime(BUY_PHASE_TICKS / 20), 1f);

        getAllPlayers(server).forEach(p -> {
            int credits = economy.getCredits(p.getUUID());
            ServerPlayNetworking.send(p, new BuyMenuPayload(credits, true));
        });
    }

    private void enterRoundActive(MinecraftServer server) {
        state      = GameState.ROUND_ACTIVE;
        phaseTicks = ROUND_TICKS;

        List<ServerPlayer> atks = attackers.getOnlinePlayers(server);
        if (!atks.isEmpty()) {
            spikeCarrier = atks.get(new Random().nextInt(atks.size())).getUUID();
        }

        broadcast(server, "§c§lFIGHT!");
        sendTitle(server, "§cFIGHT!", "§7Round " + currentRound, 5, 40, 15);
        startBossBar(server, BossEvent.BossBarColor.RED,
                "ROUND " + currentRound + " — " + formatTime(ROUND_TICKS / 20), 1f);
    }

    private void endRound(MinecraftServer server, ValorantTeam.Side winningSide, String message) {
        state      = GameState.ROUND_END;
        phaseTicks = ROUND_END_TICKS;

        ValorantTeam winTeam = (winningSide == ValorantTeam.Side.ATTACKERS) ? attackers : defenders;
        winTeam.addRoundWin();

        lastRoundWinners.clear();
        lastRoundWinners.addAll(winTeam.getMembers());

        broadcast(server, message);
        broadcast(server, "§7Score — §cAttackers §f" + attackers.getRoundWins()
                + " §8: §f" + defenders.getRoundWins() + " §bDefenders");

        // Auto-send scoreboard to all players at round end
        List<String> sbRows = buildScoreboardRows(server);
        ScoreboardPayload sb = new ScoreboardPayload(
                sbRows, attackers.getRoundWins(), defenders.getRoundWins(), currentRound, state.name());
        getAllPlayers(server).forEach(p -> ServerPlayNetworking.send(p, sb));

        sendTitle(server,
                winningSide == ValorantTeam.Side.ATTACKERS ? "§cATTACKERS WIN" : "§bDEFENDERS WIN",
                "§f" + attackers.getRoundWins() + " — " + defenders.getRoundWins(),
                10, 60, 20);
        startBossBar(server, BossEvent.BossBarColor.WHITE,
                "ROUND OVER — " + attackers.getRoundWins() + " : " + defenders.getRoundWins(), 1f);
    }

    private void endGame(MinecraftServer server) {
        state = GameState.GAME_OVER;
        ValorantTeam winner = (attackers.getRoundWins() >= defenders.getRoundWins()) ? attackers : defenders;
        broadcast(server, "§6§l" + winner.getDisplayName().toUpperCase() + " WIN THE MATCH!");
        broadcast(server, "§7Final score: §c" + attackers.getRoundWins() + " §8: §b" + defenders.getRoundWins());
        sendTitle(server, "§6" + winner.getDisplayName().toUpperCase() + " WIN",
                "§7" + attackers.getRoundWins() + " — " + defenders.getRoundWins(), 10, 100, 30);
    }

    // ── Damage ────────────────────────────────────────────────────────────────

    public boolean applyDamage(ServerPlayer attacker, ServerPlayer victim,
                               int rawDamage, boolean headshot, boolean legshot) {
        if (state != GameState.ROUND_ACTIVE) return false;
        if (attacker == null || victim == null) return false;
        if (getTeam(attacker) == getTeam(victim)) return false;
        if (attackers.isDead(victim.getUUID()) || defenders.isDead(victim.getUUID())) return false;

        double mult = headshot ? 2.5 : (legshot ? 0.85 : 1.0);
        int dmg = (int)(rawDamage * mult);

        int shield = playerShield.getOrDefault(victim.getUUID(), 0);
        if (shield > 0) {
            int shieldDmg = Math.min(shield, (int)(dmg * 0.7));
            shield -= shieldDmg;
            dmg    -= shieldDmg;
            playerShield.put(victim.getUUID(), shield);
        }

        int hp = playerHealth.getOrDefault(victim.getUUID(), 100) - dmg;
        playerHealth.put(victim.getUUID(), hp);

        String loc = headshot ? "§c[HS]" : (legshot ? "§7[LS]" : "");
        attacker.displayClientMessage(Component.literal("§f" + victim.getName().getString()
                + " §7— §c" + (dmg + (int)(dmg * 0.3)) + "dmg " + loc + " §8(HP:" + Math.max(0, hp) + ")"), true);

        if (hp <= 0) {
            handleKill(attacker, victim, headshot, null);
        }
        return true;
    }

    private void handleKill(ServerPlayer killer, ServerPlayer victim,
                            boolean headshot, MinecraftServer server) {
        getTeamOf(victim.getUUID()).markDead(victim.getUUID());
        playerHealth.put(victim.getUUID(), 0);

        String feedMsg = (killer != null ? killer.getName().getString() : "?")
                + " > " + victim.getName().getString() + (headshot ? " [HS]" : "");
        getAllUuids().forEach(uid -> pendingKillFeed.put(uid, feedMsg));

        dropWeaponEntity(victim);
        victim.setGameMode(GameType.SPECTATOR);
        victim.sendSystemMessage(Component.literal("§cYou were eliminated! §8(press Tab to spectate teammates)"));

        statDeaths.merge(victim.getUUID(), 1, Integer::sum);
        if (killer != null) {
            killer.displayClientMessage(Component.literal("§aKill! §8+" + (headshot ? "HS " : "") + "(+" + 200 + "c)"), true);
            economy.addCredits(killer.getUUID(), 200);
            statKills.merge(killer.getUUID(), 1, Integer::sum);
            String killerAgent = playerAgents.get(killer.getUUID());
            if (killerAgent != null) AgentAbilities.addUltPoint(killer.getUUID(), killerAgent);
        }
    }

    // ── Spike logic ───────────────────────────────────────────────────────────

    // ── Walk mode ─────────────────────────────────────────────────────────────

    private final Set<UUID> walkMode = new HashSet<>();

    public void toggleWalk(ServerPlayer player) {
        if (walkMode.contains(player.getUUID())) {
            walkMode.remove(player.getUUID());
            player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7Walk: §cOFF"), true);
        } else {
            walkMode.add(player.getUUID());
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, Integer.MAX_VALUE, 0, false, false));
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7Walk: §aON"), true);
        }
    }

    public void clearWalk(UUID uuid) {
        walkMode.remove(uuid);
    }

    // ── Spike plant ───────────────────────────────────────────────────────────

    public void plantSpike(ServerPlayer player, MinecraftServer server) {
        if (!attackers.contains(player)) return;
        if (spikePlanted) return;

        // Bomb site validation (skip if no sites are configured)
        BombSiteManager bsm = BombSiteManager.getInstance();
        if (bsm.hasSites()) {
            String site = bsm.getSiteAt(player.position());
            if (site == null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§cYou must be in a bomb site (A or B) to plant!"), true);
                return;
            }
            broadcast(server, "§c§lSPIKE BEING PLANTED §8— §eSite " + site);
        }

        spikeCarrier  = null;
        spikePlanted  = true;
        spikeDetonTicks = SPIKE_TICKS;
        spikePlantPos = player.position();

        broadcast(server, "§c§lSPIKE PLANTED at §f" + formatPos(player.position()));
        sendTitle(server, "§cSPIKE PLANTED", "§7Defuse it!", 5, 40, 15);

        String planterAgent = playerAgents.get(player.getUUID());
        if (planterAgent != null) AgentAbilities.addUltPoint(player.getUUID(), planterAgent);
    }

    public void startDefuse(ServerPlayer player) {
        if (!defenders.contains(player) || !spikePlanted) return;
        spikeDefuser = player.getUUID();
        defuseTicks  = 20 * 7;
        player.displayClientMessage(Component.literal("§bDefusing spike... don't move!"), true);
    }

    public void cancelDefuse(UUID uuid) {
        if (uuid.equals(spikeDefuser)) {
            spikeDefuser = null;
            defuseTicks  = 0;
        }
    }

    // ── Weapon management ─────────────────────────────────────────────────────

    public boolean buyWeapon(ServerPlayer player, WeaponType type) {
        if (state != GameState.BUY_PHASE) {
            player.sendSystemMessage(Component.literal("§cCan only buy during buy phase!"));
            return false;
        }
        int cost = type.getCost();
        if (!economy.canAfford(player.getUUID(), cost)) {
            player.sendSystemMessage(Component.literal("§cNot enough credits! Need §f" + cost + "c"));
            return false;
        }
        economy.spend(player.getUUID(), cost);
        Weapon weapon = new Weapon(type);
        setWeapon(player, weapon);
        player.sendSystemMessage(Component.literal("§aBought §f" + type.getDisplayName()
                + " §8(§6-" + cost + "c§8) — §7Credits: §6" + economy.getCredits(player.getUUID())));
        return true;
    }

    private void setWeapon(ServerPlayer player, Weapon weapon) {
        playerWeapons.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                     .put(weapon.getType().getCategory(), weapon);
        int slot = switch (weapon.getType().getCategory()) {
            case SIDEARM -> 1;
            case MELEE   -> 2;
            default      -> 0;
        };
        player.getInventory().setItem(slot, weapon.toItemStack());
        player.getInventory().selected = slot;
    }

    /** Returns the weapon matching the item currently held in the main hand, or null. */
    private Weapon getHeldWeapon(ServerPlayer player) {
        WeaponType held = Weapon.getWeaponType(player.getMainHandItem());
        if (held == null) return null;
        Map<WeaponCategory, Weapon> ws = playerWeapons.get(player.getUUID());
        return ws != null ? ws.get(held.getCategory()) : null;
    }

    public boolean handleShot(ServerPlayer shooter) {
        if (state != GameState.ROUND_ACTIVE) return false;
        if (isDeadOrSpectator(shooter)) return false;
        int cd = shotCooldown.getOrDefault(shooter.getUUID(), 0);
        if (cd > 0) return false;

        Weapon w = getHeldWeapon(shooter);
        if (w == null || w.getType().isMelee()) return false;
        if (!w.canShoot()) return false;
        if (!w.consumeBullet()) return false;

        shotCooldown.put(shooter.getUUID(), w.getType().getTicksBetweenShots());
        return true;
    }

    public void startReload(ServerPlayer player) {
        if (state != GameState.ROUND_ACTIVE) return;
        Weapon w = getHeldWeapon(player);
        if (w == null || w.getType().isMelee() || w.isReloading()) return;
        w.setReloading(true);
        reloadTimer.put(player.getUUID(), (int) w.getType().getReloadTicks());
        player.displayClientMessage(Component.literal("§7Reloading..."), true);
    }

    // ── Shield ────────────────────────────────────────────────────────────────

    private static final int ABILITY_C_COST = 200;
    private static final int ABILITY_Q_COST = 200;

    public boolean buyAbilityCharge(ServerPlayer player, String slot) {
        if (state != GameState.BUY_PHASE) {
            player.sendSystemMessage(Component.literal("§cBuy phase only!")); return false;
        }
        int cost = slot.equals("C") ? ABILITY_C_COST : ABILITY_Q_COST;
        if (!economy.canAfford(player.getUUID(), cost)) {
            player.sendSystemMessage(Component.literal("§cNeed §f" + cost + "c")); return false;
        }
        economy.spend(player.getUUID(), cost);
        int[] ch = AgentAbilities.getCharges(player.getUUID());
        int idx = slot.equals("C") ? 0 : 1;
        if (ch[idx] < 0) ch[idx] = 0;
        ch[idx]++;
        player.sendSystemMessage(Component.literal("§aBought ability §f" + slot + " §7charge. §8(" + ch[idx] + " total)"));
        return true;
    }

    public boolean buyShield(ServerPlayer player, boolean heavy) {
        if (state != GameState.BUY_PHASE) { player.sendSystemMessage(Component.literal("§cBuy phase only!")); return false; }
        int cost = heavy ? 1000 : 400;
        if (!economy.canAfford(player.getUUID(), cost)) {
            player.sendSystemMessage(Component.literal("§cNeed §f" + cost + "c")); return false;
        }
        economy.spend(player.getUUID(), cost);
        playerShield.put(player.getUUID(), heavy ? 50 : 25);
        player.sendSystemMessage(Component.literal("§aBought " + (heavy ? "Heavy" : "Light") + " Shield!"));
        return true;
    }

    // ── Spawn / setup ─────────────────────────────────────────────────────────

    private void teleportToSpawns(MinecraftServer server) {
        List<ServerPlayer> atks = attackers.getOnlinePlayers(server);
        List<ServerPlayer> defs = defenders.getOnlinePlayers(server);

        for (int i = 0; i < atks.size(); i++) {
            Vec3 sp = attackerSpawns.isEmpty()
                    ? new Vec3(ArenaManager.CENTER_X + i * 2, ArenaManager.FLOOR_Y + 1, ArenaManager.CENTER_Z - 17)
                    : attackerSpawns.get(i % attackerSpawns.size());
            atks.get(i).teleportTo(sp.x, sp.y, sp.z);
        }
        for (int i = 0; i < defs.size(); i++) {
            Vec3 sp = defenderSpawns.isEmpty()
                    ? new Vec3(ArenaManager.CENTER_X + i * 2, ArenaManager.FLOOR_Y + 1, ArenaManager.CENTER_Z + 17)
                    : defenderSpawns.get(i % defenderSpawns.size());
            defs.get(i).teleportTo(sp.x, sp.y, sp.z);
        }
    }

    private void giveStartingWeapons(MinecraftServer server) {
        getAllPlayers(server).forEach(p -> {
            p.getInventory().clearContent();
            playerWeapons.remove(p.getUUID());
            setWeapon(p, new Weapon(WeaponType.KNIFE));
            setWeapon(p, new Weapon(WeaponType.CLASSIC));
            // leave Classic selected (slot 1)
        });
    }

    // ── HUD packet building ───────────────────────────────────────────────────

    private void sendHudToAll(MinecraftServer server) {
        int atkScore = attackers.getRoundWins();
        int defScore = defenders.getRoundWins();
        int roundPhase = switch (state) {
            case BUY_PHASE    -> 1;
            case ROUND_ACTIVE -> 2;
            case ROUND_END    -> 3;
            default           -> 0;
        };
        int spikeState = spikePlanted ? (spikeDefuser != null ? 2 : 1) : 0;
        int spikeTimer = spikePlanted ? spikeDetonTicks : 0;

        StringBuilder roster = new StringBuilder();
        for (UUID uid : getAllUuids()) {
            if (roster.length() > 0) roster.append(",");
            String name = getPlayerName(server, uid);
            int hp    = playerHealth.getOrDefault(uid, 0);
            int sh    = playerShield.getOrDefault(uid, 0);
            String agent = playerAgents.getOrDefault(uid, "?");
            roster.append(name).append(":").append(hp).append(":").append(sh).append(":").append(agent);
        }
        if (roster.length() > 512) roster.setLength(512);

        getAllPlayers(server).forEach(p -> {
            UUID uid    = p.getUUID();
            Weapon w    = getHeldWeapon(p);
            int hp      = playerHealth.getOrDefault(uid, 100);
            int shield  = playerShield.getOrDefault(uid, 0);
            int ammo    = (w != null && !w.getType().isMelee()) ? w.getCurrentAmmo() : 0;
            int maxAmmo = (w != null && !w.getType().isMelee()) ? w.getType().getMagazineSize() : 0;
            int reserve = (w != null && !w.getType().isMelee()) ? w.getReserveAmmo() : 0;
            String agent = playerAgents.getOrDefault(uid, "");
            int credits = economy.getCredits(uid);
            String kf = pendingKillFeed.remove(uid);
            if (kf == null) kf = "";
            if (kf.length() > 64) kf = kf.substring(0, 64);

            int[] ch = AgentAbilities.getCharges(uid);
            int chargesC  = ch[0];
            int chargesQ  = ch[1];
            int chargesE  = ch[2];
            int ultPts    = AgentAbilities.getUltPoints(uid);
            int ultMax    = agent.isEmpty() ? 7 : AgentAbilities.getUltMax(agent);

            HudPayload hud = new HudPayload(
                    true, hp, shield, ammo, maxAmmo, reserve,
                    chargesC, chargesQ, chargesE,
                    0, 0, 0,
                    ultPts, ultMax,
                    agent.length() > 64 ? agent.substring(0, 64) : agent,
                    credits, atkScore, defScore,
                    spikeState, spikeTimer, roundPhase,
                    roster.length() > 512 ? roster.substring(0, 512) : roster.toString(),
                    kf
            );
            ServerPlayNetworking.send(p, hud);
        });
    }

    private void sendRadarToAll(MinecraftServer server) {
        if (state != GameState.ROUND_ACTIVE && state != GameState.BUY_PHASE) return;

        getAllPlayers(server).forEach(self -> {
            Vec3 selfPos = self.position();
            float selfYaw = self.getYRot();
            StringBuilder sb = new StringBuilder();
            sb.append((int) selfPos.x).append(":").append((int) selfPos.z).append(":").append((int) selfYaw);

            getAllPlayers(server).forEach(other -> {
                if (other == self) return;
                boolean ally = getTeam(self) == getTeam(other);
                String tag = ally ? "A" : "E";
                sb.append("|").append(other.getName().getString())
                        .append(":").append((int) other.position().x)
                        .append(":").append((int) other.position().z)
                        .append(":").append(tag);
            });

            String data = sb.toString();
            if (data.length() > 512) data = data.substring(0, 512);
            ServerPlayNetworking.send(self, new RadarPayload(data));
        });
    }

    // ── Boss bar helpers ──────────────────────────────────────────────────────

    private void startBossBar(MinecraftServer server, BossEvent.BossBarColor color, String label, float pct) {
        if (bossBar != null) getAllPlayers(server).forEach(bossBar::removePlayer);
        bossBar = new ServerBossEvent(Component.literal(label),
                color, BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setProgress(pct);
        getAllPlayers(server).forEach(bossBar::addPlayer);
    }

    private void updateBossBar(String label, float pct) {
        if (bossBar == null) return;
        bossBar.setName(Component.literal(label));
        bossBar.setProgress(Math.max(0f, Math.min(1f, pct)));
    }

    public void addPlayerToBossBar(ServerPlayer p) {
        if (bossBar != null) bossBar.addPlayer(p);
    }

    // ── Broadcast / title helpers ─────────────────────────────────────────────

    public void broadcast(MinecraftServer server, String msg) {
        getAllPlayers(server).forEach(p -> p.sendSystemMessage(Component.literal(msg)));
    }

    private void sendTitle(MinecraftServer server, String title, String subtitle,
                           int fadeIn, int stay, int fadeOut) {
        getAllPlayers(server).forEach(p -> {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
        });
    }

    // ── Agent selection ───────────────────────────────────────────────────────

    public boolean selectAgent(ServerPlayer p, String agentName) {
        Set<String> knownAgents = Set.of(
                "jett","reyna","raze","phoenix","sova","sage",
                "killjoy","cypher","omen","viper","brimstone","breach",
                "neon","skye","chamber","fade","gekko"
        );
        String lower = agentName.toLowerCase();
        if (!knownAgents.contains(lower)) return false;

        String canonical = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        playerAgents.put(p.getUUID(), canonical);
        p.sendSystemMessage(Component.literal("§aAgent selected: §b" + canonical));
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public ValorantTeam getTeam(ServerPlayer p) {
        return getTeamOf(p.getUUID());
    }

    public ValorantTeam getTeamOf(UUID uuid) {
        return attackers.contains(uuid) ? attackers : defenders;
    }

    public ValorantTeam getEnemyTeam(ServerPlayer p) {
        return attackers.contains(p) ? defenders : attackers;
    }

    public boolean isDeadOrSpectator(ServerPlayer p) {
        return attackers.isDead(p.getUUID()) || defenders.isDead(p.getUUID());
    }

    public boolean isInGame(UUID uuid) {
        return attackers.contains(uuid) || defenders.contains(uuid);
    }

    public Set<UUID> getAllUuids() {
        Set<UUID> all = new HashSet<>();
        all.addAll(attackers.getMembers());
        all.addAll(defenders.getMembers());
        return all;
    }

    public List<ServerPlayer> getAllPlayers(MinecraftServer server) {
        List<ServerPlayer> list = new ArrayList<>();
        list.addAll(attackers.getOnlinePlayers(server));
        list.addAll(defenders.getOnlinePlayers(server));
        return list;
    }

    private String getPlayerName(MinecraftServer server, UUID uuid) {
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        return p != null ? p.getName().getString() : uuid.toString().substring(0, 8);
    }

    public void shutdown(MinecraftServer server) {
        if (bossBar != null) getAllPlayers(server).forEach(bossBar::removePlayer);
        getAllPlayers(server).forEach(this::removePlayer);
        state = GameState.GAME_OVER;
    }

    private static String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    private static String formatPos(Vec3 v) {
        return (int) v.x + "," + (int) v.y + "," + (int) v.z;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String       getId()            { return id;                              }
    public GameState    getState()         { return state;                           }
    public int          getCurrentRound()  { return currentRound;                    }
    public ValorantTeam getAttackers()     { return attackers;                       }
    public ValorantTeam getDefenders()     { return defenders;                       }
    public EconomyManager getEconomy()     { return economy;                         }
    public int          getHealth(UUID u)  { return playerHealth.getOrDefault(u, 0); }
    public int          getShield(UUID u)  { return playerShield.getOrDefault(u, 0); }
    public String       getAgent(UUID u)   { return playerAgents.get(u);             }
    public Weapon getWeapon(UUID u) {
        Map<WeaponCategory, Weapon> ws = playerWeapons.get(u);
        if (ws == null) return null;
        // return first non-melee weapon found, fallback to melee
        for (WeaponCategory cat : new WeaponCategory[]{
                WeaponCategory.RIFLE, WeaponCategory.SMG, WeaponCategory.SNIPER,
                WeaponCategory.HEAVY, WeaponCategory.SHOTGUN, WeaponCategory.SIDEARM}) {
            if (ws.containsKey(cat)) return ws.get(cat);
        }
        return ws.get(WeaponCategory.MELEE);
    }
    public boolean      isSpikePlanted()   { return spikePlanted;                    }
    public UUID         getSpikeCarrier()  { return spikeCarrier;                    }

    private void dropWeaponEntity(ServerPlayer victim) {
        Map<WeaponCategory, Weapon> ws = playerWeapons.get(victim.getUUID());
        if (ws == null) return;
        // prefer dropping the primary weapon (rifle/smg/sniper/heavy/shotgun)
        Weapon toDrop = null;
        for (WeaponCategory cat : new WeaponCategory[]{
                WeaponCategory.RIFLE, WeaponCategory.SMG, WeaponCategory.SNIPER,
                WeaponCategory.HEAVY, WeaponCategory.SHOTGUN}) {
            if (ws.containsKey(cat)) { toDrop = ws.get(cat); break; }
        }
        if (toDrop == null || toDrop.getType().isMelee()) return;
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
                victim.serverLevel(), victim.getX(), victim.getY() + 0.5, victim.getZ(), toDrop.toItemStack());
        item.setPickUpDelay(40);
        victim.serverLevel().addFreshEntity(item);
        droppedWeapons.put(item.getUUID(), item);
    }

    private int pickupTick = 0;
    private void checkWeaponPickups(MinecraftServer server) {
        if (state != GameState.ROUND_ACTIVE || droppedWeapons.isEmpty()) return;
        droppedWeapons.entrySet().removeIf(e -> !e.getValue().isAlive());

        getAllPlayers(server).forEach(p -> {
            if (isDeadOrSpectator(p)) return;
            droppedWeapons.forEach((uid, ent) -> {
                if (!ent.isAlive()) return;
                if (p.distanceTo(ent) > 1.5) return;
                net.minecraft.world.item.ItemStack stack = ent.getItem();
                WeaponType wt = Weapon.getWeaponType(stack);
                if (wt == null || wt.isMelee()) return;
                Map<WeaponCategory, Weapon> ws = playerWeapons.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
                if (ws.containsKey(wt.getCategory())) return; // already has one in this slot
                setWeapon(p, new Weapon(wt));
                ent.discard();
                p.displayClientMessage(Component.literal("§a⬆ Picked up §f" + wt.getDisplayName()), true);
            });
        });
    }

    public void giveWeapon(ServerPlayer player, Weapon weapon) { setWeapon(player, weapon); }
    public void heal(UUID uuid, int amount) {
        int hp = Math.min(100, playerHealth.getOrDefault(uuid, 0) + amount);
        playerHealth.put(uuid, hp);
    }

    public void addAttackerSpawn(Vec3 v) { attackerSpawns.add(v); }
    public void addDefenderSpawn(Vec3 v) { defenderSpawns.add(v); }

    /** All online players on the given team, resolved via a known online player's server. */
    public List<ServerPlayer> getTeamPlayersVia(ValorantTeam team, ServerPlayer via) {
        List<ServerPlayer> result = new ArrayList<>();
        for (UUID uid : team.getMembers()) {
            ServerPlayer found = via.getServer().getPlayerList().getPlayer(uid);
            if (found != null && !found.isSpectator()) result.add(found);
        }
        return result;
    }

    /** All online enemy players for the given player's perspective. */
    public List<ServerPlayer> getEnemyPlayers(ServerPlayer p) {
        ValorantTeam enemy = getEnemyTeam(p);
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer e : p.getServer().getPlayerList().getPlayers()) {
            if (enemy.contains(e.getUUID()) && !e.isSpectator()) result.add(e);
        }
        return result;
    }

    // ── Admin helpers ─────────────────────────────────────────────────────────

    public void adminSetHealth(UUID uuid, int hp) {
        playerHealth.put(uuid, Math.min(100, Math.max(0, hp)));
    }

    public void adminSetShield(UUID uuid, int shield) {
        playerShield.put(uuid, Math.min(50, Math.max(0, shield)));
    }

    public void adminKill(ServerPlayer victim) {
        handleKill(null, victim, false, null);
    }

    public void adminRevive(ServerPlayer p) {
        getTeamOf(p.getUUID()).revive(p.getUUID());
        playerHealth.put(p.getUUID(), 100);
        playerShield.put(p.getUUID(), 0);
        p.setGameMode(GameType.ADVENTURE);
        p.sendSystemMessage(Component.literal("§a§lRevived §r§7by admin!"));
    }

    public void adminEndRound(MinecraftServer server, boolean atkWins) {
        if (state != GameState.ROUND_ACTIVE && state != GameState.BUY_PHASE) return;
        endRound(server,
                atkWins ? ValorantTeam.Side.ATTACKERS : ValorantTeam.Side.DEFENDERS,
                atkWins ? "§c§lAdmin §7forced §cATTACKERS §7win!" : "§b§lAdmin §7forced §bDEFENDERS §7win!");
    }

    public void adminSkipBuyPhase() {
        if (state == GameState.BUY_PHASE) phaseTicks = 0;
    }

    public void adminForceEndGame(MinecraftServer server) { endGame(server); }

    public void adminReviveAll(MinecraftServer server) {
        attackers.reviveAll();
        defenders.reviveAll();
        getAllPlayers(server).forEach(p -> {
            adminSetHealth(p.getUUID(), 100);
            p.setGameMode(GameType.ADVENTURE);
            p.sendSystemMessage(Component.literal("§a§lRevived §r§7by admin!"));
        });
    }

    public void adminRefillAllAmmo(MinecraftServer server) {
        getAllPlayers(server).forEach(p -> {
            Map<WeaponCategory, Weapon> ws = playerWeapons.get(p.getUUID());
            if (ws != null) ws.values().forEach(w -> {
                w.reload();
                p.getInventory().setItem(
                        switch (w.getType().getCategory()) { case SIDEARM -> 1; case MELEE -> 2; default -> 0; },
                        w.toItemStack());
            });
        });
    }

    public List<Vec3> getAttackerSpawnsCopy() { return Collections.unmodifiableList(attackerSpawns); }
    public List<Vec3> getDefenderSpawnsCopy() { return Collections.unmodifiableList(defenderSpawns); }

    /** Build scoreboard row strings: "name:agent:kills:deaths:assists:credits:team:alive" */
    public List<String> buildScoreboardRows(MinecraftServer server) {
        List<String> rows = new ArrayList<>();
        for (UUID uid : getAllUuids()) {
            String name   = getPlayerName(server, uid);
            String agent  = playerAgents.getOrDefault(uid, "?");
            int kills     = statKills.getOrDefault(uid, 0);
            int deaths    = statDeaths.getOrDefault(uid, 0);
            int assists   = statAssists.getOrDefault(uid, 0);
            int credits   = economy.getCredits(uid);
            String team   = attackers.contains(uid) ? "ATTACKERS" : "DEFENDERS";
            boolean alive = !attackers.isDead(uid) && !defenders.isDead(uid);
            rows.add(name + ":" + agent + ":" + kills + ":" + deaths + ":" + assists
                    + ":" + credits + ":" + team + ":" + (alive ? "1" : "0"));
        }
        return rows;
    }

    /** Revive one dead ally closest to the given player. Returns list of revived players. */
    public List<ServerPlayer> reviveDeadAlly(ServerPlayer caster) {
        ValorantTeam team = getTeamOf(caster.getUUID());
        List<ServerPlayer> revived = new ArrayList<>();
        for (UUID uid : team.getMembers()) {
            if (team.isDead(uid)) {
                team.revive(uid);
                playerHealth.put(uid, 100);
                playerShield.put(uid, 0);
                ServerPlayer target = caster.getServer().getPlayerList().getPlayer(uid);
                if (target != null) {
                    target.setGameMode(GameType.ADVENTURE);
                    target.sendSystemMessage(Component.literal("§a§lRESURRECTED §r§7by " + caster.getName().getString() + "!"));
                    // Teleport to caster position
                    target.teleportTo(caster.getX(), caster.getY(), caster.getZ());
                    revived.add(target);
                }
                break; // revive only one
            }
        }
        return revived;
    }
}
