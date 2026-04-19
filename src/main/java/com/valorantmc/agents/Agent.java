package com.valorantmc.agents;

import com.valorantmc.game.ValorantGame;
import org.bukkit.entity.Player;

/**
 * Base class for every Valorant agent.
 *
 * Ability slots (Valorant convention):
 *   Q  – Signature / Ability 1
 *   E  – Ability 2
 *   C  – Ability 3  (bought each round)
 *   X  – Ultimate
 *
 * We map these to hotbar slots 1-4 with custom items.
 */
public abstract class Agent {

    public static class Ability {
        public final String  name;
        public final int     cost;           // 0 = free/signature
        public final int     charges;        // how many uses per round
        public final int     ultimatePoints; // 0 unless this is the ult
        private       int    currentCharges;
        private       int    ultimateProgress;
        private       long   cooldownEnd = 0; // epoch ms when cooldown expires

        public Ability(String name, int cost, int charges, int ultimatePoints) {
            this.name            = name;
            this.cost            = cost;
            this.charges         = charges;
            this.ultimatePoints  = ultimatePoints;
            this.currentCharges  = charges;
        }

        public boolean canUse()       { return currentCharges > 0 && !isOnCooldown(); }
        public void    consume()      { currentCharges = Math.max(0, currentCharges - 1); }
        public void    resetCharges() { currentCharges = charges; cooldownEnd = 0; }
        public int     getCurrentCharges()    { return currentCharges; }
        public int     getUltimateProgress()  { return ultimateProgress; }
        public void    addUltPoint()          { ultimateProgress++; }
        public boolean isUltReady()           { return ultimatePoints > 0 && ultimateProgress >= ultimatePoints; }
        public void    activateUlt()          { if (isUltReady()) { ultimateProgress = 0; currentCharges = 1; } }
        /** Add one charge (used by admin give). */
        public void    addCharge()            { currentCharges = Math.min(currentCharges + 1, Math.max(charges, currentCharges + 1)); }

        /** Start a cooldown (milliseconds). canUse() returns false while active. */
        public void    setCooldown(long ms)   { cooldownEnd = System.currentTimeMillis() + ms; }
        public boolean isOnCooldown()         { return System.currentTimeMillis() < cooldownEnd; }
        /** Remaining cooldown in seconds (0 if not on cooldown). */
        public double  getCooldownSeconds()   { return Math.max(0, (cooldownEnd - System.currentTimeMillis()) / 1000.0); }
    }

    protected final AgentRole role;
    protected final String    name;
    protected final String    displayName;

    protected Ability abilityQ;   // Signature
    protected Ability abilityE;
    protected Ability abilityC;
    protected Ability abilityX;   // Ultimate

    protected Agent(String name, String displayName, AgentRole role) {
        this.name        = name;
        this.displayName = displayName;
        this.role        = role;
    }

    // ── Abstract ability activations ─────────────────────────────────────────

    public abstract void useQ(Player player, ValorantGame game);
    public abstract void useE(Player player, ValorantGame game);
    public abstract void useC(Player player, ValorantGame game);
    public abstract void useX(Player player, ValorantGame game);

    // ── Round lifecycle ───────────────────────────────────────────────────────

    /** Called at the start of each round to restore charges */
    public void onRoundStart(Player player) {
        abilityQ.resetCharges();
        abilityE.resetCharges();
        abilityC.resetCharges();
        // X is NOT reset — charges accumulate across rounds
    }

    /** Fill ultimate to max (admin give) */
    public void fillUlt() {
        if (abilityX.ultimatePoints > 0)
            while (!abilityX.isUltReady()) abilityX.addUltPoint();
    }

    /** Called when this agent kills someone */
    public void onKill(Player player, Player victim, ValorantGame game) {
        abilityX.addUltPoint();
        if (abilityX.isUltReady()) {
            player.sendMessage(com.valorantmc.ValorantMC.colorize(
                    "&6&lULTIMATE READY! &ePress your Ultimate key to activate &b" + abilityX.name));
        }
    }

    // ── Hotbar setup ─────────────────────────────────────────────────────────

    /**
     * Give ability items to the player's hotbar.
     * Slot layout:
     *   0 = primary weapon   1 = sidearm   2 = knife   3 = spike
     *   4 = C   5 = Q   6 = E   7 = X (ult)
     */
    public void giveAbilityItems(Player player) {
        player.getInventory().setItem(4, buildAbilityItem(abilityC, 'C'));
        player.getInventory().setItem(5, buildAbilityItem(abilityQ, 'Q'));
        player.getInventory().setItem(6, buildAbilityItem(abilityE, 'E'));
        player.getInventory().setItem(7, buildAbilityItem(abilityX, 'X'));
    }

    private org.bukkit.inventory.ItemStack buildAbilityItem(Ability a, char key) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(
                a.ultimatePoints > 0 ? org.bukkit.Material.NETHER_STAR :
                a.cost == 0         ? org.bukkit.Material.LIME_DYE :
                                      org.bukkit.Material.YELLOW_DYE);

        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(com.valorantmc.ValorantMC.colorize("&e[" + key + "] &f" + a.name));

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(com.valorantmc.ValorantMC.colorize("&7Agent: &b" + displayName));
        lore.add(com.valorantmc.ValorantMC.colorize("&7Charges: &f" + a.getCurrentCharges() + "/" + a.charges));
        if (a.cost > 0)
            lore.add(com.valorantmc.ValorantMC.colorize("&7Cost: &6" + a.cost + "c/charge"));
        if (a.ultimatePoints > 0)
            lore.add(com.valorantmc.ValorantMC.colorize("&6Ultimate: &f" + a.getUltimateProgress() + "/" + a.ultimatePoints));
        lore.add("");
        lore.add(com.valorantmc.ValorantMC.colorize("&8Right-click to activate"));
        meta.setLore(lore);

        // NBT to identify ability
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(com.valorantmc.ValorantMC.getInstance(), "ability_key"),
                org.bukkit.persistence.PersistentDataType.STRING, String.valueOf(key));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(com.valorantmc.ValorantMC.getInstance(), "agent_name"),
                org.bukkit.persistence.PersistentDataType.STRING, name);

        item.setItemMeta(meta);
        return item;
    }

    // ── Utility helpers ───────────────────────────────────────────────────────

    /**
     * Null-safe replacement for player.getTargetBlock(null, range).getLocation().
     * If the player is looking at sky/void and no block is found within range,
     * returns a point directly in front of the player at half the range distance
     * instead of crashing with a NullPointerException.
     */
    protected static org.bukkit.Location safeTarget(org.bukkit.entity.Player player, int range) {
        org.bukkit.block.Block b = player.getTargetBlock(null, range);
        if (b != null) return b.getLocation();
        // Fallback: project forward at half range, clamped to non-solid ground
        return player.getEyeLocation().add(
                player.getLocation().getDirection().multiply(range * 0.5));
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String    getName()        { return name;        }
    public String    getDisplayName() { return displayName; }
    public AgentRole getRole()        { return role;        }
    public Ability   getAbilityQ()    { return abilityQ;    }
    public Ability   getAbilityE()    { return abilityE;    }
    public Ability   getAbilityC()    { return abilityC;    }
    public Ability   getAbilityX()    { return abilityX;    }
}
