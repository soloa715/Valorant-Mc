package com.valorantmc.mod.server;

public enum WeaponCategory {
    SIDEARM("Sidearm"),
    SMG("SMG"),
    SHOTGUN("Shotgun"),
    RIFLE("Rifle"),
    SNIPER("Sniper"),
    HEAVY("Heavy"),
    MELEE("Melee");

    private final String displayName;
    WeaponCategory(String displayName) { this.displayName = displayName; }
    public String getDisplayName() { return displayName; }
}
