package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AdminActionPayload(String action, String targetUUID) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdminActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "adminaction"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminActionPayload> CODEC = StreamCodec.of(
            (buf, v) -> { buf.writeUtf(v.action()); buf.writeUtf(v.targetUUID()); },
            buf -> new AdminActionPayload(buf.readUtf(), buf.readUtf())
    );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
