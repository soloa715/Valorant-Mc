package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HelloPayload(String version) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HelloPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "hello"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.version(), 32),
            buf -> new HelloPayload(buf.readUtf(32))
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
