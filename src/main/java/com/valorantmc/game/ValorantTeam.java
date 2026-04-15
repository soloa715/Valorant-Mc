package com.valorantmc.game;

import com.valorantmc.ValorantMC;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

public class ValorantTeam {

    public enum Side { ATTACKERS, DEFENDERS }

    private Side side;
    private final Set<UUID> members   = new LinkedHashSet<>();
    private final Set<UUID> deadMembers = new HashSet<>();
    private int roundWins = 0;

    public ValorantTeam(Side side) {
        this.side = side;
    }

    // ── Player management ─────────────────────────────────────────────────────

    public void addPlayer(Player p) {
        members.add(p.getUniqueId());
    }

    public void removePlayer(Player p) {
        members.remove(p.getUniqueId());
        deadMembers.remove(p.getUniqueId());
    }

    public boolean contains(Player p)  { return members.contains(p.getUniqueId()); }
    public boolean contains(UUID uuid) { return members.contains(uuid); }

    public void markDead(Player p)  { deadMembers.add(p.getUniqueId()); }
    public void reviveAll()         { deadMembers.clear(); }

    public boolean isDead(Player p)  { return deadMembers.contains(p.getUniqueId()); }
    public boolean isEliminated()    { return !deadMembers.isEmpty() && deadMembers.containsAll(members); }

    // ── Utility ───────────────────────────────────────────────────────────────

    public void broadcast(String message) {
        for (UUID uuid : members) {
            Player p = ValorantMC.getInstance().getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) p.sendMessage(message);
        }
    }

    public List<Player> getOnlinePlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : members) {
            Player p = ValorantMC.getInstance().getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    /** Swap attacker/defender roles (half-time) */
    public void swapSide() {
        side = (side == Side.ATTACKERS) ? Side.DEFENDERS : Side.ATTACKERS;
    }

    public void addRoundWin() { roundWins++; }

    // ── Getters ───────────────────────────────────────────────────────────────
    public Side        getSide()        { return side;        }
    public Set<UUID>   getMembers()     { return Collections.unmodifiableSet(members); }
    public Set<UUID>   getDeadMembers() { return Collections.unmodifiableSet(deadMembers); }
    public int         getRoundWins()   { return roundWins;   }
    public int         size()           { return members.size(); }
    public ChatColor   getChatColor()   { return side == Side.ATTACKERS ? ChatColor.RED : ChatColor.AQUA; }
    public String      getDisplayName() { return side == Side.ATTACKERS ? "Attackers" : "Defenders"; }
}
