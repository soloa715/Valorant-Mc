package com.valorantmc.mod.server;

import com.valorantmc.mod.BuyActionPayload;
import com.valorantmc.mod.HelloPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Singleton game manager. Holds all active games and routes events to them.
 */
public class ValorantServer {

    private static ValorantServer INSTANCE;

    private final Map<String, ValorantGame> games         = new HashMap<>();
    private final Map<UUID, String>         playerGameMap = new HashMap<>();
    private final Set<UUID>                 modUsers      = new HashSet<>();

    private ValorantServer() {}

    public static void init() {
        INSTANCE = new ValorantServer();
    }

    public static ValorantServer getInstance() {
        return INSTANCE;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        AgentAbilities.tick(server);
        games.values().forEach(g -> g.tick(server));
    }

    // ── Game lifecycle ────────────────────────────────────────────────────────

    public ValorantGame createGame(String id) {
        ValorantGame g = new ValorantGame(id);
        games.put(id, g);
        return g;
    }

    public boolean joinGame(ServerPlayer p, String gameId) {
        if (isInGame(p.getUUID())) {
            p.sendSystemMessage(Component.literal("§cYou're already in a game!"));
            return false;
        }
        ValorantGame g = games.get(gameId);
        if (g == null) {
            p.sendSystemMessage(Component.literal("§cGame '" + gameId + "' not found!"));
            return false;
        }
        g.addPlayer(p);
        playerGameMap.put(p.getUUID(), gameId);
        if (g.getState() != GameState.WAITING) {
            g.addPlayerToBossBar(p);
        }
        return true;
    }

    public void leaveGame(ServerPlayer p, MinecraftServer server) {
        String gameId = playerGameMap.remove(p.getUUID());
        if (gameId == null) return;
        ValorantGame g = games.get(gameId);
        if (g != null) g.removePlayer(p);
    }

    public void removeGame(String id, MinecraftServer server) {
        ValorantGame g = games.get(id);
        if (g != null) g.shutdown(server);
        games.remove(id);
    }

    public void quickPlay(ServerPlayer p, MinecraftServer server) {
        if (isInGame(p.getUUID())) {
            p.sendSystemMessage(Component.literal("§cAlready in a game!"));
            return;
        }
        ValorantGame g = games.values().stream()
                .filter(x -> x.getState() == GameState.WAITING)
                .findFirst().orElse(null);
        if (g == null) {
            String id = "game-" + System.currentTimeMillis();
            g = createGame(id);
        }
        g.addPlayer(p);
        playerGameMap.put(p.getUUID(), g.getId());

        ValorantGame finalG = g;
        if (finalG.getState() == GameState.WAITING) {
            p.sendSystemMessage(Component.literal("§aStarting match in 3 seconds..."));
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override public void run() {
                    server.execute(() -> {
                        if (finalG.getState() == GameState.WAITING) finalG.start(server);
                    });
                }
            }, 3000L);
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isInGame(UUID uuid)         { return playerGameMap.containsKey(uuid); }
    public ValorantGame getGame(UUID uuid)     { String id = playerGameMap.get(uuid); return id == null ? null : games.get(id); }
    public ValorantGame getGame(String id)     { return games.get(id); }
    public Collection<ValorantGame> getGames() { return games.values(); }
    public Set<String> getGameIds()            { return games.keySet(); }
    public boolean hasMod(UUID uuid)           { return modUsers.contains(uuid); }

    // ── Connection events ─────────────────────────────────────────────────────

    public void onPlayerJoin(ServerPlayer p) {}

    public void onPlayerLeave(ServerPlayer p) {
        modUsers.remove(p.getUUID());
        ValorantGame g = getGame(p.getUUID());
        if (g != null) {
            g.removePlayer(p);
            playerGameMap.remove(p.getUUID());
        }
    }

    // ── Networking events ─────────────────────────────────────────────────────

    public void onHello(ServerPlayer p, HelloPayload payload) {
        modUsers.add(p.getUUID());
    }

