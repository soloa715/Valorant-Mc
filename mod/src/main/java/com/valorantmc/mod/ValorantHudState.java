package com.valorantmc.mod;

/**
 * Latest HUD snapshot received from the server.
 * Mutated on the network thread, read on the render thread — all fields are volatile.
 */
public final class ValorantHudState {

    private ValorantHudState() {}

    public static volatile boolean active = false;

    // Health / shield
    public static volatile int health = 100;
    public static volatile int shield = 0;

    // Weapon ammo
    public static volatile int ammo    = 0;
    public static volatile int maxAmmo = 0;
    public static volatile int reserve = 0;

    // Ability charges (-1 = slot unused)
    public static volatile int chargesC = -1;
    public static volatile int chargesQ = -1;
    public static volatile int chargesE = -1;

    // Ability cooldowns in tenths of a second (0 = ready)
    public static volatile int cooldownC = 0;
    public static volatile int cooldownQ = 0;
    public static volatile int cooldownE = 0;

    // Ultimate
    public static volatile int ultProgress = 0;
    public static volatile int ultMax      = 7;

    // Agent display name
    public static volatile String agentName = "";

    // Economy
    public static volatile int credits = 0;

    // Score
    public static volatile int atkScore = 0;
    public static volatile int defScore = 0;

    // Spike  (0=none, 1=planted, 2=defusing)
    public static volatile int spikeState      = 0;
    public static volatile int spikeTimerTicks = 0;

    // Round phase (0=inactive, 1=buy, 2=active, 3=end)
    public static volatile int roundPhase = 0;

    // Team roster: "Name:HP:Shield:Agent,..." — parsed on demand
    public static volatile String teamRoster = "";

    // Kill feed: "Killer>Victim" — latest entry, cleared after 4 s
    public static volatile String killFeed       = "";
    public static volatile long   killFeedShownAt = 0L;  // System.currentTimeMillis()

    // Radar data (from RadarPayload)
    public static volatile String radarData = "";

    // Buy menu
    public static volatile boolean inBuyPhase = false;

    // Agent / Map selection
    public static volatile java.util.List<String> agentSelectList = java.util.List.of();
    public static volatile String mySelectedAgent = "";
    public static volatile java.util.List<String> mapList    = java.util.List.of();
    public static volatile String currentMap = "";

    public static void clear() {
        active = false;
        health = 100; shield = 0;
        ammo = 0; maxAmmo = 0; reserve = 0;
        chargesC = -1; chargesQ = -1; chargesE = -1;
        cooldownC = 0; cooldownQ = 0; cooldownE = 0;
        ultProgress = 0; ultMax = 7;
        agentName = "";
        credits = 0;
        atkScore = 0; defScore = 0;
        spikeState = 0; spikeTimerTicks = 0;
        roundPhase = 0;
        teamRoster = "";
        killFeed = ""; killFeedShownAt = 0L;
        radarData = "";
        inBuyPhase = false;
    }
}
