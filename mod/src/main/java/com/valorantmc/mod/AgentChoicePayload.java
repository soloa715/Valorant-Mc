package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AgentChoicePayload(String agentName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AgentChoicePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "agentchoice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AgentChoicePayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.agentName()),
            buf -> new AgentChoicePayload(buf.readUtf())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
