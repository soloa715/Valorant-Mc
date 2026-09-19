package com.valorantmc.mod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BuyActionPayload(String weaponName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BuyActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ValorantMCMod.MOD_ID, "buyaction"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyActionPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUtf(value.weaponName(), 32),
            buf -> new BuyActionPayload(buf.readUtf(32))
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
