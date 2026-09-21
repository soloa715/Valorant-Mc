package com.valorantmc.commands;

import com.valorantmc.ValorantMC;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides comprehensive auto-completion for all /valorant subcommands,
 * admin commands, and player shortcuts.
 */
public class ValorantTabCompleter implements TabCompleter {

    private final ValorantMC plugin;

    private static final List<String> MAIN_SUBCOMMANDS = Arrays.asList(
            "help", "join", "quick", "leave", "create", "start", "forcestart",
            "shop", "agent", "stats", "maps", "skins", "tp", "reload",
            "pause", "resume", "status", "kick", "list", "setlobby",
            "gun", "weapon", "giveweapon", "credits", "money",
            "skip", "dropspike", "walk", "use", "skin", "play", "admin",
            "custom", "scoreboard", "spec", "pack", "web"
    );

    private static final List<String> TEAMS = Arrays.asList("atk", "def");
    private static final List<String> ABILITY_SLOTS = Arrays.asList("C", "Q", "E", "X");
    private static final List<String> CREDIT_SUGGESTIONS = Arrays.asList("100", "500", "800", "1000", "2000", "2900", "4000", "5000", "9000");
    private static final List<String> ADMIN_SUBS = Arrays.asList("panel", "web", "reload");

    public ValorantTabCompleter(ValorantMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmdName = command.getName().toLowerCase();

        // ── Direct command shortcuts ─────────────────────────────────────────
        if (cmdName.equals("vagent") || alias.equalsIgnoreCase("agent")) {
            if (args.length == 1) return filterCompletions(args[0], getAgentNames());
            return Collections.emptyList();
        }

        if (cmdName.equals("vjoin")) {
            if (args.length == 1) return filterCompletions(args[0], getGameIds());
            if (args.length == 2) return filterCompletions(args[1], TEAMS);
            return Collections.emptyList();
        }

        if (cmdName.equals("vstart")) {
            if (args.length == 1) return filterCompletions(args[0], getGameIds());
            if (args.length == 2) return filterCompletions(args[1], getMapNames());
            return Collections.emptyList();
        }

        if (cmdName.equals("vuse")) {
            if (args.length == 1) return filterCompletions(args[0], ABILITY_SLOTS);
            return Collections.emptyList();
        }

        if (cmdName.equals("vstats")) {
            if (args.length == 1) return filterCompletions(args[0], getPlayerNames());
            return Collections.emptyList();
        }

        if (cmdName.equals("vspec") || alias.equalsIgnoreCase("spec")) {
            if (args.length == 1) return filterCompletions(args[0], getPlayerNames());
            return Collections.emptyList();
        }

        if (cmdName.equals("vskin")) {
            if (args.length == 1) return filterCompletions(args[0], getWeaponNames());
            return Collections.emptyList();
        }

        if (cmdName.equals("vadmin") || alias.equalsIgnoreCase("admin")) {
            if (args.length == 1) return filterCompletions(args[0], ADMIN_SUBS);
            return Collections.emptyList();
        }

        if (cmdName.equals("vcustom") || alias.equalsIgnoreCase("custom")) {
            if (args.length == 1) return filterCompletions(args[0], Arrays.asList("create", "settings"));
            return Collections.emptyList();
        }

        // Commands with no arguments needed
        if (Arrays.asList("vshop", "vskip", "vreload", "vdropspike", "vwalk", "vplay", "vscoreboard", "vleave", "vquick", "vpack").contains(cmdName)) {
            return Collections.emptyList();
        }

        // ── Main /valorant (or /val, /v) command ──────────────────────────────
        if (cmdName.equals("valorant") || alias.equalsIgnoreCase("val") || alias.equalsIgnoreCase("v")) {
            if (args.length == 1) {
                return filterCompletions(args[0], MAIN_SUBCOMMANDS);
            }

            String sub = args[0].toLowerCase();
            switch (sub) {
                case "gun", "weapon", "giveweapon" -> {
                    if (args.length == 2) return filterCompletions(args[1], getWeaponNames());
                    if (args.length == 3) return filterCompletions(args[2], getPlayerNames());
                }
                case "credits", "money" -> {
                    if (args.length == 2) return filterCompletions(args[1], CREDIT_SUGGESTIONS);
                    if (args.length == 3) return filterCompletions(args[2], getPlayerNames());
                }
                case "start", "forcestart" -> {
                    if (args.length == 2) return filterCompletions(args[1], getGameIds());
                    if (args.length == 3) return filterCompletions(args[2], getMapNames());
                }
                case "join" -> {
                    if (args.length == 2) return filterCompletions(args[1], getGameIds());
                    if (args.length == 3) return filterCompletions(args[2], TEAMS);
                }
                case "create", "pause", "resume", "status" -> {
                    if (args.length == 2) return filterCompletions(args[1], getGameIds());
                }
                case "kick", "stats", "spec" -> {
                    if (args.length == 2) return filterCompletions(args[1], getPlayerNames());
                }
                case "agent" -> {
                    if (args.length == 2) return filterCompletions(args[1], getAgentNames());
                }
                case "tp" -> {
                    if (args.length == 2) return filterCompletions(args[1], getMapNames());
                }
                case "skins", "skin" -> {
                    if (args.length == 2) return filterCompletions(args[1], getWeaponNames());
                }
                case "use" -> {
                    if (args.length == 2) return filterCompletions(args[1], ABILITY_SLOTS);
                }
                case "custom" -> {
                    if (args.length == 2) return filterCompletions(args[1], Arrays.asList("create", "settings"));
                }
                case "admin" -> {
                    if (args.length == 2) return filterCompletions(args[1], ADMIN_SUBS);
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> getWeaponNames() {
        return Arrays.stream(WeaponType.values())
                .map(w -> w.name().toLowerCase())
                .collect(Collectors.toList());
    }

    private List<String> getAgentNames() {
        return plugin.getAgentManager().getAllAgents().stream()
                .map(a -> a.getName().toLowerCase())
                .collect(Collectors.toList());
    }

    private List<String> getMapNames() {
        return new ArrayList<>(plugin.getMapManager().getMapNames());
    }

    private List<String> getGameIds() {
        List<String> ids = new ArrayList<>(plugin.getGameManager().getGameIds());
        if (ids.isEmpty()) ids.add("game1");
        return ids;
    }

    private List<String> getPlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> filterCompletions(String input, List<String> candidates) {
        if (candidates == null) return Collections.emptyList();
        String lower = input.toLowerCase();
        return candidates.stream()
                .filter(c -> c.toLowerCase().startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }
}
