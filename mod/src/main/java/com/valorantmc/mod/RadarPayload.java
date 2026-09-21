package com.valorantmc.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RadarPayload(String data) {

    public static void encode(RadarPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.data != null ? msg.data : "");
    }

    public static RadarPayload decode(FriendlyByteBuf buf) {
        return new RadarPayload(buf.readUtf(2048));
    }

    public static void handle(RadarPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> ValorantHudState.radarData = msg.data);
        ctx.setPacketHandled(true);
    }
}
