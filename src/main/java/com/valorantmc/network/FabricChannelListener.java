package com.valorantmc.network;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.game.GameState;
import com.valorantmc.game.Spike;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.game.ValorantTeam;
import com.valorantmc.weapons.Weapon;
import com.valorantmc.weapons.WeaponType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side half of the Fabric companion mod protocol.
 *
 * Channels (C→S):
 *   valorantmc:hello     — client announces mod presence on join
 *   valorantmc:buyaction — client purchased a weapon from mod buy screen
 *
 * Channels (S→C):
 *   valorantmc:hud     — HUD state every HUD tick
 *   valorantmc:buymenu — buy phase open/close + credits
 *   valorantmc:radar   — minimap player positions every 10 ticks
 *
 * HUD payload format (VarInts unless noted):
 *   byte    active
 *   varint  health, shield, ammo, maxAmmo, reserve
 *   varint  chargesC, chargesQ, chargesE
 *   varint  cooldownC, cooldownQ, cooldownE  (tenths of second, 0=ready)
 *   varint  ultProgress, ultMax
 *   string  agentName (64)
 *   varint  credits, atkScore, defScore
 *   varint  spikeState (0=none,1=planted,2=defusing)
 *   varint  spikeTimerTicks
 *   varint  roundPhase (0=inactive,1=buy,2=active,3=end)
 *   string  teamRoster (512)  "Name:HP:Shield:Agent,..."
 *   string  killFeed (64)     "Killer>Victim" or ""
 */
public class FabricChannelListener implements PluginMessageListener {

    public static final String CH_HELLO      = "valorantmc:hello";
    public static final String CH_BUY_ACTION = "valorantmc:buyaction";
    public static final String CH_HUD        = "valorantmc:hud";
    public static final String CH_BUY_MENU   = "valorantmc:buymenu";
    public static final String CH_RADAR      = "valorantmc:radar";

    private final ValorantMC plugin;
    private final Set<UUID> modUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Latest kill feed entry per game player — set by GameListener, read here
    private final java.util.Map<UUID, String> pendingKillFeed = new ConcurrentHashMap<>();

    public FabricChannelListener(ValorantMC plugin) {
        this.plugin = plugin;
    }

    public void register() {
        var m = plugin.getServer().getMessenger();
        m.registerIncomingPluginChannel(plugin, CH_HELLO,      this);
        m.registerIncomingPluginChannel(plugin, CH_BUY_ACTION, this);
        m.registerOutgoingPluginChannel(plugin, CH_HUD);
        m.registerOutgoingPluginChannel(plugin, CH_BUY_MENU);
        m.registerOutgoingPluginChannel(plugin, CH_RADAR);
    }

    public void unregister() {
        var m = plugin.getServer().getMessenger();
        m.unregisterIncomingPluginChannel(plugin, CH_HELLO,      this);
        m.unregisterIncomingPluginChannel(plugin, CH_BUY_ACTION, this);
        m.unregisterOutgoingPluginChannel(plugin, CH_HUD);
        m.unregisterOutgoingPluginChannel(plugin, CH_BUY_MENU);
        m.unregisterOutgoingPluginChannel(plugin, CH_RADAR);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        switch (channel) {
            case CH_HELLO -> {
                modUsers.add(player.getUniqueId());
                plugin.getLogger().info("[FabricMod] Detected ValorantMC mod on: " + player.getName());
            }
            case CH_BUY_ACTION -> handleBuyAction(player, message);
        }
    }

    public boolean hasMod(Player player) { return modUsers.contains(player.getUniqueId()); }

    public void removePlayer(Player player) { modUsers.remove(player.getUniqueId()); }

    // ── Kill feed (called by GameListener on kill) ────────────────────────────

    public void setPendingKillFeed(UUID playerUUID, String entry) {
        pendingKillFeed.put(playerUUID, entry);
    }

    // ── HUD packet ───────────────────────────────────────────────────────────

    public void sendHud(Player player, ValorantGame game) {
        if (!player.isOnline()) return;
        try {
            player.sendPluginMessage(plugin, CH_HUD, buildHudPayload(player, game));
        } catch (IOException e) {
            plugin.getLogger().warning("[FabricMod] HUD packet error: " + e.getMessage());
        }
    }

