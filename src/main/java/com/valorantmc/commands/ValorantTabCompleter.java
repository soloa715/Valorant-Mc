package com.valorantmc.commands;

import com.valorantmc.ValorantMC;
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
 * Provides smart auto-completion for all /valorant subcommands and player shortcuts.
 */
public class ValorantTabCompleter implements TabCompleter {

    private final ValorantMC plugin;

    private static final List<String> MAIN_SUBCOMMANDS = Arrays.asList(
            "join", "leave", "start", "agent", "shop", "skip", "stats",
            "reload", "dropspike", "walk", "use", "skin", "play", "admin",
            "custom", "scoreboard", "spec", "quick", "pack", "web"
    );

    private static final List<String> TEAMS = Arrays.asList("atk", "def");
    private static final List<String> ABILITY_SLOTS = Arrays.asList("C", "Q", "E", "X");

    public ValorantTabCompleter(ValorantMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmdName = command.getName().toLowerCase();

        // 1. Direct command shortcuts (e.g. /vagent, /vjoin, /vstart, /vuse, /vstats)
        if (cmdName.equalsIgnoreCase("vagent")) {
            if (args.length == 1) return filterCompletions(args[0], getAgentNames());
            return Collections.emptyList();
        }

        if (cmdName.equalsIgnoreCase("vjoin")) {
            if (args.length == 1) return filterCompletions(args[0], getGameIds());
            if (args.length == 2) return filterCompletions(args[1], TEAMS);
            return Collections.emptyList();
        }

        if (cmdName.equalsIgnoreCase("vstart")) {
            if (args.length == 1) return filterCompletions(args[0], getGameIds());
            if (args.length == 2) return filterCompletions(args[1], getMapNames());
            return Collections.emptyList();
        }

        if (cmdName.equalsIgnoreCase("vuse")) {
            if (args.length == 1) return filterCompletions(args[0], ABILITY_SLOTS);
            return Collections.emptyList();
        }

        if (cmdName.equalsIgnoreCase("vstats")) {
            if (args.length == 1) return filterCompletions(args[0], getPlayerNames());
            return Collections.emptyList();
        }

        // Commands with no required args
        if (Arrays.asList("vshop", "vskip", "vreload", "vdropspike", "vwalk", "vskin", "vplay", "vadmin", "vcustom", "vscoreboard", "vspec", "vquick", "vpack").contains(cmdName)) {
            return Collections.emptyList();
        }

        // 2. Main command /valorant <subcommand> [args...]
        if (cmdName.equalsIgnoreCase("valorant")) {
            if (args.length == 1) {
                return filterCompletions(args[0], MAIN_SUBCOMMANDS);
            }
            String sub = args[0].toLowerCase();
            if (sub.equals("join")) {
                if (args.length == 2) return filterCompletions(args[1], getGameIds());
                if (args.length == 3) return filterCompletions(args[2], TEAMS);
            } else if (sub.equals("start")) {
                if (args.length == 2) return filterCompletions(args[1], getGameIds());
                if (args.length == 3) return filterCompletions(args[2], getMapNames());
            } else if (sub.equals("agent")) {
                if (args.length == 2) return filterCompletions(args[1], getAgentNames());
            } else if (sub.equals("use")) {
                if (args.length == 2) return filterCompletions(args[1], ABILITY_SLOTS);
            } else if (sub.equals("stats")) {
                if (args.length == 2) return filterCompletions(args[1], getPlayerNames());
            }
        }

        return Collections.emptyList();
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
        if (ids.isEmpty()) ids.add("default");
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
