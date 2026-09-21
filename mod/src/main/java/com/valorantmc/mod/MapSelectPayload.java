package com.valorantmc.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record MapSelectPayload(List<String> maps, String currentMap) {

    public static void encode(MapSelectPayload msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.maps.size());
        for (String m : msg.maps) buf.writeUtf(m);
        buf.writeUtf(msg.currentMap != null ? msg.currentMap : "");
    }

    public static MapSelectPayload decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(buf.readUtf(256));
        String cur = buf.readUtf(256);
        return new MapSelectPayload(list, cur);
    }

    public static void handle(MapSelectPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ValorantHudState.mapList    = msg.maps;
            ValorantHudState.currentMap = msg.currentMap;
        });
        ctx.setPacketHandled(true);
    }
}
