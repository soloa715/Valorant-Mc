package com.valorantmc.mod;

import com.valorantmc.mod.server.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Shared entrypoint — runs on both the Fabric server and (alongside the client
 * entrypoint) on the Fabric client.
 */
public class ValorantMCMod implements ModInitializer {

    public static final String MOD_ID = "valorantmc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // ── Payload types ──────────────────────────────────────────────────────
        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE,        HelloPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BuyActionPayload.TYPE,   BuyActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AgentChoicePayload.TYPE, AgentChoicePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapVotePayload.TYPE,     MapVotePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AdminActionPayload.TYPE, AdminActionPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(HudPayload.TYPE,         HudPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BuyMenuPayload.TYPE,     BuyMenuPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RadarPayload.TYPE,       RadarPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AgentSelectPayload.TYPE, AgentSelectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapSelectPayload.TYPE,   MapSelectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AdminSyncPayload.TYPE,   AdminSyncPayload.CODEC);

        // ── Server-side game manager ───────────────────────────────────────────
        ValorantServer.init();

        // ── Server tick ───────────────────────────────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server ->
                ValorantServer.getInstance().tick(server));

        // ── Player connection events ───────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ValorantServer.getInstance().onPlayerJoin(handler.player));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                ValorantServer.getInstance().onPlayerLeave(handler.player));

        // ── C → S packet receivers ─────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, context) ->
                context.server().execute(() ->
                        ValorantServer.getInstance().onHello(context.player(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(BuyActionPayload.TYPE, (payload, context) ->
                context.server().execute(() ->
                        ValorantServer.getInstance().onBuyAction(context.player(), payload)));

        ServerPlayNetworking.registerGlobalReceiver(AgentChoicePayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    var g = ValorantServer.getInstance().getGame(context.player().getUUID());
                    if (g != null) g.selectAgent(context.player(), payload.agentName());
                }));

        ServerPlayNetworking.registerGlobalReceiver(MapVotePayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    var g = ValorantServer.getInstance().getGame(context.player().getUUID());
                    if (g != null) MapManager.getInstance()
                            .vote(context.player().getUUID(), payload.mapName());
                }));

        ServerPlayNetworking.registerGlobalReceiver(AdminActionPayload.TYPE, (payload, context) ->
                context.server().execute(() ->
                        handleAdminAction(context.player(), payload, context.server())));

        // ── Commands ──────────────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ValorantCommands.register(dispatcher));

        // ── Weapon use (right-click in air or on a block) ─────────────────────
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            return ValorantServer.getInstance().onItemUse(sp, world, hand);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            return ValorantServer.getInstance().onItemUse(sp, world, hand);
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            return ValorantServer.getInstance().onItemUse(sp, world, hand);
        });

        // ── Melee attack (left-click) ──────────────────────────────────────────
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            return ValorantServer.getInstance().onAttack(sp, world, entity);
        });

        // ── Cancel vanilla damage for in-game players ──────────────────────────
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer sp)) return true;
            return ValorantServer.getInstance().allowDamage(sp, source);
        });

        LOGGER.info("ValorantMC loaded — Fabric server+client mod ready.");
    }

    // ── Admin action dispatcher ───────────────────────────────────────────────

    private static void handleAdminAction(ServerPlayer admin, AdminActionPayload p,
                                          net.minecraft.server.MinecraftServer server) {
        if (!admin.hasPermissions(2)) {
            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNo permission."));
            return;
        }

        String action = p.action();
        String targetUUID = p.targetUUID();
        ValorantServer vs = ValorantServer.getInstance();

        // Resolve target player (may be null for game-wide actions)
        ValorantGame game = vs.getGame(admin.getUUID());
        ServerPlayer target = null;
        if (targetUUID != null && !targetUUID.isEmpty() && !targetUUID.equals("_")) {
            try { target = server.getPlayerList().getPlayer(UUID.fromString(targetUUID)); }
            catch (Exception ignored) {}
        }
        if (game == null && !action.startsWith("spawn_") && !action.equals("end_game")) {
            // Try to find the game by target
            if (target != null) game = vs.getGame(target.getUUID());
        }

        final ValorantGame g = game;
        final ServerPlayer t = target;

        // ── Give credits ──────────────────────────────────────────────────────
        if (action.startsWith("give_credits_") && t != null && g != null) {
            String amt = action.substring("give_credits_".length());
            int credits = amt.equals("max") ? 9000 : Integer.parseInt(amt);
            g.getEconomy().addCredits(t.getUUID(), credits);
            t.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a+" + credits + " credits from admin"));
            return;
        }

        // ── Give weapon ───────────────────────────────────────────────────────
        if (action.startsWith("give_weapon_") && t != null && g != null) {
            String wepName = action.substring("give_weapon_".length());
            for (WeaponType wt : WeaponType.values()) {
                if (wt.getDisplayName().equalsIgnoreCase(wepName)) {
                    g.giveWeapon(t, new Weapon(wt));
                    t.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aGot §f" + wt.getDisplayName()));
                    return;
                }
            }
            return;
        }

        // ── Give ult ─────────────────────────────────────────────────────────
        if (action.equals("give_ult_1") || action.equals("give_ult_full")) {
            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Ult grant via admin not yet hooked to agent system."));
            return;
        }

        // ── Give shield ───────────────────────────────────────────────────────
        if (action.equals("give_shield_light") && t != null && g != null) {
            g.adminSetShield(t.getUUID(), 25);
            t.sendSystemMessage(net.minecraft.network.chat.Component.literal("§9Light Shield granted by admin."));
            return;
        }
        if (action.equals("give_shield_heavy") && t != null && g != null) {
            g.adminSetShield(t.getUUID(), 50);
            t.sendSystemMessage(net.minecraft.network.chat.Component.literal("§9Heavy Shield granted by admin."));
            return;
        }

        // ── Give ammo ─────────────────────────────────────────────────────────
        if (action.equals("give_ammo") && t != null && g != null) {
            g.adminRefillAllAmmo(server); // refills all, simpler
            return;
        }

        // ── Troll: kill ───────────────────────────────────────────────────────
        if (action.equals("kill") && t != null && g != null) {
            g.adminKill(t); return;
        }

        // ── Troll: freeze / unfreeze ──────────────────────────────────────────
        if (action.equals("freeze") && t != null) {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6000, 255, false, false));
            return;
        }
        if (action.equals("unfreeze") && t != null) {
            t.removeEffect(MobEffects.MOVEMENT_SLOWDOWN); return;
        }

        // ── Troll: blind ──────────────────────────────────────────────────────
        if (action.equals("blind") && t != null) {
            t.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0, false, false)); return;
        }

        // ── Troll: nausea ─────────────────────────────────────────────────────
        if (action.equals("nausea") && t != null) {
            t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 1, false, false)); return;
        }

        // ── Troll: launch ─────────────────────────────────────────────────────
        if (action.equals("launch") && t != null) {
            t.setDeltaMovement(t.getDeltaMovement().add(0, 2.5, 0)); return;
        }

        // ── Troll: strip ─────────────────────────────────────────────────────
        if (action.equals("strip") && t != null) {
            t.getInventory().clearContent(); return;
        }

        // ── Troll: credits max / zero ─────────────────────────────────────────
        if (action.equals("credits_max") && t != null && g != null) {
            g.getEconomy().setCredits(t.getUUID(), 9000); return;
        }
        if (action.equals("credits_zero") && t != null && g != null) {
            g.getEconomy().setCredits(t.getUUID(), 0); return;
        }

        // ── Troll: ignite ─────────────────────────────────────────────────────
        if (action.equals("ignite") && t != null) {
            t.igniteForSeconds(10); return;
        }

        // ── Troll: TP ─────────────────────────────────────────────────────────
        if (action.equals("tp_random") && t != null) {
            t.teleportTo(t.getX() + (Math.random() * 40 - 20), t.getY(), t.getZ() + (Math.random() * 40 - 20));
            return;
        }
        if (action.equals("tp_to_me") && t != null) {
            t.teleportTo(admin.getX(), admin.getY(), admin.getZ()); return;
        }
        if (action.equals("tp_to_target") && t != null) {
            admin.teleportTo(t.getX(), t.getY(), t.getZ()); return;
        }

        // ── Troll: revive / clear effects / speed / slow ──────────────────────
        if (action.equals("revive") && t != null && g != null) {
            g.adminRevive(t); return;
        }
        if (action.equals("clear_effects") && t != null) {
            t.removeAllEffects(); return;
        }
        if (action.equals("speed") && t != null) {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 2, false, false)); return;
        }
        if (action.equals("slow") && t != null) {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 3, false, false)); return;
        }

        // ── Game control ──────────────────────────────────────────────────────
        if (action.equals("end_round_atk") && g != null) { g.adminEndRound(server, true); return; }
        if (action.equals("end_round_def") && g != null) { g.adminEndRound(server, false); return; }
        if (action.equals("skip_buy") && g != null)      { g.adminSkipBuyPhase(); return; }
        if (action.equals("end_game") && g != null)      { g.adminForceEndGame(server); return; }
        if (action.equals("revive_all") && g != null)    { g.adminReviveAll(server); return; }
        if (action.equals("refill_all_ammo") && g != null) { g.adminRefillAllAmmo(server); return; }
        if (action.equals("start_round") && g != null)   { g.adminSkipBuyPhase(); return; }
        if (action.equals("pause") && g != null)         {
            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Pause not yet implemented."));
            return;
        }
        if (action.equals("resume") && g != null)        {
            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Resume not yet implemented."));
            return;
        }
        if (action.equals("balance_teams") && g != null) {
            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Balance teams not yet implemented."));
            return;
        }

        // ── Spawn management ──────────────────────────────────────────────────
        SpawnConfigManager sc = SpawnConfigManager.getInstance();
        Vec3 adminPos = admin.position();
        if (action.equals("spawn_add_atk"))   { sc.addAttacker(adminPos); admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cATK spawn added at " + fmtVec(adminPos))); return; }
        if (action.equals("spawn_add_def"))   { sc.addDefender(adminPos); admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§bDEF spawn added at " + fmtVec(adminPos))); return; }
        if (action.equals("spawn_clear_atk")) { sc.clearAttacker(); admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cATK spawns cleared.")); return; }
        if (action.equals("spawn_clear_def")) { sc.clearDefender(); admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§bDEF spawns cleared.")); return; }
        if (action.equals("spawn_save")) {
            admin.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aSpawns auto-saved (JSON updated)."));
            return;
        }

        // tp_to_spawn_atk_0, tp_to_spawn_def_2, etc.
        if (action.startsWith("tp_to_spawn_")) {
            String[] parts = action.split("_");
            if (parts.length >= 5) {
                boolean isAtk = parts[3].equals("atk");
                int idx = Integer.parseInt(parts[4]);
                List<Vec3> spawns = isAtk ? sc.getAttackerSpawns() : sc.getDefenderSpawns();
                if (idx < spawns.size()) {
                    Vec3 sp = spawns.get(idx);
                    admin.teleportTo(sp.x, sp.y, sp.z);
                }
            }
        }
    }

    private static String fmtVec(Vec3 v) {
        return String.format("%.0f, %.0f, %.0f", v.x, v.y, v.z);
    }
}
