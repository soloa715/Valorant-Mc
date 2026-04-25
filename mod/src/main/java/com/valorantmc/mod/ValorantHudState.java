package com.valorantmc.mod;

/**
 * Holds the latest HUD snapshot received from the server.
 * Mutated by the network thread, read by the render thread —
 * all fields are volatile to ensure visibility without locking.
 */
public final class ValorantHudState {

    private ValorantHudState() {}

    public static volatile boolean active        = false;

    // Health / shield
    public static volatile int health    = 100;
    public static volatile int shield    = 0;

    // Weapon ammo
    public static volatile int ammo      = 0;
    public static volatile int maxAmmo   = 0;
    public static volatile int reserve   = 0;

    // Ability charges  (–1 = not tracked)
    public static volatile int chargesC  = -1;
    public static volatile int chargesQ  = -1;
    public static volatile int chargesE  = -1;

    // Ultimate progress
    public static volatile int ultProgress = 0;
    public static volatile int ultMax      = 7;

    // Agent display name (empty = none)
    public static volatile String agentName = "";

    /** Reset to defaults when leaving a server */
    public static void clear() {
        active = false;
        health = 100; shield = 0;
        ammo = 0; maxAmmo = 0; reserve = 0;
        chargesC = -1; chargesQ = -1; chargesE = -1;
        ultProgress = 0; ultMax = 7;
        agentName = "";
    }
}
