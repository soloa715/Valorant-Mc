package com.valorantmc.managers;

import com.sun.net.httpserver.HttpServer;
import com.valorantmc.ValorantMC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Builds a resource-pack zip from the embedded pack/ folder on every plugin
 * start, then optionally self-hosts it via a tiny HTTP server so that players
 * without a pre-configured external URL still get the pack.
 *
 * Config keys used:
 *   resource-pack.enabled  – master switch
 *   resource-pack.url      – external URL; leave blank to use built-in server
 *   resource-pack.hash     – SHA-1 hex; leave blank to auto-compute
 *   resource-pack.port     – port for built-in server (default 8765)
 *   resource-pack.required – whether the pack is mandatory (kick if refused)
 *   resource-pack.message  – prompt message shown to players
 */
public class ResourcePackManager implements Listener {

    private final ValorantMC plugin;
    private HttpServer httpServer;
    private String packUrl;
    private byte[] packHash;  // raw 20-byte SHA-1

    public ResourcePackManager(ValorantMC plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", false)) return;

        File zipFile = buildZip();
        if (zipFile == null) {
            plugin.getLogger().warning("[ResourcePack] Failed to build pack zip — resource pack disabled.");
            return;
        }

        packHash = computeSha1(zipFile);

        String configUrl = plugin.getConfig().getString("resource-pack.url", "");
        if (configUrl != null && !configUrl.isBlank()) {
            packUrl = configUrl;
        } else {
            packUrl = startBuiltInServer(zipFile);
        }

        if (packUrl == null) {
            plugin.getLogger().warning("[ResourcePack] Could not determine pack URL — resource pack disabled.");
            return;
        }

        plugin.getLogger().info("[ResourcePack] Serving at " + packUrl);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    // ── Push pack to a player ─────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (packUrl == null) return;
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> sendPack(p), 20L);
    }

    public void sendPack(Player p) {
        if (packUrl == null || !p.isOnline()) return;
        String hashHex = packHash != null ? HexFormat.of().formatHex(packHash) : "";
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
        String msg = plugin.getConfig().getString("resource-pack.message",
                "&aInstall the ValorantMC resource pack for custom weapon models!");
        try {
            p.setResourcePack(packUrl, hashHex, ValorantMC.colorize(msg), required);
        } catch (Exception ex) {
            // Fallback for older Paper builds that lack the 4-arg overload
            p.setResourcePack(packUrl, hashHex);
        }
    }

    // ── Internal HTTP server ──────────────────────────────────────────────────

    private String startBuiltInServer(File zipFile) {
        int port = plugin.getConfig().getInt("resource-pack.port", 8765);
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/ValorantMC-pack.zip", exchange -> {
                byte[] data = Files.readAllBytes(zipFile.toPath());
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(data);
                }
            });
            httpServer.setExecutor(null);
            httpServer.start();

            // Try to find the public-facing IP from server.properties / config
            String host = plugin.getServer().getIp();
            if (host == null || host.isBlank()) host = detectPublicIp();
            return "http://" + host + ":" + port + "/ValorantMC-pack.zip";
        } catch (IOException ex) {
            plugin.getLogger().warning("[ResourcePack] Built-in HTTP server failed on port " + port + ": " + ex.getMessage());
            return null;
        }
    }

    private String detectPublicIp() {
        // Best-effort: return localhost so LAN servers work out of the box
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // ── Zip builder ───────────────────────────────────────────────────────────

    private File buildZip() {
        File out = new File(plugin.getDataFolder(), "ValorantMC-pack.zip");
        plugin.getDataFolder().mkdirs();

        // Read from the plugin jar
        File jarFile;
        try {
            jarFile = new File(plugin.getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            plugin.getLogger().warning("[ResourcePack] Cannot locate plugin jar: " + e.getMessage());
            return null;
        }

        try (ZipFile jar = new ZipFile(jarFile);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out))) {

            Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                // Only copy pack/ contents, strip the "pack/" prefix
                if (!name.startsWith("pack/") || name.equals("pack/")) continue;
                String outName = name.substring("pack/".length());
                if (outName.isBlank()) continue;

                zos.putNextEntry(new ZipEntry(outName));
                if (!entry.isDirectory()) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        in.transferTo(zos);
                    }
                }
                zos.closeEntry();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[ResourcePack] Failed to write zip: " + e.getMessage());
            return null;
        }

        return out;
    }

    // ── SHA-1 ─────────────────────────────────────────────────────────────────

    private byte[] computeSha1(File file) {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            return md.digest();
        } catch (Exception e) {
            plugin.getLogger().warning("[ResourcePack] SHA-1 failed: " + e.getMessage());
            return new byte[20];
        }
    }

    // ── Getter ────────────────────────────────────────────────────────────────
    public String getPackUrl() { return packUrl; }
}
