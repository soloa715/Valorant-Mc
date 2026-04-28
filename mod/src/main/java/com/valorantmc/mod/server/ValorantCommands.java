package com.valorantmc.mod.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public class ValorantCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // /vjoin [gameId]
        dispatcher.register(Commands.literal("vjoin")
                .executes(ctx -> cmdJoin(ctx, "default"))
                .then(Commands.argument("gameId", StringArgumentType.word())
                        .executes(ctx -> cmdJoin(ctx, StringArgumentType.getString(ctx, "gameId")))));

        // /vleave
        dispatcher.register(Commands.literal("vleave")
                .executes(ValorantCommands::cmdLeave));

        // /vagent <name>
        dispatcher.register(Commands.literal("vagent")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§7Available agents: §bJett, Reyna, Raze, Phoenix, Sova, Sage, " +
                            "Killjoy, Cypher, Omen, Viper, Brimstone, Breach, Neon, Skye, Chamber, Fade, Gekko"), false);
                    return 1;
                })
                .then(Commands.argument("agent", StringArgumentType.word())
                        .executes(ctx -> cmdAgent(ctx, StringArgumentType.getString(ctx, "agent")))));

        // /vshop
        dispatcher.register(Commands.literal("vshop")
                .executes(ValorantCommands::cmdShop));

        // /vreload
        dispatcher.register(Commands.literal("vreload")
                .executes(ValorantCommands::cmdReload));

        // /vuse <C|Q|E|X>
        dispatcher.register(Commands.literal("vuse")
                .then(Commands.argument("ability", StringArgumentType.word())
                        .executes(ctx -> cmdUse(ctx, StringArgumentType.getString(ctx, "ability")))));

        // /vdropspike
        dispatcher.register(Commands.literal("vdropspike")
                .executes(ValorantCommands::cmdDropSpike));

        // /vwalk
        dispatcher.register(Commands.literal("vwalk")
                .executes(ValorantCommands::cmdWalk));

        // /vstart [gameId]
        dispatcher.register(Commands.literal("vstart")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> cmdStart(ctx, "default"))
                .then(Commands.argument("gameId", StringArgumentType.word())
                        .executes(ctx -> cmdStart(ctx, StringArgumentType.getString(ctx, "gameId")))));

        // /vgame create|list|delete
        dispatcher.register(Commands.literal("vgame")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> cmdCreateGame(ctx, StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("list")
                        .executes(ValorantCommands::cmdListGames))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> cmdDeleteGame(ctx, StringArgumentType.getString(ctx, "id"))))));

        // /vstats
        dispatcher.register(Commands.literal("vstats")
                .executes(ValorantCommands::cmdStats));

        // /vquick
        dispatcher.register(Commands.literal("vquick")
                .executes(ValorantCommands::cmdQuickPlay));

        // /vmap list|reload|set <name>|open
        dispatcher.register(Commands.literal("vmap")
                .then(Commands.literal("list")
                        .executes(ValorantCommands::cmdMapList))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(ValorantCommands::cmdMapReload))
                .then(Commands.literal("set")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> cmdMapSet(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("open")
                        .executes(ValorantCommands::cmdMapOpen)));

        // /vadmin  — sends AdminSyncPayload to the requesting player (op only)
        dispatcher.register(Commands.literal("vadmin")
                .requires(src -> src.hasPermission(2))
                .executes(ValorantCommands::cmdAdminOpen));

        // /vspawn add <atk|def>  /  /vspawn clear <atk|def>  /  /vspawn list
        dispatcher.register(Commands.literal("vspawn")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(ctx -> cmdSpawnAdd(ctx, StringArgumentType.getString(ctx, "team")))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(ctx -> cmdSpawnClear(ctx, StringArgumentType.getString(ctx, "team")))))
                .then(Commands.literal("list")
                        .executes(ValorantCommands::cmdSpawnList)));
    }

    // ── Command implementations ───────────────────────────────────────────────

    private static int cmdJoin(CommandContext<CommandSourceStack> ctx, String gameId) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;

        ValorantServer vs = ValorantServer.getInstance();
        if (!vs.getGameIds().contains(gameId)) {
            vs.createGame(gameId);
            p.sendSystemMessage(Component.literal("§7Created game §e" + gameId));
        }
        vs.joinGame(p, gameId);
        return 1;
    }

    private static int cmdLeave(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        ValorantServer.getInstance().leaveGame(p, ctx.getSource().getServer());
        p.sendSystemMessage(Component.literal("§7Left the game."));
        return 1;
    }

    private static int cmdAgent(CommandContext<CommandSourceStack> ctx, String agentName) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;

        ValorantGame g = ValorantServer.getInstance().getGame(p.getUUID());
        if (g == null) {
            p.sendSystemMessage(Component.literal("§cYou're not in a game!"));
            return 0;
        }
        if (g.getState() != GameState.AGENT_SELECT) {
            p.sendSystemMessage(Component.literal("§cAgent select is not active!"));
            return 0;
        }
        boolean ok = g.selectAgent(p, agentName);
        if (!ok) p.sendSystemMessage(Component.literal("§cUnknown agent: §f" + agentName));
        return ok ? 1 : 0;
    }

    private static int cmdShop(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;

        ValorantGame g = ValorantServer.getInstance().getGame(p.getUUID());
        if (g == null || g.getState() != GameState.BUY_PHASE) {
            p.sendSystemMessage(Component.literal("§cShop is only available during buy phase!"));
            return 0;
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                new com.valorantmc.mod.BuyMenuPayload(
                        g.getEconomy().getCredits(p.getUUID()), true));
        return 1;
    }

    private static int cmdReload(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        ValorantGame g = ValorantServer.getInstance().getGame(p.getUUID());
        if (g == null) return 0;
        g.startReload(p);
        return 1;
    }

    private static int cmdUse(CommandContext<CommandSourceStack> ctx, String ability) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        ValorantGame g = ValorantServer.getInstance().getGame(p.getUUID());
        if (g == null) {
            p.sendSystemMessage(Component.literal("§cNot in a game!"));
            return 0;
        }
        String key = ability.toUpperCase();

        // Spike interactions take priority
        if ((key.equals("E") || key.equals("X")) && g.isSpikePlanted() && g.getSpikeCarrier() == null) {
            g.startDefuse(p);
            return 1;
        }
        if (key.equals("C") && !g.isSpikePlanted()
                && g.getState() == GameState.ROUND_ACTIVE
                && p.getUUID().equals(g.getSpikeCarrier())) {
            g.plantSpike(p, ctx.getSource().getServer());
            return 1;
        }

        // Dispatch to agent ability system
        String agent = g.getAgent(p.getUUID());
        if (agent == null) {
            p.displayClientMessage(Component.literal("§cNo agent selected!"), true);
            return 0;
        }
        AgentAbilities.use(p, g, agent, key);
        return 1;
    }

    private static int cmdDropSpike(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        ValorantGame g = ValorantServer.getInstance().getGame(p.getUUID());
        if (g == null || g.getState() != GameState.ROUND_ACTIVE) return 0;
        if (p.getUUID().equals(g.getSpikeCarrier())) {
            g.plantSpike(p, ctx.getSource().getServer());
        }
        return 1;
    }

    private static int cmdWalk(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        p.displayClientMessage(Component.literal("§7Walk mode toggled (not yet implemented)."), true);
        return 1;
    }

    private static int cmdStart(CommandContext<CommandSourceStack> ctx, String gameId) {
        ValorantServer vs = ValorantServer.getInstance();
        ValorantGame g = vs.getGame(gameId);
        if (g == null) {
            g = vs.createGame(gameId);
            ctx.getSource().sendSuccess(() -> Component.literal("§7Created game §e" + gameId), true);
        }
        g.start(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§aStarted game §e" + gameId), true);
        return 1;
    }

    private static int cmdCreateGame(CommandContext<CommandSourceStack> ctx, String id) {
        ValorantServer.getInstance().createGame(id);
        ctx.getSource().sendSuccess(() -> Component.literal("§aCreated game §e" + id), true);
        return 1;
    }

    private static int cmdListGames(CommandContext<CommandSourceStack> ctx) {
        Collection<ValorantGame> games = ValorantServer.getInstance().getGames();
        if (games.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7No active games."), false);
        } else {
            games.forEach(g -> ctx.getSource().sendSuccess(() ->
                    Component.literal("§e" + g.getId() + " §8— §7" + g.getState()
                            + " §8[§f" + g.getAllUuids().size() + "§8 players]"), false));
        }
        return 1;
    }

    private static int cmdDeleteGame(CommandContext<CommandSourceStack> ctx, String id) {
        ValorantServer.getInstance().removeGame(id, ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("§7Removed game §e" + id), true);
        return 1;
    }

    private static int cmdStats(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        ValorantGame g = ValorantServer.getInstance().getGame(p.getUUID());
        if (g == null) {
            p.sendSystemMessage(Component.literal("§7Not currently in a game."));
            return 0;
        }
        p.sendSystemMessage(Component.literal("§e--- Your Stats ---"));
        p.sendSystemMessage(Component.literal("§7HP: §f" + g.getHealth(p.getUUID())
                + " §8| §7Shield: §f" + g.getShield(p.getUUID())));
        p.sendSystemMessage(Component.literal("§7Agent: §b" + (g.getAgent(p.getUUID()) != null ? g.getAgent(p.getUUID()) : "None")));
        p.sendSystemMessage(Component.literal("§7Credits: §6" + g.getEconomy().getCredits(p.getUUID())));
        p.sendSystemMessage(Component.literal("§7Round: §f" + g.getCurrentRound()
                + " §8| §7Score: §c" + g.getAttackers().getRoundWins()
                + "§8:§b" + g.getDefenders().getRoundWins()));
        return 1;
    }

    private static int cmdQuickPlay(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        ValorantServer.getInstance().quickPlay(p, ctx.getSource().getServer());
        return 1;
    }

    private static int cmdAdminOpen(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;

        ValorantGame g = ValorantServer.getInstance().getGame(p.getUUID());
        SpawnConfigManager sc = SpawnConfigManager.getInstance();
        MapManager mm = MapManager.getInstance();

        // Build player list
        java.util.List<String> playerLines = new java.util.ArrayList<>();
        if (g != null) {
            for (java.util.UUID uid : g.getAllUuids()) {
                net.minecraft.server.level.ServerPlayer sp =
                        ctx.getSource().getServer().getPlayerList().getPlayer(uid);
                String name   = sp != null ? sp.getName().getString() : uid.toString().substring(0, 8);
                int    hp     = g.getHealth(uid);
                int    shield = g.getShield(uid);
                int    creds  = g.getEconomy().getCredits(uid);
                String agent  = g.getAgent(uid) != null ? g.getAgent(uid) : "none";
                String team   = g.getTeamOf(uid).getSide().name();
                playerLines.add(name + ":" + uid + ":" + hp + ":" + shield + ":" + creds + ":" + agent + ":" + team);
            }
        }

        // Build spawn list
        java.util.List<String> spawnLines = new java.util.ArrayList<>();
        sc.getAttackerSpawns().forEach(v ->
                spawnLines.add("atk:" + (int)v.x + "," + (int)v.y + "," + (int)v.z));
        sc.getDefenderSpawns().forEach(v ->
                spawnLines.add("def:" + (int)v.x + "," + (int)v.y + "," + (int)v.z));

        String gameState = g != null ? g.getState().name() : "NO_GAME";
        int round = g != null ? g.getCurrentRound() : 0;
        String mapName = mm.getActiveMap();
        String mapList = String.join(",", mm.getMapNames());

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                new com.valorantmc.mod.AdminSyncPayload(playerLines, spawnLines, gameState, round, mapName, mapList));
        return 1;
    }

    // ── /vspawn ───────────────────────────────────────────────────────────────

    private static int cmdSpawnAdd(CommandContext<CommandSourceStack> ctx, String team) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        SpawnConfigManager sc = SpawnConfigManager.getInstance();
        net.minecraft.world.phys.Vec3 pos = p.position();
        boolean atk = team.equalsIgnoreCase("atk") || team.equalsIgnoreCase("attacker");
        boolean def = team.equalsIgnoreCase("def") || team.equalsIgnoreCase("defender");
        if (!atk && !def) {
            ctx.getSource().sendFailure(Component.literal("§cUse: /vspawn add atk  or  /vspawn add def"));
            return 0;
        }
        if (atk) sc.addAttacker(pos);
        else     sc.addDefender(pos);
        String label = atk ? "§cAttacker" : "§bDefender";
        ctx.getSource().sendSuccess(() -> Component.literal(
                label + " §aspawn saved at §f" + fmt(pos)
                + " §8(total " + (atk ? sc.getAttackerSpawns().size() : sc.getDefenderSpawns().size()) + ")"), true);
        return 1;
    }

    private static int cmdSpawnClear(CommandContext<CommandSourceStack> ctx, String team) {
        SpawnConfigManager sc = SpawnConfigManager.getInstance();
        boolean atk = team.equalsIgnoreCase("atk") || team.equalsIgnoreCase("attacker");
        boolean def = team.equalsIgnoreCase("def") || team.equalsIgnoreCase("defender");
        if (!atk && !def) {
            ctx.getSource().sendFailure(Component.literal("§cUse: /vspawn clear atk  or  /vspawn clear def"));
            return 0;
        }
        if (atk) sc.clearAttacker();
        else     sc.clearDefender();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7Cleared all " + (atk ? "attacker" : "defender") + " spawns."), true);
        return 1;
    }

    private static int cmdSpawnList(CommandContext<CommandSourceStack> ctx) {
        SpawnConfigManager sc = SpawnConfigManager.getInstance();
        ctx.getSource().sendSuccess(() -> Component.literal("§e--- Spawn Points ---"), false);
        if (sc.getAttackerSpawns().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§cAttackers: §7none"), false);
        } else {
            sc.getAttackerSpawns().forEach(v ->
                ctx.getSource().sendSuccess(() -> Component.literal("§cATK §7" + fmt(v)), false));
        }
        if (sc.getDefenderSpawns().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§bDefenders: §7none"), false);
        } else {
            sc.getDefenderSpawns().forEach(v ->
                ctx.getSource().sendSuccess(() -> Component.literal("§bDEF §7" + fmt(v)), false));
        }
        return 1;
    }

    private static String fmt(net.minecraft.world.phys.Vec3 v) {
        return String.format("%.1f, %.1f, %.1f", v.x, v.y, v.z);
    }

    // ── /vmap ─────────────────────────────────────────────────────────────────

    private static int cmdMapList(CommandContext<CommandSourceStack> ctx) {
        MapManager mm = MapManager.getInstance();
        ctx.getSource().sendSuccess(() -> Component.literal("§e--- Available Maps ---"), false);
        mm.getMapNames().forEach(name -> {
            boolean active = name.equals(mm.getActiveMap());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    (active ? "§a▶ " : "§7  ") + name), false);
        });
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7Active: §e" + mm.getActiveMap() + " §8| Use §b/vmap set <name> §8to change"), false);
        return 1;
    }

    private static int cmdMapReload(CommandContext<CommandSourceStack> ctx) {
        MapManager.getInstance().reload();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§aMap config reloaded. §7Found §e"
                + MapManager.getInstance().getMapNames().size() + " §7maps."), true);
        return 1;
    }

    private static int cmdMapSet(CommandContext<CommandSourceStack> ctx, String name) {
        MapManager mm = MapManager.getInstance();
        if (!mm.getMapNames().contains(name)) {
            ctx.getSource().sendFailure(Component.literal("§cUnknown map: §f" + name
                    + " §7— use §b/vmap list §7to see options"));
            return 0;
        }
        mm.setActiveMap(name);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Active map set to §e" + name), true);
        return 1;
    }

    private static int cmdMapOpen(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = getPlayer(ctx);
        if (p == null) return 0;
        MapManager mm = MapManager.getInstance();
        java.util.List<String> names = mm.getMapNames();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                new com.valorantmc.mod.MapSelectPayload(names, mm.getActiveMap()));
        return 1;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ServerPlayer getPlayer(CommandContext<CommandSourceStack> ctx) {
        try { return ctx.getSource().getPlayerOrException(); }
        catch (Exception e) { ctx.getSource().sendFailure(Component.literal("Must be a player!")); return null; }
    }
}
