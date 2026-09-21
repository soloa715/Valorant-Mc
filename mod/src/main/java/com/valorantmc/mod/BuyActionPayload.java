package com.valorantmc.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BuyActionPayload(String item) {

    public static void encode(BuyActionPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.item);
    }

    public static BuyActionPayload decode(FriendlyByteBuf buf) {
        return new BuyActionPayload(buf.readUtf(256));
    }

    public static void handle(BuyActionPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);
    }
}
