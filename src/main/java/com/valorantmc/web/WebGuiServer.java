package com.valorantmc.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.managers.MapManager;
import org.bukkit.Bukkit;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Embedded Web GUI Admin Panel & Top-Down 2D Map Visualizer running on port 8766.
 */
public class WebGuiServer {

    private final ValorantMC plugin;
    private final int port;
    private HttpServer server;

    public WebGuiServer(ValorantMC plugin, int port) {
        this.plugin = plugin;
        this.port = port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            // Handlers
            server.createContext("/", new StaticHandler());
            server.createContext("/api/stats", new StatsHandler());
            server.createContext("/api/maps", new MapsHandler());
            server.createContext("/api/mapdetails", new MapDetailsHandler());
            server.createContext("/api/games", new GamesHandler());
            server.createContext("/api/agents", new AgentsHandler());
            server.createContext("/api/command", new CommandHandler());
            server.createContext("/api/resetmap", new ResetMapHandler());

            server.start();
            plugin.getLogger().info("[WebGUI] Admin Panel running at http://localhost:" + port);
        } catch (Exception e) {
            plugin.getLogger().warning("[WebGUI] Failed to start Web GUI on port " + port + ": " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("[WebGUI] Stopped Admin Panel.");
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                String html = getHtmlDashboard();
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                String json = String.format(
                        "{\"onlinePlayers\":%d,\"maxPlayers\":%d,\"activeGames\":%d,\"loadedMaps\":%d,\"weapons\":%d,\"agents\":%d,\"version\":\"1.0.0\"}",
                        Bukkit.getOnlinePlayers().size(),
                        Bukkit.getMaxPlayers(),
                        plugin.getGameManager().getGames().size(),
                        plugin.getMapManager().getMapCount(),
                        plugin.getWeaponManager().getWeaponCount(),
                        plugin.getAgentManager().getAgentCount()
                );
                sendJson(exchange, json);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class MapsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                StringBuilder sb = new StringBuilder("[");
                Set<String> names = plugin.getMapManager().getMapNames();
                int idx = 0;
                for (String name : names) {
                    MapManager.ValorantMap map = plugin.getMapManager().getMap(name);
                    if (map == null) continue;
                    if (idx > 0) sb.append(",");
                    sb.append(String.format(
                            "{\"name\":\"%s\",\"displayName\":\"%s\",\"world\":\"%s\",\"atkSpawns\":%d,\"defSpawns\":%d,\"siteA\":%d,\"siteB\":%d,\"siteRadius\":%.1f}",
                            escapeJson(map.getName()),
                            escapeJson(map.getDisplayName()),
                            escapeJson(map.getAttackSpawns().isEmpty() ? "valmap_" + map.getName() : map.getAttackSpawns().get(0).getWorld().getName()),
                            map.getAttackSpawns().size(),
                            map.getDefendSpawns().size(),
                            map.getSiteA().size(),
                            map.getSiteB().size(),
                            map.getSiteRadius()
                    ));
                    idx++;
                }
                sb.append("]");
                sendJson(exchange, sb.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class MapDetailsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                String query = exchange.getRequestURI().getQuery();
                String mapName = "ascent";
                if (query != null && query.contains("map=")) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("map=")) {
                            mapName = param.substring(4);
                            break;
                        }
                    }
                }
                MapManager.ValorantMap map = plugin.getMapManager().getMap(mapName);
                if (map == null) {
                    sendJson(exchange, "{\"error\":\"Map not found\"}", 404);
                    return;
                }
                StringBuilder sb = new StringBuilder("{");
                sb.append(String.format("\"name\":\"%s\",", escapeJson(map.getName())));
                sb.append(String.format("\"displayName\":\"%s\",", escapeJson(map.getDisplayName())));
                sb.append(String.format("\"siteRadius\":%.1f,", map.getSiteRadius()));

                // Attack spawns
                sb.append("\"attackSpawns\":[");
                List<org.bukkit.Location> atk = map.getAttackSpawns();
                for (int i = 0; i < atk.size(); i++) {
                    org.bukkit.Location l = atk.get(i);
                    if (i > 0) sb.append(",");
                    sb.append(String.format("{\"x\":%d,\"y\":%d,\"z\":%d,\"yaw\":%d,\"pitch\":%d}", l.getBlockX(), l.getBlockY(), l.getBlockZ(), Math.round(l.getYaw()), Math.round(l.getPitch())));
                }
                sb.append("],");

