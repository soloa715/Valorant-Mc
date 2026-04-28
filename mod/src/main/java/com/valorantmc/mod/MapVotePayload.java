package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MapVotePayload(String mapName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MapVotePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "mapvote"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapVotePayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.mapName()),
            buf -> new MapVotePayload(buf.readUtf())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
