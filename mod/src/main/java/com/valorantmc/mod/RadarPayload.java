package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarPayload(String data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RadarPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "radar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadarPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.data(), 512),
            buf -> new RadarPayload(buf.readUtf(512))
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