                // Defend spawns
                sb.append("\"defendSpawns\":[");
                List<org.bukkit.Location> def = map.getDefendSpawns();
                for (int i = 0; i < def.size(); i++) {
                    org.bukkit.Location l = def.get(i);
                    if (i > 0) sb.append(",");
                    sb.append(String.format("{\"x\":%d,\"y\":%d,\"z\":%d,\"yaw\":%d,\"pitch\":%d}", l.getBlockX(), l.getBlockY(), l.getBlockZ(), Math.round(l.getYaw()), Math.round(l.getPitch())));
                }
                sb.append("],");

                // Site A
                sb.append("\"siteA\":[");
                List<org.bukkit.Location> siteA = map.getSiteA();
                for (int i = 0; i < siteA.size(); i++) {
                    org.bukkit.Location l = siteA.get(i);
                    if (i > 0) sb.append(",");
                    sb.append(String.format("{\"x\":%d,\"y\":%d,\"z\":%d}", l.getBlockX(), l.getBlockY(), l.getBlockZ()));
                }
                sb.append("],");

                // Site B
                sb.append("\"siteB\":[");
                List<org.bukkit.Location> siteB = map.getSiteB();
                for (int i = 0; i < siteB.size(); i++) {
                    org.bukkit.Location l = siteB.get(i);
                    if (i > 0) sb.append(",");
                    sb.append(String.format("{\"x\":%d,\"y\":%d,\"z\":%d}", l.getBlockX(), l.getBlockY(), l.getBlockZ()));
                }
                sb.append("]}");

