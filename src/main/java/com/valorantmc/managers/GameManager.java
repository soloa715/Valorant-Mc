package com.valorantmc.managers;

import com.valorantmc.ValorantMC;
import com.valorantmc.game.ValorantGame;
import org.bukkit.entity.Player;

import java.util.*;

public class GameManager {

    private final ValorantMC plugin;
    private final Map<String, ValorantGame> games = new HashMap<>();
    private final Map<UUID, String> playerGameMap  = new HashMap<>();  // player → game id

    public GameManager(ValorantMC plugin) {
        this.plugin = plugin;
    }

    // ── Game lifecycle ────────────────────────────────────────────────────────

    public ValorantGame createGame(String id) {
        ValorantGame game = new ValorantGame(plugin, id);
        games.put(id, game);
        return game;
    }

    public boolean joinGame(Player p, String gameId) {
        if (isInGame(p)) {
            p.sendMessage(plugin.msg("game.already-in-game"));
            return false;
        }
        ValorantGame game = games.get(gameId);
        if (game == null) {
            p.sendMessage(plugin.colorize("&cGame '" + gameId + "' not found!"));
            return false;
        }
        game.addPlayer(p);
        playerGameMap.put(p.getUniqueId(), gameId);

        // Send resource pack if configured
        String rpUrl = plugin.getConfig().getString("resource-pack.url", "");
        if (plugin.getConfig().getBoolean("resource-pack.enabled") && !rpUrl.isEmpty()) {
            p.setResourcePack(rpUrl, plugin.getConfig().getString("resource-pack.hash", ""));
        }
        return true;
    }

    public void leaveGame(Player p) {
        String gameId = playerGameMap.remove(p.getUniqueId());
        if (gameId == null) return;
        ValorantGame game = games.get(gameId);
        if (game != null) game.removePlayer(p);
    }

    public void removeGame(String id) {
        ValorantGame game = games.get(id);
        if (game != null) {
            game.shutdown();
            games.remove(id);
        }
    }

    public void shutdown() {
        games.forEach((id, game) -> game.shutdown());
        games.clear();
        playerGameMap.clear();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public ValorantGame getGame(String id)    { return games.get(id); }
    public ValorantGame getGame(Player p)     {
        String id = playerGameMap.get(p.getUniqueId());
        return id == null ? null : games.get(id);
    }
    public boolean isInGame(Player p)         { return playerGameMap.containsKey(p.getUniqueId()); }
    public Collection<ValorantGame> getGames(){ return games.values(); }
    public Set<String> getGameIds()           { return games.keySet(); }

    // ── Admin helpers ─────────────────────────────────────────────────────────

    /** Force-start a game regardless of player count */
    public boolean forceStart(String gameId, String mapName) {
        ValorantGame game = games.get(gameId);
        if (game == null) return false;
        game.start(mapName);
        return true;
    }

    /** Kick a player from their current game */
    public boolean kickFromGame(Player p, org.bukkit.Location lobbySpawn) {
        ValorantGame game = getGame(p);
        if (game == null) return false;
        game.removePlayer(p);
        playerGameMap.remove(p.getUniqueId());
        if (lobbySpawn != null) p.teleport(lobbySpawn);
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        return true;
    }

    /** Get all active games */
    public java.util.Collection<ValorantGame> getAllGames() { return games.values(); }

    /**
     * Join the first waiting game, or auto-create one and start it on a random map.
     * Used by the Quick Play button.
     */
    public void quickPlay(Player p) {
        if (isInGame(p)) {
            p.sendMessage(plugin.msg("game.already-in-game"));
            return;
        }
        ValorantGame game = games.values().stream()
                .filter(g -> g.getState() == com.valorantmc.game.GameState.WAITING)
                .findFirst().orElse(null);
        boolean createdNew = false;
        if (game == null) {
            String id = "quick-" + System.currentTimeMillis();
            game = createGame(id);
            createdNew = true;
        }
        game.addPlayer(p);
        playerGameMap.put(p.getUniqueId(), game.getId());

        if (createdNew) {
            // Start on a random loaded map in 3 seconds so others can join
            final ValorantGame g = game;
            p.sendMessage(plugin.colorize("&aMatch starting in 3 seconds..."));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (g.getState() == com.valorantmc.game.GameState.WAITING) {
                    List<String> names = new ArrayList<>(plugin.getMapManager().getMapNames());
                    if (!names.isEmpty()) {
                        String map = names.get(new Random().nextInt(names.size()));
                        g.start(map);
                    }
                }
            }, 60L);
        }
    }
}