    // ── Buy menu packet ──────────────────────────────────────────────────────

    public void sendBuyMenu(Player player, boolean open) {
        if (!player.isOnline()) return;
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream(8);
            int credits = plugin.getEconomyManager().getCredits(player);
            writeVarInt(buf, credits);
            buf.write(open ? 1 : 0);
            player.sendPluginMessage(plugin, CH_BUY_MENU, buf.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[FabricMod] BuyMenu packet error: " + e.getMessage());
        }
    }

    // ── Radar packet ─────────────────────────────────────────────────────────

    public void sendRadar(Player player, ValorantGame game) {
        if (!player.isOnline() || game == null) return;
        try {
            String data = buildRadarData(player, game);
            ByteArrayOutputStream buf = new ByteArrayOutputStream(256);
            writeString(buf, data.length() > 512 ? data.substring(0, 512) : data);
            player.sendPluginMessage(plugin, CH_RADAR, buf.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[FabricMod] Radar packet error: " + e.getMessage());
        }
    }

    // ── Buy action handler ───────────────────────────────────────────────────

    private void handleBuyAction(Player player, byte[] message) {
        if (message.length == 0) return;
        // Read string: varint length + utf8 bytes
        int len = 0;
        int shift = 0;
        int idx = 0;
        while (idx < message.length) {
            int b = message[idx++] & 0xFF;
            len |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        if (idx + len > message.length) return;
        String weaponName = new String(message, idx, len, StandardCharsets.UTF_8);

        // Find WeaponType by name (case-insensitive)
        WeaponType type = null;
        for (WeaponType wt : WeaponType.values()) {
            if (wt.name().equalsIgnoreCase(weaponName) ||
                wt.getDisplayName().equalsIgnoreCase(weaponName)) {
                type = wt;
                break;
            }
        }
        if (type == null) return;

        ValorantGame game = plugin.getGameManager().getGame(player);
        if (game == null) return;

        final WeaponType finalType = type;
        plugin.getServer().getScheduler().runTask(plugin, () ->
            plugin.getShopManager().buyWeapon(player, finalType, game)
        );
    }

    // ── Payload builders ─────────────────────────────────────────────────────

    private byte[] buildHudPayload(Player player, ValorantGame game) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);

        buf.write(game != null ? 1 : 0);  // active

        if (game == null) {
            for (int i = 0; i < 16; i++) writeVarInt(buf, 0);
            writeString(buf, "");
            writeString(buf, "");
            writeString(buf, "");
            return buf.toByteArray();
        }

        int health = game.getHealth(player);
        int shield = game.getShield(player);

        // Ammo
        int ammo = 0, maxAmmo = 0, reserve = 0;
        Weapon w = plugin.getWeaponManager().getHeldWeapon(player);
        if (w != null) {
            ammo    = w.getCurrentAmmo();
            maxAmmo = w.getType().getMagazineSize();
            reserve = w.getReserveAmmo();
        }

        // Abilities
        Agent agent = game.getAgent(player);
        int chargesC = -1, chargesQ = -1, chargesE = -1;
        int cooldownC = 0, cooldownQ = 0, cooldownE = 0;
        int ultProg = 0, ultMax = 0;
        String agentName = "";
        if (agent != null) {
            if (agent.getAbilityC() != null) {
                chargesC  = agent.getAbilityC().getCurrentCharges();
                cooldownC = (int)(agent.getAbilityC().getCooldownSeconds() * 10);
            }
            if (agent.getAbilityQ() != null) {
                chargesQ  = agent.getAbilityQ().getCurrentCharges();
                cooldownQ = (int)(agent.getAbilityQ().getCooldownSeconds() * 10);
            }
            if (agent.getAbilityE() != null) {
                chargesE  = agent.getAbilityE().getCurrentCharges();
                cooldownE = (int)(agent.getAbilityE().getCooldownSeconds() * 10);
            }
            if (agent.getAbilityX() != null) {
                ultProg = agent.getAbilityX().getUltimateProgress();
                ultMax  = agent.getAbilityX().ultimatePoints;
            }
            agentName = agent.getDisplayName();
        }

        // Economy
        int credits = plugin.getEconomyManager().getCredits(player);

        // Score
        int atkScore = game.getAttackers().getRoundWins();
        int defScore = game.getDefenders().getRoundWins();

        // Spike
        Spike spike = game.getSpike();
        int spikeState = 0;
        int spikeTicks = 0;
        if (spike.getState() == Spike.SpikeState.PLANTED) {
            spikeState = 1;
            spikeTicks = spike.getDetonationCountdown() * 20; // seconds → ticks
        } else if (spike.getState() == Spike.SpikeState.DEFUSING) {
            spikeState = 2;
            spikeTicks = spike.getDetonationCountdown() * 20;
        }

        // Round phase
        int roundPhase = switch (game.getState()) {
            case BUY_PHASE    -> 1;
            case ROUND_ACTIVE -> 2;
            case ROUND_END, GAME_OVER -> 3;
            default -> 0;
        };

        // Team roster — "Name:HP:Shield:Agent,..."
        StringBuilder roster = new StringBuilder();
        List<Player> allPlayers = game.getAllPlayers();
        for (int i = 0; i < allPlayers.size(); i++) {
            Player p = allPlayers.get(i);
            if (p.equals(player)) continue;  // skip self
            Agent pa = game.getAgent(p);
            String aName = pa != null ? pa.getDisplayName() : "";
            roster.append(p.getName()).append(":")
                  .append(game.getHealth(p)).append(":")
                  .append(game.getShield(p)).append(":")
                  .append(aName);
            if (i < allPlayers.size() - 1) roster.append(",");
        }
        String rosterStr = roster.toString();
        if (rosterStr.length() > 512) rosterStr = rosterStr.substring(0, 512);

        // Kill feed — consume and clear pending entry
        String killFeed = pendingKillFeed.remove(player.getUniqueId());
        if (killFeed == null) killFeed = "";
        if (killFeed.length() > 64) killFeed = killFeed.substring(0, 64);

        // Write all fields in order (must match HudPayload CODEC)
        writeVarInt(buf, health);
        writeVarInt(buf, shield);
        writeVarInt(buf, ammo);
        writeVarInt(buf, maxAmmo);
        writeVarInt(buf, reserve);
        writeVarInt(buf, chargesC);
        writeVarInt(buf, chargesQ);
        writeVarInt(buf, chargesE);
        writeVarInt(buf, cooldownC);
        writeVarInt(buf, cooldownQ);
        writeVarInt(buf, cooldownE);
        writeVarInt(buf, ultProg);
        writeVarInt(buf, ultMax);
        writeString(buf, agentName.length() > 64 ? agentName.substring(0, 64) : agentName);
        writeVarInt(buf, credits);
        writeVarInt(buf, atkScore);
        writeVarInt(buf, defScore);
        writeVarInt(buf, spikeState);
        writeVarInt(buf, spikeTicks);
        writeVarInt(buf, roundPhase);
        writeString(buf, rosterStr);
        writeString(buf, killFeed);

        return buf.toByteArray();
    }

    private String buildRadarData(Player player, ValorantGame game) {
        org.bukkit.Location loc = player.getLocation();
        StringBuilder sb = new StringBuilder();
        sb.append((int)loc.getX()).append(":").append((int)loc.getZ()).append(":").append((int)loc.getYaw());

        ValorantTeam myTeam = game.getTeam(player);
        boolean first = true;
        for (Player other : game.getAllPlayers()) {
            if (other.equals(player)) continue;
            boolean ally = myTeam != null && myTeam.contains(other);
            org.bukkit.Location ol = other.getLocation();
            if (first) { sb.append("|"); first = false; }
            else sb.append(",");
            sb.append(other.getName()).append(":")
              .append((int)ol.getX()).append(":")
              .append((int)ol.getZ()).append(":")
              .append(ally ? "A" : "E");
        }
        return sb.toString();
    }

    // ── Encoding helpers ─────────────────────────────────────────────────────

    private static void writeVarInt(ByteArrayOutputStream buf, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) { buf.write(value); return; }
            buf.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static void writeString(ByteArrayOutputStream buf, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.write(bytes);
    }
}