                sendJson(exchange, sb.toString());
            } catch (Exception e) {
                sendJson(exchange, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}", 500);
            }
        }
    }

    private class GamesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                StringBuilder sb = new StringBuilder("[");
                Collection<ValorantGame> games = plugin.getGameManager().getGames();
                int idx = 0;
                for (ValorantGame g : games) {
                    if (idx > 0) sb.append(",");
                    sb.append(String.format(
                            "{\"id\":\"%s\",\"map\":\"%s\",\"state\":\"%s\",\"round\":%d,\"atkScore\":%d,\"defScore\":%d,\"players\":%d}",
                            escapeJson(g.getId()),
                            escapeJson(g.getMap() != null ? g.getMap().getDisplayName() : "None"),
                            g.getState().name(),
                            g.getCurrentRound(),
                            g.getAttackers().getRoundWins(),
                            g.getDefenders().getRoundWins(),
                            g.getAllPlayers().size()
                    ));
                    idx++;
                }
                sb.append("]");
                sendJson(exchange, sb.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class AgentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            try {
                StringBuilder sb = new StringBuilder("[");
                Collection<Agent> agents = plugin.getAgentManager().getAllAgents();
                int idx = 0;
                for (Agent a : agents) {
                    if (idx > 0) sb.append(",");
                    sb.append(String.format(
                            "{\"id\":\"%s\",\"name\":\"%s\",\"role\":\"%s\"}",
                            escapeJson(a.getName().toLowerCase()),
                            escapeJson(a.getDisplayName()),
                            escapeJson(a.getRole() != null ? a.getRole().name() : "AGENT")
                    ));
                    idx++;
                }
                sb.append("]");
                sendJson(exchange, sb.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class CommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, "{\"error\":\"POST required\"}", 405);
                return;
            }
            try {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                String cmd = parseJsonField(body, "command");
                if (cmd == null || cmd.trim().isEmpty()) {
                    sendJson(exchange, "{\"error\":\"Empty command\"}", 400);
                    return;
                }
                cmd = cmd.trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);

                final String finalCmd = cmd;
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));

                sendJson(exchange, String.format("{\"success\":true,\"message\":\"Dispatched: /%s\"}", escapeJson(finalCmd)));
            } catch (Exception e) {
                sendJson(exchange, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}", 500);
            }
        }
    }

    private class ResetMapHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, "{\"error\":\"POST required\"}", 405);
                return;
            }
            try {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                String mapName = parseJsonField(body, "map");
                if (mapName == null || mapName.isEmpty()) {
                    sendJson(exchange, "{\"error\":\"Missing map name\"}", 400);
                    return;
                }
                boolean ok = plugin.getMapManager().resetMap(mapName);
                sendJson(exchange, String.format("{\"success\":%b,\"map\":\"%s\"}", ok, escapeJson(mapName)));
            } catch (Exception e) {
                sendJson(exchange, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}", 500);
            }
        }
    }

    private void sendJson(HttpExchange exchange, String json) {
        sendJson(exchange, json, 200);
    }

    private void sendJson(HttpExchange exchange, String json, int code) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[WebGUI] Error sending response: " + e.getMessage());
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String parseJsonField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int kIdx = json.indexOf(key);
        if (kIdx == -1) return null;
        int colon = json.indexOf(":", kIdx + key.length());
        if (colon == -1) return null;
        int startQuote = json.indexOf("\"", colon + 1);
        if (startQuote == -1) return null;
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote == -1) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    // ── HTML Dashboard ───────────────────────────────────────────────────────

    private String getHtmlDashboard() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>ValorantMC — Web Admin Dashboard</title>\n" +
                "  <link href=\"https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700;900&family=Inter:wght@300;400;500;600&display=swap\" rel=\"stylesheet\">\n" +
                "  <style>\n" +
                "    :root {\n" +
                "      --bg-dark: #0f1419;\n" +
                "      --bg-card: #18222d;\n" +
                "      --bg-card-hover: #1f2c3b;\n" +
                "      --val-red: #ff4655;\n" +
                "      --val-red-hover: #e03b49;\n" +
                "      --cyan-accent: #00f0ff;\n" +
                "      --text-main: #ece8e1;\n" +
                "      --text-sub: #768089;\n" +
                "      --border-color: rgba(255, 255, 255, 0.08);\n" +
                "    }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; }\n" +
                "    body {\n" +
                "      background-color: var(--bg-dark);\n" +
                "      color: var(--text-main);\n" +
                "      font-family: 'Inter', sans-serif;\n" +
                "      min-height: 100vh;\n" +
                "    }\n" +
                "    header {\n" +
                "      background: #111721;\n" +
                "      border-bottom: 2px solid var(--val-red);\n" +
                "      padding: 16px 32px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: space-between;\n" +
                "    }\n" +
                "    .logo {\n" +
                "      font-family: 'Outfit', sans-serif;\n" +
                "      font-weight: 900;\n" +
                "      font-size: 24px;\n" +
                "      letter-spacing: 2px;\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 12px;\n" +
                "    }\n" +
                "    .badge-v {\n" +
                "      background: var(--val-red);\n" +
                "      color: #fff;\n" +
                "      padding: 2px 10px;\n" +
                "      border-radius: 4px;\n" +
                "      font-size: 12px;\n" +
                "      font-weight: 700;\n" +
                "    }\n" +
                "    nav {\n" +
                "      display: flex;\n" +
                "      gap: 12px;\n" +
                "    }\n" +
                "    .nav-btn {\n" +
                "      background: transparent;\n" +
                "      border: none;\n" +
                "      color: var(--text-sub);\n" +
                "      font-weight: 600;\n" +
                "      font-size: 14px;\n" +
                "      padding: 8px 16px;\n" +
                "      cursor: pointer;\n" +
                "      border-radius: 6px;\n" +
                "      transition: 0.2s;\n" +
                "    }\n" +
                "    .nav-btn.active, .nav-btn:hover {\n" +
                "      color: #fff;\n" +
                "      background: rgba(255, 70, 85, 0.15);\n" +
                "    }\n" +
                "    .container {\n" +
                "      max-width: 1200px;\n" +
                "      margin: 32px auto;\n" +
                "      padding: 0 24px;\n" +
                "    }\n" +
                "    .tab-content {\n" +
                "      display: none;\n" +
                "    }\n" +
                "    .tab-content.active {\n" +
                "      display: block;\n" +
                "    }\n" +
                "    .grid-4 {\n" +
                "      display: grid;\n" +
                "      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));\n" +
                "      gap: 20px;\n" +
                "      margin-bottom: 32px;\n" +
                "    }\n" +
                "    .stat-card {\n" +
                "      background: var(--bg-card);\n" +
                "      border: 1px solid var(--border-color);\n" +
                "      padding: 24px;\n" +
                "      border-radius: 12px;\n" +
                "      position: relative;\n" +
                "      overflow: hidden;\n" +
                "    }\n" +
                "    .stat-card::before {\n" +
                "      content: '';\n" +
                "      position: absolute;\n" +
                "      top: 0; left: 0; width: 4px; height: 100%;\n" +
                "      background: var(--val-red);\n" +
                "    }\n" +
                "    .stat-title {\n" +
                "      color: var(--text-sub);\n" +
                "      font-size: 13px;\n" +
                "      text-transform: uppercase;\n" +
                "      letter-spacing: 1px;\n" +
                "      font-weight: 600;\n" +
                "    }\n" +
                "    .stat-value {\n" +
                "      font-family: 'Outfit', sans-serif;\n" +
                "      font-size: 36px;\n" +
                "      font-weight: 700;\n" +
                "      margin-top: 8px;\n" +
                "    }\n" +
                "    .maps-grid {\n" +
                "      display: grid;\n" +
                "      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));\n" +
                "      gap: 20px;\n" +
                "    }\n" +
                "    .map-card {\n" +
                "      background: var(--bg-card);\n" +
                "      border: 1px solid var(--border-color);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 20px;\n" +
                "      display: flex;\n" +
                "      flex-direction: column;\n" +
                "      justify-content: space-between;\n" +
                "    }\n" +
                "    .map-name {\n" +
                "      font-family: 'Outfit', sans-serif;\n" +
                "      font-size: 22px;\n" +
                "      font-weight: 700;\n" +
                "      color: var(--cyan-accent);\n" +
                "      margin-bottom: 4px;\n" +
                "    }\n" +
                "    .map-world { font-size: 12px; color: var(--text-sub); margin-bottom: 16px; }\n" +
                "    .map-details {\n" +
                "      display: grid;\n" +
                "      grid-template-columns: 1fr 1fr;\n" +
                "      gap: 10px;\n" +
                "      font-size: 13px;\n" +
                "      margin-bottom: 20px;\n" +
                "    }\n" +
                "    .btn {\n" +
                "      background: var(--val-red);\n" +
                "      color: #fff;\n" +
                "      border: none;\n" +
                "      padding: 10px 16px;\n" +
                "      border-radius: 6px;\n" +
                "      font-weight: 700;\n" +
                "      cursor: pointer;\n" +
                "      transition: 0.2s;\n" +
                "      width: 100%;\n" +
                "      text-align: center;\n" +
                "    }\n" +
                "    .btn:hover { background: var(--val-red-hover); }\n" +
                "    .btn-sec {\n" +
                "      background: #283747;\n" +
                "      margin-top: 8px;\n" +
                "    }\n" +
                "    .btn-sec:hover { background: #34495e; }\n" +
                "    /* Visualizer styles */\n" +
                "    .visualizer-container {\n" +
                "      background: var(--bg-card);\n" +
                "      border: 1px solid var(--border-color);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 24px;\n" +
                "    }\n" +
                "    .vis-header {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      justify-content: space-between;\n" +
                "      margin-bottom: 16px;\n" +
                "    }\n" +
                "    .select-map {\n" +
                "      background: #111721;\n" +
                "      border: 1px solid var(--border-color);\n" +
                "      color: #fff;\n" +
                "      padding: 8px 16px;\n" +
                "      border-radius: 6px;\n" +
                "      font-size: 14px;\n" +
                "      font-weight: 600;\n" +
                "    }\n" +
                "    .canvas-wrapper {\n" +
                "      position: relative;\n" +
                "      background: #090d12;\n" +
                "      border: 1px solid var(--border-color);\n" +
                "      border-radius: 8px;\n" +
                "      display: flex;\n" +
                "      justify-content: center;\n" +
                "      align-items: center;\n" +
                "      overflow: hidden;\n" +
                "    }\n" +
                "    #map-canvas {\n" +
                "      cursor: crosshair;\n" +
                "      display: block;\n" +
                "    }\n" +
                "    .legend {\n" +
                "      display: flex;\n" +
                "      gap: 16px;\n" +
                "      margin-top: 16px;\n" +
                "      font-size: 13px;\n" +
                "      align-items: center;\n" +
                "    }\n" +
                "    .legend-item {\n" +
                "      display: flex;\n" +
                "      align-items: center;\n" +
                "      gap: 6px;\n" +
                "    }\n" +
                "    .dot {\n" +
                "      width: 12px; height: 12px; border-radius: 50%; display: inline-block;\n" +
                "    }\n" +
                "    .dot-atk { background: var(--val-red); }\n" +
                "    .dot-def { background: var(--cyan-accent); }\n" +
                "    .dot-sitea { background: #00ff88; }\n" +
                "    .dot-siteb { background: #ffcc00; }\n" +
                "    .console-box {\n" +
                "      background: #090d12;\n" +
                "      border: 1px solid var(--border-color);\n" +
                "      border-radius: 12px;\n" +
                "      padding: 20px;\n" +
                "      font-family: monospace;\n" +
                "    }\n" +
                "    .console-output {\n" +
                "      height: 350px;\n" +
                "      overflow-y: auto;\n" +
                "      color: #00ff88;\n" +
                "      font-size: 14px;\n" +
                "      margin-bottom: 16px;\n" +
                "      padding: 12px;\n" +
                "      background: #05070a;\n" +
                "      border-radius: 6px;\n" +
                "    }\n" +
                "    .console-input-row {\n" +
                "      display: flex;\n" +
                "      gap: 12px;\n" +
                "    }\n" +
                "    .console-input {\n" +
                "      flex: 1;\n" +
                "      background: #111721;\n" +
                "      border: 1px solid var(--border-color);\n" +
                "      color: #fff;\n" +
                "      padding: 12px 16px;\n" +
                "      border-radius: 6px;\n" +
                "      font-size: 14px;\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <header>\n" +
                "    <div class=\"logo\">VALORANT<span>MC</span> <span class=\"badge-v\">ADMIN WEB GUI</span></div>\n" +
                "    <nav>\n" +
                "      <button class=\"nav-btn active\" onclick=\"showTab('overview')\">Overview</button>\n" +
                "      <button class=\"nav-btn\" onclick=\"showTab('maps')\">Map Manager</button>\n" +
                "      <button class=\"nav-btn\" onclick=\"showTab('visualizer')\">Map Visualizer</button>\n" +
                "      <button class=\"nav-btn\" onclick=\"showTab('games')\">Live Games</button>\n" +
                "      <button class=\"nav-btn\" onclick=\"showTab('agents')\">Agents</button>\n" +
                "      <button class=\"nav-btn\" onclick=\"showTab('console')\">Console</button>\n" +
                "    </nav>\n" +
                "  </header>\n" +
                "\n" +
                "  <div class=\"container\">\n" +
                "    <!-- OVERVIEW TAB -->\n" +
                "    <div id=\"tab-overview\" class=\"tab-content active\">\n" +
                "      <div class=\"grid-4\">\n" +
                "        <div class=\"stat-card\">\n" +
                "          <div class=\"stat-title\">Online Players</div>\n" +
                "          <div class=\"stat-value\" id=\"st-players\">0 / 20</div>\n" +
                "        </div>\n" +
                "        <div class=\"stat-card\">\n" +
                "          <div class=\"stat-title\">Active Matches</div>\n" +
                "          <div class=\"stat-value\" id=\"st-games\">0</div>\n" +
                "        </div>\n" +
                "        <div class=\"stat-card\">\n" +
                "          <div class=\"stat-title\">Loaded Maps</div>\n" +
                "          <div class=\"stat-value\" id=\"st-maps\">0</div>\n" +
                "        </div>\n" +
                "        <div class=\"stat-card\">\n" +
                "          <div class=\"stat-title\">Registered Agents</div>\n" +
                "          <div class=\"stat-value\" id=\"st-agents\">17</div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- MAPS TAB -->\n" +
                "    <div id=\"tab-maps\" class=\"tab-content\">\n" +
                "      <h2 style=\"margin-bottom:20px;\">Configured Map Worlds</h2>\n" +
                "      <div id=\"maps-list\" class=\"maps-grid\">Loading maps...</div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- VISUALIZER TAB -->\n" +
                "    <div id=\"tab-visualizer\" class=\"tab-content\">\n" +
                "      <div class=\"visualizer-container\">\n" +
                "        <div class=\"vis-header\">\n" +
                "          <h2>Top-Down 2D Map Visualizer</h2>\n" +
                "          <div>\n" +
                "            <label style=\"margin-right:8px;font-weight:600\">Select Map:</label>\n" +
                "            <select id=\"vis-map-select\" class=\"select-map\" onchange=\"loadVisualizerMap(this.value)\">\n" +
                "              <option value=\"ascent\">Ascent</option>\n" +
                "              <option value=\"abyss\">Abyss</option>\n" +
                "            </select>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "        <div class=\"canvas-wrapper\">\n" +
                "          <canvas id=\"map-canvas\" width=\"850\" height=\"520\"></canvas>\n" +
                "        </div>\n" +
                "        <div class=\"legend\">\n" +
                "          <div class=\"legend-item\"><span class=\"dot dot-atk\"></span> Attacker Spawns</div>\n" +
                "          <div class=\"legend-item\"><span class=\"dot dot-def\"></span> Defender Spawns</div>\n" +
                "          <div class=\"legend-item\"><span class=\"dot dot-sitea\"></span> Site A Box/Locations</div>\n" +
                "          <div class=\"legend-item\"><span class=\"dot dot-siteb\"></span> Site B Box/Locations</div>\n" +
                "          <div style=\"margin-left:auto;color:var(--text-sub)\" id=\"vis-coords-hover\">X: 0 | Z: 0</div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- GAMES TAB -->\n" +
                "    <div id=\"tab-games\" class=\"tab-content\">\n" +
                "      <h2 style=\"margin-bottom:20px;\">Active Match Monitor</h2>\n" +
                "      <div id=\"games-list\">No active matches currently running.</div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- AGENTS TAB -->\n" +
                "    <div id=\"tab-agents\" class=\"tab-content\">\n" +
                "      <h2 style=\"margin-bottom:20px;\">Valorant Agents Roster</h2>\n" +
                "      <div id=\"agents-list\" class=\"maps-grid\">Loading agents...</div>\n" +
                "    </div>\n" +
                "\n" +
                "    <!-- CONSOLE TAB -->\n" +
                "    <div id=\"tab-console\" class=\"tab-content\">\n" +
                "      <h2 style=\"margin-bottom:20px;\">Live Web Console</h2>\n" +
                "      <div class=\"console-box\">\n" +
                "        <div class=\"console-output\" id=\"console-out\">> Web Console initialized. Ready to execute commands.<br></div>\n" +
                "        <div class=\"console-input-row\">\n" +
                "          <input type=\"text\" class=\"console-input\" id=\"cmd-input\" placeholder=\"Type command (e.g. vmapsetup reset abyss or valorant start game1 abyss)...\" onkeydown=\"if(event.key==='Enter') sendCmd()\">\n" +
                "          <button class=\"btn\" style=\"width:140px;\" onclick=\"sendCmd()\">Execute</button>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <script>\n" +
                "    let currentMapDetails = null;\n" +
                "\n" +
                "    function showTab(id) {\n" +
                "      document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));\n" +
                "      document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));\n" +
                "      document.getElementById('tab-' + id).classList.add('active');\n" +
                "      event.target.classList.add('active');\n" +
                "      if(id === 'visualizer') {\n" +
                "        loadVisualizerMap(document.getElementById('vis-map-select').value);\n" +
                "      }\n" +
                "      refreshData();\n" +
                "    }\n" +
                "\n" +
                "    async function loadVisualizerMap(mapName) {\n" +
                "      try {\n" +
                "        let res = await fetch('/api/mapdetails?map=' + mapName);\n" +
                "        currentMapDetails = await res.json();\n" +
                "        renderMapCanvas();\n" +
                "      } catch(e) {}\n" +
                "    }\n" +
                "\n" +
                "    function renderMapCanvas() {\n" +
                "      let canvas = document.getElementById('map-canvas');\n" +
                "      let ctx = canvas.getContext('2d');\n" +
                "      let w = canvas.width;\n" +
                "      let h = canvas.height;\n" +
                "      ctx.clearRect(0, 0, w, h);\n" +
                "\n" +
                "      // Grid background\n" +
                "      ctx.strokeStyle = '#18222d';\n" +
                "      ctx.lineWidth = 1;\n" +
                "      for(let x = 0; x < w; x += 40) {\n" +
                "        ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, h); ctx.stroke();\n" +
                "      }\n" +
                "      for(let y = 0; y < h; y += 40) {\n" +
                "        ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();\n" +
                "      }\n" +
                "\n" +
                "      if(!currentMapDetails) return;\n" +
                "\n" +
                "      // Compute bounding box\n" +
                "      let allX = [], allZ = [];\n" +
                "      const addPts = (arr) => arr.forEach(p => { allX.push(p.x); allZ.push(p.z); });\n" +
                "      if(currentMapDetails.attackSpawns) addPts(currentMapDetails.attackSpawns);\n" +
                "      if(currentMapDetails.defendSpawns) addPts(currentMapDetails.defendSpawns);\n" +
                "      if(currentMapDetails.siteA) addPts(currentMapDetails.siteA);\n" +
                "      if(currentMapDetails.siteB) addPts(currentMapDetails.siteB);\n" +
                "\n" +
                "      if(allX.length === 0) {\n" +
                "        ctx.fillStyle = '#768089';\n" +
                "        ctx.font = '16px Inter';\n" +
                "        ctx.fillText('No coordinates configured for map ' + currentMapDetails.displayName, 240, 260);\n" +
                "        return;\n" +
                "      }\n" +
                "\n" +
                "      let minX = Math.min(...allX) - 30;\n" +
                "      let maxX = Math.max(...allX) + 30;\n" +
                "      let minZ = Math.min(...allZ) - 30;\n" +
                "      let maxZ = Math.max(...allZ) + 30;\n" +
                "\n" +
                "      let rangeX = Math.max(1, maxX - minX);\n" +
                "      let rangeZ = Math.max(1, maxZ - minZ);\n" +
                "\n" +
                "      function mapX(x) { return 60 + ((x - minX) / rangeX) * (w - 120); }\n" +
                "      function mapZ(z) { return 60 + ((z - minZ) / rangeZ) * (h - 120); }\n" +
                "\n" +
                "      // Draw Site A box / points\n" +
                "      if(currentMapDetails.siteA && currentMapDetails.siteA.length > 0) {\n" +
                "        let sA = currentMapDetails.siteA;\n" +
                "        ctx.fillStyle = 'rgba(0, 255, 136, 0.25)';\n" +
                "        ctx.strokeStyle = '#00ff88';\n" +
                "        ctx.lineWidth = 2;\n" +
                "        if(sA.length >= 2) {\n" +
                "          let x1 = mapX(Math.min(sA[0].x, sA[1].x));\n" +
                "          let x2 = mapX(Math.max(sA[0].x, sA[1].x));\n" +
                "          let z1 = mapZ(Math.min(sA[0].z, sA[1].z));\n" +
                "          let z2 = mapZ(Math.max(sA[0].z, sA[1].z));\n" +
                "          ctx.fillRect(x1, z1, Math.max(16, x2 - x1), Math.max(16, z2 - z1));\n" +
                "          ctx.strokeRect(x1, z1, Math.max(16, x2 - x1), Math.max(16, z2 - z1));\n" +
                "          ctx.fillStyle = '#00ff88';\n" +
                "          ctx.font = 'bold 14px Outfit';\n" +
                "          ctx.fillText('SITE A', x1 + 6, z1 + 20);\n" +
                "        }\n" +
                "      }\n" +
                "\n" +
                "      // Draw Site B box / points\n" +
                "      if(currentMapDetails.siteB && currentMapDetails.siteB.length > 0) {\n" +
                "        let sB = currentMapDetails.siteB;\n" +
                "        ctx.fillStyle = 'rgba(255, 204, 0, 0.25)';\n" +
                "        ctx.strokeStyle = '#ffcc00';\n" +
                "        ctx.lineWidth = 2;\n" +
                "        if(sB.length >= 2) {\n" +
                "          let x1 = mapX(Math.min(sB[0].x, sB[1].x));\n" +
                "          let x2 = mapX(Math.max(sB[0].x, sB[1].x));\n" +
                "          let z1 = mapZ(Math.min(sB[0].z, sB[1].z));\n" +
                "          let z2 = mapZ(Math.max(sB[0].z, sB[1].z));\n" +
                "          ctx.fillRect(x1, z1, Math.max(16, x2 - x1), Math.max(16, z2 - z1));\n" +
                "          ctx.strokeRect(x1, z1, Math.max(16, x2 - x1), Math.max(16, z2 - z1));\n" +
                "          ctx.fillStyle = '#ffcc00';\n" +
                "          ctx.font = 'bold 14px Outfit';\n" +
                "          ctx.fillText('SITE B', x1 + 6, z1 + 20);\n" +
                "        }\n" +
                "      }\n" +
                "\n" +
                "      // Draw Attack Spawns\n" +
                "      if(currentMapDetails.attackSpawns) {\n" +
                "        currentMapDetails.attackSpawns.forEach((p, i) => {\n" +
                "          let cx = mapX(p.x), cy = mapZ(p.z);\n" +
                "          ctx.fillStyle = '#ff4655';\n" +
                "          ctx.beginPath(); ctx.arc(cx, cy, 7, 0, Math.PI*2); ctx.fill();\n" +
                "          ctx.fillStyle = '#fff'; ctx.font = '10px Inter'; ctx.fillText('A' + (i+1), cx - 5, cy - 10);\n" +
                "        });\n" +
                "      }\n" +
                "\n" +
                "      // Draw Defend Spawns\n" +
                "      if(currentMapDetails.defendSpawns) {\n" +
                "        currentMapDetails.defendSpawns.forEach((p, i) => {\n" +
                "          let cx = mapX(p.x), cy = mapZ(p.z);\n" +
                "          ctx.fillStyle = '#00f0ff';\n" +
                "          ctx.beginPath(); ctx.arc(cx, cy, 7, 0, Math.PI*2); ctx.fill();\n" +
                "          ctx.fillStyle = '#fff'; ctx.font = '10px Inter'; ctx.fillText('D' + (i+1), cx - 5, cy - 10);\n" +
                "        });\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    async function refreshData() {\n" +
                "      try {\n" +
                "        let stats = await (await fetch('/api/stats')).json();\n" +
                "        document.getElementById('st-players').innerText = stats.onlinePlayers + ' / ' + stats.maxPlayers;\n" +
                "        document.getElementById('st-games').innerText = stats.activeGames;\n" +
                "        document.getElementById('st-maps').innerText = stats.loadedMaps;\n" +
                "        document.getElementById('st-agents').innerText = stats.agents;\n" +
                "\n" +
                "        let maps = await (await fetch('/api/maps')).json();\n" +
                "        let htmlMaps = '';\n" +
                "        let selectOptions = '';\n" +
                "        maps.forEach(m => {\n" +
                "          selectOptions += `<option value=\"${m.name}\">${m.displayName}</option>`;\n" +
                "          htmlMaps += `\n" +
                "            <div class=\"map-card\">\n" +
                "              <div>\n" +
                "                <div class=\"map-name\">${m.displayName}</div>\n" +
                "                <div class=\"map-world\">World: <b>${m.world}</b></div>\n" +
                "                <div class=\"map-details\">\n" +
                "                  <div>ATK Spawns: <b>${m.atkSpawns}</b></div>\n" +
                "                  <div>DEF Spawns: <b>${m.defSpawns}</b></div>\n" +
                "                  <div>Site A: <b>${m.siteA}</b></div>\n" +
                "                  <div>Site B: <b>${m.siteB}</b></div>\n" +
                "                  <div>Site Radius: <b>${m.siteRadius}m</b></div>\n" +
                "                </div>\n" +
                "              </div>\n" +
                "              <div>\n" +
                "                <button class=\"btn\" onclick=\"runCmd('vmapsetup edit ${m.name}')\">Edit Map In-Game</button>\n" +
                "                <button class=\"btn btn-sec\" onclick=\"resetMap('${m.name}')\">Reset Configuration</button>\n" +
                "              </div>\n" +
                "            </div>\n" +
                "          `;\n" +
                "        });\n" +
                "        document.getElementById('maps-list').innerHTML = htmlMaps;\n" +
                "        if(selectOptions) document.getElementById('vis-map-select').innerHTML = selectOptions;\n" +
                "\n" +
                "        let agents = await (await fetch('/api/agents')).json();\n" +
                "        let htmlAgents = '';\n" +
                "        agents.forEach(a => {\n" +
                "          htmlAgents += `\n" +
                "            <div class=\"map-card\">\n" +
                "              <div class=\"map-name\" style=\"color:#fff\">${a.name.toUpperCase()}</div>\n" +
                "              <div class=\"map-world\">Role: <b style=\"color:#00f0ff\">${a.role}</b></div>\n" +
                "            </div>\n" +
                "          `;\n" +
                "        });\n" +
                "        document.getElementById('agents-list').innerHTML = htmlAgents;\n" +
                "      } catch(e) {}\n" +
                "    }\n" +
                "\n" +
                "    async function sendCmd() {\n" +
                "      let input = document.getElementById('cmd-input');\n" +
                "      let cmd = input.value.trim();\n" +
                "      if(!cmd) return;\n" +
                "      runCmd(cmd);\n" +
                "      input.value = '';\n" +
                "    }\n" +
                "\n" +
                "    async function runCmd(cmd) {\n" +
                "      let out = document.getElementById('console-out');\n" +
                "      out.innerHTML += `<div>> /${cmd}</div>`;\n" +
                "      let res = await fetch('/api/command', {\n" +
                "        method: 'POST',\n" +
                "        headers: {'Content-Type': 'application/json'},\n" +
                "        body: JSON.stringify({command: cmd})\n" +
                "      });\n" +
                "      let data = await res.json();\n" +
                "      out.innerHTML += `<div style=\"color:#00f0ff\">${data.message || data.error}</div>`;\n" +
                "      out.scrollTop = out.scrollHeight;\n" +
                "      refreshData();\n" +
                "    }\n" +
                "\n" +
                "    async function resetMap(map) {\n" +
                "      if(!confirm('Reset map ' + map + ' to default settings?')) return;\n" +
                "      let res = await fetch('/api/resetmap', {\n" +
                "        method: 'POST',\n" +
                "        headers: {'Content-Type': 'application/json'},\n" +
                "        body: JSON.stringify({map: map})\n" +
                "      });\n" +
                "      let data = await res.json();\n" +
                "      alert(data.success ? 'Reset map ' + map + ' successfully!' : 'Failed to reset map.');\n" +
                "      refreshData();\n" +
                "    }\n" +
                "\n" +
                "    setInterval(refreshData, 3000);\n" +
                "    refreshData();\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
