package com.valorantmc.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record AdminSyncPayload(
        List<String> players,
        List<String> spawns,
        String gameState,
        int round,
        String mapName
) {
    public static void encode(AdminSyncPayload msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.players.size());
        for (String p : msg.players) buf.writeUtf(p);

        buf.writeInt(msg.spawns.size());
        for (String s : msg.spawns) buf.writeUtf(s);

        buf.writeUtf(msg.gameState != null ? msg.gameState : "");
        buf.writeInt(msg.round);
        buf.writeUtf(msg.mapName != null ? msg.mapName : "");
    }

    public static AdminSyncPayload decode(FriendlyByteBuf buf) {
        int pCount = buf.readInt();
        List<String> pList = new ArrayList<>();
        for (int i = 0; i < pCount; i++) pList.add(buf.readUtf(256));

        int sCount = buf.readInt();
        List<String> sList = new ArrayList<>();
        for (int i = 0; i < sCount; i++) sList.add(buf.readUtf(256));

        String state = buf.readUtf(256);
        int round    = buf.readInt();
        String map   = buf.readUtf(256);

        return new AdminSyncPayload(pList, sList, state, round, map);
    }

    public static void handle(AdminSyncPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof AdminScreen)) {
                mc.setScreen(new AdminScreen(msg));
            }
        });
        ctx.setPacketHandled(true);
    }
}
