package com.valorantmc.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record HelloPayload(String version) {

    public static void encode(HelloPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.version);
    }

    public static HelloPayload decode(FriendlyByteBuf buf) {
        return new HelloPayload(buf.readUtf(256));
    }

    public static void handle(HelloPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);
    }
}