    public void onBuyAction(ServerPlayer p, BuyActionPayload payload) {
        ValorantGame g = getGame(p.getUUID());
        if (g == null) return;
        String weaponName = payload.weaponName().trim().toUpperCase();
        try {
            WeaponType type = WeaponType.valueOf(weaponName);
            g.buyWeapon(p, type);
        } catch (IllegalArgumentException ex) {
            if (weaponName.equals("LIGHT_SHIELD") || weaponName.equals("LIGHT")) {
                g.buyShield(p, false);
            } else if (weaponName.equals("HEAVY_SHIELD") || weaponName.equals("HEAVY")) {
                g.buyShield(p, true);
            } else if (weaponName.equals("ABILITY_C")) {
                g.buyAbilityCharge(p, "C");
            } else if (weaponName.equals("ABILITY_Q")) {
                g.buyAbilityCharge(p, "Q");
            } else {
                p.sendSystemMessage(Component.literal("§cUnknown item: " + weaponName));
            }
        }
    }

    // ── Player interaction events ─────────────────────────────────────────────

    public InteractionResult onItemUse(ServerPlayer player, Level world, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        WeaponType wt = Weapon.getWeaponType(stack);
        if (wt == null) return InteractionResult.PASS;

        ValorantGame g = getGame(player.getUUID());
        if (g == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            g.startReload(player);
            return InteractionResult.SUCCESS;
        }

        if (!g.handleShot(player)) {
            Weapon held = g.getWeapon(player.getUUID());
            if (held != null && held.getCurrentAmmo() == 0 && !held.getType().isMelee()) {
                player.displayClientMessage(Component.literal("§c*click* — reload (sneak + right-click)"), true);
            }
            return InteractionResult.FAIL;
        }

        performRaycast(player, wt, g);
        return InteractionResult.SUCCESS;
    }

    private void performRaycast(ServerPlayer shooter, WeaponType wt, ValorantGame game) {
        Vec3 eye   = shooter.getEyePosition();
        Vec3 look  = shooter.getViewVector(1.0f);
        double range = wt.getMaxRange();
        Vec3 end   = eye.add(look.scale(range));

        int pellets = wt.getPellets();
        Random rng  = new Random();

        for (int pel = 0; pel < pellets; pel++) {
            Vec3 dir = look;
            if (pellets > 1 || wt.getBaseSpread() > 0) {
                double spread = wt.getBaseSpread() * (pellets > 1 ? 3.0 : 1.0);
                dir = look.add(
                        (rng.nextDouble() - 0.5) * spread,
                        (rng.nextDouble() - 0.5) * spread,
                        (rng.nextDouble() - 0.5) * spread).normalize();
                end = eye.add(dir.scale(range));
            }

            AABB searchBox = shooter.getBoundingBox().expandTowards(dir.scale(range)).inflate(1.5);
            Vec3 finalEnd = end;

            ServerPlayer hit = null;
            double closestDist = range * range;

            for (ServerPlayer e : shooter.level().getEntitiesOfClass(ServerPlayer.class, searchBox,
                    ep -> ep != shooter && !ep.isSpectator())) {
                if (!game.isInGame(e.getUUID())) continue;
                AABB hitBox = e.getBoundingBox().inflate(0.1);
                if (hitBox.clip(eye, finalEnd).isPresent()) {
                    double dist = shooter.distanceToSqr(e);
                    if (dist < closestDist) {
                        closestDist = dist;
                        hit = e;
                    }
                }
            }

            if (hit != null) {
                AABB hitBox = hit.getBoundingBox();
                double headZone = hitBox.minY + (hitBox.maxY - hitBox.minY) * 0.7;
                Vec3 hitPoint = hit.getBoundingBox().clip(eye, finalEnd).orElse(null);
                boolean headshot = hitPoint != null && hitPoint.y > headZone;
                boolean legshot  = hitPoint != null && hitPoint.y < hitBox.minY + 0.3;
                game.applyDamage(shooter, hit, wt.getDamage(), headshot, legshot);
            }
        }
    }

    public InteractionResult onAttack(ServerPlayer player, Level world, Entity target) {
        ValorantGame g = getGame(player.getUUID());
        if (g == null) return InteractionResult.PASS;
        if (!(target instanceof ServerPlayer victim)) return InteractionResult.PASS;
        if (!g.isInGame(victim.getUUID())) return InteractionResult.PASS;

        Weapon w = g.getWeapon(player.getUUID());
        if (w == null || !w.getType().isMelee()) return InteractionResult.PASS;

        double dist = player.distanceToSqr(target);
        if (dist > 9.0) return InteractionResult.FAIL;

        g.applyDamage(player, victim, w.getType().getDamage(), false, false);
        return InteractionResult.FAIL;
    }

    public boolean allowDamage(ServerPlayer p, DamageSource source) {
        if (!isInGame(p.getUUID())) return true;
        return false;
    }
}
