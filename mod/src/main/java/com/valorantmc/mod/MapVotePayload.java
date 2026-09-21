package com.valorantmc.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MapVotePayload(String mapName) {

    public static void encode(MapVotePayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.mapName);
    }

    public static MapVotePayload decode(FriendlyByteBuf buf) {
        return new MapVotePayload(buf.readUtf(256));
    }

    public static void handle(MapVotePayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);
    }
}
