package com.valorantmc.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record AgentSelectPayload(List<String> availableAgents, String myAgent) {

    public static void encode(AgentSelectPayload msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.availableAgents.size());
        for (String a : msg.availableAgents) buf.writeUtf(a);
        buf.writeUtf(msg.myAgent != null ? msg.myAgent : "");
    }

    public static AgentSelectPayload decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(buf.readUtf(256));
        String my = buf.readUtf(256);
        return new AgentSelectPayload(list, my);
    }

    public static void handle(AgentSelectPayload msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ValorantHudState.agentSelectList = msg.availableAgents;
            ValorantHudState.mySelectedAgent  = msg.myAgent;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null || !(mc.screen instanceof AgentSelectScreen)) {
                mc.setScreen(new AgentSelectScreen(msg.availableAgents, msg.myAgent));
            }
        });
        ctx.setPacketHandled(true);
    }
}
