package com.valorantmc.commands;

import com.valorantmc.ValorantMC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TabCompleter for /vmapsetup.
 */
public class MapSetupTabCompleter implements TabCompleter {

    private final ValorantMC plugin;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "create", "edit", "reset", "addspawn", "addsite", "setsite",
            "pos1", "pos2", "radius", "setworld", "save", "list",
            "validate", "tp", "wand", "cancel", "gui", "web"
    );

    private static final List<String> TEAMS = Arrays.asList("atk", "def");
    private static final List<String> SITES = Arrays.asList("a", "b", "c");

    public MapSetupTabCompleter(ValorantMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], SUBCOMMANDS);
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("edit") || sub.equals("reset") || sub.equals("validate") || sub.equals("create")) {
            if (args.length == 2) {
                return filter(args[1], getMapNames());
            }
        }

        if (sub.equals("tp")) {
            if (args.length == 2) return filter(args[1], getMapNames());
            if (args.length == 3) return filter(args[2], TEAMS);
            if (args.length == 4) return filter(args[3], Arrays.asList("1", "2", "3", "4", "5"));
        }

        if (sub.equals("addspawn")) {
            if (args.length == 2) return filter(args[1], TEAMS);
        }

        if (sub.equals("addsite") || sub.equals("setsite")) {
            if (args.length == 2) return filter(args[1], SITES);
        }

        if (sub.equals("radius")) {
            if (args.length == 2) return filter(args[1], Arrays.asList("3", "5", "8", "10", "15"));
        }

        return Collections.emptyList();
    }

    private List<String> getMapNames() {
        return new ArrayList<>(plugin.getMapManager().getMapNames());
    }

    private List<String> filter(String input, List<String> candidates) {
        if (candidates == null) return Collections.emptyList();
        String lower = input.toLowerCase();
        return candidates.stream()
                .filter(c -> c.toLowerCase().startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }
}
