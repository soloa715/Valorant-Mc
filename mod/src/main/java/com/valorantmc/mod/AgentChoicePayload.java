package com.valorantmc.mod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AgentChoicePayload(String agentName) {

    public static void encode(AgentChoicePayload msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.agentName);
    }

    public static AgentChoicePayload decode(FriendlyByteBuf buf) {
        return new AgentChoicePayload(buf.readUtf(256));
    }

    public static void handle(AgentChoicePayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.setPacketHandled(true);
    }
}
