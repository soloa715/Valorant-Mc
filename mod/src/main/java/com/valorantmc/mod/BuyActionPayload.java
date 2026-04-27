package com.valorantmc.mod;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client → server: player purchased a weapon by name. */
public record BuyActionPayload(String weaponName) implements CustomPayload {

    public static final CustomPayload.Id<BuyActionPayload> TYPE =
            new CustomPayload.Id<>(Identifier.of(ValorantMCMod.MOD_ID, "buyaction"));

    public static final PacketCodec<RegistryByteBuf, BuyActionPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.weaponName(), 32),
            buf -> new BuyActionPayload(buf.readString(32))
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return TYPE; }
}
