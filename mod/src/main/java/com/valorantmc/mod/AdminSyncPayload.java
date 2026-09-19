package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client: full admin panel state snapshot.
 *
 * players  : "name:uuid:hp:shield:credits:agent:team"  (one per player)
 * spawns   : "atk:x,y,z" or "def:x,y,z"
 * gameState: e.g. "ROUND_ACTIVE" or "WAITING"
 * round    : current round number
 * mapName  : e.g. "Haven" or "Arena"
 * mapList  : comma-joined list of available maps
 */
public record AdminSyncPayload(
        List<String> players,
        List<String> spawns,
        String gameState,
        int round,
        String mapName,
        String mapList
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "adminsync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminSyncPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.players().size());
                for (String s : v.players()) buf.writeUtf(s);
                buf.writeVarInt(v.spawns().size());
                for (String s : v.spawns()) buf.writeUtf(s);
                buf.writeUtf(v.gameState());
                buf.writeVarInt(v.round());
                buf.writeUtf(v.mapName());
                buf.writeUtf(v.mapList());
            },
            buf -> {
                int pSize = buf.readVarInt();
                List<String> players = new ArrayList<>(pSize);
                for (int i = 0; i < pSize; i++) players.add(buf.readUtf());
                int sSize = buf.readVarInt();
                List<String> spawns = new ArrayList<>(sSize);
                for (int i = 0; i < sSize; i++) spawns.add(buf.readUtf());
                return new AdminSyncPayload(players, spawns, buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readUtf());
            }
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
