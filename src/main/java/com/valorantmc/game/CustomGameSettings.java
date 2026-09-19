package com.valorantmc.game;

/** Holds the configuration for a custom / practice match. */
public class CustomGameSettings {

    // ── Cheats ────────────────────────────────────────────────────────────────
    public boolean unlimitedAbilities = false; // charges never consumed; cooldowns reset instantly
    public boolean infiniteCredits    = false; // credits never decrease; always capped at max
    public boolean wallhack           = false; // enemies permanently glow through walls for your team
    public boolean oneShot            = false; // any hit = instant kill regardless of HP/shield
    public boolean infiniteAmmo       = false; // ammo never decreases; reload still plays animation
    public boolean noCooldowns        = false; // fire rate unlimited; reload time = 0

    // ── Match settings ────────────────────────────────────────────────────────
    public boolean showEnemyHP     = false; // broadcast enemy remaining HP to shooter after each hit
    public boolean allowTeamDamage = false; // friendly fire
    public float   abilityDmgMult  = 1.0f;  // multiply all ability damage (0.5x – 3.0x)
    public int     startingCredits = 800;   // override per-round starting credits
    public int     maxRounds       = 25;    // 0 = unlimited (practice mode, rounds don't end)

    // ── Internal ─────────────────────────────────────────────────────────────
    public String hostUUID = ""; // UUID string of the player who created the match

    public CustomGameSettings() {}
}
