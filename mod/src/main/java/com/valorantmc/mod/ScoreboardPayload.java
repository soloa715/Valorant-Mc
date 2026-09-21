package com.valorantmc.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ScoreboardPayload(
        int atkScore,
        int defScore,
        int round,
        String gameState,
        List<String> rows
) {
    public static void encode(ScoreboardPayload msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.atkScore);
        buf.writeInt(msg.defScore);
        buf.writeInt(msg.round);
        buf.writeUtf(msg.gameState != null ? msg.gameState : "");
        buf.writeInt(msg.rows.size());
        for (String r : msg.rows) buf.writeUtf(r);
    }

    public static ScoreboardPayload decode(FriendlyByteBuf buf) {
        int atk   = buf.readInt();
        int def   = buf.readInt();
        int rd    = buf.readInt();
        String st = buf.readUtf(256);
        int count = buf.readInt();
        List<String> rList = new ArrayList<>();
        for (int i = 0; i < count; i++) rList.add(buf.readUtf(512));
        return new ScoreboardPayload(atk, def, rd, st, rList);
    }

    public static void handle(ScoreboardPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new ScoreboardScreen(msg));
        });
        ctx.setPacketHandled(true);
    }
}
