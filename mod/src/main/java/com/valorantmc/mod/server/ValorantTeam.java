package com.valorantmc.mod.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class ValorantTeam {

    public enum Side { ATTACKERS, DEFENDERS }

    private Side side;
    private final Set<UUID> members     = new LinkedHashSet<>();
    private final Set<UUID> deadMembers = new HashSet<>();
    private int roundWins = 0;

    public ValorantTeam(Side side) { this.side = side; }

    // ── Membership ────────────────────────────────────────────────────────────

    public void addPlayer(ServerPlayer p)    { members.add(p.getUUID()); }
    public void removePlayer(ServerPlayer p) { members.remove(p.getUUID()); deadMembers.remove(p.getUUID()); }
    public void removePlayer(UUID uuid)      { members.remove(uuid); deadMembers.remove(uuid); }

    public boolean contains(ServerPlayer p) { return members.contains(p.getUUID()); }
    public boolean contains(UUID uuid)      { return members.contains(uuid); }

    public void markDead(UUID uuid)   { deadMembers.add(uuid); }
    public void revive(UUID uuid)     { deadMembers.remove(uuid); }
    public void reviveAll()           { deadMembers.clear(); }

    public boolean isDead(UUID uuid)   { return deadMembers.contains(uuid); }
    public boolean isEliminated()      { return !members.isEmpty() && deadMembers.containsAll(members); }

    // ── Utility ───────────────────────────────────────────────────────────────

    public void broadcast(MinecraftServer server, String message) {
        for (UUID uuid : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.sendSystemMessage(Component.literal(message));
        }
    }

    public List<ServerPlayer> getOnlinePlayers(MinecraftServer server) {
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID uuid : members) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) list.add(p);
        }
        return list;
    }

    public void swapSide() {
        side = (side == Side.ATTACKERS) ? Side.DEFENDERS : Side.ATTACKERS;
    }

    public void addRoundWin() { roundWins++; }

    // ── Getters ───────────────────────────────────────────────────────────────
    public Side      getSide()        { return side;        }
    public Set<UUID> getMembers()     { return Collections.unmodifiableSet(members); }
    public Set<UUID> getDeadMembers() { return Collections.unmodifiableSet(deadMembers); }
    public int       getRoundWins()   { return roundWins;   }
    public int       size()           { return members.size(); }
    public String    getDisplayName() { return side == Side.ATTACKERS ? "Attackers" : "Defenders"; }
    public int       getColor()       { return side == Side.ATTACKERS ? 0xFF4444 : 0x44AAFF; }
}
