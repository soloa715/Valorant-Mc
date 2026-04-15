package com.valorantmc.agents;

import org.bukkit.ChatColor;

public enum AgentRole {
    DUELIST   ("Duelist",    ChatColor.RED,    "Self-sufficient fraggers who their team to take space."),
    INITIATOR ("Initiator",  ChatColor.GOLD,   "Challenge holders and open up the map with recon."),
    CONTROLLER("Controller", ChatColor.BLUE,   "Shape the battle-field with smokes and walls."),
    SENTINEL  ("Sentinel",   ChatColor.WHITE,  "Defensive experts who anchor or protect team flanks.");

    private final String displayName;
    private final ChatColor color;
    private final String description;

    AgentRole(String displayName, ChatColor color, String description) {
        this.displayName = displayName;
        this.color       = color;
        this.description = description;
    }

    public String    getDisplayName() { return displayName; }
    public ChatColor getColor()       { return color;       }
    public String    getDescription() { return description; }
}
