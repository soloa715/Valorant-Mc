package com.valorantmc.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AdminActionPayload(String action, String targetUUID) {

    public static void encode(AdminActionPayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action);
        buf.writeUtf(msg.targetUUID != null ? msg.targetUUID : "");
    }

    public static AdminActionPayload decode(FriendlyByteBuf buf) {
        return new AdminActionPayload(buf.readUtf(256), buf.readUtf(256));
    }

    public static void handle(AdminActionPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);
    }
}
