package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BuyMenuPayload(int credits, boolean inBuyPhase) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuyMenuPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "buymenu"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyMenuPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarInt(value.credits()); buf.writeBoolean(value.inBuyPhase()); },
            buf -> new BuyMenuPayload(buf.readVarInt(), buf.readBoolean())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
