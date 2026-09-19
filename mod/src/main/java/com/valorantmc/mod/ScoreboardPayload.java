package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client: full scoreboard snapshot.
 *
 * rows: "name:agent:kills:deaths:assists:credits:team:alive"  (one per player)
 * atkScore / defScore: current round wins
 * round: current round number
 * gameState: e.g. "ROUND_ACTIVE"
 */
public record ScoreboardPayload(
        List<String> rows,
        int atkScore,
        int defScore,
        int round,
        String gameState
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ScoreboardPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "scoreboard"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScoreboardPayload> CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.rows().size());
                for (String s : v.rows()) buf.writeUtf(s, 128);
                buf.writeVarInt(v.atkScore());
                buf.writeVarInt(v.defScore());
                buf.writeVarInt(v.round());
                buf.writeUtf(v.gameState(), 32);
            },
            buf -> {
                int n = buf.readVarInt();
                List<String> rows = new ArrayList<>(n);
                for (int i = 0; i < n; i++) rows.add(buf.readUtf(128));
                return new ScoreboardPayload(rows, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32));
            }
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
