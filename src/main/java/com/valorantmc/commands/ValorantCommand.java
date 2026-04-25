package com.valorantmc.commands;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.managers.StatsManager;
import com.valorantmc.shop.AgentSelectGUI;
import com.valorantmc.shop.ShopGUI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ValorantCommand implements CommandExecutor, TabCompleter {

    private final ValorantMC plugin;
    /** Players who have voted to skip buy phase this round. Key = gameId:roundNumber. */
    private final Map<String, Set<UUID>> skipVotes = new ConcurrentHashMap<>();

    public ValorantCommand(ValorantMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // Alias commands
        switch (cmd.getName().toLowerCase()) {
            case "vshop"      -> { return handleShop(sender); }
            case "vagent"     -> { return handleAgent(sender); }
            case "vstats"     -> { return handleStats(sender, args); }
            case "vreload"    -> { return handleReloadWeapon(sender); }
            case "vdropspike" -> { return handleDropSpike(sender); }
            case "vwalk"      -> { return handleWalkToggle(sender); }
            case "vuse"       -> { return handleUseAbility(sender, args); }
            case "vskin"      -> { return handleSkinGui(sender); }
            case "vplay"      -> { return handlePlayLobby(sender); }
            case "vskip"      -> { return handleSkipVote(sender); }
        }

        // Main /valorant command
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String noPermMsg = plugin.msg("errors.no-permission");

        return switch (args[0].toLowerCase()) {
            case "help"    -> { sendHelp(sender); yield true; }
            case "join"    -> handleJoin(sender, args);
            case "leave"   -> handleLeave(sender);
            case "create"  -> handleCreate(sender, args);
            case "start"   -> handleStart(sender, args);
            case "shop"    -> handleShop(sender);
            case "agent"   -> handleAgent(sender);
            case "stats"   -> handleStats(sender, args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0]);
            case "maps"    -> handleMaps(sender);
            case "skins"   -> handleSkins(sender);
            case "tp"      -> handleTp(sender, args);
            case "reload"  -> handleReload(sender);
            case "forcestart" -> {
                if (!sender.hasPermission("valorantmc.admin")) { sender.sendMessage(noPermMsg); yield true; }
                if (args.length < 3) { sender.sendMessage(ValorantMC.colorize("&cUsage: /valorant forcestart <id> <map>")); yield true; }
                if (plugin.getGameManager().forceStart(args[1], args[2])) {
                    sender.sendMessage(ValorantMC.colorize("&aForce-started game &e" + args[1] + "&a on map &e" + args[2]));
                } else {
                    sender.sendMessage(ValorantMC.colorize("&cGame not found: " + args[1]));
                }
                yield true;
            }
            case "kick" -> {
                if (!sender.hasPermission("valorantmc.admin")) { sender.sendMessage(noPermMsg); yield true; }
                if (args.length < 2) { sender.sendMessage(ValorantMC.colorize("&cUsage: /valorant kick <player>")); yield true; }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage(ValorantMC.colorize("&cPlayer not found: " + args[1])); yield true; }
                String lobbySpawnStr = plugin.getConfig().getString("lobby-spawn", null);
                Location lobbyLoc = null;
                if (lobbySpawnStr != null) {
                    try {
                        String[] parts = lobbySpawnStr.split(",");
                        World w = Bukkit.getWorld(parts[0]);
                        if (w != null) lobbyLoc = new Location(w, Double.parseDouble(parts[1]),
                                Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
                    } catch (Exception ignored) {}
                }
                if (plugin.getGameManager().kickFromGame(target, lobbyLoc)) {
                    sender.sendMessage(ValorantMC.colorize("&aKicked &e" + target.getName() + "&a from their game."));
                    target.sendMessage(ValorantMC.colorize("&c[Admin] You were removed from the game."));
                } else {
                    sender.sendMessage(ValorantMC.colorize("&c" + target.getName() + " is not in a game."));
                }
                yield true;
            }
            case "pause" -> {
                if (!sender.hasPermission("valorantmc.admin")) { sender.sendMessage(noPermMsg); yield true; }
                if (args.length < 2) { sender.sendMessage(ValorantMC.colorize("&cUsage: /valorant pause <id>")); yield true; }
                ValorantGame gp = plugin.getGameManager().getGame(args[1]);
                if (gp == null) { sender.sendMessage(ValorantMC.colorize("&cGame not found: " + args[1])); yield true; }
                gp.pause();
                sender.sendMessage(ValorantMC.colorize("&eGame &b" + args[1] + "&e paused."));
                yield true;
            }
            case "resume" -> {
                if (!sender.hasPermission("valorantmc.admin")) { sender.sendMessage(noPermMsg); yield true; }
                if (args.length < 2) { sender.sendMessage(ValorantMC.colorize("&cUsage: /valorant resume <id>")); yield true; }
                ValorantGame gr = plugin.getGameManager().getGame(args[1]);
                if (gr == null) { sender.sendMessage(ValorantMC.colorize("&cGame not found: " + args[1])); yield true; }
                gr.resume();
                sender.sendMessage(ValorantMC.colorize("&aGame &b" + args[1] + "&a resumed."));
                yield true;
            }
            case "status" -> {
                if (args.length < 2) { sender.sendMessage(ValorantMC.colorize("&cUsage: /valorant status <id>")); yield true; }
                ValorantGame gs = plugin.getGameManager().getGame(args[1]);
                if (gs == null) { sender.sendMessage(ValorantMC.colorize("&cGame not found: " + args[1])); yield true; }
                sender.sendMessage(ValorantMC.colorize("&b=== Game: " + args[1] + " ==="));
                sender.sendMessage(ValorantMC.colorize("&7Map: &e"   + gs.getMapName()));
                sender.sendMessage(ValorantMC.colorize("&7State: &e" + gs.getState()));
                sender.sendMessage(ValorantMC.colorize("&7Round: &e" + gs.getCurrentRound()));
                sender.sendMessage(ValorantMC.colorize("&7Score: &c" + gs.getAttackers().getRoundWins() + " &7- &b" + gs.getDefenders().getRoundWins()));
                sender.sendMessage(ValorantMC.colorize("&7Spike: &e" + (gs.getSpike().isPlanted() ? "PLANTED" : gs.getSpike().isCarried() ? "Carried" : "Idle")));
                long aAlive = gs.getAttackers().getOnlinePlayers().stream().filter(pp -> !gs.getAttackers().isDead(pp)).count();
                long dAlive = gs.getDefenders().getOnlinePlayers().stream().filter(pp -> !gs.getDefenders().isDead(pp)).count();
                sender.sendMessage(ValorantMC.colorize("&cAttackers: &f" + aAlive + "/" + gs.getAttackers().size() + " alive"));
                sender.sendMessage(ValorantMC.colorize("&bDefenders: &f" + dAlive + "/" + gs.getDefenders().size() + " alive"));
                yield true;
            }
            case "list" -> {
                var allGames = plugin.getGameManager().getAllGames();
                if (allGames.isEmpty()) { sender.sendMessage(ValorantMC.colorize("&7No active games.")); yield true; }
                sender.sendMessage(ValorantMC.colorize("&b=== Active Games ==="));
                for (ValorantGame gl : allGames) {
                    sender.sendMessage(ValorantMC.colorize("  &e" + gl.getId() + " &8| &7Map: &f" + gl.getMapName()
                            + " &8| &7State: &f" + gl.getState()
                            + " &8| &7Players: &f" + gl.getAllPlayers().size()));
                }
                yield true;
            }
            case "setlobby" -> {
                if (!sender.hasPermission("valorantmc.admin")) { sender.sendMessage(noPermMsg); yield true; }
                if (!(sender instanceof Player sp)) { sender.sendMessage("Must be a player."); yield true; }
                plugin.getLobbyManager().setLobbySpawn(sp.getLocation());
                sender.sendMessage(ValorantMC.colorize("&aLobby spawn set to your current location."));
                yield true;
            }
            default        -> { sender.sendMessage(plugin.msg("errors.unknown-command")); yield true; }
        };
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("errors.player-only")); return true; }
        if (args.length < 2) { p.sendMessage(ValorantMC.colorize("&cUsage: /valorant join <game-id>")); return true; }
        plugin.getGameManager().joinGame(p, args[1]);
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("errors.player-only")); return true; }
        if (!plugin.getGameManager().isInGame(p)) { p.sendMessage(plugin.msg("game.not-in-game")); return true; }
        plugin.getGameManager().leaveGame(p);
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valorantmc.admin")) { sender.sendMessage(plugin.msg("errors.no-permission")); return true; }
        if (args.length < 2) { sender.sendMessage(ValorantMC.colorize("&cUsage: /valorant create <id>")); return true; }
        String id = args[1];
        plugin.getGameManager().createGame(id);
        sender.sendMessage(ValorantMC.colorize("&aGame &e" + id + "&a created!"));
        return true;
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valorantmc.admin")) { sender.sendMessage(plugin.msg("errors.no-permission")); return true; }
        if (args.length < 3) { sender.sendMessage(ValorantMC.colorize("&cUsage: /valorant start <id> <map>")); return true; }
        ValorantGame game = plugin.getGameManager().getGame(args[1]);
        if (game == null) { sender.sendMessage(ValorantMC.colorize("&cGame not found!")); return true; }
        if (!plugin.getMapManager().hasMap(args[2])) {
            sender.sendMessage(ValorantMC.colorize("&cMap not found! Use /valorant maps to list maps."));
            return true;
        }
        game.start(args[2]);
        sender.sendMessage(ValorantMC.colorize("&aStarted game &e" + args[1] + "&a on map &e" + args[2]));
        return true;
    }

    private boolean handleShop(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("errors.player-only")); return true; }
        ValorantGame game = plugin.getGameManager().getGame(p);
        if (game == null) { p.sendMessage(plugin.msg("game.not-in-game")); return true; }
        p.openInventory(ShopGUI.build(p));
        return true;
    }

    private boolean handleAgent(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("errors.player-only")); return true; }
        ValorantGame game = plugin.getGameManager().getGame(p);
        if (game == null) { p.sendMessage(plugin.msg("game.not-in-game")); return true; }
        p.openInventory(AgentSelectGUI.build(p));
        return true;
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("errors.player-only")); return true; }
        Player target = args.length > 0 ? plugin.getServer().getPlayerExact(args[0]) : p;
        if (target == null) { sender.sendMessage(ValorantMC.colorize("&cPlayer not found!")); return true; }

        StatsManager.PlayerStats stats = plugin.getStatsManager().getStats(target.getUniqueId());
        sender.sendMessage(ValorantMC.colorize("&6&l=== " + target.getName() + "'s Stats ==="));
        sender.sendMessage(ValorantMC.colorize("&7Kills:        &f" + stats.kills));
        sender.sendMessage(ValorantMC.colorize("&7Deaths:       &f" + stats.deaths));
        sender.sendMessage(ValorantMC.colorize("&7KDR:          &f" + String.format("%.2f", stats.getKDR())));
        sender.sendMessage(ValorantMC.colorize("&7Headshots:    &f" + stats.headshots));
        sender.sendMessage(ValorantMC.colorize("&7HS%:          &f" + String.format("%.1f%%", stats.getHSPct())));
        sender.sendMessage(ValorantMC.colorize("&7Assists:      &f" + stats.assists));
        sender.sendMessage(ValorantMC.colorize("&7Rounds Won:   &f" + stats.roundsWon + "/" + stats.roundsPlayed));
        return true;
    }

    private boolean handleMaps(CommandSender sender) {
        sender.sendMessage(ValorantMC.colorize("&6Available Maps:"));
        for (var map : plugin.getMapManager().getAllMaps()) {
            int sites = map.getSiteA().size() + map.getSiteB().size();
            boolean ready = !map.getAttackSpawns().isEmpty();
            sender.sendMessage(ValorantMC.colorize(
                    "  &7- &f" + map.getDisplayName() +
                    " &8(" + map.getName() + ")" +
                    (ready ? " &a[Ready]" : " &c[No spawns set]")));
        }
        sender.sendMessage(ValorantMC.colorize("&8Place map YAMLs in plugins/ValorantMC/maps/"));
        return true;
    }

    private boolean handleSkins(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("errors.player-only")); return true; }
        sender.sendMessage(ValorantMC.colorize("&6Available Skins:"));
        for (var skin : plugin.getSkinManager().getAllSkins()) {
            boolean owned = plugin.getSkinManager().hasSkin(p.getUniqueId(), skin.id());
            sender.sendMessage(ValorantMC.colorize(
                    "  " + (owned ? "&a✔" : "&8✗") +
                    " &f" + skin.displayName() +
                    " &8[" + skin.weaponType().getDisplayName() + "]" +
                    " &7— " + skin.tier().displayName));
        }
        return true;
    }

    private boolean handleTp(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valorantmc.admin")) { sender.sendMessage(plugin.msg("errors.no-permission")); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("errors.player-only")); return true; }
        if (args.length < 2) { p.sendMessage(ValorantMC.colorize("&cUsage: /valorant tp <game-id>")); return true; }
        ValorantGame game = plugin.getGameManager().getGame(args[1]);
        if (game == null) { p.sendMessage(ValorantMC.colorize("&cGame not found: " + args[1])); return true; }
        List<Location> spawns = game.getAttackSpawnsPublic();
        if (spawns.isEmpty()) { p.sendMessage(ValorantMC.colorize("&cNo spawn points configured for game &e" + args[1] + "&c.")); return true; }
        p.teleport(spawns.get(0));
        p.sendMessage(ValorantMC.colorize("&aTeleported to game &e" + args[1] + "&a's attacker spawn."));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("valorantmc.admin")) { sender.sendMessage(plugin.msg("errors.no-permission")); return true; }
        plugin.reloadConfig();
        sender.sendMessage(ValorantMC.colorize("&aValorantMC config reloaded!"));
        return true;
    }

    // ── Mod-keybind commands ──────────────────────────────────────────────────

    private boolean handleReloadWeapon(CommandSender sender) {
        if (!(sender instanceof Player p)) return true;
        com.valorantmc.weapons.Weapon w = plugin.getWeaponManager().getHeldWeapon(p);
        if (w == null) { p.sendMessage(ValorantMC.colorize("&cNo weapon held.")); return true; }
        plugin.getWeaponManager().tryReload(p, w);
        return true;
    }

    private boolean handleDropSpike(CommandSender sender) {
        if (!(sender instanceof Player p)) return true;
        ValorantGame g = plugin.getGameManager().getGame(p);
        if (g == null) return true;
        com.valorantmc.game.Spike s = g.getSpike();
        if (s.getCarrierUUID() != null && s.getCarrierUUID().equals(p.getUniqueId())) {
            s.drop(p.getLocation());
            // Remove only the NBT-tagged spike item, never a generic red dye
            org.bukkit.NamespacedKey spikeKey = new org.bukkit.NamespacedKey(plugin, "spike");
            for (int si = 0; si < p.getInventory().getSize(); si++) {
                org.bukkit.inventory.ItemStack sit = p.getInventory().getItem(si);
                if (sit == null || !sit.hasItemMeta()) continue;
                Boolean flag = sit.getItemMeta().getPersistentDataContainer()
                        .get(spikeKey, org.bukkit.persistence.PersistentDataType.BOOLEAN);
                if (Boolean.TRUE.equals(flag)) { p.getInventory().setItem(si, null); break; }
            }
            p.sendMessage(ValorantMC.colorize("&cSpike dropped at your feet."));
        }
        return true;
    }

    private boolean handleWalkToggle(CommandSender sender) {
        if (!(sender instanceof Player p)) return true;
        boolean nowWalking = !p.isSneaking();
        // Toggle a slow-walk marker via metadata; WeaponListener already reduces spread when moving.
        p.setWalkSpeed(p.getWalkSpeed() < 0.18f ? 0.2f : 0.1f);
        p.sendActionBar(ValorantMC.colorize(p.getWalkSpeed() < 0.18f ? "&7Walking…" : "&fRunning"));
        return true;
    }

    private boolean handlePlayLobby(CommandSender sender) {
        if (!(sender instanceof Player p)) return true;
        p.openInventory(com.valorantmc.shop.LobbyGUI.build(p));
        return true;
    }

    private boolean handleSkinGui(CommandSender sender) {
        if (!(sender instanceof Player p)) return true;
        p.openInventory(com.valorantmc.shop.SkinGUI.build(p, null));
        return true;
    }

    private boolean handleSkipVote(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage(plugin.msg("errors.player-only")); return true; }
        ValorantGame game = plugin.getGameManager().getGame(p);
        if (game == null) { p.sendMessage(plugin.msg("game.not-in-game")); return true; }
        if (game.getState() != com.valorantmc.game.GameState.BUY_PHASE) {
            p.sendMessage(ValorantMC.colorize("&cCan only vote to skip during the buy phase."));
            return true;
        }
        // Key includes round number so votes reset automatically each round
        String voteKey = game.getId() + ":" + game.getCurrentRound();
        Set<UUID> votes = skipVotes.computeIfAbsent(voteKey, k -> ConcurrentHashMap.newKeySet());
        if (!votes.add(p.getUniqueId())) {
            p.sendMessage(ValorantMC.colorize("&7You already voted to skip."));
            return true;
        }
        int total = game.getAllPlayers().size();
        int needed = Math.max(1, (int) Math.ceil(total * 0.6)); // 60% needed
        game.broadcast(ValorantMC.colorize("&e" + p.getName() + " &7voted to skip buy phase. &8("
                + votes.size() + "/" + needed + " needed)"));
        if (votes.size() >= needed) {
            skipVotes.remove(voteKey);
            game.broadcast(ValorantMC.colorize("&a&lSkip vote passed! &fStarting round now..."));
            game.adminSkipBuyPhase();
        }
        return true;
    }

    private boolean handleUseAbility(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length < 1) { p.sendMessage(ValorantMC.colorize("&cUsage: /vuse <C|Q|E|X>")); return true; }
        ValorantGame g = plugin.getGameManager().getGame(p);
        if (g == null) return true;
        char key = Character.toUpperCase(args[0].charAt(0));
        plugin.getAbilityManager().activateAbility(p, key, g);
        return true;
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ValorantMC.colorize("&c&lVALORANT&r&b MC &8— Commands"));
        sender.sendMessage(ValorantMC.colorize("&e/valorant join <id>       &7— Join a game"));
        sender.sendMessage(ValorantMC.colorize("&e/valorant leave           &7— Leave current game"));
        sender.sendMessage(ValorantMC.colorize("&e/vshop                    &7— Open the buy menu"));
        sender.sendMessage(ValorantMC.colorize("&e/vagent                   &7— Select your agent"));
        sender.sendMessage(ValorantMC.colorize("&e/vstats [player]          &7— View stats"));
        sender.sendMessage(ValorantMC.colorize("&e/valorant maps            &7— List available maps"));
        sender.sendMessage(ValorantMC.colorize("&e/valorant skins           &7— View weapon skins"));
        if (sender.hasPermission("valorantmc.admin")) {
            sender.sendMessage(ValorantMC.colorize("&6Admin:"));
            sender.sendMessage(ValorantMC.colorize("&6/valorant create <id>     &7— Create a game"));
            sender.sendMessage(ValorantMC.colorize("&6/valorant start <id> <map> &7— Start game on map"));
            sender.sendMessage(ValorantMC.colorize("&6/valorant reload          &7— Reload config"));
        }
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of(
                    "help", "join", "leave", "shop", "agent", "stats", "maps", "skins",
                    "create", "start", "list", "status", "tp", "setlobby", "reload"));
            if (sender.hasPermission("valorantmc.admin")) {
                subs.addAll(List.of("forcestart", "kick", "pause", "resume"));
            }
            return subs;
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "join", "start", "forcestart", "tp", "status", "pause", "resume" ->
                        new ArrayList<>(plugin.getGameManager().getGameIds());
                case "kick", "stats" -> plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName).toList();
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "start", "forcestart" -> new ArrayList<>(plugin.getMapManager().getMapNames());
                default -> List.of();
            };
        }
        return List.of();
    }
}
