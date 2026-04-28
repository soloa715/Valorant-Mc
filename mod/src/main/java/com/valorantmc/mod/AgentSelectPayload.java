package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record AgentSelectPayload(List<String> availableAgents, String myAgent) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AgentSelectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "agentselect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AgentSelectPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.availableAgents().size());
                for (String a : value.availableAgents()) buf.writeUtf(a);
                buf.writeUtf(value.myAgent());
            },
            buf -> {
                int size = buf.readVarInt();
                List<String> agents = new ArrayList<>(size);
                for (int i = 0; i < size; i++) agents.add(buf.readUtf());
                return new AgentSelectPayload(agents, buf.readUtf());
            }
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
