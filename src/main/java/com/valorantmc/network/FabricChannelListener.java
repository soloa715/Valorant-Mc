package com.valorantmc.network;

import com.valorantmc.ValorantMC;
import com.valorantmc.agents.Agent;
import com.valorantmc.game.ValorantGame;
import com.valorantmc.weapons.Weapon;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side half of the Fabric companion mod protocol.
 *
 * Channels:
 *   valorantmc:hello  (C→S) — client announces mod presence on join
 *   valorantmc:hud    (S→C) — server pushes HUD state every HUD tick
 *
 * Payload format for valorantmc:hud (all VarInts unless noted):
 *   byte    active (boolean)
 *   varint  health
 *   varint  shield
 *   varint  ammo
 *   varint  maxAmmo
 *   varint  reserve
 *   varint  chargesC
 *   varint  chargesQ
 *   varint  chargesE
 *   varint  ultProgress
 *   varint  ultMax
 *   string  agentName (varint length-prefix + UTF-8, max 64 chars)
 */
public class FabricChannelListener implements PluginMessageListener {

    public static final String CH_HELLO = "valorantmc:hello";
    public static final String CH_HUD   = "valorantmc:hud";

    private final ValorantMC plugin;
    private final Set<UUID> modUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public FabricChannelListener(ValorantMC plugin) {
        this.plugin = plugin;
    }

    public void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, CH_HELLO, this);
        messenger.registerOutgoingPluginChannel(plugin, CH_HUD);
    }

    public void unregister() {
        var messenger = plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, CH_HELLO, this);
        messenger.unregisterOutgoingPluginChannel(plugin, CH_HUD);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CH_HELLO.equals(channel)) return;
        modUsers.add(player.getUniqueId());
        plugin.getLogger().info("[FabricMod] Detected ValorantMC mod on: " + player.getName());
    }

    public boolean hasMod(Player player) {
        return modUsers.contains(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        modUsers.remove(player.getUniqueId());
    }

    // ── HUD packet sender ─────────────────────────────────────────────────────

    /**
     * Send a HUD state packet to the given player.
     * Call this only if {@link #hasMod(Player)} returns true.
     */
    public void sendHud(Player player, ValorantGame game) {
        if (!player.isOnline()) return;
        try {
            byte[] payload = buildHudPayload(player, game);
            player.sendPluginMessage(plugin, CH_HUD, payload);
        } catch (IOException e) {
            plugin.getLogger().warning("[FabricMod] HUD packet error: " + e.getMessage());
        }
    }

    private byte[] buildHudPayload(Player player, ValorantGame game) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(64);

        // active
        buf.write(game != null ? 1 : 0);

        if (game == null) {
            // fill with zeros so the client overlay hides itself
            for (int i = 0; i < 10; i++) writeVarInt(buf, 0);
            writeString(buf, "");
            return buf.toByteArray();
        }

        int health = game.getHealth(player);
        int shield = game.getShield(player);

        // Ammo from held weapon
        int ammo = 0, maxAmmo = 0, reserve = 0;
        Weapon w = plugin.getWeaponManager().getHeldWeapon(player);
        if (w != null) {
            ammo    = w.getCurrentAmmo();
            maxAmmo = w.getType().getMagazineSize();
            reserve = w.getReserveAmmo();
        }

        // Ability charges
        Agent agent = game.getAgent(player);
        int chargesC = -1, chargesQ = -1, chargesE = -1;
        int ultProg = 0, ultMax = 0;
        String agentName = "";
        if (agent != null) {
            chargesC  = agent.getAbilityC() != null ? agent.getAbilityC().getCurrentCharges()   : -1;
            chargesQ  = agent.getAbilityQ() != null ? agent.getAbilityQ().getCurrentCharges()   : -1;
            chargesE  = agent.getAbilityE() != null ? agent.getAbilityE().getCurrentCharges()   : -1;
            if (agent.getAbilityX() != null) {
                ultProg = agent.getAbilityX().getUltimateProgress();
                ultMax  = agent.getAbilityX().ultimatePoints;
            }
            agentName = agent.getDisplayName();
        }

        writeVarInt(buf, health);
        writeVarInt(buf, shield);
        writeVarInt(buf, ammo);
        writeVarInt(buf, maxAmmo);
        writeVarInt(buf, reserve);
        writeVarInt(buf, chargesC);
        writeVarInt(buf, chargesQ);
        writeVarInt(buf, chargesE);
        writeVarInt(buf, ultProg);
        writeVarInt(buf, ultMax);
        writeString(buf, agentName.length() > 64 ? agentName.substring(0, 64) : agentName);

        return buf.toByteArray();
    }

    // ── Encoding helpers ──────────────────────────────────────────────────────

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
