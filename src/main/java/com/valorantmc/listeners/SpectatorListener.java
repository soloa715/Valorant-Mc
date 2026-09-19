package com.valorantmc.listeners;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.game.ValorantTeam;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;

public class SpectatorListener implements Listener {

    private final ValorantMC plugin;

    public SpectatorListener(ValorantMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Every time a spectating dead player moves, ensure their spectator target is
     * still a living teammate. If it drifted (target died, left, or is an enemy),
     * snap them to the next valid teammate.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSpectatorMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SPECTATOR) return;
        ValorantGame game = plugin.getGameManager().getGame(p);
        if (game == null) return;

        Entity current = p.getSpectatorTarget();
        if (current == null || !(current instanceof Player watched) || isEnemyOf(p, watched, game)) {
            reassignTarget(p, game);
        }
    }

    /**
     * Left-click or right-click cycles through alive teammates.
     * Cancels the interact so no accidental actions fire.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpectatorClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SPECTATOR) return;
        ValorantGame game = plugin.getGameManager().getGame(p);
        if (game == null) return;

        e.setCancelled(true);
        boolean backward = e.getAction() == Action.LEFT_CLICK_AIR
                        || e.getAction() == Action.LEFT_CLICK_BLOCK;
        cycleTarget(p, game, backward);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void reassignTarget(Player spectator, ValorantGame game) {
        List<Player> alive = aliveTeammates(spectator, game);
        spectator.setSpectatorTarget(alive.isEmpty() ? null : alive.get(0));
        if (!alive.isEmpty()) {
            spectator.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§7Spectating §f" + alive.get(0).getName()
                    + " §8| §7Click to cycle teammates"));
        }
    }

    private void cycleTarget(Player spectator, ValorantGame game, boolean backward) {
        List<Player> alive = aliveTeammates(spectator, game);
        if (alive.isEmpty()) {
            spectator.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§7No alive teammates to spectate."));
            return;
        }

        Entity current = spectator.getSpectatorTarget();
        int idx = 0;
        if (current instanceof Player watched) {
            int found = alive.indexOf(watched);
            if (found >= 0) {
                idx = backward
                        ? (found - 1 + alive.size()) % alive.size()
                        : (found + 1) % alive.size();
            }
        }
        spectator.setSpectatorTarget(alive.get(idx));
        spectator.sendActionBar(net.kyori.adventure.text.Component.text(
                "§7Spectating §f" + alive.get(idx).getName()
                + " §8[" + (idx + 1) + "/" + alive.size() + "]"
                + " §8| §7Click to cycle"));
    }

    private List<Player> aliveTeammates(Player spectator, ValorantGame game) {
        ValorantTeam team = game.getTeam(spectator);
        if (team == null) return List.of();
        return team.getOnlinePlayers().stream()
                .filter(tp -> !team.isDead(tp) && !tp.getUniqueId().equals(spectator.getUniqueId()))
                .toList();
    }

    private boolean isEnemyOf(Player spectator, Player target, ValorantGame game) {
        ValorantTeam st = game.getTeam(spectator);
        ValorantTeam tt = game.getTeam(target);
        if (st == null || tt == null) return true;
        return st.getSide() != tt.getSide();
    }
}
