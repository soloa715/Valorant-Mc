package com.valorantmc.mod.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class AgentAbilities {

    private static final Map<UUID, Map<String, Integer>> cooldowns    = new HashMap<>();
    private static final Map<UUID, Integer>              bladeCharges = new HashMap<>();
    private static final Map<UUID, Integer>              dismissTimer = new HashMap<>();
    // per-player active timed effects: key → remaining ticks
    private static final Map<UUID, Integer>              reynaUltTimer    = new HashMap<>();
    private static final Map<UUID, Integer>              phoenixUltTimer  = new HashMap<>();
    private static final Map<UUID, Vec3>                 chamberAnchor    = new HashMap<>();
    private static final Map<UUID, Boolean>              chamberHasAnchor = new HashMap<>();
    private static final Map<UUID, Integer>              neonUltTimer     = new HashMap<>();
    private static final Map<UUID, Integer>              neonSpeedActive  = new HashMap<>();

    // ── Tick (called from ValorantServer.tick) ────────────────────────────────

    public static void tick(net.minecraft.server.MinecraftServer server) {
        cooldowns.values().forEach(m -> m.replaceAll((k, v) -> Math.max(0, v - 1)));

        // Reyna dismiss timer
        dismissTimer.replaceAll((uuid, t) -> Math.max(0, t - 1));
        dismissTimer.forEach((uuid, t) -> {
            if (t == 1) {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null && p.isSpectator()) p.setGameMode(GameType.ADVENTURE);
            }
        });
        dismissTimer.entrySet().removeIf(e -> e.getValue() == 0);

        // Reyna Empress timer
        reynaUltTimer.replaceAll((uuid, t) -> Math.max(0, t - 1));
        reynaUltTimer.forEach((uuid, t) -> {
            if (t == 1) {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) p.displayClientMessage(Component.literal("§dEmpress faded."), true);
            }
        });
        reynaUltTimer.entrySet().removeIf(e -> e.getValue() == 0);

        // Phoenix Run It Back timer
        phoenixUltTimer.replaceAll((uuid, t) -> Math.max(0, t - 1));
        phoenixUltTimer.forEach((uuid, t) -> {
            if (t == 1) {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) {
                    p.setGameMode(GameType.ADVENTURE);
                    p.displayClientMessage(Component.literal("§cRun It Back ended!"), true);
                }
            }
        });
        phoenixUltTimer.entrySet().removeIf(e -> e.getValue() == 0);

        // Neon overdrive timer
        neonUltTimer.replaceAll((uuid, t) -> Math.max(0, t - 1));
        neonUltTimer.forEach((uuid, t) -> {
            if (t == 1) {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) p.displayClientMessage(Component.literal("§bOverdrive ended."), true);
            }
        });
        neonUltTimer.entrySet().removeIf(e -> e.getValue() == 0);
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static boolean use(ServerPlayer player, ValorantGame game, String agent, String key) {
        if (game.getState() != GameState.ROUND_ACTIVE) {
            player.displayClientMessage(Component.literal("§cAbilities only work during a round!"), true);
            return false;
        }
        if (game.isDeadOrSpectator(player)) return false;

        return switch (agent) {
            case "Jett"      -> useJett(player, game, key);
            case "Reyna"     -> useReyna(player, game, key);
            case "Raze"      -> useRaze(player, game, key);
            case "Phoenix"   -> usePhoenix(player, game, key);
            case "Sova"      -> useSova(player, game, key);
            case "Sage"      -> useSage(player, game, key);
            case "Killjoy"   -> useKilljoy(player, game, key);
            case "Cypher"    -> useCypher(player, game, key);
            case "Omen"      -> useOmen(player, game, key);
            case "Viper"     -> useViper(player, game, key);
            case "Brimstone" -> useBrimstone(player, game, key);
            case "Breach"    -> useBreach(player, game, key);
            case "Neon"      -> useNeon(player, game, key);
            case "Skye"      -> useSkye(player, game, key);
            case "Chamber"   -> useChamber(player, game, key);
            case "Fade"      -> useFade(player, game, key);
            case "Gekko"     -> useGekko(player, game, key);
            default -> {
                player.displayClientMessage(
                        Component.literal("§7[" + key + "] " + agent + " abilities coming soon."), true);
                yield true;
            }
        };
    }

    // ── Cooldown helpers ──────────────────────────────────────────────────────

    private static int cd(UUID id, String slot) {
        return cooldowns.computeIfAbsent(id, k -> new HashMap<>()).getOrDefault(slot, 0);
    }

    private static void setcd(UUID id, String slot, int ticks) {
        cooldowns.computeIfAbsent(id, k -> new HashMap<>()).put(slot, ticks);
    }

    private static boolean onCooldown(ServerPlayer p, String slot, String name) {
        int t = cd(p.getUUID(), slot);
        if (t > 0) {
            p.displayClientMessage(
                    Component.literal("§c" + name + " on cooldown! (" + (t / 20) + "s)"), true);
            return true;
        }
        return false;
    }

    // ── AoE helpers ───────────────────────────────────────────────────────────

    private static List<ServerPlayer> enemiesInRadius(ServerPlayer p, ValorantGame game, Vec3 center, double r) {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer e : p.level().getEntitiesOfClass(ServerPlayer.class,
                new AABB(center, center).inflate(r),
                ep -> ep != p && !ep.isSpectator() && game.isInGame(ep.getUUID())
                      && game.getTeam(p) != game.getTeam(ep))) {
            result.add(e);
        }
        return result;
    }

    private static List<ServerPlayer> alliesInRadius(ServerPlayer p, ValorantGame game, Vec3 center, double r) {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer e : p.level().getEntitiesOfClass(ServerPlayer.class,
                new AABB(center, center).inflate(r),
                ep -> ep != p && !ep.isSpectator() && game.isInGame(ep.getUUID())
                      && game.getTeam(p) == game.getTeam(ep))) {
            result.add(e);
        }
        return result;
    }

    // ── Raycast helper ────────────────────────────────────────────────────────

    private static ServerPlayer rayCast(ServerPlayer shooter, ValorantGame game, double range) {
        Vec3 eye  = shooter.getEyePosition();
        Vec3 look = shooter.getViewVector(1.0f);
        Vec3 end  = eye.add(look.scale(range));
        AABB box  = shooter.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0);

        ServerPlayer best = null;
        double closest = range * range;
        for (ServerPlayer e : shooter.level().getEntitiesOfClass(ServerPlayer.class, box,
                ep -> ep != shooter && !ep.isSpectator() && game.isInGame(ep.getUUID()))) {
            if (game.getTeam(shooter) == game.getTeam(e)) continue;
            if (e.getBoundingBox().inflate(0.2).clip(eye, end).isPresent()) {
                double dist = shooter.distanceToSqr(e);
                if (dist < closest) { closest = dist; best = e; }
            }
        }
        return best;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // JETT
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useJett(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> jettCloudburst(p, game);
            case "Q" -> jettUpdraft(p);
            case "E" -> jettTailwind(p);
            case "X" -> jettBladeStorm(p, game);
            default  -> false;
        };
    }

    private static boolean jettCloudburst(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Cloudburst")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(8));
        int blinded = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 3.5)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
            blinded++;
        }
        setcd(p.getUUID(), "C", 20 * 35);
        p.displayClientMessage(Component.literal("§bCloudburst! §7(" + blinded + " blinded)"), true);
        return true;
    }

    private static boolean jettUpdraft(ServerPlayer p) {
        if (onCooldown(p, "Q", "Updraft")) return false;
        Vec3 pos = p.position();
        p.teleportTo(pos.x, pos.y + 5.5, pos.z);
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 15, 0, false, false));
        setcd(p.getUUID(), "Q", 20 * 20);
        p.displayClientMessage(Component.literal("§bUpdraft!"), true);
        return true;
    }

    private static boolean jettTailwind(ServerPlayer p) {
        if (onCooldown(p, "E", "Tailwind")) return false;
        Vec3 look = p.getLookAngle();
        Vec3 pos  = p.position();
        double dy = look.y > 0.1 ? look.y * 3.0 : 0.1;
        p.teleportTo(pos.x + look.x * 5, pos.y + dy, pos.z + look.z * 5);
        setcd(p.getUUID(), "E", 20 * 12);
        p.displayClientMessage(Component.literal("§bTailwind!"), true);
        return true;
    }

    private static boolean jettBladeStorm(ServerPlayer p, ValorantGame game) {
        int charges = bladeCharges.getOrDefault(p.getUUID(), 0);
        if (charges == 0) {
            bladeCharges.put(p.getUUID(), 5);
            p.displayClientMessage(Component.literal("§6§lBLADE STORM! §r§75 blades ready — press §eX §7to throw"), true);
            return true;
        }
        ServerPlayer target = rayCast(p, game, 30);
        int hpBefore = target != null ? game.getHealth(target.getUUID()) : 0;
        if (target != null) {
            game.applyDamage(p, target, 160, true, false);
            boolean killed = hpBefore > 0 && game.getHealth(target.getUUID()) <= 0;
            if (killed) {
                bladeCharges.put(p.getUUID(), 5);
                p.displayClientMessage(Component.literal("§6§lBlade Kill! §aCharges refilled"), true);
            } else {
                bladeCharges.put(p.getUUID(), charges - 1);
                p.displayClientMessage(Component.literal("§6Blade hit! §7(" + (charges - 1) + " left)"), true);
            }
        } else {
            bladeCharges.put(p.getUUID(), charges - 1);
            p.displayClientMessage(Component.literal("§cMiss! §7(" + (charges - 1) + " left)"), true);
        }
        if (bladeCharges.getOrDefault(p.getUUID(), 0) <= 0) {
            bladeCharges.remove(p.getUUID());
            p.displayClientMessage(Component.literal("§cBlade Storm expired."), true);
        }
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // REYNA
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useReyna(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "E" -> reynaDevour(p, game);
            case "Q" -> reynaDismiss(p);
            case "X" -> reynaEmpress(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Reyna: not bound."), true); yield true; }
        };
    }

    private static boolean reynaDevour(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Devour")) return false;
        game.heal(p.getUUID(), 50);
        setcd(p.getUUID(), "E", 20 * 2);
        p.displayClientMessage(Component.literal("§dDevour! §a+50 HP"), true);
        return true;
    }

    private static boolean reynaDismiss(ServerPlayer p) {
        if (onCooldown(p, "Q", "Dismiss")) return false;
        p.setGameMode(GameType.SPECTATOR);
        dismissTimer.put(p.getUUID(), 30);
        setcd(p.getUUID(), "Q", 20 * 20);
        p.displayClientMessage(Component.literal("§dDismiss! §71.5s invulnerable"), true);
        return true;
    }

    private static boolean reynaEmpress(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Empress")) return false;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 1, false, false));
        reynaUltTimer.put(p.getUUID(), 200);
        setcd(p.getUUID(), "X", 20 * 60);
        p.displayClientMessage(Component.literal("§d§lEMPRESS! §r§7Increased fire rate + speed for 10s"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // RAZE
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useRaze(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> razeGrenades(p, game);
            case "E" -> razeBlastPack(p);
            case "Q" -> razePaintShells(p, game);
            case "X" -> razeShowstopper(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Raze: not bound."), true); yield true; }
        };
    }

    private static boolean razeGrenades(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Grenades")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(12));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 4.0)) {
            game.applyDamage(p, e, 35, false, false);
            hits++;
        }
        setcd(p.getUUID(), "C", 20 * 30);
        p.displayClientMessage(Component.literal("§6Cluster Grenade! §7(" + hits + " hit)"), true);
        return true;
    }

    private static boolean razeBlastPack(ServerPlayer p) {
        if (onCooldown(p, "E", "Blast Pack")) return false;
        Vec3 look = p.getLookAngle();
        Vec3 pos  = p.position();
        p.teleportTo(pos.x + look.x * 4, pos.y + 3, pos.z + look.z * 4);
        setcd(p.getUUID(), "E", 20 * 20);
        p.displayClientMessage(Component.literal("§6Blast Pack! §7Launched"), true);
        return true;
    }

    private static boolean razePaintShells(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Paint Shells")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(10));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 5.0)) {
            game.applyDamage(p, e, 50, false, false);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
            hits++;
        }
        setcd(p.getUUID(), "Q", 20 * 35);
        p.displayClientMessage(Component.literal("§6Paint Shells! §7(" + hits + " hit)"), true);
        return true;
    }

    private static boolean razeShowstopper(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Showstopper")) return false;
        Vec3 eye  = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end  = eye.add(look.scale(20));
        // Find first enemy along path, deal huge splash
        ServerPlayer closest = rayCast(p, game, 20);
        Vec3 explodePos = closest != null ? closest.position() : end;
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, explodePos, 6.0)) {
            game.applyDamage(p, e, 150, false, false);
            hits++;
        }
        setcd(p.getUUID(), "X", 20 * 90);
        p.displayClientMessage(Component.literal("§6§lSHOWSTOPPER! §7(" + hits + " hit)"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PHOENIX
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean usePhoenix(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> phoenixCurveball(p, game);
            case "Q" -> phoenixHotHands(p, game);
            case "E" -> phoenixBlaze(p, game);
            case "X" -> phoenixRunItBack(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Phoenix: not bound."), true); yield true; }
        };
    }

    private static boolean phoenixCurveball(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Curveball")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(6));
        int blinded = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 5.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
            blinded++;
        }
        setcd(p.getUUID(), "C", 20 * 30);
        p.displayClientMessage(Component.literal("§cCurveball! §7(" + blinded + " blinded)"), true);
        return true;
    }

    private static boolean phoenixHotHands(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Hot Hands")) return false;
        Vec3 pos = p.position();
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, pos, 3.0)) {
            game.applyDamage(p, e, 20, false, false);
            hits++;
        }
        game.heal(p.getUUID(), hits * 15 + 10);
        setcd(p.getUUID(), "Q", 20 * 35);
        p.displayClientMessage(Component.literal("§cHot Hands! §7" + hits + " hit, +" + (hits * 15 + 10) + " HP"), true);
        return true;
    }

    private static boolean phoenixBlaze(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Blaze")) return false;
        Vec3 look = p.getLookAngle();
        int hits = 0;
        for (int i = 1; i <= 6; i++) {
            Vec3 pt = p.position().add(look.scale(i));
            for (ServerPlayer e : enemiesInRadius(p, game, pt, 1.5)) {
                game.applyDamage(p, e, 15, false, false);
                hits++;
            }
        }
        game.heal(p.getUUID(), hits * 10);
        setcd(p.getUUID(), "E", 20 * 30);
        p.displayClientMessage(Component.literal("§cBlaze! §7(" + hits + " hit, +" + (hits * 10) + " HP healed)"), true);
        return true;
    }

    private static boolean phoenixRunItBack(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Run It Back")) return false;
        p.setGameMode(GameType.SPECTATOR);
        phoenixUltTimer.put(p.getUUID(), 140);
        setcd(p.getUUID(), "X", 20 * 60);
        p.displayClientMessage(Component.literal("§c§lRUN IT BACK! §r§7You have 7s — return will spawn you here"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SOVA
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useSova(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> sovaShockBolt(p, game);
            case "Q" -> sovaReconBolt(p, game);
            case "E" -> sovaOwlDrone(p, game);
            case "X" -> sovaHuntersFury(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Sova: not bound."), true); yield true; }
        };
    }

    private static boolean sovaShockBolt(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Shock Bolt")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(15));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 3.5)) {
            game.applyDamage(p, e, 60, false, false);
            hits++;
        }
        setcd(p.getUUID(), "C", 20 * 35);
        p.displayClientMessage(Component.literal("§3Shock Bolt! §7(" + hits + " hit)"), true);
        return true;
    }

    private static boolean sovaReconBolt(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Recon Bolt")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(20));
        // Reveal all enemies near the landing zone (within 15 blocks)
        int revealed = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 15.0)) {
            Vec3 ep = e.position();
            p.sendSystemMessage(Component.literal("§3[RECON] §7Enemy spotted at §e"
                    + String.format("%.0f, %.0f, %.0f", ep.x, ep.y, ep.z)));
            revealed++;
        }
        setcd(p.getUUID(), "Q", 20 * 40);
        p.displayClientMessage(Component.literal("§3Recon Bolt! §7Revealed " + revealed + " enemies"), true);
        return true;
    }

    private static boolean sovaOwlDrone(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Owl Drone")) return false;
        // Scan all enemies and report positions to entire team
        for (ServerPlayer e : game.getTeamPlayersVia(game.getTeam(p), p)) {
            if (!e.equals(p)) {
                for (ServerPlayer enemy : game.getEnemyPlayers(p)) {
                    Vec3 ep = enemy.position();
                    e.sendSystemMessage(Component.literal("§3[OWL] §7Enemy at §e"
                            + String.format("%.0f, %.0f, %.0f", ep.x, ep.y, ep.z)));
                }
            }
        }
        // Also tell self
        for (ServerPlayer enemy : game.getEnemyPlayers(p)) {
            Vec3 ep = enemy.position();
            p.sendSystemMessage(Component.literal("§3[OWL] §7Enemy at §e"
                    + String.format("%.0f, %.0f, %.0f", ep.x, ep.y, ep.z)));
        }
        setcd(p.getUUID(), "E", 20 * 50);
        p.displayClientMessage(Component.literal("§3Owl Drone! §7Scanning enemies"), true);
        return true;
    }

    private static boolean sovaHuntersFury(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Hunter's Fury")) return false;
        Vec3 look = p.getLookAngle();
        Vec3 pos  = p.getEyePosition();
        int hits = 0;
        // Fire 3 long-range bolts in quick succession (horizontal scan)
        for (double yaw : new double[]{-0.1, 0, 0.1}) {
            Vec3 dir = new Vec3(look.x + yaw, look.y, look.z + yaw).normalize();
            Vec3 end  = pos.add(dir.scale(40));
            AABB scanBox = new AABB(pos, end).inflate(2.0);
            for (ServerPlayer e : p.level().getEntitiesOfClass(ServerPlayer.class, scanBox,
                    ep -> ep != p && !ep.isSpectator() && game.isInGame(ep.getUUID())
                          && game.getTeam(p) != game.getTeam(ep))) {
                if (e.getBoundingBox().inflate(0.3).clip(pos, end).isPresent()) {
                    game.applyDamage(p, e, 80, false, false);
                    hits++;
                }
            }
        }
        setcd(p.getUUID(), "X", 20 * 60);
        p.displayClientMessage(Component.literal("§3§lHUNTER'S FURY! §r§7(" + hits + " hit)"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SAGE
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useSage(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> sageSlow(p, game);
            case "Q" -> sageHeal(p, game);
            case "E" -> sageBarrier(p);
            case "X" -> sageResurrect(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Sage: not bound."), true); yield true; }
        };
    }

    private static boolean sageSlow(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Slow Orb")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(10));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 4.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2, false, false));
            hits++;
        }
        setcd(p.getUUID(), "C", 20 * 35);
        p.displayClientMessage(Component.literal("§aSlow Orb! §7(" + hits + " slowed)"), true);
        return true;
    }

    private static boolean sageHeal(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Healing Orb")) return false;
        // Heal nearest ally or self
        List<ServerPlayer> allies = alliesInRadius(p, game, p.position(), 6.0);
        if (allies.isEmpty()) {
            game.heal(p.getUUID(), 60);
            p.displayClientMessage(Component.literal("§aHealing Orb! §7+60 HP (self)"), true);
        } else {
            ServerPlayer target = allies.get(0);
            game.heal(target.getUUID(), 100);
            p.displayClientMessage(Component.literal("§aHealing Orb! §7Healed §f" + target.getName().getString() + " §7+100 HP"), true);
        }
        setcd(p.getUUID(), "Q", 20 * 45);
        return true;
    }

    private static boolean sageBarrier(ServerPlayer p) {
        if (onCooldown(p, "E", "Barrier Orb")) return false;
        // Teleport a short hop to "place barrier" (visual only — would need block placement for full impl)
        p.displayClientMessage(Component.literal("§aBarrier Orb! §7Wall placed in front"), true);
        setcd(p.getUUID(), "E", 20 * 40);
        return true;
    }

    private static boolean sageResurrect(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Resurrection")) return false;
        // Revive a dead ally on the team
        List<ServerPlayer> revived = game.reviveDeadAlly(p);
        if (revived.isEmpty()) {
            p.displayClientMessage(Component.literal("§cNo dead allies to revive!"), true);
            return false;
        }
        setcd(p.getUUID(), "X", 20 * 90);
        p.displayClientMessage(Component.literal("§a§lRESURRECTION! §r§7Revived " + revived.size() + " ally"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // KILLJOY
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useKilljoy(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> killjoyAlarmbot(p, game);
            case "Q" -> killjoyNanoswarm(p, game);
            case "E" -> killjoyTurret(p, game);
            case "X" -> killjoyLockdown(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Killjoy: not bound."), true); yield true; }
        };
    }

    private static boolean killjoyAlarmbot(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Alarmbot")) return false;
        Vec3 pos = p.position();
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, pos, 8.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, false));
            hits++;
        }
        setcd(p.getUUID(), "C", 20 * 40);
        p.displayClientMessage(Component.literal("§eAlarmbot! §7Detected " + hits + " enemies (vulnerable)"), true);
        return true;
    }

    private static boolean killjoyNanoswarm(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Nanoswarm")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(8));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 3.0)) {
            game.applyDamage(p, e, 40, false, false);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
            hits++;
        }
        setcd(p.getUUID(), "Q", 20 * 40);
        p.displayClientMessage(Component.literal("§eNanoswarm! §7(" + hits + " hit)"), true);
        return true;
    }

    private static boolean killjoyTurret(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Turret")) return false;
        // Scan enemies nearby and damage closest
        ServerPlayer target = rayCast(p, game, 20);
        if (target != null) {
            game.applyDamage(p, target, 30, false, false);
            p.displayClientMessage(Component.literal("§eTurret! §7Fired at " + target.getName().getString()), true);
        } else {
            p.displayClientMessage(Component.literal("§eTurret placed! §7No enemies in range"), true);
        }
        setcd(p.getUUID(), "E", 20 * 30);
        return true;
    }

    private static boolean killjoyLockdown(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Lockdown")) return false;
        Vec3 pos = p.position();
        int detained = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, pos, 12.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 4, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2, false, false));
            detained++;
        }
        setcd(p.getUUID(), "X", 20 * 120);
        p.displayClientMessage(Component.literal("§e§lLOCKDOWN! §r§7" + detained + " enemies detained"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CYPHER
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useCypher(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> cypherTrapwire(p, game);
            case "Q" -> cypherCyberCage(p, game);
            case "E" -> cypherSpyCam(p, game);
            case "X" -> cypherNeuralTheft(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Cypher: not bound."), true); yield true; }
        };
    }

    private static boolean cypherTrapwire(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Trapwire")) return false;
        Vec3 pos = p.position();
        int detected = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, pos, 5.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 3, false, false));
            e.displayClientMessage(Component.literal("§c[CYPHER] §7Trapwire triggered!"), true);
            detected++;
        }
        setcd(p.getUUID(), "C", 20 * 35);
        p.displayClientMessage(Component.literal("§7Trapwire! §7Caught " + detected + " enemies"), true);
        return true;
    }

    private static boolean cypherCyberCage(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Cyber Cage")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(8));
        for (ServerPlayer e : enemiesInRadius(p, game, target, 4.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
        }
        setcd(p.getUUID(), "Q", 20 * 30);
        p.displayClientMessage(Component.literal("§7Cyber Cage! §7Enemies blinded in zone"), true);
        return true;
    }

    private static boolean cypherSpyCam(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Spy Cam")) return false;
        int spotted = 0;
        for (ServerPlayer e : game.getEnemyPlayers(p)) {
            Vec3 ep = e.position();
            p.sendSystemMessage(Component.literal("§7[CAM] §7Enemy: §e"
                    + e.getName().getString() + " §7at §e"
                    + String.format("%.0f,%.0f,%.0f", ep.x, ep.y, ep.z)));
            spotted++;
        }
        setcd(p.getUUID(), "E", 20 * 45);
        p.displayClientMessage(Component.literal("§7Spy Cam! §7Spotted " + spotted + " enemies"), true);
        return true;
    }

    private static boolean cypherNeuralTheft(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Neural Theft")) return false;
        // Reveal all enemy positions to whole team
        for (ServerPlayer e : game.getEnemyPlayers(p)) {
            Vec3 ep = e.position();
            String msg = "§7[NEURAL] §f" + e.getName().getString() + " §7at §e"
                    + String.format("%.0f,%.0f,%.0f", ep.x, ep.y, ep.z);
            for (ServerPlayer ally : game.getTeamPlayersVia(game.getTeam(p), p)) {
                ally.sendSystemMessage(Component.literal(msg));
            }
        }
        setcd(p.getUUID(), "X", 20 * 90);
        p.displayClientMessage(Component.literal("§7§lNEURAL THEFT! §r§7All enemy positions revealed"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // OMEN
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useOmen(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> omenShrouded(p, game);
            case "Q" -> omenDarkCover(p, game);
            case "E" -> omenParanoia(p, game);
            case "X" -> omenFromTheShadows(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Omen: not bound."), true); yield true; }
        };
    }

    private static boolean omenShrouded(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Shrouded Step")) return false;
        Vec3 look = p.getLookAngle();
        Vec3 pos  = p.position();
        p.teleportTo(pos.x + look.x * 8, pos.y, pos.z + look.z * 8);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false));
        setcd(p.getUUID(), "C", 20 * 30);
        p.displayClientMessage(Component.literal("§5Shrouded Step!"), true);
        return true;
    }

    private static boolean omenDarkCover(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Dark Cover")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(12));
        for (ServerPlayer e : enemiesInRadius(p, game, target, 4.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
        }
        setcd(p.getUUID(), "Q", 20 * 30);
        p.displayClientMessage(Component.literal("§5Dark Cover! §7Smoke placed"), true);
        return true;
    }

    private static boolean omenFromTheShadows(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "From the Shadows")) return false;
        // Teleport to a random spawn point in the map (simulates global teleport)
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false));
        Vec3 look = p.getLookAngle();
        Vec3 pos  = p.position();
        p.teleportTo(pos.x + look.x * 15, pos.y, pos.z + look.z * 15);
        setcd(p.getUUID(), "X", 20 * 60);
        p.displayClientMessage(Component.literal("§5§lFROM THE SHADOWS! §r§7Teleported"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VIPER
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useViper(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> viperSnakebite(p, game);
            case "Q" -> viperPoisonCloud(p, game);
            case "E" -> viperToxicScreen(p, game);
            case "X" -> viperVipersPit(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Viper: not bound."), true); yield true; }
        };
    }

    private static boolean viperSnakebite(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Snakebite")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(10));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 3.0)) {
            game.applyDamage(p, e, 30, false, false);
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, false));
            hits++;
        }
        setcd(p.getUUID(), "C", 20 * 35);
        p.displayClientMessage(Component.literal("§2Snakebite! §7(" + hits + " poisoned)"), true);
        return true;
    }

    private static boolean viperPoisonCloud(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Poison Cloud")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(10));
        for (ServerPlayer e : enemiesInRadius(p, game, target, 5.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 40, 0, false, false));
        }
        setcd(p.getUUID(), "Q", 20 * 30);
        p.displayClientMessage(Component.literal("§2Poison Cloud! §7Gas deployed"), true);
        return true;
    }

    private static boolean viperToxicScreen(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Toxic Screen")) return false;
        Vec3 look = p.getLookAngle();
        int hits = 0;
        for (int i = 1; i <= 8; i++) {
            Vec3 pt = p.position().add(look.scale(i));
            for (ServerPlayer e : enemiesInRadius(p, game, pt, 2.0)) {
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, false));
                hits++;
            }
        }
        setcd(p.getUUID(), "E", 20 * 40);
        p.displayClientMessage(Component.literal("§2Toxic Screen! §7(" + hits + " poisoned)"), true);
        return true;
    }

    private static boolean viperVipersPit(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Viper's Pit")) return false;
        Vec3 pos = p.position();
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, pos, 10.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 1, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            hits++;
        }
        setcd(p.getUUID(), "X", 20 * 90);
        p.displayClientMessage(Component.literal("§2§lVIPER'S PIT! §r§7(" + hits + " caught in gas)"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BRIMSTONE
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useBrimstone(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> brimIncendiary(p, game);
            case "Q" -> brimSkySmoke(p, game);
            case "E" -> brimStimBeacon(p, game);
            case "X" -> brimOrbitalStrike(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Brimstone: not bound."), true); yield true; }
        };
    }

    private static boolean brimIncendiary(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Incendiary")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(12));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 3.5)) {
            game.applyDamage(p, e, 40, false, false);
            hits++;
        }
        setcd(p.getUUID(), "C", 20 * 35);
        p.displayClientMessage(Component.literal("§6Incendiary! §7(" + hits + " hit)"), true);
        return true;
    }

    private static boolean brimSkySmoke(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Sky Smoke")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(15));
        for (ServerPlayer e : enemiesInRadius(p, game, target, 5.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
        }
        setcd(p.getUUID(), "Q", 20 * 25);
        p.displayClientMessage(Component.literal("§6Sky Smoke! §7Smokes deployed"), true);
        return true;
    }

    private static boolean brimStimBeacon(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Stim Beacon")) return false;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 100, 1, false, false));
        for (ServerPlayer ally : alliesInRadius(p, game, p.position(), 6.0)) {
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false));
        }
        setcd(p.getUUID(), "E", 20 * 40);
        p.displayClientMessage(Component.literal("§6Stim Beacon! §7Speed boost applied"), true);
        return true;
    }

    private static boolean brimOrbitalStrike(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Orbital Strike")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(20));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 7.0)) {
            game.applyDamage(p, e, 140, false, false);
            hits++;
        }
        setcd(p.getUUID(), "X", 20 * 120);
        p.displayClientMessage(Component.literal("§6§lORBITAL STRIKE! §r§7(" + hits + " hit)"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BREACH
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useBreach(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> breachFaultLine(p, game);
            case "Q" -> breachAftershock(p, game);
            case "E" -> breachFlashpoint(p, game);
            case "X" -> breachRollingThunder(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Breach: not bound."), true); yield true; }
        };
    }

    private static boolean breachFaultLine(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Fault Line")) return false;
        Vec3 look = p.getLookAngle();
        int hits = 0;
        for (int i = 2; i <= 14; i += 2) {
            Vec3 pt = p.position().add(look.scale(i));
            for (ServerPlayer e : enemiesInRadius(p, game, pt, 2.5)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0, false, false));
                hits++;
            }
        }
        setcd(p.getUUID(), "C", 20 * 35);
        p.displayClientMessage(Component.literal("§cFault Line! §7(" + hits + " stunned)"), true);
        return true;
    }

    private static boolean breachAftershock(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Aftershock")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(6));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 3.0)) {
            game.applyDamage(p, e, 80, false, false);
            hits++;
        }
        setcd(p.getUUID(), "Q", 20 * 40);
        p.displayClientMessage(Component.literal("§cAftershock! §7(" + hits + " hit)"), true);
        return true;
    }

    private static boolean breachFlashpoint(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Flashpoint")) return false;
        Vec3 look = p.getLookAngle();
        Vec3 target = p.getEyePosition().add(look.scale(8));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 5.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
            hits++;
        }
        setcd(p.getUUID(), "E", 20 * 30);
        p.displayClientMessage(Component.literal("§cFlashpoint! §7(" + hits + " flashed)"), true);
        return true;
    }

    private static boolean breachRollingThunder(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Rolling Thunder")) return false;
        Vec3 look = p.getLookAngle();
        int hits = 0;
        for (int i = 2; i <= 20; i += 2) {
            Vec3 pt = p.position().add(look.scale(i));
            for (ServerPlayer e : enemiesInRadius(p, game, pt, 4.0)) {
                game.applyDamage(p, e, 30, false, false);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 4, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false));
                hits++;
            }
        }
        setcd(p.getUUID(), "X", 20 * 90);
        p.displayClientMessage(Component.literal("§c§lROLLING THUNDER! §r§7(" + hits + " launched)"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // NEON
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useNeon(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> neonRelayBolt(p, game);
            case "Q" -> neonFastLane(p);
            case "E" -> neonHighGear(p);
            case "X" -> neonOverdrive(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Neon: not bound."), true); yield true; }
        };
    }

    private static boolean neonRelayBolt(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Relay Bolt")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(10));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 4.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
            hits++;
        }
        setcd(p.getUUID(), "C", 20 * 30);
        p.displayClientMessage(Component.literal("§bRelay Bolt! §7(" + hits + " stunned)"), true);
        return true;
    }

    private static boolean neonFastLane(ServerPlayer p) {
        if (onCooldown(p, "Q", "Fast Lane")) return false;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 2, false, false));
        setcd(p.getUUID(), "Q", 20 * 25);
        p.displayClientMessage(Component.literal("§bFast Lane! §7Speed boost"), true);
        return true;
    }

    private static boolean neonHighGear(ServerPlayer p) {
        boolean active = neonSpeedActive.getOrDefault(p.getUUID(), 0) > 0;
        if (active) {
            neonSpeedActive.remove(p.getUUID());
            p.displayClientMessage(Component.literal("§bHigh Gear §7deactivated"), true);
        } else {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 2, false, false));
            neonSpeedActive.put(p.getUUID(), 600);
            p.displayClientMessage(Component.literal("§bHigh Gear §aactivated!"), true);
        }
        return true;
    }

    private static boolean neonOverdrive(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Overdrive")) return false;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 3, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 300, 2, false, false));
        neonUltTimer.put(p.getUUID(), 300);
        // Zap nearest enemy with lightning beam
        ServerPlayer target = rayCast(p, game, 10);
        if (target != null) game.applyDamage(p, target, 65, false, false);
        setcd(p.getUUID(), "X", 20 * 90);
        p.displayClientMessage(Component.literal("§b§lOVERDRIVE! §r§7Lightning active for 15s"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SKYE
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useSkye(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> skyeRegrowth(p, game);
            case "Q" -> skyeTrailblazer(p, game);
            case "E" -> skyeGuidingLight(p, game);
            case "X" -> skyeSeekers(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Skye: not bound."), true); yield true; }
        };
    }

    private static boolean skyeRegrowth(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Regrowth")) return false;
        int healed = 0;
        for (ServerPlayer ally : alliesInRadius(p, game, p.position(), 8.0)) {
            game.heal(ally.getUUID(), 40);
            ally.displayClientMessage(Component.literal("§a[SKYE] §7+40 HP from Regrowth!"), true);
            healed++;
        }
        if (healed == 0) {
            game.heal(p.getUUID(), 30);
            healed = 1;
        }
        setcd(p.getUUID(), "C", 20 * 40);
        p.displayClientMessage(Component.literal("§aRegrowth! §7Healed " + healed + " allies"), true);
        return true;
    }

    private static boolean skyeTrailblazer(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Trailblazer")) return false;
        Vec3 look = p.getLookAngle();
        Vec3 target = p.position().add(look.scale(10));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 3.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
            game.applyDamage(p, e, 30, false, false);
            hits++;
        }
        setcd(p.getUUID(), "Q", 20 * 40);
        p.displayClientMessage(Component.literal("§aTrailblazer! §7(" + hits + " hit)"), true);
        return true;
    }

    private static boolean skyeGuidingLight(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Guiding Light")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(10));
        int blinded = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 8.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
            blinded++;
        }
        setcd(p.getUUID(), "E", 20 * 30);
        p.displayClientMessage(Component.literal("§aGuiding Light! §7(" + blinded + " flashed)"), true);
        return true;
    }

    private static boolean skyeSeekers(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Seekers")) return false;
        int blinded = 0;
        for (ServerPlayer e : game.getEnemyPlayers(p)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
            blinded++;
        }
        setcd(p.getUUID(), "X", 20 * 90);
        p.displayClientMessage(Component.literal("§a§lSEEKERS! §r§7All enemies nearsighted (" + blinded + ")"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CHAMBER
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useChamber(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> chamberTrademark(p, game);
            case "Q" -> chamberHeadhunter(p, game);
            case "E" -> chamberRendezvous(p, game);
            case "X" -> chamberTourDeForce(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Chamber: not bound."), true); yield true; }
        };
    }

    private static boolean chamberTrademark(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Trademark")) return false;
        Vec3 pos = p.position();
        int slowed = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, pos, 5.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3, false, false));
            game.applyDamage(p, e, 10, false, false);
            slowed++;
        }
        setcd(p.getUUID(), "C", 20 * 30);
        p.displayClientMessage(Component.literal("§eTrademark! §7(" + slowed + " trapped)"), true);
        return true;
    }

    private static boolean chamberHeadhunter(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Headhunter")) return false;
        ServerPlayer target = rayCast(p, game, 25);
        if (target != null) {
            game.applyDamage(p, target, 160, true, false);
            p.displayClientMessage(Component.literal("§eHeadhunter! §7Precision shot on " + target.getName().getString()), true);
        } else {
            p.displayClientMessage(Component.literal("§cHeadhunter: §7No target"), true);
        }
        setcd(p.getUUID(), "Q", 20 * 5);
        return true;
    }

    private static boolean chamberRendezvous(ServerPlayer p, ValorantGame game) {
        boolean hasAnchor = chamberHasAnchor.getOrDefault(p.getUUID(), false);
        if (!hasAnchor) {
            chamberAnchor.put(p.getUUID(), p.position());
            chamberHasAnchor.put(p.getUUID(), true);
            p.displayClientMessage(Component.literal("§eRendezvous anchor §aplaced! §7Press E again to teleport back"), true);
        } else {
            if (onCooldown(p, "E", "Rendezvous")) return false;
            Vec3 anchor = chamberAnchor.get(p.getUUID());
            if (anchor != null) {
                p.teleportTo(anchor.x, anchor.y, anchor.z);
                chamberHasAnchor.put(p.getUUID(), false);
                setcd(p.getUUID(), "E", 20 * 20);
                p.displayClientMessage(Component.literal("§eRendezvous! §7Teleported to anchor"), true);
            }
        }
        return true;
    }

    private static boolean chamberTourDeForce(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Tour de Force")) return false;
        ServerPlayer target = rayCast(p, game, 50);
        if (target != null) {
            game.applyDamage(p, target, 150, true, false);
            // Slow zone at kill location
            for (ServerPlayer e : enemiesInRadius(p, game, target.position(), 5.0)) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 3, false, false));
            }
            p.displayClientMessage(Component.literal("§e§lTOUR DE FORCE! §r§7Eliminated " + target.getName().getString()), true);
        } else {
            p.displayClientMessage(Component.literal("§cTour de Force: §7No target in sight"), true);
        }
        setcd(p.getUUID(), "X", 20 * 120);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FADE
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useFade(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> fadeSeize(p, game);
            case "Q" -> fadeHaunt(p, game);
            case "E" -> fadeProwler(p, game);
            case "X" -> fadeNightfall(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Fade: not bound."), true); yield true; }
        };
    }

    private static boolean fadeSeize(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Seize")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(12));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 5.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 4, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0, false, false));
            hits++;
        }
        setcd(p.getUUID(), "C", 20 * 35);
        p.displayClientMessage(Component.literal("§8Seize! §7(" + hits + " seized)"), true);
        return true;
    }

    private static boolean fadeHaunt(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Haunt")) return false;
        int revealed = 0;
        for (ServerPlayer e : game.getEnemyPlayers(p)) {
            Vec3 ep = e.position();
            p.sendSystemMessage(Component.literal("§8[HAUNT] §7" + e.getName().getString() + " at §e"
                    + String.format("%.0f,%.0f,%.0f", ep.x, ep.y, ep.z)));
            revealed++;
        }
        setcd(p.getUUID(), "Q", 20 * 40);
        p.displayClientMessage(Component.literal("§8Haunt! §7Revealed " + revealed + " enemies"), true);
        return true;
    }

    private static boolean fadeProwler(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Prowler")) return false;
        Vec3 look = p.getLookAngle();
        int hits = 0;
        for (double spread : new double[]{-0.15, 0.15}) {
            Vec3 target = p.getEyePosition().add(new Vec3(look.x + spread, look.y, look.z + spread).normalize().scale(10));
            for (ServerPlayer e : enemiesInRadius(p, game, target, 4.0)) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
                hits++;
            }
        }
        setcd(p.getUUID(), "E", 20 * 30);
        p.displayClientMessage(Component.literal("§8Prowler! §7(" + hits + " blinded)"), true);
        return true;
    }

    private static boolean fadeNightfall(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Nightfall")) return false;
        Vec3 look = p.getLookAngle();
        int hits = 0;
        for (int i = 2; i <= 30; i += 2) {
            Vec3 pt = p.position().add(look.scale(i));
            for (ServerPlayer e : enemiesInRadius(p, game, pt, 3.0)) {
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 2, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, false));
                hits++;
            }
        }
        setcd(p.getUUID(), "X", 20 * 90);
        p.displayClientMessage(Component.literal("§8§lNIGHTFALL! §r§7(" + hits + " decayed)"), true);
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GEKKO
    // ═════════════════════════════════════════════════════════════════════════

    private static boolean useGekko(ServerPlayer p, ValorantGame game, String key) {
        return switch (key) {
            case "C" -> gekkoMoshPit(p, game);
            case "Q" -> gekkoDizzy(p, game);
            case "E" -> gekkoWingman(p, game);
            case "X" -> gekkoThrash(p, game);
            default  -> { p.displayClientMessage(Component.literal("§7[" + key + "] Gekko: not bound."), true); yield true; }
        };
    }

    private static boolean gekkoMoshPit(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "C", "Mosh Pit")) return false;
        Vec3 target = p.getEyePosition().add(p.getLookAngle().scale(12));
        int hits = 0;
        for (ServerPlayer e : enemiesInRadius(p, game, target, 4.0)) {
            game.applyDamage(p, e, 15, false, false);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
            hits++;
        }
        setcd(p.getUUID(), "C", 20 * 35);
        p.displayClientMessage(Component.literal("§aMosh Pit! §7(" + hits + " in acid zone)"), true);
        return true;
    }

    private static boolean gekkoDizzy(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "Q", "Dizzy")) return false;
        Vec3 look = p.getLookAngle();
        ServerPlayer nearest = null;
        double best = 25 * 25;
        for (ServerPlayer e : game.getEnemyPlayers(p)) {
            double d = p.distanceToSqr(e);
            if (d < best) { best = d; nearest = e; }
        }
        if (nearest != null) {
            nearest.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
            p.displayClientMessage(Component.literal("§aDizzy! §7Blinded " + nearest.getName().getString()), true);
        } else {
            p.displayClientMessage(Component.literal("§cDizzy: §7No enemies nearby"), true);
        }
        setcd(p.getUUID(), "Q", 20 * 35);
        return true;
    }

    private static boolean gekkoWingman(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Wingman")) return false;
        // Reveal nearest enemies and stun closest
        for (ServerPlayer e : enemiesInRadius(p, game, p.position(), 12.0)) {
            Vec3 ep = e.position();
            p.sendSystemMessage(Component.literal("§a[WINGMAN] §7Spotted: " + e.getName().getString()));
        }
        ServerPlayer target = rayCast(p, game, 10);
        if (target != null) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2, false, false));
        }
        setcd(p.getUUID(), "E", 20 * 35);
        p.displayClientMessage(Component.literal("§aWingman! §7Scouting ahead"), true);
        return true;
    }

    private static boolean gekkoThrash(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "X", "Thrash")) return false;
        ServerPlayer target = rayCast(p, game, 15);
        if (target != null) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 255, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 2, false, false));
            p.displayClientMessage(Component.literal("§a§lTHRASH! §r§7Captured " + target.getName().getString()), true);
        } else {
            p.displayClientMessage(Component.literal("§cThrash: §7No target found"), true);
        }
        setcd(p.getUUID(), "X", 20 * 90);
        return true;
    }

    private static boolean omenParanoia(ServerPlayer p, ValorantGame game) {
        if (onCooldown(p, "E", "Paranoia")) return false;
        Vec3 look = p.getLookAngle();
        Vec3 eye  = p.getEyePosition();
        int hits = 0;
        for (ServerPlayer e : p.level().getEntitiesOfClass(ServerPlayer.class,
                new AABB(eye, eye.add(look.scale(20))).inflate(3.0),
                ep -> ep != p && game.isInGame(ep.getUUID()) && game.getTeam(p) != game.getTeam(ep))) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, false));
            hits++;
        }
        setcd(p.getUUID(), "E", 20 * 40);
        p.displayClientMessage(Component.literal("§5Paranoia! §7(" + hits + " hit)"), true);
        return true;
    }
}
