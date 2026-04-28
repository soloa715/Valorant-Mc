package com.valorantmc.mod.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {

    private final Map<UUID, Integer> credits   = new HashMap<>();
    private final Map<UUID, Integer> vpBalance = new HashMap<>();

    private static final int MAX_CREDITS     = 9000;
    private static final int STARTING_CREDITS = 800;

    public void initPlayer(UUID uuid) {
        credits.putIfAbsent(uuid, STARTING_CREDITS);
    }

    public int getCredits(UUID uuid) {
        return credits.getOrDefault(uuid, 0);
    }

    public void setCredits(UUID uuid, int amount) {
        credits.put(uuid, Math.min(MAX_CREDITS, Math.max(0, amount)));
    }

    public void addCredits(UUID uuid, int amount) {
        setCredits(uuid, getCredits(uuid) + amount);
    }

    public boolean spend(UUID uuid, int amount) {
        int current = getCredits(uuid);
        if (current < amount) return false;
        credits.put(uuid, current - amount);
        return true;
    }

    public boolean canAfford(UUID uuid, int cost) {
        return getCredits(uuid) >= cost;
    }

    public void clearPlayer(UUID uuid) {
        credits.remove(uuid);
        vpBalance.remove(uuid);
    }

    // ── VP ────────────────────────────────────────────────────────────────────

    public int  getVP(UUID uuid)             { return vpBalance.getOrDefault(uuid, 0); }
    public void addVP(UUID uuid, int amount) { vpBalance.merge(uuid, amount, Integer::sum); }

    public boolean canAffordVP(UUID uuid, int cost) { return getVP(uuid) >= cost; }

    public boolean spendVP(UUID uuid, int cost) {
        if (!canAffordVP(uuid, cost)) return false;
        vpBalance.put(uuid, getVP(uuid) - cost);
        return true;
    }

    // ── Reset for new round ───────────────────────────────────────────────────

    /** Give round-start credits: 5000 for round 1, otherwise loss bonus or win bonus. */
    public void giveRoundStartCredits(UUID uuid, boolean wonLastRound, int roundNumber) {
        if (roundNumber == 1) {
            setCredits(uuid, 800);
            return;
        }
        int bonus = wonLastRound ? 3000 : 1900;
        addCredits(uuid, bonus);
    }
}
